# 陪伴通话 presentation.emotion 缺失 RCA 与修复（2026-11-24）

对应报告：`docs/design/p05-p2-reconciliation-20261123.md`（P0.5/P2 对账线）。
状态：**根因闭环，两端修复已合入；端侧 event 证据待装机窗口**。

## 结论

服务端确实没发 cue，但发送路径没坏——**端侧把安静的陪伴通话误判成 L3 降级并自报**，
服务端按自报档位把 cue 全闸掉（这是 B4 的设计语义，闸本身没错）。三通 job
（AJ_UNX4td6gFQfp / AJ_WQikByEhPQ8U / AJ_r2VuLzGL8Dj6）104 voice.log 全程
`client health … degrade=L3 applied=4/5`，`AnchoredCuePublisher._gate` 对 level≥2
一律 `frozenset()` 全丢。

对照闭环：剧本房 cue 路径 PASS 是因为剧本房客户端**不发** `telemetry.health` →
服务端 telemetry=None → 永远 L0 → cue 照发。陪伴通话发 health → 错误自报被忠实执行。

## 误判机理（DegradeController）

陪伴通话里 `cmp.*` 只在回合边界前后有包，这是协议形状本身：

- 她一句长 TTS 播 15 秒，期间 cmp 一条不发明——正常；
- 用户在听/在说，30 秒无 cmp——正常；
- 远端轨保活帧让 PCM 恒「活」→ 旧规则 `data 静默>6s && pcm≠null` 把每种正常
  都读成 **L3**。

还有自我强化回路：L3 → 服务端闸掉全部 cue → cmp 更静 → L3 锁死
（`applied=4` 恒定不回落）。端侧只有 `emotion_cleared`（进 L2 上升沿清表情，B3
行为）——它本身就是「端上也认为自己在 L2+」的证据。

可观测性缺口：publish 成功本来静默，报告用 `grep presentation.emotion` 判「没发」
对任何通话都永远是空——已在服务端补上（见下）。

## 修复

### Android `1ccf3642`（weifu-refactor）

- `DegradeController`：静默类升档（L2 cue 静默 / L3 data 静默）加闸
  `silenceUpgradeArmed(now)`——只在 `speechExpected`（有 speech 声明未 end/cancel）
  **且**没有新鲜可听帧（`onAudio` 新鲜=她在说话，播放期 cmp 静默解释得通）时评估。
  空闲/播放期静默最高 L1。L4（PCM 死、连接断）不受影响。
- `SpeechRegistry.expectsCueData()`：PENDING ∪ PLAYING（seg 已声明意味着 cue 与
  后续 seg 正是该到的时候）。
- `LiveKitCompanionTransport`：speech.seg / speech.end / control.cancel 后刷新
  `onSpeechExpected`；重连与断连清 latch；PCM sink `rms≥REMOTE_FIRST_AUDIO_RMS`
  帧喂 `onAudio`。
- 测试：RED 3 failed → GREEN 21/21（`DegradeControllerTest` 新 4 用例），
  `protocol.*` 全套 BUILD SUCCESSFUL。

### 服务端 `44d2d27`（agent-docs main，已于 11-24 部署 104）

只补观测性，不改闸语义：

- `cue sent t=… speech=… seg=…`——发一条记一条；
- `cue gate … level=L3 dropped=N kept=M`——闸掉留行，与「模型没出 cue」区分；
- `voice reply … cues=发出/意图`——每回合一条汇总。

## 装机后验证口径（同 job/attempt 取证）

1. voice.log `client health … degrade=`：安静窗应回 L0/L1，不再全程 L3；
2. `cue sent t=presentation.emotion` 出现 = 服务端实发；端侧对应
   `event=emotion_applied`；
3. `voice reply … cues=0/N` 且无 gate 行 → 归 prompt/模型侧，另行处理；
4. 仍有 gate 行 → 行内 dropped 数 + 端侧 health 自报可直接两侧对账。

## 边界与未动项

- `_gate` L2+ 全丢语义保留（镜像端侧 `shouldEmit`；错的是档位输入，不是闸）。
- B4 的 L1 emotion 半频镜像语义未动。
- 真机端侧 event 证据：待协调会话装机窗口（APK 从 weifu-refactor tip 现场构建）。
