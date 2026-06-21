// Clawd effects bench (batch 3) — renders the 26 kit effects on/around the crab to eyeball.
// Usage: node scripts/clawd-effects.mjs <out.png>
import { writeFileSync } from "node:fs";
import zlib from "node:zlib";

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, "ascii"), d]); const cr = Buffer.alloc(4); cr.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cr]); };
function png(w, h, rgba) { const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6; const st = w * 4, raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) rgba.copy(raw, y * (st + 1) + 1, y * st, y * st + st); return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]); }

const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
class Cv { constructor(w, h, bg) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); const b = hx(bg); for (let i = 0; i < w * h; i++) { this.buf[i * 4] = b[0]; this.buf[i * 4 + 1] = b[1]; this.buf[i * 4 + 2] = b[2]; this.buf[i * 4 + 3] = 255; } }
  px(x, y, c, a = 1) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.buf[i] = c[0] * a + this.buf[i] * (1 - a); this.buf[i + 1] = c[1] * a + this.buf[i + 1] * (1 - a); this.buf[i + 2] = c[2] * a + this.buf[i + 2] * (1 - a); this.buf[i + 3] = 255; }
  rect(x, y, w, h, c, a = 1) { for (let yy = Math.floor(y); yy < Math.ceil(y + h); yy++) for (let xx = Math.floor(x); xx < Math.ceil(x + w); xx++) this.px(xx, yy, c, a); }
  disc(cx, cy, r, c, a = 1) { for (let y = -Math.ceil(r); y <= Math.ceil(r); y++) for (let x = -Math.ceil(r); x <= Math.ceil(r); x++) if (x * x + y * y <= r * r) this.px(cx + x, cy + y, c, a); }
  blit(s, dx, dy) { for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) { const i = (y * s.w + x) * 4; this.px(dx + x, dy + y, [s.buf[i], s.buf[i + 1], s.buf[i + 2]]); } }
}

const CELL = 7, OX = 2, OY = 6;
const C = { o: hx("#56C1DE"), s: hx("#2E89A6"), e: hx("#0E2A33"), w: hx("#FFFFFF"), glow: hx("#9BDCEE"), ter: hx("#D97757"), grn: hx("#788C5D"), gray: hx("#9AA6AD"), pink: hx("#E27A8B"), gold: hx("#F2B33D") };
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
const colp = (c) => C[c] || C.o;
const PX = (g) => (OX + g) * CELL, PY = (g) => (OY + g) * CELL, MID = (g) => (OX + g) * CELL + CELL / 2;
const R = (cv, gx, gy, c, w = 1, h = 1, a = 1) => cv.rect(PX(gx), PY(gy), w * CELL, h * CELL, c, a);
const dG = (cv, gx, gy, r, c, a = 1) => cv.disc(MID(gx), (OY + gy) * CELL + CELL / 2, r * CELL, c, a);
function ringG(cv, gx, gy, r, c, a0 = 0, a1 = Math.PI * 2, a = 1) { for (let t = a0; t <= a1; t += 0.13) R(cv, gx + Math.cos(t) * r - 0.5, gy + Math.sin(t) * r - 0.5, c, 0.6, 0.6, a); }
function body(cv) { GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " ") continue; R(cv, x, y, colp(c)); } }); }

