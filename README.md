# Scalara

[![CI](https://github.com/OhMyDitzzy/Scalara/actions/workflows/ci.yml/badge.svg)](https://github.com/OhMyDitzzy/Scalara/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Scalara is a rootless Android screen resolution changer. It lets you change
your device's display resolution without root, using the
`WRITE_SECURE_SETTINGS` permission and the `WindowManager` APIs Android uses
internally to resize the display.

## How it works

Android exposes a way to override the display's rendered size
(`WindowManagerInternal.setForcedDisplaySize`, the same mechanism behind
`adb shell wm size`) without requiring root. Calling it from a third-party
app requires the `WRITE_SECURE_SETTINGS` permission, which has a
`signature|privileged` protection level — it can't be granted through the
normal runtime permission dialog. Instead, it's granted once via ADB (or a
shell-access tool like Shizuku):

```bash
adb shell pm grant id.ditzzy.scalara android.permission.WRITE_SECURE_SETTINGS
```

Once granted, Scalara can change the resolution directly, without root and
without a persistent ADB/computer connection.

## Features
- Coming soon!

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

## Versioning

Release builds derive `versionName` from the most recent git tag (e.g. tag
`v1.2.0` → versionName `1.2.0`) and `versionCode` from the total commit count,
so it always increases. See [`app/build.gradle.kts`](app/build.gradle.kts) for
the exact logic. Local/dev builds without a git history fall back to
`0.0.0-dev`.

Tags follow [Semantic Versioning](https://semver.org/): `vMAJOR.MINOR.PATCH`.

## Signing a release build locally

Release builds are unsigned unless signing credentials are supplied. To sign
locally:

1. Copy [`release.properties.example`](release.properties.example) to
   `release.properties` (this file is gitignored — never commit it).
2. Fill in your keystore path and credentials.
3. Run `./gradlew assembleRelease`.

In CI, the same values come from repository secrets instead — see
[CONTRIBUTING.md](CONTRIBUTING.md) for the full list.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
before opening a PR, and check existing [issues](../../issues) first.

## License

Licensed under the [Apache License 2.0](LICENSE). See also [NOTICE](NOTICE).
