# 图片编辑器 P0（裁剪 + 一键滤镜）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `:mediaview` 编辑器加两项 P0 能力——裁剪（显示画幅裁剪 + 比例预设）与一键滤镜（3D LUT 预设 + 强度滑杆），并把预览改造成"底图缓存 + 标注重绘"两层，保证滤镜滑杆跟手。

**Architecture:** 全部在现有 EditOp 重放模型内扩展：`EditOp.Filter`/`EditOp.Crop` 作为**按类型挑选的一次性预处理步骤**插入渲染链（Rotate→Mirror→Crop→Adjust→Filter→降采样→标注）。LUT 数据经 `lutProvider: (String) -> Lut3D?` 注入渲染器（渲染器保持无 Context）。裁剪是**视口窗口变换**：标注永远存原图源坐标，crop 只经 `EditCoords` 的窗口函数换算，不改标注数据（撤销天然安全）。P0 纯 CPU，不引 AGSL/GPU/任何新依赖。

**Tech Stack:** Kotlin、Compose、android.graphics（Canvas/ColorMatrix/createBitmap）、Robolectric NATIVE 像素测试、python3（tools/ 资产脚本）。

**Spec:** `docs/superpowers/specs/2026-11-22-photo-editor-upgrade-design.md`（实现其 §4 P0 与 §5 裁量全部；§5 裁量记录是裁决依据，冲突时以设计文档为准）

## Global Constraints

- 仓库：`/Users/gqli/work/myandroidTest/.claude/worktrees/elastic-mendeleev-ab3d36`，分支 `claude/elastic-mendeleev-ab3d36`。每 Task 独立提交，尾行 `Co-Authored-By: Claude Code <noreply@anthropic.com>`；**不合并、不推送**（主会话收尾）。
- `:mediaview` 零新依赖：不引 androidx.graphics/AGSL/OpenGL/OpenCV，只用现有 Compose + android.graphics + 标准库（设计 §5 裁量 5）。
- 坐标铁律（设计 §5 裁量 1/3）：标注（Stroke/Text/Mosaic）坐标**永远归一化于原始未变换位图**；`EditOp.Crop` 的 ltrb 归一化于**旋转/镜像后的显示画幅**；两者之间**只允许 `EditCoords` 一处**做 crop 窗口换算，禁止在 HitTest/PhotoEditor/渲染器里另发明第二套。
- `EditOp.Filter` 语义 = 最后一条生效（面板互斥）；`EditOp.Crop` 全列表至多一条，二次裁剪**替换**；存在 Crop 时旋转/镜像入口禁用（裁量 2）。
- 渲染顺序固定：Rotate(累积)→Mirror(奇偶)→Crop(交集)→Adjust(最后)→Filter(最后+intensity lerp)→downscale→标注逐条。`Adjust` 与 `Filter` 是两个独立字段，顺序不可交换。
- 测试风格沿用现状：模型/几何纯 JVM JUnit（PhotoEditsTest 风格），像素断言 Robolectric `@GraphicsMode(NATIVE) @Config(sdk=[34])` 真 Bitmap `getPixel`（PhotoRendererTest 风格）。跑法：`./gradlew :mediaview:test`。
- 强度合成唯一公式：`out = orig + (lut(orig) - orig) * intensity / 100`，逐通道，预览与导出同一函数（无两路）。
- `ChatRoute.kt` 是编辑器唯一外部消费者：`PhotoEditor(bitmap, onCancel, onDone)` 对外签名不加必填参数；`PhotoEditExporter.export` 的调用点同步改造在 Task 7 内完成。
- minSdk 28：不写任何 API 33-only 代码路径。

## Review Focus

设计隐含、单 Task 测试易漏、最可能咬人的五类（各自钉在归属 Task）：

