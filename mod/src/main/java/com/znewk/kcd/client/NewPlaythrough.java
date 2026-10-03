package com.znewk.kcd.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.client.host.HostSession;
import com.znewk.kcd.mixin.CreateWorldScreenInvoker;

/**
 * «Новое прохождение»: мир KCD (рельеф и постройки по картам KCD) создаётся в один клик — без ванильного
 * экрана настроек. Режим приключения (блоки не ломаются, как в KCD). Для автотестов: -Dkcd.autonew=true.
 */
public final class NewPlaythrough {
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
        ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "kcd"));

    private static boolean pending;
    private static boolean autoNew = Boolean.getBoolean("kcd.autonew");

    private NewPlaythrough() {}

    /** Начать новое прохождение; host — сразу открыть мир для отряда. */
    public static void start(boolean host) {
        pending = true;
        if (host) HostSession.requestHost();
        else HostSession.cancelRequest();
        Minecraft mc = Minecraft.getInstance();
        CreateWorldScreen.openFresh(mc, mc.screen);
    }

    /** Экран создания мира открылся — заполняем и сразу создаём. */
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!pending || !(event.getScreen() instanceof CreateWorldScreen screen)) return;
        pending = false;
        WorldCreationUiState ui = screen.getUiState();
        ui.setName("Скалица " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL); // в мире KCD сервер переведёт в приключение
        ui.setAllowCommands(System.getProperty("kcd.autocmd") != null); // для автотестов нужны команды
        ui.getNormalPresetList().stream()
            .filter(e -> e.preset() != null && e.preset().is(PRESET))
            .findFirst()
            .ifPresentOrElse(ui::setWorldType, () -> KcdMod.LOGGER.error("KCD: тип мира kcd:kcd не найден"));
        ((CreateWorldScreenInvoker) screen).kcd$onCreate();
    }

    /** Автотест: создать новое прохождение сразу из главного меню. */
    public static void clientTick() {
        if (!autoNew) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.znewk.kcd.client.gui.KcdTitleScreen) {
            autoNew = false;
            start(Boolean.getBoolean("kcd.autohost"));
        }
    }
}
