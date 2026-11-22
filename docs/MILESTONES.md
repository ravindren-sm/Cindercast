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
Done: all six steps pass, recorded in docs/private/PROVIDER_FINDINGS.md.

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
| `home`, `search`, `favorites`, `settings`, `tv-guide`, PIN modal | M5 |
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


## M2.0 on-device check (2026-11-21, Fire TV Stick 4K Plus, debug shell installed as a separate app)
Driven with remote keys over ADB (select, arrows, Back) with screenshots at each step.
- **Found: the TV's pixel grid.** The Fire TV runs a 1920x1080 interface at 320 dpi, so 1 dp = 2 px and
  Android sees 960x540 dp. Using the prototype's token values unscaled made everything twice too big:
  the 8-tab bar overflowed ("Favorites" wrapped, "Settings" fell off the edge) and the cards ran off
  the bottom. Fix: `OpenStreamTheme` now provides a design density so 1 dp = 1 pixel of a 1920-px-wide
  canvas on any screen (`designDensity`, unit-tested). Token values stay exactly as the prototype
  specifies. Rule for all later UI code: write sizes in the prototype's pixel values.
- **Verified on the device after the fix:** all 8 tabs fit on one row; focus ring (3 px white) and card
  scale-up render; open a detail from a card and Back returns focus to that exact card; selecting a
  tab changes the page and keeps focus on the tab; Back from a tab returns to Home with that tab
  focused; Back at the root exits to the launcher; no crashes in the crash buffer.
- Small fixes from the same check: the detail screen's "Opened from sample N" counted from 0 (now
  1-based); tab text now lines up with page content at the 48 px safe margin.
- Not yet verified: key repeat and fast navigation, long lists, overlays, and the setup screen.
Remaining for M2.0's done condition: the setup screen with the sealed settings store.

## M2.0 setup screen and sealed settings store (2026-11-21, third slice)
- Added: SettingsStore (Keystore-sealed file, tmp + rename writes), first-run SetupScreen (welcome, then portal and MAC with a connection test that must succeed before Save), two-mode OsTextField, OsButton, masked URL/MAC display helpers.
- Verified on the Fire TV (debug build): Keystore self-test all PASS; welcome to portal step; typing through the Fire OS keyboard; Back closes the keyboard without leaving the step, keeps the value and returns focus to the field; Down from the MAC field lands on "Test connection"; the failure path (unreachable placeholder host) shows a fixed message and keeps Save disabled. 88 local tests pass.
- Test lesson: a cold start takes about 5 s and key events sent before the window has focus are dropped; on-device scripts must wait about 9 s after launch. Earlier "Back leaves the step" reports were this timing artifact, not an app bug.
- Not yet verified on device: Save with a successful test (needs a real portal, held for the M2 session wiring), "unreadable settings" notice, Home start when settings exist.
- Remaining for M2.0's done condition: none for the screen itself; those three checks fold into the M2 wiring work.

