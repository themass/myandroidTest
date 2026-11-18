# 陪伴交互协议 v2 与通信架构

> 状态：**设计提案（未实现）**。本文替代 `companion-interaction-protocol-and-perception.md` 的 §4（传输）、§5（envelope）、§6（能力目录）、§8（音画同步）四节；其余章节（端侧感知、隐私状态机、分期）仍然有效，本文不重复。
> 日期：2026-11-18
> 读者：即将动工实现客户端传输层 / 服务端事件编译器的工程师。

---

## 0. 一页结论

| # | 决策 | 为什么 |
|---|---|---|
| D1 | 协议分两层：**核心信封（core）与领域 Profile（companion / perception / control）分离** | 核心只解决版本、排序、去重、过期、取消；领域可以独立演进和关闭 |
| D2 | 所有需要保序的消息走**单一 topic `cmp.cue`**；绝不把同一个有序流拆到两个 topic | LiveKit 不同 topic 之间无顺序保证 |
| D3 | 消息按**投递语义分三类**（A 必须到达 / B 有截止期 / C 可丢） | 可靠但过期的表现指令比丢了更糟：它会落在错误的那句话上 |
| D4 | 每条表现指令带 **`at` 时间锚（tagged union）**，锚在 `speech_id` 与段序号上，**不锚在包到达时刻** | 到达时刻受网络与 TTS 队列影响，抖动可达秒级 |
| D5 | **服务端只决定"做什么、在第几段、前后多少毫秒"；端侧独占"现在几点"** | 只有端侧有真实播放时钟（audio sink），服务端只能知道 TTS 队列 |
| D6 | 段（segment）是**一等公民**，有 `seg_idx`，明示 begin/end | 没有段就没有"说完这句再说下一句"的参照系 |
| D7 | 打断 = **`speech_id` 立刻作废**，之后该 id 的任何指令一律丢弃；不做协商、不做补偿 | 单调作废是最省事也最不会错的一致性模型 |
| D8 | 未知 `t`（类型）**静默忽略**并计数；未知字段忽略；缺必填字段丢弃整条；大版本不符整条忽略 | 前向兼容与"绝不半懂不懂地执行"二者必须同时成立 |
| D9 | 能力在 **hello 握手**时逐项协商，按 participant 各算各的；服务端广播交集，差异项用 `identities` 定向发 | 多端/老版本共存时不能假设大家一样 |
| D10 | 嘴型**不上行也不下行**，仍由音频 sink 的 RMS 本地驱动 | 每秒 60 个包的成本换不到任何东西；现有实现是对的 |
| D11 | 大于 2 KiB 的载荷（感知快照、landmark）走 **HTTP API**，不走数据通道 | 数据通道不是批量管道；HTTP 已经有鉴权、限流、重试 |
| D12 | 协议大版本 `v=2`，与现有 `v=1` 的 `companion.*` 并存**两个发布周期**，见 §15 迁移表 | 客户端发版不可控，必须能同时喂新老版本 |

一句话概括架构：**服务端编译"意图 + 顺序"，端侧编译"时间 + 像素"。** 中间那条线就是 `cmp.*` 四个 topic。

---

## 1. 范围与本文不做什么

**做**：wire format、topic 划分、排序/去重/过期语义、能力协商、语音-动作时序模型、打断取消、降级阶梯、迁移路径、故障用例。

**不做**：端侧 MediaPipe 具体管线（见 v1 §9）、Prompt 文本（v1 §7.3）、隐私状态机（v1 §10.3）、部署与成本（`global-rtc-deployment-and-cost.md`）。

**明确不引入**：自建 WebSocket 旁路、Protobuf、自定义二进制帧、跨房间总线。理由见 §4.2 —— LiveKit 现成能力已经够，多一条通道就多一处要保活、鉴权、监控的东西。

---

## 2. 已核实的事实（不是推测）

### 2.1 客户端（LiveKit Android 2.29.0，反编译源码核对）

| 事实 | 位置 |
|---|---|
| `publishData(data, reliability, topic, identities)`，KDoc 明写单包 **≤ 65535 字节** | `LocalParticipant.kt` |
| 可靠性枚举 `DataPublishReliability { RELIABLE, LOSSY }` | 同上 |
| 支持按 `identities` 定向投递 | 同上 |
| `sendText(text, StreamTextOptions)`，`StreamTextOptions` 支持 `topic`、`attributes`、`destinationIdentities` | `OutgoingDataStreamManager.kt:64`、`StreamTextOptions.kt` |
| `registerTextStreamHandler(topic, handler)` | `IncomingDataStreamManager.kt:48` |
| `RoomEvent.DataReceived(room, data, participant, topic, encryptionType)` | `Room.kt` |
| RPC 双向可用：`performRpc(destinationIdentity, method, payload, responseTimeout, maxRoundTripLatency)` / `registerRpcMethod` | `Room.kt:1218-1226` |
| `AudioTrackSink.onData(..., frames, absoluteCaptureTimestampMs)` —— **采集时间戳是暴露的** | `AudioTrackSink.kt` |

现有客户端常量（`LiveKitCompanionTransport.kt:568-574`）：

```
TEXT_IN_TOPIC  = "lk.chat"          // 上行文本
TEXT_OUT_TOPIC = "lk.transcription" // 下行字幕
TOPIC_TOUCH    = "companion.touch"
TOPIC_AVATAR   = "companion.avatar"
TOPIC_PROACTIVE= "companion.proactive"
TOPIC_ACTION   = "companion.action"
```

### 2.2 服务端（LiveKit Agents Python 1.8.11，venv 内源码核对）

| 事实 | 位置 |
|---|---|
| `publish_data(payload, *, reliable=True, destination_identities=[], topic="")` | `livekit/rtc/participant.py:315` |
| `SpeechHandle` 公开 `id` / `interrupt()` / `wait_for_playout()` / `wait_if_not_interrupted()` / `add_done_callback` / `scheduled` / `interrupted` / `done` | `livekit/agents/` |
| `TimedString`（str 子类，带 start/end）在 `agents/types.py:189`，经 `timed_texts_fut` 在 `voice/generation.py` 消费 | 同上 |
| `transcription_node` 在 `voice/agent.py:442` | 同上 |

