# 角色扮演（Her）架构设计 v3

状态：**待确认项已大部分确认**，剩余事项见第 13 节。本版取代 v2。

## 1. 已确认的决定

| # | 决定 |
|---|---|
| 1 | 对话链路全新设计，不复用旧 `/chat/stream.json` 与 nage/chat 接口 |
| 2 | 记忆建在 agent 层；agent 支持记忆与个人重要信息提取 |
| 3 | 渲染器接口抽象；2D 人物支持手指拖动（重点）|
| 4 | **DeerFlow 是对话主脑**（接管 LLM 节点），不再是旁路的内容工具；语音（TTS + LiveKit）由外部中间层负责，**语音供应商走策略模式，可随时替换**，第一版接火山引擎 |
| 5 | 上架 Google Play |
| 6 | 第一版文字与语音都有，**默认文字**，用户可切换到语音 |
| 7 | 角色格式：SillyTavern V2，支持 V2/V3 导入导出 |
| 8 | 记忆范围：用户维度；**允许跨角色读取**，每条记忆标注来源角色 |
| 9 | 用户创建角色的形象和音色：留扩展点，第一版不做 |
| 10 | **账号删除时删除全部数据，包括全部记忆**（取代 v2 的「记忆不删」）。日常记忆不做自动过期 |
| 11 | 官方角色由我们设计 2–3 个 |
| 12 | 端上 2D 人物 + agent（LiveKit + 中间层 TTS + DeerFlow 主脑 + memory）|
| 13 | LiveKit 使用 **LiveKit Cloud**，不自建 |
| 14 | 第一版形象做**简单动画**（静态立绘 + 表情图 + 嘴型两帧 + 眨眼），**不做跟随与拖动互动**；但上层通过 `AvatarCommand` 指令框架驱动，渲染器可替换（见第 5.1 节） |
| 15 | 角色扮演后端是**独立的 companion 服务**（Python，LiveKit Agents），从 job-come 后端拷贝改造，不与 job-come 共用部署；登录与账号接口复用 App 现有体系 |
| 16 | **文字走 LiveKit 房间**（文本流），与语音共用同一个房间；必须流式输出 |
| 17 | 对话 LLM **可配置、可切换**：沿用 job-come 的 LiteLLM 路由机制，按场景配置模型链；密钥只放环境变量，不写入文档或配置文件 |
| 18 | 不处理国内备案（部署地不一定在国内） |
| 19 | DeerFlow 以**嵌入式 harness** 方式运行在 companion 服务内（沿用 job-come 的做法），不单独部署成 HTTP 服务 |

## 2. 总体架构

```
Android 客户端
  ├─ Compose：对话（文字为默认）、角色、我的
  ├─ AvatarRenderer（接口）
  │    ├─ 阶段 A：v1 简单动画渲染器（SimpleAvatarRenderer，见第 5.2 节）
  │    ├─ 阶段 B：P3b 候选：Spine / Live2D（授权待定）/ Rive（见第 5.4 节）
  │    └─ 全部通过 AvatarCommand 指令驱动（见第 5.1 节）
  ├─ LiveKit Android SDK：房间、麦克风发布、agent 音轨播放、数据通道
  └─ 本地：角色缓存、会话、「我记住的」

LiveKit Cloud（房间与实时传输）
        │
        ▼
companion 服务（独立部署，Python，LiveKit Agents，Apache-2.0；从 job-come 拷贝改造）
  ├─ 回合管理、打断、语音活动检测（框架自带）
  ├─ 提示词组装（skill 包 + 角色 + 用户记忆 + 历史）
  ├─ **LLM 节点 = DeerFlow**（嵌入式 harness，流式产出文本）
  │    · 多步推理、工具、子 agent、上下文压缩由 DeerFlow 负责
  │    · 记忆：检索（回合前）、提取（回合后，异步）→ MemoryPort
  └─ 语音：经 VoiceProvider 接口（STT / TTS）
        │
        ├──▶ Voice Gateway（中间层，策略模式）
        │       VoiceProvider 接口 → 火山引擎（第一版）/ 其它供应商（可替换）
        │       · 音色映射、流式、句级缓存、失败切换
        │
        ├──▶ Memory Service（MemoryPort 接口 → mem0，见第 4 节）
        │
        └──▶ MCP 服务（记忆、角色查询等需要模型主动调用的动作）

角色、记忆、会话与 token 签发 API（HTTPS，companion 服务）：角色 CRUD、导入导出、记忆查看与删除、创建会话、账号删除。登录与账号基础接口复用 App 现有服务。
```

