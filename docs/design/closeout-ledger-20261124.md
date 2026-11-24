# 统一收口账本（2026-11-24）

Owner：收口 worker（session `local_0c440dde-5831-45ce-9a97-c5d3f275b91b`）。
把原先散落约 30 个 worker 会话的残余事项逐条闭环；每条按
`docs/design/worker-closeout-template-20261124.md` 记 `PASS`/`FAIL`/`BLOCKED` + 证据。

状态口径（全程统一，不得混用）：

| 层级 | 含义 |
| --- | --- |
| 已编码 | 改动在某个分支上，测试过 |
| 已合线 | 已进入该仓库的集成线（Android = `weifu-refactor`，服务端 agent-docs = `main`，testserverV = `testjava_springboot`）|
| 已部署 | 已在目标机器上跑起来（104 / 生产 / testserverV fleet）|
| 真机验收 | 在 V2279A（`10CE7P1G56004B7`）上人工确认过行为 |
| 阻塞 | 依赖设备窗口、产品决策、凭证或外部服务 |

纪律边界：禁止 docker 任何环境；本 worker 只 build 不装机、不 bump versionCode、
不复用旧 apk；生产库写入与 testserverV/104 部署重启只能用户本人决定；不改已关闭的
协议四项结论（`p05p2-proc-table-20261123.md`）；不做 LiveKit 性能指标（另有
livekit-performance 会话）、不做数字人/摄像头/特效视觉线（另有 her 视觉 worker）。

## 总览

| # | 事项 | 状态 | 层级到哪 |
| --- | --- | --- | --- |
| C1 | cue 空壳被吞（`cues=0/0`）修复 | **PASS** | 已合线（agent-docs main `ee2e7969`）；未部署、未真机 |
| C2 | F16 prewarm + 20s init budget 合并验证 | **BLOCKED**（部署窗口） | 已合线（两项都在 main，无需再合并）；收益未验证 |
| C3 | `claude/dazzling-darwin-6c5ae3` P4/P5 决策 | **MERGE**（已决策并合线） | 已合线 `8685251b`；真机腿未跑 |
| C4 | sound-subtitle-server 收口 | **PASS（代码）/BLOCKED（部署+真机）** | 已合线并已 push（`testjava_springboot`）|
| C5 | testserverV 拍照解题 maxTokens 链 | **PASS** | 已合线且**已在 origin**（前提与 brief 不同，见内文）|
| C6 | 真机验收清单交协调人 | **BLOCKED**（等装机窗口） | 清单已成文，零项验收 |
| C7 | GitHub 图片调研文档落主线 | **PASS** | 已合线 |
| C8 | 三个待定产品决策汇总 | **BLOCKED**（等用户拍板） | 汇总成文 |

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
- 根因与交接依据：`cue-shell-rca-handover-20261124.md`（共享笔记，elastic-mendeleev 线）。
  未提交的 RED 测试确实在 `fix/cue-shell-anchor-20261124` 工作区里找到（+79 行），
  本次完整接手。

**验证**
- 先 RED（本次实际观察，实现落码前）：
  `pytest tests/test_agent_speech_wiring.py -k cue_shell -q` → `2 failed, 15 deselected`；
  失败断言正是 `cues=0/0`（两条都停在计数被吞的点上）。
- 后 GREEN：`tests/test_agent_speech_wiring.py` → **17 passed**。
- 合线前把分支 `git pull --rebase origin main`（main 已前进 7 个提交，含 voice P1/P2/P3
  状态事件与首句流式），rebase 无冲突，重跑 17/17 通过后再 ff。
