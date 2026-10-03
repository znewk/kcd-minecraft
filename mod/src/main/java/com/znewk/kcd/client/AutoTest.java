package com.znewk.kcd.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.client.quest.JournalScreen;

/**
 * Автотесты без мыши: {@code -Dkcd.autocmd="time set noon;~5;kcd npc talk;~3;!shot"} — после входа в мир
 * выполняет команды по одной в секунду; {@code ~N} — пауза N секунд, {@code !shot} — снимок экрана
 * в {@code <игра>/screenshots/kcd-auto-<n>.png}, {@code !journal} — открыть дневник. В обычной игре свойство не задано и класс ничего не делает.
 */
public final class AutoTest {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int wait = 60;
    private static int shots;

    static {
        for (String s : System.getProperty("kcd.autocmd", "").split(";")) {
            if (!s.isBlank()) QUEUE.add(s.trim());
        }
    }

    private AutoTest() {}

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (QUEUE.isEmpty() || mc.player == null || --wait > 0) return;
        String step = QUEUE.poll();
        if (step.startsWith("~")) {
            wait = Integer.parseInt(step.substring(1)) * 20;
        } else if (step.equals("!shot")) {
            int n = ++shots;
            Screenshot.grab(mc.gameDirectory, "kcd-auto-" + n + ".png", mc.getMainRenderTarget(),
                msg -> KcdMod.LOGGER.info("KCD: снимок {}", n));
            wait = 1;
        } else if (step.equals("!journal")) {
            mc.setScreen(new JournalScreen());
            wait = 1;
        } else {
            mc.player.connection.sendCommand(step);
            wait = 20;
        }
    }
}
