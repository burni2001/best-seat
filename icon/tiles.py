# Entwürfe für die Kachel (Banner 320x180) von Best Seat. Aufruf: python3 tiles.py <Ausgabeordner>
# Gezeichnet in 2560x1440 (8-fach), verkleinert auf 1280x720 (Vorschau) und 320x180 (Banner).
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
W, H = 2560, 1440
AV = "/System/Library/Fonts/Avenir Next.ttc"
DIN = "/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf"
AMBER = (255, 176, 64)


def font(size, idx=2, path=AV):
    return ImageFont.truetype(path, size, index=idx) if path == AV else ImageFont.truetype(path, size)


def gradient(top, bottom):
    img = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return img


def glow(img, box, color, alpha, blur, shape="ellipse"):
    g = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    getattr(gd, shape)(box, fill=color + (alpha,))
    return Image.alpha_composite(img, g.filter(ImageFilter.GaussianBlur(blur)))


def text_center(d, cx, y, s, f, fill, spacing=0):
    if spacing:
        widths = [d.textlength(ch, font=f) for ch in s]
        total = sum(widths) + spacing * (len(s) - 1)
        x = cx - total / 2
        for ch, w in zip(s, widths):
            d.text((x, y), ch, font=f, fill=fill)
            x += w + spacing
    else:
        w = d.textlength(s, font=f)
        d.text((cx - w / 2, y), s, font=f, fill=fill)


def armchair(d, x, y, s, color, width, fill=None):
    """Sessel in Linien: Lehne, Armlehnen, Sitz, Füße. (x, y) = links oben, s = Breite."""
    w = int(width)
    h = s * 0.82
    r = s * 0.12
    # Lehne
    d.rounded_rectangle([x + s * 0.16, y, x + s * 0.84, y + h * 0.62], r, outline=color, width=w, fill=fill)
    # Armlehnen
    d.rounded_rectangle([x, y + h * 0.30, x + s * 0.2, y + h * 0.88], r * 0.8, outline=color, width=w, fill=fill)
    d.rounded_rectangle([x + s * 0.8, y + h * 0.30, x + s, y + h * 0.88], r * 0.8, outline=color, width=w, fill=fill)
    # Sitz
    d.rounded_rectangle([x + s * 0.2, y + h * 0.58, x + s * 0.8, y + h * 0.88], r * 0.6, outline=color, width=w, fill=fill)
    # Füße
    d.line([(x + s * 0.12, y + h * 0.88), (x + s * 0.12, y + h)], fill=color, width=w)
    d.line([(x + s * 0.88, y + h * 0.88), (x + s * 0.88, y + h)], fill=color, width=w)


DARK_AMBER = (214, 134, 36)


def armchair_logo(d, x, y, s):
    """Logo armchair as in the user's vector (best-seat-sessel.svg): flat amber parts, layered back, arms, seat,
    each with a 20-unit seam (1000 units wide). (x, y) = top left, s = width."""
    k = s / 1000.0

    def box(x0, y0, w, h, grow=0.0):
        return [x + (x0 - grow) * k, y + (y0 - grow) * k, x + (x0 + w + grow) * k, y + (y0 + h + grow) * k]

    def part(x0, y0, w, h, r):
        d.rounded_rectangle(box(x0, y0, w, h), r * k, fill=AMBER)

    def seam(x0, y0, w, h, r):
        # SVG stroke 20 centred 4.5 inside the edge: covers 5.5 outside to 14.5 inside
        d.rounded_rectangle(box(x0, y0, w, h, 5.5), (r + 5.5) * k, outline=DARK_AMBER, width=max(1, round(20 * k)))

    back, arm_l, arm_r, seat = (160, 0, 680, 508.4, 120), (0, 246, 200, 475.6, 96), (800, 246, 200, 475.6, 96), \
        (200, 452.93, 600, 246, 72)
    part(*back)
    for fx in (76.34, 878.95):
        d.rectangle([x + fx * k, y + 721.6 * k, x + (fx + 44.7) * k - 1, y + 820 * k], fill=AMBER)
    seam(*back)
    for lx in (98.69, 901.3):
        d.line([(x + lx * k, y + 721.6 * k), (x + lx * k, y + 820 * k)], fill=DARK_AMBER, width=max(1, round(9 * k)))
    for p in (arm_l, arm_r, seat):
        part(*p)
        seam(*p)


