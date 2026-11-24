# LiveKit 极致性能优化 · 阶段一：现状盘点 + SLO/指标提案（2026-11-24）

> 只读盘点产出。不改代码、不部署、不重启生产。目的：把「指标商定」前的全部现状取齐，
> 供逐条讨论定稿 SLO 门槛与分阶段优化计划。阶段二起才有改动。
>
> 取证方法全部可复核：Android 侧读 `origin/weifu-refactor`；服务端读 104
> `/home/web/webroot/companion-agent/.data/logs/{voice,api}.log` 与部署树；
> 拓扑读 `/Users/gqli/work/myconf`（HEAD `baec0b2a`）。

---

## 0. 两个必须先说的现网更正（与任务前提不一致）

1. **F16 prewarm `2a098aa` 与 20s 初始化预算（`claude/f16-voice-fragments`，含
   `698a7dc4`+`316f53f8`）已于 2026-11-24 14:22 UTC 随 main tip `dced3cab` 部署 104**
   （`.deploy-version`: dced3cab / branch main / published 14:22:16 +0000，来源
   `/home/web/work/agent-docs-stage5`；voice worker 已重启，pid 1326985/1327176，
   job 进程 forkserver 1327313/1327315）。谱系核对：`2a098aa`、`4b6a82f`、`9292391`、
   `316f53f8`、`6788ac20`、`cd9a70db` 均为部署 HEAD 的祖先。
   → 「同批验证」不是待办，是**待取证**：14:23 重启后现网还没有一通通话落到新 worker
   （voice.log 14:00Z 后仅 15 行，`received job` = 0）。验证判据见 §5 阶段 B。
2. **cue 空壳修复（`fix/cue-shell-anchor-20261124`）仍未落码**：worktree
   `/Users/gqli/work/cue-shell-20261124` HEAD=cd9a70db（=部署基线），工作区仍有**未提交
   RED 测试**（`server/tests/test_agent_speech_wiring.py` +2 tests）。别丢。

附带新发现（本次盘点在现网日志直接看到，先记账不修）：

- **`relationship read failed` 的真实错误码是
  `TypeError('tuple indices must be integers or slices, not str')`**（context.py，
  每语音回合都触发，含 11-24 现部署）。⓪段 RCA 里 (a) 缺表 / (b) 池超时 二选一被证伪，
  实为 (c) 代码 bug——修复廉价、且每回合都白付这段 gather 尾巴。
- **prewarm 成本已到 11.1–14.6s（modules=27），进程 initialized 18.4s，距 20s 预算只剩
  ~5s 余量**；且 `timed out waiting for idle processes` 全日志 26 次（11 月内 12 次）——
  热池经常没热，冷 spawn 通话要当场付 prewarm（见 §2 接房样本）。
- **⓪段探针（86d63a7 系列行）已随 dced3cab 上线**，11-24 11:21/11:23 两通各留下一组
  分段数字（见 §2 表）。

---

## 1. 三端现状盘点（能力与缺口）

### 1.1 Android 客户端（origin/weifu-refactor，行号按该 ref 的 blob）

埋点全部在 `feature-companion/.../LiveKitCompanionTransport.kt`（tag `MouthLevel`），
格式 `attempt=<8hex> event=<token> k=v`。**attempt 每次 `connect()` 重新生成（:247），
SDK 自动重连不换**；attempt 永不上线（协议 envelope 无此字段），与服务端 `job AJ_…`
关联只能靠 `hello_ack sid=` ↔ 104 `protocol negotiated sid=` + 手工时间对齐——这就是
proc-table 现行做法。

已发事件（按时序）：
- 连接：`connect_start`(:256) → `room_created`(:269 elapsedMs) → `room_connect_begin`(:375)
  → `room_connect_success`(:388 elapsedMs) / `room_connect_timeout`(:393，
  `CONNECT_TIMEOUT_MS=20_000` :1413) / `room_connect_failure`(:403)；
  `room_connected`(:284 elapsedMs) 是 SDK 事件的同里程碑双发
  （proc-table 时间线里写的 `connect_success` 就是这个 token 的简称；失败路径无
  elapsedMs，只能靠 logcat 墙钟差）；
  `reconnecting`(:314) / `reconnected`(:320 elapsedMs **但基线是首次 connect，不是本次
  重连起点** :248 只设一次) / `disconnected`(:347 reason)。
