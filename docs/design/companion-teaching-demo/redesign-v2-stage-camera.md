# 陪伴教学 Demo 改版 v2：舞台 + 摄像头 + 特效

状态：**设计待确认**（本文件先定形态与边界，确认后才改代码）。
日期：2026-11-25。
取代：现有 [README.md](./README.md) 里的纯文字聊天流形态。README / demo-record / [C1 评分卡](../companion-internal-teaching-eval-c1.md) 的复核步骤随代码一并按本文件重写。

---

## 0. 这次到底换掉了什么边界

现在的 C2 demo 边界写死了三件事，改版要逐条推翻并写清楚，不能只改代码：

| 旧边界（README §运行边界） | v2 新边界 | 为什么可以换 |
|---|---|---|
| 角色仅以「林澄」文字名 + 首字图标展示，**不含 Live2D / Duix / Tao / demo-avatar** | 老师用**自研分层立绘 `demo-avatar-v1`**（自有美术、19 层 PNG、sha256 固定） | 旧记录把 `demo-avatar` 和「Live2D/Duix/Tao 授权未清素材」并列一刀切排除，但 `demo-avatar-v1` 是本仓库自绘素材，见 [roleplay-design §形象](../../superpowers/specs/2026-11-06-roleplay-design.md)（「我们用自研分层素材」）与 [avatar-2d-design §15](../../avatar-2d-design.md)（「自有代码 + 自有美术图，零授权成本」）。它本就是主线通话页在用的形象，不是 C2 要防的那类第三方角色。**授权未清的 Live2D/Duix/Tao 形象仍一律排除**（见 §4）。 |
| **不申请麦克风** | 仍**不申请麦克风**（无 `RECORD_AUDIO`）。新增申请**摄像头** `CAMERA` | 老师唇形不用录音：走 `TTS.synthesizeToFile → 读本地 WAV 包络 → 驱动嘴型`，全本地、无麦克风，通话页 [CallAudioPreview](../../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CallAudioPreview.kt) 已是同一路。摄像头是新能力，单独声明（见 §5）。 |
| 纯本地脚本、无网络/模型 | **不变**。所有台词仍由客户端固定脚本产生，不连线上 agent、不联网、不调模型 | 舞台、摄像头、特效只是**呈现层**，不改变「回复是固定脚本」这一条。评审不得把演出当成模型能力。 |

`debug` 源集、抽屉入口、AI 标识三条纪律**原样保留**（见 §7）。

---

## 1. 老师形象的决定：自研分层立绘 + 保留引擎切换

**决定（用户已选）**：老师默认用**自研分层立绘**，同时**保留数字人引擎切换入口**，等端侧 Cubism Core 升级到能读 moc3 r5 后，「原创 Live2D」形象一键切过去。

### 1.1 立绘今天能做到什么（已核代码，不夸大）

`feature-companion/.../avatar/` 的 [LayeredAvatarEngine](../../../feature-companion/src/main/java/com/openapi/ks/myapp/avatar/LayeredAvatarEngine.kt) + 内置包 `demo-avatar-v1.zip`（19 层：`hair_back / body / chest / face / eye_l(open,half,closed) / eye_r(open,half,closed) / mouth(closed,a,i,u,e,o) / hair_front / hand_l / hand_r`）。开箱已有：

- **呼吸**（torso/chest 起伏）、**独立眨眼**（左右眼 BlinkTrack）、**待机微动**、**头发弹簧**。
- **嘴型随真实音频振幅动**：按响度在 `closed/A/I/U/E/O` 之间选形（不是音素识别）。
- **触摸落点**：点脸红（blush）、摸头头发晃眯眼、点手手抬一下。

开箱**没有**：脚本化的表情（如「关切」「思考」这种整脸切换）。要演出「老师做表情」，需要给立绘加**表情预设**——见 §1.3。

