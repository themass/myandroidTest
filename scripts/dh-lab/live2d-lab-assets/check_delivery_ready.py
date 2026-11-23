#!/usr/bin/env python3
"""Gate artist underwear delivery before psd2live Layout export.

Checks:
- source.json parses; canvas/layers present
- every underwear token in wardrobe-spec has >=1 layer with wardrobe.slot=underwear
- no undeclared underwear tokens; build_layout succeeds (PNG header + paths)

Does NOT verify art quality, runtime 0/1 visibility, or psd2live MCP export.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from wardrobe_layout import build_layout

DEFAULT_SPEC = Path(__file__).with_name("wardrobe-spec.json")


def underwear_tokens(spec: dict) -> set[str]:
    slot = next((s for s in spec["slots"] if s["slot"] == "underwear"), None)
    if not slot:
        return set()
    return {
        v["token"]
        for v in slot.get("variants") or []
        if v.get("token") and v["token"] != "none"
    }


def check_delivery_ready(spec: dict, source_path: Path) -> dict:
    root = source_path.resolve().parent
    source = json.loads(source_path.read_text())
    required = underwear_tokens(spec)
    bound: set[str] = set()
    errors: list[str] = []
    for i, layer in enumerate(source.get("layers") or []):
        ward = layer.get("wardrobe")
        if not ward:
            continue
        if ward.get("slot") != "underwear":
            errors.append(f"layers[{i}]: wardrobe.slot must be underwear for this gate")
            continue
        token = ward.get("token")
        if not token:
            errors.append(f"layers[{i}]: missing wardrobe.token")
            continue
        bound.add(token)
    missing = sorted(required - bound)
    extra = sorted(bound - required)
    if missing:
        errors.append(f"missing underwear layers for tokens: {', '.join(missing)}")
    if extra:
        errors.append(f"undeclared underwear tokens in source: {', '.join(extra)}")
    layout_ok = False
    layout_error: str | None = None
    if not errors:
        try:
            build_layout(spec, source, root)
            layout_ok = True
        except (ValueError, KeyError, TypeError, OSError) as exc:
            layout_error = str(exc)
            errors.append(f"build_layout: {exc}")
    summary = {
        "source": str(source_path),
        "required_tokens": sorted(required),
        "bound_tokens": sorted(bound),
        "missing_tokens": missing,
        "extra_tokens": extra,
        "layout_build_ok": layout_ok,
        "layout_build_error": layout_error,
        "runtime_verified": False,
        "ok": not errors,
        "errors": errors,
    }
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="artist source.json path")
    parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC)
    args = parser.parse_args()
    if not args.source.is_file():
        print(json.dumps({"ok": False, "errors": ["source.json not found"]}, indent=2))
        return 1
    spec = json.loads(args.spec.read_text())
    summary = check_delivery_ready(spec, args.source)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
