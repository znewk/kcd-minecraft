import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Карта местности KCD из векторного описания (запуск: tools\map\genmap.bat).
 * Вход: tools/map/skalitz.json (рельеф, вода, покрытие, дороги) + mod/.../kcdmap/buildings.json (площадки под дома).
 * Выход: mod/src/main/resources/kcdmap/world.bin.gz (для генератора мира) и tools/map/out/*.png (превью).
 * 1 пиксель = 1 блок = 1 метр. x — восток, z — юг (как в Minecraft).
 */
public class GenMap {
    // коды покрытия — как в KcdMap
    static final int GRASS = 0, PATH = 1, FIELD = 2, WATER = 3, FOREST = 4, ROCK = 5, GRAVEL = 6, MEADOW = 7, PLAZA = 8, COBBLE = 9, YARD = 10;

    static int W, D, OX, OZ;
    static double[] h;
    static byte[] land;
    static boolean[] fixedH; // под водой/площадками — высоту больше не трогаем

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        JsonObject spec = JsonParser.parseString(Files.readString(new File(root, "tools/map/skalitz.json").toPath())).getAsJsonObject();
        JsonArray size = spec.getAsJsonArray("size"), origin = spec.getAsJsonArray("origin");
        W = size.get(0).getAsInt(); D = size.get(1).getAsInt();
        OX = origin.get(0).getAsInt(); OZ = origin.get(1).getAsInt();
        h = new double[W * D];
        land = new byte[W * D];
        fixedH = new boolean[W * D];
        double base = spec.get("baseY").getAsDouble();
        int def = code(str(spec, "default", "meadow"));
        java.util.Arrays.fill(land, (byte) def);

        // 1. рельеф: база + холмы + шум
        JsonObject noise = spec.getAsJsonObject("noise");
        double amp = noise.get("amp").getAsDouble(), scale = noise.get("scale").getAsDouble();
        long seed = noise.get("seed").getAsLong();
        for (int pz = 0; pz < D; pz++) for (int px = 0; px < W; px++) {
            double x = px + OX, z = pz + OZ;
            h[pz * W + px] = base + amp * fbm(x / scale, z / scale, seed);
        }
        for (JsonElement e : arr(spec, "hills")) {
            JsonObject o = e.getAsJsonObject();
            double hx = num(o, "x"), hz = num(o, "z"), r = num(o, "r"), hh = num(o, "h");
            String shape = str(o, "shape", "dome");
            double x2 = o.has("x2") ? num(o, "x2") : hx, z2 = o.has("z2") ? num(o, "z2") : hz;
            for (int pz = 0; pz < D; pz++) for (int px = 0; px < W; px++) {
                double x = px + OX, z = pz + OZ;
                double dist = distToSeg(x, z, hx, hz, x2, z2) / r;
                if (dist >= 1.6) continue;
                double f = switch (shape) {
                    case "plateau" -> smooth(1 - (dist - 0.6) / 0.6);
                    case "cliff" -> smooth(1 - (dist - 0.85) / 0.2);
                    default -> Math.exp(-dist * dist * 2.2);
                };
                h[pz * W + px] += hh * Math.max(0, Math.min(1, f));
            }
        }

        // 2. покрытие
        for (JsonElement e : arr(spec, "areas")) {
            JsonObject o = e.getAsJsonObject();
            List<double[]> poly = pts(o.getAsJsonArray("poly"));
            int c = code(str(o, "land", "grass"));
            Double flat = o.has("flattenTo") ? num(o, "flattenTo") : null;
            forPoly(poly, i -> {
                land[i] = (byte) c;
                if (flat != null) h[i] = h[i] * 0.3 + flat * 0.7;
            });
        }

