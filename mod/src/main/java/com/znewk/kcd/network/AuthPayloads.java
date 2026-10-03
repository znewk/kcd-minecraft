package com.znewk.kcd.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Пакеты защиты мира: запрос ключа, ответ, выдача нового ключа. */
public final class AuthPayloads {
    private AuthPayloads() {}

    /** Сервер → клиент: «предъяви ключ для мира worldId». */
    public record Challenge(String worldId) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = PartyPayloads.payloadType("auth_challenge");
        public static final StreamCodec<ByteBuf, Challenge> CODEC = ByteBufCodecs.STRING_UTF8.map(Challenge::new, Challenge::worldId);

        @Override
        public Type<Challenge> type() { return TYPE; }
    }

    /** Клиент → сервер: сохранённый ключ ("" — нет). */
    public record Response(String key) implements CustomPacketPayload {
        public static final Type<Response> TYPE = PartyPayloads.payloadType("auth_response");
        public static final StreamCodec<ByteBuf, Response> CODEC = ByteBufCodecs.STRING_UTF8.map(Response::new, Response::key);

        @Override
        public Type<Response> type() { return TYPE; }
    }

    /** Сервер → клиент: новый ключ — сохранить. */
    public record Issue(String worldId, String key) implements CustomPacketPayload {
        public static final Type<Issue> TYPE = PartyPayloads.payloadType("auth_issue");
        public static final StreamCodec<ByteBuf, Issue> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Issue::worldId,
            ByteBufCodecs.STRING_UTF8, Issue::key,
            Issue::new);

        @Override
        public Type<Issue> type() { return TYPE; }
    }
}
