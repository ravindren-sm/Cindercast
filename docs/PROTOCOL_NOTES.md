# Stalker protocol notes (clean-room research)

Behavior observed in public open-source clients, read 2026-11-21 for reference only (CLAUDE.md
rule 11). No code was copied. Sources:
- Kodi `pvr.stalker`, branch Omega: https://github.com/kodi-pvr/pvr.stalker
  (`src/stalker/SAPI.cpp`, `HTTPSocket.cpp`, `ChannelManager.cpp`, `SessionManager.cpp`,
  `CWatchdog.cpp`, `src/StalkerInstance.cpp`, `lib/libstalkerclient/*.c`)
- stalkerhek (archived 2021): https://github.com/erkexzcx/stalkerhek and its wiki
  https://github.com/erkexzcx/stalkerhek/wiki/Services (`stalker/*.go`, `hls/*.go`)

## Portal API requests

| Item | pvr.stalker | stalkerhek | Our probe today |
|---|---|---|---|
| User-Agent | MAG200 stbapp (fixed) | MAG200 stbapp (fixed, not configurable) | honest `firetv-iptv-probe/0.1` |
| X-User-Agent | `Model: MAG250; Link: WiFi` | `Model: <model>; Link: Ethernet` | none (only with `--identity=mag`) |
| Cookie | `mac`, `stb_lang`, `timezone` | `PHPSESSID=null`, `sn`, `mac`, `stb_lang`, `timezone` | `mac`, `stb_lang`, `timezone` |
| Referer | portal path | none | portal URL |
| Authorization | Bearer, except on handshake | Bearer, except on handshake | Bearer, except on handshake |

Both reference clients present a MAG User-Agent on every portal call. Neither has an honest
mode.

## create_link and the stream URL
- pvr.stalker calls `create_link` at play time, only for channels flagged `use_http_tmp_link` or
  `use_load_balancing`; otherwise it uses the channel `cmd` directly. Our channels' `cmd` is a
  `localhost` placeholder, so `create_link` is required either way.
- Both clients drop everything before the URL in `cmd` (for example `ffrt `). Ours does the same.
- pvr.stalker sends `forced_storage=undefined`, `disable_ad=0`. Ours sends `forced_storage=0`
  plus `series`, `disable_ad`, `download`; the portal returned no error.
- stalkerhek treats an HLS link as fresh for about 30 s after last use and calls `create_link`
  again after that.

## Stream fetch (the part that got 403)
- **pvr.stalker passes the plain URL to Kodi's player: no Referer, no Cookie, no Authorization,
  no X-User-Agent.** The player uses Kodi's own User-Agent (Kodi core; not verified here).
- **stalkerhek fetches playlists and segments with only the MAG User-Agent header.** No Referer,
  Cookie or Authorization. It follows redirects itself (no hop limit) and rewrites playlist URLs.
- So extra headers on the stream request are **not** what working clients rely on. That makes
  "missing Referer/Cookie" an unlikely cause of our 403 and removes the need to test it first.
- stalkerhek's wiki: tokenized stream links may be mapped to the source IP and expire in
  minutes if unused. Our probe and the Fire TV share one public IP, and we fetched within
  seconds, so neither should apply.

## Session keep-alive
- Watchdog request: `type=watchdog&action=get_events&init=0&cur_play_type=1&event_active_id=0`.
- pvr.stalker polls every profile `timeslot` seconds; stalkerhek every 2 minutes. Our profile has
  `watchdog_timeout: 180`, so about every 2 minutes is a safe default (Step 4g).
- Re-authentication: both re-run handshake + profile when a call fails with the text
  `Authorization failed.` (non-JSON body). pvr.stalker retries every 30 s.

## What this means for the 403
Remaining likely causes, most likely first:
1. **User-Agent.** Our honest probe User-Agent is the one thing every working client does
   differently. A probe-like name may also trip a generic bot filter at the edge.
2. **Concurrency/session rules** at the edge (TiviMate had played shortly before).

