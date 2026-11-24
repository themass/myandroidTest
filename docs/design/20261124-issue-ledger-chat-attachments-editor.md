# 问题台账 20261124 · chat / 附件 / 图片编辑 / 首屏性能（20 条，一条不丢）

用户原话：「每个问题都要有一个标志，不能遗失」「如果是个架构设计或者共性问题要从架构层面彻底解决，要学会抽象架构设计，封装」。
本台账就是那个标志表：`A1…A19` 对应原始抱怨的 10 大项 19 个可执行点；`A20` 是用户 20261125 追问的「chat 首屏消息显示慢」。每条含 **现象 → 根因（file:line 证据）→ 修复方案 → 是否共性问题/抽象点 → 状态**。

状态标记：`已修合线` / `派工中` / `待派工` / `待取证` / `需服务端`。

对应原始编号：
`1→A1`，`2→A2`，`3-1)→A3,A4`，`3-2)→A5,A6`，`3-3)→A7`，`3-4)→A8`，`3-5)→A9`，`3-6)→A10`，`3-7)→A11`，`3-8)→A12`，`4→A13`，`5→A14`，`6→A15`，`7→A16`，`8→A17`，`9→A18`，`10→A19`；`A20` 来自独立追问「首屏消息显示特别慢，不是本地分页展示么？」。

---

## A1 · chat 页上下滑动历史，键盘不收起

- **根因**：`ChatMessageList` 的滚动状态里只区分「贴底跟随」，没有任何键盘交互；用户拖动列表时软键盘保持弹出挡住内容。
- **修复**：用户拖动（`isScrollInProgress` 且非程序化滚动）即 `LocalSoftwareKeyboardController.hide()`。关键细节：三处程序化滚动（新消息贴底、流式增长、键盘高度变化）必须用 `programmaticScroll` 标志与用户拖动区分，否则键盘高度一变化就把用户刚弹出的键盘当场收掉；标志在 `finally` 清，避免粘住后吞掉后续拖动。
- **状态**：**已修合线** —— commit `cc77dfad`，已并入 `weifu-refactor`（`6c86ab7a`），`:feature-chat` + `:chatkit` 单测全绿。

## A2 · followup 队列没有了，发一条必须等回复或取消

- **根因**：`ChatUiState.canSend` 与 `chatkit ComposerState.canSend` 都带 `&& !sending`；生成中发送键被禁或被停止键整个替换，`sendText` 在 `sending` 时直接 `return`。排队语义在旧 `app/.../agent/AgentChatState.kt`（`QueuedInput`）里有，迁移到 feature-chat 时丢了。
- **修复**：`ChatUiState.queued: List<String>`（FIFO，按 Enter 顺序）；`canSend` 去掉 `!sending`；生成中有文字时发送键与停止键并存；`ReplyStreamer` 只在**自然结束**收尾时取队首续发，用户点停止清空队列（中断意图明确）；输入栏上方显示「N 条排队中」。只有纯文字走队列——编辑/附件/位置维持原有闸。
- **状态**：**已修合线**（同上 `cc77dfad`）。顺带修了 HEAD 上 `ReplyStreamerStreamTest` 编译错误（`8e18342d` 给 `ReplyStreamer` 加了必填 `strings: ChatStrings`，测试 Harness 没补）。

## A3 · 裁剪角抓不住（贴边）、拖中间却移动整框

- **根因**（两处独立实现都有）：
  - 拍照解题 `feature-photosolve/.../ui/CropOverlay.kt`：`pickGrab` 用「8 个点取最近 + 欧氏半径」，贴边时手指只能落在角内侧，欧氏判定下半径被对角线吃掉 → 按到角标上仍掉进 `Move`；边只认边中点一个点。另外 `Grab.apply` 的夹取 `l.coerceIn(0f, r - min)` 在 `r < min` 时 min>max，直接把角往对边拖会**抛 IllegalArgumentException 崩溃**。
  - 编辑器 `mediaview/.../edit/CropMode.kt`：`hitHandle` 用方形 slop，角与边中点可视尺寸远小于热区。
- **修复**：角优先改用**切比雪夫距离**（`max(|dx|,|dy|)`）并把热区向框内侧扩展；边沿整条边判定；只有明确在框内才是 `Move`；夹取改为「先各自夹进 [0,1]，再用 `coerceAtLeast(l+min)` 保最小边长」。mediaview 侧方形∪圆形热区、slop 24→32dp。
- **状态**：**已修合线**（`3b01deae`、`100c1636`），新增 `CropGrabHitTestTest` 9 例含崩溃回归。

## A4 · 裁剪框与图片「不搭噶」（网格压在黑边上、框偏离照片）

- **根因（真机截图的直接成因，已钉死）**：`mediaview/.../edit/CropMode.kt:273-277` 的 `LaunchedEffect(viewport, aspect)` 调 `CropGeometry.fitAspectRect(aspect, viewport.width, viewport.height)`。该函数（`CropMode.kt:63-76`）返回**帧局部坐标**（以 (0,0) 为原点，测试 `CropModeTest.kt:112` 明确钉这个约定），但调用点把它当**画布绝对坐标**的 selection 直接画（`CropMode.kt:306-333`）。图片 Fit 居中时带 left/top 内缩 → 选框贴在画布左上角、压住黑边。更糟的是**后续拖拽是对的**：`resizeForHandle` 用 `frame = viewport`（带偏移），`normalizeSelection`（`CropMode.kt:135-146`）减了 `viewportPx.left/top` —— 用户一抓住角，框就跳回 viewport 坐标系，前后不一致坐实了初始锚点 bug。
- **修复**：调用点把 `fitAspectRect` 结果平移 `(viewport.left, viewport.top)`（或让几何函数收 `frame: Rect` 并在 frame 内居中），**不改**几何函数自己的帧原点约定。
- **共性/抽象点**：**「坐标系必须在唯一一处公布」**。同一个词「rect」在三层里含义不同（帧局部 / 画布绝对 / 归一化 0..1），靠调用方记得加减偏移来对齐，必然反复出错。抽象点：几何工具统一收 `frame: Rect` 参数而非 `width/height`，返回值一律在 frame 坐标系；边界处一次转换（`toNorm`/`toDisplayNorm`）。这条与拍照解题早前修的黑边错位（fit 矩形 vs 容器矩形）是同一类。
- **状态**：**已修（提交 b97ec6c1）**：`CropGeometry.initialSelection` 把 frame 局部坐标换算到视口，网格/框对齐照片本体（抽象点 4）。

## A5 · 滤镜没有反应，全是黑框

- **根因（已定位到唯一静默出口）**：`.cube` LUT 资产真实存在且能解析（`mediaview/src/main/assets/filters/{creamy,coolwhite,retro,moody,airy,mono}.cube`，33³，实测 6 个全部 OK；已进打包产物 `app/build/intermediates/assets/chatDebug/.../filters/`）。唯一的失败通道是 `LutAssets.get` 返回 null：
  - `mediaview/.../edit/FilterPanel.kt:240` `provider(id) ?: continue` → 该格跳过，`FilterPanel.kt:169` 留 `0xFF2A2A2A` 深灰底 → 六格全空在黑面板上就是「黑框」；
  - `mediaview/.../render/PhotoRenderer.kt:270` `lutProvider(edit.id) ?: return sourceBitmap` → 画面**静默不变**（点了没反应）；
  - `mediaview/.../render/LutAssets.kt:25,35-39` 读盘/解析失败返回 null 且**把失败结果进程级 memo 记住** —— 一次瞬时 IO 失败会把整个进程的滤镜永久灭掉，只在日志 `Log.w`。
- **修复**：①面板统计取不到的 LUT 数量，>0 时明确提示「滤镜资源不可用」并把格子画成可辨占位，而不是留深灰；②`LutAssets` 失败不缓存、成功才缓存；③渲染降级保持不崩，但要让调用方知道「这次编辑的滤镜没生效」（可观测信号，不改数据模型语义）。
- **仍需现场取证**：装到 V2279A 的包是否是含 assets 的最新构建（截图里面板与资产是同批提交进来的）。抓 `logcat` tag `LutAssets`/`PhotoRenderer`：若出现「未能解析」→ 装的是旧包/中间构建。参见记忆规则「装机只装主线新构建」「『旧包』先取证」。
- **共性/抽象点**：**「失败必须可见」**。本项目反复出现 `?: return` / `?: continue` / `runCatching { Log.w }` 的静默降级（解题历史落盘同一毛病已修）。抽象点：任何降级返回都要①计数或②向上抛一个可显示的原因，UI 层统一渲染；禁止把失败结果当成功结果缓存。
- **状态**：**已修（提交 b97ec6c1）**：`LutAssets` 只缓存成功、失败计数上抛可显示原因；`FilterThumbs(map, missing)` 面板横幅 + Block 图标，不再全黑框静默（抽象点 5）。

