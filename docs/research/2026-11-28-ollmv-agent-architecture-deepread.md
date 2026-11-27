# Open-LLM-VTuber agent 架构级全量深读

日期：2026-11-28｜对象：`/Users/gqli/work/Open-LLM-VTuber`，HEAD `992309c`（2026-05-15，pyproject `version = "1.2.1"`）。
性质：agent 架构级深读，补齐并修正上一份 [Open-LLM-VTuber v1.2.1 源码深读](2026-11-28-open-llm-vtuber-deepread.md)（下称「前一轮文档」）。前一轮已钉的三件可抄点（四装饰器 / 有序并行 TTS + 记忆手术 / 表情词表注入）本轮全部逐行复核成立；本文的增量是：完整管线语义、tool/MCP 线、LLM 适配层、配置/升级体系、引擎与协议腐化的取证，以及「我们只能更好」的逐条落点。

> 落点说明：本会话被工具纪律限制不能直接写 `focused-mclean-1bfa86` worktree，本文落在本会话 worktree 的同名路径，协调会话统一搬运入库。

## 0. 阅读方法与诚实约定

引证分三级，正文按级标注：

- **A 级（逐行亲读）**：`agent/transformers.py`、`agent/agents/basic_memory_agent.py`、`agent/stateless_llm/*`、`mcpp/*`、`service_context.py`、`chat_history_manager.py`、`utils/sentence_divider.py`、`conversations/tts_manager.py`、`utils/stream_audio.py`、`prompts/utils/*`、`live2d_model.py`、`routes.py`、`upgrade.py`、`mcp_servers.json`、`model_dict.json`、配置体系（config_manager/*）。行号可直接引用。
- **B 级（并行逐行深读 + 本人抽查复核关键行）**：音频引擎（tts/asr/vad/tts_preprocessor）、config_templates 与 upgrade_codes、客户端 bundle 取证、仓库体检。行号可信，个别标注「推演未实测」。
- **C 级（bundle 压缩产物取证）**：前端 `frontend/` 是 submodule 预构建产物（branch `build` @ `06a659b1`，无源码/无 sourcemap/无 lockfile），引证用字符偏移 `JS@N`，不是行号。

**行号漂移声明（诚实纪律）**：本 checkout 有 6 个已追踪文件的**本地未提交改动**（`git status --porcelain` 恰好 6 项、无 untracked）：`agent/stateless_llm/openai_compatible_llm.py`(+3，extra_headers)、`agent/stateless_llm_factory.py`(+1)、`config_manager/stateless_llm.py`(+5，给 `OpenAICompatibleConfig` 加 `extra_headers` 字段与双语描述)、`service_context.py`(1/2，cosmetic)、`.python-version`、`uv.lock`。**本文行号描述的是工作树，不是 pristine `992309c`**；漂移集中在上述 4 个 src 文件，幅度 ≤5 行。

**对任务简报的更正**：简报提到的 `agent.py` / `message_processor` 在此仓库不存在。真实文件是 `agent/agents/basic_memory_agent.py`（agent 核心）、`agent/transformers.py`（四装饰器）、`message_handler.py`（消息队列）。「skill」概念全仓 grep **0 命中**——OLLMV 没有 skill 层，只有 MCP tools + 提示词注入（§2.5）。

**两处可执行取证**（用仓库自带 venv 以 `-I` 跑只读探针，不改仓库）：`json_detector` 字符串字面量缺陷复现（§2.5）；句切分 regex 模式三例破坏复现（§2.7）。

## 1. 架构总图

```
Browser / Electron 桌宠（frontend/ = 预构建 bundle 子模块，审计面只有压缩产物）
  麦克 → 浏览器端 VAD（@rick0322/rick-mic-vad，silero v5 onnx，阈值 0.50/0.35/35 帧，JS@1320010）
  播放 → TaskQueue(20ms 间隔) + _wavFileHandler 口型 + 三级 interrupted 守卫（JS@1859747/1364593/1362050）
  │   触发消息 / audio-data 分片 / interrupt-signal{已听到的文本} / frontend-playback-complete
  ▼   单一 WebSocket /client-ws（无鉴权、ws:// 明文、默认 127.0.0.1:12393）
┌──────────────────────────── FastAPI 单进程（asyncio；无多 worker、无用户概念） ────────────────────────────┐
│ server.py（CORS allow_origins=["*"] + allow_credentials，:83-88；静态挂载 live2d-models/web_tool/cache）    │
│ routes.py：/client-ws :29 /proxy-ws :61 /web-tool :86,91 /live2d-models/info :96 /asr :141 /tts-ws :201    │
│ websocket_handler.py：19 种消息类型分发表（:78-98）；每连接 client_uid=uuid4（:33）                        │
│   ├ message_handler.py：每连接请求/响应关联队列（:16-31），wait_for_response 无 timeout                    │
│   ├ conversation_handler.py：每 client 一个 asyncio task 槽（:99 覆盖不 cancel）；打断 :112-144            │
│   ├ single_conversation.py / group_conversation.py（群聊 = 无限 FIFO 轮转，见 §3.3）                       │
│   └ service_context.py：每会话 MCP 组件隔离、其余引擎按引用共享（:191-198）；角色热切换（:471-558）        │
│                                                                                                          │
│ agent 层  AgentFactory → basic_memory（记忆）/ mem0（空文件）/ letta / hume（打断未实现）                  │
│   ┌ 呈现管线（四个装饰器，每次 chat() 重建栈 :586-593）：                                                │
│   │   agent.chat() async generator（str token + dict 事件混合流）                                        │
│   │     → sentence_divider → actions_extractor → display_processor → tts_filter        (transformers.py) │
│   └ tool 线（三模式）：Claude 原生 loop / OpenAI 原生 loop / prompt-mode（括号计数 JSON 抓取）            │
│        mcpp/：ServerRegistry←mcp_servers.json → ToolAdapter（三产物：prompt 文本/OpenAI schema/Claude    │
│        schema）→ ToolManager → ToolExecutor（状态事件 + final_tool_results 以 dict 注入 token 流）        │
│   stateless LLM：12 provider 名 → OpenAICompatibleLLM（大多数）/ ClaudeAsyncLLM / llama_cpp / template     │
│        tool 不支持时 sentinel "__API_NOT_SUPPORT_TOOLS__" → 自动降级 prompt-mode（:484-494）              │
│                                                                                                          │
│ 音频路  TTS：TTSTaskManager 并行合成/按序交付（序号重排 :99-111）→ 整 WAV base64 payload                  │
│         ASR：纯 batch（VAD 攒完整段语音，接口收整段 ndarray）；服务端 Silero VAD 可选、非会话主路         │
│ 持久化  chat_history_manager.py：每会话一个 JSON 文件，每条消息整读整写（:93-147）                        │
│ 配置    conf.yaml（不入库）+ characters/*.yaml 增量 deep_merge 热切换 + 双语 pydantic schema               │
│         + conf_version/upgrade_codes 迁移 + model_dict.json（手工注册表）                                │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

一句话架构：**单进程 asyncio 服务器把一个「async generator agent 契约」接到一条四装饰器呈现管线上，管线输出变成有序并行的 TTS payload 流，打断靠客户端 VAD + 服务端任务取消 + 记忆手术闭环；能力（表情/工具）从模型 manifest 与 MCP registry 自动注入 LLM 提示词。**

## 2. 逐模块深读笔记

### 2.1 传输与会话层（A/B 级）

- 唯一会话入口 `routes.py:29` `/client-ws`：`await websocket.accept()` 后直接 `client_uid = str(uuid4())`（:33）——这是会话标签，不是凭据。**无任何 `Depends`、无 Origin 校验**（B 级复核）。其余面：`/proxy-ws :61`、`/asr`（一次性 multipart，:149 `await file.read()` **无体积上限**）、`/tts-ws :201`（文本→TTS，音频走 `/cache/` 静态取回）、`/live2d-models/info :96-139`（目录枚举，绕开 model_dict.json 的第二套发现机制）、`/docs`/`openapi.json` 默认开放。
- `websocket_handler.py`：每连接状态字典（:64-71，含 `received_data_buffers`——`audio-data`(:484) 与 VAD 输出(:505) 都 `np.append` 无限累积，只有触发消息才清）；19 种消息类型分发表（:78-98）；`MessageType` 枚举（:32-45）是死代码，从未被引用。
- **断连清理 bug（A 级逐行确认）**：`handle_disconnect :280-316` 在 `:302` `client_contexts.pop(...)` 之后，`:311` 又 `client_contexts.get(...)` → 永远 None → `:313 await context.close()` **永不执行**。每会话的 MCPClient（stdio 子进程）与 agent 实例泄漏。旁证：`_cleanup_failed_connection :318-324` **存在**——OLLMV 自己的 `CLAUDE.md:92` 说「the missing `_cleanup_failed_connection` method」，仓库自述与代码不符。
- 打断路径：前端 `interrupt-signal`（带已听到文本）→ `_handle_interrupt :369-392` → `conversation_handler.handle_individual_interrupt :112-144`（取消 task + 调 agent.handle_interrupt）。
- `proxy_message_queue.py`：无界 `deque`（:15），0.1s 轮询（:96），`conversation_active` 单飞门（:100-111）。手机代理模式复用同一管线。
- `message_handler.py:16-31`：请求/响应关联是无缓冲的按类型等待；调用处 `wait_for_response(...)`（`conversation_utils.py:173`）**不传 timeout**——前端不回 `frontend-playback-complete` 就永久挂起。

### 2.2 会话编排（A 级）

- `conversation_handler.py`：单聊路径 `current_conversation_tasks[client_uid] = asyncio.create_task(...)`（:99-109）**覆盖旧 task 而不 cancel**——同一客户端快速连发两次触发，第一个 task 变孤儿（旧流继续跑、继续发 TTS，新流同时开跑）。群聊路径（:78-96）运行中新触发直接丢弃。主动发言（:37-46）：未配置 proactive prompt 时回退硬编码 `"Please say something."`（:43），metadata 带 `skip_memory/skip_history`。
- `single_conversation.py:88-123`：agent 流里透传的 `tool_call_status` dict 原样转发给 UI（:98 注入 `name` 字段）；收尾 `await asyncio.gather(*task_list)`（:141-142）把 TTS task 挂在会话 task 的取消语义上。
- `conversation_utils.py`：翻译只作用于 `tts_text`（:97-100，显示文本保持原文），且 `translate_engine.translate(...)` **同步 HTTP 在事件循环上**（:99）；回合背压 :162-190（等前端播放完成）；音频输出路径在 async 函数里同步做 pydub 解码+base64（:124-129）。
- `group_conversation.py`：`group_queue = list(group_members)`（:63，set 序→非确定序）→ `pop(0)`（:110-112）→ 说完 `append` 回队尾（:306）= **无轮次上限的自我延续 FIFO 轮转，没有 LLM 决定谁先说话**；群聊打断按发起者 key 查表而 task key 是群主（:54 每成员一个 TTSTaskManager），打断会找错键。

### 2.3 agent 核心与四装饰器（A 级，本文核心）

**契约**：`AgentInterface` = `chat() -> AsyncIterator[str | dict]` + `handle_interrupt(heard_response)` + `set_memory_from_history`（`agent/agents/agent_interface.py`）。`AgentFactory`（`agent/agent_factory.py`）if/elif 分发四种 agent：basic_memory / mem0 / letta / hume。

**装饰器栈**（`basic_memory_agent.py:586-593`，`chat():664-671` 每次调用重建整栈）：

```python
(response_iterator
  |> sentence_divider |> actions_extractor |> display_processor |> tts_filter)
```

`agent/transformers.py`（218 行，A 级逐行）：

1. `sentence_divider:12-55`——把 token 流切成「句」，dict 事件先 flush 挂起句子再直通（经 `SentenceDivider.process_stream:566-574`）。这是唯一改变流粒度的一层。
2. `actions_extractor:58-100`——对每句调 `live2d_model.extract_emotion`（:86）把 `[emotion_key]` 换成 `actions.expressions`（索引列表，`agent/output_types.py:10-12`：expressions/pictures/sounds 三字段——后两者**前端零消费**，见 §2.9）。含 think START/END 标签的句子跳过表情抽取（:82-85）。
3. `display_processor:103-154`——think START→`"("`、END→`")"`（:135-140），生成 `display_text`；表情关键词**不从这里删除**（`remove_emotion_keywords` 全仓零调用，§2.9）。
4. `tts_filter:157-217`——think 内容 → `tts=""`（:189-190）；其余走 `filter_text(...)`（星号/括号/特殊字符清洗，`utils/tts_preprocessor.py:7-80`，按 config 开关）。兜底 `TTSPreprocessorConfig()` 无参构造会因必填字段抛 ValidationError（:180，潜在，实际调用方都传了配置）。

**顺序保证的实质**：四者都是 `async generator → async generator` 的包装，无并发无重排；**顺序 = 装饰顺序**，且四者都对 `dict` 项原样透传——这就是 tool 事件（`tool_call_status`、`final_tool_results`）能穿过整条呈现管线直达 UI/会话层的原因。管线语义总结：**文本进、句出；标签进、括号出；非语音内容出静音；事件穿透。**

**记忆手术**（`basic_memory_agent.py:195-223`，逐行）：

```python
if self._memory and self._memory[-1]["role"] == "assistant":
    self._memory[-1]["content"] = heard_response + "..."
interrupt_role = "system" if self.interrupt_method == "system" else "user"
self._memory.append({"role": interrupt_role, "content": "[Interrupted by user]"})
```

前提：`set_system:119-126` 把打断指令拼进 system prompt；`_add_message:128-174` 把多模态 content 拍平为纯文本（**图片从不进记忆** :140-144）、去重连续相同项（:167-172）。`interrupt_method` 按 provider 各有默认（`config_manager/stateless_llm.py:11-13` 及 12 处默认值：ollama/lmstudio/openai/gemini 等用 "user"，claude/deepseek 等用 "system"）。手术语义：LLM 下一轮看到的是「我讲到 X 就被你切了」——不是删除、不是猜测，是被听证据锚定。

**tool 三模式**（`chat()` 内部按 LLM 类分派，:608-619）：

- `ClaudeAsyncLLM` → `_claude_tool_interaction_loop:290-401`：原生 tool_use/tool_result 循环。
- `OpenAICompatibleAsyncLLM` → `_openai_tool_interaction_loop:403-579`：原生 tool_calls 循环；provider 拒绝 tools 时返回 sentinel `"__API_NOT_SUPPORT_TOOLS__"`（`openai_compatible_llm.py:220-226`）→ `prompt_mode_flag=True` 并把 system 换成 `self._system + mcp_prompt_string`（:415-422）+ 调 `self._tool_manager.disable()`（:490）——**`ToolManager` 没有 `disable()` 方法**（本轮全文复核 `mcpp/tool_manager.py` 仅 50 行：`get_tool` :32-40、`get_formatted_tools` :42-50，无 disable）→ 降级路径一旦触发即 AttributeError（潜在崩溃，静态判定）。
- 其余 LLM 类（template/llama_cpp 等）→ **完全不做 tool calling**，仅 warning（:617-619）。

群聊提示以 user 消息注入记忆（`start_group_conversation:677-702`，`{human_name}`/`{other_ais}` 填充 :693-695）。

### 2.4 stateless LLM 适配层（A 级）

- 契约 `StatelessLLMInterface`：`chat_completion(messages, tools, tool_choice) -> AsyncIterator[str | dict]`——与 agent 层同一「混合流」思想：文本 token 与结构化事件（tool 累积、状态）共用一条流。
- `openai_compatible_llm.py`（241 行）：tool_calls 跨 chunk 按 index 累积 args 字符串（:88-185，:166-168 拼接）；空 `choices` 防御（:118-119）；**错误路径把人类可读错误串 yield 进 token 流**（:207-232）——错误文本会被句切分、显示、**念出来**；tool 不支持 sentinel :220-226；`finally: await stream.close()`（:234-240）是打断时的 socket 拆除点（配合 task cancel，设计正确）。
- `claude_llm.py`（246 行）：事件 dict 协议（:99-110）；**`max_tokens=1024` 硬编码**（:126）；image_url→Claude image 转换（:50-67）；tool_result 强制 role=user（:68-78）；`input_json_delta` 累积（:166-178）；错误先 yield 后 raise（:238-243）。
- `stateless_llm_with_template.py`：**`async def` 里用同步 `requests`**（:160-170，阻塞事件循环）；except 分支引用不存在的 `self.base_url`（属性实为 `completion_url`，:173）→ 错误路径必 AttributeError；`stream` 恒 None，finally 关闭是死代码（:141,181-184）。
- `agent/stateless_llm_factory.py:13-79`：openai_compatible/openai/gemini/zhipu/deepseek/groq/mistral/lmstudio 全部折叠到同一个 `OpenAICompatibleLLM`——12 个 provider 名、4 个实现类。if/elif 无注册表；加 provider 要改 4-5 处（见 §2.10）。

### 2.5 Tool / MCP 线（A 级）

**这就是用户说的「agent 设计特别棒」的本体，也是它最脆的地方。**

- 唯一工具契约 = **MCP**。根目录 `mcp_servers.json`（本轮亲验内容：`time`→`uvx mcp-server-time --local-timezone=Asia/Shanghai`、`ddg-search`→`uvx duckduckgo-mcp-server`）+ `conf.yaml` 的 `mcp_enabled_servers` 白名单（`config_manager/agent.py:35`）。`.gitignore:76` 忽略 `mcp_servers.json` 但该文件已被跟踪——本地改 = 任意 stdio 进程启动（server.py 无确认）。
- `mcpp/types.py:6-93`：ServerConfig/FormattedTool/ToolResult 等 dataclass。
- `mcpp/server_registry.py:13,48-91`：解析 JSON、运行时探测 `npx`/`uvx`/`node` 是否存在（:37-46）；**`timeout` 字段从 JSON 到达是 int 而非 timedelta**（:89）——配置项形同虚设。
- `mcpp/mcp_client.py:43-81`：懒启动 stdio 会话（AsyncExitStack 保生命周期）；`list_tools` 缓存（:83-99）；`call_tool`（:101-155）错误返回带 `isError` 的结构。
- `mcpp/tool_adapter.py:18-83`：全量发现（**每次发现开一个一次性 MCPClient** :34）；`construct_mcp_prompt_string:85-135` 产出文本目录；`:137-217` 分别整形为 OpenAI 与 Claude 的 schema 格式。三个产物：prompt 文本、OpenAI schema、Claude schema。
- `mcpp/tool_manager.py`（50 行）：只存三产物 + `get_tool`；无 `disable()`（见 §2.3 崩溃点）。
- `mcpp/tool_executor.py`：解析三模式工具调用（:27-76）；结果按模式整形（:78-122）——**OpenAI 模式 `str(result_content)`（:113）把 MCP 的 image content block 直接字符串化销毁**；`execute_tool:155-301` 先 yield `tool_call_status` 事件（start/complete/error，直达 UI 显示「正在搜索…」）再产 `final_tool_results`；stagehand `browser_view` 特判 hack（:281-287）；`run_single_tool:303-382` 用**裸工具名**查 `related_server`——**无命名空间，跨 server 同名工具互相遮蔽**。
- `mcpp/json_detector.py:90-121`：prompt-mode 兜底解析器——逐字符括号计数，**不识别字符串字面量**。探针复现（仓库自带 venv，`-I`）：

  ```
  输入含 {"mcp_server": "time", ... 字符串内含 "{" ...}
  → WARNING ... JSON structure found but parsing failed
  → detected: [[]]   all: []      # 控制组（字符串内无花括号）解析成功
  ```

  即：**LLM 只要在 arguments 字符串里输出一个花括号（代码片段、嵌套 JSON），工具调用就永久解析失败**，agent 卡在 prompt-mode 死循环（每轮重发同一 prompt）。
- `service_context.py:94-187` `_init_mcp_components`：ServerRegistry→ToolAdapter→ToolManager→MCPClient→ToolExecutor 装配；**做两次全量发现**（`get_tools:126` 与 `get_server_and_tool_info:138`）——每个 server 启动两遍。失败置 sentinel 字符串降级。
- `web_tool/`（4 文件 528 行，B 级）：与主 UI 完全分离的「引擎复用页」——`POST /asr`（multipart WAV）+ `/tts-ws` + 从 `/cache/` 取文件；原生 JS，无框架无 LLM 无 Live2D；比主 UI 健壮（`main.js:243` 有 5s 自动重连、`fetchWithRetry` 3 次）。这是「把引擎当工具」的产品直觉，但止步于调试页。`scripts/run_bilibili_live.py` 是 bilibili 弹幕直播入口（`live/bilibili_live.py` 351 行，`live_config.bilibili_live.room_ids/sessdata`，仅此一平台）。
- 与「skill」的边界（**诚实**）：OLLMV 没有 skill 抽象。能力 = ① MCP tools（运行时发现）+ ② `tool_prompts` 九项提示词文件（编译期能力说明书，§2.10）。没有工具分组/profile/权限层，没有「工序」概念。

### 2.6 记忆与会话历史（A 级）

- basic_memory_agent 的记忆 = 单会话 `self._memory` list（system + 交替 user/assistant + tool 轮）；多会话隔离靠 `conf_uid`。
- `chat_history_manager.py`（374 行）：每会话一个 `chat_history/<conf_uid>-<id>.json`；文件名正则白名单（:19-27）+ basename 净化（:30-38）+ 穿越检查（:52-60）——**这是全仓写得最严的路径守卫**（对比 §2.10 角色切换守卫的弱前缀）。`store_message:93-147` **每条消息整读整写整个 JSON**；`update_metadate`（拼写错误）`:170-206`；`get_history_list:250-308` 把「列表」实现成有副作用的操作——顺带删除空历史（:293-299）。历史文件**没有所有者**：任何连接可列表、可读取、可删除任意 uid 的历史。

### 2.7 句切分与呈现变换（A 级 + 探针）

`utils/sentence_divider.py`（608 行）核心语义：

- `COMMAS`（:10-28）逗号字符表**含重复项**（全角逗号 `，` 在 :13 与 :27 重复登记）；`END_PUNCTUATIONS :30`（含 `...`、`。。。`）；`ABBREVIATIONS :31-46`（`Dr.` 在 :34/:45 重复）；`SUPPORTED_LANGUAGES :49-73`（23 个 pysbd 码）。
- `comma_splitter:122-141`：faster_first_response 首句快切（触发点 `_process_buffer:493-507`，只切第一句一次）。**按 COMMAS 表序而非文本位置选逗号**——探针复现：`"a،b,c"` → `('a،b,', 'c')`（阿语逗号在文本最左却切在 ASCII 逗号）；空文本返回 `([], '')`——首元素类型不一致（:134）。
- `segment_text_by_regex:173-210`：**探针复现三例破坏**（`./.venv/bin/python -I`，不改仓库）：
  - `"Wait... later"` → `(['Wait.', '.', '.'], 'later')`：alternation 按 END_PUNCTUATIONS 原序，单字符 `.` 先于 `...` 命中（:192），省略号永远不整体匹配；
  - `"3.14 is pi"` → `(['3.'], '14 is pi')`：小数点切句；
  - `"Dr. Smith went out."` → 只出 `(['Smith went out.'], '')`：**命中缩写时整句连同缩写被丢弃**（:203-205 `remaining_text = remaining_text[end_pos:]`），数据丢失。
- pysbd 路径 `:213-266`：每 chunk 新建 Segmenter、每 chunk 重跑 langdetect（:229，短文本非确定）；最后一句除非标点收尾否则扣住（:246-252）；任何异常回退 regex（:263-266）。
- tag 栈（`<think>` 状态机）：`TagState :269-276`、`_extract_tag :342-403`（END 不匹配时只 warning 不 pop，栈残留）、`_process_buffer :405-526`（tag 在头部→tag 单独成句 :430-441；tag 在中部且前有标点→只 yield 完整句，**未成句尾巴被整段消耗丢弃** :459-460，推演：`"Hi.there<think>"` 只出 `"Hi."`，`there` 蒸发）。`process_stream:549-591`：dict 先 flush 再直通（:566-574）。
- `utils/tts_preprocessor.py:7-80`：清洗顺序=星号→方括号→圆括号→尖括号→特殊字符→翻译（translator 参数在此管线内实际未接线，真实翻译在 `conversation_utils.py:97-100`）；`_filter_nested:108-138` **未闭合左括号吞掉此后全部文本**（`"[abc"` → `""`）——LLM 输出半个括号，后半句静音。NFKC 后仅保留 `L*/N*/P*/空白`，emoji 从 TTS 文本删除（不影响字幕与记忆）。
- `utils/stream_audio.py:8-80`：payload 形状 `{type:"audio", audio: base64 整 wav, volumes:[RMS/20ms 归一化], slice_length:20, display_text, actions, forwarded}`；全零音频抛 ValueError（:23）；解码+ffmpeg+base64+逐 chunk RMS 全同步、跑在 async 上下文。

### 2.8 音频引擎（B 级，关键行本人抽查）

- **TTS 契约 = 同步「给我整文件路径」**（`tts/tts_interface.py:27-40`）；默认实现 `asyncio.to_thread`（:9-25）。grep 证实 19 个引擎无一覆写 async 版——**全部整文件交付**（纠正一个常见误读：它们不在事件循环上跑，但也没有任何一个真流式）。cache 目录写死相对路径（:61-81）。
- `tts/tts_factory.py`：19 个 if 分支惰性 import（azure/bark/edge/pyttsx3/cosyvoice×2/melo/x_tts/gpt_sovits/siliconflow/coqui/fish_api/minimax/sherpa/openai/spark/elevenlabs/cartesia/piper）。注意 `TTSConfig.tts_model` Literal（`config_manager/tts.py:686-705`）只有 **18** 值——**pyttsx3 在工厂里存在却无法经 conf.yaml 选中**（工厂/配置可达性缺口）；`check_tts_config`（:798-816）是对构造期已校验模型的冗余二次校验。
- 局部流被文件契约截断：openai_tts `stream_to_file`（:90-100）、elevenlabs 逐块写盘（:97-113）、gpt_sovits `streaming_mode`（默认值拼写错误 `"ture"`，:23）只是表单字段、siliconflow 的 `stream` 发进 JSON 但用非流式 requests 整收——**配置里的 stream 开关全部不影响客户端交付**。edge/openai TTS 失败返回 None 而非异常 → `prepare_audio_payload(None)` 走静音显示分支。
- `conversations/tts_manager.py:92-114` 重排 sender 是本仓最有价值的一段并发代码（§3.3）；缺陷：sender 只捕 CancelledError（:113），`websocket_send` 抛普通异常 → payload 已 pop、序号断档、sender 死亡，**其后所有音频永久卡死**（推演）；`clear():174-182` 只清 task_list 不 cancel 在跑的 TTS；取消路径下 `to_thread` 无法中断工作线程 → cache 文件泄漏（推演）。
- ASR：7 个引擎（`asr_factory.py:7-62`）**全部 batch**，接口收 VAD 攒出的整段 ndarray（`asr_interface.py:11-25`，SAMPLE_RATE=16000 :7）。azure_asr 反而**覆写 async 版后直接调同步 `recognize_once()`**（:92，阻塞事件循环）。`asr/utils.py` 是模型下载工具不是 VAD 裁剪（grep `trim` 0 命中，纠正误读）。
- 服务端 VAD `vad/silero.py`：阈值组 :14-21（prob 0.4/60dB/3 hits/24 misses）；**<512 采样的尾巴直接丢**（:57-58，跨调用无余数缓存）；torch 前向同步跑在事件循环（:61-62 ← `websocket_handler.py:496`）；**短语音（≤30 块 ≈0.96s）既不产出也不清缓冲**（:181-184 的 `len(self.probs) > 30` 门）；`PAUSE` 事件→前端发 control interrupt（:497-500），`RESUME` 忽略（:501-502）。**会话主路的 VAD 其实在浏览器**（C 级：MicVAD v5，阈值 0.50/0.35/35 帧，JS@1320010/1322640）；服务端 VAD 是可选项。
- `sentence_divider` 的类默认 `valid_tags` 含 `["think"]`（:318），装饰器默认 `[]`（transformers.py:40）——think 处理恒开，用户只能开提示词不能关处理（`prompts/utils/think_tag_prompt.txt` 只是引导）。

### 2.9 表情 / 模型元数据 → LLM 注入（A 级 + C 级）

服务端链路（全部 A 级）：`model_dict.json`（顶层 list，当前仅 `mao_pro` 一条：name/description/url/kScale/initialXshift/initialYshift/kXOffset/idleMotionGroupName/**emotionMap**/tapMotions）→ `live2d_model.py:84-144` `_lookup_model_info`（找不到**直接 KeyError**，无回退 :134-138）→ `:48-53` 建 `emo_map`（key 小写化）与 `emo_str`（`"[key], [key], …"`）→ `service_context.py:456-459` 把 `live2d_expression_prompt.txt:5` 的 `[<insert_emomap_keys>]` 占位符替换成 emo_str → LLM 输出 `[joy]` → `extract_emotion:146-172` 扫出 emotionMap **值（即 expressions 数组索引）** → `actions.expressions` → 前端 `setExpression(name)`。

前端消费真相（C 级 bundle 取证，本轮逐条判定）：

- 表情按**数组索引**取名（JS@1332775 `getExpressionName(idx)→setExpression`）；默认表情复位取 `expressions[0]`（JS@1333105，`defaultEmotion` 键在 bundle 只此一处引用且 model_dict 没有 → 永远 index 0）。
- `emotionMap` 在 bundle **只出现 1 次**且无消费点（JS@1637843 默认字面量）——emotionMap 是纯服务端概念。`idleMotionGroupName` **0 命中**：待机组硬编码 `"Idle"`（JS@1360172），model_dict 值只是恰好同值才没出事。`kXOffset/initialXshift/initialYshift` 0 消费者。`volumes/slice_length` 服务端认真计算传输、客户端解构里**不含这两个字段**（JS@1362150）——死数据。`actions.pictures/sounds` 客户端 0 处理（服务端 `output_types.py:10-12` 定义）——死协议。
- 说话动作硬编码 `"Talk"`（JS@1362992），**mao_pro model3.json 无 Talk 组**（只有 `Idle` 与**空组名** `""`，model3 :45-71）→ mao_pro 说话时动作恒 no-op。LipSync 组 = `ParamA`（日语元音参数，:73-89），mao_pro **不存在 ParamMouthOpenY**（cdi3 128 参数计数 0；moc3 strings 0）。
- **mao_pro 的 emotionMap 映射本身是错的**（exp3 内容实测比对）：fear→exp_02=**笑眼**（`ParamEyeLSmile=1`）、anger→exp_03=**28 参数全 0 的复位空脸**；8 个表情只有 4 个索引可达，真正带情绪的 exp_05..08（皱眉/委屈/瞪眼/撅嘴）**永远不可达**。链路与机制都对，表是手工维护且漂移的——这是「能力词表自动注入」机制缺少校验门的直接反例。
- 触摸：`tapMotions`（JS@1567311/1544088，按 hit-area **Id** 匹配 → 加权随机播 motion，Force 优先级）**纯客户端本地**，agent 从不知道被摸了；`hitAreas`/model3 `HitAreas[].Id`（mao_pro 的 Name 为空串）。**agent 只能设表情索引；点击/触摸从不回传，motion 只能自动 Talk。**

### 2.10 配置体系与升级（A/B 级）

- **pydantic 双语 schema**：`config_manager/main.py:16-18` 顶层三段（system/character/live）；`i18n.py:20-139` `I18nMixin` + `Description(en/zh, notes)` 双语字段说明注册表；`get_field_options`（:125-139）机制存在但无人挂 `options`、无人调用——半成品。
- `config_manager/utils.py`：多编码容错读（:87-101）；**`${ENV}` 替换发生在 YAML 解析之前、作用于整文件文本**（:39-46，未设置的变量原样保留字面量）；`validate_config:55-74` 失败时**把整份 config_data 打进日志**（:71-73）——密钥进日志的路径；`scan_config_alts_directory:127-171`（characters/ 全量 yaml = UI 角色列表）；`save_config:107-124`（by_alias dump）。
- `system.py:7-39`：`conf_version` 必填（:10）、port 0-65535 校验（:34-39）、`tool_prompts: Dict[str,str]` **自由 dict 无白名单**（:14）、`enable_proxy` 默认 False（:15）。
- `agent.py:202-206`：agent 四选一 Literal + `llm_configs` 12 provider 配置池；`BasicMemoryAgentConfig.llm_provider` 12 值 Literal（:17-30）；`faster_first_response/segment_method/use_mcpp/mcp_enabled_servers`（:32-35）。
- `character.py:13-29`：角色配置必填四件套（conf_name/conf_uid/live2d_model_name/persona_prompt）+ 五引擎块——**persona、引擎、模型三者在同一份 YAML 里，没有分层**（§3.5）。
- **角色热切换**（`service_context.py:471-558`）：`switch-config` → `os.path.normpath + startswith(characters_dir)` 守卫（:495-499，字符串前缀而非路径边界——经典 weak-prefix；绝对路径能拦、兄弟目录 `characters_x` 模式可越过；文件名不限 .yaml）→ 增量 yaml **deep_merge 进当前 character_config**（:504-506,561-571）→ 全量 pydantic 校验（:513）→ `load_from_config` 逐引擎 diff-then-reuse（:313-361：typed config 相等则复用引擎实例；provider 名即配置 key `getattr(cfg, cfg.xxx_model)` 的隐式约定 :339）→ 推 `set-model-and-conf`/`config-switched`。**「角色 = 一个增量 yaml，秒切换引擎+人设+模型」是 OLLMV 「紧致」感的机制来源。**
- `construct_system_prompt:435-469`：按 tool_prompts YAML 书写顺序拼接；跳过 `group_conversation_prompt`/`proactive_speak_prompt`（:448-452，改按事件注入）；`mcp_prompt` **先读文件再丢弃**（:454 vs :461-462，白读 bug）；实际 MCP 提示词运行时由 registry 生成（:121-131）。
- 升级线：`run_server.py:134-138` 每次启动只跑 `sync_user_config`（备份 conf.yaml→.backup）；完整合并/迁移只在手动 `python upgrade.py`（`upgrade.py:24-158`：确认→stash→fetch/pull→submodule update→`update_user_config`→stash pop）。`config_sync.py:52-96` 三步：缺 key 补默认（:120-145）、**多余 key 直接删**（:202-237）、注释同步；版本迁移 `version_manager.py:21-38` 只有一条任务 `[v1.1.1, v1.2.1) → to_v_1_2_1`（模型改名映射）。conf.yaml **不入库**（`.gitignore:2`）；本 checkout conf.yaml 含一个真实格式 sk- 型 key（conf.yaml:108——**仅指出存在，不记录值**；其余 14 处为占位符）。
- **加一个 provider 的真实成本**（B 级核账）：① 配置类 ② 配置池字段 + Literal 枚举 ③ 工厂 if 分发 ④ 引擎实现 ⑤ 两份 config_templates（CLAUDE.md:122-128 明文规定五处联动）。无 entry point、无注册表、无插件机制。

### 2.11 客户端取证（C 级；本轮逐条重验前一轮文档的客户端断言）

前一轮文档 24 条客户端断言的判决（本轮 bundle 取证）：**证实 13、证伪 8、部分证实/证伪 3**。要点修正：

- 证实：默认地址 `ws://127.0.0.1:12393/client-ws`（JS@705830，全 bundle 无 `wss://`）；三级 interrupted 守卫（消息层/入队层/播放层）；TaskQueue(20ms)；`_wavFileHandler` 口型（RMS×1.5 钳 1 权重 4，JS@1538609）+ 猴子补丁增强（JS@1363750 ×2）；打断停音停口型完整（`AudioManager.stopCurrentAudioAndLipSync` JS@1331650）；浏览器 VAD 数值（0.50/0.35/35 帧，localStorage `vadSettings`）；语音可打断（onSpeechRealStart 触发 interrupt）；Electron 桌宠模式（ipc 36 处、`update-component-hover`、pet mode 三阶段握手）。
- 证伪：不存在 `/v1/api`、`/v2/audio` 等版本化路径（0 命中）；**主 UI 无自动重连**（onclose 只置状态；重连=点小圆点；讽刺的是 web_tool 反而有）；客户端无鉴权头（bundle `Authorization` 0——浏览器 WS 本就不能设 header）；`volumes/slice_length` 死数据；agent 不能触发 pictures/sounds/motion groups；客户端不读 emotionMap/idleMotionGroupName/kXOffset/initialXshift/initialYshift；`ParamMouthOpenY` 回退对 mao_pro 不可能（无该参数）。

