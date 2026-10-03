package com.znewk.kcd.client.music;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Играющий трек: громкость плавно нарастает и затухает (переходы между ситуациями). */
public class MusicTrack extends AbstractTickableSoundInstance {
    private final int fadeInTicks;
    private int age;
    private int fadeOutTicks;
    private int fadeOutLeft = -1;
    private float fadeOutFrom;

    public MusicTrack(MusicMood mood, int fadeInTicks) {
        super(SoundEvent.createVariableRangeEvent(mood.event()), SoundSource.MUSIC, RandomSource.create());
        this.fadeInTicks = Math.max(1, fadeInTicks);
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.001F;
    }

    public void fadeOut(int ticks) {
        if (fadeOutLeft >= 0) return;
        fadeOutTicks = Math.max(1, ticks);
        fadeOutLeft = fadeOutTicks;
        fadeOutFrom = volume;
    }

    public boolean fadingOut() {
        return fadeOutLeft >= 0;
    }

    @Override
    public void tick() {
        age++;
        if (fadeOutLeft >= 0) {
            fadeOutLeft--;
            volume = Math.max(0.001F, fadeOutFrom * fadeOutLeft / (float) fadeOutTicks);
            if (fadeOutLeft <= 0) stop();
        } else {
            volume = Math.min(1F, Math.max(0.001F, age / (float) fadeInTicks));
        }
    }
}
