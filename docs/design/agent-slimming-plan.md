# 陪伴 Agent 瘦身与形象占位方案（澄清后定稿，未开发）

日期：2026-11-16　状态：方案待批，**未改任何代码**
范围：companion-agent 服务端（去 checkpointer、loop 内存化、中间件/prompt/tool/skill 裁剪）+ Android 客户端（内置占位形象）

---

## 0. 已锁定的决策（来自 2026-11-16 十点答复）

| # | 决策 |
|---|---|
| 1 | 单机/多实例都要支持，用**配置开关**切换回合后端；单机默认走进程内 |
| 2 | `loop_detection` 保留 |
| 3 | companion-core 两条底线（不制造依赖、危机处理）保留 |
| 4 | 提醒三件套的去留：见 §4，方案建议先删，等主动消息上线再加回 |
| 5 | 画风：二次元 |
| 6 | 不接受按张付费出图；改用**免费开源本地生成**（ComfyUI + NoobAI/Illustrious） |
| 7 | 形象发布最终走 TOS；**当前阶段包内置在客户端**，不依赖 TOS |
| 8 | 裁剪对象是 deerflow **自带的通用提示词**；本地 `pi` 仓库经核实是编程 agent，不是 Inflection Pi，只借鉴其极简/分层思路 |
| 9 | 动画/画图采用开源项目，候选见 §7 |
| 10 | TOS 故障已复现定位，修复动作在用户侧（火山控制台），见 §8 |

---

## 1. 实测基线：token 到底花在哪

### 1.1 我们自己的系统提示词（linche，实测）

| 层 | 字符 | ≈tokens | 占比 |
|---|---|---|---|
| `method`（growth-coaching + companion-core，经 resolver 拼进角色层） | 1396 | 698 | 75% |
| PLATFORM_LAYER（6 条角色扮演规则） | 356 | 178 | 19% |
| 输出模板 | 71 | 36 | 4% |
| 角色头 | 8 | 4 | — |
| **合计** | **1854** | **927** | |

283 个目录角色不用 skill，系统提示词只有约 230 tokens——**skill 文本是陪伴角色的专属成本**。

### 1.2 deerflow 自带、我们关不掉的部分（deerflow_harness 2.3.0.dev0 实测 + 2026-11-16 复核修正）

| 项 | 字符 | 触发条件 | 实际是否计费 |
|---|---|---|---|
| `ask_clarification_tool` 工具描述 | 2076 | factory.py:455 无条件注册，features 关不掉 | **不计费**：我们的 `ToolVisibilityMiddleware`（graph_middleware.py:79-80）在模型调用前把它从 `request.tools` 过滤，docstring 明写「永远过滤掉」。2026-11-16 复核确认，早期「每请求白付 ~900 tokens」的判断作废 |
| DurableContext authority contract | 323 | goal/summary/delegation/skill/artifact 任一通道非空才注入；我们现在全空 | **实际零注入**（durable_context_middleware.py:422-423 空状态短路） |
| LoopDetection 警告话术 | 987–1029 | 仅真检测到重复调用才注入 | 反应式，正常对话零成本（已决定保留） |
| TokenBudget 警告 | 157–217 | 仅跨阈值，每 run 至多一次 | 反应式；但中间件每轮遍历全部 AIMessage 算 usage，建议关（见 §3） |
| ToolErrorHandling recovery hint | 63 | 仅工具失败时 | 反应式，留着 |
| 其余无条件中间件（Dangling/Coalescing/MCC） | 0 | 正常流程零文本 | 不计费 |

**复核后的结论**：deerflow 无条件链在我们当前配置下**没有稳定按请求付费的通用废话**——clarification 已被自研中间件拦在模型请求之外，DurableContext 在空状态自动闭嘴。真正的 token 成本全部在我们自己的内容里：skill 文本（698 tokens）、PLATFORM_LAYER（178）、7 个工具 schema（~250）。

### 1.3 合计目标（复核修正版）

