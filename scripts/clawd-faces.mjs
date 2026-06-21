// Clawd expression bench (batch 1) — the full face kit: eyes + mouth + brow on the v1 body.
// Renders a labelled montage of all 21 expressions to PNG to eyeball before porting to Compose.
// Usage: node scripts/clawd-faces.mjs <out.png>
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
  blit(s, dx, dy) { for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) { const i = (y * s.w + x) * 4; this.px(dx + x, dy + y, [s.buf[i], s.buf[i + 1], s.buf[i + 2]]); } }
}

const CELL = 8;
const PAL = { o: hx("#56C1DE"), s: hx("#2E89A6"), e: hx("#0E2A33"), w: hx("#FFFFFF"), t: hx("#E27A8B"), g: hx("#C66A86") };
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
const colp = (c) => PAL[c] || PAL.o;
const R = (cv, gx, gy, c, w = 1, h = 1) => cv.rect(gx * CELL, gy * CELL, w * CELL, h * CELL, c);

function drawBody(cv) { GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " " || c === "e") continue; R(cv, x, y, colp(c)); } }); }

// eye drawn at left slot (lx≈7) and right slot (rx≈12), centered on rows 8-9
function eyes(cv, style) {
  const e = PAL.e, w = PAL.w, slots = [7, 12];
  slots.forEach((sx, idx) => {
    const sign = idx === 0 ? 1 : -1; // inner side
    switch (style) {
      case "dot": R(cv, sx, 8, e, 1, 2); break;
      case "wide": R(cv, sx - 1, 7, w, 2, 2); R(cv, sx, 8, e); break;
      case "half": R(cv, sx, 9, e, 1, 1); break;
      case "squint": R(cv, sx, 8, e, 1, 1); break;
      case "side": R(cv, sx + sign, 8, e, 1, 1); R(cv, sx - sign, 8, w); break;
      case "happy": R(cv, sx - 1, 8, e); R(cv, sx, 7, e); R(cv, sx + 1, 8, e); break;
      case "closed": R(cv, sx - 1, 8, e, 3, 1); break;
      case "star": [[0, -1], [0, 1], [-1, 0], [1, 0], [0, 0]].forEach((d) => R(cv, sx + d[0], 8 + d[1], d[0] === 0 && d[1] === 0 ? w : e)); break;
      case "heart": R(cv, sx - 1, 7, e); R(cv, sx + 1, 7, e); R(cv, sx, 8, e); R(cv, sx, 7, w); break;
      case "wink": if (idx === 0) R(cv, sx, 8, e, 1, 2); else R(cv, sx - 1, 8, e, 3, 1); break;
      case "spiral": [[0, 0], [1, 0], [1, 1], [-1, 1], [-1, -1], [1, -1]].forEach((d) => R(cv, sx + d[0], 8 + d[1], e)); break;
      case "x": [[-1, -1], [1, -1], [0, 0], [-1, 1], [1, 1]].forEach((d) => R(cv, sx + d[0], 8 + d[1], e)); break;
      default: R(cv, sx, 8, e, 1, 2);
    }
  });
}
function mouth(cv, style) {
  const e = PAL.e;
  switch (style) {
    case "flat": R(cv, 9, 11, e, 2, 1); break;
    case "grin": R(cv, 8, 11, e); R(cv, 9, 12, e); R(cv, 10, 12, e); R(cv, 11, 11, e); break;
    case "frown": R(cv, 8, 12, e); R(cv, 9, 11, e); R(cv, 10, 11, e); R(cv, 11, 12, e); break;
    case "open": R(cv, 9, 11, e, 2, 2); break;
    case "tongue": R(cv, 9, 11, e, 2, 1); R(cv, 9, 12, PAL.t, 2, 1); R(cv, 9, 13, PAL.g); break;
  }
}
function brow(cv, style) {
  const e = PAL.e;
  if (style === "up") { R(cv, 6, 6, e); R(cv, 8, 5, e); R(cv, 11, 5, e); R(cv, 13, 6, e); }
  else if (style === "v") { R(cv, 6, 5, e); R(cv, 8, 6, e); R(cv, 11, 6, e); R(cv, 13, 5, e); }
  else if (style === "level") { R(cv, 6, 6, e, 2, 1); R(cv, 11, 6, e, 2, 1); }
}

const FACES = [
  ["neutral-dot", "dot", "flat", null], ["wide-awake", "wide", "flat", null], ["half-lidded", "half", "flat", null],
  ["squint-focus", "squint", "flat", null], ["side-glance", "side", "flat", null], ["happy-arc", "happy", "grin", null],
  ["squeeze-blink", "closed", "grin", null], ["eyes-closed", "closed", "flat", null], ["star-eyes", "star", "grin", null],
  ["heart-eyes", "heart", "grin", null], ["wink", "wink", "grin", null], ["spiral-dizzy", "spiral", "open", null],
  ["x-x-dead", "x", "flat", null], ["worried", "dot", "frown", "up"], ["angry-vbrow", "squint", "flat", "v"],
  ["determined", "squint", "flat", "level"], ["grin", "dot", "grin", null], ["frown", "dot", "frown", null],
  ["mouth-open-o", "dot", "open", null], ["tongue-out (error)", "x", "tongue", null], ["surprised", "wide", "open", null],
];

const cols = 7, tw = 20 * CELL, th = 16 * CELL, gap = 8, pad = 12, labelH = 16;
const rows = Math.ceil(FACES.length / cols);
const sheet = new Cv(pad * 2 + cols * tw + (cols - 1) * gap, pad * 2 + rows * (th + labelH) + (rows - 1) * gap, hx("#EFEDE6"));
FACES.forEach((fc, i) => {
  const t = new Cv(tw, th, "#FAF9F5");
  drawBody(t); eyes(t, fc[1]); mouth(t, fc[2]); if (fc[3]) brow(t, fc[3]);
  const x = pad + (i % cols) * (tw + gap), y = pad + Math.floor(i / cols) * (th + labelH + gap);
  sheet.blit(t, x, y);
  sheet.rect(x - 1, y - 1, tw + 2, 1, hx("#D8CFBD")); sheet.rect(x - 1, y + th, tw + 2, 1, hx("#D8CFBD"));
});
const out = process.argv[2] || "clawd-faces.png";
writeFileSync(out, png(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — ${FACES.length} faces: ${FACES.map((f) => f[0]).join(", ")}`);
