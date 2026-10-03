package com.znewk.kcd.npc;

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

/** Все описания жителей из датапаков ({@code data/<ns>/kcd_npc/}). Перечитываются по /reload. */
public final class NpcRegistry extends SimpleJsonResourceReloadListener {
    private static Map<ResourceLocation, NpcDefinition> defs = Map.of();

    public NpcRegistry() {
        super(new Gson(), "kcd_npc");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, NpcDefinition> out = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                out.put(id, NpcDefinition.parse(id, GsonHelper.convertToJsonObject(json, "npc")));
            } catch (RuntimeException e) {
                KcdMod.LOGGER.error("KCD: ошибка в описании жителя {}: {}", id, e.getMessage());
            }
        });
        defs = Map.copyOf(out);
        KcdMod.LOGGER.info("KCD: жителей загружено: {}", defs.size());
    }

    @Nullable
    public static NpcDefinition get(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : defs.get(rl);
    }

    public static Set<ResourceLocation> ids() {
        return defs.keySet();
    }
}
