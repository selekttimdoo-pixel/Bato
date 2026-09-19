# VIPLA BATO Independent Android Runtime — M0 scaffold

Status: source scaffold only. Not built or APK-verified in this environment.

## M0 implemented in source
- Native Android Kotlin + Jetpack Compose
- Room/SQLite local persistence
- Immutable-ish STENO event rows
- Timestamped events
- Segment gap: 120s
- Session gap: 600s
- Timeline UI
- No AI provider required

## Still to implement
- JSON export
- exact search UI
- migrations from current Drive STENO / FAST GRAPH / lexicon / grammar
- semantic graph tables
- optional AI provider adapter
- audio capture / transcription
- backend sync

## Build requirement
Open in Android Studio with Android SDK 35 installed and build `assembleDebug`.
This source has not been compiled in the current container because Android SDK/Gradle are not available here.
