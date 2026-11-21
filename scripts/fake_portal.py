#!/usr/bin/env python3
"""Fake Stalker portal for on-device UI checks. Invented data only; never talks to a real portal.

Use (macOS): run it with Apple's /usr/bin/python3. The macOS firewall can silently block a
python.org / Framework Python listener (the socket shows CLOSED and nothing connects):

    /usr/bin/python3 -I scripts/fake_portal.py 8080

Then install a side-by-side debug build so the real app's saved settings are not touched:

    ./gradlew -PcheckSuffix=true :app:assembleDebug     # package ends in .check
    adb install app/build/outputs/apk/debug/app-debug.apk
    # in the app's setup screen: http://<MAC_LAN_IP>:8080/stalker_portal/c/  and any MAC
    # afterwards: adb uninstall io.github.ravindren_sm.openstream.check

Control files, created in the folder of this script:
  fail_pages   while present, channel pages 2 and up answer HTTP 500 (tests the retry row)
  slow         while present, channel pages wait 2 s (tests the loading state)
requests.log (same folder) records type/action/genre/page only: no headers, no addresses.
Both control files and requests.log are untracked scratch files: delete them when done.
"""
import json, os, re, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HERE = os.path.dirname(os.path.abspath(__file__))
GENRES = [{"id": str(i), "title": f"Group {i:02d}"} for i in range(1, 31)]
PAGE = 14

def total_for(genre):
    return 120 if genre == "*" else 20 + int(genre) * 3

def channels_page(genre, page):
    total = total_for(genre)
    first = (page - 1) * PAGE + 1
    last = min(page * PAGE, total)
    data = [{"id": f"{genre}-{n}", "number": str(n), "name": f"Channel {n} (g{genre})", "cmd": f"ffmpeg http://stream.invalid/{genre}/{n}"}
            for n in range(first, last + 1)]
    return {"total_items": str(total), "max_page_items": PAGE, "data": data}

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):  # silence default stderr logging
        pass

    def reply(self, code, body):
        raw = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/javascript; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        q = {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}
        t, a = q.get("type", ""), q.get("action", "")
        line = f"{time.strftime('%H:%M:%S')} {t}/{a}"
        if a == "get_ordered_list":
            line += f" genre={q.get('genre')} p={q.get('p')}"
        with open(os.path.join(HERE, "requests.log"), "a") as f:
            f.write(line + "\n")
        js = None
        if (t, a) == ("stb", "handshake"):
            js = {"token": "faketoken"}
        elif (t, a) == ("stb", "get_profile"):
            js = {"status": 0, "expire_billing_date": "2027-01-01 00:00:00", "watchdog_timeout": 180}
        elif (t, a) == ("watchdog", "get_events"):
            js = {"data": {"msgs": 0}}
        elif (t, a) == ("itv", "get_genres"):
            js = GENRES
        elif (t, a) == ("itv", "get_ordered_list"):
            page = int(q.get("p", "1") or "1")
            if os.path.exists(os.path.join(HERE, "slow")):
                time.sleep(2)
            if page >= 2 and os.path.exists(os.path.join(HERE, "fail_pages")):
                return self.reply(500, "fail")
            js = channels_page(q.get("genre", "*"), page)
        if js is None:
            return self.reply(404, "not found")
        self.reply(200, json.dumps({"js": js}))

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
