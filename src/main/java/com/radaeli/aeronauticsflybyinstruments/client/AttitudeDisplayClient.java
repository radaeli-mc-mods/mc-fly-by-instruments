package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AttitudeDisplayPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorScreenPayload;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the Attitude Indicator: keeps the feed the server sends for each module in range, hands the
 * renderer a smoothed state to draw, and opens the configuration screen.
 */
public final class AttitudeDisplayClient {
    /** A feed neither updated nor drawn for this long is forgotten. */
    private static final long FORGET_NANOS = 60_000_000_000L;

    private record Key(long position, int column, int row) {}

    private static final class Entry {
        final AttitudeDisplayFeed feed = new AttitudeDisplayFeed();
        long touched;
    }

    private static final Map<Key, Entry> FEEDS = new HashMap<>();

    private AttitudeDisplayClient() {}

    public static void accept(AttitudeDisplayPayload payload) {
        long now = System.nanoTime();
        FEEDS.values().removeIf(entry -> now - entry.touched > FORGET_NANOS);
        Entry entry = FEEDS.computeIfAbsent(new Key(payload.position().asLong(), payload.column(), payload.row()),
                ignored -> new Entry());
        entry.touched = now;
        entry.feed.accept(status(payload.status()), payload.pitch(), payload.roll(), payload.agl(),
                payload.altitudeTape());
    }

    /** The state to draw for the module at this origin cell of the table, or the ATT flag without a feed. */
    static AttitudeDisplayState state(BlockPos table, int column, int row) {
        Entry entry = FEEDS.get(new Key(table.asLong(), column, row));
        if (entry == null) return AttitudeDisplayFeed.NO_DATA;
        long now = System.nanoTime();
        double seconds = entry.touched == 0 ? 0 : (now - entry.touched) / 1.0e9;
        entry.touched = now;
        return entry.feed.advance(seconds);
    }

    private static AttitudeDisplayState.Status status(int ordinal) {
        var values = AttitudeIndicatorFeed.Status.values();
        if (ordinal < 0 || ordinal >= values.length) return AttitudeDisplayState.Status.NO_DATA;
        return switch (values[ordinal]) {
            case OK -> AttitudeDisplayState.Status.OK;
            case DEGRADED -> AttitudeDisplayState.Status.DEGRADED;
            case NO_DATA -> AttitudeDisplayState.Status.NO_DATA;
        };
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FEEDS.clear();
    }

    public static void openOrUpdate(AttitudeIndicatorScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AttitudeIndicatorScreen open && open.isFor(payload)) {
            open.accept(payload);
        } else {
            minecraft.setScreen(new AttitudeIndicatorScreen(payload));
        }
    }
}
