package com.znewk.kcd.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Состояние заданий — одно на весь отряд (data/kcd_quests.dat). */
public class QuestData extends SavedData {
    private static final String FILE = "kcd_quests";
    private static final Factory<QuestData> FACTORY = new Factory<>(QuestData::new, QuestData::load);

    public enum Status { ACTIVE, DONE, FAILED }

    public static final class State {
        public Status status = Status.ACTIVE;
        public final Set<String> done = new LinkedHashSet<>();
        public final List<String> diary = new ArrayList<>();
    }

    /** В порядке получения. */
    private final Map<String, State> quests = new LinkedHashMap<>();

    public static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    @Nullable
    public State state(String quest) {
        return quests.get(quest);
    }

    public Map<String, State> all() {
        return quests;
    }

    public State start(String quest) {
        setDirty();
        return quests.computeIfAbsent(quest, k -> new State());
    }

    public void remove(String quest) {
        if (quests.remove(quest) != null) setDirty();
    }

    public void clear() {
        quests.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        ListTag order = new ListTag();
        quests.forEach((id, s) -> {
            CompoundTag q = new CompoundTag();
            q.putString("status", s.status.name());
            q.put("done", strings(s.done));
            q.put("diary", strings(s.diary));
            all.put(id, q);
            order.add(StringTag.valueOf(id));
        });
        tag.put("quests", all);
        tag.put("order", order);
        return tag;
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(StringTag.valueOf(v));
        return list;
    }

    private static QuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestData data = new QuestData();
        CompoundTag all = tag.getCompound("quests");
        for (Tag t : tag.getList("order", Tag.TAG_STRING)) {
            String id = t.getAsString();
            CompoundTag q = all.getCompound(id);
            State s = new State();
            s.status = Status.valueOf(q.getString("status"));
            for (Tag d : q.getList("done", Tag.TAG_STRING)) s.done.add(d.getAsString());
            for (Tag d : q.getList("diary", Tag.TAG_STRING)) s.diary.add(d.getAsString());
            data.quests.put(id, s);
        }
        return data;
    }
}
