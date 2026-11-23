# psd2live 换装产能评估 harness

评估 `tsunehimatoi/psd2live`（v3.8.4）能否替代/辅助 Cubism Editor，
为我们 Live2D 换装线（`wardrobe-spec.json` 内衣槽 `pending-art`）产出新 ArtMesh + Opacity 参数。

**本目录不含 psd2live 源码或 DMG。** 只放我们自己写的生成脚本、跑法与证据摘要。

## 结论摘要

详见 [`EVAL.md`](EVAL.md)。一句话：**半替代可主力候选**——官方 CLI 从分层 PSD 直出真 `.moc3`（不依赖 Cubism Editor），ArtMesh 与标准 Param* 齐全；Layout toggle `ParamWear_test` 已实跑进 moc3/cdi3 字符串表。

## 前置

1. 下载官方 macOS arm64 DMG（勿入仓）：
   https://github.com/tsunehimatoi/psd2live/releases/download/v3.8.4/PSD2Live-3.8.4-macos-arm64.dmg
2. 校验 `SHA256SUMS.txt`，挂载：
   ```bash
   hdiutil attach -nobrowse -readonly PSD2Live-3.8.4-macos-arm64.dmg
   ```
3. Python 3 + pillow（生成 PSD）；可选 psd-tools（自检图层栈）。

## 复跑

```bash
# 1) 生成最小分层 PSD（仓库外输出目录）
python3 -m venv /tmp/psd2live-gen/venv
/tmp/psd2live-gen/venv/bin/pip install pillow psd-tools
mkdir -p /tmp/psd2live-gen
/tmp/psd2live-gen/venv/bin/python -I scripts/dh-lab/psd2live-eval/make_psd.py \
  /tmp/psd2live-gen/demo.psd

# 2) CLI：PSD → moc3 族（默认写 moc3+cmo3+json）
rm -rf /tmp/psd2live-gen/out && mkdir -p /tmp/psd2live-gen/out
/Volumes/PSD2Live/PSD2Live.app/Contents/MacOS/PSD2Live \
  --input /tmp/psd2live-gen/demo.psd \
  --output /tmp/psd2live-gen/out \
  --lang zh
# 注意：3.8.4 CLI 在写出后偶发挂在 IMK 消息端口；文件齐了可 kill 进程。

# 3) 断言摘要
python3 -I scripts/dh-lab/psd2live-eval/inspect_export.py /tmp/psd2live-gen/out
```

期望：`out/demo.moc3` 魔数 `MOC3`、`demo.model3.json` 引用闭合、cdi3 含 `ArtMeshTopwear` 等与 PSD 层对应的 ArtMesh、标准 `ParamAngleX`/`ParamEyeLOpen` 等。

## 证据

[`evidence/`](evidence/) 保留本次跑通的 sidecar 摘要（**不含** `.moc3`/贴图大文件）：

- `demo.model3.json` / `demo.cdi3.json` / `demo.smile.exp3.json` / `demo.psd2live.json`
- `moc3-strings.json`：从 demo.moc3 扫出的 Param*/ArtMesh* 字符串表
- `layout-moc3-strings.json` / `layout-variant-excerpt.json` / `layout.*.json`：ParamWear_test 差分专项证据
- `demo-merged.png`：输入 PSD 合成预览
- `cli-run.txt`：CLI 输出摘要

## 纪律

- 禁止 docker；本评估不占 adb 设备。
- 不要把 `/tmp/psd2live-eval` 源码树或 DMG 拷进仓库。
