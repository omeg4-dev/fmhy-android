export ADB_SERIAL=emulator-5554
UI=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"
SH=/home/omega/Projects/fmhy-android-p9-search/.work-scripts/shots
launch() { $A shell am force-stop io.github.omeg4dev.fmhy; $A shell monkey -p io.github.omeg4dev.fmhy -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6; for i in 1 2 3; do python3 $UI tap '^Search$' >/dev/null; sleep 1.5; python3 $UI has 'Search FMHY' && break; done; }
ty() { $A shell input text "$1"; sleep ${2:-0.8}; }
key() { $A shell input keyevent "$1"; sleep 0.5; }
shot() { python3 $UI shot "$SH/$1.png" >/dev/null; echo "shot $1"; }
tapt() { python3 $UI tap "$@"; sleep ${DELAY:-0.8}; }
clearq() { for i in $(seq 1 40); do $A shell input keyevent KEYCODE_DEL; done; sleep 0.5; }