## 3. OLLMV 真正强的五件事（证据）

1. **呈现管线与 agent 解耦的四装饰器**（`agent/transformers.py:12-217`；`basic_memory_agent.py:586-593`）。任何 yield token 的 agent 白得句切分/表情抽取/显示变换/TTS 过滤；dict 事件穿透让工具状态免费上 UI。**这是全仓最干净的架构决定**——四个 ~50 行纯函数，无框架无注册表。
2. **打断是一等公民，且以「被听证据」做记忆手术**（`basic_memory_agent.py:195-223`；`openai_compatible_llm.py:234-240` 流拆除；C 级：前端三级守卫 + VAD→`interrupt-signal{heard}`）。LLM 下一轮读到 `heard + "..." + "[Interrupted by user]"`——不删记忆、不装没发生。我方存在性判据里「通话页打断质量」，OSS 里只有它做到这个程度。
3. **并行合成、按序交付的 TTS 管线 + 回合背压**（`tts_manager.py:70-90` create_task 并行、`:99-111` 序号重排；`conversation_utils.py:162-190` 等 `frontend-playback-complete` 再收回合）。延迟与顺序同时保——这是语音对话体感的地基（尽管 sender 有死亡卡死缺陷，机制本身对）。
4. **能力词表从模型 manifest 自动注入 LLM**（`live2d_model.py:47-53` emo_str ← emotionMap；`service_context.py:456-459` 占位符替换；`transformers.py:86-88` 回填 actions）。persona 与呈现能力同步不需要人手写清单——机制正确（mao_pro 的错表恰好证明的是「缺校验门」而不是「机制错」）。
5. **零代码的「角色 = 一份增量 YAML」热切换 + 双语自描述配置 + 版本迁移线**（`service_context.py:471-558` deep_merge 热切换；`config_manager/i18n.py` 双语 Description；`conf_version`/`upgrade_codes` 迁移；`characters/` 放文件即进 UI）。普通用户改文件就能造角色、换引擎、换模型——「紧致」的产品感来自这里，也是我方「用户自定义生产」需求的最低成本参照。

