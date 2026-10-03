package com.znewk.kcd.cutscene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.KcdPerms;
import com.znewk.kcd.network.CutscenePayloads;
import com.znewk.kcd.quest.QuestService;

/**
 * Кат-сцены для отряда. Сначала сбор: всем «Готов?»; сцена начинается, когда готовы все или вышло время —
 * отставших переносит к месту сцены. Во время сцены игроки неуязвимы, камера у всех одна и та же.
 * Пропуск — только если проголосовали все. Одновременно идёт одна сцена.
 */
public final class CutsceneService {
    private static final int GATHER_SECONDS = 30;
    private static final double GATHER_RADIUS = 16;

    private enum Phase { NONE, GATHER, PLAYING }

    private static Phase phase = Phase.NONE;
    private static Cutscene scene;
    private static Vec3 anchor;
    private static int phaseStart;
    private static final Set<UUID> PARTICIPANTS = new HashSet<>();
    private static final Set<UUID> VOTES = new HashSet<>();

    private CutsceneService() {}

    /** Запустить сцену (из разговора: действие cutscene:id, или командой). */
    public static void play(MinecraftServer server, String id) {
        if (phase != Phase.NONE) return;
        Cutscene c = CutsceneRegistry.get(id);
        if (c == null) {
            KcdMod.LOGGER.warn("KCD: нет кат-сцены {}", id);
            return;
        }
        Vec3 a = anchorPos(server, c.anchor());
        if (a == null) {
            KcdMod.LOGGER.warn("KCD: кат-сцена {}: якорь {} не найден", id, c.anchor());
            return;
        }
        scene = c;
        anchor = a;
        phase = Phase.GATHER;
        phaseStart = server.getTickCount();
        PARTICIPANTS.clear();
        VOTES.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) PARTICIPANTS.add(p.getUUID());
        sendGather(server);
    }

    @Nullable
    private static Vec3 anchorPos(MinecraftServer server, String anchor) {
        if (anchor.startsWith("pos:")) {
            String[] s = anchor.substring(4).split(",");
            return new Vec3(Double.parseDouble(s[0]), Double.parseDouble(s[1]), Double.parseDouble(s[2]));
        }
        return QuestService.targetPos(server, anchor);
    }

    private static void sendGather(MinecraftServer server) {
        int left = Math.max(0, GATHER_SECONDS - (server.getTickCount() - phaseStart) / 20);
        CutscenePayloads.Gather g = new CutscenePayloads.Gather(scene.title(), left, VOTES.size(), PARTICIPANTS.size());
        forEach(server, p -> PacketDistributor.sendToPlayer(p, g));
    }

    public static void onAction(ServerPlayer player, CutscenePayloads.Action a) {
        if (!PARTICIPANTS.contains(player.getUUID())) return;
        MinecraftServer server = player.server;
        if (a.action() == 0 && phase == Phase.GATHER) {
            VOTES.add(player.getUUID());
            if (VOTES.containsAll(online(server))) start(server);
            else sendGather(server);
        } else if (a.action() == 1 && phase == Phase.PLAYING) {
            VOTES.add(player.getUUID());
            if (VOTES.containsAll(online(server))) end(server);
            else {
                CutscenePayloads.State s = new CutscenePayloads.State(VOTES.size(), online(server).size());
                forEach(server, p -> PacketDistributor.sendToPlayer(p, s));
            }
        }
    }

    private static void start(MinecraftServer server) {
        phase = Phase.PLAYING;
        phaseStart = server.getTickCount();
        VOTES.clear();
        List<CutscenePayloads.Key> keys = new ArrayList<>();
        for (Cutscene.Key k : scene.camera()) {
            keys.add(new CutscenePayloads.Key(k.at(), anchor.x + k.pos()[0], anchor.y + k.pos()[1], anchor.z + k.pos()[2],
                anchor.x + k.look()[0], anchor.y + k.look()[1], anchor.z + k.look()[2]));
        }
        List<CutscenePayloads.Subtitle> subs = scene.subtitles().stream()
            .map(s -> new CutscenePayloads.Subtitle(s.at(), s.until(), s.who(), s.text())).toList();
        CutscenePayloads.Start start = new CutscenePayloads.Start(scene.duration(), keys, subs);
        Vec3 spot = safeSpot(server, scene.camera().get(0));
        forEach(server, p -> {
            // отставшие — к месту сцены
            if (p.level() != server.overworld() || p.position().distanceTo(anchor) > GATHER_RADIUS) {
                p.teleportTo(server.overworld(), spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
            }
            PacketDistributor.sendToPlayer(p, start);
        });
    }

    /** Место для отставших: в 3 блоках от якоря в сторону первой точки камеры, где ноги и голова свободны. */
    private static Vec3 safeSpot(MinecraftServer server, Cutscene.Key first) {
        Vec3 dir = new Vec3(first.pos()[0], 0, first.pos()[2]);
        dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
        ServerLevel level = server.overworld();
        BlockPos p = BlockPos.containing(anchor.add(dir.scale(3)));
        for (int i = 0; i < 6 && !(level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()); i++) {
            p = p.above();
        }
        return Vec3.atBottomCenterOf(p);
    }

    private static void end(MinecraftServer server) {
        CutscenePayloads.State done = new CutscenePayloads.State(0, 0);
        forEach(server, p -> PacketDistributor.sendToPlayer(p, done));
        phase = Phase.NONE;
        scene = null;
        PARTICIPANTS.clear();
        VOTES.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (phase == Phase.NONE) return;
        MinecraftServer server = event.getServer();
        int t = server.getTickCount() - phaseStart;
        PARTICIPANTS.retainAll(online(server));
        if (PARTICIPANTS.isEmpty()) {
            end(server);
        } else if (phase == Phase.GATHER) {
            if (t >= GATHER_SECONDS * 20) start(server);
            else if (t % 20 == 0) sendGather(server);
        } else if (t >= scene.duration()) {
            end(server);
        }
    }

    /** Во время сцены (и сбора) участники неуязвимы. */
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (phase != Phase.NONE && event.getEntity() instanceof ServerPlayer p && PARTICIPANTS.contains(p.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static Set<UUID> online(MinecraftServer server) {
        Set<UUID> out = new HashSet<>();
        for (UUID id : PARTICIPANTS) if (server.getPlayerList().getPlayer(id) != null) out.add(id);
        return out;
    }

    private static void forEach(MinecraftServer server, java.util.function.Consumer<ServerPlayer> action) {
        for (UUID id : PARTICIPANTS) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) action.accept(p);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcd")
            .then(Commands.literal("cutscene").requires(KcdPerms::host)
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CutsceneRegistry.ids(), b))
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        if (CutsceneRegistry.get(id) == null) {
                            ctx.getSource().sendFailure(Component.translatable("kcd.cutscene.unknown", id));
                            return 0;
                        }
                        play(ctx.getSource().getServer(), id);
                        return 1;
                    }))));
    }
}
