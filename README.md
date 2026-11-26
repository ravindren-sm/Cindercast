# Cindercast

[![CI](https://github.com/ravindren-sm/Cindercast/actions/workflows/ci.yml/badge.svg)](https://github.com/ravindren-sm/Cindercast/actions/workflows/ci.yml)

A free, open-source IPTV player for **Amazon Fire TV** (and other Android TV devices) that connects to your own
**Stalker / Ministra (MAG) portal** subscription. It covers live TV and on-demand movies and shows, built for the remote:
D-pad navigation, reliable focus, and fast paging through very large catalogs.

Cindercast bundles **no channels, no content and no provider**. You bring the portal address and the registered MAC
address from your own subscription. There are no accounts, analytics or ads (see [docs/PRIVACY.md](docs/PRIVACY.md)).

> Status: in active development (version 0.1.1) and tested daily on a Fire TV Stick 4K Plus. There is no published
> release yet; build it from source (below). The release plan is in [docs/RELEASE_PLAN.md](docs/RELEASE_PLAN.md).

## What it does

- **Live TV**: channel groups, channels with what's on now and next, channel up/down and number entry, audio and subtitle
  choice, Back returns to the channel you were watching.
- **TV Guide**: now and next for a group or for your favourites.
- **Sports**: the sports groups and on-demand sports categories in one place, plus a games finder for NFL, college
  football, soccer, cricket and Formula 1 that reads the guide in the background and keeps the results between launches,
  with a muted live preview beside channel lists.
- **Movies and Shows**: categories, poster grids, detail pages, seasons and episodes, several versions of one episode
  (language, quality), ratings.
- **Search** across channels, movies and shows, with filters (language, genre, category, rating) and saved defaults.
- **Saved searches**: name a combination of genres, languages, rating and year once and open it from the Saved tab, with
  no typing.
- **Continue watching**, resume where you stopped, the next episode offered at the end.
- **Favourites** for channels, movies and shows; pinned categories on Home.
- **Profiles**, including Kids profiles with a PIN for Settings.
- **Secure window**: the app's screen is hidden from screenshots and screen recording.

What is built and what is still missing, screen by screen: [docs/FEATURE_COVERAGE.md](docs/FEATURE_COVERAGE.md).

## Build

Requirements: macOS, Linux or Windows with **Android Studio** (its bundled JDK is fine) and the Android SDK.
The Gradle wrapper pins Gradle 9.5.0.

```bash
export ANDROID_HOME=~/Library/Android/sdk            # where your Android SDK lives
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # Android Studio's JDK (macOS path)
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a Fire TV

1. On the Fire TV: Settings > My Fire TV > Developer options: turn on **ADB debugging** (and allow apps from unknown
   sources if asked).
2. From your computer, on the same network:

   ```bash
   adb connect <FIRETV_IP>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Open **Cindercast** from Your Apps & Channels. On first launch, enter your portal address and the MAC address
   registered with your provider. They are stored on the device only, encrypted with a key from the Android Keystore.

Most providers allow **one connection per MAC address**: close any other app using the same subscription first.

**Just want to install the app?** Use the ready-made APK and the step-by-step guide in
[docs/INSTALL_ON_FIRE_TV.md](docs/INSTALL_ON_FIRE_TV.md) (Downloader or ADB). **Want to help?** See [CONTRIBUTING.md](CONTRIBUTING.md).

## Testing on the device

The app is tested on a real Fire TV by driving the remote over ADB and reading where focus lands after every key
(`.claude/skills/firetv-drive/scripts/ftv.py`), with timings taken from the debug build's redacted log. The full guide,
with the gotchas learned along the way (screensaver, launch timing, why Select is never chained after moves, zsh quirks,
reading the players): [docs/TESTING_ON_FIRE_TV.md](docs/TESTING_ON_FIRE_TV.md). Test plan and run logs:
[docs/UX_TEST_PLAN.md](docs/UX_TEST_PLAN.md), [docs/ux-runs/](docs/ux-runs/).

## Project layout

| Path | What it is |
|---|---|
| `app/` | The Android TV app (Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room). |
| `provider/` | The Stalker/Ministra protocol client as a plain Kotlin library, tested without Android. |
| `probe/` | A command-line probe used to check a portal's behaviour step by step during development. |
| `scripts/` | Development helpers: a fake portal for offline testing, a secret scan run before every commit, build scripts. |
| `docs/` | Plans, protocol notes, UX test plan and run logs, privacy and release notes. |
| `prototype/` | The original web prototype of the interface (React), kept for reference; the app's design came from it. |

## Clean room

The protocol client was written from public protocol behaviour and open-source clients used for reference only
(for example Kodi's `pvr.stalker`); no proprietary app was decompiled or inspected and no code was copied.
Third-party libraries and their licences: [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

## Licence

[MIT](LICENSE).
