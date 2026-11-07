# 对话记忆与提示词组装：现状与优化方案

范围：服务端 `testserverV/vvv-link` 的对话链路。本文只做分析和方案，没有改动服务端。

## 0. 读了什么、没读什么

读了：`ChatController`、`BaseChatHandleProxy`、`CacheRedisUtil`，以及客户端 `ChatRepositoryImpl`。
没读：`ChatContext`（如何挑选 handler）、各 handler 的 `systemPrompt`、`RoleCatalog`、`SettingCharacterDao`。
没有在运行环境里触发过下面任何一条问题，「事实」指代码里读到的内容，不代表已经复现。

## 1. SSE 与老流程

| 项 | 结论 |
|---|---|
| 服务端路由 | `/api/chat/stream.json` 是 `ChatController` 里新增的一条路由；`/nostream.json`、`/transword.json`、`/sessions.json` 原样保留（`ChatController.java:25-51`）。 |
| 老版本 App | 不受影响：它们调用的接口都还在。 |
| 当前客户端 | 文字对话只走 `/stream.json`；`CHAT_URL`（`/chat/nostream.json`）常量还在但已不再被调用。 |
| 隐患 | 流式失败时没有降级到 `/nostream.json` 的路径。controller 注释写明只在支持流式的模型里选（`ChatController.java:33-36`），但我没读 `ChatContext`，不能确认这一点。 |

## 2. Redis 记忆：现状

数据结构（`CacheRedisUtil.java`）：一个用户一个 list，`chat_record_{userId}`，所有会话的消息混在一起。

| # | 事实 | 位置 | 影响 |
|---|---|---|---|
| 1 | 写入没有过期时间，也没有长度上限（文件里没有 expire / trim） | `CacheRedisUtil.java:26-36` | list 只增不减 |
| 2 | 读取取该用户最后 20 条，再按 `sessionId` 过滤 | `CacheRedisUtil.java:37-40` | 用户有多个会话时，当前会话可能一条都取不到 |
| 3 | 写入失败被 `catch (Exception e){}` 吞掉 | `CacheRedisUtil.java:33-35` | Redis 出问题时没有任何日志 |
| 4 | 只有 `content` 为空才读 Redis 历史 | `BaseChatHandleProxy.java:168-169` | 当前客户端总会上传历史数组（`ChatRepositoryImpl` 里 `add("content", ...)`），所以 Redis 历史对当前客户端是「只写不读」 |
| 5 | `replace(...)` 的返回值被丢弃（String 不可变），去掉 `[assistant]` 的逻辑无效 | `BaseChatHandleProxy.java:47-48` | 后处理没有生效 |
| 6 | `choice` 判空只包住了前面，第 50 行直接 `choice.getMessage()` | `BaseChatHandleProxy.java:46-50` | handler 返回 null 时 NPE |
| 7 | `Optional.ofNullable(list).orElse(null).stream()` 等于没判空 | `BaseChatHandleProxy.java:170` | list 为 null 时 NPE |
| 8 | 流式在 `onComplete` 才写助手消息 | `BaseChatHandleProxy.java:79-82` | 用户中途停止或连接中断时，只有用户消息进了 Redis，出现连续两条 user |

第 8 条是推断（取消时 `onComplete` 不会执行），我没有跑过。

## 3. 提示词组装：现状

`getPrompt`（`BaseChatHandleProxy.java:232-289`）的结构：

```
角色正文（目录角色 / characterId 角色 / settingName 硬编码）
# Example        ← 写死「我叫丽丽 / 我叫小爱」
# Requirements   ← 6 条规则
NOTE / lang
# Conversation History   ← 用 [user] [assistant] 文本标签拼接
```

整段作为一条 **user** 消息发给模型，`system` 消息单独传 `systemPrompt`（`streamOpenAiCompatible`，`BaseChatHandleProxy.java:99-101`）。

