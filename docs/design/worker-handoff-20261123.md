# Worker 收口与任务总图（2026-11-23）

**主线：** `origin/weifu-refactor` → **`58a48481`**（含 TextureView 修、Screening text/sound、camera-fx、断线重试 Banner、dh wardrobe 文档证据等）。

**真机装机纪律：** 仅从 **`weifu-refactor` tip 现场 `assembleChatDebug`**，禁止复用旧 apk。当前 tip md5 示例：`a3c4a7f2296e0b41601ea1e1512ba88c`（`58a48481`）。

**服务端 F16：** 104 **`2808748`**（touch gate 第四刀）保持，直至协调会话另有部署。

**Worktree 同步：** 多数 worktree **落后 origin**；重启 worker 前执行 `git fetch && git checkout weifu-refactor && git pull` 或在各自分支 `merge origin/weifu-refactor`。

---

## 总任务地图（你列的四块 + 必记并行线）

| 域 | 目标 | 开发在前 | 验证在后 |
|----|------|----------|----------|
| **1 数字人 + 特效** | 三引擎（Live2D/Duix/VRM/Simple）+ 换装单一事实源 + 叠加特效 | wardrobe-spec、热换模、native 背景、Lottie/Rive 叠加层、VRM 课程页 | 通话页不崩、脸/嘴/换装、真机 GL resume |
| **2 摄像头 + 特效** | MediaPipe 脸/姿态/手 + Lab Demo + 未来进通话 | camera-fx module、HUD/chip、头顶 Lottie 跟随、fail-soft | Demo lab-b3 HUD、真机全开 |
| **3 图片编辑** | 选图/修图/拍照解题链路统一 UX | 设计落地（photo-editing-design）、权限去重、API | P1-10 占位重试、解题 E2E |
| **4 频道全量新 UI** | 放映场 Compose 吃掉各 scheme | text/sound/img/movie… Screening 路由 | 真机各频道不进旧 Activity |
| **5 陪伴/LiveKit（隐性主线）** | F16 有声可打断 | 端 hello/上麦、no hello→v1、remote_pcm、barge PLAYING 门 | 与 104 voice.log 同 attempt 验收 |
| **6 对话/chat（隐性）** | SSE 稳定、角色 id 对齐 | 角色接口 -parameters、footer 平铺、错误可观测 | chat 发信成功率 |

---

## 按会话 / Worker 对照

### 数字人

| name | 目标 | 任务列表（开发优先） | 进展 | 遗留 | 验证（最后） |
|------|------|---------------------|------|------|-------------|
| **session** | 三引擎接入 + Duix/Live2D 稳定 | Duix TextureView 背景门；Live2D GL onResume；热换模 `changeModel`；VRM seed | weifu 含 `canApplyThemeBackgroundToView`；热换、1.22x scale 曾推真机 | VRM 选人+scene.bundle；纯黑主题 edge | 通话进房不 FATAL；换装 ParamWear 真机 |
| **lottie-fx** | 特效层调研 + 通话叠加 | cmp.cue→Lottie 全屏礼物；reducedMotion；陪伴页特效测试入口 | 设计见 `2026-11-23-换装抽象与特效层设计.md` §二 | **通话页 Lottie MVP 未合主线** | 礼物 cue 真机 |
| **lottie-fx-mvp** | 通话页 Lottie spike | architecture-first 叠加层，不改原 UI | spike 分支可能未 ff weifu | 与 lottie-fx 合并排期 | spike 通过后再装机 |
| **psd2live-eval** | 素材管线 / wardrobe 证据 | psd→live 评估；MCP editor pose 导出 | `58a48481` 线含 `docs/design/psd2live-wardrobe-evidence-20261123.md` + wardrobe 证据 PNG/JSON | 内衣 pending-art；Android runtime；头饰 | editor 0/0.5/1 已验；真机 Cubism 未验 |
| **avatar-2d-design** | 2D 形象选型 | 文档 + PR#1（已关） | 研究交付 | 产品拍板未绑实现 | — |
| **ui-weifu-refactor** | 通话 UI 收尾 | retry Banner、波形/字幕（前端 skill 约束） | `cfbb94b1` merge goldwasser | compile 绿，**全量/真机未跑** | 断线重试 UX |
| **p0-5-p2** | 协议真机对账 | PENDING barge 只认 PLAYING（`8a7c909e`） | 1/4 PASS on 旧栈；自掐已修代码 | emotion 陪伴真机 PARTIAL；first_ms 25–32s | 生产 LiveKit 四项复测 |

### 摄像头

