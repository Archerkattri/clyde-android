// Clawd QA renderer — draws the mascot/scenes to PNG so the art can be eyeballed off-device.
// Mirrors the Compose ClawdView/engine draw logic (fill cell-rects + simple primitives).
// Usage: node scripts/clawd-render.mjs <out.png>
import { writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ── tiny PNG encoder (RGBA, no interlace) ──────────────────────────────
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0; // 8-bit RGBA
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}

// ── canvas ─────────────────────────────────────────────────────────────
const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), 255];
class Cv {
  constructor(w, h, bg) {
    this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4);
    for (let i = 0; i < w * h; i++) { this.buf[i * 4] = bg[0]; this.buf[i * 4 + 1] = bg[1]; this.buf[i * 4 + 2] = bg[2]; this.buf[i * 4 + 3] = 255; }
  }
  px(x, y, c, alpha = 1) {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4, a = (c[3] / 255) * alpha;
    this.buf[i] = c[0] * a + this.buf[i] * (1 - a);
    this.buf[i + 1] = c[1] * a + this.buf[i + 1] * (1 - a);
    this.buf[i + 2] = c[2] * a + this.buf[i + 2] * (1 - a);
    this.buf[i + 3] = 255;
  }
  rect(x, y, w, h, c, alpha = 1) {
    for (let yy = Math.floor(y); yy < Math.ceil(y + h); yy++)
      for (let xx = Math.floor(x); xx < Math.ceil(x + w); xx++) this.px(xx, yy, c, alpha);
  }
  line(x0, y0, x1, y1, c, th = 1, alpha = 1) {
    const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx - dy, x = x0, y = y0;
    const r = Math.max(0, (th - 1) / 2);
    for (;;) {
      this.rect(x - r, y - r, th, th, c, alpha);
      if (Math.abs(x - x1) < 1 && Math.abs(y - y1) < 1) break;
      const e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x += sx; }
      if (e2 < dx) { err += dx; y += sy; }
    }
  }
  disc(cx, cy, r, c, alpha = 1) {
    for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r) this.px(cx + x, cy + y, c, alpha);
  }
  ring(cx, cy, r, c, th = 1, alpha = 1) {
    for (let a = 0; a < Math.PI * 2; a += 0.06) this.rect(cx + Math.cos(a) * r - th / 2, cy + Math.sin(a) * r - th / 2, th, th, c, alpha);
  }
  arc(cx, cy, r, a0, a1, c, th = 1, alpha = 1) {
    for (let a = a0; a <= a1; a += 0.05) this.rect(cx + Math.cos(a) * r - th / 2, cy + Math.sin(a) * r - th / 2, th, th, c, alpha);
  }
  blit(src, dx, dy) {
    for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
      const i = (y * src.w + x) * 4;
      this.px(dx + x, dy + y, [src.buf[i], src.buf[i + 1], src.buf[i + 2], 255]);
    }
  }
}

// ── Clawd ──────────────────────────────────────────────────────────────
const CELL = 8, COLS = 34, ROWS = 22, OX = 7, OY = 4;
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
const part = (x, y, c) => (y >= 13 ? "LEG" : c === "e" ? "EYE" : x <= 4 && y <= 6 ? "CL" : x >= 15 && y <= 6 ? "CR" : "B");
const CP = [];
GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " " || c === "e") continue; CP.push({ x, y, c, part: part(x, y, c) }); } });
const BLUE = { o: hx("#56C1DE"), s: hx("#2E89A6"), e: hx("#0E2A33"), w: hx("#FFFFFF") };
const TERRA = { o: hx("#D97757"), s: hx("#BE5D3E"), e: hx("#241F1C"), w: hx("#FFFFFF") };
const col = (pal, c) => pal[c] || pal.o;
const CR = (cv, gx, gy, c, w = 1, h = 1, a = 1) => cv.rect(gx * CELL, gy * CELL, w * CELL, h * CELL, c, a);

function eyes(cv, pal, o, dx, dy) {
  const e = pal.e, wt = hx("#FFFFFF"), st = o.eye || "dot";
  const L = (gx, gy, c, w, h) => CR(cv, OX + gx + dx, OY + gy + dy, c, w, h);
  if (st === "dot") { L(7, 8, e, 1, 2); L(12, 8, e, 1, 2); }
  else if (st === "wide") { L(6, 7, wt, 2, 2); L(11, 7, wt, 2, 2); L(7, 8, e); L(12, 8, e); }
  else if (st === "happy") { [[6, 8], [7, 7], [8, 8], [11, 8], [12, 7], [13, 8]].forEach((a) => L(a[0], a[1], e)); }
  else if (st === "x") { [[6, 7], [8, 7], [7, 8], [6, 9], [8, 9], [11, 7], [13, 7], [12, 8], [11, 9], [13, 9]].forEach((a) => L(a[0], a[1], e)); }
}
function crab(cv, pal, o = {}) {
  const dx0 = o.dx || 0, dy0 = (o.dy || 0) + (o.hop || 0);
  for (const p of CP) {
    let dx = dx0, dy = dy0;
    if (p.part === "CL") { dx += o.cLdx || 0; dy += o.cLdy || 0; }
    else if (p.part === "CR") { dx += o.cRdx || 0; dy += o.cRdy || 0; }
    else if (p.part === "LEG") dx += o.legdx || 0;
    CR(cv, OX + p.x + dx, OY + p.y + dy, col(pal, p.c));
  }
  eyes(cv, pal, o, dx0, dy0);
  if (o.tongue) CR(cv, OX + 9 + dx0, OY + 12 + dy0, hx("#E27A8B"), 2, 1);
}
const C = (g) => g * CELL;

