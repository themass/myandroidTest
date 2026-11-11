# 对话图片编辑：方案

状态：**方案，未改代码。**
基线：`weifu-refactor` @ `7b414878`（模块合并后：`:common` / `:mediaview` / `:speech` / `:imaging` / `:cast` / `:chatkit` / `:core` / feature-* / `:app`）。

---

## 1. 现状

| 项 | 情况 | 位置 |
|---|---|---|
| 选图 | `PickMultipleVisualMedia()` | `feature-chat/.../presentation/ui/ChatRoute.kt:65` |
| 拍照 | `TakePicture()` | `ChatRoute.kt:71` |
| 暂存 | 把 Uri 拷进缓存目录，返回 `StagedAttachment` | `presentation/AttachmentStager.kt:40` |
| 上传 | `AttachmentUploader` 上传，大小/类型校验在 `AttachmentStager` | `data/remote/AttachmentUploader.kt` |
| 显示 | 气泡内图片网格；大图用 `:mediaview` | `chatkit/message/parts/MessageParts.kt` |
| 图片依赖 | Glide 4.16（`:common` 的 `net/glide`）、Coil（`:mediaview`、`:chatkit`） | — |
| 已有图片处理 | `ImageUtils`（EXIF 解码、缩放）、`ImageCompressor`（长边 1600、JPEG 82、≤800 KB） | `:common/.../image/` |
| **没有** | 任何涂鸦、文字、马赛克、裁剪 UI（对话侧） | — |

结论：**能复用的是压缩、EXIF 解码和缓存目录约定；编辑本身要从零做。**

---

## 2. 开源库对比

调研结论：没有一个成熟、维护中的 Android 库同时提供「裁剪 + 涂鸦 + 文字 + 马赛克 + Compose 友好」。所以方案不是「选一个库」，而是「裁剪用一个库 + 标注层自己写」。

| 方案 | 能力 | 维护 / 许可证 | 包体 | Compose | 结论 |
|---|---|---|---|---|---|
| **A. `io.github.mr0xf00:easycrop`** | 裁剪（任意角度、四角拖动） | Apache-2.0，小项目；**项目已在用**（`feature-photosolve/build.gradle`） | 很小，纯 Compose | 原生 | **裁剪用它**：零新增依赖、已验证 |
| **B. `com.github.yalantis:ucrop`** | 裁剪、旋转、比例锁定 | Apache-2.0，成熟但 2023 年后更新少 | ~200 KB | 否（Activity） | 与 A 二选一，不重复引入 |
| **C. `com.github.chrisbanes:PhotoView`** | 只看大图 | Apache-2.0，维护中；**项目已引入**（`:feature-content`） | 小 | 否 | 只管查看 |
| **D. `SimurghPhotoEditor`** | 涂鸦、遮罩、变换、选区、裁剪、文字 | MIT，个人项目，更新不规律；无马赛克 | 未知 | Kotlin，未标注 Compose | 能力接近，但维护风险高，界面是 View，嵌入 Compose 会割裂 |
| **E. `Image Toolbox`（app）** | 完整编辑器（滤镜、涂鸦、马赛克、裁剪） | GPL-3.0 | — | Compose | **只作参考实现**，GPL 不适合作为依赖 |
| **F. 自己写标注层** | 涂鸦（Path）、文字（drawText）、马赛克（栅格平均色） | 自有代码 | 很小 | 原生 Compose | **推荐**，约 400–600 行 |

**结论**：裁剪用 A（已引入），涂鸦/文字/马赛克自己写（F）。不引入 D/E：
1. 对话里的编辑是**轻量标注**，不需要滤镜、高斯模糊这类重能力；
2. D 的维护不确定，D/E 的界面都是 View/GPL，和对话 Compose 界面拼接会割裂；
3. 三个标注能力本质都是「在 Bitmap 上按坐标作画」，Compose `Canvas` 直接画预览，导出时再栅格化。

---

## 3. 架构位置

**放 `:mediaview`**（第 1 层，Compose + Coil，不依赖业务），新增包 `com.openapi.mediaview.edit`。

理由：
- 编辑与对话无关，`:mediaview` 本来就是媒体查看，编辑是自然延伸；`:feature-content`（频道大图）、`:feature-photosolve` 以后也能复用。
- 依赖方向合法：`feature-chat → mediaview` 已是白名单边（`ModuleGraphTest`），不新增依赖边。
- 不放 `:common`：`common/ui` 定位是「通用部件、不带重库」，图片编辑要处理 Bitmap 和 EXIF，`:mediaview` 更合适。

```
mediaview/edit/
  PhotoEditState.kt     编辑状态：旋转、裁剪、亮度/对比度、标注列表（可撤销）
  PhotoEdits.kt         纯函数：把状态应用到 Bitmap（JVM 可测）
  PhotoEditOverlay.kt   Compose 画布：预览 + 手势
  PhotoEditToolbar.kt   底部工具条
  PhotoExporter.kt      导出为 JPEG，写缓存目录，返回 Uri
  PhotoEditor.kt        对外入口：Compose 版 + 挂起版
```

**坐标系约定**（写进代码注释，避免预览与导出不一致）：
- 标注一律存**原图坐标**（像素），不存屏幕坐标；
- 预览用 `ContentScale.Fit`，手势屏幕坐标先减偏移、再除缩放，得到原图坐标；
- 导出时按原图坐标直接画到原图 Bitmap 上。

