// Bootstrap of the v4 Attitude Reference Unit: a glass cube with four slim industrial-iron corner posts and a
// brass model airplane inside. The airplane's nose points at the Nose face and its tail fin at the Up face.
// Writes attitude_reference_unit.bbmodel. Refine in Blockbench; do NOT rerun over a refined source.
// Guide: docs/visual-design-guide.md (§8.7 "miniatura orientada"); plan: docs/attitude-reference-unit-plan.md.
//
// Layout (block units, 16 = one block; the unit's centre is 8,8,8). Source: Nose = NORTH, Up = UP.
//   frame      4 posts 2x16x2 at the corners (create:block/industrial_iron_block) + 6 data sockets 6x6x2.
//              The 6x6 socket faces live in their own texture (socket_ok), swapped by the `status` blockstate.
//   glass      one translucent cube [1,1,1]-[15,15,15]
//   airplane   fuselage, copper nose cone, low wing, stabilizer, tail fin with a light tip, stand (rod + base)
//   propeller  one blade in front of the nose cone; spun about the nose axis by the block entity renderer
// The sockets reach 2 units into the block, so the airplane lives in 2..14 on every axis: the propeller is
// at z 2-3 and the stand's base sits on the inner face of the socket opposite to Up.
import { PALETTE, blit, canvas, checkMap, texture, face, cube, group, writeProject, px } from '../_tools/bbtools.mjs';

// The copper ramp (guide §5) has no keys in the shared PALETTE yet: add it locally.
Object.assign(PALETTE, {
  '!': '#904931', '@': '#9A5038', '#': '#A75A40', '$': '#B26247', '%': '#C26B4C', '&': '#C87456', '*': '#D67B5B', '+': '#E3826C',
});

const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const RAMP = {
  metal: { d: 'w', m: 'y', l: 'z' },
  brass: { d: '3', m: '5', l: '7' },
  copper: { d: '!', m: '$', l: '*' },
};

// --- Deterministic painters (one character per pixel; no noise) -----------------------------------
// Border in the darkest tone, lit bevel on the top/left, body in the middle tone (guide §4 "Luz").
// Thin faces (a 1-2 px side) are lit on the first column/row and plain after it.
function shaded(w, h, ramp) {
  const rows = [];
  for (let y = 0; y < h; y++) {
    let row = '';
    for (let x = 0; x < w; x++) {
      if (w <= 2 || h <= 2) {
        const i = w <= 2 ? x : y, n = w <= 2 ? w : h;
        row += n > 1 && i === 0 ? ramp.l : ramp.m;
      } else if (x === 0 || y === 0 || x === w - 1 || y === h - 1) row += ramp.d;
      else if (x === 1 || y === 1) row += ramp.l;
      else row += ramp.m;
    }
    rows.push(row);
  }
  return rows;
}
const fill = (w, h, key) => Array.from({ length: h }, () => key.repeat(w));
// Flat brass surfaces (wing, stabilizer). On top the leading edge (north, first row) is light and the
// trailing edge dark; the underside is in shade.
const surface = (w, chord) => ({
  up: [`7`.repeat(w), ...fill(w, chord - 2, '5'), `3${'4'.repeat(w - 2)}3`],
  down: [...fill(w, chord - 1, '4'), '3'.repeat(w)],
  north: ['7'.repeat(w)], south: ['3'.repeat(w)],
  east: ['5'.repeat(chord - 1) + '4'], west: ['4' + '5'.repeat(chord - 1)],
});
// Tail fin: brass with a light tip on the top row, the Up marker (guide §6).
const FIN_END = ['88', '75', '75', '54'], FIN_SIDE = ['888', '755', '755', '544'];
const FIN = { north: FIN_END, south: FIN_END, east: FIN_SIDE, west: FIN_SIDE, up: ['88', '88', '88'] };
// Propeller blade: dark metal with brass tips, so the spin reads.
const BLADE = ['66zyyz66', '65yxxy65'];
const PROPELLER = { north: BLADE, south: BLADE, up: ['66zyyz66'], down: ['65yxxy65'], east: ['6', '5'], west: ['6', '5'] };
// Guide §8.3 data socket ring (4x4) inside a 1 px metal bezel: the 6x6 face of the socket block. The 2x2 core
// is the status light: steady light brass (ok), flashing amber (degraded), flashing redstone (inop).
const socketFace = core => checkMap('socket', ['yyyyyy', 'yzzzxy', `yz${core[0]}xy`, `yz${core[1]}xy`, 'yxxxxy', 'yyyyyy'], 6, 6);
const STATUS = {
  ok: { frames: [['76', '65']] },
  degraded: { frames: [['88', '87'], ['ww', 'ww']], frameTime: 6 },
  inop: { frames: [['UT', 'TS'], ['rs', 'st']], frameTime: 4 },
};
const statusImage = frames => {
  const img = canvas(16, 16 * frames.length, 'w');
  frames.forEach((core, i) => blit(img, socketFace(core), 0, 16 * i));
  return img;
};

// --- Cube specs -----------------------------------------------------------------------------------
// skip: faces that sit flush against a neighbour and are never seen. special: hand-written maps per face.
const spec = (group, name, from, to, material, skip = [], special = null) => ({ group, name, from, to, material, skip, special });
const specs = [];
const add = (...args) => specs.push(spec(...args));

