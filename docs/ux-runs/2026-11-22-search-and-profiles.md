# UX run 2026-11-22/23: Search, Home and Profiles (debug build, Fire TV Stick 4K Plus)

Method: `firetv-drive` skill, focus read back after every key; screenshots only for clipping, deleted after use.
StbEmu and TiviMate force-stopped with the owner's OK before the run (single connection). No playback (late at night): journeys
end on the Play button. Kid-safe search terms only.

## Journey 1: Find the Tamil Ramayana film with the saved filters on

- **Persona:** new adult, remote only, does not read hints.
- **Starting state:** Family (Adult) profile, saved defaults Language English, Genre Fantasy, Rating 7+; Home.
- **Goal:** the film's page with Play focused.
- **Result:** PASS (after fixes in this run)

| # | Key(s) | Focus landed on | Screen shows | Knows what to do? | Sees control? | Connects to goal? | Feedback clear? |
|---|---|---|---|---|---|---|---|
| 1 | down, right×3, select | Search box | Filters (English, Fantasy, 7+), box, recent searches | Y | Y | Y | Y |
| 2 | select, type r-a-m-a-y | Keyboard | Suggestions update behind the keyboard | Y | Y | Y | Y |
| 3 | back | Box ("ramay") | Keyboard closed; text, 5 suggestions and results kept | Y | Y | Y | Y |
| 4 | down | Suggestion "Ramayana: Rise of a Legend - CAM (Tamil)" | | Y | Y | Y | Y |
| 5 | select | First result card (the film) | "Nothing matched with your filters … These match without them" and the film | Y | Y | Y | Y |
| 6 | select | Play | Film page | Y | Y | Y | Y |
| 7 | back | The same result card | Results unchanged | Y | Y | Y | Y |
| 8 | back | Search button on Home (before the fix: the Search tab) | Home | Y | Y | Y | Y |

## Journey 2: Undo a filter where it was set, then search

- **Starting state:** Search with the saved filters. **Goal:** Language off, then results for "paddington". **Result:** PASS

| # | Key(s) | Focus landed on | Screen shows | Knows? | Sees? | Connects? | Feedback? |
|---|---|---|---|---|---|---|---|
| 1 | up (from the box) | Before the fix: "English subtitles" (far right); after: the first button of the row above | | N→Y | Y | Y | Y |
| 2 | left×4, select | Language list: Done, **Any language**, … ✓ English | | Y | Y | Y | Y |
| 3 | down, select | Language button (now plain "Language") | List closed | Y | Y | Y | Y |
| 4 | type "paddington", back | Box | Fantasy + 7+ still on: unfiltered matches with the note | Y | Y | Y | Y |

## Journey 3: Several searches without filters (frozen, bluey)

- **Result:** PASS WITH ISSUES (fixed in this run)

| # | Key(s) | Focus landed on | Note |
|---|---|---|---|
| 1 | up, select (Clear filters) | Language | A "Use default (…)" button appears directly below Language, so Down then Select puts the filters back. Kept (it is labelled), noted as minor. |
| 2 | type "frozen", back, down×6 | Before the fix: "Frozen Kiss", the 3rd card; after: the first card, "Frozen" | Ranking put "Frozen Kiss" and "Frozen Sasquatch" before "Frozen". |
| 3 | right, down, up, left×3 | Shows row first card, back to Movies first card, left edge holds | |
| 4 | type "bluey", back, down×8 | Suggestions, the Bluey channel, Movies, Shows | Down on the last row moved sideways to a taller card ("Found in: Cast"). |

## Findings (all from this run)

| # | Severity | What happened | Expected | Screen and step | Status |
|---|---|---|---|---|---|
| 1 | blocker | No "Any language"; with English + Fantasy the Tamil film gave "No matches" | Undo in the picker; show that filters hid it | Search, J1/J2 | Fixed |
| 2 | blocker | Suggestion list shrank 6→3 when a search finished, hiding what was seen | Fixed number of rows | Search, J1 | Fixed |
| 3 | major | After picking a suggestion, focus fell to the Home tab | Focus on the first result | Search, J1 step 5 | Fixed |
| 4 | major | Results whose name lacked the text (portal matched hidden fields) | Only explainable results; label cast/description matches | Search, J1 step 3 | Fixed |
| 5 | major | Up from the search box skipped the row above | First button of the row above | Search, J2 step 1 | Fixed |
| 6 | major | Down into a card row landed on a middle card | First card | Search, J3 step 2 (also Home, Favorites) | Fixed |
| 7 | minor | Exact title ranked after longer names | Exact first | Search, J3 step 2 | Fixed |
| 8 | major | Down on the bottom row moved sideways to a taller card | Stay | Search, J3 step 4 | Fixed (equal card heights) |
| 9 | major | Left from a row's first item jumped to the tab bar; Right from the last item jumped to the next row | Stay in the row | Home crawl | Fixed |
| 10 | minor | Back from a Home button's screen focused the tab, not the button | The button used | Home, J1 step 8 | Fixed |
| 11 | blocker | Profiles: Down from Family skipped Add a profile | Add a profile | Profiles crawl | Fixed |
| 12 | blocker | Profiles: Up from Family moved focus to the hidden tab bar behind the layer (remote seemed dead) | Stay in the layer | Profiles crawl | Fixed for every overlay (pickers, PIN, menus) |
| 13 | major | Back from Profiles focused the Home tab | The Manage row | Settings | Fixed |
| 14 | major | Continue-watching tile clipped at the left when focused | Fully visible | Home | Fixed (screenshot checked) |
| 15 | minor | Up from the first Home tile goes to the Live TV tab, not Home | Home tab | Home | Open |
| 16 | minor | "Use default" sits right under Language after Clear filters | Less easy to hit by accident | Search | Open |

## Crawls run

- Home: 3 findings, all "Down returns to the first button" (accepted).
- Profiles (manage): before 4 findings (focus lost, 3 unreached); after 1 accepted (Down from Add goes to the PIN button).
- Settings: see the run summary in docs/UX_TEST_PLAN.md.
- Not yet crawled: Movies, Shows, detail pages, Live TV, TV Guide, Favorites, the players.
