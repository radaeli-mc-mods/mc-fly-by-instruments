package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceScreenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Client side of the Attitude Reference Unit: the propeller renderer and its screen. */
public final class AttitudeReferenceClient {
    private AttitudeReferenceClient() {}

    public static void register(IEventBus bus) {
        bus.addListener(AttitudeReferenceClient::registerRenderers);
        bus.addListener(AttitudeReferenceClient::registerModels);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.ATTITUDE_REFERENCE_UNIT_ENTITY.get(), AttitudeReferenceRenderer::new);
    }

    /** The propeller is not part of the blockstate: load it as a standalone model for the renderer. */
    private static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(AttitudeReferenceRenderer.PROPELLER);
    }

    public static void openOrUpdate(AttitudeReferenceScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AttitudeReferenceScreen open && open.position().equals(payload.position())) {
            open.accept(payload);
        } else {
            minecraft.setScreen(new AttitudeReferenceScreen(payload));
        }
    }
}
