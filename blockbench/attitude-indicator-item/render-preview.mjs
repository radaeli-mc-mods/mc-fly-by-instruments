// Review renders of the Attitude Indicator item from the exported JSON (flat-lit, no AO), next to the FMS Compass
// and the FMS module. Run export-assets.mjs first.
//   node blockbench/attitude-indicator-item/render-preview.mjs
import { writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const save = (name, model, view) => {
  writeFileSync(new URL(name, import.meta.url), toPng(render(modelQuads({ model }), view)));
  console.log(`Wrote blockbench/attitude-indicator-item/${name}.`);
};
save('preview.png', 'attitude_indicator_item', { yaw: 180, pitch: 55, scale: 256 });
save('preview-top.png', 'attitude_indicator_item', { yaw: 180, pitch: 90, scale: 256 });
save('preview-side.png', 'attitude_indicator_item', { yaw: 150, pitch: 20, scale: 256 });
save('preview-small.png', 'attitude_indicator_item', { yaw: 180, pitch: 90, scale: 16 }); // the 16 px inventory icon
save('compare-compass.png', 'fms_navigator', { yaw: 180, pitch: 90, scale: 256 });
