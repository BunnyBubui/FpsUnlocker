<div align="center">

<img src="icon.svg" width="96" alt="FPS Unlocker icon" />

# FPS Unlocker

**Tune your games' frame rate on Android 11+ — no root required.**

Find a game's config file, change its FPS value safely with automatic backups, and check the real result with a floating FPS meter.

[![Build APK](https://github.com/BunnyBubui/FpsUnlocker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/BunnyBubui/FpsUnlocker/actions/workflows/build-apk.yml)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](../LICENSE)

**English** · [ภาษาไทย](../README.th.md)

</div>

---

## Download

Test APKs are available in [GitHub Actions artifacts](https://github.com/BunnyBubui/FpsUnlocker/actions/workflows/build-apk.yml). Debug builds use a local test key and may require uninstalling an older debug installation. Distribution releases require a private signing key; see [signing instructions](SIGNING.md).

The app is available in **English** and **Thai** and follows your phone's language.

## What it does

| | Feature | What you get |
|---|---|---|
| 🎮 | **Game library** | Every game on your phone with its real icon, publisher, package name and engine (Unity, Unreal, Cocos, Godot…). Search, filter, and see which games have data you can scan. |
| 🔍 | **Deep config scan** | Walks the game's `Android/data` folder sub-folder by sub-folder, reads small files of any extension, and lists keys that look FPS-related. With root Shizuku it also scans `/data/data`, where most games keep their settings. |
| ✍️ | **Safe editing** | Edits only the value (INI, JSON and Android XML preferences), shows a before/after diff for you to confirm, backs up the original the first time, and reads the file back to verify. |
| 📈 | **Floating FPS meter** | Draggable overlay that measures the frames the game actually puts on screen, with average and minimum. |
| 🔒 | **System-level control** | Lock the display at its highest refresh rate, or use Android 13+ Game Mode to hold the game's FPS steady. Works even when a game's files can't be edited. |
| ♻️ | **Backups and drift check** | One list of every backup across games with one-tap restore. The app tells you when a game overwrites your setting and lets you re-apply it. |
| 📖 | **Built-in setup guide** | Step-by-step Shizuku pairing, with fixes picked for your phone brand (ColorOS, HyperOS/MIUI, One UI, Flyme). |

## Be realistic about what it can do

This app edits **plain-text** settings files. It is honest about the limits:

- **Many games encrypt their settings or store them in `/data/data`.** Without root, Android doesn't let any app read `/data/data`. When a game's files are encrypted, the app says so and **does not try to break the encryption**.
- **No app can push a game above the FPS cap the game sets for itself** through system settings alone. Android's Game Mode can only *lower* or steady FPS. The display lock only stops the screen from dropping its refresh rate.
- **FPS above your display's refresh rate won't look smoother.** It only adds heat and battery drain. The app warns you when you pick a value above your screen's maximum.
- **Online games may forbid file edits** in their terms of service, and anti-cheat systems can detect them. Try a secondary account first. You are responsible for how you use this app.

The FPS meter is the honest judge: check it before and after every change.

## Requirements

- Android **11 or newer**
- **[Shizuku](https://shizuku.rikka.app/)**, started through Wireless debugging (no root) or with root. Root unlocks scanning `/data/data`.
- On Android 11–12 you can use the system folder picker (SAF) for a single game instead of Shizuku.

## Quick start

1. Install **Shizuku** and **FPS Unlocker**.
2. Open FPS Unlocker → **Setup & Guide** and follow the six steps. If Shizuku says *"the permission of adb is limited"*, the highlighted card for your brand shows the fix.
3. **Games** → tap your game → **Tune FPS** → **Scan**.
4. Tap a found key, check the file with **View file**, pick a target FPS and tap **Apply**. Review the diff and confirm.
5. Start the **FPS meter**, launch the game, and see whether the number changed.

## How it works

```text
Compose UI ──► ViewModel ──► FileAccess ──► Shizuku UserService (runs as shell/root)
                   │                        └─► SAF DocumentFile (Android 11–12)
                   ├──► ConfigEditor     regex-based value replace, keeps formatting
                   ├──► GameRepository   PackageManager + APK native-lib engine detection
                   └──► FpsOverlayService ──► FpsMeter (dumpsys SurfaceFlinger)
```

- **File access**: Shizuku starts `FileService` in its own process with shell (uid 2000) or root (uid 0) rights. Copies write *into* the existing file, so the owner and permissions don't change. This matters for `/data/data`.
- **Engine detection** looks for libraries such as `libunity.so`, `libUE4.so` or `libcocos*.so` inside the APK and its splits.
- **FPS meter**: `dumpsys SurfaceFlinger --latency <layer>` returns present timestamps for the game's surface. FPS = (frames in the last second − 1) / time span. If that isn't available, it falls back to the global page-flip counter (`service call SurfaceFlinger 1013`).

## Build from source

You don't need a computer. Every push to `main` builds an APK with GitHub Actions and publishes it to Releases.

To build locally: install Android Studio (JDK 17), open this folder, then run:

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the Android-generated local debug key. Release signing uses private environment settings and never a key committed to this repository. See [signing instructions](SIGNING.md) before distributing an APK.

## Project structure

```text
app/src/main/
├─ aidl/…/IFileService.aidl        Binder interface for the Shizuku file service
├─ java/com/fpsunlocker/tool/
│  ├─ App.kt, MainActivity.kt
│  ├─ shizuku/   ShizukuManager, FileService (UserService)
│  ├─ data/      FileAccess, ConfigEditor, GameRepository, Publishers, DisplayInfo
│  ├─ overlay/   FpsOverlayService, FpsMeter
│  └─ ui/        MainScreen, GamesTab, TuneTab, TuneExtras, SetupTab, Theme, Components
└─ res/values/strings.xml          English (default)
   res/values-th/strings.xml       Thai
```

## Translations

All text lives in `res/values/strings.xml`. To add a language, copy that file into `res/values-<code>/strings.xml` (for example `values-vi`), translate the values, keep every `%1$s` / `%1$d` placeholder, and open a pull request.

## Contributing

Bug reports and pull requests are welcome. When you report a problem with a game, please include your phone model, Android version, how Shizuku was started (adb or root), the game's package name, and the Scan summary line from the Log.

## License

[MIT](../LICENSE) © 2026 BunnyBubui

This project isn't affiliated with Shizuku, Google, or any game publisher. Game names and trademarks belong to their owners.

## Portfolio review

See [portfolio notes](PORTFOLIO.md) for scope, attribution and local configuration.
