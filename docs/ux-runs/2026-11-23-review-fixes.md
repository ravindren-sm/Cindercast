# Review fixes: check on the TV (2026-11-23, 21:08–21:20)

Fixes from an outside code review (commit b8a621f), checked with the build from b8a621f plus the grid padding below. StbEmu
not running. The stick was asleep, woken, and put back to sleep at the end. Kid-safe only (KIDS live group, KIDS TV SHOW).
Two screenshots taken of the poster grid and deleted; the log capture was scanned (no addresses, MACs or tokens) and deleted.

| Fix | Result |
|---|---|
| Screen stays on only while playing | **Pass** (live). The app window's `KEEP_SCREEN_ON` flag (from `dumpsys window`): absent while browsing, present while Caillou played, gone within 2 s of pausing, back on resume. The VOD player has the same change but was not played, to leave no history behind. |
| TMDB score badge | Not seen on the TV: no title in KIDS TV SHOW has only a TMDB score (all show ★ IMDb or KP). Covered by `RatingLabelsTest`. |
| Focused card drawn above its neighbours | **Pass.** Focused Tuttle Twins: its ring is whole beside The Simpsons and above the row below. |
| New: grid clipped the top row's ring | Found during this check: the poster grid had no inner padding, so it cut off the top ring of a focused card in the first row. Fixed with `contentPadding = Spacing.CardRow` (as card rows already have); checked on the TV, the ring is whole on every side. |
| Recovery cancelled on zap, shared stream client, one IMDb transaction, app-wide write scope, seek clamp | Built and unit-tested; not checked on the TV (no stream error or IMDb import was triggered). Caillou played normally on the shared client: first frame 1.7 s, 0 rebuffers, 0 errors. |

Cleanup done on request: the Mickey Mouse Clubhouse test entries (S02 E11 and S02 E12 in Continue watching, S02 E10 in
Recently watched) were removed with "Remove from Home". Ludwig S01 E04 in Continue watching was left as it is (not from a test).
