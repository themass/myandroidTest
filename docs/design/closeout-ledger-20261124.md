# 统一收口账本（2026-11-24）

Owner：收口 worker（session `local_0c440dde-5831-45ce-9a97-c5d3f275b91b`）。
把原先散落约 30 个 worker 会话的残余事项逐条闭环；每条按
`docs/design/worker-closeout-template-20261124.md` 记 `PASS`/`FAIL`/`BLOCKED` + 证据。

状态口径（全程统一，不得混用）：

| 层级 | 含义 |
| --- | --- |
| 已编码 | 改动在某个分支上，测试过 |
| 已合线 | 已进入该仓库的集成线（Android = `weifu-refactor`，服务端 = `main`）|
| 已部署 | 已在目标机器上跑起来（104 / 生产 / testserverV）|
| 真机验收 | 在 V2279A 上人工确认过行为 |
| 阻塞 | 依赖设备窗口、产品决策、凭证或外部服务 |

纪律边界：禁止 docker 任何环境；本 worker 只 build 不装机、不 bump versionCode、
不复用旧 apk；生产库写入与 testserverV push 只能用户本人决定；不改已关闭的协议
四项结论（`p05p2-proc-table-20261123.md`）；不做 LiveKit 性能指标（另有
livekit-performance 会话）、不做数字人/摄像头/特效视觉线（另有 her 视觉 worker）。

---

### C1 cue 空壳被吞（`cues=0/0`）修复

- Owner / session：收口 worker
- 目标分支 / commit：agent-docs `fix/cue-shell-anchor-20261124` → **`ee2e7969`**（已 ff 到 `main`）
- 状态：**PASS（代码 + 单测）／已合线，未部署，未真机验收**

**目标**
- 用户可观察条件：模型出了 cue JSON 的通话回合，服务端 `voice reply … cues=` 不再
  恒为 `0/0`；cue 能挂到它自己那句话的段上并发出。

**范围**
- 包含：`llm_node` 对 `{"say":"","cues":[…]}` 空正文壳的计数与发布；先行 RED 测试。
- 不包含：cue 触发端侧表现（另属 her 视觉线）、prompt/模型改动、say 闭合引号丢标点
  的边界（见 C1-附带）。

**交付**
- `myproject/companion-agent/server/src/companion/api/livekit/agent.py`（+22/-2）：
  1. `cue_intents` 计数拆出 `if output.text:` —— 分母不再挑正文；
  2. 空壳的 `intents` 补挂到最近一次 `speech.say` 声明的段（壳的 say 文本就是那段，
     `44d2d27` 不变量 1「seg 声明先于 cue」仍成立）；
  3. 无可挂段时只计数：`cues=0/1`（锚点没声明）与 `cues=0/0`（模型没出）在日志可分。
- `tests/test_agent_speech_wiring.py`（+79）：2 条测试，含生产 session-33 / turn
  `01M86MPWW7FD0DS4WH2979MVWM` 的 checkpoint 原文按 7 字符粒度喂 `FakeBrain`。
- 根因与交接依据：`cue-shell-rca-handover-20261124.md`（共享笔记）。

**验证**
- 先 RED（本次实际观察，实现落码前）：
  `pytest tests/test_agent_speech_wiring.py -k cue_shell -q` → `2 failed, 15 deselected`；
  失败断言正是 `cues=0/0`（两条都停在计数被吞的点上）。
- 后 GREEN：`tests/test_agent_speech_wiring.py` → **17 passed**。
- 全量：`pytest -q` → **1396 passed, 22 failed, 90 skipped**。
- 22 条失败为**既有环境问题**，非本次改动引入：将 `agent.py` 还原到基线后重跑
  `test_api_routes.py` + `test_prewarm.py`，仍是同样的 `22 failed`；报错均为
  `RuntimeError: COMPANION_DATABASE_URL (or COMPANION_MYSQL_URL) and COMPANION_REDIS_URL are required`
  （缺 mysql/redis 环境变量）。
- 合线前把分支 `git pull --rebase origin main`（main 已前进 7 个提交，含 voice P3
  首句流式），rebase 无冲突，重跑 17/17 通过后再 ff。
- 未执行的验证及原因：未部署到 104/生产，因此没有生产 `voice.log` 的 `cues=x/y` 复验；
  未做真机通话验收（只有协调会话可给 V2279A 装机，见 C6）。

**证据**
- commit `ee2e7969`（agent-docs `main`，push 后核对 `main..HEAD` 与 `HEAD..main` 均为 0）。
- 测试输出如上（本次实际执行，非引用旧记录）。
- 局限：单测在 fake/stub 的 `speech`/`cues` 替身上验证 llm_node 层计数与发布；端到端
  （真实 `SpeechRegistry.targetOf` + 端侧渲染）未在本次复跑，端侧兼容性由交接笔记中
  已核的 `Decision.Now`+lateMs 路径支撑。

**阻塞与目标内遗留**
- 部署与真机复验：依赖协调人给的部署/装机窗口（见 C2、C6）；解除条件 = 用户开窗口。

**独立后续（不改本任务状态）**
- C1-附带（归 F16 owner）：say 闭合引号与句末标点落在同一个 delta 时，标点从未喂进
  断句器（`_emit_say_stream_delta` 把含闭合引号的 probe 尾部扣住，flush 释放时标点已丢）；
  Spy 复现 `splitter.flush() -> ['我们在多个县']`（丢「。」）。与本次 cue 吞无关，是同
  一守卫的另一条边界。

---
