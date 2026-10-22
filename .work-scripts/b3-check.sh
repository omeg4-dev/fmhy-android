#!/usr/bin/env bash
# Inside emu.sh after install. Back bubble: default, drag right, hide with toolbar, hold menu + dialog.
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py; export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy; O=$(dirname "$0")/shots; mkdir -p $O; T=${1:-top}
$A root >/dev/null; $A wait-for-device; sleep 1
$A shell am force-stop $P; sleep 1; $A shell am start -n $P/.browser.BrowserActivity -a android.intent.action.VIEW -d http://10.0.2.2:18765/long.html >/dev/null; sleep 8
python3 $U shot $O/b3-$T-default.png; python3 $U dump | rg 'Back to FMHY'
read X Y < <(python3 $U dump | rg 'Back to FMHY' | head -1 | sed -E 's/^ *([0-9]+), *([0-9]+).*/\1 \2/')
echo "bubble at $X,$Y"
# drag right
$A shell input swipe $X $Y 900 $((Y-300)) 500; sleep 1.5
python3 $U shot $O/b3-$T-dragged.png; python3 $U dump | rg 'Back to FMHY'
echo "menu open after drag? (expect none):"; python3 $U dump | rg -c 'Hide back-to-FMHY' || true
# scroll down -> bars hide, bubble hides
$A shell input swipe 540 1700 540 700 300; sleep 1.5
python3 $U shot $O/b3-$T-hidden.png
$A shell input swipe 540 700 540 1700 300; sleep 1.5
read X Y < <(python3 $U dump | rg 'Back to FMHY' | head -1 | sed -E 's/^ *([0-9]+), *([0-9]+).*/\1 \2/'); echo "bubble now $X,$Y"
# hold (no movement)
$A shell input swipe $X $Y $X $Y 1300; sleep 1
python3 $U shot $O/b3-$T-menu.png; python3 $U dump | rg 'Hide back-to-FMHY'
read X2 Y2 < <(python3 $U dump | rg 'Hide back-to-FMHY' | head -1 | sed -E 's/^ *([0-9]+), *([0-9]+).*/\1 \2/')
echo "bubble after hold: $(python3 $U dump | rg 'Back to FMHY' | head -1)"
$A shell input tap $X2 $Y2; sleep 1
python3 $U shot $O/b3-$T-dialog.png; python3 $U dump | rg 'Hide the back'
python3 $U tap '^Cancel$'; sleep 1
# tap -> FMHY
$A shell input tap $X $Y; sleep 2
$A shell dumpsys activity activities | rg -m1 topResumedActivity
