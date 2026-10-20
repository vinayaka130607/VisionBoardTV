# Technical Design — VisionBoard TV

## Architecture Topology
1. **Presentation Layer (Fire OS / Jetpack Compose for TV):** Landscape 1080p split-pane layout (`0.36f` step navigation rail + `0.64f` diagnostic diff and remote quiz stage).
2. **Companion Vision Bridge (`CompanionBridgeServer`):** Embedded Ktor CIO non-blocking server on port `8080` pairing mobile camera/ML Kit OCR capture with the Fire TV runtime.
3. **Diagnostic & Quiz Engine (`GeminiDiagnosticEngine`):** Hybrid LLM + deterministic AST/regex fallback analyzer producing structured `DiagnosticStep` models with interactive multiple-choice verification.
