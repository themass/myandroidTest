# `:feature-camera-fx` 与主 App Lab 入口关系（2026-11-23）

> 读者：集成 weifu、写通话 PiP、或改 Home 抽屉的工程师。  
> 模块自述见 `feature-camera-fx/README.md`；舞台/通话分期见同目录 `2026-11-23-camera-fx-stage-integration.md`。

## 一页图

```
┌─────────────────────────────────────────────────────────────┐
│  :app（chatDebug 等 debuggable 变体）                        │
│  settings.gradle → include ':feature-camera-fx'              │
│  app/build.gradle → implementation project(':feature-camera-fx')│
│  HomeScreen 抽屉尾部 → CameraFxLabGate → CameraFxActivity    │
└──────────────────────────┬──────────────────────────────────┘
                           │ 仅 startActivity；无 feature-companion 依赖边
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  :feature-camera-fx（Android Library）                       │
│  CameraFxActivity（exported=false）→ CameraFxScreen          │
│  CameraX + FxAnalyzer（Face / Pose / Hand MediaPipe VIDEO）  │
│  纯函数 FxAnchors / FxGestures + JVM tests                   │
│  assets: camerafx/*.task + 可选 Lottie fx_pat_head          │
└─────────────────────────────────────────────────────────────┘
```

## 接线清单（改模块或入口时只动这些）

| 位置 | 作用 |
|------|------|
| `settings.gradle` | `include ':feature-camera-fx'` |
| `app/build.gradle` | `implementation project(':feature-camera-fx')` |
| `app/.../HomeScreen.kt` | debug 抽屉「实验室」→ `CameraFxLabGate.isEnabled` → `CameraFxActivity.start` |
| `app/src/main/res/values/strings_home.xml`（及 night） | `drawer_group_lab`、`drawer_entry_camera_fx` |
| `feature-camera-fx/.../AndroidManifest.xml` | 注册 `CameraFxActivity`，`exported="false"` |

**纪律**：`:feature-camera-fx` **不** `implementation` `feature-companion` / `dh-engines`。二期通话贴纸复用资产走 prefs + composable 引用，不反向 import companion 业务类。

## Lab 入口闸门

`CameraFxLabGate` 与 `DhLabGate` 同判定：`ApplicationInfo.FLAG_DEBUGGABLE`。

- **debug 包**（如 `assembleChatDebug`）：抽屉出现「摄像头定位 Demo」。
- **release 包**：无 UI 入口；Activity 仍可通过显式 Intent 调起（未 export，外部不可深链）。

## 运行时路径

1. 用户点抽屉项 → `CameraFxActivity`（全屏、竖屏）。
2. 明示同意 + `CAMERA` 权限 → `CameraFxScreen`。
3. `ProcessCameraProvider` + `UseCaseGroup`/`ViewPort` 绑前摄 Preview + ImageAnalysis。
4. `FxAnalyzer` 每帧（脸每帧；pose/hand 每 `poseEvery` 帧）→ `FrameFx` → StateFlow。
5. Compose：`drawFx` 线框 + 可选 Lottie `stickerAtHeadTop`；HUD 显示 fps / 脸·姿态·手 / 手势 / 倾角。

## 与 `:app` 其它能力的边界

| 能力 | 关系 |
|------|------|
| 陪伴通话 `CompanionCallScreen` | 另线：`CompanionFaceCameraPrefs` + 小窗相机（见 stage-integration）；**不**与 lab Activity 互斥 |
| 舞台 Lottie `emitStageGesture` | 舞台特效仍走 cmp.cue；lab 内贴纸默认**本地**跟随锚点，不冒充舞台托盘 |
| MediaPipe 感知上行 | 协议 v2：大 landmark 走 **HTTP**，不走 LiveKit 数据通道（见 `companion-interaction-protocol-v2.md` D11） |

## 构建与验收

```bash
./gradlew :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug
```

真机：抽屉「实验室 → 摄像头定位 Demo」；期望脸/姿态/手 HUD + 橙手骨架 + 头顶 Lottie（有脸时）。

## 历史 tip 参考

| 提交 | 说明 |
|------|------|
| `e61387c7` 起链 | 模块 spike |
| `a11217fa` | Lottie headTop MVP |
| `ab6b8ed2` | Pose 腕连指尖 + HandLandmarker + 手势 HUD |
| weifu `57afc0cb` | 合入手势（merge 提交名可能不同） |

维护：架构有变时同步改本文件与 `2026-11-23-camera-fx-stage-integration.md` 的边界表。
