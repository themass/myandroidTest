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

## 修复与部署状态（截至 2026-11-23 14:21 UTC）

- **根治已合 server main**：`7ef3ace` — `json_guard.py` say 增量放行：`"say":"…"` 字符串内部的字节按 chunk 喂 `SentenceSplitter`，整对象闭合后若已流式则只下发 `{"say":"","cues":[…]}` 壳防 TTS 复读；fail-closed 语义保留（畸形/嵌套/未闭合仍扣留，`{"say":` 前缀绝不泄漏）。回归用例：`test_f16_say_streams_before_object_close` 等。
- 辅助已合：`3296438`（prompt 连续短片段 + guard 认 cues 键序）、`35b4b89`/`1481617`（只读插桩 `tts first … first_frame_ms=`、模型开流墙钟）。
- **104 现部署 = `2808748`**（`.deploy-version` published 2026-11-23 12:55:32 +0000，voice pid 900973），已包含 7ef3ace。**tos 线在跑发布与真机 attempt，不要重复部署、不要抢设备。**

## 可执行的下一步（按序）

1. **真机复测验证 7ef3ace**（tos 线）：静音 + 文字触发长句（A1 PASS 同姿态）。判据：
   - `voice reply first_ms/total_ms` 中位数 **< 0.9**（修复前 1.000）；
   - 出现 `tts first … first_frame_ms=` 行，③ 段可直接读数。
   - 若仍 ratio≈1.0 → 先查部署版本是否含 7ef3ace，再看模型是否没出 cue JSON（走散文另查 prompt）。
2. **不要把最近的 FAIL 归回本 bug**：attempt `190d782a`（整 job `tts` 日志 0 条、`interrupted=True`）与 attempt `MkoJY`（零 voice turn、13:05:24 三 data channel 异常关）是**另两类失败**（回合风暴/打断、端侧 RTC），发生在 2808748 上但与被扣留的 first_ms 无关——先让这两类收敛，首帧数字才有意义。
3. **模型段方差关闭**（观测已在树内）：同网关有的回合 2s、有的 30s。worker 104:8082 无 `/metrics`（404）；部署后用 `voice turn`→`recall`→`tts start` 时间差 + `voice llm` 行拆 ②；如需指标，给 worker 挂与 api 同套 `/metrics` 或回合结束 INFO drain `LlmCallCollector`（ttft/total/reasoning_chars 字段已有）。
4. **次要点（不挡验收）**：
   - TTS 冷连接 ~5s：`service/voice/volcengine_livekit.py` NewConsoleTTS——先用多样本 `first_frame_ms` 量化再决定保活；
   - recall 0.8s 超时：修 facts/timeline 后端或调 `agent/memory/pipeline.py` `DEFAULT_TIMEOUT_SEC`；
   - `service/turns/platform_brain.py` `_POLL_MS` 1000→200。
5. **止血备选**（仅当 7ef3ace 线上异常）：语音通道停注 `CUE_OUTPUT_SNIPPET`（`roleplay.py:182`、`assemble.py:63`），散文即时流式，暂失结构化 cues。

## 未验证事项（别当已修）

- **7ef3ace 的真机 first_ms 数值未测**——结论是「根因证实 + 修复已部署」，不是「首帧已达标」。达标以第 1 步真机数据为准。
- 火山 TTS 片段级 flush 行为未确认（plugins 自带 sentencizer，不保证每片段立即出声）。
