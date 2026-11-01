package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.block.TableConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Joined tables (same face, side by side in its plane) act as one panel. Storage stays per table:
 * each module lives in the table holding its top-left cell and may run into the tables after it
 * along the panel axes. The panel frame counts cells from the panel's top-left table, so UI
 * coordinates are never negative.
 *
 * <p>Two frames are used: the <em>table frame</em> (cells relative to one table, 0-2 inside it,
 * used by physical targeting with direct neighbour lookups) and the <em>panel frame</em> (cells of
 * the whole joined surface, used by the UI and its network actions).</p>
 */
public final class TablePanel {
    /** Flood-fill cap: a larger joined surface is managed as several panels. */
    public static final int MAX_TABLES = 256;
    private static final Map<Level, int[]> VERSIONS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Finds the table at a position, or null when it is missing or its chunk is not loaded. Tests swap it. */
    interface Lookup {
        ModularTableBlockEntity table(ModularTableBlockEntity from, BlockPos pos);
    }

    static Lookup lookup = TablePanel::levelTable;

    private static ModularTableBlockEntity levelTable(ModularTableBlockEntity from, BlockPos pos) {
        var level = from.getLevel();
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ModularTableBlockEntity other ? other : null;
    }

    /** Whether the chunk at {@code pos} is loaded (unknown tables never count as missing). */
    private static boolean loaded(ModularTableBlockEntity from, BlockPos pos) {
        var level = from.getLevel();
        return level == null || level.isLoaded(pos);
    }

    /** A module and its top-left cell in the frame it was looked up in. */
    public record Placed(ModularTableBlockEntity owner, PanelGrid.Module module, int column, int row) {
        public boolean contains(int x, int y) {
            return x >= column && x < column + module.width() && y >= row && y < row + module.height();
        }
    }

    private final List<ModularTableBlockEntity> members;
    private final Direction facing;
    private final int minU, minV;
    private long lastAlertUpdate = Long.MIN_VALUE;
    private long lastBuzzerUpdate = Long.MIN_VALUE;
    private final Map<BuzzerChannel, Long> nextBuzzerAt = new HashMap<>();

    private record BuzzerChannel(String source, String channel, int severity) { }

    @FunctionalInterface
    interface BuzzerPlayer {
        void play(ModularTableBlockEntity table, PanelGrid.Module module, int severity);
    }

    private TablePanel(List<ModularTableBlockEntity> members, Direction facing) {
        this.members = List.copyOf(members);
        this.facing = facing;
        this.minU = members.stream().mapToInt(table -> u(table.getBlockPos(), facing)).min().orElse(0);
        this.minV = members.stream().mapToInt(table -> v(table.getBlockPos(), facing)).min().orElse(0);
    }

    // --- Axes ------------------------------------------------------------------------------

    /** World direction of growing panel columns (PanelCoordinates panel X). */
    public static Vec3i columnAxis(Direction facing) {
        return switch (facing) {
            case UP, DOWN, NORTH -> new Vec3i(1, 0, 0);
            case SOUTH -> new Vec3i(-1, 0, 0);
            case WEST -> new Vec3i(0, 0, -1);
            case EAST -> new Vec3i(0, 0, 1);
        };
    }

    /** World direction of growing panel rows (PanelCoordinates panel Z). */
    public static Vec3i rowAxis(Direction facing) {
        return switch (facing) {
            case UP -> new Vec3i(0, 0, 1);
            case DOWN -> new Vec3i(0, 0, -1);
            default -> new Vec3i(0, 1, 0);
        };
    }

    private static int u(BlockPos pos, Direction facing) {
        var axis = columnAxis(facing);
        return pos.getX() * axis.getX() + pos.getY() * axis.getY() + pos.getZ() * axis.getZ();
    }

    private static int v(BlockPos pos, Direction facing) {
        var axis = rowAxis(facing);
        return pos.getX() * axis.getX() + pos.getY() * axis.getY() + pos.getZ() * axis.getZ();
    }

    /** Position of the table {@code du} tables along the columns and {@code dv} along the rows. */
    public static BlockPos tilePos(BlockPos pos, Direction facing, int du, int dv) {
        var u = columnAxis(facing);
        var v = rowAxis(facing);
        return pos.offset(u.getX() * du + v.getX() * dv, u.getY() * du + v.getY() * dv, u.getZ() * du + v.getZ() * dv);
    }

