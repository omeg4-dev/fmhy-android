U="python3 /home/omega/.cache/claude-scratch/fmhy-polish/ui.py"; export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; S=.work-scripts/shots
go(){ $A shell am force-stop io.github.omeg4dev.fmhy; $A shell am start -n io.github.omeg4dev.fmhy/.MainActivity --es open whats_new ${1:+--es filter $1} >/dev/null; sleep 4; }
$A logcat -c
go; $U shot $S/w1-hub-dark.png; $U dump | head -40
$A shell cmd uimode night no; sleep 2; $U shot $S/w2-hub-light.png
$U tap '^Added'; sleep 1.5; $U shot $S/w3-feed-added-light.png
$A shell input keyevent KEYCODE_BACK; sleep 1; echo "after back:"; $U dump | rg -i 'All changes' | head -2; $U shot $S/w4-back-to-hub.png
$U tap 'Search'; sleep .5; $A shell input text 'github'; sleep 2; $U shot $S/w5-hub-search.png
$A shell input keyevent KEYCODE_BACK; sleep .5
go following; $U shot $S/w6-following.png; $U dump | head -12
go saved; $U shot $S/w7-saved.png; $U dump | head -12
$A shell cmd uimode night yes; sleep 2; go; $U shot $S/w8-hub-dark2.png
$A logcat -d -b crash | head -3