        // 3. площадки под постройками (раньше дорог — дороги к ним подводятся сверху)
        File bfile = new File(root, "mod/src/main/resources/kcdmap/buildings.json");
        if (bfile.exists()) {
            JsonObject b = JsonParser.parseString(Files.readString(bfile.toPath())).getAsJsonObject();
            for (JsonElement e : arr(b, "buildings")) pad(e.getAsJsonObject());
        }

        // 4. дороги: покрытие + сглаживание продольного профиля
        for (JsonElement e : arr(spec, "roads")) {
            JsonObject o = e.getAsJsonObject();
            List<double[]> p = pts(o.getAsJsonArray("points"));
            double width = num(o, "width");
            int c = code(str(o, "land", "path"));
            double[] blurred = blur(h, 4);
            forLine(p, width / 2 + 1.5, (i, d) -> {
                if (fixedH[i]) return;
                double t = Math.min(1, Math.max(0, (d - width / 2) / 1.5));
                h[i] = blurred[i] * (1 - t) + h[i] * t;
                if (d <= width / 2 && land[i] != WATER) land[i] = (byte) c;
            });
        }

        // 4б. скальные выходы: на крутых склонах (перепад ≥ 2 м на метр) трава не держится — серый камень
        for (int pz = 1; pz < D - 1; pz++) for (int px = 1; px < W - 1; px++) {
            int i = pz * W + px;
            if (fixedH[i] || !(land[i] == GRASS || land[i] == MEADOW || land[i] == FOREST)) continue;
            double slope = Math.max(Math.max(Math.abs(h[i] - h[i - 1]), Math.abs(h[i] - h[i + 1])),
                Math.max(Math.abs(h[i] - h[i - W]), Math.abs(h[i] - h[i + W])));
            if (slope >= 1.6) land[i] = (byte) ROCK;
        }

        // 5. вода: ручьи (уровень не растёт по течению) и пруды
        for (JsonElement e : arr(spec, "brooks")) brook(e.getAsJsonObject());
        for (JsonElement e : arr(spec, "ponds")) pond(e.getAsJsonObject());

