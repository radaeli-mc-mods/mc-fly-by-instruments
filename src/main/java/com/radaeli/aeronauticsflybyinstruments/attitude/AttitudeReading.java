package com.radaeli.aeronauticsflybyinstruments.attitude;

/**
 * One published attitude sample. {@link Status#GROUND} is a block that is not on a moving
 * aircraft: it reports level flight (0, 0, 0) as a valid reading. {@link Status#DEGRADED} keeps the
 * last defined roll and heading while the nose is nearly vertical.
 */
public record AttitudeReading(Status status, double pitch, double roll, double heading, double turnRate,
                              double verticalSpeed, double slip, long gameTime) {
    public enum Status {
        VALID, DEGRADED, GROUND, UNAVAILABLE;

        public String lowerName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static AttitudeReading unavailable(long gameTime) {
        return new AttitudeReading(Status.UNAVAILABLE, 0, 0, 0, 0, 0, 0, gameTime);
    }

    public boolean hasValues() {
        return status != Status.UNAVAILABLE;
    }
}
