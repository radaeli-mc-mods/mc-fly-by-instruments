package com.radaeli.aeronauticsflybyinstruments.avionics;

import java.util.List;
import java.util.OptionalDouble;

/** One GPWS evaluation input assembled from already-computed sensor snapshots. */
public record GpwsSample(long gameTime, NetworkStatus networkStatus, int endpointCount,
                         int altimeterCount, boolean altimeterFault, OptionalDouble clearance,
                         long clearanceGameTime, int terrainScannerCount, boolean terrainScannerFault,
                         boolean terrainCoverageComplete, boolean pullUpTerrainCoverageComplete,
                         List<TerrainContact> terrainContacts, OptionalDouble bankAngleDegrees,
                         boolean noTerrainBelow) {
    public GpwsSample(long gameTime, NetworkStatus networkStatus, int endpointCount,
                      int altimeterCount, boolean altimeterFault, OptionalDouble clearance,
                      long clearanceGameTime, int terrainScannerCount, boolean terrainScannerFault,
                      boolean terrainCoverageComplete, boolean pullUpTerrainCoverageComplete,
                      List<TerrainContact> terrainContacts, OptionalDouble bankAngleDegrees) {
        this(gameTime, networkStatus, endpointCount, altimeterCount, altimeterFault, clearance, clearanceGameTime,
                terrainScannerCount, terrainScannerFault, terrainCoverageComplete, pullUpTerrainCoverageComplete,
                terrainContacts, bankAngleDegrees, false);
    }

    public GpwsSample {
        if (networkStatus == null || clearance == null || bankAngleDegrees == null || terrainContacts == null) {
            throw new NullPointerException("GPWS sample fields cannot be null");
        }
        if (endpointCount < 0 || altimeterCount < 0 || terrainScannerCount < 0) {
            throw new IllegalArgumentException("Sensor and network counts cannot be negative");
        }
        if (noTerrainBelow && (altimeterCount == 0 || clearance.isPresent())) {
            throw new IllegalArgumentException("No terrain below requires an altimeter without measured clearance");
        }
        if (clearance.isPresent() && (!Double.isFinite(clearance.getAsDouble()) || clearance.getAsDouble() < 0)
                || bankAngleDegrees.isPresent() && !Double.isFinite(bankAngleDegrees.getAsDouble())) {
            throw new IllegalArgumentException("GPWS measurements must be finite and clearance cannot be negative");
        }
        terrainContacts = List.copyOf(terrainContacts);
    }

    public record TerrainContact(double distance, double angleFromForwardDegrees) {
        public TerrainContact {
            if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(angleFromForwardDegrees)
                    || angleFromForwardDegrees < 0 || angleFromForwardDegrees > 180) {
                throw new IllegalArgumentException("Invalid terrain contact");
            }
        }
    }
}
