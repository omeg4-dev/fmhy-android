"""Generate FMHY neon-glow launcher icon foregrounds and legacy WebP icons.

    uv run --with pillow python tools/make_icons.py

Renders the FMHY play triangle with cyan glow along the left edge and
magenta-to-purple glow along the upper edge on a 108dp viewport.
Outputs:
- app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
- app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
- app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp
"""

import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

DENSITIES = [
    ("mdpi", 1.0, 48),
    ("hdpi", 1.5, 72),
    ("xhdpi", 2.0, 96),
    ("xxhdpi", 3.0, 144),
    ("xxxhdpi", 4.0, 192),
]

# Geometry on 108x108 dp viewport (safe zone 21..87 dp)
P_TOP_DP = (38.0, 34.0)
P_BOT_DP = (38.0, 74.0)
P_RIGHT_DP = (72.6, 54.0)

CYAN_HEX = "#00E5FF"
MAGENTA_HEX = "#E040FB"
PURPLE_HEX = "#7C4DFF"


def hex_to_rgb(hex_str: str) -> tuple[int, int, int]:
    h = hex_str.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def render_logo(d: float, supersample: int = 4) -> Image.Image:
    """Render the logo on a transparent canvas of 108*d px at supersample x supersample."""
    scale = d * supersample
    size_ss = int(round(108.0 * scale))
    target_size = int(round(108.0 * d))

    p_top = (P_TOP_DP[0] * scale, P_TOP_DP[1] * scale)
    p_bot = (P_BOT_DP[0] * scale, P_BOT_DP[1] * scale)
    p_right = (P_RIGHT_DP[0] * scale, P_RIGHT_DP[1] * scale)

    canvas = Image.new("RGBA", (size_ss, size_ss), (0, 0, 0, 0))

    # (a) Cyan glow along left edge:
    # polygon thickened ~3 dp to the left, Gaussian blur radius ~3 dp, peak alpha ~0.75
    cyan_mask = Image.new("L", (size_ss, size_ss), 0)
    d_cmask = ImageDraw.Draw(cyan_mask)
    # Rectangle of left edge thickened 3dp to left (35..38 dp) plus 0.5dp under triangle,
    # extending ~1dp past top/bottom vertices to round smoothly around corners
    d_cmask.rectangle(
        [
            (P_TOP_DP[0] - 3.0) * scale,
            (P_TOP_DP[1] - 1.0) * scale,
            (P_TOP_DP[0] + 0.5) * scale,
            (P_BOT_DP[1] + 1.0) * scale,
        ],
        fill=255,
    )
    b_cmask = cyan_mask.filter(ImageFilter.GaussianBlur(3.0 * scale))
    max_c = b_cmask.getextrema()[1]
    if max_c > 0:
        target_c_alpha = int(round(0.75 * 255))
        lut_c = [min(255, int(round(a * (target_c_alpha / max_c)))) for a in range(256)]
        b_cmask = b_cmask.point(lut_c)

    cyan_rgb = hex_to_rgb(CYAN_HEX)
    cyan_layer = Image.new("RGBA", (size_ss, size_ss), (*cyan_rgb, 0))
    cyan_layer.putalpha(b_cmask)
    canvas.alpha_composite(cyan_layer)

    # (b) Magenta -> Purple glow along upper edge:
    # #E040FB at top vertex fading to #7C4DFF toward right vertex,
    # thickened ~3 dp outward, Gaussian blur ~3 dp, peak alpha ~0.8
    dx = p_right[0] - p_top[0]
    dy = p_right[1] - p_top[1]
    length = math.hypot(dx, dy)
    nx = dy / length
    ny = -dx / length
    thick = 3.0 * scale
    overlap_in = 0.5 * scale

    c_top = hex_to_rgb(MAGENTA_HEX)
    c_right = hex_to_rgb(PURPLE_HEX)

    rgb_layer = Image.new("RGB", (size_ss, size_ss), (0, 0, 0))
    d_rgb = ImageDraw.Draw(rgb_layer)
    mag_mask = Image.new("L", (size_ss, size_ss), 0)
    d_mmask = ImageDraw.Draw(mag_mask)

    wide_in = 6.0 * scale
    wide_out = 9.0 * scale
    n_slices = 220
    t_start = -1.0 * scale / length
    t_end = 1.0 + 1.0 * scale / length

    for i in range(n_slices):
        t0 = t_start + (t_end - t_start) * (i / n_slices)
        t1 = t_start + (t_end - t_start) * ((i + 1) / n_slices)
        t_mid = (t0 + t1) / 2.0
        t_col = max(0.0, min(1.0, t_mid))

        r = int(round(c_top[0] * (1.0 - t_col) + c_right[0] * t_col))
        g = int(round(c_top[1] * (1.0 - t_col) + c_right[1] * t_col))
        b = int(round(c_top[2] * (1.0 - t_col) + c_right[2] * t_col))

        x0 = p_top[0] + dx * t0
        y0 = p_top[1] + dy * t0
        x1 = p_top[0] + dx * t1
        y1 = p_top[1] + dy * t1

        # Wide quad in RGB layer ensures no black fringing across blur radius
        d_rgb.polygon(
            [
                (x0 - nx * wide_in, y0 - ny * wide_in),
                (x0 + nx * wide_out, y0 + ny * wide_out),
                (x1 + nx * wide_out, y1 + ny * wide_out),
                (x1 - nx * wide_in, y1 - ny * wide_in),
            ],
            fill=(r, g, b),
        )

        # Polygon strip in alpha mask
        d_mmask.polygon(
            [
                (x0 - nx * overlap_in, y0 - ny * overlap_in),
                (x0 + nx * thick, y0 + ny * thick),
                (x1 + nx * thick, y1 + ny * thick),
                (x1 - nx * overlap_in, y1 - ny * overlap_in),
            ],
            fill=255,
        )

    b_mmask = mag_mask.filter(ImageFilter.GaussianBlur(3.0 * scale))
    max_m = b_mmask.getextrema()[1]
    if max_m > 0:
        target_m_alpha = int(round(0.80 * 255))
        lut_m = [min(255, int(round(a * (target_m_alpha / max_m)))) for a in range(256)]
        b_mmask = b_mmask.point(lut_m)

    mag_layer = rgb_layer.convert("RGBA")
    mag_layer.putalpha(b_mmask)
    canvas.alpha_composite(mag_layer)

    # (c) Crisp white triangle on top
    tri_layer = Image.new("RGBA", (size_ss, size_ss), (0, 0, 0, 0))
    d_tri = ImageDraw.Draw(tri_layer)
    d_tri.polygon([p_top, p_bot, p_right], fill=(255, 255, 255, 255))
    canvas.alpha_composite(tri_layer)

    # Downscale supersampled canvas to target size with Lanczos
    return canvas.resize((target_size, target_size), Image.Resampling.LANCZOS)


