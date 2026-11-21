# 图片编辑美化工具升级设计（加强版编辑器）

> 状态：**方向已获用户批准，设计待审**。日期 2026-11-22。
> 范围：`:mediaview`（edit 子系统）、`:feature-chat` 接入、新模块 `:media-beauty`（P1）。
> 许可策略：**用户拍板**——本 app 为实验性质，开源代码可自由拷贝、裁剪、修改使用，
> 不受上游协议顾虑约束（正式商用前需另行合规复核，见 §7 风险）。

## 1. 意图

把现有图片编辑器（旋转/调色/画笔/文字/马赛克）升级为"体验特别好、功能特别强"的图片
编辑美化工具：对标美图/醒图的核心体验——**实时预览、裁剪、一键滤镜、自然美颜、贴纸**，
并与角色扮演场景打通（编辑完直接发给她）。

为什么现在做：图片消息 P1（`2026-11-21-image-messages-design.md`）打通"发图给她"的
链路后，"先编辑再发"是同一入口的自然的下一步；编辑器骨架已经正确，缺的是能力块。

## 2. 现状事实（已核实，2026-11-22）

| 能力 | 位置 | 现状 |
|---|---|---|
| 数据模型 | `mediaview/…/edit/PhotoEdits.kt` | 不可变 `EditOp` 列表 + undo/redo 快照栈；坐标归一化到原图 0..1；Rotate/Flip 提到 ops 头部。**专业编辑器的正确骨架** |
| 渲染 | `PhotoRenderer.kt` | 一次性预处理链（旋转→镜像→Adjust ColorMatrix→降采样）+ 标注逐条重放；纯 CPU Canvas；`render()` 失败回原图 |
| 编辑器 UI | `PhotoEditor.kt`（715 行） | 无 ViewModel，局部 remember；6 工具栏（旋转/镜像/画笔/文字/马赛克/调节）；预览**每次 ops 变化全量重放 1280 长边位图**（L137-139）；拖动中的笔走 Canvas 叠加层实时画 |
| 导出 | `PhotoEditExporter.kt` | 解码 2048 → 渲染 4096 → 上传兜底 2048，三层尺寸 |
| 接入 | `ChatRoute.kt:249`（唯一调用点） | 条件叠加 Box 非路由；选图/拍照 → 入草稿 → 编辑 → 同名写回暂存文件 |
| 缺口 | — | **无裁剪**（`allowCrop` 参数是空操作占位）、无滤镜、无美颜、无贴纸、无 GPU 路径（全仓 RuntimeShader/AGSL/GLES 零使用） |
| 约束 | `mediaview/build.gradle:20`、`PhotoRenderer.kt:19` | 自我约束「不带原生库、只依赖 Compose 和 Coil」；minSdk 28 / compileSdk 37 |
| 测试 | `PhotoEditsTest`（纯 JVM）、`PhotoRendererTest`（Robolectric NATIVE 真位图逐像素断言） | 两条测试路都现成，新 op 直接套 |

## 3. 方案与被否掉的备选

- **选定：自有骨架 + 模块外引地基**。保留 EditOp 重放模型，往里加能力块；美颜的
  原生依赖（OpenCV/MediaPipe）放新模块 `:media-beauty`，`:mediaview` 经端口接口消费，
  保住"不带原生库"的既有约束不被击穿。
- 备选 1：整个换开源编辑器（Simple Mobile Tools、各类 crop/c滤镜库）——否。
  会把 ChatRoute 接入、ops 模型、undo/redo、像素测试全部重做一遍；且没有一家带
  自然美颜（护城河本来就不在框架）。
- 备选 2：把 OpenCV/MediaPipe 直接塞进 `:mediaview`——否。模块级依赖爆炸（chatkit
  也依赖 mediaview，会连带拖大包体），且违背模块注释里的既有决定。
- 备选 3：全云端处理（服务端出图）——否。编辑要实时跟手，往返延迟毁体验；
  服务端只配 AI 重负载（P2 再议）。

## 4. 分期与能力设计

### P0 裁剪 + 一键滤镜（本计划实现）

**裁剪**
- 新 `EditOp.Crop(Left/Top/Right/Bottom: Float)`，归一化于**旋转/镜像后的显示画幅**
  （与画笔/文字坐标同一坐标系，所见即所得），排在 Rotate/Flip 之后、Adjust 之前。
- 渲染：预处理链在镜像之后插一步 `crop`（Canvas 平移+clip 或 createBitmap 交集），
  `displayWidth/Height` 改为按 crop 交集计算 → `EditCoords`/`EditHitTest` 的画幅语义
  随之自动正确（它们只消费 displayWidth/Height）。已画标注落在裁掉区域的：渲染时
  按现有归一化路径自然裁掉，**不做重新布局**（与剪映行为一致）。
- UI：编辑器工具栏加「裁剪」→ 进入独立裁剪模式（`allowCrop` 参数位落地）：
  四点把手 + 三分线 + 比例预设（自由/1:1/3:4/9:16）；裁剪模式内隐藏其他工具。
  确认后回普通编辑，撤销栈把它记一步。
- 旋转后未 90° 自由旋转**不做**（透视变换的显示↔源坐标换算要重写整个 EditCoords，
  收益/成本不划算，记入 P2 可选）。

**一键滤镜（3D LUT）**
- 新 `EditOp.Filter(id: String, intensity: Int /*0..100*/)`，同样进预处理链
  （Adjust 之后、降采样之前）。多条：语义=「只取最后一条」（与 Adjust 一致，
  面板互斥选择）。