- 全量（rebase 后）：`pytest -q` → **1396 passed, 22 failed, 90 skipped**。
  22 条失败非本次改动引入，已逐条定性（本次实测，不引用旧记录）：
  - 21 条 `test_api_routes.py` + `test_prewarm.py` 之外的一批属**环境问题**：将
    `agent.py` 还原到基线后重跑仍同样失败，报错
    `RuntimeError: COMPANION_DATABASE_URL (or COMPANION_MYSQL_URL) and COMPANION_REDIS_URL are required`。
  - `tests/test_prewarm.py::test_prewarm_imports_them_without_calling_anything` 是**既有
    RED 测试**（不是环境问题）：`prewarm()` 现会 `warm_system_catalog()` 懒 import，
    spy 断言 `calls == ["json","base64"]` 多出 120 项（首项
    `companion.bootstrap.catalog_cache`）。单独复跑确认。归 prewarm/catalog 线，
    与 cue 修复无关。
- 未执行的验证及原因：未部署到 104/生产，因此没有生产 `voice.log` 的 `cues=x/y` 复验；
  未做真机通话验收（只有协调会话可给 V2279A 装机，见 C6）。

**证据**
- commit `ee2e7969`（agent-docs `main`，push 后核对 `main..HEAD` 与 `HEAD..main` 均为 0）。
- 测试输出如上（本次实际执行）。
- 局限：单测在 `speech`/`cues` 替身上验证 llm_node 层计数与发布；端到端（真实
  `SpeechRegistry.targetOf` + 端侧渲染）未在本次复跑，端侧兼容性由交接笔记中已核的
  `Decision.Now` + lateMs 路径支撑。

**阻塞与目标内遗留**
- 部署与真机复验：依赖协调人给的部署/装机窗口（与 C2 同一窗口最省）；解除条件 = 用户开窗口。

**独立后续（不改本任务状态）**
- C1-附带（归 F16 owner，本次未修）：say 闭合引号与句末标点落在同一个 delta 时，标点
  从未喂进断句器（`_emit_say_stream_delta` 把含闭合引号的 probe 尾部扣住，flush 释放时
  标点已丢）；Spy 复现 `splitter.flush() -> ['我们在多个县']`（丢「。」）。与本次 cue 吞
  无关，是同一守卫的另一条边界。

---

### C2 F16 prewarm（`2a098aa`）+ 20s init budget：合并与收益验证

- Owner / session：收口 worker（代码线）；收益验证 owner 仍是 F16 侧线 `local_e2a702b2-de82-4ba3-98f6-0de8b11707b9`
- 目标分支 / commit：agent-docs `main`（`2a098aa4` prewarm 清单补火山插件本体；
  `316f53f8` 钉住 20s 预算挂进真实 WorkerOptions）
- 状态：**BLOCKED（真实收益验证）／已合线**

**先纠正 brief 的前提（本次取证）**
- `2a098aa4`（prewarm）**已在 `origin/main`**：`git merge-base --is-ancestor` 返回真。
- 20s init budget 也**已在 `origin/main`**：`316f53f8` 同样是 main 的祖先，且实现真的
  接上了 —— `capacity.py:26 INITIALIZE_PROCESS_TIMEOUT_S = 20.0`，
  `capacity.py:52` 无条件带 `initialize_process_timeout`，并有
  `test_voice_capacity.py` 5 处断言（含 `INITIALIZE_PROCESS_TIMEOUT_S > default`）钉住。
- 候选分支 `claude/f16-voice-fragments` **已无未合内容**：
  `git log origin/main..claude/f16-voice-fragments` 为空，其 tip 是
  `6788ac20`「Merge origin/main into claude/f16-voice-fragments (cue observability +
  20s init budget)」，落后 main 23 个提交。
- 结论：**「单独部署 prewarm 会抬高 spawn 超时率」这个风险在代码层已经消掉了** ——
  因为 prewarm 与 20s 预算现在同在 main tip，任何一次正常的 `publish main tip` 都
  同时带上两者，不存在「只部署 prewarm」的路径。剩下的只是部署与取数。

**目标**
- 用户可观察条件：prewarm 修好以后，真实通话的 ③ TTS 首帧与 spawn 超时率确实改善，
  且 spawn 超时没有因为 prewarm 变长而恶化。

