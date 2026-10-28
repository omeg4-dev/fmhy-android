#!/usr/bin/env python3
"""Browser scroll flicker check (BUG-17) on the emulator.

The test page is a column of 20px colour blocks, each colour encodes the block index, so every recorded frame
tells exactly how far the page is scrolled. During scripted swipes the content must move one way only; a
"flicker event" is the content jumping more than JUMP_PX and coming back within a few frames.

Usage:
  scroll-flicker-check.py serve [PORT]     serve the test page (emulator URL: http://10.0.2.2:PORT/)
  scroll-flicker-check.py measure [LABEL]  open that page in the app's browser first, then run this
  scroll-flicker-check.py analyze DIR      re-run the analysis on a recorded run

Two kinds of flicker are counted: a jump (content leaps and comes back within a few frames) and a gap frame
(an empty strip between the status bar and the page: the WebView moved before the bar got there).
measure prints `flicker_events=N ...` and exits 1 when N > 0. Use a GPU emulator (`-gpu host`); with
swiftshader the recording drops to ~10 fps and one-frame flicker is lost.
"""
import http.server
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import numpy as np
from PIL import Image

BLOCKS = 1000
BLOCK_CSS_PX = 20
LEVELS = 16
STEP = 17  # 16 levels per channel: 0, 17, ..., 255; survives H.264 colour noise
JUMP_PX = 12
RETURN_FRAMES = 3
GAP_MIN_ROWS = 24  # a divider plus video blur is ~10 rows; a real gap is up to the bar height


