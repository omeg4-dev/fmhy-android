#!/usr/bin/env bash
# Records the search field's inputType with whatever build is installed (the "before" value).
set -u
export ADB_SERIAL=emulator-5554
UI=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
adb -s emulator-5554 shell am force-stop io.github.omeg4dev.fmhy
adb -s emulator-5554 shell monkey -p io.github.omeg4dev.fmhy -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
python3 $UI tap '^Search$' || true
sleep 1.5
python3 $UI dump | head -20