- 资产：自制 6–8 个 `.cube` 3D LUT 进 assets（奶油胶片/冷白皮/复古胶片/氛围暗调/
  日系通透/黑白经典…）；运行时解析成 `ByteArray` 查找表。**不引入任何库。**
- 运行时三档：
  1. **API 33+**：AGSL `RuntimeShader` 3D LUT 采样 + `RenderEffect`，预览真 GPU；
  2. **API 28–32 预览**：CPU 三线性查表，但只作用于 640 长边的预览底图
     （预览降采样位图从 1280 改挂两档：滤镜滑杆期间 640，提交后 1280）；
  3. **导出**：CPU 全尺寸一次 pass（4096 上限，导出多 ~百 ms 可接受）。
- 滤镜与 Adjust 共存：面板上两个 tab（调节/滤镜），渲染顺序固定 Adjust→Filter。

**预览性能（P0 顺带）**
- `PhotoRenderer.render` 加增量入口：预处理结果（旋转+镜像+调色+滤镜后的底图）
  按 (source, ops 前缀哈希) 缓存，ops 只增标注时不重跑预处理。这是滤镜滑杆
  跟手的前提，也是美颜预览复用的地基。

### P1 自然美颜（下一步）

- 新 `:media-beauty` 模块：`com.google.mediapipe:tasks-vision`（FaceLandmarker，
  478 点，Apache-2.0）+ OpenCV Android SDK（bilateralFilter 磨皮）。
- `:mediaview` 定义端口 `interface BeautyProcessor { suspend fun process(bitmap, params): Bitmap }`
  注入；`PhotoEditor` 无注入时不显示「美颜」工具（保持库纯净）。
- 参数：磨皮（脸内 bilateral + 边缘保护，强度滑杆）、美白（Y 通道曲线）、
  锐化（unsharp）。**瘦脸大眼 P1 不做**（mesh warp 开源实现都是 demo 级，
  自然度要专项打磨，独立排期）。
- 处理结果作为**烘焙位图 op**（`Beauty(base: Bitmap)`）而非参数 op——landmark
  推理结果不进重放管线（每次重放推理一遍会卡死预览）；undo 即换回原烘焙缓存。
- 实时性目标：拍照后一次性处理（非相机实时），2s 内出结果即达标。

### P2 贴纸 / 整形 / AI（挂账不排期）

- 贴纸：`EditOp.Sticker(uri, x, y, scale, rotation)` 技术上是 ops 模型最便宜一扩；
  成本在**资产**——自制角色贴纸（她的人设印章、聊天气泡框），这是差异化。
- 瘦脸大眼/五眼四庭：landmark mesh warp + 参数精调专项。
- AI 抠图/背景虚化（MediaPipe Selfie Segmentation）、超分/修复（Real-ESRGAN/GFPGAN
  ——**模型权重要逐个核对是否禁商用**，代码许可不算）。

## 5. 已决定的 how（builder 不用再问的）

- `EditOp` 新子类命名与排序：`Crop` 在 Rotate/Flip 后、`Adjust`/`Filter` 前；
  烘焙位图不进 ops 序列化（P1，Beauty 结果以「预处理源替换」实现）。
- 滤镜强度合成：`intensity<100` 时输出 = lerp(原像素, LUT 像素, intensity/100)，
  AGSL 与 CPU 两路同一公式（有像素测试锁两路一致，容差 ≤2/255）。
- `.cube` 解析器自己写（格式简单，15–33 行表头 + 规则网格），资产上限 33³。
- 裁剪最少保留区 = 1/8 原图边长；裁剪不裁文字选中态等 UI 状态（回普通模式后
  选中清空）。
- 编辑器仍是 `PhotoEditor` composable，不引 ViewModel（715 行还能长，P1 美颜再评估）。

## 6. 验收

- 单测：Crop 的 displayWidth/Height 与 EditCoords 往返（4 旋转×裁剪组合）；
  Filter CPU/AGSL 两路同图同参像素一致；LUT 解析器 golden file；预处理缓存键正确性。
  全部沿用 PhotoEditsTest/PhotoRendererTest 既有风格。
- 真机：选图 → 裁剪 → 上滤镜 → 发给她（走图片消息 P1 链路，若其已合并）；
  低端机（ADY-AL00）滤镜滑杆拖动帧率目视 ≥30fps 不卡死；导出图与预览所见一致。

## 7. 风险与挂账

- 实验期许可策略是用户拍板；**若 app 走向正式发布，需对拷贝过的代码做一次许可清点**
  （GPL 系项目只可参考不可拷贝代码进发布物）。
- AGSL 机型碎片化：RuntimeShader 在个别老驱动上有 crash 报告，两路一致测试 +
  try/catch 降级 CPU 兜底。
- 美颜「自然度」是调参苦功，P1 排期按"能看"验收，不承诺对标醒图。
- OpenCV aar ~90MB（so 全 ABI）：只打包 arm64-v8a + 需要时走 App Bundle 语言/ABI 分割，
  实验包可整包。
- 预览降采样改 640 会让非滤镜机型文字/画笔预览略糊——可接受（点开大图不受影响），
  真机验收目视把关。

## 8. 与其他工作的关系

- 与图片消息 P1 无代码冲突（都只碰 feature-chat 的 ChatRoute 不同区域；若同期执行，
  编辑入口按钮位置在图片消息待发条定稿后再调）。
- ADY-AL00 真机窗口与 `video-channel-bugs` 会话协调（同图片消息计划的协调条款）。
