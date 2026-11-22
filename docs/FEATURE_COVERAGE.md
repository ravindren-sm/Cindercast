# Feature coverage against the AI Studio spec

Source: "OpenStream TV, Complete Feature Specification" (prototype v1.4.0), supplied by the owner on 2026-11-21.
This file compares every item in that spec with what the native app does today, so nothing from the original is
dropped by accident. Update it whenever a slice lands.

Status words: **Done** (built and checked on the Fire TV), **Partial** (some of it is built, the gap is named),
**Not started**, **Reference only** (the prototype's own dev aids, not shipped by decision).
Milestone column: where the work is planned (M3 live, M4 VOD, M5 everything else).

## 1. Platform and 10-foot UI

| Spec item | Status | Notes |
|---|---|---|
| Fixed 1080p canvas scaled to the screen | Done | Design density maps 1 dp to 1 canvas pixel (the Fire TV runs 320 dpi). |
| Overscan margins 48 px / 27 px | Partial | Tokens exist; horizontal margin applied on all screens, vertical used loosely. M5 audit. |
| Exactly one focus; room-readable focus ring (3 px ring, 1.06 scale, raised surface) | Done | Single `OsFocusable` building block. The 160 ms ease-out timing is approximate (M5). |
| Back-stack focus restoration | Done | Verified on device for lists, grids, detail pages and zapping. |
| 150 ms cross-fade between screens | Not started | M5. |
| 10-foot type scale, tabular numerals for channel numbers and timecodes | Done | |
| Two themes: Broadcast Charcoal, High-Contrast OLED Black | Not started | Charcoal only. M5 with Settings. |
| Remote keys: arrows, Enter, Back, digits 0-9 | Done | Digits work in the live player (a Fire TV remote has no number keys; keyboards and other remotes do). |
| Remote keys: `]` `[` channel up/down | Partial | Up/Down and Channel Up/Down work; the bracket keys are not mapped yet (small). M3. |
| Remote key `P` play/pause | Partial | Select and the media play/pause keys work; `P` is not mapped (small). |
| Remote key `M` menu / 15-route directory overlay | Not started | A prototype dev aid; the Fire TV Menu key could open quick navigation. M5, optional. |

## 2. Screens (15 routes)

| # | Route | Status | What is built / what is missing | Milestone |
|---|---|---|---|---|
| 1 | Home | Not started | Placeholder. Needs Continue Watching, Recently Watched, Favorite Channels, Recently Added (movies, shows), Live Now in News, clock. Resume positions are already stored, so Continue Watching can read them. | M5 |
| 2 | Live TV browser | Partial | Done: genre rail, channel list, 14-per-page focus-triggered paging, real data. Missing: 3-column layout with preview and programme panel, sort (number or A-Z), channel logos, HD/4K badges, parental lock badge, programme title, skeleton rows, digit tuning inside the browser. | M3 |
| 3 | Live player | Partial | Done: full screen, overlay, buffering, errors with retry, channel up/down, digit entry, Back to the watched channel, Language and Subtitles drawer (Menu key). Missing: stream readout on screen (we only log it), info banner with programme and actions, mini channel list (Left). | M3 |
| 4 | TV Guide (EPG) | Not started | Waiting for an EPG probe (the portal may not supply guide data; the prototype already plans a "No information" cell). | M5 |
| 5 | Movies browser | Partial | Done: category sidebar (163 real categories), 5-column poster grid, 14-per-page paging, rating badge (IMDb, else Kinopoisk labelled), real data. Missing: minimum-rating filter, genre filter, sort bar (added, year, rating), year on cards, parental lock badge, skeleton cards, Rotten Tomatoes. | M4 / M5 |
| 6 | Movie detail | Partial | Done: poster, title, year, age, genres, synopsis, director, cast, rating, Play with initial focus, resume on play. Missing: hero backdrop, original title, runtime, Rotten Tomatoes and audience badges, explicit Resume button, Favorites button, PIN gate. | M4 / M5 |
| 7 | Shows browser | Partial | Same as Movies (same screen, show categories). Same gaps. | M4 / M5 |
| 8 | Show detail | Partial | Done: poster, title, year, age, rating (IMDb else Kinopoisk), genres, synopsis, Play (first episode of the season), season chips, paged episode list with real season and episode names, version chooser when an episode has several files (language and quality), per-episode resume with a "Resume m:ss" label, Back to the episode just watched. Missing: explicit Resume button on the show, Favorites, Rotten Tomatoes badges, next-episode prompt. | M4 |
| 9 | VOD player | Partial | Done: bar with position and length, pause, 10 s seek, saved position and resume, Language and Subtitles drawer (Menu key) with the portal's external subtitle files. Missing: accelerating seek (30 s, 60 s when held), Continue Watching feed, next-episode prompt. | M4 |
| 10 | Search | Not started | D-pad keyboard and grouped results (live, movies, shows). Needs a check of the portal's server-side search. | M5 |
| 11 | Favorites | Not started | Local store for channels, movies and shows, plus the screen and the add/remove buttons. | M5 |
| 12 | Settings | Not started | Account, Connection (masked URL and MAC, setup wizard, sync), Playback (buffer presets, preferred audio, surface), Parental Lock, Appearance (theme, channel numbers, clock, ratings toggle and cache reset), About. | M5 |
| 13 | First-run setup wizard | Done | Welcome, URL and MAC entry, live connection test, Save. (The catalog sync progress step is not needed: the catalog is paged.) | M2 |
| 14 | States and error gallery | Reference only | The states it shows are built into the screens as they land: stream error with retry and portal failure messages exist; skeleton placeholders, empty favorites or search, and an offline banner do not yet. | M5 |
| 15 | Design notes / handoff | Reference only | Used as the design spec for the port. | - |

## 3. Shared overlays, modals and services

| Spec item | Status | Notes |
|---|---|---|
| Language and Subtitles overlay (audio tracks, subtitle tracks, live caption preview, focus trap) | Partial | Done in both players: right-hand drawer, audio and subtitle columns, check on the active track, format and channel tags (1.0, 2.0, 5.1, AD, SAP, SRT, VTT, CC, DVB), summary line, Done, Back closes it, focus returns to the player; the portal's external subtitle files are side-loaded. Opens with the remote's Menu button (also the Captions key, or L on a keyboard). Missing: the on-canvas caption preview inside the drawer, a button in an info banner (there is no banner yet). Checked on the Fire TV against the fake portal only; the real portal's subtitle format and real live captions (CC, DVB) are unverified. |
| Parental PIN gate modal (verify, set PIN; unlocks R, TV-MA, U/A 16+ and locked channels) | Partial | The rules and storage are built and tested (age labels to levels, Kids and Adult policy, a hashed household PIN with lockout, profiles in Room); the modal, the profile picker and enforcement in the screens are the next slices (docs/HOME_AND_PROFILES.md). Original row: | The portal sends an age/MPAA field on most titles, so the gate has real input. M5. |
| Global Remote Menu (15-route directory, simulate session expired) | Reference only | Dev aid. |
| Ratings service: lazy fetch per visible page, cache with a 7 to 30 day TTL, toggle and cache reset | Partial | Today the portal's own rating fields are used (IMDb, else Kinopoisk). Real IMDb and Rotten Tomatoes need an outside service with a user-supplied key (OMDb or MDBList) and a local cache. Open decision. M5. |

## 4. Native stack from the spec

| Spec | Today | Gap |
|---|---|---|
| Kotlin, Compose for TV, Media3 ExoPlayer | Yes | |
| `androidx.tv.material3` components (TabRow, Card, ListItem, Dialog) | Partly | Custom focus and tab components, same behaviour. A swap is optional. |
| Paging 3 with `pageSize = 14`, `maxSize = 84` | Own paging, page size 14 | Our loaders keep every loaded page. Deep scrolling in a 100,000-title catalog grows memory; a sliding window is a hardening item. |
| Room SQLite for ratings cache, favorites, history | Not used | Resume positions use SharedPreferences. Favorites and watch history will need a database. |
| Real counts | 163 VOD categories, about 5,000 live channels, 74 live genres, 100,000 VOD items (the spec's "164" and "4,912" are the prototype's samples). | |

## Where this leaves us

Built and checked: setup, live TV (browse, play, zap), movies (browse, detail, play, seek, resume) and shows (browse, detail,
seasons, episodes, versions, resume). Movies and the shows browser were checked on the real portal; the show page and
episode playback were checked against the fake portal only.
Next in order: the rest of M4 (next-episode prompt, accelerating
seek, filters and sort), the remaining M3 live items (bracket keys, stream readout, info banner, mini list, sort,
badges), then M5 (Home, Search, Favorites, Settings, PIN gate, themes, transitions, EPG, ratings enrichment).
