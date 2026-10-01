#!/usr/bin/env python3
"""Render InstaW widget screenshots with PIL (2x scale)."""
from PIL import Image, ImageDraw, ImageFont
import os

S = 2
W, H = 360 * S, 740 * S
DJ = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
DJB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def font(bold, size):
    return ImageFont.truetype(DJB if bold else DJ, size * S)

def rr(d, box, r, fill):
    d.rounded_rectangle([v * S for v in box], radius=r * S, fill=fill)

def txt(d, xy, s, f, fill, anchor="la"):
    d.text((xy[0] * S, xy[1] * S), s, font=f, fill=fill, anchor=anchor)

def wallpaper():
    im = Image.new("RGB", (W, H), (20, 22, 40))
    d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H
        r = int(24 + 30 * t); g = int(20 + 26 * t); b = int(52 + 60 * t)
        d.line([0, y, W, y], fill=(r, g, b))
    # soft circles
    for (cx, cy, rad, col) in [(90, 200, 130, (70, 50, 140)), (300, 480, 150, (40, 90, 130)),
                               (200, 700, 120, (90, 50, 110))]:
        for rr_ in range(rad, 0, -6):
            a = max(0, 60 - (rad - rr_) // 2)
            d.ellipse([(cx - rr_) * S, (cy - rr_) * S, (cx + rr_) * S, (cy + rr_) * S],
                      outline=col + (a,) if len(col) == 3 else col)
    # status bar
    d = ImageDraw.Draw(im)
    txt(d, (20, 14), "3:43", font(False, 13), (255, 255, 255))
    # battery
    d.rounded_rectangle([318 * S, 16 * S, 342 * S, 28 * S], radius=3 * S, outline=(255, 255, 255), width=2)
    d.rectangle([320 * S, 18 * S, 336 * S, 26 * S], fill=(255, 255, 255))
    d.rectangle([343 * S, 20 * S, 346 * S, 24 * S], fill=(255, 255, 255))
    return im

def avatar(d, x, y, r=26):
    d.ellipse([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], fill=(0, 150, 136))
    txt(d, (x, y - 1), "D", font(True, 24), (255, 255, 255), anchor="mm")

def verified(d, x, y, r=8):
    d.ellipse([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], fill=(56, 130, 246))
    d.line([(x - 4) * S, y * S, (x - 1) * S, (y + 3) * S], fill=(255, 255, 255), width=2 * S)
    d.line([(x - 1) * S, (y + 3) * S, (x + 4) * S, (y - 3) * S], fill=(255, 255, 255), width=2 * S)

def delta_pill(d, x_right, y, text, dark):
    f = font(False, 13)
    tw = d.textlength(text, font=f) / S
    pw = tw + 20
    x = x_right - pw
    bg = (34, 80, 50) if dark else (230, 244, 234)
    fg = (165, 214, 167) if dark else (0, 168, 70)
    rr(d, [x, y, x + pw, y + 24], 12, bg)
    txt(d, (x + pw / 2, y + 12), text, f, fg, anchor="mm")

def card_classic(d, dark):
    # card
    bg = (16, 16, 20) if dark else (255, 255, 255)
    ink = (255, 255, 255) if dark else (23, 27, 38)
    soft = (212, 212, 212) if dark else (115, 119, 128)
    faint = (154, 154, 154) if dark else (115, 119, 128)
    rr(d, [16, 150, 344, 470], 28, bg)
    txt(d, (36, 172), "56,331", font(False, 44), ink)
    txt(d, (36, 226), "followers", font(False, 15), soft)
    d.line([36 * S, 262 * S, 324 * S, 262 * S], fill=(232, 232, 232) if not dark else (50, 50, 58), width=S)
    # avatar row
    avatar(d, 62, 322)
    txt(d, (100, 300), "Deepak Deon", font(False, 16), ink)
    nw = d.textlength("Deepak Deon", font=font(False, 16)) / S
    verified(d, 100 + nw + 12, 308)
    txt(d, (100, 324), "@deepak.deon", font(False, 14), faint)
    delta_pill(d, 324, 310, "+128", dark)

def card_name_top(d, dark):
    bg = (16, 16, 20) if dark else (255, 255, 255)
    ink = (255, 255, 255) if dark else (23, 27, 38)
    faint = (154, 154, 154) if dark else (115, 119, 128)
    rr(d, [16, 150, 344, 470], 28, bg)
    avatar(d, 62, 196, r=22)
    txt(d, (96, 178), "Deepak Deon", font(False, 16), ink)
    nw = d.textlength("Deepak Deon", font=font(False, 16)) / S
    verified(d, 96 + nw + 12, 186, r=7)
    txt(d, (96, 202), "@deepak.deon", font(False, 14), faint)
    txt(d, (36, 250), "56,331", font(False, 52), ink)
    txt(d, (36, 316), "followers", font(False, 15), faint)
    delta_pill(d, 324, 250, "+128", dark)
    txt(d, (324, 292), "this week", font(False, 12), faint, anchor="ra")

def dock(d):
    for i in range(4):
        cx = 360 * (i + 0.5) / 4
        rr(d, [cx - 24, 660, cx + 24, 708], 14, (255, 255, 255, 40))
        # fake app icon: translucent square
    # redraw as translucent: use overlay
    pass

OUT = os.path.expanduser("~/workspace/follower-widget-live/screenshots")

def save(name, style, dark):
    im = wallpaper()
    d = ImageDraw.Draw(im)
    if style == "classic":
        card_classic(d, dark)
    else:
        card_name_top(d, dark)
    # dock icons (simple translucent rounded squares)
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    cols = [(255, 120, 120), (120, 200, 255), (140, 255, 170), (255, 220, 120)]
    for i, c in enumerate(cols):
        cx = 360 * (i + 0.5) / 4
        od.rounded_rectangle([(cx - 24) * S, 656 * S, (cx + 24) * S, 704 * S], radius=14 * S, fill=c + (180,))
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    os.makedirs(OUT, exist_ok=True)
    im.save(f"{OUT}/{name}")
    print("saved", name)

save("widget-dark.png", "classic", True)
save("widget-light.png", "classic", False)
save("widget-nametop-dark.png", "nametop", True)
