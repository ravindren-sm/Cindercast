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
| 4f VOD link (new, offline first) | about 5 | Movie and episode `create_link` | First bytes are a playable container |
| 4g keep-alive (new, offline first) | small, spaced | Watchdog/events polling keeps the token valid | Token still valid after a timed wait |

Small offline fixes before 4b: show the handshake's `not_valid` flag (0/1, not identifying).
If 4b fails on device fields (serial, device IDs, signature), add them as optional local
secrets read from StbEmu's settings screen by the user (looking only).
Done: all six steps pass, recorded in docs/PROVIDER_FINDINGS.md.

## M1.5: Debug player on the device (done 2026-11-21)
Done: live channel plays on the Fire TV through :provider + Media3 (HLS, H.264 1080p, AAC).
The Mac probe's 403 does not affect the device. Next M1 items (4e VOD, 4f VOD link, 4g
keep-alive) can now be tested in the debug player instead of the Mac probe.

## M2: App foundation (offline, then Phase 3 approval to install)
- Rename the package off `com.example.*`; choose a license (open decision).
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
