#!/usr/bin/env bash
# W2 emulator check: bottom bar (4 items, badge), hold menu (live + restorable variants), close-all dialog,
# status-notice overlay (Home first-item y with/without a notice). Run INSIDE emu.sh after install-nolock.sh.
set -uo pipefail
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"
PKG=io.github.omeg4dev.fmhy
OUT=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots
mkdir -p "$OUT"
ui() { python3 "$U" "$@"; }

fmhy() { $A shell am start -n $PKG/.MainActivity >/dev/null; }

echo "== A: bottom bar"
$A shell am force-stop $PKG
$A logcat -c
fmhy; sleep 5
ui shot "$OUT/n1-bar-0tabs.png"
ui dump | rg -i "^ *[0-9]+, *[0-9]+  '(Browse|Search|Browser|Saved)'" || true
ui dump | rg "(Browse|Search|Browser|Saved)'" | sort -t, -k1 -n

echo "== B: open 3 tabs via section entries, return"
ui tap 'Adblock' || true; sleep 2
ui shot "$OUT/n5-section.png"
for y in 900 1100 1300; do
  $A shell input tap 540 $y; sleep 3
  fmhy; sleep 2
done
fmhy; sleep 3
ui shot "$OUT/n1-bar-badge.png"
echo "-- hold Browser item"
read -r BX BY < <(ui dump | rg "'Browser, [0-9]+ tabs?'|'Browser'" | head -1 | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\1 \2/")
echo "browser item at $BX,$BY"
$A shell input swipe "$BX" "$BY" "$BX" "$BY" 900
sleep 1.5
ui shot "$OUT/n1-menu-live.png"
ui dump
echo "-- close-all dialog"
ui tap '^Close all tabs$' || true
sleep 1
ui shot "$OUT/n1-close-all-dialog.png"
ui has '^Close all [0-9]+ tabs\?$' && echo "dialog present"
ui tap '^Cancel$' || true
sleep 1
$A shell input keyevent KEYCODE_BACK
sleep 1

echo "== C: restorable-only variant (force-stop keeps persisted tabs)"
$A shell am force-stop $PKG
fmhy; sleep 5
read -r BX BY < <(ui dump | rg "'Browser, [0-9]+ tabs?'|'Browser'" | head -1 | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\1 \2/")
$A shell input swipe "$BX" "$BY" "$BX" "$BY" 900
sleep 1.5
ui shot "$OUT/n1-menu-restorable.png"
ui dump | rg -i "last time|tabs" || true
$A shell input keyevent KEYCODE_BACK
sleep 1

echo "== D: notice overlay"
fmhy; sleep 2
ui dump | rg "Search [0-9,.]+ sites" > "$OUT/n4-home-before.txt"; cat "$OUT/n4-home-before.txt"
$A shell input swipe 540 600 540 1500 300
sleep 0.4; ui shot "$OUT/n4-notice-a.png"
sleep 0.8; ui shot "$OUT/n4-notice-b.png"
ui dump | rg "Search [0-9,.]+ sites|Synced|Syncing|Offline|ad-block" | tee "$OUT/n4-home-during.txt"
sleep 5
ui dump | rg "Search [0-9,.]+ sites" | tee "$OUT/n4-home-after.txt"

echo "== crash log"
$A logcat -d -b crash | tail -20
