#!/usr/bin/env bash
# Usage: bars2.sh LABEL  (browser open on long page; emulator only). Counts bar hide/show events after finger lift.
D="$(cd "$(dirname "$0")" && pwd)"; L=${1:-run}; mkdir -p "$D/shots"
A="adb -s emulator-5554"
ev(){ $A shell input motionevent "$@"; }
logs(){ $A logcat -d -v time -s FmhyBars:D | rg "FmhyBars" | cut -c7-; }
phase(){ echo "--- $1 raw=$($A logcat -d | rg -c FmhyBars)"; LOG=$(logs); [ -n "$LOG" ] && echo "$LOG" | sed "s/^/  /"; echo
  H=$(echo "$LOG" | rg -c 'target 0(\.0)? -> -?[1-9]' || true); S=$(echo "$LOG" | rg -c 'target -?[1-9][0-9.]* -> 0(\.0)?' || true)
  echo "  [hides=${H:-0} shows=${S:-0}]"; $A logcat -c; }
reset(){ $A shell input swipe 540 600 540 2200 100; sleep 1.2; $A shell input swipe 540 600 540 2200 100; sleep 1.5; $A logcat -c; }
$A logcat -c; reset; echo "== $L"
# (a) tap
ev DOWN 540 1400; sleep 0.1; ev UP 540 1400; sleep 2; phase "a) tap (expect none)"
# (d) slow drag UP 400px (scrolls page down) with lift-off: expect exactly 1 hide
ev DOWN 540 1700; for y in $(seq 1690 -20 1300); do ev MOVE 540 $y; sleep 0.04; done; ev UP 540 1300; sleep 2.5; phase "d) slow drag up 400px + lift (expect hides=1 shows=0)"
# scroll further down then slow drag DOWN 400px
ev DOWN 540 1000; for y in $(seq 1010 20 1400); do ev MOVE 540 $y; sleep 0.04; done; ev UP 540 1400; sleep 2.5; phase "d2) slow drag down 400px + lift (expect shows<=1)"
reset
$A exec-out screencap -p > "$D/shots/$L-before-e.png"
# (e) real fling up
$A shell input swipe 540 1700 540 700 120; sleep 2.5; phase "e) fling up (expect hides=1 shows=0)"
$A shell input swipe 540 700 540 1700 120; sleep 2.5; phase "e2) fling down"