def block_colour(i: int) -> tuple[int, int, int]:
    # Unique for i < 1024. Red is always >= 136, so no block looks like a dark or grey UI surface; neighbouring
    # blocks differ by at least 3 red levels, so a boundary is never a near-match.
    a, m, c = i % 8, (i // 8) % LEVELS, (i // 128) % 8
    r, g, b = 8 + 5 * a % 8, (m + 7 * a) % LEVELS, 2 * ((c + 3 * m) % 8)
    return r * STEP, g * STEP, b * STEP


def page_html() -> str:
    rows = "".join(
        '<div style="height:{}px;background:rgb({},{},{})"></div>'.format(BLOCK_CSS_PX, *block_colour(i))
        for i in range(BLOCKS)
    )
    return (
        '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">'
        "<title>flicker-check</title><style>body{margin:0}</style></head><body>" + rows + "</body></html>"
    )


def serve(port: int) -> None:
    root = Path(tempfile.mkdtemp(prefix="flicker-page-"))
    (root / "index.html").write_text(page_html())
    handler = lambda *a, **k: http.server.SimpleHTTPRequestHandler(*a, directory=str(root), **k)  # noqa: E731
    print(f"serving http://10.0.2.2:{port}/ (emulator) from {root}", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()


def adb(*args: str, check: bool = True) -> str:
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def decode_index(rgb: np.ndarray) -> np.ndarray:
    """Per-pixel colour -> block index, -1 where it is no block colour."""
    q = np.clip(np.rint(rgb / STEP), 0, LEVELS - 1).astype(int)
    keys = q[..., 0] * LEVELS * LEVELS + q[..., 1] * LEVELS + q[..., 2]
    lut = np.full(LEVELS**3, -1)
    for i in range(BLOCKS):
        r, g, b = block_colour(i)
        lut[(r // STEP) * LEVELS * LEVELS + (g // STEP) * LEVELS + b // STEP] = i
    idx = lut[keys]
    idx[np.abs(rgb - q * STEP).max(axis=-1) > STEP // 2 - 1] = -1
    return idx


def frame_offset(img: Image.Image, block_px: float, y0: int, y1: int) -> float | None:
    """Page scroll (device px) from one frame: the first clean block boundary inside rows y0..y1."""
    w = img.width
    strip = np.asarray(img.convert("RGB"), dtype=float)[y0:y1, w // 2 - 20 : w // 2 + 20].mean(axis=1)
    idx = decode_index(strip)
    for y in range(3, len(idx) - 3):
        a, b = idx[y - 1], idx[y]
        if a >= 0 and b == a + 1 and (idx[y - 3 : y] == a).all() and (idx[y : y + 3] == b).all():
            return b * block_px - (y0 + y)
    return None


def flicker_events(offsets: list[float]) -> list[int]:
    """Frame indexes where the content jumps > JUMP_PX and then moves back by about that much."""
    events = []
    for k in range(len(offsets) - 1):
        d = offsets[k + 1] - offsets[k]
        if abs(d) <= JUMP_PX:
            continue
        for j in range(k + 1, min(k + 1 + RETURN_FRAMES, len(offsets) - 1)):
            back = offsets[j + 1] - offsets[k + 1]
            if back * d < 0 and abs(back) >= abs(d) / 2:
                events.append(k)
                break
    return events


def gap_rows(img: Image.Image, status_h: int) -> int:
    """Rows between the status bar and the top of the page that show neither the bar nor the page: the empty
    window behind a WebView that moved before the bar arrived. The bar has the status bar's colour."""
    a = np.asarray(img.convert("RGB"), dtype=float)
    status = a[status_h // 2, a.shape[1] // 2]
    rows = a[status_h:]
    idx = decode_index(rows[:, a.shape[1] // 2 - 20 : a.shape[1] // 2 + 20].mean(axis=1))
    page_top = next((y for y in range(len(idx) - 3) if (idx[y : y + 3] >= 0).all() and idx[y] == idx[y + 2]), 0)
    band = rows[:page_top]
    uniform = band.std(axis=1).max(axis=1) < 4
    off_bar = np.abs(band.mean(axis=1) - status).max(axis=1) > 8
    return int((uniform & off_bar).sum())


def analyze(out: Path, label: str) -> int:
    density = int(adb("shell", "wm", "density").split(":")[-1])
    block_px = BLOCK_CSS_PX * density / 160
    status_h = round(24 * density / 160)
    frames = sorted(out.glob("f*.png"))
    h = Image.open(frames[0]).height
    # Middle band of the screen: clear of the bars at either edge.
    y0, y1 = int(h * 0.40), int(h * 0.60)
    offsets, gaps = [], []
    for n, f in enumerate(frames):
        img = Image.open(f)
        o = frame_offset(img, block_px, y0, y1)
        if o is not None:
            offsets.append(o)
        if gap_rows(img, status_h) >= GAP_MIN_ROWS:
            gaps.append(n + 1)  # file number (f00001.png = 1)
    events = flicker_events(offsets)
    (out / "offsets.txt").write_text("\n".join(f"{o:.1f}" for o in offsets))
    for k in events:
        print(f"  jump at frame {k}: {offsets[max(0, k - 1)]:.0f} -> {offsets[k]:.0f} -> {offsets[k + 1]:.0f} -> "
              f"{' -> '.join(f'{o:.0f}' for o in offsets[k + 2 : k + 2 + RETURN_FRAMES])}")
    if gaps:
        print(f"  gap frames: {gaps}")
    log = out / "bars.log"
    moves = sum("translationY" in line for line in log.read_text().splitlines()) if log.exists() else 0
    print(f"[{label}] flicker_events={len(events) + len(gaps)} (jumps={len(events)} gap_frames={len(gaps)}) "
          f"frames={len(offsets)}/{len(frames)} webview_moves={moves} out={out}")
    return 1 if events or gaps else 0


def measure(label: str) -> int:
    w = int(adb("shell", "wm", "size").split(":")[-1].split("x")[0])
    out = Path(tempfile.mkdtemp(prefix=f"flicker-{label}-"))
    x = w // 2
    adb("logcat", "-c")
    adb("shell", "rm", "-f", "/sdcard/flicker.mp4", check=False)
    rec = subprocess.Popen(["adb", "shell", "screenrecord", "--bit-rate", "20000000", "/sdcard/flicker.mp4"])
    time.sleep(1.5)
    # Page down: two slow drags, two flings. Then page up the same way. Pauses keep gestures apart.
    for y_from, y_to, ms in [(1900, 900, 900), (1900, 900, 900), (1900, 700, 150), (1900, 700, 150)]:
        adb("shell", "input", "swipe", str(x), str(y_from), str(x), str(y_to), str(ms))
        time.sleep(1.2)
    for y_from, y_to, ms in [(900, 1900, 900), (900, 1900, 900), (700, 1900, 150)]:
        adb("shell", "input", "swipe", str(x), str(y_from), str(x), str(y_to), str(ms))
        time.sleep(1.2)
    adb("shell", "pkill", "-INT", "screenrecord", check=False)
    rec.wait()
    time.sleep(1)
    adb("pull", "/sdcard/flicker.mp4", str(out / "flicker.mp4"))
    subprocess.run(
        # screenrecord only writes frames that changed: resample to a fixed rate so pauses between gestures
        # stay pauses and a gesture reversal never looks like a jump back.
        ["ffmpeg", "-loglevel", "fatal", "-i", str(out / "flicker.mp4"), "-vf", "fps=60", str(out / "f%05d.png")],
        check=True,
    )
    (out / "bars.log").write_text(adb("logcat", "-d", "-s", "FmhyBars"))
    return analyze(out, label)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "serve":
        serve(int(sys.argv[2]) if len(sys.argv) > 2 else 8765)
    elif cmd == "measure":
        sys.exit(measure(sys.argv[2] if len(sys.argv) > 2 else "run"))
    elif cmd == "analyze" and len(sys.argv) > 2:
        sys.exit(analyze(Path(sys.argv[2]), Path(sys.argv[2]).name))
    else:
        print(__doc__)
        sys.exit(2)