// ── scenes (representative frame each) ─────────────────────────────────
function scene(name) {
  const cv = new Cv(COLS * CELL, ROWS * CELL, hx("#FAF9F5"));
  const blue = hx("#56C1DE"), ter = hx("#D97757"), grn = hx("#788C5D"), deep = hx("#2E89A6");
  if (name === "v1") { crab(cv, BLUE, { eye: "dot" }); }
  else if (name === "listening") {
    crab(cv, BLUE, { eye: "wide", cRdy: -2.4, cRdx: -1.2 });
    for (let i = 0; i < 3; i++) cv.arc(C(OX + 21), C(OY + 4), CELL * (2.5 + i * 2.4), -0.7, 0.7, blue, CELL * 0.55, 1 - i * 0.28);
  } else if (name === "working") {
    CR(cv, 0, 2, hx("#2A2622"), 6, 7); CR(cv, 1, 3, hx("#0E2A33"), 4, 4); CR(cv, 1, 4, blue, 2, 1);
    CR(cv, 1, 6, hx("#1C3A45"), 4, 1); CR(cv, 1, 6, blue, 2.4, 1); CR(cv, 4, 4, blue); CR(cv, 2, 9, hx("#2A2622"), 2, 1);
    crab(cv, BLUE, { eye: "dot", cLdy: 1.8, cRdy: 1 });
  } else if (name === "navigating") {
    cv.line(C(7), C(OY + 2), C(16), C(OY + 0.5), ter, CELL * 0.5);
    cv.line(C(16), C(OY + 0.5), C(OX + 19), C(OY - 0.5), ter, CELL * 0.5);
    cv.ring(C(4), C(OY + 2), CELL * 2.2, deep, CELL * 0.4);
    const ang = -Math.PI / 2 + 0.3; cv.line(C(4), C(OY + 2), C(4) + Math.cos(ang) * CELL * 2.2, C(OY + 2) + Math.sin(ang) * CELL * 2.2, ter, CELL * 0.5);
    cv.disc(C(OX + 19), C(OY - 1), CELL * 1.5, hx("#BE5D3E")); cv.disc(C(OX + 19), C(OY - 1), CELL * 0.6, hx("#FFFFFF"));
    crab(cv, BLUE, { eye: "wide", dx: 0.5, legdx: -0.4, dy: 0.3 });
  } else if (name === "success") {
    const burst = (cx, cy, c, p) => { const r = p * CELL * 5; for (let i = 0; i < 8; i++) { const a = (i / 8) * Math.PI * 2; cv.line(cx + Math.cos(a) * r * 0.35, cy + Math.sin(a) * r * 0.35, cx + Math.cos(a) * r, cy + Math.sin(a) * r, c, CELL * 0.4, 1 - p); } };
    burst(C(OX + 5), C(OY - 1), blue, 0.55); burst(C(OX + 14), C(OY), ter, 0.4); burst(C(OX + 10), C(OY - 2.5), grn, 0.7);
    crab(cv, BLUE, { eye: "happy", cLdy: -3, cRdy: -3, hop: -0.9 });
  } else if (name === "error") {
    crab(cv, TERRA, { eye: "x", tongue: true, dx: 0.6 });
    CR(cv, OX + 17, OY - 2, ter, 1, 2); CR(cv, OX + 17, OY + 1, ter, 1, 1); CR(cv, OX + 4, OY + 2, blue);
  } else if (name === "thinking") {
    crab(cv, BLUE, { eye: "dot", cLdy: 4, cLdx: 4 });
    for (let i = 0; i < 3; i++) CR(cv, OX + 15 + i * 2, OY - 1, deep, 1, 1, i < 2 ? 1 : 0.18);
  }
  return cv;
}

// ── contact sheet ──────────────────────────────────────────────────────
const NAMES = ["v1", "listening", "working", "navigating", "success", "error", "thinking"];
const cols = 4, gap = 10, pad = 12, tw = COLS * CELL, th = ROWS * CELL;
const rows = Math.ceil(NAMES.length / cols);
const sheet = new Cv(pad * 2 + cols * tw + (cols - 1) * gap, pad * 2 + rows * th + (rows - 1) * gap, hx("#EFEDE6"));
NAMES.forEach((n, i) => {
  const cx = pad + (i % cols) * (tw + gap), cy = pad + Math.floor(i / cols) * (th + gap);
  const t = scene(n);
  for (let x = -1; x <= tw; x++) { t.px(x, -1, hx("#E8E6DC")); }
  sheet.blit(t, cx, cy);
  sheet.rect(cx - 1, cy - 1, tw + 2, 1, hx("#D8CFBD")); sheet.rect(cx - 1, cy + th, tw + 2, 1, hx("#D8CFBD"));
  sheet.rect(cx - 1, cy - 1, 1, th + 2, hx("#D8CFBD")); sheet.rect(cx + tw, cy - 1, 1, th + 2, hx("#D8CFBD"));
});
const out = process.argv[2] || "clawd-sheet.png";
writeFileSync(out, encodePng(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — order: ${NAMES.join(", ")}`);
