package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Draws {@link AttitudeDisplayLayout} on the lens of the Attitude Indicator, in one flat unlit pass like the FMS
 * compass: it reads as a lit display in any light and shader, stays crisp at any distance, and nothing in it can
 * fight the lens for depth. No allocation per frame beyond the layout's own clip objects.
 *
 * <p>The layout is authored on a 12 x 12 lens; the module's lens is 8 x 8 units, so it is drawn at {@link #SCALE}.
 * Two lens placements (guide §9): the wedge's upright lens faces +Z (z 13, centre X 8 / Y 7), screen up is +Y; the
 * plate's lens faces up (y 3, centre X/Z 8) with screen up toward -Z, as on the FMS.
 */
final class AttitudeDisplayRenderer {
    /** Unlit flat colour, no depth write, offset over the lens to avoid z-fighting (same state as the compass). */
    private static final RenderType FLAT = RenderType.create("aeronautics_fly_by_instruments_attitude_display",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    /** Lens plane in model units: centres, and how far over the lens face it is drawn. */
    static final float CENTER = 8, WEDGE_CENTER_Y = 7, LENS_FACE_WEDGE = 13.02f, LENS_FACE_PLATE = 3.02f;
    /** Model lens (8 units) over the layout's lens (12 units). */
    static final float LENS = 8, SCALE = LENS / (2 * AttitudeDisplayLayout.HALF);

    private AttitudeDisplayRenderer() {}

    /**
     * @param poses positioned at the module origin, in blocks
     * @param wedge true for the upright wedge (floor tables), false for the flat plate (wall and ceiling tables)
     */
    static void render(PoseStack poses, MultiBufferSource buffers, boolean wedge, AttitudeDisplayState state) {
        poses.pushPose();
        poses.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var pose = poses.last();
        var buffer = buffers.getBuffer(FLAT);
        AttitudeDisplayLayout.build((color, xy, count) -> emit(buffer, pose, wedge, color, xy, count), state);
        poses.popPose();
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, boolean wedge, int color, float[] xy, int count) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        // Fan of quads around point 0: a triangle becomes a quad with a repeated corner.
        for (int i = 1; i < count - 1; i += 2) {
            vertex(buffer, pose, wedge, r, g, b, xy, 0);
            vertex(buffer, pose, wedge, r, g, b, xy, i);
            vertex(buffer, pose, wedge, r, g, b, xy, i + 1);
            vertex(buffer, pose, wedge, r, g, b, xy, Math.min(i + 2, count - 1));
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, boolean wedge, int r, int g, int b,
                               float[] xy, int index) {
        float x = xy[index * 2] * SCALE, y = xy[index * 2 + 1] * SCALE;
        if (wedge) buffer.addVertex(pose, CENTER + x, WEDGE_CENTER_Y + y, LENS_FACE_WEDGE).setColor(r, g, b, 255);
        else buffer.addVertex(pose, CENTER + x, LENS_FACE_PLATE, CENTER - y).setColor(r, g, b, 255);
    }
}
