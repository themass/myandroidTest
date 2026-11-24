# Original avatar制作与调整 demo

日期：2026-11-24。这里的机器人角色是本仓库脚本用 Pillow 绘制的全新合成素材；未使用现有三个 Live2D 形象、其图片、模型或参数。此 demo 用来验证“分层输入 → PSD2Live Editor 导出/预览”的工具流程，不是正式美术，也不是 Android runtime 验收。

## 可复现输入

在仓库根目录运行（输出目录须不存在）：

```bash
/tmp/psd2live-gen/venv/bin/python -I scripts/dh-lab/psd2live-eval/make_original_avatar_demo.py /tmp/psd2live-original-avatar-20261124
```

生成 13 个独立 PNG 图层及 `layout.json`，画布 640×896。可调内容：

- `ParamFace_smile`：笑嘴图层 toggle，作为表情调整样例。
- `ParamWear_cape`：披风图层 toggle，作为服装调整样例。
- `smile.exp3.json`、`cape-on.exp3.json`：两个参数的表达式样例。
- `source-default.png`、`source-smile.png`、`source-cape.png`、`source-smile_cape.png`：四种参数组合的源图合成预览，仅证明输入图层，不是模型渲染结果。

源图状态四联图（明确标注 SOURCE ONLY）：[`evidence/original-avatar-source-20261124.png`](evidence/original-avatar-source-20261124.png)；机器可读摘要：[`evidence/original-avatar-fixture-20261124.json`](evidence/original-avatar-fixture-20261124.json)。

## Editor 导出与姿态结果（2026-11-24）

使用官方 PSD2Live 3.8.4 的 loopback MCP 完成：`project_import_layout` → `rig_generate` → `project_export_target target=moc3` → `view_render_poses`。导出 `layout.moc3` 为 547,968 B（魔数 `MOC3`）；`ParamFace_smile` 与 `ParamWear_cape` 均出现在 moc3 字符串与 cdi3。model3 引用的 14 个文件全部存在。模型本体和贴图保留在 `/tmp/psd2live-original-avatar-export-20261124`，不入仓。

四联 Editor 姿态图：[`evidence/original-avatar-editor-poses-20261124.png`](evidence/original-avatar-editor-poses-20261124.png)；机器可读记录（含模型 SHA-256、参数组合和文件检查）：[`evidence/original-avatar-editor-20261124.json`](evidence/original-avatar-editor-20261124.json)；完整导出报告（保留 18 项 loss 详情）：[`evidence/original-avatar-moc3-report-20261124.json`](evidence/original-avatar-moc3-report-20261124.json)。可见披风 0/1 切换，以及直线嘴/弧线笑嘴变化。**这是 PSD2Live Editor 证据，不是 Android App 验收。**

导出报告另有 18 项 loss：自动生成的 modifier/deformer 结构因 modifier ID 超出 Cubism 记录宽度而未写入。本 demo 只确认静态图层开关，不证明一般动作/变形保真；若要把这条管线用于正式模型，应先解决或接受这些导出损失并重新验收。

## 边界与 BLOCKED 项

- 本次素材完全原创，未使用现有三个形象的美术或模型，也不进入 APK。
- **BLOCKED：Android Cubism Runtime/App 接入**尚未执行；还须单独核对项目使用的 SDK、格式许可/EULA，再由 App runtime 加载并验收。不能把 Editor 姿态图写成真机结果。
- 本 demo 的工具侧结果不构成法律结论；正式分发前仍需确认 Live2D 格式/EULA 与素材授权。
