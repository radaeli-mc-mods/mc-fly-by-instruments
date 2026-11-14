// Review renders of the Attitude Reference Unit from the exported models and blockstate (flat-lit, no AO;
// glass is blended last). Run export-assets.mjs first.   node blockbench/attitude-reference-unit/render-preview.mjs
// The propeller is drawn at rest here; in the game the block entity renderer spins it.
import { readFileSync, writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const NS = 'aeronautics_fly_by_instruments';
const blockstate = JSON.parse(readFileSync(new URL(
  `../../src/main/resources/assets/${NS}/blockstates/attitude_reference_unit.json`, import.meta.url), 'utf8'));

const quadsFor = ({ nose, up, status = 'ok' }) => {
  const state = { nose, up, status };
  const quads = [];
  for (const part of blockstate.multipart) {
    if (part.when && Object.entries(part.when).some(([key, value]) => state[key] !== value)) continue;
    const model = part.apply.model.split('/').pop();
    quads.push(...modelQuads({ model, x: part.apply.x, y: part.apply.y }));
    // The propeller follows the airplane. Its roll about the nose axis is only a spin angle, so the plain
    // model with the airplane's x/y is a valid rest pose for the rolled variant too.
    if (model.includes('_airplane')) {
      quads.push(...modelQuads({ model: 'attitude_reference_unit_propeller', x: part.apply.x, y: part.apply.y }));
    }
  }
  return quads;
};
const save = (name, state, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(quadsFor(state), view)));
  console.log(`Wrote blockbench/attitude-reference-unit/${name}.`);
};
save('preview.png', { nose: 'north', up: 'up' }, { yaw: 160, pitch: 35, scale: 256 });
save('preview-rear.png', { nose: 'north', up: 'up' }, { yaw: 340, pitch: -30, scale: 256 });
save('preview-east-north.png', { nose: 'east', up: 'north' }, { yaw: 160, pitch: 35, scale: 256 }); // knife edge
save('preview-nose-up.png', { nose: 'up', up: 'south' }, { yaw: 160, pitch: 35, scale: 256 });
save('preview-degraded.png', { nose: 'north', up: 'up', status: 'degraded' }, { yaw: 160, pitch: 35, scale: 256 });
save('preview-small.png', { nose: 'north', up: 'up' }, { yaw: 160, pitch: 35, scale: 32 }); // ~5 blocks
