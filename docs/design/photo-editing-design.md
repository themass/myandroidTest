# 对话里的图片本地编辑：选型与集成设计

日期：2026-11-11
分支：`claude/happy-fermi-300550`（基线 `weifu-refactor`）
相关：`docs/design/architecture.md` 第 3 节（分层与依赖规则）、`docs/design/permissions-design.md`

---

## 0. 状态总览（先说清楚哪些是代码，哪些是设计）

| 项 | 状态 | 证据 |
|---|---|---|
| 选型调研与结论（第 2、3.1 节） | **已完成** | 本文 |
| 操作模型 `PhotoEdits` | **已实现** | `mediaview/.../edit/PhotoEdits.kt` |
| 渲染器 `PhotoRenderer` | **已实现** | `mediaview/.../edit/PhotoRenderer.kt` |
| 导出器 `PhotoEditExporter` | **已实现** | `mediaview/.../edit/PhotoEditExporter.kt` |
| Compose 编辑器 `PhotoEditor` | **已实现** | `mediaview/.../edit/PhotoEditor.kt` |
| 操作模型单测 | **已实现并通过** | `mediaview/src/test/.../PhotoEditsTest.kt` |
| 接入对话草稿托盘「编辑」入口 | **设计但未实现** | 第 4 节第 2 步 |
| 历史消息「编辑后重发」 | **设计但未实现** | 第 4 节第 3 步 |
| 拍照解题结果页「标注」入口 | **设计但未实现**（可选） | 第 4 节第 4 步 |
| 贴纸 / emoji / 滤镜 | **明确不做** | 第 3.1 节末尾 |
| 真机手感与导出耗时 | **待验证** | 第 6 节 |

> 上一轮先落了代码，是因为编辑器是本次唯一「不接业务也能独立验证」的件；
> 文档里的接入步骤（第 4 节）确实还没做，对应上表后三行。

---

## 1. 现状

### 1.1 对话里的图片现在能做什么

| 位置 | 现在的能力 | 文件 |
|---|---|---|
| 发送前草稿托盘 | 选图、拍照后直接进托盘，**不能编辑** | `feature-chat/.../presentation/SendActions.kt`、`ui/ChatRoute.kt` |
| 消息里的图片 | 全屏查看（翻页、双指缩放、双击放大）、长按菜单：下载 / 复制图片 / 分享 / 查看路径 | `chatkit/.../media/ImageViewer.kt`、`feature-chat/.../ui/AttachmentActions.kt` |
| 图片的落盘与压缩 | `ImageCompressor`：长边 1600、JPEG 82 起、≤800 KB | `common/.../image/ImageCompressor.kt` |

`ImageAttachment` 只存一个 `path`（应用私有目录下的本地文件）。所以「编辑后回传给对话」最自然的形态就是：**写一个新文件，把 path 交回去**，不引入新的数据类型。

### 1.2 可复用的已有件

- `:mediaview` 已经是第 1 层的「媒体查看」模块（Compose + Coil，不认识业务），`chatkit` 依赖它。
- `:common` 是第 0 层（合并后唯一底层），放工具、网络、主题、权限；不适合放「编辑图片」这种成体系的交互。
- `:feature-chat` 是第 3 层，业务组合。

---

## 2. 开源库调研

调研方法：以 GitHub 仓库页 / README / Maven 页面为准（2026-11 抓取）。**抓不到的版本号、日期一律写「未验证」，不猜。**