    // --- Cache -----------------------------------------------------------------------------

    /** Any table placed, loaded, removed or turned in this level may change every panel. */
    static void invalidate(Level level) {
        if (level != null) VERSIONS.computeIfAbsent(level, ignored -> new int[1])[0]++;
    }

    static int version(Level level) {
        return VERSIONS.computeIfAbsent(level, ignored -> new int[1])[0];
    }

    /** The panel the table belongs to; cached per table until any table in the level changes. */
    public static TablePanel of(ModularTableBlockEntity table) {
        var facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
        var level = table.getLevel();
        int version = level == null ? -1 : version(level);
        if (level != null && table.panel != null && table.panelVersion == version) return table.panel;
        List<ModularTableBlockEntity> members = new ArrayList<>();
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<ModularTableBlockEntity>();
        queue.add(table);
        seen.add(table.getBlockPos());
        while (!queue.isEmpty() && members.size() < MAX_TABLES) {
            var current = queue.poll();
            members.add(current);
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                var next = neighbor(current, step[0], step[1]);
                if (next != null && seen.add(next.getBlockPos())) queue.add(next);
            }
        }
        members.sort(Comparator.comparingLong(member -> member.getBlockPos().asLong()));
        var panel = new TablePanel(members, facing);
        if (level == null) return panel;
        for (var member : members) {
            member.panel = panel;
            member.panelVersion = version;
        }
        return panel;
    }

    // --- Panel frame ----------------------------------------------------------------------

    public List<ModularTableBlockEntity> members() { return members; }

    /** Samples shared inputs (GPWS) for the whole panel. */
    public ModularTableBlockEntity leader() { return members.get(0); }

    /** The leader samples first, then sounds all buzzers on each channel in the same tick. */
    void tickAlerts(ModularTableBlockEntity tickingTable, long now, int scanInterval,
                    Runnable sampleAlerts, BuzzerPlayer player) {
        if (tickingTable != leader() || lastBuzzerUpdate == now) return;
        lastBuzzerUpdate = now;
        if (lastAlertUpdate == Long.MIN_VALUE || now - lastAlertUpdate >= scanInterval) {
            lastAlertUpdate = now;
            sampleAlerts.run();
        }
        Map<BuzzerChannel, List<Placed>> active = new HashMap<>();
        for (var member : members) member.expirePanelTest(now);
        for (var placed : modules()) {
            var module = placed.module();
            var owner = placed.owner();
            if (!module.type().equals("buzzer") || module.buzzerVolume() <= 0) continue;
            boolean testing = owner.testingPanel(now);
            int severity = testing ? 3 : owner.moduleSeverity(module);
            if (severity == 0 || (!testing && owner.muted())) continue;
            String source = testing ? "panel_test" : module.outputSource();
            String name = module.outputSource().equals("test") ? module.channel() : module.alertChannel();
            var channel = new BuzzerChannel(source, testing ? "" : name, severity);
            active.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(placed);
        }
        // Inactive or muted channels restart together when they next become audible.
        nextBuzzerAt.keySet().retainAll(active.keySet());
        active.forEach((channel, buzzers) -> {
            if (now < nextBuzzerAt.getOrDefault(channel, Long.MIN_VALUE)) return;
            nextBuzzerAt.put(channel, now + PanelAlertLogic.buzzerInterval(channel.severity()));
            for (var buzzer : buzzers) player.play(buzzer.owner(), buzzer.module(), channel.severity());
        });
    }

    public int originColumn(ModularTableBlockEntity table) {
        return (u(table.getBlockPos(), facing) - minU) * PanelGrid.SIZE;
    }

    public int originRow(ModularTableBlockEntity table) {
        return (v(table.getBlockPos(), facing) - minV) * PanelGrid.SIZE;
    }

    /** Converts a lookup in {@code table}'s frame to this panel's frame. */
    public Placed toPanel(ModularTableBlockEntity table, Placed local) {
        return new Placed(local.owner(), local.module(), local.column() + originColumn(table),
                local.row() + originRow(table));
    }

    public List<Placed> modules() {
        List<Placed> result = new ArrayList<>();
        for (var member : members) {
            int column = originColumn(member), row = originRow(member);
            for (var module : member.grid().modules()) {
                result.add(new Placed(member, module, column + module.column(), row + module.row()));
            }
        }
        return result;
    }

    public Optional<Placed> at(int column, int row) {
        return modules().stream().filter(placed -> placed.contains(column, row)).findFirst();
    }

    public void setMuted(boolean muted) {
        for (var member : members) member.setMuted(muted);
    }

    public void testPanel() {
        for (var member : members) member.testPanel();
    }

    public int applyBasicSetup() {
        return members.stream().mapToInt(ModularTableBlockEntity::applyBasicSetup).sum();
    }

    public boolean setTestSignal(String channel, double value) {
        boolean accepted = true;
        for (var member : members) accepted &= member.setTestSignal(channel, value);
        return accepted;
    }

    // --- Table frame (direct neighbours only; safe for shapes and hit tests) --------------

    /** The joined table {@code du}/{@code dv} tables away, if its chunk is loaded. */
    public static ModularTableBlockEntity neighbor(ModularTableBlockEntity table, int du, int dv) {
        if (du == 0 && dv == 0) return table;
        var pos = tilePos(table.getBlockPos(), table.getBlockState().getValue(ModularTableBaseBlock.FACING), du, dv);
        var other = lookup.table(table, pos);
        return other != null && TableConnections.joins(table.getBlockState(), other.getBlockState()) ? other : null;
    }

    /** Modules over the table's own 3 x 3 cells: its own and those running in from the tables before it. */
    public static List<Placed> covering(ModularTableBlockEntity table) {
        List<Placed> result = new ArrayList<>();
        for (int du = -1; du <= 0; du++) for (int dv = -1; dv <= 0; dv++) {
            var owner = neighbor(table, du, dv);
            if (owner == null) continue;
            for (var module : owner.grid().modules()) {
                int column = du * PanelGrid.SIZE + module.column(), row = dv * PanelGrid.SIZE + module.row();
                if (column < PanelGrid.SIZE && column + module.width() > 0
                        && row < PanelGrid.SIZE && row + module.height() > 0) {
                    result.add(new Placed(owner, module, column, row));
                }
            }
        }
        return result;
    }

    /** Module over a cell in {@code table}'s frame (the cell may lie in a neighbouring table). */
    public static Optional<Placed> localAt(ModularTableBlockEntity table, int column, int row) {
        int tu = Math.floorDiv(column, PanelGrid.SIZE), tv = Math.floorDiv(row, PanelGrid.SIZE);
        for (int du = tu - 1; du <= tu; du++) for (int dv = tv - 1; dv <= tv; dv++) {
            var owner = neighbor(table, du, dv);
            if (owner == null) continue;
            for (var module : owner.grid().modules()) {
                var placed = new Placed(owner, module, du * PanelGrid.SIZE + module.column(),
                        dv * PanelGrid.SIZE + module.row());
                if (placed.contains(column, row)) return Optional.of(placed);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a module anchored at a cell of {@code table} fits: every cell lies on a joined table
     * and is free, so footprints may cross into the tables after this one.
     */
    public static boolean canFit(ModularTableBlockEntity table, int column, int row, int width, int height) {
        if (width < 1 || height < 1 || width > PanelGrid.SIZE || height > PanelGrid.SIZE
                || column < 0 || row < 0 || column >= PanelGrid.SIZE || row >= PanelGrid.SIZE) return false;
        for (int x = column; x < column + width; x++) for (int y = row; y < row + height; y++) {
            if (neighbor(table, Math.floorDiv(x, PanelGrid.SIZE), Math.floorDiv(y, PanelGrid.SIZE)) == null
                    || localAt(table, x, y).isPresent()) return false;
        }
        return true;
    }

    /**
     * False once a table under the footprint is gone or no longer joined. Unloaded tables count as
     * present, so a module is never dropped just because the next chunk has not loaded yet.
     */
    public static boolean supported(ModularTableBlockEntity table, PanelGrid.Module module) {
        var facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
        int lastU = Math.floorDiv(module.column() + module.width() - 1, PanelGrid.SIZE);
        int lastV = Math.floorDiv(module.row() + module.height() - 1, PanelGrid.SIZE);
        for (int du = 0; du <= lastU; du++) for (int dv = 0; dv <= lastV; dv++) {
            if (du == 0 && dv == 0) continue;
            var pos = tilePos(table.getBlockPos(), facing, du, dv);
            if (loaded(table, pos) && neighbor(table, du, dv) == null) return false;
        }
        return true;
    }
}
