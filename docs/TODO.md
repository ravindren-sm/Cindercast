# Open items

One list for everything that is known and not done. The detail behind each item is in `docs/MILESTONES.md`
(what was measured) and `docs/FEATURE_COVERAGE.md` (what the AI Studio spec still needs). Update this file when an
item lands or a new one is found. Order within a group is rough priority.

## Family safety (decided 2026-11-21, being built now)
- Profiles with a Kids flag, one household PIN, rating policy, adult categories hidden, Kids profile without Live TV.
  Design in `docs/HOME_AND_PROFILES.md`.
- Home screen per profile (adult, kids, first run), built after the profile layer.
- Real-portal check of the age labels the catalog really carries, so the rating policy covers every value seen.

## Observability and memory (found 2026-11-21: the app records no memory at all)
- Debug-only health sampler: every 30 s write one line to a file in the app's private storage with Java heap, native
  heap, graphics, total PSS, thread count, poster-cache bytes, loaded title and channel counts, player buffer and
  session uptime; warn in the log above a threshold (for example Java heap over 75% of the limit). Redacted like the
  rest: numbers only.
- A script to pull and plot that file (`scripts/pull_health_log.sh`), so a long run needs no hand-run `dumpsys`.
- Make the log survive: logcat rolls over in about ten minutes, so also write the redacted player and portal lines
  to the same private file in debug builds (the old debug player already did this).
- Measure memory during live TV, which has never been measured, over at least 30 minutes, and during long VOD
  sessions. Open question: the Java heap grew from about 11 MB to 40 MB across a 30-minute film. Candidates: the
  poster cache (capped at 24 MB, filled by browsing before playing), loaded title lists (descriptions are long),
  player buffers. Not yet attributed.
- Release the poster cache and trim loaded lists when the player opens; give the loaders a sliding window (the spec's
  Paging 3 `maxSize = 84` idea) so deep scrolling in a 100,000-title catalog cannot grow without limit.
- Crash and ANR capture on the device (local only), shown in Settings, so a family member can report a problem
  without adb. No remote telemetry: nothing leaves the device unless the owner decides otherwise later.

## Reliability
- 30-minute soak on a live channel (the VOD soak passed; live was skipped because content cannot be vetted).
- Whether the video decoder glitch seen once at about 1 min 50 s recurs, and on which streams.
- The one `IOException` line seen in the capture of the family-use run and never examined.
- Behaviour at the end of an episode (no next-episode prompt yet).

## Features still missing against the AI Studio spec
See `docs/FEATURE_COVERAGE.md`. Largest first: Home, Search, Favorites and Continue Watching, the next-episode prompt,
accelerating seek, filters and sort, the live info banner and mini channel list, the EPG (needs a probe), themes,
transitions, Settings.

## Release readiness
- Release builds cannot reach an HTTP-only provider (cleartext is allowed in debug only). Needs a release
  network-security decision.
- Public repo: publish the `native-app` branch once GitHub login works (`gh auth login`, then
  `git push -u origin main:native-app`). History was scrubbed first; see `docs/MILESTONES.md`.
- Rotten Tomatoes and real IMDb ratings need an outside service and a user-supplied key; the portal's own rating
  (IMDb when present, otherwise Kinopoisk, labelled) is what is shown today.
- Docs review before anything is public beyond the scrubbed branch.

## Environment notes
- The owner's TV is the family TV: real runs use kid-safe titles only (memory file `kids-in-the-house-clean-content`).
- The macOS firewall blocks non-Apple Python listeners: run the fake portal with `/usr/bin/python3`.
