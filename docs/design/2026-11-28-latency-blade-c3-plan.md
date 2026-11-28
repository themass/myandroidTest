# 延迟刀 C3 — 语音回合首响 <2s：全链路方案 + 端侧已落地件（2026-11-28）

> 依据：`docs/research/2026-11-28-surpass-ollmv-synthesis.md` §1 C3 / §4 刀 3。
> 服务端（agent-docs/companion-agent）实现归协调线，本文件只出方案与契约；
> 端侧件已在 `weifu-refactor` 线落地（本 worktree commit），JVM 单测先行。
> 前作必读：`docs/f16-tts-first-frame-latency-rca.md`（守卫扣留 RCA，已修）、
> `docs/design/livekit-performance-slo-proposal-20261124.md`（计时口径）、
> `docs/design/2026-11-28-heard-prefix-feedback-contract.md`（打断刀契约）。

## 0. 实测基线（复现取数，全部来自既有验收日志）

本次无设备（`adb devices` 空），基线取共享笔记与 RCA 文档里的同名字段
（`ttft` / `first_frame_ms` / `first_ms`），口径见 SLO 提案 §2：

| 段 | 字段 | 实测 | 出处 |
|---|---|---|---|
| 提交→首句进 TTS | `voice reply … first_ms` | 守卫修复后非打断 n=2：**24.8s**（修复前 15.2s 是被守卫钉在流末的假低） | f16 RCA 11-24 复核 |
| ⓪ 排队/drain/context/recall | turn→recall 行差 | **9–10s**（最大单块） | f16 RCA「剩余大头」表 |
| ① LLM 首包 | `voice llm … ttft` | **2.2–5s**（修复后）；11-28 真机验收窗口报 **2.3–15.8s**（含守卫放大段） | f16 RCA / surpass-synthesis C3 行 |
| ② 首句进 TTS→首帧出 | `tts first … first_frame_ms` | **n=14：min 1.2 / 中位 7.2 / max 12.6s**；11-28 真机窗口报 **13.3s** | f16 RCA / surpass-synthesis C3 行 |
| ③ 首帧→端上首个非零 PCM | `remote_pcm_first_audio rms>` | **~70ms**（下行链不慢） | SLO 提案 §2 |
| 端到端样本 | job 对照 | fe591de0 30986/12601、b3a67d32 19559/7622、ba5865c9 18529/11642 | p05p2-proc-table |

**用户听到第一句 ≈ ⓪ + ① + ② + ~70ms。** 今天的 P50 在这条链上是 20s 级；
<2s 不是任何单点能给的，必须 ⓪①② 三段同时动手，且 ② 按句拆。

## 1. 客户端侧（本 commit 已落地的部分）

### 1.1 事实澄清：端上没有「整段 base64 才播」

C3 行里「废整段 base64」指的是**服务端** TTS 交付形态（agent 把整段音频 base64
回给 room 再推流）。通话音链路端侧现状（代码为证）：

- 播放：LiveKit `RemoteAudioTrack` → jitter buffer → 帧级连续播放
  （`LiveKitCompanionTransport.attachMouthSink`），服务端出多少帧端上播多少帧，
  端侧不存在等待整段落完的缓冲。
- 截断：`bargeMuted`（P2 契约 §2.1）在打断到达**当帧**压 `setVolume(0)` 并拦
  `feedRemotePcm`，jitter buffer 残留不出声；下一条被接受的 `speech.seg` 重开门。
  即刻截断已成立，无需新件。

所以端侧「按 chunk 到即播」不需要新播放器——需要的是**流水线下音频帧归属不错位**
（服务端并行合成按序交付后，一条轨上会同时在飞多条未定 `t0` 的句子），以及
把「端上何时听见」变成可复测字段。

### 1.2 已落地（纯决策层，TDD）

`SpeechRegistry` / `SpeechTimeline`（JVM 单测 `SpeechStreamOrderTest`，先红后绿）：

1. **FIFO 归属**：`timebasePendingId()` 从「最新一条 PENDING」改为「**最早声明**、
   未定 `t0` 的一条」，并把 DONE（`speech.end` 早于首帧是短回复常态）纳入可标定
   集合——修掉了 end 早到时 transport 永不标 `t0`、挂在该句上的 after cue 全部
   2s 过期的洞。
2. **出声窗口**：`timebasePendingId(now)` ——候选之前任何一条已定 `t0`、未作废的
   句子仍在估算终点内（`estimatedEnd = t0 + Σ effective_ms`）或有声未收尾
   （连续静音 < `silenceGapMs=200ms`），这一帧就归它，**不许**给新句定 `t0`。
   下一句首帧的 `t0` 因此在流水线里仍然准 → cue 锚点、`playedMs`→`heard_text`
   前缀、字幕截断全部不错位。
3. **静音收尾**：`est_ms` 偏长时唯一能提前放行下一句的信号；副作用（句内
   ≥200ms 自然停顿 + 下一句已声明 → 错归下一句）已知，彻底解法是服务端按句
   打流标记（§2.4），端侧启发式只求 P50 不错位。
4. **端侧首响字段**：`speech.seg` 到达记 `declaredAt`；首帧标定时打
   `event=turn_first_audio id=… waitMs=…`。这是「超越 OLLMV」验收口径里
   端上唯一能自证的一段（服务端 `first_frame_ms` 只到合成出口）。

### 1.3 端侧没做的（明确不做）

