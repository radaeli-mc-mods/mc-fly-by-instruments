package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.radaeli.aeronauticsflybyinstruments.integration.ComputerCraftIntegration;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class NetworkBridgePeripheralTest {
    @Test
    void bridgeOwnsTheNetworkApiAndComputerCraftMethods() throws Exception {
        var bridge = new NetworkBridgeBlockEntity(BlockPos.ZERO, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState());
        var peripheral = ComputerCraftIntegration.peripheral(bridge);
        assertEquals("avionics_network_bridge", peripheral.getType());
        assertEquals("disconnected", peripheral.getNetworkStatus().get("status"));
        assertTrue(peripheral.getConnectedDevices().isEmpty());
        assertNotNull(NetworkBridgePeripheral.class.getMethod("getNetworkStatus")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
        assertNotNull(NetworkBridgePeripheral.class.getMethod("getConnectedDevices")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
    }

    @Test
    void notifiesAttachedComputersWithChangedNetworkSnapshot() {
        var bridge = new NetworkBridgeBlockEntity(BlockPos.ZERO, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState());
        var peripheral = ComputerCraftIntegration.peripheral(bridge);
        var events = new ArrayList<Object[]>();
        var computer = (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(),
                new Class<?>[]{IComputerAccess.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAttachmentName" -> "bridge";
                    case "queueEvent" -> { events.add(arguments); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        peripheral.attach(computer);
        var connected = new NetworkSnapshot(NetworkStatus.CONNECTED, 1,
                java.util.List.of(new NetworkDevice("radar_altimeter", 1, 0, -1)));
        peripheral.publishIfChanged(connected);
        assertEquals(1, events.size());
        assertEquals("avionics_network_changed", events.getFirst()[0]);
        assertEquals("bridge", ((Object[]) events.getFirst()[1])[0]);
        peripheral.publishIfChanged(connected);
        assertEquals(1, events.size());
        peripheral.detach(computer);
    }

    @Test
    void signalsOfTheWholeNetworkAreEmptyWithoutALevelOrAConnectedNetwork() throws Exception {
        var bridge = new NetworkBridgeBlockEntity(BlockPos.ZERO, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState());
        assertTrue(ComputerCraftIntegration.peripheral(bridge).getSignals().isEmpty());
        assertNotNull(NetworkBridgePeripheral.class.getMethod("getSignals")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
    }

    @Test
    void aSignalNamesItsDeviceAndItsReadingForLua() {
        var channel = new AvionicsDataChannel<>("velocity.axial",
                SensorReading.valid(-2.5, "blocks/s", 40L));
        var signal = new AvionicsNetworkManager.ConnectedSignal(new NetworkDevice("velocity_sensor", 3, -1, 2), channel);
        var described = NetworkBridgePeripheral.describeSignal(signal);
        assertEquals("velocity.axial", described.get("name"));
        assertEquals("velocity_sensor", described.get("type"));
        assertEquals(3, described.get("x"));
        assertEquals(-1, described.get("y"));
        assertEquals(2, described.get("z"));
        assertEquals("valid", described.get("status"));
        assertEquals(-2.5, described.get("value"));
        assertEquals("blocks/s", described.get("unit"));
        assertEquals(40L, described.get("gameTime"));
    }

    @Test
    void anUnavailableSignalHasNoValue() {
        var channel = new AvionicsDataChannel<>("velocity.axial", SensorReading.<Double>unavailable("blocks/s", 9L));
        var described = NetworkBridgePeripheral.describeSignal(
                new AvionicsNetworkManager.ConnectedSignal(new NetworkDevice("velocity_sensor", 0, 0, 1), channel));
        assertEquals("unavailable", described.get("status"));
        assertFalse(described.containsKey("value"));
    }
}
