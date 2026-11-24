# 多引擎特效对比 demo：设计

日期：2026-11-24。用户 2026-11-24 问「能否用这些技术做几个 demo 效果，就放到项目里」，并明确选了**多引擎对比版**（不是只做原生轻量版）。场景口径由用户给出：数字人有**互动场景**、**教学场景**；摄像头有**全屏**、**小屏**。

前置阅读：[2026-11-24-effects-rendering-architecture-and-options.md](2026-11-24-effects-rendering-architecture-and-options.md)（两条效果链路 + 候选库核查）。那份文档有三处措辞过强，本文按下方「对前一份文档的修正」为准。

## 1. 意图与为什么

现在两条链路的视觉动画只有 Lottie 一个后端。用户看过真机效果后觉得「调研的那些 sticker 效果都没出来」，需要的是**能亲眼对比**：同一场景、同一触发下，不同渲染后端各自能做到什么效果、代价多大（包体、资产工作流、API 门槛、能否吃现有资产）。口头选型表不足以做这个判断，所以要在项目里放**可跑的对比页**。

成功长这样：在 debug 包的一个 Lab 页里，四个场景 × 若干引擎自由切换，屏幕上直接看到差异，页面上写明「这个效果是哪个引擎画的、缺什么」。失败长这样：换了个库但看不出区别，或者 demo 里画的是手写 Canvas 却贴 Rive/Spine 的标签。

## 2. 目标 / 非目标 / 反目标

**目标**

1. 一个 debug-only 的「特效引擎对比」页，四种场景可切：数字人互动、数字人教学、摄像头全屏、摄像头小窗。
2. 每个场景下可切渲染后端，且**真用那个后端**：Lottie（现状基线）、Rive、自研 OpenGL ES 粒子、Three.js（WebView/WebGL）、AGSL RuntimeShader。
3. 每个格子说清楚：引擎名、资产来源与许可、依赖体积、缺资产/缺 API 时的原因（不是空白屏幕）。
4. 触发词表与生产同源（`gestureId` + `gestureSeq`、`StageEffect.Play`），不新增事件总线。
5. 相机隐私边界不变：帧只在本机内存，不上传、不落盘、不 log 人脸数据。

**非目标**

- 不做生产特效选型结论，不改生产 UI 默认行为（默认后端仍是 Lottie）。
- 不做 Spine / PAG / Effekseer / DragonBones 的可跑集成（各自需要编辑器授权或 NDK backend，见 §7）；它们在本文只作为「未纳入 + 原因」存在。
- 不做相机录制/对端可见合成（`OverlayEffect` / `SurfaceProcessor` / LiveKit 出向轨）——那是另一条独立工作，见 §9。
- 不做 UGC、形象换装、iOS。
- 不做性能基准表（fps/内存曲线）。对比页只给肉眼可见的差异与依赖体积；真要取数另立任务。

**反目标**（做了就算失败）

- 假 demo：用手写 Compose Canvas 冒充「Rive / Spine 效果」。
- 把 demo 依赖或资产泄漏进生产路径（release 包里多出 Rive so、或生产页默认换后端）。
- 为了 demo 改相机链路的语义（新的 coordinate 体系、新的手势判定）。
- 引入未确认授权的第三方美术素材。

## 3. 今天的实际情况（recon 结论）