要点：

- **文字和语音共用同一个 agent 和同一个房间**，保证同一角色的人设、记忆、情绪在两种模式下一致。
- 文字模式用 LiveKit 房间的文本流，不发音频，也不调用语音供应商。
- companion 服务独立部署；登录与账号复用 App 现有接口，角色扮演与 job-come 不共用代码或部署。
- 语音供应商、记忆后端都在接口后面，替换实现不改 agent 与客户端。

## 3. 角色

字段同 v2，以下为变更：

- `voice_ref` 不再直接指向火山的音色 id，而是 `{provider, voice_id}`，由 Voice Gateway 解析。第一版 `provider` 只有 `volcengine`。
- 角色卡格式：V2 字段为内部主格式；V3 的 `emotion` 资源映射到 `emotion_map`；V3 的其它新字段保留在 `extensions.vvv`，导出时原样带回。

## 4. 记忆

### 4.1 范围（决定 8、10）

- **主键是用户**：同一用户的所有角色共享用户记忆。
- **跨角色可读**：角色 A 可以读到用户在角色 B 中说过的内容。
- **来源标注**：每条记忆记录 `source_character_id`、`source_message_id`、时间。客户端「我记住的」页面显示每条记忆来自哪个角色。
- **删除**：
  - 用户可以删除单条记忆；
  - 用户删除账号时，**删除该用户全部记忆**（含向量索引、摘要、原始抽取记录）。这是 Google Play 的要求，也和决定 10 一致；
  - 日常不做自动过期。
- 不同用户之间完全隔离，网关层强制注入 `user_id`，不信任客户端传参。

### 4.2 agent 层流程

```
回合开始：retrieve(user_id, 当前话题) → Top-K 记忆 + 最近摘要 → 「关于用户」段
回合结束（异步）：extract(本回合) → 候选事实/偏好/重要事件 → 打分 → 去重（ADD/UPDATE/NOOP）→ 写入
```

- 提取标准与禁止清单（身份证号、银行卡、密码、精确住址、健康诊断等默认不存）见 v2，不变。
- 提取模型可与对话模型不同，用便宜模型。

### 4.3 记忆后端选型

重新核实后的候选（许可已查证）：

| 项目 | 许可 | 按用户隔离 | 个人事实提取 | 自托管 | 备注 |
|---|---|---|---|---|---|
| **mem0**（约 68.5k star） | Apache-2.0 | `user_id` 过滤 | ✅ 单轮抽取、ADD-only 积累 | ✅ Docker | 最普及，社区资料最多 |
| **Hindsight**（vectorize-io/hindsight） | **MIT** | 按 `bank_id` 分库，每用户一个 bank | ✅ `retain` 写入、`recall` 召回、`reflect` 生成综合画像；有论文与基准 | ✅ | Hermes 的记忆提供方之一（来自 hermes-webui 变更记录）；`reflect` 很适合「关于用户」段 |
| **Graphiti**（getzep/graphiti） | Apache-2.0 | `group_id`，**无内置鉴权** | ✅ 事件→实体与关系，带时间有效期 | ✅，需 Neo4j/FalkorDB | 需要时间线推理时再上（P5 候选） |
| **Honcho**（plastic-labs/honcho） | **AGPL-3.0** | 工作区 + 对等体（peer） | ✅ 派生对等体画像 | ✅ | **不采用**：AGPL 作为网络服务提供时有源码开放义务，与闭源上架冲突 |
| ReMe（agentscope-ai，阿里） | Apache-2.0 | 本地优先的文件型记忆 | ✅ | ✅ | 面向个人知识库，多用户服务端需自建 |
| TencentDB Agent Memory | MIT | 用户/团队 ACL | ✅ 分层 L0–L3 | ✅ | 分层思想可借鉴，生态偏 IDE 与团队 |
| Letta | Apache-2.0 | 每用户一个 agent | 文档未明确 | ✅ | 每用户一个长驻 agent，运维重，不推荐 |

**决定（v1）：采用 mem0**，经 `MemoryPort` 接入。理由：`user_id` 隔离、`metadata` 可记录 `source_character_id`、`delete_all(user_id)` 可做账号级全量删除，Apache-2.0，社区与资料最多，运维成本最低。

