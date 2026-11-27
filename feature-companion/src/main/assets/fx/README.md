# 舞台特效资产（HER 侧，Lottie）

| 文件 | 锚点 | 语义类别 | 触发词（主词 / 别名） | 面板文案 | 尺寸 | 许可 |
|---|---|---|---|---|---|---|
| `fx_pat_head.json` | HEAD | 事件响应 | `pat_head` / `pathead`,`head_pat` | 摸头爱心 | 512×512 | **仓库自制**（`tools/gen_lottie_fx.py`），无第三方素材，商用无限制 |
| `fx_wave.json` | HAND | 事件响应 | `wave` / `waves`,`greeting` | 挥手光弧 | 512×512 | 同上 |
| `fx_connected.json` | FULL | 氛围 | `connected` / `connect`,`call_connected` | 接通花瓣 | 1080×1920 | 同上 |
| `fx_swipe.json` | FULL | 事件响应 | `swipe` | 滑动流光 | 512×512 | 同上 |
| `fx_cat_ears.json` | HEAD | 事件响应 | `cat_ears` / `cat`,`ears` | 猫耳 | 512×512 | 同上 |
| `fx_star_eyes.json` | HEAD | 事件响应 | `star_eyes` / `stars`,`star_struck` | 星星眼 | 512×512 | 同上 |
| `fx_blush.json` | HEAD | 事件响应 | `blush` / `shy`,`blushing` | 脸红 | 512×512 | 同上 |
| `fx_bubbles.json` | BODY | 事件响应 | `bubbles` / `bubble` | 气泡 | 512×512 | 同上 |
| `fx_halo.json` | BODY | 事件响应 | `halo` / `angel` | 光环 | 512×512 | 同上 |
| `fx_sparkle.json` | HAND | 事件响应 | `sparkle` / `sparkles` | 指尖闪光 | 512×512 | 同上 |
| `fx_fireworks.json` | FULL | 事件响应 | `fireworks` / `firework`,`celebrate` | 烟花 | 1080×1920 | 同上 |
| `fx_coins.json` | FULL | 事件响应 | `coins` / `coin` | 金币雨 | 1080×1920 | 同上 |
| `fx_heart_rain.json` | FULL | 氛围 | `heart_rain` / `hearts` | 爱心雨 | 1080×1920 | 同上 |
| `fx_snow.json` | FULL | 氛围 | `snow` / `snowfall` | 雪花飘落 | 1080×1920 | 同上 |
| `fx_rain.json` | FULL | 氛围 | `rain` / `rainfall` | 雨滴 | 1080×1920 | 同上 |
| `fx_spotlight.json` | FULL | 氛围 | `spotlight` / `spot` | 追光 | 1080×1920 | 同上 |

类别决定降级行为（`FxRenderPolicy`，测试钉死在 `FxWordTableTest`）：

- **事件响应**：`particles` 关**仍播**；系统「减弱动态」下降为静态帧（保留「发生了什么」的反馈）。
- **氛围**：`particles` 关**不播**；减弱动态下**完全不播**（静态花瓣等于屏幕贴纸片）。
- 锚点类（HEAD/BODY/HAND）在舞台放大态不播（特效层不跟捏合，基准点对不上人）；FULL 类不受影响。

## 规格（方案 lottie-fx-plan-20261123.md §3）

透明背景、无 AE 表达式（`tx`）、无内嵌位图（`ty:img`）、无 matte（`ty:tt`）、无 3D（`ddd:1`）、
单文件 ≤100KB、24 或 30fps。事件类一次播完；氛围类首尾同态可循环。

## 生成与校验

```bash
python3 -I tools/gen_lottie_fx.py                    # 全部
python3 -I tools/gen_lottie_fx.py fx_snow fx_coins    # 只生成指定几条
```

脚本自带自检（静态向量必须是扁平数字数组、≤100KB、禁词）；仓库侧的结构校验在
`feature-companion/src/test/java/.../fx/FxWordTableTest.kt`：白名单每个 id 的 json
必须存在、可解析、含 `v/fr/ip/op/w/h/layers`，且资产目录里不得有白名单外的 json。

## 加一条特效要动几处

1. `tools/gen_lottie_fx.py` 加一个函数 + `ASSETS` 注册；跑脚本生成 json。
2. `StageEffect.kt` 的 `FxAssets` 加 `const val` 与一行 `Spec(id, 类别, 锚点)`。
3. `StageEffectMapper.kt` 的 `gestureAliases` 加触发词（第一个是主词，面板用它直发）。
4. `StageFxOptions.kt` 的 `labels` 加面板文案。
5. 跑 `:feature-companion:testDebugUnitTest` —— 词表/类别/降级/资产结构四组钉子会告诉你漏了哪一步。

服务端没下发过的词不会自己出现：面板/教学脚本走 `emitStageGesture(主词)`，
仍是既有 `gesture → gestureSeq` 那一条流，不另起总线。
