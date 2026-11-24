# F16 — 通话首帧 25–32s 慢启动：RCA 收口（2026-11-23）

任务：把语音通话里 `voice reply … first_ms= 25.5–32.2s` 拆解定位，给出结论与可执行下一步。
调查全程只读（104 日志/指标、本地仿真），未部署；部署与真机 attempt 由另一条线（tos，`f16-measured.md` 笔记）在跑。

## 结论（先说哪一段）

**25–32s 的主导段不是 prewarm / imports / 冷启动，而是服务端结构性扣留：`CueJsonGuard` 把首句扣到整行 cue JSON 闭合，first_ms ≈ 模型全文生成时间。**

三条线要分开：

| 线 | 症状 | 状态 |
|---|---|---|
| **首句慢启动（本篇）** | 每回合 `first_ms` 25–32s | 根因证实，修复 `7ef3ace`（server main）已含在 104 现部署 |
| 接房 10–15s（另一线） | received job → joined room 阻塞 | VAD prewarm `9fe52e2`、langgraph `e0e9890`、deerflow 链 `1118f69`、stage-6 catalog/`to_thread` `2f3eb70` + 热修 `f9fc091`，见 `f16-measured.md` |
| 语音稳定性（另一线） | 首句被打断 / `frames=0` | min_words `a734741`、min_duration `0956fae`、endpointing `421e839`、touch gate `2808748` |

prewarm/imports 只影响接房段；它们修好后 `first_ms` 不会跟着变——两者叠加在用户「点通话→说话→听到」体感上，但计时口径不同。

## 证据（room `companion-73XOA5vXJZmdZPGCk2zhmA` / job `AJ_VqBNKXPDP2zW`，全部现有日志）

`first_ms` = 提交回合 → 第一句可朗读文本进 TTS（`agent.py` llm_node）；用户听到首帧 = first_ms + TTS 首包 + 下发链。

五段分解（58 条非打断 `voice reply`）：

| 段 | 量级 | 判定 |
|---|---|---|
| ① submit→context（含 recall） | 3.4–8.7s（facts/timeline 全 `error=timeout`，0.8s 默认超时） | 次要 |
| ② 模型生成 | 16.6–28.7s | **与守卫 1:1 叠加** |
| ③ TTS 首包（tts start→首帧） | 5.36s（单样本） | 次要 |
| ④ PlatformBrain 轮询 | ≤1s | 可忽略 |
| 守卫扣留 | first_ms/total_ms **中位数 = 1.000**，tail 中位 **5ms** | **主导** |

- 长回复（len≥100）26 条全部 JSON 形态，ratio=1.000 → 首句几乎总在流末尾才出。
- 反证对照组：JSON 前有正文的几条 ratio 0.35–0.5（正文即时放行、JSON 扣到闭合）——同一守卫两种行为，锁定守卫本身。
- 本地仿真（部署树 md5 与仓库一致）：cue JSON 形态 `CueJsonGuard.feed` 在最后一个 token 才返回首句（30/30）；纯散文第 2/11 个 chunk 就出。
- 机制：语音 prompt 注入 `CUE_OUTPUT_SNIPPET`（`roleplay.py:182` / `assemble.py:63`）让整条回复是一行 `{"say":…,"cues":[…]}`，cues 在 say 之后 ⇒ say 可读时刻 == 流末尾，模型侧任何慢（TTFT、网关、prompt 体量）被 1:1 放大进用户等待。

详表与实施记录：协作笔记 `f16-first-ms-rca.md`（RCA + 7ef3ace 实施节）、`f16-server-recommendations.md`（server 建议）、`f16-measured.md`（接房/部署时间线）。

## 现网验证（2026-11-24 凌晨，只读 voice.log 统计）

**守卫修复已在现网生效**（部署树 `json_guard.py` 含 `_say_streamed_chars`，md5 `a0b15640`）：

| 窗口 | n | first_ms 中位 | ratio 中位 | tail（total−first）中位 |
|---|---|---|---|---|
| 部署前（<11-23 12:55，非打断） | 65 | 15.2s | **0.999**（tail 7ms） | 首句钉死在流末 |
| 部署后（2808748/21db8a2，有文本 reply） | 8 | 19.3s | **0.668**（tail ~10s） | 首句早于流末 ~10s ✓ |

小样本（n=8，多数回合随后被 barge-in 取消），但方向确凿：first_ms 不再 = 全文生成。

**剩余的用户等待大头已移位**（per-turn 分解，job `AJ_itNehYhcGDpf` 等 8 条）：

