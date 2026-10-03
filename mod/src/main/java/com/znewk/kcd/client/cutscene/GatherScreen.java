package com.znewk.kcd.client.cutscene;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.client.gui.KcdMenuButton;
import com.znewk.kcd.client.gui.KcdUi;
import com.znewk.kcd.network.CutscenePayloads;

/** «Сюжетная сцена — отряд собирается. Готов?» Сцена начнётся, когда готовы все или выйдет время. */
public class GatherScreen extends Screen {
    private CutscenePayloads.Gather state;
    private boolean ready;
    private KcdMenuButton button;

    public GatherScreen(CutscenePayloads.Gather state) {
        super(Component.translatable("kcd.cutscene.gather"));
        this.state = state;
    }

    public void update(CutscenePayloads.Gather g) {
        state = g;
    }

    public void markReady() {
        ready = true;
        if (button != null) button.active = false;
    }

    @Override
    protected void init() {
        button = addRenderableWidget(new KcdMenuButton(width / 2, height / 2 + 30, Component.translatable("kcd.cutscene.ready"), () -> {
            PacketDistributor.sendToServer(new CutscenePayloads.Action(0));
            markReady();
        }));
        button.active = !ready;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2, y = height / 2 - 40;
        KcdUi.ribbon(g, font, cx, y, title);
        KcdUi.title(g, font, Component.literal(state.title()), cx, y + 20, KcdUi.GOLD_LIGHT);
        Component info = Component.translatable("kcd.cutscene.waiting", state.ready(), state.total(), state.seconds());
        g.drawCenteredString(font, info, cx, y + 46, KcdUi.TEXT_LIGHT);
        if (ready) g.drawCenteredString(font, Component.translatable("kcd.cutscene.you_ready"), cx, y + 92, KcdUi.GOLD);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xA0100806, 0xD0100806);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
