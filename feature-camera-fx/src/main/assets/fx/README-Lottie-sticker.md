# 相机贴纸资产（USER 侧，Lottie）

贴纸**不复用**舞台特效词表（用户 2026-11-28：「贴纸只是挪用其中 3 个……贴纸你也没搞啊」）。
特效是对「发生了什么」的一次性反馈，贴纸是挂在人已跟踪部位上的常驻装饰，因此这里是独立的
`sticker_*` 一组，与 `fx_*` 在文件名上就能分开。

| 文件 | 部位 | 跟踪信号（复用 `FxAnalyzer` 已算好的 landmark） | 面板文案 | 尺寸 | 许可 |
|---|---|---|---|---|---|
| `fx_pat_head.json` | 头顶 | `FaceFx.headTop`（`FACE_OVAL` 最高点 + 12% 脸高） | 爱心 | 512×512 | **仓库自制**（`tools/gen_lottie_stickers.py`），无第三方素材，商用无限制 |
| `sticker_cat_ears.json` | 头顶 | 同上 | 猫耳贴纸 | 512×512 | 同上 |
| `sticker_crown.json` | 头顶 | 同上 | 皇冠 | 512×512 | 同上 |
| `sticker_flower_wreath.json` | 头顶 | 同上 | 花环 | 512×512 | 同上 |
| `sticker_sunglasses.json` | 眼睛 | `FaceFx.eyeLine`（虹膜中心 468/473） | 墨镜 | 512×512 | 同上 |
| `sticker_mustache.json` | 嘴 | `FaceFx.mouth`（`FACE_LIPS_OUTER` 均值） | 胡子 | 512×512 | 同上 |
| `sticker_hand_heart.json` | 手 | `HandFx.points`（21 点均值 = 掌心） | 手持比心 | 512×512 | 同上 |

`fx_pat_head` 保留原名是历史原因（通话页默认贴纸 + 已存的偏好键），新增一律 `sticker_*`。

## 画位口径（与 `CameraStickerAnchors.kt` 一一对应，改一处必须改另一处）

- 画布 512×512，**画布中心 = 该贴纸的锚点**：头饰底边画在中心线略下，墨镜压在中心，
  胡子挂在中心略上（挂在唇上方而不是压在唇上）。
- 相机侧 `iterations = Int.MAX_VALUE`：贴纸循环播放 → **首尾同态**（seamless loop），
  只做轻微摆动/呼吸，不做入场退场（生成脚本自检图层必须整程在场）。
- 尺寸按部位换算：头顶类 = 脸宽 ×1.05，墨镜 = 两眼距 ×1.4（按脸宽会盖到耳朵），
  胡子 = 脸宽 ×0.55，手持 = 手掌跨度（腕→中指根 ×2）。全部 clamp 到 24..512px。
- 倾角沿用两眼连线（`lineTiltDegrees(mirrorX = true)`，与 HUD 同一口径）。
- 跟踪不重写：`FxAnalyzer` 每帧只解一次 landmark，唇锚点挂在 `FaceFx.mouth` 上，
  面板判定 / 预览绘制 / HUD 用同一个数。

## 规格（与特效同一套，方案 lottie-fx-plan-20261123.md §3）

透明背景、无 AE 表达式（`tx`）、无内嵌位图（`ty:img`）、无 matte（`ty:tt`）、无 3D（`ddd:1`）、
单文件 ≤100KB、30fps。

## 生成

```bash
python3 -I tools/gen_lottie_stickers.py                       # 全部
python3 -I tools/gen_lottie_stickers.py sticker_crown         # 只生成指定几条
```

构件（`anim` / `group` / `poly_path` / `heart_path` …）从 `tools/gen_lottie_fx.py` import，
不复制第二套口径。结构校验在 `feature-camera-fx/src/test/java/.../StickerWordTableTest.kt`：
白名单每个 id 的 json 必须存在、可解析、含 `v/fr/ip/op/w/h/layers`。

## 加一张贴纸要动几处

1. `tools/gen_lottie_stickers.py` 加函数 + `ASSETS` 注册；跑脚本生成 json。
2. `CameraFxOverlayPolicy.kt` 的 `StickerAssetIds` 加 `const val` 与一行 `Spec(id, 部位, 短标签)`。
3. `StageFxOptions.kt` 的 `CameraStickers` + `labels` 加面板镜像与文案
   （两模块不建依赖边，`StickerPanelMirrorTest` 扫源码钉住两边同一组 id）。
4. 若部位是新的一类，在 `CameraStickerAnchors.kt` 的 `stickerAnchorsForFrame` 加一路锚点。

不可用的贴纸在面板里**置灰 + 给原因**（缺相机权限 / 未识别人脸 / 手不在画面），不隐藏。
