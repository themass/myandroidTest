# Worker 总结与同步状态（2026-11-24）

## 口径与基线

- 本文按用户要求汇总所有能在 linked sessions、提交记录和仓库文档中识别的 worker；把开发和设计排在前面，设备/测试验证统一放到最后。
- 本轮最初 GitHub fetch 因 HTTPS 连接超时失败；之后 worker 推送并更新本机跟踪引用 `origin/weifu-refactor` 到 `6d16f201`。该提交在装机代码 `5e17a2f5` 之后，主要补充 PhotoSolve 真机结果表与 QA 记录。远端最新仍未由本轮 fetch 独立确认；当前以本机最新跟踪引用 `6d16f201` 为同步基准。
- V2279A 最近已证实安装 `5e17a2f5` 构建的 `1.1.0.72 / 1001000072`；安装时间 `2026-11-24 10:36:54`，APK MD5 `741637d1d3f208166f4caf80c53f0ad7`。**只有协调会话装机**；worker 不执行 adb install、不 bump 版本、不复用旧 APK。
- 当前本机原始 `refs/heads/weifu-refactor` 仍停在 `5e17a2f5`；本轮已将 15 个安全、干净且闲置的 worker worktree 更新到 `6d16f201`（其中 14 个从 `62697dab` 快进，`happy-fermi` 已在该 tip）。活跃、脏或分叉 worktree 保留原样。详见文末同步清单。

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
| `design-3-tasks` | 收敛图片编辑、权限去重、PhotoSolve 端到端链路；先完成 S0/S1，再设备验收和后续切片。 | S0 操作模型/渲染器/编辑器、S1 选图/拍照后编辑并替换草稿、S3 草稿托盘长按“再编辑”均已完成；worker 报告 `feature-photosolve` 42/0（含 4 项 CaptureFlowTest）、`imaging` 48/0、`:app:compileChatDebugKotlin` 通过。提交 `ece403c2`、`28e9a193`、`8d38e82f`、`6d16f201` 位于 `origin/weifu-refactor` 祖先链；worktree 当前 HEAD `6d16f201`。 | PS1–PS5 与图片编辑 S2 真机验收仍未跑完，设备窗口交回给放映场 worker 后等待协调信号；已准备两张失败实拍素材，可经「对话→拍照解题→从相册选择」验证。worker 如实记录一次 force-stop 与一次误坐标 tap，现已停手排队；后续开跑前先确认前台页面归属。40002 是否退款、屏幕题识别是否补 DocQuadNet 样本/几何兜底属于待产品决策。 |
| `p1-10` | 图片加载失败时显示占位并支持点击重试。 | 代码及测试基线修复 `0464c151`、weifu 合入记录在主线；已同步到新 tip。 | 弱网下点击重试的真机点测仍列为 deferred。 |
| `p0-5-testserverV-maxtoken` | 修复拍题 JSON 因 maxTokens 截断后被误报非题目，并补结构 salvage。 | testserverV 三提交 `f0de0021 → a48e2425 → cbc260d9`；报告 photosolve 41 测 0 失败。 | **独立 testserverV 仓库/分支，未 push**；worker 明确等待用户决定，不能代推。真实豆包 API 联调需可用 API key。 |
| `p0-5-p2` / PhotoSolve 额度 | 梳理 40002/50001 额度行为和真机通话/产品边界。 | 产品问题已记录：当前 50001 才退款，40002 不退。 | 是否把 40002 纳入退款规则影响计费，须用户决策；不作为已完成改动。 |

## 4. 频道、放映场与全量新 UI

