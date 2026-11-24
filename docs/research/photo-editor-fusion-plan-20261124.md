# 图片编辑器对比与融合方案 · 2026-11-24

对比对象：我们的 `:mediaview` 自研 Compose 图片编辑器 vs 开源调研（PhotoEditor / android-gpuimage(+plus) / compose-image-editor / CanHub Cropper / VrFace / MediaPipe / Real-ESRGAN 系）。

先立一个前提（重要）：**调研报告写的时候看的是旧快照**（`com.timeline.vpn`、Java Support 23.2.0、无 Compose），它说「当前仓只有 :app 一个模块、没有 Compose」——这与我们主线（weifu-refactor）的现状**不符**。我们如今是 Kotlin/Compose/Material3 多模块工程，`:mediaview` 就是自研的纯 Compose 编辑器。所以报告里「先迁移 AndroidX/Compose 才能用这些库」的接入门槛对我们不成立，**我们已经在它建议到达的地方了**。下面的融合方案按我们现状评估。

## 一、我们现在有什么（:mediaview，自研，纯 Compose）

架构：不可变 `PhotoEdits(sourceW, sourceH, ops, undoStack, redoStack)` + `List<EditOp>`，每次改动生成新实例；撤销/重做存**操作列表快照**而不是单条记录——所以「删除选中笔迹」「拖动文字改位置」也能正确撤销。这也是标榜能给未来扩展留的底子。

工具（底部 8 项等分栏 + 顶栏撤销/重做/还原/确认）：

| 工具 | 实现 | 备注 |
|---|---|---|
| 旋转 / 镜像 | `EditOp.Rotate(quarterTurns)` / `FlipHorizontal` | 裁剪后禁用；动画式 live-edit |
| 裁剪 | `EditOp.Crop(left,top,right,bottom)` | 归一化于旋转+镜像后的**显示画幅**；比例可选；标注不因裁剪而改数据，靠渲染截断 |
| 涂鸦 | `EditOp.Stroke(points, colorArgb, widthRatio)` | 连续折线，选中后可改色/粗细/删除/拖动 |
| 文字 | `EditOp.Text(value,x,y,colorArgb,sizeRatio,bold)` | 左上角归一化；选中后可改内容/颜色/字号/粗体 |
| 马赛克 | `EditOp.Mosaic(points,widthRatio,blockRatio)` | **沿笔迹**打码，不是矩形 |
| 滤镜 | `EditOp.Filter(id,intensity)` + 6 个 LUT `.cube`（creamy/coolwhite/retro/moody/airy/mono，33³，`tools/gen_luts.py` 生成） | 缩略图+滑杆+「按住=从预览里摘掉滤镜」对比 |
| 调整 | `Adjust(brightness,contrast,saturation)` -100..100 | 滑杆只对预览图生效 |
| — | 顶栏撤销/重做/还原/导出（Check） | — |

渲染：CPU（`PhotoRenderer.renderBase` + `drawOps`），`liveBase` 快照 + `withOpsUnrecorded` 做动画式预览；`RenderBaseCache` + `guardRender` OOM/运行时保护，失败回退源图。窗口感知（`displayWidth/Height`、`cropWindowWidth/HeightFraction`）。出口：导出 + 草稿托盘（S3）「长按图片→再编辑」复用同一编辑器。

测试底座：`:mediaview:testDebugUnitTest` 118 用例全绿，纯决策层（坐标换算、op 序列、undo/redo、裁剪几何）单测钉住。

## 二、功能对比（我们 vs 调研对象）

