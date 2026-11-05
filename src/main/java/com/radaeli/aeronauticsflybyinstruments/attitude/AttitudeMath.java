package com.radaeli.aeronauticsflybyinstruments.attitude;

import com.radaeli.aeronauticsflybyinstruments.avionics.GpwsAttitude;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalDouble;

/**
 * Pitch, roll and heading of an aircraft from its nose and up vectors, both in world space.
 * Pitch is positive with the nose up, roll positive with the right wing down, heading is clockwise
 * from north (Minecraft: north is -Z, east is +X), the same convention as the FMS Compass.
 */
public final class AttitudeMath {
    /** Beyond this pitch the roll and the heading stop being defined (the nose is nearly vertical). */
    public static final double ROLL_LIMIT_PITCH = 85.0;
    /** Largest level calibration an installer may record on either axis. */
    public static final double CALIBRATION_LIMIT = 15.0;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    private AttitudeMath() {}

    public static double pitch(Vec3 forward) {
        Vec3 nose = forward.normalize();
        return Math.toDegrees(Math.asin(Math.clamp(nose.dot(WORLD_UP), -1.0, 1.0)));
    }

    /** Empty when the nose is (nearly) vertical: its horizontal projection has no direction. */
    public static OptionalDouble heading(Vec3 forward) {
        double east = forward.x;
        double north = -forward.z;
        if (east * east + north * north < 1.0E-8) return OptionalDouble.empty();
        return OptionalDouble.of(wrapHeading(Math.toDegrees(Math.atan2(east, north))));
    }

    /** Empty when the nose is vertical, where the wings have no defined level. */
    public static OptionalDouble roll(Vec3 forward, Vec3 up) {
        return GpwsAttitude.bankDegrees(forward, up);
    }

    public static boolean rollDefined(double pitch) {
        return Math.abs(pitch) <= ROLL_LIMIT_PITCH;
    }

    /** Maps any angle to [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double wrapped = (degrees + 180.0) % 360.0;
        if (wrapped < 0) wrapped += 360.0;
        return wrapped - 180.0;
    }

    /** Maps any angle to [0, 360). */
    public static double wrapHeading(double degrees) {
        double wrapped = degrees % 360.0;
        return wrapped < 0 ? wrapped + 360.0 : wrapped;
    }

    /** Signed change from one angle to another along the short way round. */
    public static double shortestDelta(double from, double to) {
        return wrapDegrees(to - from);
    }
}
