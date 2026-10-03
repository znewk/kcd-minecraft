import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Генератор пиксельной графики KCD (запуск: java tools/art/GenArt.java <папка assets>).
 * Рисует: панораму главного меню (6 граней), логотип, спрайты меню.
 * Стиль: Minecraft-пиксели + палитра KCD2 (пергамент, золото, тёмно-красный).
 */
public class GenArt {
    static String ASSETS;

    public static void main(String[] args) throws Exception {
        ASSETS = args.length > 0 ? args[0] : "mod/src/main/resources/assets";
        panorama();
        logo();
        menuSprites();
        System.out.println("Готово: " + ASSETS);
    }

    // ------------------------------------------------------------------ утилиты

    static void save(BufferedImage img, String rel) throws Exception {
        File f = new File(ASSETS, rel);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("  " + rel + " " + img.getWidth() + "x" + img.getHeight());
    }

    static void saveText(String text, String rel) throws Exception {
        File f = new File(ASSETS, rel);
        f.getParentFile().mkdirs();
        java.nio.file.Files.writeString(f.toPath(), text);
    }

    static int rgb(double r, double g, double b) {
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    static int argb(int a, int rgb) { return (a << 24) | (rgb & 0xFFFFFF); }

    static int clamp255(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static double[] hex(int c) { return new double[]{(c >> 16) & 255, (c >> 8) & 255, c & 255}; }

    static double[] mix(double[] a, double[] b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    /** Постеризация с упорядоченным дизерингом — «пиксельный» вид градиентов. */
    static int dither(double[] c, int x, int y, double levels) {
        double step = 255.0 / levels;
        double d = (BAYER[y & 3][x & 3] / 16.0 - 0.5) * step;
        double[] o = new double[3];
        for (int i = 0; i < 3; i++) o[i] = Math.round((c[i] + d) / step) * step;
        return rgb(o[0], o[1], o[2]);
    }

    // value noise
    static double hash(int x, int seed) {
        int h = x * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    static double hash2(int x, int y, int seed) { return hash(x * 73856093 ^ y * 19349663, seed); }

    static double noise1(double x, int seed) {
        int i = (int) Math.floor(x);
        double f = smooth(x - i);
        return hash(i, seed) * (1 - f) + hash(i + 1, seed) * f;
    }

    static double noise2(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = smooth(x - ix), fy = smooth(y - iy);
        double a = hash2(ix, iy, seed), b = hash2(ix + 1, iy, seed);
        double c = hash2(ix, iy + 1, seed), d = hash2(ix + 1, iy + 1, seed);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    static double fbm1(double x, int seed, int oct) {
        double s = 0, a = 0.5, n = 0;
        for (int i = 0; i < oct; i++) { s += a * noise1(x, seed + i * 31); n += a; x *= 2.03; a *= 0.5; }
        return s / n;
    }

    static double fbm2(double x, double y, int seed, int oct) {
        double s = 0, a = 0.5, n = 0;
        for (int i = 0; i < oct; i++) { s += a * noise2(x, y, seed + i * 31); n += a; x *= 2.03; y *= 2.03; a *= 0.5; }
        return s / n;
    }

    /** Азимут, периодический: шум по кругу без шва. */
    static double ring(double az, double freq, int seed, int oct) {
        double r = freq / (2 * Math.PI);
        double a = Math.toRadians(az);
        return fbm2(Math.cos(a) * r + 100, Math.sin(a) * r + 100, seed, oct);
    }

    static double angDist(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    // ------------------------------------------------------------------ панорама

    static final double SUN_AZ = 205, SUN_EL = 5;
    static final double CASTLE_AZ = 18;     // Трошки-подобный замок на двух скалах
    static final double VILLAGE_AZ = 248;   // деревня в долине, против солнца
    static final double EYE = 12;           // высота взгляда над полями (в блоках)
    static final double[] TREE_AZ = {112, 158, 300, 338}; // большие деревья на переднем плане

    static final double[] SKY_ZENITH = hex(0x355A9E);
    static final double[] SKY_HIGH = hex(0x5F86C4);
    static final double[] SKY_MID = hex(0xC29CB0);
    static final double[] SKY_LOW = hex(0xF4A96E);
    static final double[] SKY_HORIZON = hex(0xFFD8A0);
    static final double[] SUN = hex(0xFFF6D2);
    static final double[] HAZE = hex(0xD8B39A);

    static void panorama() throws Exception {
        int n = 384;
        for (int k = 0; k < 6; k++) {
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double u = (x + 0.5) / n, v = (y + 0.5) / n;
                    double[] p = facePoint(k, u, v);
                    // CubeMap поворачивает всё на 180° вокруг X: (x, y, z) -> (x, -y, -z)
                    double dx = p[0], dy = -p[1], dz = -p[2];
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double az = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
                    double el = Math.toDegrees(Math.asin(dy / len));
                    img.setRGB(x, y, scene(az, el, x, y));
                }
            }
            save(img, "minecraft/textures/gui/title/background/panorama_" + k + ".png");
        }
        // затемнение сверху/снизу (ванильный overlay)
        BufferedImage ov = new BufferedImage(16, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) {
            double t = y / 127.0;
            double a = Math.max(0, 1 - t / 0.28) * 150 + Math.max(0, (t - 0.62) / 0.38) * 190;
            for (int x = 0; x < 16; x++) ov.setRGB(x, y, argb(clamp255(a), 0x120A06));
        }
        save(ov, "minecraft/textures/gui/title/background/panorama_overlay.png");
    }

    /** Точка на грани куба так же, как в CubeMap.render (до поворота). */
    static double[] facePoint(int k, double u, double v) {
        double a = -1 + 2 * u, b = -1 + 2 * v;
        return switch (k) {
            case 0 -> new double[]{a, b, 1};
            case 1 -> new double[]{1, b, 1 - 2 * u};
            case 2 -> new double[]{1 - 2 * u, b, -1};
            case 3 -> new double[]{-1, b, -1 + 2 * u};
            case 4 -> new double[]{a, -1, -1 + 2 * v};
            default -> new double[]{a, 1, 1 - 2 * v};
        };
    }

    static int scene(double az, double el, int px, int py) {
        double[] c = sky(az, el);
        double levels = 24;
        double lit = sunSide(az);

        // 1. дальние горы (дымка)
        double hFar = 2.5 + 7.0 * Math.pow(ring(az, 9, 11, 4), 1.5) + 1.2 * ring(az, 40, 12, 3);
        if (el < hFar) {
            c = mix(hex(0x9C8AB0), hex(0x7C6E9C), (hFar - el) / 6.0);
            c = mix(c, HAZE, 0.30 + 0.40 * sunGlow(az, 0));
            if (hFar - el < 0.35) c = mix(c, hex(0xFFD0A0), 0.6 * lit);
        }

        // 2. средние холмы с лесом + скалы замка
        double hMid = midHill(az);
        double hMidT = hMid + treeLine(az, 1.0, 31, 0.9);
        if (el < hMidT) c = forestColor(az, el, hMidT, 0.32, 37);

        // 3. замок
        double[] castle = castle(az, el);
        if (castle != null) c = castle;

        // 4. ближние холмы: луга и перелески
        double hNear = nearHill(az);
        double hNearT = hNear + treeLine(az, 1.6, 41, 1.3) * forestPatch(az, 43);
        if (el < hNearT && hNearT > -1.2) {
            double depth = hNearT - el;
            if (el > hNear - 0.05 || forestPatch(az, 43) > 0.5 && depth < 6) {
                c = forestColor(az, el, hNearT, 0.12, 47);
            } else {
                // луг на склоне: полосы освещения + кусты
                double[] m = mix(hex(0x8DAA4A), hex(0x5E8236), Math.min(1, depth / 7));
                m = mix(m, hex(0xFFD58A), 0.30 * lit * Math.max(0, 1 - depth / 4));
                if (noise2(az / 1.3, el / 0.8, 49) > 0.72) m = hex(0x3E5E2E);
                c = mix(m, HAZE, 0.12);
            }
            if (depth < 0.25) c = mix(c, hex(0xFFE0A0), 0.55 * lit);
        }

        // 5. поля в перспективе (ниже ближних холмов)
        double fieldTop = Math.min(-3.5, hNear - 2.5);
        if (el < fieldTop) c = ground(az, el);

        // 6. деревня в долине и дым
        double[] village = village(az, el);
        if (village != null) c = village;
        double smoke = smoke(az, el);
        if (smoke > 0) c = mix(c, hex(0xE6DCD0), smoke);

        // 7. большие деревья переднего плана
        double[] tree = null; // большие деревья переднего плана — отключены
        if (tree != null) c = tree;

        return dither(c, px, py, levels);
    }

    static double midHill(double az) { return 0.8 + 3.0 * ring(az, 14, 21, 4) + castleHill(az); }

    static double nearHill(double az) {
        double h = -3.2 + 5.5 * Math.pow(ring(az, 5, 23, 3), 1.3);
        // у замка ближние холмы ниже — чтобы скалы были видны целиком
        double d = angDist(az, CASTLE_AZ);
        if (d < 30) h -= 3.0 * Math.pow(Math.cos(Math.toRadians(d / 30 * 90)), 2);
        return h;
    }

    static double forestPatch(double az, int seed) { return smooth((ring(az, 11, seed, 3) - 0.45) / 0.12); }

    static double[] forestColor(double az, double el, double top, double haze, int seed) {
        double depth = top - el;
        double lit = sunSide(az);
        // кроны: пятна света и тени
        double crown = noise2(az / 0.7, el / 0.55, seed);
        double[] f = mix(hex(0x47703A), hex(0x2C4A2C), Math.min(1, depth / 4.5));
        if (crown > 0.62) f = mix(f, hex(0x9AB04E), 0.55 * lit + 0.15);
        else if (crown < 0.3) f = mix(f, hex(0x1E3222), 0.5);
        return mix(f, HAZE, haze);
    }

    /** Большие деревья у края кадра — «рамка», как в превью-артах. */
    static double[] foregroundTree(double az, double el) {
        for (int i = 0; i < TREE_AZ.length; i++) {
            double rel = ((az - TREE_AZ[i] + 540) % 360) - 180;
            double size = 1.0 + (i % 2) * 0.25;
            if (Math.abs(rel) > 30 * size) continue;
            // ствол
            if (Math.abs(rel) < 1.6 * size && el < 6 * size && el > -30) {
                double[] bark = rel < 0 ? hex(0x3A2A1E) : hex(0x5E4430);
                if (noise2(rel * 2, el * 0.6, 121 + i) > 0.7) bark = hex(0x2A1E16);
                return bark;
            }
            // крона: несколько «шаров» с неровным краем
            double best = -1;
            double[] cx = {-8, 6, 0, -14, 13, -3};
            double[] cy = {16, 18, 24, 11, 12, 30};
            double[] r = {10, 10.5, 10, 7, 7, 7};
            for (int b = 0; b < cx.length; b++) {
                double dx = rel - cx[b] * size, dy = el - cy[b] * size;
                double rr = r[b] * size * (0.86 + 0.22 * noise2(Math.atan2(dy, dx) * 3 + b, i, 131));
                double k = 1 - Math.hypot(dx, dy) / rr;
                if (k > best) best = k;
            }
            if (best > 0) {
                double leaf = noise2(rel / 1.1, el / 1.0, 141 + i);
                double[] g = mix(hex(0x2E4A26), hex(0x1C2E1A), 0.4 + 0.3 * (1 - best));
                double sunHere = 0.5 + 0.5 * Math.cos(Math.toRadians(angDist(TREE_AZ[i], SUN_AZ)));
                if (leaf > 0.58) g = mix(g, hex(0x7E9E3E), 0.35 + 0.35 * sunHere);
                if (best < 0.06) g = mix(g, hex(0xFFC888), 0.45 * sunHere);
                return g;
            }
        }
        return null;
    }

    static double sunGlow(double az, double el) {
        double d = Math.hypot(angDist(az, SUN_AZ), (el - SUN_EL) * 1.4);
        return Math.exp(-d / 28.0);
    }

    /** 1 — сторона, освещённая солнцем, 0 — противоположная. */
    static double sunSide(double az) {
        return 0.5 + 0.5 * Math.cos(Math.toRadians(angDist(az, SUN_AZ)));
    }

    static double[] sky(double az, double el) {
        double[] c;
        if (el < 0) c = SKY_HORIZON;
        else if (el < 6) c = mix(SKY_HORIZON, SKY_LOW, el / 6);
        else if (el < 18) c = mix(SKY_LOW, SKY_MID, (el - 6) / 12);
        else if (el < 45) c = mix(SKY_MID, SKY_HIGH, (el - 18) / 27);
        else c = mix(SKY_HIGH, SKY_ZENITH, (el - 45) / 45);
        // противоположная от солнца сторона — холоднее
        double cold = 1 - sunSide(az);
        c = mix(c, mix(hex(0x7C86B8), hex(0x3A4778), Math.min(1, Math.max(0, el) / 30)), 0.35 * cold * smooth(el / 4 + 0.5));
        // свечение солнца
        double g = sunGlow(az, el);
        c = mix(c, hex(0xFFD9A0), 0.85 * g);
        // облака: вытянутые полосы
        if (el > 4 && el < 26) {
            double band = Math.exp(-Math.pow((el - 13) / 6.5, 2));
            double cl = ring(az, 26, 51, 4) * 0.7 + 0.3 * noise2(az / 6.0, el / 2.5, 52);
            double m = smooth((cl - 0.56) / 0.08) * band;
            if (m > 0) {
                double lit = sunGlow(az, el) * 1.5 + 0.25 * sunSide(az);
                double[] cloud = mix(hex(0x9A7E9E), hex(0xFFC79A), Math.min(1, lit));
                double under = noise2(az / 4.0, el / 1.5, 53);
                if (under < 0.45) cloud = mix(cloud, hex(0xF6A884), 0.4 * sunSide(az));
                c = mix(c, cloud, m);
            }
        }
        // солнце
        double ds = Math.hypot(angDist(az, SUN_AZ), el - SUN_EL);
        if (ds < 2.6) c = SUN;
        return c;
    }

    static double treeLine(double az, double scale, int seed, double cell) {
        // кроны деревьев — «бугорки» по краю холма
        double q = az / cell;
        int i = (int) Math.floor(q);
        double f = q - i;
        double h = hash(i, seed);
        double crown = Math.sqrt(Math.max(0, 1 - Math.pow((f - 0.5) * 2.1, 2)));
        double forestMask = smooth((ring(az, 7, seed + 5, 3) - 0.38) / 0.1);
        return scale * crown * (0.6 + 0.6 * h) * forestMask;
    }

    /** Скалистый холм под замком. */
    static double castleHill(double az) {
        double d = angDist(az, CASTLE_AZ);
        if (d > 22) return 0;
        double base = 5.0 * Math.pow(Math.cos(Math.toRadians(d / 22 * 90)), 2);
        // две скалы-иглы (как Трошки: Панна и Баба)
        double p1 = 7.5 * Math.exp(-Math.pow(angDist(az, CASTLE_AZ - 4.5) / 2.6, 2));
        double p2 = 9.0 * Math.exp(-Math.pow(angDist(az, CASTLE_AZ + 4.2) / 2.4, 2));
        return base + Math.max(p1, p2);
    }

    static double[] castle(double az, double el) {
        double rel = ((az - CASTLE_AZ + 540) % 360) - 180;
        if (Math.abs(rel) > 18) return null;
        double hillTop = midHill(az);
        double lit = sunSide(az) * 0.6 + 0.4;
        double[] wall = mix(hex(0xB9A88E), hex(0xF1E3C4), lit);
        double[] wallShade = mix(hex(0x8C7A66), hex(0xC7B49A), lit);
        double[] roof = hex(0x5A3A2E);
        double[] roofLit = hex(0x8A4E36);
        double[] rock = mix(hex(0x5E5560), hex(0x9C8A7E), lit);

        // скалы — серый камень с прожилками
        double rockTop = hillTop;
        if (el < rockTop && el > rockTop - 11 && castleHill(az) > 5) {
            double vein = noise2(az * 2.2, el * 1.3, 61);
            double[] r = mix(rock, hex(0x3E3842), vein < 0.35 ? 0.45 : 0.0);
            if (rockTop - el < 0.4) r = mix(r, hex(0xE8C49A), 0.4);
            return mix(r, HAZE, 0.15);
        }
        // башня на левой игле (круглая, «Баба»)
        double[] t1 = tower(rel, el, -4.5, peak(-4.5), 2.0, 4.6, wall, wallShade, roof, roofLit, true);
        if (t1 != null) return t1;
        // башня на правой игле (четырёхгранная, «Панна»)
        double[] t2 = tower(rel, el, 4.2, peak(4.2), 2.3, 5.4, wall, wallShade, roof, roofLit, false);
        if (t2 != null) return t2;
        // стены и палас между иглами
        double wallBase = Math.min(peak(-4.5), peak(4.2)) - 3.0;
        if (Math.abs(rel) < 3.9 && el > hillTopRel(rel) - 0.2 && el < wallBase + 3.0) {
            double top = wallBase + 3.0;
            // зубцы
            if (el > top - 0.5 && ((int) Math.floor((rel + 10) / 0.5)) % 2 == 0) return null;
            double[] w = (rel < 0) ? wallShade : wall;
            // окна
            if (Math.abs(el - (wallBase + 1.4)) < 0.35 && ((int) Math.floor((rel + 10) / 0.8)) % 3 == 0) w = hex(0x2A2026);
            return mix(w, HAZE, 0.12);
        }
        // палас с крышей
        if (rel > -2.4 && rel < 1.8) {
            double base = wallBase + 3.0;
            double ridge = base + 2.4 - Math.abs(rel + 0.3) * 0.9;
            if (el >= base && el < ridge) return mix(sunSide(az) > 0.5 ? roofLit : roof, HAZE, 0.1);
        }
        return null;
    }

    static double peak(double rel) { return midHill(CASTLE_AZ + rel); }

    static double hillTopRel(double rel) { return peak(rel); }

    static double[] tower(double rel, double el, double at, double base, double halfW, double height,
                          double[] wall, double[] wallShade, double[] roof, double[] roofLit, boolean round) {
        double dx = rel - at;
        if (Math.abs(dx) > halfW + 0.6) return null;
        double top = base + height;
        if (Math.abs(dx) <= halfW && el >= base - 0.5 && el < top) {
            double[] w = dx < -halfW * 0.25 ? wallShade : wall;
            if (round) w = mix(wallShade, wall, smooth((dx + halfW) / (halfW * 1.6)));
            // фахверк в верхней части
            if (el > top - 1.4) {
                w = hex(0xE9DAB8);
                if (Math.abs(el - (top - 1.4)) < 0.15 || ((int) Math.floor((dx + 5) / 0.5)) % 2 == 0 && el > top - 1.35 && el < top - 1.2) w = hex(0x4A3424);
            }
            // окна-бойницы
            if (Math.abs(dx) < 0.18 && ((int) Math.floor((el - base) / 1.1)) % 2 == 1 && el < top - 1.6) w = hex(0x261C22);
            return mix(w, HAZE, 0.12);
        }
        // шатровая крыша
        double roofH = round ? 3.2 : 2.6;
        if (el >= top && el < top + roofH) {
            double wAt = (halfW + 0.35) * (1 - (el - top) / roofH);
            if (Math.abs(dx) <= wAt) return mix(dx > 0 ? roofLit : roof, HAZE, 0.1);
        }
        return null;
    }

    static double[] ground(double az, double el) {
        double dist = EYE / Math.tan(Math.toRadians(-el));
        double a = Math.toRadians(az);
        double gx = Math.sin(a) * dist, gz = Math.cos(a) * dist;
        // лоскутные поля
        double fx = Math.floor(gx / 38 + 0.4 * fbm2(gx / 90, gz / 90, 71, 2));
        double fz = Math.floor(gz / 26 + 0.4 * fbm2(gx / 80, gz / 80, 72, 2));
        double kind = hash2((int) fx, (int) fz, 73);
        double[] c;
        if (kind < 0.30) c = hex(0xC9A54E);          // пшеница
        else if (kind < 0.55) c = hex(0x6E8C3E);     // луг
        else if (kind < 0.72) c = hex(0x8A6A44);     // пашня
        else if (kind < 0.88) c = hex(0x9AA44E);     // молодая трава
        else c = hex(0x4E6B36);                      // перелесок
        // борозды
        double rows = Math.sin((kind < 0.72 && kind >= 0.55 ? gx : gz) * 1.3);
        if (dist < 160 && rows > 0.6) c = mix(c, hex(0x000000), 0.10);
        // межи
        double ex = Math.abs(gx / 38 + 0.4 * fbm2(gx / 90, gz / 90, 71, 2) - fx - 0.5);
        double ez = Math.abs(gz / 26 + 0.4 * fbm2(gx / 80, gz / 80, 72, 2) - fz - 0.5);
        if (Math.max(ex, ez) > 0.46) c = hex(0x4F6634);
        // река
        double river = Math.abs(gx * 0.6 + gz * 0.8 + 70 * fbm2(gx / 140, gz / 140, 81, 3) - 40);
        if (dist > 45 && river < 3 + dist * 0.003) c = mix(hex(0x5F8FC8), hex(0xFFE0B0), 0.45 * sunSide(az) + 0.1 * noise2(gx / 4, gz / 4, 82));
        // дорога
        double road = Math.abs(gx * 0.95 - gz * 0.3 + 50 * fbm2(gx / 120, gz / 120, 91, 3) + 20);
        if (road < 2.2 + dist * 0.002) c = hex(0xB59A70);
        // освещение и воздушная перспектива
        c = mix(c, hex(0xFFD9A0), 0.18 * sunSide(az));
        c = mix(c, HAZE, Math.min(0.75, dist / 900));
        // ближний план темнее
        c = mix(c, hex(0x22301E), Math.max(0, (-el - 30) / 70) * 0.6);
        // цветы на ближнем лугу
        if (dist > 12 && dist < 45 && hash2((int) (gx * 8), (int) (gz * 8), 95) > 0.993) c = hash2((int) gx, (int) gz, 96) > 0.5 ? hex(0xE8D86A) : hex(0xD45A4A);
        return c;
    }

    static double[] village(double az, double el) {
        double rel = ((az - VILLAGE_AZ + 540) % 360) - 180;
        if (Math.abs(rel) > 16 || el > 0 || el < -9) return null;
        // дома стоят рядами на склоне: «ячейки» по азимуту и высоте
        for (int row = 0; row < 4; row++) {
            double baseEl = -2.2 - row * 1.7;
            double size = 0.9 + row * 0.35;
            double q = (rel + 20) / (size * 1.6) + row * 0.37;
            int i = (int) Math.floor(q);
            double f = q - i;
            double hh = hash(i * 7 + row, 101);
            if (hh < 0.35) continue;
            if (Math.abs(rel) > 13 - row * 1.5) continue;
            double w = 0.62;
            if (Math.abs(f - 0.5) > w / 2 + 0.12) continue;
            double dx = (f - 0.5) * size * 1.6;
            double wallTop = baseEl + size * 0.75;
            double ridge = wallTop + size * 0.75 - Math.abs(dx) * 1.1;
            // церковь с колокольней
            boolean church = row == 1 && i == (int) Math.floor((20) / (size * 1.6) + row * 0.37);
            if (church) {
                if (Math.abs(dx) < size * 0.18 && el >= baseEl && el < wallTop + size * 2.6) {
                    double spireStart = wallTop + size * 1.4;
                    if (el > spireStart) {
                        double ww = size * 0.18 * (1 - (el - spireStart) / (size * 1.2));
                        if (Math.abs(dx) > ww) continue;
                        return hex(0x4A3A34);
                    }
                    return dx < 0 ? hex(0xC9B9A0) : hex(0xF0E4CC);
                }
            }
            if (el >= baseEl && el < wallTop && Math.abs(dx) < w * size * 0.8) {
                double[] wall = dx < 0 ? hex(0xBFAE94) : hex(0xEDE0C6);
                if (Math.abs(el - (baseEl + size * 0.35)) < 0.12 && Math.abs(dx) < size * 0.15) wall = hex(0x3A2A20);
                return mix(wall, HAZE, 0.2);
            }
            if (el >= wallTop && el < ridge && Math.abs(dx) < w * size * 0.8 + 0.15) {
                double[] r = hh > 0.7 ? hex(0x9C4A30) : hex(0x6A4A3A);
                if (dx > 0) r = mix(r, hex(0xF0A070), 0.25);
                return mix(r, HAZE, 0.2);
            }
        }
        return null;
    }

    static double smoke(double az, double el) {
        double best = 0;
        for (int s = 0; s < 3; s++) {
            double at = VILLAGE_AZ - 6 + s * 5.5;
            double baseEl = -2.0 - (s % 2) * 1.5;
            if (el < baseEl) continue;
            double up = el - baseEl;
            if (up > 16) continue;
            double drift = up * 0.35 + Math.sin(up * 0.7 + s) * 0.5;
            double wdt = 0.35 + up * 0.09;
            double d = Math.abs(az - at - drift);
            double m = Math.max(0, 1 - d / wdt) * Math.max(0, 1 - up / 16) * 0.55;
            if (noise2(az * 1.5, el * 0.9 + s * 10, 111) < 0.35) m *= 0.4;
            best = Math.max(best, m);
        }
        return best;
    }

    // ------------------------------------------------------------------ логотип

    static final java.util.Map<Character, String[]> GLYPHS = new java.util.HashMap<>();

    static {
        GLYPHS.put('K', new String[]{"##..##", "##.##.", "####..", "###...", "####..", "##.##.", "##..##"});
        GLYPHS.put('I', new String[]{"####", ".##.", ".##.", ".##.", ".##.", ".##.", "####"});
        GLYPHS.put('N', new String[]{"##...##", "###..##", "####.##", "##.####", "##..###", "##...##", "##...##"});
        GLYPHS.put('G', new String[]{".####.", "##..##", "##....", "##.###", "##..##", "##..##", ".####."});
        GLYPHS.put('D', new String[]{"#####.", "##..##", "##..##", "##..##", "##..##", "##..##", "#####."});
        GLYPHS.put('O', new String[]{".####.", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."});
        GLYPHS.put('M', new String[]{"##...##", "###.###", "#######", "##.#.##", "##...##", "##...##", "##...##"});
        GLYPHS.put('C', new String[]{".####.", "##..##", "##....", "##....", "##....", "##..##", ".####."});
        GLYPHS.put('E', new String[]{"#####", "##...", "##...", "####.", "##...", "##...", "#####"});
        GLYPHS.put('L', new String[]{"##...", "##...", "##...", "##...", "##...", "##...", "#####"});
        GLYPHS.put('V', new String[]{"##..##", "##..##", "##..##", "##..##", ".####.", ".####.", "..##.."});
        GLYPHS.put('R', new String[]{"#####.", "##..##", "##..##", "#####.", "####..", "##.##.", "##..##"});
        GLYPHS.put('A', new String[]{".####.", "##..##", "##..##", "######", "##..##", "##..##", "##..##"});
        GLYPHS.put(' ', new String[]{"..", "..", "..", "..", "..", "..", ".."});
    }

    /** Маска текста в клетках (true = блок). */
    static boolean[][] textMask(String text) {
        int w = 0;
        for (char ch : text.toCharArray()) w += GLYPHS.get(ch)[0].length() + 1;
        w -= 1;
        boolean[][] m = new boolean[7][w];
        int x = 0;
        for (char ch : text.toCharArray()) {
            String[] g = GLYPHS.get(ch);
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < g[r].length(); c++)
                    if (g[r].charAt(c) == '#') m[r][x + c] = true;
            x += g[0].length() + 1;
        }
        return m;
    }

    /**
     * Рисует объёмный блочный текст (как в превью-артах Minecraft): лицевая грань с градиентом и фаской,
     * «выдавленный» объём вниз, толстая тёмная обводка.
     */
    static void blockText(BufferedImage img, String text, int ox, int oy, int cell, int depth, int outline,
                          int faceTop, int faceBottom, int hi, int shade, int extTop, int extBottom, int outlineColor) {
        boolean[][] m = textMask(text);
        int H = 7 * cell, W = m[0].length * cell;
        boolean[][] face = new boolean[H][W];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) face[y][x] = m[y / cell][x / cell];
        int fw = W + 2 * outline, fh = H + depth + 2 * outline;
        boolean[][] solid = new boolean[fh][fw];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (face[y][x])
            for (int d = 0; d <= depth; d++) solid[y + d + outline][x + outline] = true;
        // обводка: расширение формы
        boolean[][] out = new boolean[fh][fw];
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) {
            if (!solid[y][x]) continue;
            for (int dy = -outline; dy <= outline; dy++) for (int dx = -outline; dx <= outline; dx++) {
                if (Math.abs(dx) + Math.abs(dy) > outline + 1) continue;
                int yy = y + dy, xx = x + dx;
                if (yy >= 0 && yy < fh && xx >= 0 && xx < fw) out[yy][xx] = true;
            }
        }
        Random rnd = new Random(7);
        // тень под логотипом
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) if (out[y][x]) {
            int sx = ox + x + 3, sy = oy + y + 4;
            if (inside(img, sx, sy) && (img.getRGB(sx, sy) >>> 24) < 90) img.setRGB(sx, sy, argb(90, 0x000000));
        }
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) if (out[y][x]) put(img, ox + x, oy + y, outlineColor);
        // объём
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (face[y][x])
            for (int d = 1; d <= depth; d++) {
                double t = d / (double) depth;
                double[] c = mix(hex(extTop), hex(extBottom), t);
                put(img, ox + x + outline, oy + y + d + outline, rgb(c[0], c[1], c[2]));
            }
        // лицевая грань
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (!face[y][x]) continue;
            double t = y / (double) (H - 1);
            double[] c = mix(hex(faceTop), hex(faceBottom), t);
            // лёгкая фактура «блоков»
            int cx = x / cell, cy = y / cell;
            double nz = (hash2(cx, cy, 5) - 0.5) * 14 + (rnd.nextDouble() - 0.5) * 6;
            c = new double[]{c[0] + nz, c[1] + nz, c[2] + nz * 0.8};
            boolean topEdge = y < 2 || !face[y - 2][x] || !face[y - 1][x];
            boolean leftEdge = x < 1 || !face[y][x - 1];
            boolean botEdge = y >= H - 2 || !face[y + 1][x] || (y + 2 < H && !face[y + 2][x]);
            boolean rightEdge = x >= W - 1 || !face[y][x + 1];
            int col = rgb(c[0], c[1], c[2]);
            if (topEdge || leftEdge) col = hi;
            else if (botEdge || rightEdge) col = shade;
            put(img, ox + x + outline, oy + y + outline, col);
        }
    }

