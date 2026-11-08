package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorActionPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorScreenPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Attitude Indicator in the mod's screen language (visual guide §12): what the display shows now, where it gets it
 * from on the cable network, and which tapes it draws. Every change applies at once; the server answers with the
 * fresh state, and the screen asks for it every half second while open.
 */
public final class AttitudeIndicatorScreen extends HudScreen {
    static final String KEY = "gui.aeronautics_fly_by_instruments.attitude_indicator.";
    private static final int WIDTH = 220;
    private static final int REFRESH_TICKS = 10;

    private final Consumer<AttitudeIndicatorActionPayload> sender;
    private AttitudeIndicatorScreenPayload payload;
    private int ticks;

    public AttitudeIndicatorScreen(AttitudeIndicatorScreenPayload payload) {
        this(payload, PacketDistributor::sendToServer);
    }

    AttitudeIndicatorScreen(AttitudeIndicatorScreenPayload payload,
                            Consumer<AttitudeIndicatorActionPayload> sender) {
        super(Component.translatable(KEY + "title"));
        this.payload = payload;
        this.sender = sender;
    }

    boolean isFor(AttitudeIndicatorScreenPayload other) {
        return payload.position().equals(other.position()) && payload.column() == other.column()
                && payload.row() == other.row();
    }

    void accept(AttitudeIndicatorScreenPayload updated) { payload = updated; }

    @Override
    public void tick() {
        if (++ticks % REFRESH_TICKS == 0) send("refresh", false);
    }

    void send(String action, boolean value) {
        sender.accept(new AttitudeIndicatorActionPayload(payload.position(), payload.column(), payload.row(),
                payload.interactionToken(), action, value));
    }

    void toggleAltitudeTape() { send("altitude_tape", !payload.altitudeTape()); }

    void toggleSpeedTape() { send("speed_tape", !payload.speedTape()); }

    @Override
    protected HudPanel build() {
        var palette = HudPanel.palette();
        String status = payload.status();
        int accent = status.equals("ok") ? palette.success() : status.equals("degraded") ? palette.warning()
                : palette.error();
        HudPanel panel = new HudPanel(WIDTH).accent(accent)
                .header(tr(KEY + "title"), tr(KEY + "status." + status));

        panel.section(tr(KEY + "attitude"), tr(KEY + "info_attitude"));
        if (status.equals("no_data")) {
            panel.note(tr(KEY + "no_data_note"));
        } else {
            panel.row(tr(KEY + "pitch"), degrees(payload.pitch()));
            panel.row(tr(KEY + "roll"), degrees(payload.roll()));
            if (status.equals("degraded")) panel.note(tr(KEY + "degraded_note"));
        }

        panel.section(tr(KEY + "sources"), tr(KEY + "info_sources"));
        panel.row(tr(KEY + "unit"), tr(KEY + "link." + payload.attitude()), linkColor(payload.attitude(), true), null);
        boolean altimeterOk = payload.altimeter().equals("ok");
        String altimeter = !altimeterOk ? tr(KEY + "link." + payload.altimeter())
                : Double.isNaN(payload.agl()) ? tr(KEY + "no_ground")
                : tr(KEY + "blocks", String.format(Locale.ROOT, "%.1f", payload.agl()));
        // The altimeter is optional: its absence is only worth a warning while the tape asks for it.
        panel.row(tr(KEY + "altimeter"), altimeter,
                linkColor(payload.altimeter(), payload.altitudeTape()), null);
        boolean velocityOk = payload.velocity().equals("ok");
        String velocity = velocityOk
                ? tr(KEY + "speed_value", String.format(Locale.ROOT, "%.1f", payload.speed()))
                : tr(KEY + "link." + payload.velocity());
        panel.row(tr(KEY + "velocity"), velocity, linkColor(payload.velocity(), payload.speedTape()), null);

        panel.section(tr(KEY + "tapes"), tr(KEY + "info_tapes"));
        boolean tape = payload.altitudeTape();
        panel.choice(tr(KEY + "altitude_tape"), tr(tape ? KEY + "on" : KEY + "off"), tape, true,
                this::toggleAltitudeTape).tooltip(tr(KEY + "altitude_tape_note"));
        boolean speed = payload.speedTape();
        panel.choice(tr(KEY + "speed_tape"), tr(speed ? KEY + "on" : KEY + "off"), speed, true,
                this::toggleSpeedTape).tooltip(tr(KEY + "speed_tape_note"));
        return panel;
    }

    /** Green when the source answers; a missing source is red when the display cannot work without it. */
    private static int linkColor(String link, boolean required) {
        var palette = HudPanel.palette();
        return switch (link) {
            case "ok" -> palette.success();
            case "none" -> required ? palette.error() : palette.mutedText();
            case "no_network" -> palette.error();
            default -> palette.warning();
        };
    }

    private static String degrees(double value) {
        return String.format(Locale.ROOT, "%+.1f°", value);
    }
}