**范围**
- 包含：合并状态核对（上面已做）；前后对比取数设计；阻塞与路线列明。
- 明确不包含：本 worker 不执行 104 `publish.sh` / `process.sh restart voice`（需用户
  当面授权）、不拨测、不装机。

**交付（前后对比设计，可直接照跑）**

同场景 = 同一台 V2279A、同一账号/角色、同一 104 worker 谱系，部署前后各 ≥4 通（对齐
stage-5 的 n=4 口径）。取数：

| 指标 | 部署前基线（已实测，见证据） | 部署后通过标准 |
| --- | --- | --- |
| ① 每 job pid 首条 `first_frame_ms` | 生产 ③ 冷轮 n=12 **P50=7515 / P95=12601 ms**；同构造隔离 bench（`server/tools/qa_tts_firstframe.py`）fresh n=10 P50=766 / P95=978 ms | 冷轮向 bench 量级（~1s）靠拢；至少 P50 显著下降 |
| ② 新 worker 段 `event loop blocked … importing livekit.plugins.volcengine*` | 有（这就是根因签名） | **0 笔** |
| ③ `prewarm done modules=` | `modules=24`（stage-5 已发布态）| **`modules=27`**（`2a098aa4` 把 import 清单 24→27） |
| ④ spawn 回归指标 `error initializing process`（`proc.initialize` TimeoutError，失败子 pid 无 `prewarm done` 行） | 生产 prewarm cost_ms 已 5.3–8.6s（nproc=1），默认 `initialize_process_timeout` 10s → 该错误就是被杀重生 | **不得上升**；20s 预算生效后期望 0 笔 |
| ⑤ `received job → joined room` | stage-5（worker 836082）四通 **14.0 / 14.1 / 14.5 / 16.4 s** | 判据原为 ~2s 量级 —— **预期仍达不到**，见下 |

**关键提醒（写下来免得下一轮误判为 prewarm 没修好）**：stage-5 四通证明 prewarm 只盖
import，**每房间同步 `build_platform`（含 283 角色 yaml 扫盘）仍在事件循环里跑**，每间
仍有 ~9.9–11.7s blocked（样本帧在 `pathlib.read_text ↔ build_platform.load_system_catalog`）。
所以 C2 的 prewarm+20s 是**必要但不充分**：指标 ①②③④ 归本任务，指标 ⑤ 归 stage-6
（进程级 catalog/platform 单例缓存，或把 `build_platform` 挪出事件循环）——不得用 ⑤
未达标去否定 ①②③④，也不得反过来用 ①②③④ 声称首帧问题关闭。

**验证（本次实际执行的）**
- 上面「先纠正 brief 的前提」四条全部是本次命令实测（`merge-base --is-ancestor`、
  `git log origin/main..branch`、`grep` 实读 `capacity.py`/`test_voice_capacity.py`）。
- 未执行的验证及原因：指标 ①②③④⑤ 全部需要 104 发布 + worker 重启 + 真机通话，
  三项都不在本 worker 权限内。

**证据**
- `2a098aa4`、`316f53f8` ∈ `origin/main`；`capacity.py:26`、`capacity.py:52`。
- 基线数据与部署阻塞取证：共享笔记 `f16-measured.md`（stage-5 全段）、
  `f16-server-recommendations.md`；记忆口径 `f16-b1-prewarm-verification-blocked`。
- 局限：基线是历史实测，本 worker 未重测生产；引用时按「历史基线」用，不作为本次新证。

**阻塞与目标内遗留**
- **104 发布克隆脏**：`/home/web/work/agent-docs` 有其他会话未提交 WIP（11 文件，+687
  行），`publish.sh` 拒脏。三条路线待用户定：
  a) 等该会话提交后正常 publish main tip（会连带上线其他会话已合入 main 的服务端改动）；
  b) 新建干净克隆 publish main tip（stage-5 用过这招：`/home/web/work/agent-docs-stage5`）；
  c) 只把 `prewarm.py` 单拷进 webroot（偏离发布工具、manifest 与代码不符，不推荐）。
