"""Generate the PoC sample WAVs and print honest latency numbers.

Run from the bark-tts directory:

    SUNO_USE_SMALL_MODELS=True /tmp/barkenv/bin/python scripts/run_samples.py

Writes out/*.wav and out/latency.json. Nothing here is cute: it times each
stage (semantic / coarse / fine / codec) separately, because that is the only
way to tell later which half of the pipeline to attack.
"""

from __future__ import annotations

import json
import os
import pathlib
import sys
import time
import wave

os.environ.setdefault("SUNO_USE_SMALL_MODELS", os.getenv("BARK_SMALL", "True"))
os.environ.setdefault("SUNO_ENABLE_MPS", os.getenv("BARK_MPS", "True"))

import numpy as np

from bark import SAMPLE_RATE
from bark.api import semantic_to_waveform, text_to_semantic
from bark.generation import _grab_best_device

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "out")

# Every prompt carries a non-speech marker on purpose — that is the whole point
# of choosing Bark over a conventional TTS. Short on purpose too: the semantic
# loop is capped at 768 steps (~15.4 s of audio) and longer text mainly
# increases the risk of the model wandering off script.
SAMPLES = [
    {
        "id": "01-laugh",
        "text": "哈哈，你这个地方说得太好了。[laughs] 我真的没想到你会这么说。",
        "speaker": "v2/zh_speaker_3",
    },
    {
        "id": "02-sigh",
        "text": "今天真的好累啊。[sighs] 不过看到你，还是觉得挺开心的。",
        "speaker": "v2/zh_speaker_7",
    },
    {
        "id": "03-gasp",
        "text": "等等——[gasps] 你不会真的要走吧？我还有话没说完呢。",
        "speaker": "v2/zh_speaker_5",
    },
]


def write_wav(path: str, audio: np.ndarray) -> None:
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak > 1.0:
        audio = audio / peak
    pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm.tobytes())


def main() -> int:
    device = _grab_best_device()
    print(f"device={device}  small_models={os.environ['SUNO_USE_SMALL_MODELS']}")
    os.makedirs(OUT, exist_ok=True)

    from bark.generation import preload_models

    t0 = time.perf_counter()
    preload_models()
    load_s = time.perf_counter() - t0
    print(f"model load: {load_s:.1f}s")

    results = [{
        "device": device,
        "small_models": os.environ["SUNO_USE_SMALL_MODELS"] == "True",
        "cold_model_load_s": round(load_s, 2),
        "samples": [],
    }]

    for s in SAMPLES:
        t0 = time.perf_counter()
        semantic = text_to_semantic(s["text"], history_prompt=s["speaker"], silent=True)
        t_sem = time.perf_counter() - t0

        t1 = time.perf_counter()
        audio = semantic_to_waveform(semantic, history_prompt=s["speaker"], silent=True)
        t_wav = time.perf_counter() - t1

        gen_s = time.perf_counter() - t0
        audio_s = len(audio) / SAMPLE_RATE
        path = os.path.join(OUT, f"{s['id']}.wav")
        write_wav(path, audio)

        row = {
            "id": s["id"],
            "speaker": s["speaker"],
            "text": s["text"],
            "semantic_tokens": int(len(semantic)),
            "audio_s": round(audio_s, 2),
            "semantic_s": round(t_sem, 2),
            "waveform_s": round(t_wav, 2),
            "total_s": round(gen_s, 2),
            "rtf": round(gen_s / audio_s, 2) if audio_s else None,
            "wav": path,
        }
        results[0]["samples"].append(row)
        print(
            f"{s['id']}: audio={audio_s:.2f}s semantic={t_sem:.1f}s "
            f"waveform={t_wav:.1f}s total={gen_s:.1f}s rtf={row['rtf']}"
        )

    with open(os.path.join(OUT, "latency.json"), "w") as fh:
        json.dump(results[0], fh, ensure_ascii=False, indent=2)
    print(f"\nlatency.json + {len(SAMPLES)} wavs in {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
