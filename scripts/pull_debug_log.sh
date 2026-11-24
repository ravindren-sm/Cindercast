#!/usr/bin/env bash
# Prints the debug player's run log from its private storage (files/debug-log.txt).
# The lines are already redacted by the app; this script prints nothing else.
# Debug builds only (run-as). Usage: bash scripts/pull_debug_log.sh
set -euo pipefail
PKG="io.github.ravindren_sm.openstream"
FILE="${FIRETV_IPTV_SECRETS:-$HOME/.config/firetv-iptv/secrets.env}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
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
[ -n "$SERIAL" ] || { echo "pull-log: no FIRETV_IP in secrets file and no ADB_SERIAL"; exit 78; }
"$ADB" -s "$SERIAL" shell "run-as $PKG cat files/debug-log.txt" | tr -d '\r'
