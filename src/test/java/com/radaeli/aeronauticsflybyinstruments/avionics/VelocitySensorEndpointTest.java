package com.radaeli.aeronauticsflybyinstruments.avionics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelocitySensorEndpointTest {
    @Test
    void aSensorOnASublevelPublishesItsSignedSpeedInBlocksPerSecond() {
        var reading = VelocitySensorEndpoint.reading(true, -4.25, 120L);
        assertEquals(ReadingStatus.VALID, reading.status());
        assertEquals(-4.25, reading.value().orElseThrow(), 1e-9, "backwards along the face is negative");
        assertEquals(VelocitySensorEndpoint.UNIT, reading.unit());
        assertEquals("blocks/s", reading.unit());
        assertEquals(120L, reading.gameTime());
    }

    @Test
    void aSensorOffTheAircraftIsUnavailableNotZero() {
        var reading = VelocitySensorEndpoint.reading(false, 0.0, 7L);
        assertEquals(ReadingStatus.UNAVAILABLE, reading.status());
        assertTrue(reading.value().isEmpty());
        assertTrue(VelocitySensorEndpoint.availability(reading).isOperating(7, 0));
    }

    @Test
    void aNonFiniteValueIsAFailure() {
        assertEquals(ReadingStatus.FAILED, VelocitySensorEndpoint.reading(true, Double.NaN, 1L).status());
        assertEquals(ReadingStatus.FAILED, VelocitySensorEndpoint.reading(true, Double.POSITIVE_INFINITY, 1L).status());
        assertFalse(VelocitySensorEndpoint.availability(VelocitySensorEndpoint.reading(true, Double.NaN, 1))
                .isOperating(1, 0));
    }

    @Test
    void theTypeAndChannelNamesAreStable() {
        assertEquals("velocity_sensor", VelocitySensorEndpoint.TYPE);
        assertEquals("velocity.axial", VelocitySensorEndpoint.AXIAL_CHANNEL);
        assertEquals(List.of(), AvionicsEndpoints.signalsOf(null, null), "nothing to publish without a block entity");
        assertNull(AvionicsEndpoints.typeOf(null));
    }
}