| 库 | 最新版本 | 坐标 | 许可证 | 形态 | 裁剪 | 旋转/镜像 | 亮度/对比度 | 涂鸦 | 文字 | 马赛克 |
|---|---|---|---|---|---|---|---|---|---|---|
| **CanHub/Android-Image-Cropper**（`edmodo`→`ArthurHub`→`CanHub` 的现行分支） | 4.7.0（README 固定该版本；发布日期未在页面给出） | `com.vanniktech:android-image-cropper:4.7.0` | Apache-2.0 | View；需 `CropImageActivity` 或嵌入 `CropImageView` | ✅ | 旋转 ✅ / 镜像 ❌（页面未提） | ❌ | ❌ | ❌ | ❌ |
| **Yalantis/uCrop** | 2.2.11 | `com.github.yalantis:ucrop:2.2.11`（JitPack；`-native` 变体 +~1.5 MB） | Apache-2.0 | View/Activity；**必须在 Manifest 声明 `UCropActivity`** | ✅ | 旋转 ✅ / 镜像 ❌（页面未提） | ❌ | ❌ | ❌ | ❌ |
| **burhanrashid52/PhotoEditor** | 3.1.1 | `com.burhanrashid52:photoeditor:3.1.1` | MIT | View；需 `PhotoEditorView` 包裹 `ImageView` | ❌（作者 FAQ 明确「Currently, No.」） | 仅插入对象的旋转，无整图旋转 | 有滤镜（`PhotoFilter.BRIGHTNESS` 等），但走 `GLSurfaceView`，作者自己标注「a lot of issues」 | ✅ | ✅ | ❌ |
| **cats-oss / wasabeef android-gpuimage** | **未验证**（README 里写的是占位 `2.x.x`） | `jp.co.cyberagent.android:gpuimage` | Apache-2.0 | `GLSurfaceView` + ~120 个 shader 滤镜 | 只有 Crop 滤镜（无 UI） | 无 UI | 滤镜层面有 | ❌ | ❌ | 只有整图 Pixellate，**没有区域马赛克** |
| ~~ngoctrungit/ImageEditor-Android~~ | — | — | — | — | — | — | — | — | — | — |
| SimurghPhotoEditor（Compose） | **未验证** | — | — | Compose | ✅ | 有 | 有 | ✅ | ✅ | **未验证** |

几点补充事实：

- **`ngoctrungit/ImageEditor-Android` 无法验证**：`github.com/ngoctrungit/ImageEditor-Android` 现在返回 404，搜索也查不到 `com.github.ngoctrungit` 的任何 JitPack 坐标。结论：**不采用**，也不能在文档里给它编造版本号和许可证。
- **没有任何一个库同时满足「裁剪 + 涂鸦 + 文字 + 区域马赛克」**。四类能力分散在裁剪库（CanHub/uCrop）和标注库（PhotoEditor）里，混用意味着两套 View 体系、两套 URI 约定、两套保存逻辑。
- **所有候选都是 View 体系**（PhotoEditor 明确无 Compose API；uCrop / CanHub 只有 Activity 或 View）。而本项目的对话界面已经是 Compose（`chatkit`），引入 View 库要么包一层 `AndroidView` 并处理生命周期，要么走「启动一个 Activity 再回来拿 Uri」——后者会打断「编辑完立刻接着发」的连贯性。
- **所有候选的能力边界都不覆盖本次需求**：PhotoEditor 没有裁剪和马赛克，CanHub/uCrop 没有涂鸦/文字。

### 2.1 一个决定性的工程约束

本机 Gradle 离线缓存里**没有**任何一个候选库（`~/.gradle/caches/modules-2/files-2.1` 下没有 `com.vanniktech` / `com.github.yalantis` / `com.burhanrashid52` / `jp.co.cyberagent`）。引入任意一个都会让 `./gradlew --offline` 构建失败，需要联网拉 JitPack 或 Maven Central，并且在被墙的网络环境下 JitPack 拉取本身就不稳（uCrop 只在 JitPack 发布）。

这不是说永远不能引第三方库，而是说：**为了三个可自绘的基础操作（折线、文字、色块）引入两套 View 编辑器，换来的收益配不上它带来的构建风险、包体和 Compose 互操作成本。**

---

## 3. 方案

### 3.1 选型结论

**自建 Compose 编辑器，放在 `:mediaview`，不引入第三方库。**

理由按权重排序：

