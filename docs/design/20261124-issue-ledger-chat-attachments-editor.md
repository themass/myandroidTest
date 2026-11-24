# 问题台账 20261124 · chat / 附件 / 图片编辑（19 条，一条不丢）

用户原话：「每个问题都要有一个标志，不能遗失」「如果是个架构设计或者共性问题要从架构层面彻底解决，要学会抽象架构设计，封装」。
本台账就是那个标志表：`A1…A19` 对应原始抱怨的 10 大项 19 个可执行点。每条含 **现象 → 根因（file:line 证据）→ 修复方案 → 是否共性问题/抽象点 → 状态**。

状态标记：`已修合线` / `派工中` / `待取证` / `需服务端`。

对应原始编号：
`1→A1`，`2→A2`，`3-1)→A3,A4`，`3-2)→A5,A6`，`3-3)→A7`，`3-4)→A8`，`3-5)→A9`，`3-6)→A10`，`3-7)→A11`，`3-8)→A12`，`4→A13`，`5→A14`，`6→A15`，`7→A16`，`8→A17`，`9→A18`，`10→A19`。

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
- **状态**：**派工中**（mediaview worker）。

## A5 · 滤镜没有反应，全是黑框

- **根因（已定位到唯一静默出口）**：`.cube` LUT 资产真实存在且能解析（`mediaview/src/main/assets/filters/{creamy,coolwhite,retro,moody,airy,mono}.cube`，33³，实测 6 个全部 OK；已进打包产物 `app/build/intermediates/assets/chatDebug/.../filters/`）。唯一的失败通道是 `LutAssets.get` 返回 null：
  - `mediaview/.../edit/FilterPanel.kt:240` `provider(id) ?: continue` → 该格跳过，`FilterPanel.kt:169` 留 `0xFF2A2A2A` 深灰底 → 六格全空在黑面板上就是「黑框」；
  - `mediaview/.../render/PhotoRenderer.kt:270` `lutProvider(edit.id) ?: return sourceBitmap` → 画面**静默不变**（点了没反应）；
  - `mediaview/.../render/LutAssets.kt:25,35-39` 读盘/解析失败返回 null 且**把失败结果进程级 memo 记住** —— 一次瞬时 IO 失败会把整个进程的滤镜永久灭掉，只在日志 `Log.w`。
- **修复**：①面板统计取不到的 LUT 数量，>0 时明确提示「滤镜资源不可用」并把格子画成可辨占位，而不是留深灰；②`LutAssets` 失败不缓存、成功才缓存；③渲染降级保持不崩，但要让调用方知道「这次编辑的滤镜没生效」（可观测信号，不改数据模型语义）。
- **仍需现场取证**：装到 V2279A 的包是否是含 assets 的最新构建（截图里面板与资产是同批提交进来的）。抓 `logcat` tag `LutAssets`/`PhotoRenderer`：若出现「未能解析」→ 装的是旧包/中间构建。参见记忆规则「装机只装主线新构建」「『旧包』先取证」。
- **共性/抽象点**：**「失败必须可见」**。本项目反复出现 `?: return` / `?: continue` / `runCatching { Log.w }` 的静默降级（解题历史落盘同一毛病已修）。抽象点：任何降级返回都要①计数或②向上抛一个可显示的原因，UI 层统一渲染；禁止把失败结果当成功结果缓存。
- **状态**：**派工中**（mediaview worker）。

## A6 · 颜色没有色板可选（只有 6 个固定色块）