> **结论**：语速对齐数据（TTS 给出的每词起止）服务端是拿得到的，但**播放起点**只有端侧知道。D5 由此而来。

### 2.3 现存的两处不一致（已修复）

> 下面两条已在 `2026-11-18` 处理完毕。结论和最初的判断不同：**两个都是孤儿代码，不是断掉的功能**。真正的确认流一直是通的，走 HTTP。

| 现象 | 核实结果 | 处理 |
|---|---|---|
| `entrypoint.py:31` 有 `TOPIC_CONFIRM = "companion.confirm"`，客户端**没有对应 handler** | 这个常量**两端都没实现过**：服务端无处 publish，客户端无 handler，`handleData` 里也没有。而确认流本身是通的——`publish_action` 发 `companion.action`，客户端解成 `NeedsConfirmation`，用户点按钮走 HTTP `POST /v1/actions/{id}/confirm`（`api/http/routers/actions.py`）。客户端 `CompanionTransport.confirm()` 是从未被 override、也从未被调用的空实现 | 删除服务端常量与客户端空方法。将来的控制面按 §11.3 的 `cmp.ctl` 定义，不复用这个名字 |
| `agent.py` 的 `build_event_sink` 调 `publish_data(...)` **未传 topic** | 这个函数**没有任何调用点**（`src/` 与 `tests/` 均无）。生产接线用的是 `AvatarPublisher`，它的 `_publish` 正确传了 `topic=TOPIC_AVATAR` | 删除 `build_event_sink`。上一版判断"形象事件被丢弃"是错的 |

---

## 3. 通信架构

```
┌──────────────────────── 端侧 (Android) ────────────────────────┐
│  UI / AvatarRenderer                                            │
│        ▲ AvatarCommand                                         │
│  ┌─────┴──────────────────────────────────────────┐            │
│  │ PresentationScheduler  (本方案的客户端核心)      │            │
│  │  · SpeechTimeline: speech_id → 段序列 → 锚点    │            │
│  │  · 播放时钟: audio sink 的 capture ts + 单调钟   │            │
│  │  · 截止期裁剪 / 作废丢弃 / 本地兜底动画          │            │
│  └─────▲──────────────────────────┬───────────────┘            │
│        │ cmp.cue (B类)            │ cmp.in (A类)                │
│  ┌─────┴──────────────────────────▼───────────────┐            │
│  │ ProtocolCodec: 信封校验 / 去重 / 版本 / 计数     │            │
│  └─────▲──────────────────────────┬───────────────┘            │
└────────┼──────────────────────────┼────────────────────────────┘
         │ LiveKit (data / text stream / audio / RPC)
┌────────┼──────────────────────────▼────────────────────────────┐
│  ┌─────┴──────────────────────────┬───────────────┐            │
│  │ ProtocolCodec (服务端镜像)                       │            │
│  └─────▲──────────────────────────┬───────────────┘            │
│  ┌─────┴──────────────────────────▼───────────────┐            │
│  │ CueCompiler  (本方案的服务端核心)               │            │
│  │  · LLM 结构化输出 → 段 → cue 计划               │            │
│  │  · 能力交集裁剪 / 定向路由                       │            │
│  │  · TTS 时长对齐（TimedString）→ 时长提示         │            │
│  └─────▲──────────────────────────┬───────────────┘            │
│        │                          │                            │
│  Brain / TTS / ASR (LiveKit Agents)                            │
└────────────────────────────────────────────────────────────────┘
```

**职责边界**（这是本方案最需要守住的一条线）：

- **服务端知道**：说什么、分几段、每段多长（TTS 后）、某动作应该在第几段之前/之后、用户刚打断了。
- **服务端不知道**：端侧现在播到第几帧、音频缓存还有多少、屏幕是不是在前台。
- **端侧知道**：真实播放位置。
- **端侧不知道**：这段话是什么意思、下一段还有没有。

任何一方越界都会产生"看起来能跑、偶发错位"的实现 —— v1 草案里 playback head 估算方案的失败就在于此。

---

## 4. 传输选择

| 用途 | 通道 | 可靠性 | 预算 |
|---|---|---|---|
| 表现指令（state/emotion/gesture/gaze） | data → `cmp.cue` | RELIABLE | ≤ 1.2 KiB/条，≤ 8 条/秒，≤ 40 条/回合 |
| 控制（hello / cancel / request / response） | data → `cmp.ctl` | RELIABLE | ≤ 2 KiB/条，事件驱动 |
| 上行输入（打断 / 触摸语义 / 确认结果） | data → `cmp.in` | RELIABLE | ≤ 1 KiB/条 |
| 感知遥测（在场、注视、粗略姿态） | data → `cmp.tel` | **LOSSY** | ≤ 512 B/条，≤ 1 条/秒 |
| 感知快照（landmark / 图像特征） | **HTTP** `POST /api/companion/v1/perception` | 由 HTTP 保证 | ≤ 256 KiB |
| 用户文本输入 | text stream → `lk.chat` | 流式 | 不限 |
| 字幕 / 屏幕文字 | text stream → `lk.transcription` | 流式 | 不限 |
| 音频（含嘴型驱动源） | audio track + `AudioTrackSink` | — | — |

### 4.1 为什么是这四个 topic

- **`cmp.cue` 单条有序流**：LiveKit 不同 topic 之间不保序，同一 topic 的 RELIABLE 包保序。把保序需求集中在一条流上，排序逻辑只需实现一次。
- **`cmp.ctl` 与 `cmp.cue` 分开**：控制消息（取消、握手）不能排在几十条表现指令后面等。分开后取消可以插队。
- **`cmp.in` 单独用于上行**：方向白名单要能按 topic 做，见 §14.2。
- **`cmp.tel` 用 LOSSY**：遥测丢了无所谓，用 RELIABLE 会在弱网下排队拖垮真实消息。