1. **依赖方向**：`:mediaview` 是第 1 层，只依赖 Compose/Coil，已经不认识业务；`chatkit`（第 1 层）和 `:feature-chat`（第 3 层）都可以直接用。放进 `:common`（第 0 层）会让工具/网络/主题模块背上图像处理代码；放进 `:feature-chat` 则拍照解题用不上，以后头像编辑也要重写一份。
2. **能力完整度**：需求里的裁剪、旋转、亮度/对比度/饱和度、涂鸦、文字、马赛克，用 Compose Canvas + `android.graphics` 全部能实现，且**操作模型（见 3.2）天然支持撤销/重做**——这是 PhotoEditor 的 View 叠加方式做不到的（它靠遍历 view 树删最后一个子 view）。
3. **不打断对话流**：编辑器是一个 Compose 页面，编辑完直接 `onDone(edits)` 回到对话继续发送，不需要 Activity 往返。
4. **包体**：新增代码约 700 行，0 个第三方依赖；对比 PhotoEditor（含 GL 滤镜）+ CanHub 两个 AAR 再加 R8 规则。
5. **不引入 GL/原生风险**：PhotoEditor 的滤镜走 `GLSurfaceView`，在低端机、多窗口、后台恢复时有已知问题；本项目已经吃过 16 KB page size 的苦（见 `imaging/build.gradle` 里 OpenCV 的注释），不再引入新的原生依赖。

**代价**：没有贴纸/emoji/120 种滤镜。这几项不在本次需求里（需求是「裁剪、旋转、亮度/对比度、涂鸦、文字、马赛克」）。如果以后要滤镜，优先复用 `:imaging` 已有的 OpenCV CLAHE/锐化管线，而不是引 gpuimage。

### 3.2 数据模型：操作列表，而不是「每画一笔改一次位图」

```
PhotoEdits(sourceWidth, sourceHeight, ops: [Rotate, Flip, Adjust, Stroke, Text, Mosaic], undoStack, redoStack)
```

- 一次编辑 = 一条不可变记录；界面每次改动生成新的 `PhotoEdits`，Compose 才能正确重组。
- **撤销/重做存的是 `ops` 的快照**，不是"弹出最后一条"。原因：选中后**删除**或**拖动**会改动列表中间的某一条，
  如果撤销只弹最后一条，撤销"拖动文字"会把整段文字删掉。快照让每种改动都能精确回到上一步。
  滑杆拖动期间用 `withOpsUnrecorded` 做实时预览，松手时 `withOps` 一次提交，所以拖一次滑杆只占一步撤销。
- 对已有标注的操作：`removeAt(i)` 删除、`replaceAt(i, op)` 替换（拖动、改颜色/字号/文字内容），内容没变不产生历史。
- **保存时只渲染一次**（`PhotoRenderer.render`），而不是每笔都重新编码整图——涂 30 笔也只是一次编码。
- 坐标一律是**相对原图的 0..1 归一化值**，所以旋转图片后已画的标注不会跑偏；渲染时统一做「原图坐标 → 画布坐标」映射。

文件：

| 文件 | 作用 |
|---|---|
| `mediaview/.../edit/PhotoEdits.kt` | 操作模型 + 撤销/重做 + 删除/替换/平移（纯 Kotlin，可单测） |
| `mediaview/.../edit/EditCoords.kt` | 原图坐标 ⇄ 显示坐标（旋转+镜像）的映射，纯函数，可单测 |
| `mediaview/.../edit/EditHitTest.kt` | 点选：点在哪条标注上、选中框多大（像素空间算距离，非正方形图也准） |
| `mediaview/.../edit/PhotoRenderer.kt` | 把操作重放到位图：旋转→镜像→颜色矩阵调整→标注→马赛克 |
| `mediaview/.../edit/PhotoEditExporter.kt` | 解码（EXIF 感知、按需采样）+ 渲染 + 落盘到 `cacheDir/photo_edit/` |
| `mediaview/.../edit/PhotoEditor.kt` | Compose 编辑器：预览 + 手势 + 工具条 + 样式/选中面板 + 文字输入框 |
| `mediaview/src/test/.../PhotoEditsTest.kt` | 操作模型、坐标映射、点选的单测（27 个） |

### 3.2.1 交互（对应反馈的 5 条问题）

| 问题 | 现在的行为 |
|---|---|
| 涂鸦不能调粗细、颜色 | 选涂鸦工具后面板有 6 色色板 + 粗细滑杆（给**下一笔**用）；点选已画的笔迹，同一块面板改的是**那一笔** |
| 文字不能调粗细/颜色/位置，不能拖动，不能改字 | 点图添加；点已有文字选中后：**拖动移动**，面板里改颜色、字号、粗体，"修改文字"重开输入框 |
| 马赛克是固定矩形 | 改成笔刷：手指划过的地方打码，面板里调笔刷大小 |
| 调整面板打开后关不掉 | 面板右上角"完成调整"；再点工具条的"调整"也能收起 |
| 画完的东西不能选中删除 | 点任意涂鸦/文字/马赛克选中（黄色虚线框），面板里"删除"/"取消选中"；删除可撤销 |