- 握手：`hello_sent`(:472) / `hello_retry n`(:446，间隔 3s×20=60s 预算 :1416-1417) /
  `hello_gave_up`(:451) / `hello_ack sid,compat,caps`(:1168)。
- 上行：`local_track_published`(:360) `mic_applied`(:504) `local_pcm_first`(:1021)
  `local_pcm_summary samples,meanRms,maxLevel,windowMs`(:1025)。
- 下行：`remote_sink_attached`(:795) `remote_pcm_first rms`(:870，常为 0=引导静音)
  `remote_pcm_first_audio rms`(:873，阈值 0.02 :1380) `remote_pcm_summary`(:877)。
- 打断：`barge_in id,playedMs`(:717 **在 publish 成功臂内打，时间戳=发布完成时刻，
  不是决策时刻**) `speech_silenced`(:738) `speech_end`(:1229) `speech_cancel`(:1244)。
- 降级/cue：`degrade_force`(:147) `emotion_cleared`(:1301) `cue_gated`(:1316)
  `cue_dropped`(:1329) `cue_rejected`(:1274)。

时钟与口径：
- 客户端计时只用 `SystemClock.elapsedRealtime()`（单调钟），`elapsedMs` 基线
  `connectStartedAtMs`(:96/:248)。**全部 companion/protocol 代码零
  `System.currentTimeMillis()`**；logcat 行自带设备 epoch（墙钟）。
- t0（§9.4）：`SpeechTimeline.startFromCapture`——`t0 = now_mono -
  (now_capture - first_capture_ts)`（`protocol/SpeechTimeline.kt:91-92`），capture ts 取
  LiveKit sink `absoluteCaptureTimestampMs`(:855)；仅当 rms>0.02（与 FirstAudioDetector
  同阈 :175）才标；t0 只定一次(:97-105)。`playedMs` = 单调 ms 自 t0 的已播时长
  （`SpeechRegistry:338-341`），不是延迟。
- **`Envelope.ts`（发布方墙钟）被解码(`ProtocolCodec.kt:66/:143`)但从不消费**；
  协议 v2 §5 规则 5 明说跨设备漂移可达秒级。→ 端↔服务端**没有任何时钟同步机制**。

关键缺口（对首音频 SLO 致命）：
- **发布构建不发这些日志**：`logInfo/W/E` 全部 `if (isDebugBuild)`(:1429-1441)——release
  包一行都没有。生产 SLO 若建在这上面必须先决策（见 §4 决策项 D1）。
- 无「用户说完」（endpointing）端侧测量——只有 barge 用的mic RMS 上升沿门
  （`BargeInGate.kt:22-37`，`SELF_NOISE_GATE=0.003`）。
- 无 ICE 候选类型/TURN 使用/RTT/丢包/jitter 深度——零 `RoomOptions`（裸
  `LiveKit.create` :259）、零 `NetworkQuality`。livekit-android 2.29.0。
- 无日志落盘、无上传；唯一上线遥测是 `telemetry.health`（30s 周期 :1420，cmp.tel，
  计数+`degrade_level`+`late_ratio`）——协议健康代理，非 RTC 质量。
- §14.3 设计的 `cue_intent_to_apply_ms` p50/p90 **完全没发**（lateMs 算完即弃
  :1308）；§17 P3 验收线（p90 ≤250ms）已定但无数据面。
- token：`POST /v1/voice/sessions`（`CompanionApi.kt:101`），`expires_at` 解析后从不读
  （`CompanionModels.kt:18`）；**无刷新路径**。服务端 TTL=600s
  （`livekit_tokens.py:125 ttl_sec=600`，`config.py:43 companion_token_ttl_sec=600`）。
  >10 分钟通话掉线接不回（旧结论仍在）。

