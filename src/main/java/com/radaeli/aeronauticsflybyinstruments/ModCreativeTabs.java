package com.radaeli.aeronauticsflybyinstruments;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeronauticsFlyByInstruments.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AERONAUTICS_FLY_BY_INSTRUMENTS =
            TABS.register("aeronautics_fly_by_instruments", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeronautics_fly_by_instruments"))
                    .icon(() -> new ItemStack(ModBlocks.AVIONICS_COMPUTER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        // Delivered sensors (redstone + CC:Tweaked) come first.
                        output.accept(ModBlocks.RADAR_ALTIMETER_ITEM);
                        output.accept(ModBlocks.TERRAIN_SCANNER_ITEM);
                        output.accept(ModBlocks.TERRAIN_SCANNER_ADVANCED_ITEM);
                        output.accept(ModBlocks.ATTITUDE_REFERENCE_UNIT_ITEM);
                        // The avionics suite is craftable; the Network Bridge and the Attitude Reference Unit are Experimental.
                        output.accept(ModBlocks.MODULAR_TABLE_BASE_ITEM);
                        output.accept(ModBlocks.AVIONICS_WIRE);
                        output.accept(ModBlocks.WIRE_CONNECTOR_ITEM);
                        output.accept(ModBlocks.WIRE_SUPPORT_ITEM);
                        output.accept(ModBlocks.NETWORK_BRIDGE_ITEM);
                        output.accept(ModBlocks.AVIONICS_COMPUTER_ITEM);
                        output.accept(ModBlocks.LAMP_MODULE);
                        output.accept(ModBlocks.LAMP_MODULE_2X1);
                        output.accept(ModBlocks.BUZZER_MODULE);
                        output.accept(ModBlocks.FMS_MODULE);
                        output.accept(ModBlocks.FMS_NAVIGATOR);
                    })
                    .build());

    private ModCreativeTabs() {}
}