### 4.2 为什么不用别的

| 备选 | 否决理由 |
|---|---|
| 自建 WebSocket 旁路 | 多一条连接的鉴权、保活、重连、监控、成本；LiveKit 数据通道已经覆盖需求 |
| RPC 承载表现指令 | RPC 是请求-响应，自带单飞与超时语义，表现指令是单向广播；用 RPC 会引入无谓往返 |
| 二进制 / Protobuf | 当前 QPS 下体积不是瓶颈，JSON 可读性对调试的价值更大；`ensure_ascii=False` 已足够省字节 |
| 把 cue 塞进音频 SEI | 需要自建编码链路，端侧还要改解码；收益（几十毫秒）抵不上复杂度，列入 P3 之后 |
| 文本流附带 attributes 承载 cue | attributes 只在流创建时设定，无法承载段级时序 |

### 4.3 硬上限

- **SDK 强限 65535 字节/包**（`publishData` KDoc）。这是硬墙，不是建议。
- **建议自限 1.2 KiB/条**（MTU 安全区），超过则拆分或改走 HTTP。任何单条消息超过 4 KiB 视为实现缺陷，编解码层直接拒收并计数。
- LiveKit 官方文档给过更小的推荐值，与 SDK 的 64 KiB 是**两个不同口径**（文档建议 vs SDK 强制）。本方案按**两者取小并更保守**执行，实测后可在 §18 更新。

---

## 5. 信封

```jsonc
{
  "v": 2,                       // 协议大版本，整数
  "t": "presentation.emotion",  // 类型，点分层级，见 §8
  "id": "01J9Z8K3QP7N4",        // 全局唯一（ULID 或 {publisher}:{epoch}:{seq}）
  "seq": 1042,                  // 同一 (publisher, epoch, topic) 内单调递增
  "ep": "a3f1",                 // epoch：一次连接会话的非空短标签，重连必换
  "ts": 1731900000123,          // 发布方墙上时钟，仅供诊断，不参与调度
  "at": { },                    // 时间锚，可选；缺省=到达即生效
  "dst": null,                  // 定向目标 identity 列表；null=广播
  "args": { }                   // 类型专属载荷
}
```

**规则**：

1. `v` 只升不降，整数。接收方 `v > 本端支持的大版本` → 整条忽略并计数（不尝试部分理解）。
2. `t` 未知 → 忽略并计数。这是前向兼容的唯一机制，必须默认开启。
3. `seq` 在同一 `(publisher, ep, topic)` 三元组内严格递增，从 0 开始。`ep` 变化即视为新的流，`seq` 重新计数。
4. `id` 用于去重：A/B 类消息重发时 `id` 不变。接收方维护每个 `(publisher, ep)` 的**有界 LRU（256 条或 10 秒）**，命中则丢弃。
5. `ts` **不参与任何调度判断**。跨设备时钟漂移可达秒级，用它排时序是引入 bug 最快的方式。
6. `dst` 非空时，非目标 participant 收到即丢弃（且在 `identities` 定向发送时本就不会收到）。

### 5.1 序列号的回绕与 epoch

`seq` 用 64 位整数，按每秒 10 条的稳态速率算，回绕需要数千万年，实现上不需要处理回绕。

真正要处理的是**重连**：`ep` 必须每次连接重新生成。端侧在 `RoomEvent.Reconnected` 后清空所有 `(publisher, *)` 的 `last_seq` 与去重表 —— 旧 epoch 的序号与新 epoch 不可比。

### 5.2 缺口处理

RELIABLE 通道理论上不丢包，但**不保证不丢**（对端进程崩溃、发送缓冲溢出）。规则：

- 收到 `seq = last + 1` → 正常。
- 收到 `seq ≤ last` → 重复或乱序，按 `id` 去重后丢弃。
- 收到 `seq > last + 1` → 记一次 gap，**计数并打点，不做重传请求**。原因：表现指令的价值窗口极短，等重传的空档比丢失更伤。若 gap 发生在 `speech.end` 这类结构消息上，端侧以"该段提前结束"兜底。

这条规则很重要：**不为表现指令做重传**，只做去重与过期。重传只发生在 A 类消息上，且由发送方基于 ack 重试，不由接收方发起。

---

## 6. 投递语义三分类

| 类 | 语义 | 丢了的后果 | 例子 |
|---|---|---|---|
| **A 必须到达** | 发送方重试直到 ack 或超时；接收方幂等 | 功能断掉 | `hello` / `hello_ack` / `control.cancel` / `control.request` / `control.response` / `speech.seg` |
| **B 有截止期** | 不重传；超过 `at` 的 `max_late_ms` 即丢弃 | 少一个表情，可接受 | `presentation.emotion` / `presentation.gesture` / `presentation.state` |
| **C 可丢** | LOSSY 通道，采样 | 无感 | `telemetry.attention` |

**B 类必须带截止期，否则不要用 RELIABLE。** 一条 RELIABLE 的情绪指令在弱网下晚到 3 秒，会让角色在说到"我很难过"之后的第三句话才皱眉 —— 比完全没有这个表情更伤体验。`max_late_ms` 是 B 类消息的**必填字段**（默认值见 §10.4），缺失即按类型默认值处理，实现不得省略。

---

## 7. 会话与握手

### 7.1 时序

```
端                                         服务端
 │  connect(room)                            │
 │ ─────────────────────────────────────────▶│
 │  cmp.ctl  hello {proto, caps, limits}     │
 │ ─────────────────────────────────────────▶│
 │              cmp.ctl  hello_ack {sid,     │
 │                        caps(交集), limits,│
 │                        compat: "v1|v2"}   │
 │ ◀─────────────────────────────────────────│
 │  （之后才允许发 cue；此前收到的 cue 缓存≤2s│
 │    或直接丢弃，取决于 caps 是否已知）      │
```

