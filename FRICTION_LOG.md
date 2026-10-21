# Amazon Developer Hackathon — Official Friction Log (VisionBoard TV)

**Project:** VisionBoard TV (Fire TV Primary Track + AWS Builder Mini Challenge + Open Source Mini Challenge)  
**Developer:** Deshoju Vinayaka  
**Repositories:**  
- Main Fire TV App: https://github.com/vinayaka130607/VisionBoardTV  
- Open Source Companion Library: https://github.com/vinayaka130607/firetv-companion-bridge-kt  

---

### Entry 1: `:app:checkDebugAarMetadata` Compile SDK Mismatch (`android-34` vs `android-35`) When Targeting Fire OS
* **1. Specific task attempted:** Compiling a Fire TV application (`compileSdk = 34`, matching Fire OS / Android TV emulator targets) using modern Jetpack Compose TV (`androidx.tv:tv-material:1.0.0-alpha10`) and AndroidX libraries.
* **2. Steps taken:** Configured `app/build.gradle.kts` with `compileSdk = 34`, `minSdk = 26`, and `targetSdk = 34`, then executed `.\gradlew.bat assembleDebug`.
* **3. Expected vs. actual result:** Expected a clean APK build for Fire OS. Actual result: Build failed at `:app:checkDebugAarMetadata` with 17 AAR metadata errors because transitive dependencies (`androidx.compose.material3:material3-android:1.4.0`, `androidx.compose.foundation:foundation-android:1.10.4`, and `androidx.core:core-ktx:1.16.0`) strictly require `compileSdk = 35` or later.
* **4. Severity rating:** High (Build Blocker)
* **5. Workaround used:** Upgraded `compileSdk = 35` in `app/build.gradle.kts` to satisfy AAR metadata checks while keeping `minSdk = 26` and `targetSdk = 34` so the compiled APK remains 100% binary-compatible with existing Fire OS devices and Android TV emulators.
* **6. Actionable suggestion for Amazon Team:** Update the Fire TV Android Studio / Jetpack Compose starter documentation to explicitly advise developers to set `compileSdk = 35+` alongside `minSdk = 26` / `targetSdk = 34` so latest AndroidX Compose BOM packages do not fail AAR verification.

---

### Entry 2: `JdkImageTransform` (`jlink.exe` Missing) & Gradle Configuration Cache Serialization Crash on API 35
* **1. Specific task attempted:** Building the Fire TV APK against `android-35` inside an AI-assisted IDE environment (Google Antigravity) using `./gradlew assembleDebug`.
* **2. Steps taken:** Updated `compileSdk = 35` and ran `./gradlew assembleDebug` from the integrated terminal.
* **3. Expected vs. actual result:** Expected `compileDebugJavaWithJavac` to compile cleanly. Actual result: `JdkImageTransform` failed on `C:\Android\Sdk\platforms\android-35\core-for-system-modules.jar` because the IDE extension's bundled JRE (`redhat.java.../jre/25.0.4.1-win32-x86_64`) lacked `jlink.exe`. Furthermore, after pointing `org.gradle.java.home` to Android Studio's full JetBrains Runtime (`jbr`), subsequent runs failed with `error writing value of type 'TransformBackedProvider'` due to a stale Gradle daemon and Configuration Cache serialization conflict.
* **4. Severity rating:** High (Build Blocker)
* **5. Workaround used:** Located the full Android Studio JDK (`C:/Users/vinay/AppData/Local/Android/jbr`) containing `jlink.exe`, configured `org.gradle.java.home` with forward slashes in `gradle.properties`, disabled configuration caching (`org.gradle.configuration-cache=false`), and terminated stale background daemons via `.\gradlew.bat --stop`.
* **6. Actionable suggestion for Amazon Team:** In Kiro and Fire TV Android build guides, include a troubleshooting snippet for `gradle.properties` (`org.gradle.java.home` + `org.gradle.configuration-cache=false`) when switching between agentic IDEs and Android Studio with API 35+.

---