// crab center ≈ grid (9.5, 9); top ≈ y 1; feet ≈ y 14
const FX = {
  "glow-pulse": (cv) => ringG(cv, 9.5, 9, 9.2, C.glow),
  "sound-waves": (cv) => { for (let i = 0; i < 3; i++) ringG(cv, 17, 5, 2 + i * 1.6, C.o, -0.7, 0.7, 1 - i * 0.25); },
  "voice-waves-out": (cv) => { for (let i = 0; i < 3; i++) ringG(cv, 9.5, 12, 2 + i * 1.6, C.o, 0.6, 2.54, 1 - i * 0.25); },
  "spinner-dots": (cv) => { [[16, 9, 1], [17.6, 9, 0.55], [19.2, 9, 0.25]].forEach(([x, y, a]) => dG(cv, x, y, 0.5, C.s, a)); },
  "loading-ring": (cv) => { for (let i = 0; i < 8; i++) { const t = i / 8 * Math.PI * 2; dG(cv, 9.5 + Math.cos(t) * 1.9, 2.4 + Math.sin(t) * 1.9, 0.45, C.o, i === 2 ? 1 : 0.3); } },
  "progress-bar": (cv) => { R(cv, 4, 16, hx("#1C3A45"), 12, 0.8); R(cv, 4, 16, C.o, 7, 0.8); },
  "ripple": (cv) => { ringG(cv, 9.5, 14, 4, C.o, 0, Math.PI * 2, 0.7); ringG(cv, 9.5, 14, 6, C.o, 0, Math.PI * 2, 0.3); },
  "scan-line": (cv) => R(cv, 3, 9, C.glow, 14, 0.5, 0.9),
  "sparkles": (cv) => [[5, 1], [14, 0], [10, 2]].forEach(([x, y], i) => { const s = 1.2 - i * 0.2; R(cv, x - s, y, C.glow, 2 * s, 0.4); R(cv, x, y - s, C.glow, 0.4, 2 * s); }),
  "exclamation": (cv) => { R(cv, 9.5, 1, C.ter, 0.8, 1.6); R(cv, 9.5, 3, C.ter, 0.8, 0.8); },
  "question-mark": (cv) => { R(cv, 9, 1, C.s, 2, 0.8); R(cv, 10.5, 1.5, C.s, 0.8, 0.8); R(cv, 9.7, 2.2, C.s, 0.8, 0.8); R(cv, 9.7, 3.4, C.s, 0.8, 0.8); },
  "thought-bubbles": (cv) => { dG(cv, 14, 3, 0.5, C.glow); dG(cv, 16, 1.6, 0.8, C.glow); dG(cv, 18, 0, 1.1, C.glow); },
  "lightbulb": (cv) => { dG(cv, 9.5, 1.2, 1.3, C.gold); R(cv, 9.2, 2.4, hx("#C8862A"), 1, 0.6); for (let k = 0; k < 6; k++) { const t = k / 6 * Math.PI * 2; R(cv, 9.5 + Math.cos(t) * 2.4 - 0.3, 1.2 + Math.sin(t) * 2.4 - 0.3, C.gold, 0.5, 0.5, 0.8); } },
  "checkmark-pop": (cv) => { R(cv, 8, 2, C.grn, 0.8, 0.8); R(cv, 8.7, 2.7, C.grn, 0.8, 0.8); R(cv, 9.4, 2, C.grn, 0.8, 0.8); R(cv, 10.1, 1, C.grn, 0.8, 0.8); R(cv, 10.8, 0, C.grn, 0.8, 0.8); },
  "x-cross-pop": (cv) => { for (let k = -2; k <= 2; k++) { R(cv, 9.5 + k, 1 + k, C.ter, 0.8, 0.8); R(cv, 9.5 + k, 1 - k, C.ter, 0.8, 0.8); } },
  "static": (cv) => { let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff; for (let i = 0; i < 28; i++) R(cv, 5 + rnd() * 9, 7 + rnd() * 6, rnd() > 0.5 ? C.w : C.e, 0.5, 0.5, 0.8); },
  "shake-lines": (cv) => { [3, 16].forEach((x) => { R(cv, x, 7, C.s, 0.4, 1.5); R(cv, x, 9.5, C.s, 0.4, 1.5); }); },
  "sweat-drop": (cv) => { dG(cv, 13.5, 6, 0.6, C.o); R(cv, 13.4, 5, C.o, 0.5, 1.2); },
  "tear-pixel": (cv) => { R(cv, 7, 10.5, C.o, 0.7, 1.4); },
  "fireworks": (cv) => { [[6, 1, C.o], [13, 0, C.ter], [10, 2, C.grn]].forEach(([cx, cy, col]) => { for (let k = 0; k < 8; k++) { const t = k / 8 * Math.PI * 2; R(cv, cx + Math.cos(t) * 2.2 - 0.3, cy + Math.sin(t) * 2.2 - 0.3, col, 0.6, 0.6, 0.9); } }); },
  "confetti": (cv) => { [[5, 1, C.o], [8, 3, C.ter], [11, 0, C.grn], [14, 2, C.o], [16, 4, C.ter], [9, 5, C.grn]].forEach(([x, y, c]) => R(cv, x, y, c, 0.8, 0.8)); },
  "heart-float": (cv) => { [[10, 2, 1], [13, 0, 0.7]].forEach(([x, y, a]) => { R(cv, x, y + 0.3, C.pink, 0.7, 0.7, a); R(cv, x + 0.9, y + 0.3, C.pink, 0.7, 0.7, a); R(cv, x + 0.45, y + 1, C.pink, 0.7, 0.7, a); }); },
  "dust-puff": (cv) => { [[4.5, 15, 1], [15, 15, 1], [9.5, 16, 0.8]].forEach(([x, y, a]) => dG(cv, x, y, 1, C.gray, a * 0.7)); },
  "zzz": (cv) => { [[15, 4, 0.7], [16.5, 2.5, 1], [18, 0.7, 1.3]].forEach(([x, y, s]) => { R(cv, x, y, C.gray, s, 0.4); R(cv, x + s - 0.4, y, C.gray, 0.4, s); R(cv, x, y + s - 0.4, C.gray, s, 0.4); }); },
  "camera-flash": (cv) => { cv.rect(0, 0, cv.w, cv.h, C.w, 0.5); for (let i = 0; i < cv.w * cv.h; i++) { } },
  "steam-wisp": (cv) => { for (let i = 0; i < 2; i++) { const bx = 8 + i * 3; for (let k = 0; k < 4; k++) R(cv, bx + Math.sin(k) * 0.6, 3 - k, C.gray, 0.5, 0.5, 0.7); } },
};

const NAMES = Object.keys(FX);
const cols = 6, tw = 20 * CELL, th = 22 * CELL, gap = 8, pad = 12, lh = 14;
const rows = Math.ceil(NAMES.length / cols);
const sheet = new Cv(pad * 2 + cols * tw + (cols - 1) * gap, pad * 2 + rows * (th + lh) + (rows - 1) * gap, hx("#EFEDE6"));
NAMES.forEach((n, i) => {
  const t = new Cv(tw, th, "#FAF9F5");
  body(t);
  R(t, 7, 8, C.e, 1, 2); R(t, 12, 8, C.e, 1, 2); // dot eyes
  FX[n](t);
  const x = pad + (i % cols) * (tw + gap), y = pad + Math.floor(i / cols) * (th + lh + gap);
  sheet.blit(t, x, y);
  sheet.rect(x - 1, y - 1, tw + 2, 1, hx("#D8CFBD")); sheet.rect(x - 1, y + th, tw + 2, 1, hx("#D8CFBD"));
});
const out = process.argv[2] || "clawd-effects.png";
writeFileSync(out, png(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — ${NAMES.length} effects: ${NAMES.join(", ")}`);
