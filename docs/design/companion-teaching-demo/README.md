# 陪伴教学 Demo（C2 · v2 舞台形态）

这是内部研发评审用的**教学流程 prototype**，用于演示同一个自创角色在**老师形象（数字人舞台）、学生画面（本机前置摄像头）、表扬/sticker 特效**下，于文字/语音呈现、记忆纠错、工具确认、AI 标识和退出中的行为边界。它不是线上 agent，也不代表线上模型、记忆服务或工具实现。

所有台词由 Android 客户端内的固定脚本生成（`feature-companion/src/debug/.../teaching/TeachingScript.kt`，v2 只加演出调度，台词与旧版一字不差）；没有网络 API、生产模型调用、账号数据或真实用户素材。老师语音由设备 Android 系统 TTS 本地合成，唇形来自合成 WAV 的本地包络（`VoiceEnvelope`），**全程不申请麦克风**。

形态决定与新边界声明见 [redesign-v2-stage-camera.md](./redesign-v2-stage-camera.md)（用户 2026-11-25 确认：立绘+保留引擎切换 / 预览+本地人脸贴纸 / 场景走导演脚本）。评分场景定义与评分标准见 [C1 评分卡](../companion-internal-teaching-eval-c1.md)。可复核演示记录见 [演示记录](./demo-record.md)。

## 代码位置（v2）

| 部件 | 位置 |
|---|---|
| UI 根（双画面舞台 + 导演脚本） | `feature-companion/src/debug/.../teaching/TeachingDemoRoute.kt` |
| 导演脚本（纯决策层，TDD） | `feature-companion/src/debug/.../teaching/TeachingScript.kt` |
| 老师 TTS + 唇形包络（不申请麦克风） | `feature-companion/src/debug/.../teaching/TeachingSpeech.kt` |
| Activity 壳（注入学生摄像头） | `app/src/debug/java/com/openapi/app/teaching/TeachingDemoActivity.kt` |
| 老师形象（表情枚举 + 姿态映射） | `feature-companion/src/main/.../avatar/TeacherExpression.kt` |
| 摄像头内容 | `feature-camera-fx`（由 :app 组合根注入，feature 互不依赖） |
| 舞台特效 | `feature-companion/src/main/.../fx/`（自制 Lottie，见 assets/fx/README.md） |

Activity 壳住在 `:app` debug 源集是架构要求：Activity 不能接收 lambda，摄像头内容必须由组合根注入（`ModuleGraphTest`：feature 模块互不依赖）——与 fxlab、通话页人脸小窗同一条路。

## 启动

1. 使用仓库已有 Android 开发环境，连接设备或启动模拟器。设备需有中文系统 TTS 引擎；没有时，文字与演出仍可用，唇形不动（页面会显示降级说明）。
2. 构建 debug 包：

   ```bash
   ./gradlew :app:assembleChatDebug
   ```

3. 安装到已连接的设备（**装机只由协调会话执行**）：

   ```bash
   adb install -r app/build/outputs/apk/chat/debug/app-chat-debug.apk
   ```

4. 打开应用，从抽屉的「实验室」→「陪伴教学 Demo（本地）」进入。
5. 选择「正常 / 含糊 / 情绪化 / 纠正记忆 / 退出」场景，页面按该场景的导演脚本重置到第 1 步。点「▶ 播放」自动逐步推进，或「单步」逐步走；每步页面标注步骤序号、老师表情和特效 cue。
6. 老师在说话时嘴型随本机 TTS 音频动；表扬步会做「表扬」表情并向舞台送爱心/光弧特效（走 cmp.cue 同源词表）。
7. 首次进入学生画面会先弹本机说明，同意后才申请相机权限；拒绝可在页面重试。学生画面 = 前置摄像头实时预览 + 本机人脸贴纸（默认摸头爱心，表扬步换光弧）。
8. 「自由输入」走同一段本地脚本（`scriptedReply` 原文），文字与 TTS 照常。点「示范工具确认」（正常路第 5 步）分别检查「取消」和「确认」；确认只改变页面上的本地演示状态，不创建真实提醒。
9. 检查每条角色回复附近的「AI 生成 · 林澄」标识、顶部「本地脚本」「不上传」「不申请麦克风」说明。选退出场景播到 END 后，输入框隐藏并说明不会安排主动联系；右上角退出可随时关闭页面。
10. 「引擎」菜单可切 SIMPLE/LIVE2D/DUIX/TAO/VRM 看可用性原因；选「原创 Live2D」会安装 `OriginalDemo`（moc3 r5）——端侧 Core 已升 5-r.5、闸抬到 r6，**闸门放行**；能否真机渲染属装机复核项（旧 Core 下曾 SIGSEGV，从未在新 Core 上验证过），菜单行如实显示状态，不假装已可用。

## 运行边界

- Demo 入口、Activity 壳和页面代码仅在 debug 源集（`:app` 与 `:feature-companion` 各自的 debug）；抽屉入口在 debuggable 门控内。发布变体不包含这套教学实现。
- 台词是固定脚本，只覆盖教学场景。输入不在脚本范围时，不能把响应解释为线上 agent 能力。
- 未接入真实记忆、外部工具、通知、账号、网络或线上 agent；UI 中的记忆更新和确认均为本地演示状态。
- **不向设备申请麦克风权限。** 语音只由系统 TTS 播报脚本回复；唇形由合成 WAV 的本机包络驱动。
- **新增摄像头边界**：只申请 `CAMERA`；预览帧只在本机经 MediaPipe 做人脸检测，**不落盘、不上传、不录音视频**；退出页面即释放 CameraX；不存储任何人脸/画面数据。进入学生画面前有本机处理说明的同意步骤。
- 老师形象只用自研分层立绘 `demo-avatar-v1`（自有素材）。授权未清的 ATRI/Amadeus/Yuuka、Duix、Tao、Hiyori 形象不默认出现；原创 Live2D `OriginalDemo` 素材原创，端侧 Core 升 5-r.5 后闸门放行，但真机渲染未验收（见上）。
- 特效只用仓库自制三个 Lottie（`fx_pat_head` / `fx_wave` / `fx_connected`）；`reduce-motion` 开时氛围类不播，舞台放大时锚定特效不播。
- 关闭页面不会影响账号、会话或生产数据。

## 推荐评审顺序

按 C1 评分卡五路逐路「播放/单步」并重置，再演示自由输入、工具取消/确认、AI 标识、退出，以及四条 v2 新增观测：摄像头同意→授权→拒绝重试、人脸贴纸跟随与表扬换贴纸、`reduce-motion` 下特效 SKIP、引擎菜单的版本闸拒绝原因。记录设备型号、Android 版本、TTS 引擎、APK SHA-256、构建提交、每路实际输入与输出、各步表情/特效是否按脚本出现，以及是否存在未覆盖分支。不要把本地脚本响应、TTS 播报或舞台演出写成线上能力已验证。
