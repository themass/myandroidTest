#!/usr/bin/env python3
"""Generate our synthetic vest fixture; previews are source composites, not renders.

Run with the Pillow venv and -I. The destination must not already exist.
No PSD2Live code, character artwork, or production wardrobe state is used.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import runpy

from PIL import Image, ImageDraw, ImageChops

PARAM = "ParamWear_test"


def vest(draw):
    draw.polygon([(260, 375), (292, 375), (320, 414), (348, 375),
                  (380, 375), (386, 545), (254, 545)], fill=(35, 150, 100, 255))
    draw.line([(320, 414), (320, 543)], fill=(245, 235, 190, 255), width=5)
    draw.rectangle((268, 475, 299, 498), fill=(20, 105, 75, 255))
    draw.rectangle((341, 475, 372, 498), fill=(20, 105, 75, 255))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    out = args.output.resolve()
    # Load only our own sibling generator, using an absolute path under -I.
    base = runpy.run_path(str(Path(__file__).with_name("make_psd.py")))
    out.mkdir(parents=True, exist_ok=False)
    (out / "parts").mkdir()
    layers = list(base["LAYERS"])
    layers.insert(next(i for i, (name, _) in enumerate(layers) if name == "topwear") + 1,
                  ("wardrobe vest", vest))
    width, height = base["CANVAS_W"], base["CANVAS_H"]
    manifest = {"format": "psd2live-layout", "version": 1,
                "name": "wardrobe-smoke", "canvas": {"width": width, "height": height},
                "layers": []}
    frames = {value: Image.new("RGBA", (width, height), "white") for value in (0, 1)}
    for name, draw in layers:
        image, (left, top, right, bottom) = base["part_image"](draw)
        layer_id = name.replace(" ", "_")
        path = f"parts/{layer_id}.png"
        image.save(out / path)
        layer = {"id": layer_id, "name": name,
                 "image": {"path": path, "width": image.width, "height": image.height},
                 "rect": {"left": left, "top": top, "width": right-left, "height": bottom-top},
                 "tags": []}
        if draw is vest:
            layer["tags"] = ["topwear"]
            layer["variant"] = {"type": "toggle", "parameter": PARAM}
        manifest["layers"].append(layer)
        for value, frame in frames.items():
            if draw is not vest or value == 1:
                frame.alpha_composite(image, (left, top))
    (out / "layout.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for value in (0, 1):
        expression = {"Type": "Live2D Expression", "FadeInTime": 0.0, "FadeOutTime": 0.0,
                      "Parameters": [{"Id": PARAM, "Value": value, "Blend": "Overwrite"}]}
        (out / f"vest-{value}.exp3.json").write_text(json.dumps(expression, indent=2) + "\n")
        frames[value].save(out / f"source-vest-{value}.png")
    sheet = Image.new("RGB", (width * 2, height + 48), "white")
    text = ImageDraw.Draw(sheet)
    for value, frame in frames.items():
        sheet.paste(frame.convert("RGB"), (width * value, 48))
        text.text((width * value + 12, 12), f"SOURCE COMPOSITE ONLY - {PARAM} = {value}", fill="black")
    sheet.save(out / "source-comparison.png")
    diff = ImageChops.difference(frames[0].convert("RGB"), frames[1].convert("RGB"))
    summary = {"kind": "source-fixture-only", "runtime_verified": False,
               "parameter": PARAM, "layer_count": len(layers),
               "changed_source_bbox": diff.getbbox(),
               "production_spec_modified": False}
    assert summary["changed_source_bbox"] is not None, "fixture must show a visible vest"
    (out / "fixture-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
