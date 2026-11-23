# 全屏通话页五个问题：诊断与修复（已实施）

日期：2026-11-16
范围：`feature-companion` 模块，全屏通话覆盖层。
涉及文件：
- [CompanionCallScreen.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionCallScreen.kt)（1276 行，纯 UI，自包含）
- [CompanionRoute.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt)（通话页装配与 state 映射）
- [CompanionViewModel.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionViewModel.kt)
- [LiveKitCompanionTransport.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/LiveKitCompanionTransport.kt) / [CompanionTransport.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionTransport.kt)

结论：**问题 3、5 是纯客户端 UI 修复；问题 1 是 UI + transport 小改；问题 2 原本是「能力不存在」（麦克风从未发布过），本轮补上麦克风；问题 4 的插槽本轮已接线，真正的头像素材依赖瘦身计划 Task E/F 的内置形象包。** 下文「修复方案」保留为当时的诊断记录，实际落地见下方实施状态。

## 实施状态（2026-11-16）

用户拍板：摄像头**置灰标「即将支持」**，系统返回键**也走二次确认**。按 P1→P2→P3→P4 顺序落地，全部完成。

| 批次 | 实际改动 |
|---|---|
| P1 | `HangUpButton` 二次确认（红圈 + 文案变「再按一次挂断」+ 3 秒自动撤防）；`BackHandler` 同一套二次确认（独立 3 秒窗口，`inCall` 变 false 时清空）；`CaptionStack` 改 `LazyColumn`，渲染全部历史、不再 `maxLines` 截断，区域上限语音形态 200dp / 视频形态 140dp；跟随尾部自动滚动，用户上滑后暂停跟随、滚回底部恢复；`CompanionRoute` 传给字幕的消息去掉 `takeLast(3)` |
| P2 | `TransportConnection` 四态（CONNECTING/CONNECTED/RECONNECTING/DISCONNECTED）；LiveKit 侧把 `RoomEvent.Reconnecting/Reconnected/Disconnected` 全部写入 `connection` StateFlow；`CompanionConnection` 加 `RECONNECTING`，ViewModel 逐态映射（DISCONNECTED→ERROR）；Route 不再把 ERROR 压成「正在接通」，改为 ENDED + `statusHint` 显示 `uiState.error`；计时器在 Route 里按 `connection == CONNECTED` 每秒 tick，重连期间暂停、恢复后继续 |
| P3 | `CompanionTransport.setMicEnabled`（接口默认空实现，测试替身不用改）；LiveKit 侧 `localParticipant.setMicrophoneEnabled` 真实发布/取消，失败发 `CompanionEvent.Error`；建连前就点了通话的麦克风意图先存 `micIntent`、连上后补偿执行；开/关用 `Mutex` 串行化，防连点乱序；Route 用 `rememberLauncherForActivityResult` 申请 `RECORD_AUDIO`，拒绝时**仍进通话但静音**（她照样说、字幕照样来），接通与「接听来电」走同一入口；`isMuted` 进 `CompanionUiState`，静音药丸是现成的；摄像头按钮置灰、标签「即将支持」、点了 toast |
| P4 | Route 用 `AvatarPackStore.avatarFor(characterId)?.let { load(it)?.portrait }` 取立绘，经 `avatarContent` 插槽用新增的 `AvatarPortrait`（IO 线程按 `MAX_LAYER_PX` 缩放解码，路径+修改时间为键）渲染；角色名从 `/v1` 角色接口取，用于顶栏与字标兜底；素材缺失仍回退「陪伴」+ 首字圆，不空屏 |

新单测：`CompanionCallStateTest`（4 例）——拒绝权限进通话且静音、静音开关同时驱动 UI 与 transport、挂断清静音并下发关闭、RECONNECTING/DISCONNECTED 映射到界面（后者归 ERROR）。

验证：`:feature-companion:compileDebugKotlin`、`:app:compileChatDebugKotlin` 通过；`:feature-companion:testDebugUnitTest`、`:app:testChatDebugUnitTest` 全绿（新类 4/4）。**未做真机验证**：麦克风实际出声、断网重连的琥珀态、二次确认手感都还需要一台真机连真实房间过一遍。

---

---

## 问题 1：进入通话页后看不出有没有连上

### 根因（三处叠加）

1. **状态映射把异常吞了**。[CompanionRoute.kt:172-176](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt:172) 只区分两种：`CONNECTED → CONNECTED`，其余（IDLE / CONNECTING / **ERROR**）一律映射成 `CallConnection.CONNECTING`。ViewModel 在连接失败时写的是 `CompanionConnection.ERROR`（[CompanionViewModel.kt:78-84](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionViewModel.kt:78)），于是失败后界面永远显示「正在接通…」，没有错误、没有重试入口。
2. **计时器是死值**。`elapsedSeconds = 0L` 硬编码（CompanionRoute.kt:166），UI 层不自己计时（这个设计本身是对的，权威应在传输层），但没有人给它喂秒数，所以即使连上了，顶部也永远 `00:00`，看起来和没连上一样。
3. **transport 不上报房间事件**。LiveKit 的 `RoomEvent.Connected / Reconnecting / Reconnected / Disconnected` 在 [LiveKitCompanionTransport.kt:59-65](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/LiveKitCompanionTransport.kt:59) 里只处理了 `Disconnected`（还被笼统发成 `CompanionEvent.Error`），`Reconnecting/Reconnected` 直接 `else -> Unit` 丢弃，UI 设计好的琥珀色「连接不稳定，正在重连…」永远没有数据源。

