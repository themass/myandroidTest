# 聊天与角色扮演图片消息（P1）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 角色扮演（companion）消息支持图片+文字一条消息，chat 与 companion 全局统一为「收纳盒」（MediaStack）图片样式，图片复用 chat 既有的「端上传 → upload_id → 服务端读图喂模型」链路。

**Architecture:** 图片不走 LiveKit 数据通道——客户端发送时逐图 `POST /v1/uploads` 拿 `upload_id`，companion 用 `control.input kind=attachments` 事件先发、文字后发，服务端把 ids 暂存 pending 槽并并入该用户下一次 `submit`，复用既有 `get_owned → 喂模型` 管线。样式在 chatkit 落为显式 `ImageLayout` 枚举，默认 Stack。

**Tech Stack:** Kotlin/Compose（chatkit、mediaview、feature-companion、feature-chat、common）、org.json、OkHttp、LiveKit Android 2.29.0；服务端 Python（companion-agent，FastAPI + LiveKit Agents + Redis），pytest。

**Spec:** `docs/superpowers/specs/2026-11-21-image-messages-design.md`（本计划实现其 §8 P1 全部七项）

## Global Constraints

- Android 仓库：`/Users/gqli/work/myandroidTest/.claude/worktrees/elastic-mendeleev-ab3d36`（分支 `claude/elastic-mendeleev-ab3d36`）。每个 Task 独立提交；提交信息尾行必须带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。合并进 `weifu-refactor` 与推送由主会话收尾统一执行，执行者不得自行合并/推送。
- 服务端仓库：`/Users/gqli/work/deepagents/docs/myproject/companion-agent`（git root `/Users/gqli/work/deepagents/docs`，分支 `main`）。同样只提交不推送。
- Android JSON 一律 `org.json.JSONObject`，项目没有序列化库，不要新增。
- 协议共享向量 `feature-companion/src/test/resources/protocol_v2.json` 与 `ProtocolCatalog` 的一致性由 `ProtocolCodecTest` 锁住：`EVENTS` 白名单新增事件类型必须同步改向量，否则测试红。
- hello caps 必须**双端同步**：客户端 `ProtocolHandshake.kt:28 CLIENT_CAPS` 与服务端 `SERVER_CAPS` 写同一个名字，否则握手交集落空，能力协商不到。
- `feature-companion` 不得 import `feature-chat` 包（模块依赖是单向的，build.gradle 里只有 `:common :core :chatkit :speech`）。
- 单条消息图片上限 **9 张**：客户端选图器拦截；服务端 `SendBody` 校验（Task 6）。
- 样式统一是用户拍板的决定：chat 现有气泡行为**允许变化**（单图从等比 168×200 变为 190×140 centerCrop 顶卡），不要为此保留 chat 特例。
- 真机安装只用 `adb push` + `pm install -r`，不用裸 `adb install`。
- **协调（设计 §10）**：Task 1 改变 chat 气泡默认渲染；`video-channel-bugs` 会话正用 ADY-AL00 做 chat 真机回归——合入 chat 可见变更前与主会话对齐时间窗（主会话负责，执行者遇设备冲突先报告）。

## Review Focus

以下失败模式是设计文档隐含但单个 Task 测试不直接覆盖、最可能咬人的五类，各自钉在归属 Task 的测试步骤里：

1. **乱序**：文本先于 attachments 事件到达 → 本轮按纯文字处理，pending 留槽给下一轮消费并记 warn，不得丢图。→ Task 5 测试。
2. **pending 未消费/TTL 过期**：60s 内没等到该 participant 的用户回合 → 静默丢弃，文字回合完全不受影响。→ Task 5 测试。
3. **重复重发**：同一 `client_msg_id` 重发 attachments 事件 → pending 不叠加（幂等）。→ Task 5 测试。
4. **部分上传失败**：一条消息里某图上传失败 → 文字不得先走、待发缩略图保留、不产生半条消息。→ Task 4 测试。
5. **签名 URL 拉取失败/过期**：历史图片换 URL 失败 → 气泡渲染灰占位，文字照常渲染。→ Task 3 测试。