| 能力 | 我们 `:mediaview` | burhanrashid52/PhotoEditor | compose-image-editor | CanHub Cropper | MediaPipe/VrFace | Real-ESRGAN 系 | 结论 |
|---|---|---|---|---|---|---|---|
| 涂鸦/画笔 | ✅ 折线+粗细+颜色 | ✅ ViewView 级 | ✅ | — | — | — | 我们更贴合 Compose，无落差 |
| 文字 | ✅ 内容/颜色/字号/粗体 | ✅ 同样级别 | ✅ | — | — | — | 平；**但没有描边/背景/阴影/多样式** |
| 贴纸/emoji | ❌ | ✅ **emoji+贴纸** | ✅（内部桥接 PhotoEditor） | — | — | — | **我们最大缺口**；PhotoEditor 的贴纸缩放/拖动是现成交互 |
| 马赛克 | ✅ 沿笔迹（更精致） | ✅（一般矩形） | ✅ | — | — | — | 我们方案已超它 |
| 裁剪 | ✅ 比例/显示空间 | ✅ | ✅ | ✅ 专职旋转/手柄 | — | — | 我们够用；CanHub 的全屏体验可借鉴 |
| 滤镜 | ✅ 6 个 LUT+强度+按住对比 | ✅（RenderScript 滤镜组） | ✅ | — | — | — | 我们 LUT 管线可横向加表（成本低）；运行时用 CPU 而非 RS |
| 调整 | ✅ 亮度/对比/饱和 | ✅ | — | — | — | — | 平 |
| GPU/实时特效 | ❌ 无 GL | ❌ 编辑库，非实时 | ❌ | — | — | ❌ | android-gpuimage(+plus) 是我们的「实时滤镜+相机」备选（见方案三） |
| 人脸贴纸/AR | ❌ | ❌ | ❌ | — | ✅ MediaPipe Landmarker | — | 独立能力线（camera-fx/room），不进编辑器核心 |
| 超分/去马赛克 | ❌ | ❌ | ❌ | — | — | ✅ Real-ESRGAN(许可未核实) | **别进编辑器**；作为离线后处理/服务端能力评估 |
| 撤销/重做 | ✅ 全量快照 | ✅（操作栈，但粒度保存集） | ✅ | — | — | — | **我们的快照方案能撤「删除/拖动/改样式」**，强于单操作栈 |

### 我们 UI/UX 优势
1. **纯 Compose、无 AndroidView 桥接**：compose-image-editor 是靠 `AndroidView` 包 PhotoEditor 的，跌回旧的 View/事件体系；我们是原生的 Modifier 手势，动画/主题/窄屏适配都顺。
2. **撤销语义最完整**：快照式 undo/redo 能撤销「删除中间某条笔迹」「拖动文字后的位置」，调研对象大多是「每条操作压栈」的朴素实现。
3. **滤镜对比体验**：按住临时摘掉滤镜看原图 —— PhotoEditor 那几个是点一下滤镜直接换，没有强度 + 按住对比的组合。
4. **马赛克沿笔迹**：比矩形框的交互直觉（手指滑哪打哪），调研对象没有。
5. **窄屏幸存**：底部栏用等分而不是 SpaceEvenly，360dp 也八项全在 —— 这是踩过坑换来的。
6. **性能安全网**：CPU 渲染 + `guardRender` 回退，不像 GL 管线在无 GL/低端机上直接黑屏。

### 我们 UI/UX 短板
1. **没有贴纸/emoji 入口**（功能+交互双重缺口）。
2. **文字只有一种视觉**：无描边/阴影/背景/多字体，PhotoEditor 的文字层有更多可玩性。
3. **滤镜只有 6 张 LUT**：横向可加，但没照片没关系（旋转/圆角/漏光）。
4. **工具图标无文字标签 vs 换行**：我们 8 项等分栏是「图标 + 下方 label」，你在解题微调页看到的是另一处（QuadAdjustScreen 底部三按钮），那个已修（换行→单行截断）；编辑器这里 8×48dp 是平衡过的，别改回 SpaceEvenly。
5. **无「撤销的历史预览/分支」**：快照栈只有线性前进后退，没有 PS 式历史面板。
6. **裁剪/滤镜/文字没有全屏沉浸模式**：CanHub 用全屏裁剪手势，Snapseed 用底部工具页全屏化 —— 我们的面板都叠在编辑区下方，小屏上画布被挤压。

## 三、融合方案（按「自研扩展优先」排序，全部遵守离线 Gradle 缓存纪律）

关键约束（侧栏手记 `handover-photosolve-three-designs.md`）：**Gradle 离线缓存里没有任何图片编辑库**（无 com.burhanrashid52 / CanHub / android-gpuimage / com.vanniktech 等）。加任何一条依赖都会破坏 `--offline` 构建。所以融合路线是：**吸收开源实现的交互与好点子，代码自研**，而不是 `implementation` 一行引入。

