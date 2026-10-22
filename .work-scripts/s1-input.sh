#!/usr/bin/env bash
# Focus the search field and print the IME inputType (S1). Usage: s1-input.sh <label>
set -u
export ADB_SERIAL=emulator-5554
UI=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"
$A shell am force-stop io.github.omeg4dev.fmhy
$A shell monkey -p io.github.omeg4dev.fmhy -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
python3 $UI tap '^Search$' >/dev/null || true
sleep 1.5
python3 $UI tap 'Search FMHY' >/dev/null || true
sleep 1.5
echo "== $1"
$A shell dumpsys input_method | rg -m4 'inputType|imeOptions|mInputShown'