| | 现在（linche 一次请求） | 目标 |
|---|---|---|
| 自有系统提示词（skill 占 75%） | ~927 tokens | ~300 tokens |
| 工具 schema（7 个，clarification 本就不计） | ~250 tokens | 4 个 ~150 tokens |
| **合计节省** | | **约 700–750 tokens/请求（约 -60% 的自有提示词部分）** |

---

## 2. 任务 A：去 checkpointer + loop 内存化（含单机/多实例开关）

### 2.1 现状事实

- `PromptPlan` 字段为 `system/injections/version/lore_uids/dropped/skills`，**没有 history**；模型现在能看到的历史**只来自 checkpointer**。直接拆 checkpointer = 每轮失忆。
- `ContextBuilder` 读最近 6 条消息只用于世界书关键词扫描，不进对话。
- `history.py` 已有 5 处 `if self._conversation is None` 无状态回退——代码本来就按「可能没有 checkpointer」写。
- 单 uvicorn worker，回合是同进程 `asyncio.create_task`；流式期间只写 Redis（每 50ms/64 字一次，单次往返 ~193ms，400 字回复 6–10 次 = 1.2–2 秒纯等待），DB 只在 `_finish` 写一次。
- 回合控制面已经有端口抽象 `TurnControlPort`（`service/turn/control.py`，24 个方法的 Protocol），生产实现是 `RedisTurnControl`，组合根在 `bootstrap/platform.py:228`。**开关插在这里，业务层零改动。**

### 2.2 开关设计

新增配置（`config.py` Settings，与 `companion_jobs_in_api` 同风格）：

```
companion_turn_backend: str = "local"   # local=进程内（单机）| redis=现状（多实例）
```

| 组件 | local（单机，默认） | redis（多实例） |
|---|---|---|
| 回合锁 | 进程内 `asyncio.Lock`（按 session_id 字典） | RedisTurnControl，原样保留 |
| 流式事件 | `asyncio.Queue`，SSE 协程直接读 | Redis Stream，原样保留 |
| 取消/插话 | `asyncio.Event` | Redis ctl Stream |
| fence/renew_lock/process_name | 删除（多进程抢锁才需要） | 保留 |
| DB 读写节奏 | **两种模式相同**，见 2.3 | 同左 |

注意：local 模式去掉的只是**回合控制面**的 Redis。鉴权 `redis_token`（读 Java 登录态）和后台 jobs 仍可用 Redis；dev 下鉴权本就是 `allow_all`。

`LocalTurnControl` 实现 `TurnControlPort`，组合根按 settings 二选一：

```
control = LocalTurnControl() if settings.turn_backend == "local" else RedisTurnControl(redis)
```

`TurnRunner` 只依赖端口，不感知实现。契约测试复用现有 queue.py 的纯逻辑参考实现（T13 已有 Lua 与参考实现的对比测试，加一套 Local 对参考实现的对比即可）。

### 2.3 loop 内零 DB 的消息生命周期

```
回合开始（一次性读，并发）
  ├─ 历史 N 条消息（messages 表，建议 N=40 条 + 字符预算双限）
  ├─ 角色卡 / 记忆 / 画像（现有 4 路并发，~180ms）
  └─ 构造进程内 list[BaseMessage] 工作副本

loop 中（0 DB、0 Redis[local 模式]）
  ├─ 历史经 PromptMiddleware 作为真实 Human/AI 消息注入图请求
  ├─ token → Queue/Stream 直推 SSE
  ├─ 取消 → Event/ctl
  └─ 全部增量累积在内存 list

回合结束（一次性写）
  ├─ messages.append 单条 assistant 消息（含 interrupted 标记）
  └─ QA/turn 记录（可选，受 companion_qa_enabled 控制）
```

