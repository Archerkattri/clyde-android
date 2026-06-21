// Turn the clyde-secure-prod-audit workflow result into a prioritized report.
// Usage: node scripts/audit-extract.mjs <workflow-output.json> <out.md>
import { readFileSync, writeFileSync } from "node:fs";

const [, , src, outMd] = process.argv;
const raw = JSON.parse(readFileSync(src, "utf8"));
const result = raw.result ?? raw;
const confirmed = result.confirmed ?? [];
const order = { critical: 0, high: 1, medium: 2, low: 3 };
confirmed.sort((a, b) => (order[a.severity] ?? 9) - (order[b.severity] ?? 9));

const counts = {};
for (const f of confirmed) counts[f.severity] = (counts[f.severity] ?? 0) + 1;

const md = [];
md.push("# Clyde security + production audit");
md.push("");
md.push(`Adversarial audit (find → independent verify). **${result.confirmedCount ?? confirmed.length} confirmed**, ${result.dismissedFalsePositives ?? 0} false positives dismissed.`);
md.push("");
md.push(`Severity: ${["critical", "high", "medium", "low"].map((s) => `${counts[s] ?? 0} ${s}`).join(" · ")}`);
md.push("");
confirmed.forEach((f, i) => {
  md.push(`## ${i + 1}. [${f.severity.toUpperCase()}] ${f.title}`);
  md.push(`*${f.dimension}* — \`${f.file}\``);
  md.push("");
  md.push(`**Problem:** ${f.detail}`);
  md.push("");
  md.push(`**Fix:** ${f.fix}`);
  md.push("");
});
writeFileSync(outMd, md.join("\n"));

const brief = confirmed.map((f, i) => `${i + 1}. [${f.severity}] (${f.dimension}) ${f.title}`);
console.log(JSON.stringify({ counts, total: confirmed.length }, null, 2));
console.log("\n" + brief.join("\n"));
