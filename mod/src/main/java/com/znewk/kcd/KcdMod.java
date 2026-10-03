package com.znewk.kcd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import com.znewk.kcd.auth.AuthService;
import com.znewk.kcd.cutscene.CutsceneRegistry;
import com.znewk.kcd.cutscene.CutsceneService;
import com.znewk.kcd.dialogue.DialogueRegistry;
import com.znewk.kcd.dialogue.DialogueService;
import com.znewk.kcd.network.KcdNetwork;
import com.znewk.kcd.npc.KcdEntities;
import com.znewk.kcd.npc.NpcCommands;
import com.znewk.kcd.npc.NpcRegistry;
import com.znewk.kcd.party.PartyService;
import com.znewk.kcd.quest.QuestRegistry;
import com.znewk.kcd.quest.QuestService;
import com.znewk.kcd.stats.KcdStats;
import com.znewk.kcd.world.TestVillage;

/** Главный класс мода KCD. Здесь подключаются все системы (отряд, диалоги, квесты, NPC...). */
@Mod(KcdMod.MODID)
public class KcdMod {
    public static final String MODID = "kcd";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KcdMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(KcdNetwork::register);
        modEventBus.addListener(KcdEntities::onAttributes);
        KcdEntities.ENTITIES.register(modEventBus);
        KcdStats.ATTACHMENTS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(PartyService::onLogin);
        NeoForge.EVENT_BUS.addListener(PartyService::onNameFormat);
        NeoForge.EVENT_BUS.addListener(PartyService::onTabListNameFormat);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PartyService.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> NpcCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new NpcRegistry());
            e.addListener(new DialogueRegistry());
            e.addListener(new QuestRegistry());
            e.addListener(new CutsceneRegistry());
        });
        NeoForge.EVENT_BUS.addListener(DialogueService::onServerTick);
        NeoForge.EVENT_BUS.addListener(QuestService::onLogin);
        NeoForge.EVENT_BUS.addListener(QuestService::onServerTick);
        NeoForge.EVENT_BUS.addListener(CutsceneService::onServerTick);
        NeoForge.EVENT_BUS.addListener(CutsceneService::onDamage);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CutsceneService.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(AuthService::onLogin);
        NeoForge.EVENT_BUS.addListener(AuthService::onLogout);
        NeoForge.EVENT_BUS.addListener(AuthService::onServerTick);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> AuthService.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestService.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TestVillage.registerCommands(e.getDispatcher()));
    }

    private static String version;

    /** Версия мода. Читается из ресурса, т.к. заголовок окна создаётся раньше, чем готов ModList. */
    public static String version() {
        if (version == null) {
            try (InputStream in = KcdMod.class.getResourceAsStream("/kcd_version.txt")) {
                version = in == null ? "?" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                version = "?";
            }
        }
        return version;
    }

    public static String windowTitle() {
        return "KCD by znewk · v" + version();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("KCD: мод загружен");
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("KCD: сервер запускается");
    }
}
