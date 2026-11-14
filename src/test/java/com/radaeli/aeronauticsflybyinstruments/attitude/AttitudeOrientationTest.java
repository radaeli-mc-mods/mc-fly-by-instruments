package com.radaeli.aeronauticsflybyinstruments.attitude;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The wrench and screen rules: Nose and Up stay perpendicular in all 24 frames. */
class AttitudeOrientationTest {
    private static List<AttitudeOrientation> validFrames() {
        var frames = new ArrayList<AttitudeOrientation>();
        for (Direction nose : Direction.values()) {
            for (Direction up : Direction.values()) {
                if (AttitudeOrientation.valid(nose, up)) frames.add(new AttitudeOrientation(nose, up));
            }
        }
        return frames;
    }

    @Test
    void thereAreTwentyFourValidFrames() {
        assertEquals(24, validFrames().size());
    }

    @Test
    void standardPlacementIsNorthNoseAndUpUp() {
        assertEquals(new AttitudeOrientation(Direction.NORTH, Direction.UP), AttitudeOrientation.standard());
    }

    @Test
    void parallelNoseAndUpAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AttitudeOrientation(Direction.UP, Direction.DOWN));
        assertThrows(IllegalArgumentException.class, () -> new AttitudeOrientation(Direction.NORTH, Direction.NORTH));
    }

    @Test
    void everyWrenchOnEveryFaceKeepsTheFramePerpendicular() {
        for (var frame : validFrames()) {
            for (Direction face : Direction.values()) {
                var nose = frame.withNose(face);
                assertTrue(AttitudeOrientation.valid(nose.nose(), nose.up()), frame + " nose " + face);
                assertEquals(face, nose.nose());
                var up = frame.withUp(face);
                assertTrue(AttitudeOrientation.valid(up.nose(), up.up()), frame + " up " + face);
                assertEquals(face, up.up());
            }
        }
    }

    @Test
    void wrenchOnTheUpFaceSwapsNoseAndUp() {
        var frame = new AttitudeOrientation(Direction.NORTH, Direction.UP);
        assertEquals(new AttitudeOrientation(Direction.UP, Direction.NORTH), frame.withNose(Direction.UP));
    }

    @Test
    void wrenchOnTheOppositeOfTheUpFaceMakesItTheNoseAndTheOldNoseTheUp() {
        var frame = new AttitudeOrientation(Direction.NORTH, Direction.UP);
        assertEquals(new AttitudeOrientation(Direction.DOWN, Direction.NORTH), frame.withNose(Direction.DOWN));
    }

    @Test
    void wrenchOnAFaceBesideTheNoseKeepsTheUp() {
        var frame = new AttitudeOrientation(Direction.NORTH, Direction.UP);
        assertEquals(new AttitudeOrientation(Direction.EAST, Direction.UP), frame.withNose(Direction.EAST));
        assertEquals(new AttitudeOrientation(Direction.SOUTH, Direction.UP), frame.withNose(Direction.SOUTH));
    }

    @Test
    void scrollOnTheNoseFaceSwapsNoseAndUp() {
        var frame = new AttitudeOrientation(Direction.NORTH, Direction.UP);
        assertEquals(new AttitudeOrientation(Direction.UP, Direction.NORTH), frame.withUp(Direction.NORTH));
    }

    @Test
    void scrollOnAFaceBesideTheUpKeepsTheNose() {
        var frame = new AttitudeOrientation(Direction.NORTH, Direction.UP);
        assertEquals(new AttitudeOrientation(Direction.NORTH, Direction.EAST), frame.withUp(Direction.EAST));
    }

    @Test
    void clickingTheCurrentNoseOrUpChangesNothing() {
        var frame = new AttitudeOrientation(Direction.WEST, Direction.DOWN);
        assertSame(frame, frame.withNose(Direction.WEST));
        assertSame(frame, frame.withUp(Direction.DOWN));
    }

    @Test
    void theRotationTakesTheStandardNoseAndUpToTheFrameInAllTwentyFourFrames() {
        for (var frame : validFrames()) {
            var rotation = frame.rotation();
            var nose = rotation.transform(new Vector3f(0, 0, -1));
            var up = rotation.transform(new Vector3f(0, 1, 0));
            assertEquals(frame.nose().getStepX(), nose.x, 1e-5, frame + " nose");
            assertEquals(frame.nose().getStepY(), nose.y, 1e-5, frame + " nose");
            assertEquals(frame.nose().getStepZ(), nose.z, 1e-5, frame + " nose");
            assertEquals(frame.up().getStepX(), up.x, 1e-5, frame + " up");
            assertEquals(frame.up().getStepY(), up.y, 1e-5, frame + " up");
            assertEquals(frame.up().getStepZ(), up.z, 1e-5, frame + " up");
        }
        var standard = AttitudeOrientation.standard().rotation();
        assertEquals(1, Math.abs(standard.w), 1e-6);
    }

    @Test
    void rotationAndMirrorKeepTheFramePerpendicular() {
        for (var frame : validFrames()) {
            for (Rotation rotation : Rotation.values()) {
                var turned = frame.rotated(rotation);
                assertTrue(AttitudeOrientation.valid(turned.nose(), turned.up()));
            }
            for (Mirror mirror : Mirror.values()) {
                var mirrored = frame.mirrored(mirror);
                assertTrue(AttitudeOrientation.valid(mirrored.nose(), mirrored.up()));
            }
        }
    }
}
