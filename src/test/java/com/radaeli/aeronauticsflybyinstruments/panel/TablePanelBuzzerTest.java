package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TablePanelBuzzerTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private final Map<BlockPos, ModularTableBlockEntity> world = new HashMap<>();
    private final List<Beep> beeps = new ArrayList<>();
    private TablePanel.Lookup original;

    private record Beep(BlockPos table, int column, int severity, long tick) { }

    @BeforeEach
    void useFakeWorld() {
        original = TablePanel.lookup;
        TablePanel.lookup = (from, pos) -> world.get(pos);
    }

    @AfterEach
    void restoreLookup() {
        TablePanel.lookup = original;
    }

    private ModularTableBlockEntity table(int z) {
        var pos = new BlockPos(0, 0, z);
        var table = new ModularTableBlockEntity(pos, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
        world.put(pos, table);
        assertTrue(table.placeBuzzer(0, 0));
        return table;
    }

    private void alerts(TablePanel panel, Map<String, Integer> severities) {
        for (var member : panel.members()) {
            var tag = member.getUpdateTag(REGISTRIES);
            var alerts = new CompoundTag();
            severities.forEach(alerts::putInt);
            tag.put("AlertSeverities", alerts);
            member.loadCustomOnly(tag, REGISTRIES);
        }
    }

    private void tick(TablePanel panel, ModularTableBlockEntity member, long now, Runnable sample) {
        panel.tickAlerts(member, now, 5, sample, (owner, module, severity) ->
                beeps.add(new Beep(owner.getBlockPos(), module.column(), severity, now)));
    }

    private void tick(TablePanel panel, long now) {
        tick(panel, panel.leader(), now, () -> { });
    }

    @Test
    void sameChannelSoundsTogetherRegardlessOfTableTickOrder() {
        for (boolean neighborFirst : new boolean[]{true, false}) {
            world.clear();
            beeps.clear();
            var a = table(0);
            var b = table(1);
            var panel = TablePanel.of(b);
            var leader = panel.leader();
            var follower = leader == a ? b : a;
            // Both ticks begin with no active alert. The leader acquires it this tick.
            Runnable sample = () -> alerts(panel, Map.of("gpws.any", 3));
            for (int now = 0; now <= 20; now++) {
                tick(panel, neighborFirst ? follower : leader, now, sample);
                tick(panel, neighborFirst ? leader : follower, now, sample);
            }
            assertEquals(6, beeps.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(i * 10L, beeps.get(i * 2).tick());
                assertEquals(i * 10L, beeps.get(i * 2 + 1).tick());
                assertNotEquals(beeps.get(i * 2).table(), beeps.get(i * 2 + 1).table());
            }
        }
    }

    @Test
    void addedAndUnmutedBuzzersJoinTheExistingChannelCadence() {
        var a = table(0);
        var b = table(1);
        assertTrue(b.removeModule(0, 0));
        var panel = TablePanel.of(a);
        alerts(panel, Map.of("gpws.any", 3));
        tick(panel, 0);
        assertTrue(b.placeBuzzer(0, 0));
        assertTrue(b.setBuzzerVolume(0, 0, 40));
        tick(panel, 1);
        assertEquals(1, beeps.size(), "A new buzzer waits for the channel's next beat");
        tick(panel, 10);
        assertEquals(3, beeps.size());
        assertEquals(10, beeps.get(1).tick());
        assertEquals(10, beeps.get(2).tick());
        b.setMuted(true);
        tick(panel, 20);
        b.setMuted(false);
        tick(panel, 21);
        assertEquals(4, beeps.size());
        tick(panel, 30);
        assertEquals(6, beeps.size());
        assertEquals(30, beeps.get(4).tick());
        assertEquals(30, beeps.get(5).tick());
    }

    @Test
    void differentThresholdsJoinTogetherOnEscalationAndRestartAfterSilence() {
        var a = table(0);
        var b = table(1);
        assertTrue(b.setIndication(0, 0, "warning"));
        var panel = TablePanel.of(a);
        alerts(panel, Map.of("gpws.any", 2));
        tick(panel, 0);
        assertEquals(List.of(new Beep(a.getBlockPos(), 0, 2, 0)), beeps);
        alerts(panel, Map.of("gpws.any", 3));
        tick(panel, 5);
        assertEquals(3, beeps.size());
        assertEquals(5, beeps.get(1).tick());
        assertEquals(5, beeps.get(2).tick());
        alerts(panel, Map.of());
        tick(panel, 6);
        alerts(panel, Map.of("gpws.any", 3));
        tick(panel, 7);
        assertEquals(5, beeps.size(), "Reactivated channels have no stale per-module cooldown");
        assertEquals(7, beeps.get(3).tick());
        assertEquals(7, beeps.get(4).tick());
        panel.setMuted(true);
        tick(panel, 8);
        panel.setMuted(false);
        tick(panel, 9);
        assertEquals(7, beeps.size());
    }

    @Test
    void separateChannelsKeepTheirCadenceAndZeroVolumeStaysSilent() {
        var a = table(0);
        var b = table(1);
        assertTrue(a.placeBuzzer(1, 0));
        assertTrue(a.configureAlertModule(1, 0, "gpws", "gpws.terrain", 2));
        assertTrue(b.setBuzzerVolume(0, 0, 0));
        var panel = TablePanel.of(a);
        alerts(panel, Map.of("gpws.any", 3, "gpws.terrain", 2));
        for (int now = 0; now <= 40; now++) tick(panel, now);
        assertEquals(List.of(0L, 10L, 20L, 30L, 40L), beeps.stream()
                .filter(beep -> beep.column() == 0).map(Beep::tick).toList());
        assertEquals(List.of(0L, 20L, 40L), beeps.stream()
                .filter(beep -> beep.column() == 1).map(Beep::tick).toList());
        assertTrue(beeps.stream().allMatch(beep -> beep.table().equals(a.getBlockPos())));
    }

    @Test
    void localTestChannelsAlsoShareCadenceAcrossThePanel() {
        var a = table(0);
        var b = table(1);
        for (var member : List.of(a, b)) {
            assertTrue(member.configureAlertModule(0, 0, "test", "gpws.any", 2, "alarm", 0.5));
            assertTrue(member.setTestSignal("alarm", 1));
        }
        var panel = TablePanel.of(a);
        tick(panel, 0);
        tick(panel, 1);
        tick(panel, 10);
        assertEquals(List.of(0L, 0L, 10L, 10L), beeps.stream().map(Beep::tick).toList());
    }
}
