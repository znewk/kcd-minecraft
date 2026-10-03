package com.znewk.kcd.client.cutscene;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.client.gui.KcdUi;
import com.znewk.kcd.network.CutscenePayloads;

/**
 * Кат-сцена: камера летит по ключам (плавно), чёрные полосы сверху и снизу, субтитры в нижней полосе.
 * Камера — невидимая стойка только на клиенте; игроки видят себя со стороны. Пробел — голос за пропуск.
 */
public class CutsceneScreen extends Screen {
    private final CutscenePayloads.Start scene;
    private final long startNanos = System.nanoTime();
    private ArmorStand camera;
    private boolean prevHideGui;
    private boolean voted;
    private int votes, total;

    public CutsceneScreen(CutscenePayloads.Start scene) {
        super(Component.empty());
        this.scene = scene;
    }

    public void votes(int votes, int total) {
        this.votes = votes;
        this.total = total;
    }

    @Override
    protected void init() {
        if (camera == null && minecraft.level != null) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, minecraft.level);
            camera.setInvisible(true);
            prevHideGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            place(0);
            minecraft.setCameraEntity(camera);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null) {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            mc.options.hideGui = prevHideGui;
            camera = null;
        }
    }

    private float elapsedTicks() {
        return (System.nanoTime() - startNanos) / 50_000_000F;
    }

    /** Поставить камеру в момент t (тики): между двумя ключами — плавно. */
    private void place(float t) {
        List<CutscenePayloads.Key> keys = scene.camera();
        CutscenePayloads.Key a = keys.get(0), b = a;
        for (CutscenePayloads.Key k : keys) {
            if (k.at() <= t) a = k;
            if (k.at() >= t) { b = k; break; }
            b = k;
        }
        float span = b.at() - a.at();
        float f = span <= 0 ? 0 : Mth.clamp((t - a.at()) / span, 0, 1);
        f = f * f * (3 - 2 * f);
        double x = Mth.lerp(f, a.x(), b.x()), y = Mth.lerp(f, a.y(), b.y()), z = Mth.lerp(f, a.z(), b.z());
        double lx = Mth.lerp(f, a.lx(), b.lx()), ly = Mth.lerp(f, a.ly(), b.ly()), lz = Mth.lerp(f, a.lz(), b.lz());
        double dx = lx - x, dy = ly - y, dz = lz - z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        double eyeY = y - camera.getEyeHeight();
        camera.setPos(x, eyeY, z);
        camera.xo = camera.xOld = x;
        camera.yo = camera.yOld = eyeY;
        camera.zo = camera.zOld = z;
        camera.setYRot(yaw);
        camera.yRotO = yaw;
        camera.setXRot(pitch);
        camera.xRotO = pitch;
        camera.setYHeadRot(yaw);
        camera.yHeadRotO = yaw;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Math.min(elapsedTicks(), scene.duration());
        if (camera != null) place(t);

        int bar = height / 8;
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);

        for (CutscenePayloads.Subtitle s : scene.subtitles()) {
            if (t < s.at() || t >= s.until()) continue;
            Component line = s.who().isEmpty() ? Component.literal(s.text())
                : Component.literal(s.who() + ": ").withStyle(Style.EMPTY.withColor(KcdUi.GOLD_LIGHT))
                    .append(Component.literal(s.text()).withStyle(Style.EMPTY.withColor(KcdUi.TEXT_LIGHT)));
            List<FormattedCharSequence> lines = font.split(line, Math.min(420, width - 40));
            int y = height - bar + Math.max(4, (bar - lines.size() * 11) / 2);
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, (width - font.width(l)) / 2, y, KcdUi.TEXT_LIGHT, true);
                y += 11;
            }
        }

        Component hint = voted
            ? Component.translatable("kcd.cutscene.skip_wait", votes, total)
            : Component.translatable("kcd.cutscene.skip");
        g.drawString(font, hint, width - font.width(hint) - 6, 4, 0x80F2E8D5, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE && !voted) {
            voted = true;
            PacketDistributor.sendToServer(new CutscenePayloads.Action(1));
            return true;
        }
        return true; // остальное во время сцены не работает
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
