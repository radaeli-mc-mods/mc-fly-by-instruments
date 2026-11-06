// Bootstrap of the v3 Attitude Reference Unit: a glass cube with four slim industrial-iron corner posts,
// a gimbal inside (animated by a block entity renderer) and two reference arms (Nose, Up).
// Writes attitude_reference_unit.bbmodel. Refine in Blockbench; do NOT rerun over a refined source.
// Guide: docs/visual-design-guide.md (§8.7); plan: docs/attitude-reference-unit-plan.md.
//
// Layout (block units, 16 = one block; the unit's centre is 8,8,8):
//   frame   4 posts 2x16x2 at the corners (create:block/industrial_iron_block) + 6 data sockets 4x4x1 outside the glass
//   glass   one translucent cube [1,1,1]-[15,15,15]
//   arm_nose  source facing NORTH (z): copper bulb 4x4x2 at the front, plain 2x2 rod at the back
//   arm_up    source facing UP (y):    brass bulb 4x4x2 at the top,   plain 2x2 rod at the bottom
//   gimbal_ring   square ring 8x8, 4 deep, in the XY plane (rocks about Y in the renderer)
//   gimbal_rotor  4x4x2 wheel on a 2x2 axle along Z (spins about Z in the renderer)
// Arms end on the ring or the axle (y/x at 4 and 12, z at 4 and 12) so nothing overlaps in any rotation.
import { PALETTE, blit, canvas, checkMap, texture, face, cube, group, writeProject, px } from '../_tools/bbtools.mjs';

// The copper ramp (guide §5) has no keys in the shared PALETTE yet: add it locally.
Object.assign(PALETTE, {
  '!': '#904931', '@': '#9A5038', '#': '#A75A40', '$': '#B26247', '%': '#C26B4C', '&': '#C87456', '*': '#D67B5B', '+': '#E3826C',
});

const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const RAMP = {
  metal: { d: 'w', m: 'y', l: 'z' },
  brass: { d: '2', m: '4', l: '6' },
  copper: { d: '!', m: '$', l: '&' },
};

// --- Deterministic painters (one character per pixel; no noise) -----------------------------------
// Border in the darkest tone, lit bevel on the top/left, body in the middle tone (guide §4 "Luz").
function shaded(w, h, ramp) {
  const rows = [];
  for (let y = 0; y < h; y++) {
    let row = '';
    for (let x = 0; x < w; x++) {
      // Thin faces (a 1-2 px side): lit on the first column/row, shaded on the last, nothing between.
      if (w <= 2 || h <= 2) {
        const i = w <= 2 ? x : y, n = w <= 2 ? w : h;
        row += n === 1 ? ramp.m : i === 0 ? ramp.l : i === n - 1 ? ramp.d : ramp.m;
      } else if (x === 0 || y === 0 || x === w - 1 || y === h - 1) row += ramp.d;
      else if (x === 1 || y === 1) row += ramp.l;
      else row += ramp.m;
    }
    rows.push(row);
  }
  return rows;
}
// Guide §8.3 data socket ring (4x4) inside a 1 px metal bezel: the 6x6 face of the socket block.
const SOCKET = ['yyyyyy', ...['zzzx', 'zwwx', 'zwwx', 'xxxx'].map(r => `y${r}y`), 'yyyyyy'];
// Flywheel face: dark metal with a brass marker, so the spin reads.
const WHEEL = ['xzzx', 'z6wz', 'zw4z', 'xzzx'];

// --- Cube specs -----------------------------------------------------------------------------------
// skip: faces that sit flush against a neighbour and are never seen.
const spec = (group, name, from, to, material, skip = [], special = null) => ({ group, name, from, to, material, skip, special });
const specs = [];
const add = (...args) => specs.push(spec(...args));

// Corner posts (Create texture, referenced by id) and sockets.
const POSTS = [['nw', 0, 0], ['ne', 14, 0], ['sw', 0, 14], ['se', 14, 14]];
// Sockets are 6x6x2 so the wire connector (flange 6x6 outside, stem 4x4 reaching 2 units in) seats on the outer
// face and its stem stays buried in the socket instead of crossing the glass (cableSocket is on the face plane).
const SOCKET_BOXES = {
  north: [[5, 5, 0], [11, 11, 2], 'south'], south: [[5, 5, 14], [11, 11, 16], 'north'],
  west: [[0, 5, 5], [2, 11, 11], 'east'], east: [[14, 5, 5], [16, 11, 11], 'west'],
  down: [[5, 0, 5], [11, 2, 11], 'up'], up: [[5, 14, 5], [11, 16, 11], 'down'],
};
for (const [side, [from, to, inner]] of Object.entries(SOCKET_BOXES)) {
  // The inner face is drawn too: seen from inside the glass the socket must not be a hole (backface culling).
  add('sockets', `socket_${side}`, from, to, 'metal', [], { [side]: SOCKET, [inner]: SOCKET });
}

// Reference arms.
add('arm_nose', 'nose_bulb', [6, 6, 1], [10, 10, 3], 'copper');
add('arm_nose', 'nose_front', [7, 7, 3], [9, 9, 4], 'metal', ['north', 'south']);
add('arm_nose', 'nose_rear', [7, 7, 12], [9, 9, 15], 'metal', ['north']);
add('arm_up', 'up_bulb', [6, 13, 6], [10, 15, 10], 'brass');
add('arm_up', 'up_front', [7, 12, 7], [9, 13, 9], 'metal', ['up']);
add('arm_up', 'up_rear', [7, 1, 7], [9, 4, 9], 'metal');