测量收集现状：手工 `adb logcat -s MouthLevel | tee /tmp/<name>.log` + 104 voice.log
时间对齐（proc-table 的既有口径）；`scripts/devicetest/*` 只收 crash 缓冲，无通话计时收集器。

### 1.2 104 companion-agent voice 服务（现网 dced3cab）

日志：`/home/web/webroot/companion-agent/.data/logs/voice.log`（98MB，11-15→11-24）、
`api.log`。每行 JSON 带 `job_id AJ_…`/`room`/`room_id`/`session_id`，可与端 `sid` 配对。

链路事件（服务端侧，全部现网在打）：
- 接房：`received job request` → （冷池时）`initializing process` +
  `prewarm done modules=27 vad=loaded cost_ms=…` + `process initialized
  elapsed_time=…` → `RoomIO linked to participant` → `volc asr stream start/open` →
  `companion joined room`。
- 回合：`voice turn` 家族（queued/drained/cancel + 86d63a7 探针：`voice submit chain
  upstream_ms/activity_ms`、`voice turn submit done cursor_ms/submit_ms/status/
  disposition`、`turn prelude ms`、`context build ms`、`voice prepare ms`、`recall …
  recall_ms/session_ms/gather_ms`、`brain first graph chunk ms`）。
- 出话：`voice reply user=… first_ms= total_ms= interrupted= cues=x/y said='…'`；
  `tts start` → `tts first turn=… first_frame_ms= peak=` → `tts done frames= audio_ms=
  peak= completed=`。
- 旁路：`inbound audio summary`、`volc asr push summary`、`user_transcript final=True`、
  `protocol negotiated sid=`、`client health … degrade=L0`、`relationship read failed`。

口径定义（现有代码语义）：
- **`first_ms` = 回合提交 → 第一句可朗读文本进 TTS**（agent.py llm_node）；不含 TTS
  合成、不含下行传输。`tts first first_frame_ms` = tts start → 首帧出（合成+连接）。
  用户听到 ≈ first_ms + first_frame_ms + 下行（下行实测 ~70ms，见 §2）。
- 空回复三分类（tos RCA 已定死，不重开）：`interrupted=True said=''`=barge-in 设计语义
  （1085 条里 853 条，指标噪声）；`interrupted=False said=''`=真候选（52 条：机制 A 120s
  不收流已修 9292391、机制 B cue 残尾已修 4b6a82f、**机制 C MERGED 跟随者游标未修**）。

### 1.3 LiveKit 服务与网络拓扑（myconf HEAD baec0b2a + 104 现场核对）

```
App ─ wss://livekit.ok123find.top:443 → 8.217.122.83 nginx（TLS 终结）
      → 104.160.191.87:80 nginx（本机） → 127.0.0.1:7880（LiveKit，只绑回环）
媒体：UDP 50000–50019 直达 104；TURN UDP 3478（内置，自动发凭证）；ICE-TCP fallback 7881。
```
- livekit-server **v1.13.11**（官方 release 二进制，`current` 符号链接可回滚）；
  systemd `livekit.service`（User=web，Restart=on-failure，LimitNOFILE=65535；
  **无 CPU/内存 cgroup 限额**）。无 Docker（104 未装，livekit.sh:10 明文）。
- config.yaml：`rtc.node_ip=104.160.191.87` 钉死 + `use_external_ip=false`（多公网 IP
  网卡坑）；`turn.udp_port=3478` 显式（不配崩）；TURN/TLS 5349 未启用。
  **无 room 默认值（empty_timeout/max_session_duration 用上游默认）、无任何 limits
  （max_clients/cpu_cost）、无 metrics 端口**（现网 `ss` 证实未监听）。
- 信令两跳的 `/rtc/`→`/rtc` 改写修复（baec0b2a）已在配置里；`proxy_read_timeout
  3600s` 保 WS 长连。
