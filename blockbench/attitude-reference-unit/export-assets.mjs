// Mechanical export from the saved Blockbench source (attitude_reference_unit.bbmodel).
// Enforces docs/visual-design-guide.md v2: integer cuboids, no rotation, 1 texel per unit.
//
// Models (models/block/attitude_reference_unit_<part>.json):
//   frame_ok / frame_degraded / frame_inop   corner posts + data sockets (cutout); the three only differ in
//                                             the socket status texture, chosen by the `status` blockstate
//   glass             the glass cube (translucent)
//   airplane          the model airplane, source with Nose = NORTH and Up = UP
//   airplane_rolled   the same airplane rolled 90 degrees about the nose axis (Nose = NORTH, Up = EAST)
//   propeller         drawn by the block entity renderer, never in the blockstate
//   item              everything at rest, for the inventory (a renderer does not draw items)
// Blockstate: glass always; a frame by `status`; one airplane per valid (nose, up) pair. The blockstate's x/y
// rotations reach only 16 of the 24 orientations of a rigid model; the 8 with Nose and Up both horizontal use
// airplane_rolled.
import { readdirSync, readFileSync, unlinkSync, writeFileSync, mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';
import { FACE } from '../_tools/render-scene.mjs';

const NS = 'aeronautics_fly_by_instruments';
const NAME = 'attitude_reference_unit';
const STATUSES = ['ok', 'degraded', 'inop'];
const root = new URL('./', import.meta.url);
const assets = new URL(`../../src/main/resources/assets/${NS}/`, root);
const project = JSON.parse(readFileSync(new URL(`${NAME}.bbmodel`, root), 'utf8'));
assert.equal(project.meta.model_format, 'java_block');

const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const base = t => t.name.replace(/\.png$/, '');
const ids = project.textures.map(t => `${t.namespace || NS}:${t.folder || 'block'}/${base(t)}`);
const indexOf = name => { const i = project.textures.findIndex(t => base(t) === name); assert.ok(i >= 0, name); return i; };
const PARTS = indexOf(`${NAME}_parts`);
const SOCKET = Object.fromEntries(STATUSES.map(status => [status, indexOf(`${NAME}_socket_${status}`)]));

// Textures owned by this mod: embedded PNGs written as-is. Create textures are referenced by id only.
for (const t of project.textures) {
  if ((t.namespace || NS) !== NS) continue;
  assert.ok(t.source?.startsWith('data:image/png;base64,'), `${t.name} must be embedded`);
  const target = new URL(`textures/block/${base(t)}.png`, assets);
  mkdirSync(new URL('./', target), { recursive: true });
  writeFileSync(target, Buffer.from(t.source.split(',')[1], 'base64'));
  // A strip of square frames is an animated texture: Minecraft needs the .mcmeta beside it.
  assert.equal(t.height % t.width, 0, `${t.name}: square frames`);
  if (t.height > t.width) {
    writeFileSync(new URL(`${base(t)}.png.mcmeta`, target),
      `${JSON.stringify({ animation: { frametime: t.frame_time || 1 } }, null, 2)}\n`);
  }
}

const elementByUuid = new Map(project.elements.map(e => [e.uuid, e]));
const groupElements = name => {
  const node = project.outliner.find(g => g.uuid === project.groups.find(x => x.name === name)?.uuid);
  assert.ok(node, `group ${name}`);
  return node.children.map(uuid => elementByUuid.get(uuid));
};

// Converts a cube to a model element, checking the v2 rules; records the textures it uses too.
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
    assert.ok(!f.rotation, `${c.name}.${side}: no UV rotation in the source`);
    const [gu, gv] = AXES[side].map(a => size[a]);
    assert.equal(Math.abs(f.uv[2] - f.uv[0]) * t.width / t.uv_width, gu, `${c.name}.${side}: 1 texel per unit (u)`);
    // UVs of an animated strip address one square frame.
    assert.equal(Math.abs(f.uv[3] - f.uv[1]) * t.width / t.uv_height, gv, `${c.name}.${side}: 1 texel per unit (v)`);
    faces[side] = { uv: f.uv.map((v, i) => v * 16 / (i % 2 ? t.uv_height : t.uv_width)), texture: `#${f.texture}` };
    used.add(f.texture);
  }
  return { name: c.name, from: c.from, to: c.to, faces };
}

