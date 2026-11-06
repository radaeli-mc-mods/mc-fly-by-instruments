package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
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
 * The gimbal inside the Attitude Reference Unit's glass: a ring that rocks about Y and a flywheel that spins
 * about Z, on a fixed loop. Purely decorative: it ignores Nose, Up and the attitude reading, so the
 * reference arms (static blockstate parts) are the only thing that shows the configured frame.
 * The two models are the gimbal_ring / gimbal_rotor groups of blockbench/attitude-reference-unit.
 */
public final class AttitudeReferenceRenderer implements BlockEntityRenderer<AttitudeReferenceUnitBlockEntity> {
    public static final ModelResourceLocation RING = model("attitude_reference_unit_gimbal_ring");
    public static final ModelResourceLocation ROTOR = model("attitude_reference_unit_gimbal_rotor");

    /** Ring swing in degrees either side of rest, and the ticks one swing cycle takes. */
    static final float ROCK_DEGREES = 14f;
    static final float ROCK_PERIOD_TICKS = 100f;
    /** Flywheel turn per tick: one revolution every 40 ticks. */
    static final float SPIN_DEGREES_PER_TICK = 9f;

    public AttitudeReferenceRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsFlyByInstruments.MOD_ID, "block/" + name));
    }

    /**
     * Inside a Sable sublevel the block's own 1x1x1 box can fall outside the frustum test (it is tested against the
     * plot-space position), which made a lone unit's gimbal vanish. Like the table and wire renderers, draw it
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
        float time = level.getGameTime() + partialTick;
        var manager = Minecraft.getInstance().getModelManager();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        // A fixed buffer of the vanilla buffer source (as the other renderers use): RenderType.cutout() is a lazily
        // allocated one, and Sable's lone-block sublevel path never flushed it, so the gimbal vanished there.
        var consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(ROCK_DEGREES * (float) Math.sin(time * 2 * Math.PI / ROCK_PERIOD_TICKS)));
        poses.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(poses.last(), consumer, null, manager.getModel(RING), 1f, 1f, 1f, light, overlay);
        poses.popPose();

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.ZP.rotationDegrees(time * SPIN_DEGREES_PER_TICK));
        poses.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(poses.last(), consumer, null, manager.getModel(ROTOR), 1f, 1f, 1f, light, overlay);
        poses.popPose();
    }
}
