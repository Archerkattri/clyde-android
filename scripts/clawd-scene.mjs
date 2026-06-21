// Clawd unified SCENE engine — composes the full kit (expression + mouth/brow + rich claw poses +
// costume + props + effects + eased motion) and renders REAL catalog scenarios so "any scenario"
// coverage can be eyeballed. Catalog-driven: reads design/clawd/scenario-catalog.json, maps each
// variant's named parts to draw fns (graceful fallback + coverage log), renders a montage.
// Usage: node scripts/clawd-scene.mjs <out.png> [count]
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ── PNG ──
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, "ascii"), d]); const cr = Buffer.alloc(4); cr.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cr]); };
const png = (w, h, rgba) => { const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6; const st = w * 4, raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) rgba.copy(raw, y * (st + 1) + 1, y * st, y * st + st); return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]); };

const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
class Cv { constructor(w, h, bg) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); const b = hx(bg); for (let i = 0; i < w * h; i++) { this.buf[i * 4] = b[0]; this.buf[i * 4 + 1] = b[1]; this.buf[i * 4 + 2] = b[2]; this.buf[i * 4 + 3] = 255; } }
  px(x, y, c, a = 1) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.buf[i] = c[0] * a + this.buf[i] * (1 - a); this.buf[i + 1] = c[1] * a + this.buf[i + 1] * (1 - a); this.buf[i + 2] = c[2] * a + this.buf[i + 2] * (1 - a); this.buf[i + 3] = 255; }
  rect(x, y, w, h, c, a = 1) { for (let yy = Math.floor(y); yy < Math.ceil(y + h); yy++) for (let xx = Math.floor(x); xx < Math.ceil(x + w); xx++) this.px(xx, yy, c, a); }
  disc(cx, cy, r, c, a = 1) { for (let y = -Math.ceil(r); y <= Math.ceil(r); y++) for (let x = -Math.ceil(r); x <= Math.ceil(r); x++) if (x * x + y * y <= r * r) this.px(cx + x, cy + y, c, a); }
  odisc(cx, cy, r, f, o) { this.disc(cx, cy, r + 1.3, o); this.disc(cx, cy, r, f); }
  line(x0, y0, x1, y1, c, th = 1, a = 1) { const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1; let e = dx - dy, x = x0, y = y0; const r = (th - 1) / 2; for (; ;) { this.rect(x - r, y - r, th, th, c, a); if (Math.abs(x - x1) < 1 && Math.abs(y - y1) < 1) break; const e2 = 2 * e; if (e2 > -dy) { e -= dy; x += sx; } if (e2 < dx) { e += dx; y += sy; } } }
  arc(cx, cy, r, a0, a1, c, th = 1, a = 1) { for (let t = a0; t <= a1; t += 0.05) this.rect(cx + Math.cos(t) * r - th / 2, cy + Math.sin(t) * r - th / 2, th, th, c, a); }
  blit(s, dx, dy) { for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) { const i = (y * s.w + x) * 4; this.px(dx + x, dy + y, [s.buf[i], s.buf[i + 1], s.buf[i + 2]]); } }
}

// ── character ──
const CELL = 7, ORX = 4, ORY = 6, TAU = Math.PI * 2;
const SKIN_BLUE = { o: hx("#56C1DE"), s: hx("#2E89A6"), w: hx("#FFFFFF"), e: hx("#0E2A33"), L: hx("#236E86") };
const SKIN_TER = { o: hx("#D97757"), s: hx("#BE5D3E"), w: hx("#FFFFFF"), e: hx("#241F1C"), L: hx("#8A4B33") };
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
const isClaw = (x, y) => (x <= 4 && y <= 6) || (x >= 15 && y <= 6);
const BODY = []; GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " " || c === "e" || isClaw(x, y)) continue; BODY.push({ x, y, c }); } });
const col = (sk, c) => sk[c] || sk.o;
const PX = (g) => (ORX + g) * CELL, PY = (g) => (ORY + g) * CELL;

