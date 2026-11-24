#!/usr/bin/env bash
# Read-only environment discovery for the Fire TV IPTV project (macOS).
# Installs nothing, changes no settings, makes no network calls.
# Prints no env var values, hostnames, serials, or account data.
# Usage: bash scripts/discover_env.sh | tee docs/env_report.local.txt

line() { printf '\n== %s ==\n' "$1"; }
have() { command -v "$1" >/dev/null 2>&1; }

line "macOS / hardware"
sw_vers
uname -m
sysctl -n machdep.cpu.brand_string 2>/dev/null
sysctl -n hw.memsize 2>/dev/null | awk '{printf "RAM_GB=%.0f\n", $1/1073741824}'

line "Free disk (home volume)"
df -h "$HOME" | tail -1

line "Xcode Command Line Tools"
xcode-select -p 2>&1

line "Git"
if have git; then git --version; command -v git; else echo "git: missing"; fi

line "Homebrew"
if have brew; then brew --version | head -1; command -v brew; else echo "brew: missing"; fi

line "Java"
/usr/libexec/java_home -V 2>&1 | head -20
if have java; then java -version 2>&1 | head -3; else echo "java on PATH: missing"; fi
have javac && javac -version 2>&1

line "Android SDK location"
for v in ANDROID_HOME ANDROID_SDK_ROOT; do
  if [ -n "${!v}" ]; then echo "$v is set -> ${!v}"; else echo "$v: not set"; fi
done
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -d "$SDK" ]; then
  echo "SDK dir present: $SDK"
  ls "$SDK" 2>/dev/null
  echo "-- platforms:";        ls "$SDK/platforms" 2>/dev/null
  echo "-- build-tools:";      ls "$SDK/build-tools" 2>/dev/null
  echo "-- cmdline-tools:";    ls "$SDK/cmdline-tools" 2>/dev/null
  echo "-- platform-tools adb:"; "$SDK/platform-tools/adb" version 2>&1 | head -2
else
  echo "SDK dir not found at $SDK"
fi

line "ADB on PATH"
if have adb; then command -v adb; adb version | head -2; else echo "adb on PATH: missing"; fi
# Does NOT run 'adb devices', 'adb connect', or start a server.

line "Android Studio"
found_studio=0
for d in "/Applications/Android Studio.app" "$HOME/Applications/Android Studio.app"; do
  if [ -d "$d" ]; then echo "$d"; found_studio=1; fi
done
[ "$found_studio" -eq 1 ] || echo "Android Studio.app: not found"
ls -d "/Applications/JetBrains Toolbox.app" 2>/dev/null

line "Other toolchain bits"
for t in gradle kotlin kotlinc node npm python3 unzip; do
  if have "$t"; then printf '%s: ' "$t"; "$t" --version 2>&1 | head -1; else echo "$t: missing"; fi
done

line "Homebrew packages of interest (if brew present)"
have brew && brew list --versions 2>/dev/null | grep -Ei '^(openjdk|temurin|android|gradle|kotlin|git|scrcpy)' 

line "Done"
