#!/usr/bin/env bash
# Inside emu.sh after install. Tab overview: 7 tabs incl. 1 private, LIST then GRID.
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py; export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy; O=$(dirname "$0")/shots; mkdir -p $O
$A root >/dev/null; $A wait-for-device; sleep 1
$A shell am force-stop $P; sleep 1
for i in 1 2 3 4 5 6; do
  $A shell am start -n $P/.browser.BrowserActivity -a android.intent.action.VIEW -d "http://10.0.2.2:18765/long.html?t=$i" >/dev/null; sleep 2.5
done
$A shell am start -n $P/.browser.BrowserActivity --ez new_tab_page true --ez private_tab true >/dev/null; sleep 2.5
python3 $U tap 'Tabs|tabs' ; sleep 1
$A shell am start -n $P/.browser.BrowserActivity --ez show_tabs true >/dev/null; sleep 2
python3 $U shot $O/b4-list.png; python3 $U dump | rg -i 'tabs|Search|Current' | head
python3 $U tap 'Show as grid'; sleep 1.5; python3 $U shot $O/b4-grid.png
python3 $U tap 'Show as list'; sleep 1
python3 $U tap 'Search tabs'; $A shell input text '10.0.2.2'; sleep 1; python3 $U shot $O/b4-search.png
$A shell input keyevent BACK; $A shell input keyevent BACK; sleep 1; $A shell dumpsys activity activities | rg -m1 topResumedActivity
$A logcat -d -b crash | tail -3