- 「关于用户」段由 agent 层自己生成（用 mem0 检索结果 + 便宜模型摘要），不依赖 Hindsight 的 `reflect`。
- Hindsight（MIT）保留为备选：`MemoryPort` 之后替换实现即可，不改 agent 与客户端。
- 上线前仍要跑一遍 50 段模拟对话，验证事实抽取、跨角色召回、删除零残留；这是验收，不再是两家的对比。

### 4.4 DeerFlow 与记忆

- **检索**：回合前由 companion 查记忆，注入 DeerFlow 的上下文；DeerFlow 自己不必知道记忆存储。
- **提取**：回合结束后异步触发，用 `memory_extract` 场景的便宜模型，与对话模型分开。
- DeerFlow 生成的内容（如它替用户写的故事）是内容，**不自动变成用户事实**。只有用户在对话中表达的信息进入记忆。

## 5. 2D 人物

### 5.1 指令框架（决定 14：先设计框架，实现可替换）

v1 的人物很简单，但**上层只通过「指令」驱动人物**。以后换 Spine / Live2D / Rive 时，只替换渲染器实现，agent 协议、事件映射、客户端状态机都不动。

```kotlin
// 上层唯一的输入：指令（与渲染技术无关）
sealed interface AvatarCommand {
    data class SetState(val state: AvatarState) : AvatarCommand   // idle | listening | thinking | speaking
    data class Emotion(val name: String) : AvatarCommand          // 如 "joy"，由 emotion_map 解析
    data class Mouth(val openness: Float) : AvatarCommand         // 0..1，来自音频振幅
    data class Gaze(val x: Float, val y: Float) : AvatarCommand   // v1 不实现
    data class Gesture(val id: String) : AvatarCommand            // v1 不实现
}

// 渲染器声明自己支持哪些能力；不支持的指令直接忽略，不报错
enum class AvatarCapability { STATE, EMOTION, MOUTH, GAZE, GESTURE, TOUCH_DRAG }

interface AvatarRenderer {
    val capabilities: Set<AvatarCapability>
    fun bind(view: AvatarView, spec: AvatarSpec)
    fun dispatch(command: AvatarCommand)          // 所有指令入口
    fun onTouch(event: AvatarTouch): TouchResult  // v1 一律返回 Ignored
    fun release()
}
```

- **事件映射**：agent 发来的 JSON 事件（5.5）只在一个地方（`AvatarEventMapper`）转成 `AvatarCommand`。协议不因渲染器而变。
- **能力协商**：`capabilities` 决定上层是否发 `Gaze`、`Gesture` 等指令。v1 只声明 `STATE`、`EMOTION`、`MOUTH`。
- **未知指令**：渲染器忽略，记日志，不崩溃。
- **资源规格**：`AvatarSpec` 只描述资源（图片或骨架文件、版本、情绪到资源的映射），不含业务逻辑。

### 5.2 v1 形象：简单动画（决定 14）

第一版目标是**把流程走通**，形象只做到能看、能表达状态，不做跟随互动。

- **实现**：`SimpleAvatarRenderer`，不依赖任何第三方运行时（无授权风险）。
- **素材**：每个角色一张静态立绘，加一组表情图（如 joy、calm、sad 各一张）和 2 张嘴型图（闭、开）。
- **动画**：眨眼（随机间隔 3–6 秒，用两张眼睛图切换）；呼吸（整体缩放 ±1%）。
- **指令处理**：`SetState` 切换呼吸快慢（speaking 时嘴巴动）；`Emotion` 切换表情图；`Mouth` 按振幅阈值在闭/开两张之间切换。
- **不做**：拖动、点击反应、视线、手势。`onTouch` 返回 `Ignored`。
- **后续替换**：P3b 再决定 Spine、Live2D（需书面授权）或 Rive。候选的对比见第 5.4 节。届时只新增一个渲染器实现，并把 `capabilities` 补齐。

### 5.3 后续互动（P3b，不在 v1）

以下能力在 P3b 通过新渲染器实现，不改 v1 的指令接口：

- **拖动跟随**：手指偏移映射到视线、头部、身体三层，权重 1.0 / 0.6 / 0.3，阻尼回中约 0.4 秒。对应 `Gaze` 与 `TOUCH_DRAG`。
- **点击部位**：命中区域（BoundingBox 或 HitArea）触发本地反应，并发 `touch` 事件给 agent。
- **手势与动作**：对应 `Gesture`。
- 手感参数（阻尼、权重）需要在设备上调参。

### 5.4 候选渲染技术（P3b 决策用）

