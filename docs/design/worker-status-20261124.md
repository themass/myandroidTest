# Worker 总结与同步状态（2026-11-24）

## 口径与基线

- 本文按用户要求汇总所有能在 linked sessions、提交记录和仓库文档中识别的 worker；把开发和设计排在前面，设备/测试验证统一放到最后。
- 此前状态记录曾以 `6d16f201` 为基线；本轮 fetch 先确认 `a8bf409e`，随后同步期间又到达 PhotoSolve 旋转修复 `1cb41189` 与 QA 根因闭环 `816f8ab7`。本报告更新提交 `6309c680` 已推送至 `weifu-refactor`，包含上述功能、QA 与 FeedListShell `75b00100`。
- V2279A 最近已证实安装 `5e17a2f5` 构建的 `1.1.0.72 / 1001000072`；安装时间 `2026-11-24 10:36:54`，APK MD5 `741637d1d3f208166f4caf80c53f0ad7`。**只有协调会话装机**；worker 不执行 adb install、不 bump 版本、不复用旧 APK。
- 本轮开始时集成线为 `a8bf409e`，之后更新至 `816f8ab7`；报告提交 `6309c680` 已推送。16 个干净、闲置且可快进的 worker worktree 现同步到 `6309c680`；活跃、脏或分叉 worktree 保留原样。详见文末同步清单。

## 1. 数字人、舞台特效与素材调研

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `session`（数字人三引擎） | 接通 Simple、Live2D、Duix/VRM；处理 TextureView 背景、GL 生命周期、换模、seed/资源状态。 | 主线已有 Duix TextureView 背景门、Live2D 生命周期与换模相关实现；Simple 在旧 avatar 包缺失时用分层立绘回退。数字人入口、引擎舞台及资源状态逻辑仍在项目内。 | VRM 选人/scene.bundle 和真实资源完整性需要产品与素材支持；黑色主题等边界要确认。设备资源目录曾要求补取证，本轮未取得资源存在性的可靠结果。 |
| `lottie-fx`（特效层架构） | cmp.cue/舞台手势驱动 Lottie 叠加层；把 CameraFx PIP 从 companion feature 依赖移到 App 组合根注入；补 ModuleGraph 规则。 | camera-fx package/root 映射和组合根注入已合入；`feature-companion -> feature-camera-fx` 依赖已移除。模块图任务报告 4/4 通过，`:feature-companion` 编译通过。 | Live2D 可用性有一条在改动前基线也存在的失败，属于数字人适配线；实际礼物 cue 与引擎组合仍需设备观察。 |
| `lottie-fx-mvp`（通话礼物 spike） | 在不改原通话 UI 的前提下验证独立 Lottie 特效叠加层、cue 映射与 reduced-motion 方向。 | Spike/设计边界已对齐；架构决策是叠加层 + cmp.cue，主线已有 stage effect mapper 方向。 | 礼物 MVP 的产品决策及真机效果仍需验收；不能把 spike 结论当作所有引擎已验收。 |
| `camera-fx-lottie` | 将头顶 Lottie 锚点绑定脸部跟踪，支持平滑位置与丢帧行为。 | 跟随实现并入 camera-sticker-follow 线；`036244ad` 增加最多 3 帧 hold，连续漏检时保持最后位置，重新检测后 snap。新增 7 个状态机测试。 | `graceFrames=3`、EMA 参数和跳脸阈值还未在真机确认；需与 lab-b3 贴纸一起看观感。 |
| `psd2live-eval`（PSD→Live2D 素材管线） | 评估 PSD 素材拆层、Cubism 参数、换装变形和导出闭环。 | 已交付变形三联图、cmo3 导入/导出闭环证据、说明文档；`84d109d0` 推送到主线的记录含 9/9 单测结果。 | editor_only 证据不代表 Android Cubism 运行时；真实角色美术、atri_8 头饰扫参、EULA 法务仍待解决。参数范围须按 rig 实测，不可猜。 |
| `avatar-2d-design` | 调研 Android 2D 形象方案并给出选型。 | PR #1 已关闭，选型/设计材料已交付，worktree 已同步。 | 这是研究交付；具体形象资产与产品选型尚未绑定实现。 |

