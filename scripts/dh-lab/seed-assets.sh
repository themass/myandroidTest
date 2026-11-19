#!/usr/bin/env bash
# 开发机代下载 DH 引擎素材并推到真机缓存，替代设备端下载（设备到 GitHub 可能很慢/超时）。
#
# 用法:
#   scripts/dh-lab/seed-assets.sh            # 三家全部（Live2D 需自备 ATRI，见下）
#   scripts/dh-lab/seed-assets.sh duix tao   # 只推指定引擎
#   SERIAL=xxxx scripts/dh-lab/seed-assets.sh
#
# 推送布局与 AssetStore 完全一致（应用内部 filesDir/dh-engines/<engine>/…，经 run-as 落盘，
# 要求 debuggable 包），并补写 marker 文件（tmp/<id>，最后写），所以 AssetStore.isReady()
# 认它，不会重复下载。布局改动时必须同步 AssetManifest.kt / AssetStore.markerRelativePath。
#
# Live2D：ATRI 无公开 URL（清单故意留空），自备模型目录后放到
#   .superpowers/dh-packs/ATRI/ 本脚本会推到 cacheRoot(LIVE2D)/ATRI/。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACKS="$REPO_ROOT/.superpowers/dh-packs"
APP_ID="com.openapi.ks.chat"
DEVICE_TMP="/data/local/tmp/dh-seed"
DATA="/data/user/0/$APP_ID"
SERIAL="${SERIAL:-}"

mkdir -p "$PACKS"
ADB=(adb ${SERIAL:+-s "$SERIAL"})

ra() { "${ADB[@]}" shell "run-as $APP_ID sh -c \"$*\""; }

fetch() { # fetch <out> <url>
  local out="$PACKS/$1" url="$2"
  if [ -s "$out" ]; then echo "== 已有 $1"; else
    echo "== 下载 $1"
    curl -fL --retry 5 --retry-all-errors -C - -o "$out" "$url"
  fi
}

# 把主机上 unpack 好的目录推入应用内部 filesDir/dh-engines/<engine>/<rel>/，最后写 marker。
push_unpack() { # push_unpack <engine> <zip> <relativePath>
  local engine="$1" zip="$2" rel="$3"
  local tmp="$PACKS/.unzip-$engine"
  rm -rf "$tmp"; mkdir -p "$tmp"
  # 与 AssetStore.extractZip 一致：若 zip 的唯一顶层目录与 relativePath 末段同名
  # （gj_dh_res.zip 的 gj_dh_res/ 等），剥掉这一层；否则原样保留（native zip 的 app/）。
  unzip -q "$PACKS/$zip" -d "$tmp"
  local leaf base count only
  leaf=$(basename "$rel")
  base="$tmp/$leaf"
  count=$(find "$tmp" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')
  only=$(find "$tmp" -mindepth 1 -maxdepth 1 | head -1)
  if [ "$count" = "1" ] && [ "$only" = "$base" ] && [ -d "$base" ]; then tmp="$base"; fi
  local dest_rel="files/dh-engines/$engine/$rel"
  local parent name marker_rel
  parent="${rel%/*}"; name="${rel##*/}"
  if [ "$parent" = "$rel" ]; then marker_rel="files/dh-engines/$engine/tmp/$name"
  else marker_rel="files/dh-engines/$engine/$parent/tmp/$name"; fi
  local stage="$DEVICE_TMP/$engine-$(basename "$rel")"
  "${ADB[@]}" shell "rm -rf '$stage' && mkdir -p '$stage'"
  "${ADB[@]}" push "$tmp/." "$stage/" >/dev/null
  ra "rm -rf $DATA/$dest_rel"
  ra "mkdir -p $DATA/$dest_rel && cp -R $stage/. $DATA/$dest_rel/"
  ra "mkdir -p $DATA/\$(dirname $marker_rel) && touch $DATA/$marker_rel"
  echo "== $engine/$rel 已推送并打 marker"
}

ENGINES="${*:-duix live2d tao}"

if echo " $ENGINES " | grep -q " audio "; then
  AUDIO_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-audio"
  ls "$AUDIO_SRC"/*.wav >/dev/null 2>&1 || { echo "!! 先生成: scripts/dh-lab/make-test-audio.sh"; exit 1; }
  "${ADB[@]}" shell "rm -rf '$DEVICE_TMP/audio' && mkdir -p '$DEVICE_TMP/audio'"
  "${ADB[@]}" push "$AUDIO_SRC/." "$DEVICE_TMP/audio/" >/dev/null
  ra "rm -rf $DATA/files/dh-lab-audio && mkdir -p $DATA/files/dh-lab-audio && cp $DEVICE_TMP/audio/*.wav $DATA/files/dh-lab-audio/"
  echo "== 测试语音已推送到 files/dh-lab-audio/"
fi

if echo " $ENGINES " | grep -q " duix "; then
  fetch gj_dh_res.zip "https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip"
  fetch Oliver.zip "https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip"
  push_unpack duix gj_dh_res.zip "model/gj_dh_res"
  push_unpack duix Oliver.zip "model/Oliver"
fi

if echo " $ENGINES " | grep -q " live2d "; then
  if [ -d "$PACKS/ATRI" ]; then
    local_stage="$DEVICE_TMP/live2d-ATRI"
    "${ADB[@]}" shell "rm -rf '$local_stage' && mkdir -p '$local_stage'"
    "${ADB[@]}" push "$PACKS/ATRI/." "$local_stage/" >/dev/null
    ra "rm -rf $DATA/files/dh-engines/live2d/ATRI"
    ra "mkdir -p $DATA/files/dh-engines/live2d/ATRI && cp -R $local_stage/. $DATA/files/dh-engines/live2d/ATRI/"
    echo "== live2d/ATRI 已推送（无 marker：ATRI 不在清单里，靠目录存在判定）"
  else
    echo "!! 跳过 Live2D：把 ATRI 模型目录放到 $PACKS/ATRI/ 再运行"
  fi
fi

if echo " $ENGINES " | grep -q " tao "; then
  fetch native-libs-arm64-v8a.zip "https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip"
  push_unpack tao native-libs-arm64-v8a.zip "native-libs"
  for model in TaoAvatar-NNR-MNN UniTalker-MNN; do
    if [ -d "$PACKS/$model" ]; then
      stage="$DEVICE_TMP/tao-$model"
      "${ADB[@]}" shell "rm -rf '$stage' && mkdir -p '$stage'"
      "${ADB[@]}" push "$PACKS/$model/." "$stage/" >/dev/null
      ra "rm -rf $DATA/files/dh-engines/tao/$model"
      ra "mkdir -p $DATA/files/dh-engines/tao/$model && cp -R $stage/. $DATA/files/dh-engines/tao/$model/"
      echo "== tao/$model 已推送（目录存在即 modelsReady）"
    else
      echo "!! 跳过 tao/${model}：上游只有 ModelScope SDK 分发，模型目录请放 $PACKS/$model/"
    fi
  done
fi

echo "完成。冷启动 App 后进通话页即可看到对应引擎可用。"
