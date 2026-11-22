# Open items

One list for everything that is known and not done. The detail behind each item is in `docs/MILESTONES.md`
(what was measured) and `docs/FEATURE_COVERAGE.md` (what the AI Studio spec still needs). Update this file when an
item lands or a new one is found. Order within a group is rough priority.

## Priority (owner, 2026-11-21)
The owner is focused on the UI and interaction experience for adults. The children are too young to use the app
without supervision, so profile enforcement is not needed before the first release. Build order follows from that:
adult Home rows, focus and navigation polish, Search, Favorites, then the rest of the spec. The kid-safe rule for real
runs on the family TV still stands (see Environment notes).

## Family safety (built 2026-11-22; see docs/HOME_AND_PROFILES.md)
- Done 2026-11-22: real-portal Kids check and the age-label survey (see docs/HOME_AND_PROFILES.md). Still to check: that ADULTS-named categories are gone for the Family profile.
- Kids see little where a category's titles carry no age label (71 percent of the catalogue has none): consider letting an adult mark a category as trusted (its unlabelled titles pass the word filter only).
- Editing the Kids word list from Settings; PIN prompt for 16+ titles on adult profiles (optional); a PIN option for adult profiles at launch.
- Kids profile lists use the portal's pages only (no on-device sort); an age and word filter on the on-device copy would restore sorting.

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
- Online tags (themoviedb.org) are built and running (docs/ONLINE_TAGS.md). OMDb was dropped. IMDb ratings come from IMDb's free personal-use file (Settings, Download; credit line shown); waiting on IMDb's licensing reply (email sent 2026-11-22) before any public release shows IMDb scores. No Rotten Tomatoes source.
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

## Sort, filter and content-filter follow-ups
- Check on the TV: the sort picker, the rating chip, the Settings rows, opening a title from the device copy; then on the real portal once the index has ratings.
- Filter by genre, year range and language inside a category; remember the chosen sort per category.
- The content filter is rating-based only. Age labels (G, PG, R) are in the data and could back a proper parental filter (deferred with profiles).

## Title index follow-ups
- The index is building on the real portal (started 2026-11-21 21:17; about 1.75 hours). When it finishes record the final title count, size and any errors; check suggestions against real titles; tune the pause between requests (the portal did not complain at 0.3 s).
- Use the index for instant results (not only suggestions) and offline-ish search while the portal is slow.
- Show the category of a title in the version picker, since real names may not differ.

## Buffering and memory (reported 2026-11-21 while watching a live channel)
- At the time: stream segments arrived in 70 to 180 ms each (about 6 s of video per segment), Wi-Fi signal -52 dBm, CPU about 70 percent idle,
  so the feed and the network were not the limit; but the stick had about 40 MB of RAM free and 95 percent of its 800 MB swap in use,
  and this app held about 170 MB (Java heap 63 MB, up from 12 MB at the start of the title crawl). Memory pressure is the suspect.
- Done (2026-11-21): poster cache capped at 10 MB and dropped when a player opens; the other tab's loaded titles are dropped when a
  player opens; player buffers cut to 10 to 25 s and 14 MB (was about 50 s and tens of MB); the title index already pauses while a
  player is open; a debug health sampler writes memory numbers every 30 s to files/health-log.txt and logcat tag OpenStream.health.
- Checked on the TV against the fake portal: live playback with the smaller buffers (first frame in 1.3 s, no errors) and the sampler output
  (Java heap 4 to 12 MB, native 60 MB while playing). Not yet measured on the real portal: read health-log.txt after a long real watch
  (`adb shell run-as io.github.ravindren_sm.openstream cat files/health-log.txt`) and see whether the decoder errors stop.
- Seen once on the fake portal: Play on a movie ended in "This title is not available" with no player error (create_link answered 200).
  Not yet explained (fake data or a real fault); recheck, and try a show episode.
- Still to do: heap dump to find what grows the Java heap in long sessions; a sliding window for the loaded lists (Paging `maxSize`).

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