## 2. 摄像头、跟踪特效与调研

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `camera-demo` | 独立 MediaPipe module 展示五官、姿态、手部定位，并说明接入 App 的边界。 | demo 功能存在；`d715707d` 在 worker 自己的分支推送了主 App module wiring 文档，gesture 功能提交 `ab6b8ed2` 已在 weifu 主线祖先链。 | wiring 文档分支与主线有分叉，未确认合入；当前 branch 相对 `origin/weifu-refactor` diverged，不能自动 ff。独立 demo 需自行启动确认。 |
| `lottie-fx` / `camera-fx` | CameraX + Face/Pose、overlay policy、HUD/chip、手势 cue；按用户意图开摄像头，不自动开全局 camera。 | `CameraFxOverlayPolicy`、lab 偏好、PIP 注入及 gesture→stage cue 已实现；架构约束文档明确本地处理、相机贴纸与舞台 Lottie 分层。 | 通话内人脸贴纸菜单入口、指尖 landmark、HandLandmarker/GestureRecognizer 到 stage cue 仍是明确后续；当前只实现 v1 的姿态骨架范围。 |
| `camera-sticker-follow` | 为跟脸贴纸加入 EMA 平滑、跳脸 snap 和短暂漏检容错。 | `036244ad` 已在主线祖先链；7 项状态机单测与 compile/test 报告通过。 | lab-b3 真机贴纸随脸/倾角观感未验证。 |
| `cam-lab-adb` | 在 V2279A 上逐项核验 lab-b3 HUD、无脸提示、chip/全开、退出清理及头顶 Lottie。 | checklist 多数入口、权限和退出项有 PASS 记录。 | 三项需要正脸进入前摄约 30 秒：脸部线框、手/姿态 HUD、Lottie 随头部移动。设备验收尚未全部关闭。 |

## 3. 图片编辑、拍照解题与权限

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `design-3-tasks` | 收敛图片编辑、权限去重、PhotoSolve 端到端链路；先完成 S0/S1，再设备验收和后续切片。 | S0 操作模型/渲染器/编辑器、S1 选图/拍照后编辑并替换草稿、S3 草稿托盘长按“再编辑”均已完成；worker 最新报告 `feature-photosolve` 46/0、`:app:compileChatDebugKotlin` 通过（此前另报告 `imaging` 48/0）。旋转入口 worker commit 为 `040f580c`，同一补丁已以 `1cb41189` 进入集成线；QA 根因闭环见 `docs/qa/photo-solve-device-result-20261124.md`（`816f8ab7`）。 | S0/S1/S3 已完成；PhotoEditor S2 仍待真机验收。PhotoSolve PS1、PS2（使用转正图）、PS3、PS5 与头像 AV 的 worker 验收均 PASS；横倒图触发 `40002` 的根因是 EXIF orientation=0 且图中文字横倒，不是屏幕翻拍本身。转正图成功返回解答并落历史。PS4 未跑，需相机拍摄真实纸张题目。本次真机额度净消耗 2 次（一次 `40002` 不退款、一次成功；一次 `50001` 已退款）。设备仍是 `1.1.0.72` 旧包，旋转按钮尚未装机验收；worker 报告设备已 RELEASED，待协调会话构建、安装并公告后可复验。`happy-fermi` 已 idle，worktree 已同步到 `6309c680`。 |
| `p1-10` | 图片加载失败时显示占位并支持点击重试。 | 代码及测试基线修复 `0464c151`、weifu 合入记录在主线；已同步到新 tip。 | 弱网下点击重试的真机点测仍列为 deferred。 |
| `p0-5-testserverV-maxtoken` | 修复拍题 JSON 因 maxTokens 截断后被误报非题目，并补结构 salvage。 | testserverV 三提交 `f0de0021 → a48e2425 → cbc260d9`；报告 photosolve 41 测 0 失败。 | **独立 testserverV 仓库/分支，未 push**；worker 明确等待用户决定，不能代推。真实豆包 API 联调需可用 API key。 |
| `p0-5-p2` / PhotoSolve 额度 | 梳理 40002/50001 额度行为和真机通话/产品边界。 | 产品问题已记录：当前 50001 才退款，40002 不退。 | 是否把 40002 纳入退款规则影响计费，须用户决策；不作为已完成改动。 |