- 防火墙：104 云安全组 11-23 已开 udp 50000-50019 / tcp 7881 / udp 3478；7880 不对外。
- 变更/回滚工具：`livekit.sh`（status 自探 + restart）、`livekit-companion.sh`
  （.env 三键 switch/rollback + 时间戳备份）、publish.sh/.deploy-version（服务端代码）。
- **机器容量现状（本次实测）**：104 = **1 vCPU / 2GB RAM（+2G swap，已用 858MB）**；
  空载 PSS：livekit-server 26MB、companion serve 104MB、worker 28MB、forkserver 父
  139MB、**两个 idle job 进程各 ~295MB**——两通并发通话≈590MB 直接压在已用 swap 的机器上。
  journalctl 里 livekit `high cpu load (≈1.0)` 11-23 以来 10 次。**单并发是当前物理上限，
  不是软件调优问题。**

---

## 2. 既有测量证据（每个数字带口径与窗口）

### 2.1 协议四项（proc-table 三轮 attempt/job 配对，11-23 夜）——**已 PASS，关闭，不重开**
`docs/design/p05p2-proc-table-20261123.md`：hello/上麦、t0 能量门、可听窗打断（含截断
样本 `completed=False`）、下行可听（A1 口径仪器证+用户口径）全 PASS。第 4 行 first_ms
FAIL 移交本线。

### 2.2 下行传输链不是瓶颈（同 attempt 双端对表）
`AJ_itNehYhcGDpf`（11-23 23:03 北京）：104 出帧 15:03:18.42Z → 端
`remote_pcm_first_audio` 23:03:18.49（同一瞬间，北京=UTC+8）≈ **70ms**。慢全部在
TTS 首帧之前。⚠️ 这是一次偶然样本的跨墙钟相减，设备↔104 无时钟同步——把它升级为
指标前必须做 §3-C3 的偏移标定。

### 2.3 端到端首音频（服务端 `voice reply first_ms`，含/不含打断混合）
全 voice.log（11-15→11-24，n=362 非空 first_ms）：

| 分位 | min | p50 | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|
| first_ms | 1.6s | **14.2s** | 25.5s | **31.0s** | 39.4s | 57.3s |

按日（n 小要谨慎）：11-22 n=18 p50 15.7s；11-23 n=19 p50 18.2s p95 31.0s；
11-24 n=3 p50 17.6s。proc-table 正式样本 30986/19559/18529ms 与分布一致。
（任务背景里「19.6–31.3s 慢样本」即该分布的 p90–p95 段。）

### 2.4 first_ms 分段分解（RCA `docs/f16-tts-first-frame-latency-rca.md` + 11-24 探针实测）
守卫扣留（first_ms≈全文生成）已被 `7ef3ace` 解除（ratio 0.999→0.668，tail 7ms→~10s，
现网已验证）；剩余大头：

| 段 | 口径 | 实测 |
|---|---|---|
| ⓪ 提交→context/recall | `voice turn`→`recall` | **n=564：中位 4.33s / p75 8.7s / p95 15.6s** |
| ② 模型生成 | recall→首句进 TTS | ttft 2.2–5s（修复后） |
| ③ TTS 首帧 | `tts first first_frame_ms` | **n=14：min 1.2 / 中位 7.2 / max 12.6s**（11-21 曾低至 1.2–1.5=热连接下限） |

11-24 探针逐段（两通，样本 n=2 只作形状证据）：
`submit_ms 6206`（其中 upstream 4523 + activity 1683 + cursor 970）→ `turn prelude
2907` → `voice prepare 2165` → `brain first graph chunk 3381` ≈ **~14.7s 到模型开流**，
与 first_ms p50 吻合——⓪段内部（串行库往返 + relationship 失败 gather 尾巴）是第一嫌疑。

### 2.5 接房 `received job → companion joined room`（近 11 通）
11-23：10.4–22.3s；11-24（新 worker 前）：**15.5 / 23.1 / 20.2 / 21.8 / 23.8s**。
拆解最新一通（`AJ_nzKtX93Lj7HZ`）：冷 spawn（prewarm 11.1s + 初始化 18.4s）占 16.8s，
join 本体（RoomIO+ASR ws 1.17s）≈7s。→ **波动主因是热池没热**（`timed out waiting for
idle processes` 11 月 12 次），不是 join 本体。RCA 定的判据「recv→join ~2s 量级」至今
**未有一通取证**。

