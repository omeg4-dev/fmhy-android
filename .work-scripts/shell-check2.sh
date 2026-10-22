#!/usr/bin/env bash
# Seeds 3 restorable tabs, checks bar badge, restorable + live hold menus, close-all dialog, notice overlay.
set -uo pipefail
U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; PKG=io.github.omeg4dev.fmhy
OUT=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots; mkdir -p "$OUT"
ui() { python3 "$U" "$@"; }
fmhy() { $A shell am start -n $PKG/.MainActivity >/dev/null; }
hold() { local l; l=$(ui dump | rg "'Browser(, [0-9]+ tabs?)?'" | head -1); local x y
  x=$(echo "$l" | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\1/"); y=$(echo "$l" | sed -E "s/^ *([0-9]+), *([0-9]+) .*/\2/")
  echo "hold at $x,$y"; $A shell input swipe "$x" "$y" "$x" "$y" 900; sleep 1.5; }
$A logcat -c
$A shell am force-stop $PKG
$A root >/dev/null; sleep 1
DB=/data/data/$PKG/files/user/user.db
$A shell "sqlite3 $DB \"INSERT OR REPLACE INTO kv(key,value) VALUES('browser_tabs','[{\\\"url\\\":\\\"https://example.com/\\\",\\\"title\\\":\\\"Example Domain\\\"},{\\\"url\\\":\\\"https://www.wikipedia.org/\\\",\\\"title\\\":\\\"Wikipedia\\\"},{\\\"url\\\":\\\"https://example.org/\\\",\\\"title\\\":\\\"Example Org\\\"}]')\""
$A shell "sqlite3 $DB \"select substr(value,1,60) from kv where key='browser_tabs'\""
fmhy; sleep 5
echo "== bar with badge"; ui shot "$OUT/n1-bar-badge.png"; ui dump | rg "Browse|Search|Browser|Saved|^ *[0-9]+, *[0-9]+  '[0-9]\+?'"
echo "== restorable menu"; hold; ui shot "$OUT/n1-menu-restorable.png"; ui dump | rg -i "last time|tab|Browser"
$A shell input keyevent KEYCODE_BACK; sleep 1
echo "== live menu (open browser via Show all tabs)"
hold; ui tap '^Show all tabs$'; sleep 5
fmhy; sleep 3
hold; ui shot "$OUT/n1-menu-live.png"; ui dump | rg -i "tab|Example|Wikipedia|private"
ui tap '^Close all tabs$'; sleep 1; ui shot "$OUT/n1-close-all-dialog.png"; ui dump | rg -i "Close all|Cancel"
ui tap '^Cancel$'; sleep 1; $A shell input keyevent KEYCODE_BACK; sleep 1
echo "== notice overlay"
fmhy; sleep 2
ui dump | rg "Search [0-9,.]+ sites" | tee "$OUT/n4-home-before.txt"
$A shell input swipe 540 600 540 1500 300
sleep 0.5; ui shot "$OUT/n4-notice-a.png"; ui dump | rg "Search [0-9,.]+ sites|Synced|Syncing|Offline|ad-block" | tee "$OUT/n4-home-during.txt"
sleep 1; ui shot "$OUT/n4-notice-b.png"
sleep 5; ui dump | rg "Search [0-9,.]+ sites" | tee "$OUT/n4-home-after.txt"
echo "== crash log"; $A logcat -d -b crash | tail -20
# restore the seeded state
$A shell "sqlite3 $DB \"INSERT OR REPLACE INTO kv(key,value) VALUES('browser_tabs','[]')\""
$A shell am force-stop $PKG
