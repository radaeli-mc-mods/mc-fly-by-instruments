// Mechanical export of the saved network_bridge.bbmodel (no regeneration).
// Writes models/block/network_bridge.json and network_bridge.png.
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';

const root = new URL('./', import.meta.url);
const result = exportBlockModel({
  projectUrl: new URL('network_bridge.bbmodel', root),
  assetsUrl: new URL(`../../src/main/resources/assets/${NS}/`, root),
  model: 'network_bridge',
});
console.log(`Exported network_bridge: ${result.cubes} cubes; ${result.textures.join(', ')}.`);
