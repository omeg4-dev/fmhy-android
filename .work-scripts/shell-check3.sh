#!/usr/bin/env bash
# Live tabs: open via Browser item tap (blank tab) + two search results; menu, close-all dialog, notice overlay.
set -uo pipefail
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; PKG=io.github.omeg4dev.fmhy
OUT=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots; mkdir -p "$OUT"
ui() { python3 "$U" "$@"; }
fmhy() { $A shell am start -n $PKG/.MainActivity >/dev/null; }
bar() { local l x y; l=$(ui dump | rg "'Browser(, [0-9]+ tabs?)?'" | head -1)
  x=$(echo "$l" | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\1/"); y=$(echo "$l" | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\2/"); echo "$x $y"; }
$A logcat -c
$A shell am force-stop $PKG; fmhy; sleep 5
echo "== tap Browser (0 tabs -> new tab)"; read -r X Y < <(bar); $A shell input tap $X $Y; sleep 4
fmhy; sleep 3
for row in 700 900; do
  ui tap '^Search$' 1 || ui tap 'Search' ; sleep 2
  ui dump | head -12
  $A shell input text invidious; sleep 2
  $A shell input tap 540 $row; sleep 1.5
  ui tap '^Open$'; sleep 4
  fmhy; sleep 3
done
ui shot "$OUT/n1-bar-badge.png"; ui dump | rg "Browser|'[0-9]\+?'"
echo "== live menu"; read -r X Y < <(bar); $A shell input swipe $X $Y $X $Y 900; sleep 1.5
ui shot "$OUT/n1-menu-live.png"; ui dump
ui tap '^Close all tabs$'; sleep 1; ui shot "$OUT/n1-close-all-dialog.png"; ui dump | rg -i "Close all|Cancel"
ui tap '^Cancel$'; sleep 1; $A shell input keyevent KEYCODE_BACK; sleep 1
echo "== notice overlay"
fmhy; sleep 2
ui tap '^Browse$'; sleep 1
ui dump | rg "Search [0-9,.]+ sites" | tee "$OUT/n4-home-before.txt"
$A shell input swipe 540 600 540 1500 300
sleep 0.5; ui shot "$OUT/n4-notice-a.png"; ui dump | rg "Search [0-9,.]+ sites|Synced|Syncing|Offline|ad-block" | tee "$OUT/n4-home-during.txt"
sleep 1; ui shot "$OUT/n4-notice-b.png"
sleep 5; ui dump | rg "Search [0-9,.]+ sites" | tee "$OUT/n4-home-after.txt"
echo "== crash log"; $A logcat -d -b crash | tail -20
