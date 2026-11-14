package com.radaeli.aeronauticsflybyinstruments.avionics;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One sensor as the Avionics Computer sees it: role, health and live reading. A terrain scanner's
 * role is the side the player gave it ({@link ScannerSide}), never inferred.
 * Coordinates are relative to the computer, like the Network Bridge's device list.
 */
public record AvionicsSensor(String type, String role, int x, int y, int z, String status,
                             OptionalDouble value, String unit, String issue) {
    public static final String OK = "ok";
    public static final String STALE = "stale";
    public static final String FAILED = "failed";
    public static final String UNLOADED = "unloaded";
    public static final String OTHER_SUBLEVEL = "other_sublevel";

    public static final String ISSUE_NONE = "";
    public static final String ISSUE_NO_READING = "no_reading";
    /** Legacy issue id; the GPWS now accepts narrow apertures without emitting this issue. */
    public static final String ISSUE_NARROW_APERTURE = "narrow_aperture";
    /** The player has not told the scanner which side of the aircraft it looks at. */
    public static final String ISSUE_NO_SIDE = "no_side";
    /** The last sweep did not cover every ray (unloaded chunks). */
    public static final String ISSUE_PARTIAL_COVERAGE = "partial_coverage";

    public AvionicsSensor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(issue, "issue");
    }

    /** Same sensor, same role, same health: only the reading may differ. */
    public boolean sameIdentityAndHealth(AvionicsSensor other) {
        return other != null && type.equals(other.type) && role.equals(other.role) && x == other.x
                && y == other.y && z == other.z && status.equals(other.status) && issue.equals(other.issue);
    }
}
