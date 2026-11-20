#!/usr/bin/env python3
"""Generates the HalkatOTT logo set (clapperboard 🎬 + wordmark). Needs Pillow + DejaVu Sans Bold."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BG = (11, 16, 32, 255)          # deep navy
AMBER = (255, 176, 0, 255)
WHITE = (250, 250, 252, 255)
BOARD = (38, 42, 56, 255)
DARK = (22, 24, 32, 255)
S = 2  # supersample


def stripes_bar(w, h, n=6, c1=WHITE, c2=DARK, radius=14):
    im = Image.new("RGBA", (w, h), c2)
    d = ImageDraw.Draw(im)
    step = w / n
    for i in range(-1, n + 1):
        if i % 2 == 0:
            x = i * step
            d.polygon([(x, h), (x + step, h), (x + step + h * 0.9, 0), (x + h * 0.9, 0)], fill=c1)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    im.putalpha(mask)
    return im


def clapper(size=1000):
    """Return an RGBA clapperboard drawn on a size x size transparent canvas."""
    k = size * S / 1000.0
    W = int(1000 * k)
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    # board body
    d.rounded_rectangle([110 * k, 400 * k, 890 * k, 880 * k], radius=36 * k, fill=BOARD)
    # fixed (lower) stripe bar
    lower = stripes_bar(int(780 * k), int(105 * k), n=7, radius=int(16 * k))
    canvas.alpha_composite(lower, (int(110 * k), int(300 * k)))
    # "text" lines on the board
    for i, (y, wd) in enumerate([(520, 560), (620, 440), (720, 500)]):
        d.rounded_rectangle([170 * k, y * k, (170 + wd) * k, (y + 26) * k], radius=13 * k, fill=(250, 250, 252, 200 if i else 255))
    # play triangle accent
    d.polygon([(700 * k, 600 * k), (700 * k, 780 * k), (830 * k, 690 * k)], fill=AMBER)
    # hinged (upper) clapper bar, rotated about the hinge
    upper = stripes_bar(int(780 * k), int(105 * k), n=7, radius=int(16 * k))
    pad = int(300 * k)
    tmp = Image.new("RGBA", (upper.width + 2 * pad, upper.height + 2 * pad), (0, 0, 0, 0))
    tmp.alpha_composite(upper, (pad, pad))
    hinge = (pad + 0, pad + upper.height)  # bottom-left corner
    rot = tmp.rotate(14, resample=Image.BICUBIC, center=hinge)
    canvas.alpha_composite(rot, (int(110 * k) - pad, int(300 * k) - pad - upper.height))
    # hinge pin
    d = ImageDraw.Draw(canvas)
    d.ellipse([(150 - 26) * k, (302 - 26) * k, (150 + 26) * k, (302 + 26) * k], fill=AMBER)
    return canvas.resize((size, size), Image.LANCZOS)


def wordmark(height, bold=True):
    """'Halkat' (white) + 'OTT' (amber) rendered at the given font pixel height."""
    f = ImageFont.truetype(FONT, height)
    a, b = "Halkat", "OTT"
    wa = f.getlength(a)
    wb = f.getlength(b)
    im = Image.new("RGBA", (int(wa + wb) + 4, int(height * 1.35)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.text((0, 0), a, font=f, fill=WHITE)
    d.text((wa, 0), b, font=f, fill=AMBER)
    return im.crop(im.getbbox())


def lockup(w, h, bg=BG):
    """Horizontal logo: clapperboard icon left, wordmark right (always fits the canvas)."""
    im = Image.new("RGBA", (w, h), bg if bg else (0, 0, 0, 0))
    icon_sz = int(min(h * 0.78, w * 0.30))
    gap = int(h * 0.06)
    avail = int(w * 0.92) - icon_sz - gap
    wm = wordmark(int(h * 0.30))
    if wm.width > avail:
        wm = wm.resize((avail, max(1, int(wm.height * avail / wm.width))), Image.LANCZOS)
    ic = clapper(icon_sz)
    total = icon_sz + gap + wm.width
    x0 = (w - total) // 2
    im.alpha_composite(ic, (x0, (h - icon_sz) // 2))
    im.alpha_composite(wm, (x0 + icon_sz + gap, (h - wm.height) // 2))
    return im


def app_icon(size):
    """Square launcher icon: rounded navy tile + clapperboard (+ tiny wordmark on large sizes)."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=BG)
    ic = clapper(int(size * 0.66))
    im.alpha_composite(ic, ((size - ic.width) // 2, int(size * 0.07)))
    wm = wordmark(int(size * 0.13))
    if wm.width > size * 0.86:
        wm = wm.resize((int(size * 0.86), int(wm.height * size * 0.86 / wm.width)), Image.LANCZOS)
    im.alpha_composite(wm, ((size - wm.width) // 2, int(size * 0.78)))
    return im


if __name__ == "__main__":
    lockup(2048, 1024).save(f"{OUT}/halkatott-logo-1024.png")
    lockup(2048, 1024, bg=None).save(f"{OUT}/halkatott-logo-transparent.png")
    app_icon(512).save(f"{OUT}/halkatott-icon-512.png")
    # Android TV home-screen banner (xhdpi 320x180 + 2x)
    lockup(640, 360).convert("RGB").save(f"{OUT}/tv-banner-640x360.png")
    lockup(320, 180).convert("RGB").save(f"{OUT}/tv-banner-320x180.png")
    # launcher mipmaps
    for name, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        d = f"{OUT}/res/mipmap-{name}"
        os.makedirs(d, exist_ok=True)
        app_icon(px * 4).resize((px, px), Image.LANCZOS).save(f"{d}/ic_launcher.png")
    os.makedirs(f"{OUT}/res/drawable-xhdpi", exist_ok=True)
    lockup(320, 180).convert("RGB").save(f"{OUT}/res/drawable-xhdpi/banner.png")
    print("ok")