手势分两层：`detectTapGestures`（选中/取消选中/添加文字）和 `detectDragGestures`（画一笔 / 拖动选中项）。
点按在拖动越过 touch slop 之前不会被消耗，所以两层可以共存。涂鸦/马赛克工具下按住即开始画（不会误拖已有标注），
其它工具下按住文字或涂鸦就是拖动它。**马赛克不支持拖动**——它的内容取决于它下面的像素，移位后要重新取样，
目前只支持选中后删除/调笔刷大小。

### 3.3 渲染细节

- **旋转/镜像**：`Matrix` + `Bitmap.createBitmap`，90° 的倍数。
- **亮度/对比度/饱和度**：合成一个 `ColorMatrix`（亮度平移、对比度围绕 128 缩放、饱和度用标准矩阵），一次 `drawBitmap` 完成；不逐像素循环。
- **涂鸦**：`Path` + 圆头圆角 `Stroke`，线宽按图片短边比例，保证不同分辨率手感一致。
- **文字**：锚点是**左上角**（拖动时手指按住的位置就是文字块的一角），`Paint.textSize = sizeRatio * min(w,h)`，
  `bold` 控制粗细，支持 `\n` 多行；`drawText` 的 y 是基线，渲染时用 `fontMetrics.ascent` 把"顶边"换算成基线。
- **马赛克：沿笔迹打码，不是矩形**。`EditOp.Mosaic(points, widthRatio)` 存的是手指划过的折线。渲染：
  ① 取笔迹外接矩形，聚合成 `cols × rows` 色块图；② 用笔迹路径（圆头、宽 `widthRatio`）做 `DST_IN` 蒙版，只保留笔迹覆盖到的像素；
  ③ 贴回原位。只处理外接矩形，不整图处理。
- **预览与导出同一条渲染路径**：编辑时 `PhotoRenderer.render(预览图, edits, 1280)`，保存时 `render(原图, edits, 4096)`，
  所以马赛克和亮度调整在编辑时就是真实效果。预览图是缩小的（长边 1280），标注坐标是归一化的，两边等价。
  拖动某条标注时，底图先不画它，改用上层 Canvas 实时叠加，松手再提交，避免每帧重渲染整图。
- **尺寸上限**：`PhotoRenderer.MAX_OUTPUT_LONG_EDGE = 4096`（防 OOM），`PhotoEditExporter.MAX_LONG_EDGE = 2048`（上传尺寸）。超限时等比缩小。

### 3.4 回传给对话的接口

```kotlin
// 对话里点开图片 → 编辑
val uri: Uri? = PhotoEditExporter.export(sourceBitmap, edits, context.cacheDir)
// uri 形如 file:///.../cache/photo_edit/edited_1731...jpg
```

约定表：

| 项 | 约定 | 理由 |
|---|---|---|
| 载体 | `Uri`（`file://`），而不是 `Bitmap` | 对话的 `ImageAttachment` 本来就是一个 path；传 Bitmap 会让 ViewModel 持有像素、生命周期和回收时机都变复杂 |
| 目录 | `cacheDir/photo_edit/` | 不覆盖原图；缓存目录可被系统回收；`clearCache()` 可主动清理 |
| 文件名 | `edited_<epochMillis>.jpg` | 天然不重名；取消发送时可按 Uri 删除（`PhotoEditExporter.delete(uri)`） |
| 格式 | 有 alpha → PNG；否则 JPEG q=92 | 绝大多数拍照/截图没有 alpha，JPEG 体积小 |
| EXIF | 解码时用 `ImageUtils.decodeUriApplyingExifOrientation`；导出时**不携带原 EXIF** | 编辑后方向已烧进像素，再带原 Orientation 会被二次旋转；隐私上也不该把拍摄地点带进分享图 |
| 尺寸 | 长边 ≤ 2048 | 和 `ImageCompressor.MAX_LONG_EDGE=1600` 兼容：发送时会再压一次（1600 / 82），编辑结果留一点余量给「看得清标注」 |
| 上传 | 仍走原链路 | 编辑结果落盘后就是一张普通本地图片，发送时由 `ImageCompressor` 压缩、由现有上传逻辑处理 |

