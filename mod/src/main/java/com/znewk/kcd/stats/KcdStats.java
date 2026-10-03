package com.znewk.kcd.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.znewk.kcd.KcdMod;

/**
 * Характеристики персонажа (как в KCD2: Сила, Ловкость, Живучесть, Речь + навыки), уровень 0..30.
 * Пока только хранение и проверки в разговорах; рост «делаешь — растёшь» — позже.
 */
public final class KcdStats {
    public static final List<String> ALL = List.of("strength", "agility", "vitality", "speech", "warfare");
    /** Виды проверок в разговоре, как в KCD2. */
    public static final List<String> CHECKS = List.of("persuasion", "coercion", "impress", "dominate", "presence", "intimidate");
    public static final int DEFAULT = 2;
    public static final int MAX = 30;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, KcdMod.MODID);
    private static final Supplier<AttachmentType<Map<String, Integer>>> STATS = ATTACHMENTS.register("stats",
        () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
            .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
            .copyOnDeath()
            .build());

    private KcdStats() {}

    public static int get(Player player, String stat) {
        return player.getData(STATS).getOrDefault(stat, DEFAULT);
    }

    public static void set(Player player, String stat, int value) {
        Map<String, Integer> m = new HashMap<>(player.getData(STATS));
        m.put(stat, Math.max(0, Math.min(MAX, value)));
        player.setData(STATS, Map.copyOf(m));
    }

    /** Какая характеристика решает проверку. Обаяние от одежды и чистоты добавим вместе с инвентарём. */
    public static String forCheck(String kind) {
        return switch (kind) {
            case "dominate" -> "warfare";
            case "intimidate" -> "strength";
            default -> "speech"; // persuasion, coercion, impress, presence
        };
    }
}
