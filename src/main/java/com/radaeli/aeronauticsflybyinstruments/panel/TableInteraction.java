package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.item.*;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Shared physical targeting and live-stack rules; never mutates the grid. */
public final class TableInteraction {
    private TableInteraction() {}

    public static void onRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().hasChunkAt(event.getPos())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof ModularTableBlockEntity table)) return;
        Player player = event.getEntity();
        if (event.getHand() == InteractionHand.OFF_HAND
                && (isModule(player.getOffhandItem()) || ModularTableBaseBlock.isCreateWrench(player.getOffhandItem()))) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        } else if (event.getHand() == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && target(table, event.getHitVec().getDirection(), event.getHitVec().getLocation()).interactive()) {
            // Vanilla sneaking bypass considers BOTH hands; our empty-main shortcut deliberately does not.
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
        }
    }

    /**
     * Hit cell in the clicked table's frame (0-2), or the anchor of the module hit, which may lie in
     * a joined table before this one (column/row are then negative in this frame).
     */
    public record Target(int column, int row, TablePanel.Placed module, boolean exposed) {
        public boolean interactive() { return exposed || module != null; }
    }

    public record HeldModule(String type, int width, int height) {}

    public static java.util.Optional<HeldModule> heldModule(Player player) {
        if (player == null) return java.util.Optional.empty();
        ItemStack stack = player.getMainHandItem();
        if (!isModule(stack)) return java.util.Optional.empty();
        if (stack.getItem() instanceof FmsModuleItem) return java.util.Optional.of(new HeldModule("fms", 2, 3));
        if (stack.getItem() instanceof AttitudeIndicatorModuleItem)
            return java.util.Optional.of(new HeldModule(PanelCoordinates.ATTITUDE_INDICATOR, 2, 2));
        if (stack.getItem() instanceof LampModule2x1Item) {
            boolean vertical = com.radaeli.aeronauticsflybyinstruments.network.PanelNetwork.isVertical(player);
            return java.util.Optional.of(new HeldModule("lamp_2x1", vertical ? 1 : 2, vertical ? 2 : 1));
        }
        return java.util.Optional.of(new HeldModule(stack.getItem() instanceof BuzzerModuleItem ? "buzzer" : "lamp", 1, 1));
    }

    public static boolean isModule(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof LampModuleItem
                || stack.getItem() instanceof LampModule2x1Item
                || stack.getItem() instanceof BuzzerModuleItem || stack.getItem() instanceof FmsModuleItem
                || stack.getItem() instanceof AttitudeIndicatorModuleItem);
    }

    public static boolean validPlacement(UseOnContext context, String type) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND || stack.isEmpty()
                || stack != player.getMainHandItem() || !player.mayBuild()) return false;
        return switch (type) {
            case "lamp" -> stack.getItem() instanceof LampModuleItem;
            case "lamp_2x1" -> stack.getItem() instanceof LampModule2x1Item;
            case "buzzer" -> stack.getItem() instanceof BuzzerModuleItem;
            case "fms" -> stack.getItem() instanceof FmsModuleItem;
            case PanelCoordinates.ATTITUDE_INDICATOR -> stack.getItem() instanceof AttitudeIndicatorModuleItem;
            default -> false;
        };
    }

    public static Target target(ModularTableBlockEntity table, Direction hitFace, Vec3 worldHit) {
        Direction facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
        Vec3 p = PanelCoordinates.toPanel(facing, worldHit.subtract(Vec3.atLowerCornerOf(table.getBlockPos())));
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z))
            return new Target(-1, -1, null, false);
        // A side face on the elevated selection volume belongs to the entire installed footprint.
        // Includes modules running in from the joined tables before this one.
        for (TablePanel.Placed placed : TablePanel.covering(table)) {
            var module = placed.module();
            double x0 = placed.column() / 3.0 + .06, z0 = placed.row() / 3.0 + .06;
            double x1 = (placed.column() + module.width()) / 3.0 - .06;
            double z1 = (placed.row() + module.height()) / 3.0 - .06;
            if (p.y > PanelCoordinates.SURFACE + 1e-7 && p.y <= PanelCoordinates.moduleTop(module.type(), facing) + 1e-7
                    && p.x >= x0 - 1e-7 && p.x <= x1 + 1e-7
                    && p.z >= z0 - 1e-7 && p.z <= z1 + 1e-7)
                return new Target(placed.column(), placed.row(), placed, hitFace == facing);
        }
        int column = PanelCoordinates.cell(p.x), row = PanelCoordinates.cell(p.z);
        boolean exposed = hitFace == facing && column >= 0 && row >= 0
                && p.y >= PanelCoordinates.SURFACE - 1e-7 && p.y <= PanelCoordinates.MODULE_TOP + 1e-7;
        return new Target(column, row, exposed ? TablePanel.localAt(table, column, row).orElse(null) : null, exposed);
    }

    public static boolean canReach(net.minecraft.server.level.ServerPlayer player, ModularTableBlockEntity table) {
        return player.level() == table.getLevel() && player.level().hasChunkAt(table.getBlockPos())
                && PanelSpatial.entityWorldPosition(player).distanceToSqr(PanelSpatial.tableWorldPosition(table)) <= 64;
    }
}