## A6 · 颜色没有色板可选（只有 6 个固定色块）

- **根因**：`mediaview/.../edit/PhotoEditor.kt:496-499` 只有 6 色 `PALETTE`，`Swatches`（:944-975）就渲染这 6 个；没有任何任意色入口；马赛克 `color = null`（:437-442）连颜色行都没有。
- **修复（按 Astryx 设计稿 `protos/photosolve-ui/src/EditorScreen.tsx`）**：扩到 16 色（每行 8、选中描边+勾，走主题色阶不散写 hex）+「自定义」开 Dialog（色相/饱和度/明度三滑杆 + 实时预览 + 使用这个颜色）；下游已全量接受任意 `Color`（`toArgb().toLong()`，`PhotoEditor.kt:290,366,473`），**不需要改数据模型**；选中已画标注的 `SelectionPanel`（:924）共用同一套。
- **共性/抽象点**：**取色器是一等组件，不是散在面板里的色块数组**。Astryx 无 ColorPicker，官方规范是 swatch grid（`role=grid/row/gridcell`、`aria-pressed`、每行 8、一个 Tab 停靠点）+ 自定义色 Dialog。抽象点：`Swatches(selected, onPick)` 签名不变（已可承载任意色），新增 `CustomColorDialog` 复用同一个 `onPick`。
- **状态**：**已修（提交 b97ec6c1）**：16 色 2×8 色板 + `ColorPickerDialog`（HSL，从当前色起步）；设计稿 astryx build 已通过。

## A7 · 整体功能区域按钮布局不好，最佳实践是什么

- **现状问题（截图证据）**：面板贴在屏幕底边、标签被切一半；工具与面板关系不固定。
- **设计答案（最佳实践，落在样稿里）**：**固定三段**——顶栏（关闭 · 标题 · 撤销/重做/重置 · 完成）→ 画布 → **面板槽在工具条上方** → 工具条贴底。面板永不与工具条抢底边，标签永不被裁；工具条等宽图标+标签，选中态用 primary/下划线短横而不是染黄；画布区 = 图片实际显示矩形（见 A4）。这套已在 `EditorScreen.tsx` 可视化，并作为 mediaview worker 的验收要求（面板必须在工具条上方、任何面板内容完整可见）。
- **共性/抽象点**：**编辑器 = 「画布 + 一个面板槽 + 一个工具条」的三段骨架**，任何工具（裁剪/涂鸦/文字/马赛克/滤镜/调整）只是往面板槽里换内容。抽象点：`panelSlot` 单一注入点 + 统一「面板在工具条上方」布局约束，而不是每个工具各自排布局。
- **状态**：设计**已完成并验证**；代码侧三段骨架已随 `b97ec6c1` 落地（面板槽 `heightIn(max=240.dp)+verticalScroll`，滤镜缩略图不撑爆画布）。

## A8 · 多选图片只能编辑第一张 → 要求相册多选不进编辑器，拍照才进编辑

- **根因**：`feature-chat/.../ui/ChatRoute.kt:92-96` 刻意分流——`uris.first()` 带 `openEditor` 进编辑，`drop(1)` 直接进草稿（注释写「避免一次弹多个编辑器」）。相册与拍照本来就是两个 launcher（相册 `PickMultipleVisualMedia` :88-89，拍照 `TakePicture` :108-115）。
- **修复**：相册路径两处合一、调 `onImagesPicked(context, uris)` **不传 openEditor**；拍照路径不动（「拍照才进编辑器」已成立）。接缝 `SendActions.imagesPicked(... openEditor: ((File,Bitmap)->Unit)?)`（`SendActions.kt:184-222`）已支持为空。
- **状态**：**已修合线（843213b5）**：相册多选不再直接进编辑器、草稿缩略图点开大图（A9）、消息大图浮动「编辑」（A10）、编辑结果作新草稿再入、保存相册带 toast（A11）、选择上限 4（A12）。并线树 `:feature-chat` 265 测 0 失败（20261125 fresh 复跑，--rerun-tasks）。陪同侧上限仍是 9 需同步（见 A12 残留）。

## A9 · 选完图在输入框里无法点击看大图

- **根因**：`chatkit/.../input/ComposerPanels.kt:56-157` 的 `DraftTray`，图片分支只有裸 `AsyncImage`（:66-73），全文件可点的只有删除 ×（:162）和「+」面板项（:193）。
- **修复**：图片草稿缩略图加点击 → 复用现成全屏查看器 `chatkit/.../media/ImageViewer.kt:51-96`（Dialog 全屏 + HorizontalPager + 缩放/双击），sources = 当前全部图片草稿、startIndex = 被点那张；开关状态收在 chatkit 内部（不往 `ChatScreen` 加参数）。
- **共性/抽象点**：**「大图查看器」必须是一个组件，被草稿托盘和消息气泡共用**（见 A10、A15）。抽象点：`ImageViewer(sources, startIndex, onDismiss, onEdit?, …)` 单一入口，调用方只提供数据与动作。
- **状态**：**已修合线（843213b5）**：同上——草稿缩略图 `combinedClickable(onClick=看大图, onLongClick=再编辑)` 收口在 ComposerPanels 一处（合并冲突按「两者都留」解）。

## A10 · 消息大图上浮动「编辑」按钮，点击再进编辑页

- **根因**：查看器就是 `ImageViewer`（由 `ImageGrid` 调起，`chatkit/.../message/parts/MessageParts.kt:316,348-362`），但它只有 `onDismiss/onLongPress/onRetry`（`ImageViewer.kt:54-58`），没有动作槽；编辑机器 `pendingEdit` + `PhotoEditor` + `replaceDraftImage`（`ChatRoute.kt:80,300-327`）只对草稿里的 `File` 生效，已发送消息没有 draft。
- **修复**：`ImageViewer` 加可选 `onEdit: ((String)->Unit)?`，非空时底部居中浮「编辑」按钮；`ImageGrid` 透传；`ChatRoute` 把该图解码进 `pendingEdit` 复用现有编辑流，**编辑结果作为新草稿附件加入输入框（`composer.addDraft`），不回写已发送消息**——已发送消息没有本地覆盖文件，硬造覆盖语义会腐化历史记录（这是本次的产品决策，代码里要写注释）。
- **状态**：**已修合线（843213b5）**：同上——大图浮动编辑按钮走 `PendingEdit.Message/Draft` 封闭类型；合并批把 ChatRoute 长按再编辑也统一改走 Draft。

## A11 · 编辑完保存到本地相册

- **根因**：功能已有但接错地方——`AttachmentActions.saveToPictures`（`feature-chat/.../ui/AttachmentActions.kt:133-156`，MediaStore insert + `DIRECTORY_PICTURES` + IS_PENDING 两段式）是 **private**，只有长按图片菜单的 `Download` 动作能用；编辑产物 `PhotoEditExporter.export`（`mediaview/.../edit/PhotoEditExporter.kt:70-103`）返回 `Uri.fromFile(File(cacheDir,"photo_edit/..."))`，在 `ChatRoute.kt:307-324` 导出后没有任何保存入口。权限无忧：minSdk 28 / targetSdk 37，scoped storage，清单刻意不声明写权限（`app/src/main/AndroidManifest.xml:32-37`）。
- **修复**：把保存提成可复用（`saveEditedImageToGallery(context, uri)` 走 `localImageFile` 本地分支 :121-130），编辑导出成功后提供「保存到相册」，成功/失败都要有可见反馈；`Build.VERSION < Q` 分支给可读提示（现有实现直接返回 null）。
- **状态**：**已修合线（843213b5）**：同上——`saveEditedImageToGallery` + toast。

## A12 · 图片消息限制最多 4 张

