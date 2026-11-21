# CLAUDE.md: IPTV app for Fire TV Stick 4K Plus

Goal: replace StbEmu on the user's Fire TV with a remote-friendly TV app.
Status (2026-11-21): custom build PAUSED. User uses TiviMate on the Fire TV, which works
with the provider's Stalker portal. Phase 2 done through Step 3b; Step 4 (MAC) built
offline, never run, not approved. Do not resume probing or building without the user's
go-ahead. Findings: docs/PROVIDER_FINDINGS.md. Plan: docs/PHASE2_PROBE_PLAN.md
Repo: ~/Developer/firetv-iptv (outside Google Drive). Build with ./gradlew using
Android Studio's JBR as JAVA_HOME; wrapper pins Gradle 9.5.0 (AGP 8.x breaks on 9.6+).
Full plan: docs/PROJECT_PLAN.md

## Hard rules
1. Never modify, uninstall, clear data from, or overwrite StbEmu on any device.
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
   Exception granted 2026-11-20: the Phase 1 hello app (com.example.firetvhello)
   may be installed. Any APK with provider or playback code still needs Phase 3 approval.

## Secret handling
- Secrets live outside this repo and outside Google Drive sync, in
  ~/.config/firetv-iptv/secrets.env (chmod 600), entered with scripts/save_secret.sh.
  Claude may read values when needed (hard rule 3) but prefers redacted probe output;
  values in Claude's context also land in session transcripts under ~/.claude/.
- Code reads secrets from the path in env var FIRETV_IPTV_SECRETS.
- On the device, the user enters portal details through an in-app settings screen.
  Store them with EncryptedSharedPreferences / Android Keystore. Never in
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
- Code comments: generous and impersonal. Pseudocode: language-agnostic.
- Diagrams: draw.io (.drawio plus timestamped PNG export; archive older versions).
- Each phase ends with a stated done condition and a user approval.
