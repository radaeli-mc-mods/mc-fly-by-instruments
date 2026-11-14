package com.radaeli.aeronauticsflybyinstruments.sound;

import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelSpatial;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Sends each short buzzer beep only to players inside its configured global-world radius. */
public final class PanelAudio {
    private PanelAudio() { }

    public static void play(ServerLevel level, ModularTableBlockEntity table, PanelGrid.Module module,
                            int severity, int volumePercent) {
        if (volumePercent < 1 || volumePercent > 100 || severity < 1 || severity > 3) return;
        Vec3 source = PanelSpatial.moduleWorldPosition(table, module);
        double radius = com.radaeli.aeronauticsflybyinstruments.panel.PanelAlertLogic.audibleRadius(volumePercent);
        float pitch = switch (severity) { case 1 -> 0.75f; case 2 -> 1.0f; default -> 1.25f; };
        for (ServerPlayer player : level.players()) {
            double distance = com.radaeli.aeronauticsflybyinstruments.panel.PanelSpatial
                    .entityWorldPosition(player).distanceTo(source);
            if (distance >= radius) continue;
            float requestedGain = com.radaeli.aeronauticsflybyinstruments.panel.PanelAlertLogic.audibleGain(
                    volumePercent, distance);
            // The client preserves 3D panning but disables camera-distance attenuation.
            // Apply the configured falloff here, using the player's physical position only.
            player.connection.send(new ClientboundSoundPacket(Holder.direct(ModSounds.BUZZER.get()),
                    SoundSource.BLOCKS, source.x, source.y, source.z, requestedGain, pitch,
                    level.random.nextLong()));
        }
    }
}
