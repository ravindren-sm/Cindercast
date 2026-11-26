# OpenStream prototype: intake review (2026-11-21)

Source: https://github.com/ravindren-sm/Cindercast (public since 2026-11-21), commit `01bcc7990f67`
(2026-11-21). Read-only review of a tarball unpacked in an isolated scratch folder; nothing from
the repo was installed, built or run. The scratch copy was deleted afterwards.

## What it is
- A **web design prototype**: TypeScript, React 19, Vite, Tailwind. 42 files, about 440 KB, 15 screens,
  11 components. Its own metadata calls it a "10-Foot Fire TV IPTV Prototype ... design reference
  ... built for Jetpack Compose for TV handoff". All data is sample data (fictional titles); no
  network calls to any provider.
- It is not a runnable client: a browser page cannot send the portal's `mac` cookie, and the
  portal is plain HTTP. All real protocol work stays in `:provider`.

## Risk checks
| Check | Result |
|---|---|
| API keys, tokens | None found. `.env.example` lists only AI Studio placeholders (`GEMINI_API_KEY`, `APP_URL`) |
| External hosts | Google Fonts only (`fonts.googleapis.com`, `fonts.gstatic.com`); a placeholder portal URL on `example.net` |
| Provider-specific details | None real. One sample MAC in the setup screen (a made-up MAG-range value); category names such as "X | Y" follow real provider naming style |
| Gemini / server code | `@google/genai`, `express`, `dotenv` are template leftovers, unused in `src` |
| Names of other apps | No mention of StbEmu, TiviMate or other clients |
| Assets | No images or fonts bundled; art is generated placeholders |
| License | No LICENSE or README. `src/App.tsx` has an **Apache-2.0** SPDX header (AI Studio template); Settings screen text says "Apache License 2.0 / MIT" |

## What is worth carrying over (as a specification)
`src/screens/DesignNotesScreen.tsx` and `src/design/tokens.ts` are a ready handoff spec:
- Color tokens (dark 60/30/10 system), type scale (24 px minimum body), spacing on an 8 px grid,
  radii and badges, each with its Compose mapping.
- **Focus model**: exactly one focused element at all times; Back restores the saved focus ID;
  overlays trap focus; per-screen initial focus and D-pad flow.
- **Paging architecture** that matches what we measured: page size 14, prefetch 5-6, never hold the
  whole catalog, Paging 3 with `maxSize = 84`, RGB_565 posters, heap under about 192 MB on the
  32-bit stick.
- A **component to Compose for TV mapping table** and the route list (`/home`, `/live-tv`,
  `/live-tv/player`, `/tv-guide`, `/movies`, `/movies/:id`, `/shows`, `/shows/:id`, `/vod/player`,
  `/search`, `/favorites`, `/settings`, `/setup`).
- Data shapes (`src/types/tv.ts`) and a settings model (buffer size, preferred audio, hardware
  decoder, parental PIN, theme, clock format, ratings toggle).

## Where the prototype and reality differ (to resolve while porting)
1. **Show structure.** The prototype holds seasons and episodes inline. The portal is lazy and
   four-level: show, seasons, episodes, files (language and quality per file). The UI needs a
   file chooser and per-level loading states.
2. **Channel numbers.** The prototype numbers channels from 1; the portal's own numbers run
   differently (for example 2780 for a channel TiviMate shows as 1).
3. **TV guide.** Guide data is unverified: the channels carry guide fields, but TiviMate showed
   "No information" for the channels we looked at. The guide screen should wait for an EPG probe.
4. **Ratings overlay.** The prototype simulates IMDb and Rotten Tomatoes scores. A real version
   needs an OMDb or MDBList key, caching and a user-supplied key setting; plan as optional (M5).
5. **Counts.** The notes say 104,000 VOD items; we measured 104,000 (the portal's count changes).
6. **Stream info.** Real values come from Media3 (for example HEVC 1916x800 or H.264 1280x536),
   not fixed strings.

7. **Contrast claim.** The notes say secondary text has "above 8.5:1" contrast. Measured: 9.1 on the
   canvas, 8.3 on resting cards, 7.3 on selected cards, 6.2 on the focused row surface. All are above
   the 4.5:1 body-text minimum; the claim is accurate for the canvas only.

8. **Pixel grid.** The notes assume 1 px = 1 dp on a 1920x1080 canvas. On the Fire TV Stick 4K Plus the
   1080p interface runs at 320 dpi (1 dp = 2 px), so token values used as raw dp came out twice too
   big. The app normalizes with a design density (1 dp = 1 design pixel); see MILESTONES.md.

## Decision
Use OpenStream as the **UI source of truth** and port it screen by screen to Compose for TV on top
of `:provider`, as its own handoff notes intend. A WebView hybrid is not planned (it would need a
native bridge for every portal call, and carries performance and D-pad focus risk on this stick).
If a literal reuse is still wanted, run a one-hour WebView spike first (focus, scrolling 5,000
rows, memory) and decide on evidence.

## Licensing notes (not legal advice)
- Porting the design to new Kotlin code lets the app carry its own license (MIT is the stated
  leaning). Copying prototype files would require keeping their Apache-2.0 notice.
- Who owns AI Studio output, and whether the template header can be removed, is the owner's call
  and should be checked against the AI Studio terms before the first public release.
- Add a LICENSE file to the OpenStream repo too, so its status is explicit.
