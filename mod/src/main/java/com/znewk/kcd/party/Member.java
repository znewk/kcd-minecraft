package com.znewk.kcd.party;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * Персонаж игрока в прохождении.
 *
 * @param story номер предыстории брата (0 у Индржиха), см. {@link #STORIES}
 */
public record Member(UUID id, String account, Role role, String name, int story) {
    public static final String HENRY_NAME = "Индржих";
    /** Ключи предысторий братьев (перевод в lang). */
    public static final String[] STORIES = {"smith", "reveler", "quiet", "fencer"};

    public static final int MAX_NAME = 16;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("account", account);
        tag.putString("role", role.name());
        tag.putString("name", name);
        tag.putInt("story", story);
        return tag;
    }

    public static Member load(CompoundTag tag) {
        return new Member(tag.getUUID("id"), tag.getString("account"), Role.byName(tag.getString("role")),
            tag.getString("name"), tag.getInt("story"));
    }

    /** Имя персонажа, нормализованное: без лишних пробелов, с заглавной, не длиннее {@link #MAX_NAME}. */
    public static String cleanName(String raw) {
        String s = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        s = s.replaceAll("[^\\p{L}\\p{N} \\-']", "");
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME);
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