### 7.2 hello 载荷

```jsonc
{
  "proto": {"min": 2, "max": 2},
  "caps": {
    "presentation.state": 1,
    "presentation.emotion": 1,
    "presentation.gesture": 1,
    "presentation.gaze": 1,
    "presentation.mouth": 0,
    "speech.v1": 1,
    "perception.attention": 1
  },
  "limits": {
    "max_payload_bytes": 12288,
    "max_cue_per_sec": 8,
    "clock_skew_ms": 0
  },
  "client": {"app": "android", "version": "3.2.0", "sdk": "livekit-android/2.29.0"}
}
```

`caps` 的 value 是**该能力自身的版本号**，0 表示"知道但未启用"。用整数而不是布尔，是为了让单个能力可以独立升到 v2 而不动协议大版本。

语音相关的能力只有一个 `speech.v1`（简化版，2026-11-18：原 `speech.timing` 与 `speech.extend` 合并）。

### 7.3 无 hello 的兼容路径

老客户端不会发 hello。服务端在 `ParticipantConnected` 后等 **3 秒**；收不到 hello 则按 `compat: "v1"` 处理：只发旧 `companion.*` topic 的旧格式，不发任何 `cmp.*`。这条路径只保留**两个发布周期**，之后删除。

服务端同样必须容忍客户端在任意时刻重发 hello（重连后）。

---

## 8. 核心事件目录

命名规则：`<profile>.<object>.<verb>`。profile 目前五个：`presentation` / `speech` / `control` / `perception` / `telemetry`。新增 profile 不需要协议升版；删除或改名需要。

| `t` | 类 | 方向 | 说明 |
|---|---|---|---|
| `control.hello` / `control.hello_ack` | A | C→S / S→C | 握手与能力协商 |
| `control.ping` / `control.pong` | A | 双向 | RTT 与存活，10s 周期 |
| `control.cancel` | A | S→C | 作废某 `speech_id`，见 §10 |
| `control.request` | A | S→C | 需要用户回答（含确认），见 §11.3 |
| `control.response` | A | C→S | 对上一条的回答，幂等 |
| `control.barge_in` | A | C→S | 端侧检测到用户开口 |
| `control.input` | A | C→S | 端侧即时输入（`cmp.in`），目前是触摸：`{kind:"touch", region}` |
| `speech.seg` | A | S→C | 一句语音的段声明（句级，按出句顺序，idx 连续），cap `speech.v1` |
| `speech.end` | A | S→C | 该 `speech_id` 的本轮 TTS **生成侧**已全部排空（不是扬声器播完） |
| `presentation.state` | B | S→C | IDLE/LISTENING/THINKING/SPEAKING |
| `presentation.emotion` | B | S→C | 情绪 |
| `presentation.gesture` | B | S→C | 动作/手势 |
| `presentation.gaze` | B | S→C | 注视意图 |
| `perception.attention` | C | C→S | 端侧粗粒度在场/注视（LOSSY） |
| `telemetry.health` | C | C→S | 端侧渲染健康度（掉帧、降级档位），30s |

**渲染器专属参数（`mouth`、`blink`、`head_roll` 等）不进协议**。它们是端侧实现细节，服务端只说"开心"，不说"嘴角上扬 0.3"。这条 v1 §6.1 已经写对，此处重申：一旦协议里出现渲染参数，换一套美术资源就要改服务端。

---

## 9. 语音时序模型（本方案的核心）

> 修订 2026-11-18（简化版）：取代此前的 `speech.begin` / `speech.extend` / `speech.segment` 三件套与四相位锚点。消息数、事件种类、锚点形态都大幅减少；代价是段起点精度降低，取舍见 §9.7 与 §9.8。

### 9.1 问题重述

用户要的两件事：

1. **说到某一句时，同步做某个动作**（段首同步）。
2. **在某一句开口之前，先做某个动作**（段首前导，由 TTS 合成时延天然提供，见 §9.6）。

"说完再做"的需求在简化版里不单独表达，见 §9.8。

v1 草案的做法是"服务端估算 playback head"，但它算出来的只是"TTS 队列里还有多少"，不是"扬声器里播到第几毫秒"。中间隔着网络传输、端侧 jitter buffer、系统音频混音。所以 v2 把时钟责任整体移到端侧。

### 9.2 锚点 `at`

```jsonc
// 1) 段首：主力形态
"at": {"speech": "sp_7f3a", "seg": 2}

// 2) 相对接收：给"不要立刻，过一会儿"用的兜底
"at": {"not_before_ms": 800}

// 3) 缺省：到达即生效（仅 presentation.state 这类"当前事实"）
```

- 只有段首一种语段锚点：没有 `phase`、没有 `offset_ms`、没有负偏移。
- "在段 k 开口之前"不用新字段表达：服务端在发送段 k 的 TTS 之前发出该 cue，由 §9.6 的 TTS 前导保证它先到。
- `max_late_ms` 可选，缺省取 §10.4。不允许设为无穷。

### 9.3 `speech.seg` 与 `speech.end`

```jsonc
{"v":2,"t":"speech.seg","id":"01J9ZA..","seq":201,"ep":"a3f1",
 "args":{"speech_id":"sp_7f3a","idx":0,"chars":12,"est_ms":780}}
{"v":2,"t":"speech.seg","id":"01J9ZB..","seq":202,"ep":"a3f1",
 "args":{"speech_id":"sp_7f3a","idx":1,"chars":31,"est_ms":2100}}
{"v":2,"t":"speech.end","id":"01J9ZC..","seq":210,"ep":"a3f1",
 "args":{"speech_id":"sp_7f3a"}}
```

规则：

