package com.znewk.kcd.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.znewk.kcd.client.ClientPayloads;
import com.znewk.kcd.party.PartyService;

/** Регистрация всех пакетов мода. Клиентские обработчики вызываются только на клиенте. */
public final class KcdNetwork {
    public static final String VERSION = "1";

    private KcdNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToClient(PartyPayloads.OpenRoleSelect.TYPE, PartyPayloads.OpenRoleSelect.CODEC,
            (p, ctx) -> ClientPayloads.openRoleSelect(p));
        r.playToClient(PartyPayloads.PartySync.TYPE, PartyPayloads.PartySync.CODEC,
            (p, ctx) -> ClientPayloads.partySync(p));
        r.playToServer(PartyPayloads.ChooseRole.TYPE, PartyPayloads.ChooseRole.CODEC,
            (p, ctx) -> PartyService.chooseRole((ServerPlayer) ctx.player(), p));
    }
}
