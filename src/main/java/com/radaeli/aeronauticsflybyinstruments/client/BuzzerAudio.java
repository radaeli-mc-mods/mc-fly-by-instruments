package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.sound.ModSounds;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Keeps a nearby player's buzzer audible when the listening camera moves away. */
public final class BuzzerAudio {
    private BuzzerAudio() { }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !sound.getLocation().equals(ModSounds.BUZZER.getId())
                || sound.getSource() != SoundSource.BLOCKS || sound.isRelative()
                || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR) return;

        // PanelAudio already selects recipients and applies falloff from the player's
        // world position. Replace this instance so camera distance cannot attenuate
        // it a second time. World coordinates retain directional audio and subtitles.
        event.setSound(new PlayerDistanceSound(sound));
    }

    // PlaySoundEvent runs before sound resolution. Delegate resolution and gain/pitch
    // sampling so we retain the original seed and resource-pack sound properties.
    private record PlayerDistanceSound(SoundInstance original) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() { return original.getLocation(); }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) { return original.resolve(manager); }

        @Override
        public Sound getSound() { return original.getSound(); }

        @Override
        public SoundSource getSource() { return original.getSource(); }

        @Override
        public boolean isLooping() { return original.isLooping(); }

        @Override
        public boolean isRelative() { return original.isRelative(); }

        @Override
        public int getDelay() { return original.getDelay(); }

        @Override
        public float getVolume() { return original.getVolume(); }

        @Override
        public float getPitch() { return original.getPitch(); }

        @Override
        public double getX() { return original.getX(); }

        @Override
        public double getY() { return original.getY(); }

        @Override
        public double getZ() { return original.getZ(); }

        @Override
        public Attenuation getAttenuation() { return Attenuation.NONE; }
    }
}