## 协议契约（跨仓接口，Task 4/5 共同遵守）

- 事件：`control.input`（既有事件类型，无 catalog EVENTS 变更），args：
  ```json
  {"kind": "attachments", "upload_ids": ["u1", "u2"], "client_msg_id": "att-<epochMillis>-<seq>"}
  ```
- 时序：客户端**先发 attachments 事件、后发文字**（文字仍走 `lk.chat` 文本流）。
- 服务端：单槽 pending（**进程内存**，TTL 60s，后到覆盖前值——调查证实 attachments 事件与房间文本落在同一个 agent worker 进程：`bridge.on_input`（entrypoint.py:408 挂载）与 `llm_node → TurnPipeline → PlatformBrain.start_turn`（entrypoint.py:373-374 装配）都在 entrypoint 为每个房间 new 的对象里，房间 worker 只对应一个用户，故 Redis 不必要，裁定见「裁量记录」）；该房间下一次用户回合 `PlatformBrain.start_turn` 的 `submit` 调用以 `attachments=[{"upload_id": id, "kind": "image"}]` 并入并消费清空；`client_msg_id` 相同视为重发不叠加。
- 服务端落库/读 API 形状（已核实）：`submit(attachments=…)` → `meta["attachments"]` 原样存 → 历史 API `_message_json` 原样回带。一条带图消息：
  ```json
  {"id": 123, "role": "user", "kind": "chat", "content": "照片里有什么？",
   "attachments": [{"upload_id": "01J…", "kind": "image", "name": "a.jpg"}]}
  ```
- caps：`"media.image" to 1` 同时写进客户端 `CLIENT_CAPS` 与服务端 `SERVER_CAPS`；客户端只在协商成功后显示「+」入口。

---

### Task 1: chatkit `ImageLayout` 样式库 + ImageGrid 默认收纳盒（含 mediaview 单图角标收敛）

**Files:**
- Modify: `chatkit/src/main/java/com/openapi/chatkit/model/ChatItem.kt`（`MessagePart.Images`，:24）
- Modify: `chatkit/src/main/java/com/openapi/chatkit/message/parts/MessageParts.kt:156-210`（`ImageGrid`）
- Modify: `chatkit/src/main/java/com/openapi/chatkit/message/ChatMessageItem.kt:252-257`（透传 layout）
- Modify: `mediaview/src/main/java/com/openapi/mediaview/MediaComponents.kt:169-183`（角标）
- Modify: `chatkit/build.gradle`（加 junit testImplementation）
- Test: `chatkit/src/test/java/com/openapi/chatkit/message/parts/ImageArrangementTest.kt`（新建）

**Interfaces:**
- Produces: `enum class ImageLayout { Auto, Stack }`（chatkit.model，与 MessagePart 同文件）；`MessagePart.Images(val sources: List<String>, val layout: ImageLayout = ImageLayout.Stack)`；`internal fun imageArrangement(count: Int, layout: ImageLayout): ImageArrangement`，`internal enum class ImageArrangement { SingleFit, Stack, Carousel }`（均在 `MessageParts.kt`）。Task 3/4 依赖默认值 Stack：映射图片时**不需要**显式传 layout。

- [ ] **Step 1: 写失败测试**（决策函数是纯 Kotlin，无 Android 依赖，纯 JUnit 即可）

```kotlin
class ImageArrangementTest {
    @Test fun `Stack 策略下任意张数都进收纳盒`() {
        for (count in listOf(1, 2, 4, 7, 9)) {
            assertEquals(ImageArrangement.Stack, imageArrangement(count, ImageLayout.Stack))
        }
    }
    @Test fun `Auto 策略保留旧分支`() {
        assertEquals(ImageArrangement.SingleFit, imageArrangement(1, ImageLayout.Auto))
        assertEquals(ImageArrangement.Stack, imageArrangement(2, ImageLayout.Auto))
        assertEquals(ImageArrangement.Stack, imageArrangement(4, ImageLayout.Auto))
        assertEquals(ImageArrangement.Carousel, imageArrangement(5, ImageLayout.Auto))
        assertEquals(ImageArrangement.Carousel, imageArrangement(9, ImageLayout.Auto))
    }
}
```

