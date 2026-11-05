package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceScreenPayload;
import net.minecraft.client.Minecraft;

/** Opens the Attitude Reference Unit screen, or refreshes the one already open for the same unit. */
public final class AttitudeReferenceClient {
    private AttitudeReferenceClient() {}

    public static void openOrUpdate(AttitudeReferenceScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AttitudeReferenceScreen open && open.position().equals(payload.position())) {
            open.accept(payload);
        } else {
            minecraft.setScreen(new AttitudeReferenceScreen(payload));
        }
    }
}
