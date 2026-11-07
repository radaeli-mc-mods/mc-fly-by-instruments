// Bootstrap of the Attitude Indicator module (2x2, v3 paint). Writes attitude_indicator_desk.bbmodel (wedge, for
// tables that face up) and attitude_indicator_wall.bbmodel (flat plate, for wall and ceiling tables). Both share one
// 32 x 32 atlas. Material line of the Avionics Computer (guide §9.3): painted industrial iron (iron ramp n..W, lit
// bevel on top and left, dark rim, brass rivets, a vent grille on the lid) and polished brass for the screen bezel,
// the visor and the plate rim (brass ramp 8/7 lit, 4/3 shaded). The screen is ONE cube with the 1 px frame painted
// around a dark 8 x 8 lens (the renderer paints the display over it); its sides are iron with only the front lip in
// brass, so the bezel reads as set into the iron box instead of a brass slab stuck to it.
// Modelled with the table facing up: rests on Y 0, centred on X/Z 8, 10 x 10 carcass (guide §9.2, 2-cell axis).
//
// Wedge profile (+Z is the pilot side), 5 cubes, nothing coplanar, nothing see-through:
//   - `base`: 10 x 2 x 10 plinth under everything (replaces the v2 foot under the screen);
//   - `housing`: 10 x 6 x 8 box behind the screen;
//   - `housing_slope`, the lid: 8 wide (inset 1, a 1-unit ledge on each side) and 4 thick, turned 22.5 degrees about
//     X (guide §3 exception) around the back top edge (z 3, y 8). Its underside and back stay inside the housing and
//     its front end is buried in the screen, so no gap opens under it;
//   - `screen`: solid 10 x 10 x 2 block, frame + lens painted on the front;
//   - `visor`: brass lip 1 unit proud over the top of the frame (sun visor).
// Refine in Blockbench afterwards; do NOT rerun over a refined source.
import { texture, face, cube, group, writeProject, canvas, blit, checkMap } from '../_tools/bbtools.mjs';

const rep = (key, n) => key.repeat(n);
// --- Atlas (32 x 32, pixels = model units) -----------------------------------------------------
// Dark lens 8 x 8: medium rim, blue glint at the top left, deepest at the bottom right.
const lens = [
  'OOOOOOOO',
  'OYVMMMMO',
  'OVQMMMMO',
  'OMMMMMMO',
  'OMMMMMMO',
  'OMMMMLLO',
  'OMMMLLLO',
  'OOOOOOOO',
];
// Polished brass frame 10 x 10 around the lens: brightest top and left, warm shade bottom and right.
const screenFace = [
  rep('8', 9) + '7',
  ...lens.map(row => '7' + row + '4'),
  '5' + rep('3', 8) + '2',
];
// Painted iron side panels: lit top edge, rivets (6) near the corners, a louvre (dark slot over a lit lip), dark
// seam along the bottom where the plinth's lit edge starts. Only the sides carry these details; the lid and the back
// are plain iron with the bevel.
const panel = width => [
  rep('W', width - 1) + 'v',
  'v6' + rep('q', width - 4) + '6o',
  'vq' + rep('w', width - 4) + 'qo',
  'vq' + rep('v', width - 4) + 'qo',
  'v6' + rep('q', width - 4) + '6o',
  rep('n', width),
];
const MAPS = {
  screenFace,                                                             // wedge front and plate top
  // Iron back (row 0 is the top edge); only its top rows and the side columns show over the lid.
  screenBack: ['o' + rep('W', 8) + 'o', ...Array.from({ length: 9 }, () => 'v' + rep('q', 8) + 'p')],
  // Lid top, back (row 0) to front: plain iron, lit back lip and bevel only; the last row is buried in the screen.
  lidTop: ['n' + rep('W', 6) + 'n', ...Array.from({ length: 7 }, () => 'nvqqqqpn'), 'nppppppn'],
  // Screen sides: iron behind, the brass lip in front. West u runs back -> front, east u front -> back.
  screenWest: ['W8', ...Array.from({ length: 8 }, () => 'v7'), 'o5'],
  screenEast: ['6q', ...Array.from({ length: 8 }, () => '4o'), '3n'],
  housingSide: panel(8),
  // The back stays plain (bevel only): rivets and the louvre are kept for the sides.
  housingBack: ['W' + rep('W', 8) + 'v', ...Array.from({ length: 4 }, () => 'vq' + rep('q', 6) + 'qo'), rep('n', 10)],
  lidSide: [rep('W', 9), rep('v', 9), rep('q', 9), rep('p', 9)],
  screenTop: ['o' + rep('W', 8) + 'v', rep('7', 9) + '6'],              // back row iron, front row brass
  baseSide: ['W' + rep('v', 8) + 'p', rep('n', 10)],
  // Only the 1-unit ledges beside the lid show: lit on both sides (they face up).
  housingTop: Array.from({ length: 8 }, () => 'W' + rep('o', 8) + 'v'),
  baseDown: [rep('n', 10), ...Array.from({ length: 8 }, () => 'n' + rep('o', 8) + 'n'), rep('n', 10)],
  visorFront: [rep('7', 9) + '5'],
  visorTop: [rep('8', 9) + '7'],
  visorUnder: ['3' + rep('2', 8) + '1'],
  visorWest: ['7'],
  visorEast: ['4'],
  plateLit: [rep('7', 9) + '5'],
  plateShaded: ['5' + rep('4', 8) + '3'],
};
export const UV = {
  screenFace: [0, 0, 10, 10], screenBack: [10, 0, 20, 10], lidTop: [20, 0, 28, 9],
  screenWest: [28, 0, 30, 10], screenEast: [30, 0, 32, 10],
  housingSide: [0, 10, 8, 16], housingBack: [8, 10, 18, 16], lidSide: [18, 10, 27, 14], screenTop: [18, 14, 28, 16],
  baseSide: [0, 16, 10, 18], housingTop: [0, 18, 10, 26], baseDown: [10, 16, 20, 26],
  visorFront: [20, 16, 30, 17], visorTop: [20, 17, 30, 18], visorUnder: [20, 18, 30, 19],
  visorWest: [30, 16, 31, 17], visorEast: [31, 16, 32, 17],
  plateLit: [20, 19, 30, 20], plateShaded: [20, 20, 30, 21],
};
function atlas() {
  const img = canvas(32, 32, 'n');
  for (const [name, map] of Object.entries(MAPS)) {
    const [u0, v0, u1, v1] = UV[name];
    blit(img, checkMap(name, map, u1 - u0, v1 - v0), u0, v0);
  }
  return img;
}