改造点：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `agent/prompt/plan.py` | PromptPlan 加 `history: tuple[Message, ...]` |
| 2 | `service/chat/context.py` | 扫描窗口从 6 扩到可配置；同时产出 history |
| 3 | `agent/brain/graph_middleware.py` | PromptMiddleware 把 history 拼进 `request.messages`（注意 splice_injections 的 depth 语义） |
| 4 | `bootstrap/platform.py` | checkpointer=None、conversation=None、不构造 ResummarizeService；按开关选 TurnControl |
| 5 | `agent/brain/deerflow.py` | `stateful=False`，`_config` 恒返回 None（llm_collector 回调仍挂） |
| 6 | `service/turn/` | 新增 `local_control.py`；TurnRunner 去掉 fence/renew 分支 |
| 7 | 删/留空壳 | `conversation_state.py`（DeerFlowConversationState、CheckpointThreadEraser） |

**摘要策略**：第一版不做 LLM 摘要，历史按字符预算硬截断（保留最近 N 字，边界对齐到整条消息）。理由：9000 token 才触发摘要，角色扮演单轮很短，性价比低；将来要做时用自定义短模板替换 langchain 那套 2724 字符的 SESSION INTENT/ARTIFACTS 模板。

### 2.4 明确会失去什么（用户已同意忽略断点恢复）

- 进程崩溃 → 在途回合丢失，不补偿；已写完的消息不受影响。
- local 模式不能多副本水平扩容；扩容时切 `redis` 模式（代码路径保留，不是一次性删掉）。
- 账号注销的 checkpoint 线程擦除（CheckpointThreadEraser）随 checkpointer 一起消失；消息表的擦除本来就独立存在。
- 编辑/删除/重新生成：改为对消息表操作后，下一回合从表重读历史，天然一致；不再有「消息表 + checkpoint 双写对齐」问题（反而简化）。

### 2.5 验收

1. 同一会话连续 3 轮，第 3 轮能正确引用第 1 轮内容（**最关键**）。
2. 清空历史后下一轮不引用旧内容。
3. local 与 redis 两种 backend 跑同一组契约测试。
4. 首字延迟对比（预期省掉 1.2–2s 的 flush 等待）。
5. 取消在 500ms 内生效；连续发两条验证合并/排队语义不回退。

---

## 3. 任务 B：中间件瘦身（2026-11-16 复核后改判）

### 3.1 复核结论：不做 full takeover

原方案假设「ask_clarification 的 2076 字符每请求稳定计费、只有 full takeover 能删」。复核 `graph_middleware.py:71-84` 后确认：**自研 `ToolVisibilityMiddleware` 已经无条件把它过滤掉**（`if name == ASK_CLARIFICATION: continue`，在 `awrap_model_call` 里改 `request.tools`，先于模型请求构造）。full takeover 的主要收益不存在，而它的代价（deerflow 升级手动同步链、失去错误处理/合并等免费补丁）是实打实的。**改判：维持 `features=` + `extra_middleware=` 现状，不接管链。**

### 3.2 实际要改的只有一处

`deerflow.py:52` 的 `default_features(token_budget=True)` 改成 `token_budget=False`（deerflow 上游默认本来就是 False，是我们主动打开的）。理由：TokenBudget 的提示词只在跨阈值时注入，省不了多少 token，但中间件**每次模型调用都遍历全部 AIMessage 汇总 usage_metadata**，长会话下是纯 CPU 开销。

`loop_detection=True` 按你的决定保留。

### 3.3 无条件链的最终态度（全部保留，零改动）

| 中间件 | 理由 |
|---|---|
| SystemMessageCoalescing | 严格模型后端要求合并 system，必需 |
| ModelContentCompatibility | 注入历史后历史里可能有 URL/图片块，需要它改写占位 |
| DanglingToolCall / ToolErrorHandling | 反应式补丁，正常流程零文本 |
| DurableContext | 空状态零注入；唯一风险是 goal 通道被误写——在 §2 去掉 checkpointer/不引入 goal API 的前提下不会发生，加一条注释说明这个前提即可 |
| Clarification | 工具已被 ToolVisibility 拦掉，中间件本身不注入文本 |
| 自研 5 件套（Prompt/ToolVisibility/Steer/ToolAudit/ToolAuthorization） | 不动 |

