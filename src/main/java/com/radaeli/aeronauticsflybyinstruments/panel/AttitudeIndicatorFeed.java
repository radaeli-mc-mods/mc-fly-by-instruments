package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeMath;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReading;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * What an Attitude Indicator shows, chosen from the devices on the table's cable network. Pure: the sampler
 * gathers the candidates from the world and this class decides which one wins and why another does not.
 *
 * <p>The attitude comes from the nearest Attitude Reference Unit that is on the same aircraft as the table and
 * has a fresh reading. The height comes from the Radar Altimeter with the lowest clearance, the same rule the
 * Avionics Computer uses for its primary altimeter. The speed comes from the nearest Velocity Sensor on the same
 * aircraft with a valid reading. Without a usable unit the display is {@link Status#NO_DATA} (the red ATT flag);
 * without a usable altimeter or sensor only its tape suffers ("----" or "---").
 */
public final class AttitudeIndicatorFeed {
    /** Roll and pitch change that makes a new sample worth sending to the clients, in degrees. */
    static final double ANGLE_STEP = 0.25;
    /** Height change that makes a new sample worth sending, in blocks. */
    static final double HEIGHT_STEP = 0.5;
    /** Speed change that makes a new sample worth sending, in blocks per second. */
    static final double SPEED_STEP = 0.5;

    private AttitudeIndicatorFeed() {}

    /** How the display reads the attitude. Declaration order is the wire order: never reorder. */
    public enum Status {
        OK, DEGRADED, NO_DATA;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** State of one source on the network, for the display and for the configuration screen. */
    public enum Link {
        /** A usable device answers. */
        OK,
        /** The cable network is incomplete or the table is not on one. */
        NO_NETWORK,
        /** The network is complete and has no such device. */
        NONE,
        /** Every such device sits on another aircraft than the table. */
        OTHER_VESSEL,
        /** Such devices exist on this aircraft but none has a fresh, healthy reading. */
        STALE;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Link of(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NO_NETWORK;
        }
    }

    /** An Attitude Reference Unit seen on the network. {@code distanceSquared} is from the cable source. */
    public record AttitudeSource(long distanceSquared, boolean sameVessel, boolean fresh, AttitudeReading reading) {
        boolean usable() { return sameVessel && fresh && reading.hasValues(); }
    }

    /**
     * A Radar Altimeter seen on the network. {@code healthy} is false for a failed scan; a healthy altimeter with
     * no {@code height} simply has no ground in range.
     */
    public record AltimeterSource(boolean sameVessel, boolean fresh, boolean healthy, OptionalDouble height) {
        boolean usable() { return sameVessel && fresh && healthy; }
    }

    /**
     * A Velocity Sensor seen on the network: the signed speed along its face in blocks per second, when it has a
     * valid reading. {@code distanceSquared} is from the cable source.
     */
    public record SpeedSource(long distanceSquared, boolean sameVessel, boolean fresh, OptionalDouble speed) {
        boolean usable() { return sameVessel && fresh && speed.isPresent(); }
    }

    /**
     * One resolved sample. {@code agl} is NaN without a height and {@code speed} NaN without a speed;
     * {@code altitudeTape} and {@code speedTape} are whether each tape is shown (switched on in the module and its
     * source is on the network).
     */
    public record Sample(Status status, Link attitude, Link altimeter, double pitch, double roll, double agl,
                         boolean altitudeTape, Link velocity, double speed, boolean speedTape) {
        public static final Sample DISCONNECTED = new Sample(Status.NO_DATA, Link.NO_NETWORK, Link.NO_NETWORK,
                0, 0, Double.NaN, false, Link.NO_NETWORK, Double.NaN, false);

        public Sample(Status status, Link attitude, Link altimeter, double pitch, double roll, double agl,
                      boolean altitudeTape) {
            this(status, attitude, altimeter, pitch, roll, agl, altitudeTape, Link.NONE, Double.NaN, false);
        }

        /** Whether {@code other} differs enough from this sample to be worth a packet. */
        public boolean differsFrom(Sample other) {
            if (other == null || status != other.status || attitude != other.attitude
                    || altimeter != other.altimeter || altitudeTape != other.altitudeTape
                    || velocity != other.velocity || speedTape != other.speedTape) return true;
            if (Math.abs(pitch - other.pitch) >= ANGLE_STEP
                    || Math.abs(AttitudeMath.shortestDelta(roll, other.roll)) >= ANGLE_STEP) return true;
            if (Double.isNaN(agl) != Double.isNaN(other.agl)) return true;
            if (!Double.isNaN(agl) && Math.abs(agl - other.agl) >= HEIGHT_STEP) return true;
            if (Double.isNaN(speed) != Double.isNaN(other.speed)) return true;
            return !Double.isNaN(speed) && Math.abs(speed - other.speed) >= SPEED_STEP;
        }
    }

    public static Sample resolve(boolean connected, List<AttitudeSource> units, List<AltimeterSource> altimeters,
                                 boolean tapeEnabled) {
        return resolve(connected, units, altimeters, List.of(), tapeEnabled, false);
    }

    public static Sample resolve(boolean connected, List<AttitudeSource> units, List<AltimeterSource> altimeters,
                                 List<SpeedSource> speeds, boolean tapeEnabled, boolean speedTapeEnabled) {
        if (!connected) return Sample.DISCONNECTED;

        AttitudeSource unit = null;
        boolean unitOnThisVessel = false;
        for (AttitudeSource candidate : units) {
            unitOnThisVessel |= candidate.sameVessel();
            if (candidate.usable() && (unit == null || candidate.distanceSquared() < unit.distanceSquared())) {
                unit = candidate;
            }
        }
        Link attitude = unit != null ? Link.OK : link(units.isEmpty(), unitOnThisVessel);

        Double lowest = null;
        boolean altimeterUsable = false, altimeterOnThisVessel = false;
        for (AltimeterSource candidate : altimeters) {
            altimeterOnThisVessel |= candidate.sameVessel();
            if (!candidate.usable()) continue;
            altimeterUsable = true;
            if (candidate.height().isPresent() && (lowest == null || candidate.height().getAsDouble() < lowest)) {
                lowest = candidate.height().getAsDouble();
            }
        }
        Link altimeter = altimeterUsable ? Link.OK : link(altimeters.isEmpty(), altimeterOnThisVessel);
        boolean tape = tapeEnabled && !altimeters.isEmpty();
        double agl = tape && lowest != null ? lowest : Double.NaN;

        SpeedSource speedSource = null;
        boolean speedOnThisVessel = false;
        for (SpeedSource candidate : speeds) {
            speedOnThisVessel |= candidate.sameVessel();
            if (candidate.usable() && (speedSource == null || candidate.distanceSquared() < speedSource.distanceSquared())) {
                speedSource = candidate;
            }
        }
        Link velocity = speedSource != null ? Link.OK : link(speeds.isEmpty(), speedOnThisVessel);
        boolean speedTape = speedTapeEnabled && !speeds.isEmpty();
        double speed = speedSource != null ? speedSource.speed().getAsDouble() : Double.NaN;

        if (unit == null) {
            return new Sample(Status.NO_DATA, attitude, altimeter, 0, 0, Double.NaN, tape, velocity, speed, speedTape);
        }
        AttitudeReading reading = unit.reading();
        Status status = reading.status() == AttitudeReading.Status.DEGRADED ? Status.DEGRADED : Status.OK;
        return new Sample(status, Link.OK, altimeter, reading.pitch(), reading.roll(), agl, tape, velocity, speed,
                speedTape);
    }

    private static Link link(boolean none, boolean onThisVessel) {
        return none ? Link.NONE : onThisVessel ? Link.STALE : Link.OTHER_VESSEL;
    }
}
