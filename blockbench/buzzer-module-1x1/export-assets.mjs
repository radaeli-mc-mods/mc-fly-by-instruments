// Mechanical export of the saved buzzer_module_1x1.bbmodel (no regeneration).
// Writes models/block/buzzer_module_1x1.json and this mod's PNG.
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';

const root = new URL('./', import.meta.url);
const result = exportBlockModel({
  projectUrl: new URL('buzzer_module_1x1.bbmodel', root),
  assetsUrl: new URL(`../../src/main/resources/assets/${NS}/`, root),
  model: 'buzzer_module_1x1',
});
console.log(`Exported buzzer_module_1x1: ${result.cubes} cubes; ${result.textures.join(', ')}.`);
