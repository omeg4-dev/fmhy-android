export ADB_SERIAL=emulator-5554; W=/home/omega/Projects/fmhy-android-p9-shell/.work-scripts
A="adb -s emulator-5554"
for i in 1 2 3 4; do $A shell input swipe 540 500 540 2000 120; sleep 0.5; done
$A shell input swipe 540 1700 540 1000 400; sleep 1
python3 $W/swipe-check.py '^Report Issues$'
