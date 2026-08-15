# Contributing to Scalara

Thanks for taking the time to contribute!

## Development setup

1. Fork and clone the repo.
2. Open the project in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
3. Before pushing, run:
   ```bash
   ./gradlew lintDebug testDebugUnitTest
   ```
   The same checks run automatically in CI on every PR — running them
   locally first saves a round trip.

> **Windows:** this repo currently ships only the Unix `gradlew` script.
> Android Studio will run Gradle fine without it, but if you need the
> command-line wrapper, generate it once with `gradle wrapper` (using any
> local Gradle install) — this recreates `gradlew.bat` matching the version
> pinned in `gradle/wrapper/gradle-wrapper.properties`.

> **Testing resolution-change functionality:** the app builds and installs
> normally without any special setup, but the core feature requires
> `WRITE_SECURE_SETTINGS` to actually be granted on your test device/emulator
> — see [How it works](README.md#how-it-works) in the README. Without it,
> expect a `SecurityException` when the app attempts to change the
> resolution.

## Project conventions

- **Pure Java + Android Views.** This project intentionally does not use
  Kotlin or Jetpack Compose. Please keep new code in Java using the existing
  View/ViewBinding patterns.
- **Dependencies go through the version catalog.** Add new libraries to
  [`gradle/libs.versions.toml`](gradle/libs.versions.toml) rather than
  hardcoding a `group:artifact:version` string directly in
  `app/build.gradle.kts`.
- **Comments:** only where they add real explanatory value (e.g. non-obvious
  "why"). Avoid decorative separators or comments that just restate the code.
- **Commits:** write clear, descriptive commit messages. Small, focused PRs
  are easier to review than large ones.

## Submitting changes

1. Create a branch from `main`.
2. Make your changes, with tests where it makes sense.
3. Open a pull request using the provided template. Link any related issue.
4. A maintainer will review; CI must pass before merge.

## Reporting bugs / requesting features

Please use the [issue templates](.github/ISSUE_TEMPLATE/) — they collect the
information needed to triage quickly (repro steps, device/OS, logs, etc.).

## Release process (maintainers)

Releases are cut by pushing an annotated tag:

```bash
git tag -a v1.2.0 -m "v1.2.0"
git push origin v1.2.0
```

This triggers [`release.yml`](.github/workflows/release.yml), which builds a
signed release APK and publishes it as a GitHub Release with auto-generated
release notes.

### Required repository secrets

The release workflow needs these secrets configured under
**Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Your release keystore file, base64-encoded (`base64 -w0 release.keystore`) |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias within the keystore |
| `SIGNING_KEY_PASSWORD` | Key password |

Without these, `release.yml` fails fast with a clear error rather than
silently producing an unsigned APK. The manual [`build.yml`](.github/workflows/build.yml)
workflow, by contrast, degrades gracefully to an unsigned release build with
a warning if secrets are missing, since it's meant for quick ad-hoc builds.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating, you agree to uphold it.
