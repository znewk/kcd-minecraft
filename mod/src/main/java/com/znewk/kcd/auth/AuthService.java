package com.znewk.kcd.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.KcdPerms;
import com.znewk.kcd.network.AuthPayloads;

/**
 * Защита мира без паролей: у всех TLauncher (вход без Mojang), поэтому ник ничего не доказывает. При первом
 * входе ник привязывается к ПК — сервер выдаёт секретный ключ, игра хранит его в {@code <игра>/kcd/keys.json}
 * и дальше предъявляет сама. Кто зайдёт под тем же ником без ключа — будет отключён. Хост (владелец мира) не проверяется.
 * Сброс привязки: {@code /kcd auth reset <ник>}.
 */
public final class AuthService {
    private static final int TIMEOUT_TICKS = 200;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Ждём ответ: игрок → тик, когда спросили. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private AuthService() {}

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.server;
        if (!server.isDedicatedServer() && server.isSingleplayerOwner(p.getGameProfile())) return;
        PENDING.put(p.getUUID(), server.getTickCount());
        PacketDistributor.sendToPlayer(p, new AuthPayloads.Challenge(AuthData.get(server).worldId()));
    }

    public static void onResponse(ServerPlayer p, AuthPayloads.Response r) {
        if (PENDING.remove(p.getUUID()) == null) return;
        AuthData data = AuthData.get(p.server);
        String account = p.getGameProfile().getName();
        String known = data.hash(account);
        if (known == null) {
            // первый вход этого ника — привязываем к его ПК
            byte[] raw = new byte[24];
            RANDOM.nextBytes(raw);
            String key = HexFormat.of().formatHex(raw);
            data.bind(account, hash(key));
            p.server.overworld().getDataStorage().save(); // сразу на диск: вылет хоста не должен терять привязку
            PacketDistributor.sendToPlayer(p, new AuthPayloads.Issue(data.worldId(), key));
            KcdMod.LOGGER.info("KCD: ник {} привязан к ПК игрока", account);
        } else if (!known.equals(hash(r.key()))) {
            KcdMod.LOGGER.warn("KCD: вход под ником {} без верного ключа — отключён", account);
            p.connection.disconnect(Component.translatable("kcd.auth.denied", account));
        }
    }

    /** Кто не ответил за 10 секунд (нет мода/подделка) — отключить. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        PENDING.entrySet().removeIf(e -> {
            if (server.getTickCount() - e.getValue() < TIMEOUT_TICKS) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) p.connection.disconnect(Component.translatable("kcd.auth.timeout"));
            return true;
        });
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcd")
            .then(Commands.literal("auth").requires(KcdPerms::host)
                .then(Commands.literal("reset")
                    .then(Commands.argument("account", StringArgumentType.word()).executes(ctx -> {
                        String account = StringArgumentType.getString(ctx, "account");
                        boolean ok = AuthData.get(ctx.getSource().getServer()).unbind(account);
                        ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kcd.auth.reset" : "kcd.auth.unknown", account), true);
                        return ok ? 1 : 0;
                    })))));
    }
}