        // вывод
        File out = new File(root, "mod/src/main/resources/kcdmap/world.bin.gz");
        out.getParentFile().mkdirs();
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(out)))) {
            o.writeInt(W); o.writeInt(D); o.writeInt(OX); o.writeInt(OZ);
            for (int i = 0; i < W * D; i++) o.writeByte((int) Math.max(1, Math.min(250, Math.round(h[i]))));
            o.write(land);
        }
        preview(new File(root, "tools/map/out"), bfile);
        System.out.println("Готово: " + W + "x" + D + " → " + out + " (" + out.length() / 1024 + " КБ)");
    }

    // ------------------------------------------------------------------ вода

    static void brook(JsonObject o) {
        List<double[]> p = pts(o.getAsJsonArray("points"));
        double width = num(o, "width");
        // уровень воды вдоль русла: по рельефу, но не выше, чем выше по течению
        List<double[]> samples = new ArrayList<>();
        double level = Double.MAX_VALUE;
        for (int s = 0; s + 1 < p.size(); s++) {
            double[] a = p.get(s), b = p.get(s + 1);
            int n = (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]));
            for (int k = 0; k <= n; k++) {
                double x = a[0] + (b[0] - a[0]) * k / Math.max(1, n), z = a[1] + (b[1] - a[1]) * k / Math.max(1, n);
                level = Math.min(level, hAt(x, z) - 1.2);
                samples.add(new double[]{x, z, level});
            }
        }
        int reach = (int) Math.ceil(width / 2 + 4);
        for (double[] s : samples) {
            int cx = (int) Math.round(s[0]) - OX, cz = (int) Math.round(s[1]) - OZ;
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                int px = cx + dx, pz = cz + dz;
                if (px < 0 || pz < 0 || px >= W || pz >= D) continue;
                int i = pz * W + px;
                double d = Math.hypot(dx, dz);
                if (d <= width / 2) {
                    if (land[i] != WATER || h[i] > s[2]) { h[i] = s[2]; land[i] = WATER; fixedH[i] = true; }
                } else if (d <= width / 2 + 4 && land[i] != WATER && !fixedH[i]) {
                    // пологие берега
                    double bank = s[2] + 1 + (d - width / 2) * 0.6;
                    if (h[i] > bank) h[i] = bank;
                    if (d <= width / 2 + 1.2 && land[i] != PATH && land[i] != COBBLE) land[i] = (byte) GRAVEL;
                }
            }
        }
    }

    static void pond(JsonObject o) {
        double cx = num(o, "x"), cz = num(o, "z"), rx = num(o, "rx"), rz = num(o, "rz");
        double level = Double.MAX_VALUE;
        for (double a = 0; a < Math.PI * 2; a += 0.1) level = Math.min(level, hAt(cx + Math.cos(a) * rx, cz + Math.sin(a) * rz) - 0.8);
        for (int pz = 0; pz < D; pz++) for (int px = 0; px < W; px++) {
            double x = px + OX, z = pz + OZ;
            double e = Math.pow((x - cx) / rx, 2) + Math.pow((z - cz) / rz, 2);
            int i = pz * W + px;
            if (e <= 1) { h[i] = level; land[i] = WATER; fixedH[i] = true; }
            else if (e <= 1.6 && h[i] > level + 1) h[i] = Math.min(h[i], level + 1 + (Math.sqrt(e) - 1) * 6);
        }
    }

    // ------------------------------------------------------------------ площадки

    static final java.util.Set<String> PADDED = java.util.Set.of("house", "forge", "tavern", "keep", "tower", "palace", "hall", "gatehouse",
        "watchtower", "mill", "barn", "shed", "stall", "pen", "well");

    static void pad(JsonObject b) {
        String type = b.get("type").getAsString();
        if (b.has("points")) {
            // линии (стены, частокол): без деревьев вдоль
            forLine(pts(b.getAsJsonArray("points")), 2, (i, d) -> { if (land[i] == FOREST) land[i] = (byte) YARD; });
            return;
        }
        if (!PADDED.contains(type)) return;
        int rot = b.has("rot") ? b.get("rot").getAsInt() : 0;
        int w = b.get("w").getAsInt(), d = b.has("d") ? b.get("d").getAsInt() : w;
        int sx = rot == 90 || rot == 270 ? d : w, sz = rot == 90 || rot == 270 ? w : d;
        int cx = b.get("x").getAsInt(), cz = b.get("z").getAsInt();
        int x0 = cx - sx / 2, z0 = cz - sz / 2;
        double y = b.has("y") ? b.get("y").getAsDouble() : Math.round(hAt(cx, cz));
        int m = type.equals("keep") || type.equals("tower") || type.equals("gatehouse") ? 2 : 3;
        for (int z = z0 - m - 3; z < z0 + sz + m + 3; z++) for (int x = x0 - m - 3; x < x0 + sx + m + 3; x++) {
            int px = x - OX, pz = z - OZ;
            if (px < 0 || pz < 0 || px >= W || pz >= D) continue;
            int i = pz * W + px;
            double dx = Math.max(Math.max(x0 - m - x, x - (x0 + sx - 1 + m)), 0);
            double dz = Math.max(Math.max(z0 - m - z, z - (z0 + sz - 1 + m)), 0);
            double dist = Math.hypot(dx, dz);
            if (dist == 0) {
                h[i] = y;
                fixedH[i] = true;
                if (land[i] == FOREST || land[i] == FIELD || land[i] == MEADOW || land[i] == GRASS) land[i] = (byte) YARD;
            } else if (!fixedH[i]) {
                double t = smooth(dist / 3.0);
                h[i] = y * (1 - t) + h[i] * t;
            }
        }
    }

    // ------------------------------------------------------------------ превью

    static void preview(File dir, File bfile) throws Exception {
        dir.mkdirs();
        int[] colors = {0x6A9A3A, 0xB59A6A, 0xC8B44A, 0x3A6ACA, 0x2F5A26, 0x8A8A8A, 0x9A948A, 0x7CAE48, 0xA8865A, 0x777777, 0x8E7A52};
        BufferedImage img = new BufferedImage(W, D, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < D; pz++) for (int px = 0; px < W; px++) {
            int i = pz * W + px;
            int c = colors[land[i]];
            // отмывка рельефа: свет с северо-запада
            double hx = px > 0 ? h[i] - h[i - 1] : 0, hz = pz > 0 ? h[i] - h[i - W] : 0;
            double shade = Math.max(0.55, Math.min(1.35, 1 - (hx + hz) * 0.25));
            double alt = (h[i] - 60) / 80.0;
            int r = clamp(((c >> 16) & 255) * shade * (0.85 + alt * 0.3)), g = clamp(((c >> 8) & 255) * shade * (0.85 + alt * 0.3)), bl = clamp((c & 255) * shade * (0.85 + alt * 0.3));
            if (land[i] != WATER && Math.round(h[i]) % 5 == 0 && px > 0 && Math.round(h[i - 1]) % 5 != 0) { r = g = bl = 60; } // горизонтали через 5 м
            img.setRGB(px, pz, (r << 16) | (g << 8) | bl);
        }
        if (bfile.exists()) {
            JsonObject b = JsonParser.parseString(Files.readString(bfile.toPath())).getAsJsonObject();
            for (JsonElement e : arr(b, "buildings")) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("points")) {
                    for (double[] p : pts(o.getAsJsonArray("points"))) dot(img, (int) p[0], (int) p[1], 0x402010);
                    forLine(pts(o.getAsJsonArray("points")), 0.8, (i, d) -> img.setRGB(i % W, i / W, 0x5A3A1A));
                    continue;
                }
                if (!o.has("w")) { dot(img, o.get("x").getAsInt(), o.get("z").getAsInt(), 0x103010); continue; }
                int rot = o.has("rot") ? o.get("rot").getAsInt() : 0, w = o.get("w").getAsInt(), d = o.has("d") ? o.get("d").getAsInt() : w;
                int sx = rot == 90 || rot == 270 ? d : w, sz = rot == 90 || rot == 270 ? w : d;
                int x0 = o.get("x").getAsInt() - sx / 2 - OX, z0 = o.get("z").getAsInt() - sz / 2 - OZ;
                for (int z = z0; z < z0 + sz; z++) for (int x = x0; x < x0 + sx; x++) {
                    if (x < 0 || z < 0 || x >= W || z >= D) continue;
                    boolean edge = x == x0 || z == z0 || x == x0 + sx - 1 || z == z0 + sz - 1;
                    img.setRGB(x, z, edge ? 0x2A1A10 : 0xB0503A);
                }
            }
        }
        // крестик в начале координат (рынок)
        for (int k = -6; k <= 6; k++) { dot(img, k, 0, 0xFFFFFF); dot(img, 0, k, 0xFFFFFF); }
        ImageIO.write(img, "png", new File(dir, "map.png"));
    }

    static void dot(BufferedImage img, int x, int z, int c) {
        int px = x - OX, pz = z - OZ;
        if (px >= 0 && pz >= 0 && px < W && pz < D) img.setRGB(px, pz, c);
    }

    // ------------------------------------------------------------------ утилиты

    interface Cell { void at(int index, double dist); }

    interface Idx { void at(int index); }

    static void forPoly(List<double[]> poly, Idx f) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : poly) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
        for (int z = (int) Math.floor(minZ); z <= maxZ; z++) for (int x = (int) Math.floor(minX); x <= maxX; x++) {
            int px = x - OX, pz = z - OZ;
            if (px < 0 || pz < 0 || px >= W || pz >= D) continue;
            if (inside(poly, x + 0.5, z + 0.5)) f.at(pz * W + px);
        }
    }

    static boolean inside(List<double[]> poly, double x, double z) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double[] a = poly.get(i), b = poly.get(j);
            if ((a[1] > z) != (b[1] > z) && x < (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
        }
        return in;
    }

    static void forLine(List<double[]> p, double radius, Cell f) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] q : p) { minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]); minZ = Math.min(minZ, q[1]); maxZ = Math.max(maxZ, q[1]); }
        int r = (int) Math.ceil(radius);
        for (int z = (int) minZ - r; z <= maxZ + r; z++) for (int x = (int) minX - r; x <= maxX + r; x++) {
            int px = x - OX, pz = z - OZ;
            if (px < 0 || pz < 0 || px >= W || pz >= D) continue;
            double best = Double.MAX_VALUE;
            for (int s = 0; s + 1 < p.size(); s++) best = Math.min(best, distToSeg(x, z, p.get(s)[0], p.get(s)[1], p.get(s + 1)[0], p.get(s + 1)[1]));
            if (best <= radius) f.at(pz * W + px, best);
        }
    }

    static double distToSeg(double x, double z, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
        return Math.hypot(x - (ax + t * dx), z - (az + t * dz));
    }

    static double hAt(double x, double z) {
        int px = (int) Math.max(0, Math.min(W - 1, Math.round(x) - OX)), pz = (int) Math.max(0, Math.min(D - 1, Math.round(z) - OZ));
        return h[pz * W + px];
    }

    static double[] blur(double[] src, int r) {
        double[] tmp = new double[src.length], out = new double[src.length];
        for (int z = 0; z < D; z++) for (int x = 0; x < W; x++) {
            double s = 0; int n = 0;
            for (int k = -r; k <= r; k++) { int xx = x + k; if (xx >= 0 && xx < W) { s += src[z * W + xx]; n++; } }
            tmp[z * W + x] = s / n;
        }
        for (int z = 0; z < D; z++) for (int x = 0; x < W; x++) {
            double s = 0; int n = 0;
            for (int k = -r; k <= r; k++) { int zz = z + k; if (zz >= 0 && zz < D) { s += tmp[zz * W + x]; n++; } }
            out[z * W + x] = s / n;
        }
        return out;
    }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

    static double fbm(double x, double z, long seed) {
        double s = 0, a = 1, f = 1, norm = 0;
        for (int o = 0; o < 4; o++) { s += a * valueNoise(x * f, z * f, seed + o * 101); norm += a; a *= 0.5; f *= 2; }
        return s / norm;
    }

    static double valueNoise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = rnd(x0, z0, seed), b = rnd(x0 + 1, z0, seed), c = rnd(x0, z0 + 1, seed), d = rnd(x0 + 1, z0 + 1, seed);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    static double rnd(int x, int z, long seed) {
        long n = x * 374761393L + z * 668265263L + seed * 2147483647L;
        n = (n ^ (n >>> 13)) * 1274126177L;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 32767.5 - 1;
    }

    static int code(String s) {
        return switch (s) {
            case "path" -> PATH; case "field" -> FIELD; case "water" -> WATER; case "forest" -> FOREST; case "rock" -> ROCK;
            case "gravel" -> GRAVEL; case "meadow" -> MEADOW; case "plaza" -> PLAZA; case "cobble" -> COBBLE; case "yard" -> YARD;
            default -> GRASS;
        };
    }

    static List<double[]> pts(JsonArray a) {
        List<double[]> out = new ArrayList<>();
        for (JsonElement e : a) out.add(new double[]{e.getAsJsonArray().get(0).getAsDouble(), e.getAsJsonArray().get(1).getAsDouble()});
        return out;
    }

    static JsonArray arr(JsonObject o, String k) { return o.has(k) ? o.getAsJsonArray(k) : new JsonArray(); }

    static double num(JsonObject o, String k) { return o.get(k).getAsDouble(); }

    static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
}
