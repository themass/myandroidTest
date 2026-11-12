# testserverV 的 chat 相关接口

日期：2026-11-12　来源：`testserverV` 分支 `testjava_springboot`（含提交 `cd4cf4a4`）的控制器源码
范围：对话、会话、历史、角色、识图、语音、拍照解题、LiveKit。**只读源码整理，没有逐个调用验证。**

通用约定
- 网关：`https://api.ok123find.top/vvv/api` + 下表路径（nginx 去掉 `/vvv` 前缀后转发）。
- 响应封装：`JsonResult`，`data` 是 AES-ECB 加密的 hex，密钥取自 UA 最后 16 位（见部署方案）。**例外**：`/chat/stream.json` 是 SSE，帧内是明文 JSON。
- 登录：`@UserInfo(required = true)` 才强制登录；只写 `@UserInfo` 的**默认不强制**（注解默认值 `required=false`），是否被拦截器拦下没有验证。

---

## 1. 对话

| 接口 | 方法 | 作用 | 参数 | 客户端 |
|---|---|---|---|---|
| `/chat/stream.json` | POST | **流式对话**（SSE） | `ChatContentForm` + `PageBaseForm` | 在用 |
| `/chat/nostream.json` | POST | 非流式对话 | 同上 | 常量在，无人引用 |
| `/chat/transword.json` | POST | 翻译（复用非流式） | 同上 | 常量在，无人引用 |

`ChatContentForm` 字段：`text`、`id`（客户端消息 id，重发去重用）、`settingName`、`sessionId`（0 = 新建会话）、`regenerate`、`characterId`（旧自定义人设）、`roleId`（角色目录里的角色）。

SSE 每帧是 `StreamChunk`：`id`、`sessionId`、`delta`、`done`、`error`、`userMessageId`、`assistantMessageId`。

**转发到哪个模型**
- 流式：只在“支持流式”的三个处理器里**随机**选一个：Devcto、DevctoDeepSeek、Deepseek。选中后不重试，失败走 `completeWithError`。
- 非流式：随机数选处理器，失败重试 3 次。另有 Qwen、Doubao、Gemini、MyGpt4、Kimi 处理器，它们没接流式，只在非流式路径里出现。
- 所以客户端用的流式对话**同一个会话的不同回合可能由不同模型回答**，口吻和能力可能有差异。

**人设怎么定**（`cd4cf4a4` 之后）
- 人设属于会话。会话里已有 `roleId`/`characterId` 就一直用它，请求带的不同角色忽略并记 warn。
- 会话还没有任何消息时，才采用请求带的人设并写进会话。聊过的默认助手会话不会被改标成角色。
- 提示词：有 `roleId` 且目录里查得到 → 用角色正文 + 最近 30 条历史；查不到记 error 并回退旧人设（`characterId`）→ 再回退默认。

---

## 2. 会话与历史（都按 `sessionId` + `userId` 校验归属）

| 接口 | 方法 | 作用 | 参数 |
|---|---|---|---|
| `/chat/sessions.json` | GET/POST | 会话列表，按更新时间倒序；没有会话时自动建一个 | — |
| `/chat/session/create.json` | POST | 新建会话 | `name?`、`characterId?`、`roleId?` |
| `/chat/session/rename.json` | POST | 重命名 | `id`、`name` |
| `/chat/session/delete.json` | POST | 删除（软删） | `id` |
| `/chat/messages.json` | GET/POST | 分页历史（向前翻页） | `sessionId`、`beforeId?`、`limit=30` |
| `/chat/message/delete.json` | POST | 删一条 | `sessionId`、`id` |
| `/chat/message/edit.json` | POST | 编辑一条 | `sessionId`、`id`、`text` |
| `/chat/messages/clear.json` | POST | 清空**这一个会话**的历史 | `sessionId` |

会话列表项 `ChatSessionVo`：`id`、`name`、`characterId`、`roleId`、`createdAt`、`updatedAt`、`lastMessage`（截 40 字）、`messageCount`。