## 4. 频道、放映场与全量新 UI

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `text-sound-screening` | 将 text/sound 从旧 Activity 路由迁入 Screening Compose，并梳理 scheme 分发。 | `8f863cee`（并入 `ddf13a21`）令各 FeedPager 仅在用户下拉时刷新，覆盖文字馆、声音馆、图集和视频内页；`5e17a2f5` 为正文提供 `#FFFDF8` 浅纸面、`#1A1A1A` 字色及 viewport/图片自适应，并补取数失败分类日志。worker 回报 A/B/C 真机验收全部 PASS：正文可读；返回目录不重取（text/items.json 仍 1 次）；慢下拉后请求 +1 并重填第一页，视频内页 video/channel.json 也 +1。V2279A 安装包为 1.1.0.72 / tip `5e17a2f5`。同一 worker 另交付 FeedListShell：四类 feed（电影、图集、音频、文章）共用吸顶 toolbar 和浮动回顶按钮，刷新及安全区处理集中到壳；功能提交 `75b00100`，集成 tip `a8bf409e`。 | FeedListShell worker 报告 `:feature-video:testDebugUnitTest` 全绿、`assembleChatDebug` exit 0；worker 只 build，未装机或改 versionCode，故新壳尚无真机验收。既有 A/B/C 验收不等同于 FeedListShell 验收。其他未覆盖事项：图集 Lightbox item/page.json 尾部续取、sound 与 PlayService 功能差距、首页 ChannelsScreen 旧二级页、markdown 内联图本地优先。服务端坏行 id=2352605 不在当前列表且不影响本链；session 已 idle，worktree 当前在 `6309c680`。 |
| `optimistic-pike` / Screening 子频道 | 将 `qvod_user` 用户片单和 `video_item_user` 视频子频道接入新页面；把未知 scheme 安全回退。 | `03c542ea`、`630639e8`、`4b758dea`、`a16a6f0c` 等已在本机历史；已完成用户片单接口族、video_item_user 白名单和 scheme fallback 的代码路径。worktree 当前已同步到 `6309c680`。 | 仍需跑剩余 scheme 清单及真机页面验收；本轮未复核这些交互。 |
| 放映场 UI（协调合入） | 新频道 Hub、feed 列表/详情与内容可读性。 | 放映场正文浅色底、深色字和下拉刷新已在安装包代码 tip。 | 只装机并不代表人工验收已完成；A/B/C 项需单独记录。 |

