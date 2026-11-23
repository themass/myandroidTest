# P0.5/P2 真机对账报告（2026-11-23）

Owner：elastic-mendeleev 会话（`claude/elastic-mendeleev-ab3d36` @ `5db985a9`）
设备：ADY-AL00 `2MH0224B20002068`（本报告**不抢设备**；线上 LiveKit 主冒烟归协调会话）
取代：`p2-e2e-status.md`（2026-11-19 FAIL/PARTIAL 快照）

## 1. 目标与计划

**目标**：对账 P0.5/P2 真机四项，把「代码已落地」与「真机已过线」分开写清，并记下本会话新钉出的回归风险。

**四项清单**（沿用 `p2-e2e-status.md`）：

| # | 项 | 判据 |
|---|---|---|
| 1 | `voice turn` / `voice reply` + `said=` | 有回合；`said=` 为干净散文；同轮 `tts done peak>0` |
| 2 | 首帧无 blocked imports | job 进程 `prewarm` 盖住冷 import；无明显 1s+ stall 挡 hello/首帧 |
| 3 | `presentation.emotion` | 服务端发出 cue；端侧收到并落到通话页渲染器 |
| 4 | `control.barge_in` → 停口（F11） | 可听下行中打断；端侧 cancel；服务端 TTS 截断 |

## 2. 已完成（证据已在 sibling notes）

### 代码落地（不靠真机）

| 块 | 仓库/分支 | 证据 |
|---|---|---|
| P0.5 `cmp.cue` + `telemetry.health` | Android `4ca7de4f`；server `bed4d91` | `p05-smoke-loop-done.md` |
| P2 barge_in 全链路接线 | Android `24b92f4d`；server `ca2f270` | `p2-barge-in.md` |
| 通话页 emotion/state 两跳 | Android `36c4b499`（AvatarEventMapper + CompanionRoute dispatch） | `p4-device-accept.md` |
| CueJsonGuard / outbound probe / pause 门诊断 | server 多提交，见 `remote-pcm-rca.md` | 同上 |
| Redis / VAD / langgraph prewarm | server `1174acb` / `9fe52e2` / `e0e9890` | `f16-measured.md`、`p4-device-accept.md` |

### 真机过线（当前栈上的 PASS）

来源：`remote-pcm-rca.md`「A1 PASS」段 + `p06-handover.md` 更正段。

- **attempt `ccd3e3a6`**（2026-11-22 00:26–00:35 本地），APK tip `7e218cc8` 重装，room `companion-73XOA5vXJZmdZPGCk2zhmA`，sid `675b041f7adf`。
- 姿态：静音 + 文字触发（避开环境噪音 VAD）。

| # | 项 | 结果 | 证据摘要 |
|---|---|---|---|
| 1 | voice turn/reply + said= | **PASS** | 三轮干净 `said=`（「你好呀…」「从前有个小孩…」「从前有座山…」）；`tts done` peak 0.95–0.96、frames 41–180 |
| 1b | 下行可听（A1） | **PASS** | 同轮设备 `remote_pcm_summary maxLevel` 0.788 / 0.900 / 0.858；104 第二订阅者同轨 peak **0.9838** |
| 4 | barge_in → 停口（F11） | **PASS** | 可听窗内 `speech_cancel … reason=user_barge_in`；同轮服务端 `tts done completed=False`（生成器被掐） |

旁证剧本房（不替代陪伴通话项，但证明 cue 路径能发）：`script-room-device-accept-wip.md` `barge_mid_sentence` 时间线含 `presentation.emotion` + `control.cancel`（`--tts fake`）。

