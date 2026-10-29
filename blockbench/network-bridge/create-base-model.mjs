// Bootstrap of the v2.1 Network Bridge: a plain andesite-alloy metal box with a dark metal plate on
// top, cut by brass grooves. Writes network_bridge.bbmodel. Refine in Blockbench; do NOT rerun over a
// refined source. Guide: docs/visual-design-guide.md; identity: README.md.
// It must not read as the Avionics Computer (graphite iron cabinet in bands, one screen in front):
// light andesite alloy instead of iron, one plain volume, a brass grille on top and a small data socket on two opposite sides.
import { canvas, blit, checkMap, texture, face, cube, group, writeProject } from '../_tools/bbtools.mjs';

// --- Pixel maps --------------------------------------------------------------------------
// Box side (16 x 15, y 15 -> 0): dark rim, lit bevel top/left, shaded bevel bottom/right, four rivets.
const RIVETS = 'BFDFDDDDDDDDFDCB';
const PANEL = 'BFDDDDDDDDDDDDCB';
const SIDE = checkMap('side', [
  'ABBBBBBBBBBBBBBA',
  'BFFFFFFFFFFFFFCB',
  RIVETS,
  ...Array(9).fill(PANEL),
  RIVETS,
  'BCCCCCCCCCCCCCCB',
  'ABBBBBBBBBBBBBBA',
], 16, 15);
// Detailed sides (north and south of the source): the plain side plus a small data socket in the
// middle (guide §8.3: dark metal ring, darker well), where a Wire Connector goes.
const socket = row => `BFDDDD${row}DDDDCB`;
const DETAIL = checkMap('detail', [
  ...SIDE.slice(0, 5),
  socket('zzzx'), socket('zwwx'), socket('zwwx'), socket('xxxx'),
  ...SIDE.slice(9),
], 16, 15);
// Box top and bottom (16 x 16): the same frame; on top the plate covers the middle 12 x 12.
const CAP = checkMap('cap', [
  'ABBBBBBBBBBBBBBA',
  'BFFFFFFFFFFFFFCB',
  ...Array(12).fill(PANEL),
  'BCCCCCCCCCCCCCCB',
  'ABBBBBBBBBBBBBBA',
], 16, 16);
// Top plate (12 x 12, 56% of the face): dark metal with a fine grille of five 1 px brass slots.
const PLAIN = 'zyyyyyyyyyyx';
const SLOT = 'zy36666663yx';
const PLATE = checkMap('plate', [
  'zzzzzzzzzzzy',
  PLAIN, SLOT, PLAIN, SLOT, PLAIN, SLOT, PLAIN, SLOT, PLAIN, SLOT,
  'yxxxxxxxxxxw',
], 12, 12);
const PLATE_LIT = checkMap('plate lit edge', ['zzzzzzzzzzzz'], 12, 1);
const PLATE_DARK = checkMap('plate dark edge', ['xxxxxxxxxxxx'], 12, 1);

// --- Texture assembly (1:1) ---------------------------------------------------------------
const AT = {
  side: [0, 0, 16, 15], top: [16, 0, 32, 16], detail: [0, 16, 16, 31],
  plate: [16, 16, 28, 28], plateLit: [16, 28, 28, 29], plateDark: [16, 29, 28, 30],
};
const MAPS = { side: SIDE, top: CAP, detail: DETAIL, plate: PLATE, plateLit: PLATE_LIT, plateDark: PLATE_DARK };
function atlas() {
  const img = canvas(32, 32, 'A');
  for (const [name, map] of Object.entries(MAPS)) blit(img, map, AT[name][0], AT[name][1]);
  return img;
}

// --- Geometry (detailed sides on the Z axis; the blockstate turns them to X) -----------------------------------------------------------------
// The box stops 1 short of the top so the plate ends flush with the block: a Wire Connector seats on
// the plate, and its 2 px stem reaches the box from any face.
const textures = [texture('network_bridge', 32, { source: atlas(), particle: true })];
const A = name => face(AT[name], 0);
const elements = [
  cube('box', [0, 0, 0], [16, 15, 16],
    { north: A('detail'), south: A('detail'), east: A('side'), west: A('side'), up: A('top'), down: A('top') }, 1),
  cube('plate', [2, 15, 2], [14, 16, 14],
    { up: A('plate'), north: A('plateLit'), west: A('plateLit'), south: A('plateDark'), east: A('plateDark') }, 6),
];
const groups = [group('housing', [8, 8, 8], elements)];
writeProject(new URL('network_bridge.bbmodel', import.meta.url), { name: 'network_bridge', elements, groups, textures });
console.log(`Wrote network_bridge.bbmodel: ${elements.length} cubes, ${textures.length} textures.`);
