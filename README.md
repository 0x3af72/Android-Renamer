# Renamer

A tiny Android app that batch-renames the photos already on your phone, in
place, using each photo's capture date.

## What it does

- Lists the images in your media library (DCIM, Pictures, Screenshots, ...) as a
  scrollable grid of thumbnails.
- A single text field at the top holds a name format, e.g.
  `Holiday Pics {DD} {MM} {YYYY}`.
- Tap photos to select them (or use All / None), then Rename selected. Each
  selected file is renamed in place based on its own date metadata.

### Supported format tokens

| Token    | Meaning          | Example |
|----------|------------------|---------|
| `{YYYY}` | 4-digit year     | 2026    |
| `{YY}`   | 2-digit year     | 26      |
| `{MM}`   | 2-digit month    | 05      |
| `{MMM}`  | short month name | May     |
| `{DD}`   | 2-digit day      | 31      |
| `{HH}`   | hour (24h)       | 14      |
| `{mm}`   | minute           | 09      |
| `{ss}`   | second           | 07      |

Photos that would collide (same date -> same name) automatically get a
` (1)`, ` (2)` ... suffix. The original file extension is always preserved.

## How the rename works

On Android 11+ apps cannot silently rename files they did not create. The app
uses `MediaStore.createWriteRequest`, which shows a single system consent dialog
listing the affected photos. After you approve, each file's `DISPLAY_NAME` is
updated through the `MediaStore`, which renames the actual file on disk.

## Building

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: Android SDK with platform 34 + build-tools, JDK 17+.
minSdk 30 (Android 11), targetSdk / compileSdk 34.