// ── easing + motion → body transform ──
const E = { inOutSine: (t) => -(Math.cos(Math.PI * t) - 1) / 2, outQuad: (t) => 1 - (1 - t) * (1 - t), inQuad: (t) => t * t, outBack: (t) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2; }, outBounce: (t) => { const n = 7.5625, d = 2.75; if (t < 1 / d) return n * t * t; if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75; if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375; return n * (t -= 2.625 / d) * t + 0.984375; } };
const vol = (s) => ({ sx: s ** -0.55, sy: s });
function bodyTf(motion, p) {
  const breath = E.inOutSine((Math.sin(p * TAU) + 1) / 2);
  let s = 1, tx = 0, ty = 0;
  switch (motion) {
    case "hop": case "pop-in": { const a = p < 0.4 ? E.outQuad(p / 0.4) : 1 - E.outBounce((p - 0.4) / 0.6); ty = -3.0 * a; s = p < 0.14 ? 0.82 + 0.4 * E.outQuad(p / 0.14) : 1 + 0.04 * Math.sin(p * TAU * 3) * (1 - p); break; }
    case "wobble": case "shake-head": tx = 0.9 * Math.exp(-3 * p) * Math.sin(3 * p * TAU); s = 1; break;
    case "sway": case "groove": tx = 1.2 * (E.inOutSine((Math.sin(p * TAU) + 1) / 2) - 0.5); s = 1 - 0.05 * E.inOutSine((Math.sin(p * TAU * 2) + 1) / 2); break;
    case "walk": tx = 1.0 * Math.sin(p * TAU); ty = Math.abs(Math.sin(p * TAU * 2)) * 0.3; s = 1; break;
    case "lean": tx = 0.5; s = 0.98 + 0.04 * breath; ty = -0.2 * breath; break;
    case "tap": case "nod": { const d = (p * 2) % 1 < 0.5 ? E.outQuad(((p * 2) % 1) / 0.5) : 1 - E.inQuad((((p * 2) % 1) - 0.5) / 0.5); s = 1 - 0.05 * d; ty = -0.15 * d; break; }
    default: s = 0.97 + 0.06 * breath; ty = -0.35 * breath; // idle-bob
  }
  const v = vol(s); return { sx: v.sx, sy: v.sy, tx, ty };
}
const PIVX = 9.5, PIVY = 14;
const T = (tf, gx, gy) => [PIVX + (gx - PIVX) * tf.sx + tf.tx, PIVY + (gy - PIVY) * tf.sy + tf.ty];

