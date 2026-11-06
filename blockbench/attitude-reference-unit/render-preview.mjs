// Review renders of the Attitude Reference Unit from the exported models and blockstate (flat-lit, no AO;
// glass is blended last). Run export-assets.mjs first.   node blockbench/attitude-reference-unit/render-preview.mjs
// The gimbal is drawn at rest here; in the game the block entity renderer animates it.
import { readFileSync, writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const NS = 'aeronautics_fly_by_instruments';
const blockstate = JSON.parse(readFileSync(new URL(
  `../../src/main/resources/assets/${NS}/blockstates/attitude_reference_unit.json`, import.meta.url), 'utf8'));

const quadsFor = ({ nose, up }) => {
  const quads = [];
  for (const part of blockstate.multipart) {
    if (part.when && (part.when.nose ?? nose) !== nose) continue;
    if (part.when && (part.when.up ?? up) !== up) continue;
    quads.push(...modelQuads({ model: part.apply.model.split('/').pop(), x: part.apply.x, y: part.apply.y }));
  }
  for (const gimbal of ['gimbal_ring', 'gimbal_rotor']) quads.push(...modelQuads({ model: `attitude_reference_unit_${gimbal}` }));
  return quads;
};
const save = (name, state, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(quadsFor(state), view)));
  console.log(`Wrote blockbench/attitude-reference-unit/${name}.`);
};
save('preview.png', { nose: 'north', up: 'up' }, { yaw: 135, pitch: 30, scale: 128 });
save('preview-rear.png', { nose: 'north', up: 'up' }, { yaw: 315, pitch: -30, scale: 128 });
save('preview-east-north.png', { nose: 'east', up: 'north' }, { yaw: 135, pitch: 30, scale: 128 });
save('preview-small.png', { nose: 'north', up: 'up' }, { yaw: 135, pitch: 30, scale: 32 }); // ~5 blocks
