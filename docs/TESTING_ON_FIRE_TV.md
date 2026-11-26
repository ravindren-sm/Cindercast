# Testing on a Fire TV: driving the stick from a computer

How Cindercast is installed, driven and measured on a real Fire TV over ADB, and the gotchas learned doing it (2026-11-20
to 2026-11-23, Fire TV Stick 4K Plus, Fire OS 8 on Android 11). It is written for people working on the app and for coding
agents that test it. The tool is `.claude/skills/firetv-drive/scripts/ftv.py`; its short guide is the skill's
[SKILL.md](../.claude/skills/firetv-drive/SKILL.md). The journeys and crawl plan are in [UX_TEST_PLAN.md](UX_TEST_PLAN.md);
run logs are in [ux-runs/](ux-runs/).

The idea: treat the TV as a test rig with an API. After every remote key, read **where focus landed** from the UI tree
instead of guessing from a screenshot. Screenshots are only for what the tree cannot show (clipping, overlap, images).

## 1. Setup

1. On the Fire TV: Settings > My Fire TV > Developer options > **ADB debugging** on. The first connection shows an
   "Allow USB debugging?" prompt on the TV; a person must accept it with the remote.
2. Connect over the network and keep the address out of files (use an environment variable):

   ```bash
   adb connect <FIRETV_IP>:5555
   export FTV_SERIAL=<FIRETV_IP>:5555
   export FTV_APP=io.github.ravindren_sm.openstream   # the only package ftv.py will send keys to
   ```

3. Build and install a debug build. Debug builds are needed for `run-as` (reading the app's private files) and for the
   debug-only redacted traffic log.

   ```bash
   ./gradlew :app:assembleDebug
   adb -s "$FTV_SERIAL" install -r -d app/build/outputs/apk/debug/app-debug.apk
   ```

4. Start the app by its activity. (`ftv.py launch`, which uses `monkey`, did not start it reliably on Fire OS.)

   ```bash
   adb -s "$FTV_SERIAL" shell am start -n io.github.ravindren_sm.openstream/.MainActivity
   ```

Element ids: the app's root sets `testTagsAsResourceId = true` and focusable elements carry test tags (`nav-sports`,
`genre-8`, `channel-3`, `episode-1`, `home-cw-0`, `search-filter-clear`...). `ftv.py dump` prints them after `#`; that is
how scripts and crawls know where they are without reading text.

## 2. The tool

| Command | Use |
|---|---|
| `ftv.py fg` | Which app is in front. **Run first**, every time. `asleep/None` means the screen is off. |
| `ftv.py focus` | The focused element: label and bounds. |
| `ftv.py dump [--all]` | Focusable elements (`>>` marks focus); `--all` adds text nodes such as status lines. |
| `ftv.py key right right select` | Press keys; prints where focus landed after each. Refuses if `FTV_APP` is not in front. |
| `ftv.py text "abc"` | Type into the focused field (never presses Enter, see 4.6). |
| `ftv.py crawl <name> --exclude nav-` | Map a screen's focus graph with arrows only (never Select or Back) and report skipped, unreachable, irreversible moves. |
| `ftv.py shot <path>` | Screenshot (not redacted: delete after use). Cindercast blocks screen capture; see 4.11. |

Text read from the screen is redacted before it is printed (URLs, hosts, IPs, MACs, long tokens), so portal details never
reach a log or a report.

## 3. Measuring with the log

The debug build logs to two tags, both redacted (hosts appear as `<HOST>`):

- `OpenStream.portal`: every portal request with its action and time (`action=create_link -> HTTP 200 in 252 ms`,
  `action=get_short_epg`, `type=watchdog`).
- `OpenStream.player`: player states and milestones (`playing: true`, `first frame after 733 ms`, `load error: HTTP 404`,
  `player state: ENDED`, `window: ... lengthSec=1440`).

```bash
adb -s "$FTV_SERIAL" logcat -c
adb -s "$FTV_SERIAL" logcat -v time -s OpenStream.portal OpenStream.player > <scratch>/log.txt &
```

Timings come from timestamps, not from the tool's own clock: Select at T0 (print `date +%T.%N` just before the key),
`create_link` answered at T1, `playing: true` at T2. Examples measured this way: channel start 2.3–4 s; a VOD first frame
about 1.2 s; Select right after fast scrolling waited behind one guide request (about 0.3 s).