- 舞台侧：`StageEffect.kt` 定义唯一事件词表 + `FxAssets` 白名单 + `FxAnchors`（HEAD/BODY/HAND/FULL 视口归一化锚点）；`LottieFxLayer.kt` 是渲染层，预缓存资产、按 `gestureSeq` 播、按 `FxRenderPolicy` 降级。它是引擎视口之后的 Box 兄弟：不进 GL、不加 `pointerInput`。
- 相机侧：`feature-camera-fx` 里 `CameraFxGrantedContent` = CameraX Preview + ImageAnalysis + MediaPipe Face/Pose/Hand；`FxAnchors`/`CanvasMap`（FILL_CENTER + mirrorX）把归一化 landmark 转成画布坐标；头顶贴纸目前只有 Lottie（`CameraFxOverlayPolicy.stickerAssetId`）。`CameraFxStickerPip` 是通话页小窗内容，由 :app 注入。
- 数字人本体：`DhEngineId` = SIMPLE / LIVE2D / DUIX / TAO_AVATAR / VRM，`DigitalHumanLabHost` 已能在 Lab 里切本体引擎，Simple 舞台已能显示随包内置的分层立绘（`BundledAvatars` + `LayeredAvatar`，含呼吸/眨眼/嘴型/触点反应）。
- Three.js 已在仓库：`feature-companion/src/main/assets/dh-vrm/scene.bundle.js`（离线，760K，esbuild 从 `scripts/dh-lab/vrm-scene` 打，three 0.187.0 + three-vrm 3.5.5，皆 MIT）；`VrmDigitalHumanEngine` 用 WebView + 伪源拦截，不联网。**但**它需要下载的 .vrm 模型，全新装机显示「未下载」。
- 没有 Rive、PAG、Effekseer、Spine 的任何依赖或资产。
- 模块纪律：feature 互不依赖（`ModuleGraphTest`）；相机 PIP 由组合根 :app 注入。
- minSdk 28 / target 37；CameraX 1.4.2；Lottie Compose 6.7.1。
- 真机验收纪律：只有协调会话给 V2279A 装机，worker 只 build、不 `adb install`、不 bump versionCode。

## 4. 三个做法与选择

**A. 原生轻量版**：只加 Lottie 之外的一个原生引擎（Rive），其余场景复用现有代码。代价小、风险小；但用户明确要多引擎对比，A 答不了「OpenGL / Three.js 到底长什么样」。

**B. 多引擎对比版（用户选的）**：一个 debug Lab 页，四个场景 × 五个后端（Lottie 基线 + Rive + GL 粒子 + Three.js + AGSL），每个后端一个真正的最小渲染器 + 自制资产。代价：新增 2 个运行时依赖（Rive、无——GL/AGSL 是平台 API，Three.js 走已有 WebView 栈）；需要为每个引擎产出**可自由再分发**的资产（自制，见 §7）；工作量最大但一次覆盖全部疑问。

**C. 独立 spike 工程**（不接进 app，单独一个 sample module/工程）。隔离最彻底、可以随便崩；但用户说「就放到项目里」，而且脱离现有场景（真形象、真 cue、真相机链路）后对比失真。

**选 B**，并用 A 的保守手段落地：全部挂在 debug-only Lab 入口后面，生产默认路径一行行为不改。

## 5. 页面形态

debug 包抽屉 →「实验室」→ 现有「摄像头定位 Demo」旁边加一条「特效引擎对比」。入口判定与 `CameraFxLabGate` / `DhLabGate` 同思路（debuggable），不进生产导航。

放置：新 Activity 在 **`app/src/debug`**（:app 是组合根，已经负责把 `CameraFxStickerPip` 注入 companion；跨 feature 的东西只能在 :app 拼）。它复用 `DigitalHumanLabHost` 与 `CameraFxGrantedContent`，不改这两个函数的现有语义。

界面草图：

```
┌ 特效引擎对比 ────────────────────────┐
│ 场景: [互动][教学][相机全屏][相机小窗] │
│ 引擎: [Lottie][Rive][GL粒子][Three][AGSL] │
│ ─────────────────────────────────────│
│                                      │
│   （场景画布：数字人 / 相机预览）      │
│        ← 特效按锚点叠在这里            │
│                                      │
│ 触发: [摸头][挥手][接通][停]           │
│ 状态: Rive · 资产 OK · 依赖 +3.7MB ·  │
│       API 28+ · 不吃 Lottie JSON      │
│ 教学模式: 步骤 2/4 「指给观众看这里」   │
└──────────────────────────────────────┘
```

