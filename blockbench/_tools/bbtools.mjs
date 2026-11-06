// Shared helpers for the v2 Blockbench bootstraps and exporters (docs/visual-design-guide.md).
// Pixel art is authored as character maps whose keys come from PALETTE (guide §5 ramps);
// never procedural noise. Create textures are referenced by id only (All Rights Reserved).
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { deflateSync, inflateSync } from 'node:zlib';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';

export const NS = 'aeronautics_fly_by_instruments';
// Blockbench's Java Block format shares ONE UV space (project resolution) for every texture
// and ignores per-texture UV size on export. Smaller textures get their UVs scaled by px().
export const RES = 32;
export const px = (size, uv) => uv.map(v => v * RES / size);

export const PALETTE = {
  // brass, dark -> light
  1: '#592424', 2: '#724731', 3: '#9E6947', 4: '#BF8E55', 5: '#CEA05A', 6: '#E4B763', 7: '#F7CB6C', 8: '#FFEB8C',
  // dark wood
  a: '#301E0E', b: '#3A2411', c: '#442B15', d: '#4F3118', e: '#53381A', f: '#5A3E1E',
  // medium wood
  g: '#553A1F', h: '#5A4424', i: '#614B2E', j: '#70522E', k: '#7A5A34', l: '#82613A', m: '#886539',
  // andesite
  A: '#505351', B: '#60635F', C: '#707570', D: '#828784', E: '#9AA49D', F: '#A8B3AB',
  // industrial iron
  n: '#353438', o: '#3F3E42', p: '#4F4F4F', q: '#595858', v: '#676161', W: '#757171',
  // insulated wire (Avionics Wire only), dark -> light; continues into dark metal w
  0: '#141416', 9: '#1B1B1E', u: '#232326',
  // dark metal
  w: '#2B2B2B', x: '#353535', y: '#3B3B3C', z: '#464647',
  // redstone off / on
  r: '#460000', s: '#500101', t: '#580101', R: '#970000', S: '#AE0000', T: '#CD0000', U: '#FD0000',
  // dark lens + blue glint
  L: '#13161D', M: '#171B23', N: '#20232A', O: '#282C37', Q: '#333846', V: '#464C5F', Y: '#5371C6', Z: '#5059AB',
  // parchment dial, dark -> light, + grey marks
  G: '#D4C2A4', H: '#E0D2AE', I: '#EEE4CE', J: '#F6EDD8', K: '#F9F2DE', P: '#676562', X: '#949188',
};

export function canvas(width, height, fill) {
  return Array.from({ length: height }, () => Array.from({ length: width }, () => PALETTE[fill]));
}
export function blit(target, map, x0, y0) {
  map.forEach((row, y) => [...row].forEach((key, x) => {
    if (key === '.') return; // transparent in the map: keep what is below
    assert.ok(key in PALETTE, `Unknown palette key "${key}"`);
    target[y0 + y][x0 + x] = PALETTE[key];
  }));
}
export function checkMap(name, map, width, height) {
  assert.equal(map.length, height, `${name}: ${map.length} rows, expected ${height}`);
  map.forEach((row, y) => assert.equal(row.length, width, `${name} row ${y}: ${row.length} cols, expected ${width}`));
  return map;
}
export const mirror = map => map.map(row => [...row].reverse().join(''));

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, bytes) {
  const data = Buffer.concat([Buffer.from(type), bytes]);
  const size = Buffer.alloc(4), crc = Buffer.alloc(4);
  size.writeUInt32BE(bytes.length); crc.writeUInt32BE(crc32(data));
  return Buffer.concat([size, data, crc]);
}
export function png(img) {
  const h = img.length, w = img[0].length;
  const rows = Buffer.alloc(h * (w * 4 + 1));
  img.forEach((row, y) => row.forEach((hex, x) => {
    const o = y * (w * 4 + 1) + 1 + x * 4;
    rows[o] = parseInt(hex.slice(1, 3), 16); rows[o + 1] = parseInt(hex.slice(3, 5), 16);
    // '#rrggbbaa' = translucent pixel; only glass may use it (guide §9.4).
    rows[o + 2] = parseInt(hex.slice(5, 7), 16); rows[o + 3] = hex.length === 9 ? parseInt(hex.slice(7, 9), 16) : 255;
  }));
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0); header.writeUInt32BE(h, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header), chunk('IDAT', deflateSync(rows)), chunk('IEND', Buffer.alloc(0))]);
}

