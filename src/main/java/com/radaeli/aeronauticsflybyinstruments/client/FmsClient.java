package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.item.FmsNavigatorItem;
import com.radaeli.aeronauticsflybyinstruments.network.FmsScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class FmsClient {
    /** Item model property: 1 while the FMS Compass is linked to a module (models/item/fms_navigator.json overrides). */
    public static final ResourceLocation LINKED = ResourceLocation.fromNamespaceAndPath(AeronauticsFlyByInstruments.MOD_ID, "linked");

    private FmsClient() {}

    public static void register(IEventBus bus) {
        bus.addListener(FmsClient::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModBlocks.FMS_NAVIGATOR.get(), LINKED,
                (stack, level, entity, seed) -> FmsNavigatorItem.boundFmsId(stack) != null ? 1 : 0));
    }

    public static void openOrUpdate(FmsScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FmsScreen screen && screen.matches(payload)) {
            screen.acceptServerState(payload);
        } else if (minecraft.screen instanceof FmsWaypointScreen editor && editor.parent().matches(payload)) {
            editor.parent().acceptServerState(payload);
        } else {
            minecraft.setScreen(new FmsScreen(payload));
        }
    }
}