// Create's assets are All Rights Reserved: industrial iron is referenced by id, only as the particle texture.
const textures = () => [
  texture('attitude_indicator_module', 32, { source: atlas() }),                      // 0
  texture('industrial_iron_block', 16, { namespace: 'create', particle: true,
    relativePath: '../../reference/create-textures/industrial_iron_block.png' }),      // 1 (particle only)
];
const A = name => face(UV[name], 0);
const ring = name => ({ north: A(name), south: A(name), east: A(name), west: A(name) });

// --- Wedge (tables that face up) ---------------------------------------------------------------
{
  const slope = cube('housing_slope', [4, 4, 3], [12, 8, 12], { up: A('lidTop'), east: A('lidSide'), west: A('lidSide') });
  slope.rotation = [-22.5, 0, 0];
  slope.origin = [8, 8, 3];                       // the back top edge: the lid rises toward the screen
  const elements = [
    cube('base', [3, 0, 3], [13, 2, 13], { ...ring('baseSide'), down: A('baseDown') }),
    cube('housing', [3, 2, 3], [13, 8, 11], { north: A('housingBack'), east: A('housingSide'), west: A('housingSide'),
      up: A('housingTop') }),
    slope,
    cube('screen', [3, 2, 11], [13, 12, 13], { south: A('screenFace'), up: A('screenTop'), west: A('screenWest'),
      east: A('screenEast'), north: A('screenBack') }, 3),
    cube('visor', [3, 11, 13], [13, 12, 14], { south: A('visorFront'), up: A('visorTop'), down: A('visorUnder'),
      west: A('visorWest'), east: A('visorEast') }, 3),
  ];
  const groups = [
    group('housing', [8, 0, 8], elements.slice(0, 3)),
    group('display', [8, 7, 13], elements.slice(3, 5), 3),
  ];
  writeProject(new URL('attitude_indicator_desk.bbmodel', import.meta.url),
    { name: 'attitude_indicator_desk', elements, groups, textures: textures() });
  console.log(`Wrote attitude_indicator_desk.bbmodel: ${elements.length} cubes.`);
}

// --- Plate (wall and ceiling tables) -----------------------------------------------------------
{
  const elements = [
    cube('housing', [3, 0, 3], [13, 2, 13], { ...ring('baseSide'), down: A('baseDown') }),
    cube('screen', [3, 2, 3], [13, 3, 13], { up: A('screenFace'), north: A('plateLit'), west: A('plateLit'),
      south: A('plateShaded'), east: A('plateShaded') }, 3),
  ];
  const groups = [group('housing', [8, 0, 8], elements.slice(0, 1)), group('display', [8, 2, 8], elements.slice(1), 3)];
  writeProject(new URL('attitude_indicator_wall.bbmodel', import.meta.url),
    { name: 'attitude_indicator_wall', elements, groups, textures: textures() });
  console.log(`Wrote attitude_indicator_wall.bbmodel: ${elements.length} cubes.`);
}