## 5. Chat 页面、图片消息与语音输入

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| Chat 图片输入 + MediaStack | 保留选图/拍照、编辑回草稿和图片消息收纳展示。 | `chatkit` 默认 `ImageLayout.Stack`，chat/companion 图片消息走 MediaStack；选图/拍照→PhotoEditor→替换草稿链路存在。主线安装后截图确认用户头像可见。 | 设备上还需实际打开两类图片消息并点开编辑链，避免只凭安装截图推断所有交互。 |
| Chat 用户头像 | 补回对话消息用户侧头像槽位。 | `ece403c2` 合入；装机后截图显示用户侧头像。 | 完整聊天发送/多消息头像节奏仍需端到端验收。 |
| `p1-12-markdown` | 降低流式更新重组成本，单槽更新并分段 Markdown。 | `08cd430a` / `3a4ea771` 等实现与测试在主线祖先链；worker 报告 PASS。 | 不再是代码遗留；长回复实际流畅度属于体验抽检。 |
| `sse-feature-chat` | 修复真实 socket SSE 集成测试挂死。 | `a40ac702` 修改测试 harness 的 chunked 终止块/RST 丢帧/accept 竞态；worker 报告 feature-chat 222/222，相关场景连跑 3 次通过；未改生产 SSE 代码。 | 可按当前基线复核测试；该修复针对测试稳定性，不等于生产 SSE 故障全部排除。 |
| `p2-13` | 增加位置与视频消息类型展示、协议解析和跳转。 | `dbe941d9` 与 merge `94b55eb0` 已在 weifu 祖先链；worker 报告 PASS。 | media3 播放、地理定位粗定位、`geo:` 跳转等真机点测需随协调安装批完成。 |
| `ui-weifu-refactor` | 语音输入 UI 收尾及通话断连重试/电平状态清理。 | 语音输入 UI 已推主线；`7c80f7d8` 为 401 登录 CTA 与断连电平清零。安装纪律修正已做，worker 未留版本号改动。 | 通话断连重试 UX 与完整语音输入真机交互仍需验收。 |
| Chat/陪伴链路澄清 | 区分 Java SSE chat 与 LiveKit companion；定位角色 ID、登录错误、头像/footer 等问题。 | 问题与拆解记录在 `chat-companion-status-and-plan.md`；部分 chat avatar、图片栈与通话 UI 已在当前主线。 | Java roles `-parameters`/服务端目录对齐、错误详情可观测等需按产品/服务端排期；不得把旧设计文档里的“待实现”直接当作现状，应逐项对照提交。 |

## 6. LiveKit、TTS、服务端与数据传输

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `tos` / F16 服务端 | 保证同一 attempt 有 ASR final 和 TTS outbound audio；定位无声、空回复与图像直传。 | 104 当前部署记录为 `2808748`；尝试链路和首帧指标已与 Android 端记录对齐。TTS RCA 将长首句主要归因于 CueJsonGuard 结构扣留和模型生成方差；根治 `7ef3ace` 据记录已在部署中。 | 最近可听通话仍有服务端空回复样本；下一次拨测要求同 attempt 有 transcript final + outbound audio。共享图片/TOS 桶是设计线，需按原部署/隐私约束处理。 |
| `f16` | 在 V2279A 上现场量首帧并确认可听、可打断。 | worker 报告 2026-11-23 新测量：设备首能量帧相对服务端 TTS first 为 0.240s / 0.153s；一轮长故事听到完整落地。 | first_ms 仍有 16.3s 慢样本；另一轮还记录 ASR/STT 阻塞。TTS 保温 follow-up 未做；不能把所有通话均宣称 PASS。 |
| `tts-25-32s-f16` | 分解 25–32 秒首帧慢启动来源。 | RCA 文档已合入；说明 CueJsonGuard 结构扣留与模型方差为主，冷启动不是该段的主因。 | 静音+文字长句复测还需验证 ratio 中位数和首帧日志；这是另一条待做拨测。 |
| `p0-5-p2` | 在 V2279A 上对账 hello/上麦、下行起播门、可听窗打断与同 attempt 的实际听感。 | 三轮 attempt↔job 配对全部通过：hello_ack 与 104 negotiated 同 sid；静音预缓冲不触发 t0/barge；真实首音频后才允许打断；两轮有可听窗中断/截断证据，并有客户端电平、服务端 TTS peak 与用户听感相互印证。记录见 `docs/design/p05p2-proc-table-20261123.md`，已在 `origin/weifu-refactor` 的 `09de236f`。 | **本 worker 原定协议/真机对账目标已完成，无本线遗留。** first_ms 慢和服务端空回复已在对账文档按签名分流至 tos/F16 线，不属于本线验收。 |
| TOS 共享图片直传 | 设计客户端直传和服务端对象存储路径。 | 设计文档与桶方案已提交于 `tos` 会话材料。 | 本文无法证实该方案所有环境都已上线；不得误写成生产已部署。 |

