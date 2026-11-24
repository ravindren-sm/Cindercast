---
name: firetv-drive
description: Drive and test an app on an Amazon Fire TV (or Android TV) over ADB by reading where focus is after every remote key, instead of guessing from screenshots. Use when asked to try, test, walk through, reproduce a bug in, or check navigation of a TV app on a real device; for user-journey tests (a persona trying to reach a goal), focus-graph crawls that find skipped or unreachable elements, and Back-behaviour checks. Writes a journey log and crawl reports.
---

# Fire TV driving and journey testing

Treat the TV like a test rig with an API, not a picture. After each key, read the focused element
from the UI tree (`scripts/ftv.py`). Use screenshots only for things the tree cannot show: clipping,
overlap, contrast, images.

## Ground rules (check every time)

1. **Foreground first.** Run `ftv.py fg` before any key. If someone else's app is on screen (for
   example a person is watching TV), stop and ask; do not switch apps. `ftv.py key` refuses to send
   keys unless the foreground package is the one named with `--expect` (or `FTV_APP`).
2. **Ask before** changing any device setting, installing, uninstalling, clearing data, or
   force-stopping an app. Ask before any step that contacts a service the user has limited (for
   example an IPTV provider that allows one session per account).
3. **No secrets in files or commands.** `ftv.py` redacts URLs, host names, IPs, MACs and long tokens
   from on-screen text. Screenshots are not redacted: take them only when needed, never of setup
   or account screens, keep them outside the repo and delete them after use. Device addresses go in
   `FTV_SERIAL`, never in a file.
4. **Destructive controls.** Never select Delete, Remove, Reset, Sign out, Clear or purchase controls
   unless the user asked for that exact action.
5. **Keyboard trap (Fire OS).** `ftv.py text` types into a focused field but never presses Enter:
   Enter on the Fire OS keyboard activates the focused on-screen key and types a stray letter. To
   finish typing, use Back (closes the keyboard) or navigate to the keyboard's Next/Done key.
6. **Settle time.** Compose animations take a moment; the default 0.6 s per key is safe. If
   `uiautomator dump` reports "could not get idle state", the screen is animating or playing video:
   pause or wait, then retry.

## The tool

Run with `python3 .claude/skills/firetv-drive/scripts/ftv.py <command>` (set `FTV_APP` to the app
package to avoid repeating `--expect`).

| Command | What it does |
|---|---|
| `fg` | Foreground package/activity. |
| `focus` | The focused element: label and bounds. |
| `dump [--all]` | Focusable elements (`>>` = focused), with resource ids when exposed. |
| `key down*3 select --expect <pkg>` | Press keys; prints where focus landed after each. Names: up down left right select back home menu playpause play pause ff rew del enter. |
| `text "abc" --expect <pkg>` | Type into the focused field (no Enter). |
| `launch <pkg>` | Start the app from its TV launcher entry. |
| `shot <path>` | Screenshot (unredacted; delete after). OpenStream blocks capture: see docs/TESTING_ON_FIRE_TV.md 4.11 for the debug-only switch. |
| `crawl <name> --expect <pkg> [--exclude nav-]` | Map the current screen's focus graph with D-pad keys only, then report findings. |

### The crawler

`crawl` starts at the focused element, presses Up/Down/Left/Right from every element it can reach,
undoes each move with the opposite key, and writes `crawl.md` and `crawl.json` to `FTV_OUT` (default
`<tmp>/ftv-runs/`). It never presses Select or Back. Findings:

- **skipped**: a move jumped past a focusable element lying in between (for example Down from a
  full-width row to a button on the right, skipping the button directly below).
- **focus-lost**: after a press nothing is focused (the remote seems dead to the user).
- **not-reversible**: Down then Up does not return to where you were. Sometimes intended (a row
  remembering its last item), often a trap; judge each one.
- **unreached**: a focusable element was on screen but the crawl never got there.
- **left-app**: a direction key left the app (crawl stops).

Use `--exclude nav-` (or the id prefix of a top tab bar) to keep the crawl on the screen's body;
tab bars that switch screens on focus otherwise drag it elsewhere. Compose apps expose element ids
only when the root sets `testTagsAsResourceId = true` and elements carry `testTag`s; without them
the crawler identifies elements by their text, which is fine unless two elements share a label.

## Journey testing (cognitive walkthrough)

A journey is a persona with a goal, from a defined starting state, on the real device. Write it up
with `templates/journey.md`. For each step record the key, where focus landed, what the screen
showed, and answer the four walkthrough questions:

1. Would this user know what to do next?
2. Can they see the control that does it?
3. Would they connect that control with their goal?
4. After acting, does the screen tell them it worked?

A "no" to any question is a finding even if the step technically works. Also check, on every screen:

- **Escape:** Back always does something sensible and never loses typed text or results the user
  was looking at; repeated Back eventually leaves the app (no loops, no confirmation gates).
- **Reach:** everything visible can be focused with the D-pad, including content shown behind or
  beside an on-screen keyboard.
- **Stability:** content does not reorder or jump while the user is moving through it.
- **Visibility:** the focused element is fully on screen and clearly highlighted (not clipped at an
  edge); text readable from about 3 m.
- **Undo:** filters and choices can be turned off from where they are turned on ("Any" options).

Plan several journeys per release: first launch and setup, the most common task (find and play
something) with different search terms and filters, resume, favourites, profiles and PINs, settings,
and Back from every screen. Run a `crawl` on every screen a journey visits. Put the journey logs and
a summary of findings in the project's test-plan document; keep crawl output outside the repo unless
reviewed for private text.

## Gotchas learned on the device

The long version, with examples, is `docs/TESTING_ON_FIRE_TV.md` in this repository. The ones that cost the most:

- Read focus before every Select; never chain Select (or Menu, Back) after moves in one command.
- Wait about 9 s after `am start`, then check the foreground window; early keys are dropped or go to the launcher.
- Tabs and rails change on Select, not on focus. Lazy lists only contain what is on screen.
- In zsh, quote `'down*8'` and use a shell function, not a variable, for `adb -s <serial>`.
- In a player the tree is one node; read the redacted log for timings and states.

## References

- Amazon, Test criteria for Amazon Appstore apps: https://developer.amazon.com/docs/app-testing/test-criteria
- Amazon, Vega test-before-submission scenarios (Back to launcher, screensaver): https://developer.amazon.com/docs/vega/2.0/test-before-submission
- Android, TV navigation (D-pad focus search, Back rules): https://developer.android.com/training/tv/get-started/navigation
- Android TV automated testing with UiAutomator focus checks: https://alexilyenko.github.io/androidtv-automated-tests
