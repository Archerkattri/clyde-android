# 05 — Permissions & AndroidManifest

## Permission table

| Permission | Tier | Why | Notes / gotcha |
|---|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | 1 | the device-control engine | user enables manually; **Android 13+ "restricted settings"** blocks sideloaded apps until you tap **App info ▸ ⋮ ▸ Allow restricted settings** |
| `SYSTEM_ALERT_WINDOW` | shell | status bubble, ask-about-screen, confirm overlay | "Display over other apps" |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` + `FOREGROUND_SERVICE_SPECIAL_USE` | shell | keep the orchestrator + (optional) wake word alive | A14+ requires a declared `foregroundServiceType` |
| `RECORD_AUDIO` | shell | STT / wake word | while-in-use; can't start mic FGS from background/boot |
| `CALL_PHONE` | 0 | `start_call` | consequential → confirm |
| `SEND_SMS` | 0 | `send_sms` | consequential → confirm; Play restricts SMS apps (you're sideloading, fine) |
| `READ_SMS` / `READ_CALL_LOG` / `READ_CONTACTS` | 0 | `list_sms`/`list_call_log`/`list_contacts` | sensitive; confirm; only if you want these |
| `READ_CALENDAR` / `WRITE_CALENDAR` | 0 | calendar read/insert | |
| `ACCESS_FINE_LOCATION` | 0 | `get_location`, navigation context | |
| `POST_NOTIFICATIONS` | shell | status + notifications | A13+ runtime |
| `QUERY_ALL_PACKAGES` | 0 | resolve `launch_app` by name | declare package `<queries>` instead if you can, to ease distribution |
| `com.termux.permission.RUN_COMMAND` | brain (Model B) | fire Claude in Termux via intent | also set Termux `allow-external-apps=true` |
| `INTERNET` | brain link | loopback + Claude calls | |
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | optional | proactive "smart reply"/notification triage (novel idea #4) | separate user grant |

**Not requestable by a sideloaded app** (need Tier 4 / custom ROM): `BIND_VOICE_INTERACTION`, `CAPTURE_AUDIO_HOTWORD` (true hotword). Leave seams; don't request them.

## AndroidManifest.xml skeleton

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="dev.kris.clyde">

  <uses-permission android:name="android.permission.INTERNET"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
  <uses-permission android:name="android.permission.RECORD_AUDIO"/>
  <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
  <uses-permission android:name="android.permission.CALL_PHONE"/>
  <uses-permission android:name="android.permission.SEND_SMS"/>
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
  <uses-permission android:name="android.permission.READ_CALENDAR"/>
  <uses-permission android:name="android.permission.WRITE_CALENDAR"/>
  <uses-permission android:name="com.termux.permission.RUN_COMMAND"/>
  <!-- add READ_SMS/READ_CONTACTS/READ_CALL_LOG only if you want those tools -->

  <application android:label="Clyde" android:icon="@mipmap/ic_launcher">

    <!-- TRIGGER: makes the app selectable as Digital assistant app -->
    <activity android:name=".assist.AssistEntryActivity"
        android:theme="@style/Theme.Translucent.NoTitleBar"
        android:exported="true" android:noHistory="true"
        android:excludeFromRecents="true">
      <intent-filter>
        <action android:name="android.intent.action.ASSIST"/>
        <category android:name="android.intent.category.DEFAULT"/>
      </intent-filter>
      <intent-filter>
        <action android:name="android.intent.action.VOICE_COMMAND"/>
        <category android:name="android.intent.category.DEFAULT"/>
      </intent-filter>
    </activity>

    <activity android:name=".settings.SettingsActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>

    <!-- ORCHESTRATOR: session owner + hosts LocalControlServer:8766 -->
    <service android:name=".service.AgentOrchestratorService"
        android:exported="false"
        android:foregroundServiceType="microphone|specialUse">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Conversational on-device assistant orchestrator"/>
    </service>

    <!-- TIER 1 HANDS: accessibility engine -->
    <service android:name=".service.PhoneControlAccessibilityService"
        android:exported="false"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
      <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
      </intent-filter>
      <meta-data android:name="android.accessibilityservice"
                 android:resource="@xml/accessibility_service_config"/>
    </service>

    <!-- OPTIONAL later: wake-word FGS, notification listener -->
  </application>
</manifest>
```

## res/xml/accessibility_service_config.xml

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:description="@string/a11y_description"/>
```

## Setup-wizard checklist (SettingsActivity should walk the user through)

1. Grant **Display over other apps** (SYSTEM_ALERT_WINDOW).
2. Enable **Clyde accessibility service** → if greyed out, **Allow restricted settings** first (A13+).
3. Grant runtime perms (mic, call, SMS, location, notifications, calendar) as you opt into tools.
4. Install/activate **Shizuku** (wireless debugging) → Clyde detects it for Tier 2.
5. Confirm **brain is reachable** (ping `127.0.0.1:8765/healthz`).
6. Set Clyde as **Digital assistant app** (button deep-links to `ACTION_VOICE_INPUT_SETTINGS`).