function drawBody(cv, tf, sk) { const over = CELL * 0.08; for (const c of BODY) { const [X, Y] = T(tf, c.x, c.y); cv.rect(PX(X), PY(Y), tf.sx * CELL + over, tf.sy * CELL + over, col(sk, c.c)); } }
function face(cv, tf, sk, eye, mouth, brow) {
  const over = CELL * 0.08; const cell = (gx, gy, c, w = 1, h = 1) => { const [X, Y] = T(tf, gx, gy); cv.rect(PX(X), PY(Y), w * tf.sx * CELL + over, h * tf.sy * CELL + over, c); };
  for (const [slot, sign] of [[7, 1], [12, -1]]) {
    const sx = slot;
    if (eye === "wide") { cell(sx - 1, 7, sk.w, 2, 2); cell(sx, 8, sk.e); }
    else if (eye === "happy") { cell(sx - 1, 8, sk.e); cell(sx, 7, sk.e); cell(sx + 1, 8, sk.e); }
    else if (eye === "x") {[[-1, -1], [1, -1], [0, 0], [-1, 1], [1, 1]].forEach((d) => cell(sx + d[0], 8 + d[1], sk.e)); }
    else if (eye === "closed") cell(sx - 1, 8, sk.e, 3, 1);
    else if (eye === "squint") cell(sx, 8, sk.e);
    else if (eye === "half") cell(sx, 9, sk.e);
    else if (eye === "side") { cell(sx + sign, 8, sk.e); cell(sx - sign, 8, sk.w); }
    else if (eye === "star") {[[0, -1], [0, 1], [-1, 0], [1, 0], [0, 0]].forEach((d) => cell(sx + d[0], 8 + d[1], d[0] === 0 && d[1] === 0 ? sk.w : sk.e)); }
    else if (eye === "wink") { if (sign === 1) cell(sx, 8, sk.e, 1, 2); else cell(sx - 1, 8, sk.e, 3, 1); }
    else cell(sx, 8, sk.e, 1, 2); // dot
  }
  if (mouth === "flat") cell(9, 11, sk.e, 2, 1);
  else if (mouth === "grin") { cell(8, 11, sk.e); cell(9, 12, sk.e); cell(10, 12, sk.e); cell(11, 11, sk.e); }
  else if (mouth === "frown") { cell(8, 12, sk.e); cell(9, 11, sk.e); cell(10, 11, sk.e); cell(11, 12, sk.e); }
  else if (mouth === "open") cell(9, 11, sk.e, 2, 2);
  else if (mouth === "tongue") { cell(9, 11, sk.e, 2, 1); cell(9, 12, hx("#E27A8B"), 2, 1); }
  if (brow === "up") { cell(6, 6, sk.e); cell(8, 5, sk.e); cell(11, 5, sk.e); cell(13, 6, sk.e); }
  else if (brow === "v") { cell(6, 5, sk.e); cell(8, 6, sk.e); cell(11, 6, sk.e); cell(13, 5, sk.e); }
  else if (brow === "level") { cell(6, 6, sk.e, 2, 1); cell(11, 6, sk.e, 2, 1); }
}
function claw(cv, tf, sk, side, q) {
  q = q || {}; const sh = T(tf, side < 0 ? 6 : 13, 5); const cs = (q.center || 0) * -side;
  const wGX = (side < 0 ? 4 : 15) + cs, wGY = 4.5 - (q.raise || 0);
  const wx = PX(wGX) + side * (q.raise || 0) * 0.18 * CELL, wy = PY(wGY);
  cv.line(PX(sh[0]), PY(sh[1]), wx, wy, sk.s, CELL * 0.55); cv.line(PX(sh[0]), PY(sh[1]), wx, wy, sk.o, CELL * 0.32);
  cv.odisc(wx, wy, CELL * 0.7, sk.o, sk.L);
  if (q.vertical) { cv.odisc(wx, wy - CELL * 1.1, CELL * 0.6, sk.o, sk.L); cv.odisc(wx, wy + CELL * 1.1, CELL * 0.6, sk.o, sk.L); return [wx, wy]; }
  const reach = (q.reach != null ? q.reach : 1.15) * side * CELL;
  if (q.tip) { const tx = wx + reach * 1.7; cv.line(wx, wy, tx, wy, sk.o, CELL * 0.5); cv.odisc(tx, wy, CELL * 0.5, sk.o, sk.L); return [tx, wy]; }
  const open = q.open != null ? q.open : 0.12;
  cv.odisc(wx + reach, wy - (0.9 + open * 1.1) * CELL, CELL * 0.62, sk.o, sk.L);
  cv.odisc(wx + reach, wy + (0.9 + open * 1.1) * CELL, CELL * 0.62, sk.o, sk.L);
  return [wx + reach, wy];
}

