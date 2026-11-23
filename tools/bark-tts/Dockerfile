# Bark PoC image. NOT a production Dockerfile — see README "Not for prod".
#
# Weights are deliberately NOT baked in: 4.6 GB (small) / 13 GB (full) in an
# image layer is unpushable and un-updatable. They are mounted as a volume,
# pre-populated with scripts/fetch_weights.py.
#
#   docker build -t bark-tts:poc -f tools/bark-tts/Dockerfile tools/bark-tts
#   docker run --rm -p 8088:8088 \
#     -v $HOME/.cache/suno/bark_v0:/data/bark \
#     -e HF_ENDPOINT=https://hf-mirror.com \
#     bark-tts:poc
#
# GPU (CUDA): build from a torch CUDA base instead of the CPU default, and add
#   --gpus all
# at run time. VRAM sizing is in README.md.

FROM python:3.12-slim

# torch pulls ~800 MB (CPU wheel). Pin a CUDA base + --index-url for GPU builds.
RUN pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu

WORKDIR /srv/bark

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Bark from upstream git (MIT). Pin the SHA once you have tested against it —
# `main` is not a stable surface.
RUN pip install --no-cache-dir "git+https://github.com/suno-ai/bark.git@f4f32d4"

COPY app/server.py app/server.py

# Bark computes this cache path itself; point it at the mounted volume.
ENV XDG_CACHE_HOME=/data \
    SUNO_USE_SMALL_MODELS=True \
    SUNO_ENABLE_MPS=False \
    BARK_DEFAULT_SPEAKER=v2/zh_speaker_3 \
    HF_HUB_DISABLE_TELEMETRY=1

EXPOSE 8088

# One worker on purpose: bark keeps its models in a module-global dict, so N
# workers means N copies of 4.6 GB of weights and unsynchronised mutation.
CMD ["uvicorn", "app.server:app", "--host", "0.0.0.0", "--port", "8088", "--workers", "1"]
