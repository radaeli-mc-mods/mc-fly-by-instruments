// Mechanical export of the saved wire_connector.bbmodel (no regeneration): block model, PNG,
// blockstate (end-rod rotations of the UP model), item model, and the slim variant without the flange
// for table edges (WireConnectorBlock.SLIM).
import { readFileSync, writeFileSync } from 'node:fs';
import { exportBlockModel, NS } from '../_tools/bbtools.mjs';
import { writeNodeBlockstate, writeNodeItem } from '../_tools/wire-nodes.mjs';

const root = new URL('./', import.meta.url);
const assets = new URL(`../../src/main/resources/assets/${NS}/`, root);
const result = exportBlockModel({ projectUrl: new URL('wire_connector.bbmodel', root), assetsUrl: assets,
  model: 'wire_connector' });
const modelUrl = name => new URL(`models/block/${name}.json`, assets);
const model = JSON.parse(readFileSync(modelUrl('wire_connector'), 'utf8'));
model.elements = model.elements.filter(element => element.name !== 'flange');
// On the table the body is flush with the edge: the stem keeps only the 1 px that replaces the flange.
// Below y = 0 it would sit inside the table with faces on its top and bottom planes (z-fighting).
const stem = model.elements.find(element => element.name === 'stem');
stem.from = [stem.from[0], 0, stem.from[2]];
for (const face of Object.values(stem.faces)) face.uv = [face.uv[0], face.uv[1], face.uv[2], face.uv[1] + 1];
writeFileSync(modelUrl('wire_connector_slim'), `${JSON.stringify(model, null, 2)}\n`);
writeNodeBlockstate(assets, 'wire_connector', { slim: 'wire_connector_slim' });
writeNodeItem(assets, 'wire_connector', { translation: 10.25, scale: 1.6 });
console.log(`Exported wire_connector: ${result.cubes} cubes; ${result.textures.join(', ')}.`);
