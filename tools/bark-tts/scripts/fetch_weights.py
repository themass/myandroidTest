#!/usr/bin/env python3
"""Download the Bark *small* checkpoints into the cache dir Bark itself reads from.

Bark's `generation.py` looks for `<XDG_CACHE_HOME|~/.cache>/suno/bark_v0/<file_name>`
and only calls `hf_hub_download` when the file is missing. hf_hub_download is a
single stream and crawls on this network (~0.3-0.6 MB/s), so we fetch with
parallel HTTP range requests instead and write straight to the expected path.

Small set (SUNO_USE_SMALL_MODELS=True) == text.pt + coarse.pt + fine.pt
= 2.32 GB + 1.25 GB + 1.11 GB = ~4.68 GB on disk.

Usage:
    python3 fetch_weights.py                 # all three, small set
    python3 fetch_weights.py --which text    # one model
    python3 fetch_weights.py --full          # text_2 / coarse_2 / fine_2 (~13 GB)
    python3 fetch_weights.py --cache-dir DIR --endpoint https://hf-mirror.com
"""

from __future__ import annotations

import argparse
import concurrent.futures
import os
import sys
import tempfile
import time
import urllib.request

REPO = "suno/bark"

SMALL = ["text.pt", "coarse.pt", "fine.pt"]
FULL = ["text_2.pt", "coarse_2.pt", "fine_2.pt"]

# Sanity sizes so a truncated file is never mistaken for a finished one.
EXPECT = {
    "text.pt": 2_315_982_725,
    "coarse.pt": 1_251_939_909,
    "fine.pt": 1_107_111_557,
    "text_2.pt": 5_353_258_741,
    "coarse_2.pt": 3_934_534_533,
    "fine_2.pt": 3_741_740_229,
}


def default_cache_dir() -> str:
    base = os.getenv("XDG_CACHE_HOME") or os.path.join(os.path.expanduser("~"), ".cache")
    return os.path.join(base, "suno", "bark_v0")


def fetch_piece(
    url: str, path: str, piece: int, start: int, end: int, attempts: int = 6
) -> int:
    """Fetch one byte range into a scratch file. Returns bytes written."""
    target = os.path.join(path, f"part{piece:03d}")
    have = os.path.getsize(target) if os.path.exists(target) else 0
    want = end - start + 1
    if have >= want:
        return have
    for attempt in range(1, attempts + 1):
        # Append-resume: `have` bytes already on disk are trustworthy because
        # each part is written by exactly one worker, sequentially.
        req = urllib.request.Request(
            url, headers={"Range": f"bytes={start + have}-{end}"}
        )
        try:
            with urllib.request.urlopen(req, timeout=60) as resp, open(
                target, "ab"
            ) as fh:
                while True:
                    chunk = resp.read(1 << 16)
                    if not chunk:
                        break
                    fh.write(chunk)
            written = os.path.getsize(target)
            if written >= want:
                return written
            have = written
        except Exception as exc:  # transient: retry the remaining tail
            print(
                f"  part{piece:03d} attempt {attempt}/{attempts} failed: {exc}",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(min(2 ** attempt, 15))
            have = os.path.getsize(target) if os.path.exists(target) else 0
    raise RuntimeError(f"part{piece:03d} incomplete: {have}/{want} bytes")


def download(
    name: str,
    cache_dir: str,
    endpoint: str,
    workers: int,
    piece_bytes: int,
) -> None:
    url = f"{endpoint}/{REPO}/resolve/main/{name}"
    total = EXPECT[name]
    dest = os.path.join(cache_dir, name)
    if os.path.exists(dest) and os.path.getsize(dest) == total:
        print(f"{name}: already complete ({total} bytes), skipping")
        return

    os.makedirs(cache_dir, exist_ok=True)
    workdir = os.path.join(cache_dir, f".{name}.parts")
    os.makedirs(workdir, exist_ok=True)

    starts = list(range(0, total, piece_bytes))
    pieces = [(i, s, min(s + piece_bytes - 1, total - 1)) for i, s in enumerate(starts)]
    print(f"{name}: {total/1e9:.2f} GB in {len(pieces)} pieces x {workers} streams")

    t0 = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [
            pool.submit(fetch_piece, url, workdir, i, s, e) for (i, s, e) in pieces
        ]
        done = 0
        for fut in concurrent.futures.as_completed(futures):
            done += fut.result()
            el = time.time() - t0
            print(
                f"  {done/1e6:.0f}/{total/1e6:.0f} MB"
                f"  {done/1e6/el:.1f} MB/s  [{el/60:.1f} min]",
                flush=True,
            )

    # Concatenate in order, then verify before declaring success.
    tmp = dest + ". assembling"
    with open(tmp, "wb") as out:
        for i in range(len(pieces)):
            part = os.path.join(workdir, f"part{i:03d}")
            expect = pieces[i][2] - pieces[i][1] + 1
            got = os.path.getsize(part)
            if got != expect:
                raise RuntimeError(f"{part} is {got} bytes, expected {expect}")
            with open(part, "rb") as fh:
                while True:
                    chunk = fh.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
    if os.path.getsize(tmp) != total:
        raise RuntimeError(f"{name} assembled to {os.path.getsize(tmp)}, expected {total}")
    os.replace(tmp, dest)
    for i in range(len(pieces)):
        os.remove(os.path.join(workdir, f"part{i:03d}"))
    os.rmdir(workdir)
    print(f"{name}: OK ({total/1e9:.2f} GB)")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache-dir", default=default_cache_dir())
    ap.add_argument(
        "--endpoint",
        default=os.getenv("HF_ENDPOINT", "https://hf-mirror.com"),
        help="Hugging Face endpoint; hf-mirror.com is much faster from CN",
    )
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument(
        "--piece-mb", type=int, default=64, help="range size per stream"
    )
    ap.add_argument("--which", choices=["text", "coarse", "fine"], action="append")
    ap.add_argument("--full", action="store_true", help="grab the big v2 checkpoints")
    args = ap.parse_args()

    names = args.which if args.which else (FULL if args.full else SMALL)
    files = [n if n.endswith(".pt") else f"{n}.pt" for n in names]
    for name in files:
        download(name, args.cache_dir, args.endpoint, args.workers, args.piece_mb * 2**20)
    print(f"\nAll weights in {args.cache_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