### 2.6 打断与空回复率（voice.log JSON 去重行）
按日 reply / 其中 interrupted=True（barge-in 设计语义）：11-19 433/363、11-22
482/457、11-23 89/82、11-24 5/3。历史全程 1085 条：853 barge-in、52 真候选空回复
（≈4.8%）、180 非空。机制 A/B 已修已部署，机制 C 未修（owner 待决策）。

### 2.7 握手慢样本（proc-table 观测类）
`hello_sent→hello_ack`：正常 ~1s；慢样本 9s（retry×3）、12.3/12.4s（retry×4-5），
104 有 `no hello within 3.0s treating as v1` 兜底。**握手重试不阻塞 track_subscribed**
（两轮实证 subscribed 先于 hello_ack）——握手慢影响功能协商（发图能力），不阻塞音频。

### 2.8 发牌成功率（api.log access）
`POST /v1/voice/sessions`：11-20 7/7、11-21 39/41、11-22 30/30、11-23 29/30、
11-24 5/5 → 合计 **110/113 ≈ 97.3%**（n 小，仅 HTTP 层；不含端上 connect/ICE 失败）。

### 2.9 服务稳定性
voice.log 中 `event loop blocked` 按日：11-22 134、11-23 113、11-24 36（中位 213ms，
max 31.5s——RCA 判定为放大器非主因）。livekit journal 11-23 以来零重启、`high cpu
load`×10（1vCPU 满载信号）。

---

## 3. 指标提案（逐条商定用）

图例：**〔建议〕**=现有数据可直接支撑的建议门槛；**〔待基线〕**=先测再定；
**〔需拍板〕**=方向/取舍要用户决定。Cn = 口径锚点。

### C1 连接成功率 = 「拨通并进入可用通话」的 attempt 占比
- 起止：端 `connect_start` → `room_connect_success`（+ `local_track_published` 且
  104 侧 `joined room`）；失败类分 `room_connect_timeout/failure`、发牌失败、ICE 不通
  （signaling 通而无媒体）。
- 现状：发牌层 98.2%（n=112）；端到端无数据（release 无日志，debug 手工样本太少）。
- 门槛建议：**〔待基线〕**；先定观测口径，基线≥20 次后设日线（参考发牌层先定
  〔建议〕≥98%，端到端待定）。
- 〔需拍板〕分母：按 attempt（含自动重连）还是按用户拨通话次。

### C2 房间就绪 = 两段分开（用户点通话 → 可以开始说）
- **C2a 信令段**：`connect_start`→`room_connect_success` elapsedMs（已有埋点）。
  现网样本 ~2–3s（proc-table 时间线）。〔建议〕P95 ≤5s。
- **C2b agent 接房段**：104 `received job`→`joined room`。现状 10.4–23.8s，热池命中时
  理论 ~2s 未取证。〔建议〕**P50 ≤3s，P95 ≤8s**；判据含「冷 spawn 占比 ≤20%」子指标
  （`initializing process` 行数/`received job` 行数）。
- 〔需拍板〕验收用蜂窝网还是 Wi-Fi（runbook 已有蜂窝 ICE 冒烟，指标应绑定蜂窝）。

### C3 用户说完 → agent 首音频（核心 SLO，必须拆三截定口径）
- **起止事件定义（提案）**：
  - 起点 T_ASR = 104 `user_transcript final=True`（服务端墙钟；endpointing 在服务端
    volc，`min_delay=0.75 max_delay=4.0` 影响语义——「说完」= endpoint 判定时刻）。
  - 中点 T_TTS = 104 `tts first`（服务端墙钟）；**服务端纯段 C3s = T_TTS − T_ASR**，
    无跨钟问题，先把这个立起来。
  - 终点 T_PLAY = 端 `remote_pcm_first_audio`（设备墙钟 epoch）；
    **端到端 C3e = T_PLAY − T_ASR**，需要端↔服务端偏移 δ。