1. **裁剪后触摸/点选漂移**：触摸点经裁剪窗口回源坐标，`editsWithoutOps` 选中框路径、live 叠加层必须同一窗口——任何一处漏了窗口，点选/选中框/实时笔迹就错位。→ Task 3 测窗口互逆，Task 6 测选中框含 Crop。
2. **缓存脏读**：base 缓存键必须含 maxLongEdge 与 source 身份；滑杆 `withOpsUnrecorded` 只改 Adjust/Filter 参数时应命中同键底图缓存（不重跑旋转/镜像/crop），而 ops 列表换标注时不得失效误判。→ Task 4 用计数 provider 钉死。
3. **出界标注**：落在裁掉区域的标注不渲染但**不删数据**，撤销裁剪后原样复现。→ Task 3 Robolectric 像素测。
4. **滑杆提交语义**：拖动期间 `withOpsUnrecorded` 不污染 undo 栈，`onValueChangeFinished` 恰提交一次历史。→ Task 6 JVM 测。
5. **导出失败不得用原图覆盖草稿**：渲染失败 export 返回 null，ChatRoute 走"已使用原图"提示路径。→ Task 7。

---

### Task 1: LUT 内核——.cube 解析器 + CPU 三线性应用（纯算法）

**Files:**
- Create: `mediaview/src/main/java/com/openapi/mediaview/edit/Lut3D.kt`
- Create: `mediaview/src/test/java/com/openapi/mediaview/edit/Lut3DTest.kt`
- Create: `tools/gen_luts.py`（生成 6 张 33³ .cube 到 `mediaview/src/main/assets/filters/`）

**Interfaces:**
- Produces:
  ```kotlin
  data class Lut3D(val size: Int, val rgb: FloatArray)  // size³*3，值域 0..1，r 最快变
  object CubeLut { fun parse(text: String): Lut3D? }     // 坏文件一律 null，不抛
  object LutApply { fun apply(target: Bitmap, lut: Lut3D, intensity: Int): Bitmap } // 就地改可变 target 并返回它
  object FilterPresets { val ids: List<String> /* creamy coolwhite retro moody airy mono */; const val ASSET_DIR = "filters" }
  ```
- Task 2 用 `LutApply.apply`，Task 6 用 `FilterPresets.ids`；渲染器经 `(String) -> Lut3D?` 注入，LUT 与 Bitmap 解耦。

- [ ] **Step 1: 写失败测试**（像素用 Robolectric NATIVE；解析用纯 JVM）：
  - 解析：内嵌 2³ identity cube 文本 → `size==2`、9 组值正确；`LUT_ORDER` 非 RGB 首行 / 数字不够 size³ / 非数字 → null；4³ 表头 `LUT_3D_SIZE 4` 解析成功。
  - 应用：identity LUT → 每个采样像素 delta ≤1；"抬黑"cube（黑场→0.2）→ 纯黑像素变亮且非纯白像素 delta ≤1（只动暗部——若实现是全局线性就抓不住，cube 值要设计成只抬低端）；`intensity=0` → 像素全同；`intensity=50` → 黑像素 ≈ (0+51)/2 ±2（lerp 中点）；三线性连续性：相邻输入值输出差单调无跳变（沿对角线采样 64 点断言 max 步进 ≤ 8/255）。
- [ ] **Step 2: `./gradlew :mediaview:test --tests '*Lut3DTest'` 确认编译失败**
- [ ] **Step 3: 实现**：解析器逐行扫 `LUT_3D_SIZE`/`TITLE`/`DOMAIN_`，其余按三元 float 收；应用 = 每像素把 r/g/b 映射到 `size-1` 网格、取包围 8 角三线性插值（含 intensity lerp 公式，逐通道 float，ARGB 拆回）。性能注记：每像素 O(1)，640×480 ≈ 30 万点，纯 Kotlin 循环可接受（不要求 SIMD）。
- [ ] **Step 4: 测试绿**：`./gradlew :mediaview:test --tests '*Lut3DTest'`
- [ ] **Step 5: 写 `tools/gen_luts.py` 并生成资产**：python 标准库；6 个预设各由「饱和度/对比曲线/色温偏移/暗部抬升」参数组合程序化采样 33³ 写出 .cube；运行 `python3 tools/gen_luts.py mediaview/src/main/assets/filters` 生成 6 文件；加一条测试读真实资产：6 个文件都 `parse != null && size == 33`。
- [ ] **Step 6: 全模块 `./gradlew :mediaview:test` 绿 + 提交**：`git commit -m "feat(mediaview): 3D LUT 解析与 CPU 三线性应用 + 程序化滤镜资产"`

