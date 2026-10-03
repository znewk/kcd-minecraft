package com.znewk.kcd.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Пакеты кат-сцен: сбор отряда, старт (с камерой и субтитрами), голос «пропустить», конец. */
public final class CutscenePayloads {
    private CutscenePayloads() {}

    /** Сервер → клиент: «Сюжетная сцена — готов?». ready/total — сколько уже готовы. */
    public record Gather(String title, int seconds, int ready, int total) implements CustomPacketPayload {
        public static final Type<Gather> TYPE = PartyPayloads.payloadType("cutscene_gather");
        public static final StreamCodec<ByteBuf, Gather> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Gather::title,
            ByteBufCodecs.VAR_INT, Gather::seconds,
            ByteBufCodecs.VAR_INT, Gather::ready,
            ByteBufCodecs.VAR_INT, Gather::total,
            Gather::new);

        @Override
        public Type<Gather> type() { return TYPE; }
    }

    /** Ключ камеры: время и мировые координаты позиции и точки взгляда. */
    public record Key(int at, double x, double y, double z, double lx, double ly, double lz) {}

    public record Subtitle(int at, int until, String who, String text) {}

    /** Сервер → клиент: начать сцену (камера уже в мировых координатах). */
    public record Start(int duration, List<Key> camera, List<Subtitle> subtitles) implements CustomPacketPayload {
        public static final Type<Start> TYPE = PartyPayloads.payloadType("cutscene_start");
        public static final StreamCodec<FriendlyByteBuf, Start> CODEC = StreamCodec.ofMember(
            (s, buf) -> {
                buf.writeVarInt(s.duration());
                buf.writeCollection(s.camera(), (b, k) -> {
                    b.writeVarInt(k.at());
                    b.writeDouble(k.x()); b.writeDouble(k.y()); b.writeDouble(k.z());
                    b.writeDouble(k.lx()); b.writeDouble(k.ly()); b.writeDouble(k.lz());
                });
                buf.writeCollection(s.subtitles(), (b, t) -> {
                    b.writeVarInt(t.at()); b.writeVarInt(t.until()); b.writeUtf(t.who()); b.writeUtf(t.text());
                });
            },
            buf -> new Start(buf.readVarInt(),
                buf.readList(b -> new Key(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble())),
                buf.readList(b -> new Subtitle(b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf()))));

        @Override
        public Type<Start> type() { return TYPE; }
    }

    /** Сервер → клиент: голоса за пропуск (votes из total); total = 0 — сцена окончена. */
    public record State(int votes, int total) implements CustomPacketPayload {
        public static final Type<State> TYPE = PartyPayloads.payloadType("cutscene_state");
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, State::votes,
            ByteBufCodecs.VAR_INT, State::total,
            State::new);

        @Override
        public Type<State> type() { return TYPE; }
    }

    /** Клиент → сервер: 0 — готов к сцене, 1 — голос за пропуск. */
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = PartyPayloads.payloadType("cutscene_action");
        public static final StreamCodec<ByteBuf, Action> CODEC = ByteBufCodecs.VAR_INT.map(Action::new, Action::action);

        @Override
        public Type<Action> type() { return TYPE; }
    }
}
