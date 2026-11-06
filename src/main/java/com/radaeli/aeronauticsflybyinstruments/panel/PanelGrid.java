package com.radaeli.aeronauticsflybyinstruments.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;

/**
 * Occupancy of one table, owned by each module's top-left cell. {@link #place} keeps a footprint
 * inside the table; {@link #placeAnchored} lets it run into joined tables (see TablePanel).
 */
public final class PanelGrid {
    public static final int SIZE = 3;
    private final List<Module> modules = new ArrayList<>();

    /**
     * {@code view} is the horizontal direction the player was looking when the module was placed; only the
     * attitude indicator reads it (see {@link PanelCoordinates#attitudeTurn}).
     */
    public record Module(String type, int column, int row, int width, int height, String channel, double threshold,
                         String outputSource, String alertChannel, int minimumSeverity, int buzzerVolume,
                         Direction view) {
        public Module(String type, int column, int row, int width, int height, String channel, double threshold,
                      String outputSource, String alertChannel, int minimumSeverity, int buzzerVolume) {
            this(type, column, row, width, height, channel, threshold, outputSource, alertChannel, minimumSeverity,
                    buzzerVolume, Direction.NORTH);
        }
        public Module(String type, int column, int row, int width, int height) {
            this(type, column, row, width, height, "test", 0.5, "test", "gpws.any", 2, 100);
        }
        public Module(String type, int column, int row, int width, int height, String channel, double threshold) {
            this(type, column, row, width, height, channel, threshold, "test", "gpws.any", 2, 100);
        }

        public boolean contains(int x, int y) {
            return x >= column && x < column + width && y >= row && y < row + height;
        }
    }

    public boolean canFit(int column, int row, int width, int height) {
        if (column < 0 || row < 0 || width < 1 || height < 1
                || width > SIZE || height > SIZE || column > SIZE - width || row > SIZE - height) {
            return false;
        }
        for (int x = column; x < column + width; x++) {
            for (int y = row; y < row + height; y++) {
                if (at(x, y).isPresent()) return false;
            }
        }
        return true;
    }

    public boolean place(Module module) {
        if (module.type() == null || module.type().isBlank()
                || !canFit(module.column(), module.row(), module.width(), module.height())) return false;
        modules.add(module);
        return true;
    }

    /**
     * Places a module anchored in this table whose footprint may run past its far edges into the
     * joined tables after it (TablePanel checks those cells). Only this grid's own overlap is checked.
     */
    public boolean placeAnchored(Module module) {
        if (module.type() == null || module.type().isBlank() || module.width() < 1 || module.height() < 1
                || module.width() > SIZE || module.height() > SIZE || module.column() < 0 || module.row() < 0
                || module.column() >= SIZE || module.row() >= SIZE) return false;
        for (int x = module.column(); x < module.column() + module.width(); x++) {
            for (int y = module.row(); y < module.row() + module.height(); y++) {
                if (at(x, y).isPresent()) return false;
            }
        }
        modules.add(module);
        return true;
    }

    /** Whether the footprint runs past this table into its neighbours. */
    public static boolean spans(Module module) {
        return module.column() + module.width() > SIZE || module.row() + module.height() > SIZE;
    }

    public Optional<Module> at(int column, int row) {
        return modules.stream().filter(module -> module.contains(column, row)).findFirst();
    }

    public Optional<Module> removeAt(int column, int row) {
        var module = at(column, row);
        module.ifPresent(modules::remove);
        return module;
    }

    public boolean configureAt(int column, int row, String channel, double threshold) {
        var existing = at(column, row);
        if (existing.isEmpty()) return false;
        var module = existing.get();
        modules.set(modules.indexOf(module), new Module(module.type(), module.column(), module.row(),
                module.width(), module.height(), channel, threshold, "test", module.alertChannel(),
                module.minimumSeverity(), module.buzzerVolume(), module.view()));
        return true;
    }

    public boolean configureAlertAt(int column, int row, String source, String alertChannel, int minimumSeverity) {
        var existing = at(column, row);
        if (existing.isEmpty()) return false;
        return configureAlertAt(column, row, source, alertChannel, minimumSeverity,
                existing.get().channel(), existing.get().threshold());
    }

    public boolean configureAlertAt(int column, int row, String source, String alertChannel, int minimumSeverity,
                                    String localChannel, double localThreshold) {
        var existing = at(column, row);
        boolean validLocalSignal = !source.equals("test") || localChannel != null && !localChannel.isBlank()
                && localChannel.length() <= 64 && Double.isFinite(localThreshold);
        if (existing.isEmpty() || !(source.equals("gpws") || source.equals("test")
                || source.equals(LampIndication.SOURCE_REDSTONE_LINK))
                || alertChannel == null || alertChannel.isBlank() || alertChannel.length() > 64
                || minimumSeverity < 1 || minimumSeverity > 3 || !validLocalSignal) return false;
        var module = existing.get();
        modules.set(modules.indexOf(module), new Module(module.type(), module.column(), module.row(),
                module.width(), module.height(), source.equals("test") ? localChannel : module.channel(),
                source.equals("test") ? localThreshold : module.threshold(), source, alertChannel,
                minimumSeverity, module.buzzerVolume(), module.view()));
        return true;
    }

    public boolean setBuzzerVolumeAt(int column, int row, int volume) {
        var existing = at(column, row);
        if (existing.isEmpty() || !existing.get().type().equals("buzzer") || volume < 0 || volume > 100) return false;
        var module = existing.get();
        modules.set(modules.indexOf(module), new Module(module.type(), module.column(), module.row(),
                module.width(), module.height(), module.channel(), module.threshold(), module.outputSource(),
                module.alertChannel(), module.minimumSeverity(), volume, module.view()));
        return true;
    }

    public List<Module> modules() {
        return List.copyOf(modules);
    }
}
