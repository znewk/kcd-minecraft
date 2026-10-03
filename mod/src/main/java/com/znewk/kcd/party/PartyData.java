package com.znewk.kcd.party;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Отряд прохождения: кто каким персонажем играет. Хранится в мире (data/kcd_party.dat). */
public class PartyData extends SavedData {
    private static final String FILE = "kcd_party";
    private static final Factory<PartyData> FACTORY = new Factory<>(PartyData::new, PartyData::load);

    private final Map<UUID, Member> members = new LinkedHashMap<>();

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Optional<Member> member(UUID id) {
        return Optional.ofNullable(members.get(id));
    }

    public Collection<Member> members() {
        return members.values();
    }

    public Optional<Member> henry() {
        return members.values().stream().filter(m -> m.role() == Role.HENRY).findFirst();
    }

    public void put(Member member) {
        members.put(member.id(), member);
        setDirty();
    }

    public boolean remove(UUID id) {
        boolean removed = members.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Member m : members.values()) list.add(m.save());
        tag.put("members", list);
        return tag;
    }

    private static PartyData load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyData data = new PartyData();
        for (Tag t : tag.getList("members", Tag.TAG_COMPOUND)) {
            Member m = Member.load((CompoundTag) t);
            data.members.put(m.id(), m);
        }
        return data;
    }
}
