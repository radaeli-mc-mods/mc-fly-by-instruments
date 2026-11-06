package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkDevice;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkSnapshot;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.OptionalDouble;

/**
 * Reads the Attitude Reference Units and Radar Altimeters on a table's cable network and hands them to
 * {@link AttitudeIndicatorFeed}. Server only; never loads a chunk.
 */
final class AttitudeIndicatorSampler {
    private AttitudeIndicatorSampler() {}

    /**
     * @param source   the block the network was discovered from; device offsets are relative to it
     * @param topology that network
     */
    static AttitudeIndicatorFeed.Sample sample(ServerLevel level, ModularTableBlockEntity table, BlockPos source,
                                               NetworkSnapshot topology, boolean tapeEnabled, long now) {
        if (topology.status() != NetworkStatus.CONNECTED) return AttitudeIndicatorFeed.Sample.DISCONNECTED;
        long maxAge = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get() * 2L;
        Object host = Sable.HELPER.getContaining(table);
        var units = new ArrayList<AttitudeIndicatorFeed.AttitudeSource>();
        var altimeters = new ArrayList<AttitudeIndicatorFeed.AltimeterSource>();
        for (NetworkDevice device : topology.devices()) {
            boolean unit = device.type().equals("attitude_reference_unit");
            if (!unit && !device.type().equals("radar_altimeter")) continue;
            BlockPos at = source.offset(device.x(), device.y(), device.z());
            if (!level.hasChunkAt(at)) continue;
            BlockEntity found = level.getBlockEntity(at);
            if (unit && found instanceof AttitudeReferenceUnitBlockEntity aru) {
                var reading = aru.reading();
                long distance = (long) device.x() * device.x() + (long) device.y() * device.y()
                        + (long) device.z() * device.z();
                units.add(new AttitudeIndicatorFeed.AttitudeSource(distance,
                        Sable.HELPER.getContaining(aru) == host,
                        fresh(reading.gameTime(), now, maxAge), reading));
            } else if (found instanceof RadarAltimeterBlockEntity radar) {
                var reading = radar.reading();
                altimeters.add(new AttitudeIndicatorFeed.AltimeterSource(
                        Sable.HELPER.getContaining(radar) == host,
                        fresh(reading.gameTime(), now, maxAge),
                        reading.status() != ReadingStatus.FAILED,
                        reading.status() == ReadingStatus.VALID && Double.isFinite(reading.value().orElse(Double.NaN))
                                ? OptionalDouble.of(reading.value().orElseThrow()) : OptionalDouble.empty()));
            }
        }
        return AttitudeIndicatorFeed.resolve(true, units, altimeters, tapeEnabled);
    }

    private static boolean fresh(long readingTime, long now, long maxAge) {
        return readingTime <= now && now - readingTime <= maxAge;
    }
}
