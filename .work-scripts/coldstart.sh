#!/usr/bin/env bash
# Usage: coldstart.sh  -> 10x cold start with URL, 10x without; prints resumed counts
A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy
$A root >/dev/null 2>&1; $A wait-for-device; sleep 1  # BrowserActivity is not exported: start it as root
run(){ # label, extra args
  ok=0
  for i in $(seq 1 10); do
    $A shell am force-stop $P; sleep 1
    $A shell am start -n $P/.browser.BrowserActivity $2 >/dev/null; sleep 3
    if $A shell dumpsys activity activities | rg -m1 'mResumedActivity|topResumedActivity' | rg -q 'BrowserActivity'; then ok=$((ok+1)); r=resumed; else r=GONE; fi
    echo "  $1 #$i $r"
  done
  echo "$1: $ok/10 resumed"
}
run "with-url" "-a android.intent.action.VIEW -d https://github.com/gorhill/uBlock"
run "no-url" ""
# new-tab extra: cold and warm
echo "== new-tab extra"
$A shell am force-stop $P; sleep 1
$A shell am start -n $P/.browser.BrowserActivity --ez new_tab_page true >/dev/null; sleep 3
$A shell dumpsys activity activities | rg -m1 'topResumedActivity'
echo "cold tabs:"; $A shell run-as $P true 2>/dev/null
$A exec-out screencap -p > "$(dirname "$0")/shots/newtab-cold.png"
$A shell am start -n $P/.browser.BrowserActivity --ez new_tab_page true --ez private_tab true >/dev/null; sleep 2
$A exec-out screencap -p > "$(dirname "$0")/shots/newtab-warm-private.png"