- **根因**：`mediaview/.../edit/PhotoEditor.kt:496-499` 只有 6 色 `PALETTE`，`Swatches`（:944-975）就渲染这 6 个；没有任何任意色入口；马赛克 `color = null`（:437-442）连颜色行都没有。
- **修复（按 Astryx 设计稿 `protos/photosolve-ui/src/EditorScreen.tsx`）**：扩到 16 色（每行 8、选中描边+勾，走主题色阶不散写 hex）+「自定义」开 Dialog（色相/饱和度/明度三滑杆 + 实时预览 + 使用这个颜色）；下游已全量接受任意 `Color`（`toArgb().toLong()`，`PhotoEditor.kt:290,366,473`），**不需要改数据模型**；选中已画标注的 `SelectionPanel`（:924）共用同一套。
- **共性/抽象点**：**取色器是一等组件，不是散在面板里的色块数组**。Astryx 无 ColorPicker，官方规范是 swatch grid（`role=grid/row/gridcell`、`aria-pressed`、每行 8、一个 Tab 停靠点）+ 自定义色 Dialog。抽象点：`Swatches(selected, onPick)` 签名不变（已可承载任意色），新增 `CustomColorDialog` 复用同一个 `onPick`。
- **状态**：**派工中**（mediaview worker）；设计稿已 build 通过（`astryx doctor --built` 0 fail，已在 Browser pane 验证渲染）。

## A7 · 整体功能区域按钮布局不好，最佳实践是什么

- **现状问题（截图证据）**：面板贴在屏幕底边、标签被切一半；工具与面板关系不固定。
- **设计答案（最佳实践，落在样稿里）**：**固定三段**——顶栏（关闭 · 标题 · 撤销/重做/重置 · 完成）→ 画布 → **面板槽在工具条上方** → 工具条贴底。面板永不与工具条抢底边，标签永不被裁；工具条等宽图标+标签，选中态用 primary/下划线短横而不是染黄；画布区 = 图片实际显示矩形（见 A4）。这套已在 `EditorScreen.tsx` 可视化，并作为 mediaview worker 的验收要求（面板必须在工具条上方、任何面板内容完整可见）。
- **共性/抽象点**：**编辑器 = 「画布 + 一个面板槽 + 一个工具条」的三段骨架**，任何工具（裁剪/涂鸦/文字/马赛克/滤镜/调整）只是往面板槽里换内容。抽象点：`panelSlot` 单一注入点 + 统一「面板在工具条上方」布局约束，而不是每个工具各自排布局。
- **状态**：设计**已完成并验证**；代码侧对齐**派工中**。

## A8 · 多选图片只能编辑第一张 → 要求相册多选不进编辑器，拍照才进编辑

- **根因**：`feature-chat/.../ui/ChatRoute.kt:92-96` 刻意分流——`uris.first()` 带 `openEditor` 进编辑，`drop(1)` 直接进草稿（注释写「避免一次弹多个编辑器」）。相册与拍照本来就是两个 launcher（相册 `PickMultipleVisualMedia` :88-89，拍照 `TakePicture` :108-115）。
- **修复**：相册路径两处合一、调 `onImagesPicked(context, uris)` **不传 openEditor**；拍照路径不动（「拍照才进编辑器」已成立）。接缝 `SendActions.imagesPicked(... openEditor: ((File,Bitmap)->Unit)?)`（`SendActions.kt:184-222`）已支持为空。
- **状态**：**派工中**（附件 UX worker）。

## A9 · 选完图在输入框里无法点击看大图

- **根因**：`chatkit/.../input/ComposerPanels.kt:56-157` 的 `DraftTray`，图片分支只有裸 `AsyncImage`（:66-73），全文件可点的只有删除 ×（:162）和「+」面板项（:193）。
- **修复**：图片草稿缩略图加点击 → 复用现成全屏查看器 `chatkit/.../media/ImageViewer.kt:51-96`（Dialog 全屏 + HorizontalPager + 缩放/双击），sources = 当前全部图片草稿、startIndex = 被点那张；开关状态收在 chatkit 内部（不往 `ChatScreen` 加参数）。
- **共性/抽象点**：**「大图查看器」必须是一个组件，被草稿托盘和消息气泡共用**（见 A10、A15）。抽象点：`ImageViewer(sources, startIndex, onDismiss, onEdit?, …)` 单一入口，调用方只提供数据与动作。
- **状态**：**派工中**。

## A10 · 消息大图上浮动「编辑」按钮，点击再进编辑页

