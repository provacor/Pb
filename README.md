# সাথী (Sathi): AI phone agent for Android

সাথী একটি Android অ্যাপ। বাংলা, ইংরেজি বা দুটো মিশিয়ে কথা বললে এটি বুঝে ফোনে কাজটা করে দেয়। এটা চ্যাটবট নয়, এজেন্ট: প্রতিটি কাজ ধাপে ধাপে করে এবং প্রতিটি ধাপের ফল যাচাই করে।

Sathi is a native Android app (Kotlin, Jetpack Compose, Material 3). It understands spoken Bengali, English and mixed commands, plans the steps, runs them through official Android APIs, and answers by voice.

```
voice → speech-to-text → understand → plan → act → check → reply (text + voice)
```

## Status: Phase 2

| Works now | How |
|---|---|
| Voice input in Bengali (`bn-BD`) or English, with live partial text; a command ends 2 s after you stop talking | `SpeechRecognizer` |
| **Always listen** (hands-free): listens, runs the command, waits for the spoken reply, listens again, also when Sathi is closed | `ListeningService` (foreground service with a notification and a Stop button) |
| Natural Bengali, English and mixed commands, in any verb form ("ইউটিউব টা খুলতে বলছি", "ফেসবুক খুলবা") and in chains | `core/parse/CommandInterpreter` |
| Opening apps by spoken name; in-app and web search | launcher and search intents |
| **Screen control**: tap by label, open the Nth video or result, type, scroll, back, home, read the screen aloud | `AgentAccessibilityService` + `core/screen/ScreenQueries` |
| Every screen step is checked afterwards: the app really came to the front, the screen really changed, the text really went in | `ActionExecutor` |
| Asks before sending, deleting, paying, calling or posting; acts only after "হ্যাঁ" | `core/plan/Confirmation` |
| Flashlight and volume; Wi‑Fi, Bluetooth, data and location settings panels | `AndroidDeviceControls` |
| Spoken replies with pause, resume and stop; task memory between commands; developer log | |

### Turning on screen control

Android requires the user to turn on an Accessibility Service by hand: Settings → Accessibility → Sathi → On. Sathi's Permissions page has a button for it.

For an APK installed from a browser, Android 13+ greys that switch out as a "restricted setting". To unblock it, go to App info → ⋮ → **Allow restricted settings**, then turn the switch on.

## Install on your phone (no Android Studio needed)

GitHub builds the app on every push. The newest APK is always at:

**https://github.com/provacor/Pb/releases/download/sathi-latest/sathi.apk**

1. Open the link on the phone and download `sathi.apk`.
2. Open the file. If Android asks, allow "Install unknown apps" for your browser or file manager.
3. Later builds install over the old one; your settings are kept.

ফোনে লিংকটা খুলে `sathi.apk` নামান, তারপর ফাইলটা খুলে install করুন। Android জিজ্ঞেস করলে "Install unknown apps" অনুমতি দিন।

## Build and run

Requirements: Android Studio Ladybug (2024.2) or newer, JDK 17+, Android SDK 35.

```bash
./gradlew :app:assembleDebug      # build the APK
./gradlew :app:installDebug       # install on a connected phone (USB debugging on)
./gradlew :core:test :app:testDebugUnitTest
```

Or open the folder in Android Studio and press **Run**. Use a real phone if you can: emulators often lack a speech service and Bengali voices.

On the phone:

1. Allow the microphone on the first screen.
2. For Bengali replies, install the Bengali voice if the card says it is missing. On Google's engine, go to Settings → Text-to-speech → Install voice data.
3. Tap the microphone and say "ইউটিউব খুলে physics wave সার্চ করো".

## Architecture

```
core/            Pure Kotlin. No Android dependency, so it is fully unit-tested on the JVM.
  parse/         CommandInterpreter: text → AgentIntent list (Bengali/English/mixed)
  apps/          AppResolver + AppAliases: spoken name → installed app
  plan/          TaskPlanner: intents → steps; prefers direct intents over UI automation
  model/         AgentIntent, Capability, TaskContext, AppInfo, Language
  response/      Everything the agent says, in Bengali and English
  log/           AgentLog: COMMAND / UNDERSTANDING / PLAN / ACTION / RESULT / ERROR
  ai/            AIProvider interface (vendor-neutral; no keys in source)
app/
  agent/         AgentController (the act-and-check loop), ActionExecutor, Ports
  voice/         SpeechRecognizerManager
  tts/           TextToSpeechManager
  apps/          InstalledAppsRepository, AppLauncher
  settings/      DataStore-backed settings
  ui/            Home (animated mic), Permissions onboarding, Settings
```

Every `AgentIntent` declares the `Capability` it needs (`NONE`, `ACCESSIBILITY`, `FILES`, `AI`). Each phase adds one capability without changing how commands are understood.

The agent loop never fires a chain blindly. If a step fails or needs missing setup, the remaining steps are marked skipped. A task has a hard cap of 12 steps.

## Privacy

- The app has no `INTERNET` permission. Understanding, planning and launching run on the phone.
- Speech-to-text and text-to-speech come from the phone's own services. Speech recognition may use the network unless "Prefer offline recognition" is on and an offline pack is installed.
- Visible apps come from the launcher `<queries>` entry, not `QUERY_ALL_PACKAGES`.
- The microphone is used only while the button is on, or while "Always listen" is on. In that mode a notification is shown the whole time.
- The Accessibility Service reads the screen only while a command runs. It never reads password fields.

## Roadmap

1. **Phase 1 (done):** voice, TTS, offline parser, app launching, in-app and web search, onboarding.
2. **Phase 2 (done):** Accessibility Service with verified tap, type, scroll, back, home and read-screen; hands-free listening; confirmations; device controls.
3. **Phase 3:** messaging by contact name (SMS and WhatsApp, with confirmation), and AI provider adapters for commands the offline parser can't handle.
4. **Phase 4:** file search through MediaStore and the Storage Access Framework.
5. **Phase 5:** PDF text extraction with chunking, summaries and Q&A.
6. **Phase 6:** image OCR.
7. **Phase 7:** richer screen understanding.
8. **Phase 8:** task history (Room) and UI polish.

## Also in this repo

`jonaki/index.html` is "জোনাকির রাত", a small browser game in a single HTML file.
