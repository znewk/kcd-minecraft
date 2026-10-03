package com.znewk.kcd.quest;

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

/** Все задания из датапаков ({@code data/<ns>/kcd_quest/}). Перечитываются по /reload. */
public final class QuestRegistry extends SimpleJsonResourceReloadListener {
    private static Map<String, QuestDefinition> quests = Map.of();

    public QuestRegistry() {
        super(new Gson(), "kcd_quest");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, QuestDefinition> out = new HashMap<>();
        files.forEach((rl, json) -> {
            try {
                out.put(rl.getPath(), QuestDefinition.parse(rl.getPath(), GsonHelper.convertToJsonObject(json, "quest")));
            } catch (RuntimeException e) {
                KcdMod.LOGGER.error("KCD: ошибка в задании {}: {}", rl, e.getMessage());
            }
        });
        quests = Map.copyOf(out);
        KcdMod.LOGGER.info("KCD: заданий загружено: {}", quests.size());
    }

    @Nullable
    public static QuestDefinition get(String id) {
        return quests.get(id);
    }

    public static Set<String> ids() {
        return quests.keySet();
    }
}
