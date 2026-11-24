#!/usr/bin/env python3
"""Generate a fully original layered robot avatar Layout for PSD2Live.

Run with the evaluation Pillow venv and Python isolated mode. Output must not exist.
This is an editor/demo fixture, not production art and not Android runtime evidence.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw

W, H = 640, 896
LAYERS = [
    ("antenna", ("decor",), None, lambda d: (d.line((320, 115, 320, 72), fill="#27364d", width=10), d.ellipse((302, 50, 338, 86), fill="#ffbf69"))),
    ("body", ("body",), None, lambda d: (d.rounded_rectangle((225, 330, 415, 620), 48, fill="#e8edf2", outline="#27364d", width=12), d.rounded_rectangle((250, 365, 390, 490), 24, fill="#b8d9e8", outline="#27364d", width=8))),
    ("legs", ("body",), None, lambda d: (d.rounded_rectangle((250, 590, 300, 765), 20, fill="#9aa8b5", outline="#27364d", width=8), d.rounded_rectangle((340, 590, 390, 765), 20, fill="#9aa8b5", outline="#27364d", width=8))),
    ("boots", ("body",), None, lambda d: (d.rounded_rectangle((225, 740, 310, 800), 22, fill="#53677b", outline="#27364d", width=8), d.rounded_rectangle((330, 740, 415, 800), 22, fill="#53677b", outline="#27364d", width=8))),
    ("left arm", ("body",), None, lambda d: d.rounded_rectangle((150, 360, 250, 535), 42, fill="#c8d4de", outline="#27364d", width=10)),
    ("right arm", ("body",), None, lambda d: d.rounded_rectangle((390, 360, 490, 535), 42, fill="#c8d4de", outline="#27364d", width=10)),
    ("head", ("head",), None, lambda d: (d.rounded_rectangle((185, 120, 455, 355), 78, fill="#f4f6f8", outline="#27364d", width=12), d.rounded_rectangle((215, 164, 425, 285), 42, fill="#293b54"))),
    ("eye left", ("eye",), None, lambda d: d.ellipse((250, 195, 295, 240), fill="#71e0c1")),
    ("eye right", ("eye",), None, lambda d: d.ellipse((345, 195, 390, 240), fill="#71e0c1")),
    ("mouth neutral", ("mouth",), None, lambda d: d.rounded_rectangle((290, 260, 350, 272), 6, fill="#f4f6f8")),
    ("mouth smile", ("mouth smile",), {"type": "toggle", "parameter": "ParamFace_smile"}, lambda d: d.arc((282, 238, 358, 294), 10, 170, fill="#ffbf69", width=10)),
    ("cape", ("topwear",), {"type": "toggle", "parameter": "ParamWear_cape"}, lambda d: (d.polygon([(225, 338), (415, 338), (455, 620), (185, 620)], fill="#e66f51", outline="#27364d"), d.line((320, 360, 320, 594), fill="#ffd166", width=8))),
    ("chest badge", ("decor",), None, lambda d: d.ellipse((296, 405, 344, 453), fill="#ffd166", outline="#27364d", width=6)),
]


def draw_layer(draw_fn):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im))
    bounds = im.getbbox()
    return im.crop(bounds), bounds


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("output", type=Path)
    out = ap.parse_args().output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    (out / "parts").mkdir()
    doc = {"format": "psd2live-layout", "version": 1, "name": "original-robot-demo", "canvas": {"width": W, "height": H}, "layers": []}
    pose_parameters = {
        "default": set(),
        "smile": {"ParamFace_smile"},
        "cape": {"ParamWear_cape"},
        "smile_cape": {"ParamFace_smile", "ParamWear_cape"},
    }
    comps = {pose: Image.new("RGBA", (W, H), "white") for pose in pose_parameters}
    for name, tags, variant, fn in LAYERS:
        im, (left, top, right, bottom) = draw_layer(fn)
        layer_id = name.replace(" ", "_")
        relpath = f"parts/{layer_id}.png"
        im.save(out / relpath)
        entry = {"id": layer_id, "name": name, "image": {"path": relpath, "width": im.width, "height": im.height}, "rect": {"left": left, "top": top, "width": right-left, "height": bottom-top}, "tags": list(tags)}
        if variant:
            entry["variant"] = variant
        doc["layers"].append(entry)
        for pose, canvas in comps.items():
            visible = not variant or variant["parameter"] in pose_parameters[pose]
            if visible:
                canvas.alpha_composite(im, (left, top))
    (out / "layout.json").write_text(json.dumps(doc, indent=2) + "\n")
    for pose, canvas in comps.items():
        canvas.convert("RGB").save(out / f"source-{pose}.png")
    sheet = Image.new("RGB", (W * 2, (H + 36) * 2), "white")
    sheet_draw = ImageDraw.Draw(sheet)
    for index, (pose, canvas) in enumerate(comps.items()):
        x, y = (index % 2) * W, (index // 2) * (H + 36)
        sheet_draw.text((x + 12, y + 10), f"SOURCE ONLY: {pose}", fill="#27364d")
        sheet.paste(canvas.convert("RGB"), (x, y + 36))
    sheet.save(out / "source-parameter-states.png")
    for param, val, fn in (("ParamFace_smile", 1, "smile.exp3.json"), ("ParamWear_cape", 1, "cape-on.exp3.json")):
        exp = {"Type": "Live2D Expression", "FadeInTime": 0.0, "FadeOutTime": 0.0, "Parameters": [{"Id": param, "Value": val, "Blend": "Overwrite"}]}
        (out / fn).write_text(json.dumps(exp, indent=2) + "\n")
    summary = {"kind": "original-layered-editor-demo", "runtime_verified": False, "layer_count": len(LAYERS), "parameters": ["ParamFace_smile", "ParamWear_cape"], "production_assets_used": False}
    (out / "fixture-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
