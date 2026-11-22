# Home, profiles and parental controls

Decided with the owner on 2026-11-21 after reading the AI Studio prototype's Home source and checking each row against
the real portal. Children live in the household, so this comes before more features.

## Decisions
- **Home layout:** the AI Studio rows and order for adults; a simpler big-tile Home for Kids profiles; a "doors and an
  invitation" Home on first run. No featured banner (heavy on this stick, and the portal has no backdrop images).
- **Kids and Live TV:** none at first. Kids see movies and shows only.
- **Storage:** Room (the spec's choice), with the KSP plugin. Logic stays in plain Kotlin that is unit-tested; Room is a
  thin layer for profiles, favorites, history and pinned categories.
- **Profile types:** Adult and Kids only. No Teen tier.

## What the AI Studio Home does
Rows in order: Continue watching (wide tiles, progress bar, time left), Recently watched (resume percent or "watched"),
Favorite channels, Recently added movies, Recently added shows (2:3 posters, New and lock badges), Live now in News.
Top bar with a clock; first focus on the first Continue watching tile; Down from the bar lands there. A card whose
rating is in {R, TV-MA, U/A 16+, NC-17} shows a lock badge and asks for the PIN. The prototype has no profiles.

## What the real portal changes
- "Recently added" across the whole catalog is poor: the newest items are mostly CAM-quality prints and series, and
  English titles are lost among many other languages. The "new" rows come from categories each profile pins.
- No verified guide data: channel tiles show number, name and a Live tag, not the current programme.
- Continue watching, Recently watched and Favorites are local, per profile.
- Ratings: the portal's IMDb value when present, else its Kinopoisk value, labelled. Age labels (G, PG, U/A 13+ and so
  on) exist on most titles; an unknown label is treated as restricted for Kids.
- Portal calls run one at a time (about 0.3 s each): local rows show at once, network rows fill in, results are cached
  for about 10 minutes.

## Profile model
Each profile: name, colour, Kids flag, its own favorites, history and pinned categories. A Kids profile also has a
rating ceiling (default G and PG) and an allow-list of kid-safe VOD categories (seeded from category names, confirmed
by an adult). A "Who's watching?" screen appears at launch when there is more than one profile.

One household PIN (four digits), stored only as a salted hash, guards: creating, editing or deleting profiles; leaving a
Kids profile; Settings; unlocking a restricted title; showing adult categories. Five wrong attempts lock the PIN for a
growing delay. Nothing about a profile or the PIN leaves the device.

## Rating policy (plain Kotlin, tested)
A label is normalised to an age level: G, U, ALL, 0+ is 0; PG, U/A 7+ is 7; PG-13, U/A 13+ is 13; R, U/A 16+, TV-MA,
18+, A, NC-17 is 16 or 18; blank or unrecognised is unknown. Kids allow a known level at or under the ceiling and
nothing unknown. Adults see everything except restricted levels, which show a lock and ask for the PIN. Categories
whose names mark adult content (ADULT, XXX, 18+) are hidden from every profile unless an adult enables them with the PIN.
Open item: survey the labels the real catalog uses so the table covers all of them.

## Home per profile
- Adult: top bar with profile chip and clock; Continue watching; Favorite channels; New in your categories (movies, shows,
  from pinned categories); Live row from one chosen genre. Lock badge per the policy.
- Kids: Keep watching; Kids movies; Kids shows. Bigger tiles, no rating badges, no Live TV, no Settings or profile
  switching without the PIN.
- First run: four big doors (Live TV, Movies, Shows, Search), an invitation to pin categories, one starter row.

## Build slices
1. Data layer: Room (profiles, favorites, history, pinned categories), PIN service, rating policy, Kids policy. Tested.
2. Profile picker and profile creation and editing behind the PIN; current profile held by the app shell.
3. Enforcement: filter categories and titles by profile; lock badge and PIN prompt; Kids without Live TV and Settings;
   adult categories hidden.
4. Capture history and favorites (the Continue watching feed).
5. Home screen (adult, kids, first run) and the pinned-categories chooser.
6. Settings for profiles and the PIN.
Each slice is checked on the Fire TV against the fake portal; the real portal only with kid-safe titles.
