#!/usr/bin/env bash
# 生成一组数字人唇形/动作测试语音（16 kHz 单声道 PCM16 LE WAV），输出到 scripts/dh-lab/test-audio/。
#
# 不经过真实 TTS/LLM：用 macOS 自带 `say -v Tingting`（zh_CN）合成固定文案，
# 每一类口型场景一个文件，方便重复对比三家引擎（Duix 推 PCM、Live2D 推音量、Tao 推 PCM）。
#
# 用法:  scripts/dh-lab/make-test-audio.sh          # 全部重新生成
#        scripts/dh-lab/make-test-audio.sh list      # 只列出现有文件
#
# 推送到设备: scripts/dh-lab/seed-assets.sh audio
set -euo pipefail

OUT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-audio"
VOICE="${VOICE:-Tingting}"
mkdir -p "$OUT"

if [ "${1:-}" = "list" ]; then ls -la "$OUT"; exit 0; fi

which say ffmpeg >/dev/null || { echo "需要 macOS 的 say 和 ffmpeg"; exit 1; }
# `say -o` 按输出文件的扩展名决定格式：目标没有 .aiff 扩展名时会静默写出 0 字节文件（实测）。
# mktemp 要求 X 在名字末尾，所以扩展名加在 mktemp 结果之后。
probe=$(mktemp /tmp/dh-audio-probe-XXXXXX).aiff
say -v "$VOICE" -o "$probe" "测试" || { echo "语音 $VOICE 不可用（say -v '?' 查看）"; rm -f "$probe"; exit 1; }
[ -s "$probe" ] || { echo "语音 $VOICE 输出为空"; rm -f "$probe"; exit 1; }
rm -f "$probe"

# say -> aiff -> 16k mono pcm_s16le wav。第 3 参为 -r 语速（默认 190）。
say_wav() { # say_wav <name> <text> [rate]
  local name="$1" text="$2" rate="${3:-190}" tmp
  tmp=$(mktemp /tmp/dh-audio-XXXXXX).aiff
  echo "== $name ($rate 字/分): $text"
  say -v "$VOICE" -r "$rate" -o "$tmp" "$text"
  ffmpeg -hide_banner -loglevel error -y -i "$tmp" -ar 16000 -ac 1 -c:a pcm_s16le "$OUT/$name.wav"
  rm -f "$tmp"
}

# 1) 普通整句：基本唇形跟随
say_wav sentence "你好，我是数字人助手，很高兴见到你。"

# 2) 单元音连读：a/o/e/i/u，口型张合幅度最夸张，验证嘴型极值
say_wav vowels "啊，哦，额，衣，乌。啊，哦，额，衣，乌。啊，哦，额，衣，乌。" 150

# 3) 长句：连续二十秒以上，验证不掉帧、不积压
say_wav long "今天天气不错，我们来做一组完整的测试。先说一段普通的话，让嘴型跟着声音动。然后连读几个单元音，看看嘴巴张合的幅度够不够大。再说一段快一点的，看看会不会跟不上。中间会停下来，这时候嘴巴应该合上，回到安静的表情。最后再说一句结束的话，整个流程就跑完了。这种连续播放最能看出渲染管线稳不稳。" 200

# 4) 停顿：句中 1.5 秒静音，验证静音段嘴部回落。
#    静音段直接生成 raw s16le，和两段语音一起用 concat 滤镜统一重编码。
#    （以前用 `concat:协议 + -c copy` 拼 aiff，实测丢段只剩 1.4s，不能再用。）
tmpa=$(mktemp /tmp/dh-audio-XXXXXX).aiff; tmpb=$(mktemp /tmp/dh-audio-XXXXXX).aiff
raw=$(mktemp /tmp/dh-audio-XXXXXX).raw
trap 'rm -f "$tmpa" "$tmpb" "$raw"' EXIT
echo "== pauses (1.5s 静音间隔)"
say -v "$VOICE" -r 190 -o "$tmpa" "第一句说完之后，"
say -v "$VOICE" -r 190 -o "$tmpb" "隔一会儿再说第二句。"
# 1.5s 静音 @16k mono s16le = 48000 字节
head -c 48000 /dev/zero > "$raw"
ffmpeg -hide_banner -loglevel error -y \
  -i "$tmpa" -f s16le -ar 16000 -ac 1 -i "$raw" -i "$tmpb" \
  -filter_complex "[0:a]aresample=16000,aformat=sample_fmts=s16:channel_layouts=mono[a];[2:a]aresample=16000,aformat=sample_fmts=s16:channel_layouts=mono[b];[a][1:a][b]concat=n=3:v=0:a=1[out]" \
  -map "[out]" -c:a pcm_s16le "$OUT/pauses.wav"

# 5) 快语速：验证引擎跟得上密集中文音节
say_wav fast "板凳宽扁担长板凳没有扁担长扁担没有板凳宽" 300

# 6) 语气起伏：问句+感叹，验证口型不是单调开合
say_wav tone "真的吗？太好了！我们一起把三个引擎都跑通吧。"

# 校验：每个文件必须非静音（mean_volume > -60dB）且时长符合预期下限，防止再产出静音/丢段文件
CHECKS="sentence 2 vowels 6 long 20 pauses 3 fast 2 tone 3"
fail=0
set -- $CHECKS
while [ $# -gt 0 ]; do
  name="$1"; min="$2"; shift 2
  f="$OUT/$name.wav"
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f")
  mean=$(ffmpeg -hide_banner -i "$f" -af volumedetect -f null - 2>&1 | sed -n 's/.*mean_volume: \(.*\) dB.*/\1/p')
  ok=$(echo "$d >= $min && ${mean:--99} > -60" | bc -l)
  printf "  %-14s %6.1fs  mean %sdB  %s\n" "$name" "$d" "${mean:-?}" "$([ "$ok" = 1 ] && echo OK || echo FAIL)"
  [ "$ok" = 1 ] || fail=1
done
[ "$fail" = 0 ] || { echo "!! 有文件校验失败，见上"; exit 1; }

echo
echo "完成: $OUT （推送到设备: scripts/dh-lab/seed-assets.sh audio）"
