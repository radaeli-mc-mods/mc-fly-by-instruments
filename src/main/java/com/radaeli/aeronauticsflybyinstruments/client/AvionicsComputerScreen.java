package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AvionicsComputerControlPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AvionicsComputerScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Diagnostic screen of the Avionics Computer (plan §5.1): overall state, sensors with roles and
 * the GPWS functions. Clicking a function inhibits it; the footer cycles the sensitivity and tests.
 * Asks the server for a fresh snapshot every half second while open.
 */
public final class AvionicsComputerScreen extends HudScreen {
    private static final String KEY = "gui.aeronautics_fly_by_instruments.avionics_computer.";
    private static final int WIDTH = 236;
    private static final int REFRESH_TICKS = 10;

    private final Consumer<AvionicsComputerControlPayload> sender;
    private AvionicsComputerScreenPayload payload;
    private int ticks;

    public AvionicsComputerScreen(AvionicsComputerScreenPayload payload) {
        this(payload, PacketDistributor::sendToServer);
    }

    AvionicsComputerScreen(AvionicsComputerScreenPayload payload, Consumer<AvionicsComputerControlPayload> sender) {
        super(Component.translatable(KEY + "title"));
        this.payload = payload;
        this.sender = sender;
    }

    public BlockPos position() { return payload.position(); }

    public void accept(AvionicsComputerScreenPayload updated) { payload = updated; }

    @Override
    public void tick() {
        if (++ticks % REFRESH_TICKS == 0) send("refresh", "");
    }

    void send(String action, String value) {
        sender.accept(new AvionicsComputerControlPayload(payload.position(), action, value));
    }

    @Override
    protected HudPanel build() {
        HudPanel panel = new HudPanel(WIDTH)
                .accent(stateColor(payload.state()))
                .header(tr(KEY + "title"), tr(KEY + "sensitivity_meta", tr(KEY + "sensitivity." + payload.sensitivity())))
                .highlight(headline(), stateColor(payload.state()));

        panel.section(tr(KEY + "sensors"));
        if (payload.sensors().isEmpty()) panel.note(tr(KEY + "no_sensors"));
        for (var sensor : payload.sensors()) {
            panel.row(sensorLabel(sensor), sensorValue(sensor), sensorColor(sensor), null);
            if (sensor.issue().equals("no_side")) panel.tooltip(tr(KEY + "no_side_tooltip"));
        }

        panel.section(tr(KEY + "functions"));
        for (var function : payload.functions()) {
            boolean inop = function.id().equals("sensor_fail");
            panel.row(tr(KEY + "function." + function.id()), functionValue(function), functionColor(function),
                    inop ? null : () -> send("inhibit", function.id()));
        }
        panel.buttons(List.of(tr(KEY + "sensitivity_button"), tr(KEY + "test")),
                List.of(() -> send("sensitivity", ""), () -> send("test", "")));
        return panel;
    }

    private String headline() {
        String state = payload.state();
        if (state.equals("caution") || state.equals("warning")) {
            return tr(KEY + "state." + state) + " · " + tr(KEY + "function." + payload.alert()).toUpperCase(Locale.ROOT);
        }
        if (state.equals("inop") && !payload.network().equals("connected")) {
            return tr(KEY + "state.inop") + " · " + tr(KEY + "network." + payload.network());
        }
        if (state.equals("normal") && payload.phase().equals("ground")) {
            return tr(KEY + "state.normal") + " · " + tr(KEY + "on_ground");
        }
        return tr(KEY + "state." + state);
    }

    static int stateColor(String state) {
        var palette = HudPanel.palette();
        return switch (state) {
            case "warning", "test" -> palette.error();
            case "caution", "inop" -> palette.warning();
            default -> palette.success();
        };
    }

    private String sensorLabel(AvionicsComputerScreenPayload.SensorLine sensor) {
        return tr(KEY + "role." + sensor.type() + "." + sensor.role());
    }

    private String sensorValue(AvionicsComputerScreenPayload.SensorLine sensor) {
        if (!sensor.status().equals("ok")) return tr(KEY + "sensor_status." + sensor.status());
        if (!sensor.issue().isEmpty()) return tr(KEY + "issue." + sensor.issue());
        if (Double.isNaN(sensor.value())) return tr(KEY + "clear");
        return String.format(Locale.ROOT, "%.1f m", sensor.value());
    }

    private static int sensorColor(AvionicsComputerScreenPayload.SensorLine sensor) {
        var palette = HudPanel.palette();
        if (!sensor.status().equals("ok")) return palette.error();
        return sensor.issue().isEmpty() ? palette.text() : palette.warning();
    }

    private String functionValue(AvionicsComputerScreenPayload.FunctionLine function) {
        return switch (function.state()) {
            case "active" -> function.id().equals("sensor_fail") ? tr(KEY + "inop")
                    : tr(KEY + "severity." + function.severity());
            case "inhibited" -> tr(KEY + "inhibited");
            case "unavailable" -> function.reason().isEmpty() ? tr(KEY + "inop")
                    : tr(KEY + (List.of("no_clearance", "partial_coverage", "settling").contains(function.reason())
                            ? "no_data" : "inop")) + ": " + tr(KEY + "reason." + function.reason());
            default -> function.id().equals("sensor_fail") ? tr(KEY + "ok") : tr(KEY + "armed");
        };
    }

    private static int functionColor(AvionicsComputerScreenPayload.FunctionLine function) {
        var palette = HudPanel.palette();
        return switch (function.state()) {
            case "active" -> function.severity().equals("warning") ? palette.error() : palette.warning();
            case "unavailable" -> palette.warning();
            case "inhibited" -> palette.mutedText();
            default -> palette.success();
        };
    }
}
