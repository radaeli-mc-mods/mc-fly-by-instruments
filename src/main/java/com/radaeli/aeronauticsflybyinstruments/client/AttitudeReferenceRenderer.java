package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeOrientation;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlock;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * The propeller of the model airplane inside the Attitude Reference Unit's glass, spinning about the nose
 * axis on a fixed loop. Purely decorative: it ignores the attitude reading and the status. The airplane
 * itself is a static blockstate part; the propeller follows it through {@link AttitudeOrientation#rotation()},
 * so it is right in all 24 frames. The model is the propeller group of blockbench/attitude-reference-unit.
 */
public final class AttitudeReferenceRenderer implements BlockEntityRenderer<AttitudeReferenceUnitBlockEntity> {
    public static final ModelResourceLocation PROPELLER = model("attitude_reference_unit_propeller");

    /** Propeller turn per tick: one revolution every 15 ticks. */
    static final float SPIN_DEGREES_PER_TICK = 24f;
    /** The loop length in ticks, so the angle stays small on long-running worlds. */
    private static final long SPIN_PERIOD_TICKS = 15;

    public AttitudeReferenceRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsFlyByInstruments.MOD_ID, "block/" + name));
    }

    /**
     * Inside a Sable sublevel the block's own 1x1x1 box can fall outside the frustum test (it is tested against the
     * plot-space position), which made a lone unit's moving part vanish. Like the table and wire renderers, draw it
     * whenever the block is: a slightly larger box and no off-screen cull.
     */
    @Override
    public AABB getRenderBoundingBox(AttitudeReferenceUnitBlockEntity unit) {
        return new AABB(unit.getBlockPos()).inflate(1);
    }

    @Override
    public boolean shouldRenderOffScreen(AttitudeReferenceUnitBlockEntity unit) {
        return true;
    }

    @Override
    public void render(AttitudeReferenceUnitBlockEntity unit, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = unit.getLevel();
        if (level == null) return;
        var state = unit.getBlockState();
        var nose = state.getValue(AttitudeReferenceUnitBlock.NOSE);
        var up = state.getValue(AttitudeReferenceUnitBlock.UP);
        // A collinear pair has no airplane in the blockstate either.
        if (!AttitudeOrientation.valid(nose, up)) return;
        float time = level.getGameTime() % SPIN_PERIOD_TICKS + partialTick;
        var manager = Minecraft.getInstance().getModelManager();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        // A fixed buffer of the vanilla buffer source (as the other renderers use): RenderType.cutout() is a lazily
        // allocated one, and Sable's lone-block sublevel path never flushed it, so the moving part vanished there.
        var consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(new AttitudeOrientation(nose, up).rotation());
        poses.mulPose(Axis.ZP.rotationDegrees(time * SPIN_DEGREES_PER_TICK));
        poses.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(poses.last(), consumer, null, manager.getModel(PROPELLER), 1f, 1f, 1f, light, overlay);
        poses.popPose();
    }
}
