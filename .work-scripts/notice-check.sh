export ADB_SERIAL=emulator-5554; U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"; O=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots; D=/data/data/io.github.omeg4dev.fmhy/files
$A root >/dev/null; sleep 1
$A shell am force-stop io.github.omeg4dev.fmhy; $A shell mv $D/filters $D/filters.bak
$A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null
for i in 1 2 3 4 5 6 7 8; do sleep 0.7; python3 $U shot $O/n4-notice-$i.png >/dev/null; python3 $U dump | rg "Search [0-9,.]+ sites|ad-?block|[Dd]ownload|Syncing" | head -4; echo --; done
sleep 10; $A shell am force-stop io.github.omeg4dev.fmhy; $A shell rm -rf $D/filters; $A shell mv $D/filters.bak $D/filters; $A shell ls $D/filters | head -3
