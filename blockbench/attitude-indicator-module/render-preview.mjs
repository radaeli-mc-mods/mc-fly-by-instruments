// Review renders of the Attitude Indicator models from the exported JSON (flat-lit, no AO; the display itself is
// drawn by the renderer in game, see display-preview.png). Run export-assets.mjs first.
//   node blockbench/attitude-indicator-module/render-preview.mjs
import { writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const save = (name, model, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(modelQuads({ model }), view)));
  console.log(`Wrote blockbench/attitude-indicator-module/${name}.`);
};
save('preview.png', 'attitude_indicator_desk', { yaw: 205, pitch: 25, scale: 256 });
save('preview-side.png', 'attitude_indicator_desk', { yaw: 270, pitch: 0, scale: 256 });
save('preview-rear.png', 'attitude_indicator_desk', { yaw: 25, pitch: 25, scale: 256 });
save('preview-front.png', 'attitude_indicator_desk', { yaw: 180, pitch: 0, scale: 256 });
save('preview-wall.png', 'attitude_indicator_wall', { yaw: 205, pitch: 35, scale: 256 });
save('preview-small.png', 'attitude_indicator_desk', { yaw: 205, pitch: 25, scale: 32 }); // ~5 blocks
