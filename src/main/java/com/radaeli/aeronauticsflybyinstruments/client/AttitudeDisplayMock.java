package com.radaeli.aeronauticsflybyinstruments.client;

/**
 * Sample values for the preview images and layout tests: a slow, gentle flight pattern so pitch, bank and the
 * readings all move. The game does not use it: the table draws the feed of {@link AttitudeDisplayClient}.
 */
final class AttitudeDisplayMock {
    private AttitudeDisplayMock() {}

    static AttitudeDisplayState sample(double seconds) {
        double pitch = 6 + 9 * Math.sin(seconds * 0.45);
        double roll = 24 * Math.sin(seconds * 0.3);
        double agl = 150 + 70 * Math.sin(seconds * 0.2);
        return new AttitudeDisplayState(AttitudeDisplayState.Status.OK, pitch, roll, agl, speed(seconds), 1f, 1f);
    }

    /** Fictitious airspeed in blocks per second, for previews and layout tests. */
    static double speed(double seconds) {
        return 24 + 6 * Math.sin(seconds * 0.25) + 2 * Math.sin(seconds * 0.9);
    }
}
