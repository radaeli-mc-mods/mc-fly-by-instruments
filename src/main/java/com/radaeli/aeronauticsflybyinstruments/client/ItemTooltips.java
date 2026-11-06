package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Create-style "Hold [Shift] for Summary" guides. Text comes from the lang keys
 * {@code <descriptionId>.tooltip.summary}, {@code .conditionN} and {@code .behaviourN}.
 */
public final class ItemTooltips {
    private ItemTooltips() {}

    public static void register(IEventBus bus) {
        bus.addListener(ItemTooltips::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            guide(ModBlocks.RADAR_ALTIMETER.get());
            guide(ModBlocks.TERRAIN_SCANNER.get());
            guide(ModBlocks.TERRAIN_SCANNER_ADVANCED.get());
            guide(ModBlocks.MODULAR_TABLE_BASE.get());
            guide(ModBlocks.AVIONICS_WIRE.get());
            guide(ModBlocks.WIRE_CONNECTOR.get());
            guide(ModBlocks.WIRE_SUPPORT.get());
            guide(ModBlocks.NETWORK_BRIDGE.get());
            guide(ModBlocks.AVIONICS_COMPUTER.get());
            guide(ModBlocks.LAMP_MODULE.get());
            guide(ModBlocks.LAMP_MODULE_2X1.get());
            guide(ModBlocks.BUZZER_MODULE.get());
            guide(ModBlocks.FMS_MODULE.get());
            guide(ModBlocks.FMS_NAVIGATOR.get());
            guide(ModBlocks.ATTITUDE_INDICATOR_MODULE.get());
        });
    }

    private static void guide(ItemLike itemLike) {
        Item item = itemLike.asItem();
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }
}
