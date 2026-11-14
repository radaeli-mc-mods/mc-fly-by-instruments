// Documentation renders: an orthographic, flat-lit software rasterizer for the exported block models.
// Reads models/block/*.json and their PNGs from src/main/resources (plus Create and vanilla textures from
// reference/, outside git), places them like blockstates do (x then y rotation about the block centre) and adds
// free quads (the Avionics Wire tube, see wire-scene.mjs). Not a game render: no AO, no shaders, no
// block-entity text. Nearest-neighbour sampling, so 1 texel stays a crisp square.
import { readFileSync, existsSync } from 'node:fs';
import { inflateSync } from 'node:zlib';
import { NS, png } from './bbtools.mjs';

const ROOT = new URL('../../', import.meta.url);
const ASSETS = new URL(`src/main/resources/assets/${NS}/`, ROOT);
const REFERENCE = new URL('reference/create-textures/', ROOT);
// Vanilla textures, copied from the NeoForge client-extra jar (build/moddev/artifacts), outside git.
const VANILLA = new URL('reference/minecraft-textures/', ROOT);

// --- textures ------------------------------------------------------------------------------
function decode(bytes) {
  let i = 8, width, height, type, depth = 8, palette = null, alphas = null; const idat = [];
  while (i < bytes.length) {
    const n = bytes.readUInt32BE(i), kind = bytes.toString('ascii', i + 4, i + 8), data = bytes.subarray(i + 8, i + 8 + n);
    if (kind === 'IHDR') { width = data.readUInt32BE(0); height = data.readUInt32BE(4); depth = data[8]; type = data[9]; }
    if (kind === 'PLTE') palette = data;
    if (kind === 'tRNS') alphas = data;
    if (kind === 'IDAT') idat.push(data);
    i += 12 + n;
  }
  const bpp = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type];
  // Palette and grey images may pack 1, 2 or 4 bits per pixel; filters then work on whole bytes.
  const stride = depth < 8 ? Math.ceil(width * depth / 8) : width * bpp;
  const raw = inflateSync(Buffer.concat(idat)), out = new Uint8ClampedArray(width * height * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)], line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let x = 0; x < stride; x++) {
      const step = depth < 8 ? 1 : bpp;
      const a = x >= step ? line[x - step] : 0, b = prev[x], c = x >= step ? prev[x - step] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      line[x] += [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : c][filter];
    }
    for (let x = 0; x < width; x++) {
      const o = (y * width + x) * 4;
      const s = depth < 8 ? [(line[(x * depth) >> 3] >> (8 - depth - ((x * depth) & 7))) & ((1 << depth) - 1)]
        : line.subarray(x * bpp, x * bpp + bpp);
      if (type === 3) {
        out.set([palette[s[0] * 3], palette[s[0] * 3 + 1], palette[s[0] * 3 + 2], alphas && s[0] < alphas.length ? alphas[s[0]] : 255], o);
      } else if (type === 0) out.set([s[0], s[0], s[0], 255], o);
      else if (type === 4) out.set([s[0], s[0], s[0], s[1]], o);
      else out.set([s[0], s[1], s[2], bpp === 4 ? s[3] : 255], o);
    }
    prev = line;
  }
  // Animated strips: keep the first square frame.
  return { width, height: Math.min(height, width), data: out };
}

const textureCache = new Map();
export function texture(id) {
  if (textureCache.has(id)) return textureCache.get(id);
  const [ns, path] = id.includes(':') ? id.split(':') : ['minecraft', id];
  const name = path.split('/').pop();
  const candidates = ns === NS ? [new URL(`textures/${path}.png`, ASSETS)]
    : [new URL(`${name}.png`, ns === 'create' ? REFERENCE : VANILLA)];
  const file = candidates.find(url => existsSync(url));
  const result = file ? decode(readFileSync(file)) : null;
  if (!result) console.warn(`render-scene: texture ${id} not found, drawn grey`);
  textureCache.set(id, result);
  return result;
}

// --- geometry ------------------------------------------------------------------------------
const rotate = (p, axis, degrees, origin) => {
  const r = degrees * Math.PI / 180, c = Math.cos(r), s = Math.sin(r);
  const [x, y, z] = [p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]];
  const q = axis === 'x' ? [x, y * c - z * s, y * s + z * c] : axis === 'y' ? [x * c + z * s, y, -x * s + z * c]
    : [x * c - y * s, x * s + y * c, z];
  return [q[0] + origin[0], q[1] + origin[1], q[2] + origin[2]];
};

