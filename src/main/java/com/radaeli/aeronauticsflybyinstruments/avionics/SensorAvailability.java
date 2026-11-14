package com.radaeli.aeronauticsflybyinstruments.avionics;

/** A sensor's live response, independent of whether it can currently measure anything. */
public record SensorAvailability(boolean operating, long gameTime) {
    public static final String CHANNEL = "sensor.available";
    public static final SensorAvailability UNKNOWN = new SensorAvailability(false, -1);

    public boolean isOperating(long now, long maxAge) {
        return operating && gameTime >= 0 && gameTime <= now && now - gameTime <= maxAge;
    }

    public AvionicsDataChannel<Boolean> channel(long now, long maxAge) {
        return new AvionicsDataChannel<>(CHANNEL,
                SensorReading.valid(isOperating(now, maxAge), "boolean", gameTime));
    }
}