- **δ 标定方法（提案）**：现成原料——`hello_ack` 的 `Envelope.ts`（服务端墙钟，端已解码
  但从不读）。测量构建里端在收 ack 时记 `t_send_logcat/t_recv_logcat/ts_server`，
  δ = t_recv − ts − 单向延迟（用 ack 往返对称估计，误差 ~RTT/2，蜂窝 ~50–150ms）——
  对秒级 SLO 足够；协议 §5 的「漂移秒级」警告由此从假设变成每通话实测值。
- 现状：C3s ≈ first_ms 分布（§2.3/2.4：p50 14.2s，分段⓪②③已定位）；C3e 仅一次
  偶然样本 ≈ +70ms 下行。
- 〔建议〕**分阶段门槛**：阶段二结束（⓪段 relationship bug + 串行往返 + TTS 保活）
  P50 ≤6s / P95 ≤12s；阶段三结束（模型段+热池稳定）P50 ≤4s / P95 ≤8s。
  现分布支撑「先定 P50≤8s 过线线」——p50 14.2s 说明当前连最宽的可辩护目标都没到。
- 〔需拍板〕endpointing 语义要不要收紧（max_delay 4s 直接进 SLO 起点，是最大单源方差之一）。

### C4 服务端 TTS first_frame_ms（合成为主，独立于 C3）
- 口径：`tts start`→`tts first`（现成）。现状 n=14：min 1.2 / p50 7.2 / max 12.6s；
  热连接下限 1.2–1.5s（11-21 样本）证明地板存在。
- 〔建议〕P50 ≤2s / P95 ≤5s（以热连接下限为锚，靠保活实验把冷样本拉下来）。
  未做保活前任何「合成慢」结论都是连接建立+排队混合——〔需拍板〕是否批准做保活实验
  （改 volc TTS 连接生命周期，属服务端代码改动）。

### C5 可打断性
- 三个子指标：
  - **可打断窗口正确率**：barge 只发生在 t0 后（proc-table 四项之 2/3 已 PASS）——
    回归护栏，任何改动后必跑（沿用 proc-table 判据）。
  - **打断生效时延**：端 `barge_in` → 104 `tts done completed=False` 时刻差（现成两
    端日志）。现状无分布；代码注释给出抖动残余 0.2–0.5s。〔建议〕P90 ≤1.5s、
    听感残余 ≤500ms。
  - **误打断率**：`interrupted=True` / reply 总数现 90%+（barge-in 设计语义正常，但
    需要按「非刻意打断」拆 STT 驱动 vs 用户真实意图——endpointing/backchannel 阈值
    调过三轮仍未收敛）。〔待基线〕。
- 〔需拍板〕barge 指标以客户端决策时刻为准（`speech_silenced` 前）还是发布完成
  （`barge_in` 行）为准——后者含 data channel 发布延迟。

### C6 空回复率
- 口径：`interrupted=False said=''` / reply 总数（三分类 RCA 已定死口径）。
  现状历史 ≈4.8%（52/1085）；机制 A/B 已修已部署（预期归零），机制 C 未修。
- 〔建议〕**≤1%**；机制 C 是否修、用什么形状修〔需拍板〕（机械修=复读风险，tos RCA
  已写明要 owner 决策）。
- 配套看 `cue_shell` 吞正文修复（cue-shell-anchor 分支）落地后 `cues=0/0` 应可区分
  `cues=0/1`——这是 C6 的日志可分性前提。

### C7 通话中断率 / 恢复
- 口径：端 `disconnected reason` / attempt；`reconnecting`→`reconnected` 恢复率与
  时长。**现有 `reconnected elapsedMs` 基线错误（从首次 connect 起算 :248/:320），
  要修一行才能测恢复时长**——这是阶段二的一个前置小改动。
- 已知风险：token TTL 600s、端无刷新 → >10min 通话掉线不可恢复（旧账，未修）。
  〔需拍板〕TTL 调大 vs 补 refresh 端点（涉及安全语义）。
