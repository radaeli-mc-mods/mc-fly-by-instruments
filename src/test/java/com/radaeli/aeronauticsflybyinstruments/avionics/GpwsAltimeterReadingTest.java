package com.radaeli.aeronauticsflybyinstruments.avionics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GpwsAltimeterReadingTest {
    @ParameterizedTest
    @ValueSource(doubles = {115.2, 125, 128})
    void lastReturnNearRangeMeansNoTerrainBelowWhileSensorKeepsResponding(double previous) {
        var input = GpwsAltimeterReading.resolve(SensorReading.unavailable("blocks", 20),
                SensorReading.valid(previous, "blocks", 15), new SensorAvailability(true, 25), 128, 25, 10);
        assertTrue(input.noTerrainBelow());
        assertFalse(input.fault());
        assertTrue(input.clearance().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 32, 115.19, 129, Double.NaN, Double.POSITIVE_INFINITY})
    void missingReturnWithoutNearRangeHistoryIsUnknownTerrainButNotSensorFailure(double previous) {
        var input = GpwsAltimeterReading.resolve(SensorReading.unavailable("blocks", 20),
                SensorReading.valid(previous, "blocks", 15), new SensorAvailability(true, 25), 128, 25, 10);
        assertFalse(input.noTerrainBelow());
        assertFalse(input.fault());
        assertTrue(input.clearance().isEmpty());
    }

    @Test
    void honorsConfiguredRange() {
        var reading = SensorReading.<Double>unavailable("blocks", 20);
        var last = SensorReading.valid(60.0, "blocks", 15);
        var availability = new SensorAvailability(true, 25);
        assertTrue(GpwsAltimeterReading.resolve(reading, last, availability, 64, 25, 10).noTerrainBelow());
        assertFalse(GpwsAltimeterReading.resolve(reading, last, availability, 128, 25, 10).noTerrainBelow());
    }

    @Test
    void realTerrainReturnTakesPriorityOverNearRangeHistory() {
        var input = GpwsAltimeterReading.resolve(SensorReading.valid(3.0, "blocks", 20),
                SensorReading.valid(125.0, "blocks", 15), new SensorAvailability(true, 25), 128, 25, 10);
        assertEquals(3.0, input.clearance().orElseThrow());
        assertFalse(input.noTerrainBelow());
        assertFalse(input.fault());
    }

    @Test
    void nearRangeHistoryDoesNotHideFailedOrInvalidReadings() {
        for (var reading : java.util.List.of(
                SensorReading.<Double>failed("blocks", 20),
                SensorReading.valid(Double.NaN, "blocks", 20),
                SensorReading.valid(Double.POSITIVE_INFINITY, "blocks", 20),
                SensorReading.valid(-1.0, "blocks", 20))) {
            var input = GpwsAltimeterReading.resolve(reading, SensorReading.valid(125.0, "blocks", 15),
                    new SensorAvailability(true, 25), 128, 25, 10);
            assertTrue(input.fault(), reading.toString());
            assertFalse(input.noTerrainBelow(), reading.toString());
        }
    }

    @Test
    void historyCanOutliveTheMeasurementWhileHeartbeatMustRemainFresh() {
        var last = SensorReading.valid(125.0, "blocks", 15);
        var missing = SensorReading.<Double>unavailable("blocks", 20);
        var alive = GpwsAltimeterReading.resolve(missing, last, new SensorAvailability(true, 10000), 128, 10000, 10);
        assertTrue(alive.noTerrainBelow());
        assertFalse(alive.fault());
        for (var availability : java.util.List.of(new SensorAvailability(true, 9989),
                new SensorAvailability(true, 10001), new SensorAvailability(false, 10000), SensorAvailability.UNKNOWN)) {
            var lost = GpwsAltimeterReading.resolve(missing, last, availability, 128, 10000, 10);
            assertTrue(lost.fault());
            assertFalse(lost.noTerrainBelow());
        }
    }

    @Test
    void aNewSensorWithoutHistoryDoesNotClaimClearOrFailed() {
        var missing = SensorReading.<Double>unavailable("blocks", 20);
        var result = GpwsAltimeterReading.resolve(missing, missing, new SensorAvailability(true, 25), 128, 25, 10);
        assertFalse(result.noTerrainBelow());
        assertFalse(result.fault());
        assertTrue(result.clearance().isEmpty());
    }
}
