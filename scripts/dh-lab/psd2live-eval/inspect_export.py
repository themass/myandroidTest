#!/usr/bin/env python3
"""Assert a PSD2Live export directory (demo or layout prefix) against wardrobe-spec.

Usage:
  python -I inspect_export.py /path/to/out
  python -I inspect_export.py /path/to/out --prefix layout --spec ../live2d-lab-assets/wardrobe-spec.json

Exit 0 on pass; 1 on failure. Prints JSON summary. Does not replace runtime 0/1 visibility tests.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from wardrobe_export import detect_prefix, inspect_directory, load_spec

DEFAULT_SPEC = Path(__file__).resolve().parents[1] / "live2d-lab-assets" / "wardrobe-spec.json"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("out_dir", type=Path, help="export directory from PSD2Live CLI/MCP")
    parser.add_argument("--prefix", help="file basename prefix (default: auto-detect layout/demo)")
    parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC, help="wardrobe-spec.json path")
    parser.add_argument(
        "--skip-wardrobe",
        action="store_true",
        help="only structural + demo smoke checks (legacy demo harness)",
    )
    args = parser.parse_args()
    out = args.out_dir
    prefix = args.prefix or detect_prefix(out)
    if not prefix:
        print(json.dumps({"ok": False, "errors": ["no *.model3.json found"]}, indent=2))
        return 1
    spec = None if args.skip_wardrobe or not args.spec.is_file() else load_spec(args.spec)
    summary = inspect_directory(
        out,
        prefix=prefix,
        spec=spec,
        require_demo_smoke=(prefix == "demo"),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