（候补第 6：tool 三模式降级策略 `basic_memory_agent.py:484-494` + sentinel 协议——弱 provider 也保工具可用性的产品意识；但其实现带崩溃与解析器缺陷，见 §4.4。）

## 4. 弱点与空洞（逐一验证，无臆测）

### 4.1 工程质量：零测试，且没有能挡回归的 CI

- 本轮亲验：全仓（排除 .venv/frontend）无任何 `test*` 文件、无 tests/ 目录、pyproject 无 pytest/coverage、`import pytest/unittest` 0 命中；`.github/workflows/` 六个 = ruff/codeql/docker-blacksmith/create_release/fossa/update-requirements——**没有任何一步执行代码或断言行为**；唯一每 PR 的门是 ruff。`pre-commit`/`ruff` 在**运行时依赖**里（pyproject:23,30），pytest 根本装不上。`CLAUDE.md:142-148` 自述质量手段 = 「manual testing」。最该测的 1200 行纯逻辑（sentence_divider/websocket_handler）裸奔。
- 单人主导：913 commits / 35 作者 / 主作者 643 ≈70%；2026 全年仅 9 commits（最新 2026-05-15）。仓库自述文档还会说谎（CLAUDE.md:92）。
- `agent/agents/mem0_llm.py` 是 **0 字节已发布模块**；`hume_ai.py:240` 打断「not implemented」静默无效；asr/vad 的「只校验选中模型」validator 因字符串比较错配**永不生效**（`asr.py:360-373`、`vad.py:57-62`）；死代码成簇（MessageType、has_punctuation、remove_emotion_keywords、load_persona、concise_style_prompt.txt、get_field_options、complete_response）。

