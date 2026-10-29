// Mechanical export of the saved wire_support.bbmodel (no regeneration): block model, PNG,
// blockstate (end-rod rotations of the UP model) and item model.
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';
import { writeNodeBlockstate, writeNodeItem } from '../_tools/wire-nodes.mjs';

const root = new URL('./', import.meta.url);
const assets = new URL(`../../src/main/resources/assets/${NS}/`, root);
const result = exportBlockModel({ projectUrl: new URL('wire_support.bbmodel', root), assetsUrl: assets,
  model: 'wire_support' });
writeNodeBlockstate(assets, 'wire_support');
writeNodeItem(assets, 'wire_support', { translation: 6.5, scale: 1.25 });
console.log(`Exported wire_support: ${result.cubes} cubes; ${result.textures.join(', ')}.`);
