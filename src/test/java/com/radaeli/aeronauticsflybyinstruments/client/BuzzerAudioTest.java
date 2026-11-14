package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.panel.PanelAlertLogic;
import com.radaeli.aeronauticsflybyinstruments.sound.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuzzerAudioTest {
    @Test
    void nearbyPlayerHearsOnePositionalBeepWithoutCameraDistanceFalloff() {
        // At 50% volume, a player four blocks away should hear gain .25 even
        // when a contraption camera moves beyond the engine's 16-block range.
        var original = new TestBuzzer(1, 1);
        var event = new PlaySoundEvent(null, original);

        BuzzerAudio.onPlaySound(event);

        SoundInstance beep = event.getSound();
        assertNotNull(beep);
        assertNull(beep.getSound(), "Wrapping must not resolve the sound prematurely");
        assertSame(original.definition, beep.resolve(null));
        assertSame(original.getSound(), beep.getSound());
        assertEquals(SoundInstance.Attenuation.NONE, beep.getAttenuation());
        assertEquals(.25f, beep.getVolume());
        assertEquals(1.25f, beep.getPitch());
        assertEquals(SoundSource.BLOCKS, beep.getSource());
        assertEquals(original.getLocation(), beep.getLocation());
        assertFalse(beep.isRelative());
        assertFalse(beep.isLooping());
        assertEquals(original.getX(), beep.getX());
        assertEquals(original.getY(), beep.getY());
        assertEquals(original.getZ(), beep.getZ());
        // A repeated event pass must keep the same single replacement instance.
        BuzzerAudio.onPlaySound(event);
        assertSame(beep, event.getSound());
    }

    @Test
    void resourcePackVolumeAndPitchAreAppliedOnce() {
        var original = new TestBuzzer(.5f, .8f);
        var event = new PlaySoundEvent(null, original);
        BuzzerAudio.onPlaySound(event);
        SoundInstance beep = event.getSound();
        assertNotNull(beep);
        beep.resolve(null);
        assertEquals(.125f, beep.getVolume(), .0001f);
        assertEquals(1, beep.getPitch(), .0001f);
    }

    @Test
    void otherSoundsAndTutorialBeepsKeepTheirExistingPlayback() {
        var ordinary = new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS,
                .5f, 1, SoundInstance.createUnseededRandom(), 0, 0, 0);
        var tutorial = SimpleSoundInstance.forUI(ModSounds.BUZZER.get(), 1, .3f);
        for (SoundInstance sound : new SoundInstance[]{ordinary, tutorial}) {
            var event = new PlaySoundEvent(null, sound);
            BuzzerAudio.onPlaySound(event);
            assertSame(sound, event.getSound());
        }
    }

    @Test
    void aCancelledSoundStaysCancelled() {
        var event = new PlaySoundEvent(null, SimpleSoundInstance.forUI(ModSounds.BUZZER.get(), 1));
        event.setSound(null);
        BuzzerAudio.onPlaySound(event);
        assertNull(event.getSound());
    }

    /** Resolves an actual sound definition without opening an audio device or Minecraft window. */
    private static final class TestBuzzer extends SimpleSoundInstance {
        private final WeighedSoundEvents definition = new WeighedSoundEvents(ModSounds.BUZZER.getId(), null);

        TestBuzzer(float resourceVolume, float resourcePitch) {
            super(ModSounds.BUZZER.get(), SoundSource.BLOCKS, PanelAlertLogic.audibleGain(50, 4),
                    1.25f, SoundInstance.createUnseededRandom(), 120.5, 80.25, -40.75);
            definition.addSound(new Sound(ModSounds.BUZZER.getId(), ConstantFloat.of(resourceVolume),
                    ConstantFloat.of(resourcePitch), 1, Sound.Type.FILE, false, false, 16));
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            sound = definition.getSound(random);
            return definition;
        }
    }
}