### 4.2 安全：整个模型假定「本机单一可信用户」

零鉴权（routes.py:29-33 无 Depends）+ CORS `["*"]` 与 `allow_credentials=True` 并存（server.py:83-88）+ 无 Origin/WS 握手校验 + 明文 ws://（run_server.py:159-164 无 ssl）+ `/docs` 开放 + `/asr` 无体积上限 + `/tts-ws` 可驱动付费 TTS + `mcp_servers.json` 本地改=任意进程启动 + `/live2d-models/info` 目录枚举。角色切换路径守卫是 weak-prefix（`service_context.py:495-499`，对比 chat_history 的正则白名单写法反而健全）且切任意 characters/ yaml 可触发 `${ENV}` 展开（config_manager/utils.py:39-46）。conf.yaml 注释鼓励改 `0.0.0.0`——与上述全部后果直接冲突。`validate_config` 失败把含密钥的整份配置打进 debug 日志（config_manager/utils.py:71-73）。

### 4.3 会话与并发语义：单进程假设处处漏

- 断连清理死代码 → **MCP 子进程泄漏**（§2.1）。
- conversation task 槽覆盖不 cancel → 双流并发（§2.2）。
- 群聊 = 无 LLM 决策的无限 FIFO 轮转 + 打断 key 错位（§2.2）。
- `wait_for_response` 无 timeout（message_handler.py:16-31 ← conversation_utils.py:173）：前端不回包回合永久挂起；TTS sender 异常后序号断档永久卡死（tts_manager.py:113，推演）。
- `load_cache:191-198` 把 live2d_model/asr/tts/vad/agent/translate **按引用共享给所有会话**，只有 MCP 每会话隔离——两个客户端并发对话互相踩 agent 记忆。历史文件无所有者。
- 无界缓冲成串：sentence_divider._buffer、received_data_buffers、proxy deque（:15）、TTS payload queue、VAD probs/bytes（§2.1/2.7/2.8）。
- 伪流式全链路：TTS 整文件契约 → 整 wav base64 → 前端整段播；ASR 纯 batch；服务端 VAD 丢尾巴且短语音不产出；多处同步阻塞在事件循环（silero torch :61-62、pydub+ffmpeg+base64 tts_manager.py:143、azure recognize_once :92、translate conversation_utils.py:99、with_template requests :160-170）。
- 无多用户/账号/租户/配额概念——client_uid 每次连接随机（routes.py:33），断连即无身份。

