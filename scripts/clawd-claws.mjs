// Clawd claw-pose bench (batch 2) — renders the 23 named claw poses on the v1 body to eyeball.
// Usage: node scripts/clawd-claws.mjs <out.png>
import { writeFileSync } from "node:fs";
import zlib from "node:zlib";

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, "ascii"), d]); const cr = Buffer.alloc(4); cr.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cr]); };
function png(w, h, rgba) { const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6; const st = w * 4, raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) rgba.copy(raw, y * (st + 1) + 1, y * st, y * st + st); return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]); }

const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
class Cv { constructor(w, h, bg) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); const b = hx(bg); for (let i = 0; i < w * h; i++) { this.buf[i * 4] = b[0]; this.buf[i * 4 + 1] = b[1]; this.buf[i * 4 + 2] = b[2]; this.buf[i * 4 + 3] = 255; } }
  px(x, y, c, a = 1) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.buf[i] = c[0] * a + this.buf[i] * (1 - a); this.buf[i + 1] = c[1] * a + this.buf[i + 1] * (1 - a); this.buf[i + 2] = c[2] * a + this.buf[i + 2] * (1 - a); this.buf[i + 3] = 255; }
  rect(x, y, w, h, c) { for (let yy = Math.floor(y); yy < Math.ceil(y + h); yy++) for (let xx = Math.floor(x); xx < Math.ceil(x + w); xx++) this.px(xx, yy, c); }
  disc(cx, cy, r, c) { for (let y = -Math.ceil(r); y <= Math.ceil(r); y++) for (let x = -Math.ceil(r); x <= Math.ceil(r); x++) if (x * x + y * y <= r * r) this.px(cx + x, cy + y, c); }
  odisc(cx, cy, r, f, o) { this.disc(cx, cy, r + 1.4, o); this.disc(cx, cy, r, f); }
  line(x0, y0, x1, y1, c, th) { const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1; let e = dx - dy, x = x0, y = y0; const r = (th - 1) / 2; for (;;) { this.rect(x - r, y - r, th, th, c); if (Math.abs(x - x1) < 1 && Math.abs(y - y1) < 1) break; const e2 = 2 * e; if (e2 > -dy) { e -= dy; x += sx; } if (e2 < dx) { e += dx; y += sy; } } }
  blit(s, dx, dy) { for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) { const i = (y * s.w + x) * 4; this.px(dx + x, dy + y, [s.buf[i], s.buf[i + 1], s.buf[i + 2]]); } }
}

const CELL = 8, OX = 2, OY = 5;
const PAL = { o: hx("#56C1DE"), s: hx("#2E89A6"), e: hx("#0E2A33"), w: hx("#FFFFFF"), L: hx("#236E86") };
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
// body = v1 minus the top claws minus baked eyes (claws are posed)
const BODY = [];
GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " " || c === "e") continue; if ((x <= 4 && y <= 6) || (x >= 15 && y <= 6)) continue; BODY.push({ x, y, c }); } });
const colp = (c) => PAL[c] || PAL.o;
const PX = (g) => (OX + g) * CELL, PY = (g) => (OY + g) * CELL;

function body(cv) { for (const p of BODY) cv.rect(PX(p.x), PY(p.y), CELL, CELL, colp(p.c)); }
function eyes(cv, st) { const e = PAL.e; if (st === "closed") { cv.rect(PX(6), PY(8), 3 * CELL, CELL, e); cv.rect(PX(11), PY(8), 3 * CELL, CELL, e); } else { cv.rect(PX(7), PY(8), CELL, 2 * CELL, e); cv.rect(PX(12), PY(8), CELL, 2 * CELL, e); } }

// one articulated claw. q = {raise,open,reach,center,tip,vertical}
function claw(cv, side, q) {
  q = q || {};
  const shX = PX(side < 0 ? 6 : 13), shY = PY(5);
  const centerShift = (q.center || 0) * -side;
  const wGX = (side < 0 ? 4 : 15) + centerShift, wGY = 4.5 - (q.raise || 0);
  const wx = PX(wGX), wy = PY(wGY);
  cv.line(shX, shY, wx, wy, PAL.s, CELL * 0.55);
  cv.line(shX, shY, wx, wy, PAL.o, CELL * 0.34);
  cv.odisc(wx, wy, CELL * 0.7, PAL.o, PAL.L);
  if (q.vertical) {                                  // stacked jaws (shush / shield)
    cv.odisc(wx, wy - CELL * 1.1, CELL * 0.6, PAL.o, PAL.L);
    cv.odisc(wx, wy + CELL * 1.1, CELL * 0.6, PAL.o, PAL.L);
    return;
  }
  const reach = (q.reach != null ? q.reach : 1.15) * side * CELL;
  if (q.tip) {                                       // pointing: extended closed tip
    const tx = wx + reach * 1.7, ty = wy;
    cv.line(wx, wy, tx, ty, PAL.o, CELL * 0.5);
    cv.odisc(tx, ty, CELL * 0.5, PAL.o, PAL.L);
    return;
  }
  const open = q.open != null ? q.open : 0.12;
  cv.odisc(wx + reach, wy - (0.9 + open * 1.1) * CELL, CELL * 0.62, PAL.o, PAL.L);
  cv.odisc(wx + reach, wy + (0.9 + open * 1.1) * CELL, CELL * 0.62, PAL.o, PAL.L);
}

