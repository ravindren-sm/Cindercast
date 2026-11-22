# CLAUDE.md: IPTV app for Fire TV Stick 4K Plus

Goal (resumed 2026-11-21): a clean, modern, open-source Stalker/MAG client for Fire TV,
free on GitHub, covering Live TV and VOD (movies and shows) without paywalls.
Status (2026-11-21): RESUMED by the user. TiviMate 5.3.3 proved the portal accepts a
third-party MAC-only Stalker client (user-reported: 5,100 live channels, 99,500 movies,
27,700 shows). Steps 4a-4c done: HANDSHAKE_ACCEPTED, PROFILE_AUTHORIZED (status 0, MAC only),
LIVE_CATALOG_LISTED (74 genres, 4,900 channels); 4d: LINK_CREATED; streams are HLS but the
edge returned 403 to the probe, also with MAG headers (User-Agent ruled out). Also 403
on the channel TiviMate plays. RESOLVED 2026-11-21: the debug player on the Fire TV plays
live TV (PLAYBACK_VERIFIED_ON_DEVICE); the 403 is Mac-specific. Mac probing stopped.
Step 4e (on device): VOD_CATALOG_LISTED: 164 categories, 104,000 items, shows inside type=vod.
Step 4f (on device): VOD_MOVIE_PLAYED and VOD_EPISODE_PLAYED (show > season > episode > file).
Step 4g: KEEPALIVE_VERIFIED (watchdog every 120 s; token valid with and without it over
~8 min; re-auth unit-tested only). M1 complete. Debug app logs to files/debug-log.txt (debug source set: start with
.DebugPlayerActivity; MainActivity is the real app shell). Ask before changing any device setting
(for example stay-awake), even temporarily. Playback unverified. Milestones: docs/MILESTONES.md. Findings: docs/private/PROVIDER_FINDINGS.md. Plan: docs/private/PHASE2_PROBE_PLAN.md
Repo: ~/Developer/firetv-iptv (outside Google Drive). Build with ./gradlew using
Android Studio's JBR as JAVA_HOME; wrapper pins Gradle 9.5.0 (AGP 8.x breaks on 9.6+).
UI source: the OpenStream prototype (docs/OPENSTREAM_INTAKE.md), ported to Compose for TV.
Full plan: docs/PROJECT_PLAN.md

## Hard rules
1. Never modify, uninstall, clear data from, or overwrite StbEmu on any device.
   Force-stopping StbEmu or TiviMate (`am force-stop`) before a live portal run is
   allowed when the user OKs it for that run (rule 12); it changes no app data.
2. Never contact the IPTV provider until the user approves Phase 2 in chat.
3. Never write credentials, tokens, portal URLs, registered MAC addresses, device
   serials, or the Fire TV IP into repo files, docs, logs, commits, command lines,
   or any file outside ~/.config/firetv-iptv/.
   Placeholders only in files: <PORTAL_URL>, <EMU_MAC>, <USERNAME>, <PASSWORD>, <FIRETV_IP>.
   Seeing values (user decision 2026-11-20, after a risk discussion): Claude may read
   secret values (secrets file, provider emails) when a task needs them. Claude still
   does not repeat them in chat unless the user asks, and never sends them anywhere
   except the provider during an approved NETWORK step.
   Fire TV IP: allowed in chat and in local commands.
4. Never disable TLS validation. Never change router/UniFi settings.
5. Ask before installing anything or changing system configuration.
6. Do not assume ADB exposes StbEmu private data or the provider protocol.
7. Do not build the full UI before provider compatibility is proven (Phase 2 gate).
8. Native macOS tools only. No Windows, WSL, or Docker.
9. Do not deploy an APK until the user approves Phase 3 in chat.
   Exception granted 2026-11-20: the Phase 1 hello app (io.github.ravindren_sm.openstream)
   may be installed. Phase 3 go-ahead 2026-11-21 ("build the debug player"): debug builds
   of the player may be installed for testing. Release builds still need user approval.

