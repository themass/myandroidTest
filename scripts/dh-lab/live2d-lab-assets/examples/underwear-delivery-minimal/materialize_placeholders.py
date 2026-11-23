#!/usr/bin/env python3
"""Write 1x1 placeholder PNGs for the minimal delivery example only.

Not production art — structure check for wardrobe_layout / check_delivery_ready.
"""
import base64
from pathlib import Path

PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII="
)

NAMES = ("body.png", "lace.png", "sport.png", "cat.png")


def main() -> None:
    here = Path(__file__).resolve().parent
    for name in NAMES:
        dest = here / name
        if not dest.exists():
            dest.write_bytes(PNG_1X1)
            print(f"wrote {dest.name}")
        else:
            print(f"exists {dest.name}")


if __name__ == "__main__":
    main()
