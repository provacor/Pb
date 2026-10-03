# সাথী (Sathi): AI phone agent for Android

সাথী একটি Android অ্যাপ। বাংলা, ইংরেজি বা দুটো মিশিয়ে কথা বললে এটি বুঝে ফোনে কাজটা করে দেয়। এটা চ্যাটবট নয়, এজেন্ট: প্রতিটি কাজ ধাপে ধাপে করে এবং প্রতিটি ধাপের ফল যাচাই করে।

Sathi is a native Android app (Kotlin, Jetpack Compose, Material 3). It understands spoken Bengali, English and mixed commands, plans the steps, runs them through official Android APIs, and answers by voice.

```
voice → speech-to-text → understand → plan → act → check → reply (text + voice)
```

## Status: Phase 1

| Works now | How |
|---|---|
| Voice input in Bengali (`bn-BD`) or English, with live partial text | `SpeechRecognizer` |
| Typed commands | Text field on the home screen |
| Offline understanding of Bengali, English and mixed commands, including chains like "ইউটিউব খুলে physics wave সার্চ করো" | `core/parse/CommandInterpreter` |
| Opening installed apps by spoken name ("ইউটিউব", "গ্যালারি", "file manager"), matched against what the phone actually has | `core/apps/AppResolver` + launcher intents |
| Searching inside apps that accept a search intent (YouTube, Chrome, Play Store…) and web search | `ACTION_SEARCH` / `ACTION_WEB_SEARCH` |
| Follow-up commands ("এখন physics search করো" after "ইউটিউব খোলো") | `TaskContext` short-term memory |
| Spoken replies in Bengali or English, with pause, resume and stop | `TextToSpeech`, sentence by sentence |
| Permission onboarding, settings, developer log (digit runs such as OTPs are masked) | Compose UI |

Commands that need screen control ("প্রথম ভিডিওটা চালাও", "পেছনে যাও", "উপরে স্ক্রল করো") or file access ("Find my latest PDF") are already understood. In this phase the agent says they are not available yet and stops, without guessing.

A launched app is reported as **"sent, not verified"**. Sending an intent does not prove the app is on screen, and checking that needs the Accessibility Service in Phase 2.

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
- The microphone is used only while the button is on.

## Roadmap

1. **Phase 1 (done):** voice, TTS, offline parser, app launching, in-app and web search, onboarding.
2. **Phase 2:** Accessibility Service for reading the screen, tap, scroll, back, home and typing. It will also verify each launch and search through `ScreenState`.
3. **Phase 3:** AI provider adapters for commands the offline parser can't handle, plus a confirmation step for sending, deleting and buying.
4. **Phase 4:** file search through MediaStore and the Storage Access Framework.
5. **Phase 5:** PDF text extraction with chunking, summaries and Q&A.
6. **Phase 6:** image OCR.
7. **Phase 7:** richer screen understanding.
8. **Phase 8:** task history (Room) and UI polish.

## Also in this repo

`jonaki/index.html` is "জোনাকির রাত", a small browser game in a single HTML file.
