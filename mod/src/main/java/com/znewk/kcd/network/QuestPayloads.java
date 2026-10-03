package com.znewk.kcd.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Пакеты заданий: весь журнал отряда и плашки «Новое задание / обновлено / выполнено». */
public final class QuestPayloads {
    private QuestPayloads() {}

    public record Objective(String text, boolean done) {}

    /** status: 0 — активно, 1 — выполнено, 2 — провалено. Цели — только видимые. */
    public record Quest(String id, String title, boolean main, int status, List<String> diary, List<Objective> objectives) {
        static Quest read(FriendlyByteBuf buf) {
            return new Quest(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(b -> new Objective(b.readUtf(), b.readBoolean())));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(title);
            buf.writeBoolean(main);
            buf.writeVarInt(status);
            buf.writeCollection(diary, FriendlyByteBuf::writeUtf);
            buf.writeCollection(objectives, (b, o) -> {
                b.writeUtf(o.text());
                b.writeBoolean(o.done());
            });
        }
    }

    /** Сервер → клиент: весь журнал отряда. */
    public record Sync(List<Quest> quests) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = PartyPayloads.payloadType("quest_sync");
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.ofMember(
            (s, buf) -> buf.writeCollection(s.quests(), (b, q) -> q.write(b)),
            buf -> new Sync(buf.readList(Quest::read)));

        @Override
        public Type<Sync> type() { return TYPE; }
    }

    /** Сервер → клиент: плашка. kind: 0 — новое задание, 1 — обновлено, 2 — выполнено, 3 — провалено. */
    public record Notice(int kind, String questId, String title, String detail) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = PartyPayloads.payloadType("quest_notice");
        public static final StreamCodec<FriendlyByteBuf, Notice> CODEC = StreamCodec.ofMember(
            (n, buf) -> {
                buf.writeVarInt(n.kind());
                buf.writeUtf(n.questId());
                buf.writeUtf(n.title());
                buf.writeUtf(n.detail());
            },
            buf -> new Notice(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<Notice> type() { return TYPE; }
    }
}
