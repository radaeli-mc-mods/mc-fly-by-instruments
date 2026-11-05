// Bootstrap of the v2.1 Attitude Reference Unit (visual only, not wired into the game yet).
// Writes attitude_reference_unit.bbmodel. Refine in Blockbench; do NOT rerun over a refined source.
// Guide: docs/visual-design-guide.md (§8.7 configurable faces); plan: docs/attitude-reference-unit-plan.md.
//
// Anatomy: a 14^3 andesite body split into six one-face parts (so each face can later take its own role
// model from the blockstate) plus twelve 2 px brass edge bars. Roles are one ring around the data socket:
// copper = Nose, brass = Up (no arrow: it read as "forward"). The source shows the default placement,
// Nose = NORTH, Up = UP, the rest neutral.
import { PALETTE, canvas, blit, checkMap, texture, face, cube, group, writeProject } from '../_tools/bbtools.mjs';

// The copper ramp (guide §5) has no keys in the shared PALETTE yet: add it locally.
Object.assign(PALETTE, {
  '!': '#904931', '@': '#9A5038', '#': '#A75A40', '$': '#B26247', '%': '#C26B4C', '&': '#C87456', '*': '#D67B5B', '+': '#E3826C',
});

const rep = (key, n) => key.repeat(n);

// --- Pixel maps --------------------------------------------------------------------------
// Every body face is 14 x 14: 2 px casing frame (dark rim, lit bevel top/left, shaded bevel bottom/right)
// around a 10 x 10 field. The 4 x 4 data socket sits at field (3,3) on every role (guide §8.7).
const SOCKET = ['zzzx', 'zwwx', 'zwwx', 'xxxx'];
const frame = field => checkMap('face', [
  'A' + rep('B', 12) + 'A',
  'BF' + rep('F', 10) + 'CB',
  ...field.map(row => 'BF' + row + 'CB'),
  'BC' + rep('C', 10) + 'CB',
  'A' + rep('B', 12) + 'A',
], 14, 14);
const RIVETS = 'DFDDDDDDFD';

// Neutral: andesite field, two rivets, socket, and the 8 x 3 redstone slit under it (off).
const slit = core => ['DzzzzzzzzD', `Dw${core}wD`, 'D' + rep('x', 8) + 'D'];
const neutralField = core => [
  rep('D', 10), RIVETS, rep('D', 10),
  ...SOCKET.map(row => 'DDD' + row + 'DDD'),
  ...slit(core),
];
const NEUTRAL = frame(neutralField(rep('r', 6)));
const NEUTRAL_LIT = frame(neutralField('STUUTS'));

// Marked roles: a 1 px ring (8 px circle, lit top/left, shaded bottom/right) around the socket, nothing else.
const ringField = (lit, mid, dark) => [
  RIVETS,
  'DDD' + lit + lit + mid + mid + 'DDD',
  'DD' + lit + 'DDDD' + mid + 'DD',
  ...SOCKET.map(row => 'D' + lit + 'D' + row + 'D' + dark + 'D'),
  'DD' + mid + 'DDDD' + dark + 'DD',
  'DDD' + mid + mid + dark + dark + 'DDD',
  RIVETS,
];
const NOSE = frame(ringField('*', '&', '!')); // copper ring
const UP = frame(ringField('8', '6', '3')); // brass ring

// Brass rim lips, 1 px tall x 2 px deep, on the top and bottom edges only. Sides are 1 px tall (darker
// than the lit top), tops are 2 px deep (lit on the outer row).
const SIDE_16 = checkMap('side 16', ['3' + rep('4', 14) + '3'], 16, 1);
const SIDE_12 = checkMap('side 12', ['3' + rep('4', 10) + '3'], 12, 1);
const TOP_16 = checkMap('top 16', ['8' + rep('8', 14) + '7', '6' + rep('7', 14) + '5'], 16, 2);
const TOP_Z = checkMap('top z', ['87', ...Array(10).fill('76'), '65'], 2, 12);
const END = checkMap('end', ['34'], 2, 1);

// --- Texture assembly (1:1) ---------------------------------------------------------------
const AT = {
  neutral: [0, 0, 14, 14], nose: [16, 0, 30, 14], up: [0, 16, 14, 30],
  side16: [16, 16, 32, 17], top16: [16, 17, 32, 19], side12: [16, 19, 28, 20], end: [16, 20, 18, 21],
  topZ: [30, 20, 32, 32],
};
const atlas = neutral => {
  const img = canvas(32, 32, 'A');
  blit(img, neutral, AT.neutral[0], AT.neutral[1]);
  blit(img, NOSE, AT.nose[0], AT.nose[1]);
  blit(img, UP, AT.up[0], AT.up[1]);
  blit(img, SIDE_16, AT.side16[0], AT.side16[1]);
  blit(img, TOP_16, AT.top16[0], AT.top16[1]);
  blit(img, SIDE_12, AT.side12[0], AT.side12[1]);
  blit(img, END, AT.end[0], AT.end[1]);
  blit(img, TOP_Z, AT.topZ[0], AT.topZ[1]);
  return img;
};

// --- Geometry ------------------------------------------------------------------------------
const textures = [
  texture('attitude_reference_unit', 32, { source: atlas(NEUTRAL), particle: true }),
  texture('attitude_reference_unit_powered', 32, { source: atlas(NEUTRAL_LIT) }),
];
const A = name => face(AT[name], 0);
const ROLE = { north: 'nose', up: 'up' }; // default placement shown in the source
const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const bodyParts = SIDES.map((side, i) =>
  cube(`face_${side}`, [1, 1, 1], [15, 15, 15], { [side]: A(ROLE[side] ?? 'neutral') }, i + 1));

// Edges: only the top and bottom rims. Two full-length x bars per rim, and two z bars shortened to 12
// between them, so nothing overlaps. No vertical edges.
const edges = [];
for (const [yName, y] of [['down', 0], ['up', 15]]) {
  for (const [zName, z] of [['n', 0], ['s', 14]]) {
    edges.push(cube(`edge_x_${yName}_${zName}`, [0, y, z], [16, y + 1, z + 2], {
      north: A('side16'), south: A('side16'), up: A('top16'), down: A('top16'), east: A('end'), west: A('end') }, 6));
  }
  for (const [xName, x] of [['w', 0], ['e', 14]]) {
    edges.push(cube(`edge_z_${yName}_${xName}`, [x, y, 2], [x + 2, y + 1, 14], {
      east: A('side12'), west: A('side12'), up: A('topZ'), down: A('topZ') }, 6));
  }
}
const elements = [...bodyParts, ...edges];
const groups = [group('body', [8, 8, 8], bodyParts), group('edges', [8, 8, 8], edges, 6)];
writeProject(new URL('attitude_reference_unit.bbmodel', import.meta.url),
  { name: 'attitude_reference_unit', elements, groups, textures });
console.log(`Wrote attitude_reference_unit.bbmodel: ${elements.length} cubes, ${textures.length} textures.`);
