package com.znewk.kcd.world.gen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

import com.znewk.kcd.KcdMod;

/**
 * Постройки мира KCD из плана {@code /kcdmap/buildings.json} (тот же файл читает GenMap, чтобы выровнять под ними
 * землю). Все постройки один раз собираются в память блок-за-блоком ({@link BuildingKit}) и раскладываются по
 * чанкам; генератор при создании чанка ставит только его блоки — порядок загрузки чанков не важен.
 * <pre>
 * { "buildings": [ {"type": "house", "x": 10, "z": -4, "w": 7, "d": 9, "rot": 90, "style": "timber", "floors": 2}, ... ] }
 * </pre>
 * rot — поворот фасада (0: вход с севера, 90: с востока, 180: с юга, 270: с запада).
 */
public final class Buildings {
    public record Spec(String type, int x, int z, int w, int d, int rot, JsonObject params) {
        public String str(String key, String def) {
            return GsonHelper.getAsString(params, key, def);
        }

        public int num(String key, int def) {
            return GsonHelper.getAsInt(params, key, def);
        }

        public List<int[]> points(String key) {
            List<int[]> out = new ArrayList<>();
            if (params.has(key)) for (JsonElement e : GsonHelper.getAsJsonArray(params, key)) {
                JsonArray p = e.getAsJsonArray();
                out.add(new int[]{p.get(0).getAsInt(), p.get(1).getAsInt()});
            }
            return out;
        }
    }

    /** Блоки одного чанка: упакованные позиции и состояния. */
    private record ChunkBlocks(long[] pos, BlockState[] states) {}

    private static Map<Long, ChunkBlocks> byChunk;
    /** Большие деревья (фичи Minecraft): ставятся в чанке, где их корень. */
    private static Map<Long, List<BlockPos>> trees;

    private Buildings() {}

    /** Прочитать план (и запланировать жителей), если ещё не читали. */
    public static void ensureLoaded() {
        ensureBuilt();
    }

    private static synchronized void ensureBuilt() {
        if (byChunk != null) return;
        List<Spec> specs = load();
        Map<Long, List<Long>> posLists = new HashMap<>();
        Map<Long, List<BlockState>> stateLists = new HashMap<>();
        Map<Long, List<BlockPos>> treeMap = new HashMap<>();
        BuildingKit.Sink sink = new BuildingKit.Sink() {
            @Override
            public void set(int x, int y, int z, BlockState state) {
                long key = ChunkPos.asLong(x >> 4, z >> 4);
                posLists.computeIfAbsent(key, k -> new ArrayList<>()).add(BlockPos.asLong(x, y, z));
                stateLists.computeIfAbsent(key, k -> new ArrayList<>()).add(state);
            }

            @Override
            public void tree(int x, int y, int z) {
                treeMap.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), k -> new ArrayList<>()).add(new BlockPos(x, y, z));
            }
        };
        int count = 0;
        for (Spec s : specs) {
            try {
                BuildingKit.build(s, KcdMap.get(), sink);
                count++;
            } catch (RuntimeException e) {
                KcdMod.LOGGER.error("KCD: постройка {} в ({}, {}): {}", s.type(), s.x(), s.z(), e.toString());
            }
        }
        Map<Long, ChunkBlocks> out = new HashMap<>();
        posLists.forEach((k, list) -> {
            long[] p = new long[list.size()];
            for (int i = 0; i < p.length; i++) p[i] = list.get(i);
            out.put(k, new ChunkBlocks(p, stateLists.get(k).toArray(new BlockState[0])));
        });
        byChunk = out;
        trees = treeMap;
        KcdMod.LOGGER.info("KCD: построек в плане: {}, чанков с постройками: {}", count, out.size());
    }

    private static List<Spec> load() {
        List<Spec> out = new ArrayList<>();
        try (InputStream in = Buildings.class.getResourceAsStream("/kcdmap/buildings.json")) {
            if (in == null) return out;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : GsonHelper.getAsJsonArray(root, "buildings")) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Spec(GsonHelper.getAsString(o, "type"), GsonHelper.getAsInt(o, "x", 0), GsonHelper.getAsInt(o, "z", 0),
                    GsonHelper.getAsInt(o, "w", 1), GsonHelper.getAsInt(o, "d", GsonHelper.getAsInt(o, "w", 1)), GsonHelper.getAsInt(o, "rot", 0), o));
            }
        } catch (Exception e) {
            KcdMod.LOGGER.error("KCD: план построек не прочитан", e);
        }
        return out;
    }

    /** Поставить блоки построек, попадающие в этот чанк. */
    public static void placeInChunk(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator) {
        ensureBuilt();
        long key = chunk.getPos().toLong();
        ChunkBlocks blocks = byChunk.get(key);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (blocks != null) {
            for (int i = 0; i < blocks.pos().length; i++) {
                pos.set(blocks.pos()[i]);
                level.setBlock(pos, blocks.states()[i], 2);
            }
        }
        List<BlockPos> roots = trees.get(key);
        if (roots != null) {
            var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.FANCY_OAK);
            RandomSource rnd = RandomSource.create(key);
            for (BlockPos p : roots) {
                feature.ifPresent(f -> f.value().place(level, generator, rnd, p));
            }
        }
    }
}
