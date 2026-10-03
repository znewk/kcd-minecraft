package com.znewk.kcd.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import com.znewk.kcd.KcdPerms;
import com.znewk.kcd.npc.KcdEntities;
import com.znewk.kcd.npc.KcdNpc;

/**
 * «Тестовая деревня» для проверки систем вместе: кузница Мартина, лавка угольщика, ворота замка со стражником,
 * дом и пара сельчан. Строится вокруг игрока (площадка ~34×32, земля выравнивается). Повторный вызов
 * перестраивает и заменяет жителей. Это не Скалица — та будет по карте KCD в M2.
 */
public final class TestVillage {
    private static final int X0 = -16, X1 = 16, Z0 = -18, Z1 = 13, CLEAR_H = 12;

    private TestVillage() {}

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcd")
            .then(Commands.literal("testvillage")
                .requires(KcdPerms::host)
                .executes(TestVillage::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        build(player.serverLevel(), player.blockPosition());
        ctx.getSource().sendSuccess(() -> Component.translatable("kcd.village.built"), true);
        return 1;
    }

    public static void build(ServerLevel level, BlockPos o) {
        // старые жители этой площадки
        level.getEntitiesOfClass(KcdNpc.class, new AABB(o.offset(X0, -2, Z0).getCenter(), o.offset(X1, CLEAR_H, Z1).getCenter()))
            .forEach(n -> n.discard());

        // площадка: трава, над ней пусто
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            set(level, o, x, -2, z, Blocks.DIRT.defaultBlockState());
            set(level, o, x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y < CLEAR_H; y++) set(level, o, x, y, z, Blocks.AIR.defaultBlockState());
        }
        // дороги
        for (int z = -13; z <= 11; z++) for (int x = -1; x <= 1; x++) set(level, o, x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int x = -11; x <= 3; x++) for (int z = 1; z <= 2; z++) set(level, o, x, -1, z, Blocks.DIRT_PATH.defaultBlockState());

        forge(level, o);
        charcoalStall(level, o);
        gate(level, o);
        house(level, o, -11, -9, 6, 5, Blocks.OAK_PLANKS, Direction.EAST);
        decor(level, o);

        npc(level, o, "kcd:martin", 6, 0, 6, 90F);
        npc(level, o, "kcd:charcoal_burner", -9, 0, 3, -90F);
        npc(level, o, "kcd:guard", -2, 0, -12, 0F);
        npc(level, o, "kcd:peasant", -4, 0, -5, -45F);
        npc(level, o, "kcd:peasant", 4, 0, -3, 135F);
    }

    // ------------------------------------------------------------------ постройки

