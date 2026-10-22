U="python3 /home/omega/.cache/claude-scratch/fmhy-polish/ui.py"
export ADB_SERIAL=emulator-5554
A="adb -s emulator-5554"; S=.work-scripts/shots
$A logcat -c
$A shell am force-stop io.github.omeg4dev.fmhy
$A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null; sleep 4
$U tap '^Check a link$'; sleep 1.5
$U dump | rg -i 'recent|example|github' | head
$U shot $S/h1-history.png
$U tap '^Clear input$'; sleep .5
$A shell input text 'github.com/gorhill/uBlock'; sleep 1.8
$U shot $S/c5-starred.png
$U dump | rg -i 'found|›|gorhill|ublock' | head
$A logcat -d -b crash | head -3
