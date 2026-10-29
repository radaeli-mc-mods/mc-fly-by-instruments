// Shared blockstate/item writers for the Avionics Wire nodes (connector, support).
// Models are authored facing UP; the six facings rotate them like vanilla end rods.
import { writeFileSync } from 'node:fs';
import { NS } from './bbtools.mjs';

const ROTATIONS = {
  up: {}, down: { x: 180 }, north: { x: 90 }, south: { x: 90, y: 180 }, west: { x: 90, y: 270 }, east: { x: 90, y: 90 },
};

// flags: { property: model } adds a boolean property that swaps in another model when true.
export function writeNodeBlockstate(assets, name, flags = {}) {
  const variants = {};
  for (const [facing, rotation] of Object.entries(ROTATIONS)) {
    if (Object.keys(flags).length === 0) {
      variants[`facing=${facing}`] = { model: `${NS}:block/${name}`, ...rotation };
      continue;
    }
    for (const [property, model] of Object.entries(flags)) {
      variants[`facing=${facing},${property}=false`] = { model: `${NS}:block/${name}`, ...rotation };
      variants[`facing=${facing},${property}=true`] = { model: `${NS}:block/${model}`, ...rotation };
    }
  }
  writeFileSync(new URL(`blockstates/${name}.json`, assets), `${JSON.stringify({ variants }, null, 2)}\n`);
}

// The nodes are small: the item shows them enlarged, standing on their host face. `gui` centres each node in
// the slot ({ translation, scale }): the nodes sit low in the block space, so the lift depends on the model.
export function writeNodeItem(assets, name, gui) {
  const view = (rotation, translation, scale) => ({ rotation, translation, scale: [scale, scale, scale] });
  const display = {
    gui: view([30, 225, 0], [0, gui.translation, 0], gui.scale),
    ground: view([0, 0, 0], [0, 3, 0], 0.8),
    fixed: view([-90, 0, 0], [0, 0, -4], 1.4),
    thirdperson_righthand: view([75, 45, 0], [0, 3, 2], 0.8),
    thirdperson_lefthand: view([75, 45, 0], [0, 3, 2], 0.8),
    firstperson_righthand: view([0, 45, 0], [0, 4, 0], 1.0),
    firstperson_lefthand: view([0, 225, 0], [0, 4, 0], 1.0),
  };
  writeFileSync(new URL(`models/item/${name}.json`, assets),
    `${JSON.stringify({ parent: `${NS}:block/${name}`, display }, null, 2)}\n`);
}
