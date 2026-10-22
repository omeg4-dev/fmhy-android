#!/usr/bin/env bash
# Inside emu.sh after install. Status icon for starred + non-FMHY page, popover.
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py; export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy; O=$(dirname "$0")/shots; mkdir -p $O
POS=${1:-top}
$A root >/dev/null; $A wait-for-device
open(){ $A shell am force-stop $P; sleep 1; $A shell am start -n $P/.browser.BrowserActivity -a android.intent.action.VIEW -d "$1" >/dev/null; sleep 7; }
open https://github.com/gorhill/uBlock
python3 $U dump | rg -i 'FMHY|Starred|Listed|Not on' | head -5
python3 $U shot $O/b2-$POS-starred.png
python3 $U tap 'Starred on FMHY|Listed on FMHY|Site is on FMHY' ; sleep 1
python3 $U shot $O/b2-$POS-popover.png
$A shell input keyevent BACK; sleep 1
open https://example.com
python3 $U dump | rg -i 'FMHY|Not on' | head -3
python3 $U shot $O/b2-$POS-nonfmhy.png