**发送前 vs 发送后**：
- **发送前**（草稿托盘里）：编辑等同于「替换这一项附件」——`ChatViewModel` 把草稿里的 path 换成新 Uri 对应的文件即可。
- **发送后**（历史消息）：编辑结果**另存为新文件**并作为一条新消息的图片，不修改原消息（原消息可能已在服务端留档，改本地文件会让「本地看到的是修过的、服务端还是原图」）。

---

## 4. 集成步骤

1. **已完成**：模型、渲染器、导出器、Compose 编辑器、单测（见 3.2 的文件表）。`PhotoEditor(bitmap, onCancel, onDone)` 是唯一对外入口。
2. 在 `:feature-chat` 的草稿托盘加「编辑」入口：
   - 托盘项长按 → 「编辑」；
   - 页面用 `PhotoEditor` 包一层全屏 `Dialog`（编辑完回草稿，不丢输入框内容）；
   - `onDone` 里 `PhotoEditExporter.export(...)`，成功则 `viewModel.onAttachmentReplaced(draftId, uri)`。
3. 历史消息长按菜单里加「编辑后重发」：先 `AttachmentActions.localImageFile(context, source)` 拿到本地文件（网络图片会先下载到缓存），再走同一个编辑器。
4. 拍照解题的结果页加「标注错处」入口（可选）：复用 `PhotoEditor`，`allowCrop = false`。

> 第 2–4 步依赖对话的草稿数据流（`StagedAttachment` / `SendActions`），本次只做到「组件可用 + 接入点明确」，没有改对话的业务数据流，避免和正在进行的对话重构冲突。

---

## 5. 风险与取舍

| 风险 | 影响 | 缓解 |
|---|---|---|
| 4096 长边全尺寸位图在低端机 OOM | 编辑闪退 | 解码即按 2048 采样；渲染包 `try/catch (OutOfMemoryError)`，失败回退原图 |
| 文字预览与导出的字号/换行不完全一致 | 用户看到的位置和最终图略有差异 | 两者都用「短边 × sizeRatio」算字号、都按 `\n` 分行；差异在 1–2px 级别，验收时用同一张图对比 |
| 编辑后图片体积偏大（2048 + q92） | 上传慢 | 发送时 `ImageCompressor` 会再压到 1600/82/≤800KB |
| 用户编辑后取消发送 | 缓存堆积 | `PhotoEditExporter.delete(uri)`；`clearCache(cacheDir)` 可在「清理缓存」入口调用 |
| 后续需要滤镜/贴纸 | 需要扩展 | 在 `EditOp` 里加类型、在 `PhotoRenderer` 加渲染分支即可；优先用 `:imaging` 的 OpenCV 管线 |
| 没有第三方库，审美/交互不如成熟编辑器 | 体验差距 | 工具条交互对齐系统相册的套路（工具图标 + 滑杆 + 撤销/重做），不接受「点开就是一堆按钮」 |

---

## 6. 验证

```bash
./gradlew --offline :mediaview:compileDebugKotlin
./gradlew --offline :mediaview:testDebugUnitTest   # PhotoEditsTest：27 个，含撤销快照、删除/拖动可撤销、坐标映射逆运算、点选
./gradlew --offline :feature-chat:compileDebugKotlin
```

已验证：上述命令本机全部通过（离线，无网络）。

**未验证**（JVM 单测够不到 `Bitmap` / `Canvas` / 手势，需要真机或 instrumented 测试）：
- 马赛克蒙版的实际像素效果、笔迹靠近图片边缘时的外接矩形夹紧；
- 文字点选框是按"中文 1.0 / 西文 0.58 倍字号"估算的，和实际排版有偏差，点在文字边缘可能选不中或多选中；
- 拖动手感、触摸容差（`DEFAULT_TOLERANCE = 0.04`，约 12~15dp）是否合适；
- 预览与导出的文字位置、字号在 1~2px 以内一致这一点只做了设计，没有对比过同一张图；
- 编辑器尚未接入聊天草稿栏（见第 4 节）。
