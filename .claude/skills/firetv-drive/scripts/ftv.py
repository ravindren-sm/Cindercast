#!/usr/bin/env python3
"""
ftv.py: drive and inspect an Amazon Fire TV (or any Android TV device) over ADB.

The point is to READ where focus is after every key press instead of guessing from screenshots.
After each key it runs `uiautomator dump`, finds the node with focused="true" and reports its label
and bounds. On top of that it can crawl a screen's focus graph (D-pad directions only) and flag
navigation bugs: focus lost, moves that cannot be undone, and focusable elements a move skipped over.

Safety rules built in:
- Keys are only sent when the foreground app is the one you name (--expect, or FTV_APP).
- The crawler presses only Up/Down/Left/Right. It never presses Select, Back, Home or Menu, so it
  cannot trigger an action, delete anything or leave the app on purpose.
- Text read from the screen is redacted (URLs, host names, IP addresses, MAC addresses, long tokens)
  before it is printed or written, so reports never carry portal or account details.
- Output files go to FTV_OUT or a temp folder, never into a repository by default.

Environment:
  FTV_SERIAL  adb serial to use (for example <FIRETV_IP>:5555). Optional when one device is connected.
  FTV_APP     the app package keys may be sent to, when --expect is not given.
  FTV_OUT     folder for crawl reports and screenshots (default: <tmp>/ftv-runs).

Standard library only. Requires `adb` on PATH.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from collections import deque
from datetime import datetime

# Remote keys by friendly name -> Android key code names (KEYCODE_ prefix added when sent).
KEYS = {
    "up": "DPAD_UP", "down": "DPAD_DOWN", "left": "DPAD_LEFT", "right": "DPAD_RIGHT",
    "select": "DPAD_CENTER", "center": "DPAD_CENTER", "ok": "DPAD_CENTER",
    "back": "BACK", "home": "HOME", "menu": "MENU",
    "playpause": "MEDIA_PLAY_PAUSE", "play": "MEDIA_PLAY", "pause": "MEDIA_PAUSE",
    "ff": "MEDIA_FAST_FORWARD", "rew": "MEDIA_REWIND",
    "del": "DEL", "enter": "ENTER", "wakeup": "WAKEUP",
}
DIRECTIONS = ["up", "down", "left", "right"]
OPPOSITE = {"up": "down", "down": "up", "left": "right", "right": "left"}

# ---------------------------------------------------------------- redaction

_REDACTIONS = [
    (re.compile(r"\b[a-z][a-z0-9+.-]*://\S+", re.I), "<URL>"),
    (re.compile(r"\b[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}\b", re.I), "<MAC>"),
    (re.compile(r"\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b"), "<IP>"),
    (re.compile(r"\b(?:[a-z0-9-]+\.)+(?:com|net|org|tv|io|me|cc|xyz|info|biz|co|uk|ru|de|nl|fr|eu|us|in|online|site|live|pro|top|club)(?::\d+)?(?:/\S*)?", re.I), "<HOST>"),
    (re.compile(r"\b[A-Za-z0-9_-]{24,}\b"), "<TOKEN>"),
]


def redact(text: str) -> str:
    for pattern, repl in _REDACTIONS:
        text = pattern.sub(repl, text)
    return text

# ---------------------------------------------------------------- adb


def adb(*args, binary=False, check=True):
    cmd = ["adb"]
    serial = os.environ.get("FTV_SERIAL")
    if serial:
        cmd += ["-s", serial]
    r = subprocess.run(cmd + list(args), capture_output=True)
    if check and r.returncode != 0:
        raise SystemExit(f"adb {' '.join(args[:2])} failed: {r.stderr.decode(errors='replace').strip()}")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def foreground():
    """(package, activity) of the window that has input focus, or (None, None)."""
    out = adb("shell", "dumpsys", "window")
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^/\s}]+)(?:/([^\s}]+))?", out)
    if m:
        return (m.group(1), m.group(2))
    # No focused window: the screen is asleep (screensaver off, TV standby) or a window is changing.
    if "mWakefulness=Asleep" in adb("shell", "dumpsys", "power"):
        return ("asleep", None)
    return (None, None)


def guard(expect):
    """Stop unless the foreground app is the expected one ("any" skips the check)."""
    expect = expect or os.environ.get("FTV_APP")
    if not expect:
        raise SystemExit("Refusing to send keys: name the app with --expect <package> (or FTV_APP), or --expect any.")
    pkg, _ = foreground()
    if pkg == "asleep" and expect != "any":
        raise SystemExit("Refusing to send keys: the TV is asleep. Wake it with `ftv.py key wakeup --expect any` (ask first if someone may be using it).")
    if expect != "any" and pkg != expect:
        raise SystemExit(f"Refusing to send keys: foreground is {pkg!r}, expected {expect!r}.")
    return pkg


def press(name, settle):
    code = KEYS.get(name.lower())
    if code is None:
        raise SystemExit(f"Unknown key {name!r}. Known: {', '.join(sorted(KEYS))}")
    adb("shell", "input", "keyevent", f"KEYCODE_{code}")
    time.sleep(settle)

# ---------------------------------------------------------------- UI dump


def dump():
    """Every node on screen: label (own and descendant text), ids, flags and bounds. Text is redacted."""
    remote = "/sdcard/ftv-dump.xml"
    xml = None
    for _ in range(4):
        out = adb("shell", "uiautomator", "dump", remote, check=False)
        if "dumped" in out.lower():
            xml = adb("exec-out", "cat", remote)
            break
        time.sleep(0.6)   # "could not get idle state": animation or video still running
    adb("shell", "rm", "-f", remote, check=False)
    if xml is None:
        raise SystemExit("uiautomator dump failed (screen not idle, for example during playback).")
    root = ET.fromstring(xml)
    nodes = []

    def walk(el):
        texts = []
        for child in el.findall("node"):
            texts += walk(child)
        own = [t for t in (el.get("text"), el.get("content-desc")) if t]
        all_texts = own + texts
        if el.tag == "node":
            nums = re.findall(r"-?\d+", el.get("bounds", ""))
            if len(nums) == 4:
                x1, y1, x2, y2 = map(int, nums)
                nodes.append({
                    "label": redact(" · ".join(all_texts))[:90],
                    "rid": el.get("resource-id", ""),
                    "cls": el.get("class", "").split(".")[-1],
                    "pkg": el.get("package", ""),
                    "focusable": el.get("focusable") == "true",
                    "focused": el.get("focused") == "true",
                    "bounds": [x1, y1, x2, y2],
                })
        return all_texts

    walk(root)
    return nodes


def focused(nodes):
    """The focused node: the smallest one marked focused (containers can report focus too)."""
    hits = [n for n in nodes if n["focused"]]
    if not hits:
        return None
    return min(hits, key=lambda n: (n["bounds"][2] - n["bounds"][0]) * (n["bounds"][3] - n["bounds"][1]))


def ident(n):
    """A stable-ish id: the resource id (Compose testTag when exposed), else the label, else the bounds."""
    if n is None:
        return None
    if n["rid"]:
        return n["rid"]
    if n["label"]:
        return n["label"]
    return "@" + ",".join(map(str, n["bounds"]))


def describe(n):
    if n is None:
        return "(nothing focused)"
    b = n["bounds"]
    name = n["label"] or n["rid"] or n["cls"]
    return f"{name}  [{b[0]},{b[1]} - {b[2]},{b[3]}]"

# ---------------------------------------------------------------- focus-graph crawl


def skipped_between(a, b, d, nodes, app):
    """Focusable nodes lying between a and b in direction d that overlap a across the other axis."""
    ax1, ay1, ax2, ay2 = a["bounds"]
    bx1, by1, bx2, by2 = b["bounds"]
    out = []
    for c in nodes:
        if not c["focusable"] or c["pkg"] != app or ident(c) in (ident(a), ident(b)):
            continue
        cx1, cy1, cx2, cy2 = c["bounds"]
        if cx2 <= cx1 or cy2 <= cy1:
            continue
        # Skip containers that enclose the start or end element.
        if (cx1 <= ax1 and cy1 <= ay1 and cx2 >= ax2 and cy2 >= ay2) or (cx1 <= bx1 and cy1 <= by1 and cx2 >= bx2 and cy2 >= by2):
            continue
        h_overlap = min(ax2, cx2) - max(ax1, cx1) > 0
        v_overlap = min(ay2, cy2) - max(ay1, cy1) > 0
        if d == "down" and h_overlap and cy1 >= ay2 - 4 and cy1 < by1 - 4:
            out.append(c)
        elif d == "up" and h_overlap and cy2 <= ay1 + 4 and cy2 > by2 + 4:
            out.append(c)
        elif d == "right" and v_overlap and cx1 >= ax2 - 4 and cx1 < bx1 - 4:
            out.append(c)
        elif d == "left" and v_overlap and cx2 <= ax1 + 4 and cx2 > bx2 + 4:
            out.append(c)
    return out


def crawl(app, settle, max_presses, dirs, exclude_prefixes):
    """
    Breadth-first walk of the focus graph from the focused element, pressing only D-pad directions.
    Each move is undone with the opposite key; when that fails, focus is routed back over known edges.
    """
    nodes = dump()
    start = focused(nodes)
    if start is None:
        raise SystemExit("Nothing is focused on screen; cannot start a crawl.")
    graph, info, findings, seen_focusable = {}, {}, [], {}
    presses = 0

    def note_focusable(ns):
        for n in ns:
            if n["focusable"] and n["pkg"] == app and n["label"]:
                seen_focusable.setdefault(ident(n), n)

    def current():
        ns = dump()
        note_focusable(ns)
        return ns, focused(ns)

    def route(src, dst):
        """Key sequence from src to dst over recorded edges, or None."""
        q, prev = deque([src]), {src: None}
        while q:
            u = q.popleft()
            if u == dst:
                keys = []
                while prev[u] is not None:
                    p, k = prev[u]
                    keys.append(k)
                    u = p
                return list(reversed(keys))
            for k, v in graph.get(u, {}).items():
                if v and v not in prev and not v.startswith("("):
                    prev[v] = (u, k)
                    q.append(v)
        return None

    def toward(a, b):
        """The D-pad direction from node a toward node b (the larger of the two centre offsets)."""
        ax, ay = (a["bounds"][0] + a["bounds"][2]) / 2, (a["bounds"][1] + a["bounds"][3]) / 2
        bx, by = (b["bounds"][0] + b["bounds"][2]) / 2, (b["bounds"][1] + b["bounds"][3]) / 2
        if abs(by - ay) >= abs(bx - ax):
            return "down" if by > ay else "up"
        return "right" if bx > ax else "left"

    def goto(target):
        nonlocal presses
        _, f = current()
        if ident(f) == target:
            return True
        keys = route(ident(f), target) if f else None
        # Stranded on an excluded element (for example the tab bar) with no recorded way back: step toward the target,
        # which usually re-enters the body where it was left. A few tries at most.
        tries = 0
        while keys is None and f is not None and target in info and tries < 3 \
                and any(ident(f).startswith(p) for p in exclude_prefixes):
            press(toward(f, info[target]), settle)
            presses += 1
            tries += 1
            _, f = current()
            if ident(f) == target:
                return True
            keys = route(ident(f), target) if f else None
        if keys is None:
            return False
        for k in keys:
            press(k, settle)
            presses += 1
        _, f = current()
        return ident(f) == target

    sid = ident(start)
    info[sid] = start
    note_focusable(nodes)
    queue = deque([sid])
    stopped = None
    while queue and presses < max_presses and not stopped:
        cur = queue.popleft()
        if any(cur.startswith(p) for p in exclude_prefixes):
            continue
        if not goto(cur):
            findings.append({"kind": "lost-route", "from": cur, "detail": "could not return to this element to test it"})
            continue
        graph.setdefault(cur, {})
        for d in dirs:
            if presses >= max_presses:
                break
            before_nodes, before = current()
            if ident(before) != cur and not goto(cur):
                break
            press(d, settle)
            presses += 1
            pkg, _ = foreground()
            if pkg != app:
                graph[cur][d] = f"(left app: {pkg})"
                findings.append({"kind": "left-app", "from": cur, "dir": d, "detail": f"foreground became {pkg}"})
                stopped = f"A {d.upper()} press from {cur!r} left the app; crawl stopped."
                break
            after_nodes, after = current()
            to = ident(after)
            if after is None:
                graph[cur][d] = "(focus lost)"
                findings.append({"kind": "focus-lost", "from": cur, "dir": d, "detail": "no element has focus after the press"})
                continue
            graph[cur][d] = to
            info.setdefault(to, after)
            if to == cur:
                continue   # an edge of the screen: focus stays
            for c in skipped_between(info[cur] if cur in info else before, after, d, after_nodes, app):
                findings.append({"kind": "skipped", "from": cur, "dir": d, "to": to, "skipped": ident(c),
                                 "detail": f"{d.upper()} went past {describe(c)}"})
            if to not in graph and to not in queue:
                queue.append(to)
            press(OPPOSITE[d], settle)
            presses += 1
            _, back = current()
            if ident(back) != cur:
                findings.append({"kind": "not-reversible", "from": cur, "dir": d, "to": to,
                                 "detail": f"{OPPOSITE[d].upper()} from {to!r} went to {ident(back)!r}, not back"})
                goto(cur)
    unreached = [ident(n) for k, n in seen_focusable.items() if k not in graph and k not in queue
                 and not any(k.startswith(p) for p in exclude_prefixes)]
    for u in unreached:
        findings.append({"kind": "unreached", "from": u, "detail": "focusable on screen but never reached by the crawl"})
    return {"app": app, "start": sid, "presses": presses, "stopped": stopped,
            "graph": graph, "elements": {k: describe(v) for k, v in info.items()}, "findings": findings,
            "incomplete": bool(queue)}


def write_report(result, name):
    base = os.environ.get("FTV_OUT") or os.path.join(tempfile.gettempdir(), "ftv-runs")
    folder = os.path.join(base, datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + re.sub(r"[^A-Za-z0-9_-]+", "-", name))
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "crawl.json"), "w") as f:
        json.dump(result, f, indent=2)
    lines = [f"# Focus crawl: {name}", "", f"App: `{result['app']}`. Start: `{result['start']}`. Key presses: {result['presses']}."]
    if result["stopped"]:
        lines.append(f"\n**Stopped:** {result['stopped']}")
    if result["incomplete"]:
        lines.append("\n**Incomplete:** the press budget ran out before every element was tested (raise --max-presses).")
    lines += ["", "## Findings", ""]
    if not result["findings"]:
        lines.append("None.")
    for fd in result["findings"]:
        lines.append(f"- **{fd['kind']}** from `{fd['from']}`" + (f" ({fd['dir'].upper()})" if fd.get("dir") else "") + f": {fd['detail']}")
    lines += ["", "## Focus graph", "", "| Element | Up | Down | Left | Right |", "|---|---|---|---|---|"]
    for el, edges in result["graph"].items():
        cells = [("=" if edges.get(d) == el else (edges.get(d) or "")) for d in DIRECTIONS]
        lines.append(f"| {el} | " + " | ".join(c.replace('|', '/') for c in cells) + " |")
    lines += ["", "`=` means focus stayed (edge of the screen)."]
    with open(os.path.join(folder, "crawl.md"), "w") as f:
        f.write("\n".join(lines) + "\n")
    return folder

# ---------------------------------------------------------------- commands


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("fg", help="print the foreground package and activity")
    sub.add_parser("focus", help="print the focused element")
    s = sub.add_parser("dump", help="list focusable elements (redacted), focused one marked")
    s.add_argument("--all", action="store_true", help="include non-focusable nodes that have text")
    s = sub.add_parser("key", help="press keys, printing focus after each")
    s.add_argument("keys", nargs="+", help=f"key names: {', '.join(sorted(KEYS))}; append *N to repeat (down*3)")
    s.add_argument("--expect", help="package that must be in the foreground ('any' to skip the check)")
    s.add_argument("--settle", type=float, default=0.6, help="seconds to wait after each key")
    s.add_argument("--quiet", action="store_true", help="print focus only after the last key")
    s = sub.add_parser("text", help="type text into the focused field (never presses Enter)")
    s.add_argument("text")
    s.add_argument("--expect")
    s = sub.add_parser("launch", help="start an app from its TV launcher entry")
    s.add_argument("package")
    s = sub.add_parser("shot", help="save a screenshot (may show unredacted text: delete it after use)")
    s.add_argument("path")
    s = sub.add_parser("crawl", help="map the focus graph of the current screen with D-pad keys only")
    s.add_argument("name", help="short name for the report, for example profiles-manage")
    s.add_argument("--expect", help="app package (required unless FTV_APP is set)")
    s.add_argument("--settle", type=float, default=0.7)
    s.add_argument("--max-presses", type=int, default=160)
    s.add_argument("--dirs", default="up,down,left,right")
    s.add_argument("--exclude", action="append", default=[], help="skip elements whose id starts with this (repeatable), e.g. nav-")
    a = p.parse_args()

    if a.cmd == "fg":
        pkg, act = foreground()
        print(f"{pkg}/{act}" if pkg else "unknown")
    elif a.cmd == "focus":
        print(describe(focused(dump())))
    elif a.cmd == "dump":
        for n in dump():
            if n["focusable"] or (a.all and n["label"]):
                mark = ">>" if n["focused"] else ("  " if n["focusable"] else " .")
                rid = f" #{n['rid']}" if n["rid"] else ""
                print(f"{mark} {describe(n)}{rid}")
    elif a.cmd == "key":
        guard(a.expect)
        seq = []
        for k in a.keys:
            name, _, times = k.partition("*")
            seq += [name] * (int(times) if times else 1)
        for i, k in enumerate(seq):
            press(k, a.settle)
            if not a.quiet or i == len(seq) - 1:
                pkg, _ = foreground()
                print(f"{k:>9} -> {describe(focused(dump()))}" + ("" if pkg == (a.expect or os.environ.get('FTV_APP')) or a.expect == "any" else f"   (foreground now {pkg})"))
    elif a.cmd == "text":
        guard(a.expect)
        adb("shell", "input", "text", a.text.replace(" ", "%s"))
    elif a.cmd == "launch":
        adb("shell", "monkey", "-p", a.package, "-c", "android.intent.category.LEANBACK_LAUNCHER", "1")
    elif a.cmd == "shot":
        with open(a.path, "wb") as f:
            f.write(adb("exec-out", "screencap", "-p", binary=True))
        print(a.path)
    elif a.cmd == "crawl":
        app = guard(a.expect)
        result = crawl(app, a.settle, a.max_presses, [d.strip() for d in a.dirs.split(",") if d.strip()], a.exclude)
        folder = write_report(result, a.name)
        print(f"{len(result['findings'])} finding(s), {result['presses']} presses. Report: {folder}/crawl.md")
        for fd in result["findings"]:
            print(f"  - {fd['kind']}: {fd['from']}" + (f" {fd['dir'].upper()}" if fd.get("dir") else "") + f": {fd['detail']}")


if __name__ == "__main__":
    main()
