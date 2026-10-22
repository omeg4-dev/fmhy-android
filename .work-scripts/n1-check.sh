#!/usr/bin/env bash
# Bottom bar 4 items + hold menu. Run inside emu.sh lock after install.
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
export ADB_SERIAL=emulator-5554
OUT=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots
mkdir -p $OUT
adb -s emulator-5554 shell am force-stop io.github.omeg4dev.fmhy
adb -s emulator-5554 shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null
sleep 4
python3 $U shot $OUT/n1-bar.png
python3 $U dump | rg -i "Browse|Search|Browser|Saved" | head
