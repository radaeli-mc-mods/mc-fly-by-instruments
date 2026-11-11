package com.radaeli.aeronauticsflybyinstruments.integration;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReading;
import com.radaeli.aeronauticsflybyinstruments.avionics.AlertChannel;
import com.radaeli.aeronauticsflybyinstruments.avionics.AlertReading;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsSignalSnapshot;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScanSnapshot;

import java.util.Map;

/** Domain events consumed by the optional computer integration. Defaults require no integration. */
public final class PeripheralEvents {
    private PeripheralEvents() {}

    public interface Table {
        default void moduleChanged(String action, PanelGrid.Module module) {}
        default void testSignalChanged(String channel, double value) {}
        default void alertOutputsChanged() {}
        default void panelStateChanged(String state, boolean value) {}
        default void fmsWaypointChanged(String action, PanelGrid.Module module, FmsWaypoint waypoint, boolean selected) {}
        default void fmsSignalChanged(ModularTableBlockEntity.FmsModuleAt module, FmsSignalSnapshot previous,
                                      FmsSignalSnapshot snapshot) {}
    }

    public interface Computer {
        default void publishStatusChanged() {}
        default void publishSensorsChanged() {}
        default void publishAlertsChanged(Map<AlertChannel, AlertReading> changed, Map<AlertChannel, String> reasons) {}
        default void publishConfigurationChanged() {}
    }

    public interface Bridge {
        default void refreshNetworkAndNotify() {}
    }

    public interface Radar {
        default void publishReading(SensorReading<Double> reading) {}
        default void publishOutputConfiguration() {}
        default void publishOutputs() {}
    }

    public interface Attitude {
        default void publishReading(AttitudeReading reading) {}
    }

    public interface Terrain {
        default void publishScan(TerrainScanSnapshot snapshot) {}
        default void publishConfiguration(int degrees) {}
        default void publishOutputConfiguration() {}
        default void publishOutputs() {}
    }
}