| 段 | 实测 | 备注 |
|---|---|---|
| ⓪ voice turn → recall（提交前排队的 drain/poll/context） | **9–10s** | 比原估 3.4–8.7s 更大，现为最大单块 |
| ② llm start → 首句进 TTS（tts start） | ~8s | 其中模型 ttft 只 2.2–5s（修复后模型本身不慢） |
| ③ TTS 首帧 `first_frame_ms` | **n=14：min 1.2 / 中位 7.2 / max 12.6s** | 比原单样本 5.36s 更重；11-21 样本曾低至 1.2–1.5s，方差大 |

11-24 通 `AJ_uDGncZakRcD9`：reply 有文本但 `tts done frames=0` + `voice turn cancel sent`——**打断类（A 类），不是守卫**，与 `said-empty-rca-20261123.md` 分层一致。

## 修复与部署状态（更新到 2026-11-24 06:0x UTC 只读复核）

- **根治已合 server main**：`7ef3ace` — `json_guard.py` say 增量放行：`"say":"…"` 字符串内部的字节按 chunk 喂 `SentenceSplitter`，整对象闭合后若已流式则只下发 `{"say":"","cues":[…]}` 壳防 TTS 复读；fail-closed 语义保留（畸形/嵌套/未闭合仍扣留，`{"say":` 前缀绝不泄漏）。回归用例：`test_f16_say_streams_before_object_close` 等。
- 辅助已合：`3296438`（prompt 连续短片段 + guard 认 cues 键序）、`35b4b89`/`1481617`（只读插桩 `tts first … first_frame_ms=`、模型开流墙钟）。
- **104 部署进度（tos 线，别重复部署/抢设备）**：2808748（12:55）→ 0a00501（volc push/result 探针）→ **21db8a2**（platform_brain stream-end/first-delta/cancel 探针，`.deploy-version` published 2026-11-23 15:04:43 +0000，worker 930199）。三者都含 7ef3ace。

## 可执行的下一步（按序，2026-11-24 更新）

1. ~~真机复测验证 7ef3ace~~ —— **服务端日志已验证生效**（见上：部署后 ratio 中位 0.668、tail ~10s，n=8）。补一通 A1 姿态（静音+文字触发长句、不打断）的干净样本即可完全关账，仍归 tos 线。
2. **主攻 ⓪ 段（现最大块）**：`voice turn` → `recall` 实测 **9–10s**（如 `AJ_itNehYhcGDpf` 15:02:48→15:02:57、`AJ_uDGncZakRcD9` 04:59:14→04:59:24）。拆法现成：21db8a2 探针（`voice turn queued/drained/cancel/first delta`）+ recall 行时间差。候选：PlatformBrain `_POLL_MS=1000` 轮询、turn 排队 drain、context build、recall 0.8s 超时×2。这是「听到第一句」剩余等待的大头。
3. **TTS 首帧保活决策（原 P2 升为可决策）**：`first_frame_ms` 已有多样本（n=14：中位 7.2s、max 12.6s，早期样本低至 1.2s）——方差本身说明冷连接嫌疑，`service/voice/volcengine_livekit.py` NewConsoleTTS 预热/保活值得做了；先让 21db8a2 通再取一窗对照。
4. **不要把最近的 FAIL 归回本 bug**：attempt `190d782a`、`MkoJY`、以及 11-24 `AJ_uDGncZakRcD9` 的 `tts done frames=0`+`cancel sent` 都是**打断/RTC 类**（见 `said-empty-rca-20261123.md` 的 2×2 分层），先收敛它们，首帧数字才干净。
5. **模型段方差**：修复后 ttft 2.2–5s（`voice llm` 行已在打），原「同网关 2s vs 30s」的主体其实是被守卫放大的 total；残余方差继续用 `voice llm` 行观测即可，不必急着给 worker 挂 /metrics。
6. **止血备选**（仅当线上异常）：语音通道停注 `CUE_OUTPUT_SNIPPET`（`roleplay.py:182`、`assemble.py:63`）。

## 未验证事项（别当已修）

- 部署后样本 n=8 且多数被 barge-in 取消——**ratio<0.9 的方向确凿，精确值待一通不被打断的 A1 姿态通话**。
- 火山 TTS 片段级 flush 行为未确认（plugins 自带 sentencizer，不保证每片段立即出声）；③ 段 7s 中位里有多少是连接建立、多少是合成排队，要保活实验才能拆。