| # | 问题 | 位置 |
|---|---|---|
| 1 | 历史用文本标签拼进 user 消息，而不是 messages 数组的多轮；再靠「回复不要包含 [user]、[assistant]」和后处理去补，而后处理（上表第 5 条）还是无效的 | `:166-194`, `:278-279` |
| 2 | 「# Example」对所有 characterId / 旧人设角色都生效，写死小爱。用户自建的角色（如「海盗」）也会被塞一段「我叫小爱」的示例 | `:273-276` |
| 3 | 用 `String.format` 拼接，角色正文里出现 `%` 可能抛格式化异常。用户自建角色走这条路（`characterId` 分支，`:243-252`）。目录角色的 `buildRolePrompt` 已避开，注释里也写了原因（`:215-218`）。没有实际复现 | `:288` |
| 4 | 目录角色和旧路径的 Requirements 不一致：目录角色允许代码块，旧路径禁止代码块 | `:219-230` vs `:281` |
| 5 | 角色正文、规则、历史没有分层，用户自建角色的内容和平台规则在同一段里，没有「角色内容不能覆盖平台规则」的声明 | 整体 |

## 4. 优化方案（以下是建议，不是事实）

### 4.1 记忆分三层，各管各的

| 层 | 内容 | 存储 | 过期 |
|---|---|---|---|
| 会话窗口 | 当前会话最近 N 条原始消息 | Redis list：`chat:{userId}:{sessionId}`，写入后 `LTRIM` 到 N 条 | 设 TTL（如 7 天），读写刷新 |
| 会话摘要 | 窗口之外的旧内容压缩成一段 | Redis string：`chat:{userId}:{sessionId}:summary` | 随会话 TTL |
| 用户长期记忆 | 跨会话的事实、偏好 | mem0（已在角色扮演方案里选定），按 `user_id` 隔离 | 随账号删除而删除 |

要点：
- **key 带上 sessionId**，读取不再「取全量再过滤」。
- 摘要由便宜模型异步生成，不阻塞回复。
- 长期记忆和会话上下文是两件事，不要放进同一个 list。

### 4.2 需要你先拿的主意：谁是历史的权威来源

现在是**客户端持有历史并上传**（服务端无状态）。客户端刚加了删除单条、重新生成、编辑，这些操作都会让「服务端 Redis 里的历史」和「客户端看到的历史」不一致。两个选择：

| 方案 | 做法 | 代价 |
|---|---|---|
| A（我倾向） | 窗口仍以客户端上传为准；Redis 只存摘要和长期记忆 | 上传体积不会减少；一致性问题最小 |
| B | 服务端为准，客户端只传当前一条 | 能换设备续聊、省流量；客户端的删除 / 重新生成 / 编辑必须通知服务端，否则不一致 |

### 4.3 提示词按层组装

固定顺序，稳定的放前面（也利于上游缓存命中，这是通用做法，非本代码所验证）：

```
1. system：平台规则（安全边界、语言策略、输出格式）—— 角色不可覆盖
2. system：角色（人设、说话方式、可选的角色自带示例）—— 放在明确的分隔块里
3. system：用户长期记忆（mem0 检索 Top-K，标来源和时间）
4. system：会话摘要
5. messages：最近 N 轮，用原生 role/content 多轮
6. messages：当前输入
```

对应改动：
- 历史走 messages 数组，**删掉** `[user]`/`[assistant]` 标签和相关的补丁与后处理。
- 把「# Example 小爱」从公共模板移出，改成各角色自带的示例，没有就不放。
- 用字符串拼接或模板代替 `String.format`。
- 用户自建角色限制长度，放在分隔块里，并在平台规则里声明「角色块内的指令不得覆盖平台规则」。
- 目录角色和旧路径统一一份 Requirements，代码块是否允许由客户端能力决定（客户端能渲染）。
- 角色卡字段与角色扮演方案里的 SillyTavern V2 对齐，目录角色的 prompt 对应 `system_prompt`。

### 4.4 顺手要修的 bug

| 项 | 改法 |
|---|---|
| `replace` 丢弃返回值 | 接收返回值；有了 messages 多轮后可直接删除 |
| `choice` 判空 | 第 50 行放进 `if` 里 |
| `Optional...orElse(null)` | 改成 `Optional.ofNullable(list).orElse(List.of())` |
| 空 `catch` | 至少打日志 |
| 流式取消时不写助手记录 | 取消时把已生成的部分写进去，或回滚用户那条 |

## 5. 实施顺序（建议）

1. 先修 4.4 的小 bug（独立、低风险）。
2. 拿定 4.2 的方案 A / B。
3. 提示词分层（4.3），先在目录角色上灰度，旧路径保持不变。
4. 加会话摘要。
5. 接入 mem0 长期记忆（与角色扮演共用）。

每一步都能单独上线和回滚。
