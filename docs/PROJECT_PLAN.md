# Project plan: modern IPTV app for Fire TV Stick 4K Plus

Date: 2026-11-20. Status: Phase 0 (discovery). Nothing installed, no provider contact.

## Problem
StbEmu emulates a MAG set-top box (Stalker-style portal client) with a dated UI.
Goal: a remote-friendly Fire TV app that plays the same provider content with a
modern interface, without touching StbEmu.

## Verified vs unverified (as of this document)
| Item | State |
|---|---|
| Project folder is empty | Verified |
| Mac is Apple Silicon (arm64) per desktop app | Verified |
| Mac toolchain (Git, JDK, Android SDK, ADB, Android Studio) | Unverified: run scripts/discover_env.sh |
| Fire TV model AFTMA08C15, Android 11 (API 30), build RS8185.3898N | Verified via ADB (see docs/DEVICE_FACTS.md) |
| ABIs: armeabi-v7a only (32-bit, no 64-bit ABI) | Verified via ADB |
| Free storage about 0.9 GB available on /data (4.9 GB partition) | Verified via ADB |
| Fire OS 8.1.8.5 (build RS8185/3898) on Android 11 base | Verified via ADB (ro.build.version.name); Android 14 assumption was wrong |
| Provider uses a Stalker/MAG portal protocol | Hypothesis only |
| Provider offers M3U/Xtream/other API | Unknown |
| TLS on the portal, stream formats (HLS vs MPEG-TS), DRM | Unknown |

## Done conditions per phase
- Phase 0 done: toolchain present, ADB talks to the Fire TV, device facts recorded.
- Phase 1 done: an empty "hello" TV app builds on the Mac and launches on the Fire TV.
- Phase 2 done: a written verdict on how to authenticate and list/play content.
- Phase 3 done: one live channel plays on the Fire TV with logged buffering/error metrics.
- Phase 4 done: channel browse, search, favorites, EPG and remote navigation usable by the household.

## Phase 0: Environment setup
1. Run scripts/discover_env.sh on the Mac (read-only). Save redacted output locally.
2. Compare against the target toolchain below; list gaps; user approves each install.
3. Get <FIRETV_IP> from the user (Fire TV: Settings > My Fire TV > About > Network).
   Recommend a DHCP reservation in UniFi done by the user, not by Claude.
4. After approval: enable ADB debugging on the Fire TV (user action: Settings > My Fire TV >
   Developer Options > ADB debugging; Developer Options appears after tapping the
   device name 7 times in About). Then `adb connect <FIRETV_IP>:5555`; the TV shows an
   authorization prompt the user accepts.
5. Read-only device queries: `getprop ro.product.model`, `ro.build.version.sdk`,
   `ro.build.version.release`, Fire OS version property, `ro.product.cpu.abilist`,
   `df /data`. Do not dump `adb devices -l` or `getprop` wholesale into docs (serials).
   Do not run any command touching StbEmu's package (no pm clear/uninstall, no run-as).
6. Record verified facts in docs/DEVICE_FACTS.md (no serials, no IP).

### Target toolchain (proposed; install only after approval)
| Need | Proposal | Why |
|---|---|---|
| Package manager | Homebrew (if absent) | Standard on macOS |
| Git | Apple Command Line Tools git or `brew install git` | Version control |
| IDE + SDK manager | Android Studio (current stable), installed as a cask | Bundles a JDK (JBR) and SDK Manager; simplest path for Gradle/AGP |
| JDK | Use Android Studio's bundled JBR; add Temurin 17 only if CLI builds need it | AGP 8.x needs JDK 17+ |
| Android SDK | Installed: platform android-37.0, build-tools 36.0.0, Platform-Tools 37.0.3. Compile against 37; target Android 11 (API 30) on device | Device is API 30, so minSdk must be 30 or lower; compileSdk 37 satisfies "34+" |
| ADB | Platform-Tools from the SDK (`~/Library/Android/sdk/platform-tools`) | Same version as the SDK; avoids a second adb |
| App stack | Kotlin, Compose for TV, Media3 ExoPlayer | Native D-pad focus handling; HLS and MPEG-TS support |

