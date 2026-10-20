# Kiro Steering Guidelines — Fire OS 10-Foot UI & Hybrid Cloud Architecture

## 1. Purpose & Scope
This steering document enforces architectural, visual, and resilience invariants across all source files in **VisionBoard TV** (`com.example.visionboardtv`) targeting **Amazon Fire TV (Fire OS)** and **Android TV** 10-foot living-room environments.

## 2. Fire OS 10-Foot UI Design Rules

### 2.1 Typography & Legibility (Minimum 14sp Rule)
- All visible text rendered on the 10-foot television canvas **must** use a minimum font size of `14.sp` (`fontSize >= 14.sp`), with section headers and diagnostic step titles sized at `15.sp`–`18.sp` or higher.
- Code and mathematical formula blocks (`SELECT ...`, `d/dt [...]`) **must** use `FontFamily.Monospace` on high-contrast dark slate containers (`#0F172A` / `#1E293B`) to remain crisp across 1080p and 4K Fire TV displays.

### 2.2 Mandatory 3dp `#FF9900` D-Pad Focus Borders
- Every interactive control (Preset selectors, Step navigation buttons, Voice Coach toggles, and 3-option Quiz cards) **must** track directional focus via `.onFocusChanged { ... }` and `.focusable()`.
- When focused (`isFocused == true`), the component **must** render an explicit `3.dp` solid Amazon Fire Orange (`#FF9900`) border (`BorderStroke(3.dp, Color(0xFFFF9900))`) paired with an elevated surface tint (`#283548`) so the active cursor position is unmistakable from 10 feet away.

### 2.3 Non-Touchscreen Directional Navigation
- `AndroidManifest.xml` **must** declare `android.hardware.touchscreen` with `android:required="false"` and `android.software.leanback` with `android:required="false"`, alongside both `LAUNCHER` and `LEANBACK_LAUNCHER` intent-filter categories.
- No feature or workflow may depend on multi-touch gestures, swipe-only carousels, or hover states. 100% of application actions must be reachable and executable via standard 5-way Fire TV Remote D-Pad events (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`).

## 3. Hybrid Cloud & Offline Resilience Architecture

### 3.1 Multi-Tier Diagnostic Fallback Chain
- **Primary Cloud Tier (Amazon Bedrock Nova Pro):** `BedrockAgentClient.invokeNovaDiagnostic(prompt)` integrates the official AWS Kotlin SDK (`aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient`) and `ConverseRequest` targeting model ID `"amazon.nova-pro-v1:0"`.
- **Zero-Credential Safety (`runCatching`):** All AWS Bedrock SDK calls **must** be wrapped inside `runCatching { ... }` so that if local AWS credentials or network connectivity are absent at runtime, execution never crashes or blocks the UI.
- **Secondary / Offline Deterministic Fallback (`GeminiDiagnosticEngine`):** When `runCatching` catches missing AWS credentials or network errors, `BedrockAgentClient` **must** cleanly delegate to `GeminiDiagnosticEngine.analyzeInput(prompt)` (which in turn supports Gemini 2.5 Flash or deterministic offline SQL/STEM diagnostic parsing).

### 3.2 Local LAN Companion Bridge Invariants
- `CompanionBridgeServer` runs an embedded Ktor `CIO` server on port `8080` (`0.0.0.0:8080`) off the main thread and cleanly releases sockets in `Activity.onDestroy()`.
- Voice coaching via `android.speech.tts.TextToSpeech` **must** use `TextToSpeech.QUEUE_FLUSH` on step transitions to prevent overlapping utterances during rapid D-pad navigation.
