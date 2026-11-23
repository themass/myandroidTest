"""Minimal HTTP front end for Suno Bark: POST text+speaker -> WAV.

PoC scope only. What this file deliberately does *not* do:
  * no auth, no rate limiting, no queue persistence  -> see README "Not for prod"
  * no streaming of partial audio                    -> see README "Latency"

Two facts from `bark/generation.py` drive the design and are worth stating up
front because they are easy to get wrong:

1. There is no `speaker=` parameter. `generate_audio(text, history_prompt=...)`
   is the voice selector; `history_prompt` is either a name in ALLOWED_PROMPTS
   (`zh_speaker_3`, `v2/zh_speaker_3`, `announcer`) or a dict of three numpy
   arrays. `_load_history_prompt` (generation.py:356) raises
   "history prompt not found" for anything else, so we validate up front and
   return 400 instead of a 500.

2. `[laughs]` / `[sighs]` / `[gasps]` are NOT special tokens. They go through
   the ordinary BERT tokenizer as subwords and the text->semantic GPT has
   learned what to do with them. So the only correct server behaviour is to
   pass them through untouched in the text. Do not HTML-escape, do not strip
   brackets, do not run them through a Markdown/sanitise step anywhere between
   the app and here. (`_normalize_whitespace`, generation.py:346, collapses
   runs of whitespace only — it is safe for the markers.)

Hard limits to remember:
  * semantic output is capped at 768 steps at SEMANTIC_RATE_HZ=49.9
    -> one call produces at most ~15.4 s of audio (generation.py:447).
  * input text is truncated to 256 BERT tokens (generation.py:417).
"""

from __future__ import annotations

import io
import logging
import os
import threading
import time
import wave

# Bark reads these at import time (generation.py:92-94), so they MUST be set
# before `import bark`. Default to the small checkpoints: ~4.6 GB of weights,
# ~8 GB VRAM, and the only variant that fits a 16 GB Mac.
os.environ.setdefault("SUNO_USE_SMALL_MODELS", os.getenv("BARK_SMALL", "True"))
os.environ.setdefault("SUNO_ENABLE_MPS", os.getenv("BARK_MPS", "True"))
os.environ.setdefault("SUNO_OFFLOAD_CPU", os.getenv("BARK_OFFLOAD_CPU", "False"))

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from bark import SAMPLE_RATE
from bark.api import semantic_to_waveform, text_to_semantic
from bark.generation import ALLOWED_PROMPTS, SEMANTIC_RATE_HZ, preload_models

log = logging.getLogger("bark-tts")
logging.basicConfig(level=os.getenv("BARK_LOG_LEVEL", "INFO").upper())

app = FastAPI(title="bark-tts", version="0.1-poc")

# The bark package keeps its loaded models in a module-global dict and every
# generate_* reads and writes it; OFFLOAD_CPU additionally moves models between
# devices inside a call. Two concurrent generations therefore corrupt each other.
# One process, one generation at a time — everything else queues on this lock.
_GEN_LOCK = threading.Lock()
_STATS = {
    "requests": 0,
    "errors": 0,
    "audio_s_total": 0.0,
    "gen_s_total": 0.0,
    "last": None,
}

# 768 semantic steps is the loop bound baked into generate_text_semantic.
MAX_OUTPUT_S = 768.0 / SEMANTIC_RATE_HZ


def _default_speaker() -> str:
    # zh_speaker_* is the right default for this app; v2 prompts are the newer,
    # better multilingual set, hence the v2/ prefix.
    return os.getenv("BARK_DEFAULT_SPEAKER", "v2/zh_speaker_3")


class TtsRequest(BaseModel):
    text: str = Field(
        ...,
        min_length=1,
        max_length=1200,
        description=(
            "Text to speak. Non-speech markers such as [laughs], [sighs], "
            "[gasps], [clears throat] are passed through verbatim."
        ),
    )
    speaker: str | None = Field(
        None,
        description="A Bark history prompt, e.g. v2/zh_speaker_3 or en_speaker_0.",
    )
    text_temp: float = Field(0.7, ge=0.0, le=2.0)
    waveform_temp: float = Field(0.7, ge=0.0, le=2.0)
    history_prompt: str | None = Field(
        None,
        description=(
            "Alias for speaker, kept because Bark's own naming is history_prompt. "
            "If both are given, speaker wins."
        ),
    )