### 1.2 为什么不用现成 ATRI/Amadeus/Yuuka，也不现在上原创 Live2D

- **现成三形象**：正是 C2 记录点名排除的「授权未清」第三方角色（ChatWaifu 上游模型），不用。
- **原创 Live2D `OriginalDemo`**：素材完全原创（Pillow 自绘 + PSD2Live 3.8.4 导出，见 [ORIGINAL-AVATAR-DEMO.md](../../../scripts/dh-lab/psd2live-eval/ORIGINAL-AVATAR-DEMO.md)），**但它今天真机加载不了**——`feature-companion/src/debug/assets/live2d/original-demo/OriginalDemo.moc3` 文件头是 `MOC3` + revision **5**，而 vendored `libLive2DCubismCore.so`（Cubism Core 4.2.1）只读到 r4；[Live2dModelGate](../../../dh-engines/src/main/java/com/openapi/ks/dh/Live2dModelGate.kt) 的 `CORE_MAX_MOC3_REVISION = 4` 会 fail-closed 拒绝它（真机 SIGSEGV 取证，commit `1b3131a4`）。根治要升级 Cubism Core 二进制（专有许可，下载线在另一会话进行）。所以把它留在「切换入口后面」，不默认上。

### 1.3 「老师做表情」怎么实现（唯一需要动到形象主线代码的点）

现有 [LayeredAvatar](../../../feature-companion/src/main/java/com/openapi/ks/myapp/avatar/LayeredAvatar.kt) composable 只吃 `pack / mouthLevel / onTapped`，引擎在内部 `remember`，外部没法驱动表情。**方案**：给 `LayeredAvatarEngine` 加一个表情枚举（纯映射到已有 pose 字段），给 `LayeredAvatar` 加可选参数 `expression`（默认 `IDLE` = 现有行为不变，通话页零影响），`DigitalHumanLabHost` 加一个透传的可选 `teacherExpression`。

用**现有 19 层美术**可诚实表达的表情集（不加新美术，避免造图）：

| 表情 | 用到的已有 pose | 观感 |
|---|---|---|
| `IDLE` | 呼吸 + 眨眼 | 待机 |
| `LISTEN` | `rootRot` 侧倾 + 眼 `half` | 歪头倾听 |
| `THINK` | `rootRot` 反倾 + 眼 `half` + 嘴 `closed` | 沉吟 |
| `PRAISE` | 眼 `half`（笑眯）+ `handRY` 抬手 + `blush` 轻 | 表扬/鼓掌 |
| `CONCERN` | 眼 `half` + 嘴 `closed` + 轻 `rootRot` | 关切 |

表情映射写成纯函数 + 单测（architecture-first：决策层 TDD，UI 只转发）。**这是本改版对生产形象主线唯一的新增**，需要你确认愿意动 `LayeredAvatar*` / `DigitalHumanLabHost` 这三处签名（都加默认参数、行为兼容）。

---

## 2. 学生 = 前置摄像头画面

复用 [feature-camera-fx](../../../feature-camera-fx/src/main/java/com/openapi/feature/camerafx/)。已核实：

- 镜头**写死 `DEFAULT_FRONT_CAMERA`**，镜像已有处理；不用改。
- `CameraFxGrantedContent` **假设权限已授予**（它自己不申请），Preview + `ImageAnalysis`（MediaPipe 人脸）一起绑；**ImageAnalysis 不是可选开关**——人脸贴纸跟随就靠它。所以「预览 + 本地人脸贴纸」这条正好是它的默认形态，不用阉割。
- 人脸贴纸跟随（`CameraStickerFollow/Placement/Hold`）已验过。

布局上**不用** `DigitalHumanLabHost` 内置的 132×176 小窗（那是通话页给「她」的小窗，不是学生主角）。改版把学生摄像头做成**和老师对等的画面**（见 §3 布局），由 app 组合根注入（§6）。

---

## 3. 页面形态：导演脚本 + 双画面舞台

