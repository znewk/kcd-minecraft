package com.znewk.kcd.client.music;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import com.znewk.kcd.KcdMod;

/**
 * «Дирижёр» музыки по правилам KCD: ванильная музыка выключена, трек выбирается по ситуации,
 * между треками — тишина, смена ситуации — плавное затухание, бой включается почти сразу.
 */
public final class MusicDirector {
    private static final int SCAN_PERIOD = 10;
    private static final int COMBAT_GRACE = 8 * 20;
    private static final RandomSource RANDOM = RandomSource.create();

    private static MusicMood mood;
    private static MusicMood forced;
    private static MusicTrack current;
    private static int silence;
    private static int scanTimer;
    private static int combatGrace;
    private static MusicMood scanned = MusicMood.EXPLORE_DAY;

    private MusicDirector() {}

    /** Ванильная музыка Minecraft не играет никогда. */
    public static void onSelectMusic(SelectMusicEvent event) {
        event.setMusic(null);
    }

    /** Принудительная ситуация (кат-сцены, регионы, команда /kcdmusic). null — автоматически. */
    public static void force(MusicMood m) {
        forced = m;
    }

    public static MusicMood mood() {
        return mood;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sounds = mc.getSoundManager();
        MusicMood want = decide(mc);

        if (current != null && !current.fadingOut() && !sounds.isActive(current)) {
            current = null;
            silence = gap(mood);
        }

        if (want != mood) {
            MusicMood old = mood;
            mood = want;
            if (current != null) current.fadeOut(want.urgent ? 20 : old == null ? 20 : old.fadeTicks);
            current = null;
            silence = want.urgent ? 0 : 40;
        }

        if (current == null) {
            if (silence > 0) {
                silence--;
                return;
            }
            if (!hasTracks(sounds, want)) {
                silence = 100;
                return;
            }
            current = new MusicTrack(want, want.urgent ? 10 : 60);
            sounds.play(current);
        }
    }

    private static MusicMood decide(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) return MusicMood.MENU;
        if (forced != null) return forced;

        if (--scanTimer <= 0) {
            scanTimer = SCAN_PERIOD;
            scanned = scan(mc, player);
        }
        return scanned;
    }

    private static MusicMood scan(Minecraft mc, LocalPlayer player) {
        List<Monster> near = mc.level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(20), m -> m.isAlive());
        boolean fight = player.hurtTime > 0 && !near.isEmpty();
        for (Monster m : near) {
            if (m.isAggressive() && m.distanceToSqr(player) < 14 * 14) fight = true;
        }
        if (fight) combatGrace = COMBAT_GRACE;
        else if (combatGrace > 0) combatGrace -= SCAN_PERIOD;
        if (combatGrace > 0) return MusicMood.COMBAT;
        if (!near.isEmpty()) return MusicMood.TENSION;
        long time = mc.level.getDayTime() % 24000L;
        return time >= 13000 && time < 23000 ? MusicMood.EXPLORE_NIGHT : MusicMood.EXPLORE_DAY;
    }

    private static boolean hasTracks(SoundManager sounds, MusicMood m) {
        WeighedSoundEvents ev = sounds.getSoundEvent(m.event());
        return ev != null && ev.getWeight() > 0;
    }

    private static int gap(MusicMood m) {
        if (m == null) return 40;
        return (m.gapMin + RANDOM.nextInt(m.gapMax - m.gapMin + 1)) * 20;
    }

    public static void reset() {
        forced = null;
        combatGrace = 0;
    }

    // ------------------------------------------------------------------ личный пакет музыки

    /** Папка с личной музыкой игрока (собирается скриптом tools/import-music.ps1, в сборку не входит). */
    public static Path personalPack() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("kcd").resolve("music_pack");
    }

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            Path dir = personalPack();
            if (!Files.exists(dir.resolve("pack.mcmeta"))) return;
            PackLocationInfo info = new PackLocationInfo("kcd_personal_music",
                Component.translatable("kcd.music.pack"), PackSource.BUILT_IN, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir),
                PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) {
                consumer.accept(pack);
                KcdMod.LOGGER.info("KCD: подключена личная музыка из {}", dir);
            }
        });
    }

    // ------------------------------------------------------------------ команда для проверки

    public static void registerClientCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kcdmusic")
            .then(Commands.literal("auto").executes(c -> {
                force(null);
                c.getSource().sendSuccess(() -> Component.translatable("kcd.music.auto"), false);
                return 1;
            }))
            .then(Commands.argument("mood", StringArgumentType.word())
                .suggests((c, b) -> {
                    for (MusicMood m : MusicMood.values()) b.suggest(m.id);
                    return b.buildFuture();
                })
                .executes(c -> {
                    MusicMood m = MusicMood.byId(StringArgumentType.getString(c, "mood"));
                    if (m == null) return 0;
                    force(m);
                    c.getSource().sendSuccess(() -> Component.translatable("kcd.music.forced", m.id), false);
                    return 1;
                })));
    }
}