- [ ] **Step 2: chatkit/build.gradle 加 `testImplementation 'junit:junit:4.13.2'`（照 mediaview 写法），跑测试确认编译失败**（`imageArrangement` 未定义）。Run: `./gradlew :chatkit:testDebugUnitTest`；Expected: 编译错误。
- [ ] **Step 3: 实现决策函数与枚举**：`MessageParts.kt` 顶部加 `ImageArrangement`；`ChatItem.kt` 在 `MessagePart` 旁加 `ImageLayout`（含 KDoc：Auto=按张数分支旧行为，Stack=一律收纳盒）；`Images` 加默认参 `layout: ImageLayout = ImageLayout.Stack`。`imageArrangement` 按 Step 1 的表实现。
- [ ] **Step 4: ImageGrid 按 arrangement 分支**。签名加 `layout: ImageLayout = ImageLayout.Stack`（置于 `modifier` 前）。`SingleFit` 走现有单图 AsyncImage 分支（:167-182 原样）；`Stack` 一律 `MediaStack(items, theme, onClick, onLongClick)`（**任意张数**，含 1 张）；`Carousel` 走 `MediaCarousel`。ImageViewer 逻辑（:198-209）不动。`ChatMessageItem.kt:252` 的调用加 `layout = part.layout`。
- [ ] **Step 5: mediaview 角标收敛**：`MediaComponents.kt` 角标 Box 用 `if (count > 1) { … }` 包住（设计 §4.2：单图无角标）。拖拽已有 `count <= 1` 门（:111），不动。
- [ ] **Step 6: 跑测试确认通过**：`./gradlew :chatkit:testDebugUnitTest :mediaview:test`；Expected: PASS。
- [ ] **Step 7: 提交**：`git add chatkit mediaview && git commit -m "feat(chatkit): 图片布局策略 ImageLayout，默认收纳盒统一 chat 气泡"`

**验证补充（非阻塞）**：1/2/4/7 张 × Stack/Auto 的真机截图对比放 Task 7。

### Task 2: common `MediaUploader` 提公（压缩暂存 + 上传拿 id + 换签名 URL），chat 接入

**Files:**
- Create: `common/src/main/java/com/openapi/common/media/MediaUploader.kt`
- Modify: `common/build.gradle`（okhttp 已是 `api libs.okhttp` 无需加；只加 `testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'`）
- Modify: `feature-chat/src/main/java/com/openapi/feature/chat/data/remote/AgentChatRemote.kt:167-199, 218-220`（委托）
- Test: `common/src/test/java/com/openapi/common/media/MediaUploaderTest.kt`（新建）

**Interfaces:**
- Produces:
  ```kotlin
  class MediaUploader(baseUrl: String, tokenProvider: () -> String?, client: OkHttpClient = …) {
      @Throws(MediaUploadException::class)
      suspend fun upload(file: File, mime: String): String        // POST /uploads 原始字节，返回 upload_id
      suspend fun signedUrl(uploadId: String): String?            // GET /uploads/{id} → url，取不到返回 null
  }
  object MediaStager { fun stageImage(context: Context, uri: Uri, dir: File, stamp: Long): File? }
  ```
  Task 4 用 `MediaStager.stageImage` + `MediaUploader.upload/signedUrl`；`upload_id` 字段名与服务端一致（`json.optString("upload_id")`）。
- 暂存逻辑照抄 `feature-chat/…/presentation/AttachmentStager.kt:49-56`（`ImageUtils.decodeUriApplyingExifOrientation` → `ImageCompressor.compress(Bitmap): Result(bytes,…)` → 写 `img_$stamp.jpg`）；失败/无法解码返回 null（companion 不需要 chat 的 Rejected 原因细分，理由差异记在 KDoc）。

