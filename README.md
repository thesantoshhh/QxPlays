# QxPlays 🎬🎧

**QxPlays** is a powerful, fully offline video & music player for Android — clean dark UI, real gestures,
a real encrypted private space, and zero ads, zero network, zero tracking.

```
com.qxplays.player  ·  v1.0.0  ·  minSdk 24 (Android 7.0)  ·  targetSdk 35
```

---

## ✨ Features

### Media playback
- Plays every format the device's platform codecs support: MP4, MKV, WebM, 3GP, TS, AVI, MOV, FLV,
  MPG, WMV, OGV, MP3, M4A, AAC, FLAC, WAV, OGG/Opus, AMR, MIDI, AIFF, AC3/EAC3 (device-dependent) and more
- Video: **real gesture controls** — double-tap ±10s seek, horizontal swipe seek, vertical swipe for
  volume (right edge) & brightness (left edge), hold for 2× speed, single tap to toggle controls
- Rotate, sleep timer, playback speed (0.25×–4×), subtitles (sidecar `.srt` auto-detected + manual pick),
  picture-in-picture, play-audio-in-background, screen lock, real file details (codec/resolution/bitrate)
- Audio: full-screen player with **live music visualizer** (real FFT), **5-band equalizer with presets,
  bass boost, 3D surround, loudness enhancer**, queue management, shuffle/repeat/repeat-one, sleep timer
  (including "end of track"), favorites, resume positions, background playback with media notification
  and lock-screen controls

### Library
- Tabs: Videos · Audio · Folders · Playlists · Favorites · Recent
- Search, 8 sort modes, folder browsing (MediaStore), built-in storage browser (needs
  *All files access*), and **SAF folder picker** (add any folder — USB/cloud too — without extra permissions)
- Playlists: create / rename / delete / add / remove / reorder by queue
- Recent with per-file resume progress bars, real thumbnails & album art

### 🔒 Private Space (real encryption — no dummy security)
- You create your **own unique password** (never hardcoded, never stored — only a salted verifier:
  PBKDF2-HMAC-SHA256, **150 000 rounds**, random 16-byte salt)
- Files are **encrypted with AES-256-GCM** (random nonce per file, authenticated) and moved out of
  the public media index so they vanish from every other app
- Restore decrypts back into your library; change password re-encrypts everything
- Auto-lock on app close, session keys wiped on lock, backups disabled (`allowBackup=false`)
- No backdoor: a forgotten password means the only option is a destructive wipe

### Permissions (all optional, features degrade gracefully)
| Permission | Why |
|---|---|
| READ_MEDIA_VIDEO / READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE | scan & play your media |
| POST_NOTIFICATIONS | playback controls notification |
| RECORD_AUDIO | live music visualizer (never records/stores audio) |
| FOREGROUND_SERVICE / WAKE_LOCK / MODIFY_AUDIO_SETTINGS / VIBRATE | background playback, equalizer, haptics |
| MANAGE_EXTERNAL_STORAGE (optional) | built-in file browser over all storage |

---

## 📦 Install

Download `release/QxPlays-v1.0.0.apk`, copy it to your phone, and install it
(allow "install unknown apps" for your file manager/browser when asked).

The APK is signed with a dedicated release keystore (`keystore/qxplays-release.keystore`),
so every future update installs over the previous one.

## 🛠 Build from source (no Gradle, no network)

This project is deliberately built with a **pure AOSP toolchain** — `aapt2 → javac → d8 → zipalign → apksigner`.
There are **zero third-party dependencies**; the entire app is platform APIs.

Requirements (all standard Android SDK pieces):
- JDK 17, `aapt2`, `d8` (r8.jar), `zipalign`, `apksigner.jar`, `android.jar` (API 35)

Then simply:

```bash
bash build.sh
```

The script produces `release/QxPlays-v1.0.0.apk` and verifies the signature.

## 🧭 Architecture

```
app/src/main/java/com/qxplays/player/
├── PlaybackService.java   foreground service: MediaPlayer, MediaSession, notification,
│                          audio focus, equalizer/visualizer, sleep timer
├── Player.java            global playback state mirror + command facade
├── Vault.java / Crypto.java   private space (PBKDF2 + AES-256-GCM)
├── Library.java / MediaItem.java / LibraryData.java   MediaStore + file scanning
├── Playlists / History / Favorites / Thumbs          local persistence & caching
├── MainActivity.java      single-activity host, onboarding, navigation
├── HomeView / MediaLists  tabs, grids, mini-player
├── PlayerActivity.java    video player (gestures, PiP, subtitles)
├── AudioPlayerView.java   music player + visualizer + equalizer UI
├── PrivateView / SettingsView / SearchView / Browsers / Sheets / Ui
└── res/                   57 vector icons, themes, adaptive launcher icon
```

## 🔏 Privacy

- **No `INTERNET` permission at all** — the app physically cannot connect anywhere.
- No analytics, no ads, no account, no cloud. Everything stays on your device.

## ⚠️ Notes

- Format support follows the device's built-in codecs (same family of codecs every
  Android player uses). Very exotic codecs (e.g. DTS/AC3 on some devices) may not decode;
  that is a device limitation, not an app one.
- Built and signed for sideloading. If you ever publish to Google Play, generate a
  **new private keystore** and do not share it.

---
*QxPlays — your media, your rules.*