def v1_armchair():
    img = gradient((22, 26, 40), (6, 7, 12))
    img = glow(img, [80, 200, 1300, 1400], AMBER, 110, 220)
    d = ImageDraw.Draw(img)
    armchair(d, 330, 400, 560, (255, 214, 150), 34)
    f = font(250, 2)
    d.text((1060, 560), "Best Seat", font=f, fill=(245, 245, 250))
    return img


def v2_front_row():
    img = gradient((14, 16, 26), (4, 4, 8))
    img = glow(img, [700, -500, 1860, 380], (120, 160, 255), 90, 160)
    d = ImageDraw.Draw(img)
    # Leinwand oben
    d.rounded_rectangle([760, 110, 1800, 150], 20, fill=(200, 215, 255))
    rows, cols = 4, 9
    sw, sh, gap = 150, 92, 34
    for r in range(rows):
        n = cols - (rows - 1 - r) % 2  # leicht versetzt
        total = n * sw + (n - 1) * gap
        x0 = (W - total) / 2
        y = 330 + r * (sh + 46)
        for c in range(n):
            x = x0 + c * (sw + gap)
            best = (r == 1 and c == n // 2)
            col = AMBER if best else (58, 62, 78)
            if best:
                img2 = glow(img, [x - 80, y - 80, x + sw + 80, y + sh + 80], AMBER, 160, 50)
                img.paste(img2)
                d = ImageDraw.Draw(img)
            d.rounded_rectangle([x, y, x + sw, y + sh], 26, fill=col)
            d.rounded_rectangle([x, y + sh - 26, x + sw, y + sh], 12, fill=tuple(max(0, v - 30) for v in col))
    text_center(d, W / 2, 1000, "Best Seat", font(250, 2), (245, 245, 250))
    return img


def v3_spotlight():
    img = gradient((10, 10, 14), (2, 2, 4))
    cone = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cone)
    cd.polygon([(1180, -50), (1380, -50), (1820, 1150), (740, 1150)], fill=(255, 236, 200, 70))
    cd.ellipse([760, 1030, 1800, 1230], fill=(255, 236, 200, 110))
    img = Image.alpha_composite(img, cone.filter(ImageFilter.GaussianBlur(60)))
    d = ImageDraw.Draw(img)
    armchair(d, 1030, 640, 500, (20, 18, 16), 30, fill=(28, 25, 22))
    armchair(d, 1030, 640, 500, (255, 226, 170), 14)
    f = font(150, 2)
    d.text((140, 1170), "Best Seat", font=f, fill=(245, 240, 230))
    return img


def v4_ticket():
    img = gradient((120, 24, 36), (52, 8, 16))
    img = glow(img, [300, 100, 2260, 1340], (255, 120, 90), 70, 200)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 360, 330, 2200, 1110
    paper = (250, 238, 214)
    d.rounded_rectangle([x0, y0, x1, y1], 60, fill=paper)
    # Kerben links/rechts und Abrisslinie
    bg = (95, 18, 28)
    for cx in (x0, x1):
        d.ellipse([cx - 70, (y0 + y1) / 2 - 70, cx + 70, (y0 + y1) / 2 + 70], fill=bg)
    sx = 1720
    for y in range(y0 + 60, y1 - 40, 70):
        d.line([(sx, y), (sx, y + 34)], fill=(190, 160, 130), width=10)
    ink = (120, 24, 36)
    d.text((480, 420), "BEST SEAT", font=font(330, path=DIN), fill=ink)
    d.text((480, 860), "ROW 1  ·  SEAT 1", font=font(120, path=DIN), fill=(150, 110, 90))
    armchair(d, 1800, 560, 300, ink, 22)
    return img


