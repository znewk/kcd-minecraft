package com.znewk.kcd.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Общие элементы интерфейса KCD: пергаментные панели, ленты, золотые линии. */
public final class KcdUi {
    public static final int OUTLINE = 0xFF1B120C;
    public static final int PARCHMENT = 0xFFEADFC8;
    public static final int PARCHMENT_DARK = 0xFFD6C6A4;
    public static final int GOLD = 0xFFC9A24A;
    public static final int GOLD_LIGHT = 0xFFF2D27A;
    public static final int RED = 0xFFB3262A;
    public static final int RED_DARK = 0xFF7E1518;
    public static final int INK = 0xFF3A2A14;
    public static final int INK_FADED = 0xFF7A6A50;
    public static final int TEXT_LIGHT = 0xFFF2E8D5;
    public static final int SLATE = 0xE0222830;

    private KcdUi() {}

    /** Пергаментная панель с тёмной обводкой и золотой каймой. */
    public static void parchment(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE);
        g.fill(x, y, x + w, y + h, selected ? GOLD_LIGHT : GOLD);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PARCHMENT);
        g.fill(x + 2, y + h - 4, x + w - 2, y + h - 2, PARCHMENT_DARK);
        // лёгкая тень
        g.fill(x + 1, y + h + 1, x + w + 2, y + h + 3, 0x66000000);
    }

    /** Тёмная панель (как в KCD2 под характеристиками). */
    public static void slate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE);
        g.fill(x, y, x + w, y + h, SLATE);
        g.fill(x, y, x + w, y + 1, GOLD);
    }

    /** Красная лента-заголовок с текстом по центру. */
    public static void ribbon(GuiGraphics g, Font font, int cx, int y, Component text) {
        int w = font.width(text) + 16, x = cx - w / 2;
        g.fill(x - 4, y + 3, x + 2, y + 13, RED_DARK);
        g.fill(x + w - 2, y + 3, x + w + 4, y + 13, RED_DARK);
        g.fill(x - 1, y - 1, x + w + 1, y + 13, OUTLINE);
        g.fill(x, y, x + w, y + 12, RED);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + 11, x + w, y + 12, RED_DARK);
        g.drawString(font, text, cx - font.width(text) / 2, y + 2, TEXT_LIGHT, true);
    }

    /** Золотая линия с тенью. */
    public static void goldLine(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y + 1, x + w, y + 2, 0x80000000);
        g.fill(x, y, x + w, y + 1, GOLD);
    }

    /** Текст с переносом строк; возвращает высоту. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, boolean centered) {
        int line = 0;
        for (FormattedCharSequence l : font.split(text, width)) {
            int lx = centered ? x + (width - font.width(l)) / 2 : x;
            g.drawString(font, l, lx, y + line * 10, color, false);
            line++;
        }
        return line * 10;
    }

    /** Крупный заголовок (масштаб 2) с тенью. */
    public static void title(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2F, 2F, 1F);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }
}
