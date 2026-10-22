export ADB_SERIAL=emulator-5554; U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"; O=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots
$A shell input swipe 678 2226 678 2226 900; sleep 1.5
python3 $U shot $O/n1-menu-live.png; python3 $U dump | head -40
python3 $U tap '^Close all tabs$'; sleep 1; python3 $U shot $O/n1-close-all-dialog.png; python3 $U dump | rg -i "close all|cancel"
python3 $U tap '^Cancel$'; sleep 1; $A shell input keyevent KEYCODE_BACK; sleep 1
python3 $U dump | rg "Browser"
