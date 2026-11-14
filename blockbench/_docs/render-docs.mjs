// Documentation previews (README and the mods.radaeli.dev portal). Builds scenes from the exported block
// models and writes docs/gallery/renders/*.png. Flat-lit renders: no AO, no shaders, no block-entity text.
//   node blockbench/_docs/render-docs.mjs
// Host blocks are stripped oak logs lying along X, for contrast with the iron and brass models (vanilla
// textures in reference/minecraft-textures, outside git; see README.md).
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { modelQuads, logQuads, render, toPng } from '../_tools/render-scene.mjs';
import { wireQuads } from '../_tools/wire-scene.mjs';

const OUT = new URL('../../docs/gallery/renders/', import.meta.url);
mkdirSync(OUT, { recursive: true });
const deck = at => logQuads(at, 'minecraft:block/stripped_oak_log', 'minecraft:block/stripped_oak_log_top', 'x');
const SURFACE = 4 / 16; // PanelCoordinates.SURFACE

// Blockstate rotations of the wire nodes (end-rod style) and the horizontal devices.
const NODE = { up: {}, down: { x: 180 }, north: { x: 90 }, south: { x: 90, y: 180 }, west: { x: 90, y: 270 }, east: { x: 90, y: 90 } };
const HORIZONTAL = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 } };

const node = (model, at, facing, extra = {}) => modelQuads({ model, at, ...NODE[facing], ...extra });
const device = (model, at, facing = 'south', extra = {}) => modelQuads({ model, at, ...HORIZONTAL[facing], ...extra });
/** A module on a floor table at `at`, as PanelCoordinates.modelOffset places it. */
const module = (model, at, column, row, width = 1, height = 1, extra = {}) => modelQuads({
  model, at, ...extra,
  offset: [(column + width / 2) / 3 - 0.5, SURFACE, (row + height / 2) / 3 - 0.5],
});
const table = at => modelQuads({ model: 'modular_table_base', at });

function save(name, quads, view = {}) {
  const img = render(quads, { yaw: 225, pitch: 30, scale: 96, ...view });
  writeFileSync(new URL(`${name}.png`, OUT), toPng(img));
  console.log(`Wrote docs/gallery/renders/${name}.png (${img[0].length} x ${img.length}).`);
}

// --- Avionics Computer: normal and warning --------------------------------------------------
save('avionics-computer', [
  ...device('avionics_computer_normal', [0, 0, 0]),
  ...device('avionics_computer_warning', [1.5, 0, 0]),
], { scale: 128 });

// --- Avionics Wire: computer -> two supports -> Terrain Scanner, on a wooden deck ----------------
{
  const quads = [];
  for (let x = 0; x < 7; x++) for (let z = 0; z < 2; z++) quads.push(...deck([x, -1, z]));
  quads.push(...device('avionics_computer_normal', [0, 0, 1], 'south'));
  // Scanner looking ahead (east); its rear data socket is centred on the west face, where the connector goes.
  quads.push(...device('terrain_scanner', [6, 0, 1], 'east'));
  quads.push(...node('wire_connector', [1, 0, 1], 'east'));
  quads.push(...node('wire_support', [3, 0, 1], 'up'));
  quads.push(...node('wire_support', [4, 0, 1], 'up'));
  quads.push(...node('wire_connector', [5, 0, 1], 'west'));
  quads.push(...wireQuads([
    { at: [1, 0, 1], facing: 'east', connector: true },
    { at: [3, 0, 1], facing: 'up' },
    { at: [4, 0, 1], facing: 'up' },
    { at: [5, 0, 1], facing: 'west', connector: true },
  ]));
  save('avionics-wire', quads, { scale: 72 });
}

// --- Wire nodes close up: connector -> support -> slim connector on a table edge ----------------
{
  const quads = [];
  for (let x = 0; x < 4; x++) quads.push(...deck([x, -1, 0]));
  quads.push(...node('wire_connector', [0, 0, 0], 'up'));
  quads.push(...node('wire_support', [1, 0, 0], 'up'));
  quads.push(...table([3, 0, 0]));
  // Slim connector on the table's west edge: centred on the 4 px edge (WireNodeBlock.tableEdgeSocket).
  const slim = [0, SURFACE / 2 - 0.5, 0];
  quads.push(...node('wire_connector_slim', [2, 0, 0], 'west', { offset: slim }));
  quads.push(...wireQuads([
    { at: [0, 0, 0], facing: 'up', connector: true },
    { at: [1, 0, 0], facing: 'up' },
    { at: [2, 0, 0], facing: 'west', connector: true, offset: slim },
  ]));
  save('wire-nodes', quads, { scale: 160 });
}

