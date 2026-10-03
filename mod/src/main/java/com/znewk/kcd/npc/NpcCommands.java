package com.znewk.kcd.npc;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.znewk.kcd.KcdPerms;
import com.znewk.kcd.dialogue.DialogueService;
import com.znewk.kcd.stats.KcdStats;
import com.znewk.kcd.story.StoryFlags;

/** Команды хоста/отладки: жители, флаги сюжета, характеристики. Все — для оператора (уровень 2). */
public final class NpcCommands {
    private NpcCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // "kcd" сливается с командами отряда, поэтому права — на каждой ветке
        d.register(Commands.literal("kcd")
            .then(Commands.literal("npc").requires(KcdPerms::host)
                .then(Commands.literal("spawn")
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(NpcRegistry.ids(), b))
                        .executes(NpcCommands::spawn)))
                .then(Commands.literal("list").executes(NpcCommands::list))
                .then(Commands.literal("talk").executes(NpcCommands::talk))
                .then(Commands.literal("forget")
                    .then(Commands.argument("players", EntityArgument.players()).executes(NpcCommands::forget))))
            .then(Commands.literal("flag").requires(KcdPerms::host)
                .then(Commands.literal("list").executes(NpcCommands::flagList))
                .then(Commands.literal("set")
                    .then(Commands.argument("flag", StringArgumentType.word()).executes(ctx -> flag(ctx, true))))
                .then(Commands.literal("clear")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StoryFlags.get(ctx.getSource().getServer()).all(), b))
                        .executes(ctx -> flag(ctx, false)))))
            .then(Commands.literal("stat").requires(KcdPerms::host)
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(NpcCommands::statShow)
                    .then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(KcdStats.ALL, b))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, KcdStats.MAX))
                            .executes(NpcCommands::statSet))))));
    }

    /** Житель появляется в двух шагах перед игроком, лицом к нему. */
    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        if (NpcRegistry.get(id.toString()) == null) {
            ctx.getSource().sendFailure(Component.translatable("kcd.npc.unknown", id.toString()));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        KcdNpc npc = KcdEntities.NPC.get().create(level);
        if (npc == null) return 0;
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 pos = player.position().add(look.scale(2));
        float yaw = player.getYRot() + 180F;
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setNpcId(id.toString());
        level.addFreshEntity(npc);
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.npc.spawned", npc.getName()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        String ids = NpcRegistry.ids().stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.npc.list", ids), false);
        return NpcRegistry.ids().size();
    }

    /** Заговорить с ближайшим жителем (для тестов без мыши). */
    private static int talk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<KcdNpc> near = player.level().getEntitiesOfClass(KcdNpc.class, player.getBoundingBox().inflate(DialogueService.VIEW_RADIUS));
        KcdNpc npc = near.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.translatable("kcd.npc.none_near"));
            return 0;
        }
        DialogueService.start(player, npc);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        NpcMemory mem = NpcMemory.get(ctx.getSource().getServer());
        for (ServerPlayer p : players) mem.forgetPlayer(p.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.npc.forgot", players.size()), true);
        return players.size();
    }

    private static int flagList(CommandContext<CommandSourceStack> ctx) {
        Collection<String> flags = StoryFlags.get(ctx.getSource().getServer()).all();
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.flag.list", flags.isEmpty() ? "—" : String.join(", ", flags)), false);
        return flags.size();
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, boolean set) {
        String flag = StringArgumentType.getString(ctx, "flag");
        StoryFlags flags = StoryFlags.get(ctx.getSource().getServer());
        if (set) flags.set(flag);
        else flags.clear(flag);
        ctx.getSource().sendSuccess(() -> Component.translatable(set ? "kcd.flag.set" : "kcd.flag.cleared", flag), true);
        return 1;
    }

    private static int statShow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
            MutableComponent line = Component.literal(DialogueService.name(p) + ":");
            for (String s : KcdStats.ALL) {
                line.append(" ").append(Component.translatable("kcd.stat." + s)).append(" " + KcdStats.get(p, s));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int statSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String stat = StringArgumentType.getString(ctx, "stat");
        if (!KcdStats.ALL.contains(stat)) {
            ctx.getSource().sendFailure(Component.translatable("kcd.stat.unknown", stat, String.join(", ", KcdStats.ALL)));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer p : players) KcdStats.set(p, stat, value);
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.stat.set", Component.translatable("kcd.stat." + stat), value, players.size()), true);
        return players.size();
    }
}