const POSES = [
  ["rest", {}, {}, "dot"], ["claws-up-attentive", { raise: 1.6, open: 0.6 }, { raise: 1.6, open: 0.6 }, "wide"],
  ["cup-ear", {}, { raise: 1.0, center: 2.2, open: 0.9, reach: 0.5 }, "wide"], ["wave", {}, { raise: 2.4, open: 0.7 }, "dot"],
  ["salute", {}, { raise: 1.9, center: 1.5, open: 0.05 }, "dot"], ["knock", {}, { raise: 1.0, reach: 2.0, open: 0.05 }, "dot"],
  ["tap-tap", { raise: -0.6, open: 0.1 }, { raise: -0.3, open: 0.1 }, "dot"], ["point-claw", {}, { raise: 0.6, tip: true, reach: 1.6 }, "dot"],
  ["scratch-head", {}, { raise: 1.6, center: 5.0, open: 0.3 }, "dot"], ["shrug", { raise: 1.0, open: 0.8, reach: 1.4 }, { raise: 1.0, open: 0.8, reach: 1.4 }, "dot"],
  ["facepalm", { raise: -3.0, center: 5.0, open: 0.2 }, {}, "closed"], ["claws-crossed", { raise: 0.0, center: 3.0, open: 0.05 }, { raise: 0.0, center: 3.0, open: 0.05 }, "dot"],
  ["shield-up", { raise: 0.6, center: 3.5, vertical: true }, {}, "dot"], ["thumbs-up", {}, { raise: 1.6, tip: true, reach: 0.4 }, "dot"],
  ["present-platter", { raise: -0.6, open: 0.9, reach: 1.3 }, { raise: -0.6, open: 0.9, reach: 1.3 }, "dot"], ["hold-two-claws", { raise: 0.2, center: 2.2, open: 0.2 }, { raise: 0.2, center: 2.2, open: 0.2 }, "dot"],
  ["reach-up", { raise: 2.6, open: 0.7 }, { raise: 2.6, open: 0.7 }, "wide"], ["drag-claw", { raise: -0.9, reach: 1.8, open: 0.1 }, {}, "dot"],
  ["pinch-zoom", { raise: 0.8, center: -1.2, open: 0.6 }, { raise: 0.8, center: -1.2, open: 0.6 }, "dot"], ["wipe-brow", {}, { raise: 1.6, center: 4.5, open: 0.2 }, "closed"],
  ["stretch", { raise: 2.8, open: 0.5 }, { raise: 2.8, open: 0.5 }, "closed"], ["shush-claw", {}, { raise: -5.5, center: 5.5, vertical: true }, "wink"],
  ["catch-drop", {}, { raise: 1.2, reach: 1.9, open: 0.9 }, "dot"],
];
function wink(cv) { const e = PAL.e; cv.rect(PX(7), PY(8), CELL, 2 * CELL, e); cv.rect(PX(11), PY(8), 3 * CELL, CELL, e); }

const cols = 6, tw = 20 * CELL, th = 16 * CELL, gap = 8, pad = 12, lh = 14;
const rows = Math.ceil(POSES.length / cols);
const sheet = new Cv(pad * 2 + cols * tw + (cols - 1) * gap, pad * 2 + rows * (th + lh) + (rows - 1) * gap, hx("#EFEDE6"));
POSES.forEach((p, i) => {
  const t = new Cv(tw, th, "#FAF9F5");
  body(t);
  if (p[3] === "wink") wink(t); else eyes(t, p[3]);
  claw(t, -1, p[1]); claw(t, +1, p[2]);
  const x = pad + (i % cols) * (tw + gap), y = pad + Math.floor(i / cols) * (th + lh + gap);
  sheet.blit(t, x, y);
  sheet.rect(x - 1, y - 1, tw + 2, 1, hx("#D8CFBD")); sheet.rect(x - 1, y + th, tw + 2, 1, hx("#D8CFBD"));
});
const out = process.argv[2] || "clawd-claws.png";
writeFileSync(out, png(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — ${POSES.length} poses: ${POSES.map((p) => p[0]).join(", ")}`);
