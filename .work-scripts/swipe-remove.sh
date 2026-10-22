export ADB_SERIAL=emulator-5554; U=/home/omega/.cache/claude-scratch/fmhy-polish/ui.py
A="adb -s emulator-5554"; DB=/data/data/io.github.omeg4dev.fmhy/files/user/user.db
$A shell am force-stop io.github.omeg4dev.fmhy; $A shell am start -n io.github.omeg4dev.fmhy/.MainActivity >/dev/null; sleep 5
$A shell input tap 953 2274; sleep 2
python3 $U shot /home/omega/Projects/fmhy-android-p9-shell/.work-scripts/shots/n6-saved.png; python3 $U dump | sed -n 1,12p
Y=${Y:-700}
for x in 950 850 750 650 550 450 350 250 150; do $A shell input motionevent $([ $x = 950 ] && echo DOWN || echo MOVE) $x $Y; done
$A shell "n0=\$(sqlite3 $DB 'select count(*) from bookmarks'); input motionevent UP 150 $Y; t1=\$(date +%s%N); while :; do c=\$(sqlite3 $DB 'select count(*) from bookmarks'); [ \$c != \$n0 ] && break; n=\$(( (\$(date +%s%N)-t1)/1000000 )); [ \$n -gt 2000 ] && break; done; echo before=\$n0 after=\$c ms=\$(( (\$(date +%s%N)-t1)/1000000 ))"