Before deleting a capture, scan it (no IPs, MACs, `token`, `Authorization`, `Cookie`), then delete it.

## 4. Gotchas

### 4.1 The TV, the screensaver and sleep
- **Check what is on screen before any key.** Someone may be watching. If another app is in front, stop and ask; never
  switch apps for them. `ftv.py key` refuses unless the expected package is in front.
- **The screensaver** (`com.amazon.ftv.screensaver`) starts after about 2.5 minutes without input and swallows the next
  key. Wake it with `ftv.py key wakeup --expect any` (or `input keyevent KEYCODE_WAKEUP`), then check `fg` again.
- **Asleep:** `fg` says `asleep/None` and `dumpsys power` shows `mWakefulness=Asleep`. Installing a build does **not**
  wake the stick, so a build can be put on the device without disturbing anyone. Put it back with `KEYCODE_SLEEP` at the
  end if it was asleep when you started.
- The app releases its portal session when it leaves the screen (screensaver, sleep, another app). Long background work
  (the title index) keeps the screen on with a window flag while it runs; that is the app's choice, not a device setting.
- Device settings (stay-awake, developer options beyond ADB) are not changed without the owner's OK.

### 4.2 Launching and installing
- **Wait after a launch.** A cold start takes about 5 s, and keys sent before the window has focus are dropped. Wait about
  9 s, then check `dumpsys window | grep mCurrentFocus`. One early run sent keys to the Fire TV home screen because the
  launch raced the install.
- `install -r` kills the running app; relaunch afterwards. The process id changes, but a `logcat -s <tags>` capture keeps
  following the tags.
- `am force-stop <package>` is the clean way to end a run (the app then holds no portal session). Only force-stop other
  apps with the owner's OK.

### 4.3 Single connection
Most providers allow one session per MAC address. Before any live portal run, check that no other IPTV app using the same
subscription is running (`adb shell pidof <package>`), and stop the app at the end.

### 4.4 Select is the dangerous key
- **Read focus before every Select. Never chain a Select after moves in one command.** Two real incidents: a sequence of
  moves ended one row off and Select started a channel; another ended on **Clear filters** and wiped the saved search
  filters (restored at once with "Use default"). Arrows are safe to batch; Select, Menu and Back are not.
- Never select Delete, Remove, Clear, Reset, Sign out or purchase controls unless that is the task.
- In content lists, choose kid-safe items when the TV is in a family room; focus may pass over adult category names, but
  never open them.

### 4.5 Navigation behaviour that surprises scripts
- **Tabs open on Select, not on focus.** Focus on "Sports" plus Down goes into the page that is already open (for example
  Home), not into Sports.
- **Rails (channel groups, categories): focus is not choice.** Moving through the rail only highlights; Select loads the
  group. Down*8 from "All" lands on the ninth group but the list on the right still shows "All".
- **Lazy lists only contain what is on screen.** An element scrolled out of view is not in `dump`; scroll, then dump. Ids
  are indexes in the list (`genre-8`), not positions on screen.
- **Remembered focus.** Left, Right, Down from the tab bar and Back return to a remembered element (the selected group,
  the first visible card, the episode just watched). A crawl reports these as "not-reversible"; judge each one, most are
  intended.
- **Zapping:** in the live player Up is the next channel and Down the previous; at the first channel Down shows "First
  channel" and does nothing else.
- **Back on Home leaves the app.** Further keys would go to the Fire TV launcher (`ftv.py` refuses them).

### 4.6 Keyboard
`ftv.py text` types into a focused field but never presses Enter: Enter on the Fire OS keyboard presses the focused
on-screen key and types a stray letter. Close the keyboard with Back, or move to its Next/Done key.

