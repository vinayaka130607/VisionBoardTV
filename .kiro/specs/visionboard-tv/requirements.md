# Requirements Specification — VisionBoard TV (Fire OS / Vega OS)

## 1. Overview
VisionBoard TV transforms any Amazon Fire TV into a 10-foot multi-modal AI diagnostic studio for handwritten computer science queries (SQL/Python) and secondary school STEM homework.

## 2. User Stories & Acceptance Criteria (EARS Notation)
- **REQ-1 (Second-Screen Computer Vision Bridge):** WHEN a user scans a handwritten code snippet or math derivation using the companion mobile scanner, THE VisionBoard TV system SHALL ingest the OCR payload over the local WebSocket/HTTP bridge within 500ms.
- **REQ-2 (10-Foot Fire TV D-Pad Navigation):** WHEN navigating on Fire OS using the Fire TV Remote (`KEYCODE_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT`, `CENTER`), THE UI SHALL render a minimum 3dp `#FF9900` high-contrast focus border and 1.05x scale transition on the active surface.
- **REQ-3 (Multi-Modal Voice + Visual Pedagogy):** WHEN a diagnostic step is selected or triggered via the Voice Explain action, THE system SHALL synthesize spoken coaching via Android TTS while highlighting the syntax diff and interactive 3-option remote quiz on screen.
