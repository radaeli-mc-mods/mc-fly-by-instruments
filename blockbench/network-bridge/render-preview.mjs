// Review renders of the Network Bridge beside the Avionics Computer (flat-lit, no AO).
//   node blockbench/network-bridge/render-preview.mjs
import { writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const save = (name, quads, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(quads, { yaw: 225, pitch: 30, scale: 128, ...view })));
  console.log(`Wrote blockbench/network-bridge/${name}.`);
};
save('preview.png', modelQuads({ model: 'network_bridge', at: [0, 0, 0] }));
save('preview-beside-computer.png', [
  ...modelQuads({ model: 'avionics_computer_normal', at: [0, 0, 0], y: 180 }),
  ...modelQuads({ model: 'network_bridge', at: [1.5, 0, 0] }),
], { scale: 96 });
