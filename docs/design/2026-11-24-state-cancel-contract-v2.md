# P1/P2 契约:角色状态显式事件 + 真取消式打断(协议 v2 增量)

日期:2026-11-24。前置:总方案 `2026-11-24-vtuber-extension-plan.md`(P1/P2)。
两端现状勘查结论(类名/字段以两份勘查报告为准,本文只定契约):
- 端上 v2 已有 `presentation.state/emotion/gesture/gaze`、`speech.seg/end`、`control.cancel`(S→C)、`control.barge_in`(C→S);缺 `interrupted` 状态,缺「本地真停」。
- 服务端 `presentation.state` 在 `catalog.py`/`handshake.py` 已编目但 **cap=0、无发送方**;`speech.seg` 不带 `turn_id`;历史(`ca_chat_message`)存的是清理后的**全部已生成文本** + `interrupted` 布尔,无「已说话前缀」;HTTP cancel 是会话级,port `send_cancel(turn_id=)` 是回合级。

## 1. 状态词汇(P1)

规范化 wire 值(小写,`presentation.state` 的 `args.state`):
`idle | listening | thinking | speaking | interrupted`

| 状态 | 谁产生 | 时机 |
|---|---|---|
| `thinking` | 服务端 | 语音回合开始生成(voice turn started)时发 |
| `speaking` | 服务端 | 每段 `speech.seg` 首发前发(与 §13-1 顺序:state → speech.seg → cue → TTS) |
| `idle` | 服务端 | 回合结束(`message.done` / `turn.cancelled` 排干后)发 |
| `interrupted` | **端侧乐观本地写**(barge-in 触发即写,不等服务端)+ 服务端 barge 确认后也可发(可缺省) | 打断瞬间 |
| `listening` | **端侧本地推导**:麦克风开、无活动 speech、无生成 | 等待用户说话 |

规则:
- 服务端 `presentation.state` cap 从 0 置 1;事件类别仍是 DEADLINE。
- 端侧 `PresentationCue.parseState` 由「透传小写」改为**校验白名单**,未知值丢弃并计入 receiver 计数(不炸渲染)。
- 端侧 `AvatarState` 枚举加 `INTERRUPTED`;`AvatarEventMapper`、`SimpleAvatarRenderer.SetState`、dh 引擎按现有 STATE 能力处理;无 `INTERRUPTED` 素材的模型退回 `IDLE` 表现 + 立即闭嘴。
- `CompanionCallState` 增加 `avatarState` 字段(现仅有 `isAgentSpeaking` 从 streaming 推导),通话页表现层直接吃状态。

## 2. 真取消式打断(P2)

### 2.1 端侧(barge 触发瞬间,不等服务端往返)
`BargeInGate` 火后,`sendBargeIn` 在现有「作废时间线 + 清 cue + 发 `control.barge_in{speech_id,played_ms,reason:"vad"}`」之外**追加**:
1. **本地静音**:远端音量置 0(现有 `setPlaybackAudible(false)` 语义)+ `_mouthLevel=0f` + 清空 `_remotePcm` 缓冲(把只存在于测试 rig 的 `flushRemotePcm` 接进产品路径)。
2. **字幕截断**:当前 streaming 消息停止追加 `TextDelta`,按「已完整播出的段」的累计 `chars` 截断气泡文本,消息标 `interrupted`。截断边界 = Σ 段 `chars`(该段 `endOf(idx) ≤ barge 时刻`)。段边界字符数与 `speech.seg.chars` 之和即字幕前缀长度(字幕流与 TTS 文本同源同序)。
3. **状态**:`avatarState=interrupted` 本地先写(见 §1)。
4. **恢复**:下一个 `speech.seg`(新 speech_id)到达即恢复可听 + 恢复口型喂数。

### 2.2 服务端(barge 到达)
`BargeInRelay` 现有 `session.interrupt()` + `speech.cancel(reason)` 之外**追加**:
- **停生成**:`pipeline.cancel()`(→ `PlatformBrain.cancel` → `send_cancel(turn_id)`,回合级)。`TurnRunner` 现有 `state.cancelled` 检查 + `stream.aclose()` 已支持,无需新机制。
- **已说话前缀入库**:`speech.seg` args 增加 `turn_id`;`SpeechPublisher.cancel()` 时按客户端上报 `played_ms`(服务端仍以 `open_speech_id` 为准)从 `PlannedSegment` 累计 `est_ms` 算出「说完的整段 + 正在段的按字符比例部分」→ 前缀文本;`after_turn`→`HistoryService.note_interrupted` 用**该前缀**(而非全部已生成文本)+ 现有 `INTERRUPTED_SUFFIX` 写进 LangGraph 线程。DB `ca_chat_message.content` 仍存全量(不丢信息),`meta` 增加 `spoken_prefix_chars`(无需新列;`meta` 是 JsonText)。**不动 migrations**(基线守卫测试不受影响)。

### 2.3 wire 增量(两仓同步)
- `speech.seg` args:`{speech_id, idx, chars, est_ms, dur_ms?, turn_id?}` —— `turn_id` 可选,旧端忽略未知键,向后兼容。
- `control.barge_in` args 不变(服务端已按 `open_speech_id` 权威处理)。
- 共享跨语言向量 `feature-companion/src/test/resources/protocol_v2.json` ↔ `server/tests/fixtures/protocol_v2.json` 同步新增:`presentation.state{state:"interrupted"}` 合法向量、`speech.seg` 带 `turn_id` 向量。

## 3. 不做

- 不加 turn-level 客户端→服务端 cancel 类型(barge_in 即入口,turn 归属由服务端 `open_speech_id` 权威绑定)。
- 不加新 DB 列、不动 `0001_baseline`。
- 不动 `presentation.resync`(已被两侧刻意删除,不复活)。

## 4. 验收

- 服务端离线用例 `cases/voice/barge_mid_sentence.yaml` 扩展:断言顺序含 `presentation.state(thinking→speaking)`、barge 后 `cancel`、`RecordingBridge` 无后续 `speech.seg`;新增前缀计算单测(FakeVoice 路径)。
- 端上纯层单测:`parseState` 白名单、字幕截断边界纯函数、`sendBargeIn` 追加动作的 fake-transport 测试。
- 真机口径(总方案 §4):打断后 2s 内本地完全静默;字幕无未说话尾巴;下一轮模型上下文只含已说话前缀。