---

## 4. 任务 C+D：prompt / tool / skill 裁剪

### 4.1 先解释「提醒三件套」是什么

- `create_reminder`：用户说「明天下午 3 点叫我开会」，模型调它建一条服务端定时记录；到点后后台 jobs 触发**她主动给用户发消息**。
- `list_reminders` / `cancel_reminder`：查看/取消还没到点的提醒，是 create 的配套。
- 依赖链：ReminderService + 后台 worker + 主动消息通道。**主动消息整条链路现在没有产品化入口**，工具却已经在每次请求的 schema 里。

建议：**三个一起删**，同时保留 `remember_event`（只写记忆、无定时副作用，「记住我妈生日」这类仍然成立）。等主动消息功能真正排期时，把三件套作为一个整体加回。这是待你拍板的唯一产品问题。

### 4.2 工具裁剪表

| 工具 | 决定 | 理由 |
|---|---|---|
| get_current_time | 留 | 「明天/周五」的时间判断必需 |
| recall_memory | 留 | 记忆是核心差异 |
| remember_fact | 留 | 「记住」显式指令 |
| remember_event | 留 | 生日/面试/复诊，无副作用 |
| create/list/cancel_reminder | **建议删（待确认）** | 依赖未上线的主动消息；省 ~110 tokens + 减少误调用 |

7 → 4 个。

### 4.3 skill 裁剪表（8 个）

| skill | 现状 | 决定 |
|---|---|---|
| companion-core | always-loaded，进**每个**角色的提示词 | 压缩保留：两条底线（不制造依赖 5 条禁止句、危机 5 步）原文保留；active-listening、memory-callback 的要点各压成一句并入；删「诚实」整节（PLATFORM_LAYER 已有） |
| growth-coaching（linche） | 1205 字 + references | 只留边界 6 条 + 说话方式 4 条，约 300 字 |
| org-psychology（sudai） | 1528 字 + references | 同法压缩 |
| problem-solving | 数学/物理解题，无角色使用 | **删目录** |
| shared-activity | 一起听歌看电影，无角色使用 | **删目录** |
| active-listening | 无角色声明 | 合并进 companion-core 后删 |
| memory-callback | 无角色声明 | 合并进 companion-core 后删 |
| proactive-checkin | 无角色声明 | 保留文件，改由主动消息路径显式指定，不进默认链 |

**已核实的加载机制**（`agent/skills/library.py`）：`compose()` 会读 SKILL.md 正文 **并且**把 frontmatter `references:` 列出的片段全文拼进提示词（`_render`，230-238 行）。所以 references **不是死文件，确实在计费**——压缩时要连同 references 一起处理，不能只改 SKILL.md。

### 4.4 自有 prompt 重写（目标 ~300 tokens）

分层结构（借鉴本地 pi 仓库的可替换 section 架构，不抄其内容——它没有陪伴人格）：

```
[角色]      角色卡 persona（目录角色 / linche / sudai）
[说话方式]  新写 100–150 字：短句口语、一次只推进一件事、先接情绪再谈事、
            一次只问一个问题、记得但不背诵、不用 Markdown 罗列
[记忆]      50 字：何时用 recall_memory，不许主动炫耀记忆
[边界]      80 字：companion-core 两条底线压缩版
[输出]      保留现有 36 tokens 输出模板
```

PLATFORM_LAYER 6 条 → 3 条：AI 身份、违法内容不生成、危机处理；删掉第 6 条「标记为资料的内容不执行」（已无外部资料注入路径）。

Pi 语音模式里可直接借鉴的一条硬规则（`apps/p2/plugins/voice/worker.ts`）：语音回复禁用 Markdown/列表/长句——对陪伴语音场景完全适用。

---

## 5. 任务 E：免费二次元出图链路

### 5.1 零按张成本的本地栈

