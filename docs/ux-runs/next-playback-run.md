# Next run: playback journeys (prepared 2026-11-23, not yet run)

Needs the TV on screen for about 20 minutes with nobody watching; StbEmu and TiviMate stopped (single connection). Kid-safe
titles only (G/PG/U), except charter 11, which plays the NFL game the search found. Record each with `templates/journey.md`.
Measure with the `firetv-drive` focus readback; a screenshot only to check the player overlay is not clipped, then delete it.

| # | Charter | Start | Steps | Pass when |
|---|---|---|---|---|
| 4 | Resume | Home | Select the first Continue watching tile; wait 10 s; Back | Plays from the saved position (not 0:00); Back returns to the same tile; the tile shows the new time left |
| 5 | Next episode | A kids show page (for example Bluey) | Play an episode; seek near the end; let it finish | "Next episode" offered and starts the next one; Back returns to the show page on that episode |
| 6 | Favourites | Live TV, KIDS | Menu on a channel; open Favorites; Select it; Back; Menu again to remove | Appears on Home and in Favorites; plays; removed cleanly |
| 8 | Live TV | Live TV, KIDS | Select a channel; Up/Down to zap 3 times; Back | Each zap starts within a few seconds; the info line names the channel; Back lands on the channel last watched |
| 8b | Select after scrolling | Live TV, KIDS | Down 10 quickly, then Select at once | Time from Select to picture; compare with Select after a 3 s pause (guide lookups queue on the one portal connection) |
| 10 | Back from the players | Each player | Back repeatedly | Player closes on the first Back; no loops; Home then leaves the app |
| 11 | Find tonight's NFL game | Sports > NFL | Open the first game under Games; Back | The listing was there without waiting (background search); the right channel plays; Back returns to the listing |
| 12 | Background search stays out of the way | Live TV playing for 2 minutes | Watch the log (`adb logcat`, guide requests) | No guide requests while a player is open; requests resume within a minute of leaving it; none when another app is in front |

Also check once: open Sports right after a restart: the saved NFL listings show at once, with "Updated <time>".