def v5_screen_glow():
    img = gradient((8, 10, 18), (2, 2, 6))
    img = glow(img, [300, -100, 2260, 900], (110, 150, 255), 120, 180)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([560, 150, 2000, 800], 40, fill=(30, 44, 86), outline=(170, 195, 255), width=12)
    text_center(d, W / 2, 330, "Best Seat", font(230, 2), (240, 244, 255))
    # Sofa als Silhouette davor: erst alle Teile etwas größer in der Randfarbe, dann innen dunkel.
    sofa = (6, 6, 10)
    rim = (120, 150, 230)
    parts = [([520, 1000, 2040, 1300], 90), ([420, 1120, 700, 1460], 80), ([1860, 1120, 2140, 1460], 80),
             ([600, 1220, 1960, 1460], 10)]
    for col, grow in ((rim, 8), (sofa, 0)):
        for (x0, y0, x1, y1), r in parts:
            d.rounded_rectangle([x0 - grow, y0 - grow, x1 + grow, y1 + grow], r + grow, fill=col)
    return img


def v6_wordmark(size=(W, H)):
    """Schriftzug „best seat“, kleiner Sessel als Leerzeichen, Schein mittig hinter dem Sessel."""
    img = gradient((30, 32, 40), (10, 11, 15))
    d = ImageDraw.Draw(img)
    f = font(330, 2)
    a, b = "best", "seat"
    wa, wb = d.textlength(a, font=f), d.textlength(b, font=f)
    gapw = 220
    x = (W - (wa + gapw + wb)) / 2
    y = 470
    sx, ss = x + wa + 30, gapw - 60
    sy = y + 150
    cx, cy = sx + ss / 2, sy + ss * 0.82 / 2
    img = glow(img, [cx - 620, cy - 460, cx + 620, cy + 460], AMBER, 70, 240)
    d = ImageDraw.Draw(img)
    d.text((x, y), a, font=f, fill=(245, 245, 250))
    d.text((x + wa + gapw, y), b, font=f, fill=(245, 245, 250))
    armchair_logo(d, sx, sy, ss)
    return img


def icon6():
    """App-Symbol 192x192 (gezeichnet 1536x1536): Sessel in Bernstein mit Schein auf Grafit."""
    n = 1536
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    bg = Image.new("RGBA", (n, n))
    bd = ImageDraw.Draw(bg)
    for yy in range(n):
        t = yy / n
        bd.line([(0, yy), (n, yy)], fill=(int(30 - 20 * t), int(32 - 21 * t), int(40 - 25 * t), 255))
    g = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse([n * 0.15, n * 0.15, n * 0.85, n * 0.85], fill=AMBER + (90,))
    bg = Image.alpha_composite(bg, g.filter(ImageFilter.GaussianBlur(n * 0.12)))
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], n * 0.22, fill=255)
    img.paste(bg, (0, 0), mask)
    d = ImageDraw.Draw(img)
    s = n * 0.56
    armchair_logo(d, (n - s) / 2, (n - s * 0.82) / 2, s)
    return img


VARIANTS = [("1-sessel", v1_armchair), ("2-erste-reihe", v2_front_row), ("3-spotlight", v3_spotlight),
            ("4-ticket", v4_ticket), ("5-leinwand", v5_screen_glow), ("6-schriftzug", v6_wordmark)]

os.makedirs(OUT, exist_ok=True)
previews = []
for name, fn in VARIANTS:
    big = fn().convert("RGB")
    prev = big.resize((1280, 720), Image.LANCZOS)
    prev.save(os.path.join(OUT, name + ".png"))
    big.resize((320, 180), Image.LANCZOS).save(os.path.join(OUT, name + "-320.png"))
    previews.append((name, prev))

# Übersicht: 2 Spalten x 3 Zeilen, je 640x360, mit Nummer; dazu jeweils die echte 320er Größe daneben
sheet = Image.new("RGB", (2 * 640 + 3 * 40, 3 * (360 + 90) + 40), (24, 24, 28))
sd = ImageDraw.Draw(sheet)
lab = font(36, 5)
for i, (name, prev) in enumerate(previews):
    cx, cy = 40 + (i % 2) * 680, 40 + (i // 2) * 450
    sheet.paste(prev.resize((640, 360), Image.LANCZOS), (cx, cy))
    sd.text((cx, cy + 372), name.replace("-", " ", 1).title(), font=lab, fill=(220, 220, 225))
sheet.save(os.path.join(OUT, "uebersicht.png"))
icon6().resize((192, 192), Image.LANCZOS).save(os.path.join(OUT, "6-symbol-192.png"))
print("ok")
