package com.znewk.kcd.world.gen;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Строители построек Скалицы по скриншотам KCD1 (см. KCD-ref/skalitz/INDEX.md, «Стиль Скалицы»):
 * побелённые срубы на каменном цоколе, маленькие тёмные окошки, ОЧЕНЬ крутые крыши (солома или серый гонт)
 * выше стен, фронтоны из тёмных досок с ромбовидным продухом; замок — круглый бергфрид с конусом, куртина,
 * оштукатуренный палас; вокруг холма — бревенчатая стена; деревня — заострённый частокол с вышками.
 * Каждая постройка строится в локальной системе (u — поперёк фасада, v — вглубь, вход на v=0) и
 * поворачивается целиком вместе с блоками.
 */
public final class BuildingKit {
    public interface Sink {
        void set(int x, int y, int z, BlockState state);

        /** Большое дерево (фича Minecraft) с корнем здесь. */
        void tree(int x, int y, int z);
    }

    private BuildingKit() {}

    public static void build(Buildings.Spec s, KcdMap map, Sink sink) {
        switch (s.type()) {
            case "house" -> house(new Frame(s, map, sink), s.str("style", "whitewash"), s.str("roof", "shingle"), s.num("floors", 1), false);
            case "shed" -> house(new Frame(s, map, sink), "log", s.str("roof", "thatch"), 1, true);
            case "forge" -> forge(new Frame(s, map, sink));
            case "tavern" -> tavern(new Frame(s, map, sink));
            case "palace" -> palace(new Frame(s, map, sink));
            case "tower" -> tower(new Frame(s, map, sink), s.num("h", 22));
            case "keep" -> tower(new Frame(s, map, sink), s.num("h", 22));
            case "hall" -> palace(new Frame(s, map, sink));
            case "gatehouse" -> gatehouse(new Frame(s, map, sink), s.str("material", "stone"));
            case "watchtower" -> watchtower(new Frame(s, map, sink));
            case "stall" -> stall(new Frame(s, map, sink), s.num("color", 0));
            case "pillory" -> pillory(new Frame(s, map, sink));
            case "well" -> well(new Frame(s, map, sink));
            case "mill" -> mill(new Frame(s, map, sink));
            case "barn" -> barn(new Frame(s, map, sink), s.str("roof", "thatch"));
            case "mine" -> mine(new Frame(s, map, sink));
            case "cart" -> cart(new Frame(s, map, sink));
            case "hay" -> hay(new Frame(s, map, sink));
            case "tables" -> tables(new Frame(s, map, sink));
            case "pen" -> pen(new Frame(s, map, sink), s.str("inside", "none"));
            case "logs" -> logPile(new Frame(s, map, sink));
            case "tree" -> sink.tree(s.x(), map.surfaceY(s.x(), s.z()) + 1, s.z());
            case "wall" -> wall(s.points("points"), s.num("h", 8), map, sink);
            case "logwall" -> logwall(s.points("points"), s.num("h", 4), map, sink);
            case "palisade" -> palisade(s.points("points"), s.num("h", 4), map, sink);
            case "fence" -> fence(s.points("points"), map, sink);
            case "npc" -> WorldNpcs.plan(s, map);
            default -> throw new IllegalArgumentException("неизвестный тип постройки " + s.type());
        }
    }

    // ------------------------------------------------------------------ локальная система

    /** Прямоугольник постройки с поворотом. y0 — уровень пола (высота земли в центре). */
    static class Frame {
        final int w, d, y0;
        final int x0, z0, x1, z1;
        final int rot;
        final Rotation rotation;
        final Sink sink;
        /** Смещение локальной системы (для вложенных рамок, напр. крыши вышки). */
        final int uOff, vOff, yOff;

