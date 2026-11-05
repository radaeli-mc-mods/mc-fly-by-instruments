package com.radaeli.aeronauticsflybyinstruments.attitude;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The slip indicator: sideways specific force over the vertical one, in the aircraft frame. */
class AttitudeSlipTest {
    private static final Vec3 NORTH = new Vec3(0, 0, -1);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    void levelUnacceleratedFlightHasNoSlip() {
        assertEquals(0, AttitudeReferenceUnitBlockEntity.slip(NORTH, UP, Vec3.ZERO), 1.0E-9);
    }

    @Test
    void sidewaysAccelerationToTheRightIsPositiveSlip() {
        // Facing north, the right wing is east: a push toward east puts the ball on the right.
        double slip = AttitudeReferenceUnitBlockEntity.slip(NORTH, UP, new Vec3(3, 0, 0));
        assertEquals(Math.toDegrees(Math.atan2(3, 32)), slip, 1.0E-9);
        assertTrue(slip > 0);
        assertTrue(AttitudeReferenceUnitBlockEntity.slip(NORTH, UP, new Vec3(-3, 0, 0)) < 0);
    }

    @Test
    void verticalAccelerationAloneIsNotSlip() {
        assertEquals(0, AttitudeReferenceUnitBlockEntity.slip(NORTH, UP, new Vec3(0, 5, 0)), 1.0E-9);
    }

    @Test
    void theRightAxisFollowsTheAircraftNotTheWorld() {
        // Banked 90 degrees right (right wing down): the aircraft's right is down. Without acceleration
        // the sensor feels gravity along that axis, so the slip reads -90 (the ball sits on the low, right wing).
        Vec3 bankedUp = new Vec3(1, 0, 0);
        assertEquals(-90, AttitudeReferenceUnitBlockEntity.slip(NORTH, bankedUp, Vec3.ZERO), 1.0E-9);
    }
}
