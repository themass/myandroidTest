# Worker 全线续跑 · 状态总结（2026-11-23 夜，第二版）

**主线 tip：** `origin/weifu-refactor` → **`31f1c745`**（1.1.0.72 装机 bump）
（其下含 `8d38e82f` 拍照解题判负退四角微调、`2f16c4eb` PIP 提升组合根+camera-fx 包名登记（lottie-fx 已闭环，ModuleGraphTest 4/4 绿，tip 上复跑 exit 0）、`ece403c2` 对话页用户头像、`4b1c2b37` photo-edit S3、`cbe826e1` camera-fx 手势 cue、`fddbf38a` 通话字幕修复）

**装机 #3 完成（2026-11-24 10:36:54）：** V2279A = **1.1.0.72 / 1001000072**，包=tip **5e17a2f5** 现场 `assembleChatDebug`（md5 `741637d1…`，adb Success，vivo 确认由用户点）。中途插曲：用户 10:34 点的是更早推的旧 72 产物（tip 31f1c745，不含 screening 修）→ 删旧 apk 重新现场构建再装，最终包已核对。一次性覆盖验收面：放映场列表回刷 + 正文黑底黑字（5e17a2f5）、拍照 PS1–PS5（8d38e82f）、对话页头像（ece403c2）、通话字幕（fddbf38a）。协调侧 logcat tee：`/tmp/v2279a-tip-screening-tee.log`（-s ScreeningTextBody FeedPager MouthLevel）。已公告 screening / design-3-tasks 开跑各自验收行。

**待用户拍板（design-3-tasks 留给产品的两条）：** ① DocQuadNet 认「屏幕里的题」要补训练样本或加几何兜底（算法切片，工作量大）；② 服务端 40002 不退额度（只有 50001 退），手动四角放行后更容易撞 40002——改不退规则影响计费，归用户定。

**装机纪律：** 仅现场 `assembleChatDebug`。已在 tip `fddbf38a` 现场构建成功（exit 0，`app-chat-debug.apk` 322MB）。
V2279A（serial `10CE7P1G56004B7`）曾出现幽灵版本 1001000070（worker 从本地 worktree 装，版本号来路不明）→ 协调会话已 `adb install -r -d` 覆盖；当前装机 **tip 4b1c2b37 / 1001000071 / 1.1.0.71（22:39:02）**，含字幕修复 + photo-edit S3 + screening 收口。

**引擎舞台字幕修复 · 真机验收 PASS（attempt=b3a67d32，22:51 第二通）：** prefs 预置 `companion.dh_call_stage=SIMPLE` 后用户拨通，截图（`/tmp/stage_simple.png`）里字幕两条真实渲染（「你：你好。」「林澈：你好。上午出去溜了一圈…」）叠在波形上方、控件栏之上——`fddbf38a` 修复生效。
- 引擎舞台判定：截图出现「特效测试」胶囊（仅 `DigitalHumanLabHost` 引擎舞台绘制，`DigitalHumanLabHost.kt:473`）；形象是分层立绘 + 「轻触她的头、脸或手」= Simple 引擎的 `simpleFallback`（角色没有旧格式形象包时的既定回退，见 `CompanionRoute.kt:673`），不是「原来的形象」分栏。
- 用户此前「UI 非常旧」的观感 = 包没问题、页面停在「原来的形象」路径（call_stage=ORIGINAL）。
- ⚠ 通话中 prefs 被应用改回 **ORIGINAL**（唯一写点是引擎菜单点击 → 用户在 ⋮ 菜单里点过「原来的形象」）。下一通验引擎舞台要先在 ⋮ 菜单重选 Simple，别只看装机版本。

**第二通协议四项（p0-5-p2 复跑中）：** 上麦 1.9s ✓；hello 握手重试 5 次共 12.4s 才 ack（偏慢，记录在案）；下行首帧 rms=0.0339（拨出后 ~48s，F16 慢启动同源）；打断 ✓（barge_in playedMs=814 → speech_cancel user_barge_in，下行 maxLevel=0.867）；随后用户连说 70s 下行全零 = server 空回复（said=''）仍在，归 tos 线。22:53:09 通话被主动 force-stop（exit-info reason=10 USER_REQUESTED，非崩溃，watchdog 无隔离）。

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
| ~~P2~~ | ~~P1-12 流式单槽~~ | p1-12-markdown | ✅ **PASS**（08cd430a/3a4ea771 已在主线祖先链；tip 上实现均在） |
| P2 | SSE 集成测试挂死 `AgentSseReconnectIntegrationTest.sseFrameIdsAndEventFieldsSurviveRealParsing`（runTest 60s 超时；:feature-chat 215 例唯一失败，基线问题） | 新任务卡 | 立项待启动 |
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