- **一个 `speech_id` 对应一轮回复**。`speech.seg` 每出一句发一条（句级，不是批次）。`idx` 从 0 开始连续递增。`idx == 0` 的 `speech.seg` 隐式开启该 `speech_id` 的时间线。
- **`speech.seg(k)` 必须先于引用段 k 的 cue、以及段 k 的 TTS 开口发出**（§13 不变量 1）。
- `est_ms` 必需。给不出就按字符数估（中文按 5 字/秒）。`chars` 只用于日志与估计复核，端侧调度不读它。
- `speech.end` 在该轮 TTS **生成侧**全部排空时发出：服务端 TTS 节点已把本轮全部音频帧交给传输。它**不是**「扬声器播完」——服务端没有播放时钟（§9.1 / D5），也测不到端侧播到哪。因此 `speech.end` 常会早于端侧 `t0`（短回复尤其常见）；端侧收到后把时间线标 DONE，但挂在这句上、尚未到点的 cue 不因此取消。`speech.end` 之后不得再有该 `speech_id` 的 `speech.seg`。
- 段数上限 `MAX_SEGMENTS`（24）：超出的句子照常朗读，但不再发 `speech.seg`；锚在它们上的 cue 按 NO_ANCHOR 丢弃并计数。
- **消息量**：N 句的一轮回复发 N+1 条语音消息（旧方案约 2N+2）。每条 `speech.seg` 远小于 200 B，远低于 `max_payload_bytes`。
- 没有 `speech.v1` 能力的端侧：服务端不发任何 `speech.*`；锚到 `speech` 的 cue 在该端按 NO_ANCHOR 丢弃（与 v1 行为一致，不影响无锚的 cue）。

### 9.4 端侧时间线

```
SpeechTimeline {
  speechId
  state ∈ {OPEN, DONE, CANCELLED}
  segEst[]        // 每段 est_ms，按 idx 顺序
  t0              // 第一帧音频开始播放的单调钟时刻；未到达前为 null
  cues: [(anchor, AvatarCommand, maxLateMs)]
}
```

- `t_start(k) = t0 + Σ_{j<k} segEst[j]`。只用估计时长，不做实测修正。
- **`t0` 只定一次**：在第一帧音频真正播放时确定。实现上用 audio sink 的 `absoluteCaptureTimestampMs` 与端侧单调钟建立一次线性映射：`t0 = now_mono - (now_capture - first_capture_ts)`。
- 若 audio sink 不给时间戳，退回 `now_mono` at first non-zero frame，误差上限约一个 jitter buffer。
- 段 k 的 `speech.seg` 若晚于 t0 到达，且 cue 已到：cue 缓存（≤2 s，F3），段声明到达后再排程。

### 9.5 调度与裁剪

```
for each cue in timeline:
    target = resolve(at)            // seg → t_start(seg)；not_before_ms → receivedAt + ms
    now    = mono_now()
    if timeline.state == CANCELLED:           drop(cue, "cancelled")
    else if target is unknown:                hold (≤2 s) 或 drop(cue, "no_anchor")
    else if target > now:                     schedule(target)
    else if now - target <= cue.maxLateMs:    apply_now(cue)          // 晚了但还在窗口内
    else:                                     drop(cue, "expired")    // 晚了，宁可不要
```

这段逻辑是整个端侧协议实现里最值得写单测的一段。必须覆盖：早到、正点、晚到但在窗口内、晚到超窗、`speech_id` 已作废、段号越界（`seg` 超过已声明的最大 idx）、`t0` 尚未确定、段声明尚未到达。

### 9.6 "先做动作再说话"到底怎么实现

诚实地说：段首前导的 cue 必须在该段音频开始播放之前到达端侧。而 AudioTrack 的播放起点由服务端 TTS 合成完成时间决定，端侧无法推迟音频。

服务端的发送顺序对每一句都是：

```
speech.seg(k)  →  引用段 k 的 cue  →  段 k 的 TTS
```

TTS 从文本到首帧通常 300–800 ms，这段时间就是 cue 的天然前导。**这是默认方案，不增加端到端延迟。** 只有实测合成时延不足时才需要显式等待（`lead_ms`，本简化版不内置；需要时作为服务端内部参数加，不进协议）。

**第 0 段是特例**：一轮对话的第一句没有前置合成缓冲可用。`seg == 0` 的前导**尽力而为**，不得作为产品需求的硬保证。需要强保证的场景，让模型把首段写成短开场（"嗯——"），把动作挂到 `seg == 1`。

这条限制必须写进 Prompt 约束（v1 §7.3），而不是留给实现者猜。

### 9.7 时长估计漂移

`est_ms` 按字符数估，中文语速 4.5–6 字/秒，误差可达 ±20%。段起点的误差是**累计**的：段 k 的起点误差上界约为 20% × Σ_{j<k} est_ms[j]。段数越多、回复越长，偏差越大。

简化版的缓解（按性价比排序）：

1. **锚点就近**：编译期把 cue 挂到**最近段**的段首。跨 3 段以上的"动作在某句之后"应改写为挂到**目标段本身**，而不是远处的段。
2. **只校正 t0，不校正段**：t0 在首帧音频时定一次，这是唯一的时钟校正。
3. **承诺上限**：段起点精度只承诺到 **±500 ms** 量级（短回复更好，长回复不保证）。超出这个承诺的需求，走 §18 第 2 条的升级路径（给 `speech.seg` 加可选 `dur_ms`）。

**在文档和测试里都写清楚，不要假装精确。** F14 的验收按上面的上界写，不按"每段重设起点"写。

### 9.8 有意去掉的东西（简化版取舍）

