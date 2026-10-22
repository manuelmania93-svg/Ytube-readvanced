"""Builds adaptive launcher icon PNGs (white background layer + transparent logo layer) from branding/icon.jpg.b64."""
import base64, io, os, sys
from PIL import Image
out = sys.argv[1]
src = Image.open(io.BytesIO(base64.b64decode(open("branding/icon.jpg.b64").read()))).convert("RGB")
# crop to the logo (everything that is not near-white)
g = src.convert("L").point(lambda v: 255 if v < 235 else 0)
src = src.crop(g.getbbox())
# white -> transparent (alpha from distance to white), keeps anti-aliased edges
px = src.convert("RGBA"); a = Image.new("L", px.size)
data = []
for r, gg, b, _ in px.getdata():
    data.append(max(0, min(255, int((255 - min(r, gg, b)) * 255 / 200))) if min(r, gg, b) > 55 else 255)
a.putdata(data); px.putalpha(a)
sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for d, s in sizes.items():
    p = os.path.join(out, "launcher", f"mipmap-{d}"); os.makedirs(p, exist_ok=True)
    w = int(s * 0.50)  # logo width = 50% of the 108dp layer, inside the 66dp safe zone
    h = int(px.size[1] * w / px.size[0])
    fg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    fg.alpha_composite(px.resize((w, h), Image.LANCZOS), ((s - w) // 2, (s - h) // 2))
    fg.save(os.path.join(p, "adaptiveproduct_youtube_foreground_color_108.png"))
    Image.new("RGBA", (s, s), (255, 255, 255, 255)).save(os.path.join(p, "adaptiveproduct_youtube_background_color_108.png"))
print("icons written to", out)
