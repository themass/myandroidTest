# 「对话」Tab 要不要迁到 /v1：决策文档

日期：2026-11-15　状态：只读分析，未改任何代码
分析对象：worktree `vigorous-goldwasser-584d3f`（tip `7374abba`）+ 服务端 `companion-agent`（`~/work/deepagents/docs/myproject/companion-agent`）

---

## 0. 结论

**建议：先补能力再迁，但不是 big-bang；并且第一步应该是「先把 /v1 的入口放回来」。现在不能直接换。**

三条判断依据：

1. **/v1 的内核已经比 chat 强，而且已经在线上跑。** 我实测 `/v1/sessions`、`/v1/roles`、`/v1/uploads`、`/v1/voice/sessions` 都被 nginx 转到了 Python 服务（见 §2.3），`docs/design/deployment-plan.md` §9.7 与 `full-test-plan.md` 里「公网未部署、nginx 404」的说法**已经过期**。
2. **但客户端那一半几乎没做。** `/v1` 的 Android 侧只有 1801 行（`app/src/main/java/com/openapi/ks/myapp/agent/`），没有一个入口能点到；附件、语音、角色目录、会话列表在客户端是**零**，而这些在 `feature-chat` 侧是齐的。
3. **真正卡住「对等替换」的只有一样东西：语音转写与朗读。** 附件、会话管理、角色目录在**服务端都已就绪**，客户端补齐即可；ASR/TTS 在 `/v1` 上**根本没有 HTTP 接口**（§3.2），只能继续调 Java 的 `/chat/ai/*` 或者在 Python 侧新做——这是唯一需要跨团队、跨服务的工作。

所以顺序是：**先恢复入口 → 补客户端骨架（附件/会话/角色/渲染）→ 单独立项解决语音 → 最后按开关灰度切 Tab**。不建议现在整体切，也不建议无限期不迁：两套并存正在产生真实成本（§4），而且「陪伴」Tab 的文字已经在往 `/v1` 里写历史了（§4.1）。

---

## 1. 现状（已核实）

### 1.1 两条链路的落点

| 页面 | 客户端入口 | 传输 | 服务端 | 鉴权 |
|---|---|---|---|---|
| 「对话」Tab | `ChatRoute` → `ChatRepositoryImpl`（`feature-chat/.../ui/ChatRoute.kt:58`） | HTTP + SSE | Java testserverV，`/chat/stream.json`（`core/.../Constants.java:199`） | `BaseRequest` 的 UA/Token 头 |
| 「陪伴」Tab | `CompanionRoute` → `LiveKitCompanionTransport`（`feature-companion/.../CompanionRoute.kt:56`） | LiveKit 房间（文本流 + 音轨） | Python companion-agent `/v1` | `Authorization: Bearer`，服务端查 Redis |
| 新对话页（无入口） | `AgentChatRoute` → `HttpAgentApi`（`app/.../agent/ui/AgentChatRoute.kt:46`） | HTTP + SSE | 同一个 Python `/v1` | 同一个 Bearer |

### 1.2 `/v1` 文字对话没有入口——确认属实

- `HomeTab` 枚举里有 `AgentChat`（`HomeScreen.kt:89`），渲染分支也在（`HomeScreen.kt:217` → `AgentChatEntry()`）。
- **底部栏只列三个**：`HomeScreen.kt:146` 是 `listOf(HomeTab.Chat, HomeTab.Companion, HomeTab.Profile)`。
- **抽屉里也没有**：`53ab9ef6` 删掉了那 11 行；`HomeScreen.kt:167-179` 现在只剩「频道」。
- 所以 `AgentChat` 是一个**只能通过 `selected` 状态被程序点到的死枚举**。`HomeTab.entries[selected]`（`HomeScreen.kt:99`）永远取不到它。

### 1.3 我实际测到的服务端状态（2026-11-15，`api.ok123find.top`）

这些是 curl 实测，不是推断：