// Corners of each face (from = a, to = b) in the order that matches uv (u1,v1) (u2,v1) (u2,v2) (u1,v2).
export const FACE = {
  up: (a, b) => [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
  down: (a, b) => [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
  north: (a, b) => [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  south: (a, b) => [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
  west: (a, b) => [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
  east: (a, b) => [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
};

/**
 * Quads of a block model placed in the world (block units).
 * instance: { model, at: [x,y,z] block position, x, y (blockstate rotations), offset: [x,y,z] blocks after the
 * rotation (socket shift, module position), yaw (degrees about the block centre before the blockstate, for
 * modules), textures: { key: id } overrides }.
 */
export function modelQuads(instance) {
  // instance.json / instance.lookup: render a model that is not exported yet (straight from a .bbmodel).
  const model = instance.json ?? JSON.parse(readFileSync(new URL(`models/block/${instance.model}.json`, ASSETS), 'utf8'));
  const lookup = instance.lookup ?? texture;
  const textures = { ...model.textures, ...(instance.textures ?? {}) };
  const resolve = ref => { let id = ref; while (id?.startsWith('#')) id = textures[id.slice(1)]; return id; };
  const quads = [];
  for (const element of model.elements) {
    for (const [side, face] of Object.entries(element.faces)) {
      let corners = FACE[side](element.from, element.to);
      if (element.rotation) {
        const { axis, angle, origin } = element.rotation;
        corners = corners.map(p => rotate(p, axis, angle, origin));
      }
      let uv = face.uv ?? defaultUv(side, element.from, element.to);
      let uvs = [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
      for (let r = (face.rotation ?? 0) / 90; r > 0; r--) uvs = [uvs[3], uvs[0], uvs[1], uvs[2]];
      corners = corners.map(p => {
        let q = p;
        if (instance.yaw) q = rotate(q, 'y', -instance.yaw, [8, 8, 8]);
        if (instance.x) q = rotate(q, 'x', -instance.x, [8, 8, 8]);
        if (instance.y) q = rotate(q, 'y', -instance.y, [8, 8, 8]);
        const at = instance.at ?? [0, 0, 0], offset = instance.offset ?? [0, 0, 0];
        return [q[0] / 16 + at[0] + offset[0], q[1] / 16 + at[1] + offset[1], q[2] / 16 + at[2] + offset[2]];
      });
      quads.push({ corners, uvs: uvs.map(([u, v]) => [u / 16, v / 16]), texture: lookup(resolve(face.texture)),
        glow: instance.glow ?? 0 });
    }
  }
  return quads;
}

function defaultUv(side, a, b) {
  if (side === 'up' || side === 'down') return [a[0], a[2], b[0], b[2]];
  if (side === 'north' || side === 'south') return [a[0], 16 - b[1], b[0], 16 - a[1]];
  return [a[2], 16 - b[1], b[2], 16 - a[1]];
}

/**
 * A log lying along `axis` ('x', 'y' or 'z'): the end texture on the two faces across the axis, the bark
 * texture elsewhere with its grain (texture v) running along the axis, as Minecraft's pillar blocks do.
 */
export function logQuads(at, sideId, endId, axis = 'x') {
  const side = texture(sideId), end = texture(endId), k = { x: 0, y: 1, z: 2 }[axis];
  const b = [at[0] + 1, at[1] + 1, at[2] + 1];
  return Object.keys(FACE).map(name => {
    const corners = FACE[name](at, b);
    const normal = { up: 1, down: 1, north: 2, south: 2, west: 0, east: 0 }[name];
    const local = corners.map(c => [c[0] - at[0], c[1] - at[1], c[2] - at[2]]);
    if (normal === k) {
      const [i, j] = [0, 1, 2].filter(n => n !== k);
      return { corners, uvs: local.map(c => [c[i], c[j]]), texture: end };
    }
    const across = [0, 1, 2].find(n => n !== k && n !== normal);
    return { corners, uvs: local.map(c => [c[across], 1 - c[k]]), texture: side };
  });
}

/** A plain cube of one texture on every face (host blocks in a scene). */
export function cubeQuads(at, textureId, size = [1, 1, 1]) {
  const tex = texture(textureId), a = at, b = [at[0] + size[0], at[1] + size[1], at[2] + size[2]];
  return Object.keys(FACE).map(side => {
    const corners = FACE[side](a, b);
    const span = side === 'up' || side === 'down' ? [size[0], size[2]] : side === 'north' || side === 'south'
      ? [size[0], size[1]] : [size[2], size[1]];
    return { corners, uvs: [[0, 0], [span[0], 0], [span[0], span[1]], [0, span[1]]], texture: tex, repeat: true };
  });
}

// --- rasterizer ----------------------------------------------------------------------------
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };

// Minecraft's directional shade: top 1, bottom .5, north/south .8, east/west .6.
const shade = n => n[0] * n[0] * 0.6 + n[2] * n[2] * 0.8 + n[1] * n[1] * (n[1] > 0 ? 1 : 0.5);

/**
 * Renders quads seen from (yaw, pitch) degrees: yaw 0 looks north (towards -Z), positive turns to the east.
 * scale = screen pixels per block. Returns a '#rrggbbaa' grid for bbtools.png(), cropped with a margin.
 */
export function render(quads, { yaw = 225, pitch = 30, scale = 128, margin = 8 } = {}) {
  const ry = yaw * Math.PI / 180, rp = pitch * Math.PI / 180;
  const view = p => {
    const x1 = p[0] * Math.cos(ry) + p[2] * Math.sin(ry), z1 = -p[0] * Math.sin(ry) + p[2] * Math.cos(ry);
    const y2 = p[1] * Math.cos(rp) + z1 * Math.sin(rp), z2 = -p[1] * Math.sin(rp) + z1 * Math.cos(rp);
    // Larger z2 is farther from the viewer; depth is stored so that larger = nearer.
    return [-x1 * scale, -y2 * scale, -z2]; // looking along +z1, +x1 is to the left
  };
  const facing = n => {
    const z1 = -n[0] * Math.sin(ry) + n[2] * Math.cos(ry);
    return n[1] * Math.sin(rp) - z1 * Math.cos(rp);
  };
  const projected = quads.map(q => ({ ...q, screen: q.corners.map(view) }));
  const all = projected.flatMap(q => q.screen);
  const minX = Math.floor(Math.min(...all.map(p => p[0]))) - margin, minY = Math.floor(Math.min(...all.map(p => p[1]))) - margin;
  const width = Math.ceil(Math.max(...all.map(p => p[0]))) + margin - minX;
  const height = Math.ceil(Math.max(...all.map(p => p[1]))) + margin - minY;
  const depth = new Float64Array(width * height).fill(-Infinity);
  const color = new Float64Array(width * height * 4);
  const translucent = [];

  const drawTriangle = (q, ia, ib, ic, blend) => {
    const [a, b, c] = [q.screen[ia], q.screen[ib], q.screen[ic]];
    const [ta, tb, tc] = [q.uvs[ia], q.uvs[ib], q.uvs[ic]];
    const area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    if (Math.abs(area) < 1e-9) return;
    const x0 = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]) - minX)), x1 = Math.min(width - 1, Math.ceil(Math.max(a[0], b[0], c[0]) - minX));
    const y0 = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]) - minY)), y1 = Math.min(height - 1, Math.ceil(Math.max(a[1], b[1], c[1]) - minY));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const px = x + minX + 0.5, py = y + minY + 0.5;
      const w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
      const w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
      const w2 = 1 - w0 - w1;
      if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
      const z = w0 * a[2] + w1 * b[2] + w2 * c[2];
      const i = y * width + x;
      if (z <= depth[i] + 1e-6 * (blend ? -1 : 1)) continue;
      let u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0], v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
      let r = 140, g = 140, bl = 140, al = 255;
      const t = q.texture;
      if (t) {
        if (q.repeat || q.wrapV) { u = q.repeat ? u - Math.floor(u) : u; v -= Math.floor(v); }
        const tx = Math.min(t.width - 1, Math.max(0, Math.floor(u * t.width - 1e-7)));
        const ty = Math.min(t.height - 1, Math.max(0, Math.floor(v * t.height - 1e-7)));
        const o = (ty * t.width + tx) * 4;
        [r, g, bl, al] = [t.data[o], t.data[o + 1], t.data[o + 2], t.data[o + 3]];
      }
      if (al < 8) continue;
      const light = q.glow ? 1 : q.light;
      if (blend !== (al < 250)) continue;
      if (!blend) {
        depth[i] = z;
        color.set([r * light, g * light, bl * light, 255], i * 4);
      } else {
        const k = al / 255, o = i * 4, under = color[o + 3] / 255;
        color[o] = r * light * k + color[o] * (1 - k);
        color[o + 1] = g * light * k + color[o + 1] * (1 - k);
        color[o + 2] = bl * light * k + color[o + 2] * (1 - k);
        color[o + 3] = 255 * (k + under * (1 - k));
      }
    }
  };

  for (const q of projected) {
    const n = norm(cross(sub(q.corners[3], q.corners[0]), sub(q.corners[1], q.corners[0])));
    q.light = 0.25 + 0.75 * shade(n);
    // Back faces are skipped: the tube and the models are closed solids.
    if (!q.twoSided && facing(n) <= 1e-9) continue;
    drawTriangle(q, 0, 1, 2, false); drawTriangle(q, 0, 2, 3, false);
    translucent.push(q);
  }
  // Glass after every opaque pixel, far to near.
  translucent.sort((p, q) => Math.min(...p.screen.map(v => v[2])) - Math.min(...q.screen.map(v => v[2])));
  for (const q of translucent) { drawTriangle(q, 0, 1, 2, true); drawTriangle(q, 0, 2, 3, true); }

  const hex = v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0');
  return Array.from({ length: height }, (_, y) => Array.from({ length: width }, (_, x) => {
    const o = (y * width + x) * 4;
    return `#${hex(color[o])}${hex(color[o + 1])}${hex(color[o + 2])}${hex(color[o + 3])}`;
  }));
}

export const toPng = png;
