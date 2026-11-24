# 留白图片编辑器：浏览器设计稿到 Android 原生的落地方案

日期：2026-11-24

## 结论

浏览器原型用于确认信息层级和交互，不把 React、Canvas 代码搬进 Android。Android 继续使用 `:mediaview` 的 Kotlin + Jetpack Compose 编辑器、不可变 `PhotoEdits` 操作模型和 CPU `PhotoRenderer`。设计稿中滤镜面板、工具面板、选中图层属性、全屏裁剪/历史等交互，分别映射到现有 Compose 区域与渲染操作。

已有原生能力比原型更完整的部分要保留：原生裁剪有可拖动的四角控制点；涂鸦、文字和真实像素马赛克使用原图归一化坐标；导出使用真实源图并执行尺寸上限。浏览器里的程序化海岸图、CSS 滤镜和装饰性马赛克只是交互占位，不搬进产品。

## 现有原生架构

- `mediaview/.../edit/PhotoEditor.kt` 持有一次编辑会话；顶栏提供撤销、重做、还原、确认；中间是 `Image` 预览和 `Canvas` 手势/标注叠层；底部面板槽根据当前工具显示控制面板，工具栏保持 8 个等分槽。
- `PhotoEdits.kt` 用 `EditOp` 列表表达编辑，用不可变快照实现撤销/重做。已有操作为旋转、镜像、调整、LUT 滤镜、裁剪、涂鸦、文字、马赛克。
- `EditCoords.kt` 是原图归一化坐标与旋转/镜像/裁剪显示坐标之间的唯一映射。新增图层必须沿用它，不在 UI 或 Renderer 中另造坐标变换。
- `PhotoRenderer.kt` 将底图变换和标注绘制分层，预览用 `RenderBaseCache` 和降采样，导出使用严格失败语义。
- `FilterPanel.kt` 已有 LUT 缩略图、强度滑杆、按住对比；`CropMode.kt` 已有全屏舞台、比例控制、拖动角把手；`PhotoEditExporter.kt` 负责输出缓存文件并限制上传尺寸。

## 设计稿控件到 Compose 的对应

| 浏览器原型 | 原生落点 | 迁移方式 |
| --- | --- | --- |
| 顶部返回、撤销/重做、历史、对比、导出 | `PhotoEditor` 的顶栏及现有 `TopBar` | 保留现有取消/确认回调和历史按钮；按下对比只临时改变预览，不写 `PhotoEdits` 历史。 |
| 中央画布和选中对象拖动 | 现有 `Image` + `Canvas` + `pointerInput` | 仍用真实 `Bitmap`。新增图层走统一 hit-test、`EditCoords.toSourceNorm`、实时预览和单次提交。 |
| 滤镜分类、缩略图、强度、按住看原图 | `FilterPanel`、`FilterPresets`、`LutAssets` | 保留真实 33³ `.cube` 管线；原型 11 个 CSS 滤镜名不能直接当生产预设。新滤镜须有生成来源、`.cube` 资产和渲染测试。 |
| 亮度、对比度、饱和度 | 现有 `AdjustPanel` / `EditOp.Adjust` | 复用实时未记历史的拖动和松手一次提交；保留滑动时降低预览分辨率的处理。 |
| 贴纸/Emoji 面板、画布图层 | `EditorTool`、`SelectionPanel`、`PhotoEdits`、`PhotoRenderer.drawOps` | 新增 `EditOp.Sticker`（资源键/Emoji、归一化锚点、相对尺寸、旋转）；沿用选中、拖动、缩放、删除、撤销。素材先用自绘矢量或系统 Emoji，不引入未经确认授权的素材包。 |
| 文字编辑、描边/背景 | `EditOp.Text`、文字对话框、`SelectionPanel`、`PhotoRenderer.drawText` | 在文字操作增加默认关闭的 `outlineColor`、`backgroundArgb` 等可选属性；Renderer 用 `Paint` 背景框和描边顺序绘制，旧文字默认视觉保持不变。 |
| 自由涂鸦和马赛克 | 现有 `EditOp.Stroke` / `EditOp.Mosaic` 与手势层 | 复用真实路径渲染；原型中的彩色方块马赛克不搬入。界面文案明确“视觉像素化”，不承诺安全删除敏感信息。 |
| 比例裁剪、旋转 | `CropMode` / `CropControls` / `CropGeometry` | 原生已有自由、1:1、4:3、16:9 和四角拖拽；保留完整手势，不退化为原型的固定居中框。9:16 预设可单独补充几何和本地化测试。 |
| 全屏编辑历史 | `PhotoEdits.undoStack` / `redoStack` 与编辑器面板槽 | 可以从快照构造历史列表；先定义选择历史项后的撤销/重做语义，再添加时间线 UI。缩略图必须后台生成且有尺寸上限，避免每次组合重算整图。 |
| 底部九工具横向栏 | 现有 8 槽 `ToolBar` | 当前窄屏 8 项等分是已验证行为。新增贴纸前先把旋转/镜像收进“变换”面板以回收一个槽；或单独评审横向滚动，不直接把 9 项塞进 360dp 栏。 |
| 浏览器 PNG 下载 | 现有 `PhotoEditExporter` 和宿主发送/缓存流程 | 不做浏览器式下载。继续输出私有缓存 Uri，供现有图片发送链路使用；保留实际原图分辨率渲染、失败显式反馈和 2048 长边上传上限。 |

## 建议实现顺序

### Slice A：文字样式

1. 先为 `EditOp.Text` 默认样式兼容、复制/替换、undo/redo 编写纯模型测试。
2. 扩展选中面板的描边和背景开关；实时拖动不产生多条历史，确认一次提交。
3. 扩展预览和导出共用的 `drawText`，测试旋转、裁剪、描边、背景及多行文字的像素结果。

### Slice B：贴纸与 Emoji

1. 明确素材形式和授权来源；优先系统 Emoji 与仓内自绘 VectorDrawable/位图资源。
2. 新增不可变 `EditOp.Sticker`，字段使用原图归一化坐标和短边相对尺寸；编写操作序列、坐标、命中测试和历史测试。
3. 接入 `EditHitTest`、`EditCoords`、`drawOps`、选中框和属性面板；拖动中仅临时预览，结束后替换操作一次。
4. 为旋转/镜像入口增加组合面板，维持现有 8 槽底栏；新增资源按导出和预览两条路径校验。

### Slice C：滤镜和体验收口

1. 每次增加 LUT 同时提交资产、清单、中文名称和色彩样例测试；不以 CSS 近似作为通过标准。
2. 在现有 `CropMode` 上先做沉浸布局适配，沿用已有可拖四角几何。
3. 历史时间线单独定义选择/分支/再编辑行为，再做低分辨率缩略图；若收益不高，继续保留撤销/重做按钮，不为视觉相似而增加状态复杂度。

每个 Slice 单独合入 `weifu-refactor`，先跑 `:mediaview:testDebugUnitTest`，并按现有设备清单验收；设备安装仍由协调会话作为唯一写者完成。

## 不照搬的内容

- Astryx 只负责浏览器设计稿。原生页面使用现有 Material 3 Compose 组件与应用主题，不新增 WebView，也不把 CSS token 或 StyleX 加进 Android。
- 程序化海岸画不替代真实拍照/截图/相册 Bitmap 输入。
- 原型的 CSS/Canvas 近似滤镜不能替代生产 LUT；原型马赛克彩色方块不能替代真实图像像素化。
- 固定画布 900×1200 不能限制手机图片尺寸；预览、导出和上传沿用各自的现有缩放与内存边界。
