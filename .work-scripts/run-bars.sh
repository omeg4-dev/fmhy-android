# usage: run-bars.sh LABEL [skipinstall]
cd /home/omega/Projects/fmhy-android-p9-browser
[ "$2" = skip ] || bash .work-scripts/install-nolock.sh >/dev/null
A="adb -s emulator-5554"; P=io.github.omeg4dev.fmhy
$A root >/dev/null; $A wait-for-device; sleep 1
$A shell am force-stop $P; sleep 1; $A shell am start -n $P/.browser.BrowserActivity -a android.intent.action.VIEW -d http://10.0.2.2:18765/long.html >/dev/null; sleep 8
bash .work-scripts/bars2.sh "$1"
