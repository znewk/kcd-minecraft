package com.znewk.kcd;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.znewk.kcd.client.ClientPayloads;
import com.znewk.kcd.client.gui.DialogueScreen;
import com.znewk.kcd.client.gui.KcdTitleScreen;
import com.znewk.kcd.client.host.HostSession;
import com.znewk.kcd.client.host.PlayitService;
import com.znewk.kcd.client.music.MusicDirector;
import com.znewk.kcd.client.npc.KcdNpcRenderer;
import com.znewk.kcd.npc.KcdEntities;

/** Клиентская часть мода (интерфейс, HUD, кат-сцены). На выделенном сервере не загружается. */
@Mod(value = KcdMod.MODID, dist = Dist.CLIENT)
public class KcdModClient {
    public KcdModClient(IEventBus modEventBus) {
        modEventBus.addListener(KcdModClient::onClientSetup);
        modEventBus.addListener(MusicDirector::addPackFinders);
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(KcdEntities.NPC.get(), KcdNpcRenderer::new));
        NeoForge.EVENT_BUS.addListener(DialogueScreen::onComputeFov);
        NeoForge.EVENT_BUS.addListener(KcdModClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(KcdModClient::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientPayloads::onNameFormat);
        NeoForge.EVENT_BUS.addListener(MusicDirector::onSelectMusic);
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> MusicDirector.registerClientCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientPayloads.clientTick();
            HostSession.clientTick();
            MusicDirector.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientPayloads.reset();
            HostSession.onLogout();
            MusicDirector.reset();
        });
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

    /** В меню паузы у хоста — адрес для отряда (клик копирует). */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || !HostSession.isHosting()) return;
        String addr = PlayitService.address();
        Component label = addr != null
            ? Component.translatable("kcd.host.pause.address", addr)
            : Component.translatable("kcd.host.pause.state." + PlayitService.state().name().toLowerCase());
        event.addListener(Button.builder(label, b -> {
            if (PlayitService.address() != null) {
                event.getScreen().getMinecraft().keyboardHandler.setClipboard(PlayitService.address());
                b.setMessage(Component.translatable("kcd.host.pause.copied"));
            }
        }).bounds(6, 6, 200, 20).build());
    }
}
