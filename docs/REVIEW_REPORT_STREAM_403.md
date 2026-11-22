# Technical review request: Stalker portal stream returns 403 to our client

Prepared 2026-11-21 for an external technical review (for example Gemini). All provider values
are placeholders: `<PORTAL_HOST>`, `<EMU_MAC>`, `<TOKEN>`. No secrets are included.

## 1. Project summary
- Goal: a free, open-source Fire TV client for Stalker/Ministra (MAG) IPTV portals, covering
  Live TV and VOD. Built for the user's own paid account. Clean-room: no decompiling of
  proprietary apps; open-source clients (Kodi `pvr.stalker`, stalkerhek) used as behavioral
  reference only.
- Target device: Fire TV Stick 4K Plus (AFTMA08C15), Fire OS 8.1.8.5 on Android 11 (API 30),
  32-bit `armeabi-v7a` only.
- Stack: Kotlin. `:provider` = plain Kotlin/JVM library (OkHttp 4.12, kotlinx.serialization),
  shared between a Mac command-line probe (`:probe`) and the Android app (`:app`, Compose for
  TV + Media3 1.6.1). Gradle 9.5 wrapper, AGP 8.13, Kotlin 2.1.20.
- Account facts: Stalker portal only (reseller confirmed no Xtream/M3U login; `/player_api.php`
  returns 404). Auth is MAC-only.

## 2. Reference result: a third-party app works
TiviMate 5.3.3 on the same Fire TV, configured with portal URL + MAC only, loads 5,092 live
channels, 99,461 movies, 27,736 shows, and plays live video (verified by screenshot several
times, including News 12 Long Island). So the account and portal accept third-party
MAC-only clients, and streams play from the Fire TV.

## 3. What our client has proven (Mac probe, same home LAN as the Fire TV)
All portal calls: `GET http://<PORTAL_HOST>/stalker_portal/server/load.php?...&JsHttpRequest=1-xml`
over plain HTTP (behind Cloudflare). Headers on portal calls: `User-Agent`, `Referer` (portal
client URL), `Cookie: mac=<EMU_MAC>; stb_lang=en; timezone=America/Los_Angeles`, `Accept: */*`,
`Authorization: Bearer <TOKEN>` (after handshake). OkHttp adds `Host`, `Connection: Keep-Alive`,
`Accept-Encoding: gzip`.

| Step | Request | Result |
|---|---|---|
| 4a | `type=stb&action=handshake&token=` | 200 JSON; keys `not_valid` (0), `random`, `token` |
| 4b | `type=stb&action=get_profile&hd=1&stb_type=MAG250&auth_second_step=1&not_valid_token=0` | 200; `status` 0, `blocked` 0, `watchdog_timeout` 180 |
| 4c | `type=itv&action=get_genres`; `type=itv&action=get_ordered_list&genre=*&fav=0&sortby=number&p=1` | 74 genres; 4,939 channels, 14 per page |
| 4d | `type=itv&action=create_link&cmd=<cmd>&series=&forced_storage=0&disable_ad=0&download=0` | 200; keys `cmd,error,id,link_id,load,streamer_id`; `error` = "" |

Channel `cmd` values look like `ffrt http://localhost/ch/<id>_` (placeholder; create_link
required). `create_link` returns a plain `http://` URL whose host has a non-standard name
(Java `URI.host` is null, likely an underscore).

Selected channel fields (names only): `nginx_secure_link`, `use_http_tmp_link`,
`use_load_balancing`, `wowza_tmp_link`, `flussonic_dvr`, `nimble_dvr`, `archive`,
`tv_archive_duration`, `xmltv_id`. Profile fields include `now_playing_*`, `keep_alive`, `ip`,
`last_change_ip`, `allowed_stb_types`, `strict_stb_type_check`, `timeslot`.

## 4. The failure: stream fetch ends in 403
The probe fetches the `create_link` URL with only a `User-Agent` header (no Referer, cookie or
bearer), following at most 2 redirects manually, reading at most 64 KB:

| Hop | Host | Response |
|---|---|---|
| 0 | host A (from `create_link`) | `301`, `Server: cloudflare`, HTML, `Location` to host B |
| 1 | host B | `302`, `Content-Type: application/vnd.apple.mpegurl`, 151-163 byte body, `Location` to host C |
| 2 | host C | `403`, `Content-Type: application/vnd.apple.mpegurl`, 14-byte body starting `4` (likely "403 Forbidden") |

Each run fetched the stream within seconds of `create_link`. Query strings (tokens) are present
in every hop's URL but were not logged.

