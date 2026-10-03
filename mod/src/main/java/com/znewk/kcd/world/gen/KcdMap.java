package com.znewk.kcd.world.gen;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import com.znewk.kcd.KcdMod;

/**
 * Карта местности KCD: высота (Y поверхности) и покрытие для каждого блока, 1 блок = 1 метр.
 * Файл {@code /kcdmap/world.bin.gz} рисует {@code tools/map/GenMap.java} из векторного описания
 * ({@code tools/map/*.json}). Формат: int ширина, int глубина, int originX, int originZ,
 * затем ширина×глубина байт высоты (Y = значение 0..255), затем столько же байт покрытия.
 * Вне карты — ровный луг на высоте края.
 */
public final class KcdMap {
    /** Коды покрытия (совпадают с GenMap). */
    public static final int GRASS = 0, PATH = 1, FIELD = 2, WATER = 3, FOREST = 4, ROCK = 5, GRAVEL = 6,
        MEADOW = 7, PLAZA = 8, COBBLE = 9, YARD = 10;

    private static KcdMap instance;

    public final int width, depth, originX, originZ;
    private final byte[] height;
    private final byte[] land;
    private final int defaultY;

    private KcdMap(int width, int depth, int originX, int originZ, byte[] height, byte[] land) {
        this.width = width;
        this.depth = depth;
        this.originX = originX;
        this.originZ = originZ;
        this.height = height;
        this.land = land;
        this.defaultY = width == 0 ? 64 : (height[0] & 0xFF);
    }

    public static synchronized KcdMap get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static KcdMap load() {
        try (InputStream raw = KcdMap.class.getResourceAsStream("/kcdmap/world.bin.gz")) {
            if (raw == null) {
                KcdMod.LOGGER.warn("KCD: нет карты местности /kcdmap/world.bin.gz — ровный луг");
                return new KcdMap(0, 0, 0, 0, new byte[0], new byte[0]);
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            int w = in.readInt(), d = in.readInt(), ox = in.readInt(), oz = in.readInt();
            byte[] h = new byte[w * d];
            byte[] l = new byte[w * d];
            in.readFully(h);
            in.readFully(l);
            KcdMod.LOGGER.info("KCD: карта местности {}×{} м загружена", w, d);
            return new KcdMap(w, d, ox, oz, h, l);
        } catch (IOException e) {
            throw new IllegalStateException("KCD: карта местности повреждена", e);
        }
    }

    private int index(int x, int z) {
        int px = Math.clamp(x - originX, 0, width - 1);
        int pz = Math.clamp(z - originZ, 0, depth - 1);
        return pz * width + px;
    }

    public boolean inside(int x, int z) {
        return x >= originX && z >= originZ && x < originX + width && z < originZ + depth;
    }

    /** Y верхнего блока поверхности (для воды — уровень воды). */
    public int surfaceY(int x, int z) {
        if (width == 0) return 64;
        return height[index(x, z)] & 0xFF;
    }

    public int land(int x, int z) {
        if (width == 0 || !inside(x, z)) return GRASS;
        return land[index(x, z)];
    }

    public int defaultY() {
        return defaultY;
    }
}