- 发布后需重启 voice worker 才生效（重启需用户授权）。
- 真机通话窗口归 C6（单写者规则）。
- 下一动作：把「部署 + 重启 + 一通拨测」排进同一个用户窗口，一次跑完 ①②③④。

**独立后续**
- stage-6：进程级 catalog/platform 单例缓存（`build_platform` 出事件循环）—— 这才是
  `received job → joined room` 到 ~2s 的那一步。
- 既有 RED：`test_prewarm_imports_them_without_calling_anything`（见 C1 验证段）归
  prewarm/catalog 线。

---

### C3 `claude/dazzling-darwin-6c5ae3`（P4/P5 表情口型）合并决策

- Owner / session：收口 worker（决策 + 合线）；diff 审查由独立子代理做（避免我自己写自己审）
- 目标分支 / commit：并入 `weifu-refactor` → **`8685251b`**（已 push，0/0）
- 状态：**PASS（决策与合线）／真机腿未跑，转 C6**

**目标**
- 给这条分支一个「合入或废弃」决策 + 理由，不悬空。

**范围**
- 包含：核 diff、查重（主线是否已有等价物）、试合并、决策与执行。
- 明确不包含：不实现 P4/P5 生产移植（属 her 视觉线）、不做真机验收。

**交付**
- 决策：**MERGE**（普通 merge commit，不需 rebase）。
- 内容：3 个提交（`59bda772` P5 元音加权口型驱动 + node 单测 + 程序合成语音样本、
  `afeadf79` P4 状态→表情映射表、`c898c339` P4 语法表 + P5 真实音频接线 tip），
  9 个文件 +704/−89，**全部在 `demos/avatar-lab` + `scripts/dh-lab/avatar-comparison`
  沙箱 + 一条 `.claude/launch.json` 8792 端口项**。
- 真机遗留已写进 merge commit message 并入 C6。

**验证**
- 试合并：`git merge-tree --write-tree weifu-refactor claude/dazzling-darwin-6c5ae3`
  → **exit 0，零冲突**（tree `48a027e5…`）。分叉基是当天主线 `e44195aa`（19:07），
  「落后 32 提交」是同日无关改动，不是陈旧。
- 主线无等价物：`git grep` 对 `state-grammar` / `lipsync-driver` / `speech-sample` /
  `stateGrammar` 在 `weifu-refactor` **零命中**；自 merge-base 起主线对这 9 个路径
  **零提交**（不会撞）。
- 测试证据：`node lipsync-driver.test.mjs` 12 组断言全绿，且协调线（focused-mclean）
  已独立复跑过一次。工作区干净（无未提交内容）。
- 未执行的验证及原因：**真机腿一条没跑**（Hiyori 五状态目视 + 口型帧率）—— 单写者
  规则下本 worker 不装机；驱动只被程序合成伪语音喂过，真实 TTS 下行 PCM16k 未测。

**证据**
- `8685251b`（`weifu-refactor`），push 后 `HEAD..origin/weifu-refactor` 与
  `origin/weifu-refactor..HEAD` 均 0。
- 边界核对：9 个文件全部不碰 `dh-engines` / `feature-companion` / 生产 UI，与视觉
  worker 领地零重叠（这决定了它可安全并线）。

**阻塞与目标内遗留**
- 真机五状态 + 口型帧率验收 → 已列入 C6 清单 #7。
- `launch.json` 新增 8792 假设 `focused-mclean-1bfa86` 的 8791 占用不变；换机需自查端口。

