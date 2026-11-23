# 内衣素材到模型的交付管线

此管线生成新模型的输入，当前 ATRI 的内衣槽仍为 pending-art。
唯一变体目录是 wardrobe-spec.json 的 underwear 槽；productionBinding 定义 ParamWear_、0 隐藏 / 1 显示。
不从现有 ATRI moc3 自动拆图，不将示意图作为正式美术交付。

## 输入

美术交付一个目录，包含 PNG 分层和 source.json。图层从后到前排列；保留底图、脸、身体、头发等普通层，三套内衣各有独立 PNG。坐标与尺寸由美术给出，不自动猜测。身体基线应使用有授权、已确认成年的原创角色素材。

source.json 示例（PNG 必须真实存在，image 尺寸必须与 PNG 匹配）：

```json
{
  "name": "wardrobe-v2",
  "canvas": {"width": 1024, "height": 2048},
  "layers": [
    {
      "name": "body",
      "image": {"path": "body.png", "width": 1024, "height": 2048},
      "rect": {"left": 0, "top": 0, "width": 1024, "height": 2048},
      "tags": ["body"]
    },
    {
      "name": "underwear lace",
      "image": {"path": "lace.png", "width": 500, "height": 700},
      "rect": {"left": 262, "top": 700, "width": 500, "height": 700},
      "wardrobe": {"slot": "underwear", "token": "lace"}
    }
  ]
}
```

sport / cat 同样以 wardrobe.slot/token 声明；一套可由多个图层组成，共享参数。
示例仅说明格式与坐标，不能作为真实美术规格。工具检查 PNG 头部尺寸，不替代完整解码与视觉验收。

## 生成

在仓库目录运行（替换路径），输出必须与输入在同一目录，保持图片相对路径有效；已有输出不会被覆盖。

```bash
python3 scripts/dh-lab/live2d-lab-assets/wardrobe_layout.py /path/to/art/source.json --output /path/to/art/layout.json
```

输出为 psd2live v1 Layout，内衣图层得到 variant.type=toggle / parameter=ParamWear_lace 等。
布局契约来源：psd2live v3.8.4 的 docs/zh/spec/LAYOUT_MANIFEST.md；前次实跑证据见 ../psd2live-eval/EVAL.md。
CLI --input 仅吃 PSD，Layout 走官方应用 MCP：project_import_layout → rig_generate → project_export_target moc3。
本脚本不启动外部工具、不写运行时资产、不改变 active 状态。

## 导出后的启用门槛

1. 完整解码所有图片，导出新 moc3/model3/cdi3/贴图，检查 model3 全部引用存在。
2. 在真实 Cubism runtime 加载新模型，确认每套参数 0/1 的可见性、遮挡、动作变形和互斥；检查全关时基线符合交付要求。
3. 保存模型哈希和各套显隐截图；不能用 cdi3 有参数或 moc3 字符串命中代替显隐验收。
4. 将验证后的模型作为独立模型配置接入，完成现有上衣/鞋/手持/表情的兼容性验证。新模型不能沿用旧 ATRI 的 Param17 等绑定。
5. 此后再把对应 spec 变体转 active，补 verifiedFrom，更新 naming.order 与 Kotlin 绑定，生成 exp3/model3 并跑四处同源测试。

## 测试

```bash
python3 -m unittest discover -s scripts/dh-lab/live2d-lab-assets -p 'test_*.py'
```

未交付：正式内衣美术、新生产模型、真实模型开关截图及真机验收。当前交付是可测试的 Layout 准备工具。
