<div align="center">
  <img src=".github/assets/logo.png" width="96" height="96" alt="Scalara logo">

  <h1>Scalara</h1>

  <p><strong>A rootless Android screen resolution &amp; DPI changer.</strong></p>

  <p>
    <a href="https://github.com/OhMyDitzzy/Scalara/actions/workflows/ci.yml"><img src="https://github.com/OhMyDitzzy/Scalara/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
    <a href="https://github.com/OhMyDitzzy/Scalara/actions/workflows/release.yml"><img src="https://github.com/OhMyDitzzy/Scalara/actions/workflows/release.yml/badge.svg" alt="Release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
    <a href="https://github.com/OhMyDitzzy/Scalara/releases/latest"><img src="https://img.shields.io/github/v/release/OhMyDitzzy/Scalara?include_prereleases" alt="Latest release"></a>
    <br>
    <img src="https://img.shields.io/badge/minSdk-24%20(Android%207.0)-3DDC84?logo=android&logoColor=white" alt="minSdk 24">
    <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17">
    <img src="https://img.shields.io/badge/UI-Views%20%2B%20Material%203-757575" alt="Material 3 Views">
    <a href="https://github.com/OhMyDitzzy/Scalara/issues"><img src="https://img.shields.io/github/issues/OhMyDitzzy/Scalara" alt="Open issues"></a>
  </p>
</div>

Scalara changes your Android device's display resolution and density (DPI)
**without root**, using the `WRITE_SECURE_SETTINGS` permission and the same
hidden `WindowManager` APIs Android itself uses behind `adb shell wm size` /
`wm density`. No root, no persistent PC connection, no reboot — grant the
permission once (via ADB or [Shizuku](https://shizuku.rikka.app/)) and Scalara
does the rest, entirely on-device.

## How it works

Android exposes a way to override the display's rendered size and density
(`IWindowManager.setForcedDisplaySize` / `setForcedDisplayDensityForUser`,
the exact mechanism behind `adb shell wm size` and `wm density`) without
requiring root. Scalara reaches this hidden API via reflection
(`DisplayResolutionController`), the same approach the shell command itself
uses internally.

The catch: calling it from a third-party app requires the
`WRITE_SECURE_SETTINGS` permission, which has a `signature|privileged`
protection level — it can never be granted through the normal runtime
permission dialog. Scalara supports two ways to grant it once, up front,
during first-run setup:

**Option A — ADB.** Connect your device to a computer and run:

```bash
adb shell pm grant id.ditzzy.scalara android.permission.WRITE_SECURE_SETTINGS
```

**Option B — [Shizuku](https://shizuku.rikka.app/).** If you'd rather not
plug into a computer every time, Shizuku gives Scalara the same permission
through a privileged, on-device broker instead. Once Shizuku itself is
running (via its own one-time ADB/wireless-debugging/root activation),
Scalara binds a small privileged helper service
(`MyUserService`, launched through Shizuku's `bindUserService` API) that runs
`pm grant … WRITE_SECURE_SETTINGS` **on your behalf, on-device** — no typing
ADB commands, no computer needed for this step. The setup wizard
(`SetupActivity`) walks through requesting the Shizuku permission and
triggering this grant with a couple of taps.

Either way, once granted:
- Scalara can change resolution and density directly, instantly, without root
  and without a persistent ADB/computer connection.
- The grant is checked every time the app resumes (`MainActivity`'s
  permission guard); if it's ever revoked — by the system, by `pm revoke`, or
  because the Shizuku service was stopped — Scalara detects it and routes you
  back into setup rather than failing silently mid-action.

## Features

### 🖥️ Resolution & density control
- Change screen **resolution** (width × height) and **density (DPI)**
  independently, applied instantly — no reboot.
- **Reset to default** with one tap, restoring the device's real physical
  resolution and density at any time.
- Read-only **default resolution card** on the home screen, always showing
  your device's true (unforced) resolution and DPI for reference.

### 🧪 "Try it out" — safe timed preview
- Preview any resolution/DPI combination for a short window (**default 10s**,
  configurable 3–60s in Settings) before committing to it.
- Runs as a **foreground service** (`PreviewRevertService`) with a live
  countdown notification, so it reliably reverts even if you leave the app,
  rotate the screen, or it gets backgrounded — a plain in-app timer couldn't
  guarantee that.
- **"Revert now"** action right from the notification if you want to bail
  out early.

### 📌 Presets
- Save named width/height/DPI combinations for instant reuse later.
- Four independent actions per preset, matching real workflows:
  **Apply** (save + apply immediately), **Try it out** (preview only, nothing
  saved), **Save** (just save it), or apply/delete an existing one from its
  own options sheet.

### ⚠️ Dangerous-resolution guard
- Before applying or previewing a value that deviates **more than 50%** from
  your device's default width, height, *or* DPI, Scalara shows a confirmation
  dialog first — the exact kind of change that can otherwise leave a device
  hard to see or navigate.
- Toggleable in Settings, on by default.

### 🔒 Encrypted preset export/import
- Export a single preset or your entire saved list to a password-protected
  `.scl` file, and import it back later or on another device.
- Real encryption, not obfuscation: **AES-256-GCM** with a
  **PBKDF2-HMAC-SHA256** key (600,000 iterations, per OWASP's current
  guidance) derived from your password and a random per-file salt. See
  [Preset export format](#-preset-export-format-scl) below.
- Import supports **merge** (add to your existing presets) or **replace**
  (start fresh from the imported file), with a preview of what's inside
  before anything is committed.

## Getting started
### Prerequisites

- Android Studio (recent stable) or the command line with JDK 17 if you want to build scalara locally
- An Android device or emulator running API 24+
- ADB (or [Shizuku](https://shizuku.rikka.app/)) to grant `WRITE_SECURE_SETTINGS` — see [How it works](#how-it-works)

### Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### Run tests & lint

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
before opening a PR, and check existing [issues](../../issues) first.

### Adding a translation

Scalara's in-app language picker (Settings → Language) never hardcodes which
languages are available — a Gradle task
(`generateLocalesList`, in [`app/build.gradle.kts`](app/build.gradle.kts))
scans `app/src/main/res/` at build time for `values-<lang>` directories and
generates the picker's option list from whatever it finds. Currently shipped:
🇬🇧 English (`values/`, the default) and 🇮🇩 Bahasa Indonesia (`values-in/`).

To add a new language, copy
[`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml)
into a new `app/src/main/res/values-<tag>/strings.xml` (e.g. `values-es/` for
Spanish, `values-pt-rBR/` for Brazilian Portuguese) and translate every
string. That's it 

## License

Licensed under the [Apache License 2.0](LICENSE). See also [NOTICE](NOTICE).