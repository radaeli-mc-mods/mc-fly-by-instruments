// Mechanical export from the saved Blockbench source (attitude_reference_unit.bbmodel).
// Writes the blockstate (multipart), the block and item models, and the two PNGs of this mod's textures.
// Enforces docs/visual-design-guide.md v2: integer cuboids, no rotation, 1 texel per unit.
//
// Blockstate: the body is six one-face parts (one per side). Each part has three role models
// (plain, nose, up), chosen by the nose/up properties, times the powered variant. The edges are fixed.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';

const NS = 'aeronautics_fly_by_instruments';
const NAME = 'attitude_reference_unit';
const root = new URL('./', import.meta.url);
const assets = new URL(`../../src/main/resources/assets/${NS}/`, root);
const project = JSON.parse(readFileSync(new URL(`${NAME}.bbmodel`, root), 'utf8'));
assert.equal(project.meta.model_format, 'java_block');

const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const OPPOSITE_AXIS = { north: 'z', south: 'z', east: 'x', west: 'x', up: 'y', down: 'y' };
const ids = project.textures.map(t => `${t.namespace || NS}:${t.folder || 'block'}/${t.name.replace(/\.png$/, '')}`);
const PLAIN = project.textures.findIndex(t => t.name === `${NAME}.png`);
const POWERED = project.textures.findIndex(t => t.name === `${NAME}_powered.png`);
assert.ok(PLAIN >= 0 && POWERED >= 0, 'Plain and powered atlases must exist');
const atlasWidth = project.textures[PLAIN].uv_width;
assert.equal(project.textures[POWERED].uv_width, atlasWidth);

// Textures: embedded PNG sources written as-is.
for (const t of project.textures) {
  assert.ok(t.source?.startsWith('data:image/png;base64,'), `${t.name} must be embedded`);
  const target = new URL(`textures/block/${t.name.replace(/\.png$/, '')}.png`, assets);
  mkdirSync(new URL('./', target), { recursive: true });
  writeFileSync(target, Buffer.from(t.source.split(',')[1], 'base64'));
}

// Source regions (texels) in the atlas. Neutral is the same on every face; the marked roles own
// their region, and the body cube on that face carries it in the source.
const byName = name => project.elements.find(e => e.name === name);
const sourceUv = (cube, side) => cube.faces[side].uv;
const NEUTRAL_TEXELS = sourceUv(byName('face_east'), 'east');
const NOSE_TEXELS = sourceUv(byName('face_north'), 'north');
const UP_TEXELS = sourceUv(byName('face_up'), 'up');
assert.deepEqual(NEUTRAL_TEXELS, [0, 0, 14, 14]);
assert.deepEqual(NOSE_TEXELS, [16, 0, 30, 14]);
assert.deepEqual(UP_TEXELS, [0, 16, 14, 30]);

// Scale texels to the 16-unit model space (a 32 px atlas: 1 texel = 0.5 model units).
const toModelUv = texels => texels.map(v => v * 16 / atlasWidth);

function checkCube(cube) {
  assert.equal(cube.type, 'cube');
  assert.ok(!cube.rotation?.some(v => v !== 0), `${cube.name}: carcass cuboids stay unrotated`);
  for (const v of [...cube.from, ...cube.to]) assert.ok(Number.isInteger(v), `${cube.name}: integer coordinates`);
}

// One source face: 1 texel per unit, same test as the radar exporter.
function checkTexelDensity(cube, side, uv) {
  const size = [0, 1, 2].map(i => cube.to[i] - cube.from[i]);
  const axes = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] }[side];
  const texelsU = Math.abs(uv[2] - uv[0]);
  const texelsV = Math.abs(uv[3] - uv[1]);
  assert.equal(texelsU, size[axes[0]], `${cube.name}.${side}: 1 texel per unit (u)`);
  assert.equal(texelsV, size[axes[1]], `${cube.name}.${side}: 1 texel per unit (v)`);
}

// Body: one face per cube. The face's source texture region is the role it is drawn with.
const bodyCube = side => {
  const cube = byName(`face_${side}`);
  checkCube(cube);
  assert.equal(Object.values(cube.faces).filter(f => f.texture !== null && f.texture !== undefined && f.texture !== false).length, 1,
    `${cube.name}: only one face is drawn`);
  return cube;
};

