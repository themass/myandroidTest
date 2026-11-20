#!/usr/bin/env bash
# 从 ModelScope 直接下载 Tao 引擎所需模型（MNN/TaoAvatar-NNR-MNN + MNN/UniTalker-MNN，约 532MB），
# 落盘到 .superpowers/dh-packs/<模型名>/，之后由 seed-assets.sh tao 推送到设备。
#
# URL 形态已实测可用（HEAD 200）：
#   https://www.modelscope.cn/models/<repo>/resolve/master/<file>
# 只下载渲染 NNR 网格 + UniTalker 需要的文件；configuration.json/.gitattributes 跳过。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACKS="$REPO_ROOT/.superpowers/dh-packs"
BASE="https://www.modelscope.cn/models"

fetch_file() { # fetch_file <repo> <dest-dir> <file...>
  local repo="$1" dest="$PACKS/$2"; shift 2
  mkdir -p "$dest"
  local f url
  for f in "$@"; do
    url="$BASE/$repo/resolve/master/$f"
    if [ -s "$dest/$f" ]; then echo "== 已有 $repo/$f"; continue; fi
    echo "== 下载 $repo/$f"
    curl -fL --retry 5 --retry-all-errors -C - -o "$dest/$f" "$url"
  done
}

fetch_file MNN/TaoAvatar-NNR-MNN TaoAvatar-NNR-MNN \
  compute.nnr background.nnr render_full.nnr render_half.nnr render_vr_full.nnr render_vr_half.nnr

fetch_file MNN/UniTalker-MNN UniTalker-MNN \
  audio2verts.mnn body_converter.mnn body_params.bin idle_speech_slices.json

echo "完成。推送到设备: scripts/dh-lab/seed-assets.sh tao"
