# Notes — Encrypted Journal

A privacy-first, offline journal app for Android. Every entry, attachment and
setting stays on the device: the database is encrypted with SQLCipher and the
app is unlocked with a password or biometrics.

## Features

- **Encrypted storage** — SQLCipher database plus encrypted media files, unlocked
  via password or biometric authentication.
- **Journaling** — multi-page entries with titles, moods, tags, custom text color
  and per-page styling.
- **Timeline & calendar** — browse entries by day, jump to a date, and see
  Turkish important days highlighted.
- **Rich attachments** — photos, videos, and voice recordings captured in-app.
- **Dual camera** — simultaneous front/back capture for photos and video.
- **Reminders & alarms** — one-off or repeating reminders, with notifications
  that can deep-link back into an entry.
- **Location** — attach and view a location in an in-app map view.
- **Extras** — QR scanning/generation, RSS reader, offline Turkish–English
  translator, scientific calculator, Markdown rendering, PDF export.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Room + SQLCipher · AndroidX Security &
Biometric · CameraX · Media3 (ExoPlayer/Transformer) · ML Kit barcode scanning ·
ZXing · Coil

## Building

Requires Android Studio with JDK 11+.

```bash
# point to your Android SDK
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug
```

The app targets `minSdk 36` / `compileSdk 37`.

## License

No license has been added yet. Add one before accepting third-party
contributions.