- **根因/现状**：当前硬上限是 **9**（`common/.../media/ImageMessageLimits.kt:19` `MAX_MESSAGE_IMAGES`，注释说明与服务端 `MAX_MESSAGE_ATTACHMENTS` 同源）。三道闸：选择器 `ChatRoute.kt:89 maxItems`、草稿暂存 `ComposerActions.addDraft`（`ComposerActions.kt:57-62`，超限由 `SendActions.imagesPicked:199-208` 停止并报 `attachmentLimitReached`）、发送处无独立闸。用户看到的「4」只是气泡排布决策表（`MessageParts.kt:96-103` `count <= 4 -> Stack`），不是数量限制。
- **修复**：**不动服务端锚定值 9**；新增产品上限常量（`MAX_IMAGES_PER_PICK = 4`）+ 注释区分「服务端传输 9 / 产品 4」，用它替换 `ChatRoute.kt:89` 的 `maxItems`，并让 `addDraft` 的闸用产品上限。陪伴页同一套（`CompanionViewModel.kt:1078,176-179`、`CompanionScreen.kt:178-179`）需同步，已在 worker brief 里标注为待协调项。
- **共性/抽象点**：**「上限」分两层：协议上限（与服务端同源，改一处要同步服务端）与产品上限（客户端体验决策）**，必须两个常量两个注释，不能靠同一个数字兜两边。
- **状态**：**已修合线（843213b5）**：`MAX_IMAGES_PER_MESSAGE_PICK=4` 产品值与服务端 9 分层锚定。**残留未修：陪同侧仍是 9（`CompanionViewModel.kt`、`CompanionScreen.kt`），待同步成 4。**

## A13 · 图片消息里多张图片，服务端只看了一张就乱回

- **根因（取证完成，推翻「发送路径裁图」假设）**：发送链**不裁图**——一次带图发送是「一条 `ChatMessage` + N 元素 `attachments` 数组」全程到 wire：`SendActions.kt:62-98`（全部 drafts→同一消息）→ `AttachmentSender.kt:76-90`（N 个 upload_id）→ `AgentChatRemote.kt:442-449`（`put("attachments", JSONArray)` 整数组，无 `first()`）；服务端也是逐个读图（`docs/design/shared-media-upload-and-storage.md:213-222`：`_load_inputs → TurnRunner._load_images` 逐个取，读失败只跳过该张）。能把「N 张」塌成「1 张」的是端上两处：
  1. **暂存文件名同毫秒互相覆盖（最可能真凶）**：`AttachmentStager.kt:97` 每次 `stage()` 取一个 `System.currentTimeMillis()` 做 `img_$stamp.jpg`（:110），多选循环（`SendActions.kt:185-222`）里两次 stage 落在同一毫秒 → 后一张**静默覆盖**前一张文件，N 个草稿的 `staged.file` 指向同一路径 → 上传 N 次同一份字节。陪伴页已点名并修掉同一坑（`CompanionViewModel.kt:243-262` 注释原文「选了九张只剩一张」，CAS 单调时间戳 `nextStagingStamp()`），**feature-chat 侧没有这套修法**（全仓 `nextStagingStamp` 只在 feature-companion 命中）。
  2. **显示端收成「一沓」**：`ChatItemMapper.kt:48` 把全部图合成一个 `MessagePart.Images`，`MessageParts.kt:96-103,212-227` 默认 `ImageLayout.Stack` 一律收纳盒、不看张数 → 9 张只露顶卡，用户读作「只有一张」（不影响服务端，但影响观感）。
- **修复**：①暂存命名单写者化——把单调 stamp（CAS）提到 `common/media` 层与 `StagingGc` 同层共用，feature-chat 与 companion 两处调用；②发送 payload 表征测试（N 个 upload_id、顺序、文件名互不相同）钉死不再复发；③显示侧收纳盒按张数显示「N 张」或按张数选布局。**服务端不需要改**（取证表明逐个读图）。
- **状态**：**根因已定位（端侧两处）+ 待派工**——`AttachmentStager.kt` 在状态机 worker 的文件区内，待其收口后紧接派工，避免同文件冲突。

## A14 · 「（回复被中断）」太容易出现

- **先澄清一个事实**：字符串「回复被中断」在本仓库与 git 全历史都不存在。最接近的既有文案族是 `feature-chat/src/main/res/values-zh/strings.xml:81-85`。若截图确实显示这几个字，唯一原样输出任意中文的通道是**服务端 `reason` 透传**（`AgentChatRemote.kt` failure() ~355-372：`reason != null -> reason`）。
- **根因（端上能产生 5 类「中断」，3 类极易命中）**：
  1. `chat_reply_truncated`（`ReplyStreamer.kt:196-198`）：流结束但没有 done 帧——服务端提前关流（`TurnEventCollector.kt:154`）或掉线重连 3 次（1s/2s/4s，:68,166-169）后仍等不到 done（:172-183）。WiFi 断、代理掐长连接都命中。
  2. `chat_empty_reply`（:172,189）：正常结束但零字（`docs/chat-reply-lifecycle.md:63` 承认原因未查明）。
  3. `chat_reply_failed`（:161-166,190）：submit REST 异常或 error 帧。
  4. `chat_stopped_no_reply`（:142-156）：用户主动停止且一字未收——这是唯一区分主动停止的路径。
  5. 服务端 `reason` 透传（见上）。
  另外：服务端 `interrupted` 布尔被解析（`AgentChatRemote.kt:411`）却在 `AgentVoMappers.toChatMessage():36-46` **被丢弃**，历史里被服务端标中断的回复端上与普通回复无差别。
- **修复**：①**SSE 帧间看门狗**——事件流 `readTimeout(0)`（`AgentChatRemote.kt:55-57`）等于永不超时，服务端 15s 心跳在 :269 被直接 `continue`，「连接活着但 LLM 不出字」= 永远挂在 collect；看门狗判定「超过 N 个心跳周期无内容帧」即走现有失败收尾 + 明确文案（文案走 `ChatStrings` 端口，不硬编码）。②`interrupted` 并入 `ChatMessage` 并在 mapper 保留。
- **共性/抽象点**：**任何「等待远端事件」的循环都必须有超时兜底**，不能假设「真断开时框架会报错」（OkHttp 只在 reset 时报，静默不报）。
- **状态**：**已修合线（843213b5）**：帧间看门狗 45s（`TurnEventCollector`+`CODE_TURN_TIMEOUT`）+ 停止走服务端真取消（`ChatRepository.cancelTurn`）+ 409 冲突恢复（`TurnConflictRecovery` 取消一次重试一次）。并线树 fresh 复跑：`TurnWatchdogTest` 4/4、`TurnConflictRecoveryTest` 4/4。真机「中断太常见」复发率未复测——用户如果还看到频繁「（回复被中断）」，多半是服务端那条旧 jar 混跑线（A30 同因），要按回合现场取证。

## A15 · 文件消息必失败 + 输入框里的文件不能点击查看

- **根因（取证完成）**：文件消息走的是 **legacy Java 逐条 multipart 接口** `/chat/ai/local/file.json`（`Constants.java:196`；`AttachmentSender.kt:129-142` 类注释自认「`/v1/uploads` 没有文件类型，那是能力缺口」；`AgentBackedChatRepository.kt:393-395` 把非图片一律委托 legacy），失败是**四个叠加事实**：
  1. **`sessionId` 跨 id 空间**：喂给 legacy 的是 `/v1` 的整数 id（`AttachmentSender.kt:69` → `AgentBackedChatRepository` 的 `agent_chat_current_session`），legacy 会话 id 存在另一个键（`SessionStore.kt:23`）——`AgentBackedChatRepository.kt:50-54` 的注释正是这条禁令的反向违反 → 服务端会话校验拒（历史同类返回 `errno 11003 会话不存在或无权访问`，`docs/design/chat-bugs-and-gallery-plan.md:9`）。
  2. **错误全被抹平成 null**：`BaseServiceAwait.kt:74-103` 的 `awaitUpload` 把任何 onError 变 `resume(null)`，无 HTTP 码无 errno；端上只剩 `AttachmentSender.kt:154` 固定文案「发送失败，请稍后重试」——**失败原因根本看不见**。
  3. **鉴权与实例分裂**：legacy 上传走 UA 签名（`ChatRepositoryImpl.kt:48-51`），`/v1` 走 Bearer；文件端点还落在另一个服务实例（`docs/design/chat-companion-status-and-plan.md:43`）。
  4. **即使服务端回话也被丢**：`AttachmentSender.kt:131-137` 拿到 `ChatReply` 只判 null，`reply.text` 从不进列表；且文件不进 `/v1` 历史 → 换设备/刷新后文件行只活在本地缓存（`ResendActions.kt:49-55`）。