- [ ] **Step 1: 写失败测试**（MockWebServer 起两路：`POST /uploads` 回 `{"upload_id":"u42"}`；无鉴权头时回 401 → 断言抛 `MediaUploadException`；`GET /uploads/u42` 回 `{"url":"https://x/y?sig=1"}` → 断言 `signedUrl` 返回该 url；404 → null）
- [ ] **Step 2: `./gradlew :common:testDebugUnitTest` 确认失败（类不存在）**
- [ ] **Step 3: 实现 `MediaUploader`/`MediaStager`**（OkHttp 超时参数照 `AgentChatRemote` :43-56 restClient；`signedUrl` 沿用注释语义「签名 URL 不落日志/缓存」；base url 拼接方式与 `AgentChatRemote.upload` 完全一致）
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: `AgentChatRemote.upload` 与 `attachmentUrl` 改为委托内部持有的 `MediaUploader`**（保持现有签名与错误语义 `Failure`；`AttachmentSender`/`hydrateImages` 零改动），`./gradlew :feature-chat:assembleDebug :common:testDebugUnitTest` 确认编译+测试绿
- [ ] **Step 6: 提交**：`git commit -m "refactor(common): 媒体上传/暂存提公 MediaUploader，chat 接入"`

### Task 3: companion 读路径——历史附件解析、逐 id 换 URL、消息模型与列表映射

**Files:**
- Modify: `feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionModels.kt:43-49`（`CompanionHistoryItem`）
- Modify: `feature-companion/…/CompanionApi.kt:33, 103-111`（history 解析 + `attachmentUrl`）
- Modify: `feature-companion/…/CompanionUiState.kt`（`CompanionMessage`）
- Modify: `feature-companion/…/CompanionViewModel.kt`（进页/刷新后 hydration）
- Modify: `feature-companion/…/CompanionScreen.kt` `companionItems()`（:780-860 区）
- Test: `feature-companion/src/test/java/com/openapi/ks/myapp/companion/CompanionModelsTest.kt`（扩展）、`CompanionViewModelTest.kt`（扩展）

**Interfaces:**
- Consumes: Task 2 `MediaUploader.signedUrl`（HttpCompanionApi 委托，别再造一份 okhttp GET）。
- Produces: `CompanionHistoryItem(val role: String, val text: String, val attachmentIds: List<String> = emptyList())`；`CompanionMessage` 增加 `images: List<String> = emptyList()`（渲染源：本地路径或签名 URL）与 `attachmentIds: List<String> = emptyList()`（未 hydration 的 id）；`companionItems()` 对 `images` 非空的消息产出 `MessagePart.Images(images, ImageLayout.Stack)` **排在 Text 之前**（chat 渲染顺序：图片→文字）。Task 4 依赖这两个字段做乐观消息。

- [ ] **Step 1: 写失败测试（CompanionModelsTest）**：history JSON 条目 `"attachments": [{"upload_id": "01J…", "kind": "image", "name": "a.jpg"}]`（服务端已核实形状：meta.attachments 原样回带）→ `attachmentIds` 解析正确、`text` 不受影响；无 `attachments` 字段 → 空列表（老数据兼容）；`attachments` 为 `[]` → 空列表；某项缺 `upload_id` → 跳过该项不崩。
- [ ] **Step 2: `./gradlew :feature-companion:testDebugUnitTest --tests '*CompanionModelsTest'` 确认失败**
- [ ] **Step 3: 实现解析**；`CompanionApi` 接口加 `suspend fun attachmentUrl(uploadId: String): String?`，Http 实现委托 `MediaUploader.signedUrl`。
- [ ] **Step 4: ViewModel hydration**：历史装载与增量刷新后，对 `attachmentIds` 非空且 `images` 为空的消息逐 id 调 `attachmentUrl`；成功 → url 进 `images`，失败 → `images` 填入哨兵空串（渲染层灰占位由 chatkit `ChatKit.colors.surfaceVariant` 底自动兜底，无需新组件）。Review Focus 5 的测试钉这里：`attachmentUrl` 返回 null 的 fake → 断言该消息 `images` 含空串、`text` 保留、无异常。
- [ ] **Step 5: `companionItems()`** 映射 `MessagePart.Images`；长按暂接默认空实现（companion 无转发/删除菜单，**记为 P2 补菜单**——设计 §4.3 与现实的差异，本计划裁定）。
- [ ] **Step 6: 全模块测试绿**：`./gradlew :feature-companion:testDebugUnitTest`
- [ ] **Step 7: 提交**：`git commit -m "feat(companion): 历史图片附件解析与渲染（收纳盒样式，逐 id 换签名 URL）"`

