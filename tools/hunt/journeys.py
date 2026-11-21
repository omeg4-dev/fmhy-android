#!/usr/bin/env python3
"""Scripted user journeys on the emulator (online; real sites allowed).

Each journey is a function with explicit asserts on visible text. StrictMode is forced on
(debug.fmhy.strict=1, previous value restored). Per journey: pass/fail, failed assert, crashscan
delta (fatal/ANR fail the journey; StrictMode violations are only counted), and a small
screenshot path on failure. Every device setting a journey changes is restored in `finally`.

  journeys.py [--only NAME[,NAME]] [--list] [--out PATH]

Exit 0 = all passed, 1 = at least one failed.
"""
from __future__ import annotations

import argparse
import re
import sys
import time
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import crashscan
import huntlib
from huntlib import PKG, adb, sh

LICENSE_URL = "https://raw.githubusercontent.com/omeg4-dev/fmhy-android/main/LICENSE"
SECTION = "Reading"          # a long wiki section that is always on Home
ENTRY = "Anna's Archive"     # an entry in SECTION, found by searching QUERY
QUERY = "anna"


class Fail(Exception):
    pass


# ---------------------------------------------------------------- UI helpers

def nodes():
    return [n for n in huntlib.emu().dump_nodes() if n.useful]


def _label(n) -> str:
    return n.text or n.desc or n.summary


def find(label: str, ns=None, desc_only: bool = False, nth: int = 1):
    """Exact text/desc first, then case-insensitive, then substring (text, desc, {summary})."""
    ns = nodes() if ns is None else ns
    low = label.lower()
    fields = (lambda n: [n.desc]) if desc_only else (lambda n: [n.text, n.desc])
    tiers = (
        lambda v: v == label,
        lambda v: v.lower() == low,
        lambda v: low in v.lower(),
    )
    for t in tiers:
        hit = [n for n in ns if any(v and t(v) for v in fields(n))]
        if len(hit) >= nth:
            return hit[nth - 1]
    if not desc_only:
        hit = [n for n in ns if low in n.summary.lower()]
        if len(hit) >= nth:
            return hit[nth - 1]
    return None


def has(label: str, ns=None) -> bool:
    return find(label, ns) is not None


def closest(ns, label: str) -> str:
    return " · ".join(f'"{l}"' for l in huntlib.emu().closest(ns, label)[:5])


