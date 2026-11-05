package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeDamping;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceActionPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Attitude Reference Unit in the mod's screen language (visual guide §12). The home page shows only the
 * live attitude and the setups in use (the Nose and Up faces, the level offset and the damping mode),
 * each with a button that opens its own page: {@link Page#FRAME}, {@link Page#LEVEL} and
 * {@link Page#DAMPING}. Every change applies at once; the server answers with the fresh state, and the
 * screen also asks for it every half second while open.
 */
public final class AttitudeReferenceScreen extends HudScreen {
    static final String KEY = "gui.aeronautics_fly_by_instruments.attitude_reference.";
    private static final int WIDTH = 220;
    private static final int REFRESH_TICKS = 10;
    /** Copper ring for the Nose, brass for the Up (guide §8.7). */
    static final int NOSE_COLOR = 0xFFC8703F;
    static final int UP_COLOR = 0xFFD9B04C;

    /** The home page and the dedicated page of each setup. */
    enum Page { HOME, FRAME, LEVEL, DAMPING }

    private final Consumer<AttitudeReferenceActionPayload> sender;
    private AttitudeReferenceScreenPayload payload;
    /** Which role the next face click sets on the frame page: "nose" or "up". */
    private String role = "nose";
    private Page page = Page.HOME;
    private int ticks;

    public AttitudeReferenceScreen(AttitudeReferenceScreenPayload payload) {
        this(payload, PacketDistributor::sendToServer);
    }

    AttitudeReferenceScreen(AttitudeReferenceScreenPayload payload, Consumer<AttitudeReferenceActionPayload> sender) {
        super(Component.translatable(KEY + "title"));
        this.payload = payload;
        this.sender = sender;
    }

    BlockPos position() { return payload.position(); }

    Page page() { return page; }

    void accept(AttitudeReferenceScreenPayload updated) { payload = updated; }

    @Override
    public void tick() {
        if (++ticks % REFRESH_TICKS == 0) send("refresh", "", "");
    }

    void send(String action, String face, String damping) {
        sender.accept(new AttitudeReferenceActionPayload(payload.position(), action, face, damping));
    }

    @Override
    protected HudPanel build() {
        return switch (page) {
            case HOME -> home();
            case FRAME -> frame();
            case LEVEL -> level();
            case DAMPING -> damping();
        };
    }

    private HudPanel home() {
        var palette = HudPanel.palette();
        HudPanel panel = new HudPanel(WIDTH)
                .header(tr(KEY + "title"), tr(KEY + "status." + payload.status()));

        panel.section(tr(KEY + "attitude"), tr(KEY + "info_attitude"));
        if (payload.status().equals("unavailable")) {
            panel.note(tr(KEY + "no_reading"));
        } else {
            panel.row(tr(KEY + "pitch"), degrees(payload.pitch()));
            panel.row(tr(KEY + "roll"), degrees(payload.roll()));
            panel.row(tr(KEY + "heading"), String.format(Locale.ROOT, "%03.0f°", payload.heading()));
        }
        if (!payload.onSubLevel()) panel.note(tr(KEY + "ground_note"));

        panel.section(tr(KEY + "faces"), tr(KEY + "info_frame"));
        panel.row(tr(KEY + "nose"), face(payload.nose()), NOSE_COLOR, null);
        panel.row(tr(KEY + "up"), face(payload.up()), UP_COLOR, null);
        panel.buttons(List.of(tr(KEY + "configure_frame")), List.of(() -> open(Page.FRAME)));

        panel.section(tr(KEY + "level"), tr(KEY + "info_level"));
        panel.row(tr(KEY + "offset"), tr(KEY + "offset_value",
                String.format(Locale.ROOT, "%+.1f°", payload.pitchOffset()),
                String.format(Locale.ROOT, "%+.1f°", payload.rollOffset())));
        panel.buttons(List.of(tr(KEY + "configure_level")), List.of(() -> open(Page.LEVEL)));

        panel.section(tr(KEY + "damping"), tr(KEY + "info_damping"));
        AttitudeDamping mode = AttitudeDamping.parse(payload.damping()).orElse(AttitudeDamping.OFF);
        panel.row(tr(KEY + "damping"), tr(KEY + "damping." + mode.id()), palette.text(), null);
        panel.buttons(List.of(tr(KEY + "configure_damping")), List.of(() -> open(Page.DAMPING)));
        return panel;
    }

    private HudPanel frame() {
        HudPanel panel = new HudPanel(WIDTH).header(tr(KEY + "frame_title"), "");
        panel.buttons(List.of(tr(KEY + "back")), List.of(() -> open(Page.HOME)));

        panel.section(tr(KEY + "faces"));
        panel.row(tr(KEY + "nose"), face(payload.nose()), NOSE_COLOR, null);
        panel.row(tr(KEY + "up"), face(payload.up()), UP_COLOR, null);
        panel.choice(tr(KEY + "set_nose"), "", role.equals("nose"), true, () -> role = "nose")
                .tooltip(tr(KEY + "set_nose_note"));
        panel.choice(tr(KEY + "set_up"), "", role.equals("up"), true, () -> role = "up")
                .tooltip(tr(KEY + "set_up_note"));
        List<String> labels = List.of(face("west"), face("north"), face("east"), face("south"));
        panel.buttons(labels, List.of(() -> pick("west"), () -> pick("north"), () -> pick("east"), () -> pick("south")));
        panel.buttons(List.of(face("up"), face("down")), List.of(() -> pick("up"), () -> pick("down")));
        return panel;
    }

    private HudPanel level() {
        HudPanel panel = new HudPanel(WIDTH).header(tr(KEY + "level_title"), "");
        panel.buttons(List.of(tr(KEY + "back")), List.of(() -> open(Page.HOME)));

        panel.section(tr(KEY + "level"));
        panel.row(tr(KEY + "offset"), tr(KEY + "offset_value",
                String.format(Locale.ROOT, "%+.1f°", payload.pitchOffset()),
                String.format(Locale.ROOT, "%+.1f°", payload.rollOffset())));
        panel.buttons(List.of(tr(KEY + "calibrate"), tr(KEY + "reset")),
                List.of(() -> send("calibrate", "", ""), () -> send("reset", "", "")))
                .tooltip(tr(KEY + "calibrate_note"));
        return panel;
    }

    private HudPanel damping() {
        AttitudeDamping mode = AttitudeDamping.parse(payload.damping()).orElse(AttitudeDamping.OFF);
        HudPanel panel = new HudPanel(WIDTH).header(tr(KEY + "damping_title"), "");
        panel.buttons(List.of(tr(KEY + "back")), List.of(() -> open(Page.HOME)));

        panel.section(tr(KEY + "damping")).tooltip(tr(KEY + "damping_note"));
        for (AttitudeDamping option : AttitudeDamping.values()) {
            panel.choice(tr(KEY + "damping." + option.id()), "", option == mode, true,
                    () -> send("damping", "", option.id()));
        }
        return panel;
    }

    private void open(Page next) {
        page = next;
    }

    private void pick(String face) {
        send(role, face, "");
    }

    private static String degrees(double value) {
        return String.format(Locale.ROOT, "%+.1f°", value);
    }

    static String face(String name) {
        return tr(KEY + "face." + name);
    }
}
