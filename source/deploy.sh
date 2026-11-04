#!/usr/bin/env bash
# Usage: ./deploy.sh <TV_IP> [image_folder] [--build] [--skip-images]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
PKG=com.tvart.slideshow
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
DEST=/sdcard/Pictures/TVArt

[ $# -ge 1 ] || { echo "usage: $0 <TV_IP> [image_folder] [--build] [--skip-images]"; exit 1; }
TV="$1"; shift
[[ "$TV" == *:* ]] || TV="$TV:5555"
IMAGES=""; BUILD=0; SKIP=0
for a in "$@"; do
  case "$a" in --build) BUILD=1 ;; --skip-images) SKIP=1 ;; *) IMAGES="$a" ;; esac
done

ADB="$(command -v adb || true)"
[ -n "$ADB" ] || for p in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "${LOCALAPPDATA:-}/Android/Sdk"; do
  [ -x "$p/platform-tools/adb" ] && ADB="$p/platform-tools/adb" && break
  [ -x "$p/platform-tools/adb.exe" ] && ADB="$p/platform-tools/adb.exe" && break
done
[ -n "$ADB" ] || { echo "adb not found"; exit 1; }
adb_() { "$ADB" -s "$TV" "$@"; }

if [ $BUILD = 1 ] || [ ! -f "$APK" ]; then
  echo "==> Building APK"; (cd "$ROOT" && ./gradlew assembleDebug -q)
fi

echo "==> Connecting to $TV (accept 'Allow USB debugging?' on the TV)"
"$ADB" connect "$TV" || true
for i in $(seq 1 60); do
  [ "$("$ADB" -s "$TV" get-state 2>/dev/null || true)" = device ] && break
  [ $((i % 5)) = 0 ] && "$ADB" connect "$TV" >/dev/null 2>&1 || true
  sleep 2
  [ "$i" = 60 ] && { echo "TV did not authorize ADB"; exit 1; }
done

echo "==> Installing"; adb_ install -r "$APK"
adb_ shell mkdir -p "$DEST"
if [ $SKIP = 0 ]; then
  [ -z "$IMAGES" ] && [ -d "$ROOT/Walls" ] && IMAGES="$ROOT/Walls"
  if [ -n "$IMAGES" ]; then
    echo "==> Pushing art from $IMAGES"
    adb_ push "$IMAGES/." "$DEST/"
    adb_ shell "find $DEST -iname Thumbs.db -delete; find $DEST -iname desktop.ini -delete" || true
  fi
fi

echo "==> Granting permissions"
adb_ shell pm grant $PKG android.permission.READ_EXTERNAL_STORAGE
adb_ shell pm grant $PKG android.permission.WRITE_EXTERNAL_STORAGE
adb_ shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

BK="$ROOT/screensaver_backup.txt"
SS="$(adb_ shell settings get secure screensaver_components | tr -d '\r')"
if ! grep -q "screensaver_components=" "$BK" 2>/dev/null; then
  {
    echo "# Saved $(date) before TV Art changed anything"
    echo "screensaver_components=$SS"
    echo "screensaver_enabled=$(adb_ shell settings get secure screensaver_enabled | tr -d '\r')"
    echo "enabled_accessibility_services=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')"
    echo "# Restore: adb shell settings put secure screensaver_components \"$SS\""
  } > "$BK"
  echo "    Saved original values to $BK"
fi
echo "    Ember fonts: $(adb_ shell 'ls /system/fonts | grep -i ember' | tr -d '\r' | tr '\n' ' ')"

adb_ shell am start -n "$PKG/.ui.MainActivity" >/dev/null
echo "Done. On the TV: TV Art > Screensaver & YouTube > turn on 'Art screensaver' and 'Art over YouTube'."
