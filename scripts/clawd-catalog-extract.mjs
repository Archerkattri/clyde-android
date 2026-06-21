// One-off: turn the clawd-scenario-catalog workflow result into repo artifacts.
// Usage: node scripts/clawd-catalog-extract.mjs <workflow-output.json> <out-dir>
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const [, , src, outDir] = process.argv;
const raw = JSON.parse(readFileSync(src, "utf8"));
const result = raw.result ?? raw;
const catalog = result.catalog ?? [];
const kit = result.kit ?? {};
mkdirSync(outDir, { recursive: true });

let totalScenarios = 0;
let totalVariants = 0;
const perDomain = catalog.map((d) => {
  const scenarios = d.scenarios ?? [];
  const variants = scenarios.reduce((a, s) => a + (s.variants?.length ?? 0), 0);
  totalScenarios += scenarios.length;
  totalVariants += variants;
  return { domain: d.domain, scenarios: scenarios.length, variants };
});

// Machine-readable catalog (engine consumes this).
writeFileSync(`${outDir}/scenario-catalog.json`, JSON.stringify({ catalog, kit }, null, 2));

// Human-readable overview.
const md = [];
md.push("# Clawd scenario catalog");
md.push("");
md.push(`Generated from the \`clawd-scenario-catalog\` workflow. **${totalScenarios} scenarios · ${totalVariants} animations** across ${catalog.length} domains — every one composable from the finite parts kit below (no bundled art).`);
md.push("");
md.push("| Domain | Scenarios | Animations |");
md.push("| --- | --: | --: |");
for (const d of perDomain) md.push(`| ${d.domain} | ${d.scenarios} | ${d.variants} |`);
md.push(`| **Total** | **${totalScenarios}** | **${totalVariants}** |`);
md.push("");

const kitSection = (title, arr) => {
  md.push(`### ${title} (${arr?.length ?? 0})`);
  for (const p of arr ?? []) md.push(`- **${p.name}** — ${p.draw}`);
  md.push("");
};
md.push("## Parts kit");
md.push("");
kitSection("Expressions", kit.expressions);
kitSection("Claw poses", kit.clawPoses);
kitSection("Props", kit.props);
kitSection("Effects", kit.effects);
kitSection("Motions", kit.motions);
if (kit.gaps?.length) {
  md.push("### Notes / gaps");
  for (const g of kit.gaps) md.push(`- ${g}`);
  md.push("");
}

md.push("## Scenarios by domain");
md.push("");
for (const d of catalog) {
  md.push(`### ${d.domain}`);
  for (const s of d.scenarios ?? []) {
    md.push(`- \`${s.key}\` — **${s.title}** (${s.variants?.length ?? 0}) · _${s.fires}_`);
  }
  md.push("");
}
writeFileSync(`${outDir}/CATALOG.md`, md.join("\n"));

console.log(JSON.stringify({
  totalScenarios,
  totalVariants,
  perDomain,
  kitCounts: {
    expressions: kit.expressions?.length ?? 0,
    clawPoses: kit.clawPoses?.length ?? 0,
    props: kit.props?.length ?? 0,
    effects: kit.effects?.length ?? 0,
    motions: kit.motions?.length ?? 0,
  },
  kitTotals: kit.totals ?? null,
}, null, 2));