10. Zero secrets in git: never commit the portal URL, MAC, tokens, or raw captured
    portal responses. Local test config lives in gitignored files (dev-config*.json,
    local.properties) or ~/.config/firetv-iptv/; the app gets an on-device setup screen.
    A local pre-commit hook runs scripts/secret_scan.sh --staged (install it in a new
    clone: see the script header).
11. Clean room: never decompile or inspect proprietary APKs (TiviMate, StbEmu). For
    Stalker/Ministra quirks (handshake, get_profile, create_link, VOD pages and seasons,
    token refresh) use public protocol behavior and open-source clients such as Kodi's
    pvr.stalker for reference only. Do not copy their code (license compatibility).
12. Single active connection: before any live portal request, remind the user to stop,
    or with their OK force-stop via ADB, both StbEmu (com.mvas.stb.emu.pro) and TiviMate
    (ar.tvplayer.tv), so the MAC never has two concurrent sessions.
13. Debug-only diagnostics: OkHttp logging interceptor whose saved/shared output redacts
    MACs, tokens, portal hosts; structured Media3 error and codec/stream logging readable
    via adb logcat (stream URLs redacted). None of this in release builds.
14. Fire TV UX from the start: D-pad navigation, reliable focus, fast paging for large
    catalogs (about 100k movies, 28k shows), search, favorites.

## Before publishing on GitHub (open decisions)
- License: MIT, decided by the user 2026-11-21 (LICENSE added; the copyright line uses the git
  author name and can be changed). App name: OpenStream (decided 2026-11-21). A web search found no
  IPTV project with that name; one small unrelated Google Play podcast/radio app is called
  OpenStream; trademark databases were not checked. Package id: io.github.ravindren_sm.openstream
  (chosen 2026-11-21; was com.example.firetvhello for the debug app, so an older debug install on
  the Fire TV is a separate app that can be removed). Library and probe packages are
  io.github.ravindren_sm.openstream.provider and .probe.
- Review docs/ for provider-specific detail before making the repo public; the
  provider is never named, but findings describe its setup.

## Secret handling
- Secrets live outside this repo and outside Google Drive sync, in
  ~/.config/firetv-iptv/secrets.env (chmod 600), entered with scripts/save_secret.sh.
  Claude may read values when needed (hard rule 3) but prefers redacted probe output;
  values in Claude's context also land in session transcripts under ~/.claude/.
- Code reads secrets from the path in env var FIRETV_IPTV_SECRETS.
- On the device, the user enters portal details through an in-app settings screen.
  Store them sealed with AES-256-GCM under an Android Keystore key (AesGcmBox in :provider;
  EncryptedSharedPreferences is deprecated upstream, see docs/MILESTONES.md M2). Never in
  BuildConfig, resources, logs, crash reports, or the APK.
- Logging: a redaction helper is required before any network code logs anything.
  Redact URLs (keep scheme + path shape only), MACs, tokens, usernames.
- Probe output goes to the untracked probe_output/ directory, redacted.

## Conventions
- Language/stack (proposed, pending approval): Kotlin, Jetpack Compose for TV
  (androidx.tv), Media3 ExoPlayer, Gradle wrapper from Android Studio.
- Target device (verified via ADB, see docs/DEVICE_FACTS.md): Amazon AFTMA08C15,
  Fire OS 8.1.8.5 on the Android 11 base (API 30), 32-bit ABI only (armeabi-v7a),
  about 0.9 GB free on /data. compileSdk 37 (installed platform),
  minSdk 30 or lower so the app runs on the device.
- UI sizes: write the OpenStream prototype's pixel values as dp/sp; `OpenStreamTheme` maps 1 dp to 1 pixel of a
  1920-px canvas (the Fire TV runs 320 dpi, so raw dp would be twice too big).
- Code comments: generous and impersonal. Pseudocode: language-agnostic.
- Diagrams: draw.io (.drawio plus timestamped PNG export; archive older versions).
- Each phase ends with a stated done condition and a user approval.

## Publishing note
Provider-specific notes (docs/private/PROVIDER_FINDINGS.md, docs/private/PHASE2_PROBE_PLAN.md) are kept local and gitignored; the published history does not contain them (scrubbed 2026-11-21, backup tag backup/pre-scrub-2026-11-21 on this machine only).