- **根因**：查看器就是 `ImageViewer`（由 `ImageGrid` 调起，`chatkit/.../message/parts/MessageParts.kt:316,348-362`），但它只有 `onDismiss/onLongPress/onRetry`（`ImageViewer.kt:54-58`），没有动作槽；编辑机器 `pendingEdit` + `PhotoEditor` + `replaceDraftImage`（`ChatRoute.kt:80,300-327`）只对草稿里的 `File` 生效，已发送消息没有 draft。
- **修复**：`ImageViewer` 加可选 `onEdit: ((String)->Unit)?`，非空时底部居中浮「编辑」按钮；`ImageGrid` 透传；`ChatRoute` 把该图解码进 `pendingEdit` 复用现有编辑流，**编辑结果作为新草稿附件加入输入框（`composer.addDraft`），不回写已发送消息**——已发送消息没有本地覆盖文件，硬造覆盖语义会腐化历史记录（这是本次的产品决策，代码里要写注释）。
- **状态**：**派工中**。

## A11 · 编辑完保存到本地相册

- **根因**：功能已有但接错地方——`AttachmentActions.saveToPictures`（`feature-chat/.../ui/AttachmentActions.kt:133-156`，MediaStore insert + `DIRECTORY_PICTURES` + IS_PENDING 两段式）是 **private**，只有长按图片菜单的 `Download` 动作能用；编辑产物 `PhotoEditExporter.export`（`mediaview/.../edit/PhotoEditExporter.kt:70-103`）返回 `Uri.fromFile(File(cacheDir,"photo_edit/..."))`，在 `ChatRoute.kt:307-324` 导出后没有任何保存入口。权限无忧：minSdk 28 / targetSdk 37，scoped storage，清单刻意不声明写权限（`app/src/main/AndroidManifest.xml:32-37`）。
- **修复**：把保存提成可复用（`saveEditedImageToGallery(context, uri)` 走 `localImageFile` 本地分支 :121-130），编辑导出成功后提供「保存到相册」，成功/失败都要有可见反馈；`Build.VERSION < Q` 分支给可读提示（现有实现直接返回 null）。
- **状态**：**派工中**。

## A12 · 图片消息限制最多 4 张

- **根因/现状**：当前硬上限是 **9**（`common/.../media/ImageMessageLimits.kt:19` `MAX_MESSAGE_IMAGES`，注释说明与服务端 `MAX_MESSAGE_ATTACHMENTS` 同源）。三道闸：选择器 `ChatRoute.kt:89 maxItems`、草稿暂存 `ComposerActions.addDraft`（`ComposerActions.kt:57-62`，超限由 `SendActions.imagesPicked:199-208` 停止并报 `attachmentLimitReached`）、发送处无独立闸。用户看到的「4」只是气泡排布决策表（`MessageParts.kt:96-103` `count <= 4 -> Stack`），不是数量限制。
- **修复**：**不动服务端锚定值 9**；新增产品上限常量（`MAX_IMAGES_PER_PICK = 4`）+ 注释区分「服务端传输 9 / 产品 4」，用它替换 `ChatRoute.kt:89` 的 `maxItems`，并让 `addDraft` 的闸用产品上限。陪伴页同一套（`CompanionViewModel.kt:1078,176-179`、`CompanionScreen.kt:178-179`）需同步，已在 worker brief 里标注为待协调项。
- **共性/抽象点**：**「上限」分两层：协议上限（与服务端同源，改一处要同步服务端）与产品上限（客户端体验决策）**，必须两个常量两个注释，不能靠同一个数字兜两边。
- **状态**：**派工中**。

## A13 · 图片消息里多张图片，服务端只看了一张就乱回

- **根因**：**未定位**。客户端侧图片+视频共用一个 attachments list 上 wire（`ComposerActions.kt:36-44` 注释），发送侧代码看起来是带全部附件的；到底是客户端只送了一张、还是服务端只分析了一张，需要端到端取证（附件管道取证 agent 仍在跑）。
- **修复路径**：先在客户端补一条可观测证据（发送 payload 里附件数量与顺序落日志/测试），再对照服务端 `chat.py` 的多图分析逻辑。**服务端改动属另一条集成线（agent-docs/服务端仓库是 main）**，不在客户端台账内闭环。
- **状态**：**待取证 + 需服务端**。

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
- **状态**：**派工中**（状态机 worker）。