**独立后续（可救的东西，即使当时不并也要留）**
- `beforeModelUpdate` 钩子发现：帧末 `getParameterValueById` 读到的是恢复后的基值，
  证据钩子必须在 motion evaluation 与 physics 之间采样 —— 对任何未来 Live2D web 侧
  验证都是承重的。
- Pixi ticker / RAF 节流绕过。两条都在 `p45-expression-lipsync-20261124.md`。
- 注意：共享笔记里的 `lip-sync-evidence/` 属于 2026-11-22 mock-harness 生产 RMS 线
  （ADY-AL00，`c6cfc900`/`1eaffc32`），**不是**这条分支的证据，别混。

---

### C4 sound-subtitle-server 收口（testserverV `feature/sound-subtitle`）

- Owner / session：收口 worker（状态核对）；代码 owner 是 `local_03a70223-5ee1-461c-949a-6ca1d37d5a82`
- 目标分支 / commit：`feature/sound-subtitle` tip **`c96bb172`**，已 ff 进集成线
  **`testjava_springboot`**
- 状态：**PASS（代码已合线并已 push）／BLOCKED（fleet 部署 + 真机）**

**先纠正 brief 的前提（重要）**
- brief 说「服务端集成线是 main」。那条规则属于 **agent-docs 仓库**。testserverV 的
  集成线是 **`testjava_springboot`**：`origin/master` 比它落后 **578 个提交**，仓库
  HEAD 指向 `master` 只是历史遗留。**没有**把 weifu-refactor 或 main 的规矩搬过来。
- `feature/sound-subtitle` **已经在集成线里**：
  `git rev-list --count origin/testjava_springboot..feature/sound-subtitle` = **0**
  （落后 2，即集成线又前进了 2 笔）。`c96bb172` 是 `origin/testjava_springboot` 的祖先。
- 本地 `testjava_springboot` 与 `origin/testjava_springboot` **0/0**（同一个 SHA
  `9be66628…`）。所以「先核状态再决定合 main」的答案是：**已合并、已 push，无事可做**，
  不需要我再动这条仓库的分支。

**交付（该线内容，来自其交接台账并本次核对存在性）**
- `fc901dfe` Phase 0/1/2：items id 透出、subtitle 读端点 ×2、`generate.json`、Deepgram
  url 模式、`SrtFromSegments`、backfill job、DDL `2026112400`、测试 ×3
- `73dbe5a8` fix: `SoundAudioUrl`「谁绝对用谁」（url 列是网页路径的线上实证）
- `a3021f8f` test(student): `StudentSqlGuardTest` 假阳性修复
- `eb31168a` test(poetry): 三处测试腐化修复 + `2026112401_fk_audit.sql`
- `c96bb172` merge sync（只带 deploy/docs 两笔）

**验证（本次实测）**
- 状态核对命令与结果如上（0 ahead / 2 behind / 本地=origin）。
- 全 reactor 复跑（本次，`mvn -B test -DskipTests=false -pl vvv-link -am`）：
  **BUILD SUCCESS**，六模块全绿：vvv-poetry **114/0/0**、vvv-student **40/0/0**、
  vvv-game **376/0/0**、vvv-roles **12/0/0**、vvv-link **154/0/0**。
  其中 vvv-student 40/0/0 说明 `a3021f8f` 的 SqlGuard 假阳性修复生效（此前是 6 中 1 失败）。
- 未执行的验证及原因：fleet 部署与线上端点复验需要用户（见下）。

**证据**
- 交接台账：`lyrics-verification-ledger-20261124.md`（共享笔记）。
- 本次 maven 日志：`/tmp/tsv-link-recheck-20261124.log`。
- 局限：maven 默认 `skipTests=true`（根 pom），必须显式 `-DskipTests=false`，否则
  「绿」是假的；历史上有会话读到 stale surefire XML 报了假的 40/0（该线已自行更正）。