| 方案 | 授权 | 互动能力 | 结论 |
|---|---|---|---|
| 简单图层动画（v1） | 无第三方运行时 | 仅状态、表情、嘴型 | 第一版采用 |
| Spine | Editor：Essential 不含 IK 和 Mesh，Professional 含；运行时条款待书面确认 | 骨骼、IK、Mesh、Skin、Event 齐全 | P3b 候选，优先评估 |
| Live2D Cubism | 需要书面确认是否属于「可扩展应用」 | 参数最完整 | 书面授权后再评估 |
| Rive | 运行时 MIT；编辑器免费版的限制待核实 | 状态机，可接收指针输入 | 备选 |

| Rive | 运行时 MIT；编辑器免费版的限制待核实 | 状态机，可接收指针输入 | 备选 |

### 5.5 事件协议（agent → 客户端）

```json
{"v":1,"type":"emotion","turn":"t1","t_ms":1200,"emotion":"joy"}
{"v":1,"type":"state","turn":"t1","state":"speaking|listening|thinking|idle"}
{"v":1,"type":"touch","area":"head"}     // 客户端 → agent
```

- agent 输出的情绪标签（如 `[joy]`）在 agent 侧剥离，不进入显示文本。
- 事件通过 LiveKit 数据通道发送。

## 6. 语音

### 6.1 策略模式（决定 4）

```kotlin
// 服务端（Python 对应同名接口）；接口示意
interface VoiceProvider {
    fun tts(text: Flow<String>, voice: VoiceRef): Flow<AudioChunk>   // 流式
    fun asr(audio: Flow<AudioChunk>): Flow<Transcript>               // 流式
}
```

- `VoiceRef = {provider, voice_id, params}`；Voice Gateway 按 `provider` 选择实现。
- 第一版只实现 **火山引擎**（TTS 与流式 ASR，具体产品与接口在 P2 确认）。
- 增加供应商：新增一个实现类与配置项，角色的 `voice_ref` 改 `provider` 即可，agent 与客户端不改。
- 失败切换：主供应商连续失败时切到配置的备用供应商；每次切换记日志。
- 句级缓存：角色固定问候语等短句缓存音频。

### 6.2 回合与打断

- LiveKit Agents 负责语音活动检测与回合；用户开口即打断当前播放（可在设置中关闭）。
- 句级切分：LLM 流式输出按句切分后送 TTS，降低首包延迟。

### 6.3 默认模式（决定 6）

- 默认**文字**：打开角色即是文字对话。
- 用户可在对话页切换到语音：麦克风发布音轨，agent 回复走 TTS。切换不断开房间。

### 6.4 对话主脑：DeerFlow（决定 4、17、19）

LiveKit Agents 的 `Agent.llm_node` 是可重写的节点，接收对话上下文、返回文本流。**companion 在这里接入 DeerFlow**，DeerFlow 成为真正的对话主脑：

```python
class CompanionAgent(Agent):
    async def llm_node(self, chat_ctx, tools, model_settings):
        async for event in deerflow.stream_message(thread_id, chat_ctx, ...):
            if event["type"] == "token":
                yield event["content"]          # 流式下发，未断句
```

- **运行方式**：嵌入式 harness（进程内 LangGraph 循环 + 中间件 + 子 agent + 上下文压缩），沿用 job-come 的 `deerflow_harness_client.py` 改造。
- **模型选择在 DeerFlow 内部**：DeerFlow 通过 LiteLLM 路由决定用哪个模型（场景化模型链，见 6.4.1），companion 的 `Agent` 不再自带 LLM。
- **工具与多步推理由 DeerFlow 负责**：记忆检索、角色查询、内容生成都是 DeerFlow 的工具，经 MCP 暴露（6.4.2）。
- **取消**：用户打断时，companion 调 DeerFlow 的 session cancel，停止当前回合的生成，不再消费后续 token。
- **线程安全**：DeerFlow harness 在独立线程中运行（同步 LangGraph 循环），companion 用队列把事件转回事件循环，模式同 job-come。

代价：DeerFlow 每轮都有编排开销，首包延迟高于直连模型。这是有意的取舍：能力优先，延迟在 P2 之后再优化。

#### 6.4.1 模型路由

- 沿用 job-come 的 LiteLLM 场景路由：配置声明场景 → 模型链（主模型、备用、超时、推理开关）。
- 对话场景 `companion_chat`、记忆提取 `memory_extract`、内容生成 `companion_rewrite` 互不共用。
- 换模型只改路由配置，不改代码。角色级模型覆盖作为扩展点保留，第一版只用全局场景。
- provider 只引用环境变量名，密钥不进入配置文件。

