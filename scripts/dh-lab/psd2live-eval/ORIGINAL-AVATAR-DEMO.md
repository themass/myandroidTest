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

## Editor 验证步骤（待获准执行）

目标 MCP 地址应绑定 loopback，只在本机运行官方 PSD2Live 3.8.4：

1. `project_import_layout` 导入 `/tmp/psd2live-original-avatar-20261124/layout.json`。
2. `rig_generate` 生成 rig，再 `project_export_target` 以 `target: "moc3"` 导出至 `/tmp/psd2live-original-avatar-export-20261124`。
3. 使用 `view_render_poses` 对照 `ParamFace_smile` 与 `ParamWear_cape` 的 0/1 组合（默认、微笑、披风、微笑+披风），保存导出模型摘要和姿态图。
4. 检查实际参数范围、默认值与图层遮挡；若任一参数不在模型中或姿态无差异，记录为失败，不以源图预览代替。

本轮官方 Editor MCP 启动被自动审批拦截，因此以上导出与姿态步骤**尚未执行**，没有 `.moc3` 或 Editor 预览证据。不能尝试改用其他启动方式规避拦截；需要用户在本会话明确批准启动已下载的官方 PSD2Live 应用后再继续。

## 边界

- 本次产物不是由现有三个形象派生的，也不进入 APK。
- Editor 侧完成后仍须另行用真实 Android Cubism runtime 加载，才能称为 App 接入验证。
- Live2D 格式/EULA 与素材授权仍需产品化前单独确认；本 demo 不代表法律结论。
