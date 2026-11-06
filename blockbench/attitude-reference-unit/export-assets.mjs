// Mechanical export from the saved Blockbench source (attitude_reference_unit.bbmodel).
// Enforces docs/visual-design-guide.md v2: integer cuboids, no rotation, 1 texel per unit.
//
// Models (models/block/attitude_reference_unit_<part>.json), one per outliner group:
//   frame         corner posts + data sockets (cutout)
//   glass         the glass cube (translucent)
//   arm_nose      Nose reference arm, source facing NORTH   } rotated by the blockstate
//   arm_up        Up reference arm, source facing UP        }
//   gimbal_ring / gimbal_rotor   drawn by the block entity renderer, never in the blockstate
//   item          everything at rest, for the inventory (a renderer does not draw items)
// Blockstate: frame + glass always; the Nose arm by `nose`, the Up arm by `up` (x/y rotations).
import { readdirSync, readFileSync, unlinkSync, writeFileSync, mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';

const NS = 'aeronautics_fly_by_instruments';
const NAME = 'attitude_reference_unit';
const root = new URL('./', import.meta.url);
const assets = new URL(`../../src/main/resources/assets/${NS}/`, root);
const project = JSON.parse(readFileSync(new URL(`${NAME}.bbmodel`, root), 'utf8'));
assert.equal(project.meta.model_format, 'java_block');

const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const base = t => t.name.replace(/\.png$/, '');
const ids = project.textures.map(t => `${t.namespace || NS}:${t.folder || 'block'}/${base(t)}`);
const PARTS = project.textures.findIndex(t => base(t) === `${NAME}_parts`);
assert.ok(PARTS >= 0, 'parts atlas');

// Textures owned by this mod: embedded PNGs written as-is. Create textures are referenced by id only.
for (const t of project.textures) {
  if ((t.namespace || NS) !== NS) continue;
  assert.ok(t.source?.startsWith('data:image/png;base64,'), `${t.name} must be embedded`);
  const target = new URL(`textures/block/${base(t)}.png`, assets);
  mkdirSync(new URL('./', target), { recursive: true });
  writeFileSync(target, Buffer.from(t.source.split(',')[1], 'base64'));
}

const elementByUuid = new Map(project.elements.map(e => [e.uuid, e]));
const groupElements = name => {
  const node = project.outliner.find(g => g.uuid === project.groups.find(x => x.name === name)?.uuid);
  assert.ok(node, `group ${name}`);
  return node.children.map(uuid => elementByUuid.get(uuid));
};

// Converts a cube to a model element, checking the v2 rules; returns the textures it uses too.
function toElement(c, used) {
  assert.equal(c.type, 'cube');
  assert.ok(SIDES.every(side => side in c.faces), `${c.name}: all six faces listed`);
  assert.ok(!c.rotation?.some(v => v !== 0), `${c.name}: cuboids stay unrotated`);
  for (const v of [...c.from, ...c.to]) assert.ok(Number.isInteger(v), `${c.name}: integer coordinates`);
  const size = [0, 1, 2].map(i => c.to[i] - c.from[i]);
  const faces = {};
  for (const side of SIDES) {
    const f = c.faces[side];
    if (f.texture === null || f.texture === false || f.texture === undefined) continue;
    const t = project.textures[f.texture];
    assert.ok(t, `${c.name}.${side}: texture`);
    assert.ok(t.height === t.width, `${t.name}: square (no animation here)`);
    const [gu, gv] = AXES[side].map(a => size[a]);
    assert.equal(Math.abs(f.uv[2] - f.uv[0]) * t.width / t.uv_width, gu, `${c.name}.${side}: 1 texel per unit (u)`);
    assert.equal(Math.abs(f.uv[3] - f.uv[1]) * t.height / t.uv_height, gv, `${c.name}.${side}: 1 texel per unit (v)`);
    faces[side] = { uv: f.uv.map((v, i) => v * 16 / (i % 2 ? t.uv_height : t.uv_width)), texture: `#${f.texture}` };
    used.add(f.texture);
  }
  return { name: c.name, from: c.from, to: c.to, faces };
}

const CREDIT = 'Made with Blockbench for Aeronautics: Fly by Instruments';
const out = (dir, name) => new URL(`${dir}/${name}.json`, assets);
const written = new Set();
function writeModel(name, cubes, renderType = null) {
  const used = new Set();
  const elements = cubes.map(c => toElement(c, used));
  const textures = { particle: ids[PARTS] };
  for (const i of [...used].sort((a, b) => a - b)) textures[String(i)] = ids[i];
  const json = { credit: CREDIT, parent: 'minecraft:block/block', ambientocclusion: true,
    ...(renderType ? { render_type: renderType } : {}), textures, elements };
  writeFileSync(out('models/block', name), `${JSON.stringify(json, null, 2)}\n`);
  written.add(`${name}.json`);
  return elements.length;
}

const frame = [...groupElements('frame')];
const glass = groupElements('glass');
const noseArm = groupElements('arm_nose');
const upArm = groupElements('arm_up');
const ring = groupElements('gimbal_ring');
const rotor = groupElements('gimbal_rotor');
writeModel(`${NAME}_frame`, frame);
writeModel(`${NAME}_glass`, glass, 'minecraft:translucent');
writeModel(`${NAME}_arm_nose`, noseArm);
writeModel(`${NAME}_arm_up`, upArm);
writeModel(`${NAME}_gimbal_ring`, ring);
writeModel(`${NAME}_gimbal_rotor`, rotor);
writeModel(`${NAME}_item`, [...frame, ...glass, ...noseArm, ...upArm, ...ring, ...rotor], 'minecraft:translucent');
writeFileSync(out('models/item', NAME), `${JSON.stringify({ parent: `${NS}:block/${NAME}_item` }, null, 2)}\n`);

// Blockstate rotations (x first, then y, as Minecraft applies them). The source arms point NORTH (Nose)
// and UP (Up). x=90 turns NORTH into DOWN and UP into NORTH.
const NOSE_TURN = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
const UP_TURN = { up: {}, north: { x: 90 }, east: { x: 90, y: 90 }, south: { x: 90, y: 180 }, west: { x: 90, y: 270 }, down: { x: 180 } };
const parts = [
  { apply: { model: `${NS}:block/${NAME}_frame` } },
  { apply: { model: `${NS}:block/${NAME}_glass` } },
];
for (const side of SIDES) parts.push({ when: { nose: side }, apply: { model: `${NS}:block/${NAME}_arm_nose`, ...NOSE_TURN[side] } });
for (const side of SIDES) parts.push({ when: { up: side }, apply: { model: `${NS}:block/${NAME}_arm_up`, ...UP_TURN[side] } });
mkdirSync(new URL('blockstates/', assets), { recursive: true });
writeFileSync(new URL(`blockstates/${NAME}.json`, assets), `${JSON.stringify({ multipart: parts }, null, 2)}\n`);

// Remove assets of the previous (v2.1 opaque box) design.
let removed = 0;
for (const file of readdirSync(new URL('models/block/', assets))) {
  if (file.startsWith(`${NAME}_`) && !written.has(file)) { unlinkSync(new URL(`models/block/${file}`, assets)); removed++; }
}
for (const old of [`${NAME}.png`, `${NAME}_powered.png`]) {
  try { unlinkSync(new URL(`textures/block/${old}`, assets)); removed++; } catch { /* already gone */ }
}
console.log(`Exported ${written.size} models + item + blockstate; removed ${removed} old files.`);