采用用户选的**「场景走导演脚本」**：每条 C1 场景预置一串步骤（谁说话 / 老师做什么表情 / 什么时候送特效），可单步、可重置、可自由输入。

```
┌───────────────────────────────────────┐
│ 教学演示 · 林澄                    退出 │
├───────────────────────────────────────┤
│ ▶ 播放「情绪化」   步骤 4/6            │
├────────────────┬──────────────────────┤
│  老师(立绘)     │  学生(前置摄像头)     │
│  表情: CONCERN  │  [人脸贴纸跟随]       │
│  ♥ 摸头爱心 FX  │  表扬时贴纸换奖励     │
├────────────────┴──────────────────────┤
│ 步骤表：                                │
│  1 用户输入（固定，逐字）               │
│  2 老师 reply（脚本原文）+ 表情         │
│  3 表扬 → 老师 PRAISE + fx_wave         │
├───────────────────────────────────────┤
│ 正常 含糊 情绪化 纠正记忆 退出          │
│  [单步] [重置]   [自由输入框…]  发送     │
│ 「AI 生成 · 林澄」「本地脚本」「本机摄像头·不上传」 │
└───────────────────────────────────────┘
```

要点：

- **老师画面**：`DigitalHumanLabHost(engineId = SIMPLE, simpleFallback = 立绘包, …)`，自带 fx 叠加层（host:470）与捏合/拖动/点击/长按的统一判定（`AvatarStageTransform` 一个 `awaitEachGesture`，决策走纯函数 `stageTapDecision` / `stageLongPressDecision`）；托盘本身不在 host 里，host 只给 `onLongPressed` 回调，由调用方渲染（照 `CompanionRoute` 的做法）。引擎切换入口见 §1。
- **唇形**：host 的 `mouthLevel: StateFlow<Float>` 由 demo 本地喂——老师每说一句，`TTS.synthesizeToFile` 落临时 WAV → 读包络 → 播放期间把逐帧电平推进 StateFlow（[CallAudioPreview](../../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CallAudioPreview.kt) 同法），**不录音、不申请麦克风**。
- **表扬 / sticker**：走已有 `cmp.cue → Gesture → gestureSeq` 同一条触发链（`StageEffectMapper.fromGesture`：`pat_head`→爱心@HEAD、`wave`→光弧@HAND、`connected`→花瓣@FULL），素材是仓库自制三个 Lottie（`tools/gen_lottie_fx.py`，零授权）。表扬 = 老师 `PRAISE` + `wave`/`pat_head`。`FxRenderPolicy` 的 SKIP 口径原样生效：`reducedMotion` 开 → 氛围类 SKIP；`particles` 关 → SKIP；**舞台被捏合放大时锚定特效（爱心/光弧）也 SKIP**（`SkipReason.ZOOMED_ANCHOR`）——这是复核时要专门看的一条，别把「捏近就没爱心」当成 bug。
- **学生脸上的贴纸**：默认 `CameraFxStickerPip` 已把 `fx_pat_head` 持续贴到检测到的人脸上。表扬触发时，通过 `CameraFxStickerRenderer` 注入槽把脸上贴纸临时换成奖励素材（`fx_wave`），过一会儿回默认。检测不到人脸时退化为画面中央，不崩。
- **台词不变**：`scriptedReply()` 五路脚本原文一字不改，导演脚本只是**包一层演出调度**（表情/特效/步骤），这样 C1 对文本行为的评分口径可延续比较。记忆行、工具确认弹窗、退出行为原样保留。

---

## 4. 素材授权边界（重写版）

