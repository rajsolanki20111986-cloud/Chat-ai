# Chat AI — Version 1

A Kotlin Android assistant starter with a minimal dark interface, configurable OpenAI-compatible chat endpoint, speech input/output, local memory, optional accessibility screen text and taps, and optional notification capture.

## Build

GitHub Actions runs `assembleDebug` on pushes to `main` and manual dispatch. Download the `Chat-AI-debug` artifact from the completed workflow.

## First launch

1. Open **Settings** in Chat AI.
2. Enter an HTTPS OpenAI-compatible chat completions endpoint (including `/v1/chat/completions`), model name, and API key.
3. Grant microphone permission for voice input.
4. Enable Chat AI under Android Accessibility settings only if you want screen text reading and requested taps.
5. Enable notification access in Android settings only if you want notification capture.

API keys are stored in app preferences on this starter build; use a dedicated backend for production-grade secret protection. Do not share APKs containing personal credentials.

## Important limitations

This first foundation uses Android speech recognition and text-to-speech, not a low-latency full-duplex realtime audio model. Accessibility can read text exposed by an app and click accessible controls; it cannot guarantee perfect understanding or taps in every app/game. Reading video imagery requires a vision-capable model and frame capture, which are not yet implemented. Autonomous messaging/calling is not enabled; review and confirm external actions. Test builds before relying on the app.
