package com.radaeli.aeronauticsflybyinstruments.client;

/**
 * Sample values for the preview images and layout tests: a slow, gentle flight pattern so pitch, bank and the
 * readings all move. The game does not use it for attitude (the table draws the feed of
 * {@link AttitudeDisplayClient}); it does use {@link #speed} until there is an airspeed sensor.
 */
final class AttitudeDisplayMock {
    private AttitudeDisplayMock() {}

    static AttitudeDisplayState sample(double seconds) {
        double pitch = 6 + 9 * Math.sin(seconds * 0.45);
        double roll = 24 * Math.sin(seconds * 0.3);
        double agl = 150 + 70 * Math.sin(seconds * 0.2);
        return new AttitudeDisplayState(AttitudeDisplayState.Status.OK, pitch, roll, agl, speed(seconds), 1f, 1f);
    }

    /** Fictitious airspeed in blocks per second (TEMPORARY: stands in for a speed sensor while the band is tried). */
    static double speed(double seconds) {
        return 24 + 6 * Math.sin(seconds * 0.25) + 2 * Math.sin(seconds * 0.9);
    }
}
