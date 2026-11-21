# Milestones: first end-to-end vertical slice

Drafted 2026-11-21, after Step 4a returned HANDSHAKE_ACCEPTED. Goal: a free, open-source
Stalker/MAG client for Fire TV with Live TV and VOD, built in thin end-to-end slices.

Every milestone ends with a stated done condition and the user's approval (CLAUDE.md).
Gates that still apply:
- Each live portal run needs its own approval and rule 12 (StbEmu and TiviMate stopped,
  verified over ADB before sending).
- Rule 7: no full UI before compatibility is proven. Live TV is proven by 4d; VOD by 4f.
- Rule 9: no APK with provider or playback code on the Fire TV before Phase 3 approval.
- Rule 11: clean room. Public protocol behavior and open-source clients for reference only.

## M0: Handshake (done 2026-11-21)
Done: our client obtained a session token (HANDSHAKE_ACCEPTED). Nothing else verified.

## M1: Prove the protocol on the Mac (probe, one approval per step)
| Step | Requests | Proves | Done when |
|---|---|---|---|
| 4b profile (DONE 2026-11-21) | 2 | Token activates; account status | `status` = 0, not blocked: passed, MAC only |
| 4c live catalog (DONE 2026-11-21) | 4 | Genres, channel paging, total count | Counts close to TiviMate's 5,100: 4,900 (about 3% fewer; to explain) |
| 4d live link (2026-11-21: link created; HLS; edge 403 to the probe, stopped) | 5 (+2 redirect hops) | `create_link` gives a stream; container | First 64 KB is MPEG-TS or HLS |
| 4e VOD catalog (DONE 2026-11-21, on device) | 6 | VOD categories, movie paging, series markers | 164 categories, 104,000 items; shows inside type=vod (is_series=1); no series module |
| 4f VOD link: DONE 2026-11-21: movie (HEVC, seek 0.9 s) and episode (4-level navigation, seek 0.8 s) | 5 / 8 | Movie and episode `create_link` | Plays on the device with a working seek |
| 4g keep-alive: DONE 2026-11-21 (watchdog x3 ok; token valid with and without keep-alive; re-auth unit-tested) | 7-10 | Watchdog/events polling keeps the token valid | Token still valid after a timed wait |

Small offline fixes before 4b: show the handshake's `not_valid` flag (0/1, not identifying).
If 4b fails on device fields (serial, device IDs, signature), add them as optional local
secrets read from StbEmu's settings screen by the user (looking only).
Done: all six steps pass, recorded in docs/PROVIDER_FINDINGS.md.

M1 status 2026-11-21: complete on the device (4a-4g). Only a live re-authentication is unobserved.

## M1.5: Debug player on the device (done 2026-11-21)
Done: live channel plays on the Fire TV through :provider + Media3 (HLS, H.264 1080p, AAC).
The Mac probe's 403 does not affect the device. Next M1 items (4e VOD, 4f VOD link, 4g
keep-alive) can now be tested in the debug player instead of the Mac probe.

## M2.0: OpenStream intake and decisions (started 2026-11-21)
Review: docs/OPENSTREAM_INTAKE.md. The prototype is the UI source of truth; screens are ported to
Compose for TV on top of `:provider`.

| Prototype screen | Milestone |
|---|---|
| `setup` (first run) | M2 |
| `live-tv`, `live-player`, channel-number overlay | M3 |
| `movies`, `movie-detail`, `shows`, `show-detail`, `vod-player`, language/subtitles overlay | M4 |
| `home`, `search`, `favorites`, `settings`, `tv-guide`, ratings overlay, PIN modal | M5 |
| `states`, `design-notes`, prototype menu | reference only, not shipped |

Tasks, in order:
1. Decisions: license MIT (decided 2026-11-21; LICENSE added here, copyright line "Sriram Ravindren"
   from the git author name, changeable); app name OpenStream (decided; no IPTV project of that name
   found, trademark not checked); package id `io.github.ravindren_sm.openstream` (chosen);
   add a LICENSE file to the OpenStream
   prototype repo too (owner action: no push access from here).
2. Compose theme from `tokens.ts`: colors, type scale, spacing, radii, badges.
3. Focus and navigation base: single-focus rule, back stack with focus restore, overlay focus
   trapping, shared focusable component.
4. Data gap list from the intake (four-level shows, channel numbers, guide, ratings, counts).
5. Optional WebView spike, only if literal reuse of the web code is wanted.
Done: theme + navigation shell + setup screen running on the Fire TV with sample data.

## M2: App foundation (offline, then Phase 3 approval to install)
- Package renamed to `io.github.ravindren_sm.openstream` and license chosen (MIT), 2026-11-21.
- `:app` depends on `:provider` (plain Kotlin; reused as is).
- On-device setup screen: portal URL and MAC entered with the remote. Stored encrypted with
  an Android Keystore key. Note: `EncryptedSharedPreferences` (CLAUDE.md) is deprecated
  upstream; plan a Keystore AES-GCM wrapper or Tink instead (confirm current guidance first).
