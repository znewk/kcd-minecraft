package com.znewk.kcd.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.network.AuthPayloads;

/** Ключи этого ПК для миров друзей: {@code <игра>/kcd/keys.json} (ID мира → ключ). Не раздаётся в архиве. */
public final class ClientAuth {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ClientAuth() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("kcd").resolve("keys.json");
    }

    private static JsonObject load() {
        try {
            Path f = file();
            if (Files.exists(f)) return GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            KcdMod.LOGGER.warn("KCD: не прочитать kcd/keys.json: {}", e.toString());
        }
        return new JsonObject();
    }

    public static void challenge(AuthPayloads.Challenge c) {
        JsonObject keys = load();
        String key = keys.has(c.worldId()) ? keys.get(c.worldId()).getAsString() : "";
        PacketDistributor.sendToServer(new AuthPayloads.Response(key));
    }

    public static void issue(AuthPayloads.Issue i) {
        JsonObject keys = load();
        keys.addProperty(i.worldId(), i.key());
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(keys), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KcdMod.LOGGER.error("KCD: не сохранить ключ мира", e);
        }
    }
}
