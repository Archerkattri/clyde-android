// Clawd articulated-rig bench — v1 character, but ALIVE: squash & stretch, open/close claws,
// costume / held-prop / environment / effect layers. Renders motion filmstrips to PNG to eyeball.
// Usage: node scripts/clawd-rig.mjs <out.png>
import { writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ── PNG (RGBA) ──
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, "ascii"), d]); const cr = Buffer.alloc(4); cr.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cr]); };
function png(w, h, rgba) { const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6; const st = w * 4, raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) rgba.copy(raw, y * (st + 1) + 1, y * st, y * st + st); return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]); }

const hx = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
class Cv {
  constructor(w, h, bg) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); const b = hx(bg); for (let i = 0; i < w * h; i++) { this.buf[i * 4] = b[0]; this.buf[i * 4 + 1] = b[1]; this.buf[i * 4 + 2] = b[2]; this.buf[i * 4 + 3] = 255; } }
  px(x, y, c, a = 1) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.buf[i] = c[0] * a + this.buf[i] * (1 - a); this.buf[i + 1] = c[1] * a + this.buf[i + 1] * (1 - a); this.buf[i + 2] = c[2] * a + this.buf[i + 2] * (1 - a); this.buf[i + 3] = 255; }
  rect(x, y, w, h, c, a = 1) { for (let yy = Math.floor(y); yy < Math.ceil(y + h); yy++) for (let xx = Math.floor(x); xx < Math.ceil(x + w); xx++) this.px(xx, yy, c, a); }
  disc(cx, cy, r, c, a = 1) { for (let y = -Math.ceil(r); y <= Math.ceil(r); y++) for (let x = -Math.ceil(r); x <= Math.ceil(r); x++) if (x * x + y * y <= r * r) this.px(cx + x, cy + y, c, a); }
  odisc(cx, cy, r, fill, ol) { this.disc(cx, cy, r + 1.4, ol); this.disc(cx, cy, r, fill); }
  line(x0, y0, x1, y1, c, th = 1, a = 1) { const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1; let e = dx - dy, x = x0, y = y0; const r = (th - 1) / 2; for (;;) { this.rect(x - r, y - r, th, th, c, a); if (Math.abs(x - x1) < 1 && Math.abs(y - y1) < 1) break; const e2 = 2 * e; if (e2 > -dy) { e -= dy; x += sx; } if (e2 < dx) { e += dx; y += sy; } } }
  blit(s, dx, dy) { for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) { const i = (y * s.w + x) * 4; this.px(dx + x, dy + y, [s.buf[i], s.buf[i + 1], s.buf[i + 2]]); } }
}

// ── palette + character ──
const CELL = 10, ORX = 2.5, ORY = 5;
const PAL = { o: hx("#56C1DE"), s: hx("#2E89A6"), w: hx("#FFFFFF"), e: hx("#0E2A33"), L: hx("#236E86") };
const GRID = [
  "                    ", "  oo            oo  ", " oooo          oooo ", " oooo          oooo ",
  " ooso  oooooo  osoo ", "  oo  oooooooo  oo  ", "  o  oooooooooo  o  ", "    owoooooooooo    ",
  "   ooooeooooeoooo   ", "   ooooeooooeoooo   ", "   oooooooooooooo   ", "   oooooooooooooo   ",
  "    ssoooooooooo    ", "    o o o  o o o    ", "   o   o    o   o   ", "                    ",
];
const isClaw = (x, y) => (x <= 4 && y <= 6) || (x >= 15 && y <= 6);
// body = v1 minus the static top claws minus the baked eyes (eyes/claws are now rig parts)
const BODY = [];
GRID.forEach((row, y) => { for (let x = 0; x < row.length; x++) { const c = row[x]; if (c === " " || c === "e" || isClaw(x, y)) continue; BODY.push({ x, y, c }); } });
const colp = (c) => PAL[c] || PAL.o;
const PXX = (gx) => (ORX + gx) * CELL, PXY = (gy) => (ORY + gy) * CELL;