- 清空、删除、编辑只作用于传入的 `sessionId`，其他会话不受影响。
- 历史有 Redis 缓存，键 `chat:{userId}:{sessionId}`，写操作会使它失效。
- 会话默认名“新对话”；第一条消息发出后改成消息前若干字。

---

## 3. 自定义人设（旧的“角色”）

挂在 `/user` 下：

| 接口 | 方法 | 作用 |
|---|---|---|
| `/user/setting/character.json` | GET | 我的人设 + 系统人设列表 |
| `/user/setting/character/create.json` | POST | 新建自定义人设 |
| `/user/setting/character/update.json` | POST | 更新（只能改自己的） |
| `/user/setting/character/delete.json` | POST | 删除（只能删自己的） |

---

## 4. 角色目录（vvv-roles，公开只读）

| 接口 | 方法 | 作用 |
|---|---|---|
| `/roles/v1/divisions` | GET | 分类 |
| `/roles/v1/roles` | GET | 角色列表（分类 / 关键词 / 精选 / 分页） |
| `/roles/v1/roles/{id}` | GET | 单个角色 |

- 路径**故意没有 `.json` 后缀**，避开旧拦截器；公开，不要求登录。
- 由 `roles.enabled` 控制，默认开。关掉后对话里带 `roleId` 会回退旧人设。

---

## 5. 识图、语音、附件（`/chat/ai/*`）

| 接口 | 作用 | 备注 |
|---|---|---|
| `/chat/ai/recognize.json` | 发图识别（multipart `image`） | 经 `VisionContext` 选供应商；结果会做 LaTeX 括号替换 |
| `/chat/ai/asr.json` | 语音转文字 | `AsrContext` |
| `/chat/ai/tts.json` | 文字转语音（朗读） | `TtsContext` |
| `/chat/ai/feedback.json` | 点踩反馈 | **只打日志，不入库** |
| `/chat/ai/file.json`、`/chat/ai/local/file.json` | 上传文件 | 存图后返回占位消息 |
| `/chat/ai/local/ocr.json` | 本地 OCR 图片 | 常量在，客户端无人引用 |

这一组控制器上全部是 `@UserInfo`（不强制）。

## 6. 拍照解题

| 接口 | 作用 |
|---|---|
| `/photosolve/v1/solve.json` | 解题（multipart），强制登录 |
| `/photosolve/v1/quota.json` | 剩余额度 |
| `/photosolve/v1/health.json` | 就绪检查（**不可信**：占位符也报 configured=true，见部署方案） |

## 7. LiveKit 房间令牌（Java 侧，旧）

| 接口 | 说明 |
|---|---|
| `/live/room/create.json` | 建房 |
| `/live/room/token.json`、`/token_new.json` | 签令牌 |

- `token_new.json` 里 `name`/`identity`/`metadata` 是**字面量**（`"name"`、`"identity"`、`"metadata"`），所有用户同一身份；并且 `System.out.println` 打印了 JWT。
- 客户端现在**不调用**它，陪伴的令牌由 companion-agent 的 `POST /companion/sessions` 签发。**这一块没改，建议清理或下线。**

## 8. 不应该在生产暴露的

`/api/test/*`（`test.json`、`testmychat.json`、`testasr.json`、`testtts.json`）：测试控制器。是否在生产被拦我没有查到配置，部署时确认。

---

## 9. 客户端实际在用的（核对过引用）

对话流式、会话列表/新建/重命名/删除、历史、消息删除/编辑、人设增删改查、识图、语音转写、拍照解题、版本更新。
**常量在但无人引用**：`nostream`、`transword`、`local/ocr`、`live/room/token_new`。

## 10. 已知问题 / 没验证的

1. 流式处理器随机选择，同一会话回答模型会变。
2. 识图 / 语音 / 朗读不强制登录（注解层面），运行时是否被拦截器拦下未验证。
3. `feedback.json` 不落库，点踩没有后续。
4. `token_new.json` 身份字面量 + 打印 JWT。
5. 本文件没有逐个调用接口，所有行为来自读源码。