| Worker / 主题 | 目标与开发计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `text-sound-screening` | 将 text/sound 从旧 Activity 路由迁入 Screening Compose，并梳理 scheme 分发。 | `8f863cee`（并入 `ddf13a21`）令各 FeedPager 仅在用户下拉时刷新，覆盖文字馆、声音馆、图集和视频内页；`5e17a2f5` 为正文提供 `#FFFDF8` 浅纸面、`#1A1A1A` 字色及 viewport/图片自适应，并补取数失败分类日志。worker 回报 A/B/C 真机验收全部 PASS：正文可读；返回目录不重取（text/items.json 仍 1 次）；慢下拉后请求 +1 并重填第一页，视频内页 video/channel.json 也 +1。V2279A 安装包为 1.1.0.72 / tip `5e17a2f5`。 | 未覆盖事项：图集 Lightbox item/page.json 尾部续取、sound 与 PlayService 功能差距、首页 ChannelsScreen 旧二级页、markdown 内联图本地优先。服务端坏行 id=2352605 不在当前列表且不影响本链。worker 报告任务结束，但 session 元数据检查仍显示运行中；worktree 尚未改动，待 idle 后同步。 |
| `optimistic-pike` / Screening 子频道 | 将 `qvod_user` 用户片单和 `video_item_user` 视频子频道接入新页面；把未知 scheme 安全回退。 | `03c542ea`、`630639e8`、`4b758dea`、`a16a6f0c` 等在本机主线历史；已完成用户片单接口族、video_item_user 白名单和 scheme fallback 的代码路径。 | 本轮无法联网刷新远端；不能据日志宣称所有历史/余 scheme 都已完成。需跑剩余 scheme 清单及真机页面验收。该分支 worktree diverged，保留原样。 |
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
| LiveKit 协议复跑（`p0-5-p2`） | 对账上麦、hello、下行首帧、打断四项。 | 旧栈记录有上麦、播放态打断 PASS；最新状态文档记 hello 5 次重试共 12.4s 才 ack，首帧约 48s，打断成功。 | server 空回复、握手慢、首帧慢均未关闭；后续需要同一 attempt 同时核查客户端、104 voice.log 和听感。 |
| TOS 共享图片直传 | 设计客户端直传和服务端对象存储路径。 | 设计文档与桶方案已提交于 `tos` 会话材料。 | 本文无法证实该方案所有环境都已上线；不得误写成生产已部署。 |

## 7. 其他任务、研究与基线

| Worker / 主题 | 目标与计划 | 当前进展 | 遗留事项 |
|---|---|---|---|
| `roleplay-design` / `her-research` | 调研 Her 类陪伴产品、叙事架构与可借鉴 UX。 | 研究笔记和架构设计已交付；不阻塞 F16。 | 属产品研究，未自动成为开发需求；需产品选择后再排期。 |
| PhotoSolve API / 额度 | 对齐客户端题目/额度 API 与错误路径。 | API 与流程设计、PS1–PS5 真机清单已在仓库。 | PS1–PS5 的逐项实际结果尚未填；40002 退款与 DocQuadNet 屏幕题策略仍是决策。 |
| SSE 集成测试 | 新增测试挂死排查。 | 专项 worker 已完成 harness 修复，基线文档已更新。 | 如果后来出现新的挂死，应区分测试 harness 与生产流式路径；不能把已解决的单个用例扩大成全局保证。 |

## 8. 验证与真机验收（按要求置于最后）

### 已有明确证据

- **安装**：V2279A `10CE7P1G56004B7` 上 `1.1.0.72 / 1001000072`，安装时间 `2026-11-24 10:36:54`；设备包与 `5e17a2f5` 现场构建 APK MD5 一致（`741637d1d3f208166f4caf80c53f0ad7`）。构建日志记录 `BUILD exit=0`。
- **通话字幕**：SIMPLE 引擎舞台截图确认两行字幕实际显示；修复为 Compose modifier 先让出底部空间、再限制最大高度，并有源码护栏测试。
- **camera-fx 模块图**：lottie-fx 报告 ModuleGraphTest 4/4、companion 编译通过；camera-sticker-follow 报告 7 个状态机测试与 compile/test 通过。
- **SSE harness**：worker 报告 feature-chat 222/222，指定重连测试连跑三次通过。
- **Screening**：worker 报告 feature-video 139 tests / 0 failures，`assembleChatDebug` exit 0；V2279A A/B/C 真机验收全部 PASS。取数证据来自现有 myTag HTTP 日志；10:49:55 应用被 vivo 应用商店 force-stop（`am_kill`，非崩溃），worker 已停止商店进程。该时点后 PID 变化不是回归；uiautomator dump 偶发抢占 UiAutomation 失败并留下旧 XML，不能用 stale dump 作证。
- **PhotoEditor 逻辑**：设计文档记录模型/坐标/编辑逻辑 JVM 单测通过；这不覆盖真实 Canvas、导出像素与手势体验。
- **F16**：报告有两次设备首能量帧延迟数据及一次长故事完整播放；另有空回复与慢首帧失败样本，故整体链路仍开放。

