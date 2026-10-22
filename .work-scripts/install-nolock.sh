#!/usr/bin/env bash
# Same as tools/install.sh (debug) but WITHOUT its own flock: tools/install.sh deadlocks when called inside
# emu.sh (both flock the same file; the inner one waits for the outer). Call only from inside emu.sh.
set -euo pipefail
WT=/home/omega/Projects/fmhy-android-p9-shell; cd "$WT"
./gw :app:assembleDebug -q
APK=app/build/outputs/apk/debug/app-debug.apk
P=~/.local/share/fmhy-android/keystore.properties
get(){ rg -o "^$1=(.*)" -r '$1' $P; }
OUT=/tmp/claude-1000/fmhy-$(basename "$WT")-signed.apk
~/Android/Sdk/build-tools/36.1.0/apksigner sign --ks "$(get storeFile)" --ks-pass "pass:$(get storePassword)" \
  --ks-key-alias "$(get keyAlias)" --key-pass "pass:$(get keyPassword)" --out "$OUT" "$APK"
adb -s emulator-5554 install -r "$OUT"