## M2 session wiring (2026-11-21, fourth slice)
- Added `PortalSession` (app): one background thread owns the portal session. It connects when the app is in the foreground and a portal is configured, sends the watchdog every 120 s, and stops entirely in the background (so an idle TV does not hold the MAC's single session). Status (`Idle`, `Connecting`, `Ready(expiry)`, `Degraded`, `Failed(fixed message)`) is Compose state; a one-line status sits under the tab bar. Saving in setup, or starting with saved settings, configures it.
- Tests (5, against a local fake portal): connect and expiry, repeated keep-alives, silence in the background and reconnect on return, no portal configured, refused handshake. 93 local tests in all.
- Verified on the Fire TV with placeholder settings (no provider contact): Home starts with saved settings, shows the fixed "could not reach" line, and survives background and resume. Placeholder settings cleared afterwards.
- Not verified: a successful live session and a real keep-alive on device (needs a Phase 2 approval to contact the provider); re-auth on a real expiry is still unobserved.
- (Done later the same day: the debug-only OkHttp interceptor, see "M2 debug portal traffic log" below.)

## M2 first live session of the app (2026-11-21, user-approved)
- StbEmu and TiviMate confirmed not running before and after (read-only check). Real settings were imported into the sealed store from the pushed dev config (debug-only `--ez import true` on the self-test activity; the plaintext dev-config.json was deleted from the device afterwards).
- Result: handshake and profile succeeded; the app showed "Portal connected" with the account end date. A second run, with the screen kept awake by D-pad presses, still showed connected about 150 s in, past the first 120 s keep-alive (a failed keep-alive would have shown the warning line). Keep-alive success is inferred from the status, not from a request log; the debug interceptor (rule 13) will make it directly visible.
- Observed: the Fire TV screensaver started within about 2.5 minutes and the app paused its session as designed (foreground-only).
- Save-from-setup with a real portal and re-auth on a real expiry remain unobserved. Real settings are now in the app's sealed store on the device.

## M2 debug portal traffic log (rule 13, last M2 item)
- Added a debug-only OkHttp interceptor (`PortalTrafficLog`, src/debug). It writes one line per portal call to logcat under tag `OpenStream.portal`: method, redacted host (`<HOST>`), path shape, `type` and `action` values, HTTP status or `FAILED <exception class>`, and elapsed ms. No bodies, no query values except type and action, no MAC, token, Cookie or Authorization.
- Wiring: `HttpProbe` takes an optional interceptor list (provider, default empty). Main code reads `PortalTransport.interceptors`, which `PortalSession` and `ConnectionTester` use. The debug manifest overlay sets `DebugApp`, which fills that list at start-up. Release has no reference to any of it; checked by searching the release APK's dex (0 matches; the debug APK has 21).
- Tests: 5 new in `app/src/testDebug` against a local fake portal: fields present, no host, port, MAC (all spellings), token or query marker leaks, a failing request still logs a redacted FAILED line, unsafe type/action values show as `?`, and session keep-alive traffic is logged through the hook.
- Found while testing: the provider Redactor's URL rule mangles some stalker paths (its long-token rule could eat part of a path). The logger therefore builds the URL shape itself and still runs the final line through Redactor.
- Not done: no device run and no provider contact for this slice.

## Redactor fix: stalker paths with a long port (2026-11-21)
- Found: a URL with a 5-digit port and a stalker path (for example `:52955/stalker_portal/server/load.php`) came out as `<TOKEN>.php`. The long-token rule matched the path shape the URL rule had just kept.
- Fix: the long-token rule now leaves a slash-separated run alone only when every part is a protocol path word, a digit-only part, or the start of a file name followed by a short extension. Real token-like runs are still masked.
- Tests: RedactorTest regression for the stalker path, and a check that a long token containing a slash is still masked. 112 provider and debug app unit tests pass.
- Follow-up to the debug traffic log: the debug player's four portal clients (live, VOD, seasons, create_link) now take the same interceptor hook, so their calls are logged too. Stream (media) requests are not logged.

## M2 status after the offline close-out (2026-11-21)
- Code complete: setup screen, sealed store, session with watchdog, debug traffic log, Media3 diagnostics in the debug player (state, first frame, codecs, load and player errors, all redacted). The real app has no player until M3, so player diagnostics for it are an M3 task.
- Added a session-level test for the app's own re-auth path: a keep-alive answered with "Authorization failed." makes `PortalSession` re-handshake once and stay connected; a refused re-handshake shows a failure and the portal is not hammered. 7 tests now in PortalSessionTest.
- Still unobserved, and only a live portal run can show them (each needs the user's approval and StbEmu and TiviMate stopped, rule 12): Save from setup with a real portal; re-auth on a real expiry; a keep-alive seen as a log line (the traffic log makes it visible); the "saved settings could not be opened" notice on the device; the debug build's Application class starting on the device.


## M3 slice 1: live genres and paged channels (2026-11-21, offline)
- Provider: `LiveGenre`, `LiveChannel` (cmd never printed), `LiveChannelPage`, `StalkerClient.liveGenres()` and `liveChannels(genre, page)`. Counts sent as text parse; missing totals fall back to the page.
- App: `PortalSession.withSession` runs catalog calls on the session thread, serialized with the keep-alive; `LiveCatalog` (fixed-text failures, "All channels" always first); `PagedChannels` loads one server page at a time, retries a failed page without skipping it, drops a channel repeated across pages, stops at the total or on an empty page.
- UI: Live TV tab shows a genre rail and the channel list, loading more as the user nears the end (6 rows ahead). Browsing state is held by the shell, so opening a channel and coming back keeps the loaded pages and scroll. Selecting a channel opens the existing placeholder player screen; playback is the next slice.
- Tests: 6 provider, 7 app (fake portal with 40 channels, 14 per page). 127 in all; debug and release build.
- NOT verified: the screen has never run. Focus movement between rail and list, scroll-driven paging and Back restoration need the Fire TV, which needs either a live portal (approval, rule 12) or a fake portal on the Mac with the saved settings cleared. Layout is written from the prototype's tokens without a device look.

## M3 slice 1 on the Fire TV, fake portal (2026-11-21; no provider contact)
Method: a side-by-side debug install (`-PcheckSuffix=true`, package ends in `.check`, own empty storage) pointed at `scripts/fake_portal.py` on the Mac. The real OpenStream install, StbEmu and TiviMate were not touched; the side app was uninstalled afterwards and screenshots and logs deleted.
- **Verified on the device:** first-run setup typed through the remote, "Test connection" then Save, Home, Live TV tab; genre rail with "All channels" first and highlighted; 120-channel list with the count; D-pad: Down from the tab lands on the rail, Right on the list, Left returns to the nearest genre row; 40 fast Down presses landed exactly 40 rows down with the ring visible, and pages 1 to 4 were fetched only as needed; opening a channel then Back restores the same scroll position and the same focused row; switching genre restarts at page 1 with that genre's total; a failed page shows the fixed red message and a focusable retry row.
- **Verified on the device, closing earlier gaps:** the debug Application class starts and the traffic log prints on the real device (25 lines: 20 HTTP 200, 3 HTTP 500, 2 FAILED), with no address, MAC, token or header in any line; a keep-alive appeared on schedule 120 s after the handshake and was logged; Save from setup and Home start with saved settings work against a portal (the fake one).
- **Bugs found and fixed:** after a successful retry the retry row vanished and focus fell to the Home tab while the list scrolled back; focus now goes to the first newly loaded row. Channel numbers sat above the name's baseline; now centered.
- **Observed, not changed:** a server that closes the connection without saying so makes the app's next request fail at once (a pooled connection is reused; retries are off by design). The fake portal hit this until it sent `Connection: close`. A real portal that closes idle connections sooner than the 120 s keep-alive would show as "connection check failed" on the keep-alive. The traffic log makes it visible; a fix (drop idle connections, or one fresh-connection retry for GETs) needs a decision because HttpProbe promises no retries.
- **Environment note:** a firewall-blocked Python listener shows as `CLOSED` and times out every client. Use `/usr/bin/python3` for the fake portal (see the script header).
- **Still unverified:** the genre-list retry row's focus (same pattern as the channel retry, not exercised); real portal data shape, page size and genre list; playback (next slice).

## Resolved: server-closed idle connections (2026-11-21)
- Decision (user accepted the recommendation): `HttpProbe` keeps no idle connections, so every portal request opens a fresh one. The no-retry promise is unchanged. Cost: one extra connection setup per request, small against the 120 s keep-alive and paged browsing.
- Why: with retries off, a pooled connection that the server had closed made the next request fail at once (seen on the Fire TV against the fake portal). A real portal with a shorter idle timeout than the keep-alive would have shown "connection check failed".
- Test: `HttpProbeTest` regression (server answers then closes without `Connection: close`); it failed before the change and passes after. 128 tests in all. Not re-run on the device: the change is inside the HTTP client and covered by the unit test.

## M3 slice 2: live player (2026-11-21; fake portal and a local test video, no provider contact)
- Built: `LivePlayback` (create_link on the session thread, fixed-text failures, stream address kept in memory only), `PlaybackMessages` (fixed wording per error code, HTTP status named for 401/403), `LivePlayerScreen` (Media3 ExoPlayer over OkHttp, full screen with no tab bar, channel overlay for 4 s on start and on any key, "Buffering…", error with a retry button, pauses when the app stops and rejoins on return), hooks (`PlayerHooks`) for debug diagnostics.
- Debug diagnostics (`OpenStream.player` in logcat): state changes, time to ready and first frame, rebuffer count and each duration, dropped frames, codecs, load and player errors, a summary line when the player is released, and every stream request as `host#n -> HTTP status type` with no path and no query. Error text from the player is never logged (it can hold the stream address). None of it is in the release APK (0 matches).
- Tests: 15 new (resolution, wording, metrics, stream logger incl. redirect and failure, no leaks). 143 in all.
- **Verified on the Fire TV:** create_link then play: H.264 1280x720 30 fps decoded and shown, first frame 755 to 811 ms; overlay appears then fades; Back returns to the list with the same channel focused; stream HTTP 403 shows "The stream server refused the request (HTTP 403)." with a focused retry that then plays; a throttled stream shows "Buffering…" and logs 3 rebuffers (2.1 to 2.4 s each) and the summary; Home pauses playback (`playing: false`) and returning plays again.
- **Bugs found on the device and fixed:** time to first frame was counted from a failed earlier attempt (12.7 s shown for a retry; now timed from each start); playback continued behind the launcher after Home (now pauses on stop).
- **NOT verified, needs the real provider (approval, rule 12):** a live HLS stream (the test stream was a progressive MP4, so live-window handling and segment redirects to an edge are untested); audio (the test video is silent); real codecs (HEVC, AC-3 and so on); the stream user agent (the app sends the player's own identity; the provider's edge refused the Mac earlier, so what it accepts is unknown); 30 minutes of stable playback.
- **Not built yet:** channel up/down and number entry; EPG.
- **Open decision, affects release builds:** cleartext HTTP is allowed only by the debug network security config. The provider has no HTTPS, so a release build cannot reach its portal or streams at all. Needs a release-config decision (allow cleartext for user-entered portals, with the existing in-app plain-HTTP warning) before any public release build.

## M3 first live run on the Fire TV (2026-11-21, user-approved; StbEmu and TiviMate force-stopped and confirmed not running)
Debug build over the existing app (saved settings kept). Watch-only: browse Live TV, play three channels from three genres. About 6 minutes in all. App stopped afterwards so it holds no session.
- **Worked:** connect (handshake, profile) and "Portal connected" with the account end date; genre rail from the portal (it supplies its own "All" entry); 4,900 live channels in "All" (same count as the M1 probe); genre filtering (two genres seen: 49 and 74 channels); 14 portal calls, all HTTP 200; 2 keep-alives on the 120 s schedule, both 200.
- **Playback:** all three channels played with video and audio. create_link then a 301, a 302 and then the edge host serving an HLS playlist (refreshed about every 7 s) and MPEG-TS segments. The player's own user agent was accepted: no 403 anywhere. Video H.264 Main L3.1 1280x720 and H.264 High L4.0 1920x1080 (decoded by the device); audio AAC-LC stereo 48 kHz, with the app's audio track in the started state. First frame 1.09 s, 1.41 s and 1.74 s; the longest play (about 3.5 min) had 0 rebuffers, 0 dropped frames, 0 errors; the other two ran 25 to 60 s, also clean.
- **Expected, not a fault:** one `SocketException` on a stream request at the moment of pressing Back (the player cancelling an in-flight load as it released).
- **Observed:** channel numbers in "All" start at 40, so the first row reads 40 (the list is sorted by the portal's number). Channels labelled 4K were 720p or 1080p H.264.
- **Closes earlier gaps:** the debug Application class and traffic log on a real portal; a real keep-alive seen as a log line; real page sizes and genre list shape; the stream user agent question; live HLS through redirects to an edge.
- **Log hygiene:** every captured line was redacted (host shows as `<HOST>` or `host#n`); a scan found no address, MAC, token or header. The capture, screenshots and log were deleted after the run.
- **Still not verified:** 30 minutes of continuous playback; HEVC and AC-3 or E-AC-3 channels; resuming a real live stream after Home or after a long pause (live-window rejoin); Save from setup and re-auth on a real expiry; channel up/down and number entry (not built); VOD (M4).

## Decision: ratings come with the movie screens in M4 (2026-11-21, user)
The prototype's IMDb and Rotten Tomatoes badges are kept as intended functionality, not deferred to M5. The portal's own movie data carries `rating_imdb`, `rating_kinopoisk`, `rating_mpaa` and `age`, plus year, description, actors, director, genres and poster fields, so IMDb ratings need no OMDb key. Plan for M4: parse these fields, show the IMDb rating on cards and the detail screen, hide a rating the portal sends as 0 or empty (quality of the values is unverified until a live VOD check), and keep an optional user-supplied OMDb or MDBList key for gaps and for Rotten Tomatoes as a later extra. Rotten Tomatoes has no portal field.

