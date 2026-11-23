# psd2live 产能管线评估结论（2026-11-23）

目标：`tsunehimatoi/psd2live`（1157★，v3.8.4）能否成为 Live2D 换装素材产能管线，替代/辅助 Cubism Editor 重制。对照单一事实源 [`../live2d-lab-assets/wardrobe-spec.json`](../live2d-lab-assets/wardrobe-spec.json) 内衣槽 `pending-art` / 头饰槽 `pending-param`。

## 判定

**半替代，可作为主力候选（工具侧）**。

| 能力 | 结论 |
| --- | --- |
| 产出真 `.moc3`（含新 ArtMesh） | **能**。官方 CLI 从分层 PSD 直写 moc3，**不依赖 Cubism Editor**（自家 umamo 全合成 lowering）。本会话实跑：`demo.moc3` 魔数 `MOC3`、版本字节 5，1.1 MB；`ArtMeshTopwear`/`ArtMeshBottomwear`/`ArtMeshFace`/… 22 个 ArtMesh 字符串可读。 |
| `ParamWear_<variant>` 风格 Opacity 开关 | **能约定**。`ExpressionChannels.catalog()` 按图层 variant 参数名**字面**建 `Parameter(Id=name)`，`opacity()` 生成 0→1 Opacity 关键形——与 wardrobe「每套一个 Opacity 参数」同构。本 harness **未再跑** toggle 差分专项（代码路径 + Layout 清单字段已核实；列为后续）。 |
| 替代 atri_8 内衣重制 | **半替代**：能造「去外衣基线 + 若干内衣 ArtMesh + ParamWear_*」的**新 moc3**；不能自动把现成 atri_8 原画拆件重绑（仍是美术活）。头饰 `pending-param` 有「把 moc3 导进 psd2live 扫参数」的工具路径，产品入口未测。 |
| 许可 | 工具 GPL；**产出素材不受 GPL 传染**。MIT 的 IR/导出框架/p2lrt 可单独复用。真正法务面在 Live2D moc3 专有格式/EULA，产线化前需法务。 |

## Q1 导出物

证据来源：仓库快照 `/tmp/psd2live-eval @ f66ba46` + 官方 DMG CLI 实跑 `/tmp/psd2live-gen/out/`。

- `Moc3Export.kt`：「FULL SYNTHESIS… writing a `.moc3` from a rig that may never have been one」。
- README / DEVELOPMENT：不依赖官方 SDK 即可导出 moc3/cmo3；SDK 仅可选原生预览。
- CLI 默认写 moc3（`--no-moc3` 才关）。导出族：model3 / physics3 / motion3 / **exp3** / pose3 / cdi3 / userdata3 / 贴图；exp3 字段 `Id/Value/Blend` 与我们 `gen-expressions.py` 消费格式一致。
- 实跑 sidecar 见 [`evidence/`](evidence/)：`demo.model3.json` 引用 `demo.moc3`；Groups 含 EyeBlink=`ParamEyeLOpen/ROpen`、LipSync=`ParamMouthOpenY`；cdi3 66 参数。

**结论：内衣 ArtMesh 可以由 psd2live 造，moc 编译不需要 Cubism Editor。**

## Q2 参数体系

- 自动标准参数：`ParamAngleX/Y/Z`、`ParamEye*Open`、`ParamMouthOpenY`、头发/布料物理段等（见 `evidence/moc3-strings.json`）。
- 差分：`LayerVariant` + `ExpressionChannels`——toggle 参数名原样进 moc3；Layout 清单 `variant:{type:"toggle",parameter:"ParamWear_lace"}` 或 MCP `layer_set_variant`。
- 钉值注意：psd2live toggle 默认范围 **0…1**（1=显），atri_8 现有 Param17/18 等是 ±30 制。新模型新轴须按实测改 wardrobe-spec `value`，不能沿用 30。

## Q3 许可边界

| 块 | 许可 |
| --- | --- |
| 应用 / umamo / targets:cubism（moc3 写出）/ 根 | GPL-3.0 |
| format-model、format-compile、其余导出目标、runtime/p2lrt | MIT（Gradle 保证不依赖 GPL 模块） |
| 示例素材 | 默认 CC0 |

我们只跑官方发布包、APK 不含其代码 → GPL 义务不及于产出 moc3/贴图。MIT 模块可进脚本管线。Live2D 专有格式合法性另案（同 ayagami/PurismCore 风险面）。

## 实跑命令摘要

```text
# DMG sha256 OK → mount /Volumes/PSD2Live
python -I make_psd.py /tmp/psd2live-gen/demo.psd   # 17 层，psd-tools 自检通过
PSD2Live --input …/demo.psd --output …/out --lang zh
# 产出 demo.moc3 + model3 + cdi3 + physics3 + motions + exp3 + cmo3/can3
# CLI 结束后偶发挂 IMK 端口 → kill 无碍（文件已齐）
```

警告（可接受）：同名 eyebrow/eyewhite/iris 用稳定图层 ID 区分；嘴部自动补唇网格相对 PSD 有偏差；CMO3 透过刘海绘制顺序重编号。

## 未完成 / 后续

1. toggle 专项：Layout 清单加 `ParamWear_test` 层，断言 moc3 字符串表含该名且 Opacity 关键形挂上。
2. 头饰验证：moc3/`project_import_cmo3` 导入 atri_8，扫 Param4–7/23–30 显隐钉值（产品入口未确认）。
3. 法务：第三方生成 moc3 与 Live2D EULA。
4. 产线：美术 PSD 命名规范 + wardrobe-spec 新轴 value=1.0 纪律。

## 仓库落盘

- harness：`scripts/dh-lab/psd2live-eval/`（本目录）
- 共享笔记：`…/side-session-notes/local_0c440dde-…/psd2live-eval-20261123.md`
- **未** merge/push `weifu-refactor`（本轮指令：文档完成先落分支；合主线另说）