| name | 目标 | 任务列表 | 进展 | 遗留 | 验证 |
|------|------|----------|------|------|------|
| **lottie-fx** | camera-fx 合 weifu | HandLandmarker、手势 HUD、Pose 手扇区、`da5fd7bc` Lottie progress | **36435a7b** 已推 weifu | 用户曾装旧包无 lab-b3 | **lab-b3** HUD 冷启验收 |
| **camera-demo** | 独立 MediaPipe module | 五官/肢体 demo | module 存在 | 与主 app 入口关系 | 单独运行 |
| **camera-fx-lottie** | 头顶 Lottie 跟随脸 | 锚点 + 构图 | 子任务 | 合并到 camera-fx 主线 | Demo 头顶光弧 |
| **cam-lab-adb** | 真机清单 | demo checklist notes | 第二轮待用户 PASS/FAIL | — | 清单填报 |

### 图片编辑

| name | 目标 | 任务列表 | 进展 | 遗留 | 验证 |
|------|------|----------|------|------|------|
| **design-3-tasks** | 拍照解题 + 权限 + 编辑设计 | `photo-editing-design.md`、photo-solve-api | 设计会话 idle | **实现未开工** | E2E |
| **p1-10** | 图片加载失败 UX | 占位 + 点击重试 | 分支可能落后 weifu | 合主线 + 真机 | 弱网点图 |
| **p0-5-testserverv-maxtoke** | 拍题 maxTokens | testserverV 仓库 | 独立 repo | — | API |

### 频道 / Screening

| name | 目标 | 任务列表 | 进展 | 遗留 | 验证 |
|------|------|----------|------|------|------|
| **text-sound-screening** | text/sound 新 UI | `FeedRoute.TEXT/SOUND`、`ScreeningText/ScreeningSound` | **0cf13ed9 / 5250715d** 在 weifu；LEGACY 对 text/sound 已关 | img/movie 以外的 scheme 核对；播放/目录边界 | 真机点 text/sound 频道 |
| **session**（部分） | Screening 自动重试等 | 与频道无关的 retry 提案 | screening-auto-retry @ 36435a7b 落后 tip | ff 到 58a48481 | — |

### 服务端 / F16 / TOS

| name | 目标 | 任务列表 | 进展 | 遗留 | 验证 |
|------|------|----------|------|------|------|
| **tos** | 共享图/TOS + voice 运维 | touch warmup **2808748**；attempt 笔记 | MkoJY **FAIL**（端 TextureView 崩，非 server） | **端 hello/上麦/STT final** 下一刀 | 新 attempt + tts frames |
| **f16** | 短片段首帧部署 | 真机量 first_ms | idle | 依赖 tos+端 | PASS 听感 |
| **tts-25-32s-f16** | 慢启动 RCA | prewarm/imports | 文档 | 未关 | metrics |
| **p1-12-markdown** | 流式 Markdown 性能 | 单槽更新 | 开发在前 | 未合 weifu | 长回复流畅 |
| **p2-13** | 位置/视频消息类型 | 协议 + UI | 分支旧 | 排期 | — |

### 产品 / 研究（非阻塞开发）

| name | 说明 |
|------|------|
| **roleplay-design** / **her-research** | Her 类架构与竞品，指导陪伴叙事，不挡 F16 |
| **vigorous-goldwasser**（本 fork） | 协调、F16 装机、TextureView 修已进 weifu |

---

## 立即动作清单（协调人）

1. **所有 Android worker**：pull **`58a48481`** 再 `assembleChatDebug` 装机。
2. **MkoJY 教训**：ad92c627/5250715d 未同步 tip 会误判 F16——以 **HEAD md5** 为准。
3. **开发优先排序（建议）**：  
   - P0：端 **LiveKit hello + 麦克风发布**（unblocks F16）  
   - P0：数字人 **TextureView** 已修，确认 Duix/Live2D 双路径  
   - P1：**Screening** 余 scheme + **频道 Hub** 全 Compose  
   - P1：**camera-fx** 进通话（非仅 Lab）  
   - P2：**Lottie 礼物层**、**图片编辑**实现、**chat 角色 id**  
4. **验证批次（最后）**：F16 一通、lab-b3 摄像头、text/sound 频道点测。

---

## 推送状态（2026-11-23 晚间）

- **weifu-refactor**：已与 origin 同步 **`58a48481`**（gifted-jepsen worktree 已对齐 tip）。
- 部分会话自报 merge 停在 **cfbb94b1 / 5250715d / 36435a7b**——重启前 **必须 pull**。
- **companion-agent**：main **`2808748`** 由 tos 维护，Android 与 server attempt 须同窗口对齐。
