package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AttitudeDisplayPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorScreenPayload;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed;
import net.minecraft.client.Minecraft;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
    /** Feeds set by Ponder scenes, kept apart so a diorama can never touch a real table at the same position. */
    private static final Map<Key, Demo> DEMOS = new HashMap<>();

    /** A scene-driven feed: re-offered on every frame, so it never goes stale while the scene is shown. */
    private static final class Demo {
        final Entry entry = new Entry();
        AttitudeIndicatorFeed.Status status = AttitudeIndicatorFeed.Status.NO_DATA;
        double pitch, roll, agl = Double.NaN;
        boolean altitudeTape;
    }

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

    /**
     * Ponder: sets what the diorama's Attitude Indicator at this origin cell shows. The scene calls it as often as
     * it likes; the display eases toward the values like a live feed.
     */
    public static void demo(BlockPos table, int column, int row, AttitudeIndicatorFeed.Status status, double pitch,
                            double roll, double agl, boolean altitudeTape) {
        Demo demo = DEMOS.computeIfAbsent(new Key(table.asLong(), column, row), ignored -> new Demo());
        demo.status = status;
        demo.pitch = pitch;
        demo.roll = roll;
        demo.agl = agl;
        demo.altitudeTape = altitudeTape;
    }

    /** The state to draw for the module at this origin cell of the table, or the ATT flag without a feed. */
    static AttitudeDisplayState state(Level level, BlockPos table, int column, int row) {
        if (!(level instanceof PonderLevel)) return state(table, column, row);
        Demo demo = DEMOS.get(new Key(table.asLong(), column, row));
        if (demo == null) return AttitudeDisplayFeed.NO_DATA;
        long now = System.nanoTime();
        double seconds = demo.entry.touched == 0 ? 0 : Math.min(0.25, (now - demo.entry.touched) / 1.0e9);
        demo.entry.touched = now;
        demo.entry.feed.accept(status(demo.status.ordinal()), demo.pitch, demo.roll, demo.agl, demo.altitudeTape);
        return demo.entry.feed.advance(seconds);
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
        DEMOS.clear();
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
