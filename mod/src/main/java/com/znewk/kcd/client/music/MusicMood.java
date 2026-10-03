package com.znewk.kcd.client.music;

import net.minecraft.resources.ResourceLocation;

import com.znewk.kcd.KcdMod;

/**
 * Музыкальные ситуации (как в KCD: музыка зависит от места и действия, тишина — тоже инструмент).
 * Трек ситуации — звуковое событие {@code kcd:mood.<id>}: свободные треки из мода,
 * поверх них — личный пакет музыки игрока (kcd/music_pack), если он есть.
 *
 * @param gapMin    минимальная тишина после трека, сек
 * @param gapMax    максимальная тишина после трека, сек
 * @param urgent    включается сразу (бой, погоня), прерывая текущую музыку
 * @param fadeTicks длительность затухания при смене ситуации, тики
 */
public enum MusicMood {
    MENU("menu", 8, 20, false, 40),
    EXPLORE_DAY("explore_day", 60, 150, false, 80),
    EXPLORE_NIGHT("explore_night", 90, 200, false, 80),
    VILLAGE("village", 25, 70, false, 60),
    TOWN("town", 20, 50, false, 60),
    TAVERN("tavern", 4, 10, false, 40),
    CHURCH("church", 30, 60, false, 60),
    CAMP("camp", 30, 80, false, 60),
    TENSION("tension", 3, 8, false, 40),
    COMBAT("combat", 1, 3, true, 60),
    FISTFIGHT("fistfight", 1, 3, true, 40),
    CHASE("chase", 1, 3, true, 40),
    VICTORY("victory", 600, 600, true, 40),
    CUTSCENE("cutscene", 2, 5, true, 40);

    public final String id;
    public final int gapMin, gapMax;
    public final boolean urgent;
    public final int fadeTicks;

    MusicMood(String id, int gapMin, int gapMax, boolean urgent, int fadeTicks) {
        this.id = id;
        this.gapMin = gapMin;
        this.gapMax = gapMax;
        this.urgent = urgent;
        this.fadeTicks = fadeTicks;
    }

    public ResourceLocation event() {
        return ResourceLocation.fromNamespaceAndPath(KcdMod.MODID, "mood." + id);
    }

    public static MusicMood byId(String id) {
        for (MusicMood m : values()) if (m.id.equals(id)) return m;
        return null;
    }
}
