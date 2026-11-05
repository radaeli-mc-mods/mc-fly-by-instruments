// Review renders of the Attitude Reference Unit straight from the .bbmodel (nothing is exported to the
// game yet). Flat-lit, no AO.   node blockbench/attitude-reference-unit/render-preview.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { pixels } from '../_tools/bbtools.mjs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const project = JSON.parse(readFileSync(new URL('attitude_reference_unit.bbmodel', import.meta.url), 'utf8'));
const atlas = project.textures[0];
const bytes = Buffer.from(atlas.source.split(',')[1], 'base64');
const data = new Uint8ClampedArray(pixels(bytes).flatMap(p => p.split(',').map(Number)));
const lookup = () => ({ width: atlas.width, height: atlas.height, data });
const json = {
  textures: { 0: 'bbmodel:atlas' },
  elements: project.elements.map(c => ({
    from: c.from, to: c.to,
    faces: Object.fromEntries(Object.entries(c.faces).filter(([, f]) => f.texture !== null).map(([side, f]) =>
      [side, { uv: f.uv.map(v => v * 16 / 32), texture: '#0', ...(f.rotation ? { rotation: f.rotation } : {}) }])),
  })),
};
const quads = modelQuads({ json, lookup, at: [0, 0, 0] });
const save = (name, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(quads, view)));
  console.log(`Wrote blockbench/attitude-reference-unit/${name}.`);
};
save('preview.png', { yaw: 135, pitch: 30, scale: 128 });
save('preview-rear.png', { yaw: 315, pitch: -30, scale: 128 });
save('preview-small.png', { yaw: 135, pitch: 30, scale: 32 }); // legibility check at ~5 blocks
