package com.znewk.kcd.client.gui;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.ModListScreen;

import com.znewk.kcd.KcdMod;

/**
 * Главное меню в стиле KCD2: живая панорама, логотип и колонка пунктов справа,
 * подсказка между золотыми линиями слева.
 */
public class KcdTitleScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "textures/gui/title/logo.png");
    private static final int LOGO_W = 571, LOGO_H = 121;
    private static final ResourceLocation SHADE = ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "textures/gui/title/menu_shade.png");
    private static final int TIP_COUNT = 10;
    private static final long TIP_PERIOD_MS = 15_000L;
    private static final long FADE_MS = 1_500L;
    private static final int GOLD = 0xC9A24A;
    private static final int PARCHMENT = 0xEADFC8;

    private int logoX, logoY, logoW, logoH, columnX;
    private long openedAt;
    private int tipOffset;

    public KcdTitleScreen() {
        super(Component.translatable("kcd.menu.title"));
    }

    @Override
    protected void init() {
        if (openedAt == 0L) {
            openedAt = Util.getMillis();
            tipOffset = (int) (Math.random() * TIP_COUNT);
        }
        logoW = Math.min((int) (width * 0.46F), 300);
        logoH = logoW * LOGO_H / LOGO_W;
        logoX = width - logoW - 16;
        logoY = 14;
        columnX = logoX + logoW / 2;

        int y = logoY + logoH + 26;
        int step = 20;
        addRenderableWidget(new KcdMenuButton(columnX, y, Component.translatable("kcd.menu.party"),
            () -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new KcdMenuButton(columnX, y += step, Component.translatable("kcd.menu.singleplayer"),
            () -> minecraft.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new KcdMenuButton(columnX, y += step, Component.translatable("kcd.menu.options"),
            () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new KcdMenuButton(columnX, y += step, Component.translatable("kcd.menu.mods"),
            () -> minecraft.setScreen(new ModListScreen(this))));
        addRenderableWidget(new KcdMenuButton(columnX, y + step * 2, Component.translatable("kcd.menu.quit"),
            () -> minecraft.stop()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((Util.getMillis() - openedAt) / (float) FADE_MS, 0F, 1F);
        super.render(g, mouseX, mouseY, partialTick);

        RenderSystem.enableBlend();
        g.setColor(1F, 1F, 1F, fade);
        g.blit(LOGO, logoX, logoY, logoW, logoH, 0F, 0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        int alpha = Math.max(4, (int) (fade * 255)) << 24;
        g.drawCenteredString(font, Component.translatable("kcd.menu.subtitle"), columnX, logoY + logoH + 4, GOLD | alpha);

        renderTip(g, alpha);

        g.drawString(font, Component.translatable("kcd.menu.copyright"), 6, height - 10, 0x9A8F7A | alpha, true);
        String version = "KCD by znewk · v" + KcdMod.version();
        g.drawString(font, version, width - font.width(version) - 6, height - 10, 0x9A8F7A | alpha, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        // тень под колонкой меню справа
        RenderSystem.enableBlend();
        int shadeX = (int) (width * 0.42F);
        g.blit(SHADE, shadeX, 0, width - shadeX, height, 0F, 0F, 64, 4, 64, 4);
        RenderSystem.disableBlend();
    }

    private void renderTip(GuiGraphics g, int alpha) {
        int index = (int) ((tipOffset + (Util.getMillis() - openedAt) / TIP_PERIOD_MS) % TIP_COUNT) + 1;
        Component tip = Component.translatable("kcd.tip." + index).withStyle(ChatFormatting.ITALIC);
        int boxW = Math.min((int) (width * 0.36F), 260);
        int left = 24;
        List<FormattedCharSequence> lines = font.split(tip, boxW);
        int textH = lines.size() * 11;
        int top = (int) (height * 0.58F) - textH / 2;
        line(g, left + 10, top - 7, boxW - 20, alpha);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence l = lines.get(i);
            g.drawString(font, l, left + (boxW - font.width(l)) / 2, top + i * 11, PARCHMENT | alpha, true);
        }
        line(g, left + 10, top + textH + 3, boxW - 20, alpha);
    }

    private static void line(GuiGraphics g, int x, int y, int w, int alpha) {
        g.fill(x, y + 1, x + w, y + 2, 0x000000 | (alpha >>> 1 & 0xFF000000));
        g.fill(x, y, x + w, y + 1, GOLD | alpha);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
