package com.znewk.kcd.client.quest;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import com.znewk.kcd.client.gui.KcdUi;
import com.znewk.kcd.network.QuestPayloads;

/**
 * Поверх экрана: плашка «Новое задание / Задание обновлено / Выполнено» сверху по центру (как в KCD2)
 * и отслеживаемые задания с текущими целями справа. Пока интерфейс скрыт (разговор), плашки ждут.
 */
public final class QuestHud {
    private static final int SHOW_TICKS = 100;
    private static final int FADE = 10;

    private static QuestPayloads.Notice current;
    private static float age;

    private QuestHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        notice(g, font, delta.getRealtimeDeltaTicks());
        tracker(g, font);
    }

    private static void notice(GuiGraphics g, Font font, float dt) {
        if (current == null) {
            current = ClientQuests.NOTICES.poll();
            age = 0;
            if (current == null) return;
        }
        age += dt;
        if (age > SHOW_TICKS) {
            current = null;
            return;
        }
        float a = Mth.clamp(Math.min(age, SHOW_TICKS - age) / FADE, 0F, 1F);
        int alpha = Math.max(4, (int) (a * 255)) << 24;
        int cx = g.guiWidth() / 2, y = 36; // под компасом

        Component caption = Component.translatable("kcd.quest.notice." + current.kind());
        Component title = Component.literal(current.title());
        int w = Math.max(font.width(title) * 3 / 2, font.width(current.detail())) + 60;
        // тёмная полоса между двумя золотыми линиями
        int band = (int) (a * 0xB0) << 24;
        g.fill(cx - w / 2, y - 4, cx + w / 2, y + 38, band);
        KcdUi.goldLine(g, cx - w / 2 + 10, y - 4, w - 20);
        KcdUi.goldLine(g, cx - w / 2 + 10, y + 37, w - 20);

        int capColor = (current.kind() == 3 ? 0xE05A4A : KcdUi.GOLD & 0xFFFFFF) | alpha;
        g.drawCenteredString(font, caption, cx, y, capColor);
        g.pose().pushPose();
        g.pose().translate(cx, y + 12, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawString(font, title, -font.width(title) / 2, 0, (KcdUi.TEXT_LIGHT & 0xFFFFFF) | alpha, true);
        g.pose().popPose();
        if (!current.detail().isEmpty()) {
            g.drawCenteredString(font, Component.literal("✔ " + current.detail()), cx, y + 27, 0xC8BCA4 | alpha);
        }
    }

    private static void tracker(GuiGraphics g, Font font) {
        int right = g.guiWidth() - 8;
        int y = 80; // ниже компаса и плашки «Новое задание»
        int maxW = 170;
        for (String id : ClientQuests.tracked()) {
            QuestPayloads.Quest q = ClientQuests.get(id);
            if (q == null) continue;
            Component title = Component.literal(q.title());
            g.drawString(font, title, right - font.width(title), y, KcdUi.GOLD_LIGHT, true);
            y += 11;
            for (QuestPayloads.Objective o : q.objectives()) {
                if (o.done()) continue;
                List<FormattedCharSequence> lines = font.split(Component.literal(o.text()), maxW);
                for (int i = 0; i < lines.size(); i++) {
                    FormattedCharSequence l = lines.get(i);
                    g.drawString(font, l, right - font.width(l), y, KcdUi.TEXT_LIGHT, true);
                    if (i == 0) g.fill(right - font.width(l) - 6, y + 3, right - font.width(l) - 3, y + 6, KcdUi.GOLD);
                    y += 10;
                }
            }
            y += 6;
        }
    }
}
