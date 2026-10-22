#!/usr/bin/env python3
"""Fling strip for N5. Usage: fling-check.py OUT_PREFIX
Assumes a long list is on screen (section screen or search results). Starts a fast fling and screencaps
back-to-back; writes OUT_PREFIX-strip.png (frames side by side) and prints, per frame, the share of list
rows that are plain background (blank bands = rows whose pixels are all ~ the background colour)."""
import io
import subprocess
import sys
import time

from PIL import Image

ADB = ["adb", "-s", "emulator-5554"]
prefix = sys.argv[1]


def shot():
    raw = subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True).stdout
    return Image.open(io.BytesIO(raw)).convert("RGB")


def blank_share(img):
    w, h = img.size
    top, bottom = 330, h - 330  # skip app bar and bottom bar
    bg = img.getpixel((4, (top + bottom) // 2))
    blank = 0
    rows = 0
    for y in range(top, bottom, 6):
        rows += 1
        row = [img.getpixel((x, y)) for x in range(40, w - 40, 40)]
        if all(max(abs(a - b) for a, b in zip(p, bg)) < 8 for p in row):
            blank += 1
    return blank / rows


frames = []
for rep in range(3):
    subprocess.Popen(ADB + ["shell", "input", "swipe", "540", "2000", "540", "300", "140"])
    t0 = time.time()
    for _ in range(6):
        frames.append((time.time() - t0, shot()))
    time.sleep(1.2)
    subprocess.run(ADB + ["shell", "input", "swipe", "540", "400", "540", "2000", "140"])  # back up
    time.sleep(1.2)

w, h = frames[0][1].size
sc = 0.25
cols = 9
tw, th = int(w * sc), int(h * sc)
rows = (len(frames) + cols - 1) // cols
strip = Image.new("RGB", (tw * cols, th * rows), "white")
worst = 0
for i, (t, img) in enumerate(frames):
    b = blank_share(img)
    worst = max(worst, b)
    print(f"frame {i:2d} t={t*1000:5.0f}ms blank-rows={b*100:4.1f}%")
    strip.paste(img.resize((tw, th)), ((i % cols) * tw, (i // cols) * th))
strip.save(f"{prefix}-strip.png")
print(f"worst blank share {worst*100:.1f}%  -> {prefix}-strip.png")