    static boolean inside(BufferedImage img, int x, int y) { return x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight(); }

    /** Непрозрачный пиксель (цвета в коде пишутся как 0xRRGGBB). */
    static void put(BufferedImage img, int x, int y, int c) {
        if ((c >>> 24) == 0) c |= 0xFF000000;
        if (inside(img, x, y)) img.setRGB(x, y, c);
    }

    static int textWidth(String text, int cell, int outline) {
        int w = 0;
        for (char ch : text.toCharArray()) w += GLYPHS.get(ch)[0].length() + 1;
        return (w - 1) * cell + 2 * outline;
    }

    static void logo() throws Exception {
        String title = "KINGDOM COME", sub = "DELIVERANCE";
        int cell = 7, depth = 7, outline = 3;
        int tw = textWidth(title, cell, outline);
        int th = 7 * cell + depth + 2 * outline;
        int scell = 4, sdepth = 3, soutline = 2;
        int sw = textWidth(sub, scell, soutline);
        int ribbonH = 7 * scell + sdepth + 2 * soutline + 12;
        int ribbonW = sw + 56;
        int W = Math.max(tw, ribbonW + 40) + 12;
        int H = th + ribbonH + 4 + 8;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

        // лента (под заголовком, немного заходит на него)
        int ry = th - 6, rx = (W - ribbonW) / 2;
        ribbon(img, rx, ry, ribbonW, ribbonH);
        // заголовок
        blockText(img, title, (W - tw) / 2, 2, cell, depth, outline,
            0xFFF6DE, 0xE6CB8E, 0xFFFFFF, 0xB8945A, 0x8A5A26, 0x4A2C12, 0x1B120C);
        // подзаголовок на ленте
        blockText(img, sub, (W - sw) / 2, ry + 5, scell, sdepth, soutline,
            0xFFFFFF, 0xF2E2BE, 0xFFFFFF, 0xC9B07A, 0x6A1012, 0x3A0A0C, 0x1B0A08);
        save(img, "kcd/textures/gui/title/logo.png");
    }