def tap_xy(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap(label: str, desc: bool = False, nth: int = 1, settle: float = 1.0):
    ns = nodes()
    n = find(label, ns, desc_only=desc, nth=nth)
    if not n:
        raise Fail(f'tap: "{label}" not on screen; closest: {closest(ns, label)}')
    tap_xy(n.x, n.y)
    time.sleep(settle)
    return n


def allow_permission(ns) -> bool:
    for n in ns:
        if n.rid == "permission_allow_button":
            tap_xy(n.x, n.y)
            time.sleep(1)
            return True
    return False


def wait(label: str, timeout: float = 15, desc: bool = False, gone: bool = False):
    end = time.monotonic() + timeout
    while True:
        ns = nodes()
        if allow_permission(ns):
            continue
        hit = find(label, ns, desc_only=desc)
        if (hit is not None) != gone:
            return ns
        if time.monotonic() >= end:
            what = "still on screen" if gone else "not on screen"
            raise Fail(f'wait: "{label}" {what} after {timeout:g}s; closest: {closest(ns, label)}')
        time.sleep(0.4)


def tap_bar(desc: str, settle: float = 1.0) -> None:
    """Tap a top-bar icon by content-desc (list rows may reuse the same desc, e.g. "More")."""
    ns = nodes()
    n = next((n for n in ns if n.desc == desc and n.y < 320), None)
    if not n:
        raise Fail(f'top-bar button "{desc}" not on screen; closest: {closest(ns, desc)}')
    tap_xy(n.x, n.y)
    time.sleep(settle)


def check(cond: bool, msg: str) -> None:
    if not cond:
        raise Fail(msg)


def long_press(x: int, y: int, ms: int = 1200) -> None:
    # `input swipe x y x y ms` is read as a tap by Compose here; raw motion events are not.
    sh(f"input motionevent DOWN {x} {y}; sleep {ms / 1000:.1f}; input motionevent UP {x} {y}")
    time.sleep(1)


def type_text(s: str) -> None:
    adb("shell", "input", "text", s.replace(" ", "%s").replace("'", "\\'"))
    time.sleep(0.8)


def key(k: str, settle: float = 1.0) -> None:
    adb("shell", "input", "keyevent", k)
    time.sleep(settle)


def swipe_up(times: int = 1, dur: int = 150) -> None:
    for _ in range(times):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", str(dur))


def title_is(label: str, ns=None) -> bool:
    ns = nodes() if ns is None else ns
    return any(n.text == label and n.y < 320 for n in ns)


def screen_key(ns) -> tuple:
    return tuple((_label(n), n.y) for n in ns)


def start_app() -> None:
    r = adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    if "Events injected: 1" not in r.stdout + r.stderr:
        raise Fail(f"launch failed: {(r.stdout + r.stderr).strip()[-200:]}")


def launch_home() -> None:
    """Cold start to Home; skips onboarding if it shows."""
    adb("shell", "am", "force-stop", PKG)
    start_app()
    end = time.monotonic() + 30
    while time.monotonic() < end:
        ns = nodes()
        if allow_permission(ns):
            continue
        if has("Get started", ns) and has("Skip", ns):
            tap("Skip")
            continue
        if has("Wiki", ns) and find("Settings", ns, desc_only=True):
            return
        time.sleep(0.5)
    raise Fail("launch_home: Home (Wiki + Settings) not reached in 30s")


def tab_count(ns=None) -> int:
    ns = nodes() if ns is None else ns
    c = tab_counter(ns)
    return int((c.text or c.summary).strip("{}"))


def tab_counter(ns):
    for n in ns:
        if n.y < 320 and "long" in n.flags and re.fullmatch(r"\{?\d+\}?", n.text or n.summary):
            return n
    raise Fail("tab counter not found in the browser bar")


def open_section(name: str = SECTION) -> None:
    """Tap a wiki section on Home; scrolls Home a little if a banner pushed it below the fold."""
    for _ in range(5):
        n = find(name)
        if n and n.y < 2150:  # above the bottom navigation bar
            break
        adb("shell", "input", "swipe", "540", "1700", "540", "1100", "400")
        time.sleep(0.8)
    tap(name, settle=2)
    ns = wait(name)
    check(title_is(name, ns), f'section title "{name}" not in the top bar')


def search(q: str = QUERY) -> None:
    tap("Search", settle=1.5)
    wait("Search FMHY")
    type_text(q)
    wait("results", timeout=10)


# ---------------------------------------------------------------- device-state guards

class DeviceSetting:
    """settings put <ns> <key> <value>, restored on exit."""

    def __init__(self, ns: str, key_: str, value: str):
        self.ns, self.key, self.value, self.prev = ns, key_, value, None

    def __enter__(self):
        self.prev = sh(f"settings get {self.ns} {self.key}")
        sh(f"settings put {self.ns} {self.key} {self.value}")
        return self

    def __exit__(self, *exc):
        if self.prev in (None, "", "null"):
            sh(f"settings delete {self.ns} {self.key}")
        else:
            sh(f"settings put {self.ns} {self.key} {self.prev}")
        return False


class NightMode:
    def __enter__(self):
        out = sh("cmd uimode night")  # "Night mode: no|yes|auto|custom"
        self.prev = out.rsplit(":", 1)[-1].strip() or "no"
        sh("cmd uimode night yes")
        time.sleep(1)
        return self

    def __exit__(self, *exc):
        sh(f"cmd uimode night {self.prev if self.prev in ('yes', 'no', 'auto') else 'no'}")
        return False


# ---------------------------------------------------------------- journeys

JOURNEYS: dict[str, callable] = {}


def journey(fn):
    JOURNEYS[fn.__name__] = fn
    return fn


@journey
def onboarding():
    """pm clear, walk all onboarding pages, land on Home."""
    r = adb("shell", "pm", "clear", PKG)
    check("Success" in r.stdout, f"pm clear failed: {r.stdout.strip()} {r.stderr.strip()}")
    start_app()
    wait("Get started", timeout=20)
    tap("Get started", settle=1.5)
    for page in ("Make it yours", "Where should links open?", "Follow what you care about"):
        wait(page)
        tap("Next", settle=1.5)
    wait("You're set")
    end = time.monotonic() + 120
    while True:  # the button is disabled until the ad-block rules are saved
        ns = nodes()
        allow_permission(ns)
        btn = next((n for n in ns if n.summary == "{Start browsing FMHY}" and "click" in n.flags), None)
        if btn and "disabled" not in btn.flags and "click" in btn.flags:
            break
        check(time.monotonic() < end, '"Start browsing FMHY" still disabled after 120s')
        time.sleep(1)
    tap("Start browsing FMHY", settle=2)
    ns = wait("Wiki", timeout=20)
    check(find("Settings", ns, desc_only=True) is not None, "Home has no Settings button")
    check(any(re.match(r"Search [\d,]+ sites", n.text) for n in ns), 'Home search bar "Search N sites" missing')


@journey
def section_scroll():
    """Open a long wiki section and scroll to its end."""
    launch_home()
    open_section()
    prev, stable = None, 0
    for _ in range(30):
        swipe_up(5)
        time.sleep(0.6)
        cur = screen_key(nodes())
        stable = stable + 1 if cur == prev else 0
        if stable >= 1:
            break
        prev = cur
    check(stable >= 1, "section never stopped scrolling after 150 swipes")
    check(title_is(SECTION), f'section title "{SECTION}" lost after scrolling to the end')


@journey
def search_to_section():
    """Search, long-press a result, 'Show in section' opens the entry's section."""
    launch_home()
    search()
    ns = nodes()
    row = next((n for n in ns if "click" in n.flags and ENTRY in n.summary), None)
    check(row is not None, f'search result "{ENTRY}" not found')
    long_press(150, row.y)
    wait("Show in section")
    tap("Show in section", settle=2)
    ns = wait(ENTRY)
    check(title_is(SECTION, ns), f'"Show in section" did not open "{SECTION}"')


@journey
def browser_tabs():
    """Open an entry in the in-app browser, add a second tab, open the tab sheet, close the tab."""
    launch_home()
    search()
    tap(ENTRY, settle=3)
    ns = wait("Back to FMHY", desc=True)
    n0 = tab_count(ns)
    tap_bar("Menu")
    tap("New tab")
    ns = wait("Search or type a link")
    check(tab_count(ns) == n0 + 1, f"tab count {tab_count(ns)} after New tab, expected {n0 + 1}")
    c = tab_counter(ns)
    tap_xy(c.x, c.y)
    ns = wait(f"Tabs · {n0 + 1}")
    row = next((n for n in ns if n.summary.startswith("{New tab · Current")), None)
    check(row is not None, "current New tab row missing in the tab sheet")
    close = next((n for n in ns if n.desc == "Close tab" and abs(n.y - row.y) < 40), None)
    check(close is not None, "Close button of the current tab missing")
    tap_xy(close.x, close.y)
    time.sleep(1.5)
    ns = nodes()
    if find("Close tab overview", ns, desc_only=True):
        check(has(f"Tabs · {n0}", ns), f'tab sheet does not show "Tabs · {n0}" after closing one')
        tap("Close tab overview", desc=True)
    ns = wait("Back to FMHY", desc=True)
    check(tab_count(ns) == n0, f"tab count {tab_count(ns)} after closing, expected {n0}")


def _downloads_done(ns) -> int | None:
    for n in ns:
        m = re.fullmatch(r"Done (\d+)", n.text)
        if m:
            return int(m.group(1))
    return None


def _open_downloads():
    tap_bar("Menu", settle=1.2)
    tap("Downloads", settle=1.5)
    return wait("Done", timeout=8)


@journey
def download_file():
    """Download a small text file via 'Paste a link' and see it in the download manager."""
    before = set(sh("ls /sdcard/Download/").splitlines())
    try:
        launch_home()
        tap("Browser", settle=2)
        wait("Search or type a link")
        ns = _open_downloads()
        d0 = _downloads_done(ns)
        check(d0 is not None, 'downloads sheet has no "Done N" filter')
        tap_bar("More")
        tap("Paste a link")
        type_text(LICENSE_URL)
        tap("Download")  # dialog button (exact match beats the "Paste a link" title)
        wait("Download file", timeout=15)
        tap("Download", settle=2)
        end = time.monotonic() + 40
        while True:
            ns = nodes()
            if allow_permission(ns):
                continue
            if _downloads_done(ns) is None and find("Menu", ns, desc_only=True):
                ns = _open_downloads()  # the sheet closes after the confirm on some paths
            done = _downloads_done(ns)
            if done is not None and done >= d0 + 1 and any(n.text.startswith("LICENSE") for n in ns):
                break
            check(time.monotonic() < end, f"download not listed as done after 40s (Done {done}, was {d0})")
            time.sleep(1.5)
    finally:
        for f in set(sh("ls /sdcard/Download/").splitlines()) - before:
            if f.startswith("LICENSE"):
                sh(f"rm '/sdcard/Download/{f}'")


SETTINGS_TOGGLES = [  # (group row, switch row label) from different settings groups
    ("Lists & gestures", "Show indexes"),
    ("Video", "Double-tap to seek"),
    ("Downloads", "Show FMHY status in downloads"),
    ("Protection", "Block links that open apps"),
    ("Browser", "Dead-site rescue"),
]


def _switch_row(label: str):
    for _ in range(4):
        ns = nodes()
        row = next((n for n in ns if n.summary.startswith("{" + label) and
                    ("checked" in n.flags or "unchecked" in n.flags)), None)
        if row and 330 < row.y < 2150:
            return row
        if row and row.y <= 330:  # clipped under the top bar: scroll back
            sh("input swipe 540 800 540 1500 300")
            time.sleep(0.8)
            continue
        swipe_up(1, 400)
        time.sleep(0.8)
    raise Fail(f'switch "{label}" not found')


def _open_settings():
    tap("Settings", desc=True, settle=1.5)
    wait("Look & feel")


def _toggle_all(expect_from: dict[str, bool] | None) -> dict[str, bool]:
    """Toggle each switch once; return the states after toggling. Checks the starting state if given."""
    after = {}
    for group, label in SETTINGS_TOGGLES:
        tap(group, settle=1.5)
        row = _switch_row(label)
        was = "checked" in row.flags
        if expect_from is not None:
            check(was == expect_from[label], f'"{label}" is {was} after restart, expected {expect_from[label]}')
        tap_xy(row.x, row.y)
        time.sleep(0.8)
        now = "checked" in _switch_row(label).flags
        check(now != was, f'"{label}" did not toggle')
        after[label] = now
        key("KEYCODE_BACK")
        wait("Look & feel")
    return after


@journey
def settings_persist():
    """Toggle 5 settings in different groups, force-stop, reopen: values kept; then restore."""
    launch_home()
    _open_settings()
    toggled = _toggle_all(None)
    launch_home()  # force-stop + cold start
    _open_settings()
    # toggles again, which also restores the original values; first verifies they survived the restart
    restored = _toggle_all(toggled)
    check(all(restored[k] != toggled[k] for k in toggled), "settings not restored")


@journey
def rotate_screens():
    """Rotate to landscape and back on a section, search and browser screen; state survives."""
    with DeviceSetting("system", "accelerometer_rotation", "0"), DeviceSetting("system", "user_rotation", "0"):
        def turn(r: int):
            sh(f"settings put system user_rotation {r}")
            time.sleep(2.5)

        launch_home()
        open_section()
        turn(1)
        check(title_is(SECTION), f'section title "{SECTION}" gone in landscape')
        turn(0)
        check(title_is(SECTION), f'section title "{SECTION}" gone after rotating back')

        launch_home()
        search()
        turn(1)
        ns = wait("results")
        check(any(QUERY in n.text for n in ns if "edit" in n.flags), "search query lost in landscape")
        turn(0)
        ns = wait("results")
        check(any(QUERY in n.text for n in ns if "edit" in n.flags), "search query lost after rotating back")

        launch_home()
        tap("Browser", settle=2)
        tap("Search or type a link", settle=1)
        type_text(LICENSE_URL)
        key("KEYCODE_ENTER", settle=4)
        ns = wait("raw.githubusercontent.com")
        n0 = tab_count(ns)
        turn(1)
        ns = wait("raw.githubusercontent.com")
        check(tab_count(ns) == n0, "tab count changed in landscape")
        turn(0)
        ns = wait("raw.githubusercontent.com")
        check(tab_count(ns) == n0, "tab count changed after rotating back")


@journey
def process_death():
    """Background the app, kill its process, relaunch: same screen and scroll position."""
    launch_home()
    open_section()
    swipe_up(3)
    time.sleep(1)
    ns = nodes()
    anchors = [n.text for n in ns if n.text and 450 < n.y < 1500 and n.text != SECTION][:3]
    check(bool(anchors), "no entry text visible before the kill")
    pid0 = sh(f"pidof {PKG}")
    key("KEYCODE_HOME", settle=2)
    sh(f"am kill {PKG}")
    time.sleep(2)
    pid_mid = sh(f"pidof {PKG}")
    check(pid_mid == "", f"am kill did not kill the process (pid {pid_mid}); a foreground service may hold it")
    start_app()  # launcher intent brings the existing task back (like recents)
    ns = wait(SECTION, timeout=20)
    check(sh(f"pidof {PKG}") not in ("", pid0), "process was not restarted")
    check(title_is(SECTION, ns), f'after process death the app did not return to "{SECTION}"')
    seen = [a for a in anchors if has(a, ns)]
    check(bool(seen), f"scroll position lost after process death (none of {anchors} visible)")


def _mean_luma() -> float:
    import io
    import subprocess

    from PIL import Image, ImageStat
    raw = subprocess.run(["adb", "-s", huntlib.emu().SERIAL, "exec-out", "screencap", "-p"],
                         capture_output=True, timeout=30).stdout
    img = Image.open(io.BytesIO(raw)).convert("L").resize((108, 240))
    return ImageStat.Stat(img).mean[0]


@journey
def dark_font_scale():
    """Dark mode + font scale 1.3 on Home and Settings."""
    with NightMode(), DeviceSetting("system", "font_scale", "1.3"):
        launch_home()
        ns = nodes()
        check(has("Wiki", ns) and has("Search", ns), "Home labels missing at font scale 1.3")
        luma = _mean_luma()
        check(luma < 110, f"Home is not dark in night mode (mean luma {luma:.0f})")
        _open_settings()
        ns = nodes()
        for lbl in ("Look & feel", "Appearance", "Browsing"):
            check(has(lbl, ns), f'Settings label "{lbl}" missing at font scale 1.3')
        luma = _mean_luma()
        check(luma < 110, f"Settings is not dark in night mode (mean luma {luma:.0f})")
    launch_home()  # leave the app in the restored configuration


@journey
def whats_new():
    """What's new (Recent changes): open, open a filter, close; also via the shortcut intent."""
    launch_home()
    tap("Recent changes", desc=True, settle=2)
    wait("All changes")
    tap("Added to FMHY", settle=2)
    ns = wait("Search changes")
    check(any(re.fullmatch(r"\d+ new", n.text) for n in ns), 'filtered list has no "N new" chip')
    key("KEYCODE_BACK")
    key("KEYCODE_BACK")
    wait("Wiki")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "open", "whats_new")
    wait("Search changes", timeout=20)
    key("KEYCODE_BACK")
    ns = wait("Wiki")
    check(find("Settings", ns, desc_only=True) is not None, "Back from What's new did not land on Home")