### Task 4: companion Composer「+」选图/拍照 + 待发条 + 发送时上传 + attachments 事件

**Files:**
- Modify: `feature-companion/…/CompanionUiState.kt`（`pendingImages`、`canSend`）
- Modify: `feature-companion/…/CompanionScreen.kt`（`CompanionComposer` :784-858 加「+」与待发条；`CompanionScreen` 挂 launcher）
- Modify: `feature-companion/…/CompanionViewModel.kt`（选图暂存、发送编排）
- Modify: `feature-companion/…/CompanionTransport.kt`（接口 :103-111 旁加 `sendAttachments`）
- Modify: `feature-companion/…/LiveKitCompanionTransport.kt`（实现 + 协商位）
- Modify: `feature-companion/…/protocol/ProtocolHandshake.kt:28`（CLIENT_CAPS 加 `"media.image" to 1`）
- Modify: `feature-companion/build.gradle`（coil-compose）
- Test: `CompanionViewModelTest.kt`（扩展）、`ProtocolHandshakeTest.kt`（caps）

**Interfaces:**
- Consumes: Task 2 `MediaStager/MediaUploader`；Task 3 `CompanionMessage.images/attachmentIds`；协议契约（上文）。
- Produces: `CompanionTransport.sendAttachments(uploadIds: List<String>, clientMsgId: String)`（接口默认空实现，照 `sendTouch` :111 的样子）；`CompanionUiState.pendingImages: List<PendingImage>`，`data class PendingImage(val file: File, val path: String)`；协商查询 `Negotiator` 暴露或 transport 暴露 `val imageSupported: Boolean`（hello ack 协商到 `media.image` 才 true）。

- [ ] **Step 1: 写失败测试（ViewModel）**：
  1. 两条待发图 + 文本 → fake uploader 收到两次 upload、fake transport 收到的调用序列是 `sendAttachments(ids, clientMsgId)` 先于 `sendText`；乐观消息 `images` 为两个本地路径、`attachmentIds` 为两个 id。
  2. 第二张上传抛异常 → `sendText` 未被调、`pendingImages` 保留 2 条、出现错误提示状态（Review Focus 4）。
  3. 纯图无文本 → `canSend` 为 false（**裁定**：companion 回合由文本触发，纯图消息 P1 不做，见设计 §5 时序）。
  4. `imageSupported=false` → 「+」入口不出现（state 标志断言）。