### 4.4 文本/工具正确性（探针实证 + 静态判定）

- **prompt-mode JSON 探测器不识别字符串字面量**（`json_detector.py:90-121`）：花括号出现在字符串值内即解析失败（§2.5 探针复现），工具调用在弱 provider 上不可靠。
- **regex 句切分破坏**（§2.7 探针复现）：`...` 逐点切碎、`3.14` 切句、缩写命中时整句丢弃。默认 `segment_method` 配 pysbd，但任何 pysbd 异常即回退到这条坏路（:263-266）。
- `comma_splitter` 按表序切逗号、空文本类型不一致（探针复现）。
- think 标签栈 END 不匹配残留（:396-401）；tag 边界未成句尾巴丢弃（:459-460）；`_filter_nested` 半个左括号吞后文（tts_preprocessor.py:108-138）。
- `ToolManager.disable()` 不存在而调用存在（basic_memory_agent.py:490）——降级路径必崩（静态判定）。
- LLM 错误串进语音流（openai_compatible_llm.py:207-232）；`max_tokens=1024` 硬编码（claude_llm.py:126）；图片从不进记忆（basic_memory_agent.py:140-144）；OpenAI tool result `str()` 销毁 image block（tool_executor.py:113）；tool 无命名空间跨 server 遮蔽（tool_executor.py:303-382）；MCP 双重发现（service_context.py:126,138）。
- 每条聊天历史整读整写（chat_history_manager.py:93-147）。

