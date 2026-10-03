package com.znewk.kcd.client.quest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import com.znewk.kcd.network.QuestPayloads;

/** Журнал отряда на клиенте: задания с сервера, отслеживаемые (до 3, у каждого игрока свои) и очередь плашек. */
public final class ClientQuests {
    public static final int MAX_TRACKED = 3;

    private static List<QuestPayloads.Quest> quests = List.of();
    private static final Set<String> TRACKED = new LinkedHashSet<>();
    static final Deque<QuestPayloads.Notice> NOTICES = new ArrayDeque<>();

    private ClientQuests() {}

    public static void sync(QuestPayloads.Sync payload) {
        quests = List.copyOf(payload.quests());
        TRACKED.removeIf(id -> {
            QuestPayloads.Quest q = get(id);
            return q == null || q.status() != 0;
        });
        // при входе в мир — отслеживать идущие задания, пока есть место (сначала основные)
        if (TRACKED.isEmpty()) {
            quests.stream().filter(q -> q.status() == 0 && q.main()).forEach(q -> track(q.id(), true));
            quests.stream().filter(q -> q.status() == 0 && !q.main()).forEach(q -> track(q.id(), true));
        }
    }

    public static void notice(QuestPayloads.Notice n) {
        if (n.kind() == 0) track(n.questId(), true);
        NOTICES.add(n);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
            n.kind() == 2 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.BOOK_PAGE_TURN, 1F, n.kind() == 2 ? 0.6F : 1F));
    }

    public static List<QuestPayloads.Quest> all() {
        return quests;
    }

    @Nullable
    public static QuestPayloads.Quest get(String id) {
        for (QuestPayloads.Quest q : quests) if (q.id().equals(id)) return q;
        return null;
    }

    public static boolean isTracked(String id) {
        return TRACKED.contains(id);
    }

    public static Set<String> tracked() {
        return TRACKED;
    }

    /** Включить/выключить отслеживание. Больше трёх нельзя, как в KCD2. */
    public static boolean track(String id, boolean on) {
        if (!on) return TRACKED.remove(id);
        if (TRACKED.size() >= MAX_TRACKED) return false;
        return TRACKED.add(id);
    }

    public static void reset() {
        quests = List.of();
        TRACKED.clear();
        NOTICES.clear();
    }
}