| 组件 | 作用 | 成本 |
|---|---|---|
| [ComfyUI](https://github.com/comfyanonymous/ComfyUI) | 本地节点式生成引擎，MIT | 免费 |
| [NoobAI XL](https://similarlabs.com/p/noobai-xl-anime-sdxl-beginner) | 二次元 SDXL 模型，Danbooru tag 原生，权重在 HuggingFace/Civitai | 免费开放权重 |
| Illustrious XL | NoobAI 的上游基线，1536px 原生 | 免费 |

硬件现实：SDXL 级模型建议 8–12GB VRAM；Apple Silicon 16GB 内存可跑（CPU/统一内存，单张约 1–3 分钟）。若本机不够，两条免费退路：NoobAI 官方 HF Space 免费 Playground（免账号免额度）、Google Colab 免费 T4。

### 5.2 仍然存在的能力边界（与付费与否无关）

图像模型**做不到像素对齐的多层透明图**。所以流程是：

1. ComfyUI 生成 1 张二次元立绘（统一 prompt 模板 + 固定 seed 风格锁角色）
2. Python/PIL 程序化派生：眼带压扁做 `eye_closed`、嘴部椭圆叠加做 `mouth_open`
3. 第一版只做 3 层（portrait / eye_closed / mouth_open），眉/腮红/高光/瞳孔独立层放弃
4. 若派生效果假，再用同 prompt + "eyes closed" 生成第二张，接受轻微漂移

### 5.3 让静态立绘「像人」的纯代码投入（不依赖美术，约 150 行）

1. 微动作调度器：6–15 秒随机歪头/视线飘移/轻微前倾，随机幅度时长——这是立绘和活物的分水岭。
2. 接通 `motion` 字段：服务端已在传 `gaze_range_px`、`micro_interval_s`，客户端 0 处引用。
3. 接通 Gaze：`AvatarFiles.pupilLeft/pupilRight` 已声明却从不绘制，`SimpleAvatarRenderer` 里 `is AvatarCommand.Gaze -> Unit` 直接丢弃。

---

## 6. 任务 F：占位形象内置客户端（绕开 TOS 先跑通）

### 6.1 已核实的客户端现状

- `AvatarFiles` 注释明确「内置形象用资源 id，下载的形象用文件」；`AvatarSpec` 的资源 id 可为 null，立绘缺退到占位块——**内置通道在架构上已预留，但 `feature-companion/src/main/assets` 目录不存在，从未放过素材**。
- `AvatarSync.syncFor()` 拉不到远端时返回 null，注释写明「调用方回退到内置默认形象」。

### 6.2 内置方案（本期）

```
feature-companion/src/main/assets/avatars/<avatar_key>/
  manifest.json          与线上包同 schema（schema_version=1）
  portrait.webp
  eye_closed.webp
  mouth_open.webp
```

启动时把 assets 包**种子安装**进 `AvatarPackStore` 的 files/avatars 目录（走它现有的 sha256 校验+原子安装流程，不新开渲染分支）；`AvatarSync` 失败/无网时种子包就是 fallback。以后 TOS 修好、线上发布同 key 更高版本时，现有版本比较逻辑会自然升级，不需要迁移代码。

### 6.3 发布链路（TOS 修好后再做，本期只留接口验证）

`POST /admin/avatars`（`COMPANION_ADMIN_TOKEN` 线上已配）→ publish → `GET /v1/avatars?app_version=<整数>`（注意 app_version 是 int，传 1.1.0.66 会 422）→ 下载签名 URL → 校验 → 解压。服务端 `AvatarCatalog` 的 objects 就是 TOS，**这条链路目前必然在上传环节失败**，所以排在 §8 修复之后。

---

## 7. 动画方向的开源候选（调研存档，本期不引入）

| 项目 | 定位 | 对我们的适配判断 |
|---|---|---|
| [Inochi2D](https://inochi2d.com/) | Live2D 的开源替代，实时 2D 木偶绑定，Rust 核心 | 最正统的长期方向；Android 运行时需评估 JNI 绑定成本 |
| [Iki](https://www.npmjs.com/search?q=keywords%3Avtuber)（@ikijs） | MIT，WebGL2 运行时 + 开放 .iki JSON 格式，**带 MCP 服务器可让 AI 自动绑定角色** | 概念最契合「AI 产角色」，但只有 Web 运行时，Android 要靠 WebView |
| [Umamo](https://www.umamo.org/) | Kotlin/Compose Multiplatform，可直接读写 Live2D .cmo3/.moc3 | 技术栈与我们一致（Kotlin），但动画功能计划 2027 夏才成熟 |

本期结论：不上新框架，继续用 Compose 分层渲染（3 层 + 微动作调度）；Inochi2D 列为 P2 评估项。

---

## 8. TOS 故障诊断（2026-11-16 实测，需用户操作）

### 8.1 证据链

| 时间 | 事件 | 证据 |
|---|---|---|
| 11-09 | TOS 冒烟全成功（写/预签名读/删，桶 sspacee，前缀 companion-test/） | T13.md §冒烟记录 |
| 11-14 00:51 | 本地 `server/.env` 最后一次被修改 | 文件 mtime |
| 11-15 起 | `/v1/uploads` 全部 500 SignatureDoesNotMatch | 真机/接口实测 |
| 11-16 | 用本地 .env 的 AK/SK 直接打 TOS：head_bucket 403、list/put 全 SignatureDoesNotMatch | 本机 boto3 复现 |

已排除的诱因：服务器时钟偏差 0.9 秒（<15 分钟容忍窗）；SK 无 CR/尾空格/shell 特殊字符（58 字符、无引号、不会被 `. .env` 破坏）；endpoint/bucket/region 与 11-09 成功时一致。

### 8.2 结论与修复步骤（需要你在火山引擎控制台操作，我不碰密钥）

`SignatureDoesNotMatch` 的含义是 **AK 存在但 SK 不匹配**（若 AK 已删会是 InvalidAccessKeyId）。最大可能：11-14 改 .env 时换了 AK 但 SK 是旧的，或密钥被轮转。

1. 火山引擎控制台 → IAM → 访问密钥，确认该 AK（AKLT 开头）是否仍启用。
2. 二选一：重置该 AK 的 SK；或从 job-come 的配置里取同一桶（sspacee）当前有效的 AK/SK 对——**跨项目读凭据我不会做，需要你亲自取**。
3. 同步更新两处：本地 `companion-agent/server/.env` 第 60 行、服务器部署目录的 `server/.env`（两份可能不一致；线上 500 说明服务器那份也是坏的）。
4. `process.sh` 重启 API 进程。
5. 叫我重跑同一个本地探测脚本（只输出错误码、不打印密钥）验证。

---

## 9. 执行顺序与分批

| 批次 | 内容 | 依赖 | 风险 |
|---|---|---|---|
| 1 | 任务 A：history 注入 + LocalTurnControl + 开关，先 local 后 redis 双模式契约测试 | 无 | 中（失忆风险，验收 2.5 兜底） |
| 2 | 任务 B：`token_budget=False` 一行改动 + DurableContext 前提注释 | 无 | 低 |
| 3 | 任务 C+D：prompt/tool/skill 裁剪，逐角色回归对白 | 可与 1/2 并行 | 低 |
| 4 | 任务 E+F：ComfyUI 出立绘 → PIL 派生 3 层 → assets 种子包 → 真机看效果 | 无，可全程并行 | 低 |
| 5（你修完 TOS 后） | 线上发布链路端到端验证 | §8 完成 | 低 |

每批独立可回滚、独立提交；服务端与客户端改动不重叠。

---

## 10. 待你拍板的开放项

1. 提醒三件套：按建议删除（主动消息上线时整体加回），还是保留？
2. ComfyUI 跑在本机还是用 HF Space / Colab 免费档？（取决于本机显卡/内存，需要你告知机型）

~~3. 中间件链走 full takeover 还是等 spike~~——**2026-11-16 复核关闭**：ToolVisibility 已过滤 ask_clarification（graph_middleware.py:79-80），不做 full takeover，任务 B 降为一行配置改动。
