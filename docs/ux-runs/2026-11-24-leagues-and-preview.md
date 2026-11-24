# Leagues, live preview, logo and Profiles exit (2026-11-24)

Checked on the Fire TV with the debug build from 827ee41 plus the commit after it; StbEmu not running; the stick free (the
owner said so). One screenshot of the Sports screen (debug capture switch, removed afterwards) was viewed and deleted.

## Games per league
- One search of the channel guides now finds games and shows for NFL, College football, Soccer, Cricket and Formula 1; the rail
  shows a count per league. First pass on the real portal: 826 guides read in about 5 minutes (11:13–11:18), saved to
  `sports-listings.json` (the old `nfl-listings.json` is no longer used).
- Found at 11:18 on a Tuesday morning: NFL 1 game and 3 shows, College football 2 games and 1 show, Soccer 6 shows, no games
  (Eredivisie, FA Women's Super League, MLS, FIFA U-17 World Cup are listed without team names), Cricket and Formula 1 nothing.
- Cricket's group ("SPORTS | CRICKET", 36 "PPV CRICKET n" channels) has no programme guide at all, as with the soccer slots, so
  the search cannot find cricket there; only the preview shows what a slot carries.
- Not yet judged: titles with no team names ("Eredivisie Soccer", "MLS Soccer") are listed as shows, not games.

## Live preview inset (Sports channel lists and game rows)
- Rests on a channel for 0.9 s, then plays it muted in a 16:9 box beside the list (first frame 2.2 s after the stream request).
- Worked on "PPV CRICKET 1 4K": it was carrying a European soccer match, which shows that the numbered slots carry whatever
  event the provider assigns, whatever their name says.
- Stepping through many channels was not tried in bulk (one stream request each); no refusals in the short test.

## Logo and Profiles
- The logo shows in the top bar (right, before the clock) and above "Who's watching?"; the Fire TV launcher entry now has the
  icon and banner on the activity itself as well as the application (Fire OS reads the activity's). Not checked on the Fire TV
  home row (the launcher was not opened).
- Profiles manager: a "‹ Back" button at the top (the screen has no tab bar), plus the Done button at the bottom; not
  checked on the TV because opening it needs the household PIN.
