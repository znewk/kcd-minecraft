package com.znewk.kcd.quest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdPerms;
import com.znewk.kcd.KcdMod;
import com.znewk.kcd.network.QuestPayloads;

/**
 * Задания отряда: получить, отметить цель, запись в дневник, выполнить/провалить. Всё общее на отряд —
 * кто бы из братьев ни сделал шаг, он засчитывается всем, и у всех появляется плашка.
 */
public final class QuestService {
    private QuestService() {}

    public static void start(MinecraftServer server, String quest) {
        QuestDefinition def = QuestRegistry.get(quest);
        if (def == null) {
            KcdMod.LOGGER.warn("KCD: нет задания {}", quest);
            return;
        }
        QuestData data = QuestData.get(server);
        if (data.state(quest) != null) return;
        QuestData.State s = data.start(quest);
        if (def.entries().containsKey("start")) s.diary.add("start");
        notice(server, 0, def, "");
        sync(server);
    }

    public static void objective(MinecraftServer server, String quest, String objective) {
        QuestDefinition def = QuestRegistry.get(quest);
        QuestData data = QuestData.get(server);
        QuestData.State s = data.state(quest);
        if (def == null || s == null || s.status != QuestData.Status.ACTIVE || !def.hasObjective(objective)) return;
        if (!s.done.add(objective)) return;
        data.setDirty();
        String text = def.objectives().stream().filter(o -> o.id().equals(objective)).findFirst().map(QuestDefinition.Objective::text).orElse("");
        notice(server, 1, def, text);
        sync(server);
    }

    public static void diary(MinecraftServer server, String quest, String entry) {
        QuestDefinition def = QuestRegistry.get(quest);
        QuestData data = QuestData.get(server);
        QuestData.State s = data.state(quest);
        if (def == null || s == null || !def.entries().containsKey(entry) || s.diary.contains(entry)) return;
        s.diary.add(entry);
        data.setDirty();
        sync(server);
    }

    public static void finish(MinecraftServer server, String quest, boolean success) {
        QuestDefinition def = QuestRegistry.get(quest);
        QuestData data = QuestData.get(server);
        QuestData.State s = data.state(quest);
        if (def == null || s == null || s.status != QuestData.Status.ACTIVE) return;
        s.status = success ? QuestData.Status.DONE : QuestData.Status.FAILED;
        if (success) def.objectives().forEach(o -> s.done.add(o.id()));
        String entry = success ? "done" : "failed";
        if (def.entries().containsKey(entry) && !s.diary.contains(entry)) s.diary.add(entry);
        data.setDirty();
        notice(server, success ? 2 : 3, def, "");
        sync(server);
    }

    public static boolean isActive(MinecraftServer server, String quest) {
        QuestData.State s = QuestData.get(server).state(quest);
        return s != null && s.status == QuestData.Status.ACTIVE;
    }

    public static boolean isDone(MinecraftServer server, String quest) {
        QuestData.State s = QuestData.get(server).state(quest);
        return s != null && s.status == QuestData.Status.DONE;
    }

    public static boolean objectiveDone(MinecraftServer server, String quest, String objective) {
        QuestData.State s = QuestData.get(server).state(quest);
        return s != null && s.done.contains(objective);
    }

    // ------------------------------------------------------------------ рассылка

    private static void notice(MinecraftServer server, int kind, QuestDefinition def, String detail) {
        PacketDistributor.sendToAllPlayers(new QuestPayloads.Notice(kind, def.id(), def.title(), detail));
    }

    public static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(snapshot(server));
    }

    private static QuestPayloads.Sync snapshot(MinecraftServer server) {
        List<QuestPayloads.Quest> out = new ArrayList<>();
        QuestData.get(server).all().forEach((id, s) -> {
            QuestDefinition def = QuestRegistry.get(id);
            if (def == null) return;
            List<String> diary = new ArrayList<>();
            for (String e : s.diary) {
                String text = def.entries().get(e);
                if (text != null) diary.add(text);
            }
            List<QuestPayloads.Objective> objs = new ArrayList<>();
            for (QuestDefinition.Objective o : def.objectives()) {
                if (!s.done.containsAll(o.after())) continue;
                objs.add(new QuestPayloads.Objective(o.text(), s.done.contains(o.id())));
            }
            out.add(new QuestPayloads.Quest(id, def.title(), def.main(), s.status.ordinal(), diary, objs));
        });
        return new QuestPayloads.Sync(out);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, snapshot(p.server));
    }

    // ------------------------------------------------------------------ команды

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcd")
            .then(Commands.literal("quest").requires(KcdPerms::host)
                .then(Commands.literal("start").then(questArg().executes(ctx -> {
                    start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "quest"));
                    return 1;
                })))
                .then(Commands.literal("complete").then(questArg().executes(ctx -> {
                    finish(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "quest"), true);
                    return 1;
                })))
                .then(Commands.literal("reset").executes(QuestService::reset))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> questArg() {
        return Commands.argument("quest", StringArgumentType.word())
            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(QuestRegistry.ids(), b));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        QuestData.get(ctx.getSource().getServer()).clear();
        sync(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.quest.reset"), true);
        return 1;
    }
}
