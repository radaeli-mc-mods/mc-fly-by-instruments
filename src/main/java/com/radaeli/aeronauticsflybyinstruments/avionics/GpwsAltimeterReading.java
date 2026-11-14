package com.radaeli.aeronauticsflybyinstruments.avionics;

import java.util.OptionalDouble;

/** Measurement availability and sensor health are separate GPWS inputs. */
record GpwsAltimeterReading(OptionalDouble clearance, boolean noTerrainBelow, boolean fault) {
    private static final double NEAR_RANGE_LIMIT = 0.9;

    static GpwsAltimeterReading resolve(SensorReading<Double> reading, SensorReading<Double> lastValid,
                                        SensorAvailability availability, int maxRange, long now, long maxAge) {
        if (!availability.isOperating(now, maxAge) || reading.status() == ReadingStatus.FAILED) {
            return new GpwsAltimeterReading(OptionalDouble.empty(), false, true);
        }
        boolean fresh = reading.gameTime() <= now && now - reading.gameTime() <= maxAge;
        if (fresh && reading.status() == ReadingStatus.VALID) {
            double value = reading.value().orElse(Double.NaN);
            if (Double.isFinite(value) && value >= 0) {
                return new GpwsAltimeterReading(OptionalDouble.of(value), false, false);
            }
            return new GpwsAltimeterReading(OptionalDouble.empty(), false, true);
        }
        double previous = lastValid.value().orElse(Double.NaN);
        boolean beyondRange = lastValid.status() == ReadingStatus.VALID && lastValid.gameTime() <= now
                && Double.isFinite(previous) && previous >= maxRange * NEAR_RANGE_LIMIT && previous <= maxRange;
        return new GpwsAltimeterReading(OptionalDouble.empty(), beyondRange, false);
    }
}
