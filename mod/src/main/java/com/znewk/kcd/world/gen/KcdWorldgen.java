package com.znewk.kcd.world.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.znewk.kcd.KcdMod;

/** Регистрация генератора мира KCD (тип мира — {@code data/kcd/worldgen/world_preset/kcd.json}). */
public final class KcdWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, KcdMod.MODID);

    static {
        GENERATORS.register("map", () -> KcdChunkGenerator.CODEC);
    }

    private KcdWorldgen() {}
}
