package com.znewk.kcd.cutscene;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import com.znewk.kcd.KcdMod;

/** Все кат-сцены из датапаков ({@code data/<ns>/kcd_cutscene/}). Перечитываются по /reload. */
public final class CutsceneRegistry extends SimpleJsonResourceReloadListener {
    private static Map<String, Cutscene> scenes = Map.of();

    public CutsceneRegistry() {
        super(new Gson(), "kcd_cutscene");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, Cutscene> out = new HashMap<>();
        files.forEach((rl, json) -> {
            try {
                out.put(rl.getPath(), Cutscene.parse(rl.getPath(), GsonHelper.convertToJsonObject(json, "cutscene")));
            } catch (RuntimeException e) {
                KcdMod.LOGGER.error("KCD: ошибка в кат-сцене {}: {}", rl, e.getMessage());
            }
        });
        scenes = Map.copyOf(out);
        KcdMod.LOGGER.info("KCD: кат-сцен загружено: {}", scenes.size());
    }

    @Nullable
    public static Cutscene get(String id) {
        return scenes.get(id);
    }

    public static Set<String> ids() {
        return scenes.keySet();
    }
}
