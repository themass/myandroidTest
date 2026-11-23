# 最小服装开关样件（2026-11-23）

状态：**2026-11-23 已跑通 MCP 全链**（import → rig → export `layout.moc3`）+ **`view_render_poses` 0/0.5/1**（Editor 内马甲关/半开/开）。**未**做 Android Cubism runtime 加载验收。

没有找到可用的真实角色 PSD/cmo3。本样件复用我们自己的合成角色分层绘图，在蓝色上衣之上加绿色马甲，避免把缺失原画当成已解决。不修改生产 wardrobe-spec、APK 资产或 UI。

## 生成

依赖与已有评估一致（Python + Pillow）。输出目录必须不存在，防止覆盖已有结果。

```bash
/tmp/psd2live-gen/venv/bin/python -I scripts/dh-lab/psd2live-eval/make_wardrobe_fixture.py /tmp/psd2live-wardrobe-smoke-20261123
```

输出：

- 18 层 PNG + layout.json；绿色马甲分类 topwear，toggle 参数 ParamWear_test。
- vest-0.exp3.json / vest-1.exp3.json：Overwrite 0/1，避免 Add 反复触发造成累加。
- source-vest-0.png / source-vest-1.png / source-comparison.png：**仅源图合成，不能当作模型运行时截图**。
- fixture-summary.json：明确 runtime_verified=false，源图差异包围框为 [254,375,387,546]。

## 验证清单

1. ~~MCP import → rig → export~~ 已完成；~~0/0.5/1 显隐~~ 已完成；~~变形对比~~ 已完成（见下）。
2. **未做**：编辑器姿态渲染和导出 moc3 的运行时加载是两次验证。还需用实际 Cubism runtime 加载导出结果、设置同一组参数并更新模型，检查 drawable opacity、顶点与画面。不将编辑器内部求值称为 Android 验收。
3. **未做**：保存模型哈希后再决定是否接入真实角色。真实角色美术质量仍需分层原画。

## 本轮实际结果

生成脚本运行成功；18 层、两个 Overwrite 表情文件和源图预览齐全。绿色马甲的源图合成差异限定在衣服区域，图像已目视检查。

MCP 全链（经用户当面授权启动官方包）：`project_import_layout` → `rig_generate` → `project_export_target moc3`（`layout.moc3` 1,001,984 B，字符串表含 `ParamWear_test`）→ `view_render_poses` 0/0.5/1 三联图（A 关 / B 半透 / C 全开，已目视）→ 变形：`ParamBodyAngleX` 实际范围 **-10…10**（±15 被 `outOfRangeParameters` 标记并回退为未变形），改用 **±10 + ParamAngleZ 20** 后马甲标注框随躯干旋转、头部侧倾，无撕裂（[`evidence/wardrobe-deform-body-20261123.png`](evidence/wardrobe-deform-body-20261123.png)）。另测 cmo3 闭环：`project_import_cmo3` demo.cmo3 → 新 project → `project_export_target moc3` 成功（1,105,344 B，`ParamAngleX`/`ArtMeshTopwear` 命中）。数值与判定见 [`evidence/wardrobe-deform-cmo3-20261123.json`](evidence/wardrobe-deform-cmo3-20261123.json)。

**给正式管线的答案**：toggle 显隐与身体变形在 Editor 侧都成立；但参数范围按实测（此模型 BodyAngleX ±10，非 ±30），新模型接入 wardrobe-spec 前必须先读实际范围。
