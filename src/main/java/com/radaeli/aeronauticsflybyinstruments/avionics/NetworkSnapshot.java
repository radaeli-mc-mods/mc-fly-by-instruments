package com.radaeli.aeronauticsflybyinstruments.avionics;

import java.util.List;
import java.util.Comparator;

public record NetworkSnapshot(NetworkStatus status, int endpointCount,
                              List<NetworkDevice> devices, int wireCount) {
    public NetworkSnapshot {
        devices = devices.stream().sorted(Comparator.comparingInt(NetworkDevice::x)
                .thenComparingInt(NetworkDevice::y).thenComparingInt(NetworkDevice::z)
                .thenComparing(NetworkDevice::type)).toList();
    }

    /** Networks with no wires seen. */
    public NetworkSnapshot(NetworkStatus status, int endpointCount, List<NetworkDevice> devices) {
        this(status, endpointCount, devices, 0);
    }

    public boolean complete() {
        return status == NetworkStatus.CONNECTED || status == NetworkStatus.DISCONNECTED;
    }

    public static NetworkSnapshot disconnected() {
        return new NetworkSnapshot(NetworkStatus.DISCONNECTED, 0, List.of());
    }
}
