# 开源特效技术如何落进我们的产品（设计提案）

状态：设计提案，未实现。日期：2026-11-28。
前置阅读：[`2026-11-27-her-pack-camera-effects.md`](2026-11-27-her-pack-camera-effects.md)（效果面板/命令语义）、
[`camera-fx-capability-and-licensing-review`](../superpowers/specs/2026-11-24-camera-fx-capability-and-licensing-review.md)（能力与许可审查）、
[`feature-camera-fx/README.md`](../../feature-camera-fx/README.md)（锚点纯函数与隐私红线）。

## 一句话结论

**不引入任何商业 AR SDK，也不整体搬入某个开源特效框架。** 把开源技术按现有四段管线插进
「识别 → 锚点 → cue → 渲染」的可替换插槽：识别继续用 MediaPipe（已在用），锚点继续用自研纯函数
（已钉死单测），触发继续走 `cmp.cue`，只有渲染层按效果种类增加 1–2 个后端；复杂特效的
**制作**用 Three.js 这类前端引擎做原型和参数，**运行时**用原生渲染落地。

## 现有基线（已核实代码，非设想）

| 段 | 现状 | 位置 |
|---|---|---|
| 识别 | CameraX + MediaPipe Tasks Vision 0.10.35（face 478 点 / pose lite / hand 21 点×2），设备内推理，帧不上传不落盘 | `feature-camera-fx/…/FxAnalyzer.kt` |
| 锚点 | 归一化坐标纯函数：`faceBounds`、`headTopAnchor`、`eyeTiltDegrees`、`CanvasMap`（FILL_CENTER + 前摄镜像），JVM 单测钉死 | `feature-camera-fx/…/FxAnchors.kt`、`CameraStickerPlacement.kt` |
| 触发 | 手势/教学/服务端统一走 `cmp.cue` → `emitStageGesture`，一条路径 | `CameraGestureCue.kt`、`protocol/PresentationCue.kt` |
| 渲染（本地预览） | Lottie 贴纸已上生产性 MVP；Lab 里有 Rive、GLES3 粒子、AGSL、Three.js(WebView) 对比后端 | `CameraStickerRenderer.kt`、`app/src/debug/…/fxlab/` |
| 推流 | LiveKit `livekit-android:2.29.0`，通话只发布音频轨（`LiveKitCompanionTransport`）；**摄像头画面当前不进入通话流** | `feature-companion/…/LiveKitCompanionTransport.kt` |

隐私红线沿用：效果面板区分「本地预览」与「发送给对方」两种能力，发送前须显式接入并告知（her-pack 设计已定此语义）。

## 各开源技术在本产品中的角色

### 1. MediaPipe（已集成）——识别层唯一候选

Apache-2.0，模型随包也是 Apache-2.0，离线无 GMS（华为 ADY-AL00 已验证）。它提供的是**关键点数据**，
不是画面效果——这正是我们要的解耦：贴纸、粒子、光环都只消费 `FrameFx`，不碰像素。
- **发挥点**：头饰/眼镜贴附（headTop + tilt）、手势触发 cue（已有 OPEN/FIST/PINCH）、
  后续「手指框」效果用四指尖坐标做裁切多边形。
- **不做什么**：不引入 MediaPipe 自带的 GPU 渲染 graph 来改画面；识别输出维持纯函数可测。

### 2. Three.js（MIT）——特效**制作与原型**引擎，不是运行时

实验室已证明它能在 WebView 里画出发光圆环和光扫，但 WebView 叠加不进相机输出，而且每次
`toDataURL` 合成代价高。所以它的正确角色是**创作侧**：
- 特效设计师/我们用 Three.js 写效果原型：调形状、粒子、着色器参数，直到画面满意。
- 满意后把参数**翻译**成运行时后端资产：Lottie JSON、AGSL 源、GLES 粒子参数表，或 GLSL 片段。
- `scripts/fx-lab/three-scene/` 的构建约定（bundle 随仓库提交）保留为原型工具链。
- Babylon.js（Apache-2.0）同类可选，不重复建两条原型工具链。

### 3. AGSL / GLES（已有实验）——运行时渲染主力

API 33+ 设备上 AGSL `RuntimeShader` 适合光环、呼吸、流光这类程序化效果，零素材零依赖；
GLES3 粒子渲染器已能跑摸头/挥手粒子。**这两者 + Lottie 就是运行时的主力三件套**，
不需要引入外部渲染框架。
- 缺口：AGSL 正向渲染未在 API 33+ 真机验证（已知待办）。

### 4. Rive（运行时 MIT）——状态机驱动的互动贴纸

