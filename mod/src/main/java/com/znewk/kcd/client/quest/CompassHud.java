package com.znewk.kcd.client.quest;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.znewk.kcd.client.gui.KcdUi;
import com.znewk.kcd.network.QuestPayloads;

/**
 * Компас вверху экрана, как в KCD2 (миникарты нет): лента-пергамент в золотой рамке, стороны света,
 * метки-щиты целей отслеживаемых заданий. Цель вне обзора — щит прижат к краю.
 * Видно 180°; север — красная «С».
 */
public final class CompassHud {
    private static final int W = 200, H = 14;
    private static final float FOV = 180F;
    private static final String[] CARDINALS = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};

    private CompassHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, x0 = cx - W / 2, y0 = 4;
        float heading = Mth.wrapDegrees(mc.player.getViewYRot(delta.getGameTimeDeltaPartialTick(true)) + 180F); // 0 — север
        float px = W / FOV;

        // лента
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, KcdUi.OUTLINE);
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, KcdUi.GOLD);
        g.fill(x0, y0, x0 + W, y0 + H, 0xE6EADFC8);
        g.fill(x0, y0 + H - 2, x0 + W, y0 + H, 0xE6D6C6A4);

        g.enableScissor(x0, y0, x0 + W, y0 + H);
        // риски каждые 15°, буквы каждые 45°
        for (int deg = 0; deg < 360; deg += 15) {
            float d = Mth.wrapDegrees(deg - heading);
            if (Math.abs(d) > FOV / 2 + 10) continue;
            int x = Math.round(cx + d * px);
            if (deg % 45 == 0) {
                String s = CARDINALS[deg / 45];
                int color = deg == 0 ? KcdUi.RED : deg % 90 == 0 ? KcdUi.INK : KcdUi.INK_FADED;
                g.drawString(font, s, x - font.width(s) / 2, y0 + 3, color, false);
            } else {
                g.fill(x, y0 + 4, x + 1, y0 + H - 4, 0x807A6A50);
            }
        }
        g.disableScissor();

        // метки целей отслеживаемых заданий
        Vec3 me = mc.player.position();
        for (String id : ClientQuests.tracked()) {
            QuestPayloads.Quest q = ClientQuests.get(id);
            if (q == null || q.status() != 0) continue;
            for (QuestPayloads.Objective o : q.objectives()) {
                if (o.done() || !o.hasPos()) continue;
                double dx = o.x() - me.x, dz = o.z() - me.z;
                if (dx * dx + dz * dz < 9) continue; // уже на месте
                float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
                float d = Mth.wrapDegrees(bearing - heading);
                boolean edge = Math.abs(d) > FOV / 2;
                int x = Math.round(cx + Mth.clamp(d, -FOV / 2, FOV / 2) * px);
                shield(g, x, y0 + H - 1, q.main(), edge);
            }
        }
        // центр
        g.fill(cx, y0 - 2, cx + 1, y0 + 2, KcdUi.OUTLINE);
    }

    /** Маленький щит: золотая кайма, внутри красный (основное задание) или синий (побочное). */
    private static void shield(GuiGraphics g, int x, int y, boolean main, boolean edge) {
        int fill = main ? KcdUi.RED : 0xFF2E5A9A;
        int a = edge ? 0x99 : 0xFF;
        int gold = (a << 24) | (KcdUi.GOLD & 0xFFFFFF);
        int in = (a << 24) | (fill & 0xFFFFFF);
        int out = (a << 24) | (KcdUi.OUTLINE & 0xFFFFFF);
        g.fill(x - 4, y - 1, x + 5, y + 6, out);
        g.fill(x - 3, y + 6, x + 4, y + 8, out);
        g.fill(x - 1, y + 8, x + 2, y + 9, out);
        g.fill(x - 3, y, x + 4, y + 5, gold);
        g.fill(x - 2, y + 5, x + 3, y + 7, gold);
        g.fill(x - 2, y + 1, x + 3, y + 5, in);
        g.fill(x - 1, y + 5, x + 2, y + 6, in);
    }
}