## A15 · 文件消息必失败 + 输入框里的文件不能点击查看

- **根因**：**未完全定位**（附件管道取证 agent 仍在跑）。已确认的相邻事实：`DraftTray` 全文件只有删除 × 可点（`ComposerPanels.kt:162`），所以「输入框里文件点不开」= A9 的同一根因在文件草稿分支上；`ImageViewer` 只处理图片，文件没有查看器。「必失败」需要抓发送侧证据（上传接口、文件 MIME/大小闸、服务端返回）。
- **修复**：第一步先加可观测性（发送文件时的 payload 与失败原因落到 `ChatStrings` 错误文案 + 日志），再按证据修；文件预览能力缺失是独立的下一个问题（先让它「点了有反应/有明确不支持提示」，不要静默）。
- **状态**：**待取证**（点击查看部分已随 A9 派工）。

## A16 · 图片+文字 / 附件+文字 看起来像两条消息

- **根因**：`chatkit/.../message/parts/MessageParts.kt` 里附件块与文字块的间距/分隔让视觉上是两个独立气泡（消息数据本身是一条，`ChatItem` 未拆分）。
- **修复**：同一消息的附件块与文字块**共用一个气泡容器**（同底色同圆角，内部用分隔线而不是两个气泡）；有附件时收紧块间距、附件与气泡边缘留白一致；用户侧与助手侧都要成立。不改消息模型。若实际已是单气泡，则只调间距并把结论写清楚。
- **共性/抽象点**：**「一条消息 = 一个视觉容器」**是渲染不变量；任何新增 part（图片/文件/位置/引用）都往同一个气泡里加，而不是并排第二个气泡。抽象点：`MessageBubble` 作为容器，parts 只声明内部顺序与分隔。
- **状态**：**派工中**。

## A17 · 很容易「死机」：那边失败这边发不出新消息，点停止也不好使

- **根因（三重闸叠加，不是单一 bug）**：
  1. **句柄与标志**：`sending/streaming` 在所有 catch/cancel 路径确实有清（NonCancellable 收尾 `ReplyStreamer.kt:149-155,186-187`），但 `attachmentJob` 每次重发被直接覆盖丢句柄（`ChatViewModel.kt:66,71`），依赖旧 job 已自然结束这一前提。
  2. **服务端回合没被取消**：点停止 = `ChatViewModel.onStopGenerating:199-202` 只杀本地 Job（`ReplyStreamer.cancel():45-50`）。服务端取消接口存在（`AgentChatRemote.kt:154 POST /sessions/{id}/cancel`）但 **feature-chat 全模块无调用**（只有遗留 `app/.../agent/AgentChatViewModel.kt:97` 调旧接口）→ 服务端回合继续占坑，用户再发就吃 409「上一轮回复还没结束」（`AgentChatRemote.kt:363`）→「怎么发都失败」。
  3. **sending 卡住时**（A14 的静默回合）：新输入被静默塞进 `queued`（A2 语义），而队列只在回合自然结束时排空 → 静默回合永不结束 → 队列永不出、界面「发了没反应」；`HistoryActions.loadOlder:33` 也被 `sending` 静默挡掉。
- **修复**：①`ReplyStreamer.cancel()` fire-and-forget 调服务端 cancel；②submit 得 409 时先 cancel 再重试一次，仍失败给明确文案；③`attachmentJob` 赋值前取消旧句柄（或结构化并发）；④看门狗（A14）保证 sending 不会永远 true。
- **共性/抽象点（用户说的「架构层面彻底解决」最典型的一条）**：**「回合进行中」现在有三份互不知晓的真相**——`ReplyStreamer.replyJob`（UI）、`ChatViewModel.attachmentJob`（UI，可被覆盖）、`MigrationGuard.busy`（仓库进程级，`AgentBackedChatRepository.kt:458-470`），外加服务端自己认为的回合状态端上完全不知道。读侧另有 4 个独立 `sending` 闸（`SendActions.kt:45,103,132,192`、`HistoryActions.kt:33`、`ResendActions.kt:26,44`）。**抽象点：`ChatTurnGate`**——单一可测的小类型，输入当前状态，输出 allowSend / allowLoadOlder / allowResend + 被挡原因；各调用点改为读它。写侧（谁写 sending/streaming）保持不动但集中注释清楚边界。本次只做最小收口，不做回合重写。
- **状态**：**派工中**。