// ── costumes / props / effects (subset of the 124-part kit; extends over time) ──
const GOLD = hx("#F2B33D"), GOLDS = hx("#C8862A"), STEEL = hx("#AEB8BE"), STEELD = hx("#5E6970"), GRN = hx("#788C5D"), TER = hx("#D97757"), GRAY = hx("#9AA6AD"), PINK = hx("#E27A8B");
const COSTUMES = {
  hardhat: (cv, tf) => { const [hx0, hy0] = T(tf, 9.5, 3.2), cx = PX(hx0), cy = PY(hy0); cv.disc(cx, cy + CELL * 0.4, CELL * 3.1, GOLDS); cv.disc(cx, cy, CELL * 3.0, GOLD); cv.rect(cx - CELL * 3.6, cy + CELL * 0.4, CELL * 7.2, CELL * 0.8, GOLD); cv.rect(cx - CELL * 0.4, cy - CELL * 2.8, CELL * 0.8, CELL * 1.6, GOLDS); },
  headphones: (cv, tf) => { const [bx, by] = T(tf, 9.5, 3); for (let a = -1.2; a <= 1.2; a += 0.05) cv.rect(PX(bx) + Math.cos(a - Math.PI / 2) * CELL * 3.4, PY(by) + Math.sin(a - Math.PI / 2) * CELL * 3.4, CELL * 0.8, CELL * 0.8, hx("#33414A")); [[4, 9], [15, 9]].forEach(([gx, gy]) => { const [X, Y] = T(tf, gx, gy); cv.odisc(PX(X), PY(Y), CELL * 0.95, hx("#2E89A6"), hx("#1C5566")); }); },
  party: (cv, tf) => { const [hx0, hy0] = T(tf, 9.5, 4), cx = PX(hx0), cy = PY(hy0); for (let i = 0; i < 5; i++) cv.rect(cx - (4 - i), cy - i * CELL * 0.7, (8 - i * 2), CELL * 0.7 + 1, i % 2 ? TER : GOLD); cv.disc(cx, cy - CELL * 3.6, CELL * 0.6, PINK); },
};
const PROPS = {
  wrench: (cv, m, tf, p) => { const ang = -0.9 + (0.5 + 0.5 * Math.sin(p * TAU * 2)) * 0.7; const ex = m[0] + Math.cos(ang) * CELL * 3.0, ey = m[1] + Math.sin(ang) * CELL * 3.0; cv.line(m[0], m[1], ex, ey, STEELD, CELL * 0.7); cv.line(m[0], m[1], ex, ey, STEEL, CELL * 0.42); cv.odisc(ex, ey, CELL * 0.9, STEEL, STEELD); cv.disc(ex, ey, CELL * 0.42, hx("#FAF9F5")); },
  monitor: (cv) => { cv.rect(PX(-2.5), PY(2), CELL * 5.5, CELL * 5, hx("#2A2622")); cv.rect(PX(-2), PY(2.6), CELL * 4.5, CELL * 3.6, hx("#0E2A33")); cv.rect(PX(-1.6), PY(3.2), CELL * 2, CELL * 0.7, SKIN_BLUE.o); cv.rect(PX(-1.6), PY(4.4), CELL * 3, CELL * 0.7, hx("#1C3A45")); },
  phone: (cv) => { cv.rect(PX(-1), PY(8), CELL * 2.2, CELL * 4, hx("#2A2622")); cv.rect(PX(-0.7), PY(8.5), CELL * 1.6, CELL * 3, SKIN_BLUE.e); },
  magnifier: (cv) => { const cx = PX(-1), cy = PY(4); cv.odisc(cx, cy, CELL * 1.6, hx("#0E2A33"), STEELD); cv.disc(cx, cy, CELL * 1.1, hx("#BFE9F5")); cv.line(cx + CELL, cy + CELL, cx + CELL * 2.4, cy + CELL * 2.4, STEELD, CELL * 0.6); },
  map: (cv) => { cv.rect(PX(3), PY(11.5), CELL * 12, CELL * 4, hx("#F3E7C8")); for (let i = 1; i < 3; i++) cv.rect(PX(3 + i * 4), PY(11.5), 1, CELL * 4, hx("#D8C49A")); cv.line(PX(5), PY(13.5), PX(13), PY(12.5), TER, CELL * 0.4); cv.disc(PX(13), PY(12.5), CELL * 0.7, TER); },
  note: (cv, m, tf, p) => { [[1.5, 2], [18, 1.5]].forEach(([gx, gy], i) => { const x = PX(gx), y = PY(gy + Math.sin((p + i * 0.4) * TAU) * 0.6); cv.disc(x, y, CELL * 0.55, i ? TER : SKIN_BLUE.o); cv.rect(x + CELL * 0.4, y - CELL * 1.4, CELL * 0.35, CELL * 1.5, i ? TER : SKIN_BLUE.o); }); },
  envelope: (cv) => { cv.rect(PX(-1.5), PY(8), CELL * 4, CELL * 2.8, hx("#FAF6EC")); cv.line(PX(-1.5), PY(8), PX(0.5), PY(9.4), GRAY, CELL * 0.3); cv.line(PX(2.5), PY(8), PX(0.5), PY(9.4), GRAY, CELL * 0.3); },
  camera: (cv) => { cv.rect(PX(-2), PY(8), CELL * 4, CELL * 3, hx("#2A2622")); cv.odisc(PX(0), PY(9.5), CELL * 1.1, hx("#0E2A33"), STEELD); cv.rect(PX(1.4), PY(7.6), CELL * 0.6, CELL * 0.6, hx("#FFFFFF")); },
  mug: (cv) => { cv.rect(PX(-1.5), PY(8.5), CELL * 2.4, CELL * 2.6, hx("#E8E2D2")); cv.odisc(PX(1.1), PY(9.6), CELL * 0.7, hx("#E8E2D2"), GRAY); for (let i = 0; i < 2; i++) cv.rect(PX(-0.8 + i), PY(6.5 - i), CELL * 0.4, CELL, GRAY, 0.6); },
  keyboard: (cv) => { for (let r = 0; r < 2; r++) for (let c = 0; c < 8; c++) cv.rect(PX(2 + c * 1.7), PY(15 + r * 1.3), CELL * 1.3, CELL, hx("#3A464C")); },
  speechbubble: (cv) => { cv.rect(PX(14), PY(1), CELL * 5, CELL * 3, hx("#FAF6EC")); cv.rect(PX(14.5), PY(4), CELL, CELL, hx("#FAF6EC")); for (let i = 0; i < 3; i++) cv.disc(PX(15.3 + i * 1.3), PY(2.3), CELL * 0.4, SKIN_BLUE.e); },
  pindrop: (cv) => { const x = PX(15), y = PY(2); cv.disc(x, y, CELL * 1.3, TER); cv.disc(x, y, CELL * 0.5, hx("#FFFFFF")); cv.line(x, y + CELL, x, y + CELL * 2.4, TER, CELL * 0.5); },
  wifibars: (cv) => { for (let i = 0; i < 4; i++) cv.rect(PX(14 + i * 1.2), PY(5 - i * 0.8), CELL, CELL * (1 + i * 0.8), SKIN_BLUE.o); },
  book: (cv) => { cv.rect(PX(-1.5), PY(9), CELL * 4, CELL * 3, hx("#BE5D3E")); cv.rect(PX(0.4), PY(9), CELL * 0.2, CELL * 3, hx("#8A4B33")); },
};
function ringG(cv, gx, gy, r, c, a0 = 0, a1 = TAU, a = 1) { for (let t = a0; t <= a1; t += 0.13) cv.rect(PX(gx + Math.cos(t) * r - 0.5), PY(gy + Math.sin(t) * r - 0.5), CELL * 0.6, CELL * 0.6, c, a); }
const EFFECTS = {
  "sound-waves": (cv, tf, p) => { for (let i = 0; i < 3; i++) { const ph = (p + i / 3) % 1; cv.arc(PX(17), PY(5), CELL * (2.5 + ph * 6), -0.7, 0.7, SKIN_BLUE.o, CELL * 0.5, 1 - ph); } },
  "voice-waves-out": (cv, tf, p) => { for (let i = 0; i < 3; i++) { const ph = (p + i / 3) % 1; cv.arc(PX(9.5), PY(12), CELL * (2 + ph * 6), 0.6, 2.54, SKIN_BLUE.o, CELL * 0.5, 1 - ph); } },
  "spinner-dots": (cv, tf, p) => { for (let i = 0; i < 3; i++) cv.disc(PX(15.5 + i * 1.6), PY(0.6), CELL * 0.45, SKIN_BLUE.s, ((Math.floor(p * 6) % 3) === i ? 1 : 0.3)); },
  "loading-ring": (cv, tf, p) => { for (let i = 0; i < 8; i++) { const t = i / 8 * TAU; cv.disc(PX(9.5 + Math.cos(t) * 1.9), PY(0.5 + Math.sin(t) * 1.9 + 1.5), CELL * 0.4, SKIN_BLUE.o, (Math.floor(p * 8) % 8 === i ? 1 : 0.3)); } },
  "progress-bar": (cv, tf, p) => { cv.rect(PX(4), PY(16), CELL * 12, CELL * 0.8, hx("#1C3A45")); cv.rect(PX(4), PY(16), CELL * 12 * (0.3 + 0.6 * p), CELL * 0.8, SKIN_BLUE.o); },
  sparkles: (cv, tf, p) => {[[5, 1], [14, 0], [10, 2]].forEach(([x, y], i) => { const s = (0.5 + 1.0 * Math.abs(Math.sin((p + i * 0.33) * TAU))); cv.line(PX(x) - s * CELL, PY(y), PX(x) + s * CELL, PY(y), hx("#9BDCEE"), CELL * 0.2); cv.line(PX(x), PY(y) - s * CELL, PX(x), PY(y) + s * CELL, hx("#9BDCEE"), CELL * 0.2); }); },
  exclamation: (cv) => { cv.rect(PX(9.5), PY(1), CELL * 0.8, CELL * 1.6, TER); cv.rect(PX(9.5), PY(3), CELL * 0.8, CELL * 0.8, TER); },
  "question-mark": (cv) => { cv.rect(PX(9), PY(1), CELL * 2, CELL * 0.8, SKIN_BLUE.s); cv.rect(PX(10.5), PY(1.5), CELL * 0.8, CELL * 0.8, SKIN_BLUE.s); cv.rect(PX(9.7), PY(2.2), CELL * 0.8, CELL * 0.8, SKIN_BLUE.s); cv.rect(PX(9.7), PY(3.4), CELL * 0.8, CELL * 0.8, SKIN_BLUE.s); },
  "thought-bubbles": (cv) => { cv.disc(PX(14), PY(3), CELL * 0.5, hx("#9BDCEE")); cv.disc(PX(16), PY(1.6), CELL * 0.8, hx("#9BDCEE")); cv.disc(PX(18), PY(0), CELL * 1.1, hx("#9BDCEE")); },
  lightbulb: (cv) => { cv.disc(PX(9.5), PY(1.2), CELL * 1.3, GOLD); cv.rect(PX(9.2), PY(2.4), CELL, CELL * 0.6, GOLDS); for (let k = 0; k < 6; k++) { const t = k / 6 * TAU; cv.rect(PX(9.5 + Math.cos(t) * 2.4 - 0.3), PY(1.2 + Math.sin(t) * 2.4 - 0.3), CELL * 0.5, CELL * 0.5, GOLD, 0.8); } },
  "checkmark-pop": (cv) => {[[8, 2], [8.7, 2.7], [9.4, 2], [10.1, 1], [10.8, 0]].forEach(([x, y]) => cv.rect(PX(x), PY(y), CELL * 0.8, CELL * 0.8, GRN)); },
  "x-cross-pop": (cv) => { for (let k = -2; k <= 2; k++) { cv.rect(PX(9.5 + k), PY(1 + k), CELL * 0.8, CELL * 0.8, TER); cv.rect(PX(9.5 + k), PY(1 - k), CELL * 0.8, CELL * 0.8, TER); } },
  "sweat-drop": (cv) => { cv.disc(PX(13.5), PY(6), CELL * 0.6, SKIN_BLUE.o); cv.rect(PX(13.4), PY(5), CELL * 0.5, CELL * 1.2, SKIN_BLUE.o); },
  fireworks: (cv) => {[[6, 1, SKIN_BLUE.o], [13, 0, TER], [10, 2, GRN]].forEach(([cx, cy, c]) => { for (let k = 0; k < 8; k++) { const t = k / 8 * TAU; cv.line(PX(cx) + Math.cos(t) * CELL, PY(cy) + Math.sin(t) * CELL, PX(cx) + Math.cos(t) * CELL * 2.4, PY(cy) + Math.sin(t) * CELL * 2.4, c, CELL * 0.35, 0.9); } }); },
  confetti: (cv) => {[[5, 1, SKIN_BLUE.o], [8, 3, TER], [11, 0, GRN], [14, 2, SKIN_BLUE.o], [16, 4, TER]].forEach(([x, y, c]) => cv.rect(PX(x), PY(y), CELL * 0.8, CELL * 0.8, c)); },
  "heart-float": (cv) => {[[10, 2, 1], [13, 0, 0.7]].forEach(([x, y, a]) => { cv.rect(PX(x), PY(y + 0.3), CELL * 0.7, CELL * 0.7, PINK, a); cv.rect(PX(x + 0.9), PY(y + 0.3), CELL * 0.7, CELL * 0.7, PINK, a); cv.rect(PX(x + 0.45), PY(y + 1), CELL * 0.7, CELL * 0.7, PINK, a); }); },
  zzz: (cv) => {[[15, 4, 0.7], [16.5, 2.5, 1], [18, 0.7, 1.3]].forEach(([x, y, s]) => { cv.rect(PX(x), PY(y), s * CELL, CELL * 0.4, GRAY); cv.rect(PX(x + s - 0.4), PY(y), CELL * 0.4, s * CELL, GRAY); cv.rect(PX(x), PY(y + s - 0.4), s * CELL, CELL * 0.4, GRAY); }); },
  "glow-pulse": (cv, tf, p) => ringG(cv, 9.5, 9, 9.0 + Math.sin(p * TAU) * 0.5, hx("#9BDCEE")),
  "checklist": (cv) => { for (let i = 0; i < 3; i++) { cv.rect(PX(-1.5), PY(8 + i * 1.3), CELL, CELL, hx("#FAF6EC")); cv.rect(PX(0), PY(8.3 + i * 1.3), CELL * 2.5, CELL * 0.5, GRAY); if (i < 2) cv.line(PX(-1.3), PY(8.5 + i * 1.3), PX(-0.5), PY(8.9 + i * 1.3), GRN, CELL * 0.3); } },
  "scan-line": (cv) => cv.rect(PX(3), PY(9), CELL * 14, CELL * 0.5, hx("#9BDCEE"), 0.9),
  ripple: (cv, tf, p) => { for (let i = 0; i < 2; i++) ringG(cv, 9.5, 14, 3 + i * 2 + (p * 3) % 3, SKIN_BLUE.o, 0, TAU, Math.max(0, 0.7 - i * 0.3)); },
};

