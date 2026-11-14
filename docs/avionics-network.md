# Avionics Network and Network Bridge

The avionics network carries data only. Devices join it through the
[Avionics Wire](avionics-wire.md): hanging wires between Wire Connectors mounted
on their faces, with connectors on plain blocks acting as junctions. There are
no ports, frequencies or wireless links. The Modular Table Base, Network Bridge,
flight sensors and the [Avionics Computer](avionics-computer.md) are endpoints.
The old Avionics Cable block was removed in 0.2.0.

A Modular Table Base mounted directly on any face of an Avionics Computer (on top
or on a side) is **docked**: the two act as one device on the network, with no
wire between them. Reaching either reaches both, and the connectors on both
blocks link them to the rest of the network.

The `Network Bridge` is the dedicated CC:Tweaked gateway; endpoint peripherals
remain independent.

## Lua API

The `avionics_network_bridge` peripheral provides:

| Method | Return |
| --- | --- |
| `getNetworkStatus()` | `{status, connected, complete, endpointCount, wireCount}` |
| `getConnectedDevices()` | List of `{type, x, y, z}` relative to the bridge |
| `getSignals()` | List of `{name, type, x, y, z, status, value?, unit, gameTime}` for every channel published by the complete network; empty otherwise |

Possible statuses are `disconnected`, `connected`, `incomplete`, and
`limit_exceeded`. `complete` is false for the last two states. Counts are the
number of devices and wires observed so far; when a network is incomplete they are lower
bounds, not a claim that the entire topology was inspected. Device enumeration
is empty for incomplete or limited results, so a partial result cannot be
mistaken for a complete device list. Device coordinates are relative to this
bridge in its current level/sublevel and are not internal graph identifiers.
`connected` is true only when the entire reachable topology is confirmed.

The `avionics_network_changed` event is queued on computers attached to the
bridge after the server confirms a changed snapshot. Its arguments are the
attachment name and the same status table returned by `getNetworkStatus()`.
The bridge also exposes the channels the endpoints publish (`getSignals()`, below).
For every sensor reading at once, with roles and the GPWS alerts, use the
[Avionics Computer](avionics-computer.md) (`avionics_computer`); each sensor also
keeps its own peripheral.

```lua
local bridge = assert(peripheral.find("avionics_network_bridge"), "Bridge não encontrada")
local network = bridge.getNetworkStatus()
print(network.status, network.endpointCount, network.wireCount)

for _, device in ipairs(bridge.getConnectedDevices()) do
    print(device.type, device.x, device.y, device.z)
end

while true do
    local _, name, updated = os.pullEvent("avionics_network_changed")
    print(name, updated.status, updated.endpointCount, updated.wireCount)
end
```

Channels are named after the value they carry (for example `velocity.axial`, in `blocks/s`, signed along the
sensor's face). Status is `valid`, `unavailable` or `failed`; `value` is present only when valid.

### Sensor availability

Every Radar Altimeter, Terrain Scanner (both variants), Attitude Reference Unit
and Simulated Velocity Sensor publishes a separate `sensor.available` channel.
It has a boolean `value`, unit `boolean`, and `gameTime` for the most recent
availability response. `true` means the sensor is operating and responding;
it does not promise that a measurement exists. A scan without returns can
therefore coexist with `sensor.available = true`. Failed acquisition or a
heartbeat older than two scan intervals produces `false`. The Velocity Sensor
answers live when queried; the other sensors refresh their heartbeat every
server tick. Availability is queried live, not stored in the topology cache.

```lua
local bridge = assert(peripheral.find("avionics_network_bridge"), "Network Bridge missing")
for _, signal in ipairs(bridge.getSignals()) do
  if signal.name == "sensor.available" then
    print(signal.type, signal.x, signal.y, signal.z, signal.value, signal.gameTime)
  end
end
```

## Endpoints from other mods

Some blocks come from other mods and cannot implement our interfaces. `AvionicsEndpoints` adapts them, one class at a
time, without changing the other mod: today the Simulated **Velocity Sensor** (`velocity_sensor`, channel
`velocity.axial`, see [velocity-sensor-plan.md](velocity-sensor-plan.md)). A wire connector mounts on any face, so such
a block joins the network the same way as ours.

## Limits and persistence

Topology is calculated server-side from the saved connectors and endpoint blocks;
the graph itself is not duplicated in saved data. Changes to relevant blocks
and chunk load state invalidate the cache, with multiple changes coalesced at
server tick end. No periodic full-network scan runs while the world is idle.

The server config `maxAvionicsNetworkBlocks` defaults to `4096` visited connectors
and endpoint blocks per network. Traversal never loads chunks. If a path reaches
an unloaded chunk, status is `incomplete` and the graph is reconsidered when
chunk state changes. If the configured visit limit is exceeded, status is
`limit_exceeded` and no partial endpoint list is returned. Networks do not
bridge separate levels, dimensions, or sublevels.

## Test status

Automated tests cover graph lines, branches, cycles, endpoints, disconnection,
unloaded boundaries, visit limits, rediscovery after removal/reconnection,
typed reading invariants, bridge methods/events, and the table's lack of
aggregate network methods. In-game validation should connect a bridge, table,
and sensor; exercise branches and removals; and verify Lua topology/events.
