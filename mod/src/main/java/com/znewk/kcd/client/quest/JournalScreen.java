package com.znewk.kcd.client.quest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import com.znewk.kcd.client.KcdKeys;
import com.znewk.kcd.client.gui.KcdUi;
import com.znewk.kcd.network.QuestPayloads;

/**
 * Дневник в духе KCD2: слева — задания (основные, побочные, завершённые), справа — записи дневника
 * выбранного задания и его цели. Отслеживать можно до трёх заданий — они видны на экране справа.
 */
public class JournalScreen extends Screen {
    private static String selectedId;

    private int bx, by, bw, bh, leftW;
    private int scroll;
    private int contentH;
    private final List<Row> rows = new ArrayList<>();
    private int trackX, trackY, trackW;

    private record Row(String id, int y) {}

    public JournalScreen() {
        super(Component.translatable("kcd.journal.title"));
    }

    @Override
    protected void init() {
        bw = Math.min(460, width - 20);
        bh = Math.min(260, height - 30);
        bx = (width - bw) / 2;
        by = (height - bh) / 2 + 6;
        leftW = Math.min(160, bw * 2 / 5);
        if (selectedId == null || ClientQuests.get(selectedId) == null) {
            selectedId = ClientQuests.tracked().stream().findFirst()
                .orElse(ClientQuests.all().isEmpty() ? null : ClientQuests.all().get(0).id());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // книга: две страницы и корешок
        KcdUi.parchment(g, bx, by, bw, bh, false);
        g.fill(bx + leftW, by + 6, bx + leftW + 1, by + bh - 6, 0x40000000);
        g.fill(bx + leftW + 1, by + 6, bx + leftW + 2, by + bh - 6, 0x30FFFFFF);
        KcdUi.ribbon(g, font, width / 2, by - 8, title);

        renderList(g, mouseX, mouseY);
        renderPage(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ левая страница

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        rows.clear();
        int x = bx + 10, y = by + 12, w = leftW - 16;
        if (ClientQuests.all().isEmpty()) {
            KcdUi.wrapped(g, font, Component.translatable("kcd.journal.empty"), x, y, w, KcdUi.INK_FADED, false);
            return;
        }
        y = section(g, "kcd.journal.main", q -> q.status() == 0 && q.main(), x, y, w, mouseX, mouseY);
        y = section(g, "kcd.journal.side", q -> q.status() == 0 && !q.main(), x, y, w, mouseX, mouseY);
        section(g, "kcd.journal.finished", q -> q.status() != 0, x, y, w, mouseX, mouseY);
    }

    private int section(GuiGraphics g, String key, java.util.function.Predicate<QuestPayloads.Quest> filter,
                        int x, int y, int w, int mouseX, int mouseY) {
        List<QuestPayloads.Quest> list = ClientQuests.all().stream().filter(filter).toList();
        if (list.isEmpty()) return y;
        g.drawString(font, Component.translatable(key), x, y, KcdUi.RED_DARK, false);
        g.fill(x, y + 10, x + w, y + 11, KcdUi.GOLD);
        y += 15;
        for (QuestPayloads.Quest q : list) {
            if (y > by + bh - 14) break;
            boolean sel = q.id().equals(selectedId);
            boolean hover = mouseX >= x - 2 && mouseX < x + w && mouseY >= y - 2 && mouseY < y + 10;
            if (sel || hover) g.fill(x - 3, y - 2, x + w, y + 10, sel ? KcdUi.PARCHMENT_DARK : 0x30D6C6A4);
            if (ClientQuests.isTracked(q.id())) g.fill(x - 1, y + 2, x + 3, y + 6, KcdUi.RED);
            String title = font.plainSubstrByWidth(q.title(), w - 8);
            int color = q.status() == 0 ? KcdUi.INK : KcdUi.INK_FADED;
            g.drawString(font, title, x + 6, y, color, false);
            rows.add(new Row(q.id(), y));
            y += 12;
        }
        return y + 6;
    }

    // ------------------------------------------------------------------ правая страница

    private void renderPage(GuiGraphics g, int mouseX, int mouseY) {
        QuestPayloads.Quest q = selectedId == null ? null : ClientQuests.get(selectedId);
        int x = bx + leftW + 12, top = by + 10, w = bw - leftW - 22, bottom = by + bh - 24;
        trackW = 0;
        if (q == null) return;

        g.enableScissor(x - 2, top, x + w + 2, bottom);
        int y = top - scroll;
        // заголовок
        for (FormattedCharSequence l : font.split(Component.literal(q.title()).withStyle(ChatFormatting.BOLD), w)) {
            g.drawString(font, l, x, y, KcdUi.INK, false);
            y += 10;
        }
        if (q.status() != 0) {
            Component st = Component.translatable(q.status() == 1 ? "kcd.journal.status.done" : "kcd.journal.status.failed");
            g.drawString(font, st, x, y + 1, q.status() == 1 ? 0xFF8A6A10 : KcdUi.RED, false);
            y += 11;
        }
        y += 4;
        // записи дневника
        for (String entry : q.diary()) {
            y += KcdUi.wrapped(g, font, Component.literal(entry), x, y, w, KcdUi.INK, false) + 5;
        }
        // цели
        if (!q.objectives().isEmpty()) {
            g.fill(x, y, x + w, y + 1, KcdUi.GOLD);
            y += 5;
            for (QuestPayloads.Objective o : q.objectives()) {
                g.fill(x, y + 1, x + 7, y + 8, KcdUi.INK_FADED);
                g.fill(x + 1, y + 2, x + 6, y + 7, KcdUi.PARCHMENT);
                if (o.done()) g.fill(x + 2, y + 3, x + 5, y + 6, KcdUi.RED_DARK);
                MutableComponent t = Component.literal(o.text());
                if (o.done()) t.withStyle(ChatFormatting.STRIKETHROUGH);
                y += KcdUi.wrapped(g, font, t, x + 11, y, w - 11, o.done() ? KcdUi.INK_FADED : KcdUi.INK, false) + 3;
            }
        }
        contentH = y + scroll - top;
        g.disableScissor();

        // отслеживать
        if (q.status() == 0) {
            boolean on = ClientQuests.isTracked(q.id());
            Component label = Component.translatable(on ? "kcd.journal.untrack" : "kcd.journal.track");
            trackW = font.width(label) + 12;
            trackX = bx + bw - 12 - trackW;
            trackY = by + bh - 20;
            boolean hover = mouseX >= trackX && mouseX < trackX + trackW && mouseY >= trackY && mouseY < trackY + 12;
            g.fill(trackX, trackY, trackX + trackW, trackY + 12, hover ? KcdUi.GOLD_LIGHT : KcdUi.PARCHMENT_DARK);
            g.drawString(font, label, trackX + 6, trackY + 2, KcdUi.RED_DARK, false);
            if (!on && ClientQuests.tracked().size() >= ClientQuests.MAX_TRACKED) {
                Component full = Component.translatable("kcd.journal.track_full");
                g.drawString(font, full, x, trackY + 2, KcdUi.INK_FADED, false);
            }
        }
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Row r : rows) {
            if (mx >= bx + 6 && mx < bx + leftW - 4 && my >= r.y() - 2 && my < r.y() + 10) {
                selectedId = r.id();
                scroll = 0;
                return true;
            }
        }
        if (trackW > 0 && mx >= trackX && mx < trackX + trackW && my >= trackY && my < trackY + 12) {
            ClientQuests.track(selectedId, !ClientQuests.isTracked(selectedId));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int visible = bh - 34;
        scroll = Mth.clamp(scroll - (int) (sy * 12), 0, Math.max(0, contentH - visible));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (KcdKeys.JOURNAL.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
