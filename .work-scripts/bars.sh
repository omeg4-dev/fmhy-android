#!/usr/bin/env bash
# Usage: bars.sh LABEL   (browser must be open on a long starred page, emulator only)
# Prints FmhyBars log counts per phase; screenshots to .work-scripts/shots/LABEL-*.png
D="$(cd "$(dirname "$0")" && pwd)"; L=${1:-run}; mkdir -p "$D/shots"
A="adb -s emulator-5554"
ev(){ $A shell input motionevent "$@"; }
dump(){ echo "--- $1"; LOG=$($A logcat -d -s FmhyBars:D | rg "FmhyBars" | sed 's/^.*FmhyBars: /  /'); [ -n "$LOG" ] && echo "$LOG"
  echo "  [writes: $(echo "$LOG" | rg -c 'webView.translationY' || true) | targets: $(echo "$LOG" | rg -c 'target ' || true) | writes-while-down: $(echo "$LOG" | rg 'translationY' | rg -c 'pointerDown=true' || true)]"; $A logcat -c; }
shot(){ $A exec-out screencap -p > "$D/shots/$L-$1.png"; }
$A logcat -c
# reset: fast swipe down to top
$A shell input swipe 540 600 540 2000 100; sleep 1.5; $A shell input swipe 540 600 540 2000 100; sleep 1.5; $A logcat -c
echo "== $L"
# (a) tap
ev DOWN 540 1400; sleep 0.1; ev UP 540 1400; sleep 1; dump "a) tap"
# (b) slow drag up 10px steps 1600 -> 1300
ev DOWN 540 1600; for y in $(seq 1590 -10 1300); do ev MOVE 540 $y; sleep 0.05; done; dump "b1) slow drag, finger still down"
ev UP 540 1300; sleep 1.5; dump "b2) after UP"
# (c) fast swipe up then down
$A shell input swipe 540 1600 540 600 150; sleep 1.5; shot after-swipe-up; dump "c1) fast swipe up"
$A shell input swipe 540 600 540 1600 150; sleep 1.5; dump "c2) fast swipe down"
