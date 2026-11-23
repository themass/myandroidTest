# 最小服装开关样件（2026-11-23）

状态：**输入样件 + wardrobe_layout 单测 9/9 通过；Editor 导出与 view_render_poses 仍待 MCP（token 须 `secrets.token_urlsafe`，启动 PSD2Live 可能被自动审批拦截）**。

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

## 待完成的验证

1. 用官方 PSD2Live 的 MCP project_import_layout 导入 layout.json，rig_generate，再 project_export_target moc3 导出。不能把 layout.json 传给只接受 PSD 的 CLI --input。
2. 检查输出模型、纹理引用与 ParamWear_test 的范围/默认值；实际检查马甲网格的 Opacity 关键形，不能只检查参数字符串存在。
3. view_render_poses 固定同一画布视角，比较参数 0、0.5、1；0 隐藏、1 显示，0.5 检查插值。再分别在 ParamBodyAngleX=-15/15 和 ParamAngleX=-15/15 下比较开关；确认参数存在且范围允许后才给值。检查马甲是否跟随身体、是否穿过手臂、是否出现裂缝。
4. 编辑器姿态渲染和导出 moc3 的运行时加载是两次验证。还需用实际 Cubism runtime 加载导出结果、设置同一组参数并更新模型，检查 drawable opacity、顶点与画面。不将编辑器内部求值称为 Android 验收。
5. 保存模型哈希、工具版本、参数读数、截图和结论后再决定是否接入真实角色。真实角色美术质量仍需分层原画。

## 本轮实际结果

生成脚本运行成功；18 层、两个 Overwrite 表情文件和源图预览齐全。绿色马甲的源图合成差异限定在衣服区域，图像已目视检查。

启动官方发布包的本地 MCP 被自动审批拒绝，原因为 `[Code from External]`。未绕过拒绝，未做新的模型导出、变形或运行时验证。恢复验证需要允许启动已下载的官方 PSD2Live 可执行文件；授权不涉及 adb 或 APK 发布。
