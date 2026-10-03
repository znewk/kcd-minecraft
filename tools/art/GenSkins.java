import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Скины жителей KCD (запуск: java tools/art/GenSkins.java [папка assets]).
 * Формат — обычный скин игрока 64x64; рисуем по частям тела «как в ванили»: плоские цвета эпохи
 * (лён, шерсть, кожа), лёгкий шум и тень снизу. Внешние слои (капюшон, куртка) — где нужно.
 */
public class GenSkins {
    static String ASSETS;

    public static void main(String[] args) throws Exception {
        ASSETS = args.length > 0 ? args[0] : "mod/src/main/resources/assets";
        save(martin(), "kcd/textures/entity/npc/martin.png");
        save(peasant(), "kcd/textures/entity/npc/peasant.png");
        save(guard(), "kcd/textures/entity/npc/guard.png");
        save(charcoalBurner(), "kcd/textures/entity/npc/charcoal_burner.png");
        System.out.println("Готово: " + ASSETS);
    }

    static void save(BufferedImage img, String rel) throws Exception {
        File f = new File(ASSETS, rel);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("  " + rel);
    }

    // ------------------------------------------------------------------ части тела

    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    interface Painter { int color(Face f, int x, int y, int w, int h); }

    /** Коробка в развёртке скина: (u,v) — левый верхний угол, w×h×d — ширина, высота, глубина. */
    static void box(BufferedImage img, int u, int v, int w, int h, int d, Painter p, long seed) {
        Random r = new Random(seed);
        face(img, u + d, v, w, d, Face.TOP, p, r);
        face(img, u + d + w, v, w, d, Face.BOTTOM, p, r);
        face(img, u, v + d, d, h, Face.RIGHT, p, r);
        face(img, u + d, v + d, w, h, Face.FRONT, p, r);
        face(img, u + d + w, v + d, d, h, Face.LEFT, p, r);
        face(img, u + d + w + d, v + d, w, h, Face.BACK, p, r);
    }