| 路径 | 结果 | 说明 |
|---|---|---|
| `/v1/sessions` | **401** `{"detail":"missing credentials"}` | FastAPI 风格响应 ⇒ 已转发到 Python 服务 |
| `/v1/roles`（带 `Bearer 1`） | **401** `{"detail":"invalid credentials"}` | 与 `service/auth/redis_token.py:21` 的 `^token_[A-Za-z0-9+/]{8,512}={0,2}$` 校验一致 ⇒ 确实是 companion-agent |
| `/v1/uploads`（GET） | **405** `{"detail":"Method Not Allowed"}` | 该路由只注册了 POST（`routers/uploads.py:16`）⇒ 路由已挂载 |
| `/v1/voice/sessions`（GET） | **405** | 只注册了 POST（`api/livekit/sessions_router.py:50`） |
| `/api/agent/v1/sessions` | **404**（nginx HTML） | 旧文档里写的这个前缀**不存在** |
| `/companion/characters` | **404**（nginx HTML） | 同上 |
| `/vvv/api/roles/v1/divisions` | **400** `... '-parameters' flag` | `1940141d` 修的客户端同源问题，服务端这个 bug **仍然在** |
| `/vvv/api/chat/stream.json`（GET） | 200 `{"errno":11002,"data":null...}` | chat 链路活着（GET 不支持属于正常，接口是 POST） |

> **重要更正**：`deployment-plan.md` §9.7、`full-test-plan.md:24-25` 都写着「公网未部署」。
> 那是当时的结论。现在的实际前缀是 **`/v1`**，不是 `/companion/*` 也不是 `/api/agent/v1/*`。
> 客户端代码也是按 `/v1` 拼的：`HttpAgentApi.execute` 拼 `"/v1" + path`（`AgentApi.kt:169`），
> `HttpCompanionApi` 直接写 `"/v1/voice/sessions"`（`CompanionApi.kt:54`）。文档要改。

### 1.4 角色目录现在是同一份（这点比 `53ab9ef6` 写的时候好）

`53ab9ef6` 的提交信息说「两个入口并存会让用户看到三套角色（对话 14 个、陪伴 2 个、角色对话写死 2 个）」。现在：

- APK 打包目录：283 条、18 个分组（`feature-chat/src/main/assets/personas/agency-agents-import.json`）。
- Java 服务端 `vvv-roles/src/main/resources/roles/catalog-zh.json`：283 条，**与 APK 打包的 id 集合完全相同**（我逐 id 比对过），且文件 md5 与 Python 侧 `agent/roles/catalog/catalog-zh.json` **相同**（`7b6fdca4613987f69a0ef9bd6202dd70`）。
- Python 侧 `agent/roles/catalog/`：同一份 283 条；另有 2 张陪伴角色卡 `linche`、`sudai`（`bootstrap/platform.py:87` `BUILTIN_PERSONA_IDS`）。

所以「三套角色」实际收敛成：**283 条目录（一处数据、三处副本）+ 2 个陪伴角色**。剩下的不一致在**客户端选择器**：`ChatRoute` 用 `RolePickerSheet` 走 283 条；`AgentChatEntry` 把角色**写死成 2 个**（`AgentChatEntry.kt:41` `DefaultAgentRoles`），既没调 `/v1/roles` 也没调 `/v1/characters`（全文件只有 `api.createSession` 一处网络调用，`AgentChatEntry.kt:97`）。

---

## 2. 能力对比（逐项，带代码依据）

图例：**有** / **部分** / **没有**。
「/v1 客户端」= `app/src/main/java/com/openapi/ks/myapp/agent/`；「/v1 服务端」= companion-agent 的 `/v1`。

