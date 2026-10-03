package com.znewk.kcd.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.znewk.kcd.client.quest.JournalScreen;

/** Клавиши мода (меняются в Настройки → Управление → KCD). */
public final class KcdKeys {
    public static final String CATEGORY = "key.categories.kcd";
    public static final KeyMapping JOURNAL = new KeyMapping("key.kcd.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private KcdKeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(JOURNAL);
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (JOURNAL.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new JournalScreen());
        }
    }
}
