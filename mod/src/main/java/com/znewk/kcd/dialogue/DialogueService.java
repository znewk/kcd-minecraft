package com.znewk.kcd.dialogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.znewk.kcd.KcdMod;
import com.znewk.kcd.network.DialoguePayloads;
import com.znewk.kcd.npc.KcdNpc;
import com.znewk.kcd.npc.NpcDefinition;
import com.znewk.kcd.npc.NpcMemory;
import com.znewk.kcd.npc.NpcRegistry;
import com.znewk.kcd.party.Member;
import com.znewk.kcd.party.PartyData;
import com.znewk.kcd.party.Role;
import com.znewk.kcd.quest.QuestService;
import com.znewk.kcd.stats.KcdStats;
import com.znewk.kcd.story.StoryFlags;

/**
 * Разговоры с жителями. Говорит подошедший; все игроки в радиусе видят разговор у себя и могут подсказать
 * вариант; в сюжетных местах («story») отвечает Индржих, если он рядом. Пока житель разговаривает, он занят:
 * второй подошедший просто присоединяется к зрителям.
 */
public final class DialogueService {
    /** Кто ближе — видит разговор. */
    public static final double VIEW_RADIUS = 10.0;
    /** Отвечающий отошёл дальше — разговор обрывается. */
    private static final double SPEAKER_RADIUS = 8.0;
    private static final String LEAVE_TEXT = "@kcd.dialogue.leave";

    private static final Map<Integer, Conversation> BY_NPC = new HashMap<>();

    private DialogueService() {}

    private static final class Conversation {
        final KcdNpc npc;
        final NpcDefinition def;
        final Dialogue dialogue;
        final UUID speaker;
        final Set<UUID> viewers = new LinkedHashSet<>();
        /** Зрители, закрывшие окно сами, — обратно не подключаем. */
        final Set<UUID> left = new HashSet<>();
        final Map<UUID, Integer> suggestions = new LinkedHashMap<>();
        Dialogue.Node node;
        /** Индексы видимых вариантов в node.options(); -1 — «Уйти». */
        List<Integer> visible = List.of();
        UUID decider;
        String prevSpeaker = "", prevLine = "";
        int notice;
        String noticeKind = "";

        Conversation(KcdNpc npc, NpcDefinition def, Dialogue dialogue, UUID speaker) {
            this.npc = npc;
            this.def = def;
            this.dialogue = dialogue;
            this.speaker = speaker;
            this.decider = speaker;
        }

        String npcKey() {
            return def.id().toString();
        }
    }

    // ------------------------------------------------------------------ начало и конец

    /** Игрок заговорил с жителем (ПКМ). */
    public static void start(ServerPlayer player, KcdNpc npc) {
        NpcDefinition def = NpcRegistry.get(npc.npcId());
        if (def == null) return;

        Conversation busy = BY_NPC.get(npc.getId());
        if (busy != null) {
            // житель занят — присоединяемся к разговору зрителем
            busy.left.remove(player.getUUID());
            if (!busy.speaker.equals(player.getUUID())) busy.viewers.add(player.getUUID());
            sendView(busy, player);
            return;
        }

        Dialogue dialogue = def.dialogue() == null ? null : DialogueRegistry.get(def.dialogue());
        String startNode = dialogue == null ? null : pickStart(dialogue, def, player);
        if (startNode == null) {
            bark(player, npc, def);
            return;
        }
        leaveAll(player);
        Conversation c = new Conversation(npc, def, dialogue, player.getUUID());
        BY_NPC.put(npc.getId(), c);
        npc.setTalkingTo(player.getUUID());
        enter(c, startNode);
    }

    @Nullable
    private static String pickStart(Dialogue d, NpcDefinition def, ServerPlayer player) {
        for (Dialogue.Start s : d.start()) {
            if (test(player, def.id().toString(), s.when())) return s.node();
        }
        return null;
    }

    /** Житель без разговора — короткая фраза над панелью быстрого доступа. */
    private static void bark(ServerPlayer player, KcdNpc npc, NpcDefinition def) {
        if (def.barks().isEmpty()) return;
        String line = def.barks().get(npc.getRandom().nextInt(def.barks().size()));
        player.displayClientMessage(Component.literal(def.name() + ": " + format(line, player)), true);
        npc.getLookControl().setLookAt(player);
    }