- Session manager: handshake + profile at start, token refresh on invalid-token responses,
  watchdog polling while active.
- Debug-only diagnostics (rule 13): OkHttp interceptor logging through `Redactor`;
  Media3 `EventLogger` plus error/codec logging, stream URLs redacted, read via `adb logcat`.
Done: debug build connects on the Fire TV and shows "connected" with account status. This
needs Phase 3 approval because it installs an APK with provider code.

## M3: Live TV slice
- Genre rail, then channel list with server-side paging (14 per page) and lazy loading.
- Select a channel, `create_link`, play in Media3 (MPEG-TS/HLS), on-screen errors.
- D-pad: focus restoration on Back, channel up/down, number entry.
- Measure on the device: time to first frame, rebuffering, decoder support (H.264/HEVC,
  audio formats) on the Fire TV's Android 11.
Done: browse and play any live channel for 30 minutes with stable focus and metrics logged.

## M4: VOD slice (movies, then shows)
- Category rails; paged grids sized for about 100k movies and 28k shows (never load all).
- Movie detail and play via VOD `create_link`; seek and resume.
- Shows: seasons, then episodes, then play.
Done: play a movie and an episode end to end; scrolling stays smooth on the largest category.

## M5: Polish for public release
Search (server-side), favorites (local), EPG if the portal provides it, settings, accessibility,
release build without diagnostics. Open-source prep: license, README, docs scrub of
provider-specific detail, CI running the unit tests and the secret scan.
Done: user approves a public GitHub release.


## M2 progress (2026-11-21)
Decisions:
- **Secret storage: platform Keystore + AES-256-GCM, no new dependency.** `EncryptedSharedPreferences`
  and `MasterKey` are deprecated upstream (https://developer.android.com/reference/androidx/security/crypto/package-summary).
  The DataStore + Tink route exists but is alpha and documented mainly by third parties
  (https://proandroiddev.com/finally-the-official-way-of-secure-datastore-is-here-ae878f5eb457);
  our data is two short strings, so one small wrapper is simpler. Keystore AES-GCM works from API 23;
  `minSdk` is 25. Do not use StrongBox or unlock-required flags (Android 9+ only).
- **Request limits:** the probe's strict `RequestBudget` (10 requests) stays for probing. The app uses
  `RequestPacer` (about 3 requests per second, no cap), through a shared `RequestGate` interface.

Done offline, 80 unit tests:
- `AesGcmBox` (seal/open, fresh IV each time, tamper/wrong-key/wrong-AAD rejected without leaking).
- `PortalSettings` (URL and MAC validation, MAC normalization, fixed problem messages, no values in
  `toString`, sealed round trip).
- `SessionManager` (start, one re-auth on "Authorization failed.", 30 s gap between re-auths, watchdog
  every 120 s, no other retries).

Next slices: Keystore key + sealed file in `:app`; on-device setup screen (D-pad, TV keyboard);
wire the session manager and a watchdog timer into the app; debug-only OkHttp interceptor (rule 13).
Needs the user's decision first: package name and license (see CLAUDE.md "Before publishing").


## M2.0 progress (2026-11-21, second slice)
Built from the OpenStream prototype (commit 01bcc7990f67) and verified offline; not yet run on the TV.
- **Theme**: `ui/theme/Tokens.kt` and `Theme.kt`: colors, type scale (nothing under 24 sp for body),
  spacing, radii, focus style; mapped onto Compose for TV `darkColorScheme` and `Typography`.
- **Contrast, measured** (locked in `ContrastTest`): primary text 17.8:1 on canvas, 16.2 on cards, 12.1
  on the focused surface; secondary text 9.1 on canvas, 8.3 on cards, 7.3 on selected, 6.2 on the
  focused surface. The prototype's "secondary above 8.5:1" is true on the canvas only; every pair stays
  above the 4.5:1 minimum.
- **Navigation**: `nav/NavStack.kt`, an immutable back stack that remembers the focused element per
  screen, restores it on Back, resets to [Home, tab] on a tab change, and returns null at the root so
  the app can exit. 6 unit tests.
- **Focus**: `ui/focus/FocusMemory.kt` (focus IDs to requesters, single-focus rule) and
  `ui/components/OsFocusable.kt` (3 dp ring, card scale, select key, registered ID);
  `TopNavBar` with the prototype's tab order.
- **Shell**: `AppShell` + `PlaceholderScreen` exercise it (sample cards, open a detail, Back restores
  the exact card). `MainActivity` now hosts the shell.
- **Debug tooling moved to the debug source set**: `DebugPlayerActivity`, `DevConfig`, the cleartext
  network config. A release build contains none of it (checked: 0 hits for the debug class, the log
  file name, the config file name, the log tag, or the debug manifest entries).
  Start the debug player with:
  `adb shell am start -n io.github.ravindren_sm.openstream/.DebugPlayerActivity`.
- Tests: 10 in `:app` (4 contrast, 6 navigation) plus 80 in `:provider`.
Remaining for M2.0's done condition: run the shell on the Fire TV; build the setup screen.
