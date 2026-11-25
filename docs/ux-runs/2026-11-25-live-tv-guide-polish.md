# Live TV, TV Guide grid, programme search, player polish (2026-11-25)

Checked on the Fire TV with the debug build of the merged branches (kid-safe content only; nothing opened or played except
Caillou for under a minute; no history added). One screenshot per screen taken with the debug capture switch, then deleted.

## TV Guide (time grid)
- Grid with channel rows, 30-minute header, programme cells sized by duration; Down from a cell lands on the programme overlapping
  the same time (Global National 3:00–3:29 under Global News at 6). Programme search found "news" across cached guides: live
  now first, then by start ("Today 2:30 PM"). Not run: the favorites sweep, the Back-from-player focus return.

## Live TV
- Channel rows show a quality badge (4K) with the name cleaned, Sort: Number button, a side panel with a muted live preview of
  the focused channel (first frame 2.0 s), now/next on channels that have a guide. Logos: the boxes stay empty placeholders; the
  guessed logo address (<portal>/stalker_portal/misc/logos/320/<name>) may be wrong or the list may carry no logo: unverified.
- Player: bottom banner with number, name and badge (no guide on this channel, so nothing more); Left opens a channel list over
  the picture (Select watches, Back closes); `[` and `]` changed channel; `P` paused and the label read "Paused · -0:30 behind
  live · can go back 0:25".
- Not run: A to Z sort, the banner with guide data, Select in the mini list.

## Home and Saved
- A Saved searches row on Home (tiles open the Saved tab on that search): compiled, not looked at on the TV.

## Notes
- During this run the Shows rail listed only 14 (English) categories, so KIDS TV SHOW was not reachable; "Use my search
  defaults everywhere" may have been turned on. The VOD position marker and time-left text were therefore not seen on screen.
- Key changes: Left on the live player opens the channel list (it rewound before); Right shows the banner (it skipped); rewind
  and skip are on the media keys (30 s); `[` / `]` change channel; `P` plays and pauses in both players.
