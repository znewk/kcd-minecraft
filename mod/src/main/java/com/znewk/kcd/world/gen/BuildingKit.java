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
 * Строители построек «как построил бы хороший майнкрафтер»: ванильные блоки и пропорции, силуэты KCD.
 * Каждая постройка строится в своей локальной системе (u — поперёк фасада, v — от фасада вглубь, вход на v=0)
 * и поворачивается целиком вместе с блоками (лестницы, двери, брёвна).
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
            case "house" -> house(new Frame(s, map, sink), s.str("style", "timber"), s.num("floors", 1), false);
            case "forge" -> forge(new Frame(s, map, sink));
            case "tavern" -> tavern(new Frame(s, map, sink));
            case "keep" -> keep(new Frame(s, map, sink), s.num("h", 18));
            case "hall" -> house(new Frame(s, map, sink), "stone", s.num("floors", 2), false);
            case "gatehouse" -> gatehouse(new Frame(s, map, sink), s.str("material", "stone"));
            case "stall" -> stall(new Frame(s, map, sink), s.num("color", 0));
            case "pillory" -> pillory(new Frame(s, map, sink));
            case "well" -> well(new Frame(s, map, sink));
            case "mill" -> mill(new Frame(s, map, sink));
            case "barn" -> barn(new Frame(s, map, sink));
            case "shed" -> house(new Frame(s, map, sink), "log", 1, true);
            case "mine" -> mine(new Frame(s, map, sink));
            case "cart" -> cart(new Frame(s, map, sink));
            case "hay" -> hay(new Frame(s, map, sink));
            case "tables" -> tables(new Frame(s, map, sink));
            case "pen" -> pen(new Frame(s, map, sink), s.str("inside", "none"));
            case "tree" -> sink.tree(s.x(), map.surfaceY(s.x(), s.z()) + 1, s.z());
            case "wall" -> wall(s.points("points"), s.num("h", 8), map, sink);
            case "palisade" -> palisade(s.points("points"), s.num("h", 4), map, sink);
            case "fence" -> fence(s.points("points"), map, sink);
            default -> throw new IllegalArgumentException("неизвестный тип постройки " + s.type());
        }
    }

    // ------------------------------------------------------------------ локальная система

    /** Прямоугольник постройки с поворотом. y0 — уровень пола (высота земли в центре). */
    static final class Frame {
        final int w, d, y0;
        final int x0, z0, x1, z1;
        final int rot;
        final Rotation rotation;
        final Sink sink;

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
        }

        int wx(int u, int v) {
            return switch (rot) {
                case 90 -> x1 - v;
                case 180 -> x1 - u;
                case 270 -> x0 + v;
                default -> x0 + u;
            };
        }

        int wz(int u, int v) {
            return switch (rot) {
                case 90 -> z0 + u;
                case 180 -> z1 - v;
                case 270 -> z1 - u;
                default -> z0 + v;
            };
        }

        /** y — относительно пола (0 — пол). Состояние задаётся как для фасада на севере. */
        void set(int u, int y, int v, BlockState state) {
            sink.set(wx(u, v), y0 + y, wz(u, v), state.rotate(rotation));
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

        /** Расчистить объём и подсыпать фундамент на склоне. */
        void clearAndFound(int extra) {
            fill(-extra, 1, -extra, w - 1 + extra, 14, d - 1 + extra, Blocks.AIR);
            fill(0, -3, 0, w - 1, -1, d - 1, Blocks.COBBLESTONE);
        }
    }

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

    /** Палитра стиля: столбы, балки, заполнение, цоколь. */
    private record Style(Block post, Block beam, Block infill, Block base, Block roofStairs, Block roofPlanks) {}

    private static Style style(String name) {
        return switch (name) {
            case "log" -> new Style(Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
            case "plaster" -> new Style(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.CALCITE, Blocks.STONE_BRICKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS);
            case "stone" -> new Style(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
            default -> new Style(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.MUD_BRICKS, Blocks.COBBLESTONE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS); // фахверк
        };
    }

    /** Дом: цоколь, стены по стилю, окна, дверь, перекрытия, двускатная крыша (конёк вглубь), немного обстановки. */
    static void house(Frame f, String styleName, int floors, boolean open) {
        Style st = style(styleName);
        int w = f.w, d = f.d, top = floors * 4;
        f.clearAndFound(1);
        f.fill(0, 0, 0, w - 1, 0, d - 1, st.base() == Blocks.STONE_BRICKS ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS);
        f.fill(0, 0, 0, w - 1, 0, 0, st.base());
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                boolean edgeU = u == 0 || u == w - 1, edgeV = v == 0 || v == d - 1;
                if (!edgeU && !edgeV) continue;
                boolean corner = edgeU && edgeV;
                for (int y = 1; y <= top; y++) {
                    BlockState s;
                    boolean beamRow = y % 4 == 0;
                    boolean post = corner || (!edgeV ? v % 3 == 0 : u % 3 == 0) && !styleName.equals("log") && !styleName.equals("stone");
                    if (y == 1 && !styleName.equals("log")) s = st.base().defaultBlockState();
                    else if (corner || post) s = log(st.post(), Direction.Axis.Y);
                    else if (beamRow && !styleName.equals("stone")) s = log(st.beam(), edgeV ? Direction.Axis.X : Direction.Axis.Z);
                    else if (styleName.equals("log")) s = log(st.infill(), edgeV ? Direction.Axis.X : Direction.Axis.Z);
                    else s = st.infill().defaultBlockState();
                    f.set(u, y, v, s);
                }
            }
        }
        // окна: на каждом этаже, через два блока, не на столбах
        for (int fl = 0; fl < floors; fl++) {
            int y = fl * 4 + 2;
            for (int u = 2; u < w - 2; u += 3) {
                if (fl > 0 || u != w / 2) f.set(u, y, 0, Blocks.GLASS_PANE);
                f.set(u, y, d - 1, Blocks.GLASS_PANE);
            }
            for (int v = 2; v < d - 2; v += 3) {
                f.set(0, y, v, Blocks.GLASS_PANE);
                f.set(w - 1, y, v, Blocks.GLASS_PANE);
            }
        }
        // перекрытия
        for (int fl = 1; fl <= floors; fl++) f.fill(1, fl * 4, 1, w - 2, fl * 4, d - 2, st.roofPlanks());
        if (floors > 1) {
            for (int y = 1; y <= 4; y++) f.set(w - 2, y, d - 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            f.set(w - 2, 4, d - 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }
        if (open) f.fill(1, 1, 0, w - 2, 3, 0, Blocks.AIR);
        else door(f, w / 2, 0, Blocks.SPRUCE_DOOR);
        roof(f, top, st.roofStairs(), st.roofPlanks());
        if (!open && w >= 5 && d >= 5) {
            bed(f, 1, d - 3);
            f.set(w - 2, 1, 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            f.set(1, 1, 1, Blocks.BARREL);
            f.set(w / 2, 1, d / 2, Blocks.OAK_FENCE);
            f.set(w / 2, 2, d / 2, Blocks.OAK_PRESSURE_PLATE);
            f.set(w / 2, 3, d / 2, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }
    }

    /** Двускатная крыша: конёк вдоль v, скаты к бокам u, свесы по 1; фронтоны — доски. */
    static void roof(Frame f, int top, Block stairs, Block planks) {
        int w = f.w, d = f.d;
        for (int i = 0; ; i++) {
            int left = -1 + i, right = w - i, y = top + i;
            if (left > right) break;
            for (int v = -1; v <= d; v++) {
                if (left == right) {
                    f.set(left, y, v, slabFor(planks));
                } else {
                    f.set(left, y, v, stairs(stairs, Direction.EAST));
                    f.set(right, y, v, stairs(stairs, Direction.WEST));
                }
            }
            for (int u = left + 1; u < right; u++) {
                if (u <= 0 || u >= w - 1) continue;
                f.set(u, y, 0, planks);
                f.set(u, y, d - 1, planks);
            }
        }
    }

    private static BlockState slabFor(Block planks) {
        Block s = planks == Blocks.DARK_OAK_PLANKS ? Blocks.DARK_OAK_SLAB
            : planks == Blocks.DEEPSLATE_TILES ? Blocks.DEEPSLATE_TILE_SLAB
            : Blocks.SPRUCE_SLAB;
        return slab(s, SlabType.BOTTOM);
    }

    /** Кузница: сруб с открытым к улице фасадом, горн с трубой, наковальня, бочка с водой, точило. */
    static void forge(Frame f) {
        house(f, "log", 1, true);
        int w = f.w, d = f.d;
        f.set(w / 2, 1, 2, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(w - 2, 1, d - 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        f.set(w - 3, 1, d - 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        for (int y = 2; y <= 4 + f.w / 2 + 2; y++) {
            f.set(w - 2, y, d - 2, Blocks.BRICKS);
            f.set(w - 3, y, d - 2, Blocks.BRICKS);
        }
        f.set(1, 1, d - 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        f.set(1, 1, d / 2, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(w - 2, 1, 1, Blocks.SMITHING_TABLE);
        f.set(1, 1, 1, Blocks.BARREL);
        f.set(w / 2, 3, d / 2, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    /** Корчма: двухэтажный фахверк, внутри стойка с бочками, снаружи столы со скамьями. */
    static void tavern(Frame f) {
        house(f, "timber", 2, false);
        int w = f.w, d = f.d;
        for (int u = 1; u < w - 1; u++) f.set(u, 1, d - 3, u == w / 2 ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int u = 1; u < w - 1; u += 2) f.set(u, 1, d - 2, Blocks.BARREL);
        for (int v = 2; v < d - 4; v += 3) {
            f.set(2, 1, v, Blocks.OAK_FENCE);
            f.set(2, 2, v, Blocks.OAK_PRESSURE_PLATE);
            f.set(1, 1, v, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            f.set(3, 1, v, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        // столы на улице перед входом
        for (int u : new int[]{1, w - 2}) {
            f.set(u, 1, -3, Blocks.OAK_FENCE);
            f.set(u, 2, -3, Blocks.OAK_PRESSURE_PLATE);
            f.set(u, 1, -2, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            f.set(u, 1, -4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
    }

    // ------------------------------------------------------------------ замок

    /** Донжон: квадратная каменная башня, этажи через 5 блоков, бойницы, зубцы. */
    static void keep(Frame f, int h) {
        int w = f.w, d = f.d;
        f.clearAndFound(0);
        f.fill(0, -3, 0, w - 1, 0, d - 1, Blocks.STONE_BRICKS);
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            for (int y = 1; y <= h; y++) {
                if (edge) f.set(u, y, v, stone(u, y, v));
                else if (y % 5 == 0) f.set(u, y, v, Blocks.SPRUCE_PLANKS);
            }
        }
        for (int y = 3; y < h; y += 5) {
            f.set(w / 2, y, 0, Blocks.AIR);
            f.set(w / 2, y, d - 1, Blocks.AIR);
            f.set(0, y, d / 2, Blocks.AIR);
            f.set(w - 1, y, d / 2, Blocks.AIR);
        }
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (edge && (u + v) % 2 == 0) f.set(u, h + 1, v, Blocks.STONE_BRICK_WALL);
        }
        door(f, w / 2, 0, Blocks.DARK_OAK_DOOR);
        for (int y = 1; y <= h; y++) f.set(w - 2, y, d - 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static BlockState stone(int x, int y, int z) {
        int n = Math.floorMod(x * 7 + y * 13 + z * 5, 11);
        return (n == 0 ? Blocks.MOSSY_STONE_BRICKS : n == 1 ? Blocks.CRACKED_STONE_BRICKS : n < 4 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState();
    }

    /** Надвратная башня: две башни и арка с решёткой; каменная (замок) или деревянная (частокол). */
    static void gatehouse(Frame f, String material) {
        boolean wood = material.equals("wood");
        int w = f.w, d = f.d, h = wood ? 7 : 11;
        f.clearAndFound(0);
        int gap = 3, left = (w - gap) / 2, right = left + gap - 1;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean opening = u >= left && u <= right;
            for (int y = 1; y <= h; y++) {
                if (opening && y <= 4) continue;
                BlockState s = wood ? log(Blocks.SPRUCE_LOG, Direction.Axis.Y) : stone(u, y, v);
                boolean shell = u == 0 || v == 0 || u == w - 1 || v == d - 1 || opening || y == h;
                if (shell) f.set(u, y, v, s);
            }
            if ((u + v) % 2 == 0) f.set(u, h + 1, v, wood ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        if (!wood) for (int u = left; u <= right; u++) f.set(u, 4, 0, Blocks.IRON_BARS);
        f.set(left - 1, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        f.set(right + 1, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        if (wood) roof(f, h, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
    }

    // ------------------------------------------------------------------ рынок и двор

    static void stall(Frame f, int color) {
        Block[] wools = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.BROWN_WOOL, Blocks.WHITE_WOOL};
        Block wool = wools[Math.floorMod(color, wools.length)];
        int w = f.w, d = f.d;
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            for (int y = 1; y <= 3; y++) f.set(c[0], y, c[1], Blocks.SPRUCE_FENCE);
        }
        f.fill(0, 4, 0, w - 1, 4, d - 1, wool);
        for (int u = 0; u < w; u++) f.set(u, 1, 0, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        f.set(1, 1, d - 1, Blocks.BARREL);
        if (w > 3) f.set(w - 2, 1, d - 1, Blocks.COMPOSTER);
    }

    static void pillory(Frame f) {
        f.set(0, 1, 0, Blocks.OAK_FENCE);
        f.set(0, 2, 0, Blocks.OAK_FENCE);
        f.set(0, 3, 0, Blocks.OAK_PLANKS);
        f.set(-1, 3, 0, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        f.set(1, 3, 0, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        f.set(0, 0, 0, Blocks.COBBLESTONE);
    }

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
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) f.set(u, 4, v, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
    }

    /** Водяная мельница: срубовый дом и колесо сбоку (на стороне u = w). */
    static void mill(Frame f) {
        house(f, "log", 2, false);
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

    /** Конюшня/сарай: навес на столбах, с одной стороны стена, сено. */
    static void barn(Frame f) {
        int w = f.w, d = f.d;
        f.clearAndFound(1);
        f.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.COARSE_DIRT);
        for (int u = 0; u < w; u += Math.max(1, (w - 1) / 3)) {
            for (int y = 1; y <= 4; y++) {
                f.set(u, y, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                f.set(u, y, d - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            }
        }
        f.fill(0, 1, d - 1, w - 1, 3, d - 1, Blocks.SPRUCE_PLANKS);
        for (int v = 1; v < d - 1; v += 2) f.set(1, 1, v, Blocks.HAY_BLOCK);
        roof(f, 4, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
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
        f.set(0, 2, 0, Blocks.HAY_BLOCK);
    }

    /** Столы со скамьями (у корчмы). */
    static void tables(Frame f) {
        for (int u = 0; u < f.w; u += 3) {
            f.set(u, 1, 0, Blocks.OAK_FENCE);
            f.set(u, 2, 0, Blocks.OAK_PRESSURE_PLATE);
            f.set(u, 1, -1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            f.set(u, 1, 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }
    }

    /** Загон: ограда по периметру, калитка спереди; внутри — сено и мишени для ристалища. */
    static void pen(Frame f, String inside) {
        int w = f.w, d = f.d;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (!edge) continue;
            f.set(u, 1, v, u == w / 2 && v == 0 ? Blocks.OAK_FENCE_GATE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
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

    private interface LineStep { void at(int x, int z, int y, boolean along); }

    private static void line(List<int[]> pts, KcdMap map, LineStep step) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            int dx = b[0] - a[0], dz = b[1] - a[1];
            int n = Math.max(Math.abs(dx), Math.abs(dz));
            for (int k = 0; k <= n; k++) {
                int x = a[0] + Math.round((float) dx * k / Math.max(1, n));
                int z = a[1] + Math.round((float) dz * k / Math.max(1, n));
                step.at(x, z, map.surfaceY(x, z), Math.abs(dx) >= Math.abs(dz));
            }
        }
    }

    /** Каменная стена толщиной 2 с ходом поверху и зубцами. */
    static void wall(List<int[]> pts, int h, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX) -> {
            for (int t = 0; t < 2; t++) {
                int wx = alongX ? x : x + t, wz = alongX ? z + t : z;
                for (int dy = -3; dy <= h; dy++) sink.set(wx, y + dy, wz, stone(wx, dy, wz));
                if (t == 1 && Math.floorMod(x + z, 2) == 0) sink.set(wx, y + h + 1, wz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
        });
    }

    /** Частокол: заострённые брёвна. */
    static void palisade(List<int[]> pts, int h, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX) -> {
            for (int dy = -1; dy < h; dy++) sink.set(x, y + 1 + dy, z, Blocks.SPRUCE_LOG.defaultBlockState());
            sink.set(x, y + 1 + h, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        });
    }

    static void fence(List<int[]> pts, KcdMap map, Sink sink) {
        line(pts, map, (x, z, y, alongX) -> sink.set(x, y + 1, z, Blocks.OAK_FENCE.defaultBlockState()));
    }
}