// Sockets are 6x6x2 so the wire connector (flange 6x6 outside, stem 4x4 reaching 2 units in) seats on the outer
// face and its stem stays buried in the socket instead of crossing the glass (cableSocket is on the face plane).
const SOCKET_BOXES = {
  north: [[5, 5, 0], [11, 11, 2], 'south'], south: [[5, 5, 14], [11, 11, 16], 'north'],
  west: [[0, 5, 5], [2, 11, 11], 'east'], east: [[14, 5, 5], [16, 11, 11], 'west'],
  down: [[5, 0, 5], [11, 2, 11], 'up'], up: [[5, 14, 5], [11, 16, 11], 'down'],
};
// The outer and the inner 6x6 faces come from the status texture (seen from inside the glass the socket must
// not be a hole); only the four 6x2 sides are in the parts atlas.
const SOCKET_FACES = new Map();
for (const [side, [from, to, inner]] of Object.entries(SOCKET_BOXES)) {
  add('sockets', `socket_${side}`, from, to, 'metal', [side, inner]);
  SOCKET_FACES.set(`socket_${side}`, [side, inner]);
}

// Airplane (static, turned by the blockstate) and its propeller (block entity renderer).
add('airplane', 'fuselage', [7, 7, 4], [9, 9, 14], 'brass', ['north', 'south']);
add('airplane', 'nose_cone', [7, 7, 3], [9, 9, 4], 'copper', ['south']);
add('airplane', 'wing', [2, 6, 5], [14, 7, 9], 'brass', [], surface(12, 4));
add('airplane', 'stabilizer', [4, 6, 12], [12, 7, 14], 'brass', [], surface(8, 2));
add('airplane', 'fin', [7, 9, 11], [9, 13, 14], 'brass', ['down'], FIN);
add('airplane', 'stand_rod', [7, 3, 7], [9, 6, 9], 'metal', ['up', 'down']);
add('airplane', 'stand_base', [5, 2, 5], [11, 3, 11], 'metal', ['down']);
add('propeller', 'propeller', [4, 7, 2], [12, 9, 3], 'metal', [], PROPELLER);

const size = s => [0, 1, 2].map(i => s.to[i] - s.from[i]);
const dims = (s, side) => AXES[side].map(a => size(s)[a]);

// --- Atlas packing (32x32, 1 texel = 1 unit): one region per distinct (material, w, h) or special map ----
const regions = new Map();
const regionKey = (s, side) => {
  const [w, h] = dims(s, side);
  const special = s.special?.[side];
  return special ? `special:${w}:${special.join('')}` : `${s.material}:${w}x${h}`;
};
for (const s of specs) for (const side of SIDES) {
  if (s.skip.includes(side)) continue;
  const [w, h] = dims(s, side);
  const special = s.special?.[side] ?? null;
  if (special) checkMap(`${s.name}.${side}`, special, w, h);
  const key = regionKey(s, side);
  if (!regions.has(key)) regions.set(key, { w, h, material: s.material, special });
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
const PARTS = 0, GLASS = 1, IRON = 2, SOCKET = 3;
const textures = [
  texture('attitude_reference_unit_parts', 32, { source: atlas, particle: true }),
  texture('attitude_reference_unit_glass', 16, { source: glassImage }),
  texture('industrial_iron_block', 16, { namespace: 'create',
    relativePath: '../../reference/create-textures/industrial_iron_block.png' }),
  // The cubes use socket_ok; the exporter swaps it for the other two in the frame_degraded / frame_inop models.
  ...Object.entries(STATUS).map(([name, { frames, frameTime = 1 }]) =>
    texture(`attitude_reference_unit_socket_${name}`, 16, { source: statusImage(frames), frameTime })),
];

const colors = { frame: 1, sockets: 2, glass: 3, airplane: 4, propeller: 6 };
const make = s => {
  const faces = {};
  for (const side of SIDES) {
    if (s.skip.includes(side)) continue;
    const r = regions.get(regionKey(s, side));
    faces[side] = face([r.x, r.y, r.x + r.w, r.y + r.h], PARTS);
  }
  for (const side of SOCKET_FACES.get(s.name) ?? []) faces[side] = face(px(16, [0, 0, 6, 6]), SOCKET);
  return cube(s.name, s.from, s.to, faces, colors[s.group]);
};
const cubes = specs.map(make);

// Posts: 2x16 sides and 2x2 caps from the Create texture (16 px, so UVs are doubled in the 32 space).
const POSTS = [['nw', 0, 0], ['ne', 14, 0], ['sw', 0, 14], ['se', 14, 14]];
const posts = POSTS.map(([name, x, z]) => cube(`post_${name}`, [x, 0, z], [x + 2, 16, z + 2], {
  north: face(px(16, [0, 0, 2, 16]), IRON), south: face(px(16, [0, 0, 2, 16]), IRON),
  east: face(px(16, [0, 0, 2, 16]), IRON), west: face(px(16, [0, 0, 2, 16]), IRON),
  up: face(px(16, [0, 0, 2, 2]), IRON), down: face(px(16, [0, 0, 2, 2]), IRON),
}, colors.frame));
const glass = cube('glass', [1, 1, 1], [15, 15, 15],
  Object.fromEntries(SIDES.map(side => [side, face(px(16, [0, 0, 14, 14]), GLASS)])), colors.glass);

const groupsOf = name => cubes.filter(c => specs.find(s => s.name === c.name).group === name);
const elements = [...posts, ...groupsOf('sockets'), glass, ...groupsOf('airplane'), ...groupsOf('propeller')];
const groups = [
  group('frame', [8, 8, 8], [...posts, ...groupsOf('sockets')], colors.frame),
  group('glass', [8, 8, 8], [glass], colors.glass),
  group('airplane', [8, 8, 8], groupsOf('airplane'), colors.airplane),
  group('propeller', [8, 8, 8], groupsOf('propeller'), colors.propeller),
];
writeProject(new URL('attitude_reference_unit.bbmodel', import.meta.url),
  { name: 'attitude_reference_unit', elements, groups, textures });
console.log(`Wrote attitude_reference_unit.bbmodel: ${elements.length} cubes, ${textures.length} textures, ${regions.size} atlas regions.`);
