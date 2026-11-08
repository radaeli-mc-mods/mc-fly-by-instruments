// Render do Avionics Computer em alerta para a logo (ver compose-logo.py).
// Uso: node blockbench/_logo/render-logo-computer.mjs <saida.png>
// Escala 512 = 32 px por texel = 4 unidades da grade de 8 px do horizonte.
import { writeFileSync } from 'node:fs';
import { modelQuads, render, toPng } from '../_tools/render-scene.mjs';

const out = process.argv[2];
if (!out) throw new Error('uso: render-logo-computer.mjs <saida.png>');
const img = render(
  modelQuads({ model: 'avionics_computer_warning', at: [0, 0, 0], y: 180 }),
  { yaw: 210, pitch: 30, scale: 512 },
);
writeFileSync(out, toPng(img));
console.log(`Wrote ${out} (${img[0].length} x ${img.length}).`);
