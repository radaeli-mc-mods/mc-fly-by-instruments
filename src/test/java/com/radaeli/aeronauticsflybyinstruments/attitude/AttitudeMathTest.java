package com.radaeli.aeronauticsflybyinstruments.attitude;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pitch, roll and heading in the sign convention of the Attitude Reference Unit. */
class AttitudeMathTest {
    private static final double EPS = 1.0E-6;

    @Test
    void levelNorthNoseHasZeroPitchRollAndHeadingNorth() {
        Vec3 forward = new Vec3(0, 0, -1);
        Vec3 up = new Vec3(0, 1, 0);
        assertEquals(0, AttitudeMath.pitch(forward), EPS);
        assertEquals(0, AttitudeMath.roll(forward, up).orElseThrow(), EPS);
        assertEquals(0, AttitudeMath.heading(forward).orElseThrow(), EPS);
    }

    @Test
    void headingIsClockwiseFromNorth() {
        assertEquals(90, AttitudeMath.heading(new Vec3(1, 0, 0)).orElseThrow(), EPS);
        assertEquals(180, AttitudeMath.heading(new Vec3(0, 0, 1)).orElseThrow(), EPS);
        assertEquals(270, AttitudeMath.heading(new Vec3(-1, 0, 0)).orElseThrow(), EPS);
    }

    @Test
    void noseUpIsPositivePitchAndKeepsTheWingsLevel() {
        double p = Math.toRadians(30);
        Vec3 forward = new Vec3(0, Math.sin(p), -Math.cos(p));
        Vec3 up = new Vec3(0, Math.cos(p), Math.sin(p));
        assertEquals(30, AttitudeMath.pitch(forward), EPS);
        assertEquals(0, AttitudeMath.roll(forward, up).orElseThrow(), 1.0E-6);
        assertEquals(0, AttitudeMath.heading(forward).orElseThrow(), EPS);
    }

    @Test
    void rightWingDownIsPositiveRoll() {
        double r = Math.toRadians(20);
        // Facing north, the right wing is east; the up vector leans toward it when the right wing drops.
        Vec3 forward = new Vec3(0, 0, -1);
        Vec3 up = new Vec3(Math.sin(r), Math.cos(r), 0);
        assertEquals(20, AttitudeMath.roll(forward, up).orElseThrow(), 1.0E-6);
        assertEquals(-20, AttitudeMath.roll(forward, new Vec3(-Math.sin(r), Math.cos(r), 0)).orElseThrow(), 1.0E-6);
    }

    @Test
    void invertedFlightIsAStraightHundredEightyDegreeRoll() {
        double roll = AttitudeMath.roll(new Vec3(0, 0, -1), new Vec3(0, -1, 0)).orElseThrow();
        assertEquals(180, Math.abs(roll), EPS);
    }

    @Test
    void verticalNoseLeavesRollAndHeadingUndefined() {
        Vec3 up = new Vec3(0, 0, -1);
        Vec3 nose = new Vec3(0, 1, 0);
        assertEquals(90, AttitudeMath.pitch(nose), EPS);
        assertTrue(AttitudeMath.roll(nose, up).isEmpty());
        assertTrue(AttitudeMath.heading(nose).isEmpty());
        assertEquals(-90, AttitudeMath.pitch(new Vec3(0, -1, 0)), EPS);
    }

    @Test
    void rollIsDefinedUpToEightyFiveDegreesOfPitch() {
        assertTrue(AttitudeMath.rollDefined(85));
        assertTrue(AttitudeMath.rollDefined(-85));
        assertFalse(AttitudeMath.rollDefined(85.1));
        assertFalse(AttitudeMath.rollDefined(-90));
    }

    @Test
    void anglesWrapTheShortWayRound() {
        assertEquals(-170, AttitudeMath.wrapDegrees(190), EPS);
        assertEquals(170, AttitudeMath.wrapDegrees(-190), EPS);
        assertEquals(350, AttitudeMath.wrapHeading(-10), EPS);
        assertEquals(20, AttitudeMath.shortestDelta(350, 10), EPS);
        assertEquals(-20, AttitudeMath.shortestDelta(10, 350), EPS);
    }

    @Test
    void calibrationIsLimitedToFifteenDegreesPerAxis() {
        assertEquals(15, AttitudeMath.CALIBRATION_LIMIT, EPS);
    }

    @Test
    void everyValidFrameReadsAsOneAttitudeInEveryMounting() {
        // Nose and Up are perpendicular in all 24 frames; each gives a finite, consistent attitude.
        for (var nose : net.minecraft.core.Direction.values()) {
            for (var up : net.minecraft.core.Direction.values()) {
                if (!AttitudeOrientation.valid(nose, up)) continue;
                Vec3 forward = new Vec3(nose.getStepX(), nose.getStepY(), nose.getStepZ());
                Vec3 upVector = new Vec3(up.getStepX(), up.getStepY(), up.getStepZ());
                double pitch = AttitudeMath.pitch(forward);
                assertTrue(Math.abs(pitch) <= 90 + EPS);
                if (AttitudeMath.rollDefined(pitch)) {
                    assertTrue(AttitudeMath.roll(forward, upVector).isPresent() || Math.abs(pitch) > 89,
                            nose + "/" + up);
                }
            }
        }
    }
}