| 能力 | chat（`/chat/stream.json`） | /v1 客户端 | /v1 服务端 | 备注 |
|---|---|---|---|---|
| 图片选择 | 有 `ChatRoute.kt:97-99` | **没有** | 有 `routers/uploads.py:16` | 服务端只收图片 |
| 文件选择 | 有 `ChatRoute.kt:81,126` | **没有** | **没有** | 上传策略只认 jpeg/png/webp/heic（`service/uploads/policy.py:11-16`） |
| 相机拍照 | 有 `ChatRoute.kt:84,100-125` | **没有** | — | chat 还接了运行时权限 |
| 图片编辑（cropper） | 有 `ChatRoute.kt:249` `PhotoEditor` | **没有** | — | |
| 语音录制 | 有 `VoiceActions.kt:38` | **没有** | — | 录音在 `:speech` 模块，可复用 |
| 语音转写（ASR） | 有，两路：录音中实时（`VoiceActions.kt:49-67`，火山流式）+ 结束后端识别（`VoiceActions.kt:96` → `BackendSpeechToText.kt:37` 打 `/chat/ai/asr.json`） | **没有** | **没有 HTTP 接口** | 这是最大的硬缺口 |
| 「转文字」编辑态 | 有 `VoiceActions.kt:86-139` | **没有** | — | 可发原语音 / 发文字 / 取消 |
| TTS 朗读 | 有 `SendActions.kt:144-161`：先设备 TTS（`:148`），失败回退服务端合成（`:153` → `/chat/ai/tts.json`） | **没有** | **没有 HTTP 接口** | 同上 |
| 重新生成 | 有 `ResendActions.kt:40`，菜单里只对最新助手开放（`ChatMenuTargets.kt:114`） | **有** `AgentChatViewModel.kt:121`，且是「多候选并存 + 左右滑动切换」`AgentChatScreen.kt:198-204,236-240` | 有 `chat.py:337` | /v1 更强 |
| 「继续」（catch 被中断的回复） | **没有** | 有 `AgentChatViewModel.kt:117` | 有 `chat.py:345` | |
| 编辑用户消息 | 有 `ComposerActions.kt:18`（仅纯文字），提交后**自动重新回答** `ResendActions.kt:64` | **有** `AgentChatViewModel.kt:146`，**编辑后不自动重答**，需手动点重新生成 | 有 `chat.py:206` | 语义不同，迁移时注意 |
| 删除单条 | 有 `MessageRemoval.kt:29` | 有 `AgentChatViewModel.kt:137` | 有 `chat.py:250` | |
| 多选批量删 | 有 `ChatScreen.kt:221` `MessageSelectionBar` + `MessageRemoval.kt:43` | **没有** | 只能逐条 | |
| 清空会话 | 有 `MessageRemoval.kt:73`，**逐条删**（服务端有 `/chat/messages/clear.json` 但客户端没用，见 `testserverv-chat-api.md:48`） | **没有** | 有 `chat.py:281`（一次请求） | /v1 服务端更好，客户端没用 |
| 会话列表 | 有 `ChatSessionsSheet.kt:55` | **没有**（`AgentApi` 无 `listSessions`） | 有 `chat.py:120` | |
| 重命名会话 | 有 `SessionActions.kt:61` | **没有** | 有 `chat.py:132` | |
| 删除会话 | 有 `SessionActions.kt:49` | **没有** | 有 `chat.py:138` | |
| 新建会话 | 有 `SessionActions.kt:35` | 部分：只能「为当前角色开一段新对话」`AgentChatEntry.kt:122` | 有 `chat.py:126` | |
| 历史分页（往前翻） | 有 `ChatViewModel.kt:245` → `HistoryActions.kt:30` | **没有**：只拉最新 30 条，无 `before` 翻页（`AgentChatViewModel.kt:193`、`AgentApi.kt:25`） | 有 `before` 参数 `chat.py:193` | |
| 草稿 | 部分：文字 + 附件草稿托盘（`ChatUiState.kt:11,38`），仅内存，进程被杀即丢 | 部分：只有文字（`AgentChatViewModel.kt:47`），同样只内存 | — | 两边都不持久化 |
| 失败重试 | 有：失败气泡显示重试（`MessageFooter.kt:168`、`ChatMenuTargets.kt:102`、`ResendActions.kt:25`） | 有 `AgentChatScreen.kt:231-233` → `AgentChatViewModel.kt:89`，复用同一个 `client_message_id` | 有去重语义 `submit.py:65` | /v1 设计更严谨 |
| 停止生成 | 有 `ChatViewModel.kt:188`（同时停文字流与附件任务） | 有 `AgentChatScreen.kt:138-142` → `AgentChatViewModel.kt:95` | 有 `chat.py:184` | |
| 角色选择（283 目录） | 有 `RolePickerSheet.kt:159` + 收藏/最近（`RolePickerViewModel.kt:17-23`） | **写死 2 个** `AgentChatEntry.kt:41-46` | 有 `/v1/roles`、`/v1/characters` | 差最大的一项 |
| 收藏/最近使用（角色） | 有 `RolesRepositoryImpl.kt:41-50` | **没有** | — | |
| 消息点赞 | 有 `ChatViewModel.kt:279`，写本地收藏 `ChatMessageActions.kt:22`（`FavoriteType.CHAT_OK`） | **没有** | — | chat 的点赞只写本地，没有读取方 |
| 点踩反馈 | 有 `ChatMessageActions.kt:31` → `/chat/feedback` | **没有** | — | |
| Markdown 渲染 | 有 `MarkdownText.kt:24`（Markwon + LaTeX + 表格） | **没有**，纯 `Text`（`AgentChatScreen.kt:207`） | — | |
| 消息头像 | 有 `ChatScreen.kt:188-199` | **没有** | — | |
| 聊天气泡/输入栏组件化 | 有，`:chatkit`（3767 行） | **没有**，自绘极简 `OutlinedTextField`（`AgentChatScreen.kt:131-148`） | — | chatkit 是公共模块，可复用 |
| 断线续传 | **没有**（`ChatStreamClient` 无 `Last-Event-ID`） | 有：`AgentEventReconnector.kt:22`，指数退避 + `Last-Event-ID` + `gone` 重拉历史 | 有 | /v1 明显更强 |
| 排队 / 插话 / 合并 | **没有** | 有：`AgentChatState.kt:143-168`、`AgentChatScreen.kt:114-128` | 有 `chat.py:264,296,306` | /v1 独有 |
| 定时提醒 | **没有** | 部分：能列出/取消（`AgentChatViewModel.kt:155,162`） | 有 `routers/reminders.py` | |
| 工具确认卡片 | **没有** | **没有**（只是把事件塞进 `pendingConfirmations`，`AgentChatState.kt:37,182`；**界面从不渲染它**，`AgentApi` 也没有 confirm 调用） | 有 `routers/actions.py:20,29` | 半成品 |
| 跨会话记忆 | **没有**（chat 只按 sessionId 读最近 30 条历史） | 服务端有（事实/事件/画像三层，见 `docs/design/companion-agent-architecture.md` §7） | 有 | /v1 唯一的产品级差异 |
| 离线可看历史 | 有：Room `ChatLog` 本地缓存（`MessageCache.kt:33`） | 部分：Room 窗口 100 条（`AgentMessageCache.kt:19-50`） | — | 两边都有 |
| 附件在历史里重新渲染 | 有（本地缓存合并，`ChatRepositoryImpl.kt:69-71`） | **没有**：服务端历史会返回 `attachments`（`chat.py:65-67`），但客户端 `toBubble()` 完全忽略（`AgentApi.kt:138-147`） | 有 | |