- 〔待基线〕中断率门槛；恢复率〔建议〕≥90%（SDK 自动重连兜底）先立观测。

### C8 ICE 路径分布（UDP 直达 / TURN / TCP fallback）
- 现状：**三端全无数据**——端零采集，livekit-server info 级不打 candidate，无 metrics
  端口。这是提案里唯一「指标定义先于实现」的项。
- 取数方案：端侧 SDK 有 ICE candidate 回调（2.29.0），测量构建采集 + `telemetry.health`
  扩展一字段；服务端可开 `prometheus_port`（配置一行，属变更需授权）。
- 〔需拍板〕先做哪侧；门槛值〔待基线〕——蜂窝直达率决定 TURN/TLS 5349 要不要开。

### C9 CPU/内存与并发容量
- 现状实测（§1.3）：1vCPU/2GB、swap 858MB 在用、job 进程 2×295MB PSS、livekit
  `high cpu load`×10。
- 〔建议〕**把「并发 1 通话稳定、2 通话必须压测」写进 SLO 硬约束**：先压测 2 并发
  （内存 OOM/swap 抖动、CPU 饱和对 first_ms 的污染）再谈任何延迟门槛的全网适用性；
  `max_clients` 显式化（config.yaml 现在完全没 limits）。
- 〔需拍板〕升配机器 vs 迁 voice 到独立机 vs 接受低并发——容量决策先于拓扑优化，
  否则所有 P95 数字都在饱和机器上量出来没有意义。

### C10 协议健康（护栏，不是性能线）
- 现成 `telemetry.health`（30s，topic `cmp.tel`，LOSSY，payload 构建 :553-572）：
  gap_count、dropped_*、degrade_level、late_ratio。沿用为回归护栏；
  协议 v2 §14.3 已设计的 `cue_intent_to_apply_ms` p50/p90 **完全没发**
  （`lateMs` 在 `Scheduler.kt:107-110` 算完即被塞进 degrade 窗口 :1308 丢弃），
  而 §17 P3 验收线早已定 p90 ≤250ms——补上数据面〔建议，属埋点小改〕。
  另 §18.1 的 publishData 到达延迟分布「P1 前必须压测」也仍无数据面。

---

## 4. 需用户拍板的决策清单（汇总）

| # | 决策 | 影响 |
|---|---|---|
| D1 | **release 埋点策略**：MouthLevel 日志 debug-only → 生产 SLO 拿不到数。选项：a) 关键事件在 release 降级常开（一行改 gate）；b) 扩 `telemetry.health` 承载计数/直方图；c) 只用 104 服务端日志 + 装机测量窗口 | 全部指标的数据面 |
| D2 | C3 起点 endpointing 语义（max_delay 4s 收不收） | C3 数值口径 |
| D3 | token TTL 600s / 无刷新：调大 vs 补 refresh | C7、>10min 通话 |
| D4 | 空回复机制 C（MERGED 游标）修不修、什么形状 | C6 |
| D5 | TTS 保活实验授权（volc 连接生命周期改动） | C4 |
| D6 | 容量路线：升配 / 拆机 / 接受并发 1 | C9，所有 P95 的可信度 |
| D7 | ICE 分布取数在哪侧做（端 vs 服务端 prometheus_port） | C8 |
| D8 | SLO 验收网络口径（蜂窝为主，Wi-Fi 仅对照？） | 全部 |

---

## 5. 按证据排序的优化路线草案（每项含前置/验证/回滚）

**顺序依据**：① 没有基线数字一切门槛都是猜；② cue 空壳修复已诊断完、RED 在手、
改动面最小；③ prewarm+20s 预算已部署，**先取证再决定是否调热池参数**（现在改参数
会把已部署未验证的东西搅浑）；④ ⓪段 relationship TypeError 是本次新钉死的廉价修复；
⑤ TTS 保活和拓扑放最后因为依赖前面数字干净。