- 「互动」= 现有真形象（内置分层立绘，Simple 舞台）+ 点身体部位 + 触发按钮 + 相机手势（小窗开启时）→ 走 `gestureId/gestureSeq`。
- 「教学」= 同一形象 + **脚本化 cue 序列**（按步骤自动播：提示环 → 指示箭头 → 强调闪烁 → 收束），验证非手势触发路径与 `repeat`/`LOOP_UNTIL_CANCELLED`/氛围类降级。
- 「相机全屏」= 复用 `CameraFxGrantedContent`（wireframe 可开），特效锚在**真实头顶锚点**（`stickerAtHeadTop`），不是视口固定点。
- 「相机小窗」= 132×176 dp 的 `CameraFxStickerPip` 尺寸，验证同一套在小尺寸下的可读性与开销。
- 顶栏显示当前引擎的元数据（资产状态、缺什么、API 门槛），避免「黑屏当效果」。

## 6. 引擎 × 场景矩阵（谁在哪跑，为什么）

| 后端 | 互动 | 教学 | 相机全屏 | 相机小窗 | 接入方式 | 已知代价 |
|---|---|---|---|---|---|---|
| **Lottie**（基线） | ✅ | ✅ | ✅ | ✅ | 现状（Compose `LottieAnimation`） | 无新增依赖；不是粒子引擎 |
| **Rive** | ✅* | ✅* | ✅* | ✅* | 新增 `app.rive:rive-android:11.16.3`（runtime MIT，官方实测 Maven metadata 2026-11-11）；官方 Compose API 为 Beta；作为 Box 兄弟渲染 | `.riv` 资产要重做（不吃 Lottie JSON）；**官方样例资产无明确再分发授权 → 资产来源是这格的开放问题（见 §7、§13）** |
| **GL 粒子**（自研 GLES3） | ✅ | ✅ | ✅ | ✅* | 透明 **TextureView + EGL**（绕开 GLSurfaceView 的 Surface 层级风险），Box 兄弟 | 无资产；自研责任（生命周期/坐标/纹理同步）|
| **Three.js**（WebView/WebGL） | ✅ | ✅ | ✅ | ✅* | 复用离线 WebView 伪源模式，新增**不依赖外部模型**的场景 bundle（three.js MIT，esbuild 重打包） | WebView 开销；相机场景为透明 WebView 本地叠加，标注清楚 |
| **AGSL RuntimeShader** | ✅ | ✅ | ✅ | ✅ | Compose `RuntimeShader` + `ShaderBrush`，**透明叠加光效（不采样相机/数字人像素，不是后处理）** | **API 33+**；minSdk 28 下低版本显示「本机 API 28，需 33+」而不是回退假效果 |

`*` = 该格在相机小窗/缺资产时按 §11 的实现约束走：相机只新增默认 null 的贴纸 renderer 注入口，透明 GL/WebView 组件叠在本机预览上，尺寸、头顶 EMA 与丢失容错仍归相机侧。

## 7. 资产与许可（每个引擎必须自带可分发资产）

- **Lottie**：沿用仓库自制 `fx_pat_head` / `fx_wave` / `fx_connected`（`tools/gen_lottie_fx.py` 生成，无第三方素材）。新增一个「教学提示环/箭头」资产也用同一脚本生成，同规格（透明背景、无表达式、无内嵌位图、≤100KB）。
- **Rive**（研究结论 2026-11-24，已核官方仓库/文档）：
  - runtime：MIT（`rive-app/rive-android/LICENSE`），坐标 `app.rive:rive-android:11.16.3`（Maven metadata 实测 2026-11-11）；官方 Compose API 为 **Beta**，加载流程已文档化：`rememberRiveWorker()` → `rememberRiveFile(RiveFileSource.RawRes.from(R.raw.x))`（处理 Loading/Error/Success）→ `Rive(riveFile.value)`。
  - 资产：`.riv` 只能由 Rive 编辑器产出。官方 sample app 里确实带小样例（`app/src/main/res/raw/loopy.riv` 211B、`circle_move.riv`、`clipping.riv`），**但仓库只有 MIT LICENSE，未对 `.riv` 给素材级再分发授权**——代码 MIT ≠ 素材可用。结论：**官方样例不作为内置资产**；这是 Rive 这一格的开放问题（见 §13）。
  - 默认做法（我的决定）：Rive 接线做全（debug 依赖 + 渲染器 + 状态机输入名 + 失败提示），从 `app/src/debug/res/raw/` 读 `.riv`；没有放授权素材时页面显示「缺 .riv 资产（Rive 编辑器导出或用户提供）」，**绝不用手写动画冒充 Rive**，也不默认拉官方 sample 进仓库。