- 不改 `CompanionCallScreen.kt`（多路改动热区，零侵入）。
- 不新造播放器/缓冲层：LiveKit 帧级消费已是到即播。
- 不动 `protocol_v2.json`：`turn_first_audio` 是端侧 logcat 字段，不上协议、
  不碰两端 SHA 锁。

## 2. 服务端方案（agent-docs，实现归协调线）

照抄 OLLMV 的骨架（`tts_manager.py:70-111`：LLM 流 → 逐句 → **并行** TTS 任务 →
**按序**交付），加上我们已有件。分四段，按收益排序：

### 2.1 ⓪ 段：拆掉 9–10s 排队（现最大块）

- `PlatformBrain` `_POLL_MS=1000` 轮询改事件驱动（新回合入队即唤醒）。
- turn drain 与 context build 重叠：recall（0.8s 超时×2）与 prompt 组装并行，
  recall 超时不重试第二轮（降级为无记忆回复，`docs/design/p4-degrade-ladder` 口径）。
- 目标：⓪ P50 ≤ 0.5s。取数：21db8a2 探针 `voice turn queued/drained` 行已有。

### 2.2 ① 段：LLM 首包治理（doubao 侧）

- 首句短化：语音 prompt 明确「第一个片段 ≤ 20 字」（已有 CUE_OUTPUT_SNIPPET
  短片段形状，`3296438`），首片段与 cues 解耦（cues 放最后一个对象，say 先闭合）。
- 会话前缀缓存（网关 prompt cache）压低 ttft 下界；模型预热挂接房路径
  （prewarm `9fe52e2` 已做 VAD，模型连接复用待做）。
- 目标：ttft P50 ≤ 1.0s；`voice llm` 行已打 ttft，直接复测。

### 2.3 ② 段：并行合成、按序交付（本刀核心）

- 句子级流水线：`SentenceSplitter` 每切出一句立即 `asyncio.create_task` 合成；
  交付侧按句序 `await`，第 k 句出帧即推流——第 1 句的等待 = 单句合成时间
  （热连接实测下限 1.2–1.5s），不再是全文合成。
- **per-task timeout**：单句合成超 `TTS_TASK_TIMEOUT_MS`（建议 4000ms）→ 该句
  降级为 TTS 重试一次 → 仍失败则文本直发（字幕照常）+ `tts task timeout` 日志，
  **绝不阻塞**后续句（队列 drain）。整回合兜底 `TTS_TURN_TIMEOUT_MS`（8000ms）。
- 连接保活：`NewConsoleTTS` 连接预热/常驻（f16 RCA 下一步 #3；n=14 的方差
  min 1.2 vs max 12.6 就是冷连接签名）。
- 目标：首句 `first_frame_ms` P50 ≤ 1.5s（向 1.2–1.5 热下限收敛，SLO 提案 C4 同判）。

### 2.4 按句流标记（端侧启发式的根治，下一刀可选）

端侧现在靠「声明序 == 音频序 + 静音收尾」猜句界。彻底做法是服务端在音频流上
给每句边界打标记（LiveKit Agents 每句 `flush`，或 data channel 发
`speech.audio_done{speech_id, idx}`），端上直接以标记切换归属与 `t0`。
需要协议加事件（两端 SHA 锁要同批改），收益是消掉 §1.2(3) 的错归副作用——
建议 P50 达标后作为稳健性项排期。

### 2.5 背压与打断交互（别打破已有契约）

- 并行合成不得早于 `speech.seg`：seg(k) 必须先于句 k 音频出帧（§9.3 已有，
  端侧 FIFO 归属依赖它；契约测试应钉住「先 seg 后帧」）。
- `control.cancel` 语义不变：已出帧作废由端侧 `bargeMuted` 兜住；服务端取消
  未完成任务（`task.cancel()`）省配额。
- barge-in 记忆手术（heard 前缀）与并行合成兼容：端侧按 `playedMs` 切前缀，
  服务端不需要知道哪句合成过。

## 3. 预估收益（P50，可证伪）

| 段 | 现在 | 方案后 | 手段 |
|---|---|---|---|
| ⓪ 排队/context | 9–10s | ≤0.5s | §2.1 |
| ① ttft | 2.2–5s | ≤1.0s | §2.2 |
| ② 首句合成 | 7.2s 中位 | ≤1.5s | §2.3（首句独立合成+保活） |
| ③ 下行 | ~70ms | ~70ms | — |
| **听到第一句** | **~20s** | **≤3.1s（P50）** | 合计 |

`<2s` 需要 §2.1–2.3 全部到位且 ttft 压到 ~0.5s 级；上表按保守值给 ≤3.1s，
**<2s 是 stretch 目标**——真机口径不变：同 logcat 字段 `ttft` / `first_frame_ms`
复测 P50 < 2s（另加本刀新增的 `turn_first_audio`）。

## 4. 验收与交接

- 端侧（本 commit）：`:feature-companion:testDebugUnitTest` 全绿（含新
  `SpeechStreamOrderTest` 9 例）；`:app:assembleChatDebug` 通过；真机复测归协调会话。
- 服务端：按 §2.1–2.3 实施时，每段先取一窗现网对照（探针都已合 main：
  `voice llm`、`tts first`、`voice turn queued/drained`），改一段测一段。
- 端-服务端联动测试点（并线后必测）：并行合成开启后连续两句，第二句 cue 锚点
  时刻 = 其自身 `t0` + 段偏移（端侧 FIFO+窗口判定），打断第二句时 `heard_text`
  只含第二句前缀。
