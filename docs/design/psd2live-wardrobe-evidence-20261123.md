# psd2live / 内衣换装证据索引（2026-11-23）

与 [`2026-11-23-换装抽象与特效层设计.md`](2026-11-23-换装抽象与特效层设计.md) 配合阅读。单一事实源仍为 [`../scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json`](../scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json)。

## 工具评估（psd2live v3.8.4）

| 问题 | 结论 | 证据 |
| --- | --- | --- |
| 能否产出真 moc3，无需 Cubism Editor？ | 能 | CLI `demo.moc3`；详见 [`../scripts/dh-lab/psd2live-eval/EVAL.md`](../scripts/dh-lab/psd2live-eval/EVAL.md) |
| `ParamWear_*` toggle 能否进模型？ | 能 | `layout.moc3` 字符串表；[`evidence/layout-moc3-strings.json`](../scripts/dh-lab/psd2live-eval/evidence/layout-moc3-strings.json) |
| Layout 管线端到端？ | Editor MCP 已跑通 | [`WARDROBE-SMOKE.md`](../scripts/dh-lab/psd2live-eval/WARDROBE-SMOKE.md)、[`evidence/wardrobe-runtime-export-20261123.json`](../scripts/dh-lab/psd2live-eval/evidence/wardrobe-runtime-export-20261123.json) |
| 开关 0/0.5/1 画面？ | Editor `view_render_poses` 已验 | [`evidence/wardrobe-poses-editor-20261123.png`](../scripts/dh-lab/psd2live-eval/evidence/wardrobe-poses-editor-20261123.png)（合成测试角色，非正式美术） |
| 身体变形下马甲跟随？ | Editor 已验（BodyAngleX ±10 / AngleZ 20） | [`evidence/wardrobe-deform-body-20261123.png`](../scripts/dh-lab/psd2live-eval/evidence/wardrobe-deform-body-20261123.png)；范围与判定见 [`evidence/wardrobe-deform-cmo3-20261123.json`](../scripts/dh-lab/psd2live-eval/evidence/wardrobe-deform-cmo3-20261123.json) |
| cmo3 导入→再导出闭环？ | 已验（demo.cmo3 → moc3 1,105,344 B） | 同上 JSON；atri_8 头饰扫参仍未验（无真实 cmo3） |
| Android Cubism runtime？ | **未验** | — |
| atri_8 头饰扫参？ | **未验**（无 moc3 import 产品入口） | — |

## 交付管线（内衣 pending-art → 新模型）

- 流程说明：[`../scripts/dh-lab/live2d-lab-assets/UNDERWEAR-PIPELINE.md`](../scripts/dh-lab/live2d-lab-assets/UNDERWEAR-PIPELINE.md)
- Layout 生成：`wardrobe_layout.py` + `check_delivery_ready.py`
- 单测：`python3 -m unittest discover -s scripts/dh-lab/live2d-lab-assets -p 'test_*.py'`（9/9）

## 纪律

禁止 docker；评估不默认 adb；不把 psd2live 源码/DMG 入仓；不把示意图或合成样件标为 production active。
