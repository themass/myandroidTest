#!/usr/bin/env python3
"""Generate a simple layered PSD for psd2live evaluation.

Hand-rolled PSD writer (raw compression, 8-bit RGB + per-layer alpha), parts drawn
with Pillow. Names use psd2live's supported layer-name tags (zh/en).

Run:  python -I make_psd.py <out.psd>
Self-check: opening with psd-tools (if installed) prints the layer stack.
"""
import struct
import sys

from PIL import Image, ImageDraw

CANVAS_W, CANVAS_H = 640, 896


def _ell(draw, box, fill):
    draw.ellipse(box, fill=fill)


def part_image(draw_fn):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d)
    bbox = img.getbbox()
    return img.crop(bbox), bbox


# Head center ~ (320, 230), body under it. Flat colors, generous overlap.
def backhair(d):
    _ell(d, (150, 90, 490, 430), (90, 60, 140, 255))          # back hair mass
    d.polygon([(170, 300), (150, 520), (210, 500), (220, 320)], fill=(90, 60, 140, 255))
    d.polygon([(470, 300), (490, 520), (430, 500), (420, 320)], fill=(90, 60, 140, 255))


def face(d):
    _ell(d, (200, 110, 440, 350), (245, 215, 185, 255))       # face skin


def fronthair(d):
    d.pieslice((190, 95, 450, 300), 180, 360, fill=(110, 75, 160, 255))
    d.polygon([(200, 190), (250, 230), (230, 150)], fill=(110, 75, 160, 255))
    d.polygon([(440, 190), (390, 230), (410, 150)], fill=(110, 75, 160, 255))


def eye_white_l(d):
    _ell(d, (240, 200, 290, 240), (250, 250, 250, 255))


def eye_white_r(d):
    _ell(d, (350, 200, 400, 240), (250, 250, 250, 255))


def iris_l(d):
    _ell(d, (255, 205, 280, 235), (60, 110, 200, 255))


def iris_r(d):
    _ell(d, (365, 205, 390, 235), (60, 110, 200, 255))


def brow_l(d):
    d.rounded_rectangle((240, 185, 292, 196), 5, fill=(90, 60, 140, 255))


def brow_r(d):
    d.rounded_rectangle((348, 185, 400, 196), 5, fill=(90, 60, 140, 255))


def nose(d):
    d.polygon([(316, 250), (324, 250), (320, 262)], fill=(225, 190, 160, 255))


def mouth(d):
    d.pieslice((295, 265, 345, 300), 20, 160, fill=(200, 90, 90, 255))


def neck(d):
    d.rectangle((295, 330, 345, 380), fill=(240, 208, 180, 255))


def topwear(d):
    d.rounded_rectangle((240, 370, 400, 560), 30, fill=(70, 100, 180, 255))  # 上衣
    d.rounded_rectangle((200, 380, 250, 540), 24, fill=(70, 100, 180, 255))  # sleeve L
    d.rounded_rectangle((390, 380, 440, 540), 24, fill=(70, 100, 180, 255))  # sleeve R


def arms(d):
    _ell(d, (205, 520, 245, 580), (245, 215, 185, 255))       # hand L
    _ell(d, (395, 520, 435, 580), (245, 215, 185, 255))       # hand R


def bottomwear(d):
    d.polygon([(250, 545), (390, 545), (410, 640), (230, 640)], fill=(180, 80, 110, 255))  # 下装


def legs(d):
    d.rectangle((270, 630, 300, 790), fill=(245, 215, 185, 255))
    d.rectangle((340, 630, 370, 790), fill=(245, 215, 185, 255))


def footwear(d):
    d.rounded_rectangle((262, 780, 308, 815), 10, fill=(60, 60, 70, 255))
    d.rounded_rectangle((332, 780, 378, 815), 10, fill=(60, 60, 70, 255))


