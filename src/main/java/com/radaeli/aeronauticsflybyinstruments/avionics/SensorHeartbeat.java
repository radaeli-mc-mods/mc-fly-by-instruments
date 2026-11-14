package com.radaeli.aeronauticsflybyinstruments.avionics;

/** Tick heartbeat; a failed acquisition stays failed until a subsequent acquisition succeeds. */
public final class SensorHeartbeat {
    private SensorAvailability availability = SensorAvailability.UNKNOWN;
    private boolean failed;

    public SensorAvailability snapshot() { return availability; }

    public void tick(long gameTime) {
        availability = new SensorAvailability(!failed, gameTime);
    }

    public void sampled(ReadingStatus status, long gameTime) {
        failed = status == ReadingStatus.FAILED;
        tick(gameTime);
    }

    public void invalidate() {
        availability = SensorAvailability.UNKNOWN;
    }
}
