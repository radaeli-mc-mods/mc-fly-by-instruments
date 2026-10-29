package com.radaeli.aeronauticsflybyinstruments.avionics;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** CC:Tweaked gateway to network-wide topology; endpoints retain their own peripherals. */
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
        return Map.of("status", snapshot.status().name().toLowerCase(java.util.Locale.ROOT),
                "connected", snapshot.status() == NetworkStatus.CONNECTED,
                "complete", snapshot.complete(),
                "endpointCount", snapshot.endpointCount(), "wireCount", snapshot.wireCount());
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