### 4.7 Players and the UI tree
- `uiautomator dump` often fails while video plays or animates ("could not get idle state"); wait and retry.
- Inside a player the tree is one full-screen node. Its label carries the overlay text (channel, position, "-0:30 behind
  live", "Buffering…") only while the overlay is visible; press Up (shows the overlay without zapping in the VOD player) or
  read the log. A screenshot is the fallback for the position bar; delete it afterwards.
- **Seeking a VOD title to its end:** the media fast-forward key jumps 30 s; send many in one command:
  `adb shell input keyevent 90 90 90 ...` (45 presses ≈ 22.5 min). Then wait for `player state: ENDED` in the log.

### 4.8 Speed
- `ftv.py key` waits about 0.6 s per key and reads the tree after each, so eight presses can take 30 s or more. For a
  timing test (fast scrolling then Select), send raw keys in one call, which arrive as fast as a held button:
  `adb shell 'input keyevent 20 20 20 20 20 20 20 20 20 20 19 19 19 19 19 19; input keyevent 23'`. Read focus afterwards.
- Key codes: 19 up, 20 down, 21 left, 22 right, 23 select, 4 back, 82 menu, 85 play/pause, 90 fast-forward, 89 rewind,
  224 wake, 223 sleep.

### 4.9 Shell gotchas (zsh on macOS)
- `A="adb -s $FTV_SERIAL"; $A shell ...` fails in zsh ("command not found: adb -s ..."): zsh does not split words in
  variables. Use a function: `a(){ adb -s "$FTV_SERIAL" "$@"; }`.
- `ftv.py key down*8` fails in zsh ("no matches found"): `*` is a glob. Quote it: `ftv.py key 'down*8'`.
- `logcat` prints `--------- beginning of crash` as a buffer header even when nothing crashed. Check
  `adb logcat -b crash -d` and the app's `pidof` before concluding anything.
- A long `adb` command with no timeout can hang the session; give device commands a timeout and run long waits as a loop
  on a condition (`until grep -q "state: ENDED" log.txt; do sleep 2; done`) rather than a fixed sleep.

### 4.10 Inspecting the app's own files (debug builds)
`adb shell run-as io.github.ravindren_sm.openstream ls -l files` lists the app's private files, for example
`nfl-listings.json` (the saved NFL search, with its write time) or `debug-log.txt` (the debug player's log, see
`scripts/pull_debug_log.sh`). Do not copy these off the device unless needed; some hold channel play commands.

### 4.11 Screenshots of Cindercast are blocked
The app marks its window secure (`CaptureGuard`, FLAG_SECURE): `screencap` exits with an error and writes an empty file, and
screen recording and mirroring show black. UI-tree reads (`ftv.py focus`, `dump`, `crawl`) are not affected. When a screenshot is
really needed, on a debug build only:

```bash
adb -s "$FTV_SERIAL" shell run-as io.github.ravindren_sm.openstream touch files/allow-screen-capture
adb -s "$FTV_SERIAL" shell am force-stop io.github.ravindren_sm.openstream   # the switch is read when the app starts
# ...launch, take the screenshot, delete it...
adb -s "$FTV_SERIAL" shell run-as io.github.ravindren_sm.openstream rm files/allow-screen-capture
```

Always remove the file at the end of the run and restart the app, so the TV is left protected. Release builds ignore it.

## 5. Privacy and hygiene
- No portal address, MAC, token, device serial or TV address in any file, commit or command line saved to a script;
  use `<PORTAL_URL>`, `<EMU_MAC>`, `<FIRETV_IP>` placeholders and environment variables. `scripts/secret_scan.sh` runs
  before every commit.
- Screenshots are not redacted: never of setup or account screens; keep them outside the repo; delete after use.
- Logs: capture only the two redacted tags, scan, then delete.
- Leave the device as found: app stopped, test favourites removed, filters as they were, asleep if it was asleep. Say in
  the run log what was left behind (for example a Continue watching entry from a test).

## 6. A run, start to finish
1. `fg` (is anyone watching?), `pidof` for other IPTV apps, `mWakefulness`.
2. Start the log capture; wake the stick if needed; launch; wait; `fg`.
3. For each journey: one key at a time near any Select; `focus` before Select; timings from the log.
4. `crawl` each new screen with `--exclude nav-`.
5. Write the run log (template: `.claude/skills/firetv-drive/templates/journey.md`; examples in `ux-runs/`): result per charter, findings with severity, what was fixed,
   what is not verified.
6. Clean up: `am force-stop` the app, stop and scan the capture, delete captures and screenshots, sleep the stick if it
   was asleep.
