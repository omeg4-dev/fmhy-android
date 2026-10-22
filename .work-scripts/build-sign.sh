#!/usr/bin/env bash
# Usage: build-sign.sh OUT.apk  -- build debug + re-sign with release key (no lock)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
./gw :app:assembleDebug -q
P=~/.local/share/fmhy-android/keystore.properties
get(){ rg -o "^$1=(.*)" -r '$1' $P; }
~/Android/Sdk/build-tools/36.1.0/apksigner sign --ks "$(get storeFile)" --ks-pass "pass:$(get storePassword)" \
  --ks-key-alias "$(get keyAlias)" --key-pass "pass:$(get keyPassword)" --out "$1" app/build/outputs/apk/debug/app-debug.apk