// Gimbal.
add('gimbal_ring', 'ring_top', [4, 11, 6], [12, 12, 10], 'brass');
add('gimbal_ring', 'ring_bottom', [4, 4, 6], [12, 5, 10], 'brass');
add('gimbal_ring', 'ring_left', [4, 5, 6], [5, 11, 10], 'brass');
add('gimbal_ring', 'ring_right', [11, 5, 6], [12, 11, 10], 'brass');
add('gimbal_rotor', 'rotor_axle', [7, 7, 4], [9, 9, 12], 'metal');
add('gimbal_rotor', 'rotor_wheel', [6, 6, 7], [10, 10, 9], 'metal', [], { north: WHEEL, south: WHEEL });

const size = s => [0, 1, 2].map(i => s.to[i] - s.from[i]);
const dims = (s, side) => AXES[side].map(a => size(s)[a]);

// --- Atlas packing (32x32, 1 texel = 1 unit): one region per distinct (material, w, h) ----------
const regions = new Map();
const regionKey = (s, side) => {
  const [w, h] = dims(s, side);
  const special = s.special?.[side];
  return special ? `special:${special.join('')}` : `${s.material}:${w}x${h}`;
};
for (const s of specs) for (const side of SIDES) {
  if (s.skip.includes(side)) continue;
  const [w, h] = dims(s, side);
  const key = regionKey(s, side);
  if (!regions.has(key)) regions.set(key, { w, h, material: s.material, special: s.special?.[side] ?? null });
}
const ordered = [...regions.entries()].sort((a, b) => b[1].h - a[1].h || b[1].w - a[1].w);
let cx = 0, cy = 0, rowH = 0;
for (const [, r] of ordered) {
  if (cx + r.w > 32) { cx = 0; cy += rowH; rowH = 0; }
  r.x = cx; r.y = cy; cx += r.w; rowH = Math.max(rowH, r.h);
}
if (cy + rowH > 32) throw new Error(`Atlas overflow: needs ${cy + rowH} rows`);
const atlas = canvas(32, 32, 'w');
for (const [, r] of regions) {
  blit(atlas, r.special ?? shaded(r.w, r.h, RAMP[r.material]), r.x, r.y);
}

// --- Glass (16x16, guide §9.4: low uniform alpha, lighter edges, deeper core) ---------------------
const glassPixel = (x, y) => {
  if (x > 13 || y > 13) return '#9AA49D30';
  if (x === 0 || y === 0 || x === 13 || y === 13) return '#A8B3AB78';
  if (x + y === 5 || x + y === 6 || x + y === 9) return '#A8B3AB50'; // reflection streak, top left
  return '#9AA49D30';
};
const glassImage = Array.from({ length: 16 }, (_, y) => Array.from({ length: 16 }, (_, x) => glassPixel(x, y)));

// --- Textures and cubes ---------------------------------------------------------------------------
const textures = [
  texture('attitude_reference_unit_parts', 32, { source: atlas, particle: true }), // 0
  texture('attitude_reference_unit_glass', 16, { source: glassImage }),            // 1
  texture('industrial_iron_block', 16, { namespace: 'create',
    relativePath: '../../reference/create-textures/industrial_iron_block.png' }),   // 2
];

const byName = new Map();
const colors = { frame: 1, sockets: 2, glass: 3, arm_nose: 4, arm_up: 5, gimbal_ring: 6, gimbal_rotor: 7 };
const make = s => {
  const faces = {};
  for (const side of SIDES) {
    if (s.skip.includes(side)) continue;
    const r = regions.get(regionKey(s, side));
    faces[side] = face([r.x, r.y, r.x + r.w, r.y + r.h], 0);
  }
  const c = cube(s.name, s.from, s.to, faces, colors[s.group]);
  byName.set(s.name, c);
  return c;
};
const cubes = specs.map(make);

// Posts: 2x16 sides and 2x2 caps from the Create texture (16 px, so UVs are doubled in the 32 space).
const posts = POSTS.map(([name, x, z], i) => {
  const c = cube(`post_${name}`, [x, 0, z], [x + 2, 16, z + 2], {
    north: face(px(16, [0, 0, 2, 16]), 2), south: face(px(16, [0, 0, 2, 16]), 2),
    east: face(px(16, [0, 0, 2, 16]), 2), west: face(px(16, [0, 0, 2, 16]), 2),
    up: face(px(16, [0, 0, 2, 2]), 2), down: face(px(16, [0, 0, 2, 2]), 2),
  }, colors.frame);
  return c;
});
const glass = cube('glass', [1, 1, 1], [15, 15, 15],
  Object.fromEntries(SIDES.map(side => [side, face(px(16, [0, 0, 14, 14]), 1)])), colors.glass);

const groupsOf = name => cubes.filter(c => specs.find(s => s.name === c.name).group === name);
const elements = [...posts, ...groupsOf('sockets'), glass, ...groupsOf('arm_nose'), ...groupsOf('arm_up'),
  ...groupsOf('gimbal_ring'), ...groupsOf('gimbal_rotor')];
const groups = [
  group('frame', [8, 8, 8], [...posts, ...groupsOf('sockets')], colors.frame),
  group('glass', [8, 8, 8], [glass], colors.glass),
  group('arm_nose', [8, 8, 8], groupsOf('arm_nose'), colors.arm_nose),
  group('arm_up', [8, 8, 8], groupsOf('arm_up'), colors.arm_up),
  group('gimbal_ring', [8, 8, 8], groupsOf('gimbal_ring'), colors.gimbal_ring),
  group('gimbal_rotor', [8, 8, 8], groupsOf('gimbal_rotor'), colors.gimbal_rotor),
];
writeProject(new URL('attitude_reference_unit.bbmodel', import.meta.url),
  { name: 'attitude_reference_unit', elements, groups, textures });
console.log(`Wrote attitude_reference_unit.bbmodel: ${elements.length} cubes, ${textures.length} textures, ${regions.size} atlas regions.`);