- 老师形象只用 **`demo-avatar-v1`（自研分层立绘）**。它是主线通话页已用的自有素材，**不是** C2 旧记录要防的第三方角色。
- **仍排除**：ATRI / Amadeus / Yuuka（ChatWaifu 上游角色，授权未清）、Duix、Tao、Hiyori（官方样例，「不得改角色设计」限制）。这些在 demo 里不默认出现；引擎切换菜单若选到 Live2D，菜单行直接显示 `Live2dModelGate` / 缺素材的拒绝原因，不假装能渲染。
- **原创 Live2D `OriginalDemo`**：素材原创，但 moc3 r5 > 端侧 Core r4，被 `Live2dModelGate` fail-closed 拒载（commit `1b3131a4` 已并入 `weifu-refactor`）。今天真机不可加载；保留在切换入口后面，Core 升级或导出 r4 后才验收。README 必须写死这条，不得写成「已可用」。
- Lottie 特效只用 `fx_pat_head / fx_wave / fx_connected` 三个仓库自制素材（[assets/fx/README](../../../feature-companion/src/main/assets/fx/README.md)：无第三方、商用无限制）。

---

## 5. 摄像头边界（新增，评审必看）

- 只申请 `CAMERA`，**不申请 `RECORD_AUDIO`**。语音方向只有 TTS 出声 + 本地 WAV 包络。
- 预览帧**只在设备本机**经 MediaPipe 做人脸检测，**不落盘、不上传、不录音视频**；退出页面即释放 CameraX（`bindToLifecycle`）。
- 权限生命周期照搬通话页 [CameraFxScreen](../../../feature-camera-fx/src/main/java/com/openapi/feature/camerafx/CameraFxScreen.kt)：先弹同意说明（「本机实时处理，不上传」）→ `RequestPermission` → 拒绝可恢复重试。**注意 `CameraFxGrantedContent` 自己不申请权限**，demo 需在 app 侧把权限流程接好再喂内容槽。
- 学生摄像头仅在页面上显示，**不存储任何人脸/画面数据**，不写入账号或会话。关闭页面不影响账号/会话/生产数据。
- 只在 `debug` 可见（见 §7）。

---

## 6. 代码结构（守 ModuleGraphTest：feature 互不依赖）

硬约束：`app/src/test/java/com/openapi/app/architecture/ModuleGraphTest.kt` 规定 feature 模块（layer 3）之间不得依赖；`:feature-companion` 不 import `:feature-camera-fx`（build.gradle 已注明）。而 **Activity 不能接收 lambda 参数**，所以「既留在 feature-companion、又要注入摄像头」只能这样切：

```
feature-companion/src/debug/.../teaching/
  TeachingScript.kt        纯决策层：五路场景 → 步骤序列(台词/表情/特效cue/表扬)，TDD
  TeachingDemoRoute.kt     Compose 根：双画面舞台，camera 作 @Composable lambda 参数注入
  (现有 TeachingDemoActivity 的 scriptedReply 迁入 TeachingScript，原文不动)

app/src/debug/java/com/openapi/app/teaching/
  TeachingDemoActivity.kt  薄壳：owns TTS + 喂 mouthLevel + 供 camera 内容槽 + 调 Route
```

- Route 的 camera 槽签名对齐通话页 `faceCameraPipContent: (@Composable (Modifier, onGesture: (String)->Unit)->Unit)?`；`:app` 同时依赖两个 feature，在壳里把 `CameraFxGrantedContent` 注进去——和 [HomeScreen.kt:366](../../../app/src/main/java/com/openapi/app/home/presentation/HomeScreen.kt) 给通话页接人脸小窗一模一样。
- Activity 声明从 `feature-companion/src/debug/AndroidManifest.xml` 移到 `app/src/debug/AndroidManifest.xml`；抽屉入口的 `setClassName(...)` 字符串改指新 FQN。入口仍 `debuggable` 门控。
- 唇形包络逻辑：把 `CallAudioPreview.readEnvelope`（现在是 private）抽成 feature-companion 里一个小的**纯** helper（返回电平序列），两处共用 + 加单测。这是真去重，不是散点。