    static void ribbon(BufferedImage img, int x, int y, int w, int h) {
        int red = 0xB3262A, redHi = 0xD9443E, redSh = 0x7E1518, back = 0x6A1013, dark = 0x2A0608, gold = 0xD8B25A;
        int tail = 22, drop = 7;
        // хвосты (сзади, ниже)
        for (int side = 0; side < 2; side++) {
            for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < tail + 8; xx++) {
                int px = side == 0 ? x - tail + xx : x + w - 8 + xx;
                int py = y + drop + yy;
                // вырез «ласточкин хвост»
                int notchDepth = 10;
                int fromOuter = side == 0 ? xx : tail + 8 - 1 - xx;
                double mid = Math.abs(yy - h / 2.0) / (h / 2.0);
                if (fromOuter < notchDepth * (1 - mid)) continue;
                boolean edge = yy < 2 || yy >= h - 2 || fromOuter < notchDepth * (1 - mid) + 2;
                put(img, px, py, edge ? dark : back);
            }
            // складка (тень между хвостом и лентой)
            for (int yy = 0; yy < drop + 2; yy++) for (int xx = 0; xx < 8; xx++) {
                int px = side == 0 ? x + xx : x + w - 8 + xx;
                if (xx < yy) put(img, px, y + h + yy - 2, dark);
            }
        }
        // основная лента
        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) {
            int c = red;
            if (yy < 2 || yy >= h - 2 || xx < 2 || xx >= w - 2) c = dark;
            else if (yy < 4) c = gold;
            else if (yy >= h - 4) c = 0xA8822E;
            else if (yy < 7) c = redHi;
            else if (yy >= h - 7) c = redSh;
            put(img, x + xx, y + yy, c);
        }
    }

    // ------------------------------------------------------------------ спрайты меню

    static void menuSprites() throws Exception {
        // золотой свиток для выбранного пункта меню (nine-slice), 1 тексель = 1 пиксель GUI
        int w = 64, h = 16;
        BufferedImage s = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int outline = 0xFF3B2A10, fillTop = 0xFFF5E2A0, fill = 0xFFE7CB75, fillBot = 0xFFCDA44C, shade = 0xFFA27C2C, roll = 0xFFB88A34, rollHi = 0xFFF7E3A0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int cap = 7;
            boolean leftCap = x < cap, rightCap = x >= w - cap;
            int c = 0;
            if (!leftCap && !rightCap) {
                if (y == 1 || y == h - 2) c = outline;
                else if (y > 1 && y < h - 2) c = y < 4 ? fillTop : (y > h - 5 ? fillBot : fill);
                if (y == h - 3 && c != outline) c = shade;
            } else {
                int cx = leftCap ? x : w - 1 - x; // 0 у края
                // свёрнутый край свитка: вертикальный рулон + завиток
                if (cx >= 3) {
                    if (y >= 0 && y < h) {
                        if (y == 0 || y == h - 1 || cx == 3) c = outline;
                        else c = (cx == 4) ? rollHi : roll;
                    }
                } else {
                    // острые «шипы» орнамента
                    if ((y == 3 || y == h - 4) && cx >= 1) c = outline;
                    if ((y == 4 || y == h - 5) && cx >= 2) c = roll;
                    if (y >= 6 && y <= h - 7 && cx >= 2) c = (cx == 2) ? outline : roll;
                }
            }
            s.setRGB(x, y, c);
        }
        save(s, "kcd/textures/gui/sprites/menu/highlight.png");
        saveText("{\n  \"gui\": {\n    \"scaling\": {\n      \"type\": \"nine_slice\",\n      \"width\": 64,\n      \"height\": 16,\n      \"border\": { \"left\": 8, \"right\": 8, \"top\": 4, \"bottom\": 4 }\n    }\n  }\n}\n",
            "kcd/textures/gui/sprites/menu/highlight.png.mcmeta");

        // горизонтальная тень за колонкой меню (справа темнее)
        BufferedImage sh = new BufferedImage(64, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            int a = clamp255(Math.pow(x / 63.0, 1.6) * 190);
            for (int y = 0; y < 4; y++) sh.setRGB(x, y, argb(a, 0x100806));
        }
        save(sh, "kcd/textures/gui/title/menu_shade.png");
    }
}
