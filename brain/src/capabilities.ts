import type { AppClient } from "./appClient";
import { rish, su } from "./shell";
import type { Capabilities } from "./types";

/** Merge the app's /caps with local probes for Shizuku (rish) and root (su). */
export async function probeCapabilities(app: AppClient): Promise<Capabilities> {
  const appCaps = await app.caps();
  const a = appCaps.ok && appCaps.result ? appCaps.result : {};

  const [t2, t3] = await Promise.all([
    rish("id")
      .then((r) => r.ok && /uid=2000|uid=\d+\(shell\)/.test(r.stdout))
      .catch(() => false),
    su("id")
      .then((r) => r.ok && /uid=0\(root\)/.test(r.stdout))
      .catch(() => false),
  ]);

  const shizuku = Boolean(a.shizuku) || t2;
  const root = Boolean(a.root) || t3;

  return {
    tier0: true,
    tier1: Boolean(a.accessibility),
    tier2: shizuku,
    tier3: root,
    accessibility: Boolean(a.accessibility),
    shizuku,
    root,
    overlay: Boolean(a.overlay),
    perms: a.perms ?? {},
    defaultApps: a.defaultApps ?? {},
  };
}
