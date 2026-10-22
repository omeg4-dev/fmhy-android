U="python3 /home/omega/.cache/claude-scratch/fmhy-polish/ui.py"; export ADB_SERIAL=emulator-5554
adb -s emulator-5554 shell am force-stop io.github.omeg4dev.fmhy
adb -s emulator-5554 shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null; sleep 4
$U dump | rg -i "new|change" 