// body transform (squash/stretch about a pivot + translate)
function tf(p) { return { px: 9.5, py: 14, sx: p.sx ?? 1, sy: p.sy ?? 1, tx: p.tx ?? 0, ty: p.ty ?? 0 }; }
function T(t, gx, gy) { return [t.px + (gx - t.px) * t.sx + t.tx, t.py + (gy - t.py) * t.sy + t.ty]; }

function drawBody(cv, t) {
  for (const p of BODY) { const [X, Y] = T(t, p.x, p.y); cv.rect(PXX(X), PXY(Y), t.sx * CELL + 0.8, t.sy * CELL + 0.8, colp(p.c)); }
}
function drawEyes(cv, t, style) {
  const e = PAL.e, wt = PAL.w;
  const cell = (gx, gy, c, w = 1, h = 1) => { const [X, Y] = T(t, gx, gy); cv.rect(PXX(X), PXY(Y), w * t.sx * CELL + 0.8, h * t.sy * CELL + 0.8, c); };
  if (style === "happy") { [[6, 8], [7, 7], [8, 8], [11, 8], [12, 7], [13, 8]].forEach((a) => cell(a[0], a[1], e)); }
  else if (style === "wide") { cell(6, 7, wt, 2, 2); cell(11, 7, wt, 2, 2); cell(7, 8, e); cell(12, 8, e); }
  else if (style === "x") { [[6, 7], [8, 7], [7, 8], [6, 9], [8, 9], [11, 7], [13, 7], [12, 8], [11, 9], [13, 9]].forEach((a) => cell(a[0], a[1], e)); }
  else if (style === "closed") { cell(6, 8, e, 2, 1); cell(11, 8, e, 2, 1); }
  else { cell(7, 8, e, 1, 2); cell(12, 8, e, 1, 2); }
}

// articulated pincer claw: open 0=closed .. 1=wide; raise lifts it; reach points outward
function drawClaw(cv, t, anchorGx, anchorGy, side, open, raise) {
  const [sx, sy] = T(t, side < 0 ? 6 : 13, 5);                 // shoulder on the body
  const [ax, ay] = T(t, anchorGx, anchorGy);
  const wx = PXX(ax), wy = PXY(ay) - raise * CELL;
  cv.line(PXX(sx), PXY(sy), wx, wy, PAL.s, CELL * 0.55);       // short stubby arm
  cv.line(PXX(sx), PXY(sy), wx, wy, PAL.o, CELL * 0.32);
  const reach = side * 1.15 * CELL;
  cv.odisc(wx, wy, CELL * 0.7, PAL.o, PAL.L);                  // knuckle/wrist
  cv.odisc(wx + reach, wy - (0.9 + open * 1.1) * CELL, CELL * 0.62, PAL.o, PAL.L); // upper jaw
  cv.odisc(wx + reach, wy + (0.9 + open * 1.1) * CELL, CELL * 0.62, PAL.o, PAL.L); // lower jaw
  return [wx + reach, wy]; // mouth point (for held props)
}