### 2.1 一句话概括

- **chat 强在「输入侧」**：附件（含相机/编辑）、语音录制与转写、TTS、多选、会话管理、283 角色、Markdown。这些是**用户每天碰的**。
- **/v1 强在「对话内核」**：服务端权威状态、排队/插话/合并、多候选回复、断线续传、跨会话记忆、定时提醒。这些是**用户不容易察觉但决定"像不像一个记得你的人"**的。
- 两边都不完整，而且缺的部分**正好互补**——这是「不能直接换」的直接原因。

---

## 3. 切换成本

### 3.1 能直接复用的（换协议即可，不是重写）

| 资产 | 位置 | 可复用性 |
|---|---|---|
| 整个聊天 UI 组件库 | `:chatkit`（3767 行） | 已同时被 chat 与陪伴使用；agent 页完全可以改成用它组装 |
| 录音 + 音频播放 | `:speech`（`VoiceRecorder`、`AudioPlayer`、`DeviceSpeech`、`VolcengineStreamingAsrClient`） | 与后端无关，直接可用 |
| 附件暂存/压缩 | `feature-chat/.../AttachmentStager.kt:33`（public object） | 直接可用 |
| Markdown | `feature-chat/.../MarkdownText.kt:24`（public fun） | app 已依赖 `feature-chat`，可直接调用 |
| 本地缓存表 | `AppDatabase` 的 `ChatLog` 表 | 可复用，但字段是老的 |
| 会话列表/角色选择器 | `ChatSessionsSheet.kt:55`、`RolePickerSheet.kt:95,159`（均 public） | UI 可复用，数据层要换 |

