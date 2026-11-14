package com.radaeli.aeronauticsflybyinstruments.avionics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensorHeartbeatTest {
    @Test
    void ticksKeepSensorAvailableWithoutMeasurementsButStoppingExpiresAvailability() {
        var heartbeat = new SensorHeartbeat();
        assertFalse(heartbeat.snapshot().isOperating(0, 10));
        heartbeat.sampled(ReadingStatus.UNAVAILABLE, 5);
        heartbeat.tick(1000);
        assertTrue(heartbeat.snapshot().isOperating(1010, 10));
        assertFalse(heartbeat.snapshot().isOperating(1011, 10));
        assertFalse(heartbeat.snapshot().isOperating(999, 10));
        var channel = heartbeat.snapshot().channel(1011, 10);
        assertEquals(SensorAvailability.CHANNEL, channel.name());
        assertEquals(false, channel.reading().value().orElseThrow());
        assertEquals(1000, channel.reading().gameTime());
    }

    @Test
    void failurePersistsAcrossTicksUntilSuccessfulAcquisitionEvenWithNoReturn() {
        var heartbeat = new SensorHeartbeat();
        heartbeat.sampled(ReadingStatus.FAILED, 5);
        heartbeat.tick(6);
        assertFalse(heartbeat.snapshot().isOperating(6, 10));
        heartbeat.sampled(ReadingStatus.UNAVAILABLE, 10);
        assertTrue(heartbeat.snapshot().isOperating(10, 10));
        heartbeat.invalidate();
        assertFalse(heartbeat.snapshot().isOperating(10, 10));
    }
}