// ── costume / prop / environment / effect ──
function hardHat(cv, t) {
  const [hx0, hy0] = T(t, 9.5, 3.2); const cx = PXX(hx0), cy = PXY(hy0);
  cv.disc(cx, cy + CELL * 0.4, CELL * 3.1, hx("#C8862A")); // dome shade
  cv.disc(cx, cy, CELL * 3.0, hx("#F2B33D"));
  cv.rect(cx - CELL * 3.6, cy + CELL * 0.4, CELL * 7.2, CELL * 0.8, hx("#F2B33D")); // brim
  cv.rect(cx - CELL * 0.4, cy - CELL * 2.8, CELL * 0.8, CELL * 1.6, hx("#C8862A")); // ridge
}
function headphones(cv, t) {
  for (let a = -1.2; a <= 1.2; a += 0.05) cv.rect(PXX(T(t, 9.5, 3)[0]) + Math.cos(a - Math.PI / 2) * CELL * 3.4, PXY(T(t, 9.5, 3)[1]) + Math.sin(a - Math.PI / 2) * CELL * 3.4, CELL * 0.8, CELL * 0.8, hx("#33414A")); // band
  [[4, 9], [15, 9]].forEach(([gx, gy]) => { const [X, Y] = T(t, gx, gy); cv.odisc(PXX(X), PXY(Y), CELL * 0.95, hx("#2E89A6"), hx("#1C5566")); });
}
function wrench(cv, mx, my, ang) {
  const ex = mx + Math.cos(ang) * CELL * 3.1, ey = my + Math.sin(ang) * CELL * 3.1;
  cv.line(mx, my, ex, ey, hx("#5E6970"), CELL * 0.7);
  cv.line(mx, my, ex, ey, hx("#AEB8BE"), CELL * 0.42);
  cv.odisc(ex, ey, CELL * 0.9, hx("#AEB8BE"), hx("#5E6970"));
  cv.disc(ex, ey, CELL * 0.42, hx("#FAF9F5")); // open jaw of the wrench
}
function boltOnGround(cv) { const x = PXX(18.5), y = PXY(14.5); cv.odisc(x, y, CELL * 0.8, hx("#9AA6AD"), hx("#5E6970")); cv.disc(x, y, CELL * 0.3, hx("#3A4348")); }
function bang(cv, t) { const [X, Y] = T(t, 9.5, -2); cv.rect(PXX(X) - CELL * 0.3, PXY(Y), CELL * 0.8, CELL * 1.6, hx("#D97757")); cv.rect(PXX(X) - CELL * 0.3, PXY(Y) + CELL * 2.0, CELL * 0.8, CELL * 0.8, hx("#D97757")); }
function note(cv, gx, gy, c) { const x = PXX(gx), y = PXY(gy); cv.disc(x, y, CELL * 0.55, c); cv.rect(x + CELL * 0.4, y - CELL * 1.4, CELL * 0.35, CELL * 1.5, c); }

// ── a frame = pose params ──
function frame(W, H, pose) {
  const cv = new Cv(W, H, "#FAF9F5");
  const t = tf(pose);
  if (pose.env === "bolt") boltOnGround(cv);
  if (pose.effect === "notes") { note(cv, 1.5, 2 + Math.sin(pose.p * 6.28) * 0.6, hx("#56C1DE")); note(cv, 18, 1.5 + Math.cos(pose.p * 6.28) * 0.6, hx("#D97757")); }
  drawBody(cv, t);
  drawEyes(cv, t, pose.eye || "dot");
  // claws
  const ml = drawClaw(cv, t, 4, 4.5, -1, pose.openL ?? 0, pose.raiseL ?? 0);
  const mr = drawClaw(cv, t, 15, 4.5, +1, pose.openR ?? 0, pose.raiseR ?? 0);
  if (pose.prop === "wrench") wrench(cv, mr[0], mr[1], pose.propAng ?? -0.6);
  if (pose.accessory === "hardhat") hardHat(cv, t);
  if (pose.accessory === "headphones") headphones(cv, t);
  if (pose.effect === "bang") bang(cv, t);
  return cv;
}

