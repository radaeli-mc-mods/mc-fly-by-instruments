package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.network.FmsActionPayload;
import com.radaeli.aeronauticsflybyinstruments.network.FmsScreenPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The FMS in the mod's screen language (visual guide §12), one panel for the whole flow: the
 * destination on top, the waypoint list as radio choices (a click flies direct to one, a click on
 * the active one clears it, a right click edits it in {@link FmsWaypointScreen}; past
 * {@link #VISIBLE_WAYPOINTS} the list scrolls) and the FMS
 * Compass link below. Every choice applies immediately; the server answers with a fresh snapshot.
 */
public final class FmsScreen extends HudScreen {
    static final String KEY = "gui.aeronautics_fly_by_instruments.fms.";
    private static final int WIDTH = 210;
    static final int VISIBLE_WAYPOINTS = 7;

    private final Consumer<FmsActionPayload> sender;
    private FmsScreenPayload payload;
    /** First waypoint row shown; HudPanel clamps it to the list. */
    private int scroll;

    public FmsScreen(FmsScreenPayload payload) {
        this(payload, PacketDistributor::sendToServer);
    }

    FmsScreen(FmsScreenPayload payload, Consumer<FmsActionPayload> sender) {
        super(Component.translatable(KEY + "title"));
        this.payload = payload;
        this.sender = sender;
        scroll = scrollToShow(payload.data().waypoints(), payload.data().selectedWaypointId().orElse(null));
    }

    boolean matches(FmsScreenPayload other) {
        return payload.position().equals(other.position()) && payload.column() == other.column()
                && payload.row() == other.row() && payload.interactionToken().equals(other.interactionToken());
    }

    void acceptServerState(FmsScreenPayload updated) { payload = updated; }

    FmsScreenPayload payload() { return payload; }

    @Override
    protected HudPanel build() {
        var palette = HudPanel.palette();
        FmsModuleData data = payload.data();
        List<FmsWaypoint> waypoints = data.waypoints();
        FmsWaypoint target = data.selectedWaypoint().orElse(null);
        HudPanel panel = new HudPanel(WIDTH).header(tr(KEY + "title"), waypoints.isEmpty() ? ""
                : tr(KEY + (waypoints.size() == 1 ? "waypoint_count.one" : "waypoint_count.other"), waypoints.size()));

        if (target == null) {
            panel.highlight(tr(KEY + "no_destination"), palette.mutedText());
        } else if (sameDimension(target)) {
            panel.accent(palette.success()).highlight(tr(KEY + "direct_to", target.name()), palette.success());
            panel.section(tr(KEY + "destination"));
            panel.row(tr(KEY + "distance"), distanceLabel(target));
            panel.row(tr(KEY + "bearing"), bearingLabel(target));
            panel.row(tr(KEY + "position"), positionLabel(target));
        } else {
            panel.accent(palette.warning()).highlight(tr(KEY + "direct_to", target.name()), palette.warning());
            panel.section(tr(KEY + "destination"));
            panel.row(tr(KEY + "distance"), tr(KEY + "other_dimension"), palette.warning(), null);
            panel.row(tr(KEY + "position"), positionLabel(target));
        }

        panel.section(tr(KEY + "waypoints"));
        if (waypoints.isEmpty()) panel.note(tr(KEY + "empty"));
        scroll = Math.clamp(scroll, 0, Math.max(0, waypoints.size() - VISIBLE_WAYPOINTS));
        panel.scroll(VISIBLE_WAYPOINTS, scroll, this::scrollTo, list -> {
            for (FmsWaypoint waypoint : waypoints) {
                boolean active = target != null && target.id().equals(waypoint.id());
                list.choice(waypoint.name(), sameDimension(waypoint) ? distanceLabel(waypoint) : tr(KEY + "elsewhere"),
                        active, true, () -> toggleDestination(waypoint), () -> edit(waypoint));
                list.tooltip(tr(KEY + "waypoint_tooltip", positionLabel(waypoint)));
            }
        });

        panel.section(tr(KEY + "compass"));
        String compass = compassState();
        panel.row(tr(KEY + "compass_status"), tr(KEY + "compass." + compass), compassColor(compass), null);
        panel.tooltip(tr(KEY + "compass_tooltip"));
        if (compass.equals("unlinked") || compass.equals("other")) {
            panel.buttons(List.of(tr(KEY + (compass.equals("other") ? "relink_compass" : "link_compass"))),
                    List.of(() -> linkCompass(compass.equals("other"))));
        }

        List<String> labels = new ArrayList<>(List.of(tr(KEY + "new_waypoint")));
        List<Runnable> actions = new ArrayList<>(List.of(this::create));
        if (target != null) {
            labels.add(tr(KEY + "clear_target"));
            actions.add(() -> send("clear_target", "", "", 0, 0, 0, -1, false));
        }
        panel.buttons(labels, actions);
        return panel;
    }

    /** Clicking the active waypoint clears the destination; any other one becomes the destination. */
    void toggleDestination(FmsWaypoint waypoint) {
        boolean active = payload.data().selectedWaypointId().map(waypoint.id()::equals).orElse(false);
        if (active) send("clear_target", "", "", 0, 0, 0, -1, false);
        else send("select", waypoint.id().toString(), "", 0, 0, 0, -1, false);
    }

    void scrollTo(int firstRow) { scroll = firstRow; }

    int scroll() { return scroll; }

    /** First row that keeps {@code waypoint} in view when the screen opens (0 without one). */
    static int scrollToShow(List<FmsWaypoint> waypoints, java.util.UUID waypoint) {
        for (int index = 0; index < waypoints.size(); index++) {
            if (waypoints.get(index).id().equals(waypoint)) return Math.max(0, index - VISIBLE_WAYPOINTS + 1);
        }
        return 0;
    }

    private void create() {
        if (minecraft != null) minecraft.setScreen(new FmsWaypointScreen(this, null));
    }

    private void edit(FmsWaypoint waypoint) {
        if (minecraft != null) minecraft.setScreen(new FmsWaypointScreen(this, waypoint));
    }

    /** "missing", "linked" (to this FMS), "unlinked" or "other" (linked to another FMS). */
    String compassState() {
        if (payload.navigatorDisplaySlot() < 0) return "missing";
        String bound = payload.navigatorBoundId();
        if (bound.isBlank()) return "unlinked";
        return payload.data().id().toString().equals(bound) ? "linked" : "other";
    }

    private static int compassColor(String state) {
        var palette = HudPanel.palette();
        return switch (state) {
            case "linked" -> palette.success();
            case "missing" -> palette.mutedText();
            default -> palette.warning();
        };
    }

    /** The button names the relink, so pressing it is the confirmation the server asks for. */
    void linkCompass(boolean relink) {
        send("bind_navigator", "", "", 0, 0, 0, payload.navigatorDisplaySlot(), relink);
    }

    void send(String action, String waypointId, String name, double x, double y, double z,
              int displaySlot, boolean confirmRebind) {
        sender.accept(new FmsActionPayload(payload.position(), payload.column(), payload.row(), action, waypointId,
                name, x, y, z, displaySlot, confirmRebind, payload.interactionToken()));
    }

    boolean sameDimension(FmsWaypoint waypoint) { return payload.dimension().equals(waypoint.dimension()); }

    String distanceLabel(FmsWaypoint waypoint) {
        double distance = Math.sqrt(square(waypoint.x() - payload.currentX())
                + square(waypoint.y() - payload.currentY()) + square(waypoint.z() - payload.currentZ()));
        return distance < 1000 ? String.format(Locale.ROOT, "%.0f m", distance)
                : String.format(Locale.ROOT, "%.1f km", distance / 1000);
    }

    /** Clockwise from north, like the FMS Compass needle. */
    String bearingLabel(FmsWaypoint waypoint) {
        double degrees = Math.toDegrees(FmsCompass.bearing(payload.currentX(), payload.currentZ(),
                waypoint.x(), waypoint.z()));
        return String.format(Locale.ROOT, "%03d°", Math.floorMod(Math.round(degrees), 360));
    }

    static String positionLabel(FmsWaypoint waypoint) {
        return formatCoordinate(waypoint.x()) + " " + formatCoordinate(waypoint.y()) + " "
                + formatCoordinate(waypoint.z());
    }

    private static double square(double value) { return value * value; }

    static String formatCoordinate(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Typed coordinate as the block that contains it, or null when it is not a finite number. */
    static Double parseBlockCoordinate(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) && Math.abs(parsed) <= FmsWaypoint.MAX_COORDINATE ? Math.floor(parsed) : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