- **修复**：真修需要 `/v1` 侧补文件 upload scene（**需服务端**，另一条集成线，`AttachmentApi.kt` 现在只有 `chat-image`/`chat-video`）。端上先做两步止血：①可观测性——`awaitUpload` 失败不再抹平，HTTP 码/errno 透到 `AttachmentSender.fail` 与日志；②文件草稿点击查看（随 A9 派工）至少给「预览暂不支持」明确反馈，不静默。
- **状态**：**根因已定位（端侧可观测性部分待派工）+ 需服务端**（`/v1` 文件 scene）。点击查看部分已随 A9 派工。

## A16 · 图片+文字 / 附件+文字 看起来像两条消息

- **根因（取证完成，两层）**：
  1. **布局层**：`chatkit/.../message/parts/MessageParts.kt` 里附件块与文字块的间距/分隔让视觉上是两个独立气泡（消息数据本身是一条，`ChatItem` 未拆分）。
  2. **数据层重复（取证新增，是真「两条」的来源）**：用户侧「图片+文字 = 1 记录 1 气泡」已逐环节证实（`SendActions.kt:62-98` 一个 `ChatMessage`；`ChatItemMapper.kt:25-78` 一个 `ChatItem`；`ChatMessageItem.kt:246-335` 同一行堆叠 parts）。但**助手侧会真的出现两条**：每轮 `persistTurn` 写一行 `local-assistant-<millis>`（`AgentBackedChatRepository.kt:242-249`，id 每次现取时间戳），服务端历史里同一条回复带**自己的** assistant id，而 `client_message_id` 只在用户行上（`AgentVoMappers.kt:45`）；`LocalChatStore.cacheRemote` 对齐只认 `chatId == 服务端 id 或 client_message_id`（`LocalChatStore.kt:138-144`），本地助手行两个都不匹配 → 服务端行另存一行；且 `deleteSessionTextOnly` 的 SQL 排除 `local-%`（`ChatLogDao.java:44-48`），整页刷新清不掉 → 切会话/离线回退缓存时本地+服务端助手行并存，chatkit 给每条已完成助手消息挂一排 footer 芯片（`ChatMessageItem.kt:182-188`）→「两条 AI 气泡各有一排 action」。刷新后消失、切会话又出现，与用户观察吻合。