    /** Кузница: каменный пол, открытая к дороге сторона, наковальня, горн, бочка с водой. */
    private static void forge(ServerLevel level, BlockPos o) {
        int x0 = 4, x1 = 10, z0 = 3, z1 = 9, h = 4;
        walls(level, o, x0, x1, z0, z1, h, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG);
        // передняя стена к дороге открыта (как навес кузницы)
        for (int z = z0 + 1; z < z1; z++) for (int y = 0; y < h - 1; y++) set(level, o, x0, y, z, Blocks.AIR.defaultBlockState());
        set(level, o, x0, 0, z0 + 3, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(level, o, x0, 1, z0 + 3, Blocks.SPRUCE_FENCE.defaultBlockState());
        gableRoof(level, o, x0, x1, z0, z1, h, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        set(level, o, 7, 0, 7, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        set(level, o, 9, 0, 5, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        set(level, o, 9, 0, 6, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        set(level, o, 9, 1, 5, Blocks.BRICKS.defaultBlockState());
        set(level, o, 9, 1, 6, Blocks.BRICKS.defaultBlockState());
        set(level, o, 9, 0, 8, Blocks.SMITHING_TABLE.defaultBlockState());
        set(level, o, 8, 0, 8, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        set(level, o, 5, 0, 8, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        set(level, o, 9, 0, 4, Blocks.BARREL.defaultBlockState());
        set(level, o, 8, 2, 4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    /** Лавка угольщика у рынка: навес на столбах, мешки угля (блоки угля), бочки. */
    private static void charcoalStall(ServerLevel level, BlockPos o) {
        int x0 = -13, x1 = -9, z0 = 2, z1 = 6;
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            for (int y = 0; y < 3; y++) set(level, o, c[0], y, c[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            set(level, o, x, 3, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
        }
        set(level, o, -12, 0, 5, Blocks.COAL_BLOCK.defaultBlockState());
        set(level, o, -11, 0, 5, Blocks.COAL_BLOCK.defaultBlockState());
        set(level, o, -12, 1, 5, Blocks.COAL_BLOCK.defaultBlockState());
        set(level, o, -12, 0, 4, Blocks.BARREL.defaultBlockState());
        set(level, o, -10, 0, 5, Blocks.BARREL.defaultBlockState());
        set(level, o, -9, 0, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
    }

    /** Ворота замка: две башни и арка. */
    private static void gate(ServerLevel level, BlockPos o) {
        int z0 = -17, z1 = -15;
        for (int x = -5; x <= 5; x++) for (int z = z0; z <= z1; z++) {
            boolean tower = x <= -3 || x >= 3;
            int top = tower ? 7 : 5;
            for (int y = 0; y < top; y++) {
                boolean arch = !tower && y < 3;
                if (!arch) set(level, o, x, y, z, (y + x + z) % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
            // зубцы
            if ((x + z) % 2 == 0) set(level, o, x, top, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        set(level, o, -2, 2, -14, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, o, 2, 2, -14, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
    }

    /** Жилой дом: фахверк, дверь, окна, двускатная крыша. */
    private static void house(ServerLevel level, BlockPos o, int x0, int z0, int w, int d, Block wall, Direction door) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1, h = 4;
        walls(level, o, x0, x1, z0, z1, h, Blocks.COBBLESTONE, wall, Blocks.OAK_LOG);
        gableRoof(level, o, x0, x1, z0, z1, h, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS);
        int dz = (z0 + z1) / 2;
        int dx = door == Direction.EAST ? x1 : x0;
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(level, o, dx, 0, dz, lower);
        set(level, o, dx, 1, dz, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, o, x0 + w / 2, 1, z0, Blocks.GLASS_PANE.defaultBlockState());
        set(level, o, x0 + w / 2, 1, z1, Blocks.GLASS_PANE.defaultBlockState());
        set(level, o, x0 + 1, 0, z0 + 1, Blocks.RED_BED.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            .setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        set(level, o, x0 + 1, 0, z0 + 2, Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(level, o, x0 + 1, 0, z1 - 1, Blocks.CHEST.defaultBlockState());
        set(level, o, x1 - 1, 0, z0 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private static void decor(ServerLevel level, BlockPos o) {
        // колодец на перекрёстке
        for (int x = -4; x <= -2; x++) for (int z = 4; z <= 6; z++) {
            boolean centre = x == -3 && z == 5;
            set(level, o, x, 0, z, centre ? Blocks.WATER.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState());
        }
        // забор огорода и сено
        for (int x = 10; x <= 15; x++) {
            set(level, o, x, 0, -6, Blocks.OAK_FENCE.defaultBlockState());
            set(level, o, x, 0, -1, Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int x = 11; x <= 14; x++) for (int z = -5; z <= -2; z++) {
            set(level, o, x, -1, z, Blocks.FARMLAND.defaultBlockState());
            set(level, o, x, 0, z, Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
        }
        set(level, o, 3, 0, 10, Blocks.HAY_BLOCK.defaultBlockState());
        set(level, o, 2, 0, 11, Blocks.HAY_BLOCK.defaultBlockState());
    }

    // ------------------------------------------------------------------ помощники

    /** Стены с каменным цоколем, угловыми столбами и окнами. */
    private static void walls(ServerLevel level, BlockPos o, int x0, int x1, int z0, int z1, int h, Block base, Block wall, Block post) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            set(level, o, x, -1, z, base.defaultBlockState());
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (!edge) continue;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int y = 0; y < h; y++) {
                Block b = corner ? post : y == 0 ? base : wall;
                set(level, o, x, y, z, b.defaultBlockState());
            }
        }
    }

    /** Двускатная крыша вдоль оси X со свесами; фронтоны — доски. */
    private static void gableRoof(ServerLevel level, BlockPos o, int x0, int x1, int z0, int z1, int h, Block stairs, Block planks) {
        int north = z0 - 1, south = z1 + 1;
        for (int i = 0; north + i <= south - i; i++) {
            int y = h - 1 + i;
            int zn = north + i, zs = south - i;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (zn == zs) {
                    set(level, o, x, y, zn, planks.defaultBlockState());
                } else {
                    set(level, o, x, y, zn, stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                    set(level, o, x, y, zs, stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                }
            }
            // фронтоны
            for (int z = zn + 1; z < zs; z++) {
                set(level, o, x0, y, z, planks.defaultBlockState());
                set(level, o, x1, y, z, planks.defaultBlockState());
            }
        }
    }

    private static void npc(ServerLevel level, BlockPos o, String id, int x, int y, int z, float yaw) {
        KcdNpc npc = KcdEntities.NPC.get().create(level);
        if (npc == null) return;
        BlockPos p = o.offset(x, y, z);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setNpcId(id);
        level.addFreshEntity(npc);
    }

    private static void set(ServerLevel level, BlockPos o, int x, int y, int z, BlockState state) {
        level.setBlock(o.offset(x, y, z), state, Block.UPDATE_CLIENTS);
    }
}