### 尚待完成的人工设备检查

1. PhotoEditor S2：涂鸦、文字、马赛克、调整面板开关、标注选择删除、导出回草稿和存储行为。现有结果表仍为空。
2. PhotoSolve PS1–PS5，尤其屏幕里的题目是否进入四角微调并显示预期提示。
3. Camera Lab：正脸线框、手/姿态 HUD、头顶 Lottie 随动三项。
4. Camera sticker-follow：实际跟脸观感、漏检不闪与重新锚定。
5. Chat：选图/拍照编辑回草稿、MediaStack 展示、图片打开/长按操作、弱网重试和用户头像完整交互。
6. 视频/位置消息播放、定位和 `geo:` 跳转。
7. LiveKit：一次 attempt 完整具备 hello、上麦、ASR final、TTS outbound audio、实际听到并可打断；随后单独复测首帧慢和服务端空回复。
8. 数字人：设备模型资源存在性、Simple/Live2D/Duix/VRM 加载与切换、GL 恢复和素材换装。

## 9. Worktree 同步与分支归属

`origin/weifu-refactor` 当前本机跟踪 tip 为 `6d16f201`。共 15 个干净、可快进且闲置的 worker worktree 已位于该 tip：14 个从 `62697dab` 快进，`happy-fermi` 已经在 tip。这里只更新 worktree 分支指针，不代表重新构建或安装了 APK。

### 已同步到 `6d16f201`

`charming-shaw`（p1-12-markdown）、`determined-leavitt`（avatar-2d-design）、`distracted-wright`（lottie-device-accept）、`elastic-lovelace`（tts-25-32s-f16）、`elastic-mendeleev`（p0-5-p2）、`exciting-tereshkova`（camera-sticker-follow）、`f16-3d859c44`、`gallant-brahmagupta`（p1-10）、`gifted-shtern`（roleplay-design）、`happy-fermi`（design-3-tasks）、`mystifying-vaughan`（cam-lab）、`recursing-hugle`（f16）、`recursing-lederberg`（tos）、`vibrant-hugle`（p2-13）、`zealous-franklin`（SSE）。

### 按纪律保留、未擅自改动

- 活跃会话 worktree 不改：`focused-mclean`（数字人，HEAD `62697dab`）与 `optimistic-pike`（text-sound-screening，HEAD `5e17a2f5`）。前者分支名仍显示 `screening-auto-retry`，以当前 session 的 cwd 和运行状态为准；待会话结束后再核实并同步。
- `distracted-mirzakhani`（lottie-fx-mvp）和 `gifted-jepsen`（psd2live-eval）有未提交改动；保留现场，禁止 reset 覆盖。
- `upbeat-poitras`（camera-demo）与本 fork `vigorous-goldwasser` 相对 `weifu-refactor` 分叉；不得强制重置。camera-demo 的文档提交尚未确认合入主线。
- `weifu-refactor` 主 checkout 在 `5e17a2f5` 且有未提交修改；未动。
- `/private/tmp/weifu-push` 仍位于 `62697dab`，工作区干净；它不在 worker worktrees 目录中，本轮未改动。GitHub fetch 连接失败，因此 `6d16f201` 是本机跟踪引用当前值，未声称本轮独立确认了远端实时 tip。

## 10. 下一步执行顺序

1. 等 text-sound-screening session 元数据转为 idle；worker 已报告任务完成且 worktree 干净（HEAD `5e17a2f5`），届时再将其快进到集成 tip。
2. 对齐相机与 PSD2Live 两个独立/脏 worktree 的差异，先保存未提交工作，再决定是否合入；camera-demo 文档需要单独集成。
3. 逐项完成上面的真机验收表并回填 P/F/阻塞，不以“已装机”代替验收。
4. 对独立 testserverV 分支保留“未 push，等待用户决定”的状态；不代替用户推送。