| 去掉 | 原因 | 代价 | 如何回补（都是加字段，不升版） |
|---|---|---|---|
| `speech.begin` / `speech.extend` | 合并为句级的 `speech.seg`；流式出字时不需要"先声明全部段" | 无 | — |
| `speech.segment`（`dur_ms` / `start_boundary`） | 每段多一条消息，换来的精度不足以抵消消息量与状态复杂度 | 段起点精度降到 ±500 ms 量级（§9.7） | 给 `speech.seg` 加可选 `dur_ms` |
| `phase`（`before` / `during` / `after`）与 `offset_ms` | 段首锚 + TTS 前导已覆盖"同步"与"之前"；`during` 与段首同步等价 | "说完再做"无精确表达（见下） | 加新的锚点形态 |
| "说完再做"（原 `after`） | 去掉 `phase` 的代价 | 近似为锚到**下一段段首**；最后一段之后的动作本协议不表达。`speech.end` 只表示生成侧排空，不能当「扬声器播完」用；需要时由客户端按本地 `est_ms`/`t0` 估算句尾后触发（不在协议里约定） | 加 `{"speech","end":true}` 形态 |
| `wall` 绝对时间锚 | 当前没有跨播放器对齐的场景 | 无 | 加形态 |
| `speech.timing` / `speech.extend` 两个能力 | 合并为一个 `speech.v1` | 无 | — |

## 10. 打断与取消

### 10.1 端侧发起（用户开口）

```
端: 检测到 VAD/输入 → cmp.ctl {t:"control.barge_in", args:{speech_id, played_ms, reason:"vad"}}
端: 立刻本地把该 SpeechTimeline 置 CANCELLED，并开始本地衰减动画
```

端侧**不等服务端确认**就把自己的时间线作废，这是对的：音频本身由服务端 `SpeechHandle.interrupt()` 截断，端侧只需同步视觉。

### 10.2 服务端确认

```
服: SpeechHandle.interrupt() → publish cmp.ctl {t:"control.cancel", args:{speech_id, reason:"user_barge_in"}}
```

`control.cancel` 是 A 类：端侧收到后把该 `speech_id` 标死（进入墓碑集合），之后该 id 的任何 `speech.seg` 与 cue 一律丢弃。

### 10.3 单调作废模型

规则只有一条：**`speech_id` 一旦作废，不可复活**。

- 端侧墓碑：`cancelledSpeechIds` 有界集合，FIFO，上限 8（简化版，原 32）。一回合只会有少数几个被打断的 speech，8 足够。
- 服务端在同轮内不得复用 `speech_id`。`speech_id` 用 ULID，天然不复用。
- 迟到到达的 `speech.seg`（先 cancel 后 seg 的乱序）→ 端侧看到 id 已在墓碑里，直接丢弃。这是必须测的用例（§16 F5）。

### 10.4 默认截止期（`max_late_ms`）

| 类型 | 默认 | 理由 |
|---|---|---|
| `presentation.state` | 300 | 状态错了会让人立刻出戏 |
| `presentation.emotion` | 1500 | 情绪可以稍微晚一点，仍落在同一句里 |
| `presentation.gesture` | 800 | 动作与词的绑定更紧 |
| `presentation.gaze` | 400 | 注视是短时意图 |

服务端可以在 `at.max_late_ms` 里覆盖，但**不允许设为无穷**。缺失则取上表。

---

## 11. Profile 定义

### 11.1 presentation

```jsonc
{"t":"presentation.emotion","at":{"speech":"sp_7f3a","seg":1},
 "args":{"name":"joy","intensity":0.7}}

{"t":"presentation.gesture","at":{"speech":"sp_7f3a","seg":2},
 "args":{"id":"nod","repeat":1}}

{"t":"presentation.state","args":{"state":"LISTENING"}}   // 无 at，到达即生效
```

`intensity` 可选，缺省 1.0。端侧对**未知 emotion 名**的处理：映射到 `neutral` 并计数，**不得崩溃、不得卡住后续 cue** —— 这是当前 `AvatarEventMapper` 返回 null 的行为，需要保留但要显式化并加指标。

### 11.2 speech

见 §9.2 / §9.3。

### 11.3 control

**确认流（修 §2.3 的断点）**：

```jsonc
// S→C
{"t":"control.request","id":"rq_9","args":{
  "kind":"confirm",
  "prompt":"要我把这张照片发给妈妈吗？",
  "options":[{"key":"yes","label":"好"},{"key":"no","label":"先不要"}],
  "timeout_ms":15000,
  "idempotency_key":"turn:tn_55:confirm"
}}

// C→S（幂等，同一 id 重发只生效一次）
{"t":"control.response","id":"rs_9","args":{
  "request_id":"rq_9","key":"yes","answered_at":1731900004500
}}
```

`idempotency_key` 是给服务端判重用的：端侧网络抖动重发时，服务端按它去重，不会二次执行副作用。确认类请求**必须**有副作用幂等键。

### 11.4 perception / telemetry

端侧 → 服务端，**一律不携带图像**，只带结构化的粗粒度字段（在不在、朝向、注意力、置信度）。任何需要像素的推理留在端侧（v1 §9）。超过 2 KiB 的一律走 HTTP，见 D11。

---

## 12. 能力协商与降级阶梯

### 12.1 协商规则

- `hello_ack.caps` = 服务端支持 ∩ 客户端支持，逐项取**低版本**。
- 之后服务端**不得**发送不在交集里的 `t`。这是服务端的义务，端侧不做二次判断（但仍做未知类型忽略作为兜底）。
- 端侧在握手完成前收到的 cue：缓存 ≤ 2 s，超时丢弃。不缓存也可以，但会丢首句表现，体验更差。
- 能力可以在会话中通过 `control.hello` 重发而升级/降级（例如用户关掉了摄像头 → `perception.attention: 0`）。

### 12.2 多端

同一房间可能有多个观察端（手机 + 平板 + 调试端）。

- 能力按 participant 各算各的。
- **全部一致** → 广播（`dst: null`），一条包。
- **不一致** → 广播交集 + 对差异 participant 用 `publishData(identities=[...])` 定向补发。定向包数量受 participant 数限制，不会爆炸。

### 12.3 降级阶梯（必须逐级可测）

