package com.znewk.kcd.client.cutscene;

import net.minecraft.client.Minecraft;

import com.znewk.kcd.network.CutscenePayloads;

/** Пакеты кат-сцен на клиенте. */
public final class ClientCutscenes {
    private ClientCutscenes() {}

    public static void gather(CutscenePayloads.Gather g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GatherScreen gs) gs.update(g);
        else mc.setScreen(new GatherScreen(g));
        if (System.getProperty("kcd.autoready") != null && mc.screen instanceof GatherScreen gs) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new CutscenePayloads.Action(0));
            gs.markReady();
        }
    }

    public static void start(CutscenePayloads.Start s) {
        Minecraft.getInstance().setScreen(new CutsceneScreen(s));
    }

    public static void state(CutscenePayloads.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (s.total() == 0) {
            if (mc.screen instanceof CutsceneScreen || mc.screen instanceof GatherScreen) mc.setScreen(null);
        } else if (mc.screen instanceof CutsceneScreen cs) {
            cs.votes(s.votes(), s.total());
        }
    }
}
