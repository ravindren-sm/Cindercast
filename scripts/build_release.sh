#!/bin/zsh
# Builds the signed release APK. The signing key and passwords are read from
# ~/.config/firetv-iptv/release/release.properties (override with OPENSTREAM_RELEASE_PROPS); they are never in the repo.
# Output: app/build/outputs/apk/release/app-release.apk (signed) and a SHA-256 line to publish beside it.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew -q :app:testDebugUnitTest :app:assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)
"$BT/apksigner" verify "$APK" >/dev/null 2>&1 && echo "signature OK" || { echo "APK is NOT signed (is release.properties present?)"; exit 1; }
shasum -a 256 "$APK"
ls -l "$APK"
