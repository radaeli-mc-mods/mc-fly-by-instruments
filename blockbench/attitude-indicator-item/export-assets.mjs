// Mechanical export of the saved attitude_indicator_item.bbmodel (no regeneration).
// Writes models/block/attitude_indicator_item.json and textures/block/attitude_indicator_item.png;
// models/item/attitude_indicator_module.json inherits the model and only sets the display transforms and the
// overrides that hand the table renderer the wedge (custom model data 2) and the plate (custom model data 1).
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';

const root = new URL('./', import.meta.url);
const result = exportBlockModel({
  projectUrl: new URL('attitude_indicator_item.bbmodel', root),
  assetsUrl: new URL(`../../src/main/resources/assets/${NS}/`, root),
  model: 'attitude_indicator_item',
});
console.log(`Exported attitude_indicator_item: ${result.cubes} cubes; ${result.textures.join(', ')}.`);
