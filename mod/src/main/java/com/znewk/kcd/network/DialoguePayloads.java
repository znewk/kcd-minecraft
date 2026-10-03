package com.znewk.kcd.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Пакеты разговора с жителем. */
public final class DialoguePayloads {
    private DialoguePayloads() {}

    /** Вариант ответа. check — вид проверки или ""; seen — уже выбирал; suggestedBy — кто из отряда подсказал. */
    public record Opt(String text, String check, boolean seen, String suggestedBy) {
        static Opt read(FriendlyByteBuf buf) {
            return new Opt(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readUtf());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(text);
            buf.writeUtf(check);
            buf.writeBoolean(seen);
            buf.writeUtf(suggestedBy);
        }
    }

    /**
     * Сервер → клиент: открыть/обновить окно разговора.
     * prevSpeaker/prevLine — последняя реплика отряда; notice: 0 — нет, 1 — проверка удалась, 2 — не удалась.
     * canChoose — этот игрок отвечает; остальные могут только подсказать. decider — имя отвечающего.
     */
    public record View(int npcId, String npcName, String prevSpeaker, String prevLine, String text,
                       int notice, String noticeKind, List<Opt> options, boolean canChoose, String decider,
                       boolean story) implements CustomPacketPayload {
        public static final Type<View> TYPE = PartyPayloads.payloadType("dialogue_view");
        public static final StreamCodec<FriendlyByteBuf, View> CODEC = StreamCodec.ofMember(View::write, View::read);

        private static View read(FriendlyByteBuf buf) {
            return new View(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readUtf(), buf.readList(Opt::read), buf.readBoolean(), buf.readUtf(), buf.readBoolean());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(npcId);
            buf.writeUtf(npcName);
            buf.writeUtf(prevSpeaker);
            buf.writeUtf(prevLine);
            buf.writeUtf(text);
            buf.writeVarInt(notice);
            buf.writeUtf(noticeKind);
            buf.writeCollection(options, (b, o) -> o.write(b));
            buf.writeBoolean(canChoose);
            buf.writeUtf(decider);
            buf.writeBoolean(story);
        }

        @Override
        public Type<View> type() { return TYPE; }
    }

    /** Сервер → клиент: разговор окончен. */
    public record Close(int npcId) implements CustomPacketPayload {
        public static final Type<Close> TYPE = PartyPayloads.payloadType("dialogue_close");
        public static final StreamCodec<ByteBuf, Close> CODEC = ByteBufCodecs.VAR_INT.map(Close::new, Close::npcId);

        @Override
        public Type<Close> type() { return TYPE; }
    }

    /** Клиент → сервер: выбрать вариант (или подсказать, если отвечает другой). */
    public record Choose(int npcId, int option) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = PartyPayloads.payloadType("dialogue_choose");
        public static final StreamCodec<ByteBuf, Choose> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Choose::npcId,
            ByteBufCodecs.VAR_INT, Choose::option,
            Choose::new);

        @Override
        public Type<Choose> type() { return TYPE; }
    }

    /** Клиент → сервер: игрок закрыл окно (отвечающий — конец разговора, зритель — перестал смотреть). */
    public record Leave(int npcId) implements CustomPacketPayload {
        public static final Type<Leave> TYPE = PartyPayloads.payloadType("dialogue_leave");
        public static final StreamCodec<ByteBuf, Leave> CODEC = ByteBufCodecs.VAR_INT.map(Leave::new, Leave::npcId);

        @Override
        public Type<Leave> type() { return TYPE; }
    }
}