## A18 · 头顶「加载更早的消息」按钮常驻，下拉加载更多应是默认功能

- **根因**：按钮条件只有 `state.hasMoreHistory`（`ChatScreen.kt:190-199`），而 hasMore 是「这页被填满(30)就当作还有」的粗近似（`AgentChatRemote.kt:110-122` 注释明说故意的），与滚动位置无关 → 几乎永远挂在顶栏。列表是普通 `LazyColumn`（`ChatMessageList.kt:231-283`，无 reverseLayout），**整个 chatkit 没有触顶判定**（三个滚动效果只服务贴底 :188-228）。加载逻辑在 `HistoryActions.loadOlder:31-51`，`sending` 时还静默 no-op（:33）。
- **修复**：去掉常驻 TextButton；在 `ChatMessageList` 加「滚到顶自动加载」（用 `firstVisibleItemIndex`/`canScrollBackward`，加载中不重复触发，且与 `programmaticScroll` 配合——程序滚动不得触发加载）；`loadingOlder` 时列表顶部显示小进度指示。现成下拉件 `PullToRefreshBox`（`core/.../legacyui/list/ListShell.kt:189`；`ChannelBodyScreen.kt:115-117`、`FeedbackActivity.kt:280` 在用）作为备选，但 chat 列表贴底逻辑复杂，优先做触顶自动加载。`loadOlder` 的 `sending` 静默 no-op 放开（读操作与发送无关）。
- **共性/抽象点**：**「还有更多」的判定要尽量精确化**：服务端应返回真实 hasMore（游标/总数），客户端「填满即当作还有」是权宜；至少在 UI 层把「有没有更多」与「用户滚到哪」两个概念分开。
- **状态**：**派工中**。

## A19 · 多选删除没有立刻在消息列表体现，应该立刻成功、后台异步删服务端

- **根因**：`MessageRemoval`（`feature-chat/.../presentation/MessageRemoval.kt`）严格「服务端确认成功后才从 UI 移除」：`deleteMany:44-68` 逐条**串行** `deleteOnServer`（:49-56），全部跑完才一次性过滤（:57），再 `reconcile()`（:59 = `ChatViewModel.fallbackReloadMessages:276-292`）；`deleteOne:30-42` 同样。无逐条进度，失败也不回滚（因为从未前滚）。另有一处**契约矛盾**：确认框文案说「只会从本机移除，服务器不受影响」（`values-zh/strings.xml:15`），代码实际先删服务端（:27,36）。
- **修复（乐观更新，正是用户要的产品语义）**：先立即从 `state.messages` 移除并落本地缓存 → 后台删服务端 → 失败则把条目放回**原位** + 报错误条（复用 `state.error`/`ChatErrorBar`）。多选点了立刻消失，服务端可并行。文案改成与代码一致（确实删服务端），选「诚实」而不是「假装只删本机」。竞态防护：删除过程中来了历史同步不能复活已乐观删除的条目——加「待删除 id」集合挡复活。
- **共性/抽象点**：**乐观更新（local-first + 失败回滚 + 防复活）是本项目缺的一层**。「先等服务端再改 UI」的模式在删除、重命名、收藏等处会反复出现同样的「点了没反应」。抽象点：一个小的 optimistic mutation helper（快照 → 应用 → 失败回滚 → 错误上报），配合 reconcile 侧的 tombstone 集合。本次只在删除上落这个模式，其他调用点列为后续。
- **状态**：**派工中**。

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

## 未闭环项（明确列出，不当作已解决）

- **A13** 多图只分析一张：端侧证据未取完 + 服务端需改（另一条集成线）。
- **A15** 文件消息必失败：根因待取证，先加可观测性。
- **A5 设备侧**：需确认装机包含 `.cube` 资产（logcat `LutAssets`）。
- 陪伴页图片上限（`CompanionViewModel`）与本次产品上限需同步，未在同一 worker 改动范围内。