#### 6.4.2 MCP

- LiveKit Agents 自带 MCP 客户端（`MCPServerHTTP` / `MCPServerStdio` + `MCPToolset`），支持 SSE 与 streamable HTTP。
- 需要模型主动调用的动作（记忆检索、角色查询）做成 MCP 服务；工具过多时用 `tool_search` 按需挂载。
- 不给模型开放文件读写类工具。

### 6.5 流式输出与断句（决定 16）

- agent 输出同时支持流式和非流式。文字模式默认流式，经 LiveKit 文本流逐段下发。
- 流式时按句断句（中英文标点、换行），每句立即送 TTS，不等整段回复结束。
- TTS 支持流式合成；不支持流式的供应商退化为按句合成。
- 断句与情绪标签剥离都在 agent 侧完成，客户端只收到已断好的文本段和事件。

### 6.6 LiveKit 房间与 token（设计建议，待实现）

- companion 服务创建房间，房间名由服务端生成，绑定 `user_id` 与 `character_id`。
- 客户端调用 `POST /companion/sessions`（携带 App 现有登录凭证）→ 服务端校验用户、创建房间、签发 LiveKit JWT 并返回。
- JWT 由服务端用 LiveKit API key/secret 签名；身份为用户 id；grant 只允许加入该房间并发布/订阅；有效期短（建议 10 分钟），过期前重新请求。
- agent 使用服务端签发的独立 token 加入同一房间，不下发给客户端。
- LiveKit 密钥只放环境变量。

## 7. DeerFlow（决定 4、19）

DeerFlow 是**对话主脑**，不是旁路工具。

- **接入点**：LiveKit `Agent.llm_node`。DeerFlow 每轮产出流式 token，companion 透传给 `AgentSession`。
- **能力**：多步推理、工具调用、子 agent、上下文压缩，均在 DeerFlow 内。
- **运行**：嵌入式 harness（进程内 LangGraph），从 job-come 的 `deerflow_harness_client.py` 改造；不单独部署成 HTTP 服务。
- **取消**：用户打断时取消 DeerFlow 当前回合，丢弃剩余 token。
- **模型**：DeerFlow 经 LiteLLM 路由选择模型（6.4.1），companion 不另设 LLM。
- **输出仍要断句**：DeerFlow 产出的是连续 token，断句、情绪标签剥离、送 TTS 都在 companion 侧完成（6.5）。

风险与已知代价：

- **首包延迟**：DeerFlow 每轮有编排开销。P2 之后用实测决定是否给简单回合走直连模型的分支。
- **线程模型**：harness 是同步循环，需在线程与事件循环之间用队列转递，job-come 已有实现可参考。
- **嵌入依赖**：DeerFlow 以 wheel 形式引入，升级需同步锁文件。

## 8. Skill 与工具

### 8.1 Skill 包

LiveKit Agents 没有 skill 概念，skill 由**宿主组装**进提示词（job-come 已有实现）。

- **格式沿用 job-come**：`skills/public/<id>/SKILL.md` + `references/*.md`，frontmatter 用同一套（`name`、`description`、`allowed-tools`、`metadata`）。
- **组装在 companion 做**：建会话或换角色时，按角色与场景选出片段，拼进 `Agent` 的 instructions。`AgentSession.update_agent()` 支持中途更换，不用重开房间。
- **不要依赖模型主动读文件**。job-come 踩过这个坑：skill 正文要等模型调 `read_file`，而工具白名单里没有它，正文成了死文本。语音场景更不该让模型为读 skill 多花一次工具调用。
- skill 内容是**第一方编写、随仓库发布**，视为可信输入；用户资料、模型输出不可信。

### 8.2 工具与 MCP

- LiveKit Agents 自带 MCP 客户端：`MCPServerHTTP`（SSE / streamable HTTP）与 `MCPServerStdio`，经 `MCPToolset` 挂到 agent。
- DeerFlow 是主脑，工具的实际调用发生在 DeerFlow 内；MCP 用于把外部能力（记忆检索、角色查询）暴露给它。
- 工具过多时用 `tool_search` 按需挂载，避免提示词膨胀。
- 可对单个工具设置取消、重复调用策略与进度上报。
- 不开放文件读写类工具。

## 9. 账号与数据