        Frame(Buildings.Spec s, KcdMap map, Sink sink) {
            this.w = s.w();
            this.d = s.d();
            this.rot = Math.floorMod(s.rot(), 360);
            this.rotation = switch (rot) {
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            int sx = rot == 90 || rot == 270 ? d : w;
            int sz = rot == 90 || rot == 270 ? w : d;
            this.x0 = s.x() - sx / 2;
            this.z0 = s.z() - sz / 2;
            this.x1 = x0 + sx - 1;
            this.z1 = z0 + sz - 1;
            this.y0 = s.params().has("y") ? s.num("y", 64) : map.surfaceY(s.x(), s.z());
            this.sink = sink;
            this.uOff = 0;
            this.vOff = 0;
            this.yOff = 0;
        }

        /** Вложенная квадратная рамка size×size внутри родителя: начало в (off, off), пол на высоте dy. */
        Frame(Frame parent, int off, int dy, int size) {
            this.w = size;
            this.d = size;
            this.rot = parent.rot;
            this.rotation = parent.rotation;
            this.x0 = parent.x0;
            this.z0 = parent.z0;
            this.x1 = parent.x1;
            this.z1 = parent.z1;
            this.y0 = parent.y0;
            this.sink = parent.sink;
            this.uOff = parent.uOff + off;
            this.vOff = parent.vOff + off;
            this.yOff = parent.yOff + dy;
        }

        int wx(int u, int v) {
            u += uOff;
            v += vOff;
            return switch (rot) {
                case 90 -> x1 - v;
                case 180 -> x1 - u;
                case 270 -> x0 + v;
                default -> x0 + u;
            };
        }

        int wz(int u, int v) {
            u += uOff;
            v += vOff;
            return switch (rot) {
                case 90 -> z0 + u;
                case 180 -> z1 - v;
                case 270 -> z1 - u;
                default -> z0 + v;
            };
        }

        /** y — относительно пола (0 — пол). Состояние задаётся как для фасада на севере. */
        void set(int u, int y, int v, BlockState state) {
            sink.set(wx(u, v), y0 + yOff + y, wz(u, v), state.rotate(rotation));
        }

        void set(int u, int y, int v, Block block) {
            set(u, y, v, block.defaultBlockState());
        }

        void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
            for (int u = u0; u <= u1; u++) for (int y = y0; y <= y1; y++) for (int v = v0; v <= v1; v++) set(u, y, v, state);
        }

        void fill(int u0, int y0, int v0, int u1, int y1, int v1, Block block) {
            fill(u0, y0, v0, u1, y1, v1, block.defaultBlockState());
        }

