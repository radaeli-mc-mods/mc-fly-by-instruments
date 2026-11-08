package com.radaeli.aeronauticsflybyinstruments.avionics;

import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;

import java.util.List;

/**
 * Makes the Simulated Velocity Sensor a cable endpoint without changing it. The sensor measures the speed of its
 * own point along its face, signed: positive where the face points. It publishes that as {@link #AXIAL_CHANNEL}
 * in blocks per second. Outside a sublevel nothing is measured, so the reading is unavailable, not zero.
 */
public final class VelocitySensorEndpoint {
    public static final String TYPE = "velocity_sensor";
    public static final String AXIAL_CHANNEL = "velocity.axial";
    public static final String UNIT = "blocks/s";

    private VelocitySensorEndpoint() {}

    public static boolean is(Object blockEntity) {
        return blockEntity instanceof VelocitySensorBlockEntity;
    }

    /** The reading as the network sees it: a sensor outside a sublevel is unavailable, a NaN is a failure. */
    public static SensorReading<Double> reading(boolean onSubLevel, double value, long gameTime) {
        if (!onSubLevel) return SensorReading.unavailable(UNIT, gameTime);
        if (!Double.isFinite(value)) return SensorReading.failed(UNIT, gameTime);
        return SensorReading.valid(value, UNIT, gameTime);
    }

    public static SensorReading<Double> reading(VelocitySensorBlockEntity sensor, long gameTime) {
        return reading(Sable.HELPER.getContaining(sensor) != null, sensor.getAdjustedVelocity(), gameTime);
    }

    public static List<AvionicsDataChannel<?>> signals(VelocitySensorBlockEntity sensor, long gameTime) {
        return List.of(new AvionicsDataChannel<>(AXIAL_CHANNEL, reading(sensor, gameTime)));
    }
}