## Phase 1: Working baseline
- Create the Android project (Gradle wrapper, version catalog), a single Activity with a
  Leanback launcher intent and a banner, shown as a Compose TV screen.
- Debug build installed over ADB as a new package id. Not touching StbEmu.
- Done: app launches on the Fire TV and responds to D-pad and Back.
- Status 2026-11-20: DONE. Verified on the device over ADB: cold launch OK, D-pad focus
  plus 3 select presses counted on screen, Back returns to the launcher, no crashes.
  Build: Gradle 9.5.0 wrapper, AGP 8.13.0, Kotlin 2.1.20, compileSdk 37.0, minSdk 25,
  armeabi-v7a only. Installed under the user's one-off exception to hard rule 9.
- Lesson: AGP 8.x needs the org.jetbrains.kotlin.android plugin; without it the Kotlin
  sources are skipped silently and the APK crashes with ClassNotFoundException.

## Phase 2: Provider compatibility probe (GATE; needs explicit approval first)
Purpose: find out what the provider speaks before any UI work.
- Step A, offline: write the secret-entry workflow (see CLAUDE.md). User fills
  ~/.config/firetv-iptv/secrets.env. No values shown to Claude.
- Step B, probe tool on the Mac (small script or JVM CLI), TLS validation on, redacted logs.
  Candidate paths to test, each against the user's own account:
  1. Stalker/MAG portal handshake, profile, channel list, create_link (needs the
     registered MAC and portal URL).
  2. Xtream-style `player_api` endpoints (needs username and password).
  3. M3U/XMLTV playlist URL if the provider issues one.
- Record per path: reachable, auth outcome, channel count, stream URL scheme, container
  (HLS/TS), token expiry, concurrent-session behavior, geo/IP restrictions.
- Capture nothing that includes secrets. Write findings to docs/PROVIDER_FINDINGS.md using placeholders.
- Risks to check before probing: provider terms of service on non-approved clients, one-device
  MAC binding (a probe from the Mac using the registered MAC may log out StbEmu or trigger
  a ban; confirm with the user before using the MAC), connection limits.
- Done: written verdict, one chosen protocol path, or a stop if none works.

## Phase 3: Minimal instrumented Fire TV player
- Media3 ExoPlayer screen playing one live channel via the Phase 2 path.
- Instrumentation: time-to-first-frame, rebuffer count/duration, error codes, codec info.
  Redacted logging only.
- Credentials entered on the TV via a settings screen, stored in Keystore-backed storage.
- Done: a channel plays for 30 minutes with metrics captured; decoder behavior on
  Fire TV Stick 4K Plus recorded (HEVC/H.264, audio formats, 4K).

## Phase 4: Modern TV interface
- Home with Continue Watching and Favorites; category browse; channel zapping with
  number entry and channel up/down; search; EPG grid if XMLTV/portal EPG is available.
- Focus and remote behavior reviewed on the LG TV at 10-foot distance.
- Polish (animation, theming) after functional v1.

## Risks
- Google Drive sync: this folder is under "My Drive". Git metadata, Gradle caches and build
  output inside Drive sync badly and can corrupt. Secrets must never sit in Drive.
- MAC binding can disrupt the working StbEmu profile during probing.
- Fire OS builds differ from stock Android; some Play-only APIs are absent. Use only Fire-safe APIs.
- ADB over Wi-Fi needs a stable IP; a DHCP reservation avoids reconnect failures.
- Provider may require a MAG-specific user agent or headers; compliance with the provider's terms is the user's call.

## Decisions
Resolved 2026-11-20:
- Repo location: `~/Developer/firetv-iptv`, outside Google Drive.
- Toolchain installed with approval: Android Studio, SDK, Gradle (Homebrew).
- Fire TV IP: known to the user and Claude; kept out of all repo files.
- UniFi API key: kept in the local secrets file. ADB debugging stays on.

Open (need user answer):
1. Approval to start Phase 2 (provider compatibility probe). Gate: no provider contact before this.
2. Whether to set a UniFi DHCP reservation for the Fire TV (user action, not Claude's).