- **GL 粒子**：纯代码生成（无资产）。
- **Three.js**：场景由我们自己写的 JS + three（MIT）生成，**不加载任何外部模型**，避开 VRM 示例模型的 personalNonProfit 限制。
- **AGSL**：shader 由我们写，无资产。
- 任何下载的第三方素材都不进仓库；如果后面想用 LottieFiles/Rive 市场的素材，逐个核资产许可再另说。

## 8. 触发、降级与代码结构

- 词表不变：Lab 页触发按钮调 `emitStageGesture` 同一条路径的等价本地入口（`gestureId` + 递增 `gestureSeq`）喂给 `LottieFxLayer`/新渲染层；教学模式是页内脚本按序喂同样的词，**不新增总线**。
- 新增纯函数层（JVM 单测，TDD）：
  - `FxEngineId`（LOTTIE / RIVE / GL_PARTICLE / THREE_WEBGL / AGSL）+ `FxEngineRegistry`；
  - `FxEngineGate.decide(engine, sdkInt, assetState, scene)` → `ENABLED / UNAVAILABLE(reason)`：把「API 门槛」「缺资产」「场景不支持」三件事从 UI 里拆出来钉死。低版本不猜、不假回退。
  - `FxEngineMeta`（依赖坐标、体积、资产来源与许可、是否吃 Lottie JSON）——就是页面上那行状态文字的数据源，避免宣传式描述。
- 舞台侧渲染层按 `FxEngineId` 分派；**默认参数 = Lottie**，生产调用点不传参 → 行为与今天完全一致。
- 舞台侧渲染层按 `FxEngineId` 分派；**默认参数 = Lottie**，生产调用点不传参 → 行为与今天完全一致。
- 相机侧：`CameraFxOverlayPolicy`（数据类）**不动**。注入口加在 Composable 边界——`CameraFxGrantedContent` 增加一个默认 null 的 `stickerRenderer` 参数；null = 走今天的 Lottie 分支，非 null = 用注入的后端画同一位置/同一尺寸。尺寸、EMA、丢失容错仍由相机侧拥有，不新增坐标体系。
- 渲染层红线不变：不加 `pointerInput`/`clickable`（手势仲裁只有一处）；不打帧、不落盘。

## 9. 明确不做的一件事，顺手把前一份文档的措辞修正

对 [上一份选型文档](2026-11-24-effects-rendering-architecture-and-options.md) 的修正（本文优先）：

1. ~~「Rive 是唯一『状态机 + 数据绑定 + 原生 Android』的组合」~~ —— 说过头。Spine 运行时也有状态机与 skin/attachment 切换，原生 Android 可用（商业授权另算）；准确说法是「在**交互动效**这一档里 Rive 的状态机工具链最顺手」。
2. ~~「相机贴纸不是 Spine 的场景」~~ —— 太绝对。Spine 能做骨骼化贴纸（跟随 landmark 播骨骼动画），代价是要 Spine 编辑器资产 + 授权核算。本 demo 不纳入它，理由是资产与授权，不是「技术上不适合」。
3. 「LiveKit 出向视频轨今天还不存在」是基于 grep 的判断，不是完整架构审计；本轮不做这一层，结论保留为「未验证」。
4. GSAP 免费含商用 ≠ OSI 开源；dotLottie 也有其他 runtime，「Lottie 运行时只有 Airbnb」不准确。
5. 「选型分三类」实际列了四类。

## 10. 验收

