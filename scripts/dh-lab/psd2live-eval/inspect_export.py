#!/usr/bin/env python3
"""Assert a PSD2Live CLI export directory looks usable for our wardrobe pipeline.

Usage: python -I inspect_export.py /path/to/out
Exit 0 on pass; 1 on failure. Prints a short JSON summary to stdout.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: inspect_export.py <export-dir>", file=sys.stderr)
        return 2
    out = Path(sys.argv[1])
    model3 = out / "demo.model3.json"
    moc3 = out / "demo.moc3"
    cdi3 = out / "demo.cdi3.json"
    errors: list[str] = []

    if not model3.is_file():
        errors.append("missing demo.model3.json")
    if not moc3.is_file():
        errors.append("missing demo.moc3")
    if not cdi3.is_file():
        errors.append("missing demo.cdi3.json")

    summary: dict = {"dir": str(out)}
    if moc3.is_file():
        blob = moc3.read_bytes()
        summary["moc3_size"] = len(blob)
        summary["magic"] = blob[:4].decode("ascii", "replace")
        summary["moc_version_byte"] = blob[4] if len(blob) > 4 else None
        if blob[:4] != b"MOC3":
            errors.append(f"bad moc3 magic: {blob[:4]!r}")
        strs = sorted({m.group().decode() for m in re.finditer(rb"[A-Za-z_][A-Za-z0-9_]{3,63}", blob)})
        params = [s for s in strs if s.startswith("Param")]
        arts = [s for s in strs if s.startswith("ArtMesh")]
        summary["param_count"] = len(params)
        summary["artmesh_count"] = len(arts)
        summary["has_ParamAngleX"] = "ParamAngleX" in params
        summary["has_ArtMeshTopwear"] = "ArtMeshTopwear" in arts
        if "ParamAngleX" not in params:
            errors.append("ParamAngleX missing from moc3 strings")
        if "ArtMeshTopwear" not in arts:
            errors.append("ArtMeshTopwear missing (expected from topwear layer)")

    if model3.is_file():
        ref = json.loads(model3.read_text())["FileReferences"]
        summary["model3_moc"] = ref.get("Moc")
        if ref.get("Moc") != "demo.moc3":
            errors.append(f"model3 Moc ref unexpected: {ref.get('Moc')!r}")
        for name in ref.get("Textures") or []:
            if not (out / name).is_file():
                errors.append(f"missing texture {name}")

    if cdi3.is_file():
        cdi = json.loads(cdi3.read_text())
        summary["cdi_param_count"] = len(cdi.get("Parameters") or [])
        summary["cdi_drawable_count"] = len(cdi.get("Drawables") or [])

    summary["ok"] = not errors
    summary["errors"] = errors
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
