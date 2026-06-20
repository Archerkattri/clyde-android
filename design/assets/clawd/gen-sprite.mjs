// Generate an ORIGINAL Clawd-style pixel crab (license-clean, our own art).
// Inspired by the official 8-bit crab; authored from a pixel grid. Recolorable.
import { writeFileSync } from "node:fs";

const PALETTES = {
  terracotta: { o: "#D97757", s: "#BE5D3E", e: "#241F1C", w: "#FFFFFF" }, // Claude warmth
  blue:       { o: "#56C1DE", s: "#2E89A6", e: "#0E2A33", w: "#FFFFFF" }, // Clyde variant
};

// . = empty · o = body · s = shade · e = eye · w = highlight
const grid = [
  "                    ",
  "  oo            oo  ",
  " oooo          oooo ",
  " oooo          oooo ",
  " ooso  oooooo  osoo ",
  "  oo  oooooooo  oo  ",
  "  o  oooooooooo  o  ",
  "    owoooooooooo    ",
  "   ooooeooooeoooo   ",
  "   ooooeooooeoooo   ",
  "   oooooooooooooo   ",
  "   oooooooooooooo   ",
  "    ssoooooooooo    ",
  "    o o o  o o o    ",
  "   o   o    o   o   ",
  "                    ",
];

const PX = 12;
const W = 20, H = grid.length;

function svgFor(pal) {
  let rects = "";
  grid.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      const c = row[x];
      if (c !== " " && pal[c]) {
        rects += `<rect x="${x * PX}" y="${y * PX}" width="${PX}" height="${PX}" fill="${pal[c]}"/>`;
      }
    }
  });
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W * PX} ${H * PX}" shape-rendering="crispEdges">${rects}</svg>`;
}

writeFileSync("clyde-clawd.svg", svgFor(PALETTES.terracotta));
writeFileSync("clyde-clawd-blue.svg", svgFor(PALETTES.blue));
console.log(`wrote clyde-clawd.svg (+blue), ${W * PX}x${H * PX}`);
