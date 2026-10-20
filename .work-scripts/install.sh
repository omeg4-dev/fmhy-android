#!/usr/bin/env bash
# Build debug, re-sign with the release key (emulator has the release-signed app), install on emulator only.
set -e
cd "$(dirname "$0")/.."
./gw :app:assembleDebug -q
P=~/.local/share/fmhy-android/keystore.properties
get(){ rg -o "^$1=(.*)" -r '$1' $P; }
OUT=/tmp/claude-1000/fmhy-p8-signed.apk
~/Android/Sdk/build-tools/36.1.0/apksigner sign --ks "$(get storeFile)" --ks-pass "pass:$(get storePassword)" --ks-key-alias "$(get keyAlias)" --key-pass "pass:$(get keyPassword)" --out $OUT app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r $OUT