# (name, draw fn) bottom -> top; names match psd2live LayerClassifier tags
LAYERS = [
    ("back hair", backhair),
    ("legs", legs),
    ("footwear", footwear),
    ("bottomwear", bottomwear),
    ("neck", neck),
    ("topwear", topwear),
    ("arm", arms),
    ("face", face),
    ("nose", nose),
    ("mouth", mouth),
    ("eyebrow_l", brow_l),
    ("eyebrow_r", brow_r),
    ("eyewhite_l", eye_white_l),
    ("eyewhite_r", eye_white_r),
    ("iris_l", iris_l),
    ("iris_r", iris_r),
    ("front hair", fronthair),
]


def pascal(s: str) -> bytes:
    b = s.encode("latin-1", "replace")[:255]
    pad = (4 - (len(b) + 1) % 4) % 4  # pad total (length byte + name) to multiple of 4
    return len(b).to_bytes(1, "big") + b + b"\x00" * pad


def build_psd(path: str) -> dict:
    infos = []  # (name, img cropped, rect)
    for name, fn in LAYERS:
        img, rect = part_image(fn)
        infos.append((name, img, rect))

    layer_records = bytearray()
    channel_blocks = []
    for name, img, (l, t, r, b) in infos:
        w, h = r - l, b - t
        chans = {}
        for cid, idx in ((0, 0), (1, 1), (2, 2), (-1, 3)):
            plane = img.getchannel(["R", "G", "B", "A"][idx]).tobytes()
            data = struct.pack(">H", 0) + plane
            chans[cid] = data
        nch = len(chans)
        rec = struct.pack(">iiiiH", t, l, b, r, nch)
        # channel order in record: R, G, B, A(-1)
        for cid in (0, 1, 2, -1):
            rec += struct.pack(">hI", cid, len(chans[cid]))
        rec += b"8BIM" + b"norm"
        rec += struct.pack(">BBBx", 255, 0, 0)  # opacity, clipping, flags(+filler)
        # extra data: mask len 0, blend ranges len 0, name
        extra = struct.pack(">I", 0) + struct.pack(">I", 0) + pascal(name)
        rec += struct.pack(">I", len(extra)) + extra
        layer_records += rec
        # channel image data per layer after all records: R G B A
        for cid in (0, 1, 2, -1):
            channel_blocks.append(chans[cid])

    # LayerAndMask section = [len][LayerInfo: count + records + channel data][len][global mask]
    data_blob = b"".join(channel_blocks)
    layer_info = struct.pack(">h", len(infos)) + bytes(layer_records) + data_blob
    inner = struct.pack(">I", len(layer_info)) + layer_info + struct.pack(">I", 0)
    section = struct.pack(">I", len(inner)) + inner

    # merged image: 3 channels planar raw, flattened over white
    merged = Image.new("RGB", (CANVAS_W, CANVAS_H), (255, 255, 255))
    for name, img, (l, t, r, b) in infos:
        merged.alpha_composite(img.convert("RGBA"), (l, t)) if merged.mode == "RGBA" else None
    flat = Image.new("RGBA", (CANVAS_W, CANVAS_H), (255, 255, 255, 255))
    for name, img, (l, t, r, b) in infos:
        flat.alpha_composite(img, (l, t))
    flat = flat.convert("RGB")
    merged_img = struct.pack(">H", 0)
    for ch in range(3):
        merged_img += flat.getchannel("RGB"[ch]).tobytes()

    header = struct.pack(">4sH6xHIIHH", b"8BPS", 1, 3, CANVAS_H, CANVAS_W, 8, 3)
    with open(path, "wb") as f:
        f.write(header)
        f.write(struct.pack(">I", 0))  # color mode data
        f.write(struct.pack(">I", 0))  # image resources
        f.write(section)
        f.write(merged_img)
    return {name: (r[0], r[1], r[2] - r[0], r[3] - r[1]) for name, _, r in infos}


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "demo.psd"
    rects = build_psd(out)
    for n, r in rects.items():
        print(f"{n:16s} rect={r}")
    print(f"wrote {out}")