### Task 2: `EditOp.Filter` 接入模型与渲染管线

**Files:**
- Modify: `mediaview/…/edit/PhotoEdits.kt`（EditOp 新子类 + `filter` 访问器）
- Modify: `mediaview/…/edit/PhotoRenderer.kt`（预处理链插 Filter 步 + `lutProvider` 参数）
- Test: 扩展 `mediaview/src/test/java/com/openapi/mediaview/edit/PhotoRendererTest.kt`、`PhotoEditsTest.kt`

**Interfaces:**
- Consumes: Task 1 `Lut3D/LutApply`。
- Produces: `data class EditOp.Filter(val id: String, val intensity: Int)`（intensity 域 0..100）；`PhotoEdits.filter: EditOp.Filter?`（ops 中最后一条）；渲染器新签名
  ```kotlin
  fun render(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int = MAX_OUTPUT_LONG_EDGE,
             lutProvider: (String) -> Lut3D? = { null }): Bitmap
  ```
  渲染顺序按 Global Constraints；`lutProvider` 返回 null（缺资产）→ 跳过该滤镜（图保持 Adjust 后状态）。Task 4 把预处理段拆出 `renderBase` 时保持本任务的链序。

- [ ] **Step 1: 写失败测试**：`PhotoEditsTest`——两条 Filter 时 `filter` 取最后；无 Filter 时 null；Adjust 访问器回归。`PhotoRendererTest`（Robolectric，`lutProvider` 传内嵌抬黑 Lut3D 构造）：带 Filter 的黑像素变亮；`intensity=0` 与不带 Filter 像素全同；provider 返回 null 时与不带 Filter 全同；Filter+Adjust 共存时输出 = 先 Adjust 后 Filter（对照分步手算的像素值 ±2）。
- [ ] **Step 2: 跑上述测试确认失败**
- [ ] **Step 3: 实现**：`PhotoEdits.filter` 照 `adjust` 的 `lastOrNull` 写法；`renderInternal` 在 `applyAdjust` 之后、`downscaleIfNeeded` 之前插 `applyFilter(bmp, edits.filter, lutProvider)`（`LutApply.apply` 需可变位图——复用现有 canvasTarget 可变性保护，必要时 copy 可变副本）。`render` 旧调用点（PhotoEditor L138、exporter）暂传默认 `{ null }`，行为零变化。
- [ ] **Step 4: `./gradlew :mediaview:test` 全绿 + 提交**：`git commit -m "feat(mediaview): EditOp.Filter 接入渲染管线（lutProvider 注入，最后一条生效）"`

### Task 3: `EditOp.Crop`——模型、窗口换算、渲染交集

**Files:**
- Modify: `mediaview/…/edit/PhotoEdits.kt`（Crop 子类、`crop`/`setCrop`、`displayWidth/Height` 计入 crop）
- Modify: `mediaview/…/edit/EditCoords.kt`（crop 窗口换算，唯一实现处）
- Modify: `mediaview/…/edit/EditHitTest.kt`（经 EditCoords 新入口取坐标）
- Modify: `mediaview/…/edit/PhotoRenderer.kt`（镜像后、Adjust 前插 crop 步）
- Test: 扩展 `PhotoEditsTest.kt`、`PhotoRendererTest.kt`；新建 `EditCoordsCropTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class EditOp.Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) : EditOp // 归一化于显示画幅，0<=l<r<=1, 0<=t<b<=1
  val PhotoEdits.crop: EditOp.Crop?          // 最后一条
  fun PhotoEdits.setCrop(crop: EditOp.Crop?): PhotoEdits  // 已有则 replaceAt 替换（一步历史），否则 push；null 移除
  object EditCoords {
      fun cropWindow(edits: PhotoEdits): Rect  // 无 Crop = Rect(0,0,1,1)；在"旋转后全幅显示归一化"空间
      fun toDisplayNorm(x, y, edits): Offset   // 现有签名：语义升级为「源 → 裁剪后显示帧」，内部 = 全幅映射 − 窗口
      fun toSourceNorm(x, y, edits): Offset    // 逆变换
  }
  ```
  `displayWidth/Height` = 旋转后尺寸 × 窗口宽/高（四舍五入）。渲染链插入点见 Interfaces-Task2 链序。**不改标注数据**：出界标注靠渲染 clip（crop 步后 canvas 坐标系即裁剪帧，归一化源点经窗口映射后 0..1 外的由 clip 截断）。

