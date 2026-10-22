#!/usr/bin/env bash
# usage: onb-shots.sh TAG NIGHT(yes|no) FONT   (run inside emu.sh lock; app installed).
# Replays onboarding, shoots WELCOME + appearance states, restores Violet/System, finishes onboarding, restores uimode/font.
set -u
export ADB_SERIAL=emulator-5554
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
OUT=/home/omega/Projects/fmhy-android-p9-onboarding/.work-scripts/shots; mkdir -p $OUT
TAG=$1; A="adb -s emulator-5554 shell"
ORIG=$($A cmd uimode night | rg -o 'yes|no|auto' | head -1)
$A cmd uimode night $2; $A settings put system font_scale $3; sleep 1
$A am force-stop io.github.omeg4dev.fmhy; $A am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null; sleep 3
python3 $U tap '^Settings$'; sleep 1.5
$A input swipe 540 2000 540 400 300; $A input swipe 540 2000 540 400 300; sleep 1
python3 $U tap 'Version 0'; sleep 1.5
python3 $U tap 'Run setup again'; sleep 3
python3 $U shot $OUT/$TAG-welcome.png
python3 $U tap '^Get started$'; sleep 2
python3 $U shot $OUT/$TAG-look-system.png
python3 $U tap 'Material You accent'; sleep 1; python3 $U shot $OUT/$TAG-look-materialyou.png
python3 $U tap '^Orange accent'; sleep 1; python3 $U shot $OUT/$TAG-look-orange.png
python3 $U tap '^Light$'; sleep 1; python3 $U shot $OUT/$TAG-look-lightpreview.png
python3 $U tap '^Violet accent'; python3 $U tap '^System$'; sleep 1
python3 $U tap '^Skip$'; sleep 3
$A cmd uimode night ${ORIG:-auto}; $A settings put system font_scale 1.0
