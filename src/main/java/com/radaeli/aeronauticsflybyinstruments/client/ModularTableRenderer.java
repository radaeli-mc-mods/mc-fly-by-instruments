package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.panel.TablePanel;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

public final class ModularTableRenderer implements BlockEntityRenderer<ModularTableBlockEntity> {
    private static final double SURFACE = PanelCoordinates.SURFACE;
    private static final double CELL = 1.0 / PanelGrid.SIZE;
    private final BlockRenderDispatcher blocks;
    private final LampModuleRenderer lamps1x1 = new LampModuleRenderer(ModBlocks.LAMP_MODULE.get());
    private final LampModuleRenderer lamps2x1 = new LampModuleRenderer(ModBlocks.LAMP_MODULE_2X1.get());
    private final ItemStack buzzerStack = new ItemStack(ModBlocks.BUZZER_MODULE.get());
    private final ItemStack fmsStack = new ItemStack(ModBlocks.FMS_MODULE.get());
    /** Attitude indicator: the wedge for tables facing up, the flat plate (custom model data 1) for the others. */
    private final ItemStack attitudeWedgeStack = new ItemStack(ModBlocks.ATTITUDE_INDICATOR_MODULE.get());
    private final ItemStack attitudePlateStack = new ItemStack(ModBlocks.ATTITUDE_INDICATOR_MODULE.get());
    {
        attitudePlateStack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(1));
    }
    /** Per table and FMS origin cell: when the lock was first drawn, for the compass boot-in. */
    private final java.util.Map<ModularTableBlockEntity, java.util.Map<Integer, Double>> compassLocks =
            new java.util.WeakHashMap<>();

    public ModularTableRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    /** Modules may run up to one table past this one, so do not cull them with the block. */
    @Override
    public AABB getRenderBoundingBox(ModularTableBlockEntity table) {
        return new AABB(table.getBlockPos()).inflate(1);
    }

    @Override
    public void render(ModularTableBlockEntity table, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
        poses.pushPose();
        orient(poses, facing);
        for (var module : table.grid().modules()) {
            if (ModularTableBlockEntity.isLampModule(module)) {
                renderLamp(table, module, poses, buffers, light, overlay);
            } else if (module.type().equals("buzzer")) {
                renderBuzzer(table, module, poses, buffers, light, overlay);
            } else if (module.type().equals("fms")) {
                renderFms(table, module, partialTick, poses, buffers, light, overlay);
            } else if (module.type().equals(PanelCoordinates.ATTITUDE_INDICATOR)) {
                renderAttitudeIndicator(table, module, partialTick, facing, poses, buffers, light, overlay);
            } else {
                // Keep a fallback for future module types without authored renderers.
                var lampState = Blocks.REDSTONE_LAMP.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, table.isModuleActive(module));
                cube(poses, buffers, lampState,
                        module.column() * CELL + 0.06, SURFACE, module.row() * CELL + 0.06,
                        module.width() * CELL - 0.12, 2.0 / 16, module.height() * CELL - 0.12, light, overlay);
            }
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var held = com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction.heldModule(player).orElse(null);
        boolean removing = player != null && ModularTableBaseBlock.isCreateWrench(player.getMainHandItem());
        // Every table of the hovered panel draws its share of the grid; the hovered one adds the outline.
        if (minecraft.screen == null && (held != null || removing) && minecraft.hitResult instanceof BlockHitResult hit
                && table.getLevel() != null
                && table.getLevel().getBlockEntity(hit.getBlockPos()) instanceof ModularTableBlockEntity hovered
                && (hovered == table || TablePanel.of(hovered) == TablePanel.of(table))
                && (hit.getDirection() == facing || removing && com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction
                .target(hovered, hit.getDirection(), hit.getLocation()).module() != null)) {
            for (int i = 0; i <= PanelGrid.SIZE; i++) {
                double line = Math.min(1 - 0.012, Math.max(0, i * CELL - 0.006));
                cube(poses, buffers, Blocks.GRAY_CONCRETE.defaultBlockState(),
                        line, SURFACE + 0.001, 0, 0.012, 0.004, 1, light, overlay);
                cube(poses, buffers, Blocks.GRAY_CONCRETE.defaultBlockState(),
                        0, SURFACE + 0.001, line, 1, 0.004, 0.012, light, overlay);
            }
            var target = com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction.target(table,
                    hit.getDirection(), hit.getLocation());
            int column = target.column();
            int row = target.row();
            if (hovered == table && column >= 0 && row >= 0) {
                int width = held == null ? 1 : held.width();
                int height = held == null ? 1 : held.height();
                int outlineColumn = column;
                int outlineRow = row;
                boolean valid = false;
                if (removing) {
                    var module = TablePanel.localAt(table, column, row);
                    if (module.isPresent()) {
                        var installed = module.get();
                        outlineColumn = installed.column();
                        outlineRow = installed.row();
                        width = installed.module().width();
                        height = installed.module().height();
                        valid = true;
                    }
                } else {
                    valid = TablePanel.canFit(table, column, row, width, height);
                }
                if (held != null || valid) {
                    var color = (valid ? Blocks.LIME_CONCRETE : Blocks.RED_CONCRETE).defaultBlockState();
                    outline(poses, buffers, color, outlineColumn, outlineRow, width, height, light, overlay);
                }
            }
        }
        poses.popPose();
    }

    private void renderLamp(ModularTableBlockEntity table, PanelGrid.Module module, PoseStack poses,
                            MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        // Center the model on its full footprint, then turn the long axis for a vertical 1x2 placement.
        var offset = PanelCoordinates.modelOffset(module);
        poses.translate(offset.x, offset.y, offset.z);
        if (module.height() > module.width()) {
            poses.translate(0.5, 0, 0.5);
            poses.mulPose(Axis.YP.rotationDegrees(90));
            poses.translate(-0.5, 0, -0.5);
        }
        var renderer = module.type().equals("lamp_2x1") ? lamps2x1 : lamps1x1;
        renderer.render(table.lampRenderIntensity(module), table.lampColor(module), table.getLevel(), poses, buffers,
                light, overlay);
        poses.popPose();
    }

    private void renderBuzzer(ModularTableBlockEntity table, PanelGrid.Module module, PoseStack poses,
                              MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        // Render raw model quads: item display transforms must not shift mounted modules.
        var offset = PanelCoordinates.modelOffset(module);
        poses.translate(offset.x, offset.y, offset.z);
        var items = Minecraft.getInstance().getItemRenderer();
        var model = items.getModel(buzzerStack, table.getLevel(), null, 0);
        items.renderModelLists(model, buzzerStack, light, overlay, poses,
                buffers.getBuffer(Sheets.cutoutBlockSheet()));
        poses.popPose();
    }

    private void renderFms(ModularTableBlockEntity table, PanelGrid.Module module, float partialTick,
                           PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        var offset = PanelCoordinates.modelOffset(module);
        poses.translate(offset.x, offset.y, offset.z);
        // Old occupied 2x2 saves retain their footprint; fit the visual inside it.
        if (module.height() != 3) {
            poses.translate(0.5, 0, 0.5);
            poses.scale(1, 1, module.height() / 3.0f);
            poses.translate(-0.5, 0, -0.5);
        }
        var items = Minecraft.getInstance().getItemRenderer();
        var model = items.getModel(fmsStack, table.getLevel(), null, 0);
        items.renderModelLists(model, fmsStack, light, overlay, poses,
                buffers.getBuffer(Sheets.solidBlockSheet()));
        poses.popPose();
        renderFmsCompass(table, module, partialTick, poses, buffers);
    }

    /**
     * The attitude indicator: the housing (wedge on floor tables, plate otherwise) and a display drawn from the
     * feed the server sends for this module ({@link AttitudeDisplayClient}); the ATT flag without one.
     */
    private void renderAttitudeIndicator(ModularTableBlockEntity table, PanelGrid.Module module, float partialTick,
                                         Direction facing, PoseStack poses, MultiBufferSource buffers, int light,
                                         int overlay) {
        boolean wedge = facing == Direction.UP;
        poses.pushPose();
        var offset = PanelCoordinates.modelOffset(module);
        poses.translate(offset.x, offset.y, offset.z);
        // Housing and display turn together about the footprint centre (see PanelCoordinates.attitudeTurn).
        poses.translate(0.5, 0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees((float) PanelCoordinates.attitudeTurn(facing, module.view())));
        poses.translate(-0.5, 0, -0.5);
        var items = Minecraft.getInstance().getItemRenderer();
        var stack = wedge ? attitudeWedgeStack : attitudePlateStack;
        var model = items.getModel(stack, table.getLevel(), null, 0);
        items.renderModelLists(model, stack, light, overlay, poses, buffers.getBuffer(Sheets.solidBlockSheet()));
        AttitudeDisplayRenderer.render(poses, buffers, wedge,
                AttitudeDisplayClient.state(table.getBlockPos(), module.column(), module.row()));
        poses.popPose();
    }

    /** Compass rose on the lens while a waypoint is locked (FmsCompass). */
    private void renderFmsCompass(ModularTableBlockEntity table, PanelGrid.Module module, float partialTick,
                                  PoseStack poses, MultiBufferSource buffers) {
        var cells = compassLocks.computeIfAbsent(table, ignored -> new java.util.HashMap<>());
        int cell = module.row() * PanelGrid.SIZE + module.column();
        var level = table.getLevel();
        var waypoint = level == null ? null : table.fmsTarget(module.column(), module.row());
        if (waypoint == null) {
            cells.remove(cell);
            return;
        }
        double seconds = (level.getGameTime() + partialTick) / 20.0;
        double lockedAt = cells.computeIfAbsent(cell, ignored -> seconds);

        // World heading and position of the screen, through the contraption's interpolated pose.
        var facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
        var center = new Vec3((module.column() + module.width() / 2.0) / PanelGrid.SIZE, SURFACE,
                (module.row() + module.height() / 2.0) / PanelGrid.SIZE);
        Vec3 local = PanelCoordinates.fromPanel(facing, center);
        Vec3 up = PanelCoordinates.fromPanel(facing, center.add(0, 0, -1)).subtract(local);
        Vec3 normal = PanelCoordinates.fromPanel(facing, center.add(0, 1, 0)).subtract(local);
        Vec3 position = Vec3.atLowerCornerOf(table.getBlockPos()).add(local);
        if (dev.ryanhcode.sable.Sable.HELPER.getContaining(table) instanceof dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
            var pose = subLevel instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client
                    ? client.renderPose(partialTick) : subLevel.logicalPose();
            up = pose.transformNormal(up);
            normal = pose.transformNormal(normal);
            position = pose.transformPosition(position);
        }
        double bearing = waypoint.dimension().equals(level.dimension().location().toString())
                ? FmsCompass.bearing(position.x, position.z, waypoint.x(), waypoint.z()) : Double.NaN;

        poses.pushPose();
        var offset = PanelCoordinates.modelOffset(module);
        poses.translate(offset.x, offset.y, offset.z);
        FmsCompassRenderer.render(poses, buffers, module.height() / 3.0f, FmsCompass.heading(up, normal), bearing,
                FmsCompass.intro(seconds - lockedAt));
        poses.popPose();
    }

    private void outline(PoseStack poses, MultiBufferSource buffers, BlockState color,
                        int column, int row, int width, int height, int light, int overlay) {
        // Not clipped: a footprint may run on over the joined tables.
        double x = column * CELL + 0.012;
        double z = row * CELL + 0.012;
        double w = width * CELL - 0.024;
        double d = height * CELL - 0.024;
        cube(poses, buffers, color, x, SURFACE + 0.005, z, w, 0.006, 0.016, light, overlay);
        cube(poses, buffers, color, x, SURFACE + 0.005, z + d - 0.016, w, 0.006, 0.016, light, overlay);
        cube(poses, buffers, color, x, SURFACE + 0.005, z, 0.016, 0.006, d, light, overlay);
        cube(poses, buffers, color, x + w - 0.016, SURFACE + 0.005, z, 0.016, 0.006, d, light, overlay);
    }

    private void cube(PoseStack poses, MultiBufferSource buffers, BlockState state,
                      double x, double y, double z, double width, double height, double depth, int light, int overlay) {
        poses.pushPose();
        poses.translate(x, y, z);
        poses.scale((float) width, (float) height, (float) depth);
        blocks.renderSingleBlock(state, poses, buffers, light, overlay);
        poses.popPose();
    }

    private static void orient(PoseStack poses, Direction facing) {
        poses.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case UP -> { }
            case DOWN -> poses.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> poses.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> {
                poses.mulPose(Axis.YP.rotationDegrees(180));
                poses.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case WEST -> {
                poses.mulPose(Axis.YP.rotationDegrees(90));
                poses.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case EAST -> {
                poses.mulPose(Axis.YP.rotationDegrees(-90));
                poses.mulPose(Axis.XP.rotationDegrees(-90));
            }
        }
        poses.translate(-0.5, -0.5, -0.5);
    }
}
