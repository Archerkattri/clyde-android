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
  // arc the claw OUT as it raises (limbs travel on curved paths, never straight lines)
  const wx = PXX(ax) + side * raise * 0.18 * CELL, wy = PXY(ay) - raise * CELL;
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

// ── motions (p in [0,1)) — real animation craft: eased timing (no linear/raw-sine), anticipation,
//    follow-through (claws lag the body), overshoot-and-settle, volume-preserving squash & stretch. ──
const TAU = Math.PI * 2;
const lerp = (a, b, x) => a + (b - a) * x;
const clamp01 = (x) => (x < 0 ? 0 : x > 1 ? 1 : x);
const seg = (p, s, e, a, b, fn) => lerp(a, b, fn(clamp01((p - s) / (e - s)))); // eased sub-range of p
const E = {
  inOutSine: (t) => -(Math.cos(Math.PI * t) - 1) / 2,
  inQuad: (t) => t * t,
  outQuad: (t) => 1 - (1 - t) * (1 - t),
  outCubic: (t) => 1 - Math.pow(1 - t, 3),
  outBack: (t) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
  outBounce: (t) => { const n = 7.5625, d = 2.75; if (t < 1 / d) return n * t * t; if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75; if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375; return n * (t -= 2.625 / d) * t + 0.984375; },
};
const vol = (s) => ({ sx: Math.pow(s, -0.55), sy: s });            // squash/stretch with volume preserved
const damp = (p, amp, freq, decay) => amp * Math.exp(-decay * p) * Math.sin(freq * p * TAU); // settling wobble

const MOTIONS = {
  idle: (p) => {
    const breath = E.inOutSine((Math.sin(p * TAU) + 1) / 2);      // eased breathing (slow-in/out)
    const s = vol(0.97 + 0.06 * breath);
    const lagL = (Math.sin((p - 0.10) * TAU) + 1) / 2;           // claws trail the body (follow-through)
    const lagR = (Math.sin((p - 0.16) * TAU) + 1) / 2;           // …and L/R are offset (no twinning)
    return { sx: s.sx, sy: s.sy, ty: -0.35 * breath, raiseL: 0.22 + 0.16 * lagL, raiseR: 0.22 + 0.16 * lagR, openL: 0.12, openR: 0.12, eye: (p > 0.46 && p < 0.52) ? "closed" : "dot" };
  },
  jump: (p) => {
    let s, ty;
    if (p < 0.16) { s = lerp(1, 0.74, E.outQuad(clamp01(p / 0.16))); ty = seg(p, 0, 0.16, 0, 0.8, E.outQuad); }                       // anticipate: compress + dip
    else if (p < 0.26) { s = lerp(0.74, 1.28, E.outCubic(clamp01((p - 0.16) / 0.10))); ty = seg(p, 0.16, 0.26, 0.8, -2.2, E.outCubic); } // launch: stretch + shove off
    else if (p < 0.5) { s = lerp(1.28, 1.06, E.outQuad(clamp01((p - 0.26) / 0.24))); ty = seg(p, 0.26, 0.5, -2.2, -4.6, E.outQuad); }    // rise, decelerating into apex
    else if (p < 0.6) { s = 1.06; ty = -4.6 + 0.15 * Math.sin(((p - 0.5) / 0.1) * Math.PI); }                                           // apex hang
    else if (p < 0.8) { s = lerp(1.06, 1.2, E.inQuad(clamp01((p - 0.6) / 0.2))); ty = seg(p, 0.6, 0.8, -4.6, 0.6, E.inQuad); }           // fall, accelerating + stretch
    else if (p < 0.9) { s = lerp(1.2, 0.72, E.outQuad(clamp01((p - 0.8) / 0.1))); ty = seg(p, 0.8, 0.9, 0.6, 0.5, E.outQuad); }          // land squash
    else { s = lerp(0.72, 1, E.outBack(clamp01((p - 0.9) / 0.1))); ty = seg(p, 0.9, 1, 0.5, 0, E.outBack); }                            // recover with overshoot
    const v = vol(s), air = ty < -1.0;
    const cl = air ? 2.0 : (p > 0.8 && p < 0.92 ? -0.4 : 0);      // claws whip up in air, overshoot down on land
    return { sx: v.sx, sy: v.sy, ty, eye: air ? "wide" : "dot", raiseL: cl, raiseR: cl, openL: air ? 0.85 : 0.12, openR: air ? 0.85 : 0.12, effect: (p > 0.16 && p < 0.3) ? "bang" : null };
  },
  wave: (p) => {
    const o = (Math.sin(p * TAU * 2 - Math.PI / 2) + 1) / 2;
    const s = vol(1 + 0.02 * Math.sin(p * TAU * 2));
    return { sx: s.sx, sy: s.sy, eye: "happy", raiseL: 1.5 + 0.2 * o, raiseR: 0.5 - 0.15 * o, openL: o, openR: 0.15, tx: 0.15 * Math.sin(p * TAU) };
  },
  work: (p) => {
    const beat = (p * 2) % 1;                                     // two taps per cycle
    const down = beat < 0.5 ? E.outQuad(beat / 0.5) : 1 - E.inQuad((beat - 0.5) / 0.5); // snappy down, ease-in recover
    const s = vol(1 - 0.05 * down);                              // body squashes on impact
    return { sx: s.sx, sy: s.sy, eye: "dot", accessory: "hardhat", prop: "wrench", env: "bolt", raiseR: 0.5 + down * 0.8, propAng: -1.0 + down * 0.8, openR: 0.15, raiseL: 0.5 + (1 - down) * 0.5, openL: 0.2, ty: -0.15 * down };
  },
  groove: (p) => {
    const sway = Math.sin(p * TAU);
    const beat = E.inOutSine((Math.sin(p * TAU * 2) + 1) / 2);
    const s = vol(1 - 0.05 * beat);
    return { sx: s.sx, sy: s.sy, eye: "happy", accessory: "headphones", effect: "notes", tx: 1.2 * (E.inOutSine((sway + 1) / 2) - 0.5), raiseL: 0.7 + 0.5 * Math.sin((p - 0.1) * TAU), raiseR: 0.7 - 0.5 * Math.sin((p - 0.1) * TAU), openL: 0.6, openR: 0.6, p };
  },
  success: (p) => {                                              // a bouncy hop: rise, then bounce-land
    const ty = p < 0.4 ? seg(p, 0, 0.4, 0, -3.5, E.outQuad) : -3.5 * (1 - E.outBounce((p - 0.4) / 0.6));
    const s = p < 0.14 ? vol(lerp(0.82, 1.16, E.outCubic(p / 0.14))) : vol(1 + 0.04 * Math.sin(p * TAU * 3) * (1 - p));
    return { sx: s.sx, sy: s.sy, ty, eye: "happy", raiseL: 2.0, raiseR: 2.0, openL: 0.8, openR: 0.8, effect: "bang" };
  },
  error: (p) => {                                                // damped wobble — sharp then settling
    const w = damp(p, 0.9, 3.0, 3.2);
    return { eye: "x", sx: 1 + 0.04 * Math.cos(p * TAU * 3) * Math.exp(-3 * p), sy: 1, tx: w, raiseL: -0.2 + w * 0.2, raiseR: -0.2 - w * 0.2, openL: 0.1, openR: 0.1 };
  },
};

// ── filmstrip ──
const ORDER = ["idle", "jump", "wave", "work", "groove", "success", "error"];
const FR = 10, Wc = 21, Hc = 22, fw = Wc * CELL, fh = Hc * CELL, gap = 6, pad = 10;
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
