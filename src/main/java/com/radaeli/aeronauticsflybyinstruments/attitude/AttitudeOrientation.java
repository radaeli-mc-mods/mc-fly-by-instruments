package com.radaeli.aeronauticsflybyinstruments.attitude;

import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The aircraft frame chosen on the block: Nose and Up, always perpendicular. Each change keeps the
 * pair perpendicular, so the block never reaches an invalid state.
 */
public record AttitudeOrientation(Direction nose, Direction up) {
    public AttitudeOrientation {
        if (nose.getAxis() == up.getAxis()) throw new IllegalArgumentException("Nose and Up must be perpendicular");
    }

    public static AttitudeOrientation standard() {
        return new AttitudeOrientation(Direction.NORTH, Direction.UP);
    }

    public static boolean valid(Direction nose, Direction up) {
        return nose.getAxis() != up.getAxis();
    }

    /**
     * Wrench on a face: that face becomes the Nose. If it was the Up (or is opposite to it), the old
     * Nose becomes the Up, so the pair stays perpendicular.
     */
    public AttitudeOrientation withNose(Direction face) {
        if (face == nose) return this;
        if (face.getAxis() == up.getAxis()) return new AttitudeOrientation(face, nose);
        return new AttitudeOrientation(face, up);
    }

    /**
     * Scroll on a face: that face becomes the Up. If it was the Nose (or is opposite to it), the old
     * Up becomes the Nose.
     */
    public AttitudeOrientation withUp(Direction face) {
        if (face == up) return this;
        if (face.getAxis() == nose.getAxis()) return new AttitudeOrientation(up, face);
        return new AttitudeOrientation(nose, face);
    }

    /**
     * Rotation that takes the standard frame (Nose = NORTH, Up = UP) to this one. The model airplane is
     * authored in the standard frame, so this turns anything drawn with it (the propeller) in all 24 frames.
     */
    public Quaternionf rotation() {
        Vector3f forward = new Vector3f(nose.getStepX(), nose.getStepY(), nose.getStepZ());
        Vector3f sky = new Vector3f(up.getStepX(), up.getStepY(), up.getStepZ());
        Vector3f right = forward.cross(sky, new Vector3f());
        // Columns are the images of +X (right), +Y (up) and +Z (the tail, opposite to the nose).
        return new Quaternionf().setFromNormalized(new Matrix3f(right, sky, forward.negate()));
    }

    public AttitudeOrientation rotated(net.minecraft.world.level.block.Rotation rotation) {
        return new AttitudeOrientation(rotation.rotate(nose), rotation.rotate(up));
    }

    public AttitudeOrientation mirrored(net.minecraft.world.level.block.Mirror mirror) {
        return new AttitudeOrientation(mirror.mirror(nose), mirror.mirror(up));
    }
}