### 4.5 产品结构空洞（对照我方定位逐条）

- **persona 与呈现未分层**：一个 character = 一份 YAML（persona_prompt + live2d_model_name + 五引擎配置，`config_manager/character.py:13-29`）。换形象=改同一文件；呈现实体没有所有权、版本、状态机。我方 persona/her 分层（ca_her_pack + ca_character_her + /v1/hers）正是这一刀的结构化版本。
- **无产线**：新增角色=手写 yaml + 手改 model_dict.json（characters/README「How to add new models」）；`upgrade.py` 只是 git 操作 + 配置迁移，不生产任何资产。表情映射表无校验直接漂移（mao_pro 4/8 表情不可达、fear=笑眼、anger=空脸，§2.9）——没有产线就没有质量门，这是因果不是巧合。
- **呈现是单向的**：agent 只能选表情索引；点击/触摸不回传（tapMotions 纯客户端，JS@1544088）；motion 只能自动 `"Talk"` 且对 mao_pro 是 no-op；pictures/sounds 死协议；volumes/slice_length 死数据。**「UI 可互动」是前端动画丰富，不是 agent 可感知的互动回路。**
- **桌面单机定位**：绑 localhost、conf 不入库、无账号、无服务端部署形态（dockerfile 存在但产品模型是桌面）；依赖 34 个强制（torch/onnxruntime/scipy/sherpa…全无条件），requirements.txt 钉死 305 包。移动端不在其能力半径内。
- **前端黑盒**：UI 在另一个仓库的 `build` 分支预构建产物里，主仓库无源码/无 sourcemap/无 lockfile——它的「UI 可互动」不可继承、不可改、只能重实现。
- **协议两侧腐化**：死数据与硬编码（§2.9/2.11）证明没有契约测试——这是零测试文化的直接后果。