> 但注意这些散落在 `feature-chat` 里，且 `ChatUiState.assistantIdentity()`（`RolePickerSheet.kt:77`）是 `internal`。
> 如果 agent 页要复用，要么把公共件下沉到 `:chatkit`/`:common`，要么在 `app` 里重写一份——**这是工作量估算里最容易被低估的一块**。

### 3.2 必须先补的（按「是否阻塞对等替换」排序）

**P0 — 客户端零、服务端已就绪（改客户端即可）**

1. 会话管理：列表/重命名/删除/清空。服务端 `chat.py:120,132,138,281` 全有，客户端 `AgentApi.kt` 一条都没包。
2. 角色目录：`AgentChatEntry.kt:41` 的硬编码 2 个改成 `/v1/roles`（283 条 + 收藏/最近）+ `/v1/characters`（2 张陪伴卡）。**服务端数据与 chat 用的那份完全相同**，所以这是纯客户端工作。
3. 附件：客户端要接 `POST /v1/uploads`（裸字节，不是 multipart，`routers/uploads.py:20`）+ `POST /v1/sessions/{id}/messages` 的 `attachments[]`（`chat.py:79-92`）。服务端**只收图片**，`文件` 这一类要服务端补（`policy.py` 只认四种图片 MIME）。
4. 历史分页 + 渲染：`before` 参数服务端有，客户端没用；`attachments` 服务端返回了，客户端忽略。
5. 消息渲染换成 chatkit + Markdown + 头像 + 多选批量删。
6. 消息点赞/点踩：服务端**没有**对应接口，要么不做，要么新增。
7. 工具确认 UI：状态已经在 reduce 里了（`AgentChatState.kt:182-189`），但没有任何渲染，`AgentApi` 也没有 `POST /v1/actions/{id}/confirm`（服务端有 `routers/actions.py:29`）。

**P1 — 服务端也没有，必须先做服务端（真正的阻塞项）**

8. **ASR**：`/v1` 没有任何转写路由（我 grep 过 `api/http/routers/*.py`，没有 asr/transcribe）。
9. **TTS**：同样没有。服务端的 TTS 只存在于 LiveKit 语音管线里（`api/livekit/agent.py:204-251`），不是 HTTP 接口。
10. **文件上传**：`policy.detect_type` 只认图片。

P1 的三条意味着：**只要「对话」Tab 还需要语音和朗读，就不能只是换 baseUrl**。可选路径：
- (a) 客户端保留调用 Java 的 `/chat/ai/asr.json`、`/chat/ai/tts.json`，对话走 `/v1`——两条链路混在一个页面里，登录态和错误口径都不同（chat 用 UA/Token 头，/v1 用 Bearer 读 Redis），排查会更难；
- (b) 在 companion-agent 补 ASR/TTS HTTP 接口，再迁——干净，但要动服务端且有密钥/供应商接入成本；
- (c) 先迁「无语音」的用法，语音在旧页面保留。

**我的建议是 (b)，并且在 (b) 完成前不做 Tab 切换。**

### 3.3 规模估算（只算要动的，不含 P1 服务端）

| 项 | 粗略规模 |
|---|---|
| `AgentApi` 补会话 CRUD + 分页 + 附件 + 确认 | ~150 行 |
| 角色选择器接 `/v1/roles`（含收藏/最近本地存储） | ~200 行（可大量参照 `RolePickerViewModel`） |
| `AgentChatScreen` 换 chatkit 组装 + 附件草稿托盘 | ~250 行（主要是接线） |
| 附件发送适配（上传 → 拿 upload_id → 随消息提交） | ~120 行 |
| 多选批量删 | ~60 行（chatkit 已提供 `MessageSelectionState`） |
| 历史分页 + 附件渲染 | ~80 行 |
| 公共件下沉（Markdown/AttachmentStager/头像） | ~半天，纯搬迁 |
| **合计** | **约 900–1100 行新代码 + 一次模块整理** |

这不小，但**大部分是「接线」而不是「发明」**：服务端能力齐、chatkit 齐、录音齐。真正的未知数只有 P1。

---

## 4. 两套并存的具体风险

### 4.1 同一个用户，两段互不可见的历史——而且 `/v1` 那边已经在写了

