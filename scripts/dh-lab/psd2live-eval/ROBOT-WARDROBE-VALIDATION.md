# 原创机器人多槽换装验证（2026-11-27）

## 范围和结果

- `robot-wardrobe-spec.json` 定义上装、鞋、表情的槽位与本模型的参数；`robot_wardrobe.py` 从规格生成 15 层 Layout、8 个包含所有六个开关值的组合表达式，以及素材合成预览。
- 本地 PSD2Live 3.8.4 无界面工作区成功导入 Layout、执行 `rig_generate` 并导出 moc3。导出 CDI 包含六个规格参数。导出报告有 **18 项结构丢弃**（标识符截断导致部件或变形器变更无法表示）。
- `robot-wardrobe-editor-poses.png` 是编辑器工程的固定镜头渲染：左侧披风、靴子、平嘴；右侧夹克、运动鞋、笑脸。两种上装和鞋可见且互斥，但**右侧笑脸显示不完整**，素材合成预览中完整；尚未查明原因。该图不是导出 moc3 的 Cubism 运行时画面。
- 对照实验使用 `rig_generate` 的 `preset_group=none`：导出报告为 0 项丢失，但编辑器报告六个换装参数均不存在，无法渲染这些换装姿态。因此不能简单关闭默认预设来解决导出损失。
- 仅验证了上述两种编辑器组合。其余组合、连续切换、触摸交互、动作变形和 Android/Cubism 运行时均未验证。模型尚未接入 Android 衣橱，不能认定达到 ATRI 的换装体验。

## 复现素材与纯决策测试

安装 Pillow 后，在仓库根目录运行，输出目录须不存在：

```bash
python3 -I scripts/dh-lab/psd2live-eval/robot_wardrobe.py /tmp/original-robot-wardrobe-20261127
```

```bash
python3 -B -m unittest discover -s scripts/dh-lab/psd2live-eval -p test_robot_wardrobe.py -v
```

测试覆盖 8 种组合的槽位互斥和参数完整性、无效选择、Layout 与表达式一致性；不证明模型渲染或运行时行为。

## 后续验收条件

排查笑脸不完整与导出丢失结构的原因，修正后在编辑器和导出的 Cubism 模型分别验证全部组合、独立槽位保持、快速切换及动作变形；再以模型专用规格接入现有 Android 衣橱并做运行时验证。设备安装由协调会话从主线最新构建执行。第三方工具生成 moc3 的许可需按实际发布用途核对。
