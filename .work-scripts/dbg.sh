A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy
$A root >/dev/null; $A wait-for-device; sleep 1
$A shell am force-stop $P; sleep 1; $A shell am start -n $P/.browser.BrowserActivity -a android.intent.action.VIEW -d https://github.com/gorhill/uBlock >/dev/null; sleep 8
$A logcat -c
$A shell input swipe 540 1700 540 700 120; sleep 2
echo A; $A logcat -d -v time -s FmhyBars:D | head -3
echo B; $A logcat -d -s FmhyBars:D | head -3
echo C; $A logcat -d | rg -c FmhyBars
