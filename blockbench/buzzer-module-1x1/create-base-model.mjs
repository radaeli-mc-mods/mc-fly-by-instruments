// Bootstrap of the v2.1 buzzer module 1x1. Writes buzzer_module_1x1.bbmodel.
// Square dark-metal housing with a 1 px brass rim and a stepped cap carrying a painted grille
// (guide §9.4): reads as a Create-style speaker without rotated facets.
// Modelled with the table facing up: rests on Y 0, centred on X/Z 8, 4 x 4 footprint (guide §9.2).
// Refine in Blockbench afterwards; do NOT rerun over a refined source.
import { texture, face, cube, group, writeProject, px, canvas, blit, checkMap } from '../_tools/bbtools.mjs';

// --- One 16 x 16 atlas, 1 texel = 1 unit ----------------------------------------------------
const MAPS = {
  // Lit from the top-left; darkest dark-metal tone on the bottom edge.
  housingSide: checkMap('housing side', ['zzyx', 'xxxw'], 4, 2),
  // Brass rim around the cap (centre hidden under it).
  housingTop: checkMap('housing top', ['6554', '5ww3', '5ww3', '4332'], 4, 4),
  housingBottom: checkMap('housing bottom', ['wxxw', 'xyyx', 'xyyx', 'wxxw'], 4, 4),
  capSide: checkMap('cap side', ['zx'], 2, 1),
  // Grille: alternating dark holes in the dark-metal cap.
  capTop: checkMap('cap top', ['zw', 'wy'], 2, 2),
};
const UV = {
  housingSide: [0, 0, 4, 2], housingTop: [4, 0, 8, 4], housingBottom: [8, 0, 12, 4],
  capSide: [0, 4, 2, 5], capTop: [2, 4, 4, 6],
};
const atlas = canvas(16, 16, 'w');
for (const [key, map] of Object.entries(MAPS)) blit(atlas, map, UV[key][0], UV[key][1]);

const textures = [texture('buzzer_module', 16, { source: atlas, particle: true })];
const uv = region => px(16, UV[region]);
const sides = region => Object.fromEntries(['north', 'south', 'east', 'west'].map(s => [s, face(uv(region), 0)]));
const elements = [
  cube('housing', [6, 0, 6], [10, 2, 10],
    { ...sides('housingSide'), up: face(uv('housingTop'), 0), down: face(uv('housingBottom'), 0) }, 1),
  // No down face: it would sit on the housing top.
  cube('grille_cap', [7, 2, 7], [9, 3, 9], { ...sides('capSide'), up: face(uv('capTop'), 0) }, 2),
];
const groups = [group('buzzer', [8, 0, 8], elements)];
writeProject(new URL('buzzer_module_1x1.bbmodel', import.meta.url),
  { name: 'buzzer_module_1x1', elements, groups, textures });
console.log(`Wrote buzzer_module_1x1.bbmodel: ${elements.length} cubes, ${textures.length} texture.`);
