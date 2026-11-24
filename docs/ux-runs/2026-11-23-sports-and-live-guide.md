# UX run 2026-11-23: Sports tab, NFL search, now/next on Live TV (debug build, Fire TV Stick 4K Plus)

Owner's request (from a photo of Live TV): a Sports section prefiltered to live and on-demand sports, an NFL search, and
channel lists that say what is on instead of only the channel name. StbEmu and TiviMate not running; the owner said go ahead.
No playback in this run except opening screens; kid-safe or sports titles only.

## What was built

- **Live TV:** each channel row has a second line, "Now: <programme> · 35 min left · Next 4:00 PM: <programme>", asked for
  as the row appears (after a quarter-second pause so rows flicked past are not requested) and kept ten minutes.
- **Sports tab** (between TV Guide and Movies; hidden on Kids profiles): rail with NFL, the live sports groups (every group
  whose title has a sport word; on this portal all 29 start with "SPORTS"), then the on-demand sports categories, which open
  in Movies or Shows. A live group lists its channels with now (times and progress) and next.
- **NFL:** reads the short guide (10 programmes, one request per channel, about 0.3 s) of the likely channels in stages:
  channels named NFL and the NFL group; the ESPN+, Paramount+, Peacock and DAZN groups; network channels (FOX, CBS, NBC, ABC,
  ESPN, TSN...) in the US, local and English groups and in the other sports groups; the rest of the sports groups only on
  "Check more channels". Games (two teams, "at"/"vs", or the prime-time broadcasts) are listed before NFL shows.

## Checked on the TV

| Check | Result |
|---|---|
| Live TV rows show now / time left / next | Pass |
| Sports rail picks up exactly the sports groups (compared with the full list of 72 groups) | Pass |
| NFL search, first stages | 489 channels in about 4 minutes; found tonight's game on a FOX affiliate and the Manningcast on DAZN, then NFL shows |
| Team channels with only "No details available" | Were listed as "Live now"; fixed (filler entries ignored everywhere) |
| Games added at the top while results showed | Pushed off-screen above the list; fixed (list stays at the top until the viewer moves into it) |
| Open an on-demand category | Opened the right titles but focused the first Movies category; fixed (focus on the opened category); Back returns to Sports |
| Sports crawl (120 presses) | Only the intended "back to the selected group / first visible channel" findings |

## Not verified

- ESPN's own listing of tonight's Monday Night Football was not found; whether its guide lists it was not confirmed. The
  prime-time titles ("Monday Night Football", "MNF"...) now count as games, but no ESPN entry matched on the TV.
- Whether Select right after scrolling Live TV waits behind the guide requests (the portal answers one at a time): the TV
  went to sleep before this was tried.
- "Check more channels" (stage 3) was not run.
