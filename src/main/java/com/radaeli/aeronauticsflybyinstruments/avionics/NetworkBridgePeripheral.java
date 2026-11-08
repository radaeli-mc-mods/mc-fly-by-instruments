package com.radaeli.aeronauticsflybyinstruments.avionics;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CC:Tweaked gateway to the network as a whole: its topology and every channel the endpoints publish (for example
 * the velocity sensor's speed). Single-device flows keep the endpoints' own peripherals.
 */
public final class NetworkBridgePeripheral implements IPeripheral {
    private final NetworkBridgeBlockEntity bridge;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();
    private volatile NetworkSnapshot lastSnapshot;

    public NetworkBridgePeripheral(NetworkBridgeBlockEntity bridge) {
        this.bridge = bridge;
    }

    @Override
    public String getType() {
        return "avionics_network_bridge";
    }

    @Override
    public Object getTarget() {
        return bridge;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof NetworkBridgePeripheral peripheral && peripheral.bridge == bridge;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
        if (computers.size() == 1) {
            lastSnapshot = snapshot();
            AvionicsNetworkManager.register(this, bridge.getLevel());
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        if (computers.isEmpty()) AvionicsNetworkManager.unregister(this, bridge.getLevel());
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNetworkStatus() {
        return describe(snapshot());
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getConnectedDevices() {
        return snapshot().devices().stream().map(NetworkBridgePeripheral::describeDevice).toList();
    }

    /**
     * Every channel published by the endpoints of the complete network, with the device it comes from (coordinates
     * relative to this bridge). Empty while the network is not complete, like the device list.
     */
    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getSignals() {
        Level level = bridge.getLevel();
        if (level == null) return List.of();
        return AvionicsNetworkManager.connectedSignals(level, bridge.getBlockPos()).stream()
                .map(NetworkBridgePeripheral::describeSignal).toList();
    }

    public void refreshNetworkAndNotify() {
        publishIfChanged(snapshot());
    }

    void publishIfChanged(NetworkSnapshot updated) {
        NetworkSnapshot previous = lastSnapshot;
        lastSnapshot = updated;
        if (previous == null || previous.equals(updated)) return;
        Map<String, Object> state = describe(updated);
        for (IComputerAccess computer : computers) {
            computer.queueEvent("avionics_network_changed", computer.getAttachmentName(), state);
        }
    }

    private NetworkSnapshot snapshot() {
        return AvionicsNetworkManager.snapshot(bridge.getLevel(), bridge.getBlockPos());
    }

    private static Map<String, Object> describe(NetworkSnapshot snapshot) {
        return Map.of("status", snapshot.status().name().toLowerCase(Locale.ROOT),
                "connected", snapshot.status() == NetworkStatus.CONNECTED,
                "complete", snapshot.complete(),
                "endpointCount", snapshot.endpointCount(), "wireCount", snapshot.wireCount());
    }

    static Map<String, Object> describeSignal(AvionicsNetworkManager.ConnectedSignal signal) {
        NetworkDevice device = signal.device();
        SensorReading<?> reading = signal.channel().reading();
        Map<String, Object> result = new HashMap<>();
        result.put("name", signal.channel().name());
        result.put("type", device.type());
        result.put("x", device.x());
        result.put("y", device.y());
        result.put("z", device.z());
        result.put("status", reading.status().name().toLowerCase(Locale.ROOT));
        reading.value().ifPresent(value -> result.put("value", value));
        result.put("unit", reading.unit());
        result.put("gameTime", reading.gameTime());
        return Map.copyOf(result);
    }

    private static Map<String, Object> describeDevice(NetworkDevice device) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", device.type());
        result.put("x", device.x());
        result.put("y", device.y());
        result.put("z", device.z());
        return Map.copyOf(result);
    }
}