- [ ] **Step 1: 写失败测试**：
  - `EditCoordsCropTest`（纯 JVM）：无 Crop 时新旧语义一致（恒等窗口回归现有测试）；`toDisplayNorm→toSourceNorm` 在 4 旋转×2 镜像×3 种 crop 组合下往返互逆（delta ≤1e-4）；窗口边角点映射到 0/1 边界。
  - `PhotoEditsTest`：`setCrop` 替换不堆叠（两条 ops 里 Crop 恒一条）、替换占一步 undo；`displayWidth/Height` 旋转 90°×crop(0.1,0.1,0.9,0.9) 数值精确。
  - `PhotoRendererTest`：400×200 渐变图 crop 中心 50% → 输出 200×100、四角像素等于源对应点 ±2；带一条中心白线 Stroke + 覆盖左半的 Crop → 左半输出无白线像素、右半有（出界标注被 clip 不渲染）；undo crop 后白线左半复现（对 undo 后 ops 重渲染断言）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：crop 渲染步 = `Bitmap.createBitmap(mirrored, x, y, w, h)`（镜像后尺寸上取整）；`EditCoords` 现有 `toDisplayNorm` 全幅映射保留为 private `toFullDisplayNorm`，公开入口叠窗口；`EditHitTest.findOpAt/selectionBounds/textBoundsPx` 内部的全幅调用全部改走新入口（签名不变）。`editsWithoutOps`（PhotoEditor.kt:382）本 Task 不动——它现在只保留 Rotate/Flip，Crop 会被滤掉；Task 6 扩为保留 Crop（裁量 3），本 Task 的测试不要依赖它的 Crop 行为。
- [ ] **Step 4: `./gradlew :mediaview:test` 全绿（现有坐标/点选测试零回归）+ 提交**：`git commit -m "feat(mediaview): EditOp.Crop 裁剪（窗口换算集中于 EditCoords，标注坐标不迁移）"`

### Task 4: 预览底图/标注分层 + renderBase 缓存

**Files:**
- Modify: `mediaview/…/edit/PhotoRenderer.kt`（拆 `renderBase`/`drawOps`，`render` 合成两者）
- Create: `mediaview/…/edit/RenderBaseCache.kt`
- Test: 新建 `RenderBaseCacheTest.kt`；扩展 `PhotoRendererTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object PhotoRenderer {
      fun renderBase(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int, lutProvider: (String) -> Lut3D? = { null }): Bitmap // 到降采样为止，无标注
      fun drawOps(base: Bitmap, edits: PhotoEdits): Bitmap          // 可变副本上画 Stroke/Text/Mosaic，返回副本
      fun render(source, edits, maxLongEdge, lutProvider): Bitmap   // = drawOps(renderBase(…))，对外行为不变
  }
  class RenderBaseCache(capacity: Int = 4) {
      fun get(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int): Bitmap?   // 键含 identity + 预处理指纹
      fun put(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int, bmp: Bitmap)
  }
  ```
  预处理指纹 = 覆盖 Rotate/Flip/Crop/Adjust/Filter 的规范化字符串（不含标注）。PhotoEditor 接线在 Task 6（本 Task 只交付渲染层与缓存，`render` 对外行为零变化保证现网预览不受影响）。

