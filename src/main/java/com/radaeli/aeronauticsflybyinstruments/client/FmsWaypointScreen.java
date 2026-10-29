package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import net.minecraft.util.StringUtil;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Creates or edits one FMS waypoint on top of {@link FmsScreen}, which it returns to. A new
 * waypoint starts at the player's block with the name focused, so marking the spot is a name and
 * Enter. Editing applies when leaving with Done or ESC; Delete asks for a second click.
 * Tab moves between the fields, Enter runs the first footer button.
 */
public final class FmsWaypointScreen extends HudScreen {
    private static final String KEY = FmsScreen.KEY;
    private static final int WIDTH = 200;
    private static final int COORDINATE_LENGTH = 12;
    static final int NAME = 0, X = 1, Y = 2, Z = 3;

    private final FmsScreen parent;
    /** Null while creating. */
    private final FmsWaypoint editing;
    private final String[] values = new String[4];
    private int focused = NAME;
    /** Validation shows only once the player tried to save, or while a coordinate is unreadable. */
    private boolean attempted;
    private boolean confirmingDelete;

    FmsWaypointScreen(FmsScreen parent, FmsWaypoint editing) {
        super(Component.translatable(KEY + (editing == null ? "create_waypoint" : "edit_waypoint")));
        this.parent = parent;
        this.editing = editing;
        if (editing == null) {
            var payload = parent.payload();
            values[NAME] = "";
            values[X] = FmsScreen.formatCoordinate(Math.floor(payload.currentX()));
            values[Y] = FmsScreen.formatCoordinate(Math.floor(payload.currentY()));
            values[Z] = FmsScreen.formatCoordinate(Math.floor(payload.currentZ()));
        } else {
            values[NAME] = editing.name();
            values[X] = FmsScreen.formatCoordinate(editing.x());
            values[Y] = FmsScreen.formatCoordinate(editing.y());
            values[Z] = FmsScreen.formatCoordinate(editing.z());
        }
    }

    FmsScreen parent() { return parent; }

    @Override
    protected HudPanel build() {
        var palette = HudPanel.palette();
        String problem = problem();
        boolean showProblem = problem != null && (attempted || !problem.equals("name"));
        HudPanel panel = new HudPanel(WIDTH).header(tr(KEY + (editing == null ? "create_waypoint" : "edit_waypoint")),
                editing != null && parent.sameDimension(editing) ? parent.distanceLabel(editing) : "");
        if (showProblem) panel.accent(palette.warning()).highlight(tr(KEY + "invalid." + problem), palette.warning());

        panel.field(tr(KEY + "name"), values[NAME], tr(KEY + "name_hint"), focused == NAME,
                showProblem && problem.equals("name"), () -> focus(NAME));
        panel.section(tr(KEY + "position"));
        String[] axes = {"X", "Y", "Z"};
        for (int field = X; field <= Z; field++) {
            int index = field;
            panel.field(axes[field - X], values[field], "", focused == field,
                    FmsScreen.parseBlockCoordinate(values[field]) == null, () -> focus(index));
            panel.tooltip(tr(KEY + "coordinate_tooltip"));
        }
        panel.buttons(List.of(tr(KEY + "current_position")), List.of(this::useCurrentPosition));

        if (editing == null) {
            panel.buttons(List.of(tr(KEY + "add"), tr(KEY + "back")), List.of(this::submit, this::back));
        } else {
            panel.buttons(List.of(tr(KEY + "done"), tr(KEY + (confirmingDelete ? "confirm_delete" : "delete"))),
                    List.of(this::submit, this::delete));
        }
        return panel;
    }

    /** Null when the form can be saved, otherwise "name" or "coordinates". */
    String problem() {
        if (values[NAME].trim().isEmpty()) return "name";
        for (int field = X; field <= Z; field++) {
            if (FmsScreen.parseBlockCoordinate(values[field]) == null) return "coordinates";
        }
        return null;
    }

    String value(int field) { return values[field]; }

    int focused() { return focused; }

    void focus(int field) {
        focused = field;
        confirmingDelete = false;
    }

    void useCurrentPosition() {
        var payload = parent.payload();
        values[X] = FmsScreen.formatCoordinate(Math.floor(payload.currentX()));
        values[Y] = FmsScreen.formatCoordinate(Math.floor(payload.currentY()));
        values[Z] = FmsScreen.formatCoordinate(Math.floor(payload.currentZ()));
        confirmingDelete = false;
    }

    /** Adds the waypoint, or applies the edit when something changed; stays open while invalid. */
    void submit() {
        attempted = true;
        if (problem() != null) return;
        String name = values[NAME].trim();
        double x = FmsScreen.parseBlockCoordinate(values[X]);
        double y = FmsScreen.parseBlockCoordinate(values[Y]);
        double z = FmsScreen.parseBlockCoordinate(values[Z]);
        if (editing == null) {
            parent.send("create", "", name, x, y, z, -1, false);
        } else if (!name.equals(editing.name()) || x != editing.x() || y != editing.y() || z != editing.z()) {
            parent.send("update", editing.id().toString(), name, x, y, z, -1, false);
        }
        back();
    }

    void delete() {
        if (editing == null) return;
        if (!confirmingDelete) {
            confirmingDelete = true;
            return;
        }
        parent.send("delete", editing.id().toString(), "", 0, 0, 0, -1, false);
        back();
    }

    private void back() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** ESC: an edit is kept when it is valid; a new waypoint is only added with Add. */
    @Override
    public void onClose() {
        if (editing != null && problem() == null) submit();
        else back();
    }

    void type(char character) {
        if (focused == NAME) {
            if (!StringUtil.isAllowedChatCharacter(character) || values[NAME].length() >= FmsWaypoint.MAX_NAME_LENGTH) return;
        } else if (!(Character.isDigit(character) || character == '-' || character == '.')
                || values[focused].length() >= COORDINATE_LENGTH) {
            return;
        }
        values[focused] += character;
        confirmingDelete = false;
    }

    void paste(String text) {
        for (char character : text.toCharArray()) type(character);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        type(character);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                String value = values[focused];
                values[focused] = hasControlDown() || value.isEmpty() ? "" : value.substring(0, value.length() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                focus(Math.floorMod(focused + (hasShiftDown() ? -1 : 1), 4));
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                submit();
                return true;
            }
            default -> {
                if (isPaste(keyCode) && minecraft != null) {
                    paste(minecraft.keyboardHandler.getClipboard());
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
