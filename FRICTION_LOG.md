# Amazon Developer Hackathon — Friction Log (`VisionBoardTV`)

This document records 5 structured developer friction points encountered while designing, building, and testing **VisionBoard TV** (`com.example.visionboardtv`) for Amazon Fire TV and Android TV devices.

---

## Entry 1: D-Pad Focus Visibility Loss on Dark 10-Foot Compose Surfaces

- **Specific task attempted:** Implementing 10-foot D-pad navigation across split-pane interactive controls (SQL/STEM presets, diagnostic step selectors, and 3-option quiz cards) on a Fire TV 4K Max running Fire OS.
- **Steps taken:**
  1. Created standard Jetpack Compose `Button` and `Card` components inside a two-column `Row` layout.
  2. Navigated across elements using the Fire TV Directional Pad (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`).
  3. Observed the visual focus indicator from a 10-foot viewing distance on a dark `#0F172A` surface.
- **Expected vs. actual result:**
  - *Expected:* Focused buttons and quiz cards should display an unmistakable, high-contrast focus ring suitable for living-room viewing distances.
  - *Actual:* Default Material 3 ripple/focus overlays produced a subtle ~12% alpha white tint that was virtually invisible against dark slate cards from 10 feet away, causing users to lose track of where the D-pad cursor was positioned.
- **Severity rating:** **High** (Blocks core Fire TV Appstore 10-foot UX usability and D-pad navigation clarity requirements).
- **Workaround used:** Built a custom focus-aware wrapper modifier using `onFocusChanged` and `focusable()` that explicitly renders a `3.dp` solid Amazon Fire Orange (`#FF9900`) border (`BorderStroke(3.dp, Color(0xFFFF9900))`) and elevates container luminance whenever `isFocused == true`.
- **Suggestion for improvement:** Amazon Fire TV Compose documentation and templates should include a pre-configured `FireTvFocusTheme` or modifier extension that enforces high-contrast `#FF9900` focus rings and 10-foot contrast ratios out of the box.

---

## Entry 2: Embedded HTTP Server Packaging Conflicts (`META-INF/INDEX.LIST`) in Android APK Builds

- **Specific task attempted:** Embedding a lightweight local HTTP server inside the Fire TV app on port `8080` so a mobile companion browser can beam OCR-scanned whiteboard problems over LAN without requiring cloud pairing.
- **Steps taken:**
  1. Added `io.ktor:ktor-server-core:2.3.9` and `io.ktor:ktor-server-cio:2.3.9` to `app/build.gradle.kts`.
  2. Ran `:app:assembleDebug` to package the APK for deployment to Fire TV via ADB.
- **Expected vs. actual result:**
  - *Expected:* The APK should compile and merge transitive JAR resources cleanly.
  - *Actual:* The Android Gradle Plugin `mergeDebugJavaResource` task failed with duplicate file collisions on `META-INF/INDEX.LIST`, `META-INF/io.netty.versions.properties`, and license files (`META-INF/AL2.0`, `META-INF/LGPL2.1`).
- **Severity rating:** **Medium** (Build-breaking error until Gradle packaging exclusions are explicitly configured).
- **Workaround used:** Selected Ktor's pure-coroutine `CIO` engine instead of `Netty` to avoid native epoll incompatibilities on Fire OS, and explicitly added `packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties") } }` in `app/build.gradle.kts`.
- **Suggestion for improvement:** Provide an official Fire TV Local Companion WebSocket/HTTP Bridge SDK or sample Gradle convention plugin that pre-configures resource exclusions for local LAN companion servers.

---

## Entry 3: ADB Port Forwarding & Cleartext LAN Traffic Restrictions on Fire OS

- **Specific task attempted:** Connecting a smartphone browser to the Fire TV's Companion OCR Bridge (`http://<fire-tv-ip>:8080`) and testing `POST /beam` payloads over the local Wi-Fi network.
- **Steps taken:**
  1. Started `CompanionBridgeServer` on port `8080` inside `MainActivity.onCreate()`.
  2. Attempted to load `http://<fire-tv-ip>:8080` from a mobile browser and tested loopback connections on an Android TV / Fire TV emulator.
- **Expected vs. actual result:**
  - *Expected:* Local HTTP requests to port `8080` should immediately reach the Ktor CIO server and render the mobile scanner page.
  - *Actual:* On API 28+ targets, Android blocks cleartext HTTP traffic by default, and when testing on a local emulator without LAN bridging, port `8080` is isolated inside the guest NAT unless `adb forward tcp:8080 tcp:8080` is executed.
- **Severity rating:** **Medium** (Creates onboarding friction when testing companion multi-device workflows).
- **Workaround used:** Enabled `android:usesCleartextTraffic="true"` alongside `<uses-permission android:name="android.permission.INTERNET" />` in `AndroidManifest.xml`, bound Ktor CIO to `0.0.0.0:8080`, and dynamically resolved the active IPv4 LAN interface address to display directly in the TV top header bar alongside an `adb forward tcp:8080 tcp:8080` hint for emulator users.
- **Suggestion for improvement:** Amazon's Fire TV Developer Tools / App Tester could include a built-in mDNS (`.local`) discovery responder and automatic ADB port-forwarding toggle in Android Studio for multi-screen companion apps.

---

## Entry 4: Dual-Launcher Manifest Requirements for Fire TV & Standard Android Devices

- **Specific task attempted:** Ensuring the VisionBoard TV APK launches seamlessly from both the Amazon Fire TV Leanback home screen carousel and standard Android Studio device/emulator launchers during development.
- **Steps taken:**
  1. Configured `MainActivity` in `AndroidManifest.xml` with `android.intent.category.LEANBACK_LAUNCHER`.
  2. Deployed the app to a standard non-TV Android test device and a Fire TV stick.
- **Expected vs. actual result:**
  - *Expected:* The app icon and launch intent should work across both Fire TV launchers and standard Android debug environments.
  - *Actual:* Declaring only `LEANBACK_LAUNCHER` or setting `android.software.leanback` to `required="true"` prevents installation or direct launcher invocation on standard Android tablets/emulators used during rapid UI iteration; conversely, omitting `android.hardware.touchscreen` (`required="false"`) flags the APK as incompatible with non-touch TV hardware in app store validation.
- **Severity rating:** **Medium** (Impacts cross-device testing and Appstore manifest validation).
- **Workaround used:** Declared both `<uses-feature android:name="android.software.leanback" android:required="false" />` and `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`, locked `MainActivity` to `android:screenOrientation="landscape"`, and included both `LAUNCHER` and `LEANBACK_LAUNCHER` categories in the main intent filter.
- **Suggestion for improvement:** Add a dedicated Lint check in the Amazon Fire TV SDK plugin that validates hybrid TV/Tablet manifest flags (`leanback`, `touchscreen`, and dual launcher categories) prior to APK submission.

---

## Entry 5: Android `TextToSpeech` Cold-Start Latency & Overlapping Audio on Rapid D-Pad Stepping

- **Specific task attempted:** Implementing real-time voice coaching that reads each diagnostic step aloud as the user steps through SQL or STEM solutions with the Fire TV remote.
- **Steps taken:**
  1. Initialized `android.speech.tts.TextToSpeech` in `MainActivity`.
  2. Triggered `tts.speak()` immediately upon loading the initial preset and when pressing `DPAD_RIGHT` to advance from Step 1 to Step 2 and Step 3.
- **Expected vs. actual result:**
  - *Expected:* The voice coach should begin speaking the initial diagnostic step once ready and immediately switch to the new step's audio transcript when the user navigates with the D-pad.
  - *Actual:* Calling `speak()` before the `OnInitListener` callback completed dropped the initial utterance silently, and using `QUEUE_ADD` caused a multi-second backlog of stale voice coaching when a user rapidly clicked through steps on the remote.
- **Severity rating:** **Low-Medium** (Degrades audio coaching responsiveness if not guarded).
- **Workaround used:** Tracked `isTtsReady` state inside the `TextToSpeech.OnInitListener` callback, configured `Locale.US` with a clear `1.0f` speech rate, enforced `TextToSpeech.QUEUE_FLUSH` on every step transition or quiz answer selection so audio immediately matches the visual focus, and guaranteed `tts?.stop()` / `tts?.shutdown()` inside `onDestroy()`.
- **Suggestion for improvement:** Document best practices for combining Fire TV D-pad focus changes with `TextToSpeech.QUEUE_FLUSH` and AudioFocus handling in the Fire TV Accessibility & Voice guidelines.