### Entry 3: Jetpack Compose for TV D-Pad Focus Ring Visibility at a 10-Foot Viewing Distance
* **1. Specific task attempted:** Implementing hardware D-pad remote navigation (`KEYCODE_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT`, `CENTER`) across diagnostic step cards and interactive quiz buttons on a 1080p Fire TV screen.
* **2. Steps taken:** Applied `.focusable()` and `.clickable()` modifiers to standard dark-themed Compose containers on the 10-foot split-pane layout.
* **3. Expected vs. actual result:** Expected clear visual feedback when moving focus with the Fire TV remote arrow keys. Actual result: Default Material3 focus ripples were nearly invisible from a 10-foot couch viewing distance against a dark `#0B0F19` TV background, making it hard to tell which card or quiz option was active.
* **4. Severity rating:** Medium (UX / Accessibility)
* **5. Workaround used:** Built custom `onFocusChanged` state trackers on every interactive TV card and button (`TvFocusableButton`) that dynamically apply a high-contrast 3dp `#FF9900` (Amazon Orange) `BorderStroke` and container background shift whenever `isFocused == true`.
* **6. Actionable suggestion for Amazon Team:** Publish an official `FireTvComposeTheme` snippet in the Fire TV developer docs that enforces high-contrast 10-foot focus borders and D-pad state styling out of the box.

---

### Entry 4: Second-Screen Companion Socket Isolation on Fire TV / Android TV Emulators
* **1. Specific task attempted:** Streaming live mobile camera/OCR payloads (`POST /beam`) from a companion smartphone/browser UI into the Fire TV app to overcome the lack of built-in cameras and physical keyboards on Fire TV sticks.
* **2. Steps taken:** Embedded a non-blocking Ktor CIO HTTP server (`io.ktor:ktor-server-cio:2.3.9`) on port `8080` inside the Fire TV APK and attempted to reach `http://localhost:8080` from the host browser.
* **3. Expected vs. actual result:** Expected `localhost:8080` to connect directly to the running Fire TV app. Actual result: Connection was refused because the Android/Fire TV emulator runs behind an isolated virtual router (`10.0.2.15`).
* **4. Severity rating:** Medium
* **5. Workaround used:** Bridged the host TCP port directly into the emulator container using `adb forward tcp:8080 tcp:8080` and extracted our reusable socket & payload parser into a standalone MIT-licensed open-source library (`firetv-companion-bridge-kt`) with JUnit 5 tests so other Fire TV developers can add second-screen camera/OCR input in 5 lines of Kotlin.
* **6. Actionable suggestion for Amazon Team:** Add a "Second-Screen & Companion App Local Socket Bridging" section to the Fire TV documentation covering `adb forward` for emulator testing and mDNS/NSD discovery for physical Fire TV hardware.

---

### Entry 5: Netty / CIO `META-INF` Packaging Collisions & Credential-Free AWS Bedrock Testing on Fire OS
* **1. Specific task attempted:** Bundling `aws.sdk.kotlin:bedrockruntime:1.3.12` (for Amazon Nova Pro `amazon.nova-pro-v1:0`), embedded Ktor CIO server libraries, and Google ML Kit OCR into a single deployable Fire TV APK that judges can test without configuring local AWS IAM keys on an emulator.
* **2. Steps taken:** Added all dependencies to `app/build.gradle.kts`, implemented `BedrockAgentClient.kt` using `BedrockRuntimeClient` and `ConverseRequest`, and compiled the debug APK.
* **3. Expected vs. actual result:** Expected clean APK packaging and smooth runtime execution on an unconfigured emulator. Actual result: Gradle threw duplicate file merge errors on `META-INF/INDEX.LIST` and `META-INF/io.netty.versions.properties`, and unauthenticated runtime calls to `BedrockRuntimeClient` on a fresh emulator throw credential provider exceptions if not wrapped.
* **4. Severity rating:** Medium
* **5. Workaround used:** Added explicit `packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties") } }` rules in `app/build.gradle.kts`, documented our architecture in `.kiro/specs/visionboard-tv/` and `.kiro/steering/`, and wrapped `BedrockAgentClient.invokeNovaDiagnostic()` in a `runCatching` fallback chain that automatically delegates to `GeminiDiagnosticEngine` and our deterministic offline SQL/STEM analyzer when local AWS credentials are absent.
* **6. Actionable suggestion for Amazon Team:** Provide a mock/sandbox offline mode in `aws.sdk.kotlin:bedrockruntime` (or a sample packaging exclusion template) for client-side Fire OS demos so judges and testers can run APK builds out-of-the-box without injecting AWS credentials into an emulator.