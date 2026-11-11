package com.radaeli.aeronauticsflybyinstruments.integration;

import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsDataChannel;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsSignalSnapshot;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import net.minecraft.core.BlockPos;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TablePeripheralTest {
    private ModularTableBlockEntity table() {
        return new ModularTableBlockEntity(BlockPos.ZERO, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
    }

    @Test
    void buzzerIsVisibleThroughTheTableApiButCannotActivateLampOrSignalControls() throws LuaException {
        var table = table();
        assertTrue(table.placeBuzzer(2, 1));
        var peripheral = ComputerCraftIntegration.peripheral(table);
        var slot = peripheral.getSlot(3, 2);
        assertEquals("buzzer", slot.get("type"));
        assertEquals(1, slot.get("width"));
        assertEquals(1, slot.get("height"));
        assertEquals(slot, peripheral.getModules().getFirst());
        assertEquals(false, peripheral.getOutputs().getFirst().get("active"));
        assertThrows(LuaException.class, () -> peripheral.configureModule(3, 2, "terrain", 1));
        assertThrows(LuaException.class, () -> peripheral.setLampTestIntensity(3, 2, 15));
        assertThrows(LuaException.class, () -> peripheral.clearLampTestIntensity(3, 2));
    }

    @Test
    void exposesOneBasedSlotsAndFootprintQueries() throws LuaException {
        var table = table();
        var peripheral = ComputerCraftIntegration.peripheral(table);
        assertEquals("modular_table", peripheral.getType());
        assertEquals(Map.of("columns", 3, "rows", 3), peripheral.getGridSize());
        assertEquals(false, peripheral.getSlot(1, 1).get("occupied"));
        table.placeLamp(0, 0);
        assertEquals(true, peripheral.getSlot(1, 1).get("occupied"));
        assertEquals(1, peripheral.getSlot(1, 1).get("column"));
        assertEquals("lamp", peripheral.getSlot(1, 1).get("type"));
        assertEquals(1, peripheral.getModules().size());
        assertFalse(peripheral.canFit(1, 1, 1, 1));
        assertTrue(peripheral.canFit(2, 2, 2, 2));
        assertFalse(peripheral.canFit(3, 3, 2, 2));
        assertTrue(table.placeLamp2x1(1, 0, false));
        assertTrue(peripheral.configureModule(2, 1, "long_lamp", 0.5));
        assertTrue(peripheral.setTestSignal("long_lamp", 1));
        var longOutput = peripheral.getOutputs().stream().filter(output -> output.get("type").equals("lamp_2x1"))
                .findFirst().orElseThrow();
        assertEquals(true, longOutput.get("active"));
        assertTrue(peripheral.configureModule(1, 1, "terrain", 30));
        assertTrue(peripheral.setTestSignal("terrain", 35));
        var output = peripheral.getOutputs().getFirst();
        assertEquals(true, output.get("active"));
        assertEquals("terrain", output.get("channel"));
        assertEquals(35.0, output.get("signal"));
        assertTrue(peripheral.setTestSignal("terrain", 10));
        assertEquals(false, peripheral.getOutputs().getFirst().get("active"));
        assertThrows(LuaException.class, () -> peripheral.configureModule(1, 1, "", 1));
        assertThrows(LuaException.class, () -> peripheral.setTestSignal("test", Double.NaN));
        assertThrows(LuaException.class, () -> peripheral.getSlot(0, 1));
        assertThrows(LuaException.class, () -> peripheral.getSlot(1, 4));
        assertThrows(LuaException.class, () -> peripheral.canFit(1, 1, 0, 1));
    }

    @Test
    void tablePeripheralDoesNotExposeNetworkTopology() throws Exception {
        var peripheral = ComputerCraftIntegration.peripheral(table());
        assertThrows(NoSuchMethodException.class, () -> TablePeripheral.class.getMethod("getNetworkStatus"));
        assertThrows(NoSuchMethodException.class, () -> TablePeripheral.class.getMethod("getConnectedDevices"));
        assertNotNull(TablePeripheral.class.getMethod("getGridSize")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
    }

    @Test
    void lampIntensityTestApiValidatesAndClearsTransientOverrides() throws Exception {
        var table = table();
        var peripheral = ComputerCraftIntegration.peripheral(table);
        table.placeLamp(0, 0);
        assertTrue(peripheral.setLampTestIntensity(1, 1, 8));
        assertEquals(8, table.lampRenderIntensity(table.grid().at(0, 0).orElseThrow()));
        assertThrows(LuaException.class, () -> peripheral.setLampTestIntensity(1, 1, 16));
        assertThrows(LuaException.class, () -> peripheral.setLampTestIntensity(2, 1, 4));
        assertThrows(LuaException.class, () -> peripheral.clearLampTestIntensity(4, 1));
        assertTrue(peripheral.clearLampTestIntensity(1, 1));
        assertEquals(0, table.lampRenderIntensity(table.grid().at(0, 0).orElseThrow()));

        assertNotNull(TablePeripheral.class.getMethod("setLampTestIntensity", int.class, int.class, int.class)
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
        assertNotNull(TablePeripheral.class.getMethod("clearLampTestIntensity", int.class, int.class)
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
    }

    @Test
    void longLampHasIndependentSignalAndVisualTestControlsAcrossItsFootprint() throws LuaException {
        var table = table();
        var peripheral = ComputerCraftIntegration.peripheral(table);
        assertTrue(table.placeLamp2x1(0, 0, false));
        assertTrue(peripheral.configureModule(1, 1, "warning", 2));
        assertTrue(peripheral.setTestSignal("warning", 2));
        var longLamp = table.grid().modules().getFirst();
        assertTrue(table.isModuleActive(longLamp));
        assertTrue(peripheral.setLampTestIntensity(2, 1, 11));
        assertEquals(11, table.lampRenderIntensity(longLamp));
        assertTrue(peripheral.clearLampTestIntensity(2, 1));
        assertEquals(15, table.lampRenderIntensity(longLamp));
    }

    @Test
    void fmsPeripheralExposesWaypointCrudSelectionAndTypedSignalContracts() throws Exception {
        var waypoint = new FmsWaypoint(java.util.UUID.randomUUID(), "Home", "minecraft:overworld", 30, 70, -12);
        var data = new FmsModuleData(java.util.UUID.randomUUID(), java.util.List.of(waypoint), waypoint.id());
        var table = table();
        assertTrue(table.placeFmsModule(0, 0, data));
        var peripheral = ComputerCraftIntegration.peripheral(table);

        var waypoints = peripheral.getFmsWaypoints(1, 1);
        assertEquals(1, waypoints.size());
        assertEquals("Home", waypoints.getFirst().get("name"));
        assertEquals(true, waypoints.getFirst().get("selected"));
        var signals = peripheral.getFmsSignals(1, 1);
        assertEquals(true, ((Map<?, ?>) signals.get("waypoint_selected")).get("value"));
        assertEquals("unavailable", ((Map<?, ?>) signals.get("distance_to_waypoint")).get("status"));
        assertTrue(peripheral.getNetworkSignals().isEmpty());

        var channels = table.avionicsSignals();
        assertEquals(2, channels.size());
        assertEquals(java.util.Set.of("waypoint_selected", "distance_to_waypoint"), channels.stream()
                .map(AvionicsDataChannel::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(channels.stream().allMatch(channel -> channel.metadata().equals(
                Map.of("module_column", 1, "module_row", 1))));

        for (String method : java.util.List.of("getFmsWaypoints", "getFmsSignals", "getNetworkSignals",
                "createWaypoint", "updateWaypoint", "deleteWaypoint", "selectWaypoint", "clearWaypointSelection")) {
            assertTrue(java.util.Arrays.stream(TablePeripheral.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(method))
                    .anyMatch(candidate -> candidate.isAnnotationPresent(dan200.computercraft.api.lua.LuaFunction.class)),
                    method + " must be exposed to CC:Tweaked");
        }
        assertThrows(LuaException.class, () -> peripheral.getFmsWaypoints(3, 3));
        assertThrows(LuaException.class, () -> peripheral.selectWaypoint(1, 1, "not-a-uuid"));
        assertThrows(LuaException.class, () -> peripheral.selectWaypoint(1, 1, ""));
    }

    @Test
    void fmsSignalEventsOnlyPublishChannelsWhoseValuesChanged() {
        var waypoint = new FmsWaypoint(java.util.UUID.randomUUID(), "Home", "minecraft:overworld", 1, 64, 1);
        var data = new FmsModuleData(java.util.UUID.randomUUID(), java.util.List.of(waypoint), waypoint.id());
        var table = table();
        table.placeFmsModule(0, 0, data);
        var module = new ModularTableBlockEntity.FmsModuleAt(0, 0, data);
        var previous = new FmsSignalSnapshot(SensorReading.valid(false, "boolean", 1),
                SensorReading.unavailable("blocks", 1));
        var current = new FmsSignalSnapshot(SensorReading.valid(true, "boolean", 2),
                SensorReading.unavailable("blocks", 2));
        var events = new ArrayList<Object[]>();
        var computer = (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(),
                new Class<?>[]{IComputerAccess.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAttachmentName" -> "right";
                    case "queueEvent" -> { events.add(arguments); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        var peripheral = ComputerCraftIntegration.peripheral(table);
        peripheral.attach(computer);
        peripheral.fmsWaypointChanged("selected", table.grid().modules().getFirst(), waypoint, true);
        assertEquals("modular_table_fms_waypoint_changed", events.getFirst()[0]);
        Object[] waypointArgs = (Object[]) events.getFirst()[1];
        assertEquals(true, ((Map<?, ?>) waypointArgs[4]).get("selected"));
        peripheral.fmsSignalChanged(module, previous, current);

        assertEquals(2, events.size());
        assertEquals("modular_table_fms_signal_changed", events.get(1)[0]);
        Object[] args = (Object[]) events.get(1)[1];
        assertEquals("waypoint_selected", args[3]);
        peripheral.detach(computer);
    }

    @Test
    void publishesAttachmentActionAndModuleAndStopsAfterDetach() {
        var table = table();
        var peripheral = ComputerCraftIntegration.peripheral(table);
        var events = new ArrayList<Object[]>();
        var computer = (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(),
                new Class<?>[]{IComputerAccess.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAttachmentName" -> "back";
                    case "queueEvent" -> { events.add(arguments); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        table.placeLamp(1, 2);
        var module = table.grid().at(1, 2).orElseThrow();
        peripheral.attach(computer);
        peripheral.moduleChanged("placed", module);
        assertEquals(1, events.size());
        assertEquals("modular_table_changed", events.getFirst()[0]);
        var arguments = (Object[]) events.getFirst()[1];
        assertEquals("back", arguments[0]);
        assertEquals("placed", arguments[1]);
        assertEquals(TablePeripheral.describe(module), arguments[2]);
        peripheral.detach(computer);
        peripheral.moduleChanged("removed", module);
        assertEquals(1, events.size());
    }
}
