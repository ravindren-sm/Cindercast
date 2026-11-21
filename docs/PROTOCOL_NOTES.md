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