- chat 的历史在 Java 库（`/chat/messages.json`，见 `testserverv-chat-api.md:45`）。
- `/v1` 的历史在 Python Postgres（`ca_` 表，`companion-agent-architecture.md` §0）。
- **关键**：语音房间会绑定到 `/v1` 的 chat session —— `service/room/resolve.py:23` `resolve_chat_session()` 明确写着「一个用户和一个角色只有一段对话历史，语音和文字都写进它」，没有 `chat_session_id` 时拒绝入场（`api/livekit/entrypoint.py:255-258`）。
- 也就是说：用户在「陪伴」里说的话，**已经落在 `/v1` 的历史里**，而「对话」Tab 完全看不到；反过来 chat 里的历史，「陪伴」和未来的 agent 页也看不到。
- 更糟的是：**陪伴页的 UI 根本不读历史**（`feature-companion` 里没有任何 history 调用），关掉重开就是空的。所以这段历史现在**写了但没人看得见**——这既是缺陷，也说明迁移那天用户不会觉得"记录回来了"，只会觉得"又是空的"。

### 4.2 角色选择三处不同源

- chat：283 条目录 + 旧自定义人设「二选一」互斥（`ChatViewModel.kt:133` 注释、`SessionRole.resolve`）。
- 陪伴：2 张陪伴卡（`/v1/characters`）。
- agent 页：写死 2 个（`AgentChatEntry.kt:41`），与上面两者都不完全对应。
- 服务端其实是**同一个解析器**：`u_` 前缀 → 283 目录 → 2 张卡（`companion-agent-architecture.md` §2.1）。所以**数据是统一的，分歧全在客户端**——这也意味着 4.2 是可以用客户端工作消掉的。

### 4.3 两份实现，两套状态机

- chat：命令式 `ReplyStreamer`（237 行）+ 8 个 Action 类，流式字符靠 40ms 节拍（`ReplyStreamer.kt:34`）。
- /v1：纯函数 `reduce`（`AgentChatState.kt:84-194`）+ 事件重连器。
- 两者的失败语义、重试语义、编辑语义**都不一样**（§2 表里已标）。同一个 UI bug（例如"发送失败没有提示"）要修两遍；`3798c1d8` 修 chat 的错误提示条时，agent 页的同类问题（发送失败只把气泡变红、不显示原因，`AgentChatViewModel.kt:187`）并没有跟着修。

### 4.4 能力持续漂移

`/v1` 加了排队/插话/多候选/提醒；chat 加了附件/语音/TTS/多选。**每加一个新功能，另一边的落差就大一分**，而两边都在"在用"状态，谁都不能停。

### 4.5 运维面变大

- 三套部署：Java `innerip:8888`、Python `104.160.191.87:8081`、LiveKit worker。
- 而且 chat 自己就横跨两个 upstream（`chat_server` = `innerip:8888`，`chat_server1` = `104.160.191.87:8888`，见 `chat-companion-status-and-plan.md` §1）。
- 两套鉴权：chat 走 `BaseRequest` 的 UA/Token 头（`BaseRequest.java:109`），`/v1` 走 Bearer 且服务端查 Redis（`redis_token.py:28-50`）。**token 过期时两边表现不同**——这正是"chat 发消息失败但不知道为什么"的土壤。

### 4.6 服务端角色接口仍是坏的

`/vvv/api/roles/v1/divisions` 现在仍然 400（§1.3 实测）。这意味着 chat 的角色选择器**一直在用 APK 里的离线兜底**（`RolesRepositoryImpl.kt:48-59`，`1940141d` 之后会打日志了）。在修好之前，「对话」页的角色能力实际上是不可靠的；而 `/v1/roles` 是好的。这一点对「该往哪收敛」的判断是有利的：**/v1 这边角色链路更健康**。

---

## 5. 建议

### 5.1 明确建议：**先补能力再迁；迁移分四步，不合并成一次上线**

**第 0 步（立刻，成本最低，收益最大）：把 `/v1` 的入口放回来。**

理由：现在没有任何真人能用手指到达 `/v1`，所以「/v1 好不好用」这件事**无法被验证**。所有关于迁移的判断都只能靠读代码——这已经是这一轮错误的来源。放回抽屉里的一个入口（或「对话」页顶栏一个图标），让 `/v1` 进入真机可测状态，再谈要不要把主 Tab 换过去。

