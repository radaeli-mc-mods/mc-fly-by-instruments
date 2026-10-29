// Review renders of the buzzer module, alone and beside the 1x1 lamps on a table (flat-lit, no AO).
//   node blockbench/buzzer-module-1x1/render-preview.mjs
import { writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const SURFACE = 4 / 16; // PanelCoordinates.SURFACE
const module = (model, column, row) => modelQuads({
  model, offset: [(column + 0.5) / 3 - 0.5, SURFACE, (row + 0.5) / 3 - 0.5],
});
const save = (name, quads, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(quads, { yaw: 225, pitch: 30, scale: 128, ...view })));
  console.log(`Wrote blockbench/buzzer-module-1x1/${name}.`);
};
save('preview.png', modelQuads({ model: 'buzzer_module_1x1' }), { scale: 1024 });
save('preview-on-table.png', [
  ...modelQuads({ model: 'modular_table_base' }),
  ...module('lamp_module_1x1_off', 0, 1),
  ...module('buzzer_module_1x1', 1, 1),
  ...module('lamp_module_1x1_on', 2, 1),
], { scale: 384 });