Rive 运行时免费，Android SDK 21+；但**编辑器导出免费档带 splash、付费去 splash**，且样例素材
授权未确认（见 `raw-notes/README-fxlab-rive.md`）。定位：等我们自己用 Rive 画正式素材并确认
导出条款后再启用；现阶段不作选型依赖。

### 5. GPUPixel（Apache-2.0，但有一个未决点）——美颜/滤镜专用候选

若产品要**画面级美颜/滤镜**（磨皮、LUT、美白），自研 GPUImage 式管线成本高。GPUPixel 提供
现成的 C++/OpenGL ES 滤镜链 + 美颜 demo，仓库 Apache-2.0。
- **未决点**：其人脸关键点子组件（releases 提到的 MarsFace）在 Android 上以动态库链接，README
  未列其单独许可——**须先确认 MarsFace 许可再引入**；或只用它的滤镜链、关键点仍走我们的 MediaPipe。
- 状态：仅列候选，进 PoC 前先做许可核验。

### 6. Filament / SceneView（Apache-2.0）——原生 3D 特效的备用升级道

当需要真 3D 头饰（随头转动的立体王冠/猫耳带透视）时，Filament 是原生实时渲染引擎的正解，
SceneView 提供 Kotlin/Compose 封装。现阶段 2D 贴纸 + tilt 旋转已够用；此路线仅在产品明确要
立体贴附时启动，不提前引入。

### 7. Reanimate（MIT，Claude Code Skill）——制作辅助，不进产品

用来把参考动画（竞品效果、GIF、Lottie 文件）逆向成规格并重制到我们的资产体系，逐帧对比校验。
它是**开发工具**，运行时不引入。适合后续「照某个效果做一个」这类需求。

### 8. LiveKit `VideoFrameCapturer`——「对方也能看到特效」的入口

SDK ≥2.11.0 支持直接推送视频帧（我们在 2.29.0）。这是把本地预览升级为「发送」的技术入口：
CameraX 帧 → 渲染层合成 → capturer 推流。但**当前产品未发布视频轨**，这条链路属于新产品能力，
涉及隐私文案、带宽和发热验证，单独立项（见分期 M3）。

## 推荐组合（免商业授权费的完整栈）

```
识别      MediaPipe Tasks Vision（已集成，Apache-2.0）
锚点      自研纯函数（已钉死，不依赖任何库）
触发      cmp.cue（现有唯一路径）
运行时    Lottie（2D 贴纸） + AGSL（程序化光效，API 33+） + GLES3 粒子（已有）
          [候补] GPUPixel 滤镜链 —— 待 MarsFace 许可确认；关键点仍用 MediaPipe
          [候补] Filament —— 仅当需要真 3D 贴附
制作      Three.js 原型 → 导出运行时资产；Rive 自画素材（条款确认后）；Reanimate 复刻辅助
发送      LiveKit VideoFrameCapturer（M3 立项，含隐私/性能验收）
```

全部组件许可：MIT / Apache-2.0，无运行时分。Rive 编辑器条款、GPUPixel MarsFace 条款是仅有的
两个待确认点，均不阻塞上面的主力三件套。

## 分期

- **M1（收口现有）**：Lottie 贴纸维持现状；补 AGSL 在 API 33+ 设备的正向渲染验收；用 Three.js
  原型产出 2–3 个新 cue 特效（AGSL/GLES 版本）。不新增依赖。
- **M2（制作管线）**：固化「Three.js 原型 → 运行时资产」流程（参数表 + 校验测试）；手指框效果
  （纯 MediaPipe 关键点 + Canvas clip，自写实现，参考许可存疑仓库不复制代码）。
- **M3（发送特效）**：VideoFrameCapturer PoC：本地合成画面 → 推送视频轨；验收项 = 端到端延迟、
  发热/耗电、低端机帧率、隐私文案与开关（本地预览 ≠ 发送）。需要产品决策后才启动。
- **M4（可选）**：若产品确认要美颜 → GPUPixel PoC（许可确认后）；若要立体贴附 → Filament 评估。

## 性能预算（沿用许可审查结论）

Face 每帧、Pose/Hand 每 2 帧；V2279A 空场景观测 9.3–16.7 fps 只证明管线能跑。30 fps 目标
= 33.3 ms/帧总预算（识别 + 锚点 + 渲染 + 编码）。M3 推流前必须先有「有目标入镜」的真机帧率数据。

## 明确不做

- 不引入商业 AR SDK（腾讯 Effect、BytePlus、Banuba 等均已按「不收费」排除）。
- 不把 WebView(Three.js) 作为生产渲染后端。
- 不复制许可存疑仓库的代码/素材（`sophiamyang/finger-frame-effect-ai` 等）。
- 不增加第二条 cue 事件总线；不改变现有通话 UI。
- 默认不上传相机帧；任何「发送画面」能力必须显式开关 + 文案。
