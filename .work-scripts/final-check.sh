export ADB_SERIAL=emulator-5554; U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"; W=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts; O=$W/shots
bash $W/install-nolock.sh 2>&1 | tail -1
$A shell am force-stop io.github.omeg4dev.fmhy; $A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null; sleep 5
python3 $U tap '^Adblocking / Privacy'; sleep 2.5
echo "== fling"; python3 $W/fling-check.py $O/n5-section
echo "== swipe save"; python3 $U dump | rg "Report Issues"; python3 $W/swipe-check.py '^Report Issues$'
$A logcat -d -b crash | tail -5
