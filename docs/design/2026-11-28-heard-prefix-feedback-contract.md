# 打断 heard 前缀回传契约（超越 OLLMV 第二刀 C2 + C7）

日期：2026-11-28。前置：`companion-interaction-protocol-v2.md` §10（打断与取消、单调作废）、
`2026-11-24-state-cancel-contract-v2.md`（P2 真取消式打断；本文件**增量**于它，不改它的结论）、
`docs/research/2026-11-28-surpass-ollmv-synthesis.md` C2/C7 行。

Android 端已实现（本刀）；**服务端实现由协调线按本契约接线**，服务端仓库不在本仓。

## 0. 一句话

打断发生时，端侧把「她已精确播出的文本前缀」随 `control.barge_in` 一起回传；
服务端 memory 用它改写上一条回复（heard + 「[Interrupted by user]」），下一轮 LLM
读到的是她**真说过的话**，而不是全部已生成文本。空闲时按同一根键 = 催她主动开口。

与 OLLMV 的差异（抄什么、改什么）：OLLMV 浏览器播放器量不出播到了哪，
`interrupt-signal {text: fullResponse}` 回传的是**全量**猜测
（`use-interrupt.ts:27-31` → `basic_memory_agent.py:195-223`）；我方 Android 播放器进度
是精确的（`t0` 由首帧音频标定，段时长由 `speech.seg` 声明），所以回传**精确前缀**。

## 1. 端侧事实源（已在仓内，坐标给服务端对账用）

| 事实 | 端侧位置 | 口径 |
|---|---|---|
| 这句话播了多久 | `SpeechRegistry.playedMs(speechId, now)` = `now - t0` | 单调毫秒；`t0` 未标定（首帧前）= 0 |
| 说出口了几个字 | `SpeechTimeline.spokenChars(playMs)` | 完整段照收 `chars`；在播段按 `est_ms` 线性比例；封顶 |
| 送到端上的全文 | `HeardTextTracker.snapshot()`（`lk.transcription` delta 镜像） | 打断发送侧在清镜像**之前**取快照 |
| 前缀切分 | `HeardPrefix.slice(text, chars)` | **码点**口径，与 `speech.seg.chars`（Python `len()`）一致，不劈半个 emoji |
| 组包 | `HeardPrefix.bargeArgs(...)` | 本节 §2 |
| 举手找句 | `SpeechRegistry.handInterruptibleSpeechId()` | PENDING ∪ PLAYING（按键打断与 VAD 的口径差，见 §4） |
| 催话去重 | `NudgeGate`（冷却 1500ms + 回合窗口） | 见 §3 |

## 2. `control.barge_in` args 增量（C→S，A 类，已有事件类型）

```jsonc
// cmp.ctl —— 在 11-24 契约的 {speech_id, played_ms, reason} 上追加两个键
{"t":"control.barge_in","id":"...","seq":N,"ep":"...",
 "args":{
   "speech_id":"01J9..",          // 必填，语义不变
   "played_ms":1234,              // 必填，语义不变
   "reason":"vad" | "hand",       // 新增取值 "hand"：用户按举手键；"vad" 不变
   "heard_text":"她说出",         // 新增：精确已播前缀（码点切分）。总在该消息里；
                                  //      一个字没说 = ""（空串≠缺失，见幂等节）
   "heard_truncated":true         // 仅当被预算裁过时出现
 }}
```

规则（服务端照此实现即可，端侧行为已被单测钉死）：

1. **`heard_text` 总是出现**（除老端）。键存在与否是能力信号：
   - 键存在 + `heard_text:""` = 这个端会回前缀，且事实是「一个字没播出」→ memory 里
     该轮 assistant 内容写空串 + 「[Interrupted by user]」；
   - 键缺失 = 老端（未升级），**回落 11-24 契约 §2.2 的 est_ms 估算**，不报错。
2. **禁「静默无效」**（C2 红线）：`heard_text` 超预算时端侧只裁前缀（UTF-8 ≤ 1500 B，
   按码点裁，见 `HeardPrefix.MAX_HEARD_BYTES`），`speech_id / played_ms / reason` 骨架
   必在——**打断本身永远发得出去**。信封上限 4096 B（`ProtocolCodec`），预算在信封内。
   被裁时 `heard_truncated:true`：服务端知道前缀不完整，按 est_ms 口径补齐尾部。
3. **幂等**：`barge_in` 幂等性不变——同一 `speech_id` 第二次到达时该 id 已在服务端
   作废（`open_speech_id` 权威），memory 手术只生效一次；端侧重发（网络抖动）不会
   二次改写。`heard_text` 随重发可能不同（更晚的快照），服务端以**第一次**入库的为准。
4. **顺序**：前缀是播放器证据，`played_ms` 与 `heard_text` 来自同一次采样，服务端
   不要拿 `played_ms` 再估一遍覆盖 `heard_text`——前缀优先，est_ms 只在键缺失时用。

## 3. 催话（C7 空闲分支）：走 `control.input{kind:"nudge"}`，不新造事件

```jsonc
// cmp.in，C→S，A 类（与 touch / attachments 同一入口、同一 kind 判别）
{"t":"control.input","args":{
   "kind":"nudge",
   "nudge_id":"nudge-<attemptId>-<seq>"   // 幂等键：服务端按它判重
}}
```

为什么不新造 `control.nudge`：事件目录两端各一份，内容由
`feature-companion/src/test/resources/protocol_v2.json` ↔ `server/tests/fixtures/protocol_v2.json`
共享向量锁 SHA-256，单方面加 `t` 会先红两端契约测试；`kind` 判别是 touch /
attachments 已在跑的既有形状（服务端只需在 `control.input` 的 kind 分派里多认一条），
向后兼容免费。服务端未实现 `nudge` 时行为 = 收到未知 kind 丢弃，**不炸链路**。

