package com.radaeli.aeronauticsflybyinstruments.client;

/**
 * What the Attitude Indicator display shows at one instant. In the game it comes from the server's feed (the
 * Attitude Reference Unit, the Radar Altimeter and the Velocity Sensor on the table's network) through
 * {@link AttitudeDisplayFeed};
 * previews and tests use {@link AttitudeDisplayMock}. The layout does not know the difference.
 *
 * @param pitchDegrees nose up positive
 * @param rollDegrees  right wing down positive (aviation convention)
 * @param aglBlocks    height above ground in blocks, NaN when the radar has no reading ("----")
 * @param speed        speed along the velocity sensor's face in blocks per second, NaN without a reading ("---")
 * @param altitudeTape 0..1: how much of the altitude reading is shown (0 off, 1 full); animates the reflow
 * @param speedTape    0..1: same for the speed reading
 */
record AttitudeDisplayState(Status status, double pitchDegrees, double rollDegrees, double aglBlocks, double speed,
                            float altitudeTape, float speedTape) {
    enum Status {
        /** Valid attitude. */
        OK,
        /** Attitude still valid but near the gimbal limit (about 90 degrees of pitch): small amber ATT flag. */
        DEGRADED,
        /** No unit, incomplete network or unavailable: dark screen with a red ATT flag. */
        NO_DATA
    }

    AttitudeDisplayState withStatus(Status next) {
        return new AttitudeDisplayState(next, pitchDegrees, rollDegrees, aglBlocks, speed, altitudeTape, speedTape);
    }

    AttitudeDisplayState withTapes(float altitude, float speedAmount) {
        return new AttitudeDisplayState(status, pitchDegrees, rollDegrees, aglBlocks, speed, altitude, speedAmount);
    }
}
