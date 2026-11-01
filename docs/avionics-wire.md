# Avionics Wire

Physical data wires. They are not blocks: a black wire hangs between brass
**Wire Connectors** and passes through industrial iron **Wire Supports**, and carries
the [avionics network](avionics-network.md). It replaced the old Avionics Cable
block, removed after 0.2.0. Plan and decisions: [avionics-wire-plan.md](avionics-wire-plan.md).

## Placing

1. Mount a **Wire Connector** on a face of each device you want to join (sensor,
   Avionics Computer, Network Bridge, Modular Table Base), or on any block to
   make a junction. On the Radar Altimeter and the Terrain Scanners it seats on
   the same socket the cable uses. On the edge of a Modular Table Base it drops
   its flange and centres its 4 px body on the 4 px edge. A short stem reaches
   2 px into the host, so it also meets devices whose sides are set in, like the
   Avionics Computer.
2. Mount **Wire Supports** on any face along the way. A support is a cleat: the
   wire runs through it between a base and a cap, held on walls and ceilings as
   well as floors.
3. Hold **Avionics Wire** and use it on the first connector, then on each
   support in order, then on another connector.
   - A ghost of the wire follows the crosshair with its real sag: white when the
     next click is accepted, red with the reason when it is not. Length and cost
     show under the crosshair.
   - Sneak + use in the air undoes the last point; with only the start left it
     cancels. Using the start connector again also cancels.
4. One Avionics Wire item makes 8 blocks of wire (rounded up per wire). Creative
   mode is free.

| Rule | Default | Config |
| --- | --- | --- |
| Longest span between two points | 24 blocks | `wireMaxSpan` |
| Points per wire, ends included | 32 | `wireMaxPoints` |
| Blocks of wire per item | 8 | `wireBlocksPerItem` |
| Wires per connector | 4 | — |
| Wires per support | 3 (side by side) | — |

Wires may pass through blocks for now. All points of a wire must be in the same
structure: the world, or one Aeronautics ship. A wire between a ship and the
ground is not supported.

## Removing

- Breaking a connector or a support removes every wire through it and drops the
  wire back (not in creative).
- In the Wiring View, sneak + use with a Create wrench on a wire cuts only that
  wire and returns its items to the inventory.
- Engineer's Goggles on a node show how many wires or lanes it uses and their
  length.

## Wiring View

Press **V** (Controls → Aeronautics: Fly by Instruments → Toggle Wiring View).
The world dims and every wire, connector (brass square) and support (iron
square) within 48 blocks is drawn through blocks. Devices with a connector get
a parchment diamond. Wire colours:

| Colour | Network |
| --- | --- |
| Brass | complete, with two or more devices |
| Grey | complete but nothing to talk to, or disconnected |
| Red | reaches an unloaded area or exceeds `maxAvionicsNetworkBlocks` |
| Faded brass | state not received yet |
| White | the wire under the crosshair (length, state and device count shown) |

`V` is also Simple Voice Chat's default key; rebind either one if both are
installed.

## Network

A connector joins the block it is mounted on and the far end of each of its
wires. Supports carry no data. The rest of the network is unchanged: the same
visit limit, no chunk loading, `INCOMPLETE` when a far end is unloaded.
`getNetworkStatus()` on the Network Bridge adds `wireCount`.

## How it works

- Every node on a wire keeps a copy of the route with offsets relative to
  itself, so wires move with Sable ship assembly and Create contraptions
  (offsets turn with `transform`). The first connector draws the wire.
- Removal is settled one tick later: a wire is cut only if another node still
  holds it, so assembling a ship that carries every node of a wire does not
  cut it. Nodes that load later drop routes the other end no longer has.
- The wire is a 1×1 texel tube with a shallow parabolic sag (1.5% of the
  horizontal span, at most 0.3 block, so a wire along a row of blocks stays
  within their height). Only the 1 px held by the connector's gland is straight;
  the wire then curves into the span, never standing more than 2 px off the face.
  Bends at supports are rounded. Lit per vertex.

## Test status

Automated: route data and rotation, rules, cost, geometry (exact ends, lead
direction, straight part, sag bounds and the belly along a row of blocks,
vertical spans, bends, lanes, the table-edge socket), network discovery through
wires (junctions, legacy cable, unloaded far end, limit), and asset checks.

Not yet checked in game (see the plan's checklist): placing and routing,
ship assembly and flight, contraption rotation, the Wiring View with and
without shaders, wrench cutting, and performance with many wires.
