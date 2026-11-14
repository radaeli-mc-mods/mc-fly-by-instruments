# Radar Altimeter

The Radar Altimeter measures clearance from the lower face of its block. Its
beam follows that local axis as an Aeronautics sublevel moves or tilts. The
block also samples normally when placed in a stationary world.

## Visual and placement

The v2 model follows Create's brass instruments (optical sensor, stockpile switch):
a brass lid band over a dark-wood body between brass corner posts, with a framed
redstone slot on every side. The **lower sensing face** carries a brass bezel and
a dark glass lens with concentric rings; the top uses Create's brass casing. There
are no painted arrows: the lens below and the silhouette show the cast direction.
The inventory icon is rendered slightly from below so the lens is visible.

The model stays within its block cell. Its outline and collision follow the body,
bezel and lens rather than a full cube. The lens is centred at model `(8, 0, 8)`,
matching the unchanged local cast origin. In a sublevel, this local bottom follows
the vessel's attitude as before. Cable sockets are centred on each face.

Editable source and export instructions: [Blockbench project](../blockbench/radar-altimeter/README.md).

## Sampling behavior

- Nine casts: one central and eight evenly spaced around it, in a 20-degree
  half-angle cone.
- At least three casts must hit for a valid sample. The returned value is the
  median distance of valid hits, measured from the sensor's lower face in
  decimal blocks.
- Collision shapes in the world and other sublevels are valid targets. The
  sensor's host sublevel and entities are ignored.
- Casts do not load chunks. A cast that reaches an unloaded chunk or finds no
  surface within range contributes no return. Fewer than three returns yields
  `unavailable`; internal scan errors yield `failed`.
- Server config `radarAltimeterMaxRange` defaults to `128` blocks.
- Server config `sensorScanIntervalTicks` defaults to `5` ticks.

The sensor keeps its last valid reading and reports its availability on the
avionics network independently of the scan value. If the last valid distance
was within the upper 10% of the configured range and returns disappear, the
GPWS treats that as no terrain below while the sensor continues responding.
This uses measured clearance, so it also works above terrain at negative Y.
The old distance is never presented as a current altitude. A newly acquired
return replaces the history; stopped heartbeats and scan failures still make
the sensor inoperative.

## CC:Tweaked API

Each Radar Altimeter is an independent `radar_altimeter` peripheral. The
network bridge is not required to query it.

| Method/event | Contract |
| --- | --- |
| `getReading()` | `{status, value?, unit, gameTime}` |
| `radar_altimeter_reading_changed` | attachment name and the same reading snapshot |

`status` is `valid`, `unavailable`, or `failed`. `value` is present only for
`valid` readings; `unit` is `blocks`. The event is queued when status or value
changes. A new sample timestamp alone does not emit an event.

```lua
local altimeter = assert(peripheral.find("radar_altimeter"), "Altímetro não encontrado")
local reading = altimeter.getReading()
if reading.status == "valid" then
  print(reading.value, reading.unit)
else
  print("Leitura indisponível:", reading.status)
end

while true do
  local _, name, updated = os.pullEvent("radar_altimeter_reading_changed")
  print(name, updated.status, updated.value, updated.gameTime)
end
```

The `modular_table` peripheral continues to expose only its own grid, modules,
simulated channels, and panel events. Network-wide status and endpoint
enumeration belong to the `avionics_network_bridge` peripheral.