服务端语义（提案，协调线落地）：

- 收到合法 nudge → 触发一轮主动说话（等价于一次「用户希望她主动开口」的输入；
  OLLMV 对应 `ai-speak-signal`，`use-footer.ts:41-43`）。
- **幂等**：同一 `nudge_id` 重复到达只触发一轮（与 attachments 的 `client_msg_id` 同款判重）。
- 服务端**不必**自己防连点——端侧 `NudgeGate` 已挡（冷却 1500ms；催出后回合窗口内
  按键吞掉，直到 `speech.end`/`control.cancel`/断开）。幂等键防的是网络层重发，
  两层都要有。
- 建议服务端另设最小回合间隔（如 10s）作第二道闸：端侧闸跨不了设备。
- 回合开始的标志（供端侧闸对账，均已存在无需新消息）：该轮首条 `speech.seg` = 开始，
  `speech.end` / `control.cancel` / `lk.transcription` 流结束 = 结束。

## 4. 一键两义（C7）的端侧语义（已实现，服务端了解即可）

通话页底栏常驻举手键（`CallControlBar`，静音与文字之间）：

| 她在不在说 | 键标签 | 动作 |
|---|---|---|
| 在说（PLAYING 或 PENDING） | 「打断」 | `control.barge_in{reason:"hand", heard_text}` + 本地真停（与 VAD 同一条机器） |
| 空闲 | 「叫她说话」 | `control.input{kind:"nudge"}`，过 `NudgeGate` |

口径差（钉过回归 8a7c909e，服务端不要合并这两条）：VAD 自动打断只认 PLAYING
（PENDING 时发 barge 会自掐 TTS frames=0）；**用户明确按键**时 PENDING 也作废
——「按了没反应」不可接受。服务端对两者无需区分，`speech_id` 权威作废逻辑相同。

`reason:"hand"` 是给服务端日志/统计的归因字段（手动打断 vs VAD 打断的打断率要分开看），
不参与行为分支。

## 5. 服务端 memory 手术（对 11-24 契约 §2.2 的替换点）

11-24 契约让服务端按 `played_ms` 从 `PlannedSegment.est_ms` **估算**前缀。本契约起，
`heard_text` 存在时**直接用它**（端侧播放器证据），est_ms 估算只留作键缺失时的回落：

1. `BargeInRelay` / `SpeechPublisher.cancel()`：取 `args.heard_text`（若非缺失）。
2. `HistoryService.note_interrupted`：assistant 记忆条目改写为 `heard_text + "..."`，
   追加 user（或 system，按 `interrupt_method`）「[Interrupted by user]」——
   OLLMV `basic_memory_agent.py:195-223` 的语义，词面保持 `[Interrupted by user]`
   以对齐我方提示词契约测试。
3. DB `ca_chat_message`：`content` 仍存全量（不丢信息）；`meta` 增加
   `heard_source:"client"`（vs `"est_ms"` 回落）与 `spoken_prefix_chars`。不动 migrations。
4. `heard_truncated:true` 时：`heard_text` 是前缀的真前缀，直接入库即可（它没编字），
   `meta.heard_truncated=true` 记录降级。

## 6. 时序（一次按键打断的全链）

```
用户按举手键（她在说 sp_X）
  ├─ 端：快照字幕镜像 + spokenChars(playedMs) → 切 heard_text          （发送之前！）
  ├─ 端：speech.onCancel(sp_X) 墓碑 + 本地静音/闭嘴/字幕截断           （不等往返）
  ├─ 端：cmp.ctl control.barge_in{speech_id,played_ms,reason:"hand",heard_text}
  ├─ 服：session.interrupt + pipeline.cancel(turn_id) + speech.cancel
  ├─ 服：memory 手术（§5），幂等：sp_X 已作废则只生效一次
  └─ 端：恢复可听 = 下一条被接受的 speech.seg（新 speech_id）          （11-24 §2.1-4 不变）
```

催话侧：按键 → NudgeGate 放行 → `control.input{kind:"nudge",nudge_id}` →
服务端触发主动回合 → 首条 `speech.seg` 到达 → 端侧闸进回合窗口（后续按键吞掉）。

## 7. 验收口径

- **端上（本刀，已过）**：`HeardPrefixTest`（切分/码点/预算禁静默/空串显式）、
  `HeardTextTrackerTest`、`NudgeGateTest`（冷却/回合窗口）、
  `SpeechRegistryHandInterruptTest`（PENDING 口径差）、`NudgeArgsTest`（wire 形状）、
  `CompanionHandKeyTest`（VM 转发/标签语义）、`CompanionHandKeyRenderTest`（:app 渲染可达）。
- **服务端（协调线）**：barge_in 带 `heard_text` 时 memory 用它、键缺失回落 est_ms、
  同一 speech_id 只手术一次；nudge kind 分派 + `nudge_id` 判重；
  真机口径 = 打断后 LLM 下一轮上下文含**精确已播前缀**（服务端日志取证）。
- 契约向量：`heard_text` 是 args 内字段，信封不变 → `protocol_v2.json` SHA 无需动；
  若服务端要把 `control.input.kind="nudge"` 写进向量，两仓同一改动一起落。

## 8. 不做

- 不做 word/char 级时间戳（协议无此锚点，`speech.seg` 句级已够切前缀）。
- 不加 turn 级 C→S cancel（`barge_in` 即入口，11-24 §3 结论不变）。
- 不在催话里带任何感知数据（§11.4 老规矩）。
- 不新造事件类型（见 §3 的 SHA 锁理由）。
