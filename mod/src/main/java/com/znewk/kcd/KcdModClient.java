package com.znewk.kcd;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиентская часть мода (интерфейс, HUD, кат-сцены). На выделенном сервере не загружается. */
@Mod(value = KcdMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KcdMod.MODID, value = Dist.CLIENT)
public class KcdModClient {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KcdMod.LOGGER.info("KCD: клиент готов");
    }
}
