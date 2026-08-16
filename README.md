# Stream

Stream is a frictionless Android brain-dump: launch, type, and leave. Capture is the launcher screen, the editor is focused immediately, and saving never requires a button.

## What it does

- Opens directly to a full-screen Capture editor with the keyboard requested immediately.
- Stamps an entry only when its first text arrives, using `THU AUG 13 · 14:32:07` formatting.
- Writes raw text to `filesDir/draft_buffer.txt` after a 275 ms debounce.
- Upserts a non-blank entry into Room after 2 seconds idle and synchronously at `onStop()`, then removes the committed draft.
- Never saves empty or whitespace-only notes; clearing an existing entry restores its previous saved text.
- Restores a newer non-blank draft on process restart and labels it `recovered unsaved text`.
- Swipes right to a reverse-chronological, day-grouped Log and left to Capture.
- Reopens entries for editing without changing their original creation timestamp.
- Moves entries into recoverable Trash, with confirmed per-entry deletion and a confirmed Empty Trash action.
- Searches by content or jumps to an ISO date (`YYYY-MM-DD`).
- Exports and merges timestamp-deduplicated JSON, including Trash state, through Android's document picker.
- Provides a static **Capture** launcher shortcut (long-press the app icon).

There is no service, worker, alarm, save button, splash activity, title, tag, or folder system.

## Build

The project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose, and Room.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Docker Compose development

Install Docker Engine with the Docker Compose v2 plugin first. The Docker
environment contains JDK 17, Android API 35, Build Tools 35.0.0,
and Platform-Tools. Building the image downloads those tools and accepts the
Android SDK licenses required by the pinned packages.

Build the development image once:

```bash
docker compose build android-dev
```

Start a persistent development container and open a shell:

```bash
docker compose up -d android-dev
docker compose exec android-dev bash
```

The repository is mounted at `/workspace`. Gradle and Android user caches are
kept in named volumes, and the entrypoint matches the container user to the
owner of the repository so generated host files are not root-owned.

Run all Linux checks either in the persistent container:

```bash
docker compose exec android-dev ./build-and-test.sh --linux-only
```

or as a disposable one-shot service:

```bash
docker compose run --rm android-check
```

Stop the development container without deleting caches:

```bash
docker compose down
```

To also delete the Gradle and Android cache volumes:

```bash
docker compose down --volumes
```

Phone USB access stays on the Linux host. After the container builds the APK,
wait until any open Stream capture says `saved`, leave the app, and run:

```bash
adb devices -l
adb install -r -t -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.seif.stream/.MainActivity
```

The replace install preserves compatible existing app data. The helper does not
silently uninstall a differently signed app because uninstalling would erase
its local notes.

## Export format

Exports are UTF-8 JSON documents with a format/version marker and an array of timestamped entries:

```json
{
  "format": "stream",
  "version": 2,
  "exportedAt": "2026-08-13T14:32:07Z",
  "entries": [
    { "timestamp": 1786631527000, "text": "An active thought" },
    {
      "timestamp": 1786631427000,
      "text": "A recoverable thought",
      "trashedAt": 1786631627000
    }
  ]
}
```

Import accepts Stream JSON versions 1 and 2. It uses the timestamp as the identity, preserves version 2 Trash state, and leaves an existing active or trashed entry unchanged when the same timestamp appears in a file.
