package com.radaeli.aeronauticsflybyinstruments.avionics;

import net.minecraft.world.phys.Vec3;

/** Separates terrain below a projected flight path from terrain the aircraft is approaching. */
public final class GpwsTerrainPath {
    private static final double MINIMUM_SPEED = 0.5;
    /** Keep one block below the sensing face inside the protected flight path. */
    private static final double VERTICAL_MARGIN = 1.0;

    private GpwsTerrainPath() {}

    public static boolean threatens(Vec3 offset, Vec3 velocity) {
        // Unknown motion must not hide a known obstacle.
        if (!finite(offset) || !finite(velocity)) return true;
        double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        if (horizontalSpeed < MINIMUM_SPEED) {
            return velocity.y < -MINIMUM_SPEED || offset.y >= -VERTICAL_MARGIN;
        }
        double along = (offset.x * velocity.x + offset.z * velocity.z) / horizontalSpeed;
        if (along < -VERTICAL_MARGIN) return false;
        double projectedHeight = velocity.y * Math.max(0.0, along) / horizontalSpeed;
        return offset.y >= projectedHeight - VERTICAL_MARGIN;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