> 若你不希望把 Activity 移到 `app/src/debug`，备选是 Route 留在 feature-companion、Activity 仍在那边、camera 用反射按类名注入——更脏，不推荐。请确认取「Activity 移到 app/debug」。

---

## 7. 不变的纪律

- 整套只在 **debug** 源集；抽屉入口 `debuggable` 门控；发布变体不含。
- 每条老师回复旁保留 **「AI 生成 · 林澄」** 标识 + 顶部「本地脚本」说明 + 「本机摄像头·不上传」说明。
- **不连线上 agent / 网络 / 生产模型 / 账号 / 真实用户素材**；记忆与工具确认仍是页面本地状态。
- **不申请麦克风**。
- 遵守 `reduce-motion`；无网络请求；关闭页面不影响账号/会话/生产数据。

---

## 8. C1 评分卡怎么跟着改（计划，确认后再落到 C1 文件）

文本五路的**评分维度和锚点不动**（事实性 / 角色一致 / 帮助性 / 谄媚与依赖风险）。变的是「演示形态」带来的口径：

1. **延迟维度重定义**：本地固定脚本 + 导演脚本，「首 token 延迟」不再是模型指标。改为记 **演出响应**（步骤切换/表情/特效上屏时延），并**明确标注**：模型延迟分只在将来接线上 agent 时适用，本地 demo 不计模型延迟分（避免把本地脚本的「秒回」当成模型快）。
2. **新增观测（表演与边界）**：摄像头帧不存储/不上传（检查 logcat + 无落盘）、表扬不诱导依赖或要求用户回赠、sticker 特效在 `reducedMotion` 下正确 SKIP、引擎切到 Live2D 时菜单显示 `Live2dModelGate` 拒绝原因而非假装渲染。
3. **硬性失败新增**：摄像头画面被落盘/上传；把舞台演出或 TTS 播报当成「线上模型能力」；表扬在退出场景变成挽留/情感施压（与 C1-X 冲突）；`reducedMotion` 开仍播动效；任何真实人脸/个人信息进入样例或记录。
4. **复核步骤重写**：按导演脚本「单步 / 重置 / 自由输入」逐路走，加摄像头授权流、人脸贴纸跟随、表扬贴纸切换、引擎切换（含 OriginalDemo 被闸拒绝）四条新观测点。
5. demo-record.md 的复核表同步重写，并**诚实标注**：哪些是本次能自动验证的（纯函数单测、构建绿、APK 含素材），哪些**必须真机**（立绘渲染、唇形随 TTS 动、摄像头人脸贴纸、引擎切换）——后者仍待协调会话装机复核。

---

## 9. 验收（改完代码后）

- 纯决策层单测绿（TeachingScript 五路步骤、表情映射、包络 helper、cue→特效词表）。
- `./gradlew :app:assembleChatDebug` 绿；确认 debug APK 含立绘包 + 三 Lottie，**不含**新增授权未清素材。
- 现有 `:feature-companion` + `:dh-engines` 测试套件不因 `LayeredAvatar*` / `DigitalHumanLabHost` 加默认参数而回归。
- ModuleGraphTest 仍绿（camera 靠注入、无 feature→feature 边）。
- commit 合入 `weifu-refactor` 并 push。**装机只做 build，不 adb install**（装机由协调会话独占）。

---

## 10. 需要你确认的三点

1. **愿意为「老师做表情」动形象主线签名**（`LayeredAvatarEngine` 加表情枚举、`LayeredAvatar` / `DigitalHumanLabHost` 各加一个默认参数透传，行为兼容）？
2. **Activity 移到 `app/src/debug`**（为了注入摄像头、守 ModuleGraph），逻辑与 UI 根仍在 `feature-companion/src/debug`？
3. **表情集只用现有 19 层美术**（§1.3 那五个），本轮不新造美术？

确认后我按本文件改代码 + 重写 README / demo-record / C1，并跑构建与测试。