### 修复方案

- `CompanionTransport` 增加 `val connectionState: StateFlow<TransportConnection>`，枚举 `CONNECTING / CONNECTED / RECONNECTING / DISCONNECTED`；`FakeCompanionTransport` 直接给 `CONNECTED`。
- LiveKit 实现里收集 `RoomEvent.Connected/Reconnecting/Reconnected/Disconnected` 更新这个 flow；`Disconnected` 同时保留现有 Error 事件。
- ViewModel 把 transport 的状态映射进 `CompanionConnection`（新增 `RECONNECTING` 枚举值），Route 做完整四态映射（不再把 ERROR 压成 CONNECTING），ERROR 时通过现有的 `statusHint` 字段显示 `uiState.error` 文案。
- 计时器：Route 里一个 `LaunchedEffect(connection == CONNECTED)` 每秒 tick 本地计数（重连期间暂停、恢复后继续），喂给 `elapsedSeconds`。不需要改 VM。
- 预计改动：transport ~60 行、VM ~15 行、Route ~30 行，配 transport 单测。

## 问题 2：静音 / 摄像头按钮没反应

### 根因

不是按钮坏了，是**能力不存在**：

- [CompanionRoute.kt:182-183](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt:182) 两个回调就是空 lambda，注释写明「传输层还没有这两个方法」。
- 全模块 grep 不到任何 `publishAudio / setMicrophoneEnabled / CameraCapturer / publishVideo`——`LiveKitCompanionTransport.connect()` 只建房间、注册文本流，**从未发布本地麦克风轨**。也就是说现在对 agent 说话本来就传不过去，通话页只有「她发文字/TTS、用户打字」这一条路。
- 运行时权限申请在 feature-companion 里一次都没有（`RECORD_AUDIO / CAMERA` 在 app manifest 已声明，只差运行时 request）。

### 修复方案（分两档）

**麦克风（建议本轮做，语音通话的前提）**
1. `CompanionTransport` 加 `fun setMicEnabled(enabled: Boolean)`；Fake 实现空操作。
2. LiveKit 实现用 `room.localParticipant.setMicrophoneEnabled(enabled)`（SDK 自动处理轨道发布/取消发布），连接成功后默认开麦。
3. Route 里用 `rememberLauncherForActivityResult` 申请 `RECORD_AUDIO`，拒绝时按钮给「需要麦克风权限」提示；VM 增加 `isMuted` 状态，接通 `state.isMuted`，顶部「已静音」药丸是现成的。

**摄像头（需要先回答一个产品问题）**
- 技术做法相同：`setCameraEnabled(enabled)` + CAMERA 权限 + 本地小窗用 LiveKit `VideoTrackRenderer` 填 `localVideoContent` 插槽。
- 但服务端 agent **没有视频轨**，开了摄像头只有本地预览，远端永远是「等待她的画面…」占位。两个选项：
  - (a) 本轮只做本地预览（selfie 小窗能亮，远端占位保留）；
  - (b) 暂时把摄像头按钮置灰 + 「即将支持」提示，等 agent 侧支持视频再开。**我倾向 (b)**，避免做出一个看起来通了其实是单向的功能。
- 预计：麦克风 ~80 行（含权限）；摄像头若做 (a) 再 ~80 行。

## 问题 3：挂断太敏感，要求按两次

### 根因

[CompanionRoute.kt:179](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt:179) `onHangUp = { chatViewModel.setCallActive(false) }`，[CompanionCallScreen.kt:1096-1130](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionCallScreen.kt:1096) 的 `HangUpButton` 单击即触发，没有任何中间态。

### 修复方案（纯 UI，~45 行，零跨层改动）

- `HangUpButton` 内部 `remember` 一个 `armed` 状态：第一次点变红圈描边 + 标签从「挂断」变「再按一次挂断」，`LaunchedEffect` 起 3 秒倒计时自动撤防；窗口期内第二次点才调 `onHangUp()`。
- 用 `animateFloatAsState` 做标签/透明度切换，不弹 Dialog（通话页弹对话框会遮挡字幕，也不符合拇指操作）。
- 现有系统返回键（CompanionRoute.kt:79 `BackHandler`）建议保持单次退出，还是同样二次确认？**建议返回键也走同一套 armed 状态**，否则误触返回仍然秒退。需要用户确认这一点。

## 问题 4：中央「S」圆太丑，换角色头像 + 整体风格

### 根因