- **账号体系**：复用 App 现有账号与登录接口，不与 job-come 共用。companion 服务只校验现有登录凭证。
- **删除账号**：删除用户全部记忆（Hindsight/mem0 的存储、索引、原始记录）、会话、角色（用户创建的角色）、语音与对话日志。删除后 30 天内不可恢复，期间账号不可登录（待确认，见第 13 节）。
- **导出**：用户可导出自己的记忆与会话（隐私要求，建议做）。
- Google Play 数据安全表单：声明语音、个人信息、记忆等数据类别与用途。

## 10. Google Play 与合规

| 项目 | 处理 |
|---|---|
| 账号与数据删除 | 应用内删除入口，删除全部数据（第 9 节）|
| UGC（用户角色）| 第一版不开放；上线前必须有举报、屏蔽、审核 |
| AI 生成内容 | 角色标注「AI 角色」，不冒充真人 |
| 未成年人 | 年龄门槛，未成年人模式限制角色与时长（需法务确认）|
| 第三方许可 | Live2D 需书面确认；Honcho（AGPL）不采用；其它 Apache/MIT 可商用 |

## 11. 官方角色（决定 11）

三个原创角色，美术与人设均由我们设计（沿用 v2 的晓雾、小遥、林澈）。第一版形象是简单动画所需的静态立绘与表情图，需要委托原创美术，预计每角色一套小型素材。

## 12. 扩展点（决定 9）

- `voice_ref`、`model_ref` 字段已有，第一版只允许平台列表。
- 用户创建角色时的选择器接入 `GET /voices`、`GET /models` 即可。
- 不做：用户上传形象、语音克隆。

## 13. 分期与待确认

### 13.1 分期

| 阶段 | 内容 | 不需要设备的验收 |
|---|---|---|
| P0 基础 | 角色模型与 V2 导入导出；3 个官方角色；LiveKit Cloud 房间与 token 签发；**DeerFlow 嵌入 companion 并接管 `llm_node`**；最小文字回合；VoiceProvider 接口与火山 TTS 适配器 | 导入导出往返；token 签发单测；DeerFlow 回合测试（fake 模型）；接口单测（fake 供应商） |
| P1 文字 + 记忆 | skill 组装进提示词；MemoryPort；mem0 接入；「我记住的」；跨角色读取与来源标注；删除账号的全量删除 | skill 组装的快照测试；50 段对话的抽取对比；删除后零残留测试；隔离测试 |
| P2 语音 | 火山 ASR/TTS 接通；语音模式切换；打断（含取消 DeerFlow 回合）| 回合状态机单测；取消传播测试；音频在设备上验证 |
| P3 简易形象（v1） | `AvatarCommand` 指令框架；`SimpleAvatarRenderer`：立绘、表情图、嘴型两帧、眨眼、呼吸；事件映射 | 指令映射与未知指令忽略的单测；设备上看渲染与切换 |
| P3b 形象互动（之后） | 新渲染器（Spine 优先评估）；拖动跟随、点击反应、手势；补齐 `capabilities` | 限幅与映射单测；拖动手感需设备 |
| P4 长任务与内容生成 | DeerFlow 子 agent 与上下文压缩调优；需要长耗时的生成类任务（故事、计划） | 超时与失败处理测试 |
| P5 广场与扩展 | 用户角色、审核、举报、屏蔽；Graphiti 评估；Rive 评估 | 状态机与审核测试 |

### 13.2 仍需确认

1. **Live2D**：是否要向 Live2D 发信确认「固定模型、不开放用户上传」的应用是否属于 Expandable Application，以及费用？（我的建议：P3b 之前再决定；v1 不依赖任何形象授权。）
2. **记忆后端**：已定 mem0（见 4.3），Hindsight 作为备选保留。
3. **账号删除后的 30 天冷静期**：要不要？（Google Play 允许立即删除；冷静期是我的建议，给误操作留余地。）
4. **官方角色美术**：委托原创美术的预算与周期，需要你定。
5. **火山引擎**：具体产品（豆包语音的 TTS 与流式 ASR）与合同，需要你对接。
6. **现有登录凭证**：companion 服务要校验 App 现有登录的凭证，需要知道它的格式（token 还是 cookie）与校验方式。需要现有服务端负责人给一份说明。

## 14. 假设（可推翻）

- LiveKit Agents 的自定义 TTS/STT 插件可以接入 VoiceProvider（P0 验证）。
- 简单动画的表现力在设备上足够支撑 v1 的流程验证（P3 验证）。
- 用户维度记忆跨角色读取不违反我们的隐私政策与 Google Play 政策（上架前法务确认）。