同时需要（都只有几行）：给 `AgentChatEntry` 接上 `onMenuClick`（现在 HomeScreen 调的是无参 `AgentChatEntry()`，`HomeScreen.kt:217`，顶栏没有抽屉按钮）、并修掉 `deployment-plan.md`/`full-test-plan.md` 里过期的「未部署」描述。

**第 1 步：补客户端骨架（§3.2 P0 的 1–7）。**
会话管理、角色目录、附件、分页、chatkit 组装、多选、确认卡片。这一批**不碰服务端**，可以并行、可以独立验收。

**第 2 步：单独立项解决语音（§3.2 P1 的 8–10）。**
优先做 (b)：在 companion-agent 补 `/v1/ai/asr`、`/v1/ai/tts`，把 Java 的两个接口语义搬过去。这一条不完成，就不能说「对话 Tab 可以换了」。

**第 3 步：按开关灰度切，旧历史只读保留。**
`ChatRepository` 与 `AgentChatRepository` 的接口边界（`AgentChatRepository.kt:7` 注释里已经预留了"两者并存，由 ChatDependencies 选择"）适合做成运行期开关。切换后 Java 侧的历史**不要删**，给一个只读入口或导出——否则用户会认为记录丢了。

### 5.2 判断依据（为什么不是「现在就迁」也不是「短期不迁」）

- **不是「现在就迁」**：§2 的对比里，chat 有 6 项 `/v1` 客户端完全没有的日常能力（附件、相机、语音、TTS、多选、会话管理），其中语音/TTS 连服务端都没有。硬切等于**删功能**。这正是上一轮被删按钮的同类错误，只是规模更大。
- **不是「短期不迁」**：§4.1 说明 `/v1` 已经在接收真实用户的历史（经陪伴页）。拖得越久，"两段历史"的用户就越多，将来切换时的落差越大。而且 `/v1` 的角色链路是健康的、chat 的是坏的（§4.6）。
- **所以是「补齐后迁」**，并且用「第 0 步先把入口放回来」把这个决定**变成可验证的**，而不是又一次基于读代码的推断。

### 5.3 如果必须给一个时间判断

第 0、1 步是纯客户端、可独立交付，不具备"必须等"的性质；第 2 步依赖服务端排期。**在 ASR/TTS 有了 `/v1` 接口之前，不要切主 Tab。** 在此之前，`/v1` 应该作为一个**并存入口**存在（就像现在的陪伴 Tab 一样），而不是一个死枚举。

---

## 6. 未验证 / 待确认（不猜）

1. **陪伴页在真机上是否真的连得上**。`/v1/voice/sessions` 路由存在（405 实测证明），但它依赖 LiveKit 服务与 Redis 登录态；我没有真机、也没有可用 token，**未验证**。
2. **`/v1` 的 SSE 是否真的在线上跑通**。`deployment-plan.md` §9.7 自己写着「SSE `/sessions/{id}/events` 未单独抓包验证」。我也没有 token，**未验证**。
3. **线上 Python 服务的版本是否等于本地仓库 tip**。`/v1/uploads` 返回 405 说明 uploads 路由已挂载，但 `chat.py` 里 `attachments`、`clear_history` 这些较新的接口是否已部署，**待确认**。
4. **`/v1` 是否已启用记忆抽取**（`after_turn` 后台任务）。这决定"记忆"这一优势是否真实存在，需要一次带 token 的端到端调用，**未验证**。
5. **用户量级**：是否已有真实用户在陪伴页产生历史，**待确认**（影响 4.1 的紧迫度）。
6. **`/vvv/api/roles` 的 `-parameters` 修复**是否已排期。这决定 chat 的角色能力还要"坏"多久（`chat-companion-status-and-plan.md` T1）。

---

## 附录：本文引用清单

