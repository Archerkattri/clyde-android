// Bundle the brain to a single CJS server.js for the embedded runtime.
//   node build-bundle.mjs [outfile]
// Why a script and not the esbuild CLI: the banner needs require("url") with its quotes intact.
// Passing that banner as a CLI arg through a shell strips the inner double-quotes, producing
// `require(url)` (an identifier, not the string) → "ERR_INVALID_ARG_TYPE: require received function url"
// on boot. Here the banner is a JS string literal, so the quotes always survive.
import { build } from "esbuild";

const outfile = process.argv[2] || "dist/server.js";

await build({
  entryPoints: ["src/server.ts"],
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node20",
  // The SDK + Node's createRequire read import.meta.url; esbuild's CJS output stubs import.meta to {},
  // so createRequire(import.meta.url) → createRequire(undefined) and the brain dies on boot. Define it
  // to the real file URL computed in the banner below.
  define: { "import.meta.url": "__clyde_meta_url" },
  banner: { js: 'const __clyde_meta_url = require("url").pathToFileURL(__filename).href;' },
  outfile,
  logLevel: "warning",
});
console.log("bundled ->", outfile);