| 档 | 触发 | 用户看到 | 实现位置 |
|---|---|---|---|
| L0 | 正常 | 表情/动作/口型全部按计划 | 全链路 |
| L1 | cue 迟到率 > 30% 或 `cmp.cue` gap 连续 3 次 | 表情减少，动作只保留 state 与情绪；口型不受影响（本地驱动） | 端侧 Scheduler 自动降档 |
| L2 | `cmp.cue` 完全收不到 > 5 s | 角色按音频 RMS 做口型 + 呼吸 + 眨眼，状态机跟随 audio on/off；无表情 | 端侧本地兜底动画 |
| L3 | 数据通道全断但音频在 | 同上，且字幕仍走 `lk.transcription` | 端侧 |
| L4 | 音频也断 | 缓存文本 + 明确"连接中断"提示，允许重连 | 端侧 + UI |

降级的判定必须**只看本地可观测量**（到达率、gap、超时），不依赖服务端告知 —— 否则服务端一挂，端侧就不知道该降级。

`telemetry.health` 把当前档位回报服务端，用于判断"是全网问题还是单个客户端问题"。

---

## 13. 服务端编译流水线

```
LLM 流式 token
   │
   ├─→ 文本 → lk.transcription（原样，字幕用）
   │
   ├─→ 结构化事件（prompt 约束，见 v1 §7.3）
   │      {"say":"我有点担心你","emotion":"concern","after":{"gesture":"tilt_head"}}
   │
   ├─→ 分句 → seg_idx 分配（一句一段）
   │
   ├─→ 时长：est_ms（字符数×语速），不做 TTS 后回填（§9.7）
   │
   ├─→ 能力裁剪：按 dst participant 的 caps 过滤不支持的 t
   │
   └─→ CueCompiler → publish cmp.cue（speech.seg / speech.end / presentation.* / control.cancel）
```

编译器的三条不变量（值得写成断言）：

1. 一条 `speech.seg(k)` 之前，不得有引用段 `k` 的 cue；`speech.end` 之后不得再有该 `speech_id` 的 `speech.seg`（§9.3）。
2. 同一条 cue 不得发两次（除非重试且 `id` 相同）。
3. 任何发往 `cmp.cue` 的包，其 `args` 序列化后必须 ≤ `max_payload_bytes`；超了就是编译器 bug，不是传输层问题。

---

## 14. 错误、安全、可观测

### 14.1 错误处理矩阵

| 情况 | 端侧 | 服务端 |
|---|---|---|
| JSON 解析失败 | 丢弃 + 计数 `bad_json` | 同 |
| 缺 `v`/`t`/`id` | 丢弃 + 计数 | 同 |
| `v` 超版本 | 丢弃 + 计数 | 同 |
| `t` 未知 | 丢弃 + 计数 | 同 |
| 必填 `args` 字段缺失 | 丢弃整条（不猜） | 同 |
| 未知 `args` 字段 | 忽略 | 同 |
| 载荷超限 | 拒收 + 计数 | 发送侧断言失败 |
| `seq` 倒退 | 去重后丢弃 | 同 |
| `dst` 不是我 | 丢弃（静默） | — |
| 非白名单身份发来的消息 | 丢弃 + 计数 | — |

**统一原则：任何一条消息的失败都不得影响后续消息处理，也不得抛出到 UI 线程。** 现有代码里 `sendTouch` 的静默失败是正确取向，需要推广到全部收发路径，并补上计数。

### 14.2 方向白名单（安全）

| topic | 允许的发送方 | 客户端应丢弃的 |
|---|---|---|
| `cmp.cue` | 服务端 agent identity | 其他任何 participant（包括另一个人类客户端） |
| `cmp.ctl` | 双向，但按 `t` 区分：`hello`/`barge_in` 仅客户端；`cancel`/`request` 仅服务端 | 反向的 `t` |
| `cmp.in` | 仅客户端 | 服务端发来的 |
| `cmp.tel` | 仅客户端 | 服务端发来的 |

这条必须实现，否则房间里任何人（包括被邀请的第三方）都能伪造 `presentation.emotion`，或者伪造 `control.response` 去应答一个确认。**这是当前实现完全没有的检查。**

身份来源：`Participant.identity` + 服务端签名的 room metadata（`RoomIdentity.from_room_metadata` 已经在拒非法 metadata）。不要信任客户端自己声明的角色。

### 14.3 可观测性（没有这些就调不动时序）

端侧计数（按 topic × 结果）：

```
received / applied / dropped_dup / dropped_stale / dropped_expired
dropped_unknown / dropped_unauthorized / dropped_bad / gap_count
```

时序质量指标（每回合）：

```
cue_intent_to_apply_ms   // p50/p90/max，按类型分桶
late_ratio               // 超过 max_late_ms 的比例
degrade_level            // 当前 L0..L4，及进入/退出时刻
```

服务端：`publish_fail_total`、`payload_bytes_hist`、`compile_overflow_total`、`tts_lead_margin_ms`（`required_lead - measured_latency`，这个数决定 §9.6 方案 2 要不要开）。

**新增一个埋点就多加一个能回答的问题。** 上面这几个分别回答："是网络还是我的编译器"、"这条手势为什么晚了"、"要不要加 lead"。

---

## 15. 迁移表

| 旧（v1） | 新（v2） | 备注 |
|---|---|---|
| `companion.avatar`（带 topic，`AvatarPublisher`） | `cmp.cue` `presentation.emotion` / `.state` | 两个版本并存两个发布周期 |
| `companion.action` | `cmp.cue` `presentation.gesture` | — |
| `companion.proactive` | `cmp.ctl` `control.request` (`kind:"notice"`) 或 `cmp.cue` | 视是否需要用户响应 |
| `companion.touch`（text stream） | `cmp.in` `control.input` (`region`) | 已迁移：端侧 `sendTouch` 发数据包；未协商出 v2 时仍回落文本流 |
| `companion.confirm`（孤儿，已删） | 不复用。确认回执留在 HTTP `POST /v1/actions/{id}/confirm` | 带鉴权与幂等，数据通道不重复实现 |
| `lk.chat` | 保留 | 用户文本输入，与 Agents SDK 互操作 |
| `lk.transcription` | 保留 | 字幕，端侧复用现成渲染 |