**Android — feature-chat（chat 链路）**
`presentation/ui/ChatRoute.kt:58,81,84,97,100,126,228,249`　`presentation/ui/ChatScreen.kt:165,178,188,221,235,260,272`
`presentation/ChatViewModel.kt:127,129,133,188,214,217,227,234,241,245,279,320`　`presentation/ChatUiState.kt:11,38,47`
`presentation/ReplyStreamer.kt:34,57`　`presentation/ResendActions.kt:25,40,64`　`presentation/MessageRemoval.kt:29,43,73`
`presentation/SessionActions.kt:24,35,49,61`　`presentation/ComposerActions.kt:18,33`　`presentation/HistoryActions.kt:30`
`presentation/VoiceActions.kt:38,49,86,96,108,117,145`　`presentation/AttachmentSender.kt:39,63`
`presentation/SendActions.kt:84,96,144,148,153`　`presentation/AttachmentStager.kt:33`
`presentation/ui/MarkdownText.kt:24`　`presentation/ui/ChatSessionsSheet.kt:55`　`presentation/ui/RolePickerSheet.kt:77,95,159`
`presentation/ui/ChatItemMapper.kt:19`　`roles/presentation/RolePickerViewModel.kt:17-23`　`roles/data/RolesRepositoryImpl.kt:41-59`
`data/ChatMessageActions.kt:22,31`　`data/remote/ChatRepositoryImpl.kt:58,69,130,140,162,165,168,170`
`data/remote/ChatStreamClient.kt:122-186`　`data/remote/AttachmentUploader.kt:26,41,48`　`data/remote/BackendSpeechToText.kt:37`
`data/remote/MessageCache.kt:33`　`data/remote/SessionStore.kt:17`　`domain/repository/ChatRepository.kt:22`

**Android — chatkit**
`message/ChatMessageList.kt:88,175`　`message/ChatMenuTargets.kt:83,102,114`　`message/MessageFooter.kt:21,168`
`message/MessageSelection.kt:37`　`model/ChatItem.kt:66`　`model/ComposerModels.kt:37`

**Android — /v1 客户端（app/.../agent）**
`AgentApi.kt:14,25,112,116,119,138,169`　`AgentChatRepository.kt:7`　`AgentChatState.kt:37,84,143,182`
`AgentChatViewModel.kt:47,79,89,95,113,117,121,137,146,155,162,187,193`　`AgentChatScreen.kt:82,114,131,138,198,208,223,231,241`
`AgentChatEntry.kt:41,52,97,122`　`AgentChatRoute.kt:46`　`AgentEventReconnector.kt:22`　`AgentMessageCache.kt:19`
`SseFrames.kt:72,104,108`　`AgentModels.kt:113`

**Android — 其它**
`app/.../home/presentation/HomeScreen.kt:82-91,99,146,167-179,212-219`　`app/.../home/HomeDependencies.kt:14`
`feature-companion/.../CompanionRoute.kt:56,67`　`feature-companion/.../CompanionApi.kt:54`　`feature-companion/.../LiveKitCompanionTransport.kt:41`
`common/.../net/request/BaseRequest.java:109`　`core/.../config/Constants.java:188,199,200,201,255,268`

**服务端 — companion-agent（`/v1`）**
`src/companion/api/http/routers/chat.py:79,87,120,126,132,138,146,172,184,193,206,250,264,281,337,345,353`
`src/companion/api/http/routers/uploads.py:16,28`　`src/companion/service/uploads/policy.py:11-16`
`src/companion/api/http/routers/roles.py:39,45`　`src/companion/api/http/routers/characters.py:195,197`
`src/companion/api/http/routers/actions.py:20,29`　`src/companion/api/http/routers/reminders.py:23,28`
`src/companion/api/livekit/sessions_router.py:50`　`src/companion/api/livekit/entrypoint.py:255`　`src/companion/api/livekit/agent.py:204`
`src/companion/service/room/resolve.py:23`　`src/companion/service/auth/redis_token.py:21,28`　`src/companion/bootstrap/platform.py:87,230`
`agent/roles/catalog/catalog-zh.json`（283 条，md5 `7b6fdca4613987f69a0ef9bd6202dd70`）

**仓库内文档（部分结论已过期，见 §1.3）**
`docs/design/testserverv-chat-api.md:13-21,37-56,113`　`docs/design/companion-agent-architecture.md` §0,§2.1,§7
`docs/design/deployment-plan.md` §9.5–§9.8　`docs/design/full-test-plan.md:24-25`　`docs/design/chat-companion-status-and-plan.md` §1,T1
`app/src/main/java/com/openapi/ks/myapp/agent/ui/AGENT_CHAT_NAVIGATION.md`
