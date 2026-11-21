#!/usr/bin/env bash
# Saves one secret to ~/.config/firetv-iptv/secrets.env (outside the repo and outside Google Drive).
# The value is typed at a hidden prompt: it is not echoed, not placed in shell history,
# and not passed on any command line.
# Usage: bash scripts/save_secret.sh UNIFI_API_KEY
set -euo pipefail

NAME="${1:-}"
# Variable names only: letters, digits, underscore; must not start with a digit.
[[ "$NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || { echo "Usage: $0 VAR_NAME"; exit 1; }

DIR="$HOME/.config/firetv-iptv"
FILE="$DIR/secrets.env"
umask 077                       # new files readable by the owner only
mkdir -p "$DIR"; chmod 700 "$DIR"
touch "$FILE"; chmod 600 "$FILE"

# Hidden prompt; -r keeps backslashes literal.
read -r -s -p "Value for $NAME (hidden): " VALUE; echo
[ -n "$VALUE" ] || { echo "Empty value, nothing saved."; exit 1; }

# Single-quote the value so the file can be sourced safely; escape embedded single quotes.
ESCAPED=${VALUE//\'/\'\\\'\'}

# Replace an existing entry for this name, then append the new one.
TMP="$(mktemp "$DIR/.tmp.XXXXXX")"
grep -v "^${NAME}=" "$FILE" > "$TMP" || true
printf "%s='%s'\n" "$NAME" "$ESCAPED" >> "$TMP"
mv "$TMP" "$FILE"; chmod 600 "$FILE"
unset VALUE ESCAPED

echo "Saved $NAME to $FILE (mode 600). Names stored:"
cut -d= -f1 "$FILE"
