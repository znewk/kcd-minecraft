package com.znewk.kcd.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** «Присоединиться к отряду»: адрес от хоста (playit), запоминается для следующего раза. */
public class PartyJoinScreen extends Screen {
    private final Screen parent;
    private EditBox address;
    private KcdMenuButton join;

    public PartyJoinScreen(Screen parent) {
        super(Component.translatable("kcd.party.join"));
        this.parent = parent;
    }

    private Path saved() {
        return minecraft.gameDirectory.toPath().resolve("kcd").resolve("last_server.txt");
    }

    @Override
    protected void init() {
        String old = address != null ? address.getValue() : load();
        address = new EditBox(font, width / 2 - 110, height / 2 - 10, 220, 18, Component.translatable("kcd.party.address"));
        address.setMaxLength(128);
        address.setHint(Component.translatable("kcd.party.address.hint"));
        address.setValue(old);
        address.setResponder(s -> join.active = ServerAddress.isValidAddress(s.strip()));
        addRenderableWidget(address);
        setInitialFocus(address);
        join = addRenderableWidget(new KcdMenuButton(width / 2, height / 2 + 20, Component.translatable("kcd.party.connect"), this::connect));
        join.active = ServerAddress.isValidAddress(old.strip());
        addRenderableWidget(new KcdMenuButton(width / 2, height / 2 + 44, Component.translatable("gui.back"), this::onClose));
    }

    private String load() {
        try {
            return Files.exists(saved()) ? Files.readString(saved()).strip() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private void connect() {
        String addr = address.getValue().strip();
        if (!ServerAddress.isValidAddress(addr)) return;
        try {
            Files.createDirectories(saved().getParent());
            Files.writeString(saved(), addr);
        } catch (IOException ignored) {
        }
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(addr),
            new ServerData("KCD", addr, ServerData.Type.OTHER), false, null);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && join.active) {
            connect();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KcdUi.title(g, font, title, width / 2, height / 2 - 70, KcdUi.GOLD_LIGHT);
        KcdUi.wrapped(g, font, Component.translatable("kcd.party.join.hint"), width / 2 - 150, height / 2 - 44, 300, KcdUi.TEXT_LIGHT, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        g.fillGradient(0, 0, width, height, 0x90100806, 0xD0100806);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