- [ ] **Step 2: `./gradlew :feature-companion:testDebugUnitTest --tests '*CompanionViewModelTest'` 确认失败**
- [ ] **Step 3: 实现 ViewModel**：`stageImage`（filesDir/companion_attachments）、选图去重、发送编排（逐图 upload → 全部成功才 `sendAttachments`+`sendText`，`client_msg_id = "att-" + System.currentTimeMillis() + "-" + 序号`）。上传在 `viewModelScope`，进行中 `canSend=false` 防重入。
- [ ] **Step 4: 实现 UI**：`CompanionComposer` 文本框左侧 38dp 圆形「+」（橙色系与发送按钮同款但描边样式，避免两个实心圆打架）→ 点击展开 chatkit `ActionPanel`（`ComposerAction` 两项：相册 `Icons.Outlined.PhotoLibrary`、拍摄 `Icons.Outlined.CameraAlt`）。launcher 照搬 `feature-chat/…/presentation/ui/ChatRoute.kt:74-130`：`PickMultipleVisualMedia(maxItemsPerSelection = 9)` + `PickVisualMediaRequest(ImageOnly)`；拍照 `TakePicture` + FileProvider（authority 常量 `com.openapi.ks.chat.fileprovider`，与 app 清单 :149-152 一致，注释写明；相机权限 `PermissionGateway.of(host).request(Permissions.camera(rationale))`，`:common` 已有）。待发缩略图条：输入框上方横向 `LazyRow`，36dp 圆角缩略图 + 右上角删除 X，coil 加载 `PendingImage.path`。**部分失败提示**：Toast「部分图片上传失败，已保留待重发」并中止本条发送（设计 §7「继续发成功部分/取消」在本页的等价实现，不做半发）。
- [ ] **Step 5: 实现 transport**：`LiveKitCompanionTransport.sendAttachments` 照 `sendTouch` :476-502 组包（`JSONObject().put("kind","attachments").putJSONArray("upload_ids", …).put("client_msg_id", …)` → `protocolLink.outgoing(TOPIC_IN, "control.input", args)` → `publishData(RELIABLE)`；未握手 v2 或未协商 `media.image` → 记 warn 返回，**无旧版回退通道**）。`CLIENT_CAPS` 加位 + `ProtocolHandshakeTest` 加断言（与服务端 SERVER_CAPS 名字逐字一致：`media.image`）。
- [ ] **Step 6: 全模块测试绿 + `./gradlew :feature-companion:assembleDebug`**
- [ ] **Step 7: 提交**：`git commit -m "feat(companion): 输入区选图拍照、发送时上传并以 control.input attachments 携带 upload_ids"`

### Task 5: 服务端——control.input 多 kind 分发 + pending 槽 + submit 合并 + SERVER_CAPS

**Files:**（路径以 companion-agent `server/` 为根）
- Create: `src/companion/api/livekit/input_attachments.py`（`PendingAttachmentSlot` + `CompositeInputRelay`）
- Modify: `src/companion/api/livekit/entrypoint.py:408`（`bridge.on_input = TouchInputRelay(session)` 换成按 `args["kind"]` 分发的 `CompositeInputRelay`：touch → 现 `TouchInputRelay` 原样复用，attachments → 新分支，其余 ignored 计数）
- Modify: `src/companion/bootstrap/voice_wiring.py:67`（`PlatformBrain(...)` 构造传 pending 槽，`_wire_voice` 签名加参）与 `entrypoint.py:373` 调用点（传同一个槽实例）
- Modify: `src/companion/service/turns/platform_brain.py:54-71`（`start_turn` 里 consume → `submit(attachments=…)`）
- Modify: `src/companion/service/uploads/policy.py`（新增 `MAX_MESSAGE_ATTACHMENTS = 9`，媒体限制的本家；本 Task put 处与 Task 6 SendBody 校验都 import 它）
- Modify: `src/companion/agent/protocol/handshake.py:36-46` `SERVER_CAPS` 加 `"media.image": 1`
- Test: `tests/test_input_attachments.py`（新建）+ `tests/test_platform_brain.py`、`tests/test_touch_input.py`、`tests/test_protocol_bridge.py` 扩展

**已核实事实（2026-11-21 调查，写码按此，不用重新考古）：**
- 分发链：`protocol_bridge.py:269-283 _on_input` 把 `envelope.args`（dict）交给**单一** `bridge.on_input` callable，异常吞掉记 warning；注册点就是 entrypoint.py:408 那一行。没有 kind 级分发，未知 kind 现在进 `TouchInputRelay.ignored` 静默丢弃。handler 拿不到 room/identity——也不需要：一个房间 worker 一个 bridge、一个用户，槽是 per-worker 实例。
- 文本回合：lk.chat 由框架 AgentSession 收取 → `agent.py:147 llm_node` → `build_request`（不带附件）→ `TurnPipeline.run`（pipeline.py:58）→ `PlatformBrain.start_turn`（platform_brain.py:54，现以 `client_message_id=None, role="user", kind="voice"` 调 `submit`，`attachments` 参数闲置）。**合并点就在 start_turn**，不动 `TurnRequest`/`llm_node`。
- submit 落库形状（submit.py:85-91 `meta["attachments"]` 原样存 → 读 API `_message_json` chat.py:76-79 原样回带）：一条带图消息 `"attachments": [{"upload_id": "01J…", "kind": "image", "name": "a.jpg"}]`。Task 3 客户端解析以此为准。
- `negotiate()`（handshake.py:150-181）：caps 交集外**丢弃能力项、不拒握手**；只有 proto 版本不重叠才 `NegotiationError`。老客户端不带 `media.image` → 握手照常、能力缺席。
- 测试跑法：`uv run pytest tests -q`。替身范式：bridge 测试用 `_Sink`/`_Publisher` 直接喂 `bridge.on_data`（test_protocol_bridge.py，bridge 不 import livekit）；brain 测试用 `FakeSubmit`/`FakeControl`（test_platform_brain.py）；touch 用 `_Session`（test_touch_input.py，同步 `generate_reply(*, user_input)`）。

