# UX test plan (Fire TV, remote only)

Started 2026-11-22 after the owner found blocking problems that feature checks had missed (search results that could not be
reached, a filter that could not be undone, a skipped button, a clipped tile). Feature checks prove a control works; these
tests prove a person can get from where they are to what they want with the remote.

How: the `firetv-drive` skill (`.claude/skills/firetv-drive/`). Each journey is a cognitive walkthrough on the real TV with the
debug build, logged with `templates/journey.md`; each screen a journey visits also gets a D-pad `crawl`. Journey logs go in
`docs/ux-runs/` (no portal text beyond titles; kid-safe titles only on the family TV). Crawl output stays outside the repo.

Before any run that contacts the portal: StbEmu and TiviMate stopped (single active connection), and nobody watching the TV.

## Personas

| Persona | Knows | Starting state |
|---|---|---|
| **New adult** | Has used a TV remote and other streaming apps; first week with OpenStream; does not read hints. | Adult profile, the owner's saved search defaults (Language: English, Genre: Fantasy), Home. |
| **Returning adult** | Uses OpenStream daily. | Something in Continue watching; favourites set. |
| **Parent setting up** | Wants a Kids profile and a PIN. | One adult profile, no PIN. |
| **Child (supervised)** | Can press arrows and Select; cannot read long text. | Kids profile. |

## Journey charters

Each charter: goal, start, done when. Run every charter after a change to the screens it touches.

1. **Find and play a film in another language while filters are on.** New adult, Home, saved filters on. Search for a title that
   is not in English (for example a Tamil or Hindi animated title). Done when it plays. Must be possible without knowing about
   saved defaults: the filter can be set to Any from its own picker, and a search that the filters empty still shows the title.
2. **Reach a suggestion seen while typing.** New adult types letter by letter; when the wanted title appears behind the
   keyboard, closes the keyboard and selects it. Done when its page opens. Back must not remove what was seen.
3. **Search with no filters, several terms.** Two-letter, partial word, full title, a channel name, a show. Results must not
   reorder or jump while the viewer moves down through them.
4. **Resume.** Returning adult continues the first Continue-watching tile from Home. The focused tile must be fully visible.
5. **Next episode.** From a show page, play an episode to the end, or skip near the end, and accept the next one.
6. **Favourites.** Add a film and a channel; find both on Home and in Favorites; remove one.
7. **Profiles.** Parent: Manage profiles, Add a profile (reached with Down from the last profile), make it Kids, set a PIN,
   switch to it, try to leave (PIN asked), come back.
8. **Live TV.** Open a group, zap up and down, open the guide, back to Home.
9. **Settings.** Change one switch, back out; nothing else changes.
10. **Back from everywhere.** From every screen, repeated Back reaches Home and then leaves the app; no loops; Back never
    clears typed text or results.

## Per-screen crawl list

`ftv.py crawl <screen> --expect io.github.ravindren_sm.openstream --exclude nav-` on: Home (with Continue watching), Search
(idle, with suggestions, with results, with a picker open), Movies browse, Shows browse, Movie detail, Show detail, Live TV,
TV Guide, Favorites, Settings, Profiles (choose, manage, edit). Every `skipped`, `focus-lost` and `unreached` finding is a bug
until explained; `not-reversible` findings are judged one by one.

## Findings log

| Date | Journey / screen | Finding | Severity | Status |
|---|---|---|---|---|
| 2026-11-22 | Search, charter 1 | No way to clear a Language choice from its picker; with Language English + Genre Fantasy a Tamil title gave "No matches". | blocker | Fixed: "Any language / genre / category" rows; a search the filters empty shows the unfiltered matches with a note. |
| 2026-11-22 | Search, charter 1 | With a language filter only the first 6 of 29 matching categories were searched. | major | Fixed: one global search narrowed by each title's category (falls back to per-category requests on portals that do not send it). |
| 2026-11-22 | Search, charter 2 | Six suggestions while typing became three once the search finished, so the one seen behind the keyboard vanished. | blocker | Fixed: a fixed five rows. |
| 2026-11-22 | Search, charter 2 | Down from the box went to the filter chips before suggestions and results. | major | Fixed: filters moved above the box. |
| 2026-11-22 | Search, charter 3 | Results vanished and returned ("Searching…") when the connection blinked or the policy was rebuilt; suggestions reordered as the title index grew. | major | Fixed: results kept until new ones arrive; identical searches not re-run; suggestions frozen per query. |
| 2026-11-22 | Profiles, charter 7 | Down from the last profile row skipped Add a profile and landed on Done. | major | Fixed: Add is a full-width row; Down from it goes to the PIN button. |
| 2026-11-22 | Home, charter 4 | The focused Continue-watching tile was clipped at the left (card scale-up with no start padding). | major | Fixed in every card row (Home, Search, Favorites). |

Found on the TV on 2026-11-23 (run log: `docs/ux-runs/2026-11-22-search-and-profiles.md`): focus fell to the tab bar after
picking a suggestion; results the portal matched on hidden fields; Up from the box skipped the row above; Down into a card row
landed mid-row; exact titles ranked late; Down on the bottom row moved sideways to a taller card; Left/Right left rows; Up from
Profiles moved focus behind the layer; Back from Profiles and from Home's buttons lost the opener. All fixed and re-checked on
the TV. Left on Settings rows jumped to the tab bar (fixed). The three minor items left open (Up into the tab bar picking the wrong tab, twice; "Use default" under Language) were fixed on 2026-11-23.

Crawls of the remaining screens on 2026-11-23 (same run log, second round): Shows did not load when opened from Movies;
side moves between a category or group list and its grid or channel list landed level with the source instead of on the
selected category or first visible item (Movies, Shows, Live TV); Down from the tab bar landed on whatever was below the tab;
Up from the first guide row skipped the Group button; season row and show-page Play; a timing miss when focusing the first
search result. All fixed and checked on the TV. Not yet covered: the players, Profiles edit, journeys 4 to 10.

2026-11-23, Sports tab and now/next on Live TV (run log: `docs/ux-runs/2026-11-23-sports-and-live-guide.md`): guide filler
shown as programmes, NFL team channels listed with nothing on, new games pushed off-screen above the results, and on-demand
categories opened with focus on the wrong category; all fixed and checked on the TV. Open: ESPN's Monday Night Football not
found; Select latency right after scrolling Live TV not measured. Add a charter: **Find tonight's NFL game** (Sports, NFL, the
game is listed with its channel and start time; Select plays it).