**阻塞与目标内遗留（全在用户手上）**
| # | 项 | 谁执行 | 通过标准 | 状态 |
| --- | --- | --- | --- | --- |
| V1 | 完成滚动部署（从 `c96bb172` 全 fleet 发布） | 用户 | 任取 ≥10 次 `items.json` 全部带 `id`；无 10000 | pending（约 10/12 老实例）|
| V2 | 线上外键盘点 | 用户（生产库只读） | 手跑 `vvv-link/src/main/resources/sql/2026112401_fk_audit.sql` ①② 零行 | ready |
| V3 | 灌测试 SRT（2148） | 用户（生产库写入） | `subtitle/items.json?soundItemId=2148` 带 id 行；`item.json` 回 4 cue | ready，依赖 V1 |
| V4 | 端上三级阶梯（SERVER>REALTIME>NONE）真机看到字幕 | 协调人装机 + 用户 | 见 C6 #5 | BLOCKED |

---

### C5 testserverV 拍照解题 maxTokens 链

- Owner / session：收口 worker（本地复核）
- 目标分支 / commit：`f0de0021` → `a48e2425` → `cbc260d9`（`vvv-link` photosolve）
- 状态：**PASS（复核通过）／已合线，且已在 origin（与 brief 假设不同）**

**先纠正 brief 的前提**
- brief 说这条链「push 等用户本人，只做本地复核并列明待 push」。实测：**三个提交都已在
  `origin/testjava_springboot`**（`git merge-base --is-ancestor cbc260d9
  origin/testjava_springboot` = 真；`f0de0021`/`a48e2425` 同在其历史中）。它们随
  sound-subtitle 线的 ff 一起上去了。**不存在待 push 的本地链**，我这边没有 push 动作。
- 该仓库唯一未 push 的提交是 `53ee1a8b`（`chore/externalize-secrets`，2026-11-02，
  `Secrets.java` 找 secrets 文件顺序：`-Dsecrets.file` → cwd → jar 所在目录），与
  photosolve 无关，也不在集成线上 —— 列在这里只为「未 push」这一事实准确。

**交付**
- `f0de0021` 截断的 JSON 不再报「不是题目」，`photosolve.maxTokens` 可配
- `a48e2425` 从截断 JSON 抢救已完整的题目结构，不再整坨当文本
- `cbc260d9` `PhotoSolveConfig` 类注释补 `photosolve.maxTokens` 配置行
- 改动面：`DoubaoPhotoSolveProvider.java`、`PhotoSolveConfig.java`、
  `DoubaoPhotoSolveParseTest.java`、`secrets.properties.example`

**验证（本次实测，`-DskipTests=false`）**
- photosolve 测试类合计 **47 tests / 0 failures**（本次新跑）：
  `DoubaoPhotoSolveParseTest` 11、`PhotoSolveJsonSalvageTest` 15、
  `PhotoSolveJsonRepairTest` 15、`PhotoSolveHealthContractTest` 6。
- 所在模块 vvv-link 全量 **154/0/0**；六模块 BUILD SUCCESS（见 C4）。
- 与 brief 里「41 测 0 失败」的口径差异说明：本次按测试类实际计数是 **47**，41 应是
  当时的旧快照（salvage/repair 两类后来才加全）。以本次 47/0 为准。
- 未执行的验证及原因：未对线上 `photo.solve` 端点发过真实图片请求（生产写/额度消耗需
  用户决定）；PS4「纸质题」真机项在 C6 #2。

**证据**
- `/tmp/tsv-link-recheck-20261124.log`（本次），`BUILD SUCCESS` + 上述计数。
- 真机历史：PS1/PS3/PS5 PASS、PS2 部分（见 `device-v2279a-window-20261123.md`）。

**阻塞与目标内遗留**
- 无 push 遗留。线上端点行为与「翻拍 vs 纸质题」识别质量未闭环（见 C8 决策 1）。

---

### C6 真机验收清单（交协调人装机验收，本 worker 不装机）