    static void face(BufferedImage img, int x0, int y0, int w, int h, Face f, Painter p, Random r) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = p.color(f, x, y, w, h);
            if ((c >>> 24) == 0) continue;
            // шум ±5% и лёгкая тень к низу боковых граней — «ванильная» фактура
            double k = 1 + (r.nextDouble() - 0.5) * 0.10;
            if (f != Face.TOP && f != Face.BOTTOM && h > 4) k *= 1 - 0.10 * y / (double) (h - 1);
            if (f == Face.BOTTOM) k *= 0.85;
            img.setRGB(x0 + x, y0 + y, shade(c, k));
        }
    }

    static int shade(int c, double k) {
        int a = c >>> 24;
        int rr = clamp(((c >> 16) & 255) * k), gg = clamp(((c >> 8) & 255) * k), bb = clamp((c & 255) * k);
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static final int CLEAR = 0x00000000;

    // развёртка скина 64x64
    static final int[] HEAD = {0, 0}, HAT = {32, 0}, BODY = {16, 16}, JACKET = {16, 32},
        R_ARM = {40, 16}, R_LEG = {0, 16}, L_LEG = {16, 48}, L_ARM = {32, 48};

    /** Внешность: цвета частей одежды. apron/beard/hood — 0, если нет. */
    record Look(int skin, int hair, int beard, int eyes, int shirt, int tunic, int apron, int belt,
                int trousers, int boots, int hood, boolean rolledSleeves) {}

    static BufferedImage draw(Look l, long seed) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, HEAD[0], HEAD[1], 8, 8, 8, (f, x, y, w, h) -> head(l, f, x, y), seed);
        if (l.hood != 0) box(img, HAT[0], HAT[1], 8, 8, 8, (f, x, y, w, h) -> hood(l, f, x, y), seed + 1);
        box(img, BODY[0], BODY[1], 8, 12, 4, (f, x, y, w, h) -> body(l, f, x, y), seed + 2);
        box(img, R_ARM[0], R_ARM[1], 4, 12, 4, (f, x, y, w, h) -> arm(l, f, y), seed + 3);
        box(img, L_ARM[0], L_ARM[1], 4, 12, 4, (f, x, y, w, h) -> arm(l, f, y), seed + 4);
        box(img, R_LEG[0], R_LEG[1], 4, 12, 4, (f, x, y, w, h) -> leg(l, f, x, y), seed + 5);
        box(img, L_LEG[0], L_LEG[1], 4, 12, 4, (f, x, y, w, h) -> leg(l, f, x, y), seed + 6);
        return img;
    }

    static int head(Look l, Face f, int x, int y) {
        int hair = l.hair, skin = l.skin, beard = l.beard != 0 ? l.beard : l.skin;
        switch (f) {
            case TOP: return hair;
            case BOTTOM: return l.beard != 0 ? beard : skin;
            case BACK: return y < 7 ? hair : skin;
            case RIGHT: case LEFT: {
                int fromFront = f == Face.RIGHT ? 7 - x : x; // 0 — у лица
                if (y < 2) return hair;
                if (y < 5) return fromFront >= 4 ? hair : (fromFront == 3 && y == 4 ? darker(skin) : skin);
                if (l.beard != 0 && fromFront <= 4) return beard;
                return fromFront >= 5 ? hair : skin;
            }
            default: { // лицо
                if (y < 2) return hair;
                if (y == 2) return x == 0 || x == 7 ? hair : skin;
                if (y == 3) return x == 1 || x == 2 || x == 5 || x == 6 ? darker(hair) : skin; // брови
                if (y == 4) {
                    if (x == 1 || x == 6) return 0xFFEDE6DA; // белки
                    if (x == 2 || x == 5) return l.eyes;
                    return skin;
                }
                if (y == 5) return x == 3 || x == 4 ? darker(skin) : skin; // нос
                if (l.beard != 0) {
                    if (y == 6) return x == 0 || x == 7 ? beard : x >= 2 && x <= 5 ? (x == 3 || x == 4 ? darker(beard) : beard) : skin;
                    return beard;
                }
                if (y == 6) return x == 3 || x == 4 ? 0xFF8A4A3A : skin; // рот
                return skin;
            }
        }
    }

    /** Капюшон с пелериной (внешний слой головы): лицо открыто. */
    static int hood(Look l, Face f, int x, int y) {
        if (f == Face.FRONT) return (y < 2 || x == 0 || x == 7) ? l.hood : CLEAR;
        if (f == Face.BOTTOM) return CLEAR;
        return l.hood;
    }

    static int body(Look l, Face f, int x, int y) {
        if (f == Face.TOP) return l.tunic;
        if (f == Face.BOTTOM) return l.trousers;
        if (y == 8) return (f == Face.FRONT && (x == 3 || x == 4)) ? 0xFF8A7A5A : l.belt; // ремень с пряжкой
        if (y > 8) {
            if (l.apron != 0 && f == Face.FRONT) return l.apron;
            return l.tunic == l.shirt ? l.trousers : l.tunic; // рубаха до бёдер
        }
        if (f == Face.FRONT) {
            if (l.apron != 0) {
                if (y < 2) return (x == 1 || x == 6) ? darker(l.apron) : (y == 0 && x >= 3 && x <= 4 ? l.skin : l.shirt);
                return l.apron;
            }
            if (y == 0 && (x == 3 || x == 4)) return l.skin; // ворот
            if (y == 1 && (x == 3 || x == 4)) return darker(l.tunic);
            return l.tunic;
        }
        if (l.apron != 0 && f == Face.BACK && (x == 2 || x == 5) && y < 8) return darker(l.apron); // лямки сзади
        return l.apron != 0 ? l.shirt : l.tunic;
    }

    static int arm(Look l, Face f, int y) {
        int sleeve = l.apron != 0 ? l.shirt : l.tunic;
        if (f == Face.TOP) return sleeve;
        if (f == Face.BOTTOM) return l.skin;
        if (l.rolledSleeves) {
            if (y < 4) return sleeve;
            if (y == 4) return darker(sleeve); // закатанный рукав
            return l.skin;
        }
        if (y < 10) return sleeve;
        if (y == 10) return darker(sleeve);
        return l.skin;
    }

    static int leg(Look l, Face f, int x, int y) {
        if (f == Face.BOTTOM) return darker(l.boots);
        if (f == Face.TOP) return l.trousers;
        if (y >= 8) return y == 8 ? darker(l.boots) : l.boots;
        if (l.apron != 0 && f == Face.FRONT && y < 6) return l.apron;
        return l.trousers;
    }

    static int darker(int c) { return shade(c, 0.78); }

    // ------------------------------------------------------------------ жители

    /** Мартин, кузнец Скалицы: тёмные волосы с проседью, борода, льняная рубаха, кожаный фартук. */
    static BufferedImage martin() {
        return draw(new Look(0xFFC69878, 0xFF4A3628, 0xFF5A4636, 0xFF3A2A1A,
            0xFFCDBF9F, 0xFFCDBF9F, 0xFF6B4A2E, 0xFF2E2016,
            0xFF4A3B2E, 0xFF2E2219, 0, true), 1403);
    }

    /** Стражник Скалицы: красная стёганка, кольчужный капюшон, тёмные шоссы, сапоги. */
    static BufferedImage guard() {
        BufferedImage img = draw(new Look(0xFFC09070, 0xFF3A2A1E, 0xFF4A3A2A, 0xFF2A3A4A,
            0xFF9A2A22, 0xFF9A2A22, 0, 0xFF2A1E14,
            0xFF3A3430, 0xFF2A2018, 0xFF8A8C90, false), 1405);
        // кольчуга на капюшоне — чередование светлых и тёмных колец
        for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) {
            int c = img.getRGB(x, y);
            if ((c >>> 24) != 0 && ((x + y) & 1) == 0) img.setRGB(x, y, shade(c, 0.72));
        }
        // стёжка стёганки — тёмные горизонтальные швы через ряд
        for (int y = 20; y < 32; y += 2) for (int x = 16; x < 40; x++) {
            int c = img.getRGB(x, y);
            if ((c >>> 24) != 0 && c != 0) img.setRGB(x, y, shade(c, 0.85));
        }
        return img;
    }

    /** Угольщик: закопчённое лицо и одежда, тёмный капюшон. */
    static BufferedImage charcoalBurner() {
        return draw(new Look(0xFF8E6E58, 0xFF2A2420, 0xFF2E2824, 0xFF4A3A2A,
            0xFF5A5248, 0xFF4A4038, 0xFF3A3028, 0xFF1E1812,
            0xFF3A322A, 0xFF1E1A16, 0xFF2E2A26, true), 1406);
    }

    /** Сельчанин: шерстяная туника цвета глины, суконный капюшон, обмотки. */
    static BufferedImage peasant() {
        return draw(new Look(0xFFC9A07E, 0xFF7A5A36, 0xFF7A5A36, 0xFF3A4A5A,
            0xFFB8A888, 0xFF8A6A44, 0, 0xFF3A2A1A,
            0xFF5E5444, 0xFF3A2C20, 0xFF5A6A3A, false), 1404);
    }
}
