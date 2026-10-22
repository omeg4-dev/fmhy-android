#!/usr/bin/env python3
"""Swipe-to-save check on the emulator. Usage: swipe-check.py ROW_LABEL_REGEX
Asserts: nothing changes while the finger is down; one change after release (right->left past threshold);
no change for left->right, short, and past-then-back swipes. Prints FMHY vibrations per phase."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = ["adb", "-s", "emulator-5554"]
DB = "/data/data/io.github.omeg4dev.fmhy/files/user/user.db"


def sh(*a):
    return subprocess.run(ADB + ["shell", *a], capture_output=True, text=True).stdout


def saved():
    return set(sh(f"sqlite3 {DB} 'select entry_id from bookmarks'").split())


def vibes():
    out = sh("dumpsys vibrator_manager")
    return [l for l in out.splitlines() if "io.github.omeg4dev.fmhy" in l and "|" in l]


def row_y(rx):
    sh("uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh("cat", "/sdcard/ui.xml")
    for n in ET.fromstring(xml[xml.index("<"):]).iter("node"):
        if re.search(rx, n.get("text") or ""):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            return (y1 + y2) // 2
    sys.exit(f"row {rx!r} not on screen")


def ev(kind, x, y):
    sh("input", "motionevent", kind, str(x), str(y))


def drag(y, xs, release=True):
    ev("DOWN", xs[0], y)
    for x in xs[1:]:
        ev("MOVE", x, y)
    if release:
        ev("UP", xs[-1], y)


def timed_up(x, y, before):
    """Inject UP and poll the bookmarks table on-device; returns ms from UP injection until the table changed."""
    q = "select group_concat(entry_id) from (select entry_id from bookmarks order by entry_id)"
    cmd = (f'b="{",".join(sorted(before))}"; input motionevent UP {x} {y}; t1=$(date +%s%N); '
           f'while :; do c=$(sqlite3 {DB} "{q}"); [ "$c" != "$b" ] && break; '
           f'n=$(( ($(date +%s%N)-t1)/1000000 )); [ $n -gt 2000 ] && break; done; echo $(( ($(date +%s%N)-t1)/1000000 ))')
    out = sh(cmd).strip().splitlines()
    return int(out[-1]) if out else -1


def phase(name, y, xs, expect_change):
    y = row_y(ROW)  # re-measure: a saved row is taller (inline bookmark icon)
    before, v0 = saved(), set(vibes())
    ev("DOWN", xs[0], y)
    for x in xs[1:]:
        ev("MOVE", x, y)
    mid = saved()
    ms = timed_up(xs[-1], y, before)
    time.sleep(1.5)
    after = saved()
    time.sleep(0.5)
    new_v = [l for l in vibes() if l not in v0]  # "Recent vibrations" is a fixed-size ring: diff, don't count
    changed = len(before ^ after)
    ok = mid == before and changed == (1 if expect_change else 0) and (not expect_change or 0 <= ms < 150)
    print(f"{'PASS' if ok else 'FAIL'} {name}: during-drag-unchanged={mid == before} changes-after-release={changed}"
          f" vibrations={len(new_v)} release->db={ms}ms")
    for l in new_v:
        print("      ", re.search(r"duration: +\d+ms", l).group(0), l.split("| played:")[-1].strip()[:120])
    return ok


def main():
    global ROW
    ROW = sys.argv[1]
    y = row_y(ROW)
    left = list(range(950, 150, -100))           # right -> left, ~800 px (row 1080 px, threshold 432 px)
    results = [
        phase("short swipe (200 px)", y, [950, 850, 750], False),
        phase("left->right swipe", y, list(range(150, 950, 100)), False),
        phase("past threshold then back", y, left + list(range(250, 950, 100)), False),
        phase("right->left past threshold", y, left, True),
        phase("right->left again (undo direction)", y, left, True),
    ]
    sys.exit(0 if all(results) else 1)


main()
