#!/usr/bin/env bash
# Secret scan: fails if any local secret value appears in the repo.
#
#   bash scripts/secret_scan.sh --staged   # what is about to be committed (pre-commit hook)
#   bash scripts/secret_scan.sh --tree     # tracked + untracked files (ignored files skipped)
#
# Values come from the local secrets file ($FIRETV_IPTV_SECRETS, default
# ~/.config/firetv-iptv/secrets.env). This script prints only variable NAMES and file
# PATHS, never values or matching lines. Spellings checked per value: as stored (any
# letter case), bare host of URL values, and MACs without separators or URL-encoded.
# Also blocks any 192.168.x.x address (LAN IPs never belong in this repo).
set -euo pipefail

MODE="${1:---staged}"
FILE="${FIRETV_IPTV_SECRETS:-$HOME/.config/firetv-iptv/secrets.env}"
cd "$(git rev-parse --show-toplevel)"

case "$MODE" in
  --staged) GREP=(git grep --cached -l -i -F) ; GREP_RE=(git grep --cached -l -E) ;;
  --tree)   GREP=(git grep --untracked -l -i -F) ; GREP_RE=(git grep --untracked -l -E) ;;
  *) echo "usage: $0 [--staged|--tree]"; exit 64 ;;
esac

failed=0

if [ -f "$FILE" ]; then
  PAT="$(mktemp)"; trap 'rm -f "$PAT"' EXIT
  # Load values in a subshell-free way: the file is NAME='value' lines written by
  # scripts/save_secret.sh or the probe; sourcing it only assigns variables.
  set -a; # shellcheck disable=SC1090
  . "$FILE"; set +a
  names=$(cut -d= -f1 "$FILE" | grep -E '^[A-Za-z_][A-Za-z0-9_]*$' || true)
  for name in $names; do
    value="${!name:-}"
    [ "${#value}" -ge 4 ] || continue                 # too short to scan safely
    : > "$PAT"
    printf '%s\n' "$value" >> "$PAT"
    case "$value" in
      http://*|https://*) host="${value#*://}"; host="${host%%/*}"
                          printf '%s\n' "$host" "${host%%:*}" >> "$PAT" ;;
    esac
    if [[ "$value" =~ ^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$ ]]; then
      printf '%s\n' "$(printf '%s' "$value" | tr -d ':-')" "$(printf '%s' "$value" | sed 's/[:-]/%3A/g')" >> "$PAT"
    fi
    hits=$("${GREP[@]}" -f "$PAT" 2>/dev/null || true)
    if [ -n "$hits" ]; then
      echo "secret-scan: value of $name found in:"; printf '  %s\n' $hits; failed=1
    fi
  done
else
  echo "secret-scan: no secrets file at the configured path; value scan skipped"
fi

lan=$("${GREP_RE[@]}" '192\.168\.[0-9]{1,3}\.[0-9]{1,3}' 2>/dev/null || true)
if [ -n "$lan" ]; then
  echo "secret-scan: LAN IP address (192.168.x.x) found in:"; printf '  %s\n' $lan; failed=1
fi

if [ "$failed" -ne 0 ]; then
  echo "secret-scan: FAILED. Remove the values above (use placeholders) before committing."
  exit 1
fi
echo "secret-scan: OK ($MODE)"