- [ ] **Step 1: 写失败测试**：`PhotoRendererTest`——`renderBase` 输出无标注（同图带 Stroke：renderBase 像素与无标注 renderBase 全同）；`drawOps(renderBase, edits)` 与整链 `render` 逐像素一致（±1）。`RenderBaseCacheTest`（纯 JVM，Bitmap 用 `Bitmap.createBitmap` 需 Robolectric——整类标 Robolectric）：同 (source, 预处理, 边) 命中；**maxLongEdge 不同不共享**（640/1280 各存各）；只改标注 ops → 仍命中；改 Adjust 参数 → miss；source 换一张（identity 变）→ miss；put 6 帧只留 4（capacity 淘汰）；provider 计数——同键二次 get 期间 lutProvider **零调用**（钉 Review Focus 2 的重算路径）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：拆分是搬移不是重写（预处理段进 renderBase、L72-79 循环进 drawOps）；`render` 先查逻辑不变（内部可先不接缓存，PhotoEditor 在 Task 6 接）。指纹字段顺序固定：`q=,flip=,crop=,adj=,flt=`。缓存不手动 recycle（API 26+ 像素在 Java 堆，LruCache 逐出交给 GC）。
- [ ] **Step 4: `./gradlew :mediaview:test` 全绿 + 提交**：`git commit -m "perf(mediaview): 渲染拆 renderBase/drawOps + 预处理结果 LruCache"`

### Task 5: 编辑器裁剪模式 UI

**Files:**
- Create: `mediaview/…/edit/CropMode.kt`（裁剪模式全屏层：把手/网格/比例预设/确认取消）
- Modify: `mediaview/…/edit/PhotoEditor.kt`（EditorTool 加 `Crop`；ToolBar 加裁剪入口（落地 `allowCrop`）；`edits.crop != null` 时旋转/镜像 ToolItem 置灰；预览 fit 逻辑走窗口后的 `displayWidth/Height`）
- Test: `CropMode` 几何辅助（纯函数）进新建 `CropModeTest.kt`

**Interfaces:**
- Consumes: Task 3 `EditOp.Crop/PhotoEdits.setCrop/EditCoords.cropWindow`。
- Produces: `CropEditorState(rect: RectF, aspect: CropAspect)`，`enum class CropAspect { Free, Square, FourThree, SixteenNine }`；`data class CropHandle` 命中测试 `fun hitHandle(rect: RectF, touch: Offset, slopPx: Float): CropHandle?`；确认出口 `onConfirm(EditOp.Crop)`——**编辑器把当前显示帧（已含既有 crop 窗口）内的新框换算回全 Crop 语义**：若已有 Crop，新框先经 `cropWindow` 合成到全幅显示空间再写 Crop（一步 undo 覆盖旧裁剪）。
- 出界标注的预览表现自动正确（renderBase 已 clip），无需特判。

- [ ] **Step 1: 写失败测试（几何纯函数）**：hitHandle 四角 ± slop 命中正确把手、框内非把手区返回 null；`CropAspect` 应用到 400×200 帧：Square 得到 200×200 居中框、SixteenNine 高度钳到画幅；既有 Crop 存在时，裁剪模式里再框选 → `setCrop` 后 `edits.crop` 等价两窗口复合（用 EditCoords 对一个采样点验证：复合前后 toSourceNorm 相等 ±1e-4）。
- [ ] **Step 2: 确认失败 → Step 3: 实现**：CropMode = Compose 叠加层（半透明外遮罩四块、三分线、四角把手 pointerInput 拖拽、最小框 = 当前画幅 1/8 边长（裁量 11）、比例预设顶栏）。PhotoEditor：进 Crop 工具时**隐藏标注/调色面板与 live 叠加**，只留 CropMode；确认 `edits = edits.setCrop(newCrop)` 并退出工具；有 Crop 时 `Rotate/Flip ToolItem` 传 enabled=false（置灰样式照 Material enabled 态）。
- [ ] **Step 4: `./gradlew :mediaview:test` 全绿 + `./gradlew :chatkit:assembleDebug :feature-chat:assembleDebug` 编译零破坏**
- [ ] **Step 5: 提交**：`git commit -m "feat(mediaview): 编辑器裁剪模式（四角把手+比例预设+裁后禁旋转）"`

