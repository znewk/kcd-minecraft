package com.znewk.kcd.world.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.npc.KcdEntities;
import com.znewk.kcd.npc.KcdNpc;

/**
 * Жители мира KCD по плану ({@code {"type": "npc", "id": "kcd:martin", "x": .., "z": .., "yaw": 90}} в buildings.json).
 * Каждый появляется один раз — когда игроки впервые загрузят его место; дальше живёт как обычная сущность.
 */
public final class WorldNpcs {
    public record Planned(String id, int x, int z, int y, float yaw) {
        String key() {
            return id + "@" + x + "," + z;
        }
    }

    private static final List<Planned> PLANNED = new ArrayList<>();

    private WorldNpcs() {}

    static void plan(Buildings.Spec s, KcdMap map) {
        int y = s.params().has("y") ? s.num("y", 64) : map.surfaceY(s.x(), s.z()) + 1;
        float yaw = s.params().has("yaw") ? s.params().get("yaw").getAsFloat() : 0F;
        synchronized (PLANNED) {
            PLANNED.add(new Planned(s.str("id", "kcd:peasant"), s.x(), s.z(), y, yaw));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || !KcdWorldRules.isKcdWorld(server)) return;
        Buildings.ensureLoaded();
        ServerLevel level = server.overworld();
        Spawned data = Spawned.get(server);
        List<Planned> copy;
        synchronized (PLANNED) {
            copy = List.copyOf(PLANNED);
        }
        for (Planned p : copy) {
            if (data.keys.contains(p.key())) continue;
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            if (!level.isLoaded(pos) || !level.shouldTickBlocksAt(pos)) continue;
            KcdNpc npc = KcdEntities.NPC.get().create(level);
            if (npc == null) continue;
            npc.moveTo(p.x() + 0.5, p.y(), p.z() + 0.5, p.yaw(), 0F);
            npc.setYHeadRot(p.yaw());
            npc.setYBodyRot(p.yaw());
            npc.setNpcId(p.id());
            level.addFreshEntity(npc);
            data.keys.add(p.key());
            data.setDirty();
            KcdMod.LOGGER.debug("KCD: житель {} встал на место ({}, {})", p.id(), p.x(), p.z());
        }
    }

    /** Кто уже появился в этом мире (data/kcd_world_npcs.dat). */
    public static final class Spawned extends SavedData {
        private static final Factory<Spawned> FACTORY = new Factory<>(Spawned::new, Spawned::load);
        final Set<String> keys = new HashSet<>();

        static Spawned get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "kcd_world_npcs");
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (String k : keys) list.add(StringTag.valueOf(k));
            tag.put("spawned", list);
            return tag;
        }

        private static Spawned load(CompoundTag tag, HolderLookup.Provider registries) {
            Spawned s = new Spawned();
            for (Tag t : tag.getList("spawned", Tag.TAG_STRING)) s.keys.add(t.getAsString());
            return s;
        }
    }
}