def make_legacy_icon(size: int, is_round: bool, supersample: int = 4) -> Image.Image:
    """Render legacy WebP icon with black rounded-square or circular background.

    The 108dp design is scaled so the 66dp safe zone fills ~70% of the icon width.
    """
    d_logo = (0.70 * size) / 66.0
    logo = render_logo(d_logo, supersample=supersample)

    ss = supersample
    size_ss = size * ss
    mask_ss = Image.new("L", (size_ss, size_ss), 0)
    d_mask = ImageDraw.Draw(mask_ss)
    if is_round:
        d_mask.ellipse([0, 0, size_ss - 1, size_ss - 1], fill=255)
    else:
        radius_ss = int(round(0.20 * size_ss))
        d_mask.rounded_rectangle([0, 0, size_ss - 1, size_ss - 1], radius=radius_ss, fill=255)
    mask = mask_ss.resize((size, size), Image.Resampling.LANCZOS)

    icon = Image.new("RGBA", (size, size), (0, 0, 0, 255))

    left = (logo.width - size) // 2
    top = (logo.height - size) // 2
    cropped_logo = logo.crop((left, top, left + size, top + size))
    icon.alpha_composite(cropped_logo)

    icon.putalpha(mask)
    return icon


def main() -> None:
    res_dir = Path("app/src/main/res")

    for density_name, d, legacy_size in DENSITIES:
        # 1. Launcher foreground PNG (RGBA with transparent background)
        fg_img = render_logo(d)
        fg_dir = res_dir / f"drawable-{density_name}"
        fg_dir.mkdir(parents=True, exist_ok=True)
        fg_path = fg_dir / "ic_launcher_foreground.png"
        fg_img.save(fg_path, format="PNG")
        print(f"Wrote {fg_path} ({fg_img.size[0]}x{fg_img.size[1]} RGBA)")

        # 2. Legacy square icon WebP
        sq_img = make_legacy_icon(legacy_size, is_round=False)
        mipmap_dir = res_dir / f"mipmap-{density_name}"
        mipmap_dir.mkdir(parents=True, exist_ok=True)
        sq_path = mipmap_dir / "ic_launcher.webp"
        sq_img.save(sq_path, format="WEBP", lossless=True, quality=95)
        print(f"Wrote {sq_path} ({legacy_size}x{legacy_size} WebP)")

        # 3. Legacy round icon WebP
        round_img = make_legacy_icon(legacy_size, is_round=True)
        round_path = mipmap_dir / "ic_launcher_round.webp"
        round_img.save(round_path, format="WEBP", lossless=True, quality=95)
        print(f"Wrote {round_path} ({legacy_size}x{legacy_size} WebP)")


if __name__ == "__main__":
    main()
