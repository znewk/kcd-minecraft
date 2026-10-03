package com.znewk.kcd.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.party.Member;
import com.znewk.kcd.party.Role;

/** Пакеты отряда: выбор роли и состав отряда. */
public final class PartyPayloads {
    private PartyPayloads() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, path));
    }

    public static final StreamCodec<ByteBuf, Member> MEMBER_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Member::id,
        ByteBufCodecs.STRING_UTF8, Member::account,
        ByteBufCodecs.STRING_UTF8.map(Role::byName, Role::name), Member::role,
        ByteBufCodecs.STRING_UTF8, Member::name,
        ByteBufCodecs.VAR_INT, Member::story,
        Member::new);

    /** Сервер → клиент: открыть экран выбора роли. henryTakenBy — ник того, кто уже Индржих, или "". */
    public record OpenRoleSelect(String henryTakenBy, String error) implements CustomPacketPayload {
        public static final Type<OpenRoleSelect> TYPE = PartyPayloads.payloadType("open_role_select");
        public static final StreamCodec<ByteBuf, OpenRoleSelect> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenRoleSelect::henryTakenBy,
            ByteBufCodecs.STRING_UTF8, OpenRoleSelect::error,
            OpenRoleSelect::new);

        @Override
        public Type<OpenRoleSelect> type() { return TYPE; }
    }

    /** Клиент → сервер: выбранная роль. */
    public record ChooseRole(boolean henry, String name, int story) implements CustomPacketPayload {
        public static final Type<ChooseRole> TYPE = PartyPayloads.payloadType("choose_role");
        public static final StreamCodec<ByteBuf, ChooseRole> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChooseRole::henry,
            ByteBufCodecs.STRING_UTF8, ChooseRole::name,
            ByteBufCodecs.VAR_INT, ChooseRole::story,
            ChooseRole::new);

        @Override
        public Type<ChooseRole> type() { return TYPE; }
    }

    /** Сервер → клиент: весь состав отряда. */
    public record PartySync(List<Member> members) implements CustomPacketPayload {
        public static final Type<PartySync> TYPE = PartyPayloads.payloadType("party_sync");
        public static final StreamCodec<ByteBuf, PartySync> CODEC = MEMBER_CODEC
            .apply(ByteBufCodecs.list())
            .map(PartySync::new, PartySync::members);

        @Override
        public Type<PartySync> type() { return TYPE; }
    }
}
