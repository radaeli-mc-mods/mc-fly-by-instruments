// Bootstrap of the Attitude Indicator ITEM model (item attitude_indicator_module in the inventory, hand and ground).
// The module's table models (the wedge and the plate, ../attitude-indicator-module) stay what the Modular Table
// renders; this is the thing the player carries: a small horizon gauge, in the language of the FMS
// Compass (../fms-navigator): industrial iron case with chamfered corners, brass bezel, dark lens. Where the compass
// has a needle on a dark lens, the gauge shows a horizon (sky over ground, light horizon line, brass aircraft
// symbol), so the two read apart at 16 px even though both are flat, square-ish and brass-on-iron.
// Lies flat like the compass: rests on Y 0, centred on X/Z 8, face up (north = low Z = top of the inventory icon).
// Four integer cuboids, no rotation, nothing coplanar:
//   - `case`, `case_north`, `case_south`: 12 x 12 x 2 iron plate with 1 px chamfered corners (three slabs, as the
//     compass builds its disc);
//   - `bezel`: 10 x 10 x 1 brass step on top, with the lens painted 8 x 8 (same lens size as the table module).
// Writes attitude_indicator_item.bbmodel. Refine in Blockbench afterwards; do NOT rerun over a refined source.
import { texture, face, cube, group, writeProject, canvas, blit, checkMap } from '../_tools/bbtools.mjs';

const rep = (key, n) => key.repeat(n);
// --- Atlas (32 x 32, pixels = model units) -----------------------------------------------------
// Case top 12 x 12 (x 2..14, z 2..14; '.' lies outside the chamfered plate). Only the 1 px outline outside the
// bezel stays visible: lit top and left, shaded bottom and right.
const caseTop = [
  '.' + rep('v', 10) + '.',
  ...Array.from({ length: 10 }, () => 'v' + rep('p', 10) + 'o'),
  '.' + rep('o', 10) + '.',
].map(row => row.replaceAll('.', 'n'));
// Lens 8 x 8: sky (dark lens ramp, a blue glint at the top left) over a wooden ground, a parchment horizon line and
// a brass aircraft symbol straddling it. Palette only (guide §5); no gem-blue sky.
const lens = [
  'YQQQQQQQ',
  'VVVVVVVV',
  'VVVVVVVV',
  'VVVVVVVV',
  'JJJ77JJJ',
  'j667766j',
  'eeeeeeee',
  'dddddddd',
];
// Brass bezel 10 x 10 around the lens: lit top left, shaded bottom right (same frame as the table module).
const bezelTop = [
  '8' + rep('7', 8) + '6',
  ...lens.map(row => '7' + row + '4'),
  '6' + rep('4', 8) + '3',
];
const MAPS = {
  caseTop,
  bezelTop,
  bezelLit: ['7' + rep('6', 8) + '5'],            // north and west sides
  bezelShade: ['4' + rep('3', 8) + '2'],          // south and east sides
};
export const UV = {
  caseTop: [0, 0, 12, 12], bezelTop: [12, 0, 22, 10],
  bezelLit: [12, 10, 22, 11], bezelShade: [12, 11, 22, 12],
};
function atlas() {
  const img = canvas(32, 32, 'n');
  for (const [name, map] of Object.entries(MAPS)) {
    const [u0, v0, u1, v1] = UV[name];
    blit(img, checkMap(name, map, u1 - u0, v1 - v0), u0, v0);
  }
  return img;
}

// Create's assets are All Rights Reserved: reference by id; keep a local copy in reference/create-textures/ (gitignored).
const textures = [
  texture('attitude_indicator_item', 32, { source: atlas() }),                         // 0
  texture('industrial_iron_block', 16, { namespace: 'create', particle: true,
    relativePath: '../../reference/create-textures/industrial_iron_block.png' }),       // 1
];
const own = name => face(UV[name], 0);
const iron = ([u0, v0, u1, v1]) => face([u0 * 2, v0 * 2, u1 * 2, v1 * 2], 1);          // 16 px texture in the 32 UV space
const OFF = face([0, 0, 0, 0], null);

// One slab of the chamfered plate: top = its crop of the painted face, rim sides and bottom = crops of the Create
// texture (its dark edge rows 14..16 on the sides, as on the compass). `inner` lists the faces that sit against a
// neighbouring slab: left disabled.
function slab(name, [x0, z0], [x1, z1], inner = []) {
  const faces = {
    up: face([x0 - 2, z0 - 2, x1 - 2, z1 - 2], 0), down: iron([x0, z0, x1, z1]),
    north: iron([x0, 14, x1, 16]), south: iron([x0, 14, x1, 16]),
    east: iron([z0, 14, z1, 16]), west: iron([z0, 14, z1, 16]),
  };
  for (const side of inner) faces[side] = OFF;
  return cube(name, [x0, 0, z0], [x1, 2, z1], faces, 0);
}
const elements = [
  slab('case', [2, 3], [14, 13]),
  slab('case_north', [3, 2], [13, 3], ['south']),
  slab('case_south', [3, 13], [13, 14], ['north']),
  cube('bezel', [3, 2, 3], [13, 3, 13], { up: own('bezelTop'), north: own('bezelLit'), west: own('bezelLit'),
    south: own('bezelShade'), east: own('bezelShade') }, 3),
];
const groups = [
  group('case', [8, 0, 8], elements.slice(0, 3)),
  group('face', [8, 2, 8], elements.slice(3), 3),
];
writeProject(new URL('attitude_indicator_item.bbmodel', import.meta.url),
  { name: 'attitude_indicator_item', elements, groups, textures });
console.log(`Wrote attitude_indicator_item.bbmodel: ${elements.length} cubes, ${textures.length} textures.`);
