# Contributing to Cindercast

Thank you for helping. Cindercast is a free, open-source Stalker / Ministra client for the Fire TV, MIT licensed. Fork it, change
it, send it back or keep your own copy.

## Get it building

You need **Android Studio** (its bundled JDK works) and the Android SDK (compile SDK 37). The Gradle wrapper pins Gradle 9.5.0.

```bash
export ANDROID_HOME=~/Library/Android/sdk                                            # your SDK path
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"      # Android Studio's JDK (macOS path)
./gradlew :provider:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same tests on every push and pull request.
Release builds are signed with the maintainer's key, which is not in the repo: your own builds are debug builds.

## Layout

| Folder | What it holds |
|---|---|
| `provider/` | Pure Kotlin Stalker client: handshake, channels, VOD, guides, link creation. No Android dependencies; unit tested. |
| `app/` | The Fire TV app: Jetpack Compose for TV, Media3 ExoPlayer, Room. Debug-only tools live in `app/src/debug`. |
| `probe/` | A small command-line protocol probe (needs a real portal; see `docs/`). |
| `prototype/` | The React design prototype the UI was ported from. |
| `docs/` | Plans, milestones, protocol notes, the testing guide, run logs. |
| `scripts/` | Release build, secret scan, a fake portal, helpers. |

## Working without a provider

`scripts/fake_portal.py` is a small fake Stalker portal that serves test data, so the app can be developed and unit-tested without
a real subscription. On macOS run it with `/usr/bin/python3` (the firewall blocks other Python builds from listening). A debug build
installed side by side (`./gradlew :app:assembleDebug -PcheckSuffix=true`) has its own empty storage.

## Testing on a real Fire TV

[docs/TESTING_ON_FIRE_TV.md](docs/TESTING_ON_FIRE_TV.md) describes driving the stick from a computer over ADB with
`.claude/skills/firetv-drive/scripts/ftv.py`: read where focus is after each key, time things from the redacted log, and the
gotchas learned (screensaver, launch timing, never chaining Select after moves, zsh quoting). Screenshots are blocked in the app
(`FLAG_SECURE`); debug builds have a test-only switch described there.

## Rules that matter

1. **No secrets in the repo, ever:** no portal addresses, MAC addresses, tokens, account details, device serials or LAN IPs, not in
   code, docs, tests, logs or commit messages. Use placeholders (`<PORTAL_URL>`, `<EMU_MAC>`, `<FIRETV_IP>`). Local settings go in
   gitignored files. Run the scan before committing; to run it on every commit, create `.git/hooks/pre-commit` that runs
   `bash scripts/secret_scan.sh --staged` (the script reads your own values from `~/.config/firetv-iptv/secrets.env`, see its header).
2. **Logging is redacted** (`Redactor` in `provider/`); debug logging lives only in `app/src/debug`. Never log stream addresses.
3. **Never weaken TLS** (no trust-all, no user-added certificates). Cleartext HTTP is allowed because portals need it.
4. **Clean room:** do not decompile or copy code from proprietary IPTV apps. Use public protocol behaviour and open-source clients
   for reference only, without copying their code.
5. **D-pad first:** every screen must work with arrows, Select and Back, with reliable focus. Compose sizes are written as dp where
   1 dp equals 1 pixel of a 1920-pixel canvas (`OpenStreamTheme` maps it), so use the prototype's pixel values.
6. **Comments:** generous, impersonal, saying why. Put logic in plain Kotlin where possible and unit test it.
7. **Keep the app light:** the target stick has a 32-bit userland and little memory; avoid big bitmaps and unbounded lists.

## Sending changes

1. Fork the repository and make a branch.
2. Make the change with tests; run `./gradlew :provider:test :app:testDebugUnitTest :app:assembleDebug`.
3. Open a pull request to `main` describing what and why, and what you tried on a device if you could. CI must pass.

Bug reports: open an issue and describe the steps and your Fire OS version, with no portal details. Ideas and the current to-do list:
[docs/TODO.md](docs/TODO.md).