// ── motions (p in [0,1)) ──
const TAU = Math.PI * 2;
const ease = (a, b, x) => a + (b - a) * x;
const MOTIONS = {
  idle: (p) => ({ sx: 1 - 0.035 * Math.sin(p * TAU), sy: 1 + 0.05 * Math.sin(p * TAU), ty: -0.3 * Math.sin(p * TAU), raiseL: 0.25 + 0.12 * Math.sin(p * TAU), raiseR: 0.25 + 0.12 * Math.sin(p * TAU + 0.6), openL: 0.12, openR: 0.12, eye: p > 0.5 && p < 0.58 ? "closed" : "dot" }),
  jump: (p) => {
    let sx = 1, sy = 1, ty = 0;
    if (p < 0.18) { const k = p / 0.18; sx = ease(1, 1.18, k); sy = ease(1, 0.78, k); ty = ease(0, 0.7, k); }       // anticipate
    else if (p < 0.5) { const k = (p - 0.18) / 0.32; sx = ease(1.18, 0.9, k); sy = ease(0.78, 1.22, k); ty = ease(0.7, -4.2, k); } // launch + rise
    else if (p < 0.66) { const k = (p - 0.5) / 0.16; sy = ease(1.22, 1.05, k); ty = ease(-4.2, -4.2, k); }          // apex
    else if (p < 0.86) { const k = (p - 0.66) / 0.2; sx = ease(0.9, 1.2, k); sy = ease(1.05, 0.78, k); ty = ease(-4.2, 0.5, k); } // fall + land
    else { const k = (p - 0.86) / 0.14; sx = ease(1.2, 1, k); sy = ease(0.78, 1, k); ty = ease(0.5, 0, k); }        // recover
    const air = ty < -0.5;
    return { sx, sy, ty, eye: air ? "wide" : "dot", raiseL: air ? 2.2 : 0, raiseR: air ? 2.2 : 0, openL: air ? 0.9 : 0.1, openR: air ? 0.9 : 0.1, effect: p > 0.45 && p < 0.66 ? "bang" : null };
  },
  wave: (p) => { const o = 0.5 + 0.5 * Math.sin(p * TAU * 2); return { eye: "happy", raiseL: 1.6, raiseR: 1.6, openL: o, openR: 1 - o, sy: 1 + 0.03 * Math.sin(p * TAU * 2) }; },
  work: (p) => { const tap = 0.5 + 0.5 * Math.sin(p * TAU * 2); return { eye: "dot", accessory: "hardhat", prop: "wrench", env: "bolt", raiseR: 0.4 + tap * 0.7, propAng: -0.9 + tap * 0.7, openR: 0.15, openL: 0.4 + 0.4 * Math.sin(p * TAU * 2 + 1), raiseL: 0.6, sy: 1 - 0.04 * tap, sx: 1 + 0.04 * tap }; },
  groove: (p) => ({ eye: "happy", accessory: "headphones", effect: "notes", tx: 1.3 * Math.sin(p * TAU), sx: 1 + 0.05 * Math.sin(p * TAU * 2), sy: 1 - 0.05 * Math.sin(p * TAU * 2), raiseL: 0.8 + 0.5 * Math.sin(p * TAU), raiseR: 0.8 - 0.5 * Math.sin(p * TAU), openL: 0.6, openR: 0.6, p }),
};

// ── filmstrip ──
const ORDER = ["idle", "jump", "wave", "work", "groove"];
const FR = 8, Wc = 21, Hc = 22, fw = Wc * CELL, fh = Hc * CELL, gap = 6, pad = 10;
const sheetW = pad * 2 + FR * fw + (FR - 1) * gap;
const sheetH = pad * 2 + ORDER.length * fh + (ORDER.length - 1) * gap;
const sheet = new Cv(sheetW, sheetH, "#EFEDE6");
ORDER.forEach((m, r) => {
  for (let i = 0; i < FR; i++) {
    const pose = MOTIONS[m](i / FR);
    const cv = frame(fw, fh, pose);
    const x = pad + i * (fw + gap), y = pad + r * (fh + gap);
    sheet.blit(cv, x, y);
    sheet.rect(x - 1, y - 1, fw + 2, 1, hx("#D8CFBD")); sheet.rect(x - 1, y + fh, fw + 2, 1, hx("#D8CFBD"));
  }
});
const out = process.argv[2] || "clawd-rig.png";
writeFileSync(out, png(sheet.w, sheet.h, sheet.buf));
console.log(`wrote ${out} (${sheet.w}x${sheet.h}) — rows: ${ORDER.join(", ")}, ${FR} frames each`);
