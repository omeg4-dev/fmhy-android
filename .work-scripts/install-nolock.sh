#!/usr/bin/env bash
# Usage (from a worktree root): bash /home/omega/Projects/fmhy-android/.work/0.5.4/tools/install.sh [debug|release]
# Builds, re-signs with the release key (emulator has the release-signed app) and installs on the EMULATOR only.
# Holds the shared emulator lock while installing; use emu.sh for longer check sessions.
set -euo pipefail
V=${1:-debug}
WT=$(git rev-parse --show-toplevel); cd "$WT"
if [ "$V" = release ]; then ./gw :app:assembleRelease -q; APK=app/build/outputs/apk/release/app-release.apk
  OUT=$APK
else ./gw :app:assembleDebug -q; APK=app/build/outputs/apk/debug/app-debug.apk
  P=~/.local/share/fmhy-android/keystore.properties
  get(){ rg -o "^$1=(.*)" -r '$1' $P; }
  OUT=/tmp/claude-1000/fmhy-$(basename "$WT")-signed.apk
  ~/Android/Sdk/build-tools/36.1.0/apksigner sign --ks "$(get storeFile)" --ks-pass "pass:$(get storePassword)" \
    --ks-key-alias "$(get keyAlias)" --key-pass "pass:$(get keyPassword)" --out "$OUT" "$APK"
fi
adb -s emulator-5554 install -r "$OUT"
