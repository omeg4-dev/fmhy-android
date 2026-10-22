#!/usr/bin/env bash
# Inside emu.sh only: build+sign+install (tools/install.sh deadlocks inside emu.sh).
set -euo pipefail
O=/tmp/claude-1000/fmhy-p9-browser-signed.apk
bash "$(dirname "$0")/build-sign.sh" $O
adb -s emulator-5554 install -r $O
