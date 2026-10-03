package com.znewk.kcd.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.network.DialoguePayloads;

/**
 * Разговор с жителем в духе KCD2: внизу — реплика жителя субтитрами, под ней — варианты ответа;
 * выбранный вариант лежит на золотом свитке, у проверок — метка вида проверки. Камера плавно
 * поворачивается к собеседнику и чуть приближает его. Зрители (остальной отряд) видят то же
 * и могут подсказать вариант — подсказка видна у отвечающего.
 */
public class DialogueScreen extends Screen {
    private static final ResourceLocation HIGHLIGHT = ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "menu/highlight");
    private static final int TEXT_ACTIVE = 0xFF2A1A0A;
    private static final int TEXT_SEEN = 0xFF9A9080;
    private static final int TEXT_PREV = 0xFFB8AC94;
    private static final int FAIL = 0xFFE05A4A;
    /** Расстояние, на котором собеседник виден без приближения; ближе — камера отъезжает, дальше — приближает. */
    private static final float FRAME_DISTANCE = 2.2F;
    private static final float ZOOM_MIN = 0.8F;
    private static final float ZOOM_MAX = 1.25F;

    private static float zoom = 1F;

    private DialoguePayloads.View view;
    private int selected;
    private int hovered = -1;
    private boolean waiting;
    private boolean closedByServer;
    private boolean hidGui;
    private boolean prevHideGui;
    private final List<int[]> rows = new ArrayList<>();

    // автотест: -Dkcd.autopick=2,1 — выбирать варианты сами (по номеру), раз в 3 секунды
    private final String[] autoPick = System.getProperty("kcd.autopick", "").isEmpty() ? new String[0] : System.getProperty("kcd.autopick").split(",");
    private static int autoPickStep;
    private int autoPickTimer;

    public DialogueScreen(DialoguePayloads.View view) {
        super(Component.literal(view.npcName()));
        this.view = view;
    }

    public int npcId() {
        return view.npcId();
    }

    public void update(DialoguePayloads.View v) {
        view = v;
        waiting = false;
        selected = Mth.clamp(selected, 0, Math.max(0, v.options().size() - 1));
        if (!v.prevLine().isEmpty()) selected = 0;
    }

    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    protected void init() {
        // во время разговора интерфейс прячем, как в KCD
        if (!hidGui) {
            hidGui = true;
            prevHideGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
        }
    }

    @Override
    public void removed() {
        if (hidGui) minecraft.options.hideGui = prevHideGui;
        if (!closedByServer) PacketDistributor.sendToServer(new DialoguePayloads.Leave(view.npcId()));
    }

    // ------------------------------------------------------------------ выбор

    private void choose(int index) {
        if (waiting || index < 0 || index >= view.options().size()) return;
        selected = index;
        if (view.canChoose()) waiting = true;
        PacketDistributor.sendToServer(new DialoguePayloads.Choose(view.npcId(), index));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        int n = view.options().size();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            selected = (selected + n - 1) % n;
            hovered = -1;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            selected = (selected + 1) % n;
            hovered = -1;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_E) {
            choose(selected);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0 && button == 0) {
            choose(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int i = rowAt(mx, my);
        if (i >= 0) {
            hovered = i;
            selected = i;
        }
    }

    private int rowAt(double mx, double my) {
        for (int i = 0; i < rows.size(); i++) {
            int[] r = rows.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return i;
        }
        return -1;
    }

    @Override
    public void tick() {
        if (autoPickStep < autoPick.length && view.canChoose() && !waiting && ++autoPickTimer >= 60) {
            autoPickTimer = 0;
            choose(Integer.parseInt(autoPick[autoPickStep++].trim()) - 1);
        }
    }

    // ------------------------------------------------------------------ рисование

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // мир остаётся видимым, снизу — затемнение под субтитры
        g.fillGradient(0, height * 2 / 5, width, height, 0x00000000, 0xD0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        lookAtNpc();
        super.render(g, mouseX, mouseY, partialTick);

        int panelW = Math.min(380, width - 40);
        int panelX = (width - panelW) / 2;
        int textW = panelW - 26;

        // варианты — снизу вверх считаем высоту
        List<List<FormattedCharSequence>> optLines = new ArrayList<>();
        int panelH = 6;
        for (int i = 0; i < view.options().size(); i++) {
            List<FormattedCharSequence> lines = font.split(optionText(i, i == selected && view.canChoose()), textW);
            optLines.add(lines);
            panelH += lines.size() * 10 + 4;
        }
        int panelY = height - panelH - 12;
        KcdUi.slate(g, panelX, panelY, panelW, panelH);

        rows.clear();
        int y = panelY + 3;
        for (int i = 0; i < optLines.size(); i++) {
            List<FormattedCharSequence> lines = optLines.get(i);
            int rowH = lines.size() * 10 + 4;
            rows.add(new int[]{panelX, y, panelW, rowH});
            DialoguePayloads.Opt opt = view.options().get(i);
            boolean lit = i == selected && !waiting;
            if (lit) g.blitSprite(HIGHLIGHT, panelX + 2, y, panelW - 4, rowH);
            int color = lit ? TEXT_ACTIVE : opt.seen() ? TEXT_SEEN : KcdUi.TEXT_LIGHT;
            g.drawString(font, (i + 1) + ".", panelX + 8, y + 2, lit ? TEXT_ACTIVE : KcdUi.GOLD, !lit);
            for (int l = 0; l < lines.size(); l++) {
                g.drawString(font, lines.get(l), panelX + 22, y + 2 + l * 10, color, !lit);
            }
            if (!opt.suggestedBy().isEmpty()) {
                Component s = Component.translatable("kcd.dialogue.suggested", opt.suggestedBy());
                g.drawString(font, s, panelX + panelW + 6, y + 2, KcdUi.GOLD_LIGHT, true);
            }
            y += rowH;
        }

        // кто отвечает
        int top = panelY - 6;
        if (!view.canChoose()) {
            Component who = Component.translatable("kcd.dialogue.decider", view.decider());
            g.drawCenteredString(font, who, width / 2, top - 10, TEXT_PREV);
            top -= 14;
        } else if (view.story()) {
            KcdUi.ribbon(g, font, width / 2, top - 14, Component.translatable("kcd.dialogue.story"));
            top -= 20;
        }

        // реплика жителя — субтитрами
        int subW = Math.min(360, width - 60);
        List<FormattedCharSequence> npcLines = new ArrayList<>();
        for (String para : view.text().split("\n")) npcLines.addAll(font.split(Component.literal(para), subW));
        int ty = top - 4 - npcLines.size() * 11;
        for (int l = 0; l < npcLines.size(); l++) {
            FormattedCharSequence line = npcLines.get(l);
            g.drawString(font, line, (width - font.width(line)) / 2, ty + l * 11, KcdUi.TEXT_LIGHT, true);
        }
        g.drawCenteredString(font, Component.literal(view.npcName()), width / 2, ty - 13, KcdUi.GOLD_LIGHT);
        ty -= 13;

        // итог проверки
        if (view.notice() != 0) {
            Component kind = Component.translatable("kcd.check." + view.noticeKind());
            Component n = Component.translatable(view.notice() == 1 ? "kcd.check.success" : "kcd.check.fail", kind);
            ty -= 14;
            g.drawCenteredString(font, n, width / 2, ty, view.notice() == 1 ? KcdUi.GOLD_LIGHT : FAIL);
        }

        // предыдущая реплика отряда
        if (!view.prevLine().isEmpty()) {
            Component prev = Component.literal(view.prevSpeaker() + ": ").withStyle(Style.EMPTY.withColor(KcdUi.GOLD))
                .append(Component.literal(view.prevLine()).withStyle(Style.EMPTY.withColor(TEXT_PREV)));
            List<FormattedCharSequence> pl = font.split(prev, subW);
            ty -= 6 + pl.size() * 10;
            for (int l = 0; l < pl.size(); l++) {
                g.drawString(font, pl.get(l), (width - font.width(pl.get(l))) / 2, ty + l * 10, TEXT_PREV, true);
            }
        }
    }

    private Component optionText(int i, boolean lit) {
        DialoguePayloads.Opt opt = view.options().get(i);
        String text = opt.text();
        MutableComponent body = text.startsWith("@") ? Component.translatable(text.substring(1)) : Component.literal(text);
        if (opt.check().isEmpty()) return body;
        MutableComponent tag = Component.literal("[").append(Component.translatable("kcd.check." + opt.check())).append("] ")
            .withStyle(Style.EMPTY.withColor(lit ? KcdUi.RED_DARK : KcdUi.GOLD));
        return tag.append(body.withStyle(Style.EMPTY));
    }

    /** Плавно повернуть взгляд к лицу собеседника. */
    private void lookAtNpc() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity npc = mc.level.getEntity(view.npcId());
        if (npc == null) return;
        Vec3 eye = mc.player.getEyePosition();
        // смотрим чуть ниже глаз — лицо собеседника выше субтитров, как в KCD2
        Vec3 d = npc.getEyePosition().subtract(0, 0.45, 0).subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        float k = 1F - (float) Math.pow(0.8, mc.getTimer().getRealtimeDeltaTicks() * 2);
        float ny = Mth.rotLerp(k, mc.player.getYRot(), yaw);
        float np = Mth.lerp(k, mc.player.getXRot(), pitch);
        mc.player.setYRot(ny);
        mc.player.yRotO = ny;
        mc.player.setYHeadRot(ny);
        mc.player.setXRot(np);
        mc.player.xRotO = np;
    }

    /** Пока открыт разговор, камера держит собеседника в кадре одного размера: издали приближает, вблизи отъезжает. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        float target = 1F;
        if (mc.screen instanceof DialogueScreen ds && mc.level != null && mc.player != null) {
            Entity npc = mc.level.getEntity(ds.npcId());
            if (npc != null) target = Mth.clamp(FRAME_DISTANCE / Math.max(0.5F, mc.player.distanceTo(npc)), ZOOM_MIN, ZOOM_MAX);
        }
        float k = 1F - (float) Math.pow(0.85, mc.getTimer().getRealtimeDeltaTicks() * 2);
        zoom = Math.abs(target - zoom) < 0.002F ? target : Mth.lerp(k, zoom, target);
        if (zoom != 1F) event.setFOV(event.getFOV() * zoom);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