### Variables already tested (all 403 at hop 2)
| Run | Identity (portal + stream) | Channel | Notes |
|---|---|---|---|
| 1 | honest UA `firetv-iptv-probe/0.1` | first by number (ch 40, Canadian 4K) | - |
| 2 | MAG200 stbapp UA + `X-User-Agent: Model: MAG250; Link: WiFi` on portal calls, MAG UA on stream | ch 40 | 210 s wait first (> `watchdog_timeout`), StbEmu and TiviMate force-stopped and verified not running |
| 3 | honest UA | News 12 Long Island 4K (the channel TiviMate plays), via genre lookup | apps force-stopped, verified |

### Ruled out (with evidence)
- User-Agent (honest vs MAG): same 403.
- Specific channel: same 403 on the TiviMate-verified channel.
- Lingering TiviMate session: 210 s wait above `watchdog_timeout`.
- Account flagged: TiviMate still plays after every run; profile `status` 0, `blocked` 0.
- VPN: IPv4 egress via home router (`en0`); no VPN configs or network extensions. Mac and Fire
  TV share the home public IPv4. JVM prefers IPv4.

### Not ruled out
- Address family: the Fire TV has global IPv6 addresses; the probe used IPv4. If host C binds
  the link to the requesting address and TiviMate reaches the edge (or the portal via
  Cloudflare) over IPv6, behavior would differ.
- Missing player-state calls: no `type=watchdog&action=get_events&cur_play_type=1` or other
  play-state call was sent before fetching (open-source clients poll watchdog periodically;
  profile has `now_playing_*` fields).
- Hop 1 body: the 302 at host B carries an HLS content type and a small body; real players
  follow `Location`, but the body may matter.
- Something specific to how real HLS players request (headers, connection reuse, timing).

## 5. Open-source reference behavior (read for behavior only)
- Kodi `pvr.stalker` (branch Omega): portal calls use MAG200 UA, `X-User-Agent: Model: MAG250;
  Link: WiFi`, cookie `mac/stb_lang/timezone`, Referer, Bearer (not on handshake). Calls
  `create_link` at play time only for channels flagged `use_http_tmp_link` or
  `use_load_balancing`; strips the cmd prefix; hands the plain URL to Kodi's player with
  **no** extra headers (Kodi's own UA). Watchdog interval = profile `timeslot`.
- stalkerhek (archived 2021): MAG UA on everything; fetches HLS with **only** the MAG UA,
  follows redirects itself, rewrites playlists; refreshes links after ~30 s idle; watchdog every
  2 minutes. Its wiki notes temporary stream links "might also be mapped to your source IP" and
  expire within minutes if unused.

Our stream request matches stalkerhek's profile (MAG run), yet still gets 403.

## 6. Safety constraints on further testing
- Every live portal run needs the user's approval; StbEmu and TiviMate are force-stopped and
  verified first (one active connection per MAC); request budget <= 10 per run, >= 2 s apart,
  no automatic retries; plain HTTP accepted by the user; TLS never relaxed; all output
  redacted. After a 403, probing stops until the user decides.

## 7. Next step in progress: on-device debug player
A debug APK (`:app`) runs on the Fire TV: handshake -> profile -> genre lookup -> channel ->
`create_link` -> Media3 HLS (`OkHttpDataSource`, OkHttp follows redirects). Stream User-Agent is
configurable: Media3 default (`AndroidXMedia3/1.6.1`), MAG, or honest. A network interceptor
logs each stream HTTP exchange (status, content type, host changed) and an `AnalyticsListener`
logs state, first frame, formats and errors (HTTP code), all redacted. Portal URL and MAC are
pushed into app-private storage via `adb shell run-as` (debug only). Cleartext HTTP is enabled
only in the debug build's network security config.

Note: `:provider` was adjusted for Android 11 (replaced `InputStream.readNBytes` and
`URLEncoder.encode(String, Charset)`, which need API 33).

## 8. Questions for the reviewer
1. Ranked hypotheses for a 403 at the third hop (host C) when portal auth, profile, catalog and
   `create_link` all succeed, given the evidence above.
2. Is IP/address-family binding (IPv4 probe vs IPv6-capable Fire TV, portal behind Cloudflare)
   a plausible cause? How would `nginx_secure_link`-style tokens typically bind (IP, UA, expiry)?
3. Do Stalker/Ministra edges commonly require a watchdog or play-state call before serving a
   `create_link` stream? Which call, and with which parameters?
4. Is there anything in hop 1 (302 with an HLS body) that a correct client should use instead
   of the `Location` header?
5. Is the on-device Media3 test (Section 7) the most informative next step, or is there a
   single cheaper probe run (<= 8 requests) that would discriminate between the hypotheses?
6. Any issue in our request shapes (Section 3) versus standard MAG/Stalker client behavior
   that could matter for stream authorization (for example `forced_storage=0` vs `undefined`,
   `stb_type=MAG250`, missing serial/device IDs in `get_profile`)?
