package com.znewk.kcd.story;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Флаги сюжета — общие на весь отряд («получил задание отца», «крестовина у нас»...). data/kcd_story.dat */
public class StoryFlags extends SavedData {
    private static final String FILE = "kcd_story";
    private static final Factory<StoryFlags> FACTORY = new Factory<>(StoryFlags::new, StoryFlags::load);

    private final Set<String> flags = new LinkedHashSet<>();

    public static StoryFlags get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public void set(String flag) {
        if (flags.add(flag)) setDirty();
    }

    public boolean clear(String flag) {
        boolean removed = flags.remove(flag);
        if (removed) setDirty();
        return removed;
    }

    public Collection<String> all() {
        return Collections.unmodifiableSet(flags);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String f : flags) list.add(StringTag.valueOf(f));
        tag.put("flags", list);
        return tag;
    }

    private static StoryFlags load(CompoundTag tag, HolderLookup.Provider registries) {
        StoryFlags data = new StoryFlags();
        for (Tag t : tag.getList("flags", Tag.TAG_STRING)) data.flags.add(t.getAsString());
        return data;
    }
}
