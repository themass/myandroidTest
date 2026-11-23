# Worker 全线续跑 · 状态总结（2026-11-23 夜）

**主线 tip：** `origin/weifu-refactor` → **`3d859c44`**（含首页左侧抽屉留白 `ui(home)`）。

**装机：** 仅现场 `assembleChatDebug`（`app-chat-debug`），勿用 release / 旧 apk。实验侧栏（HeroSideDrawer、引擎菜单、Lottie 测试）需 **debuggable + 通话选数字人引擎（callStage≠null）**。

**服务端 F16：** companion-agent **`2808748`**；当前用户「能打通无声」≈ **无 STT final / 无 TTS 出帧**（见 voice.log），与 Android 播放开关无关。

---

## 本轮协调动作

已向 **17 个 idle 子会话** 投递续跑消息（merge weifu `3d859c44` + 各域未完项）。**tos** 本已在跑 F16 监控，另补一条 STT/TTS 备注。

本 fork（vigorous-goldwasser）本日合入主线：

- ✅ 首页左侧 `ModalDrawerSheet` 留白 + 圆角（`3d859c44`）
- ✅ 厘清「右侧实验栏不见」：包类型 / callStage / 非删代码

---

## 已完成（可依赖）

| 项 | 证据 |
|----|------|
| text/sound Screening 新 UI | weifu 已合；LEGACY text/sound 关 |
| camera-fx module + 首页 lab 入口 | `CameraFxLabGate` + debug |
| TextureView / Duix 背景门（曾 MkoJY 端崩） | 已进 weifu 线 |
| 通话右侧收纳盒 + action 托盘 + Lottie 叠层（代码） | `HeroSideDrawer` / `DigitalHumanLabHost` @ tip |
| wardrobe 设计 + 部分 smoke 证据 | docs / psd2live 会话 |
| touch gate 第四刀 | server `2808748` |
| 断线重试 Banner 等 UI 片段 | ui-weifu / weifu 部分 merge |
| ScreeningFeedTest 修 | `c8d03feb` |

---

## 进行中（已唤醒子会话）

| 会话 | 续跑内容 |
|------|----------|
| **tos** | STT final / turn / TTS，attempt 对齐 |
| **ui-weifu-refactor** | 401 通话 UI、重试/波形收尾 |
| **session** | 数字人未下载 UX、引擎真机 |
| **p0-5-p2** | LiveKit 四项真机对账 |
| **f16** | first_ms + 听感 |
| **text-sound-screening** | 余 scheme Screening |
| **lottie-fx** | camera-fx + lab-b3 |
| **lottie-fx-mvp** | spike 是否 ff weifu |
| **p1-10** | 图片失败重试合主线 |
| **p1-12-markdown** | 流式单槽 |
| **design-3-tasks** | 图片编辑实现切片 |
| **cam-lab-adb** | 真机 checklist |
| **camera-fx-lottie** | 头顶 Lottie |
| **psd2live-eval** | wardrobe 证据 |
| **tts-25-32s-f16** | 慢启动 RCA |
| **p2-13** | 位置/视频消息 |
| **p0-5-testserverv-maxtoke** | maxTokens |
| **camera-demo** | demo 与入口关系 |

---

## 未完成 / 遗留

| 优先级 | 遗留 | 归属 |
|--------|------|------|
| **P0** | F16 **听得到 + 可打断**（server STT/TTS 主阻塞） | tos + f16 + p0-5-p2 |
| **P0** | **chatDebug 真机 tip 装机**（用户侧栏/实验「像丢了」多属 release 或 callStage=null） | 协调 / 本 fork |
| **P0** | 未登录 **401 通话页** 专用样式 | ui-weifu-refactor |
| **P1** | 数字人 **Simple/seed**，菜单不全「未下载」 | session |
| **P1** | Screening **img/movie** 等 scheme | text-sound-screening |
| **P1** | **通话页 Lottie 礼物 MVP** 合主线 | lottie-fx-mvp |
| **P1** | camera-fx **进通话**（非仅 Lab） | lottie-fx |
| **P2** | 图片编辑 **实现**（设计→代码） | design-3-tasks |
| **P2** | VRM 选人 + scene.bundle | session |
| **—** | roleplay / her 研究 | 不挡 F16 |
| **—** | avatar-2d-design PR#1 已关 | 等产品拍板 |

---

## 下一步计划（建议顺序）

1. **tos + 104**：同一 attempt 出现 **user_transcript final + outbound audio energy**（阻塞听感）。
2. **真机**：`weifu` pull → `assembleChatDebug` → 通话顶栏选引擎 → 确认右侧 ‹ 收纳盒与长按 action。
3. **Android P0 并行**：ui-weifu（401）、session（DH 可用性）、p0-5-p2（协议表）。
4. **验证批**（server 有 TTS 后）：F16 一通、lab-b3、text/sound 频道点测。
5. **P1 批**：Screening 余路由、Lottie ff 决策、P1-10 合主线。

---

## 子会话结果

各 worker 回报后由协调会话 **合并进本文** 或刷新 `worker-handoff-20261123.md` 的 tip 与 PASS/FAIL 表。用户可见 **Linked sessions** 侧栏查看运行中会话。