- `./gradlew :feature-companion:testDebugUnitTest :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug` 通过；新纯函数层有单测（gate 的三条降级路径、engine registry、教学 cue 序列的纯函数）。
- `ModuleGraphTest` 通过（新 Activity 在 `:app` debug source set，不新增 feature→feature 依赖）。
- 装包与真机四场景验收：**由协调会话**在 V2279A 上按主线的装机纪律做（本任务只 build、不 `adb install`、不 bump versionCode）。交付时写清「编译与单测已过、真机未验」。
- 页面每格必须能自证「跑的是哪个引擎」：状态行显示引擎 + 依赖 + 资产状态；Rive 若缺资产，明确显示而不是静默换回 Lottie。
- 仓库收尾按主线惯例：commit → 合 `weifu-refactor` → push（先 fetch 主线、跑测试，再 ff）。

## 11. Builder check 后的实现约束（覆盖上文冲突处）

- AGSL 是透明 ShaderBrush 叠加光环，不采样相机/数字人像素，不称为实际后处理。
- 新代码、资产、依赖全部放 `app/src/debug` 与 debugImplementation；不得放 main assets。主源集只增加受 debuggable 保护的显式类名导航，release 不注册 Activity。
- 保持生产 CameraFxOverlayPolicy、LottieFxLayer 不变。相机仅增加一个默认 null 的可组合 sticker renderer 注入口，尺寸、头顶 EMA 与丢失容错仍由相机拥有；:app debug 页面注入其他后端。相机不依赖任何新 runtime。
- GLES 使用透明 TextureView + EGL，避免 GLSurfaceView 层级问题，因此小窗也允许；Three.js 是透明 WebView，小窗也可对比，标明本机预览叠加。矩阵中的旧限制由本条覆盖。
- 所有后端支持停止/重播；数字人页从内置分层包加载真实形象；教学为手动上一步/下一步四步示例，全部本地确定性，无服务端连接。不模拟真实教学内容或语音。
- 依赖体积不写未测数字，显示依赖名和实测 APK 大小；不承诺每库增量。
- Rive 必须实际播放授权明确的样例才能算完成；缺素材只能算阻塞，不是已交付 demo。

## 12. 留给实现时的决定（不影响做什么）

- GL 粒子的具体形态（发射器形状、颜色、与 cue 的对应）——按「摸头=从头顶散落的爱心状粒子、挥手=沿手方向的弧带」做，实现时自定。
- Three.js 场景形态（环形光带 vs 旋转几何体）——实现时自定，只要不加载外部模型。
- 三个新引擎的依赖版本：以接入时的 Maven metadata 实测为准，和仓库口径一致（直接写在模块 build.gradle，不进 `libs.versions.toml`）。
- Lab 页资产放 `app/src/debug/res/raw/`（Rive `.riv`）与 `app/src/debug/assets/fx_lab/`（Lottie 教学资产、Three.js 场景 bundle）——**不放 main assets**，这样 release 包里物理上不存在这些资产，可用 grep + 打 release 包验证；生产词表 `fx/` 不受影响。

## 13. 唯一需要用户回答的开放问题：Rive 的 `.riv` 从哪来

runtime 与接线都不是问题（依赖、Compose Beta API、加载流程都已确认）。但可播放的 `.riv` 只能由 Rive 编辑器导出，且官方 sample 里的三个小样例（`loopy.riv` 211B 等）**没有素材级再分发授权**（仓库只有 MIT 覆盖「the Software」）。所以三选一：

- **默认（我已选定）**：Rive 格子做全接线 + 读 `app/src/debug/res/raw/`，缺素材时页面显示「缺 .riv 资产」。这符合 §11「缺素材只能算阻塞，不是已交付 demo」——对比页其余四格照常可看。
- 你手上有 Rive 编辑器导出的 `.riv`（或愿意注册免费账号导一个），放进 `app/src/debug/res/raw/`，那一格就真能播。
- 或者接受把官方 sample `.riv` 放进 debug-only 目录承担授权不确定——**我不建议**，除非你明确说可以。

这一条不阻塞其余四格开工；回答与否只决定 Rive 那一格是「可播」还是「接线完成 + 缺素材」。
