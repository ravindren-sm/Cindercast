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


## Publication parked (2026-11-21)
Decision: publish the native app as a new branch `native-app` of the existing public OpenStream repo, leaving its prototype `main` untouched. Local history was scrubbed first (provider findings and probe-plan docs removed from every commit and kept in gitignored `docs/private/`; exact catalog counts rounded; history scan clean; backup tag `backup/pre-scrub-2026-11-21` on this machine only). The `origin` remote is set, but nothing is pushed: GitHub login from this Mac failed (HTTPS token refused with 403, then the GitHub CLI browser login did not complete). To resume: log in with `gh auth login`, then `git push -u origin main:native-app`. Until then the work exists only on this Mac.

## M3 slice 3: channel up/down and number entry (2026-11-21; fake portal, no provider contact)
- Built: `LiveZapper` (next, previous, by number; pure and tested, 10 tests), `NavStack.withPreviousFocus` (2 tests), and the player's key handling. Up or Channel Up goes to the next channel of the genre, Down or Channel Down to the previous one, digits (main row or number pad) type a channel number that is entered after a 2 s pause or on Enter or Select. A zap waits 350 ms before asking the portal for a stream, so a held key requests only the channel it stops on. Messages: "Last channel", "First channel", "No channel N". Back from the player lands on the channel being watched, with its row on screen.
- A typed number is looked up in the loaded pages; beyond them, further pages are fetched while the numbers are still below it (the portal's order is by number), at most 40 extra pages for one lookup.
- **Verified on the Fire TV (side install, fake portal, local test video):** Up moves to the next channel with the overlay and one create_link; five Up presses sent in one burst moved five channels and cost exactly one create_link; typing 5 0 shows "Channel 50" top right, then plays channel 50 after fetching only pages 3 and 4; typing 9 9 9 shows "No channel 999" after fetching up to the end of the 120 channels while playback continued; Back returns to the list with channel 50 focused and in view. 155 tests in all.
- **Limits and not verified:** the Fire TV remote has no number keys, so number entry needs a keyboard or another remote; a number far beyond the first 600 or so channels (the cap) reports "No channel". On the real portal, number gaps, the sort order and how fast a held remote key repeats are unverified. Rapid keys on the real remote were simulated with a burst of key events.

## M4 slice 1: movies (2026-11-21; fake portal and a local test video, no provider contact)
- Provider: `VodItem` now carries year, description, actors, director, genres, the portal's IMDb and Kinopoisk ratings (0 and out-of-range values read as absent), age/MPAA and the poster address; `VodPage`, `StalkerClient.vodTitles`, `PortalText` (HTML entities such as `&#124;` decoded, ratings parsed). 6 tests.
- App data: `VodCatalog` (categories split into Movies and Shows tabs by title, the mixed "All" entry dropped), `PagedTitles` (a page at a time, retry without skipping, duplicates dropped), `VodPlayback` (VOD create_link), `ResumePolicy` and a resume store (SharedPreferences), `PosterUrl`, `TimeText`. 12 tests.
- UI: Movies and Shows tabs (category rail and a 5-column poster grid, loaded two rows ahead; the IMDb badge shows only when the portal sent a real rating), movie detail (poster, year, age, IMDb, genres, description, director, cast, Play), VOD player (Left/Right seek 10 s, Select pauses, bar with position and length, saved position resumes: not within 15 s of the start or 45 s of the end). Posters are decoded as small RGB_565 bitmaps, cached by bytes (24 MB) and fetched four at a time. The portal keeps shows among the movies, so a title's own `is_series` flag decides whether it opens the movie page or the show page.
- **Verified on the Fire TV (side install, fake portal with posters and ranged video):** Movies tab with only movie categories, poster grid and badges, unrated titles without a badge, entity-decoded title; detail page; play, seek +30 s, pause with the bar kept up; leaving and playing again gives "Resumed at 1:19"; Back from detail returns to the same card; the Shows tab lists show categories and opens the Show page placeholder. No crashes. 172 tests in all.
- **Not verified, needs the real provider:** the real poster address format and host (the loader accepts absolute, protocol-relative and host-relative paths, nothing else); how many titles carry a real IMDb value; the title-based movie/show split across the real 164 categories (the item flag is the safety net); seek and resume on real HLS VOD (the debug player proved seeks resume in under a second); memory use scrolling thousands of real posters.
- **Not built yet (slice 2):** show detail with seasons, episodes and the file/language chooser, subtitles, and the language overlay; Rotten Tomatoes (the portal has no field; needs the optional key); search and favorites (M5).

## M4 slice 1 on the real portal (2026-11-21, user-approved; StbEmu and TiviMate force-stopped and confirmed not running)
Current debug build installed over the real app (saved settings kept). Watch-only: browse Movies and Shows, open one movie, play, seek, leave and resume. About 10 minutes. Two temporary count-only diagnostics were added for the run and removed afterwards (checked: none in the final APK); the capture, screenshots and log were deleted after a scan found no address, MAC, token or header.
- **Worked:** both tabs load real categories (entities such as `&#124;` decode to `|`) and real titles, 14 per page, with totals (for example 191, 971 and 3,024 titles in three categories); real posters load; detail page shows year, age rating, genres, description, director and cast; playback of a real movie (H.264 High 1920x1080 24 fps about 3.7 Mbit/s, AAC stereo 48 kHz; create_link then a 302 to the edge host, HLS and MPEG-TS) with first frame 1.9 s; a +30 s seek rebuffered 0.8 s; leaving and playing again resumed ("Resumed at 0:54" of a 1 h 46 min film, first frame 1.3 s); 60 portal and stream calls HTTP 200, no crashes. Memory after scrolling about 150 posters: about 199 MB total, Java heap 13 MB of a 192 MB limit.
- **Poster data:** `screenshot_uri` is a host-relative path on every sampled title (84 of 84); `pic` is blank. The loader resolves host-relative paths against the portal address, so no change was needed.
- **Rating data (finding):** `rating_imdb` is "0" on almost every title (3 of 84 sampled had a value); `rating_kinopoisk` has a real number on about half (42 of 84). The IMDb badge therefore almost never showed. Fix: a title shows its IMDb value if present, else its Kinopoisk value labelled as such ("KP 6.1" on cards, "Kinopoisk 6.1" on the detail page), never passed off as IMDb. Real IMDb ratings for the rest need an external source with a user-supplied key (OMDb or similar): open decision.
- **Other fields:** `year` present on all; MPAA text present on most; `age` blank.
- **Movie/show split (finding):** the portal has 163 categories besides "All"; the title-based rule put 98 under Movies and 65 under Shows. Reviewing all 163 titles found one clear misfit (a drama-series category under Movies). The rule now also treats "drama(s)", "serial(s)", "soap(s)", "episode(s)", "sitcom(s)" and "telenovela(s)" as shows. Remaining ambiguous ones (kids collection, religious, stand-up comedy, sports events) stay under Movies, and each title's own flag still decides which page opens.
- **Still not verified:** HEVC movies in the new player (the debug player proved HEVC earlier); show detail with seasons, episodes and the file chooser (slice 2); subtitles; long playback of a movie; whether Kinopoisk values are always on a 0 to 10 scale (all sampled were).

## M4 slice 2: shows (2026-11-21; fake portal and a local test video, no provider contact)
- Provider: `StalkerClient.vodChildPage` (paged seasons, episodes or files of a show).
- App data: `VodCatalog.seasons`, `episodes` (paged, 14 at a time), `files`; `PlayTarget` and per-episode resume keys (`show/season/episode`) so every episode remembers its own position; the VOD player now takes a play target (title, file, resume key), so movies and episodes share it. 5 new tests (16 in the VOD catalog tests; 178 in all).
- UI: Show page with poster and metadata, Play (first episode of the selected season), season chips, paged episode list with "Resume m:ss" on episodes with a saved position, and a version chooser when an episode has several files (language and quality). Back closes the chooser first, then leaves the page; Back from an episode lands on that episode. Up from the first episode goes to the selected season chip.
- **Verified on the Fire TV (side install, fake portal with 3 seasons, 16 episodes each in two pages, 1 or 2 files per episode):** show page with seasons and episodes (entity-decoded names); season 1 episode with one file plays straight away; season 2 episode shows the chooser, Back closes only the chooser, choosing the second version plays it; after 22 s the episode shows "Resume 0:22" and focus returns to it; no crashes.
- **Found on the device and fixed:** Up from the first episode landed on the chip nearest the middle of the wide row (Season 3), not the selected season, which sent later key presses off the page. Now explicit.
- **Not verified on the real portal:** the real show structure beyond the earlier probe (the debug player proved show, seasons, episodes and files, `is_season`, `is_episode`, `is_file`); real episode counts per season and paging; real files with several languages; real episode resume. Not built: next-episode prompt, Language and Subtitles overlay, explicit Resume and Favorites on the show page.

## M4 slice 3: Language and Subtitles drawer (2026-11-21; fake portal and a local test video, no provider contact)
- Provider: `SubtitleParser` reads the portal's subtitles from a VOD create_link in three tolerant shapes (list of objects, list of addresses, language-to-address map); `StalkerClient.vodLink` returns the stream address with them. The real portal's shape is not documented (the earlier probe only saw a `subtitles` key), so the parser is deliberately lenient. 4 tests.
- App: `TrackLabels` (language names from two- and three-letter codes, channel layout and AD/SAP/DUB/ORIG tags, SRT/VTT/CC/DVB/TTX tags, summary line) and `SubtitleMime` (file type from the address), 7 tests; `StreamLink.Ok` carries subtitles; the movie player side-loads them (host-relative addresses resolved against the portal; addresses never logged).
- UI: a right-hand drawer shared by the movie and live players (audio and subtitle columns, check on the active track, tags, summary, Done). Menu opens it (also Captions, or L on a keyboard); a hint shows on screen. Back closes it in one press and focus returns to the player; the player's seek, zap and digit keys are ignored while it is open.
- **Verified on the Fire TV (side install, fake portal, a test video with English and Hindi audio tracks and two subtitle files, an SRT and a WebVTT):** the drawer lists both audio tracks (named from the MP4 language tags) and Off, English (SRT) and Hindi (VTT); switching audio updates the check and the summary and survives reopening; choosing a subtitle shows its cues on the video; the live player's drawer lists the same audio tracks and "No subtitle tracks in this stream". 190 tests in all; no crashes.
- **Found on the device and fixed:** (1) a focus-trap property I had added stopped Right from reaching the Subtitles column, and was unnecessary; removed. (2) Subtitle rows had no format tag because a side-loaded subtitle keeps its original type in the codecs field; now read from there. (3) After choosing a subtitle the first Back only cleared focus instead of closing the drawer; the players now handle Back themselves while the drawer is open.
- **Not verified on the real portal:** whether its create_link returns subtitles and in what shape and format; real audio track names and languages on real streams; live captions (CC, DVB). **Not built:** the on-canvas caption preview inside the drawer, and an info banner button to open it.

## Live check for family use on the real portal (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Current debug build over the real app (settings kept). About 25 minutes of real viewing: live TV (browse, play, zap, type a number, audio drawer), a movie (detail, play, drawer, seek, resume), a show (page, first episode played for about 14 minutes), Home and return, memory. Capture and screenshots deleted afterwards; a scan found no address, MAC, token or header.
- **Worked on real data:** zapping first frame 0.7 to 1.1 s; typing a channel number found the right channel; a real HEVC 1080p 24 fps movie plays (closes the HEVC question) with first frame 1.3 s, a +60 s seek and resume ("Resumed at 1:37" of 2:05:49); the show page lists the real season with episodes newest first and plays; Home pauses playback and returning shows "Paused" at the same position; memory about 124 MB after 12 minutes; the screen stayed awake; the account and keep-alive stayed healthy; 298 HTTP 200 responses.
- **One real failure, reported by the owner while watching:** about 1 min 50 s into an episode (H.264 720p, AAC) the video decoder threw `ERROR_CODE_DECODING_FAILED` and the player stopped with "This device cannot play the stream's video or audio format". The network was fine and the stream format never changed; pressing Select resumed the episode and it then played 6 minutes with no further error. So a one-off decoder glitch, shown with a wrong message and needing a manual retry.
- **Fixes made:** automatic recovery (`PlaybackRecovery`): decoder, audio-output and connection failures re-prepare the player after 1.5 s, at most 5 times in 2 minutes, then the error and retry button show; refused requests (403, 404), unreadable data and unsupported formats never retry. Decoder fallback is on (if a hardware decoder fails, the next one is tried). A decoder failure now says "The video decoder stopped unexpectedly." and only real format limits say "cannot play". Verified on the Fire TV with the fake portal: a 20 s dropped connection showed "Recovering…" and playback resumed on its own with no error screen; a longer outage ends in the error screen with Select to retry.
- **Real tracks (finding):** real audio tracks have no name ("Track 1"); the live channel offered one subtitle track that is almost certainly broadcast captions, shown as "Track 1" with no tag; the movie's subtitle track is a WebVTT labelled "eng t1" (so the portal does supply subtitles, inside the stream). Labels now turn "eng t1" into "English" and unnamed captions into "Closed captions".
- **Not done / not verified:** a 30-minute live-channel soak (the longest real run was the 14-minute episode); what happens at the end of an episode (no next-episode prompt); one `IOException` line seen in the capture was not examined before the log was deleted (playback was unaffected); whether the decoder glitch recurs, and on which streams.

## 30-minute soak on the real portal, kid-safe title (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Rule from the owner: children are in the household, so real runs on the family TV use clearly kid-safe titles (G, PG or U) and avoid adult categories and live channels, whose content cannot be vetted. Played: a G-rated animated family film from the Kids Movies category, chosen by rating, genres and poster on its detail page before pressing Play.
- **Result:** 30 minutes of continuous playback (H.264 1280x720, AAC stereo, HLS) with 0 player errors, 0 rebuffers (0 ms), 0 stream failures, 22 dropped frames in total, 283 HTTP 200 and one 302 redirect, no crashes. The screen stayed awake with OpenStream in front throughout (checked every 5 minutes). First frame 1.7 s. Memory at the end: about 152 MB total, Java heap 40 MB of a 192 MB limit (up from about 11 MB at the start of the earlier shorter runs, so worth watching in longer sessions).
- **Closes** the M3 "30 minutes of stable playback" condition for VOD. A 30-minute continuous run on a live channel has still not been done, and live channels were deliberately not used here because of the kids rule.
- **Also seen on real data:** Kinopoisk ratings now show on cards as "KP x.x" and as "Kinopoisk x.x" on the detail page; where the portal does carry an IMDb value (some titles) the star badge shows instead.
- **Cleanup:** app stopped, log scanned (no address, MAC, token or header) and deleted with the screenshots.

## Profiles and parental controls, slice 1: data layer (2026-11-21; no provider contact)
Design in `docs/HOME_AND_PROFILES.md`. Built: Room 2.7.1 with KSP (the owner chose Room; schema exported to `app/schemas`), tables for profiles, the household PIN, favorites, watch history, pinned categories and a Kids allow-list; `ProfileRepository` over them. Plain-Kotlin rules, unit-tested: `AgeLevels` (G, PG, PG-13, R, TV-MA, "U/A 13+", "16+", "12A" and more map to a minimum age; blank or unknown has none), `ParentalPolicy` (Kids: known label at or under the ceiling, unknown blocked, no Live TV, PIN for Settings and to switch; Adults: everything, restricted levels ask for the PIN; adult categories hidden from everyone unless an adult turns them on; a Kids profile sees only allow-listed categories), `PinService` (four digits, salted PBKDF2 hash, never stored; five wrong guesses lock for 30 s, doubling to 15 minutes), `HistoryRules`. 20 new unit tests (218 in all).
- **Verified on the Fire TV** with a debug-only self-test screen (throwaway databases, no portal): create adult and Kids profiles (a Kids profile refuses the adult-categories flag); the PIN persists across a fresh service and locks after five wrong guesses; favorites are per profile; history skips a barely started title, orders newest first, marks finished; pinned categories keep order; Kids suggestions take only kid-friendly names and never adult ones; deleting a profile removes only its own data; a file database survives close and reopen. 8 of 8 pass. The self-test is not in the release APK.
- **Not built yet:** profile picker and PIN pad screens, enforcement in the Movies, Shows and Live screens, capturing history and favorites from playback, Home, Settings (slices 2 to 6 in the design doc). **Not verified:** the age labels the real catalog actually uses (survey needed), so the table may miss a label.

## Continue Watching and a first Home (2026-11-21; fake portal and a local test video, no provider contact)
- A history row now keeps the command that plays the title again (Room schema 2, migration 1 to 2). Both players' saves (every 5 s and on stop) go to the current profile's history; a title watched to within 45 s of the end is marked completed and leaves the row. Titles under 15 s with no row are not recorded.
- Until the profile picker exists the app makes one default profile ("Family") on first use. Resume positions are now kept per profile (key prefix `p<id>:`); positions saved by earlier builds are not carried over.
- Home: Continue watching (poster, show and episode line, progress bar, "N min left"), newest first, one tile per show; below it four doors (Live TV, Movies, Shows, Search). With nothing watched yet the doors are the whole page with a welcome line. Selecting a tile rebuilds the play target from the stored command and resumes at the saved position.
- Unit tests: `ContinueItemsTest` (ordering, one tile per show, unplayable rows dropped, cap, progress, time-left wording, per-profile resume keys). All app and provider tests pass.
- Checked on the Fire TV with a side-by-side install against the fake portal: first-run Home with focus on the first door; a movie played 40 s appeared as a tile with "4 min left"; selecting it resumed at 0:53 (40 s earlier plus a few seconds); two episodes of one show gave a single tile ("Season 1 · S01 E02") ahead of the movie. Side app uninstalled, fake portal stopped, screenshots deleted. The real portal was not contacted.
- Known gaps: after Back from the player focus lands on the tab you came from (not the tile); no way yet to remove a tile or mark watched; Search door opens the placeholder.

## Home: Recently watched, options menu, clock, focus (2026-11-21; fake portal and a local test video, no provider contact)
- Home rows: Continue watching, then Recently watched (finished titles, green "Watched" bar; a show already in the first row is not repeated), then the doors. The page scrolls when the rows are taller than the screen.
- Menu key on a tile opens options: Remove from Home, Mark as watched or Mark as not watched (starts again from the beginning), Cancel. The D-pad stays inside the menu and Back closes it. Focus follows the tile to its new row, or to the next tile after a removal.
- A clock (the device's 12 or 24 hour setting) sits at the right of the tab bar on every screen.
- Focus: Home waits for its rows to be re-read before taking focus, so Back from a player lands on the tile that was played. With only finished titles, the first Recently watched tile takes first focus.
- Tests: two more `ContinueItemsTest` cases (watched label and full bar; no repeat across the two rows). Checked on the Fire TV with a side-by-side install: tile, menu, mark watched (moves to Recently watched), mark not watched (moves back), Back from the player onto the tile, remove (welcome state). Side app uninstalled, fake portal stopped, scratch files deleted. The real portal was not contacted.
- Not done: Favorite channels, "new in your categories" and Live rows (need favorites and a category chooser first).

## Favorites (2026-11-21; fake portal and a local test video, no provider contact)
- Channels, movies and shows can be favorites, kept per profile in Room (schema 3 adds the command and catalog details, so a favorite opens again without browsing to it; migration 2 to 3).
- Marking: Menu key on a channel in Live TV; an "Add to favorites" button beside Play on movie and show pages. A heart shows on the channel row and on the title card. Menu on a tile in the Favorites tab removes it.
- Favorites tab: Channels (in channel-number order), Movies, Shows. Selecting a channel plays the favorites as a list, so Up and Down zap between them and Back lands on the channel last watched. A movie opens its full detail page from the stored details; a show opens its seasons.
- Home: a "Favorite channels" row between the watching rows and the doors.
- Fixes found on the TV: the last card of a row had its focus ring clipped (rows now keep a little end padding); after a removal focus moves to the next tile in the row instead of dropping to the tab bar.
- Tests: `FavoriteItemsTest` (movie and show round trip, channel number order, a fixed channel list). Checked on the Fire TV with a side-by-side install: hearts in Live TV, Home row, the Favorites tab, zapping between favorites, movie detail from a favorite, removal and focus. The real portal was not contacted.

## Search (2026-11-21; fake portal, no provider contact)
- Search tab: a text box (the system keyboard), results grouped as Channels, Movies and Shows. The search starts about 0.8 s after typing stops, or at once on the keyboard's Done; two letters minimum. Select opens a result like the other screens (movie page, show page, channel player), and Back returns to the same result with the results kept.
- Titles use the portal's own `search` parameter on the VOD list (all categories, up to three pages), ranked so titles starting with the text come first, then titles containing it, then other matches the portal made on other fields. Channels are found by reading all channels once (`get_all_channels`, kept for the session) and filtering by name or exact number, at most 30; the channel results play as a list that Up and Down zap through.
- The typed text is only in the request itself: the logged purpose line, the debug log and the fake portal's request log carry no query (checked in logcat after searching).
- Home's Search door opens it; the options hint on Home no longer shows when there are no tiles.
- Tests: `SearchRankingTest` (ordering, entities and spaces, channel name and number matching, the cap, empty results) and `SearchRequestsTest` in the provider module (encoded text, purpose without it, all-channels payload as a bare array or a data array). Fake portal: `search` on VOD lists and `get_all_channels`.
- Not verified on the real portal: that its VOD `search` parameter filters as expected and what `get_all_channels` returns and costs (5,100 channels). Needs an approved, kid-safe query run. Until then channel search may report "Channels could not be searched" on that portal; movies and shows are unaffected.

## Connection status moved (2026-11-21)
- The "Portal connected · account active until …" line is now small type (18 sp) in the bottom-left corner on every screen except the players and setup, instead of a line under the tab bar, so content starts higher. Checked on the Fire TV with the fake portal.

## Search on the real portal (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Method: the current debug build installed over the real app, two searches with kid-safe words ("toy story", then "cartoon"), counts-only logging through a debug hook, the app force-stopped afterwards, screenshots deleted. No stream was played.
- **Titles: the server does the matching.** The portal's `search` parameter on the VOD list works, across all categories, one request for a short result. It matches more than titles (descriptions or other fields), so results include titles that do not contain the words. Our part is only ordering: titles starting with the text first, then containing it, then the rest, and the split into Movies and Shows. The catalog has repeated titles (the same film in several quality or language versions, each with its own id), which show as repeats.
- **Channels: we do the matching.** The portal ignores `search` on the channel list (it returned the unfiltered list, 4,939 entries in that view), so the app reads the full channel list once (`get_all_channels`, 5,092 channels, 1.3 s to download, about 2.5 s to read) and filters it by name or number on the device. The list is kept for the session, so later searches skip the download.
- **Bug found and fixed:** the 1 MB response cap cut that list off, so channels reported "could not be searched". The call now has its own 8 MB cap. A list larger than that still fails gracefully (movies and shows are unaffected). The app skips the pointless server channel search after the first time it sees it ignored.
- First search about 5 s (downloading and reading the channels), later ones about 1 s. Java heap read 75 MB right after the first channel read (a transient peak, not yet measured over time).
- UI fixes from the screenshot: the bottom-left status line has a dark backing so it stays legible over scrolled posters; channel cards are taller so two-line names fit.
- Mature-looking results can appear for harmless words (the server matches descriptions), which matters for a shared TV; the parental-control work (parked) would filter these.

## Guide (EPG) probe on the real portal (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Method: a debug-only activity (`EpgProbeActivity`) using the app's saved settings, about six requests in one session, logging only key names, counts and time spread (no programme text), then the app force-stopped. No stream was played.
- **A guide exists, per channel.** `get_short_epg` for one channel returned 10 programmes, from 0.5 h ago to 6.5 h ahead, in 0.25 s. Each programme has: id, channel id, name, description, category, director, actor, start and stop times (epoch seconds and text), duration, and two flags (`mark_memo`, `mark_archive`, the catch-up marker).
- **No bulk guide.** `get_epg_info` for all channels over 2 hours answered with an empty list. So there is no one-call guide for everything.
- **What it means.** A TV Guide screen is possible (a channel's next programmes, now and next). Searching programmes across all channels would need one request per channel (about 5,100 channels, roughly 20 minutes of requests for one sweep that covers about 7 hours), best limited to favorites, recently watched channels and channels the viewer opens. Whether a larger `size` reaches further ahead than 6.5 h is not tested.

## Search: merged versions, suggestions, local title index (2026-11-21; fake portal for the app, real portal only for the guide probe)
- **Merged titles.** Repeated catalog entries (the same film in several quality or language versions) are folded into one tile with a "N versions" badge. Names are compared with quality and language words and brackets removed (4K, 1080p, HD, CAM, Hindi, ...); different stated years stay apart; movies and shows are never mixed. The best quality leads (4K, 1080p, HD, unstated, SD, CAM last). The movie and show pages get a Versions button that opens a picker (labelled with the quality and language words found in each name, or "Version n"); picking one reopens the page for that version.
- **Instant suggestions** from text already on the device, with no portal request: recent searches (shown when the box is empty), favorites, watch history, channel names (the channel list is read ahead the first time Search opens), and the local title index. Whole-text prefixes rank above word prefixes; a title and its quality variants count once. Recent searches are stored per profile (Room schema 4).
- **Faster type-ahead.** The search starts 0.35 s after typing stops once there are three letters (0.8 s for two), or at once on Done.
- **Local title index (opt-in).** A button on the Search page starts a gentle crawl of the whole VOD catalog into Room: one request at a time through the shared session, 0.3 s between requests, paused while a player is open or the portal is disconnected, adult categories skipped, resumable per category, refreshed after seven days. Rows are lean (name, year, poster path, command, age label, category; no description or cast).
- **Size and time (estimates, not yet measured on the real portal).** About 130,000 titles (104,000 movies plus 27,700 shows) at about 150 bytes a row is roughly 20 MB; at 14 titles a page that is about 9,300 requests, so one to two hours at the measured 0.3 to 0.8 s each, plus the pauses. The fake portal indexed 234 titles in seconds.
- **Not run on the real portal.** Starting the index there is the user's choice from the Search page (it reads the catalog for a couple of hours; StbEmu and TiviMate should be stopped meanwhile).
- Tests: `TitleMergeTest`, `SuggestionEngineTest`, `TitleIndexerTest`, `EpgShapeTest`. Checked on the Fire TV against the fake portal: the opt-in offer, index build, suggestions from it, "N versions" tiles, the picker, and the no-results state. Focus fixes: after the index button disappears focus returns to the search box; suggestions shrink to three rows when results are showing.

## Search filters and a saved default (2026-11-21; fake portal, no provider contact)
- **Filters under the search box:** Language, Genre, Category and Rating, combined (all must hold; several choices inside one filter mean any of them), plus a Rotten Tomatoes button. Each button shows what is chosen. Save as default keeps the current set and applies it at the start of every visit; Use default and Clear filters restore or turn them all off.
- **How each works.** Category: the portal is asked one category at a time (its `category` parameter on the VOD search), up to six; a note says when more matched. Language: the portal has no language field, so a language means the categories whose names mention it ("ENGLISH MOVIES 4K", "HINDI WEB SERIES"); the picker offers only languages some category mentions. When both language and category are set, a category must satisfy both. Genre: matched against the genres each title lists. Rating: the portal's own value (IMDb when present, otherwise Kinopoisk); titles with no rating are left out when a minimum is set. Channels have none of these properties, so they are hidden while any filter is on.
- **Rotten Tomatoes is not available.** The portal carries no Rotten Tomatoes data. The button opens a short explanation. It would need an outside ratings service and a free key from the owner (looking each result up and keeping the scores on the device); not set up, and nothing is sent anywhere.
- **Not checked on the real portal:** that the `category` parameter restricts a search there as it does on the fake portal; the genre text and language wording the real categories use (the language list is built from the real category names, so it adapts).
- Tests: `SearchFiltersTest` (saving and loading, language reading, category plan, genre and rating rules). Checked on the Fire TV against the fake portal: pickers, combined filters, restricted requests (only the two English categories were asked), the saved default after a restart. Focus fix: after Save, Use default or Clear the focus returns to the Language button instead of falling to the tab bar.

## Search filter: English subtitles (2026-11-21; fake portal, no provider contact)
- A toggle beside the other filters, saved with the default set. It keeps a title when its name or its category name says it has English subtitles ("[Eng Sub]", "English Subtitles", "ESub", "Eng Subs", "Hardsub"); words like "Subway" or "Substitute" do not count.
- **Why a wording check.** The portal gives a title's subtitle files only when it is played (the play-link answer), not in its catalog lists, so the catalog cannot be filtered by real subtitle data. The screen says so. Titles that have subtitles but do not say so in their name are missed.
- **Not yet known:** whether the real catalog's list entries carry any subtitle or language field (a key-names-only look at one real list answer would show it; needs an approved run), and how often real names use these words. A per-title check through the play-link request would be exact but costs one request per result and may count as a play, so it is not done.
- Tests: wording recognition (including the false friends) and the filter rule. Checked on the Fire TV against the fake portal: only the "[Eng Sub]" titles remained.

## What a real VOD list entry carries (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Method: a debug-only activity (`VodShapeProbeActivity`), one request for the first page of the whole VOD list, logging field names and fill counts only (never values), then the app force-stopped. Nothing was played or displayed.
- **No subtitle, language, audio or track field exists** in a list entry (no key name mentions any of them). So the English subtitles filter stays a wording check; the exact answer is only available through the play-link request, one title at a time.
- **Fields that do exist** (14 entries on the page): `category_id` (plus `category_id_1`, `category_id_2`), `genre_id` and `genre_id_1` to `_4`, `genres_str`, `country`, `year` and `year_end`, `o_name` and `old_name` (original name), `hd`, `high_quality`, `low_quality`, `sd`, `has_files`, `is_movie`, `is_series`, `rating_imdb`, `rating_kinopoisk`, `rating_count_imdb`, `rating_count_kinopoisk`, `rating_mpaa`, `kinopoisk_id`, `age`, `added`, `screenshot_uri`, `screenshots`, `description`, `actors`, `director`, `series`, `cmd`, `path`, `file`, `protocol`, `status`, `lock`, `censored`, `fav`, `last_played`, plus vote and count fields.
- **Useful consequences.** `category_id` is on every entry, so results can be filtered by category on the device after one search request instead of asking the portal once per category. `country` may help with a language or origin filter. `hd`, `high_quality` and `low_quality` give a real quality flag for ranking versions. `o_name` (original name) can improve merging and matching. `kinopoisk_id` lets a ratings service be queried exactly if one is added later.

## Playback controls: accelerating seek and Up next (2026-11-21; fake portal and a local test video, no provider contact)
- **Accelerating seek.** Holding Left or Right makes the step grow with the key's repeat count: 10 s, then 30 s, 60 s, 3 minutes and 5 minutes, so a long film can be crossed in a few seconds. A single press is still 10 s; the remote's rewind and fast-forward keys still skip 30 s. (The held-key ramp is unit-tested; adb cannot send a real held key, so it was not seen on the TV.)
- **Up next for episodes.** In the last 2.5 minutes the app looks up the next episode (the seasons, the episodes of the current season, and the next episode's files: about four requests) and shows a card in the last 30 s: "Up next: Season 1 · S01 E02 | Episode 2, OK to play now". Select starts it at once; at the end it starts by itself after 8 seconds; Back leaves as before. The end of a season leads to the next season that has episodes; the end of the show offers nothing. Works for episodes started from the show page, from Continue watching and from search results alike (the show, season and episode ids come from the resume key).
- A bug found on the TV and fixed: the lookup ran in an effect that every position update restarted, so its answer was thrown away; the condition is now a plain flag.
- Tests: `NextEpisodeTest` (next in season, across seasons skipping empty ones, end of show, unknown ids, key parsing) and the seek steps. Checked on the Fire TV against the fake portal: the card, then the automatic hand-over to the next episode.

## Live TV pause and rewind (2026-11-21; fake portal for the app; the real window not yet measured)
- Select or the play/pause key pauses a live channel; Left and Right skip within what the stream keeps (10 s, growing while held, 30 s on the rewind and fast-forward keys); a skip that passes the live edge rejoins live. The screen shows "Paused" and, for a real live stream, "Live" or "-m:ss behind live". A stream that keeps no history says "This channel cannot be rewound". Up and Down still change channel.
- A debug log line records the stream's window (live, seekable, length in seconds) so the real channels' rewind depth can be read once a real channel is played.
- Checked on the Fire TV against the fake portal (a progressive file, not live): pause and the Paused label. The real portal's channels are not yet measured; that needs a (kid-safe) channel played once.

## TV Guide screen (2026-11-21; fake portal for the app; the real guide format seen in the earlier probe)
- The TV Guide tab lists the channels of a group (the favorites when there are some, otherwise "All channels"; the Group button picks any favorite or portal group) with what is on now (title, start to end time, a progress bar) and what is next. Select a row to watch; the listed channels are what Up and Down then zap through, and Back returns to the same row.
- **How it loads.** The portal gives a guide one channel at a time (`get_short_epg`), so each row asks for its channel when it first appears on screen (about a quarter of a second each, one at a time through the shared session) and the answer is kept for ten minutes, including "this channel has no guide". The times shown follow the device clock and refresh every 30 s. A channel with no data says "No guide data"; if the portal cannot be asked a row says "Guide unavailable".
- Tests: `EpgParserTest` (time order, dropped entries, the older time fields, unusable payloads) and `GuideLogicTest` (now, next, boundaries, gaps, before and after the listings, progress). Fake portal: `get_short_epg` with programmes around the current time and one channel with none. Checked on the Fire TV: rows, progress bars, "No guide data", opening a channel and Back.
- Not built: a time grid across channels, programme descriptions on selection, and searching programmes (see docs/TODO.md).

## Settings screen (2026-11-21; fake portal)
- Sections: **Portal** (connection state and expiry, Change portal, which returns to the setup form); **Search** (title index: build, pause or resume, delete the data; clear the default filters; clear recent searches); **Watching** (play the next episode automatically on or off; clear Continue watching and the watched list; clear favorites); **Memory** (poster cache size and a clear button); **About** (version, licence, where data is kept).
- Anything that deletes asks first ("Yes, clear" or Cancel). The next-episode choice is saved on the device and the player honours it (the Up next card still shows; only the automatic start is skipped).
- No device setting is changed by this screen. Checked on the Fire TV against the fake portal: the layout, the automatic-play toggle and the confirmation for clearing favorites.
- Not here yet: the parental-control and profile settings (deferred), and a theme choice.

## Home rows from pinned categories and a live group (2026-11-21; fake portal)
- **Pinning.** Menu on a category in Movies or Shows pins it; Menu on a group in Live TV pins it as the Home live group (one at a time; pinning another replaces it). A tick marks pinned entries. Pins are saved per profile in Room (the existing pinned-categories table).
- **Home rows.** "New in <category>" for up to three pinned categories (the portal lists newest first, so the first twelve titles are shown), and "Live · <group>" with the first twelve channels. Select opens a title's page or plays the channel (the row is what zapping then moves through); Back returns to the tile. Rows appear as "Loading…" at once and fill in one portal request each, only while Home is showing and connected, and are kept ten minutes. With no pins, Home shows a one-line tip.
- Tests: `HomeRowPlanTest` (order, the cap of three, the live pin kept apart, the next position). Checked on the Fire TV against the fake portal: a pinned movies category and a pinned live group both appeared as rows.

## Title index on the real portal (2026-11-21, user-approved; StbEmu and TiviMate confirmed not running)
Method: the current debug build installed over the real app, the index started from Settings, counts read from the app's own status line and the redacted `OpenStream.portal` log, memory from `dumpsys meminfo`. No stream was played.
- **A crash found and fixed.** The Settings text "Building 3% · N titles" was built with `String.format`, and the `%` before the middle dot is a format code, so the app crashed the moment the index showed progress. The fake portal never hit it because its index finished in seconds. Fixed (`%%`), with a test.
- **Measured rate.** About 5,500 titles in 4.5 minutes, roughly 20 titles a second (about 1.5 pages a second, each page 14 titles, each request 0.2 to 0.6 s plus the 0.3 s pause). The adult categories are skipped. At that rate the whole catalog of about 130,000 titles takes about an hour and three quarters.
- **Measured footprint.** About 1 MB of database per 6,000 titles, so about 22 MB for the whole catalog (matches the estimate). Java heap 12 MB, total PSS 130 MB while crawling. No request errors in the first five minutes.
- **Screen kept awake while building.** The Fire TV screensaver would stop the crawl (the app releases its portal session when it is not in front), so while the index builds the app's own window keeps the screen on (a window flag, not a device setting). It clears when the index finishes or is paused.
- **While it builds, StbEmu and TiviMate must stay closed** (one session per MAC). Pause in Settings first if either is needed.

## Sort and filter inside a category, and a per-profile content filter (2026-11-21; built and unit-tested, not yet checked on the TV)
- **Sort and rating chips above every category grid** (Movies and Shows): Sort by newest added (the portal's order), release year, best rated (IMDb, else Kinopoisk), IMDb, Kinopoisk or name; and a rating floor (show everything, or hide below 5, 6, 7 or 8). Each shows what is chosen; the count above the grid follows the filter.
- **How it works.** The portal cannot sort a category by year or rating (it lists newest added first), so these sorts run on the device from the title index. The index rows now also hold the portal's IMDb and Kinopoisk ratings and each title's position in its category (schema 5). A category can be sorted once its pages are all in the index; until then the Sort picker says so and the portal's order is shown, with the rating floor still applied to each page as it arrives (the list reads on through pages the floor empties). Titles with no rating come last in the rating orders.
- **Opening a title from the device copy.** Those rows are lean (no description or cast), so the full entry is fetched from the portal by name inside its category (one request) before the page opens.
- **Per-profile default (Settings, "Content filter · this profile").** Hide movies and shows rated below 5, 6, 7 or 8, and whether titles with no rating are shown or hidden. Stored per profile on the device (keys carry the profile id). It applies to category lists, search results (unless a rating filter is chosen in that search) and Home's "New in" rows; each category view can change the floor for that view only.
- **The index must be rebuilt once.** Rows built before this change have no ratings, so the first time the index runs after updating it starts its pass over and replaces them page by page (about 1.75 hours on the real portal; categories become sortable as each completes).
- Tests: `ContentPolicyTest` (floor, unrated, IMDb over Kinopoisk, summaries, a page the filter empties, sort codes, index rows keeping ratings and order). The sort query's SQL is checked by the compiler; its behaviour on a real database is not yet exercised on the TV.

## Viewing stats ordering search, and search defaults everywhere (2026-11-21; unit-tested, not yet checked on the TV)
- **Stats per profile.** A title counts once, the first time it has been played for a minute (persistently, so re-watching does not count again): the title itself (shows and movies by name with quality and language tags removed, so every version counts together), each of its genres, and each language its name or category mentions. Stored in Room (`viewing_stats`, part of schema 5) on the device only. Settings, "Your viewing · this profile", shows the top shows, genres and languages and can reset them.
- **Search order.** Relevance still comes first (titles starting with the text, then containing it, then the rest). Inside each group, titles the viewer already watches come first, then titles whose genres and languages match what the viewer watches most, then the portal's order. Nothing is hidden by it, and a profile with no history gets the old order. Settings has an on/off switch.
- **Search defaults everywhere (Settings, off by default, per profile).** When on, the saved default search filters also apply to: Movies and Shows (the category list narrows by language or category; each title is checked for genre, rating and English-subtitle wording; sorting from the device copy still works when the defaults are genres and ratings, and falls back to the portal's order when the subtitle wording check is on); Home's "New in" rows; the Live TV groups and the guide's group list (only the language applies there, hiding groups whose names mention another language, since channels have no genre or rating).
- **The index now also keeps each title's genres** (part of schema 5), so genre defaults work on the device copy.
- Tests: `PersonalizationTest` (tags, scores, version counting, the order of search results with and without stats) and `DefaultScopeTest` (categories, titles, live groups, what the device copy can express).


## Summary, 2026-11-22 to 2026-11-25 (checked on the Fire TV unless noted)
- **Profiles and family safety** (11-22): household PIN, Kids rules, profile chooser; online tags (themoviedb.org) fill age labels, genres and scores; IMDb ratings from IMDb's free personal-use file; Language filter and per-category sort and filter memory.
- **Release groundwork** (11-22): signed release build (RSA key outside the repository), cleartext allowed in release, privacy policy, notices, store text; app name OpenStream and its logo.
- **Sports** (11-22 to 11-24): Sports tab with the NFL search (2.7 s after the groups load, down from 42 s), then a games entry per league (NFL, college football, soccer, cricket, Formula 1) from one guide pass, rail counts, and a muted live preview inset beside channel lists. Now and next on Live TV rows.
- **UX runs** (11-22 to 11-24): journeys and focus crawls, persona runs (impatient, goal-driven, ADHD, browser, settings critic), playback and review-fix runs; fixes for focus, paging bursts and Back behaviour. Logs in `docs/ux-runs/`; method in `docs/TESTING_ON_FIRE_TV.md`.
- **Hardening** (11-23): screen sleeps when paused, shared HTTP client, one IMDb transaction, focused card on top, and a secure window (`FLAG_SECURE`) that blocks screenshots and recording.
- **Publishing** (11-23): the app moved to `main` of the public repository, with the web prototype under `prototype/`.
- **Saved searches** (11-24): Saved tab with a form editor, per-profile storage and a runner over the on-device index; checked on the TV (see `docs/ux-runs/2026-11-24-saved-searches.md`).
- **Version 0.1.1 (code 2)** (11-25); launcher banner and icon moved to the standard TV size. The launcher tile still shows plain navy on Fire OS (open item in `docs/TODO.md`).
- **CI** (11-25): a GitHub Actions workflow runs the unit tests and builds the debug APK on every push and pull request.
