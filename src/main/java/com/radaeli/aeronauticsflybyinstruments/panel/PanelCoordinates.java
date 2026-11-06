package com.radaeli.aeronauticsflybyinstruments.panel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class PanelCoordinates {
    private PanelCoordinates() {}

    /** Table thickness: modules rest on it. 4 px, so a wire connector's body fits the table edge. */
    public static final double SURFACE = 4.0 / 16;
    /** Top of the module selection volume: table surface plus the tallest ordinary module (4 px). */
    public static final double MODULE_TOP = SURFACE + 4.0 / 16;
    /** Module type of the 2x2 attitude indicator. */
    public static final String ATTITUDE_INDICATOR = "attitude_indicator";
    /** The upright display of the attitude indicator stands 12 px over the table: its top meets the block top (guide §9.2 exception). */
    public static final double DISPLAY_HEIGHT = 12.0 / 16;

    /**
     * Top of a module's selection volume. The attitude indicator wedge is taller than the block's own
     * cell, so its volume stops at the block top: a ray cannot hit a block from outside its cell.
     */
    public static double moduleTop(String type) {
        return ATTITUDE_INDICATOR.equals(type) ? 1.0 : MODULE_TOP;
    }

    /**
     * Degrees to turn the 2x2 attitude indicator about its footprint centre, around the panel normal, in the panel
     * frame of a table mounted on {@code facing}. {@code view} is the horizontal direction the player looked
     * when placing it.
     * <ul>
     *   <li>Floor (UP): the wedge's lens turns to face the player.</li>
     *   <li>Ceiling (DOWN): the display's top points away from the player, as seen from below.</li>
     *   <li>Wall: the plate is turned over, so its top points to world up (the panel's top is -Z, which is down on a wall).</li>
     * </ul>
     */
    public static double attitudeTurn(Direction facing, Direction view) {
        Direction look = view.getAxis().isHorizontal() ? view : Direction.NORTH;
        return switch (facing) {
            case UP -> Math.toDegrees(Math.atan2(-look.getStepX(), -look.getStepZ()));
            case DOWN -> Math.toDegrees(Math.atan2(-look.getStepX(), look.getStepZ()));
            default -> 180;
        };
    }

    /** Translation for a raw model centered at X/Z=8 pixels, with its mounting base at Y=0. */
    public static Vec3 modelOffset(PanelGrid.Module module) {
        return new Vec3((module.column() + module.width() / 2.0) / PanelGrid.SIZE - 0.5,
                SURFACE, (module.row() + module.height() / 2.0) / PanelGrid.SIZE - 0.5);
    }

    /** Convert block-local coordinates into a panel whose exposed face points up. */
    public static Vec3 toPanel(Direction facing, Vec3 point) {
        return switch (facing) {
            case UP -> point;
            case DOWN -> new Vec3(point.x, 1 - point.y, 1 - point.z);
            case NORTH -> new Vec3(point.x, 1 - point.z, point.y);
            case SOUTH -> new Vec3(1 - point.x, point.z, point.y);
            case WEST -> new Vec3(1 - point.z, 1 - point.x, point.y);
            case EAST -> new Vec3(point.z, point.x, point.y);
        };
    }

    public static Vec3 fromPanel(Direction facing, Vec3 point) {
        return switch (facing) {
            case UP -> point;
            case DOWN -> new Vec3(point.x, 1 - point.y, 1 - point.z);
            case NORTH -> new Vec3(point.x, point.z, 1 - point.y);
            case SOUTH -> new Vec3(1 - point.x, point.z, point.y);
            case WEST -> new Vec3(1 - point.y, point.z, 1 - point.x);
            case EAST -> new Vec3(point.y, point.z, point.x);
        };
    }

    public static int cell(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < -0.000001 || coordinate > 1.000001) return -1;
        return Math.max(0, Math.min(PanelGrid.SIZE - 1, (int) Math.floor(coordinate * PanelGrid.SIZE)));
    }
}
