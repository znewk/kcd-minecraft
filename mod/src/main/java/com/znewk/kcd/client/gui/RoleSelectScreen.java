package com.znewk.kcd.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.network.PartyPayloads;
import com.znewk.kcd.party.Member;

/** «Кто ты?» — выбор роли при первом входе в прохождение: Индржих или его брат. */
public class RoleSelectScreen extends Screen {
    private final String henryTakenBy;
    private final String error;

    private boolean henry;
    private boolean brother;
    private int story;
    private EditBox nameBox;
    private KcdMenuButton confirm;
    private final KcdMenuButton[] storyButtons = new KcdMenuButton[Member.STORIES.length];

    private int cardW, cardH, cardY, henryX, brotherX;

    public RoleSelectScreen(String henryTakenBy, String error) {
        super(Component.translatable("kcd.role.title"));
        this.henryTakenBy = henryTakenBy;
        this.error = error;
    }

    private boolean henryFree() {
        return henryTakenBy.isEmpty();
    }

    @Override
    protected void init() {
        cardW = Math.min(170, (width - 60) / 2);
        cardH = 86;
        int total = cardW * 2 + 16;
        henryX = (width - total) / 2;
        brotherX = henryX + cardW + 16;
        int contentH = 30 + cardH + 76;
        cardY = Math.max(36, (height - contentH) / 2 + 20);

        String oldName = nameBox != null ? nameBox.getValue() : "";
        nameBox = new EditBox(font, width / 2 - 80, cardY + cardH + 12, 160, 16, Component.translatable("kcd.role.name"));
        nameBox.setMaxLength(Member.MAX_NAME);
        nameBox.setHint(Component.translatable("kcd.role.name.hint"));
        nameBox.setValue(oldName);
        nameBox.setResponder(s -> updateState());
        addRenderableWidget(nameBox);

        int sy = cardY + cardH + 32;
        for (int i = 0; i < storyButtons.length; i++) {
            int idx = i;
            int col = i % 2, row = i / 2;
            int cx = width / 2 + (col == 0 ? -80 : 80);
            storyButtons[i] = addRenderableWidget(new KcdMenuButton(cx, sy + row * 17,
                Component.translatable("kcd.role.story." + Member.STORIES[i]), () -> { story = idx; updateState(); }));
        }

        confirm = addRenderableWidget(new KcdMenuButton(width / 2, Math.min(height - 22, sy + 40),
            Component.translatable("kcd.role.confirm"), this::send));
        updateState();
    }

    private void updateState() {
        boolean showBrother = brother;
        nameBox.visible = showBrother;
        for (KcdMenuButton b : storyButtons) b.visible = showBrother;
        confirm.active = (henry && henryFree()) || (brother && Member.cleanName(nameBox.getValue()).length() >= 2);
    }

    private void send() {
        if (!confirm.active) return;
        PacketDistributor.sendToServer(new PartyPayloads.ChooseRole(henry, henry ? "" : nameBox.getValue(), story));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inside(mx, my, henryX, cardY) && henryFree()) {
            henry = true; brother = false; updateState();
            return true;
        }
        if (inside(mx, my, brotherX, cardY)) {
            brother = true; henry = false; updateState();
            setFocused(nameBox);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean inside(double mx, double my, int x, int y) {
        return mx >= x && mx < x + cardW && my >= y && my < y + cardH;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KcdUi.title(g, font, title, width / 2, cardY - 34, KcdUi.GOLD_LIGHT);
        g.drawCenteredString(font, Component.translatable("kcd.role.subtitle"), width / 2, cardY - 14, KcdUi.TEXT_LIGHT);

        card(g, henryX, Component.translatable("kcd.role.henry"), Component.translatable("kcd.role.henry.desc"),
            henry, inside(mouseX, mouseY, henryX, cardY), !henryFree());
        card(g, brotherX, Component.translatable("kcd.role.brother"), Component.translatable("kcd.role.brother.desc"),
            brother, inside(mouseX, mouseY, brotherX, cardY), false);

        if (brother) {
            // подсветка выбранной предыстории
            KcdMenuButton b = storyButtons[story];
            KcdUi.goldLine(g, b.getX() + 6, b.getY() + b.getHeight() - 1, b.getWidth() - 12);
            g.drawCenteredString(font, Component.translatable("kcd.role.story.title"), width / 2, cardY + cardH + 32 - 11, KcdUi.GOLD);
        }
        if (!error.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(error), width / 2, cardY + cardH + 2, 0xFFFF6A5A);
        }
    }

    private void card(GuiGraphics g, int x, Component name, Component desc, boolean selected, boolean hover, boolean taken) {
        KcdUi.parchment(g, x, cardY, cardW, cardH, selected || hover && !taken);
        KcdUi.ribbon(g, font, x + cardW / 2, cardY - 6, name);
        KcdUi.wrapped(g, font, desc, x + 8, cardY + 14, cardW - 16, taken ? KcdUi.INK_FADED : KcdUi.INK, true);
        if (taken) {
            g.fill(x + 2, cardY + cardH - 18, x + cardW - 2, cardY + cardH - 4, 0xCC3A0A0C);
            Component t = Component.translatable("kcd.role.taken", henryTakenBy);
            g.drawCenteredString(font, t, x + cardW / 2, cardY + cardH - 15, KcdUi.TEXT_LIGHT);
        } else if (selected) {
            Component t = Component.translatable("kcd.role.selected");
            g.drawString(font, t, x + (cardW - font.width(t)) / 2, cardY + cardH - 14, 0xFF6A4A10, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, 0xC0100806, 0xE0100806);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
