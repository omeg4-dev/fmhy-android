#!/usr/bin/env python3
"""Perf probes that make a perf finding count (a measurement, not a feeling).

  perf.py startup [--runs 7]                  cold start `am start -W`, median/max TotalTime (ms)
  perf.py jank home|section|search [--swipes 12]   gfxinfo janky %, p50/p90/p99 frame ms after a scripted scroll
  perf.py mem [--minutes 5]                   PSS after start vs after a journey loop (leak hint)
  common: [--save-baseline] [--out PATH]

Results are compared with .claude/skills/bug-hunt/perf-baseline.json when it exists;
--save-baseline writes this run's numbers into it (per measure, other entries kept).
Emulator caveat: swiftshader is not a phone. Compare only before/after on the same AVD and boot mode;
jank numbers need the emulator booted with `-gpu host`.
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import huntlib
import journeys as J
from huntlib import PKG, adb, sh

BASELINE = huntlib.REPO / ".claude" / "skills" / "bug-hunt" / "perf-baseline.json"
CAVEAT = ("caveat: emulator numbers (swiftshader != phone). Compare only before/after on the same AVD "
          "and boot mode; jank needs `-gpu host`.")
# All metrics are "higher is worse"; a delta above this % is flagged.
FLAG_PCT = 10.0


def launcher_component() -> str:
    out = sh(f"cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {PKG}")
    comp = out.splitlines()[-1].strip() if out else ""
    if "/" not in comp:
        raise SystemExit(f"perf: cannot resolve the launcher activity: {out[:200]}")
    return comp


# ---------------------------------------------------------------- startup

def measure_startup(runs: int) -> dict:
    comp = launcher_component()
    times: list[int] = []
    for i in range(runs + 1):  # first run warms the page cache / dex; dropped
        adb("shell", "am", "force-stop", PKG)
        time.sleep(1.5)
        out = adb("shell", "am", "start", "-W", "-n", comp, timeout=60).stdout
        m = re.search(r"TotalTime:\s*(\d+)", out)
        if not m:
            raise SystemExit(f"perf: no TotalTime in `am start -W` output: {out.strip()[-300:]}")
        if i:
            times.append(int(m.group(1)))
        time.sleep(3)
    return {"runs": runs, "times_ms": times, "median_ms": statistics.median(times), "max_ms": max(times)}


# ---------------------------------------------------------------- jank

def parse_gfxinfo(out: str) -> dict:
    def num(rx: str) -> float | None:
        m = re.search(rx, out)
        return float(m.group(1)) if m else None

    return {
        "frames": num(r"Total frames rendered:\s*(\d+)"),
        "janky_pct": num(r"Janky frames:\s*\d+\s*\(([\d.]+)%\)"),
        "p50_ms": num(r"50th percentile:\s*(\d+)ms"),
        "p90_ms": num(r"90th percentile:\s*(\d+)ms"),
        "p99_ms": num(r"99th percentile:\s*(\d+)ms"),
    }


def scroll(swipes: int) -> None:
    for i in range(swipes):
        if i % 4 == 3:  # mix in a fling back up so lists keep content to render
            adb("shell", "input", "swipe", "540", "700", "540", "1800", "120")
        else:
            adb("shell", "input", "swipe", "540", "1800", "540", "600", "180")
        time.sleep(0.5)


def measure_jank(screen: str, swipes: int) -> dict:
    J.launch_home()
    if screen == "section":
        J.open_section()
    elif screen == "search":
        J.search("music")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")  # hide the keyboard, keep the results
        time.sleep(1)
    time.sleep(2)
    sh(f"dumpsys gfxinfo {PKG} reset")
    scroll(swipes)
    time.sleep(1)
    res = parse_gfxinfo(sh(f"dumpsys gfxinfo {PKG}"))
    if not res["frames"]:
        raise SystemExit("perf: gfxinfo recorded no frames; is the app in front?")
    return {"screen": screen, "swipes": swipes, **res}


# ---------------------------------------------------------------- memory

def pss_kb() -> int:
    out = sh(f"dumpsys meminfo {PKG}")
    m = re.search(r"TOTAL PSS:\s*(\d+)", out) or re.search(r"^\s*TOTAL\s+(\d+)", out, re.M)
    if not m:
        raise SystemExit(f"perf: no PSS in dumpsys meminfo: {out[:200]}")
    return int(m.group(1))


def journey_loop_once() -> None:
    J.open_section()
    J.swipe_up(6)
    J.key("KEYCODE_BACK")
    J.wait("Wiki")
    J.search("music")
    J.swipe_up(3)
    J.key("KEYCODE_BACK")
    J.key("KEYCODE_BACK")
    J.wait("Wiki")
    J.tap("Recent changes", desc=True, settle=1.5)
    J.wait("All changes")
    J.key("KEYCODE_BACK")
    J.wait("Wiki")


def measure_mem(minutes: float) -> dict:
    J.launch_home()
    time.sleep(5)
    start = pss_kb()
    end_t = time.monotonic() + minutes * 60
    loops = 0
    while time.monotonic() < end_t:
        journey_loop_once()
        loops += 1
    time.sleep(3)
    after = pss_kb()
    return {"minutes": minutes, "loops": loops, "pss_start_kb": start, "pss_after_kb": after,
            "growth_kb": after - start, "growth_pct": round(100 * (after - start) / start, 1)}


# ---------------------------------------------------------------- baseline

def baseline_key(measure: str, args) -> str:
    return f"jank_{args.screen}" if measure == "jank" else measure


METRICS = {"startup": ["median_ms", "max_ms"], "jank": ["janky_pct", "p90_ms", "p99_ms"],
           "mem": ["pss_after_kb", "growth_kb"]}


def compare(measure: str, key: str, res: dict) -> dict:
    if not BASELINE.exists():
        return {}
    base = json.loads(BASELINE.read_text()).get(key)
    if not base:
        return {}
    out = {}
    for m in METRICS[measure]:
        b, v = base.get(m), res.get(m)
        if b and v is not None:
            out[m] = {"baseline": b, "now": v, "delta_pct": round(100 * (v - b) / b, 1)}
    return out


def save_baseline(key: str, res: dict) -> None:
    BASELINE.parent.mkdir(parents=True, exist_ok=True)
    data = json.loads(BASELINE.read_text()) if BASELINE.exists() else {}
    data[key] = {**res, "saved": time.strftime("%Y-%m-%d %H:%M"), "device": huntlib.emu().SERIAL}
    BASELINE.write_text(json.dumps(data, indent=1) + "\n")


def human(measure: str, res: dict) -> str:
    if measure == "startup":
        return f"startup: median {res['median_ms']:.0f} ms, max {res['max_ms']} ms over {res['runs']} cold starts {res['times_ms']}"
    if measure == "jank":
        return (f"jank {res['screen']}: {res['janky_pct']}% janky of {res['frames']:.0f} frames, "
                f"p50 {res['p50_ms']:.0f} / p90 {res['p90_ms']:.0f} / p99 {res['p99_ms']:.0f} ms")
    return (f"mem: PSS {res['pss_start_kb'] // 1024} MB after start -> {res['pss_after_kb'] // 1024} MB after "
            f"{res['loops']} loops / {res['minutes']:g} min ({res['growth_pct']:+}%)")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="measure", required=True)
    s = sub.add_parser("startup"); s.add_argument("--runs", type=int, default=7)
    s = sub.add_parser("jank"); s.add_argument("screen", choices=["home", "section", "search"])
    s.add_argument("--swipes", type=int, default=12)
    s = sub.add_parser("mem"); s.add_argument("--minutes", type=float, default=5)
    for p in sub.choices.values():
        p.add_argument("--save-baseline", action="store_true")
        p.add_argument("--out")
    a = ap.parse_args()

    try:
        if a.measure == "startup":
            res = measure_startup(a.runs)
        elif a.measure == "jank":
            res = measure_jank(a.screen, a.swipes)
        else:
            res = measure_mem(a.minutes)
    except J.Fail as e:
        huntlib.eprint(f"perf: scripted navigation failed: {e}")
        return 1
    key = baseline_key(a.measure, a)
    cmp_ = compare(a.measure, key, res)
    print(human(a.measure, res))
    for m, d in cmp_.items():
        mark = "  <-- worse" if d["delta_pct"] > FLAG_PCT else ""
        print(f"  vs baseline {m}: {d['baseline']} -> {d['now']} ({d['delta_pct']:+}%){mark}")
    if not cmp_:
        print(f"  (no baseline for {key}; --save-baseline writes {huntlib.rel(BASELINE)})")
    if a.measure == "mem" and res["growth_pct"] > 25:
        print("  leak hint: PSS grew more than 25% during the loop")
    if a.save_baseline:
        save_baseline(key, res)
        print(f"  baseline saved: {huntlib.rel(BASELINE)} [{key}]")
    print(CAVEAT)
    out = huntlib.out_path(f"perf-{key}", a.out)
    huntlib.write_json(out, {"measure": key, "result": res, "baseline_compare": cmp_, "caveat": CAVEAT})
    print(f"json: {huntlib.rel(out)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
