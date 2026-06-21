// Clawd character design bench — build the crab from primitives on a low-res cell grid,
// auto-outline it, upscale to chunky pixels, and write a PNG to eyeball.
// Usage: node scripts/clawd-design.mjs <out.png>
import { writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ── minimal PNG (RGBA) ──
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const l = Buffer.alloc(4); l.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, "ascii"), data]); const cr = Buffer.alloc(4); cr.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cr]); };
function png(w, h, rgba) {
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  const st = w * 4, raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (st + 1) + 1, y * st, y * st + st);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

// ── low-res grid with shape primitives ──
class LG {
  constructor(w, h) { this.w = w; this.h = h; this.g = Array.from({ length: h }, () => Array(w).fill(" ")); }
  set(x, y, c) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; this.g[y][x] = c; }
  get(x, y) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return " "; return this.g[y][x]; }
  ellipse(cx, cy, rx, ry, c, fill = true) {
    for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
      for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
        const dx = (x - cx) / rx, dy = (y - cy) / ry, d = dx * dx + dy * dy;
        if (fill ? d <= 1 : d <= 1 && d >= 0.55) this.set(x, y, c);
      }
  }
  disc(cx, cy, r, c) { this.ellipse(cx, cy, r, r, c); }
  line(x0, y0, x1, y1, c) { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)); for (let i = 0; i <= n; i++) { const t = n ? i / n : 0; this.set(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c); } }
  // wrap every body cell with an outline color in empty neighbors (4-conn).
  outline(c) {
    const add = [];
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      if (this.g[y][x] !== " ") continue;
      if ([[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]].some((d) => { const v = this.get(x + d[0], y + d[1]); return v !== " " && v !== c; })) add.push([x, y]);
    }
    add.forEach((p) => this.set(p[0], p[1], c));
  }
}

// ── palette ──
const PAL = {
  o: "#56C1DE", h: "#9BDCEE", s: "#2E89A6", L: "#236E86", // shell / highlight / shade / outline
  w: "#FFFFFF", e: "#0E2A33", m: "#236E86",               // eye white / pupil / mouth
};
const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];

// ── build the crab ──
function crab() {
  const g = new LG(32, 24);
  // legs first (so shell/claws sit on top) — 3 each side, fanned out & down with clear gaps
  [[11, 16, 7, 20], [13, 17, 10, 21], [15, 17, 13, 22]].forEach(([x0, y0, x1, y1]) => g.line(x0, y0, x1, y1, "s"));
  [[21, 16, 25, 20], [19, 17, 22, 21], [17, 17, 19, 22]].forEach(([x0, y0, x1, y1]) => g.line(x0, y0, x1, y1, "s"));
  // claws: big pincers out on each side, raised
  const pincer = (cx, cy, dir) => {           // dir +1 = opening faces right (left claw), -1 = faces left
    g.disc(cx, cy, 3.2, "o");                  // claw mass
    // carve the pincer mouth (a wedge) on the inner side
    for (let y = -2; y <= 2; y++) for (let x = 0; x <= 3; x++) {
      if (Math.abs(y) <= (3 - x) - 1.2 && x >= 1) g.set(cx + dir * x, cy + y - 0.3, " ");
    }
    g.disc(cx - dir * 0.4, cy - 2.2, 1.4, "o"); // upper jaw tip
    g.disc(cx - dir * 0.4, cy + 2.2, 1.4, "o"); // lower jaw tip
  };
  pincer(5, 10, +1);
  pincer(27, 10, -1);
  // arms connecting claws to the shell shoulders
  g.line(8, 11, 11, 12, "o"); g.line(8, 12, 11, 13, "o");
  g.line(24, 11, 21, 12, "o"); g.line(24, 12, 21, 13, "o");
  // shell — wide dome
  g.ellipse(16, 12, 7.5, 5, "o");
  g.ellipse(16, 11.5, 7.5, 4.6, "o");
  // highlight (upper-left) and shade (lower-right rim)
  g.disc(12, 9.5, 1.6, "h");
  g.ellipse(16, 12.4, 7.0, 4.6, "s", false); // thin shade ring
  for (let x = 10; x <= 22; x++) if (g.get(x, 9) === "o" && Math.random < 0) {}
  // eyestalks + eyes on top of the shell
  g.line(13, 8, 13, 4, "o"); g.line(19, 8, 19, 4, "o");
  g.disc(13, 3, 1.7, "w"); g.disc(19, 3, 1.7, "w");
  g.set(13, 3, "e"); g.set(13, 2, "e"); g.set(19, 3, "e"); g.set(19, 2, "e");
  // little smile
  g.set(15, 13, "m"); g.set(16, 14, "m"); g.set(17, 13, "m");
  // outline the whole silhouette
  g.outline("L");
  return g;
}

// ── upscale to PNG ──
function render(g, cell, bg = "#FAF9F5") {
  const W = g.w * cell, H = g.h * cell, buf = Buffer.alloc(W * H * 4);
  const b = hx(bg);
  for (let i = 0; i < W * H; i++) { buf[i * 4] = b[0]; buf[i * 4 + 1] = b[1]; buf[i * 4 + 2] = b[2]; buf[i * 4 + 3] = 255; }
  for (let cy = 0; cy < g.h; cy++) for (let cx = 0; cx < g.w; cx++) {
    const k = g.g[cy][cx]; if (k === " ") continue;
    const c = hx(PAL[k] || PAL.o);
    for (let y = 0; y < cell; y++) for (let x = 0; x < cell; x++) {
      const i = ((cy * cell + y) * W + (cx * cell + x)) * 4;
      buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = 255;
    }
  }
  return { W, H, buf };
}

const out = process.argv[2] || "clawd-v2.png";
const { W, H, buf } = render(crab(), 16);
writeFileSync(out, png(W, H, buf));
console.log(`wrote ${out} (${W}x${H})`);