const bodyFaceModel = (side, role, powered) => {
  const cube = bodyCube(side);
  const texels = role === 'nose' ? NOSE_TEXELS : role === 'up' ? UP_TEXELS : NEUTRAL_TEXELS;
  const uv = toModelUv(texels);
  checkTexelDensity(cube, side, texels);
  const face = { uv, texture: '#0' };
  const element = { name: `face_${side}`, from: cube.from, to: cube.to, faces: { [side]: face } };
  const textures = { particle: ids[PLAIN], '0': ids[powered ? POWERED : PLAIN] };
  return { credit: 'Made with Blockbench for Aeronautics: Fly by Instruments',
    parent: 'minecraft:block/block', ambientocclusion: true, textures, elements: [element] };
};

const edgeElements = project.elements.filter(e => e.name.startsWith('edge_')).map(cube => {
  checkCube(cube);
  const faces = {};
  for (const side of SIDES) {
    const f = cube.faces[side];
    if (!f || f.texture === null || f.texture === undefined || f.texture === false) continue;
    const size = [0, 1, 2].map(i => cube.to[i] - cube.from[i]);
    const t = project.textures[f.texture];
    assert.equal(f.texture, PLAIN, `${cube.name}.${side}: rim uses the plain atlas`);
    const texelsU = Math.abs(f.uv[2] - f.uv[0]);
    const texelsV = Math.abs(f.uv[3] - f.uv[1]);
    const axes = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] }[side];
    assert.equal(texelsU, size[axes[0]], `${cube.name}.${side}: 1 texel per unit (u)`);
    assert.equal(texelsV, size[axes[1]], `${cube.name}.${side}: 1 texel per unit (v)`);
    assert.ok(t, `${cube.name}.${side}: texture`);
    faces[side] = { uv: toModelUv(f.uv), texture: '#0' };
  }
  return { name: cube.name, from: cube.from, to: cube.to, faces };
});
assert.equal(edgeElements.length, 8, 'Eight brass rim bars (four per aro)');

const out = (dir, name) => new URL(`${dir}/${name}.json`, assets);
const writeModel = (name, model) => writeFileSync(out('models/block', name), `${JSON.stringify(model, null, 2)}\n`);

// Role model names: attitude_reference_unit_<side>_<role>[_powered], plus the fixed rim.
const suffix = powered => (powered ? '_powered' : '');
for (const side of SIDES) for (const role of ['plain', 'nose', 'up']) for (const powered of [false, true]) {
  writeModel(`${NAME}_${side}_${role}${suffix(powered)}`, bodyFaceModel(side, role, powered));
}
const rimModel = { credit: 'Made with Blockbench for Aeronautics: Fly by Instruments',
  parent: 'minecraft:block/block', ambientocclusion: true,
  textures: { particle: ids[PLAIN], '0': ids[PLAIN] }, elements: edgeElements };
writeModel(`${NAME}_rim`, rimModel);

// Item and inventory model: the default placement (Nose = NORTH, Up = UP) with all parts in the
// plain atlas and the rim, so the item icon shows the marks of the default placement.
const defaultParts = [
  ...SIDES.map(side => bodyFaceModel(side, side === 'north' ? 'nose' : side === 'up' ? 'up' : 'plain', false).elements[0]),
  ...edgeElements,
];
writeModel(`${NAME}_item`, { credit: 'Made with Blockbench for Aeronautics: Fly by Instruments',
  parent: 'minecraft:block/block', ambientocclusion: true,
  textures: { particle: ids[PLAIN], '0': ids[PLAIN] }, elements: defaultParts });
writeFileSync(out('models/item', NAME), `${JSON.stringify({
  parent: `${NS}:block/${NAME}_item`,
}, null, 2)}\n`);

// Multipart blockstate. A part's "plain" role is every nose and up value except this side's,
// written as a pipe list: nose and up are perpendicular in every valid state, so the cross product
// only matches valid pairs.
const others = side => SIDES.filter(s => s !== side).join('|');
const parts = [{ apply: { model: `${NS}:block/${NAME}_rim` } }];
for (const side of SIDES) {
  for (const powered of ['false', 'true']) {
    const suf = powered === 'true' ? '_powered' : '';
    parts.push({ when: { nose: others(side), up: others(side), powered },
      apply: { model: `${NS}:block/${NAME}_${side}_plain${suf}` } });
    parts.push({ when: { nose: side, powered }, apply: { model: `${NS}:block/${NAME}_${side}_nose${suf}` } });
    parts.push({ when: { up: side, powered }, apply: { model: `${NS}:block/${NAME}_${side}_up${suf}` } });
  }
}
writeFileSync(new URL(`${NAME}.json`, new URL('blockstates/', assets)), `${JSON.stringify({ multipart: parts }, null, 2)}\n`);

console.log(`Exported ${SIDES.length * 6} role models, the rim and the item model; textures: ${ids.join(', ')}.`);