### Task 6: 滤镜面板 UI + 预览接线（缓存/640 降档）+ 滑杆语义

**Files:**
- Create: `mediaview/…/edit/FilterPanel.kt`（预设网格 + 强度滑杆 + 原图对比按钮）
- Create: `mediaview/src/main/java/com/openapi/mediaview/edit/LutAssets.kt`
- Modify: `mediaview/…/edit/PhotoEditor.kt`（EditorTool 加 `Filter`；预览接线 renderBase+cache+640 降档；`editsWithoutOps` 保留 Crop）
- Test: 扩展 `PhotoEditsTest.kt`（滑杆历史语义）

**Interfaces:**
- Consumes: Task 1 `FilterPresets`、Task 2 `EditOp.Filter`、Task 4 `renderBase/drawOps/RenderBaseCache`。
- Produces: `object LutAssets { fun provider(context: Context): (String) -> Lut3D? }`（assets/filters/<id>.cube 解析结果按 id 记忆化；失败返回 null）。预览管线（PhotoEditor 内）：
  - 常态：`renderBase(previewSource, edits, 1280, provider)` 过 cache → `drawOps`；
  - 滑杆拖动中：base 边降为 **640**，标注叠加层同比例缩放绘制路径复用现有 live 机制；
  - `onValueChangeFinished`：`replaceAt/push` 恰一次进历史（Filter 已有则 replaceAt）。
  `editsWithoutOps`（PhotoEditor.kt:382）从"仅 Rotate/Flip"扩为"仅 Rotate/Flip/Crop"（Review Focus 1）。

- [ ] **Step 1: 写失败测试（JVM）**：滤镜滑杆语义——`withOpsUnrecorded` 改 Filter 参数不增 undo 栈（栈快照不变），`replaceAt` 后 undo 回到拖动前参数；两条 Filter 经面板路径不可能共存（`setFilter` 辅助函数替换语义，加 `PhotoEdits.setFilter(id, intensity)` 到 PhotoEdits.kt）。
- [ ] **Step 2: 确认失败 → Step 3: 实现 `setFilter` + FilterPanel**：预设 6 格缩略图（对 128px 底图缩略逐个 `LutApply` 预渲染，`produceState` 异步）、点击预设 = `setFilter(id, 100)`、再次点击已选中预设 = 移除（回到无滤镜）、强度 `Slider` 走 `withOpsUnrecorded`/`onValueChangeFinished` 模式（照 AdjustRow L674-681 现例）、"对比"按钮按住 = 临时 `setFilter(null)` 松开还原。LutAssets：`assets.open("filters/$id.cube")` → `CubeLut.parse`，`mutableMapOf<String, Lut3D?>` 记忆化（null 也记，失败不反复读盘）。
- [ ] **Step 4: PhotoEditor 预览接线**（上文管线三档；`remember` 持有 RenderBaseCache；provider 用 `LutAssets.provider(LocalContext.current)`；拖动标志沿用现有 `liveBase` 状态扩一个 `isSliderActive`）。ToolBar 顺序：旋转 镜像 裁剪 画笔 文字 马赛克 滤镜 调节（8 项放不下则 5 可见 + 折叠箭头——按现状宽度实测定，样式不新造）。
- [ ] **Step 5: `./gradlew :mediaview:test` 全绿 + assembleDebug 编译**
- [ ] **Step 6: 提交**：`git commit -m "feat(mediaview): 滤镜面板与预览缓存接线（滑杆 640 降档、底图缓存）"`

### Task 7: 导出硬化 + ChatRoute 适配 + 真机验收