- **修复**：布局层——同一消息的附件块与文字块**共用一个气泡容器**（同底色同圆角，内部用分隔线而不是两个气泡）；有附件时收紧块间距；用户侧与助手侧都要成立。数据层——`persistTurn` 的助手行改用可与服务端对齐的键（done 帧返回的 assistant id 落 `ChatLog.chatId`，或 `cacheRemote` 对 `local-assistant-*` 按 turn 匹配消除）；这是数据层改动，与 A19 tombstone 同区。
- **共性/抽象点**：**「一条消息 = 一个视觉容器」**是渲染不变量；任何新增 part 都往同一个气泡里加。**以及：本地写入的每一行都必须有可与服务端对账的键**——时间戳 id 的行在历史合并时必然复活成重复（与 A19 tombstone 是同一「本地/服务端对账层」缺失的两面）。
- **状态**：布局层 **已修合线（843213b5）**（`MessageParts`「一条消息=一个气泡容器」不变量）。**数据层重复仍待派工**（feature-chat data/**）。

## A17 · 很容易「死机」：那边失败这边发不出新消息，点停止也不好使

- **根因（三重闸叠加，不是单一 bug）**：
  1. **句柄与标志**：`sending/streaming` 在所有 catch/cancel 路径确实有清（NonCancellable 收尾 `ReplyStreamer.kt:149-155,186-187`），但 `attachmentJob` 每次重发被直接覆盖丢句柄（`ChatViewModel.kt:66,71`），依赖旧 job 已自然结束这一前提。
  2. **服务端回合没被取消**：点停止 = `ChatViewModel.onStopGenerating:199-202` 只杀本地 Job（`ReplyStreamer.cancel():45-50`）。服务端取消接口存在（`AgentChatRemote.kt:154 POST /sessions/{id}/cancel`）但 **feature-chat 全模块无调用**（只有遗留 `app/.../agent/AgentChatViewModel.kt:97` 调旧接口）→ 服务端回合继续占坑，用户再发就吃 409「上一轮回复还没结束」（`AgentChatRemote.kt:363`）→「怎么发都失败」。
  3. **sending 卡住时**（A14 的静默回合）：新输入被静默塞进 `queued`（A2 语义），而队列只在回合自然结束时排空 → 静默回合永不结束 → 队列永不出、界面「发了没反应」；`HistoryActions.loadOlder:33` 也被 `sending` 静默挡掉。
- **修复**：①`ReplyStreamer.cancel()` fire-and-forget 调服务端 cancel；②submit 得 409 时先 cancel 再重试一次，仍失败给明确文案；③`attachmentJob` 赋值前取消旧句柄（或结构化并发）；④看门狗（A14）保证 sending 不会永远 true。
- **共性/抽象点（用户说的「架构层面彻底解决」最典型的一条）**：**「回合进行中」现在有三份互不知晓的真相**——`ReplyStreamer.replyJob`（UI）、`ChatViewModel.attachmentJob`（UI，可被覆盖）、`MigrationGuard.busy`（仓库进程级，`AgentBackedChatRepository.kt:458-470`），外加服务端自己认为的回合状态端上完全不知道。读侧另有 4 个独立 `sending` 闸（`SendActions.kt:45,103,132,192`、`HistoryActions.kt:33`、`ResendActions.kt:26,44`）。**抽象点：`ChatTurnGate`**——单一可测的小类型，输入当前状态，输出 allowSend / allowLoadOlder / allowResend + 被挡原因；各调用点改为读它。写侧（谁写 sending/streaming）保持不动但集中注释清楚边界。本次只做最小收口，不做回合重写。
- **状态**：**已修合线（843213b5）**：`ChatTurnGate` 读侧收口（决策表 `TurnGateTest` 7/7 fresh 绿）+ `attachmentJob` 句柄孤儿修 + 看门狗解「sending 卡死发不出」。

## A18 · 头顶「加载更早的消息」按钮常驻，下拉加载更多应是默认功能

- **根因**：按钮条件只有 `state.hasMoreHistory`（`ChatScreen.kt:190-199`），而 hasMore 是「这页被填满(30)就当作还有」的粗近似（`AgentChatRemote.kt:110-122` 注释明说故意的），与滚动位置无关 → 几乎永远挂在顶栏。列表是普通 `LazyColumn`（`ChatMessageList.kt:231-283`，无 reverseLayout），**整个 chatkit 没有触顶判定**（三个滚动效果只服务贴底 :188-228）。加载逻辑在 `HistoryActions.loadOlder:31-51`，`sending` 时还静默 no-op（:33）。
- **修复**：去掉常驻 TextButton；在 `ChatMessageList` 加「滚到顶自动加载」（用 `firstVisibleItemIndex`/`canScrollBackward`，加载中不重复触发，且与 `programmaticScroll` 配合——程序滚动不得触发加载）；`loadingOlder` 时列表顶部显示小进度指示。现成下拉件 `PullToRefreshBox`（`core/.../legacyui/list/ListShell.kt:189`；`ChannelBodyScreen.kt:115-117`、`FeedbackActivity.kt:280` 在用）作为备选，但 chat 列表贴底逻辑复杂，优先做触顶自动加载。`loadOlder` 的 `sending` 静默 no-op 放开（读操作与发送无关）。
- **共性/抽象点**：**「还有更多」的判定要尽量精确化**：服务端应返回真实 hasMore（游标/总数），客户端「填满即当作还有」是权宜；至少在 UI 层把「有没有更多」与「用户滚到哪」两个概念分开。
- **状态**：**已修合线（843213b5）**：常驻按钮删除，滚到顶自动加载（`shouldLoadOlderAtTop` 边沿触发；加载更早不再被 sending 静默吞）。`TopReachLoadOlderTest` 5/5 fresh 绿。真机滚顶加载未复测。

## A19 · 多选删除没有立刻在消息列表体现，应该立刻成功、后台异步删服务端

- **根因**：`MessageRemoval`（`feature-chat/.../presentation/MessageRemoval.kt`）严格「服务端确认成功后才从 UI 移除」：`deleteMany:44-68` 逐条**串行** `deleteOnServer`（:49-56），全部跑完才一次性过滤（:57），再 `reconcile()`（:59 = `ChatViewModel.fallbackReloadMessages:276-292`）；`deleteOne:30-42` 同样。无逐条进度，失败也不回滚（因为从未前滚）。另有一处**契约矛盾**：确认框文案说「只会从本机移除，服务器不受影响」（`values-zh/strings.xml:15`），代码实际先删服务端（:27,36）。
- **修复（乐观更新，正是用户要的产品语义）**：先立即从 `state.messages` 移除并落本地缓存 → 后台删服务端 → 失败则把条目放回**原位** + 报错误条（复用 `state.error`/`ChatErrorBar`）。多选点了立刻消失，服务端可并行。文案改成与代码一致（确实删服务端），选「诚实」而不是「假装只删本机」。竞态防护：删除过程中来了历史同步不能复活已乐观删除的条目——加「待删除 id」集合挡复活。
- **共性/抽象点**：**乐观更新（local-first + 失败回滚 + 防复活）是本项目缺的一层**。「先等服务端再改 UI」的模式在删除、重命名、收藏等处会反复出现同样的「点了没反应」。抽象点：一个小的 optimistic mutation helper（快照 → 应用 → 失败回滚 → 错误上报），配合 reconcile 侧的 tombstone 集合。本次只在删除上落这个模式，其他调用点列为后续。
- **状态**：**已修合线（843213b5）**：乐观删除 `removeOptimistically` + `PendingDeletions` tombstone 三道防复活。`MessageRemovalTest` 10/10 fresh 绿。

## A20 · chat 页首屏消息显示特别慢（用户追问：不是本地分页展示么？）

- **直接回答**：**首屏不是本地分页**——本地 Room 缓存只在两处提前上屏：切会话（`SessionActions.kt:33` 先 `cachedMessages` 顶上，注释自述「服务端一页要 0.4~2 秒」）和网络失败兜底（`AgentBackedChatRepository.kt:259-262`）。**冷启动首屏两者都不走**：`ChatViewModel.kt:44` 初始 `loading=true`，init（:79-86）里 `syncSessionsFromServer()` 与 `refresh()` **串行**写在同一个 launch，`refresh()` → `loadHistoryPage`（:258）**无条件先打网络**，期间只有骨架（`EmptyChatView.kt:119-139`）。本地分页能力（`cachedMessages` + 防盖写守卫 :440-448）已存在，只是冷启动路径没接。
- **根因（取证完成，按可能排序）**：
  1. **network-first 串行三跳才第一次上屏**（CONFIRMED）：`GET /v1/sessions`（:83）→ `GET /v1/sessions/{id}/messages?limit=30`（:258）→ **每张远程图一次签名 URL**（:271 `hydrateImages` 在上屏之前；`ImageHydration.kt:12` 自述单次 0.4–2s，已并发但要等最慢一张）。`loading` 才翻 false（:111）。
  2. **首屏图片必然远端重下，本机字节被绕过**（CONFIRMED）：`AgentVoMappers.kt:20-24` 一律映射 `ImageSource.REMOTE`；`cacheRemote` 合并过本机文件的行只落库不上屏（`LocalChatStore.kt:145-152`）→ 本机有的图也用签名 URL 让 Coil 重下。
  3. **每条助手消息首帧 markdown 全量解析 + 分块缓存未接线**（CONFIRMED）：`MarkdownText.kt:88,96` 首帧两次 `markwon.toMarkdown`；带块级缓存的 `MarkdownRenderCache.kt`/`MarkdownSegmenter` **生产路径零调用**（只有测试），`MarkdownText.kt:69-72` 的私有同名 cache 只缓存 stable 段，对首帧零收益；首条消息还在**主线程**懒建 Markwon 9 插件单件（`ChatMarkwon.java:31-45`），`JLatexMathPlugin`+单 `$` 内联处理器对每条文本都跑。
  4. **本地表规模放大 IO**（存在 CONFIRMED，量级 PLAUSIBLE）：一次刷新 3 次 `bySession` 无 LIMIT 全读（`ChatLogDao.java:28-29`）；`/v1` 链路从不裁剪（`trimToLatest` 只在旧链路）；无 (sessionId,id) 复合索引。
  5. **网络一跳量级**（PLAUSIBLE，需真机取数）：restClient 15s/30s 无重试（`AgentChatRemote.kt:44-49`）；nginx→104:8081 无实测记录。**全链路现在没有任何计时**（grep `measureTime|elapsedRealtime` 零命中）——取数要先加 instrumentation。
- **修复**：①`refresh()` 先 `cachedMessages(sid)` 顶一次再接服务端（能力现成：`SessionActions.kt:33-45` 同款路径+防盖写守卫）；`syncSessionsFromServer` 与 `refresh` 解耦并行（`ChatViewModel.kt:79-86`）；②`loadHistoryPage` 返回合并了本机文件的行（`cacheRemote` 已算出），`hydrateImages` 挪到上屏后异步补；③`MarkdownText` 接上 `MarkdownRenderCache`（API 就绪、测试已钉），`ChatMarkwon.get()` 预热挪 IO；④`bySession` 加 `ORDER BY id DESC LIMIT :n` + 会话级裁剪；⑤`AgentChatRemote.kt:316-325` 一处 `call` 加耗时日志（首屏取数前置，可与任一改动同做）。
- **共性/抽象点**：**「本地先行、远端对账」缺层**——读路径与写路径（A19）同一个缺失的两面：UI 永远等远端，本地缓存只当灾难备份。抽象点：所有列表读统一「cache render → remote reconcile」两段式；渲染重（markdown）的部分必须有跨消息复用的解析缓存，且初始化不在主线程。
- **状态**：**根因已定位（取证完成）+ 待派工**——①②落点在 `ChatViewModel`/`AgentBackedChatRepository`（状态机 worker 文件区），③在 `ui/MarkdownText.kt`（同一区），待其收口后合并为一条 A20+A16数据层+A13+A15可观测性 的派工；④在 core/ 可与任何线并行但为省测试轮次一并做。

---

# 第二批 20261125 · 影视 / 放映场 / 文章页（用户真机截图 6 条）

对应原始编号（用户本轮顺序）：`1→A21`，`2→A22`，`3→A23`，`4→A24`，`文章详情页卡顿→A25`，`右滑抽屉→A26`。

## A21 · 影视列表「放映场」部分没有数据时也要展示（当前行为不对）

- **现象**：首页/影视列表里放映场区块在没有数据时的展示行为与预期不符（用户原话「没有数据的时候不展示」——具体是「本该显示却空白」还是「不该显示却显示空壳」以取证结论为准，截图为放映场内页 28 条正常列表）。
- **根因（取证完成，CONFIRMED）**：结论=「不该显示却显示空壳」。`ScreeningActivity.kt` `ChannelsFeed`（:505-607）两处无守卫：①hero 空盒——hero==null 且非 loading 时仍渲染 216dp 占位 Box（:517-523）；②「逛频道」标题+FilterRow 无条件渲染（:565-566）。频道网格本身已有 `isNotEmpty` 守卫（:571）。数据侧纯网络无缓存（`ScreeningFeedStore.kt:24-38,135-162`，无样例兜底）→ 弱网/失败时整页只剩空壳。次级疑点（PLAUSIBLE，未改只加注释）：:506 `filter { routeOf(it) != FeedRoute.PLAY }` 可能把有数据的一屏全滤空。
- **修复**：hero 外包 `if (hero != null || pager.loading)`；「逛频道」标题+过滤条外包 `if (pager.loading || cards.isNotEmpty())`。:514-515「高度恒定不挪布局」旧注释以用户要求为准改写。
- **状态**：**已修（提交 ee4e3db1）**：hero/逛频道区块守卫改 `数据非空 || loading`（抽象点 18）。

## A22 · 放映页字幕开关「没效果和反应，勾一直在」

- **现象**：字幕面板点「关闭字幕」（已是绿勾选中态）没有任何反应；面板显示「这个视频服务端没有字幕。」
- **根因（取证完成，CONFIRMED）**：全部内联在 `feature-video/.../screening/ScreeningPlayback.kt`。`captionSource` 初始即 0（:148，页面内存不落 prefs），「关闭字幕」项 `selected = captionSource == 0`（:422），onClick 是 `captionSource = 0`——**写入与当前值相同的幂等值，零状态变化**；勾完全由 `selected` 决定（`Setting` :843-855）。服务端无字幕轨（:445 空态文案）、未导入文件、实时识别未开时，面板里唯一「选中」项就是「关」，**不存在任何能「开」的东西**；样式五选只改外观（cue 恒空画面无变化）→ 用户读作「点开关没反应」。不是断线，是**幂等点击+空态无反馈**的交互设计缺陷。
- **修复**：①已关闭时再点给可见反馈（「字幕本来就是关的；这个视频没有可开的字幕轨」走现有 info/hint 通道）；②空态下不给「关闭字幕」画勾（`selected = captionSource == 0 && 有可选来源`），原因并入 hint。保持 :361/:454/:487 状态机一致。注意勿误改 `prototype/PlayerControlsLayer.kt:81-83` 旧播放页另一套 `subtitleEnabled`（不在放映场路径）。
- **状态**：**已修（提交 ee4e3db1）**：`hasCaptionSourceChoice/captionOffSettingSelected/captionNoSourceHint/captionAlreadyOffNotice` 纯函数收口——空态不显示「关闭字幕」勾，重复点「关闭」走 info 通道给可见反馈。

## A23 · 发弹幕：键盘把输入框挡住，输入完要多步才能点发送

- **现象**：键盘弹出时弹幕输入行（「发个弹幕见证当下」+发送）整个被盖；输完要先收键盘再重新点开才能发送。
- **根因（取证完成，CONFIRMED）**：宿主 `HomeActivity` 虽 `windowSoftInputMode="adjustResize"`（app manifest:147），但 `enableEdgeToEdge()`（HomeActivity.kt:26）下 Android 11+ **不做经典 resize**，键盘只以 `WindowInsets.ime` 下发、须由 Compose 消费——而 `ScreeningPlayback.kt` **全文件没有任何 ime inset 消费**（grep `WindowInsets.ime`/`imePadding` 零命中；仓库先例只有 `ChatScreen.kt:180`、`CompanionScreen.kt:270`）。影院态根布局 `full` 分支不加任何 insets（:564-565），输入行挂在贴屏底 Column 的 BottomCenter（:719-730）→ 必被盖。信息态有 `safeDrawingPadding` 但内容超视口、无 `bringIntoView`（:776）。放大器：:284-286 自动收 chrome 只看 `danmakuDraft.isEmpty()` 不看键盘，聚焦未打字 3.5s 后输入框整体消失，加重「好几步」。
- **修复**：Compose 级（唯一有效路径）——影院态 :720 底部 Column 加 `.imePadding()`；信息态 :776 `.imePadding()` 或聚焦 `bringIntoViewRequester`；:284-286 收起条件补「ime 高度==0」门。改 manifest 无收益（edge-to-edge 下 adjustResize 失效）。风险：部分机型 ime inset 含导航栏高度，真机验。
- **状态**：**已修（提交 ee4e3db1）**：影院底栏与信息态 Column 加 `.imePadding()`；`shouldAutoHideChrome` 加键盘在否判定（`WindowInsets.ime` 高度 > 0 时不自动收 chrome）。

## A24 · 放映场点后退直接退出 app，应该返回上一个页面

- **现象**：放映场/播放页顶栏 ← 一点整个 app 退到桌面，不回上一页（首页）。**用户 20261125 追加：图集页（「图集 9 本」）后退同样直接退出 app → 这是「频道内各页共有」的问题，不是单页 bug**，按共性问题定性处理。
- **根因（取证完成，CONFIRMED，频道级单一根因）**：全仓库**没有 Compose Navigation**（grep NavHost 零命中），放映场全部页面是 `ScreeningActivity.kt` :237-244 的 Compose 状态机（`screen when{...}`），宿主 HomeActivity 是 singleTask（manifest :143）且 LaunchActivity `finish()` 后是任务根。子页自己的 BackHandler（`ScreeningGallery.kt:66`、`ScreeningText.kt:55`、`ScreeningSound.kt:50`）**只在更深一级 enabled**，处于 gallery 封面列表级/textHub/soundHub 选择器级时它们全关，`ScreeningActivity.kt:228` 的外层守卫 `channelPage != null && !gallery && !textHub && !soundHub` 又不覆盖这些态 → 返回穿透到系统默认 → **结束整个任务回桌面**。播放器 `ScreeningPlayback.kt:270` 无条件 BackHandler 在内层组合优先消费（不受影响）。`ScreeningActivity` 的 manifest 声明是孤儿（无 startActivity 调用者）。
- **修复（一处修全部）**：:228 扩成状态机总出口——`BackHandler(enabled = 任一非根态)`，onClick 按 when 优先级清一个 flag 回上层；播放器态从 enabled 排除避免抢返回。不改三个子页的深层退层语义。抽象点：**状态机导航必须有一个唯一的「出口判据」，任何层级都不允许落到系统默认返回**。
- **状态**：**已修（提交 ee4e3db1）**：`ScreeningBackStep` 枚举 + 纯函数 `screeningBackStep()` + 单一顶层 `BackHandler`（抽象点 15），gallery/textHub/soundHub 层返回不再落系统默认退出。

## A25 · 文章详情页太卡（WebView 展示差，参考原来的设计）

- **现象**：文章详情页（截图「今日美洲号」）滚动/打开明显卡顿；用户判断是 WebView 优化差或展示方式不对，要求参考旧设计。
- **根因（取证完成，CONFIRMED×2 + PLAUSIBLE×2）**：分流在 `feature-content/.../TextChannelListFragment.java:82`——条目 `fileUrl` 以 `.html` 结尾就走 `TextItemsWebViewFragment` 直接 `loadUrl(vo.fileUrl)`（:113-121），加载的是**整站文章页**（含站点自有页头、点赞/转发/收藏、自带滑出菜单——截图里的站点 chrome 都是远端页面画的）；「原来的设计」= `TextItemsFragment` 走 `/text/item.json` 取正文再 `MyWebView.loadHtmlBody`（`common/.../MyWebView.java:96-114`，纯净 body、无站点 chrome）。①CONFIRMED 渲染对象是整站页面非正文；②CONFIRMED 每次进页新建 WebView + 离页 destroy（无池化/复用，首帧白屏成本源）；③PLAUSIBLE 透明 WebView(`setBackgroundColor(0)`) + Compose 背景拉伸图合成开销；④PLAUSIBLE 无缓存策略/图片不降采样。Compose 化本身不是主因（`05d38cc0` 前后 WebView 设置逐项一致）。放映场同分流在 `ScreeningText.kt:90-94`。
- **修复方向**：首选对齐旧设计——有 id 的文章条目改走 item.json 正文渲染；次选 WebView 池化 + 实色背景。顺带发现：`settings.setAllowFileAccess(false)`(:157) 与收藏夹 `file://` 离线页(`FavoriteFragment.java:96`)疑似互斥，修复时一并验证。
- **B6 关联（重要反转）**：用户截图里「右滑弹出的分类抽屉」（头条号/微头条/热点…）**不是本 app 控件**——全仓源码+全部 git 历史搜不到这些字符串；它是 WebView 里远端文章页自带的 JS 菜单，与 A25 根因①同源。A26（首页原生抽屉手势）修复不受影响；文章页右滑=返回的载体 `SlidingLayout`（左缘 250px）本来就有，页面中部右滑被远端页面吃掉——A25 改走正文后该抽屉自然消失；另 `SlidingLayout.java:86` 拦截只比单增量 |dx|>|dy| 无 touch slop，上下滑抖动可误触，修复时补阈值。
- **状态**：**已修合线（0f30155d）**：文章 `.html` 条目改走 `GET /text/item.json?id=` + `loadHtmlBody`（旧设计），失败回退整页 loadUrl；`setAllowFileAccess` 按 URL 逐次开关（解收藏离线互斥）；B6 左缘返回手势收 24dp 区 + 累计位移 + touch slop（`SlidingLayout.isEdgeSwipeGesture` 纯函数 5 例钉住）。WebView 池化未做（开放项）。真机「还卡不卡」未复测。

## A29 · 三馆页首行大字丑/硬折行/无用文字（用户 20261125）

- **现象**：声音馆「戴上耳机，\n慢慢来。」32sp 硬换行、图集「一册一个地方，\n翻到最后一张为止。」37sp 顶到边、文字馆 32sp；且声音馆 0 个频道时空页还挂「继续下滑加载更多」。用户：去掉或换诗意短句，不许折行。
- **根因**：文案硬编码在 `ScreeningSound/Text/Gallery.kt` 各一处，`\n` 写死折行、字号 32-37sp 无 maxLines；`FeedEndRow`（`ScreeningFeedUi.kt`）列表为空也画「继续下滑加载更多」。
- **修复**：三处改单行短文案（声音馆「戴上耳机，世界慢下来。」/文字馆「一屏一故事，换一盏灯。」/图集「一册一地方，翻到末页。」），24sp + `maxLines=1`；空列表不画页脚行。
- **状态**：**已修合线（`854aed13`）**（三处文案已换成 24sp 单行 `maxLines=1 softWrap=false`；页脚空态守卫收进纯函数 `feedFooterShown`，`FeedFooterA29Test` 钉 5 条）。真机取证进度：tip APK 已装 ADY-AL00 并启动无崩溃，三馆页页面级走查因设备 USB 掉线未完成——观感（24sp 单行不折行）待下次接入补拍，不满意可直接指定换哪句。

## A30 · 上线标准核查：测试残留 + 字幕 mock 数据下落（用户 20261125）

- **问题①：频道测试功能去掉了么？** 核查结论：随包样例兜底已删（`ScreeningFeedStore.kt` 注释两处自述「不再拿随包样例填空」）、debug 启动图标已删（`ScreeningActivity.kt:76` 注释自述）；**残留两处**：侧边栏分组标题仍叫「测试」（`values-zh/strings_home.xml:13`，里面装的是收藏/本地音乐/频道等真实入口——纯命名问题）；origin 线新增「实验室」分组（`drawer_group_lab` + 摄像头 Demo 入口）需确认是否 debug-only（fxlab 主体在 `app/src/debug`，release 不含；入口行要钉 gate）。
- **问题②：服务端字幕 mock 数据怎么没发现？** 核查结论（证据链）：①testserverV 仓库 `origin/testjava_springboot` 全分支 **零条 INSERT**——字幕只有建表 DDL（`sql/2026112400_sound_subtitle.sql` 0 条 INSERT），**没有任何 mock 数据提交过**；②接口代码已部署：104 上运行中的 jar（2026-11-24 14:22 构建）内含 `SoundSubtitleController.class`，`/api/sound/subtitle/items.json` 在线可用；③生产库在内容底座 47.82.163.162:6666（104 本机无 MySQL）；④声音馆服务端线自述纪律=「不写生产库」。**20261125 声音馆线回证（只读探针）**：`sound_subtitle` 表在生产库**已存在、0 行**（`items.json?soundItemId=2148` ×6 全部 errno 0、total:0）；唯一起草过的测试 INSERT（2148「剑雨狼花8」）只存在于共享笔记，从未执行。另查明一层混跑：`api.ok123find.top` 网关后面轮询多台后端，新代码只在 1–2 台命中，**其余实例还是旧 jar，没有 /subtitle handler → errno 10000**——客户端即使接口路径全对，多数请求也会拿到错误。**所以放映场每片都报「这个视频服务端没有字幕」是符合现状的——mock 数据从未存在过，不是丢了。** 待声音馆会话回证后补一行确认；要真机看到字幕，需要（用户授权后）在生产库建表 + 插一条真数据（备份+回滚口径）。
- **状态**：①「测试」分组已改名「更多」（`drawer_group_more`；`HomeDrawer.kt`/`HomeScreen.kt` 判定同步，死字符串 `drawer_group_test` 两份 values 已删、全仓零引用）已合线（`854aed13`）；**真机取证（20261125 ADY-AL00，tip APK）**：侧边栏截图 `/tmp/a30-drawer2.png` 分组标题渲染为「常用/工具/更多」，全站无「测试」字样；实验室入口仅 debug 包出现（`CameraFxLabGate.isEnabled`），release 不含；②待用户决定是否授权在生产库建表+插一条样数据（备份+回滚口径），否则放映场字幕在真机上永远显示「服务端没有字幕」；且顺序应为 全部实例重部署 → 授权补那条 INSERT → 真机复验（声音馆线 20261125 结论，客户端单独改无法绕开）。

## A26 · 页面「右滑」误开右侧抽屉；用户明确：整个频道所有页面都不需要这个抽屉

- **现象**：文章详情页右滑弹出右侧抽屉（头条号/微头条/热点/…分类面板）。**用户 20261125 追加升级：「整个频道里所有的页面就没有"右滑"出抽屉的那个需要，你怎么都给加上了」——即抽屉在本产品里根本没有使用场景，是被错误地加到每个页面上的，要整体去掉而不是调灵敏度。**
- **根因（取证完成，CONFIRMED，单一公共容器）**：不是每页各一份——整个首页（含频道/放映场/图集全部层级）被**同一个** `ModalNavigationDrawer` 包住（`app/.../HomeScreen.kt:173-182`），手势开关是纯函数 `HomeDrawer.kt:171-175` `drawerSwipeEnabled(currentIsChat, drawerOpen, callVisible)` = `drawerOpen || (currentIsChat.not() && callVisible.not())` → **除 chat 外所有页面右滑都会开抽屉**，且旧语义里还允许「已打开时右滑关」。
- **修复（一处修全部）**：纯函数改返回 `drawerOpen`——关闭态任何页面都不给右滑开，打开态仍允许右滑关；`HomeScreen.kt:173-182` 调用点同步；`HomeDrawerGestureTest` 钉新语义。抽屉入口保留顶栏菜单按钮（HomeScreen.kt:306/316）。抽象点：**手势能力的默认值放在公共容器的单一判定函数里，新页面自动继承正确行为，而不是每页自己接**。
- **状态**：**已修，本仓提交 019eb1a3**。`HomeDrawerGestureTest` 已钉新语义（关闭态全站不给右滑开、打开态可右滑关）；该测试首跑被同仓回合状态机 worker 的在途改动挡了编译，绿灯复验随并线树（`HomeDrawer` 与在途文件无重叠）。

## A27 · 图集页封面网格应每行 3 个（当前每行 2 个）

- **现象**：图集列表页（「图集 9 本」）封面每行只有 2 个，用户要求「一行暂时 3 个」。
- **根因（取证完成，CONFIRMED）**：不是 `GridCells`，是手写双列瀑布流硬编码——`feature-video/.../screening/ScreeningGallery.kt` `AlbumCovers()`：**:164 `repeat(2) { column ->`**、**:166 `index % 2 == column`**（文件注释 :50 自述「双列受控瀑布流」）。对照：同文件册内图片已是 3 列（:226 `chunked(3)`）；**旧 View 版图集 span 本来就是 3**（`feature-content/.../ImgChannleBodyFragment.java:51`、`ImgChannelImgListFragment.java:52`、`ImgItemFragment.java:183`）——又是迁移时把 3 列写成了 2。
- **修复**：:164 `repeat(2)`→`repeat(3)`、:166 `% 2`→`% 3`。风险：列宽 1/3 后标题 19.sp（:181）与徽标（:171-179）拥挤、高封面滚动更深——修后需真机看一眼排版。
- **状态**：**已修（提交 ee4e3db1）**：`ALBUM_COVER_COLUMNS=3` + 纯函数 `albumCoverColumn/albumCoverColumnItems`，`AlbumCoverColumnsTest` 钉列分配；worktree 复跑 `:feature-video:testDebugUnitTest` 159 用例 0 失败 + `assembleDebug` exit 0。窄格排版（17sp 标题/10sp 徽标）需真机看一眼。

## A28 · 用户设计的图片占位图被去掉了（图集封面加载无占位）

- **现象**：截图里两枚封面是纯黑圆角块。用户：「我以前设计了一个占位图，你怎么都给去掉了？」
- **根因（取证完成，CONFIRMED，缺失型回归）**：占位图资源**一直都在**——`core/src/main/res/drawable-xxhdpi/img_bg.png`（`feature-content` 同 MD5 副本），旧链路全程在用（`core/.../ImagePhotoLoad.java:46-48` `.placeholder(R.drawable.img_bg)`、`FullScreenImagePagerAdapter.java:44-45` placeholder+error）。**新 Compose 放映场从诞生就没接**：封面链 `ScreeningActivity.kt:633 EntryCover` → `ScreeningFeedUi.kt:210 RemoteCover`，其 **Glide 链（:222-223）没有 `.placeholder()/.error()`**；空 url 退化为纯色 Box（:213）＝截图黑块。`git log -S img_bg -- feature-video` 零命中——不是删了，是重写时没带过来（P1-10 占位专项只落了 chat 的 Coil，没覆盖影视 Glide 路径）。
- **修复**：`ScreeningFeedUi.kt:222` Glide 链补 `.placeholder(R.drawable.img_bg).error(R.drawable.img_bg)`（feature-video 已依赖 :core，资源可直接引用）；:213/`ScreeningActivity.kt:636` 空 url 分支同样换成 img_bg。抽象点与 A5 同族：**占位/错误图是资源加载的默认配置，不该每次重写时靠人想起来**。
- **状态**：**已修（提交 ee4e3db1）+ 缺口补齐随并线**：`RemoteCover` 链 `ImagePlaceholderRes`（core `img_bg`）收口 placeholder/error、空 URL 分支画占位图；并线树复核发现 `ScreeningActivity.EntryCover` 无 URL 分支仍是纯色底，已改走 `ImagePlaceholderRes`（A28 缺口随本批收口）。

---

# 第三批 20261125 · AdMob 广告消失（AD 专项线）

用户原话：「开一个 admob 广告专项。我原来在很多地方增加了广告，现在都不见了」。
编号 `AD1 开屏`，`AD2 插屏`，`AD3 电影/图集/文章页插屏+banner`，`AD4 chat 消息列表 banner`。
专项由独立会话执行（分支自推，台账文件 `docs/design/20261125-admob-ads-ledger.md`）：第一步取证每条广告「原来在哪→怎么没的→恢复落点」，第二步按台账恢复（AD4 待 chat 三路 worker 收口后才动 feature-chat/chatkit 文件）。恢复需真实 ad unit id 而仓库找不到时上报用户，不编造。

---

## A31 · 声音馆「音乐都不见了」（0 个频道）（旁线 20261125 定位）

- **现象**：首页「好听的音乐」卡进声音馆，首屏显示「0 个频道」，看起来音乐全没了。
- **根因（探针实测，CONFIRMED）**：不是接口也不是服务端问题——`/sound/channel.json` 返回 30 行（errno 0，行字段=title/img/rate/id/param/baseurl，**没有 actionUrl**），被放映场共享 `page()` 的「空地址行丢掉」规则全量过滤。用 `param` 探 `items.json` 回 30 条 mp3 正常。抽象问题：**共享的行过滤器用「有没有 actionUrl」当通用身份判据，而不同协议族的身份键不同**（声音=param、图集=imageListKey、文字=textListKey）。
- **修复（旁线 commit `cf758851`，分支 claude/optimistic-pike-092514）**：`soundChannelHub` 走专用 `soundHubEntry`，以 `param` 为身份；通用 `page()` 规则不动；新增测试钉住；`feature-video` 全套 0 失败。
- **状态**：**已修（旁线），待并线复验**。装机验证按单写者规矩：APK 从主线 tip 现场 assemble，走夜色 →「好听的音乐」卡 → 声音馆应列出频道。

## A32 · ADY-AL00 真机原生崩溃：Live2D 引擎 GLThread SIGSEGV（验证轮取证 20261125）

- **现象**：真机走查 chat 页时 app 整个退出到桌面。crash buffer 取证：`signal 11 (SIGSEGV) SEGV_MAPERR fault addr 0xb8`，线程 `GLThread`，栈顶全在 `files/dh-engines/live2d/native-libs/arm64-v8a/libchatwaifu-live2d.so`（4 层 native 内部），Java 侧入口 `Live2dRuntime$buildGlView$view$1$1.onDrawFrame` → `JniBridgeJava.nativeOnDrawFrame`。
- **定性**：数字人 Live2D 运行时 native 层空指针（native so 内部，非本仓 Kotlin/Java 代码栈帧）。发生在聊天页渲染数字人形象期间。**不是本轮 chat 修复引入**（崩溃栈无 chat 线改动文件），属数字人引擎线（dh live2d runtime）。
- **状态**：**新立，待派工（数字人线）**。复现线索：ADY-AL00、聊天页带 Live2D 形象、切页/退出时机可疑（0xb8 ≈ 对象已释放后继续 onDrawFrame —— GLSurfaceView 销毁与 native 模型释放顺序竞态是常见根因，需引擎线取证）。

---

## 架构抽象点汇总（共性问题一次解决）

| # | 共性问题 | 抽象/封装 | 本次落点 |
|---|---|---|---|
| 1 | 远端事件循环无超时兜底（A14/A17） | 任何等待远端事件的流必须有帧间看门狗 | `TurnEventCollector` 看门狗 |
| 2 | 「回合进行中」多份真相 + 4 处独立闸（A17） | `ChatTurnGate`：单一判定，输出允许/拒绝 + 原因 | 新增可测类型，调用点收口 |
| 3 | 停止只杀本地不取消服务端（A14/A17） | 取消必须双向：本地 Job + 服务端回合 | `ReplyStreamer.cancel` → `/sessions/{id}/cancel` |
| 4 | 坐标系靠调用方记偏移（A4，及解题黑边） | 几何工具统一收 `frame: Rect`，返回同坐标系；边界一次转换 | `CropGeometry`/调用点 |
| 5 | 失败静默降级（A5，及解题历史） | 降级必须计数或上抛可显示原因；禁止缓存失败结果 | `FilterPanel`/`LutAssets`/`PhotoRenderer` |
| 6 | 「先服务端后 UI」无乐观层（A19） | optimistic mutation helper + tombstone 防复活 | `MessageRemoval` |
| 7 | 上限混淆协议值与产品值（A12） | 两个常量两层语义，服务端锚定值单独注释 | `ImageMessageLimits` |
| 8 | 查看器/编辑机器各路径各造一套（A9/A10/A15） | 单一 `ImageViewer` + `pendingEdit` 复用，动作通过参数注入 | chatkit 内共享 |
| 9 | 一条消息渲染成多个气泡（A16） | 「一条消息 = 一个气泡容器」不变量 | `MessageParts` |
| 10 | 编辑器布局每工具各排（A7/A6） | 三段骨架：顶栏 / 画布 / 面板槽（在工具条上方）/ 工具条 | `PhotoEditor` panelSlot |
| 11 | 读写都「先等远端」，本地只当灾难备份（A20，与 A19 同层两面） | 列表读统一「cache render → remote reconcile」两段式；重解析（markdown）跨消息缓存、初始化离开主线程 | `ChatViewModel.refresh` / `MarkdownText` |
| 12 | 本地行没有可对账键，历史合并必复活重复（A16 数据层；A19 tombstone 另一面） | 本地写入的每一行必须携带可与服务端对账的键（服务端 id / client_message_id） | `persistTurn` / `cacheRemote` |
| 13 | 同毫秒时间戳做文件名，多选互相覆盖（A13；companion 已修、chat 没修） | 暂存命名统一 CAS 单调 stamp，放 `common/media` 层共用 | `AttachmentStager` |
| 14 | 网络错误被抹平成 null，失败原因看不见（A15，A14 相邻） | 失败必须带码上抛（HTTP/errno → `ChatStrings` 文案 + 日志） | `BaseServiceAwait` / `AttachmentSender` |
| 15 | 状态机导航无唯一出口判据，浅层返回穿透到系统默认退出 app（A24，全频道） | 状态机顶层单一 `BackHandler` 覆盖全部非根态，任何层级不落系统默认返回 | `ScreeningActivity:228` |
| 16 | 手势能力默认值散在各页，新页面自动继承错误（A26，全频道） | 抽屉手势判定收在公共容器单一纯函数：关闭态不给开、打开态才给关 | `HomeDrawer.drawerSwipeEnabled` |
| 17 | 占位/错误图靠每次重写人想起来（A28，与 A5 同族） | 图片加载默认配置必含 placeholder+error（Glide/Coil 两条链都是） | `ScreeningFeedUi.RemoteCover` |
| 18 | 空数据仍渲染空壳占位（A21） | 区块渲染守卫 = `数据非空 || loading`，空态不占版面 | `ChannelsFeed` hero/逛频道 |
| 19 | 共享列表规则按单一协议的字段假设行事（A31，与 A12 上限混淆同族） | 每协议族自带身份映射（`*HubEntry`/`*ListKey`），通用过滤器只兜底不裁决 | `soundHubEntry` / `imageListKey` / `textListKey` |

## 未闭环项（明确列出，不当作已解决）

- **A13** 多图只分析一张：根因已定位（端侧两处：暂存同毫秒覆盖 + 收纳盒观感），**待派工**；服务端无需改。
- **A15** 文件消息必失败：根因已定位（legacy 链路四叠加），端侧可观测性+反馈 **待派工**；真修需服务端补 `/v1` 文件 upload scene（另一条集成线）。
- **A16 数据层**：`local-assistant-*` 行与历史无对账键导致助手气泡重复，**待派工**（与 A19 tombstone 同区）。
- **A20** 首屏慢：根因已定位（network-first 三跳串行 + 图片远端重下 + markdown 缓存未接线），修复**待派工**；网络一跳量级需真机取数（先加计时 instrumentation）。
- **A5 设备侧**：需确认装机包含 `.cube` 资产（logcat `LutAssets`）。
- 陪伴页图片上限（`CompanionViewModel`）与本次产品上限需同步，未在同一 worker 改动范围内。