## 5. 「我们只能更好」清单

每条 = 抄它什么(file:line) → 我们方案怎么改 → 落到我方哪个已定件。

1. **四装饰器管线** → 抄 `transformers.py:12-217` 的「纯 async 生成器包装 + dict 事件穿透」形状（尤其 `sentence_divider.process_stream:566-574`）。我方改法：服务端 agent 输出流本来就该发结构化 cue 而非文本让端上解析——把呈现管线定为纯函数层（架构优先纪律：纯决策层 TDD），每层单独可测（OLLMV 恰好证明无测试时这层会坏：§4.4）。落点：`cmp.cue` 协议 + `PresentationCueTest/ProtocolCodecTest`（已有测试套件继续钉）、Android 端零文本解析。
2. **打断记忆手术** → 抄 `basic_memory_agent.py:195-223` + per-provider `interrupt_method`（config_manager/stateless_llm.py:11-13）。我方改法：heard 文本不靠前端 VAD 猜——我方 Android 原生播放器有精确播放进度，`interrupt-signal` 携带**精确已播文本前缀**，手术输入更准；`[Interrupted by user]` 语义保留并进提示词契约测试；同时修它的两个洞（记忆只存文本→我方音频会话记 audio/text 双轨；hume 式后端静默无效→契约测试禁静默）。落点：通话链路会话状态机 + 服务端决策层 TDD。
3. **有序并行 TTS + 背压** → 抄 `tts_manager.py:70-90 + 99-111` 与 `conversation_utils.py:162-190`。我方改法：(a) payload 改原生流式（Android ExoPlayer/GL 音频驱动，废整段 base64——stream_audio.py:69 的 base64 是它的地板）；(b) sender 补 try/except + per-task timeout + clear 时 drain 队列（修 §4.3 三处卡死/泄漏）；(c) `wait_for_response` 带 timeout 降级（修 message_handler.py:16-31 永久挂起）。落点：通话管线 + Android 原生 GL 音频-口型驱动（替代 wavFileHandler 猴子补丁 JS@1363750）。
4. **能力词表自动注入** → 抄 `live2d_model.py:47-53` + `service_context.py:456-459` 占位符机制。我方改法：词表来源从「手工 model_dict.json」换成 **`ca_her_pack.payload` 的 effects/stickers 目录**（id+资源 hash+锚点声明），发布时 `validate_asset_pack`（Hermes 产线设计 T-10）做硬门——mao_pro 的错表（fear=笑眼、4/8 不可达）就是这个门存在的理由；强制白名单在 **cue mapper** 而非提示词（提示词只是引导，OLLMV 的 think 处理恒开、表情越界无拦截是反面教材）。落点：`ca_her_pack` payload + T-10 质量门 + `cmp.cue → StageEffectMapper` 白名单。
5. **「角色 = 增量 YAML」热切换** → 抄 `service_context.py:471-558`（deep_merge :561-571）的用户体感：列表来自目录扫描、切换即时生效。我方改法：角色切换 = identity resolution（一个函数两个消费者）+ `ca_character_her` 绑定 + release pin（HerRelease 语义）——可切换但**有版本、可回滚、可审计**，不是自由 yaml 合并；路径守卫抄 `chat_history_manager.py:19-27` 的正则白名单写法（不抄 :495-499 的 weak-prefix）。落点：`/v1/hers`（list/detail 已定）+ `ca_character_her` + wardrobe-spec 单一事实源。
6. **tool 三模式降级 + 工具状态上 UI** → 抄 sentinel 协议（`openai_compatible_llm.py:220-226` → `basic_memory_agent.py:484-494`）与 `tool_executor.py:155-301` 的 `tool_call_status` 事件流（「正在搜索…」的产品感）。我方改法：工具契约用 Hermes T-01…T-18 typed schema；**命名空间强制**（修 tool_executor.py:303-382 遮蔽）；prompt-mode 兜底禁用括号计数（json_detector.py:90-121 已实证可坏），改 JSON-schema 约束输出或按 provider 能力矩阵路由；修复 `disable()` 类崩溃教训——降级路径必须有测试（我方产线 G-A 契约门覆盖）。落点：Hermes skill profile（her-production 的 tool 层）+ 服务端工具执行层。
7. **双语自描述配置 + conf_version 迁移** → 抄 `config_manager/i18n.py:20-139` + `version_manager.py:21-38` 的思路。我方改法：用户自定义面**不是 500 行 YAML** 而是参数化模板槽（persona/her 分层 §6.3）——`ca_user_her` 实例走与系统 her 相同的下载/校验/安装链；配置迁移我方已在 DB 迁移层实践（0005 her_pack 方言分支），不需要它那种 git-stash-式用户配置同步。落点：`ca_user_her`（N11）+ Hermes 模板槽 + migrations。
8. **消息类型契约与代理复用** → 抄 `websocket_handler.py:78-98` 单表分发的可读性 + `proxy_message_queue` 的手机↔服务器复用同一管线的思路。我方改法：契约带鉴权（identity resolution 同源）+ **自动重连与续传**（OLLMV 主 UI 无自动重连，C 级证伪；web_tool 反而有——我方端上必须自动重连 + 回合幂等）+ 协议版本化（`response_model` 显式声明教训已在 persona/her 文档 §3.3 钉死）。落点：`/v1/hers` 风格的服务端 API + Android 会话状态机。
9. **触摸→动作的产品直觉** → 抄 `tapMotions`（C 级 JS@1544088 + model_dict.json:22-27）的「摸头有反应」体验。我方改法：触摸区进 `ca_her_pack.payload.touch_zones`（hitArea Id ↔ cue 映射），**且回传给 agent**——OLLMV 的 tap 是前端死循环、agent 不知情；我方触摸事件走 `cmp.cue` 上行进决策层，形成双向回路（「紧致」的另一半，persona/her 文档 §7 的判断）。诚实注：**我方端上 `touch_zones` 尚未实现（grep 0 命中）**，这是 payload 映射的第二刀待办，不是已完成件。
10. **引擎即工具的 web_tool 直觉** → 抄 web_tool 把 ASR/TTS 引擎暴露成独立可用面（4 文件 528 行即可）。我方改法：语音/形象生成能力不做调试页，做成 Hermes 工序 tool（T-* 清单已有），质量门与产物规格随工序走。落点：Hermes 产线七工序。
11. **依赖瘦身对照（负面教材）**：34 强制依赖（torch 无条件，pyproject:7-43）→ 我方服务端按 provider extra 分组、端上 GL 原生无 Python 栈——「紧致」在部署面同样成立。落点：server 打包纪律 + Android 原生 GL。

