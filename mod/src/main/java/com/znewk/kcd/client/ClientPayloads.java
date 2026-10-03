package com.znewk.kcd.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.client.gui.DialogueScreen;
import com.znewk.kcd.client.gui.RoleSelectScreen;
import com.znewk.kcd.network.DialoguePayloads;
import com.znewk.kcd.network.PartyPayloads;
import com.znewk.kcd.party.Member;

/** Обработка пакетов на клиенте. Вызывается только на клиенте (из KcdNetwork). */
public final class ClientPayloads {
    private static PartyPayloads.OpenRoleSelect pendingRoleSelect;
    private static List<Member> party = List.of();

    private ClientPayloads() {}

    public static void openRoleSelect(PartyPayloads.OpenRoleSelect payload) {
        // автотесты: -Dkcd.autorole=henry или brother:Имя:предыстория — выбрать роль без экрана
        String auto = System.getProperty("kcd.autorole");
        if (auto != null && payload.error().isEmpty()) {
            String[] p = auto.split(":");
            boolean henry = p[0].equalsIgnoreCase("henry");
            PacketDistributor.sendToServer(new PartyPayloads.ChooseRole(henry, p.length > 1 ? p[1] : "", p.length > 2 ? Integer.parseInt(p[2]) : 0));
            return;
        }
        // экран откроем, когда закроется загрузка мира (см. clientTick)
        pendingRoleSelect = payload;
    }

    public static void partySync(PartyPayloads.PartySync payload) {
        party = List.copyOf(payload.members());
        // имена над головами берутся из отряда (см. onNameFormat) — обновить у всех видимых игроков
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.players().forEach(Player::refreshDisplayName);
    }

    /** Имя над головой на клиенте — имя персонажа из отряда. */
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide()) return;
        UUID id = event.getEntity().getUUID();
        for (Member m : party) {
            if (m.id().equals(id)) {
                event.setDisplayname(Component.literal(m.name()));
                return;
            }
        }
    }

    public static void dialogueView(DialoguePayloads.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen ds && ds.npcId() == view.npcId()) ds.update(view);
        else mc.setScreen(new DialogueScreen(view));
    }

    public static void dialogueClose(DialoguePayloads.Close close) {
        if (Minecraft.getInstance().screen instanceof DialogueScreen ds && ds.npcId() == close.npcId()) ds.closeFromServer();
    }

    public static List<Member> party() {
        return party;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        AutoTest.clientTick();
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
