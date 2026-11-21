#!/usr/bin/env python3
"""Fake Stalker portal for on-device UI checks. Invented data only; never talks to a real portal.

Use (macOS): run it with Apple's /usr/bin/python3. The macOS firewall can silently block a
python.org / Framework Python listener (the socket shows CLOSED and nothing connects):

    /usr/bin/python3 -I scripts/fake_portal.py 8080 --dir /path/to/scratch --stream-file test.mp4

Then install a side-by-side debug build so the real app's saved settings are not touched:

    ./gradlew -PcheckSuffix=true :app:assembleDebug     # package ends in .check
    adb install app/build/outputs/apk/debug/app-debug.apk
    # in the app's setup screen: http://<MAC_LAN_IP>:8080/stalker_portal/c/  and any MAC
    # afterwards: adb uninstall io.github.ravindren_sm.openstream.check

What it serves: handshake, get_profile, watchdog, live genres (30) and channels (14 per page),
create_link (points at this server's /stream/ endpoint) and, if --stream-file is given, that file
as video/mp4 at /stream/<anything>.

Control files, created in --dir (default: the folder of this script); delete them to switch off:
  fail_pages   channel pages 2 and up answer HTTP 500 (tests the retry row)
  slow         channel pages wait 2 s (tests the loading state)
  stream_403   the stream answers HTTP 403 (tests the player error and its message)
  stream_slow  the stream is sent at about 4 KB/s (tests buffering and rebuffering with a low-bitrate file)
requests.log in --dir records type/action/genre/page and stream hits only: no headers, no addresses.
All of these are scratch files: delete them when done.
"""
import argparse, json, os, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

GENRES = [{"id": str(i), "title": f"Group {i:02d}"} for i in range(1, 31)]
PAGE = 14
ARGS = None


def total_for(genre):
    return 120 if genre == "*" else 20 + int(genre) * 3


def channels_page(genre, page):
    total = total_for(genre)
    first = (page - 1) * PAGE + 1
    last = min(page * PAGE, total)
    data = [{"id": f"{genre}-{n}", "number": str(n), "name": f"Channel {n} (g{genre})",
             "cmd": f"ffmpeg http://stream.invalid/{genre}/{n}"} for n in range(first, last + 1)]
    return {"total_items": str(total), "max_page_items": PAGE, "data": data}


def flag(name):
    return os.path.exists(os.path.join(ARGS.dir, name))


def log(line):
    with open(os.path.join(ARGS.dir, "requests.log"), "a") as f:
        f.write(f"{time.strftime('%H:%M:%S')} {line}\n")


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):  # silence default stderr logging
        pass

    def reply(self, code, body, ctype="text/javascript; charset=utf-8"):
        raw = body if isinstance(body, bytes) else body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)

    def stream(self):
        log("stream GET")
        if flag("stream_403"):
            return self.reply(403, "forbidden")
        path = ARGS.stream_file
        if not path or not os.path.exists(path):
            return self.reply(404, "no stream file configured")
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Content-Length", str(size))
        self.send_header("Connection", "close")
        self.end_headers()
        slow = flag("stream_slow")
        with open(path, "rb") as f:
            while True:
                chunk = f.read(1024 if slow else 65536)
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return
                if slow:
                    time.sleep(0.25)   # about 4 KB/s: slower than a low-bitrate test video needs, so it must rebuffer

    def do_GET(self):
        url = urlparse(self.path)
        if url.path.startswith("/stream/"):
            return self.stream()
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        t, a = q.get("type", ""), q.get("action", "")
        line = f"{t}/{a}"
        if a == "get_ordered_list":
            line += f" genre={q.get('genre')} p={q.get('p')}"
        log(line)
        js = None
        if (t, a) == ("stb", "handshake"):
            js = {"token": "faketoken"}
        elif (t, a) == ("stb", "get_profile"):
            js = {"status": 0, "expire_billing_date": "2027-01-01 00:00:00", "watchdog_timeout": 180}
        elif (t, a) == ("watchdog", "get_events"):
            js = {"data": {"msgs": 0}}
        elif (t, a) == ("itv", "get_genres"):
            js = GENRES
        elif (t, a) == ("itv", "create_link"):
            host = self.headers.get("Host", f"localhost:{ARGS.port}")
            js = {"cmd": f"ffmpeg http://{host}/stream/test.mp4?token=faketoken", "error": ""}
        elif (t, a) == ("itv", "get_ordered_list"):
            page = int(q.get("p", "1") or "1")
            if flag("slow"):
                time.sleep(2)
            if page >= 2 and flag("fail_pages"):
                return self.reply(500, "fail")
            js = channels_page(q.get("genre", "*"), page)
        if js is None:
            return self.reply(404, "not found")
        self.reply(200, json.dumps({"js": js}))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("port", nargs="?", type=int, default=8080)
    ap.add_argument("--dir", default=os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--stream-file", default=None)
    ARGS = ap.parse_args()
    ThreadingHTTPServer(("0.0.0.0", ARGS.port), H).serve_forever()