    private static void end(Conversation c) {
        BY_NPC.remove(c.npc.getId());
        c.npc.setTalkingTo(null);
        MinecraftServer server = c.npc.getServer();
        if (server == null) return;
        DialoguePayloads.Close close = new DialoguePayloads.Close(c.npc.getId());
        for (UUID id : participants(c)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, close);
        }
    }

    /** Игрок закрыл окно разговора. */
    public static void leave(ServerPlayer player, int npcId) {
        Conversation c = BY_NPC.get(npcId);
        if (c == null) return;
        if (c.speaker.equals(player.getUUID())) {
            end(c);
            return;
        }
        c.viewers.remove(player.getUUID());
        c.left.add(player.getUUID());
        boolean changed = c.suggestions.remove(player.getUUID()) != null;
        if (c.decider.equals(player.getUUID())) {
            // Индржих ушёл посреди сюжетного выбора — отвечает тот, кто начал разговор
            c.decider = c.speaker;
            changed = true;
        }
        if (changed) broadcast(c);
    }

    /** Перед новым разговором — выйти из прежних. */
    private static void leaveAll(ServerPlayer player) {
        for (Conversation c : new ArrayList<>(BY_NPC.values())) {
            if (c.speaker.equals(player.getUUID())) end(c);
            else c.viewers.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------ ход разговора

    private static void enter(Conversation c, String nodeId) {
        ServerPlayer speaker = player(c, c.speaker);
        Dialogue.Node node = c.dialogue.nodes().get(nodeId);
        if (Dialogue.END.equals(nodeId) || node == null || speaker == null) {
            end(c);
            return;
        }
        c.node = node;
        c.suggestions.clear();
        apply(c, speaker, node.effects());

        c.decider = c.speaker;
        if (node.story()) {
            ServerPlayer henry = henryNearby(c);
            if (henry != null) {
                c.decider = henry.getUUID();
                c.left.remove(henry.getUUID());
                if (!henry.getUUID().equals(c.speaker)) c.viewers.add(henry.getUUID());
            }
        }
        ServerPlayer decider = player(c, c.decider);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option o = node.options().get(i);
            if (!test(decider, c.npcKey(), o.when())) continue;
            // однократные варианты и проверки (как в KCD — второй попытки нет) после выбора исчезают
            if ((o.once() || o.check() != null) && NpcMemory.get(server(c)).knows(c.npcKey(), decider.getUUID(), "used:" + o.key())) continue;
            visible.add(i);
        }
        if (visible.isEmpty()) visible.add(-1);
        c.visible = List.copyOf(visible);

        updateViewers(c);
        broadcast(c);
    }

    /** Игрок нажал вариант: отвечающий — выбирает, остальные — подсказывают. */
    public static void choose(ServerPlayer player, int npcId, int index) {
        Conversation c = BY_NPC.get(npcId);
        if (c == null) {
            PacketDistributor.sendToPlayer(player, new DialoguePayloads.Close(npcId));
            return;
        }
        if (index < 0 || index >= c.visible.size()) return;
        UUID id = player.getUUID();
        if (!id.equals(c.decider)) {
            if (c.speaker.equals(id) || c.viewers.contains(id)) {
                c.suggestions.put(id, index);
                broadcast(c);
            }
            return;
        }

        int oi = c.visible.get(index);
        if (oi < 0) {
            end(c);
            return;
        }
        Dialogue.Option o = c.node.options().get(oi);
        NpcMemory mem = NpcMemory.get(server(c));
        mem.remember(c.npcKey(), id, "seen:" + o.key());
        if (o.once() || o.check() != null) mem.remember(c.npcKey(), id, "used:" + o.key());

        c.prevSpeaker = name(player);
        c.prevLine = format(o.text(), player);
        c.notice = 0;
        c.noticeKind = "";
        apply(c, player, o.effects());

        String next = o.next();
        if (o.check() != null) {
            boolean ok = check(c, player, o.check());
            c.notice = ok ? 1 : 2;
            c.noticeKind = o.check().kind();
            next = ok ? o.check().success() : o.check().fail();
        }
        enter(c, next);
    }

    /** Проверка как в KCD2: без процентов — характеристика (+ отношение жителя) против порога. */
    private static boolean check(Conversation c, ServerPlayer player, Dialogue.Check check) {
        int stat = KcdStats.get(player, KcdStats.forCheck(check.kind()));
        int rep = NpcMemory.get(server(c)).rep(c.npcKey(), player.getUUID());
        int value = stat + rep / 25;
        KcdMod.LOGGER.debug("KCD: проверка {} у {}: {} (+{} отношение) против {}", check.kind(), name(player), stat, rep / 25, check.difficulty());
        return value >= check.difficulty();
    }

    // ------------------------------------------------------------------ условия и действия

    private static boolean test(ServerPlayer player, String npc, List<Dialogue.Cond> conds) {
        for (Dialogue.Cond c : conds) {
            if (test(player, npc, c) == c.negate()) return false;
        }
        return true;
    }

    private static boolean test(ServerPlayer player, String npc, Dialogue.Cond c) {
        MinecraftServer server = player.server;
        return switch (c.type()) {
            case "flag" -> StoryFlags.get(server).has(c.arg());
            case "memory" -> NpcMemory.get(server).knows(npc, player.getUUID(), c.arg());
            case "role" -> PartyData.get(server).member(player.getUUID())
                .map(m -> m.role() == (c.arg().equals("henry") ? Role.HENRY : Role.BROTHER)).orElse(false);
            case "rep" -> NpcMemory.get(server).rep(npc, player.getUUID()) >= Integer.parseInt(c.arg());
            case "stat" -> {
                String[] s = c.arg().split(":");
                yield KcdStats.get(player, s[0]) >= Integer.parseInt(s[1]);
            }
            case "quest" -> QuestService.isActive(server, c.arg());
            case "done" -> QuestService.isDone(server, c.arg());
            case "objective" -> {
                String[] s = c.arg().split(":");
                yield QuestService.objectiveDone(server, s[0], s[1]);
            }
            case "has" -> {
                ItemStack want = stack(c.arg());
                yield player.getInventory().countItem(want.getItem()) >= want.getCount();
            }
            default -> false;
        };
    }

    private static void apply(Conversation c, ServerPlayer player, List<Dialogue.Effect> effects) {
        MinecraftServer server = player.server;
        for (Dialogue.Effect e : effects) {
            try {
                switch (e.type()) {
                    case "flag" -> StoryFlags.get(server).set(e.arg());
                    case "unflag" -> StoryFlags.get(server).clear(e.arg());
                    case "remember" -> NpcMemory.get(server).remember(c.npcKey(), player.getUUID(), e.arg());
                    case "forget" -> NpcMemory.get(server).forget(c.npcKey(), player.getUUID(), e.arg());
                    case "rep" -> NpcMemory.get(server).addRep(c.npcKey(), player.getUUID(), Integer.parseInt(e.arg().replace("+", "")));
                    case "stat" -> {
                        String[] s = e.arg().split(":");
                        KcdStats.set(player, s[0], KcdStats.get(player, s[0]) + Integer.parseInt(s[1].replace("+", "")));
                    }
                    case "give" -> give(player, e.arg());
                    case "take" -> take(player, e.arg());
                    case "quest" -> QuestService.start(server, e.arg());
                    case "objective" -> {
                        String[] s = e.arg().split(":");
                        QuestService.objective(server, s[0], s[1]);
                    }
                    case "diary" -> {
                        String[] s = e.arg().split(":");
                        QuestService.diary(server, s[0], s[1]);
                    }
                    case "complete" -> QuestService.finish(server, e.arg(), true);
                    case "fail" -> QuestService.finish(server, e.arg(), false);
                    default -> { }
                }
            } catch (RuntimeException ex) {
                KcdMod.LOGGER.error("KCD: действие {}:{} в разговоре {}: {}", e.type(), e.arg(), c.dialogue.id(), ex.toString());
            }
        }
    }

    /** "minecraft:charcoal*10" → стопка (количество может быть больше размера стопки). */
    private static ItemStack stack(String arg) {
        int star = arg.lastIndexOf('*');
        int count = star < 0 ? 1 : Integer.parseInt(arg.substring(star + 1));
        ResourceLocation id = ResourceLocation.parse(star < 0 ? arg : arg.substring(0, star));
        return new ItemStack(BuiltInRegistries.ITEM.get(id), count);
    }

    private static void give(ServerPlayer player, String arg) {
        ItemStack want = stack(arg);
        int left = want.getCount();
        while (left > 0) {
            ItemStack s = want.copyWithCount(Math.min(left, want.getMaxStackSize()));
            left -= s.getCount();
            if (!player.getInventory().add(s)) player.drop(s, false);
        }
    }

    private static void take(ServerPlayer player, String arg) {
        ItemStack want = stack(arg);
        player.getInventory().clearOrCountMatchingItems(s -> s.is(want.getItem()), want.getCount(), player.inventoryMenu.getCraftSlots());
    }

    // ------------------------------------------------------------------ зрители и рассылка

    /** Раз в 5 тиков: житель жив, отвечающий рядом, зрители подходят и уходят. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BY_NPC.isEmpty() || event.getServer().getTickCount() % 5 != 0) return;
        for (Conversation c : new ArrayList<>(BY_NPC.values())) {
            ServerPlayer speaker = player(c, c.speaker);
            if (c.npc.isRemoved() || !c.npc.isAlive() || speaker == null || speaker.level() != c.npc.level()
                || speaker.distanceToSqr(c.npc) > SPEAKER_RADIUS * SPEAKER_RADIUS) {
                end(c);
                continue;
            }
            for (ServerPlayer added : updateViewers(c)) sendView(c, added);
        }
    }

    /** Обновить список зрителей; вернуть новых. */
    private static List<ServerPlayer> updateViewers(Conversation c) {
        List<ServerPlayer> added = new ArrayList<>();
        double far = (VIEW_RADIUS + 2) * (VIEW_RADIUS + 2);
        for (UUID id : new ArrayList<>(c.viewers)) {
            ServerPlayer p = player(c, id);
            if (p == null || p.level() != c.npc.level() || p.distanceToSqr(c.npc) > far && !id.equals(c.decider)) {
                c.viewers.remove(id);
                c.suggestions.remove(id);
                if (p != null) PacketDistributor.sendToPlayer(p, new DialoguePayloads.Close(c.npc.getId()));
            }
        }
        for (ServerPlayer p : c.npc.level().getEntitiesOfClass(ServerPlayer.class, c.npc.getBoundingBox().inflate(VIEW_RADIUS))) {
            UUID id = p.getUUID();
            if (id.equals(c.speaker) || c.left.contains(id) || p.isSpectator()) continue;
            if (c.viewers.add(id)) added.add(p);
        }
        return added;
    }

    private static void broadcast(Conversation c) {
        for (UUID id : participants(c)) {
            ServerPlayer p = player(c, id);
            if (p != null) sendView(c, p);
        }
    }

    private static void sendView(Conversation c, ServerPlayer p) {
        ServerPlayer speaker = player(c, c.speaker);
        ServerPlayer decider = player(c, c.decider);
        if (speaker == null || decider == null) return;
        NpcMemory mem = NpcMemory.get(server(c));
        List<DialoguePayloads.Opt> opts = new ArrayList<>();
        for (int i = 0; i < c.visible.size(); i++) {
            int oi = c.visible.get(i);
            String suggested = suggestedBy(c, i);
            if (oi < 0) {
                opts.add(new DialoguePayloads.Opt(LEAVE_TEXT, "", false, suggested));
                continue;
            }
            Dialogue.Option o = c.node.options().get(oi);
            boolean seen = mem.knows(c.npcKey(), decider.getUUID(), "seen:" + o.key());
            opts.add(new DialoguePayloads.Opt(format(o.text(), decider), o.check() == null ? "" : o.check().kind(), seen, suggested));
        }
        PacketDistributor.sendToPlayer(p, new DialoguePayloads.View(c.npc.getId(), c.def.name(), c.prevSpeaker, c.prevLine,
            format(c.node.text(), speaker), c.notice, c.noticeKind, opts, p.getUUID().equals(c.decider), name(decider), c.node.story()));
    }

    private static String suggestedBy(Conversation c, int index) {
        List<String> names = new ArrayList<>();
        c.suggestions.forEach((id, i) -> {
            if (i == index) {
                ServerPlayer p = player(c, id);
                if (p != null) names.add(name(p));
            }
        });
        return String.join(", ", names);
    }

    private static Set<UUID> participants(Conversation c) {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(c.speaker);
        all.addAll(c.viewers);
        return all;
    }

    // ------------------------------------------------------------------ мелочи

    @Nullable
    private static ServerPlayer henryNearby(Conversation c) {
        Optional<Member> henry = PartyData.get(server(c)).henry();
        if (henry.isEmpty()) return null;
        ServerPlayer p = player(c, henry.get().id());
        return p != null && p.level() == c.npc.level() && p.distanceToSqr(c.npc) <= VIEW_RADIUS * VIEW_RADIUS ? p : null;
    }

    private static MinecraftServer server(Conversation c) {
        return c.npc.getServer();
    }

    @Nullable
    private static ServerPlayer player(Conversation c, UUID id) {
        MinecraftServer server = c.npc.getServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** Имя персонажа в отряде (или ник). */
    public static String name(ServerPlayer p) {
        return PartyData.get(p.server).member(p.getUUID()).map(Member::name).orElse(p.getGameProfile().getName());
    }

    /** Подстановки в тексте: {name} — имя собеседника. */
    private static String format(String text, ServerPlayer player) {
        return text.replace("{name}", name(player));
    }

    /** Для отладки: идёт ли разговор с этим жителем. */
    public static boolean isTalking(KcdNpc npc) {
        return BY_NPC.containsKey(npc.getId());
    }
}