- Owner / session：收口 worker 出清单；装机与验收 = 协调会话 `local_bbd5e3b7-7658-466f-87cd-559e087a6dff` + 用户
- 状态：**BLOCKED（零项验收）**

**为什么 BLOCKED（不是 FAIL）**
- 单写者规则：只有协调会话能给 V2279A `10CE7P1G56004B7` 装机；本 worker 只 build。
- vivo 安装必须**用户手指点确认**（`PackageInterceptActivity`，历史规矩：不代点）。
- 多数项需要真人入镜 / 真实人脸 / 真实通话，adb 摆不出来。

**装机前置（协调照做，禁止复用旧产物）**
```bash
# 在主线 checkout，从当刻 tip 现场 assemble
./gradlew --offline :app:assembleChatDebug
adb -s 10CE7P1G56004B7 install -r -d \
  app/build/outputs/apk/chat/debug/app-chat-debug.apk
adb -s 10CE7P1G56004B7 shell dumpsys package com.openapi.ks.chat \
  | grep -E 'versionCode|lastUpdateTime'
```
- 本 worker 合线后 `weifu-refactor` tip = **`8685251b`**；装机请以协调当刻 tip 为准。
- 核对 HUD/包版本先于任何功能判定（历史上「旧包」误判过：同版本号可能只是没选引擎）。

**清单**

| # | 项 | 判据 | 检查表出处 | 现在到哪 |
| --- | --- | --- | --- | --- |
| 1 | PhotoEditor S2（含 R1–R5 托盘长按再编辑） | D/T/M/A/S + R1–R5 + X3–X4 + Q1 全过 | `docs/qa/photo-edit-device-checklist.md`；结果表 `docs/qa/photo-edit-device-result-20261123.md` **全保持 `—`** | 未验收（happy-fermi 明确不声称通过）|
| 2 | PS4 拍照纸质题 | 纸面题走拍照→提交→出结果，历史有记录 | 历史 PS1/PS3/PS5 PASS、PS2 部分（`device-v2279a-window-20261123.md`）；服务端识别质量待 C8#1 | 部分历史 PASS，未在本 tip 复跑 |
| 3a | camera lab：手指关键点显示 | 开线框见手橙线 + HUD `手 N · 手势`（张/拳/捏） | `docs/qa/camera-lab-device-checklist-20261123.md` | 未验（P0-1c/1d 当时因包不对未验）|
| 3b | camera lab：贴纸跟随手感 | 开贴纸 → HUD `贴纸 OK` + 头顶 Lottie 随倾角跟脸 | 同上 + `camera-fx-evidence/` | PARTIAL（无脸轮已证「无脸不画」）；有脸截图缺 |
| 3c | camera lab：丢失恢复 | 遮脸 → 脸回 0、贴纸消失；移开恢复 | 同上 | 缺图 |
| 4 | 通话字幕 | 通话中字幕可见、与实际语音对齐 | vigorous-goldwasser 线 UI 修正已合 `032f9a06` | 未在本 tip 复跑 |
| 5 | chat / media 交互 | 发图/取图/图片消息往返、声音馆字幕三级阶梯（SERVER 档） | `image-messages-p1-accept.md`；`sound-lyrics-contract-20261124.md`；依赖 C4 V1/V3 | BLOCKED（等 fleet 部署）|
| 6 | 数字人资源负载 | 选定引擎下 GPU/内存与帧率可接受，无 native crash | `fxlab-device-accept-status.md`（BLOCKED 待用户手动）；历史 crash 取证 `dh-demo2b-native-crash.txt` | 未验 |
| 7 | avatar-lab P4/P5（C3 并入的） | Hiyori 五状态目视切换 + interrupted 1.4s 回落 listening + 口型帧率 | `p45-expression-lipsync-20261124.md` | 仅浏览器验过，真机零 |

**证据**
- 各检查表在主线（已核对存在）；本清单本身即交付物（本文件 C6 段）。
- 局限：本 worker 未接触设备，所有状态列只是「交接时的历史状态」，不是本次验收。