A single run with the MAG identity on all requests, and only the User-Agent on the stream fetch,
reproduces stalkerhek's known-working profile exactly. It isolates cause 1 in one run, and it
is the one MAG retry the plan allows (PHASE2_PROBE_PLAN.md section 9).

If it returns 200, the app question becomes narrower: does the edge need the MAG User-Agent
on stream requests, or only a normal player User-Agent (as Kodi sends)? That is best tested in
the real player (Media3) on the Fire TV.

## Known quirk: Mac CLI probe gets 403 at the stream edge (closed 2026-11-21)
The Mac probe (`:probe`) reaches every portal API call and `create_link`, and follows the
stream's `301` (Cloudflare) and `302` (HLS) redirects, but the third host answers `403`.
The Fire TV debug player, with the same account, channel and public IPv4, gets `200` and plays.
Tried from the Mac without change: honest and MAG User-Agents, two channels, a 210 s wait.
Likely cause, unverified: address family (the Fire TV has global IPv6, the probe used IPv4) or
another client/network-path difference at the edge. **Treated as a CLI-only environment quirk.**
Investigation stopped by the user's decision; all further protocol checks (4e-4g) run in the
Fire TV debug player. If it ever matters, log IPv4/IPv6 per stream connection in the debug
build first.

## VOD playback design (Step 4f, 2026-11-21; assumptions until verified live)
Inputs from 4e: shows live inside `type=vod` (`is_series=1`); `cmd` values are server paths;
the top-level `series` field was empty on the newest shows.

Movie (one request after choosing the item):
- `type=vod&action=create_link&cmd=<item cmd>&series=&forced_storage=undefined&disable_ad=0&download=0&force_ch_link_check=0`
- Expected: `js.cmd` holds a playable URL (possibly with a prefix word, stripped as for live).

Episode, two known Ministra shapes; the client supports both and logs which one it found:
1. **Navigation** (expected here): `get_ordered_list&movie_id=<show id>&season_id=0` lists
   seasons; `...&movie_id=<show id>&season_id=<season id>` lists episodes, each with its own
   `cmd`; then `create_link` with the episode's `cmd`.
2. **Episode numbers on the show** (older portals): the show's `series` array lists episode
   numbers; `create_link` with the show's `cmd` and `series=<episode number>`.

Choosing items: VOD category by title (exact match, else the shortest title containing the
text, because titles here combine labels with " | "), then the first suitable item on page 1.
Seek check (VOD only): after 10 s of playback, seek to the middle (at most 5 min in) and log
the time until playback resumes.

Update after the first episode run (2026-11-21): navigation is show -> seasons -> episodes ->
**files**. Seasons and episodes carry no `cmd`; episodes carry `series_files`. The file level is
`get_ordered_list&movie_id=<show>&season_id=<season>&episode_id=<episode>`; each file should
carry the `cmd` for `create_link`. The client uses inline `series_files` cmds if present,
otherwise requests the file level.

## Session keep-alive design (Step 4g, 2026-11-21; assumptions until verified live)
- Watchdog: `type=watchdog&action=get_events&init=0&cur_play_type=1&event_active_id=0`, with the
  bearer token (stalkerhek and pvr.stalker send this shape). Interval 120 s, safely inside this
  portal's `watchdog_timeout` of 180 s. pvr.stalker uses the profile's `timeslot`; to compare later.
- Expiry signal: both reference clients treat a non-JSON body `Authorization failed.` as an
  expired session and re-run handshake + `get_profile`. Our client flags such a response as
  `authFailed` and re-authenticates once (no loops).

One-run device test (at most 10 portal requests, about 8.5 minutes, no playback):
1. handshake, `get_profile`                                   (2)
2. watchdog at t = 0, 120, 240 s                              (3)
3. at about 250 s: `get_genres` as an authenticated check     (1)  -> token valid with keep-alive?
4. 240 s idle, no watchdog; then `get_genres` again           (1)  -> does the token expire without it?
5. only if step 4 says `Authorization failed.`: handshake, `get_profile`, `get_genres` (3)
If step 4 still succeeds, the re-auth path is covered by unit tests only, and the log says so.
