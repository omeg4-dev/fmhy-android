set -x
U="python3 /home/omega/.cache/claude-scratch/fmhy-polish/ui.py"
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"
$A shell am force-stop io.github.omeg4dev.fmhy
$A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null
sleep 4
$U dump | head -60