### 第一优先：低成本高感知，纯 `:mediaview` 内部新增
1. **贴纸 / emoji 面板**（对标 PhotoEditor 的贴纸层）：
   - 新增 `EditOp.Sticker(imageAssetId, x, y, widthRatio, rotation)`。数据模型 + 渲染都走现有架构：贴纸作为一张图层 op 参与 drawOps，撤销/移动/缩放/旋转全靠操作栈，天然免费。
   - 贴纸资源：**自备授权清白的素材**（emoji 用 Android 系统 emoji 字体提取，或自绘矢量贴纸；不引入第三方库，也就没有许可包袱）。
   - UI：工具条加「贴纸」入口（第 9 项或并入文字？）：面板给 emoji/贴纸网格，点选即贴画面中心，选中后可拖动/缩放（双指）/旋转/删除 —— 交互与现有 Text 选中面板同一套。
2. **文字增强**：`EditOp.Text` 增加 `outlineColor`/`backgroundArgb`/`letterSpacing`（可选字段默认无），PhotoEditor 的文字描边给视觉一个层次；纯渲染层改动，决策层几乎不动。
3. **滤镜横向扩容**：`FilterPresets.ids` 是纯列表，`tools/gen_luts.py` 已有生成管线 —— 加 3~5 张新 LUT（暖阳/电影青橙点/复古灰阶 等）按现有格式落 assets 即可，不用动渲染。栅格 6→8/9 格调整 `chunked`.

### 第二优先：实时能力线（独立于编辑器核心）
4. **实时滤镜/相机特效**：只有明确要「相机预览实时滤镜」时才评估 `wysaid/android-gpuimage-plus`（MIT，但含 native/CMake、CameraX，集成重）；**编辑器内不用 GL**，CPU LUT 已覆盖静态滤镜需求。做之前先确认 ABI/多 artifact 与离线仓库可取性。
5. **人脸贴纸/AR**：MediaPipe Face Landmarker（Apache-2.0，自研渲染）→ 归到 `camera-fx`/叠加层能力线（已在 roadmap 的 Lottie 叠加层之后），不进 `:mediaview`。VrFace 维护弱，仅参考。

### 第三优先（明确不做 / 缓做）
6. **不引入 burhanrashid52/PhotoEditor 依赖**：功能我们已经全覆盖且撤销更强，唯一的贴纸/文字增强用方案 1/2 自研吸收，不去引一条会打破离线构建的 View 栈。
7. **不引入 CanHub Cropper**：裁剪我们已有且归一化模型更讲究；其「全屏沉浸裁剪 + 旋转手柄」的体验可借鉴到我们 CropMode 的 UI 调整（低优先）。
8. **超分/去马赛克不进编辑器**：Real-ESRGAN 系许可未核实（NOASSERTION），且重（Vulkan/ncnn）。若产品要「高清增强」，应作为**离线后台任务 + 服务端能力**独立评估，与会话里的图像服务调研同一路线。

### 落地顺序建议（回写主线 weifu-refactor，遵守「每片核 diff+并线复跑再报」）
- **Slice A**：贴纸/emoji（`EditOp.Sticker` 数据模型 + 单测先行，渲染 + 面板）→ 复用 S0-S3 的验收套路（mediaview 单测 + 真机点按）。
- **Slice B**：文字描边/背景 + LUT 扩容（纯数据/渲染增量，风险最小，可与 A 并行）。
- **Slice C**：裁剪全屏沉浸化、滤镜栅格、历史面板（体验层，排后）。

### 验证纪律
- 每个 slice 沿用 `photo-edit-device-checklist.md` 的 D/T/M/A/S + R1-R5 真机点按，不自吹 PASS；
- 单测：`:mediaview:testDebugUnitTest` 118+ 必须保持全绿（新增 EditOp 类型的坐标/序列/undo 先写测试再写实现）；
- 结果回写由协调会话确认（装机单写者纪律：不自己 adb install）。