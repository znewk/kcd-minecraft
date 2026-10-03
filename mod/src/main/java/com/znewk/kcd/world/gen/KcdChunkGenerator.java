package com.znewk.kcd.world.gen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Генератор мира «KCD»: рельеф и покрытие берутся из карты {@link KcdMap} (по картам KCD), постройки — из
 * {@link Buildings}. Никакого случайного шума Minecraft: мир один и тот же у всех, как в KCD.
 * Пещер, руд и мобов при генерации нет — мир приключенческий.
 */
public class KcdChunkGenerator extends ChunkGenerator {
    public static final MapCodec<KcdChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, KcdChunkGenerator::new));

    private static final int WATER_DEPTH = 2;

    public KcdChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ рельеф и поверхность

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structures, ChunkAccess chunk) {
        KcdMap map = KcdMap.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight();
        ChunkPos cp = chunk.getPos();
        RandomSource rnd = RandomSource.create(cp.toLong() * 31L + 7L);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                int top = map.surfaceY(x, z);
                int land = map.land(x, z);
                boolean water = land == KcdMap.WATER;
                int ground = water ? top - WATER_DEPTH : top;
                for (int y = minY; y <= top; y++) {
                    BlockState s = column(land, y, ground, top, minY, rnd);
                    if (s == null) continue;
                    chunk.setBlockState(pos.set(lx, y, lz), s, false);
                    ocean.update(lx, y, lz, s);
                    surface.update(lx, y, lz, s);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Блок столба на высоте y: ground — верх земли, top — верх (для воды — уровень воды). */
    private static BlockState column(int land, int y, int ground, int top, int minY, RandomSource rnd) {
        if (y == minY) return Blocks.BEDROCK.defaultBlockState();
        if (y > ground) return Blocks.WATER.defaultBlockState();
        int depth = ground - y;
        if (depth > 4) return depth > 40 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        return switch (land) {
            case KcdMap.WATER -> depth == 0 ? (rnd.nextInt(3) == 0 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case KcdMap.ROCK -> depth == 0
                ? (rnd.nextInt(4) == 0 ? Blocks.ANDESITE : rnd.nextInt(5) == 0 ? Blocks.COBBLESTONE : Blocks.STONE).defaultBlockState()
                : Blocks.STONE.defaultBlockState();
            case KcdMap.PATH -> depth == 0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case KcdMap.FIELD -> depth == 0 ? Blocks.FARMLAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case KcdMap.GRAVEL -> depth == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case KcdMap.PLAZA -> depth == 0
                ? (rnd.nextInt(3) == 0 ? Blocks.COARSE_DIRT : rnd.nextInt(4) == 0 ? Blocks.GRAVEL : Blocks.PACKED_MUD).defaultBlockState()
                : Blocks.DIRT.defaultBlockState();
            case KcdMap.COBBLE -> depth == 0
                ? (rnd.nextInt(3) == 0 ? Blocks.ANDESITE : Blocks.COBBLESTONE).defaultBlockState()
                : Blocks.STONE.defaultBlockState();
            case KcdMap.YARD -> depth == 0 ? (rnd.nextInt(2) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case KcdMap.FOREST -> depth == 0 ? (rnd.nextInt(6) == 0 ? Blocks.PODZOL : Blocks.GRASS_BLOCK).defaultBlockState() : Blocks.DIRT.defaultBlockState();
            default -> depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        };
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        // посевы на полях
        KcdMap map = KcdMap.get();
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource rnd = RandomSource.create(cp.toLong() ^ 0x5DEECE66DL);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                if (map.land(x, z) != KcdMap.FIELD) continue;
                int y = map.surfaceY(x, z) + 1;
                // рядами: каждая четвёртая борозда — пустая
                if (Math.floorMod(x, 4) == 0) continue;
                BlockState crop = (Math.floorMod(z / 24, 3) == 0 ? Blocks.CARROTS : Blocks.WHEAT).defaultBlockState()
                    .setValue(CropBlock.AGE, 5 + rnd.nextInt(3));
                chunk.setBlockState(pos.set(lx, y, lz), crop, false);
            }
        }
    }

    // ------------------------------------------------------------------ деревья, трава, постройки

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        KcdMap map = KcdMap.get();
        ChunkPos cp = chunk.getPos();
        RandomSource rnd = RandomSource.create(cp.toLong() * 0x9E3779B97F4A7C15L);
        var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                int land = map.land(x, z);
                int y = map.surfaceY(x, z) + 1;
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) continue;
                switch (land) {
                    case KcdMap.FOREST -> {
                        if (rnd.nextInt(28) == 0) {
                            ResourceKey<ConfiguredFeature<?, ?>> tree = switch (rnd.nextInt(10)) {
                                case 0, 1, 2 -> TreeFeatures.BIRCH;
                                case 3, 4 -> TreeFeatures.SPRUCE;
                                default -> TreeFeatures.OAK;
                            };
                            place(level, features.getHolder(tree), rnd, pos.immutable());
                        } else if (rnd.nextInt(7) == 0) {
                            level.setBlock(pos, (rnd.nextInt(4) == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
                        }
                    }
                    case KcdMap.MEADOW -> {
                        if (rnd.nextInt(400) == 0) place(level, features.getHolder(TreeFeatures.OAK), rnd, pos.immutable());
                        else if (rnd.nextInt(4) == 0) level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                        else if (rnd.nextInt(40) == 0) level.setBlock(pos, (rnd.nextBoolean() ? Blocks.DANDELION : Blocks.OXEYE_DAISY).defaultBlockState(), 2);
                    }
                    case KcdMap.GRASS -> {
                        if (rnd.nextInt(10) == 0) level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    }
                    default -> { }
                }
            }
        }
        Buildings.placeInChunk(level, chunk, this);
    }

    private void place(WorldGenLevel level, java.util.Optional<? extends Holder<ConfiguredFeature<?, ?>>> feature, RandomSource rnd, BlockPos pos) {
        feature.ifPresent(f -> f.value().place(level, this, rnd, pos));
    }

    // ------------------------------------------------------------------ служебное

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return KcdMap.get().surfaceY(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        KcdMap map = KcdMap.get();
        int top = map.surfaceY(x, z);
        int land = map.land(x, z);
        int ground = land == KcdMap.WATER ? top - WATER_DEPTH : top;
        RandomSource rnd = RandomSource.create(0);
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = height.getMinBuildHeight() + i;
            BlockState s = y <= top ? column(land, y, ground, top, height.getMinBuildHeight(), rnd) : null;
            states[i] = s == null ? Blocks.AIR.defaultBlockState() : s;
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        KcdMap map = KcdMap.get();
        info.add("KCD: покрытие " + map.land(pos.getX(), pos.getZ()) + ", поверхность Y " + map.surfaceY(pos.getX(), pos.getZ()));
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 62;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return KcdMap.get().surfaceY(0, 0) + 1;
    }
}
