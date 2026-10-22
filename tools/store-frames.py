#!/usr/bin/env python3
"""Marketing frames for the README / store: headline + subline over a dark logo-colour gradient,
the screenshot in a rounded phone frame that runs off the bottom edge. Usage: store-frames.py SHOTS_DIR OUT_DIR (raw 1080x2400 emulator captures named raw-*.png)"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS, OUT = Path(sys.argv[1]), Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
W, H = 1080, 1920
BOLD = "/usr/share/fonts/TTF/Roboto-Bold.ttf"
REG = "/usr/share/fonts/TTF/Roboto-Regular.ttf"

# Logo palette: cyan, violet, magenta, amber
GLOWS = [(0, 229, 255), (124, 92, 255), (255, 62, 201), (255, 193, 77)]

FRAMES = [
    # (screenshot, headline, subline, glow colours, fraction of the screen skipped from the top)
    ("raw-welcome.png", "Your FMHY,\nas an app", "21,000+ free sites · no account · no ads", (1, 0), 0),
    ("raw-search.png", "Find anything\nin a second", "Offline, typo-proof search with smart filters", (0, 1), 0),
    ("raw-section.png", "The best sites,\nstarred", "Every section, with FMHY's own top picks", (2, 3), 0),
    ("raw-browser.png", "Browse without\nthe junk", "Ad blocking + a live FMHY check on every page", (1, 2), 0),
    ("raw-check.png", "Check a link\nbefore you click", "See if a site is on FMHY, starred, or a fake", (3, 1), 0),
    ("raw-hub.png", "Never miss\nwhat changed", "Every new, starred and removed site, day by day", (0, 2), 0),
    ("raw-menu.png", "Your browser,\none tap away", "Tabs and private tabs, right in the bottom bar", (2, 0), 0.42),
    ("raw-look.png", "Make it\nyours", "Light, dark, Material You or any accent", (3, 0), 0.24),
]


def background(a, b):
    bg = Image.new("RGB", (W, H), (12, 10, 24))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-300, -350, 800, 700), fill=GLOWS[a])
    d.ellipse((450, 900, 1500, 2200), fill=GLOWS[b])
    glow = glow.filter(ImageFilter.GaussianBlur(260))
    return Image.blend(bg, glow, 0.38)


def rounded(img, r):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.width - 1, img.height - 1), r, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), m)
    return out


def frame(shot, title, sub, glows, skip):
    img = background(*glows).convert("RGBA")
    d = ImageDraw.Draw(img)
    tf, sf = ImageFont.truetype(BOLD, 104), ImageFont.truetype(REG, 44)
    y = 150
    for line in title.split("\n"):
        w = d.textlength(line, font=tf)
        d.text(((W - w) / 2, y), line, font=tf, fill=(255, 255, 255))
        y += 120
    y += 18
    w = d.textlength(sub, font=sf)
    if w > W - 120:  # wrap the subline onto two centred lines
        words, lines, cur = sub.split(), [], ""
        for word in words:
            t = (cur + " " + word).strip()
            if d.textlength(t, font=sf) > W - 160 and cur:
                lines.append(cur)
                cur = word
            else:
                cur = t
        lines.append(cur)
    else:
        lines = [sub]
    for line in lines:
        w = d.textlength(line, font=sf)
        d.text(((W - w) / 2, y), line, font=sf, fill=(214, 208, 236))
        y += 58

    # Phone: screenshot at 74 % width, dark bezel, runs off the bottom
    s = Image.open(SHOTS / shot).convert("RGB")
    if skip:
        s = s.crop((0, int(s.height * skip), s.width, s.height))
    pw = int(W * 0.74)
    s = s.resize((pw, int(s.height * pw / s.width)), Image.LANCZOS)
    bezel = 18
    top = y + 70
    phone = Image.new("RGB", (pw + 2 * bezel, s.height + 2 * bezel), (8, 8, 12))
    phone = rounded(phone, 72)
    phone.alpha_composite(rounded(s, 56), (bezel, bezel))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        ((W - phone.width) // 2 - 10, top + 30, (W + phone.width) // 2 + 10, top + phone.height + 40), 80,
        fill=(0, 0, 0, 150))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(40)))
    img.alpha_composite(phone, ((W - phone.width) // 2, top))
    return img.convert("RGB")


for i, (shot, title, sub, glows, skip) in enumerate(FRAMES, 1):
    frame(shot, title, sub, glows, skip).save(OUT / f"{i}.png", optimize=True)
    print(OUT / f"{i}.png")
