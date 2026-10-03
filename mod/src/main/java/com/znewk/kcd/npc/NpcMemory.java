package com.znewk.kcd.npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Память жителей о каждом игроке: репутация (-100..100) и факты (met, lied, seen:...).
 * Хранится в мире (data/kcd_npc_memory.dat). Забывание со временем — позже, вместе с распорядком.
 */
public class NpcMemory extends SavedData {
    private static final String FILE = "kcd_npc_memory";
    private static final Factory<NpcMemory> FACTORY = new Factory<>(NpcMemory::new, NpcMemory::load);

    private static final class Entry {
        int rep;
        final Set<String> facts = new HashSet<>();
    }

    private final Map<String, Map<UUID, Entry>> byNpc = new HashMap<>();

    public static NpcMemory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private Entry entry(String npc, UUID player) {
        return byNpc.computeIfAbsent(npc, k -> new HashMap<>()).computeIfAbsent(player, k -> new Entry());
    }

    private Entry peek(String npc, UUID player) {
        Map<UUID, Entry> m = byNpc.get(npc);
        return m == null ? null : m.get(player);
    }

    public int rep(String npc, UUID player) {
        Entry e = peek(npc, player);
        return e == null ? 0 : e.rep;
    }

    public void addRep(String npc, UUID player, int delta) {
        Entry e = entry(npc, player);
        e.rep = Mth.clamp(e.rep + delta, -100, 100);
        setDirty();
    }

    public boolean knows(String npc, UUID player, String fact) {
        Entry e = peek(npc, player);
        return e != null && e.facts.contains(fact);
    }

    public void remember(String npc, UUID player, String fact) {
        if (entry(npc, player).facts.add(fact)) setDirty();
    }

    public void forget(String npc, UUID player, String fact) {
        Entry e = peek(npc, player);
        if (e != null && e.facts.remove(fact)) setDirty();
    }

    /** Все жители забывают игрока (для тестов). */
    public void forgetPlayer(UUID player) {
        for (Map<UUID, Entry> m : byNpc.values()) m.remove(player);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag npcs = new CompoundTag();
        byNpc.forEach((npc, players) -> {
            CompoundTag pt = new CompoundTag();
            players.forEach((id, e) -> {
                CompoundTag et = new CompoundTag();
                et.putInt("rep", e.rep);
                ListTag facts = new ListTag();
                for (String f : e.facts) facts.add(StringTag.valueOf(f));
                et.put("facts", facts);
                pt.put(id.toString(), et);
            });
            npcs.put(npc, pt);
        });
        tag.put("npcs", npcs);
        return tag;
    }

    private static NpcMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcMemory mem = new NpcMemory();
        CompoundTag npcs = tag.getCompound("npcs");
        for (String npc : npcs.getAllKeys()) {
            CompoundTag pt = npcs.getCompound(npc);
            for (String id : pt.getAllKeys()) {
                CompoundTag et = pt.getCompound(id);
                Entry e = mem.entry(npc, UUID.fromString(id));
                e.rep = et.getInt("rep");
                for (Tag f : et.getList("facts", Tag.TAG_STRING)) e.facts.add(f.getAsString());
            }
        }
        return mem;
    }
}
