package com.radaeli.aeronauticsflybyinstruments.avionics;

/** Sensors publish availability even when their measurement is unavailable. */
public interface AvionicsSensorEndpoint extends AvionicsEndpoint {
    SensorAvailability sensorAvailability();
}
