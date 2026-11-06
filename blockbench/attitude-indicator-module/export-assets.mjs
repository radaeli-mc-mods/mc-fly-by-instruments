// Mechanical export of the saved Attitude Indicator sources (no regeneration).
// Writes models/block/attitude_indicator_desk.json and attitude_indicator_wall.json plus textures/block/
// attitude_indicator_module.png (owned by the desk source; the wall source must carry the same pixels).
// The housing references create:block/industrial_iron_block by id (Create's assets are All Rights Reserved).
// Only housing_slope may turn: 22.5 degrees about X, the Minecraft element limit (guide §3).
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';

const root = new URL('./', import.meta.url);
const assetsUrl = new URL(`../../src/main/resources/assets/${NS}/`, root);
const desk = exportBlockModel({
  projectUrl: new URL('attitude_indicator_desk.bbmodel', root), assetsUrl, model: 'attitude_indicator_desk',
  rotatable: { housing_slope: -22.5 },
});
const wall = exportBlockModel({
  projectUrl: new URL('attitude_indicator_wall.bbmodel', root), assetsUrl, model: 'attitude_indicator_wall',
  textures: 'verify',
});
console.log(`Exported attitude_indicator_desk: ${desk.cubes} cubes; attitude_indicator_wall: ${wall.cubes} cubes.`);