---

## 4. 接口设计

### 4.1 编辑状态（纯数据，可单测）

```kotlin
data class PhotoEditState(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int = 0,            // 0/90/180/270
    val crop: RectF? = null,                 // 原图坐标；null = 不裁剪
    val brightness: Float = 0f,              // -1f..1f
    val contrast: Float = 1f,                // 0.5f..1.5f
    val marks: List<PhotoMark> = emptyList(),
)

sealed interface PhotoMark {
    val bounds: RectF
    data class Stroke(val points: List<Offset>, val width: Float, val color: Int) : PhotoMark
    data class Text(val text: String, val at: Offset, val size: Float, val color: Int) : PhotoMark
    data class Mosaic(val cells: List<RectF>, val cellSize: Int) : PhotoMark
}
```

JVM 单测覆盖（不需要设备）：
- 裁剪矩形钳位、旋转后裁剪坐标换算；
- 马赛克栅格化：给定区域与 cellSize，算出哪些 cell；
- 撤销：marks 出栈；
- 导出尺寸：旋转 + 裁剪后的输出宽高；
- 亮度/对比度查找表（LUT）的取值。

### 4.2 对外入口

```kotlin
@Composable
fun PhotoEditor(
    uri: Uri,
    onCancel: () -> Unit,
    onDone: (edited: Uri, width: Int, height: Int) -> Unit,
)

/** 非 Compose 页面用；取消返回 null。 */
suspend fun editPhoto(context: Context, uri: Uri): Uri?
```

### 4.3 与对话对接

```
选图/拍照
  → AttachmentStager.stage（现有拷贝）
  → PhotoEditor（只对「待发送」图片开放）
  → onDone(editedUri)
      → 用编辑结果覆盖暂存文件（保留文件名）
      → 继续原上传/发送流程；AttachmentUploader 不改
```

- **只允许编辑待发送图片**。已发送的消息不提供编辑：否则「界面上看到的图」和「AI 当时收到的图」不是同一张。
- 不改变上传协议：服务端收到的仍是一张 JPEG。
- 导出沿用现有上限：长边 ≤ 1600、JPEG 82、≤ 800 KB（复用 `ImageCompressor` 参数）。

### 4.4 导出细节

| 项 | 决定 | 理由 |
|---|---|---|
| 格式 | JPEG | 与现有对话图片一致；PNG 体积大 |
| EXIF | 旋转烧进像素，丢弃其余 EXIF | 避免二次显示再转一次 |
| 位置 | `cacheDir/chat_edit/edited_<stamp>.jpg`，与暂存目录一致 | 沿用现有清理策略 |
| 内存 | 采样解码，编辑目标长边 2048，导出缩到 1600 | 4000×3000 直接解码约 48 MB，必须采样 |
| 失败 | 保留原图，提示「编辑失败，已使用原图」 | 不让用户因编辑失败发不出消息 |

---

## 5. 实现步骤与工作量

1. `PhotoEditState` + `PhotoEdits` 纯函数 + JVM 单测（先红后绿）——0.5 天
2. 裁剪/旋转（复用 easycrop 或按 `CropOverlay` 写法）——1 天
3. 涂鸦——0.5 天；文字——0.5 天；马赛克——0.5 天
4. `PhotoExporter` + 单测——0.5 天
5. `ChatRoute` / `ChatViewModel` 接线（只对待发送图片）——1 天
6. 手机验证——0.5 天

合计约 4.5 天。

---

## 6. 手机验证清单

| # | 场景 | 期望 |
|---|---|---|
| E1 | 竖拍照片（带 EXIF 方向） | 预览方向正确，编辑后不歪 |
| E2 | 裁剪 + 旋转 90° + 转回 | 每步预览与最终导出一致 |
| E3 | 涂鸦一笔后撤销 | 笔画消失 |
| E4 | 文字标注并拖动 | 位置跟随手指，导出位置一致 |
| E5 | 马赛克一块区域 | 区域被像素化，不溢出边界 |
| E6 | 编辑后发送 | 对比服务端原图，确为编辑后版本 |
| E7 | 取消编辑 | 发送原图，不留多余缓存 |
| E8 | 4000×3000 大图编辑 | 不 OOM，导出 ≤ 800 KB |
| E9 | 编辑中旋转屏幕/切后台 | 编辑状态保留或明确重置 |

---

## 7. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 自写标注层坐标换算易错 | 预览与导出不一致 | 只存原图坐标；换算放纯函数并单测 |
| 大图内存 | OOM | 采样解码、目标长边 2048 |
| `:mediaview` 只有 2 个文件 293 行 | 编辑包会成主体 | 独立目录；真成大块再拆 `:photoedit` |
| easycrop 维护风险 | 无法升级 | 只用于裁剪界面，标注层不依赖它 |

## 8. 待确认

1. 编辑范围就是这 7 项（裁剪/旋转/亮度/对比度/涂鸦/文字/马赛克）吗？加滤镜、贴纸会明显变大。
2. 是否允许编辑**已发送**的图片（建议不允许，见 4.3）。
3. 涂鸦默认黑/白两色 + 3 档粗细，是否够用。
