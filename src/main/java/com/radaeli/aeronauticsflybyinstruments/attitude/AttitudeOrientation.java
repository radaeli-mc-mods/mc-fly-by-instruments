package com.radaeli.aeronauticsflybyinstruments.attitude;

import net.minecraft.core.Direction;

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

    public AttitudeOrientation rotated(net.minecraft.world.level.block.Rotation rotation) {
        return new AttitudeOrientation(rotation.rotate(nose), rotation.rotate(up));
    }

    public AttitudeOrientation mirrored(net.minecraft.world.level.block.Mirror mirror) {
        return new AttitudeOrientation(mirror.mirror(nose), mirror.mirror(up));
    }
}