// --- Modular Table with modules: FMS 2x3, red lamp lit, amber lamp off, buzzer ----------------
{
  const at = [0, 0, 0];
  save('modular-table', [
    ...table(at),
    ...module('fms_module_2x3', at, 0, 0, 2, 3),
    ...module('lamp_module_1x1_on', at, 2, 0),
    ...module('lamp_module_1x1_amber_off', at, 2, 1),
    ...module('buzzer_module_1x1', at, 2, 2),
  ], { scale: 256 });
}

// --- Lamps, 1x1 and 2x1, red and amber, off and lit ------------------------------------------
{
  const quads = [];
  const lamps = [['lamp_module_1x1_off', 0, 0], ['lamp_module_1x1_on', 1, 0], ['lamp_module_1x1_amber_off', 2, 0],
    ['lamp_module_1x1_amber_on', 0, 1], ['buzzer_module_1x1', 1, 1], ['lamp_module_1x1_amber_on', 2, 1]];
  quads.push(...table([0, 0, 0]));
  for (const [model, column, row] of lamps) quads.push(...module(model, [0, 0, 0], column, row));
  quads.push(...module('lamp_module_2x1_on', [0, 0, 0], 0, 2, 2, 1));
  save('table-lamps', quads, { scale: 256 });
}

// --- Table docked on an Avionics Computer --------------------------------------------------
{
  const at = [0, 1, 0];
  save('table-docked', [
    ...device('avionics_computer_normal', [0, 0, 0], 'south'),
    ...table(at),
    ...module('lamp_module_1x1_on', at, 0, 1),
    ...module('lamp_module_1x1_amber_off', at, 1, 1),
    ...module('buzzer_module_1x1', at, 2, 1),
  ], { scale: 192 });
}

// --- FMS module alone (v2.1) -------------------------------------------------------------------
save('fms-module', modelQuads({ model: 'fms_module_2x3' }), { scale: 320 });

// --- Attitude Reference Unit: default placement, Nose = north and Up = up (propeller drawn at rest) ---
{
  const NS = 'aeronautics_fly_by_instruments';
  const blockstate = JSON.parse(readFileSync(new URL(
    `../../src/main/resources/assets/${NS}/blockstates/attitude_reference_unit.json`, import.meta.url), 'utf8'));
  /** The multipart blockstate for a state, plus the propeller the block entity renderer draws in game. */
  const reference = (at, state) => {
    const quads = [];
    for (const part of blockstate.multipart) {
      if (part.when && Object.entries(part.when).some(([key, value]) => state[key] !== value)) continue;
      const model = part.apply.model.split('/').pop();
      quads.push(...modelQuads({ model, at, x: part.apply.x, y: part.apply.y }));
      if (model.includes('_airplane')) {
        quads.push(...modelQuads({ model: 'attitude_reference_unit_propeller', at, x: part.apply.x, y: part.apply.y }));
      }
    }
    return quads;
  };
  // Off the corner: at yaw 135 the front post hides the airplane.
  save('attitude-reference-unit', reference([0, 0, 0], { nose: 'north', up: 'up', status: 'ok' }),
    { yaw: 160, pitch: 35, scale: 192 });
}

// --- Attitude Indicator on a floor table (wedge, 2x2) with a lamp and the buzzer ---------------------
{
  const at = [0, 0, 0];
  save('attitude-indicator', [
    ...table(at),
    ...module('attitude_indicator_desk', at, 0, 0, 2, 2),
    ...module('lamp_module_1x1_on', at, 2, 0),
    ...module('buzzer_module_1x1', at, 2, 1),
  ], { yaw: 205, scale: 256 });
}

// --- Attitude Indicator pocket gauge next to the FMS Compass, seen from above like the inventory ---
save('attitude-indicator-item', [
  ...modelQuads({ model: 'attitude_indicator_item', at: [0, 0, 0] }),
  ...modelQuads({ model: 'fms_navigator', at: [1.25, 0, 0] }),
], { yaw: 180, pitch: 55, scale: 256 });
