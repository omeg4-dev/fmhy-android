U="python3 /home/omega/.cache/claude-scratch/fmhy-polish/ui.py"
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"
S=.work-scripts/shots
$A logcat -c
$A shell am force-stop io.github.omeg4dev.fmhy
$A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null
sleep 4
$U tap '^Check a link$'; sleep 1.5
$U shot $S/c0-empty.png
echo "--- inputType (focused field)"
$A shell dumpsys input_method | rg -m3 'inputType'
n=0
for ch in e x a; do $A shell input text $ch; sleep 0.15; done
$U shot $S/c1-exa-immediate.png
sleep 1.2; $U shot $S/c1-exa.png
for ch in m p l e . c o; do $A shell input text $ch; sleep 0.12; done
$U shot $S/c2-example-co-immediate.png
sleep 1.2; $U shot $S/c2-example-co.png
$A shell input text m; sleep 0.6
$U shot $S/c3-example-com-mid.png
sleep 1.2; $U shot $S/c3-example-com.png
echo "--- field text:"; $U dump | rg -i 'example'
# uppercase / no autocorrect
$A shell input keyevent KEYCODE_MOVE_HOME
for i in 1 2 3 4 5 6 7 8 9 10 11; do $A shell input keyevent KEYCODE_DEL; done
$A shell input keyevent 67
$A shell input text 'Example.Com/Path'; sleep 1
echo "--- typed Example.Com/Path:"; $U dump | rg -i 'example'
$U shot $S/c4-mixedcase.png
# starred site
for i in $(seq 1 30); do $A shell input keyevent KEYCODE_DEL; done
$A shell input text 'github.com/gorhill/uBlock'; sleep 1.5
$U shot $S/c5-starred.png
$U dump | head -40
echo "--- crashes"; $A logcat -d -b crash | head
