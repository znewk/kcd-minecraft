package com.znewk.kcd.world.gen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Правила мира KCD: режим приключения (блоки не ломаются — откат к сохранению реален, мир не портится). */
public final class KcdWorldRules {
    private KcdWorldRules() {}

    public static boolean isKcdWorld(MinecraftServer server) {
        return server.overworld().getChunkSource().getGenerator() instanceof KcdChunkGenerator;
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (isKcdWorld(server)) server.setDefaultGameType(GameType.ADVENTURE);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isKcdWorld(p.server) && p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
            p.setGameMode(GameType.ADVENTURE);
        }
    }
}
