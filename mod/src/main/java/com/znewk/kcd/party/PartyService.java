package com.znewk.kcd.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.network.PartyPayloads;

/** Серверная логика отряда: выбор роли при входе, имена персонажей, команды хоста. */
public final class PartyService {
    private PartyService() {}

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PartyData data = PartyData.get(player.server);
        sync(player.server);
        if (data.member(player.getUUID()).isEmpty()) askRole(player, "");
    }

    /** Отправить игроку экран выбора роли. */
    public static void askRole(ServerPlayer player, String error) {
        PartyData data = PartyData.get(player.server);
        String henryBy = data.henry().filter(h -> !h.id().equals(player.getUUID())).map(Member::account).orElse("");
        PacketDistributor.sendToPlayer(player, new PartyPayloads.OpenRoleSelect(henryBy, error));
    }

    public static void chooseRole(ServerPlayer player, PartyPayloads.ChooseRole choice) {
        MinecraftServer server = player.server;
        PartyData data = PartyData.get(server);
        if (data.member(player.getUUID()).isPresent()) return;

        Member member;
        if (choice.henry()) {
            Optional<Member> henry = data.henry();
            if (henry.isPresent()) {
                askRole(player, "kcd.role.error.henry_taken");
                return;
            }
            member = new Member(player.getUUID(), player.getGameProfile().getName(), Role.HENRY, Member.HENRY_NAME, 0);
        } else {
            String name = Member.cleanName(choice.name());
            if (name.length() < 2) {
                askRole(player, "kcd.role.error.name_short");
                return;
            }
            boolean taken = name.equalsIgnoreCase(Member.HENRY_NAME)
                || data.members().stream().anyMatch(m -> m.name().equalsIgnoreCase(name));
            if (taken) {
                askRole(player, "kcd.role.error.name_taken");
                return;
            }
            int story = Math.floorMod(choice.story(), Member.STORIES.length);
            member = new Member(player.getUUID(), player.getGameProfile().getName(), Role.BROTHER, name, story);
        }
        data.put(member);
        refreshNames(player);
        sync(server);
        MutableComponent msg = member.role() == Role.HENRY
            ? Component.translatable("kcd.role.announce.henry", player.getGameProfile().getName())
            : Component.translatable("kcd.role.announce.brother", player.getGameProfile().getName(), member.name());
        server.getPlayerList().broadcastSystemMessage(msg.withStyle(ChatFormatting.GOLD), false);
    }

    /** Разослать всем состав отряда. */
    public static void sync(MinecraftServer server) {
        PartyData data = PartyData.get(server);
        PacketDistributor.sendToAllPlayers(new PartyPayloads.PartySync(new ArrayList<>(data.members())));
    }

    public static void refreshNames(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    // ------------------------------------------------------------------ имена

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PartyData.get(player.server).member(player.getUUID())
            .ifPresent(m -> event.setDisplayname(Component.literal(m.name())));
    }

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PartyData.get(player.server).member(player.getUUID()).ifPresent(m -> event.setDisplayName(
            Component.literal(m.name()).withStyle(m.role() == Role.HENRY ? ChatFormatting.GOLD : ChatFormatting.WHITE)
                .append(Component.literal(" · " + m.account()).withStyle(ChatFormatting.GRAY))));
    }

    // ------------------------------------------------------------------ команды хоста

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcd")
            .then(Commands.literal("role")
                .then(Commands.literal("list").executes(PartyService::listRoles))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("players", EntityArgument.players()).executes(PartyService::resetRoles)))
                .then(Commands.literal("henry").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player()).executes(PartyService::makeHenry)))));
    }

    private static int listRoles(CommandContext<CommandSourceStack> ctx) {
        PartyData data = PartyData.get(ctx.getSource().getServer());
        if (data.members().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("kcd.role.list.empty"), false);
            return 0;
        }
        for (Member m : data.members()) {
            Component line = Component.literal("• " + m.name()).withStyle(m.role() == Role.HENRY ? ChatFormatting.GOLD : ChatFormatting.WHITE)
                .append(Component.literal(" — " + m.account()).withStyle(ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return data.members().size();
    }

    private static int resetRoles(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        PartyData data = PartyData.get(ctx.getSource().getServer());
        for (ServerPlayer p : players) {
            data.remove(p.getUUID());
            refreshNames(p);
            askRole(p, "");
        }
        sync(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.role.reset.done", players.size()), true);
        return players.size();
    }

    private static int makeHenry(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        PartyData data = PartyData.get(server);
        // бывший Индржих выбирает роль заново
        data.henry().filter(h -> !h.id().equals(target.getUUID())).ifPresent(old -> {
            data.remove(old.id());
            ServerPlayer oldPlayer = server.getPlayerList().getPlayer(old.id());
            if (oldPlayer != null) {
                refreshNames(oldPlayer);
                askRole(oldPlayer, "");
            }
        });
        data.put(new Member(target.getUUID(), target.getGameProfile().getName(), Role.HENRY, Member.HENRY_NAME, 0));
        refreshNames(target);
        sync(server);
        server.getPlayerList().broadcastSystemMessage(
            Component.translatable("kcd.role.announce.henry", target.getGameProfile().getName()).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
