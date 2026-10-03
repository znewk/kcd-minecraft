package com.znewk.kcd.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Привязка ников к ключам игроков и ID этого мира (data/kcd_auth.dat). Хранятся только хэши ключей. */
public class AuthData extends SavedData {
    private static final String FILE = "kcd_auth";
    private static final Factory<AuthData> FACTORY = new Factory<>(AuthData::new, AuthData::load);

    private String worldId = UUID.randomUUID().toString();
    private final Map<String, String> hashes = new HashMap<>();

    public AuthData() {
        setDirty(); // новый мир — сохранить его ID сразу
    }

    public static AuthData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public String worldId() {
        return worldId;
    }

    @Nullable
    public String hash(String account) {
        return hashes.get(account.toLowerCase());
    }

    public void bind(String account, String hash) {
        hashes.put(account.toLowerCase(), hash);
        setDirty();
    }

    public boolean unbind(String account) {
        boolean removed = hashes.remove(account.toLowerCase()) != null;
        if (removed) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("world", worldId);
        CompoundTag h = new CompoundTag();
        hashes.forEach(h::putString);
        tag.put("accounts", h);
        return tag;
    }

    private static AuthData load(CompoundTag tag, HolderLookup.Provider registries) {
        AuthData d = new AuthData();
        if (tag.contains("world")) d.worldId = tag.getString("world");
        CompoundTag h = tag.getCompound("accounts");
        for (String k : h.getAllKeys()) d.hashes.put(k, h.getString(k));
        return d;
    }
}
