package com.znewk.kcd.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.znewk.kcd.KcdMod;

/** Пункт меню в стиле KCD2: просто текст, а выбранный пункт лежит на золотом свитке. */
public class KcdMenuButton extends AbstractButton {
    private static final ResourceLocation HIGHLIGHT = ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "menu/highlight");
    private static final int TEXT = 0xF2E8D5;
    private static final int TEXT_ACTIVE = 0x2A1A0A;
    private static final int TEXT_DISABLED = 0x8A8070;

    private final Runnable onPress;

    public KcdMenuButton(int centerX, int y, Component label, Runnable onPress) {
        super(centerX - width(label) / 2, y, width(label), 16, label);
        this.onPress = onPress;
    }

    private static int width(Component label) {
        return Minecraft.getInstance().font.width(label) + 40;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean lit = this.active && this.isHoveredOrFocused();
        if (lit) g.blitSprite(HIGHLIGHT, getX(), getY(), getWidth(), getHeight());
        int color = !this.active ? TEXT_DISABLED : lit ? TEXT_ACTIVE : TEXT;
        int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        g.drawString(font, getMessage(), tx, ty, color, !lit);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