**Interfaces:**
- Consumes: 协议契约（args `{"kind":"attachments","upload_ids":[…],"client_msg_id":"…"}`、TTL 60、覆盖语义）；`submit(..., attachments: list[dict[str, Any]] | None)`（既有参数，submit.py:63-66）。
- Produces:
  ```python
  class PendingAttachmentSlot:
      def put(self, upload_ids: list[str], *, client_msg_id: str) -> None: ...
      def consume(self) -> list[str]: ...   # 空槽/过期 → []；消费即清
  class CompositeInputRelay:
      def __call__(self, args: dict[str, Any]) -> None: ...  # 同步，签名同 TouchInputRelay
  PENDING_TTL_SEC = 60
  # 上限常量放 src/companion/service/uploads/policy.py（媒体限制的本家，HTTP 与 pending 两路共用）：
  # MAX_MESSAGE_ATTACHMENTS = 9   # Task 5 put 处与 Task 6 SendBody 校验都 import 它
  ```
  entrypoint 组装：**一个** `PendingAttachmentSlot` 实例同时喂给 `CompositeInputRelay` 与 `_wire_voice → PlatformBrain`。
- 零新读图接口：并入后走既有 `_attachment_ids → TurnInputMessage.upload_ids → get_owned → 喂模型`（turn_runner.py:65-79, 248-300）。

- [ ] **Step 1: 写失败测试（槽是纯内存对象，直接注入 fake，无 Redis）**：
  1. `CompositeInputRelay`：`kind=attachments` → 槽写入；`kind=touch` → 走 TouchInputRelay 回归（断言 `generate_reply`）；未知 kind → 两边都不碰（ignored++）。
  2. 合并：槽有 2 ids → `PlatformBrain.start_turn` 调 `FakeSubmit` 收到 `attachments == [{"upload_id": i, "kind": "image"} for i in ids]` 且槽被清空；槽空 → `attachments=None`（现状回归）。
  3. 乱序（Review Focus 1）：先 `start_turn`（consume 空）后 `put` → 本轮 `attachments=None`，槽保留，第二次 `start_turn` 并入。
  4. TTL（Review Focus 2）：时钟可注入（`time.monotonic` 函数参数），过期 `consume` → `[]`，无异常。
  5. 幂等（Review Focus 3）：同 `client_msg_id` put 两次 → consume 返回一份不翻倍。
  6. `upload_ids` 超 9 或非 list/非 str 项 → put 拒绝 + warn，槽不变。
- [ ] **Step 2: `uv run pytest tests/test_input_attachments.py -q` 确认失败**
- [ ] **Step 3: 实现**（槽内记 `(monotonic, client_msg_id, ids)`；warn 用现有 logger；`CompositeInputRelay` 构造收 `(session, slot, *, cooldown_s=…)`，touch 分支内部 new TouchInputRelay 保持冷却语义）
- [ ] **Step 4: SERVER_CAPS 加 `"media.image": 1`**；在 test_protocol_bridge 的 hello 交集测试补断言：客户端报 `media.image` → `ack.caps` 含之；不报 → 缺席且 ack 仍 ok（握手不拒）。
- [ ] **Step 5: `uv run pytest tests -q` 全绿**
- [ ] **Step 6: 提交**：`git commit -m "feat(api): control.input attachments 暂存并入用户回合 upload_ids（pending 60s，幂等）"`

