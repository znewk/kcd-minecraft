package com.znewk.kcd.dialogue;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import com.znewk.kcd.KcdMod;

/** Все разговоры из датапаков ({@code data/<ns>/kcd_dialogue/}). Перечитываются по /reload. */
public final class DialogueRegistry extends SimpleJsonResourceReloadListener {
    private static Map<ResourceLocation, Dialogue> dialogues = Map.of();

    public DialogueRegistry() {
        super(new Gson(), "kcd_dialogue");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, Dialogue> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                out.put(id, Dialogue.parse(id, GsonHelper.convertToJsonObject(json, "dialogue")));
            } catch (RuntimeException e) {
                KcdMod.LOGGER.error("KCD: ошибка в разговоре {}: {}", id, e.getMessage());
            }
        });
        dialogues = Map.copyOf(out);
        KcdMod.LOGGER.info("KCD: разговоров загружено: {}", dialogues.size());
    }

    @Nullable
    public static Dialogue get(ResourceLocation id) {
        return dialogues.get(id);
    }
}
