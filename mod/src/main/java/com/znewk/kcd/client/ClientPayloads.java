package com.znewk.kcd.client;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.znewk.kcd.client.gui.RoleSelectScreen;
import com.znewk.kcd.network.PartyPayloads;
import com.znewk.kcd.party.Member;

/** Обработка пакетов на клиенте. Вызывается только на клиенте (из KcdNetwork). */
public final class ClientPayloads {
    private static PartyPayloads.OpenRoleSelect pendingRoleSelect;
    private static List<Member> party = List.of();

    private ClientPayloads() {}

    public static void openRoleSelect(PartyPayloads.OpenRoleSelect payload) {
        // экран откроем, когда закроется загрузка мира (см. clientTick)
        pendingRoleSelect = payload;
    }

    public static void partySync(PartyPayloads.PartySync payload) {
        party = List.copyOf(payload.members());
    }

    public static List<Member> party() {
        return party;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingRoleSelect != null && mc.player != null && mc.screen == null) {
            PartyPayloads.OpenRoleSelect p = pendingRoleSelect;
            pendingRoleSelect = null;
            mc.setScreen(new RoleSelectScreen(p.henryTakenBy(), p.error()));
        }
    }

    public static void reset() {
        pendingRoleSelect = null;
        party = List.of();
    }
}