### 阶段 A · 测量基线（先做，纯观测 + 一处测量构建）
- 动作：≥3 次蜂窝通话 attempt/job 配对（proc-table 同款 tee）+ C3 δ 标定
  （hello_ack ts 采样）+ 修 `reconnected` elapsedMs 基线（一行，测量用）+
  一个收集脚本（logcat tee + voice.log 对齐，替代手工）。
- 前置：协调会话装机窗口（装机单写者纪律）；蜂窝 ICE 冒烟先行（runbook）；
  D1 方向至少定到「继续 debug 包测」级别。
- 验证：产出 C1/C2/C3s/C7 首轮 P50/P95 数表；δ 逐通话记录。
- 回滚：不适用（观测；一行重连基线改动在测量构建内，不合主线或合前审）。

### 阶段 B · F16 prewarm + 20s 预算取证（已部署，补证据关账）
- 动作：阶段 A 的 3 通同时覆盖取证：`received job→joined room`、`initializing
  process` 冷占比、`timed out waiting for idle processes` 增量、prewarm cost_ms
  （现 11–14.6s）对 20s 预算的余量。
- 前置：无（已部署）；**期间禁止再 publish/restart voice**，否则样本谱系断。
- 验证：判据 = 热池命中通 recv→join ~2s 量级、无 10s+ blocked；若冷占比高，
  下一步调 idle 池大小/预热时序（另批）。
- 回滚：`livekit-companion.sh rollback` / publish 上一 .deploy-version（现网
  dced3cab → 回退目标按 .deploy-manifest）。

### 阶段 C · cue 空壳修复合并（cue-shell-anchor 分支收口）
- 前置：保住 `/Users/gqli/work/cue-shell-20261124` 的未提交 RED 测试；重建 venv
  （`uv venv + uv pip install -e ".[livekit,dev]"`）观察 RED；按 RCA 设计落 GREEN。
- 验证：全量测试绿 → ff main → 随下批 publish（不单独重启窗口，与 B 取证错开）；
  现网 grep `cues=0/0` vs `cues=0/1` 可分性 + 真机 cue 送达（端 `cue applied`）。
- 回滚：publish.sh 回退 + process.sh restart。
- 顺带记账：同守卫另一边界（闭合引号+句末标点同 delta 时标点丢失）——独立小修。

### 阶段 D · ⓪段打点（relationship TypeError + 串行往返）
- 前置：阶段 A 的探针数据已在（86d63a7 行已部署）；TypeError 修复是确定性代码 bug，
  先 RED 复现（现网 `tuple indices … not str` 形状）再修。
- 验证：`relationship read failed` 归零；`voice turn→recall` 中位从 4.33s 往下掉；
  `submit_ms/upstream_ms` 对比（6.2s 样本的串行往返是否收敛）。
- 回滚：publish 回退。
- 若 gather 尾巴另有池排队成分：调 voice 池（pool_size 2+overflow 2）另批评估。

### 阶段 E · TTS 首帧保活实验（D5 授权后）
- 前置：B/D 完成（否则 7.2s 中位里混着别的）；volc NewConsoleTTS 预热/保活实现
  + 一窗对照（保活开/关各若干通）。
- 验证：`tts first first_frame_ms` P50 向 1.2–1.5s 热下限收敛。
- 回滚：配置开关 / publish 回退。

### 阶段 F · 容量与拓扑项（D6/D7 后）
- 候选（不在本阶段展开）：livekit `prometheus_port` 开启、room defaults 显式化
  （empty_timeout/max_session_duration 现在全是上游默认）、`max_clients` 上线、
  TURN/TLS 5349、蜂窝直达率不达预期时的 ICE-TCP 路径加固、机器升配/拆分。
- 全部需要变更授权 + 蜂窝 ICE 冒烟前置（生产切换纪律不变）。

---

## 6. 本阶段明确不做

- 不改任何代码、不 push、不部署、不重启、不动防火墙/拓扑、不写生产库；
  104 上只 grep/cat（本文所有数字均可按 §2 命令口径复核）。
- 协议四项不重开（已 PASS）；空回复三分类 RCA 不重开（只引用）。
- 真实通话数据不出本机族（无第三方上传）。
