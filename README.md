# Stream

Stream is a frictionless Android brain-dump: launch, type, and leave. Capture is the launcher screen, the editor is focused immediately, and saving never requires a button.

## What it does

- Opens directly to a full-screen Capture editor with the keyboard requested immediately.
- Stamps an entry only when its first text arrives, using `THU AUG 13 · 14:32:07` formatting.
- Writes raw text to `filesDir/draft_buffer.txt` after a 275 ms debounce.
- Upserts the entry into Room after 2 seconds idle and synchronously at `onStop()`, then removes the committed draft.
- Restores a newer non-empty draft on process restart and labels it `recovered unsaved text`.
- Swipes left to a reverse-chronological, day-grouped Log and right to Capture.
- Searches by content or jumps to an ISO date (`YYYY-MM-DD`).
- Exports and merges timestamp-deduplicated JSON through Android's document picker.
- Provides a static **Capture** launcher shortcut (long-press the app icon).

There is no service, worker, alarm, save button, splash activity, title, tag, or folder system.

## Build

The project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose, and Room.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Export format

Exports are UTF-8 JSON documents with a format/version marker and an array of timestamped entries:

```json
{
  "format": "stream",
  "version": 1,
  "exportedAt": "2026-08-13T14:32:07Z",
  "entries": [
    { "timestamp": 1786631527000, "text": "A captured thought" }
  ]
}
```

Import uses the timestamp as the identity and leaves an existing entry unchanged when the same timestamp appears in a file.