        /** Расчистить объём и подсыпать каменный фундамент на склоне. */
        void clearAndFound(int extra, int height) {
            fill(-extra, 1, -extra, w - 1 + extra, height, d - 1 + extra, Blocks.AIR);
            for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) for (int y = -4; y <= -1; y++) set(u, y, v, rubble(u, y, v));
        }
    }

    // ------------------------------------------------------------------ материалы

    private static BlockState stairs(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** Бревно вдоль оси; у блоков без оси (камень) — просто блок. */
    private static BlockState log(Block b, Direction.Axis axis) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, axis) : s;
    }

    private static BlockState slab(Block b, SlabType type) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static int hash(int x, int y, int z) {
        return Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 4243, 97);
    }

    /** Бут — серый плитняк цоколя: булыжник, андезит, туф. */
    static BlockState rubble(int x, int y, int z) {
        int n = hash(x, y, z) % 7;
        return (n < 3 ? Blocks.COBBLESTONE : n < 5 ? Blocks.ANDESITE : n == 5 ? Blocks.TUFF : Blocks.STONE).defaultBlockState();
    }

    /** Кладка замка: бутовый камень с вкраплениями. */
    static BlockState masonry(int x, int y, int z) {
        int n = hash(x, y, z) % 11;
        return (n == 0 ? Blocks.MOSSY_COBBLESTONE : n < 4 ? Blocks.COBBLESTONE : n < 6 ? Blocks.ANDESITE : n < 8 ? Blocks.STONE_BRICKS : Blocks.STONE).defaultBlockState();
    }

    /** Побелка: известь с выступающими местами брёвнами. */
    static BlockState whitewash(int x, int y, int z) {
        int n = hash(x, y, z) % 13;
        return (n == 0 ? Blocks.STRIPPED_SPRUCE_WOOD : n < 3 ? Blocks.CALCITE : Blocks.WHITE_TERRACOTTA).defaultBlockState();
    }

    private static void door(Frame f, int u, int v, Block door) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        f.set(u, 1, v, lower);
        f.set(u, 2, v, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void bed(Frame f, int u, int v) {
        BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        f.set(u, 1, v, foot);
        f.set(u, 1, v + 1, foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    // ------------------------------------------------------------------ дома

    /**
     * Дом: каменный цоколь, стены по стилю (whitewash — побелённый сруб, pink — розовая штукатурка, log — сруб,
     * timber — фахверк, stone — бут), маленькие окошки, крутая крыша (thatch/shingle), обстановка.
     */
    static void house(Frame f, String style, String roof, int floors, boolean open) {
        int w = f.w, d = f.d, top = 3 + (floors - 1) * 4;
        f.clearAndFound(2, top + w + 4);
        f.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SPRUCE_PLANKS);
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                boolean edgeU = u == 0 || u == w - 1, edgeV = v == 0 || v == d - 1;
                if (!edgeU && !edgeV) continue;
                boolean corner = edgeU && edgeV;
                for (int y = 1; y <= top; y++) f.set(u, y, v, wall(style, corner, edgeV, u, y, v, f));
            }
        }
        // окошки: маленькие тёмные проёмы
        for (int fl = 0; fl < floors; fl++) {
            int y = fl * 4 + 2;
            for (int u = 2; u < w - 2; u += 3) {
                if (fl > 0 || Math.abs(u - w / 2) > 1) f.set(u, y, 0, Blocks.AIR);
                f.set(u, y, d - 1, Blocks.AIR);
            }
            for (int v = 2; v < d - 2; v += 3) {
                f.set(0, y, v, Blocks.AIR);
                f.set(w - 1, y, v, Blocks.AIR);
            }
        }
        for (int fl = 1; fl < floors; fl++) f.fill(1, fl * 4, 1, w - 2, fl * 4, d - 2, Blocks.SPRUCE_PLANKS);
        f.fill(1, top + 1, 1, w - 2, top + 1, d - 2, Blocks.SPRUCE_PLANKS); // потолок под крышей
        if (floors > 1) {
            for (int y = 1; y <= 4 * (floors - 1); y++) f.set(w - 2, y, d - 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            f.set(w - 2, 4, d - 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }
        if (open) f.fill(1, 1, 0, w - 2, top - 1, 0, Blocks.AIR);
        else door(f, w / 2, 0, Blocks.SPRUCE_DOOR);
        steepRoof(f, top + 1, roof, 1);
        if (!open && w >= 5 && d >= 5) {
            bed(f, 1, d - 3);
            f.set(w - 2, 1, 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            f.set(1, 1, 1, Blocks.BARREL);
            f.set(w / 2, 1, d / 2, Blocks.SPRUCE_FENCE);
            f.set(w / 2, 2, d / 2, Blocks.SPRUCE_PRESSURE_PLATE);
            f.set(w - 2, 1, d / 2, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false)); // очаг: дым уходит в крышу
            f.set(w / 2, top, d / 2, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }
    }

    private static BlockState wall(String style, boolean corner, boolean alongU, int u, int y, int v, Frame f) {
        int x = f.wx(u, v), z = f.wz(u, v), wy = f.y0 + y;
        Direction.Axis along = alongU ? Direction.Axis.X : Direction.Axis.Z;
        boolean base = y == 1 && !style.equals("log");
        return switch (style) {
            case "log" -> corner ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y) : log(Blocks.STRIPPED_SPRUCE_LOG, along);
            case "pink" -> base ? rubble(x, wy, z) : corner ? log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)
                : (hash(x, wy, z) % 9 == 0 ? Blocks.WHITE_TERRACOTTA : Blocks.PINK_TERRACOTTA).defaultBlockState();
            case "timber" -> {
                if (base) yield rubble(x, wy, z);
                boolean post = corner || (alongU ? u : v) % 3 == 0;
                if (post) yield log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
                if (y % 4 == 0 || y == 3) yield log(Blocks.DARK_OAK_LOG, along);
                yield Blocks.WHITE_TERRACOTTA.defaultBlockState();
            }
            case "stone" -> masonry(x, wy, z);
            default -> base ? rubble(x, wy, z) : corner ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y) : whitewash(x, wy, z); // whitewash
        };
    }

    /**
     * Крутая двускатная крыша (≈60°): на каждый шаг внутрь — два блока вверх. Конёк вдоль v, свес ov.
     * thatch — солома (снопы сена), shingle — серый гонт (ель), slate — тёмный (замок).
     * Фронтоны — тёмные доски с ромбовидным продухом.
     */
    static void steepRoof(Frame f, int base, String kind, int ov) {
        int w = f.w, d = f.d;
        Block block = switch (kind) {
            case "thatch" -> Blocks.HAY_BLOCK;
            case "slate" -> Blocks.DEEPSLATE_TILES;
            default -> Blocks.SPRUCE_PLANKS;
        };
        Block stair = switch (kind) {
            case "slate" -> Blocks.DEEPSLATE_TILE_STAIRS;
            case "thatch" -> null;
            default -> Blocks.SPRUCE_STAIRS;
        };
        int steps = (w + 2 * ov) / 2;
        for (int i = 0; i <= steps; i++) {
            int left = -ov + i, right = w - 1 + ov - i, y = base + 2 * i - 1;
            if (left > right) break;
            for (int v = -ov; v < d + ov; v++) {
                if (left == right) {
                    f.set(left, y, v, block);
                    f.set(left, y + 1, v, kind.equals("thatch") ? Blocks.HAY_BLOCK.defaultBlockState()
                        : slab(kind.equals("slate") ? Blocks.DEEPSLATE_TILE_SLAB : Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
                    continue;
                }
                f.set(left, y, v, block);
                f.set(right, y, v, block);
                if (stair != null) {
                    f.set(left, y + 1, v, stairs(stair, Direction.EAST));
                    f.set(right, y + 1, v, stairs(stair, Direction.WEST));
                } else {
                    f.set(left, y + 1, v, block);
                    f.set(right, y + 1, v, block);
                }
            }
            // фронтоны
            for (int u = left + 1; u < right; u++) {
                if (u < 0 || u > w - 1) continue;
                for (int yy = y; yy <= y + 1; yy++) {
                    boolean vent = u == w / 2 && yy == base + w / 2;
                    BlockState g = vent ? Blocks.AIR.defaultBlockState() : log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);
                    f.set(u, yy, 0, g);
                    f.set(u, yy, d - 1, g);
                }
            }
        }
    }

    /** Кузница: открытый навес на брёвнах, каменный горн посредине у задней стены, мощёный пол, верстаки. */
    static void forge(Frame f) {
        int w = f.w, d = f.d;
        f.clearAndFound(2, 16);
        for (int u = -1; u <= w; u++) for (int v = -1; v <= d; v++) {
            f.set(u, 0, v, (hash(u, 0, v) % 3 == 0 ? Blocks.SMOOTH_STONE : hash(u, 1, v) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.COBBLESTONE).defaultBlockState());
        }
        // столбы и обвязка
        for (int u : new int[]{0, w / 2, w - 1}) for (int v : new int[]{0, d - 1}) {
            for (int y = 1; y <= 4; y++) f.set(u, y, v, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        }
        for (int u = 0; u < w; u++) {
            f.set(u, 4, 0, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
            f.set(u, 4, d - 1, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        }
        for (int v = 0; v < d; v++) {
            f.set(0, 4, v, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
            f.set(w - 1, 4, v, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        }
        // задняя стенка из досок по пояс
        for (int u = 1; u < w - 1; u++) for (int y = 1; y <= 2; y++) f.set(u, y, d - 1, Blocks.SPRUCE_PLANKS);
        steepRoof(f, 5, "shingle", 1);
        // горн с трубой
        int hu = w / 2;
        for (int u = hu - 1; u <= hu + 1; u++) for (int y = 1; y <= 3; y++) f.set(u, y, d - 2, rubble(u, y, d - 2));
        f.set(hu, 1, d - 3, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        f.set(hu - 1, 1, d - 3, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        for (int y = 4; y <= 5 + w; y++) f.set(hu, y, d - 2, Blocks.BRICKS);
        f.set(hu, 6 + w, d - 2, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        // наковальня, бочка с водой, точило, верстак, сырьё
        f.set(hu, 1, d / 2 - 1, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(hu + 2, 1, d / 2 - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        f.set(1, 1, d / 2, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(w - 2, 1, 1, Blocks.SMITHING_TABLE);
        f.set(1, 1, 1, Blocks.BARREL);
        f.set(w - 2, 1, d - 2, Blocks.COAL_BLOCK);
        f.set(w - 2, 2, d - 2, Blocks.CHAIN);
        f.set(hu, 4, d / 2, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    /** Корчма: большой побелённый сруб под высокой гонтовой крышей, внутри стойка и столы, снаружи столы. */
    static void tavern(Frame f) {
        house(f, "whitewash", "shingle", 2, false);
        int w = f.w, d = f.d;
        for (int u = 1; u < w - 1; u++) f.set(u, 1, d - 3, u == w / 2 ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int u = 1; u < w - 1; u += 2) f.set(u, 1, d - 2, Blocks.BARREL);
        for (int v = 2; v < d - 4; v += 3) {
            f.set(2, 1, v, Blocks.SPRUCE_FENCE);
            f.set(2, 2, v, Blocks.SPRUCE_PRESSURE_PLATE);
            f.set(1, 1, v, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            f.set(3, 1, v, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        // венок-вывеска над входом
        f.set(w / 2 + 1, 3, -1, Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
        for (int u : new int[]{1, w - 2}) {
            f.set(u, 1, -3, Blocks.SPRUCE_FENCE);
            f.set(u, 2, -3, Blocks.SPRUCE_PRESSURE_PLATE);
            f.set(u, 1, -2, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            f.set(u, 1, -4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
    }

    // ------------------------------------------------------------------ замок

    /** Палас: каменный низ, оштукатуренный верх, высокая тёмная крыша и труба. */
    static void palace(Frame f) {
        int w = f.w, d = f.d, top = 7;
        f.clearAndFound(1, top + w + 6);
        f.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SPRUCE_PLANKS);
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (!edge) continue;
            for (int y = 1; y <= top; y++) {
                int x = f.wx(u, v), z = f.wz(u, v);
                f.set(u, y, v, y <= 3 ? masonry(x, f.y0 + y, z) : whitewash(x, f.y0 + y, z));
            }
        }
        for (int y : new int[]{2, 6}) {
            for (int u = 2; u < w - 2; u += 3) { f.set(u, y, 0, Blocks.AIR); f.set(u, y, d - 1, Blocks.AIR); }
        }
        f.fill(1, 4, 1, w - 2, 4, d - 2, Blocks.SPRUCE_PLANKS);
        door(f, w / 2, 0, Blocks.DARK_OAK_DOOR);
        steepRoof(f, top + 1, "slate", 1);
        for (int y = 1; y <= top + w + 2; y++) f.set(w - 2, y, d / 2, y <= top ? masonry(0, y, 0) : Blocks.CALCITE.defaultBlockState());
    }

    /** Круглый бергфрид из бута: этажи, бойницы, деревянная галерея и тёмная коническая крыша. */
    static void tower(Frame f, int h) {
        double r = f.w / 2.0;
        int c = f.w / 2;
        f.clearAndFound(1, h + f.w + 6);
        for (int u = 0; u < f.w; u++) for (int v = 0; v < f.w; v++) {
            double dist = Math.hypot(u - c, v - c);
            if (dist > r) continue;
            boolean shell = dist > r - 1.5;
            for (int y = -3; y <= h; y++) {
                int x = f.wx(u, v), z = f.wz(u, v);
                if (shell || y <= 0) f.set(u, y, v, masonry(x, f.y0 + y, z));
                else if (y % 5 == 0) f.set(u, y, v, Blocks.SPRUCE_PLANKS);
            }
        }
        for (int y = 4; y < h; y += 5) {
            f.set(c, y, 0, Blocks.AIR);
            f.set(c, y, f.w - 1, Blocks.AIR);
            f.set(0, y, c, Blocks.AIR);
            f.set(f.w - 1, y, c, Blocks.AIR);
        }
        for (int y = 1; y <= h; y++) f.set(c, y, c + 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        door(f, c, 0, Blocks.DARK_OAK_DOOR);
        // деревянная галерея на консолях
        for (int u = -1; u <= f.w; u++) for (int v = -1; v <= f.w; v++) {
            double dist = Math.hypot(u - c, v - c);
            if (dist <= r + 1 && dist > r - 1.5) {
                f.set(u, h + 1, v, Blocks.SPRUCE_PLANKS);
                if (dist > r) {
                    f.set(u, h + 2, v, Blocks.SPRUCE_FENCE);
                    f.set(u, h + 3, v, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
                }
            }
        }
        // конус
        double rr = r + 1.5;
        for (int k = 0; rr > 0.4; k++, rr -= 0.55) {
            for (int u = -2; u <= f.w + 1; u++) for (int v = -2; v <= f.w + 1; v++) {
                double dist = Math.hypot(u - c, v - c);
                if (dist <= rr && dist > rr - 1.6) f.set(u, h + 4 + k, v, Blocks.DEEPSLATE_TILES);
            }
        }
    }

    /** Надвратная башня: каменная (замок) или бревенчатый блокгауз с шатровой крышей (ограда). */
    static void gatehouse(Frame f, String material) {
        boolean wood = material.equals("wood");
        int w = f.w, d = f.d, h = wood ? 6 : 10;
        f.clearAndFound(0, h + w + 4);
        int gap = 3, left = (w - gap) / 2, right = left + gap - 1;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean opening = u >= left && u <= right;
            for (int y = 1; y <= h; y++) {
                if (opening && y <= 4) continue;
                int x = f.wx(u, v), z = f.wz(u, v);
                BlockState s = wood ? log(Blocks.SPRUCE_LOG, v == 0 || v == d - 1 ? Direction.Axis.X : Direction.Axis.Z) : masonry(x, f.y0 + y, z);
                boolean shell = u == 0 || v == 0 || u == w - 1 || v == d - 1 || opening || y == h;
                if (shell) f.set(u, y, v, s);
            }
            if (!wood && (u + v) % 2 == 0) f.set(u, h + 1, v, Blocks.COBBLESTONE_WALL);
        }
        if (!wood) for (int u = left; u <= right; u++) f.set(u, 4, 0, Blocks.IRON_BARS);
        else for (int u = left; u <= right; u++) f.set(u, 4, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        f.set(left - 1, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        f.set(right + 1, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        if (wood) pyramidRoof(f, h + 1, Blocks.SPRUCE_PLANKS);
    }

    /** Сторожевая вышка частокола: сруб на столбах, площадка, шатровая гонтовая крыша. */
    static void watchtower(Frame f) {
        int w = f.w;
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) {
            for (int y = -1; y <= 9; y++) f.set(c[0], y, c[1], log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        }
        f.fill(-1, 6, -1, w, 6, w, Blocks.SPRUCE_PLANKS);
        for (int u = -1; u <= w; u++) for (int v = -1; v <= w; v++) {
            boolean edge = u == -1 || v == -1 || u == w || v == w;
            if (edge) f.set(u, 7, v, log(Blocks.STRIPPED_SPRUCE_LOG, u == -1 || u == w ? Direction.Axis.Z : Direction.Axis.X));
        }
        for (int y = 0; y <= 6; y++) f.set(1, y, 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        f.set(1, 6, 1, Blocks.AIR);
        pyramidRoof(new Frame2(f, -1, 10, w + 2), 0, Blocks.SPRUCE_PLANKS);
    }

    /** Шатёр над квадратом w×d (сдвиг: верх рамки). */
    static void pyramidRoof(Frame f, int base, Block block) {
        int w = f.w, d = f.d;
        for (int k = 0; ; k++) {
            int u0 = -1 + k, u1 = w - k, v0 = -1 + k, v1 = d - k;
            if (u0 > u1 || v0 > v1) break;
            for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                if (u == u0 || u == u1 || v == v0 || v == v1) f.set(u, base + k, v, block);
            }
        }
    }

    /** Рамка-смещение для крыши вышки: квадрат size×size, начиная с (off, off), на высоте y. */
    private static final class Frame2 extends Frame {
        Frame2(Frame parent, int off, int y, int size) {
            super(parent, off, y, size);
        }
    }

    // ------------------------------------------------------------------ рынок и двор

    static void stall(Frame f, int color) {
        Block[] wools = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.BROWN_WOOL, Blocks.WHITE_WOOL};
        Block wool = wools[Math.floorMod(color, wools.length)];
        int w = f.w, d = f.d;
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            for (int y = 1; y <= 3; y++) f.set(c[0], y, c[1], Blocks.SPRUCE_FENCE);
        }
        f.fill(0, 4, 0, w - 1, 4, d - 1, Blocks.AIR);
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) f.set(u, v == 0 ? 3 : 4, v, wool); // навес с наклоном к улице
        for (int u = 1; u < w - 1; u++) f.set(u, 1, 0, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
        f.set(1, 1, d - 1, Blocks.BARREL);
        if (w > 3) f.set(w - 2, 1, d - 1, Blocks.COMPOSTER);
    }

    static void pillory(Frame f) {
        f.set(0, 0, 0, Blocks.COBBLESTONE);
        f.set(0, 1, 0, Blocks.SPRUCE_FENCE);
        f.set(0, 2, 0, Blocks.SPRUCE_FENCE);
        f.set(0, 3, 0, Blocks.SPRUCE_PLANKS);
        f.set(-1, 3, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        f.set(1, 3, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
    }

    /** Колодец под двускатным навесом. */
    static void well(Frame f) {
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) {
            f.set(u, -2, v, Blocks.COBBLESTONE);
            f.set(u, -1, v, (u == 0 && v == 0) ? Blocks.WATER : Blocks.COBBLESTONE);
            f.set(u, 0, v, (u == 0 && v == 0) ? Blocks.WATER : Blocks.COBBLESTONE);
            if (u != 0 || v != 0) f.set(u, 1, v, Blocks.COBBLESTONE_WALL);
        }
        for (int y = 2; y <= 3; y++) {
            f.set(-1, y, 0, Blocks.SPRUCE_FENCE);
            f.set(1, y, 0, Blocks.SPRUCE_FENCE);
        }
        for (int v = -1; v <= 1; v++) {
            f.set(-1, 4, v, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
            f.set(1, 4, v, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            f.set(0, 4, v, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        }
        f.set(0, 3, 0, Blocks.CHAIN);
    }

    /** Водяная мельница: сруб и колесо сбоку (на стороне u = w). */
    static void mill(Frame f) {
        house(f, "log", "shingle", 2, false);
        int d = f.d, cy = 2, cv = d / 2;
        for (int a = 0; a < 360; a += 15) {
            int dy = (int) Math.round(Math.sin(Math.toRadians(a)) * 3), dv = (int) Math.round(Math.cos(Math.toRadians(a)) * 3);
            f.set(f.w + 1, cy + dy, cv + dv, Blocks.STRIPPED_OAK_LOG);
        }
        for (int i = -2; i <= 2; i++) {
            f.set(f.w + 1, cy + i, cv, Blocks.OAK_PLANKS);
            f.set(f.w + 1, cy, cv + i, Blocks.OAK_PLANKS);
        }
        f.set(f.w, cy, cv, log(Blocks.OAK_LOG, Direction.Axis.X));
    }

    /** Амбар/конюшня: сруб без передней стены, крыша почти до земли. */
    static void barn(Frame f, String roof) {
        int w = f.w, d = f.d;
        f.clearAndFound(1, 12 + w);
        f.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.COARSE_DIRT);
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edgeU = u == 0 || u == w - 1, edgeV = v == d - 1;
            if (!edgeU && !edgeV) continue;
            for (int y = 1; y <= 2; y++) f.set(u, y, v, log(Blocks.STRIPPED_SPRUCE_LOG, edgeV ? Direction.Axis.X : Direction.Axis.Z));
        }
        for (int u : new int[]{0, w - 1}) for (int y = 1; y <= 2; y++) f.set(u, y, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        for (int v = 1; v < d - 1; v += 2) f.set(1, 1, v, Blocks.HAY_BLOCK);
        steepRoof(f, 3, roof, 2);
    }

    /** Вход в рудник: деревянная крепь, рельсы вглубь склона. */
    static void mine(Frame f) {
        int d = f.d;
        for (int v = 0; v < d; v++) {
            f.fill(-1, 1, v, 1, 3, v, Blocks.AIR);
            f.set(0, 0, v, Blocks.GRAVEL);
            f.set(0, 1, v, Blocks.RAIL);
            if (v % 3 == 0) {
                for (int y = 1; y <= 3; y++) {
                    f.set(-2, y, v, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                    f.set(2, y, v, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                }
                for (int u = -2; u <= 2; u++) f.set(u, 4, v, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
            }
        }
        f.set(-1, 1, 0, Blocks.LANTERN);
    }

    static void cart(Frame f) {
        f.fill(0, 1, 0, 1, 1, 2, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
        f.set(-1, 1, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        f.set(2, 1, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(0, 2, 1, Blocks.HAY_BLOCK);
        f.set(0, 1, -1, Blocks.SPRUCE_FENCE);
    }

    static void hay(Frame f) {
        f.set(0, 1, 0, Blocks.HAY_BLOCK);
        f.set(1, 1, 0, Blocks.HAY_BLOCK);
        f.set(0, 1, 1, Blocks.HAY_BLOCK);
        f.set(1, 1, 1, Blocks.HAY_BLOCK);
        f.set(0, 2, 0, Blocks.HAY_BLOCK);
        f.set(1, 2, 1, Blocks.HAY_BLOCK);
        f.set(0, 3, 0, Blocks.HAY_BLOCK);
    }

    /** Штабель брёвен. */
    static void logPile(Frame f) {
        for (int u = 0; u < f.w; u++) for (int y = 1; y <= 2; y++) {
            if (y == 2 && (u == 0 || u == f.w - 1)) continue;
            f.set(u, y, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
    }

    /** Столы со скамьями (у корчмы). */
    static void tables(Frame f) {
        for (int u = 0; u < f.w; u += 3) {
            f.set(u, 1, 0, Blocks.SPRUCE_FENCE);
            f.set(u, 2, 0, Blocks.SPRUCE_PRESSURE_PLATE);
            f.set(u, 1, -1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            f.set(u, 1, 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }
    }

    /** Загон из жердей: внутри сено (овчарня) или мишени (ристалище). */
    static void pen(Frame f, String inside) {
        int w = f.w, d = f.d;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (!edge) continue;
            f.set(u, 1, v, u == w / 2 && v == 0 ? Blocks.SPRUCE_FENCE_GATE.defaultBlockState() : Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        if (inside.equals("training")) {
            for (int u = 2; u < w - 2; u += 3) {
                f.set(u, 1, d - 2, Blocks.HAY_BLOCK);
                f.set(u, 2, d - 2, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            }
        } else if (inside.equals("sheep")) {
            f.set(1, 1, d - 2, Blocks.HAY_BLOCK);
            f.set(2, 1, d - 2, Blocks.HAY_BLOCK);
        }
    }

    // ------------------------------------------------------------------ линии (стены, частокол, ограды)

    private interface LineStep { void at(int x, int z, int y, boolean along, int k); }

    private static void line(List<int[]> pts, KcdMap map, LineStep step) {
        int k = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            int dx = b[0] - a[0], dz = b[1] - a[1];
            int n = Math.max(Math.abs(dx), Math.abs(dz));
            for (int s = 0; s <= n; s++, k++) {
                int x = a[0] + Math.round((float) dx * s / Math.max(1, n));
                int z = a[1] + Math.round((float) dz * s / Math.max(1, n));
                step.at(x, z, map.surfaceY(x, z), Math.abs(dx) >= Math.abs(dz), k);
            }
        }
    }

    /** Каменная куртина толщиной 2 с зубцами. */
    static void wall(List<int[]> pts, int h, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX, k) -> {
            for (int t = 0; t < 2; t++) {
                int wx = alongX ? x : x + t, wz = alongX ? z + t : z;
                for (int dy = -4; dy <= h; dy++) sink.set(wx, y + dy, wz, masonry(wx, y + dy, wz));
                if (t == 1 && k % 2 == 0) sink.set(wx, y + h + 1, wz, masonry(wx, y + h + 1, wz));
            }
        });
    }

    /** Бревенчатая стена замкового холма: горизонтальные брёвна между парными столбами. */
    static void logwall(List<int[]> pts, int h, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX, k) -> {
            boolean post = k % 4 == 0;
            for (int dy = 0; dy <= h; dy++) {
                BlockState s = post ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y)
                    : log(Blocks.STRIPPED_SPRUCE_LOG, alongX ? Direction.Axis.X : Direction.Axis.Z);
                sink.set(x, y + dy, z, s);
            }
            if (post) sink.set(x, y + h + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        });
    }

    /** Частокол деревни: заострённые брёвна (светлое окорённое дерево). */
    static void palisade(List<int[]> pts, int h, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX, k) -> {
            for (int dy = -1; dy < h; dy++) sink.set(x, y + 1 + dy, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            sink.set(x, y + 1 + h, z, (k % 2 == 0 ? Blocks.SPRUCE_FENCE : Blocks.STRIPPED_SPRUCE_LOG).defaultBlockState());
        });
    }

    /** Жердевая ограда. */
    static void fence(List<int[]> pts, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX, k) -> sink.set(x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState()));
    }
}