def _resolve_speaker(req: TtsRequest) -> str:
    speaker = req.speaker or req.history_prompt or _default_speaker()
    if speaker not in ALLOWED_PROMPTS:
        raise HTTPException(
            status_code=400,
            detail=(
                f"unknown speaker {speaker!r}. Allowed: a name from "
                f"bark/assets/prompts (e.g. v2/zh_speaker_0..9, en_speaker_0..9, "
                f"announcer), or an .npz path on the server."
            ),
        )
    return speaker


def _wav_bytes(audio: np.ndarray) -> bytes:
    """16-bit mono PCM WAV at Bark's native 24 kHz."""
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak > 1.0:
        audio = audio / peak
    pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2")
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm.tobytes())
    return buf.getvalue()


@app.on_event("startup")
def _startup() -> None:
    # Warm the process: cold model load on a first request would otherwise look
    # like a ~1 minute TTS latency and poison the numbers.
    t0 = time.perf_counter()
    preload_models()
    log.info("models loaded in %.1fs", time.perf_counter() - t0)


@app.get("/healthz")
def healthz() -> dict:
    return {
        "ok": True,
        "small_models": os.environ["SUNO_USE_SMALL_MODELS"],
        "mps_enabled": os.environ["SUNO_ENABLE_MPS"],
        "sample_rate": SAMPLE_RATE,
        "max_output_s": round(MAX_OUTPUT_S, 1),
    }


@app.get("/stats")
def stats() -> dict:
    out = dict(_STATS)
    gen, audio = out.pop("gen_s_total", 0.0), out.pop("audio_s_total", 0.0)
    out["realtime_factor"] = round(gen / audio, 2) if audio else None
    return out


@app.post("/tts")
def tts(req: TtsRequest) -> Response:
    speaker = _resolve_speaker(req)
    _STATS["requests"] += 1

    t0 = time.perf_counter()
    try:
        with _GEN_LOCK:
            # Deliberately not generate_audio(): going a level lower lets us cap
            # the output duration explicitly instead of silently being cut at
            # 768 tokens by the generation loop.
            semantic = text_to_semantic(
                req.text,
                history_prompt=speaker,
                temp=req.text_temp,
                silent=True,
            )
            audio = semantic_to_waveform(
                semantic,
                history_prompt=speaker,
                temp=req.waveform_temp,
                silent=True,
            )
    except HTTPException:
        raise
    except Exception as exc:
        _STATS["errors"] += 1
        log.exception("generation failed")
        raise HTTPException(status_code=500, detail=f"generation failed: {exc}") from exc

    gen_s = time.perf_counter() - t0
    wav = _wav_bytes(audio)
    audio_s = len(audio) / SAMPLE_RATE

    _STATS["gen_s_total"] += gen_s
    _STATS["audio_s_total"] += audio_s
    _STATS["last"] = {
        "speaker": speaker,
        "chars": len(req.text),
        "audio_s": round(audio_s, 2),
        "gen_s": round(gen_s, 2),
        "rtf": round(gen_s / audio_s, 2) if audio_s else None,
    }
    log.info("tts ok speaker=%s audio=%.2fs gen=%.2fs rtf=%.2f", speaker, audio_s, gen_s, gen_s / audio_s)

    return Response(
        content=wav,
        media_type="audio/wav",
        headers={
            "X-Generation-Seconds": f"{gen_s:.3f}",
            "X-Audio-Seconds": f"{audio_s:.3f}",
            "X-Realtime-Factor": f"{gen_s / audio_s:.3f}" if audio_s else "0",
            "Content-Disposition": 'inline; filename="bark.wav"',
        },
    )
