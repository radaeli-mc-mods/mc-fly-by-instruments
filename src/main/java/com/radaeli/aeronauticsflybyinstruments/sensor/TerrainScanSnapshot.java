package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;

import java.util.List;
import java.util.Objects;

/** A scan result with explicit coverage so partial knowledge cannot look like an empty, complete scan. */
public record TerrainScanSnapshot(ReadingStatus status, List<TerrainScanContact> contacts, int sampledRays,
                                  int totalRays, long gameTime) {
    public TerrainScanSnapshot {
        Objects.requireNonNull(status, "status");
        contacts = List.copyOf(contacts);
        if (sampledRays < 0 || totalRays < 0 || sampledRays > totalRays) {
            throw new IllegalArgumentException("Invalid ray coverage counts");
        }
        if (status == ReadingStatus.VALID && sampledRays == 0) {
            throw new IllegalArgumentException("A valid scan must contain at least one completed ray");
        }
        if (status != ReadingStatus.VALID && !contacts.isEmpty()) {
            throw new IllegalArgumentException("Unavailable/failed scans cannot expose partial contacts");
        }
    }

    public double coverage() {
        return totalRays == 0 ? 0.0 : (double) sampledRays / totalRays;
    }

    public static TerrainScanSnapshot unavailable(int totalRays, long gameTime) {
        return new TerrainScanSnapshot(ReadingStatus.UNAVAILABLE, List.of(), 0, totalRays, gameTime);
    }

    public static TerrainScanSnapshot failed(int totalRays, long gameTime) {
        return new TerrainScanSnapshot(ReadingStatus.FAILED, List.of(), 0, totalRays, gameTime);
    }

    public static boolean meaningfullyChanged(TerrainScanSnapshot previous, TerrainScanSnapshot updated) {
        return previous.status() != updated.status()
                || previous.sampledRays() != updated.sampledRays()
                || previous.totalRays() != updated.totalRays()
                || !previous.contacts().equals(updated.contacts());
    }
}