# ---------------------------------------------------------------- runner

def shot(name: str) -> str | None:
    try:
        out = huntlib.OUT_DIR / "shots" / f"{name}-{huntlib.stamp()}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        import contextlib
        import io
        with contextlib.redirect_stdout(io.StringIO()):
            huntlib.emu().cmd_shot(argparse.Namespace(crop=None, width=360, out=str(out)))
        return huntlib.rel(out)
    except Exception:  # a failed screenshot must not hide the real failure
        return None


def run_one(name: str) -> dict:
    crashscan.clear()
    t0 = time.monotonic()
    res = {"name": name, "ok": True, "error": None}
    try:
        JOURNEYS[name]()
    except Fail as e:
        res.update(ok=False, error=str(e))
    except Exception as e:  # harness bug or adb trouble; still a failed journey
        res.update(ok=False, error=f"{type(e).__name__}: {e}", trace=traceback.format_exc(limit=4))
    time.sleep(1)
    found = crashscan.scan()
    hard = [g for g in found if g["kind"] in ("fatal", "anr")]
    if hard and res["ok"]:
        res.update(ok=False, error=f"{hard[0]['kind']}: {hard[0]['signature']}")
    res["crashes"] = hard
    res["strictmode"] = [{"signature": g["signature"], "count": g["count"]} for g in found if g["kind"] == "strictmode"]
    res["secs"] = round(time.monotonic() - t0, 1)
    if not res["ok"]:
        res["shot"] = shot(name)
    return res


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", help="comma-separated journey names")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--out", help="JSON path (default .cache/hunt/journeys-<ts>.json)")
    a = ap.parse_args()
    if a.list:
        for name, fn in JOURNEYS.items():
            print(f"{name:20} {fn.__doc__.strip().splitlines()[0]}")
        return 0
    names = a.only.split(",") if a.only else list(JOURNEYS)
    bad = [n for n in names if n not in JOURNEYS]
    if bad:
        huntlib.eprint(f"unknown journey(s): {bad}; see --list")
        return 2
    if sh("settings get global airplane_mode_on") == "1":
        huntlib.eprint("journeys need the network: airplane mode is on")
        return 2

    prev_strict = sh("getprop debug.fmhy.strict")
    sh("setprop debug.fmhy.strict 1")
    results = []
    try:
        for name in names:
            r = run_one(name)
            results.append(r)
            sm = sum(s["count"] for s in r["strictmode"])
            line = f"{'PASS' if r['ok'] else 'FAIL'} {name:20} {r['secs']:>5}s  strictmode {sm}"
            if not r["ok"]:
                line += f"\n     {r['error']}" + (f"\n     shot: {r['shot']}" if r.get("shot") else "")
            print(line, flush=True)
    finally:
        sh(f"setprop debug.fmhy.strict '{prev_strict}'")
    out = huntlib.out_path("journeys", a.out)
    version = re.search(r"versionName=(\S+)", sh(f"dumpsys package {PKG} | grep -m1 versionName"))
    huntlib.write_json(out, {
        "device": huntlib.emu().SERIAL,
        "version": version.group(1) if version else None,
        "passed": sum(r["ok"] for r in results),
        "failed": sum(not r["ok"] for r in results),
        "journeys": results,
    })
    print(f"{sum(r['ok'] for r in results)}/{len(results)} passed · json: {huntlib.rel(out)}")
    return 0 if all(r["ok"] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
