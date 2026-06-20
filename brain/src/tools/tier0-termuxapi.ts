import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, gate } from "./helpers";
import { termux } from "../shell";
import type { ShellResult } from "../types";

const out = (r: ShellResult) => (r.ok ? text(r.stdout.trim() || "ok") : err(r.stderr.trim() || `exit ${r.code}`));

/** Tier 0 — Termux:API device functions (sensors, clipboard, notify, torch, …). */
export function makeTier0Termux(ctx: ToolCtx) {
  return [
    tool("get_battery", "Battery status and charge level.", {}, async () => out(await termux("termux-battery-status"))),

    tool(
      "get_location",
      "Current GPS location.",
      { provider: z.enum(["gps", "network", "passive"]).optional() },
      async (a) => out(await termux("termux-location", a.provider ? ["-p", a.provider] : [], 30000))
    ),

    tool("clipboard_get", "Read the clipboard.", {}, async () => out(await termux("termux-clipboard-get"))),
    tool("clipboard_set", "Set the clipboard.", { text: z.string() }, async (a) => out(await termux("termux-clipboard-set", [a.text]))),

    tool(
      "notify",
      "Post a notification.",
      { title: z.string().optional(), content: z.string().optional(), id: z.string().optional() },
      async (a) => {
        const args: string[] = [];
        if (a.title) args.push("-t", a.title);
        if (a.content) args.push("-c", a.content);
        if (a.id) args.push("--id", a.id);
        return out(await termux("termux-notification", args));
      }
    ),

    tool("torch", "Toggle the flashlight.", { on: z.boolean() }, async (a) => out(await termux("termux-torch", [a.on ? "on" : "off"]))),
    tool("vibrate", "Vibrate the device.", { ms: z.number().int().optional() }, async (a) => out(await termux("termux-vibrate", a.ms ? ["-d", String(a.ms)] : []))),
    tool("tts_speak", "Speak text via the device TTS engine.", { text: z.string() }, async (a) => out(await termux("termux-tts-speak", [a.text]))),
    tool("read_sensor", "Read a sensor once.", { type: z.string().describe("sensor name, e.g. accelerometer") }, async (a) => out(await termux("termux-sensor", ["-s", a.type, "-n", "1"], 8000))),
    tool("wifi_info", "Current Wi-Fi connection info.", {}, async () => out(await termux("termux-wifi-connectioninfo"))),

    tool(
      "volume_set",
      "Set a volume stream level.",
      { stream: z.enum(["music", "call", "ring", "alarm", "notification", "system"]), level: z.number().int() },
      async (a) => out(await termux("termux-volume", [a.stream, String(a.level)]))
    ),

    tool("download", "Download a URL via the system download manager.", { url: z.string() }, async (a) => out(await termux("termux-download", [a.url]))),

    // ── consequential (privacy / hardware capture) ──
    tool(
      "mic_record",
      "Record audio from the microphone. Consequential.",
      { seconds: z.number().int(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "mic_record", a);
        if (g) return g;
        return out(await termux("termux-microphone-record", ["-l", String(a.seconds), "-f", "/sdcard/clyde-rec.m4a"], (a.seconds + 5) * 1000));
      }
    ),
    tool(
      "camera_photo",
      "Take a photo. Consequential.",
      { camera: z.string().optional(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "camera_photo", a);
        if (g) return g;
        return out(await termux("termux-camera-photo", ["-c", a.camera ?? "0", "/sdcard/clyde-photo.jpg"], 20000));
      }
    ),
    tool(
      "list_sms",
      "List recent SMS messages. Consequential (privacy).",
      { limit: z.number().int().optional(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "list_sms", a);
        if (g) return g;
        return out(await termux("termux-sms-list", ["-l", String(a.limit ?? 10)]));
      }
    ),
    tool(
      "list_contacts",
      "List contacts. Consequential (privacy).",
      { token: z.string() },
      async (a) => {
        const g = gate(ctx, "list_contacts", a);
        if (g) return g;
        return out(await termux("termux-contact-list"));
      }
    ),
    tool(
      "list_call_log",
      "List recent calls. Consequential (privacy).",
      { limit: z.number().int().optional(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "list_call_log", a);
        if (g) return g;
        return out(await termux("termux-call-log", ["-l", String(a.limit ?? 10)]));
      }
    ),
  ];
}