## 7. 其他任务、研究与基线

| Worker / 主题 | 目标与计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `roleplay-design` / `her-research` | 调研 Her 类陪伴产品、叙事架构与可借鉴 UX。 | 研究笔记和架构设计已交付；不阻塞 F16。 | 属产品研究，未自动成为开发需求；需产品选择后再排期。 |
| PhotoSolve API / 额度 | 对齐客户端题目/额度 API 与错误路径。 | API 与流程设计、PS1–PS5 真机清单与逐项结果已记录于 QA 文档。复验确认转正后端到端成功；横倒照片会触发 `40002 NOT_A_QUESTION`。 | PS4 真实纸张题目尚待相机拍摄；服务端仍可考虑统一归一方向或提示旋转；`40002` 是否退款仍待产品决策。 |
| SSE 集成测试 | 新增测试挂死排查。 | 专项 worker 已完成 harness 修复，基线文档已更新。 | 如果后来出现新的挂死，应区分测试 harness 与生产流式路径；不能把已解决的单个用例扩大成全局保证。 |

## 8. 验证与真机验收（按要求置于最后）

### 已有明确证据

- **安装**：V2279A `10CE7P1G56004B7` 上 `1.1.0.72 / 1001000072`，安装时间 `2026-11-24 10:36:54`；设备包与 `5e17a2f5` 现场构建 APK MD5 一致（`741637d1d3f208166f4caf80c53f0ad7`）。构建日志记录 `BUILD exit=0`。
- **通话字幕**：SIMPLE 引擎舞台截图确认两行字幕实际显示；修复为 Compose modifier 先让出底部空间、再限制最大高度，并有源码护栏测试。
- **camera-fx 模块图**：lottie-fx 报告 ModuleGraphTest 4/4、companion 编译通过；camera-sticker-follow 报告 7 个状态机测试与 compile/test 通过。
- **SSE harness**：worker 报告 feature-chat 222/222，指定重连测试连跑三次通过。
- **Screening**：worker 报告 feature-video 139 tests / 0 failures，`assembleChatDebug` exit 0；V2279A A/B/C 真机验收全部 PASS。取数证据来自现有 myTag HTTP 日志；10:49:55 应用被 vivo 应用商店 force-stop（`am_kill`，非崩溃），worker 已停止商店进程。该时点后 PID 变化不是回归；uiautomator dump 偶发抢占 UiAutomation 失败并留下旧 XML，不能用 stale dump 作证。
- **PhotoSolve 真机**（worker 报告，QA 记录更新至 `816f8ab7`）：V2279A 原装 `1.1.0.72` 包上 PS1、PS3、PS5 与头像 AV PASS；横倒原图因 EXIF orientation=0 且像素内容横倒而回 `40002`。worker 用转正探针复测，PS2 成功返回解答并落历史；中间一次 `50001` 超时已退款。PS4 未跑，缺真实纸张照片。真机额度净消耗 2 次。旋转入口提交为 worker commit `040f580c`，同一补丁以 `1cb41189` 集成；worker 报告 `feature-photosolve` 46/0、`:app:compileChatDebugKotlin` 通过。新旋转按钮仍未装机验收；设备状态为 RELEASED，待协调会话安装新包后复验。以上均为 worker 记录，本轮未独立重跑真机测试。
- **PhotoEditor 逻辑**：设计文档记录模型/坐标/编辑逻辑 JVM 单测通过；这不覆盖真实 Canvas、导出像素与手势体验。
- **FeedListShell**：worker 报告 `:feature-video:testDebugUnitTest` 全绿、`assembleChatDebug` exit 0；提交 `75b00100` 已在 `a8bf409e`。这项新壳尚未真机安装验收。
- **LiveKit 协议/真机对账（p0-5-p2）**：三轮 attempt↔job 配对的 hello/上麦、下行起播门、可听窗打断与用户听感均 PASS；正式证据见 `docs/design/p05p2-proc-table-20261123.md`（`origin/weifu-refactor` commit `09de236f`）。其中首帧仍慢、另有服务端空回复样本，已单独分流给 tos/F16，不影响本 worker 目标完成。
- **F16**：设备侧首能量帧与服务端 TTS 首帧的关联有实测；端到端 `first_ms` 仍有 19.6–31.3s 慢样本，且有服务端空回复样本，因此性能/空回复主线仍开放。