- 中央圆走的是三级兜底的最后一级：`avatarContent` 插槽没人传 → `avatarEmoji` 传的是 `characterId.trim().take(1)`（[CompanionRoute.kt:158-160](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt:158)，角色 id 首字母，所以出现「S」）→ 落到 [CompanionCallScreen.kt:723-728](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionCallScreen.kt:723) 的首字渐变圆。
- 真正的头像素材现在不存在：`feature-companion/src/main/assets` 目录没有；`AvatarPackStore.load()` 在没有安装包时返回 null（设计好的内置兜底路径从未被填充）；`CharacterSummary` 模型也没有头像 URL 字段（只有 id/name/tagline/tags/voiceAvailable），所以连「先塞个网络图」都没有数据源。

### 修复方案（本轮接线 + 素材另立任务）

1. **本轮可做——把插槽接到现成的形象体系**：Route 里 `remember { AvatarPackStore(context) }`，按 `store.avatarFor(characterId) ?: characterId` 取 key，`store.load(key)` 命中就把 `AvatarFiles.portrait` 通过 `avatarContent` 插槽用 `Image` 圆形裁剪渲染；未命中保留字标兜底。这样瘦身计划 Task E/F 的内置形象包一进 `assets/avatars/<key>/`，通话页自动出图，不用再改 UI。
2. **兜底字标本身美化（~20 行）**：用角色展示名（`CharacterSummary.name`，角色列表接口已有）替代 id 首字母；渐变底改柔、加内阴影和细高光，避免现在这种「一个硬渐变紫饼」的观感。
3. **整体 UI 风格**：现有配色体系（深夜靛蓝渐变 + #8B7CF6 紫 + 玻璃按钮）本身完整，问题主要是头像区空洞和元素偏密。建议的小步调整：头像直径 136→148dp、呼吸环描边降透明度、控制条按钮加按压缩放（`graphicsLayer` 0.96）、字幕气泡圆角与间距加大。不做无边界的整体重设计——那是独立设计任务，应和内置形象上线放在一起评审。
4. 真正的二次元头像生产仍按瘦身计划 §5：ComfyUI + NoobAI XL 免费出图 → PIL 派生 3 层 → 打包进 assets。

## 问题 5：文字消息滚动看不到效果

### 根因（截图里两个气泡末尾的「…」就是证据）

通话页**根本没有可滚动容器**。`CaptionStack`（[CompanionCallScreen.kt:742-780](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionCallScreen.kt:742)）是普通 `Column`，只渲染最近 3 条（`CAPTION_MAX_LINES = 3`），每条 `maxLines = 3 + TextOverflow.Ellipsis`，超长直接截断成「…」，没有任何 `verticalScroll/LazyColumn`。另外 Route 喂数据时过滤成只剩 ASSISTANT 消息（CompanionRoute.kt:153-155），**用户自己发的文字在通话页完全不显示**。

### 修复方案（纯 UI + Route 数据口径，~90 行）

- `CaptionStack` 改为 `LazyColumn`（高度约束在现有字幕区域内，居中反转布局让最新消息贴底），去掉 3 条上限和 `maxLines` 截断，长文完整展示。
- `LaunchedEffect(captions.size, captions.lastOrNull())` 里 `animateScrollToItem(lastIndex)`，新消息/流式增量自动滚到底；用户手动上滑后短暂抑制自动滚动（记住 `userScrolled`，到底后恢复），避免想看历史时被拽回去。
- Route 的 transcript 口径从「仅 ASSISTANT」改为全部消息，USER 消息用不同对齐/低饱和气泡样式区分（`MessageRole` 已在数据里）。
- 视频形态 `VideoStage` 里复用同一个组件，两处自动一起修好。

---

## 建议的实施批次

| 批次 | 内容 | 性质 | 量级 |
|---|---|---|---|
| P1 | 问题 3 挂断二次确认 + 问题 5 字幕滚动 | 纯 UI，零风险，可独立编译验证 | ~140 行 |
| P2 | 问题 1 连接四态 + 计时器 + 错误可见 | UI + transport 接口小改，带单测 | ~120 行 |
| P3 | 问题 2 麦克风（含权限）；摄像头先置灰 | transport + 权限，需真机验证 | ~100 行 |
| P4 | 问题 4 头像插槽接线 + 字标美化 | UI + AvatarPackStore，素材到位即出图 | ~80 行 |
| 依赖项 | 内置二次元形象包（瘦身计划 Task E/F） | 素材生产，独立任务 | — |

## 两个决策点（已拍板）

1. 摄像头按钮：**本轮置灰标「即将支持」**（不做单向预览）。
2. 系统返回键：**也走二次确认**。

## 未做 / 留给后续

- 通话页字幕目前仍只显示 ASSISTANT 消息（原方案里提过把 USER 消息也铺进来，并用不同样式区分；本轮没有做——通话页是语音形态，用户自己的话由麦克风回传，先不引入两种气泡样式，等有真机反馈再说）。
- 摄像头单向预览、视频形态的远端画面：等服务端 agent 有视频轨再开。
- 头像素材本身：仍等瘦身计划 Task E/F 的内置形象包落进 `assets/avatars/<key>/`。
