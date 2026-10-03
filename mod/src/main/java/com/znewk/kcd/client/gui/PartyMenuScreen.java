package com.znewk.kcd.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import com.znewk.kcd.client.NewPlaythrough;
import com.znewk.kcd.client.host.HostSession;

/** «Играть с отрядом»: собрать свой отряд (хост) или присоединиться к другу. */
public class PartyMenuScreen extends Screen {
    private final Screen parent;

    public PartyMenuScreen(Screen parent) {
        super(Component.translatable("kcd.menu.party"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 10;
        addRenderableWidget(new KcdMenuButton(cx, y, Component.translatable("kcd.party.new"), () -> NewPlaythrough.start(true)));
        addRenderableWidget(new KcdMenuButton(cx, y + 20, Component.translatable("kcd.party.host"), () -> {
            HostSession.requestHost();
            minecraft.setScreen(new SelectWorldScreen(this));
        }));
        addRenderableWidget(new KcdMenuButton(cx, y + 40, Component.translatable("kcd.party.join"),
            () -> minecraft.setScreen(new PartyJoinScreen(this))));
        addRenderableWidget(new KcdMenuButton(cx, y + 76, Component.translatable("gui.back"), this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KcdUi.title(g, font, title, width / 2, height / 2 - 70, KcdUi.GOLD_LIGHT);
        KcdUi.wrapped(g, font, Component.translatable("kcd.party.hint"), width / 2 - 150, height / 2 - 44, 300, KcdUi.TEXT_LIGHT, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        g.fillGradient(0, 0, width, height, 0x90100806, 0xD0100806);
    }

    @Override
    public void onClose() {
        HostSession.cancelRequest();
        minecraft.setScreen(parent);
    }
}