### Task 6: 服务端——SendBody attachments 数量校验（≤9 → 422）

**Files:**（companion-agent `server/` 为根）
- Modify: `src/companion/api/http/routers/chat.py:115-121`——`SendBody.attachments: list[Attachment]` 加 `Field(default_factory=list, max_length=MAX_MESSAGE_ATTACHMENTS)`（pydantic 2 对 list 直接支持 max_length，422 自动带条数提示；import Task 5 放在 `service/uploads/policy.py` 的常量）
- Test: 扩展 `tests/test_clear_and_attachments.py`（L59 起已有 POST attachments 的用例，形状照它）

- [ ] **Step 1: 写失败测试**：POST `attachments` 10 条 → 422；9 条 → 2xx；既有用例（1 条、0 条）回归。
- [ ] **Step 2: `uv run pytest tests/test_clear_and_attachments.py -q` 确认失败**
- [ ] **Step 3: 实现**（一处 Field 约束，无入口代码改动）
- [ ] **Step 4: `uv run pytest tests -q` 全绿**
- [ ] **Step 5: 提交**：`git commit -m "feat(api): SendBody attachments 数量上限 9（422）"`

### Task 7: 真机验收 + `image_reply` 剧本 case（图文回合端到端）

**Files:**
- companion-agent `server/src/companion/devtools/script_room/`：新 case `image_reply`
- 验收记录：写入共享 notes 目录 `image-messages-p1-accept.md`

- [ ] **Step 1: 剧本 case**：用户 participant 依次 `control.input attachments`（fixture 预上传 id）+ `lk.chat` 文本「照片里有什么？」→ agent 真模型看图 → 断言 timeline 出 `speech.seg` 且文本含图内要素关键词。TTS 用 `--tts fake`。CLI 若需 `--upload-id` 参数则一并加。Run: `uv run python -m tools.script_room run --case image_reply --tts fake --wait 180`；Expected: exit 0。
- [ ] **Step 2: 真机（ADY-AL00，`adb push` + `pm install -r`）**：
  - chat 与 companion 各发 2 张、7 张 → 均为收纳盒、角标「n/总数」、可翻全部；chat 单图变 190×140 顶卡目视确认（与 companion 一致）。
  - companion 发图+文字 → 她回复引用图内容（真人路径复验 Step 1 的机器结论）。
  - 待发条上删一张 → 只传剩下的；上传中拔网线 → 失败提示、缩略图保留、文字未发出。
  - 历史重进页 → 她的图与用户的图逐 id 出图；断网进页 → 灰占位 + 文字正常。
- [ ] **Step 3: 兼容性目视**：老包（无 media.image caps）连房 → 不出「+」；含图历史在老包只显示文字不崩。
- [ ] **Step 4: 证据写 notes 并提交两仓收尾 commit（由主会话合并 weifu-refactor / push main）**

---

## 裁量记录（计划层的决定，执行者照办不必再问）

- **companion 纯图消息 P1 不做**：用户回合由文本触发（lk.chat 无文本即无回合），故有图必须带文字；纯图触发协议是 P2。
- **companion 长按图片菜单暂不接**：chat 的长按语义（转发/删除）在 companion 无对应功能，P2 补。
- **部分上传失败在 companion 简化为「中止本条、保留待发」**：不做「继续发成功部分」的半发 UI。
- **`ImageArrangement` 决策函数用纯 JUnit 锁逻辑**：Compose 渲染的像素级对比放真机（Task 7），chatkit 不为此引入 robolectric-compose 测试基建。
- **不新增协议事件类型**：`control.input` 已在双端 catalog 白名单，kind 是 args 层字段——`protocol_v2.json` 共享向量与 `catalog.py` 的 spec 都不需要动，`ProtocolCodecTest` 天然不受影响。
- **上线前置（非本计划代码）**：`sspacee` 桶策略确认非公共读（设计 §7 挂账），真机验收通过后、功能放开前确认。
