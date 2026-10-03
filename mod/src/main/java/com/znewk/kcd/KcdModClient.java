package com.znewk.kcd;

import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.znewk.kcd.client.ClientPayloads;
import com.znewk.kcd.client.gui.KcdTitleScreen;

/** Клиентская часть мода (интерфейс, HUD, кат-сцены). На выделенном сервере не загружается. */
@Mod(value = KcdMod.MODID, dist = Dist.CLIENT)
public class KcdModClient {
    public KcdModClient(IEventBus modEventBus) {
        modEventBus.addListener(KcdModClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(KcdModClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientPayloads.clientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientPayloads.reset());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        KcdMod.LOGGER.info("KCD: клиент готов");
    }

    /** Подменяем ванильное главное меню на своё. */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new KcdTitleScreen());
        }
    }
}