**双写期策略**：新服务端同时发 v1 与 v2 两套（v1 只发给未握手的客户端）。端侧 v2 客户端忽略 v1 topic。两个发布周期后删除 v1。`compat` 字段记录在 hello_ack 里，便于统计还有多少老客户端。

---

## 16. 故障用例清单（实现前必须能过）

| ID | 场景 | 期望 |
|---|---|---|
| F1 | 可靠包重复投递 | 按 `id` 去重，副作用只发生一次 |
| F2 | cue 晚到 3 s（超 `max_late_ms`） | 丢弃并计数，不出现"错句配错表情" |
| F3 | cue 早到（对应的 `speech.seg` 还没到） | 缓存 ≤2 s，段声明到达后按锚点排程 |
| F4 | 重连后 `ep` 变化、`seq` 归零 | 不误判为乱序，去重表正确清理 |
| F5 | 先收到 `cancel` 再收到该 `speech_id` 的 `speech.seg` | `seg` 被丢弃，时间线不复活 |
| F6 | 未知 `t`（新版本服务端） | 忽略 + 计数，同流后续消息正常处理 |
| F7 | 老客户端（无 hello） | 服务端走 v1 兼容，客户端功能不退化 |
| F8 | 房间内第三方伪造 `presentation.*` | 被身份白名单丢弃 |
| F9 | 载荷 70 KB | 发送侧断言失败；接收侧不 OOM、不崩溃 |
| F10 | 时钟漂移 10 s | 调度不受影响（`ts` 不参与计算） |
| F11 | 用户在第 2 段打断 | 音频截断、动画本地衰减、墓碑生效、`speech_id` 不复用 |
| F12 | 两个客户端能力不同 | 交集广播 + 定向补发；无端收到不支持的 `t` |
| F13 | 服务端崩溃重启，`ep` 变 | 端侧清理时间线，重连后重新握手 |
| F14 | 段时长估计偏差 20% | 段起点误差 ≤ 20% 的累计估计时长；cue 挂最近段；不承诺优于 ±500 ms 的精度 |
| F15 | 弱网 30% 丢包 | 自动降到 L1/L2，口型仍跟随音频 |

**F2 + F14 是这套设计的主要风险点**，必须在真机上用真实 TTS 跑一遍，把 `cue_intent_to_apply_ms` 的 p90 记下来写回本文 §18。

---

## 17. 分期与验收

| 期 | 范围 | 验收 |
|---|---|---|
| **P0** | 信封 + codec（校验/去重/计数）+ hello 握手 + 身份白名单；修 §2.3 两处不一致 | F1/F4/F6/F7/F8/F10 通过；v1 客户端功能不退化 |
| **P1** | `speech.seg` / `speech.end` + 端侧 Scheduler（`at` 段首锚与 `not_before_ms`）+ t0 一次校正 | F2/F3/F5/F14 通过；句级 seg 与 TTS 开口顺序可断言 |
| **P2** | `control.cancel` / `barge_in` 全链路 + 墓碑 | F11 通过；打断后 200 ms 内不再有该 id 的表现 |
| **P3** | 服务端 CueCompiler + prompt 结构化输出 + `dur_ms` 接入 | F12 通过；`cue_intent_to_apply_ms` p90 ≤ 250 ms（`before` 类记初始到达时延） |
| **P4** | 降级阶梯 L1–L4 | F15 通过；每一档都能手动触发并观察 |

每一期都必须**先有可观测指标再有功能**，否则时序问题无法定位。

---

## 18. 待验证与未决

1. **`publishData` 在真实网络下的到达时延分布**。决定 `max_late_ms` 默认值是否合理，也是 §9.6 是否需要 `lead_ms` 的唯一依据。**必须在 P1 前压测。**
2. **估计时长与端侧真实播放时长的偏差**。未接实测时长时，段起点精度由估计决定（§9.7）。若实测超出 ±500 ms 的承诺，升级路径是给 `speech.seg` 加可选 `dur_ms` 字段（加字段不升版）。
3. **段边界是否可听辨**。若端侧无法从音频检测段起点，§9.7 手段 1 失效，退化为按字符数比例切分（误差更大）。
4. **多端场景的真实需求强度**。如果只有一对一通话，§12.2 的定向补发可以先不做，但 `dst` 字段必须保留（加字段不需要升版，减字段才需要）。
5. **是否要把 cue 塞进音频 SEI**（P3 之后）。取决于上面第 1 条的实测结果。
6. **`intensity` 是否要暴露给模型**。当前给，但模型很容易滥用成"强度 1.0 的狂喜"，需要 prompt 侧限幅。
7. **"说完再做"（原 `after`）是否需要回归**。简化版只保留段首锚，"说完再做"近似为锚到下一段段首（§9.8）。真实产品若需要严格的句尾动作，再加锚点形态，不改已有消息。

---

## 19. 来源与查证边界

- LiveKit Android 2.29.0 反编译源码（`publishData` / `sendText` / `performRpc` / `AudioTrackSink` 签名与 KDoc）。
- LiveKit Agents Python 1.8.11 源码（`publish_data`、`SpeechHandle`、`TimedString`、`transcription_node`）。
- 本仓库：`LiveKitCompanionTransport.kt`（topic 常量、`sendTouch`、`handleData`、audio sink RMS）、`AvatarCommand.kt`、`AvatarEventMapper.kt`、`CompanionTransport.kt`。
- 服务端：`server/src/companion/api/livekit/entrypoint.py`（topic 常量）、`agent.py`（`build_event_sink`）、`agent/text/events.py`（情绪标签与事件构造）、`service/turn/pipeline.py`。
- 前序文档：`companion-interaction-protocol-and-perception.md`、`companion-agent-architecture.md`、`global-rtc-deployment-and-cost.md`。

**未核实、本文按保守值假设的**：LiveKit 数据通道在实际 4G/弱网下的端到端时延与丢包率；端侧 audio sink 时间戳在蓝牙耳机/系统混音场景下的准确性。这两项都需要真机实测，不得从文档推断。
