# Worker 全线续跑 · 状态总结（2026-11-23 夜，第二版）

**主线 tip：** `origin/weifu-refactor` → **`fddbf38a`**
（`a0b05092` 测试基线修复 → **`fddbf38a` 通话字幕修复**；其下含 `b02c869e` qvod_user 片单层、`0464c151` ReplyStreamerStreamTest 修、`7c80f7d8` 401 通话 UI）

**装机纪律：** 仅现场 `assembleChatDebug`。已在 tip `fddbf38a` 现场构建成功（exit 0，`app-chat-debug.apk` 322MB）。
V2279A（serial `10CE7P1G56004B7`）曾出现幽灵版本 1001000070（worker 从本地 worktree 装，版本号来路不明）→ 协调会话已 `adb install -r -d` 覆盖为 **tip fddbf38a / 1001000066 / 1.1.0.66（22:23:30）**。

**装机纪律（新增，已广播全体 worker）：** 只有协调会话可以 adb install 到 V2279A；worker 只 build 不装机、不 bump versionCode、不复用旧 apk；需要自带插桩上机先回报协调人按序合入再装机公告。

**服务端 F16：** 104 **`2808748`**；「打通但无声」根因在 server（无 ASR final / 无 TTS 出帧），与端播放开关无关。

---

## 本轮主线合入（协调会话）

| 项 | commit | 说明 |
|----|--------|------|
| 首页左侧抽屉留白 + 圆角 | `3d859c44` | 不再撑满屏幕 |
| **引擎舞台字幕失效修复** | **`fddbf38a`** | 见下节 |
| 401 通话页登录 CTA + 断连电平清零 | `7c80f7d8` | ui-weifu-refactor |

### 字幕失效根因（用户报「除了原来的形象，其他形象字幕都失效」）

`CompanionCallScreen.kt` 主角模式（CharacterHero，即 callStage≠null 的 Simple/Live2D/Duix/VRM）字幕链把
`.heightIn(max = 150.dp)` 写在 `.padding(bottom = 218.dp)` **之前**。Compose 里 padding 作用在下游内容高度上、
又被上游封顶压制 → 内容按 `max(0, 150 − 218) = 0dp` 测量，字幕栈高度为 0，**整条隐形**。

- 不是 z-order / GL 覆盖问题，也不是 `captionsVisible` 卡 false。
- 「原来的形象」走 Stacked 分栏（`weight(1f)` + 小 padding），所以正常——与用户观察完全一致。
- 缺陷自 `dd733f99`（主角模式引入）存在，`987f5397` 把 bottom 从 150 调到 218 后彻底归零。
- 修复：先 `padding` 让位、再 `heightIn` 封顶；并加源码护栏测试钉住链序
  （`CompanionCallStageLayoutTest.hero_caption_band_clears_control_bar_without_collapsing`，TDD 红→绿）。

### chat 图片输入与「收纳」展示（用户问「怎么不见了」）

代码都在 tip，未丢：
- `chatkit` `ImageLayout { Auto, Stack }` 默认 **Stack** → chat 与陪伴的图片消息一律走 `MediaStack` 收纳盒；`ImageArrangementTest` 钉住「一律收纳盒」。
- 输入侧：`ChatRoute.kt` 选图/拍照 → 暂存 → `PhotoEditor` → `replaceDraftImage`（photo-edit S0/S1 已合）。
结论：**旧包** 才是「不见了」的原因；装机批（tip chatDebug）验收即见。

---

## 遗留全部开工（本轮已唤醒，模型统一到 `anthropic/magpie-8489453032`）

| 级 | 事项 | 会话 | 投递 |
|----|------|------|------|
| P0 | F16 听得到 / 可打断（server STT+TTS） | **tos** / **f16** | 已在跑 |
| P0 | 真机 tip chatDebug 装机验收 | 本 fork | apk 已建，安装待手机端确认 |
| ~~P0~~ | ~~401 通话页样式~~ | ui-weifu-refactor | ✅ `7c80f7d8` |
| P0 | 引擎舞台字幕失效 | 本 fork | ✅ `fddbf38a` |
| P1 | Screening img/movie 余 scheme | text-sound-screening | queued |
| P1 | LiveKit 协议四项对账表 | p0-5-p2 | delivered |
| P1 | Lottie 礼物 MVP ff 决策 | lottie-fx-mvp | delivered |
| P1 | camera-fx 进通话 | lottie-fx | delivered |
| P1 | 头顶 Lottie 锚点 | camera-fx-lottie | queued |
| P1 | camera lab 真机 checklist | cam-lab-adb | queued |
| P1 | 数字人 Simple/seed、「未下载」 | **session** | 已在跑 |
| P2 | 图片编辑 S2+ 实现 | design-3-tasks | delivered |
| P2 | P2-13 位置/视频消息 | p2-13 | delivered |
| P2 | P1-12 流式单槽（需 rebase 到 0464c151 之后） | p1-12-markdown | delivered |
| P2 | TTS 25–32s 慢启动 RCA | tts-25-32s-f16 | delivered |
| ✅ | P1-10 图片失败重试 | gallant | 已闭环（weifu `4d380f89` + 基线修 `0464c151`） |
| ✅ | testserverV 拍题 maxTokens | testserverV | 链 `f0de0021`→`a48e2425`→`cbc260d9`，41 测 0 失败；**push 等用户本人** |
| — | roleplay / her 研究、avatar-2d PR#1 | 不挡 F16 | 等产品 |

---

## 下一步（顺序不变）

1. **手机端确认安装** → 通话选引擎 → 验字幕（本修复）、右侧 ‹ 收纳盒、长按 action、chat 图片收纳。
2. **tos/104**：同一 attempt 出现 transcript final + outbound audio → 真机听感验收。
3. worker 回报后把 PASS/FAIL 写回本文与 `worker-handoff-20261123.md`。

**边界提醒：** testserverV 的 push、生产库写入授权等都只能由用户本人决定/当面说，worker 之间不转述授权。