## 3. 未完成 / 部分完成

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 2 | 首帧 blocked imports | **PARTIAL** | redis/VAD/langgraph 已进 prewarm；`first_ms` 仍 25–32s（F16 候选，见 `f16-measured.md` / `f16-voice-fragments-shipped.md`）。**不等于** 2019 快照里的 `osc_data._core` 998ms 仍在——那条已过时，但「首帧够快」还没过线。 |
| 3 | `presentation.emotion` 陪伴通话真机 | **PARTIAL** | 代码两跳已合入；剧本房见过 cue；陪伴通话 PASS 窗**没有**单独把端侧 `event=emotion*` 钉成主证据。`remote-pcm-rca.md` 有一条 calm 到达后 `cue_dropped … CANCELLED`——说明 cue 能到端，但被 cancel 清掉，**听感/脸感未单独验收**。 |
| — | 统一构建回归冒烟 | **OPEN（不抢设备）** | `unified-device-window-schedule` Phase2 第 11 项：A1/F11 在**当刻 tip** 上一眼复现。协调会话已切生产 LiveKit=`wss://livekit.ok123find.top`，主冒烟归它。 |
| — | 本会话新缺陷：PENDING barge-in 自掐 | **代码已修，真机待验** | 修复提交 `8a7c909e`；见 §4 |

## 4. 本会话进展（2026-11-23，elastic-mendeleev）

### 4.1 本地栈取证（验收基线之后已作废为「主路径」，仍作旁证）

在 Mac 原生 livekit-server + 本地 worker 上：

1. 设备通话 attempt `1e49c1d9`：`tts start` → **无** `tts first` → `tts done frames=0 completed=False`；同秒设备 `event=barge_in … playedMs=83` → `speech_cancel reason=user_barge_in`。
2. 离线假用户 `/tmp/tts-probe/repro-room.py`：同 worker/TTS/SFU → `tts done frames=11 peak=0.9164`，livekit-server 转发 163 audio/red、丢包 0。**服务端合成+发布链本身绿。**

### 4.2 根因（客户端）

`SpeechRegistry.activeSpeechId()` 把 **PENDING ∪ PLAYING** 都当成可打断对象；`speech.seg` 一到就 PENDING，此时还没有任何远端 PCM。
`BargeInGate` / `LiveKitCompanionTransport` 用这个 id 发 `control.barge_in` → 服务端 `BargeInRelay` → `session.interrupt()` → TTS 生成器 GeneratorExit → frames=0。

与 §10.1「她在说 + 你开始出声」不一致：PENDING ≠ 她在说。

代码修复（`8a7c909e`）：
- `activeSpeechId()` 只认 **PLAYING**（t0 已标定），同步 gate 参数注释；
- `SpeechRegistryTest` 两条回归先红：PENDING 返回 null、较新的 PENDING 不遮住已有 PLAYING；修改后转绿；
- `SpeechRegistryTest` / `BargeInGateTest` 及模块全量 `:feature-companion:testDebugUnitTest` 已通过；
- 真机仍待在生产 LiveKit 上验证首帧前不误打断、播放中仍能打断，不以单测代替真机验收。

### 4.3 基线变更（协调会话通知）

生产 LiveKit 已切 104 自托管 `wss://livekit.ok123find.top`。手机蜂窝可直连，**不再要求**与 Mac 同网段。Mac 本地栈保留作旁证，不为它再折腾设备网络。

### 4.4 生产 attempt 5313185b（V2279A，2026-11-23）

104 `voice.log` job `AJ_rGzQGNyzfjqr`：`tts done` **frames=0** 全轮，`interrupted=True`，无 outbound chain energy。与客户端 `remote_pcm maxLevel=0` 一致。**不是** SFU 订阅故障；是 **TTS 未出帧即被打断**。APK 已含 `8a7c909e`。全链 PASS **未达成**；复测需停口等待或减少连续 STT final。

## 5. 结论（给协调会话）

1. **P0.5/P2 四项：1 与 4 在 2026-11-22 栈上 PASS；2 与 3 仍是 PARTIAL。** 不能再沿用 11-19 的「全线 FAIL」口径，也不能声称 §17 全过。
2. **对账报告此前只活在 notes 碎片里**；本文件是正式落盘。仓库副本：`docs/design/p05-p2-reconciliation-20261123.md`（同文）。
3. **本会话不抢设备**。线上冒烟 / CLAIM 窗口归协调会话。
4. **PENDING barge-in 代码已修**（`8a7c909e`），单测红→绿已完成。下一步为真机复测，等协调会话排窗或统一构建冒烟时顺带。
5. **F16 first_ms 25–32s** 与 emotion 听感/脸感专项仍开放，不在本次对账关闭范围内。
