# Open items

One list for everything that is known and not done. The detail behind each item is in `docs/MILESTONES.md`
(what was measured) and `docs/FEATURE_COVERAGE.md` (what the AI Studio spec still needs). Update this file when an
item lands or a new one is found. Order within a group is rough priority.

## Priority (owner, 2026-11-21)
The owner is focused on the UI and interaction experience for adults. The children are too young to use the app
without supervision, so profile enforcement is not needed before the first release. Build order follows from that:
adult Home rows, focus and navigation polish, Search, Favorites, then the rest of the spec. The kid-safe rule for real
runs on the family TV still stands (see Environment notes).

## Family safety (deferred: not needed before release)
- Profiles with a Kids flag, one household PIN, rating policy, adult categories hidden, Kids profile without Live TV.
  The data layer is built and tested (slice 1); the picker, enforcement and Settings slices wait. Design in
  `docs/HOME_AND_PROFILES.md`.
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

## Playback controls (asked by the owner 2026-11-21: movies are rarely finished in one sitting)
- Verified on the Fire TV against the fake portal: pause (Select or play/pause key), Left and Right skip 10 s, the
  remote's rewind and fast-forward keys skip 30 s (also while paused), resume on the next play. Pause and the 60 s
  forward skip were also verified on the real portal; rewind and the media keys only on the fake one.
- Live TV pause and rewind are built (2026-11-21). Measure how much history the real channels keep (the debug log prints the
  window length) and decide whether to show a rewind limit in the label.
- Accelerating seek is done (2026-11-21, steps grow while a key is held). A visible position scrubber is not.
- Continue Watching on Home is built (2026-11-21). Mark watched, remove and focus after Back are done too.
- Next-episode prompt: done (2026-11-21).

## Search follow-ups
- Recent searches, result counts per group, a "more results" page beyond the first three, and grouping repeated titles
  (same name in several versions).
- Measure memory over time after the full channel list is read (Java heap peaked at 75 MB right after the first read).
- Results from the server can include titles that do not contain the words typed (it matches descriptions too): decide
  whether to show those under a separate heading.

## Guide and programme search (the guide exists per channel, see docs/MILESTONES.md)
- TV Guide screen: now and next for the channels on screen, loaded one channel at a time; a grid for favorites.
- Programme search: sweep favorites and recently watched channels (not all 5,100) and search those programmes, showing title, channel and time. Check how far ahead a larger `size` reaches, and whether the catch-up flag (`mark_archive`) allows replaying.
- Reminders for a future programme are out of scope until the guide screen exists.

## Ratings and filters follow-ups
- Rotten Tomatoes (and real IMDb scores, since the portal's IMDb value is usually 0): an optional outside ratings service
  with a key entered by the owner, looked up per result and cached on the device. Needs the owner's decision: it sends title
  names to that service.
- Check on the real portal that the category-restricted search works and see which language and genre words the real
  categories and titles use; add more language names if needed.
- Apply the same filters to the Movies and Shows browse screens, and use the title index for instant filtered results.

- English subtitles: the list has no such field (checked 2026-11-21), so only the wording check or an exact per-title check
  through the play-link request (one request each, cached) remains.
- Use the real list fields: keep `category_id` on each title so category filters work on one request; read `country` for an
  origin or language filter; use `hd`, `high_quality` and `low_quality` to rank versions; use `o_name` when merging and matching.

## Title index follow-ups
- Run the index on the real portal once (the user starts it from the Search page) and record the real size, time and any errors; tune the pause between requests.
- Use the index for instant results (not only suggestions) and offline-ish search while the portal is slow.
- Show the category of a title in the version picker, since real names may not differ.

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
- Public repo: the scrubbed history is on branch `native-app` of the OpenStream repo (pushed 2026-11-21; `main` there is
  untouched). Decide when to open a PR or make it the default branch; push later commits with `git push`.
- Rotten Tomatoes and real IMDb ratings need an outside service and a user-supplied key; the portal's own rating
  (IMDb when present, otherwise Kinopoisk, labelled) is what is shown today.
- Docs review before anything is public beyond the scrubbed branch.

## Environment notes
- The owner's TV is the family TV: real runs use kid-safe titles only (memory file `kids-in-the-house-clean-content`).
- The macOS firewall blocks non-Apple Python listeners: run the fake portal with `/usr/bin/python3`.
