# 舞台特效资产（Lottie）

| 文件 | 用途 | 尺寸 | 许可 |
|---|---|---|---|
| `fx_pat_head.json` | 摸头爱心（事件响应） | 512×512 | **仓库自制**（`tools/gen_lottie_fx.py`），无第三方素材，商用无限制 |
| `fx_wave.json` | 挥手光弧（事件响应） | 512×512 | 同上 |
| `fx_connected.json` | 接通花瓣（氛围） | 1080×1920 | 同上 |
| `fx_swipe.json` | 手指滑动流光（事件响应，中性方向） | 512×512 | 同上 |

规格（方案 lottie-fx-plan-20261123.md §3）：透明背景、无 AE 表达式、无内嵌位图、无 matte、单文件 ≤100KB、24/30fps。

重新生成：

```bash
python3 -I tools/gen_lottie_fx.py
```