// --- Roll: 90 degrees about the nose axis (z through the block centre), taking UP to EAST -------------
// p -> (y, 16 - x, z). Coordinates stay integers; each face moves to a new side and keeps its UV rectangle,
// with the UV rotation that puts every texel on the same corner as before.
const ROLL_SIDE = { up: 'east', east: 'down', down: 'west', west: 'up', north: 'north', south: 'south' };
const roll = p => [p[1], 16 - p[0], p[2]];
const turned = (uvs, quarter) => { let r = uvs; for (let i = 0; i < quarter; i++) r = [r[3], r[0], r[1], r[2]]; return r; };
const corners = uv => [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
function rolled(element) {
  const a = roll(element.from), b = roll(element.to);
  const from = a.map((v, i) => Math.min(v, b[i])), to = a.map((v, i) => Math.max(v, b[i]));
  const faces = {};
  for (const [side, f] of Object.entries(element.faces)) {
    const target = ROLL_SIDE[side];
    const before = FACE[side](element.from, element.to).map(roll), uvBefore = corners(f.uv);
    const after = FACE[target](from, to);
    const quarter = [0, 1, 2, 3].find(q => {
      const uvAfter = turned(corners(f.uv), q);
      return after.every((p, j) => {
        const i = before.findIndex(o => o.every((v, k) => v === p[k]));
        return uvAfter[j][0] === uvBefore[i][0] && uvAfter[j][1] === uvBefore[i][1];
      });
    });
    assert.ok(quarter !== undefined, `${element.name}.${side}: UV rotation for the rolled face`);
    faces[target] = { ...f, ...(quarter ? { rotation: quarter * 90 } : {}) };
  }
  return { ...element, from, to, faces: Object.fromEntries(SIDES.filter(s => s in faces).map(s => [s, faces[s]])) };
}

const CREDIT = 'Made with Blockbench for Aeronautics: Fly by Instruments';
const out = (dir, name) => new URL(`${dir}/${name}.json`, assets);
const written = new Set();
// swap: { textureIndex: textureIndex } replaces one texture by another (status variants); transform: per element.
function writeModel(name, cubes, { renderType = null, swap = {}, transform = e => e } = {}) {
  const used = new Set();
  const elements = cubes.map(c => transform(toElement(c, used)));
  const textures = { particle: ids[PARTS] };
  for (const i of [...used].sort((a, b) => a - b)) textures[String(i)] = ids[swap[i] ?? i];
  const json = { credit: CREDIT, parent: 'minecraft:block/block', ambientocclusion: true,
    ...(renderType ? { render_type: renderType } : {}), textures, elements };
  writeFileSync(out('models/block', name), `${JSON.stringify(json, null, 2)}\n`);
  written.add(`${name}.json`);
  return elements.length;
}

const frame = groupElements('frame');
const glass = groupElements('glass');
const airplane = groupElements('airplane');
const propeller = groupElements('propeller');
for (const status of STATUSES) writeModel(`${NAME}_frame_${status}`, frame, { swap: { [SOCKET.ok]: SOCKET[status] } });
writeModel(`${NAME}_glass`, glass, { renderType: 'minecraft:translucent' });
writeModel(`${NAME}_airplane`, airplane);
writeModel(`${NAME}_airplane_rolled`, airplane, { transform: rolled });
writeModel(`${NAME}_propeller`, propeller);
writeModel(`${NAME}_item`, [...frame, ...glass, ...airplane, ...propeller], { renderType: 'minecraft:translucent' });
writeFileSync(out('models/item', NAME), `${JSON.stringify({ parent: `${NS}:block/${NAME}_item` }, null, 2)}\n`);

// --- Blockstate ---------------------------------------------------------------------------------------
// Minecraft applies x first, then y. x=90 turns NORTH into DOWN and UP into NORTH; y=90 turns NORTH into EAST.
const VECTOR = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
const turn = (v, x, y) => {
  const rx = x * Math.PI / 180, ry = y * Math.PI / 180;
  const a = [v[0], v[1] * Math.cos(rx) + v[2] * Math.sin(rx), -v[1] * Math.sin(rx) + v[2] * Math.cos(rx)];
  return [a[0] * Math.cos(ry) - a[2] * Math.sin(ry), a[1], a[0] * Math.sin(ry) + a[2] * Math.cos(ry)].map(Math.round);
};
const same = (a, b) => a.every((v, i) => v === b[i]);
// Each source: where its nose and its fin point before the blockstate turns it. The plain airplane comes first.
const SOURCES = [[`${NAME}_airplane`, 'north', 'up'], [`${NAME}_airplane_rolled`, 'north', 'east']];
const parts = [{ apply: { model: `${NS}:block/${NAME}_glass` } }];
for (const status of STATUSES) parts.push({ when: { status }, apply: { model: `${NS}:block/${NAME}_frame_${status}` } });
let frames = 0;
for (const nose of SIDES) for (const up of SIDES) {
  if (VECTOR[nose].every((v, i) => Math.abs(v) === Math.abs(VECTOR[up][i]))) continue; // collinear: never a state
  let apply = null;
  for (const [model, sourceNose, sourceUp] of SOURCES) for (const x of [0, 90, 180, 270]) for (const y of [0, 90, 180, 270]) {
    if (apply || !same(turn(VECTOR[sourceNose], x, y), VECTOR[nose]) || !same(turn(VECTOR[sourceUp], x, y), VECTOR[up])) continue;
    apply = { model: `${NS}:block/${model}`, ...(x ? { x } : {}), ...(y ? { y } : {}) };
  }
  assert.ok(apply, `no airplane for nose=${nose}, up=${up}`);
  parts.push({ when: { nose, up }, apply });
  frames++;
}
assert.equal(frames, 24);
mkdirSync(new URL('blockstates/', assets), { recursive: true });
writeFileSync(new URL(`blockstates/${NAME}.json`, assets), `${JSON.stringify({ multipart: parts }, null, 2)}\n`);

// Remove the models of previous designs (v3 arms and gimbal).
let removed = 0;
for (const file of readdirSync(new URL('models/block/', assets))) {
  if (file.startsWith(`${NAME}_`) && !written.has(file)) { unlinkSync(new URL(`models/block/${file}`, assets)); removed++; }
}
console.log(`Exported ${written.size} models + item + blockstate; removed ${removed} old files.`);