**Files:**
- Modify: `mediaview/…/edit/PhotoEditExporter.kt`（`export` 失败返回 null；接 lutProvider）
- Modify: `feature-chat/…/ChatRoute.kt`（export 传 assets；null → 现成"编辑失败"提示路径，不覆盖暂存）
- 验收记录：`/Users/gqli/Library/Application Support/Claude-3p/side-session-notes/local_0c440dde-5831-45ce-9a97-c5d3f275b91b/photo-editor-p0-accept.md`

**Interfaces:**
- Consumes: 前六个 Task 全部。
- Produces: `fun export(source: Bitmap, edits: PhotoEdits, cacheDir: File, assets: android.content.res.AssetManager): Uri?`（内部 `LutAssets.provider` 构 provider）；`PhotoRenderer.render` 对外保留回退语义（预览必须有图），导出侧新增 `internal fun renderStrict(source, edits, maxLongEdge, lutProvider): Bitmap?`——失败返回 null（Review Focus 5 的实现落点：回退语义只留在预览路径）。

- [ ] **Step 1: 写失败测试**：`renderStrict` 对必然 OOM/异常输入（可用 mock spy 抛异常的最简 fake 或反射注入）→ null；`export` 对 renderStrict null → 返回 null 且不写文件（临时目录断空）；正常导出回归（现有无测试，补一条：identity edits 导出的 Uri 文件存在且 >1KB）。
- [ ] **Step 2: ChatRoute 适配**：`export(editing.second, edits, context.cacheDir, context.assets)`；`exported == null` 时**不调** `onReplaceDraftImage`，复用 `SendActions` 现成文案路径提示"编辑失败，已使用原图"（ChatRoute 侧 Toast，不碰 SendActions 签名）。
- [ ] **Step 3: `./gradlew :mediaview:test :feature-chat:testDebugUnitTest` 全绿 + 提交**：`git commit -m "fix(mediaview): 导出失败不再静默回退原图；ChatRoute 适配 assets 滤镜与失败提示"`
- [ ] **Step 4: 真机（ADY-AL00，`adb push` + `pm install -r`；设备与 `video-channel-bugs` 会话时间窗协调——执行前向主会话要设备）**：
  1. 相册选图 → 进编辑 → 裁剪 1:1 → 上「moody」滤镜 → 画笔写字（字色不被滤镜改变）→ 保存 → 发送草稿预览与编辑所见一致（目视：黑字仍黑、暗部抬升）；
  2. 滤镜强度 0↔100 拖动跟手性目视（掉帧记录，不达标回报开 P2 GPU 单）；"对比"按钮按住/松手还原正确；
  3. 有 Crop 时旋转/镜像置灰不可点；裁剪内再进裁剪 = 复合替换；undo 一步回裁剪前（标注复现）；
  4. 导出大图（4000×3000 源）耗时记录（目标 <3s）；飞行模式重复一遍不崩；
  5. 结论+截图路径写 notes 文件，两仓无服务端改动。

---

## 裁量记录（执行层决定，照办）

- **标注重映射 = 窗口换算，不是数据迁移**：设计文档"提交裁剪时重映射标注"的落地 = EditCoords 窗口函数（Task 3），标注字节不动——撤销语义天然成立（Review Focus 3 的测试因此可能），不实现任何 ops 改写。
- ToolBar 8 项拥挤：优先缩图标间距一行放下；放不下列表折叠（`More` 箭头 + 下拉），折叠样式复用 `StylePanel` 现有表面色，不新造视觉。
- 滤镜缩略图预渲染尺寸 128px 固定；预渲染失败（资产缺）显示纯色占位块不崩。
- `setCrop(null)` = 移除裁剪（undo 链里作为一步提交），本 UI 无"清除裁剪"按钮（退出裁剪模式不改即无此路径），函数保留备 P1。
- `renderStrict` 的"失败"判定 = 渲染内部 catch 返回 null（把现有 catch 上移一层），不用引用比较 hack。
