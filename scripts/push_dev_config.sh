#!/usr/bin/env bash
# Writes the debug player's config into the app's PRIVATE storage on the Fire TV:
#   /data/data/<package>/files/dev-config.json
#
#   bash scripts/push_dev_config.sh [genre] [channel] [identity] [streamUserAgent] [mode] [vodCategory]
#   defaults: "ENGLISH USA LOCAL" "NEWS 12 LONG ISLAND" honest player live "ENGLISH MOVIES"
#   mode: live | vod-catalog (4e, no playback) | vod-movie | vod-episode (4f, play + seek check)
#
# How secrets stay out of sight (CLAUDE.md rules 3 and 10):
# - PORTAL_URL and EMU_MAC are read from the local secrets file by Python (not the shell),
#   serialized to JSON on stdout, and piped through `adb shell run-as` into the app's files.
# - No value appears in any command line, temp file, or output. Only names are printed.
# - Works for debug builds only (run-as requires a debuggable app).
# FIRETV_IP is read from the same file; ADB_SERIAL can override the device.
set -euo pipefail

PKG="com.example.firetvhello"
FILE="${FIRETV_IPTV_SECRETS:-$HOME/.config/firetv-iptv/secrets.env}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
GENRE="${1:-ENGLISH USA LOCAL}"; CHANNEL="${2:-NEWS 12 LONG ISLAND}"
IDENTITY="${3:-honest}"; STREAM_UA="${4:-player}"; MODE="${5:-live}"; VOD_CAT="${6:-ENGLISH MOVIES}"

[ -f "$FILE" ] || { echo "push-config: secrets file not found"; exit 78; }
SERIAL="${ADB_SERIAL:-$(python3 -I - "$FILE" <<'PY'
import shlex, sys
vals = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.strip()
    if "=" in line and not line.startswith("#"):
        k, v = line.split("=", 1); vals[k.strip()] = (shlex.split(v) or [""])[0]
print(vals.get("FIRETV_IP", "") + ":5555" if vals.get("FIRETV_IP") else "")
PY
)}"
[ -n "$SERIAL" ] || { echo "push-config: no FIRETV_IP in secrets file and no ADB_SERIAL"; exit 78; }

python3 -I - "$FILE" "$GENRE" "$CHANNEL" "$IDENTITY" "$STREAM_UA" "$MODE" "$VOD_CAT" <<'PY' \
  | "$ADB" -s "$SERIAL" shell "run-as $PKG sh -c 'mkdir -p files && cat > files/dev-config.json && chmod 600 files/dev-config.json'"
import json, shlex, sys
path, genre, channel, identity, stream_ua, mode, vod_cat = sys.argv[1:8]
vals = {}
for line in open(path, encoding="utf-8"):
    line = line.strip()
    if "=" in line and not line.startswith("#"):
        k, v = line.split("=", 1); vals[k.strip()] = (shlex.split(v) or [""])[0]
missing = [n for n in ("PORTAL_URL", "EMU_MAC") if not vals.get(n)]
if missing:
    sys.stderr.write("push-config: missing " + ", ".join(missing) + "\n"); sys.exit(78)
sys.stdout.write(json.dumps({
    "portalUrl": vals["PORTAL_URL"], "mac": vals["EMU_MAC"],
    "genre": genre, "channel": channel, "identity": identity, "streamUserAgent": stream_ua,
    "mode": mode, "vodCategory": vod_cat,
}))
PY

# Confirm without printing contents: size and permissions only.
"$ADB" -s "$SERIAL" shell "run-as $PKG sh -c 'ls -l files/dev-config.json'" | awk '{print "push-config: wrote files/dev-config.json (" $5 " bytes, " $1 ")"}'
echo "push-config: mode=$MODE vodCategory='$VOD_CAT' genre='$GENRE' channel='$CHANNEL' identity=$IDENTITY streamUserAgent=$STREAM_UA"