// Animated textures are vertical strips of square frames (source height = size x frames).
export function texture(name, size, { namespace = NS, source = null, relativePath = '', particle = false,
                                      frameTime = 1 } = {}) {
  return {
    name: `${name}.png`, path: '', relative_path: relativePath, folder: 'block', namespace,
    id: '', group: '', scope: 0, width: size, height: source ? source.length : size, uv_width: RES, uv_height: RES,
    particle, use_as_default: false, layers_enabled: false, sync_to_project: '', file_format: 'png',
    render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7,
    frame_time: frameTime, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true,
    internal: source !== null, saved: source !== null, uuid: randomUUID(),
    ...(source ? { source: `data:image/png;base64,${png(source).toString('base64')}` } : {}),
  };
}
export const face = (uv, tex) => ({ uv, texture: tex });
// Blockbench 5.2 fails to open a .bbmodel whose cube lacks any of the six faces: faces that are
// not given are written as disabled (texture null), which Blockbench neither renders nor exports.
export const SIDES = ['north', 'east', 'south', 'west', 'up', 'down'];
const allFaces = faces => Object.fromEntries(SIDES.map(side => [side, faces[side] ?? face([0, 0, 0, 0], null)]));
export function cube(name, from, to, faces, color = 0) {
  return {
    name, box_uv: false, render_order: 'default', rescale: false, locked: false, shade: true,
    light_emission: 0, export: true, scope: 0, allow_mirror_modeling: true, from, to, autouv: 0, color,
    origin: [8, 8, 8], faces: allFaces(faces), type: 'cube', uuid: randomUUID(),
  };
}
export function group(name, origin, children, color = 0) {
  return { name, origin, color, uuid: randomUUID(), export: true, mirror_uv: false, isOpen: true,
    locked: false, visibility: true, autouv: 0, children: children.map(c => c.uuid) };
}
export function writeProject(url, { name, elements, groups, textures }) {
  textures.forEach((t, i) => { t.id = String(i); });
  const project = {
    meta: { format_version: '5.0', model_format: 'java_block', box_uv: false },
    name, parent: '', java_block_version: '1.9', ambientocclusion: true, front_gui_light: false,
    visible_box: [1, 1, 0], variable_placeholders: '', multi_file_ruleset: '', variable_placeholder_buttons: [],
    unhandled_root_fields: {}, resolution: { width: RES, height: RES }, elements,
    groups: groups.map(({ children, ...g }) => g),
    outliner: groups.map(g => ({ uuid: g.uuid, isOpen: true, children: g.children })),
    textures,
  };
  writeFileSync(url, JSON.stringify(project));
  return project;
}

// Decodes an 8-bit RGB/RGBA PNG (what Blockbench and png() write) into 'r,g,b,a' strings per pixel.
export function pixels(bytes) {
  let i = 8, width, height, type; const idat = [];
  while (i < bytes.length) {
    const n = bytes.readUInt32BE(i), kind = bytes.toString('ascii', i + 4, i + 8), data = bytes.subarray(i + 8, i + 8 + n);
    if (kind === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); type = data[9]; assert.equal(data[8], 8); }
    if (kind === 'IDAT') idat.push(data);
    i += 12 + n;
  }
  const bpp = { 2: 3, 6: 4 }[type]; assert.ok(bpp, `PNG colour type ${type}`);
  const raw = inflateSync(Buffer.concat(idat)), stride = width * bpp, out = [];
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)], line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? line[x - bpp] : 0, b = prev[x], c = x >= bpp ? prev[x - bpp] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      line[x] += [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : c][filter];
    }
    for (let x = 0; x < width; x++) out.push([...line.subarray(x * bpp, x * bpp + 3), bpp === 4 ? line[x * bpp + 3] : 255].join());
    prev = line;
  }
  return out;
}

// --- Export -----------------------------------------------------------------------------
const AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
/**
 * Exports a saved .bbmodel to models/block/<model>.json (+ variants) and this mod's PNGs.
 * variants: { suffix: { fromTexture: toTexture } } — e.g. { _powered: { terrain_scanner: 'terrain_scanner_powered' } }.
 * Fails on rotated cuboids, fractional coordinates or any face that is not 1 texel per unit.
 * renderType: e.g. 'minecraft:translucent' for glass; writeBase: false when only the variants are runtime models.
 * textures: 'write' (this source owns its PNGs) or 'verify' (PNGs owned by another source must match pixel for pixel).
 * rotatable: { cubeName: angle } — the only cuboids allowed to turn, about X only, by 22.5 degrees (the Minecraft
 * element limit) around the cube's own origin; the sloped lid of the Attitude Indicator wedge (guide §3).
 */