### 尚待完成的人工设备检查

1. PhotoEditor S2：涂鸦、文字、马赛克、调整面板开关、标注选择删除、导出回草稿和存储行为。现有结果表仍为空。
2. PhotoSolve PS4：用设备相机拍真实纸张题目后验收；新旋转入口尚未随新包装机验收，服务端仍可考虑统一方向归一化。
3. Camera Lab：正脸线框、手/姿态 HUD、头顶 Lottie 随动三项。
4. Camera sticker-follow：实际跟脸观感、漏检不闪与重新锚定。
5. Chat：选图/拍照编辑回草稿、MediaStack 展示、图片打开/长按操作、弱网重试和用户头像完整交互。
6. 视频/位置消息播放、定位和 `geo:` 跳转。
7. LiveKit 的协议/上麦/可听/打断验收已由 `p0-5-p2` 三轮对账关闭；剩余首帧慢与服务端空回复归 tos/F16 性能与服务端线继续处理。
8. 数字人：设备模型资源存在性、Simple/Live2D/Duix/VRM 加载与切换、GL 恢复和素材换装。

## 9. Worktree 同步与分支归属

报告提交前的代码基线为 `816f8ab7`；本轮将 14 个干净、闲置且可快进的 worker worktree 从 `ee3dfae7` 更新到该基线，`optimistic-pike` 从 `a8bf409e`、`happy-fermi` 从 `816f8ab7` 快进。报告提交 `6309c680` 推送后，16 个干净闲置 worker worktree 均再次快进到该提交。这里只更新 worktree 分支指针，不代表重新构建或安装了 APK。

### 已同步到 `6309c680`

`charming-shaw`（p1-12-markdown）、`determined-leavitt`（avatar-2d-design）、`distracted-wright`（lottie-device-accept）、`elastic-lovelace`（tts-25-32s-f16）、`elastic-mendeleev`（p0-5-p2）、`exciting-tereshkova`（camera-sticker-follow）、`f16-3d859c44`、`gallant-brahmagupta`（p1-10）、`gifted-shtern`（roleplay-design）、`happy-fermi`（design-3-tasks）、`mystifying-vaughan`（cam-lab）、`optimistic-pike`（text-sound-screening）、`recursing-hugle`（f16）、`recursing-lederberg`（tos）、`vibrant-hugle`（p2-13）、`zealous-franklin`（SSE）。

### 按纪律保留、未擅自改动

- 活跃会话 worktree 保留：`focused-mclean`（数字人，HEAD `62697dab`，session 仍运行）。其分支名仍显示 `screening-auto-retry`，以当前 session 的 cwd 和运行状态为准；待会话结束后再核实并同步。`happy-fermi` session 已 idle；其 clean worktree 从 `816f8ab7` 快进到 `6309c680`。
- `distracted-mirzakhani`（lottie-fx-mvp）和 `gifted-jepsen`（psd2live-eval）有未提交改动；保留现场，禁止 reset 覆盖。
- `upbeat-poitras`（camera-demo）与本 fork `vigorous-goldwasser` 相对 `weifu-refactor` 分叉；不得强制重置。camera-demo 的文档提交尚未确认合入主线。
- `weifu-refactor` 集成 worktree 起始于 `a8bf409e`，存在未跟踪 `.local-shots/` 与一份 ZIP 文件；快进及文档合入时保留了这些文件。
- `/private/tmp/weifu-push` 位于 `ee3dfae7`，不在 worker worktrees 目录中，本轮未改动。