## 6. 诚实附注

1. **没有的东西（grep/find 证实，非推测）**：skill 概念（全仓 0 命中）；任何测试文件/pytest 配置/CI 测试步；任何鉴权/用户/所有权概念；流式 ASR；agent 可用的 motion/picture/sound 控制（前端无消费）；触摸事件回传；provider 注册表/entry point；`ToolManager.disable()`；mao_pro 的 ParamMouthOpenY 与 Talk 动作组。
2. **读不到/未验证的**：前端源码（submodule 预构建，无 sourcemap）——§2.11 的结论全部是 bundle 字符串/偏移取证；`Talk` 组在上游 Web 仓库是否可配置无法确认；vad worklet 帧长协商；各 TTS/ASR 库内部实现；§4 中标「推演」的卡死/泄漏路径（代码路径静态判定，未构造运行时复现；JSON 探测器与句切分两处已运行时复现）。
3. **行号漂移**：见 §0——4 个 src 文件有本地未提交改动（+3/+1/+5/+2 行，extra_headers 特性，不属于上游 992309c），引用行号以工作树为准。
4. **密钥纪律**：conf.yaml:108 存在一个真实格式 sk- 型 `llm_api_key`（其余 14 处占位符）；该文不入库（.gitignore:2，历史上 `84e27ab` 已从追踪移除）；`validate_config` 会把含密钥的配置打进日志（config_manager/utils.py:71-73）。**全部只记录存在与位置，未记录任何值。**
5. **我方侧诚实注**：第 5 节引用的我方已定件中，`touch_zones` 在 Android 端尚无实现（0 命中）；`ca_her_pack` 服务端 payload 仅结构校验（Hermes 产线设计文档「关键发现 2」），T-10 尚未存在；`/v1/hers`/迁移 0005 已在 `feat/role-pack-db-20261127` 线未合并。抄它、超过它的前提是我方这几刀落地。
