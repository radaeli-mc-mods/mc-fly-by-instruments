// Bootstrap of the Attitude Indicator module (2x2, v2). Writes attitude_indicator_desk.bbmodel (wedge, for tables
// that face up) and attitude_indicator_wall.bbmodel (flat plate, for wall and ceiling tables). Both share one
// 32 x 32 atlas. Same material line and lines as the FMS (guide §9.3/9.4): industrial iron housing
// (create:block/industrial_iron_block by id), a screen that is ONE cube with the 1 px brass frame painted around a
// dark 8 x 8 lens (the renderer paints the display over it), a brass visor that stands 1 unit proud.
// Modelled with the table facing up: rests on Y 0, centred on X/Z 8, 10 x 10 carcass (guide §9.2, 2-cell axis).
//
// Wedge profile (+Z is the pilot side), 5 cubes, nothing coplanar, nothing see-through:
//   - `housing` 10 x 8 x 8 solid box behind the screen and `housing_foot` 10 x 2 x 2 under it;
//   - `housing_slope`, the lid: 8 wide (inset 1, leaving a 1-unit ledge on each side, guide §3 layers) and 4 thick,
//     turned 22.5 degrees about X (guide §3 exception) around the back top edge (z 3, y 8). Its underside and back
//     stay inside the housing and its front end is buried in the screen, so no gap opens under it;
//   - `screen`: solid 10 x 10 x 2 block, frame + lens painted on the front, so the display can never leak behind it;
//   - `visor`: brass lip 1 unit proud over the top of the frame (sun visor);
// Refine in Blockbench afterwards; do NOT rerun over a refined source.
import { texture, face, cube, group, writeProject, px, canvas, blit, checkMap } from '../_tools/bbtools.mjs';

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
// Brass frame 10 x 10 around the lens: lit top left, shaded bottom right, darkest corner at the bottom right.
const screenFace = [
  '8' + rep('7', 8) + '6',
  ...lens.map(row => '7' + row + '4'),
  '6' + rep('4', 8) + '3',
];
const MAPS = {
  screenFace,                                                             // wedge front and plate top
  screenTop: ['6' + rep('5', 8) + '4', '7' + rep('6', 8) + '5'],          // back row, front row
  screenWest: ['56', ...Array.from({ length: 8 }, () => '56'), '45'],     // u: back -> front
  screenEast: ['54', ...Array.from({ length: 8 }, () => '43'), '32'],     // u: front -> back
  screenBack: ['5' + rep('4', 8) + '3', rep('3', 10), ...Array.from({ length: 8 }, () => rep('x', 10))],
  visorFront: ['6' + rep('5', 8) + '4'],
  visorTop: ['8' + rep('7', 8) + '6'],
  visorUnder: ['2' + rep('1', 8) + '1'],
  visorWest: ['6'],
  visorEast: ['3'],
  plateLit: [rep('6', 10)],
  plateShaded: [rep('3', 10)],
};
export const UV = {
  screenFace: [0, 0, 10, 10], screenTop: [0, 10, 10, 12],
  screenWest: [10, 0, 12, 10], screenEast: [12, 0, 14, 10], screenBack: [14, 0, 24, 10],
  visorFront: [0, 12, 10, 13], visorTop: [0, 13, 10, 14], visorUnder: [0, 14, 10, 15],
  visorWest: [10, 12, 11, 13], visorEast: [11, 12, 12, 13],
  plateLit: [0, 15, 10, 16], plateShaded: [10, 15, 20, 16],
};
function atlas() {
  const img = canvas(32, 32, 'x');
  for (const [name, map] of Object.entries(MAPS)) {
    const [u0, v0, u1, v1] = UV[name];
    blit(img, checkMap(name, map, u1 - u0, v1 - v0), u0, v0);
  }
  return img;
}

// Create's assets are All Rights Reserved: reference by id; keep a local copy in reference/create-textures/ (gitignored).
const textures = () => [
  texture('attitude_indicator_module', 32, { source: atlas() }),                      // 0
  texture('industrial_iron_block', 16, { namespace: 'create', particle: true,
    relativePath: '../../reference/create-textures/industrial_iron_block.png' }),      // 1
];
const A = name => face(UV[name], 0);
const iron = region => face(px(16, region), 1);
// Crops of the Create texture, 1 texel per unit. Centred crops for broad faces; thin bands take the bottom rows
// so the texture's dark rim sits on the lower edge, like the FMS housing.
const crop = (a, b) => {
  const u = Math.floor((16 - a) / 2), v = Math.floor((16 - b) / 2);
  return [u, v, u + a, v + b];
};
const band = (a, b) => [Math.floor((16 - a) / 2), 16 - b, Math.floor((16 - a) / 2) + a, 16];
const ironBox = (name, from, to, hidden = []) => {
  const [w, h, d] = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
  const side = h <= 2 ? band : crop;
  const faces = { north: iron(side(w, h)), south: iron(side(w, h)), east: iron(side(d, h)), west: iron(side(d, h)),
    up: iron(crop(w, d)), down: iron(crop(w, d)) };
  for (const key of hidden) delete faces[key];
  return cube(name, from, to, faces);
};

// --- Wedge (tables that face up) ---------------------------------------------------------------
{
  const slope = ironBox('housing_slope', [4, 4, 3], [12, 8, 12], ['north', 'south', 'down']);
  slope.rotation = [-22.5, 0, 0];
  slope.origin = [8, 8, 3];                       // the back top edge: the lid rises toward the screen
  const elements = [
    ironBox('housing', [3, 0, 3], [13, 8, 11], ['south', 'down']),
    ironBox('housing_foot', [3, 0, 11], [13, 2, 13], ['north', 'up', 'down']),
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
    ironBox('housing', [3, 0, 3], [13, 2, 13], ['up']),
    cube('screen', [3, 2, 3], [13, 3, 13], { up: A('screenFace'), north: A('plateLit'), west: A('plateLit'),
      south: A('plateShaded'), east: A('plateShaded') }, 3),
  ];
  const groups = [group('housing', [8, 0, 8], elements.slice(0, 1)), group('display', [8, 2, 8], elements.slice(1), 3)];
  writeProject(new URL('attitude_indicator_wall.bbmodel', import.meta.url),
    { name: 'attitude_indicator_wall', elements, groups, textures: textures() });
  console.log(`Wrote attitude_indicator_wall.bbmodel: ${elements.length} cubes.`);
}