// ── catalog variant → scene spec (parse named parts, graceful fallback) ──
const EYE_KEYS = { "wide-awake": "wide", "happy-arc": "happy", "x-x-dead": "x", "eyes-closed": "closed", "squeeze-blink": "closed", "squint-focus": "squint", "side-glance": "side", "half-lidded": "half", "star-eyes": "star", "heart-eyes": "happy", wink: "wink", "spiral-dizzy": "x", "wide-awake": "wide", "neutral-dot": "dot", "determined-squint": "squint" };
const MOUTH_KEYS = { "mouth-flat": "flat", "mouth-grin": "grin", "mouth-frown": "frown", "mouth-open-o": "open", "tongue-out": "tongue", grin: "grin" };
const BROW_KEYS = { "worried-up-brow": "up", "angry-vbrow": "v", "determined-squint": "level", "determined": "level" };
const CLAW_POSES = { rest: {}, "claws-up-attentive": { raise: 1.6, open: 0.6 }, "cup-ear": { raise: 1.0, center: 2.2, open: 0.9, reach: 0.5, side: "R" }, wave: { raise: 2.2, open: 0.7, side: "R" }, salute: { raise: 1.9, center: 1.5, open: 0.05, side: "R" }, "tap-tap": { raise: -0.4, open: 0.1, both: 1 }, "point-claw": { raise: 0.6, tip: 1, reach: 1.6, side: "R" }, "scratch-head": { raise: 1.6, center: 5.0, open: 0.3, side: "R" }, shrug: { raise: 1.0, open: 0.8, reach: 1.4, both: 1 }, facepalm: { raise: -3.0, center: 5.0, open: 0.2, side: "L" }, "claws-crossed": { center: 3.0, open: 0.05, both: 1 }, "shield-up": { raise: 0.6, center: 3.5, vertical: 1, side: "L" }, "thumbs-up-pincer": { raise: 1.6, tip: 1, reach: 0.4, side: "R" }, "reach-up": { raise: 2.5, open: 0.7, both: 1 }, "shush-claw": { raise: -5.5, center: 5.5, vertical: 1, side: "R" }, stretch: { raise: 2.6, open: 0.5, both: 1 }, "hold-prop-two-claws": { raise: 0.2, center: 1.8, open: 0.2, both: 1 } };
const findKey = (str, map) => { const t = (str || "").toLowerCase(); for (const k of Object.keys(map)) if (t.includes(k)) return map[k]; return null; };
const cov = { eye: 0, mouth: 0, claw: 0, prop: 0, propMiss: new Set(), effect: 0, effectMiss: new Set() };

