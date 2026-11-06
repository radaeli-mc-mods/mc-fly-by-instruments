package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ModularTableBlockEntityTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    public static ModularTableBlockEntity table() {
        return new ModularTableBlockEntity(BlockPos.ZERO, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
    }

    @Test
    void buzzerSharesTheGridPersistsAndHasNoLampControls() {
        var original = table();
        assertTrue(original.placeBuzzer(0, 0));
        assertFalse(original.placeLamp(0, 0));
        assertFalse(original.placeBuzzer(0, 0));
        assertTrue(original.placeLamp2x1(1, 0, false));
        assertFalse(original.placeBuzzer(2, 0));
        assertFalse(original.placeBuzzer(3, 0));
        var buzzer = original.grid().at(0, 0).orElseThrow();
        assertEquals("buzzer", buzzer.type());
        assertFalse(original.configureModule(0, 0, "terrain", 1));
        assertFalse(original.setLampTestIntensity(0, 0, 15));
        assertTrue(original.setTestSignal("test", 1));
        assertFalse(original.isModuleActive(buzzer));

        for (var data : new CompoundTag[]{original.saveWithoutMetadata(REGISTRIES), original.getUpdateTag(REGISTRIES)}) {
            var restored = table();
            restored.loadCustomOnly(data, REGISTRIES);
            assertEquals(original.grid().modules(), restored.grid().modules());
            assertTrue(restored.removeModule(0, 0));
            assertTrue(restored.placeLamp(0, 0));
        }
    }

    /** The server syncs the table through its update tag: a module type the loader rejects vanishes on the client. */
    @Test
    void attitudeIndicatorSurvivesSaveAndClientSync() {
        var original = table();
        assertTrue(original.placeSpanning(PanelCoordinates.ATTITUDE_INDICATOR, 0, 0, 2, 2, null));
        assertFalse(original.placeLamp(1, 1), "The 2x2 takes its four cells");
        for (var data : new CompoundTag[]{original.saveWithoutMetadata(REGISTRIES), original.getUpdateTag(REGISTRIES)}) {
            var restored = table();
            restored.loadCustomOnly(data, REGISTRIES);
            assertEquals(original.grid().modules(), restored.grid().modules());
            assertEquals(1, restored.grid().modules().size());
            assertTrue(restored.removeModule(1, 1));
            assertTrue(restored.grid().modules().isEmpty());
        }
    }

    @Test
    void attitudeIndicatorSettingsPersistSyncAndDieWithTheModule() {
        var original = table();
        assertTrue(original.attitudeIndicatorAt(0, 0).isEmpty());
        assertFalse(original.setAttitudeAltitudeTape(0, 0, false), "Nothing there to configure");
        assertTrue(original.placeSpanning(PanelCoordinates.ATTITUDE_INDICATOR, 1, 1, 2, 2, null));
        assertTrue(original.attitudeIndicatorAt(1, 1).orElseThrow().altitudeTape(), "The tape is on by default");
        assertTrue(original.attitudeIndicatorAt(2, 2).isPresent(), "Any cell of the footprint finds the module");
        assertTrue(original.setAttitudeAltitudeTape(2, 2, false));
        assertFalse(original.attitudeIndicatorAt(1, 1).orElseThrow().altitudeTape());
        assertFalse(original.setAttitudeAltitudeTape(0, 0, true));

        for (var data : new CompoundTag[]{original.saveWithoutMetadata(REGISTRIES), original.getUpdateTag(REGISTRIES)}) {
            var restored = table();
            restored.loadCustomOnly(data, REGISTRIES);
            assertFalse(restored.attitudeIndicatorAt(1, 1).orElseThrow().altitudeTape());
        }

        assertTrue(original.removeModule(1, 1));
        assertTrue(original.attitudeIndicatorAt(1, 1).isEmpty());
        assertTrue(original.placeSpanning(PanelCoordinates.ATTITUDE_INDICATOR, 1, 1, 2, 2, null));
        assertTrue(original.attitudeIndicatorAt(1, 1).orElseThrow().altitudeTape(),
                "A new module starts from the defaults, not from the one removed");
    }

    @Test
    void anAttitudeIndicatorSavedBeforeItHadSettingsKeepsTheDefaults() {
        var original = table();
        assertTrue(original.placeSpanning(PanelCoordinates.ATTITUDE_INDICATOR, 0, 0, 2, 2, null));
        var data = original.saveWithoutMetadata(REGISTRIES);
        data.getList("Modules", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("AttitudeData");
        var restored = table();
        restored.loadCustomOnly(data, REGISTRIES);
        assertTrue(restored.attitudeIndicatorAt(0, 0).orElseThrow().altitudeTape());
    }

    @Test
    void newModulesUseGpwsDefaultsAndLegacyModuleDataKeepsLocalTestMode() {
        var table = table();
        assertTrue(table.placeLamp(0, 0));
        assertTrue(table.placeBuzzer(1, 0));
        var newLamp = table.grid().at(0, 0).orElseThrow();
        var newBuzzer = table.grid().at(1, 0).orElseThrow();
        assertEquals("gpws", newLamp.outputSource());
        assertEquals("gpws.any", newLamp.alertChannel());
        assertEquals(2, newLamp.minimumSeverity());
        assertEquals(100, newBuzzer.buzzerVolume());

        CompoundTag legacy = table.saveWithoutMetadata(REGISTRIES);
        var modules = legacy.getList("Modules", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < modules.size(); i++) {
            var entry = modules.getCompound(i);
            entry.remove("OutputSource"); entry.remove("AlertChannel");
            entry.remove("MinimumSeverity"); entry.remove("BuzzerVolume");
        }
        var restored = table();
        restored.loadCustomOnly(legacy, REGISTRIES);
        assertTrue(restored.grid().modules().stream().allMatch(module -> module.outputSource().equals("test")));
    }

    @Test
    void persistedBuzzerWithInvalidFootprintIsIgnored() {
        var original = table();
        original.placeBuzzer(1, 1);
        var data = original.saveWithoutMetadata(REGISTRIES);
        data.getList("Modules", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("Width", 2);
        var restored = table();
        restored.loadCustomOnly(data, REGISTRIES);
        assertTrue(restored.grid().modules().isEmpty());
    }

    @Test
    void savedModulesSurviveReloadAndClientUpdateTags() {
        var original = table();
        assertTrue(original.placeLamp(0, 0));
        assertTrue(original.placeLamp(2, 2));
        assertTrue(original.configureModule(0, 0, "terrain", 20));
        assertTrue(original.setTestSignal("terrain", 25));
        var restored = table();
        restored.loadCustomOnly(original.saveWithoutMetadata(REGISTRIES), REGISTRIES);
        assertEquals(original.grid().modules(), restored.grid().modules());
        assertEquals(25, restored.testSignal("terrain"));
        assertTrue(restored.isModuleActive(restored.grid().at(0, 0).orElseThrow()));
        var client = table();
        client.loadCustomOnly(original.getUpdateTag(REGISTRIES), REGISTRIES);
        assertEquals(original.grid().modules(), client.grid().modules());
        assertEquals(25, client.testSignal("terrain"));
        assertTrue(restored.removeModule(0, 0));
        assertEquals(1, restored.grid().modules().size());
    }

    @Test
    void independentLampStatesReachTheClientAndCanBeSwapped() {
        var original = table();
        assertTrue(original.placeLamp(0, 0));
        assertTrue(original.placeLamp(1, 0));
        assertTrue(original.configureModule(0, 0, "lamp_a", 0.5));
        assertTrue(original.configureModule(1, 0, "lamp_b", 0.5));
        assertTrue(original.setTestSignal("lamp_a", 1));
        assertTrue(original.setTestSignal("lamp_b", 0));

        var client = table();
        client.loadCustomOnly(original.getUpdateTag(REGISTRIES), REGISTRIES);
        assertTrue(client.isModuleActive(client.grid().at(0, 0).orElseThrow()));
        assertFalse(client.isModuleActive(client.grid().at(1, 0).orElseThrow()));

        assertTrue(original.setTestSignal("lamp_a", 0));
        assertTrue(original.setTestSignal("lamp_b", 1));
        client.loadCustomOnly(original.getUpdateTag(REGISTRIES), REGISTRIES);
        assertFalse(client.isModuleActive(client.grid().at(0, 0).orElseThrow()));
        assertTrue(client.isModuleActive(client.grid().at(1, 0).orElseThrow()));
    }

    @Test
    void lampTestIntensityIsTransientAndSyncedOnlyToClientUpdates() {
        var original = table();
        assertTrue(original.placeLamp(0, 0));
        var lamp = original.grid().at(0, 0).orElseThrow();
        assertTrue(original.setLampTestIntensity(0, 0, 7));
        assertEquals(7, original.lampRenderIntensity(lamp));

        var client = table();
        client.loadCustomOnly(original.getUpdateTag(REGISTRIES), REGISTRIES);
        assertEquals(7, client.lampRenderIntensity(client.grid().at(0, 0).orElseThrow()));

        var saved = original.saveWithoutMetadata(REGISTRIES);
        assertFalse(saved.contains("LampTestIntensities"));
        var restored = table();
        restored.loadCustomOnly(saved, REGISTRIES);
        assertEquals(0, restored.lampRenderIntensity(restored.grid().at(0, 0).orElseThrow()));

        assertTrue(original.configureModule(0, 0, "active", 0.5));
        assertTrue(original.setTestSignal("active", 1));
        assertTrue(original.clearLampTestIntensity(0, 0));
        assertEquals(15, original.lampRenderIntensity(original.grid().at(0, 0).orElseThrow()));
    }

    @Test
    void tableLightFollowsTheBrightestLampScaledToTheCeiling() {
        var table = table();
        assertEquals(0, table.brightestLampIntensity());
        assertTrue(table.placeBuzzer(2, 2));
        assertTrue(table.placeLamp(0, 0));
        assertTrue(table.placeLamp2x1(1, 0, false));
        assertEquals(0, table.brightestLampIntensity(), "Unlit lamps and buzzers emit nothing");
        assertTrue(table.setLampTestIntensity(0, 0, 7));
        assertTrue(table.setLampTestIntensity(1, 0, 4));
        assertEquals(7, table.brightestLampIntensity());

        assertEquals(7, ModularTableBlockEntity.emittedLight(7, 15));
        assertEquals(10, ModularTableBlockEntity.emittedLight(15, 10));
        assertEquals(5, ModularTableBlockEntity.emittedLight(7, 10));
        assertEquals(0, ModularTableBlockEntity.emittedLight(15, 0), "A zero ceiling disables the light");
        assertEquals(0, ModularTableBlockEntity.emittedLight(0, 15));
    }

    @Test
    void corruptedDuplicateOutOfBoundsAndUnknownModulesAreIgnored() {
        var data = new CompoundTag();
        var modules = new ListTag();
        for (String type : new String[]{"lamp", "lamp", "unknown"}) {
            var entry = new CompoundTag();
            entry.putString("Type", type);
            entry.putInt("Width", 1);
            entry.putInt("Height", 1);
            modules.add(entry);
        }
        var bad = new CompoundTag();
        bad.putString("Type", "lamp");
        bad.putInt("Column", 3);
        bad.putInt("Width", 1);
        bad.putInt("Height", 1);
        modules.add(bad);
        data.put("Modules", modules);
        var restored = table();
        restored.loadCustomOnly(data, REGISTRIES);
        assertEquals(1, restored.grid().modules().size());
    }

    @Test
    void fmsWaypointsSurviveWorldSaveClientSyncTableRemovalAndReinstallation() {
        UUID moduleId = UUID.randomUUID();
        var waypoint = new FmsWaypoint(UUID.randomUUID(), "Hangar", "minecraft:overworld", 15.5, 72, -4.5);
        var data = new FmsModuleData(moduleId, List.of(waypoint), waypoint.id());
        var original = table();
        assertTrue(original.placeFmsModule(0, 0, data));

        for (var saved : new CompoundTag[]{original.saveWithoutMetadata(REGISTRIES), original.getUpdateTag(REGISTRIES)}) {
            var restored = table();
            restored.loadCustomOnly(saved, REGISTRIES);
            var persisted = restored.fmsAt(0, 0).orElseThrow().data();
            assertEquals(moduleId, persisted.id());
            assertEquals(List.of(waypoint), persisted.waypoints());
            assertEquals(waypoint.id(), persisted.selectedWaypointId().orElseThrow());

            var droppedStack = restored.removeModuleStack(0, 0);
            assertTrue(droppedStack.is(ModBlocks.FMS_MODULE.get()));
            var carried = FmsModuleItem.load(droppedStack);
            assertEquals(moduleId, carried.id());
            assertEquals(List.of(waypoint), carried.waypoints());
            assertEquals(waypoint.id(), carried.selectedWaypointId().orElseThrow());

            var reinstalled = table();
            assertTrue(reinstalled.placeFmsModule(1, 0, carried));
            assertEquals(waypoint, reinstalled.fmsAt(1, 0).orElseThrow().data()
                    .selectedWaypoint().orElseThrow());
        }
    }

    @Test
    void fmsModuleOccupiesItsWholeFootprintAndOnlyItCanReturnItsDataItem() {
        var original = table();
        assertTrue(original.placeFmsModule(0, 0, new FmsModuleData()));
        assertEquals(2, original.grid().at(1, 0).orElseThrow().width());
        assertEquals(3, original.grid().at(1, 0).orElseThrow().height());
        assertFalse(original.placeLamp(1, 1));
        assertFalse(original.placeFmsModule(1, 0, new FmsModuleData()));
        assertTrue(original.removeModuleStack(1, 1).is(ModBlocks.FMS_MODULE.get()));
        assertTrue(original.grid().modules().isEmpty());
    }

    @Test
    void legacyFmsFootprintExpandsWhenPossibleAndPreservesDataWhenItCannot() {
        var source = table();
        var waypoint = new FmsWaypoint(UUID.randomUUID(), "Legacy", "minecraft:overworld", 8, 70, 9);
        assertTrue(source.placeFmsModule(0, 0,
                new FmsModuleData(UUID.randomUUID(), List.of(waypoint), waypoint.id())));
        var expandable = source.saveWithoutMetadata(REGISTRIES);
        var expandableModule = expandable.getList("Modules", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        expandableModule.putInt("Height", 2);
        var restored = table();
        restored.loadCustomOnly(expandable, REGISTRIES);
        assertEquals(3, restored.grid().at(0, 2).orElseThrow().height());
        assertEquals(waypoint, restored.fmsAt(0, 0).orElseThrow().data().waypoints().getFirst());

        expandableModule.putInt("Row", 1);
        var bottomAnchored = table();
        bottomAnchored.loadCustomOnly(expandable, REGISTRIES);
        assertEquals(2, bottomAnchored.grid().at(0, 2).orElseThrow().height());
        assertEquals(waypoint, bottomAnchored.fmsAt(0, 1).orElseThrow().data().waypoints().getFirst());

        expandableModule.putInt("Row", 0);
        var lamp = new CompoundTag();
        lamp.putString("Type", "lamp");
        lamp.putInt("Column", 0);
        lamp.putInt("Row", 2);
        lamp.putInt("Width", 1);
        lamp.putInt("Height", 1);
        expandable.getList("Modules", net.minecraft.nbt.Tag.TAG_COMPOUND).add(lamp);
        var occupied = table();
        occupied.loadCustomOnly(expandable, REGISTRIES);
        assertEquals(2, occupied.grid().at(0, 0).orElseThrow().height());
        assertEquals("lamp", occupied.grid().at(0, 2).orElseThrow().type());
        assertEquals(waypoint, occupied.fmsAt(0, 0).orElseThrow().data().waypoints().getFirst());
    }
}
