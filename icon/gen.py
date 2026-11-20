# Erzeugt Banner (320x180) und Symbol (192x192) für den Launcher (alte Kachel bis 0.18.12).
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
out = sys.argv[1]
S = 4  # Supersampling

def tiles(w, h, area, draw_text):
    img = Image.new("RGBA", (w*S, h*S))
    d = ImageDraw.Draw(img)
    # Hintergrund: Verlauf Grafit -> Schwarz mit blauem Schimmer oben links
    for y in range(h*S):
        t = y / (h*S)
        c = (int(34 - 24*t), int(38 - 26*t), int(52 - 34*t), 255)
        d.line([(0, y), (w*S, y)], fill=c)
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-w*S*0.2, -h*S*0.6, w*S*0.7, h*S*0.6], fill=(70, 130, 255, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(w*S*0.12))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = [v*S for v in area]
    cols, rows, gap = 3, 2, (x1-x0)*0.06
    tw = (x1-x0 - gap*(cols-1)) / cols
    th = tw * 9/16
    oy = y0 + ((y1-y0) - (th*rows + gap*(rows-1))) / 2
    for r in range(rows):
        for c in range(cols):
            x = x0 + c*(tw+gap); y = oy + r*(th+gap)
            focused = (r == 0 and c == 1)
            if focused:
                g = tw*0.10
                sh = Image.new("RGBA", img.size, (0,0,0,0))
                ImageDraw.Draw(sh).rounded_rectangle([x-g, y-g+th*0.12, x+tw+g, y+th+g+th*0.12], radius=tw*0.14, fill=(0,0,0,170))
                sh = sh.filter(ImageFilter.GaussianBlur(tw*0.10))
                img = Image.alpha_composite(img, sh)
                d = ImageDraw.Draw(img)
                d.rounded_rectangle([x-g, y-g, x+tw+g, y+th+g], radius=tw*0.14, fill=(245, 247, 252, 255))
            else:
                ov = Image.new("RGBA", img.size, (0,0,0,0))
                ImageDraw.Draw(ov).rounded_rectangle([x, y, x+tw, y+th], radius=tw*0.12, fill=(255, 255, 255, 46))
                img = Image.alpha_composite(img, ov)
                d = ImageDraw.Draw(img)
    if draw_text:
        try:
            f = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", int(h*S*0.13), index=1)
        except Exception:
            f = ImageFont.load_default()
        d.text((w*S*0.5, h*S*0.86), "Apple TV Home", font=f, fill=(255,255,255,235), anchor="mm")
    return img.resize((w, h), Image.LANCZOS)

tiles(320, 180, (70, 14, 250, 132), True).convert("RGB").save(out + "/banner.png")
icon = tiles(192, 192, (26, 30, 166, 162), False)
mask = Image.new("L", (192*S, 192*S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, 192*S, 192*S], radius=40*S, fill=255)
icon.putalpha(mask.resize((192, 192), Image.LANCZOS))
icon.save(out + "/ic_launcher.png")
