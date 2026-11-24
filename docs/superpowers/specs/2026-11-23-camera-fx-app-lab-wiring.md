# `:feature-camera-fx` 与主 App Lab 入口关系

> 读者：集成 camera-fx、维护 Home 抽屉或通话小窗的工程师。模块能力见 [`feature-camera-fx/README.md`](../../../feature-camera-fx/README.md)，手势和舞台边界见 [`camera-fx-stage-integration.md`](2026-11-23-camera-fx-stage-integration.md)。本文最初来自 `d715707d`，并按当前 `weifu-refactor` 主线校准。

## 一页图

```text
:app（chatDebug 等 debuggable 变体）
  settings.gradle → include ':feature-camera-fx'
  app/build.gradle → implementation project(':feature-camera-fx')
  Home 抽屉 → CameraFxLabGate → CameraFxActivity
  CompanionRoute ← CameraFxStickerPip（由 :app 注入，不加 companion→camera-fx 依赖）
                 → onCameraStageGesture → CompanionViewModel.emitStageGesture

:feature-camera-fx（Android Library）
  CameraFxActivity（exported=false）→ CameraFxScreen
  CameraX + FxAnalyzer（Face / Pose / Hand，MediaPipe VIDEO）
  FxAnchors / FxGestures / CameraGestureCue + JVM tests
  assets: camerafx/*.task + Lottie fx_pat_head
```

## Lab 入口接线

| 位置 | 作用 |
|---|---|
| `settings.gradle` | `include ':feature-camera-fx'` |
| `app/build.gradle` | `implementation project(':feature-camera-fx')` |
| `app/.../HomeScreen.kt` | debug 抽屉「实验室」→ `CameraFxLabGate.isEnabled` → `CameraFxActivity.start` |
| `app/src/main/res/values/strings_home.xml`（及 night） | `drawer_group_lab`、`drawer_entry_camera_fx` |
| `feature-camera-fx/.../AndroidManifest.xml` | 注册 `CameraFxActivity`，`exported="false"` |

`:feature-camera-fx` 不依赖 `feature-companion` 或 `dh-engines`。入口仅在 debuggable 包显示。release 包不显示 Lab 入口，Activity `exported=false`，外部应用不能启动它。

## 运行时路径

1. 用户从抽屉主动打开 Lab。
2. 展示相机处理说明；用户同意并授予 `CAMERA` 后才绑定相机。
3. `ProcessCameraProvider` 通过 `UseCaseGroup` / `ViewPort` 绑定前摄 Preview 与 ImageAnalysis。
4. `FxAnalyzer` 在设备内分析帧：Face 每帧，Pose/Hand 每 `poseEvery` 帧；结果写入 `FrameFx` / StateFlow。
5. Compose 画线框、头顶 Lottie 和 HUD（fps、脸/姿态/手数量、手势、倾角）。
6. 关闭页面时停止分析并释放模型。Lab 预览不上传帧、不落盘，详见模块 README 隐私说明。

## 通话小窗与舞台 cue

通话 PIP 与全屏 Lab 是两种独立入口。通话设置由 `CompanionFaceCameraPrefs.enabled` 保存，默认关闭；只在 debuggable Lab 开关可用时显示。`CompanionRoute` 从 `:app` 接收 `CameraFxStickerPip` 内容回调，因此 `:feature-companion` 无需依赖 `:feature-camera-fx`。

主线 `cbe826e1` 已完成 camera gesture 到舞台的代码接线：`CameraGestureCue` / `StageGestureEdge` 在手势稳定 2 帧后发一次 cue，并以 24 帧冷却防止连发；张手→`wave`，捏合→`pat_head`，握拳不映射。通话路由将 cue 交给现有 `CompanionViewModel.emitStageGesture`，沿既有舞台 gesture / `cmp.cue` 路径呈现。Lab 内头顶贴纸仍是本地锚点叠加，不等于舞台托盘事件。

协议 v2 对独立的感知快照规定：大于 2 KiB 的结构化 landmark 走 HTTP API，不走 LiveKit data channel。该协议规则不代表 camera-fx Lab 上传相机帧；当前 Lab 仍仅本机处理。

## 构建与验收

```bash
./gradlew :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug
```

真机验收时从抽屉进入 Lab，核对同意/权限、脸/姿态/手 HUD、橙色手骨架与头顶 Lottie。只有实际有人脸、手入画时才能验对应识别与跟随；空场景 fps 不能代替这些验收。

## 来源提交

| 提交 | 内容 |
|---|---|
| `d715707d` | 初版 app/lab wiring 文档与 README 指针 |
| `ab6b8ed2` / `57afc0cb` | HandLandmarker、Pose 腕至指尖与手势 HUD |
| `cbe826e1` | camera 手势 cue 接入通话 PIP / 舞台 gesture 路由 |