function variantToScene(v) {
  const expr = v.expression || "";
  const eye = findKey(expr, EYE_KEYS) || "dot"; if (findKey(expr, EYE_KEYS)) cov.eye++;
  const mouth = findKey(expr, MOUTH_KEYS) || "flat"; if (findKey(expr, MOUTH_KEYS)) cov.mouth++;
  const brow = findKey(expr, BROW_KEYS) || null;
  const cp = CLAW_POSES[v.claws] || (v.claws ? null : {}); if (CLAW_POSES[v.claws]) cov.claw++;
  let clawL = {}, clawR = {};
  if (cp) { const which = cp.side; const base = { ...cp }; delete base.side; delete base.both; if (cp.both || which === "L") clawL = base; if (cp.both || which === "R" || !which) clawR = base; if (which === "L") clawR = {}; if (which === "R") clawL = {}; }
  const props = (v.props || []).map((p) => { const key = String(p).toLowerCase().replace(/[^a-z]/g, ""); const m = Object.keys(PROPS).find((k) => key.includes(k.replace(/[^a-z]/g, ""))); if (m) cov.prop++; else cov.propMiss.add(p); return m; }).filter(Boolean);
  const accessory = (v.props || []).map((p) => String(p).toLowerCase()).find((p) => /headphone|hard ?hat|helmet|party/.test(p)); const acc = accessory ? (/headphone/.test(accessory) ? "headphones" : /party/.test(accessory) ? "party" : "hardhat") : null;
  const ek = String(v.effect || "").toLowerCase().split(/[\s(]/)[0].replace(/[^a-z]/g, ""); const effect = ek ? Object.keys(EFFECTS).find((k) => { const kc = k.replace(/[^a-z]/g, ""); return kc === ek || kc.startsWith(ek) || ek.startsWith(kc); }) : null; if (effect) cov.effect++; else if (v.effect && v.effect !== "none") cov.effectMiss.add(v.effect);
  const motion = (v.motion || "idle-bob").toLowerCase();
  const skin = /error|warn|consequen|block|fail|x-x/.test(expr + " " + (v.caption || "")) ? SKIN_TER : SKIN_BLUE;
  return { eye, mouth, brow, clawL, clawR, accessory: acc, props, effect, motion, skin };
}

function drawScene(cv, scene, p) {
  const tf = bodyTf(scene.motion, p);
  if (scene.effect && /glow|waves|spinner|loading|progress|thought|zzz/.test(scene.effect)) EFFECTS[scene.effect](cv, tf, p); // behind
  scene.props.forEach((pr) => { if (pr !== "wrench" && pr !== "note") PROPS[pr](cv, null, tf, p); });
  drawBody(cv, tf, scene.skin);
  face(cv, tf, scene.skin, scene.eye, scene.mouth, scene.brow);
  const mr = claw(cv, tf, scene.skin, +1, scene.clawR);
  claw(cv, tf, scene.skin, -1, scene.clawL);
  scene.props.forEach((pr) => { if (pr === "wrench" || pr === "note") PROPS[pr](cv, mr, tf, p); });
  if (scene.accessory) COSTUMES[scene.accessory](cv, tf);
  if (scene.effect && !/glow|waves|spinner|loading|progress|thought|zzz/.test(scene.effect)) EFFECTS[scene.effect](cv, tf, p);
}

// ── render a cross-domain sample of REAL catalog scenarios ──
const cat = JSON.parse(readFileSync(new URL("../design/clawd/scenario-catalog.json", import.meta.url), "utf8")).catalog;
const COUNT = parseInt(process.argv[3] || "30", 10);
const perDomain = Math.max(1, Math.round(COUNT / cat.length));
const picks = [];
for (const d of cat) for (let i = 0; i < perDomain && i < (d.scenarios || []).length; i++) { const sc = d.scenarios[i]; picks.push({ key: sc.key, scene: variantToScene((sc.variants || [])[0] || {}) }); }

const Wc = 28, Hc = 24, fw = Wc * CELL, fh = Hc * CELL, cols = 6, gap = 8, pad = 12;
const rows = Math.ceil(picks.length / cols);
const sheet = new Cv(pad * 2 + cols * fw + (cols - 1) * gap, pad * 2 + rows * fh + (rows - 1) * gap, hx("#EFEDE6"));
picks.forEach((pk, i) => {
  const cv = new Cv(fw, fh, "#FAF9F5");
  drawScene(cv, pk.scene, 0.32);
  const x = pad + (i % cols) * (fw + gap), y = pad + Math.floor(i / cols) * (fh + gap);
  sheet.blit(cv, x, y);
  sheet.rect(x - 1, y - 1, fw + 2, 1, hx("#D8CFBD")); sheet.rect(x - 1, y + fh, fw + 2, 1, hx("#D8CFBD"));
});
const out = process.argv[2] || "clawd-scene.png";
writeFileSync(out, png(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — ${picks.length} scenarios: ${picks.map((p) => p.key).join(", ")}`);
console.log(`coverage: eye=${cov.eye} mouth=${cov.mouth} claw=${cov.claw} prop=${cov.prop} effect=${cov.effect}`);
console.log(`unimplemented props: ${[...cov.propMiss].slice(0, 20).join(", ")}`);
console.log(`unimplemented effects: ${[...cov.effectMiss].slice(0, 20).join(", ")}`);
