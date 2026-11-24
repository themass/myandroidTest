# psd2live 换装产能评估 harness（工具侧）

评估对象：`tsunehimatoi/psd2live` 3.8.4（GPL 应用 / MIT IR）。对照 `wardrobe-spec.json` 内衣槽 `pending-art`。**本目录只收我们写的脚本与跑法，禁止把 psd2live 源码或 DMG 入仓。**

## 结论（2026-11-23）

**半替代 → 主力候选**：官方 DMG CLI 能从分层 PSD **完全不碰 Cubism Editor** 直接写出真 `.moc3`（魔数 `MOC3`，本机实测 1,105,344 B）+ model3/cdi3/exp3/physics3 族。Layout toggle `ParamWear_test` 已实跑进 moc3/cdi3；**Editor** `view_render_poses` 已验 0/0.5/1 显隐（合成马甲，非正式美术）。无头 CLI `--input` **只吃 PSD**；Layout 走 MCP。详见 [`EVAL.md`](EVAL.md)、设计索引 [`docs/design/psd2live-wardrobe-evidence-20261123.md`](../../../docs/design/psd2live-wardrobe-evidence-20261123.md)、马甲 [`WARDROBE-SMOKE.md`](WARDROBE-SMOKE.md)。

许可：跑官方发布包、产物进仓库、APK 不含 psd2live 代码 → GPL 无传染。

## 前置

- 官方 macOS arm64 DMG（自行下载，sha256 核验）挂到 `/Volumes/PSD2Live`
- Python 3 + venv：`python3 -m venv /tmp/psd2live-gen/venv && /tmp/psd2live-gen/venv/bin/pip install pillow psd-tools`
- **禁止 docker**；不碰真机 adb

## 跑法

```bash
# 1) 生成 demo PSD（Adobe FileHeader 正确公式，勿用笔记旧错版）
/tmp/psd2live-gen/venv/bin/python -I scripts/dh-lab/psd2live-eval/make_psd.py /tmp/psd2live-gen/demo.psd

# 2) 自检图层栈
/tmp/psd2live-gen/venv/bin/python -I -c "from psd_tools import PSDImage; p=PSDImage.open('/tmp/psd2live-gen/demo.psd'); print(p.width,p.height,len(list(p)))"

# 3) 官方 CLI 导出 moc3 族
mkdir -p /tmp/psd2live-gen/out
/Volumes/PSD2Live/PSD2Live.app/Contents/MacOS/PSD2Live \
  --input /tmp/psd2live-gen/demo.psd --output /tmp/psd2live-gen/out --lang zh
# 注意：3.8.4 CLI 在写出后偶发挂在 IMK 消息端口；文件齐了可 kill 进程。

# 4) 断言
python3 -I scripts/dh-lab/psd2live-eval/inspect_export.py /tmp/psd2live-gen/out
```

## 全新角色分层 demo

全原创机器人角色，分层输入含 `ParamFace_smile` 表情与 `ParamWear_cape` 披风开关。生成方式、Editor MCP 导出/姿态步骤及本轮未执行原因见 [`ORIGINAL-AVATAR-DEMO.md`](ORIGINAL-AVATAR-DEMO.md)；当前入仓证据仅为源图，不含 `.moc3` 或 Editor 运行图。

## PSD header 钉桩

Adobe `FileHeader`：`struct.pack(">4sH6xHIIHH", b"8BPS", 1, 3, rows, cols, 8, 3)`
（sig / version=1 / 6x reserved / channels=3 / rows / cols / depth=8 / colorMode=3）。
错误公式 `>4s6xH6xHIIHH` 会把 version 读成 0，psd-tools 直接拒。

## 证据

[`evidence/`](evidence/) 保留本次跑通的 sidecar 摘要（**不含** `.moc3`/贴图大文件）：

- `demo.model3.json` / `demo.cdi3.json` / `demo.smile.exp3.json` / `demo.psd2live.json`
- `moc3-strings.json`：从 demo.moc3 扫出的 Param*/ArtMesh* 字符串表
- `layout-moc3-strings.json` / `layout-variant-excerpt.json` / `layout.*.json`：ParamWear_test 差分专项证据
- `wardrobe-poses-editor-20261123.png` / `wardrobe-runtime-export-20261123.json`：Editor 0/0.5/1 姿态验收
- `wardrobe-deform-body-20261123.png` / `wardrobe-deform-cmo3-20261123.json`：BodyAngleX ±10 变形跟随 + cmo3 导入再导出闭环
- `wardrobe-source-comparison.png`：源图合成（非 runtime）
- `demo-merged.png`：输入 PSD 合成预览
- `cli-run.txt`：CLI 输出摘要

## 纪律 / 不入仓

- 禁止 docker；本评估不占 adb 设备。
- 不要把 `/tmp/psd2live-eval` 源码树、`/tmp/psd2live-dmg/*.dmg`、导出的 moc3/贴图大文件拷进仓库。
