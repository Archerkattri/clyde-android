// Brain-derived, human-readable description of a tool call. Used for the confirm sheet
// and the action feed so the approved text is the REAL action, never model-authored prose.

export interface ActionDescription {
  summary: string;
  details?: string;
}

export function describeAction(tool: string, p: Record<string, unknown> = {}): ActionDescription {
  const s = (k: string) => String(p?.[k] ?? "");
  switch (tool) {
    case "send_sms": return { summary: `Text ${s("to")}`, details: s("body") };
    case "start_call": return { summary: `Call ${s("number")}` };
    case "add_calendar_event": return { summary: `Add event "${s("title")}"`, details: s("startIso") };
    case "open_url": return { summary: "Open a link", details: s("url") };
    case "share_text": return { summary: "Share text to another app", details: s("content") || s("text") };
    case "clipboard_get": return { summary: "Read the clipboard" };
    case "list_sms": return { summary: "Read your recent text messages" };
    case "list_contacts": return { summary: "Read your contacts" };
    case "list_call_log": return { summary: "Read your call history" };
    case "mic_record": return { summary: `Record ${s("seconds")}s of audio` };
    case "camera_photo": return { summary: "Take a photo" };
    case "download": return { summary: "Download a file", details: s("url") };
    case "shell": return { summary: "Run a system command (ADB)", details: s("cmd") };
    case "su_shell": return { summary: "Run a command as root", details: s("cmd") };
    case "pm_grant": return { summary: `Grant ${s("permission")} to ${s("pkg")}` };
    case "settings_put": return { summary: `Change ${s("namespace")} setting ${s("key")} → ${s("value")}` };
    case "app_disable": return { summary: `Disable the app ${s("pkg")}` };
    case "force_stop": return { summary: `Force-stop ${s("pkg")}` };
    case "disable_phantom_killer": return { summary: "Stop Android from killing the brain" };
    case "inject_event": return { summary: "Inject a low-level input event" };
    case "make_persistent": return { summary: "Make the brain start on boot" };
    case "grant_signature_perm": return { summary: `Grant ${s("permission")} to ${s("pkg")}` };
    case "delegate_to_gemini":
      return { summary: "Send the following to Google Gemini (a different vendor than Claude)", details: s("prompt") };
    default: return { summary: tool.replace(/_/g, " ") };
  }
}