---

### C7 GitHub 图片能力调研文档落主线

- Owner / session：收口 worker（确认）；调研 owner happy-fermi 线
- 状态：**PASS**

**验证**
- 文档在主线：`docs/research/github-image-feature-survey-20261124.md`，
  `git log origin/weifu-refactor -- <path>` → **`816f8ab7`**
  「docs(qa): 40002 根因闭环——照片横倒（EXIF=0），转正后端到端出结果；调研文档入库」。
- 内容在位（去马赛克 / 异物去除 / 二次元头像 / 换装换脸四类的成熟度、许可证、
  「跑在哪」结论；衣物透视人体明确标为不做）。
- 顺带核对：共享笔记里的 `gh-trends-native-rendering-20261123.md` /
  `gh-trends-web-rendering-20261123.md` 是**另一件事**（原生/网页渲染趋势），不在仓库、
  也不属于「图片」这条 —— 不混为未落线。

**局限**：只确认「文档在集成线」，其中推荐方案（IOPaint + LaMa）未实现、未验证。

---

### C8 三个待定产品决策（给用户一条汇总）

三条都**不需要写代码前的技术未知**，缺的只是用户拍板；未定前不动代码。

**决策 1 — 40002 要不要退额度（服务端）**
- 现状：`PhotoSolveController` 只在 50001 退额度，**40002 不退**。
- 为什么现在是问题：端上闸门修好（`8d38e82f` 线）后更容易撞到 40002，一次烧一次额度。
  真机取证：一次 PS2 撞 40002 净消耗 1 次；转正 EXIF 后重跑成功，整轮净消耗 2 次。
- 选项：a) 40002 也退（对用户友好，服务端要确认 40002 一定是「没消耗模型」）；
  b) 保持不退，端上把「会被收费」提示清楚；c) 40002 分类细分（模型拒答退、参数错不退）。
- 出处：`photosolve-fix-in-tip-8d38e82f-20261123.md`、`device-v2279a-window-20261123.md`。

**决策 2 — DocQuadNet 认不认「屏幕翻拍」**
- 现状：屏幕翻拍被判 40002；当前修法是把决定权交回用户（手动拖角），**不是**让它认屏幕。
- 选项：a) 补屏幕翻拍训练样本（算法切片，未做）；b) 加一条「高亮矩形」几何兜底；
  c) 明确不做，产品上只承诺纸质题。
- 出处：同上（该线明确列为「留给你的两条产品决定」之一）。

**决策 3 — 有状态贴纸走 Rive 还是继续 Lottie（是否付费）**
- 现状：Rive spike 真机 PASS（GPU mem ~2MB vs Lottie ~47MB），但 Free 导出**带 splash**，
  去 splash 需 Cadet **$9/seat/mo**；本 spike 未注册未付费。
- 三选一：a) 开 Cadet → Rive 做有状态贴纸竖切，特效层仍 Lottie；
  b) 不开付费 → 有状态也用多 Lottie 拼接，`:rive-spike` 冻结/删除作储备；
  c) 仅自产资产且法务接受无 splash 路径 → 再议。
- 架构红线不变：特效必须走叠加层 + `cmp.cue` Gesture，禁自造总线、禁进引擎。
- 出处：`fx-lines-status-rive-lottie-20261123.md`、`rive-spike-report-20261123.md`。

---

## 附：本次收口对「约 30 会话残余」的覆盖边界

账本收的是 brief 点名的 8 条。以下明确**不在**本 worker 范围，避免误认为已闭环：
- LiveKit 性能指标 → livekit-performance 会话（进行中）。
- 数字人 / 摄像头 / 特效视觉线 → her 视觉 worker。
- 声音馆七项验收矩阵 → playservice 线（另有条目）。
- 协议四项结论 → 已关闭，本次未触碰。
