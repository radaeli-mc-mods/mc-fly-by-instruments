package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkDevice;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkSnapshot;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.avionics.VelocitySensorEndpoint;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.OptionalDouble;

/**
 * Reads the Attitude Reference Units, Radar Altimeters and Velocity Sensors on a table's cable network and hands
 * them to {@link AttitudeIndicatorFeed}. Server only; never loads a chunk.
 */
final class AttitudeIndicatorSampler {
    private AttitudeIndicatorSampler() {}

    /**
     * @param source   the block the network was discovered from; device offsets are relative to it
     * @param topology that network
     */
    static AttitudeIndicatorFeed.Sample sample(ServerLevel level, ModularTableBlockEntity table, BlockPos source,
                                               NetworkSnapshot topology, boolean tapeEnabled,
                                               boolean speedTapeEnabled, long now) {
        if (topology.status() != NetworkStatus.CONNECTED) return AttitudeIndicatorFeed.Sample.DISCONNECTED;
        long maxAge = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get() * 2L;
        Object host = Sable.HELPER.getContaining(table);
        var units = new ArrayList<AttitudeIndicatorFeed.AttitudeSource>();
        var altimeters = new ArrayList<AttitudeIndicatorFeed.AltimeterSource>();
        var speeds = new ArrayList<AttitudeIndicatorFeed.SpeedSource>();
        for (NetworkDevice device : topology.devices()) {
            boolean unit = device.type().equals("attitude_reference_unit");
            boolean velocity = device.type().equals(VelocitySensorEndpoint.TYPE);
            if (!unit && !velocity && !device.type().equals("radar_altimeter")) continue;
            BlockPos at = source.offset(device.x(), device.y(), device.z());
            if (!level.hasChunkAt(at)) continue;
            BlockEntity found = level.getBlockEntity(at);
            long distance = (long) device.x() * device.x() + (long) device.y() * device.y()
                    + (long) device.z() * device.z();
            if (unit && found instanceof AttitudeReferenceUnitBlockEntity aru) {
                var reading = aru.reading();
                units.add(new AttitudeIndicatorFeed.AttitudeSource(distance,
                        Sable.HELPER.getContaining(aru) == host,
                        aru.sensorAvailability().isOperating(now, maxAge)
                                && fresh(reading.gameTime(), now, maxAge), reading));
            } else if (found instanceof RadarAltimeterBlockEntity radar) {
                var reading = radar.reading();
                altimeters.add(new AttitudeIndicatorFeed.AltimeterSource(
                        Sable.HELPER.getContaining(radar) == host,
                        radar.sensorAvailability().isOperating(now, maxAge),
                        radar.sensorAvailability().operating(),
                        fresh(reading.gameTime(), now, maxAge) && reading.status() == ReadingStatus.VALID
                                && Double.isFinite(reading.value().orElse(Double.NaN))
                                ? OptionalDouble.of(reading.value().orElseThrow()) : OptionalDouble.empty()));
            } else if (velocity && found instanceof VelocitySensorBlockEntity sensor) {
                SensorReading<Double> reading = VelocitySensorEndpoint.reading(sensor, now);
                speeds.add(new AttitudeIndicatorFeed.SpeedSource(distance,
                        Sable.HELPER.getContaining(sensor) == host,
                        VelocitySensorEndpoint.availability(reading).isOperating(now, maxAge),
                        reading.status() == ReadingStatus.VALID
                                ? OptionalDouble.of(reading.value().orElseThrow()) : OptionalDouble.empty()));
            }
        }
        return AttitudeIndicatorFeed.resolve(true, units, altimeters, speeds, tapeEnabled, speedTapeEnabled);
    }

    private static boolean fresh(long readingTime, long now, long maxAge) {
        return readingTime <= now && now - readingTime <= maxAge;
    }
}
