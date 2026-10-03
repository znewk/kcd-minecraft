package com.znewk.kcd;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Главный класс мода KCD. Здесь подключаются все системы (отряд, диалоги, квесты, NPC...). */
@Mod(KcdMod.MODID)
public class KcdMod {
    public static final String MODID = "kcd";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KcdMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    public static String version() {
        return ModList.get().getModContainerById(MODID)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("?");
    }

    public static String windowTitle() {
        return "KCD by znewk · v" + version();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("KCD: мод загружен");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("KCD: сервер запускается");
    }
}
