package com.radaeli.aeronauticsflybyinstruments.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import com.radaeli.aeronauticsflybyinstruments.client.FmsNavigatorTableRenderer;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsRegistries;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.RenderableNavigationTarget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Compass-like token bound to one FMS module identity. The Navigation Table resolves its target
 * through the {@code TARGET} component ({@link com.radaeli.aeronauticsflybyinstruments.fms.FmsNavigationTarget});
 * the item itself only implements {@link RenderableNavigationTarget} to draw the compass on the table.
 */
public final class FmsNavigatorItem extends Item implements RenderableNavigationTarget {
    private static final String BOUND_ID = "FmsModuleId";

    public FmsNavigatorItem(Properties properties) { super(properties.stacksTo(1)); }

    public static UUID boundFmsId(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        var tag = custom.copyTag();
        return tag.hasUUID(BOUND_ID) ? tag.getUUID(BOUND_ID) : null;
    }

    public static void bind(ItemStack stack, UUID id) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        var tag = custom == null ? new net.minecraft.nbt.CompoundTag() : custom.copyTag();
        if (id == null) tag.remove(BOUND_ID);
        else tag.putUUID(BOUND_ID, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public Vec3 getTarget(NavTableBlockEntity navigationTable, ItemStack navigator) {
        return FmsRegistries.NAVIGATOR_TARGET.getTarget(navigationTable, navigator);
    }

    /** Client only: called by the Navigation Table renderer. */
    @Override
    public void renderInNavTable(ItemStack self, NavTableBlockEntity navigationTable, BlockState navigationState,
                                 float partialTicks, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        FmsNavigatorTableRenderer.render(self, navigationTable, partialTicks, pose, buffer, light, overlay);
    }

    /** Linked compasses shimmer like the vanilla lodestone compass. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return boundFmsId(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID id = boundFmsId(stack);
        tooltip.add(Component.translatable(id == null
                ? "item." + AeronauticsFlyByInstruments.MOD_ID + ".fms_navigator.unbound"
                : "item." + AeronauticsFlyByInstruments.MOD_ID + ".fms_navigator.bound"));
    }
}
