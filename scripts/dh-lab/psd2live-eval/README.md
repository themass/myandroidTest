# psd2live 换装产能评估 harness（工具侧）

评估对象：`tsunehimatoi/psd2live` 3.8.4（GPL 应用 / MIT IR）。**本目录只收我们写的脚本与跑法，禁止把 psd2live 源码或 DMG 入仓。**

## 结论（2026-11-23）

**半替代 → 主力候选**：官方 DMG CLI 能从分层 PSD **完全不碰 Cubism Editor** 直接写出真 `.moc3`（魔数 `MOC3`，本机实测 1,105,344 B）+ model3/cdi3/exp3/physics3 族。内衣槽需要的「新 ArtMesh + Opacity 参数」能力在引擎侧存在（Layout `variant.toggle` → `ParamWear_<variant>`）；无头 CLI `--input` **只吃 PSD**；Layout 清单差分走 MCP：`project_import_layout` → `rig_generate` → `project_export_target moc3`。本轮实测 `layout.moc3` 参数字符串表含 **`ParamWear_test`**（1,001,728 B）。

许可：跑官方发布包、产物进仓库、APK 不含 psd2live 代码 → GPL 无传染。详见共享笔记 `psd2live-eval-20261123.md`。

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
  --input /tmp/psd2live-gen/demo.psd --output /tmp/psd2live-gen/out

# 4) 断言
python3 - <<'PY'
from pathlib import Path
b=Path('/tmp/psd2live-gen/demo.psd' if False else '/tmp/psd2live-gen/out/demo.moc3').read_bytes()
assert b[:4]==b'MOC3'
print('moc3 OK', len(b))
PY
```

## PSD header 钉桩

Adobe `FileHeader`：`struct.pack(">4sH6xHIIHH", b"8BPS", 1, 3, rows, cols, 8, 3)`
（sig / version=1 / 6x reserved / channels=3 / rows / cols / depth=8 / colorMode=3）。
错误公式 `>4s6xH6xHIIHH` 会把 version 读成 0，psd-tools 直接拒。

## 不入仓

- `/tmp/psd2live-eval` 源码快照、`/tmp/psd2live-dmg/*.dmg`、导出的 moc3/贴图大文件
