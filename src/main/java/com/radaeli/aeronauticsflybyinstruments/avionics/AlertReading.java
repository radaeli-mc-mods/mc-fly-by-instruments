package com.radaeli.aeronauticsflybyinstruments.avionics;

import java.util.Objects;

/** State of one alert channel; activation rules are supplied by the future GPWS engine. */
public record AlertReading(AlertChannel channel, AlertState state, AlertSeverity severity, long gameTime) {
    public static boolean stateChanged(AlertReading previous, AlertReading updated) {
        return previous == null || previous.state() != updated.state() || previous.severity() != updated.severity();
    }

    public AlertReading {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(severity, "severity");
    }
}