export function exportBlockModel({ projectUrl, assetsUrl, model, variants = {}, renderType = null, writeBase = true,
                                   textures: textureMode = 'write', rotatable = {} }) {
  const project = JSON.parse(readFileSync(projectUrl, 'utf8'));
  assert.equal(project.meta.model_format, 'java_block');
  const base = t => t.name.replace(/\.png$/, '');
  const ids = project.textures.map(t => `${t.namespace || NS}:${t.folder || 'block'}/${base(t)}`);
  const indexOf = name => { const i = project.textures.findIndex(t => base(t) === name); assert.ok(i >= 0, name); return i; };
  for (const t of project.textures) {
    if ((t.namespace || NS) !== NS) continue;
    assert.ok(t.source?.startsWith('data:image/png;base64,'), `${t.name} must be embedded`);
    const target = new URL(`textures/block/${base(t)}.png`, assetsUrl);
    const bytes = Buffer.from(t.source.split(',')[1], 'base64');
    if (textureMode === 'verify') {
      assert.deepEqual(pixels(bytes), pixels(readFileSync(target)), `${t.name} differs from the exported texture`);
      continue;
    }
    mkdirSync(new URL('./', target), { recursive: true });
    writeFileSync(target, bytes);
    // A strip of square frames is an animated texture: Minecraft needs the .mcmeta beside it.
    if (t.height > t.width) {
      const animation = { animation: { frametime: t.frame_time || 1 } };
      writeFileSync(new URL(`${base(t)}.png.mcmeta`, target), `${JSON.stringify(animation, null, 2)}\n`);
    }
  }
  const used = new Set();
  const elements = project.elements.map(c => {
    assert.equal(c.type, 'cube');
    assert.ok(SIDES.every(side => side in c.faces), `${c.name}: all six faces listed (Blockbench cannot open it otherwise)`);
    const turn = c.rotation ?? [0, 0, 0];
    const allowed = rotatable[c.name];
    if (allowed === undefined) assert.ok(turn.every(v => v === 0), `${c.name}: cuboids stay unrotated`);
    else assert.ok(turn[1] === 0 && turn[2] === 0 && turn[0] === allowed && Math.abs(allowed) === 22.5,
      `${c.name}: only a 22.5 degree turn about X (${allowed})`);
    for (const v of [...c.from, ...c.to]) assert.ok(Number.isInteger(v), `${c.name}: integer coordinates`);
    const size = [0, 1, 2].map(i => c.to[i] - c.from[i]);
    const faces = {};
    for (const [side, f] of Object.entries(c.faces)) {
      if (f.texture === null || f.texture === false || f.texture === undefined) continue;
      const t = project.textures[f.texture]; assert.ok(t, `${c.name}.${side}: missing texture`);
      let [gu, gv] = AXES[side].map(a => size[a]);
      if (f.rotation === 90 || f.rotation === 270) [gu, gv] = [gv, gu];
      assert.equal(Math.abs(f.uv[2] - f.uv[0]) * t.width / t.uv_width, gu, `${c.name}.${side}: 1 texel per unit (u)`);
      const frameHeight = t.height > t.width ? t.width : t.height; // UVs of an animated strip address one frame
      assert.equal(Math.abs(f.uv[3] - f.uv[1]) * frameHeight / t.uv_height, gv, `${c.name}.${side}: 1 texel per unit (v)`);
      faces[side] = { uv: f.uv.map((v, i) => v * 16 / (i % 2 ? t.uv_height : t.uv_width)), texture: `#${f.texture}` };
      if (f.rotation) faces[side].rotation = f.rotation;
      used.add(f.texture);
    }
    return { name: c.name, from: c.from, to: c.to,
      ...(allowed === undefined ? {} : { rotation: { angle: allowed, axis: 'x', origin: c.origin } }), faces };
  });
  const particle = project.textures.findIndex(t => t.particle);
  const write = (suffix, swap) => {
    const map = i => (swap[base(project.textures[i])] ? `${NS}:block/${swap[base(project.textures[i])]}` : ids[i]);
    const textures = { particle: map(particle >= 0 ? particle : [...used][0]) };
    for (const i of [...used].sort((a, b) => a - b)) textures[String(i)] = map(i);
    const json = { credit: 'Made with Blockbench for Aeronautics: Fly by Instruments',
      parent: 'minecraft:block/block', ambientocclusion: true, ...(renderType ? { render_type: renderType } : {}),
      textures, elements };
    writeFileSync(new URL(`models/block/${model}${suffix}.json`, assetsUrl), `${JSON.stringify(json, null, 2)}\n`);
  };
  for (const target of Object.values(variants)) for (const to of Object.values(target)) indexOf(to);
  if (writeBase) write('', {});
  for (const [suffix, swap] of Object.entries(variants)) write(suffix, swap);
  return { cubes: elements.length, textures: [...used].map(i => ids[i]) };
}