## 10. 下一步执行顺序

1. `focused-mclean` 结束后复核并同步其 worktree；PhotoSolve 新旋转入口待协调会话按装机纪律现场构建并单写装机，worker 已表示公告后可在 5 分钟内复验。
2. 对齐相机与 PSD2Live 两个独立/脏 worktree 的差异，先保存未提交工作，再决定是否合入；camera-demo 文档需要单独集成。
3. 逐项完成上面的真机验收表并回填 P/F/阻塞，不以“已装机”代替验收；其中 PhotoSolve PS4 需真实纸张照片。
4. 对独立 testserverV 分支保留“未 push，等待用户决定”的状态；不代替用户推送。

## 11. 完成导向与重点研发方向

### Worker 收口口径

- 每个 worker 只对齐其已分配的目标与验收条件，持续执行到代码/文档交付、必要验证通过、改动集成到目标分支；不得把未来设想或新需求塞进当前任务，制造无限遗留。
- 只有目标内的功能、证据和集成条件都通过，才汇报“完成”。最终回报只写目标、结果、验证证据、提交/集成位置；不附带未完成清单。
- 遇到目标外扩展时单独登记为新方向。遇到确实不能由 worker 消除的条件（设备占用、缺素材、产品决策、凭证、外部服务或合规审查），立即向协调会话提交一条具体阻塞及所需输入；不得把阻塞伪装成完成，也不得静默留在“遗留事项”。
- 设备验证仍由协调会话单写装机。worker 可在设备释放并收到协调会话通知后执行其验收项；worker 不安装、不改版本号。

### 重点研发方向（独立立项，不混入旧 worker 验收）

1. **LiveKit 基建与通话体验**：持续改善网络连通、媒体稳定性、agent 首音频延迟、打断和空回复。每轮优化以同一 attempt 的端到端日志和设备听感作为证据；部署走灰度、健康检查与回滚流程，先验证再扩大流量。
2. **Her 数字人体验**：围绕角色一致性、长期记忆/关系推进、自然语音互动和舞台特效做调研；交付可运行的扮演教学 demo、效果示例和可量化验收标准，再决定进入产品的切片。特效继续采用叠加层 + `cmp.cue`，不改原 UI 主结构。
3. **摄像头与虚拟试衣**：持续评估人脸/人体/手部识别、跟踪特效、手指滑动切换效果，以及在线换衣/帽和换头保留躯体的端侧与服务端方案。先交付设备可运行 demo、性能/机型边界与素材要求；摄像头由用户明确进入功能后开启。服装/头部形变统一以 `wardrobe-spec` 为事实源。
4. **图像处理候选**：评估 IOPaint/LaMa 的单进程 CPU 服务部署成本和质量；评估 AnimeGANv3 ONNX 端侧原型及其商业授权；对 `finger-frame-effect-ai` 核查许可证、模型/数据权利、端上性能后再决定复现或集成。Lada 去马赛克保持主线排除，除非有明确正当场景并完成许可证与内容审查。
5. **依赖准入规则**：开源或模型素材都要先核实许可证、模型权重/训练数据权利、商业使用限制和分发义务，再决定原型或发布。研究可以记录候选，但不能以“实验 app”为由跳过授权条件或把未审查依赖带入主线。

### 方向优先级与交付顺序

- P0：收口当前目标内的 worker 任务，并完成已承诺的验收；先处理 LiveKit 端到端无声、空回复及慢首帧的根因闭环。
- P1：并行推进 Her 教学 demo 与摄像头/手势效果 demo；都先提供可运行证据和性能边界，再定产品实现范围。
- P2：完成 IOPaint/LaMa、AnimeGANv3 与手势效果项目的许可证/性能评估；通过准入审查后再开集成任务。
