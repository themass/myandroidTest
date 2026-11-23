# 摄像头跟踪 + 舞台特效 · 架构（2026-11-23）

## 目标

用户在**主动打开**摄像头后：本机 MediaPipe → 五官/姿态锚点 →（可选）线框 HUD → 与舞台 **同一套 Lottie 词表** 的贴纸/特效；**不**自动小窗、**不**另造事件总线。

## 边界

| 层 | 模块 | 职责 |
|----|------|------|
| 感知 | `:feature-camera-fx` | CameraX + Face/Pose/Hand、纯函数锚点、**仅设备内存**；与 app 接线见 `2026-11-23-camera-fx-app-lab-wiring.md` |
| 呈现选项 | `CameraFxOverlayPolicy` | wireframe / stickers / assetId（纯数据） |
| 舞台特效 | `feature-companion.fx` | `LottieFxLayer` + `StageEffectMapper` ← **gesture 链** |
| 通话 UI | `CompanionRoute` / `CompanionCallScreen` | 何时开相机（用户意图）；**`cameraAvailable`** = LiveKit 视频轨（另线） |

## 纪律

1. **触发特效**：舞台动效仍只走 `emitStageGesture` / cmp.cue → `gestureSeq`；相机侧贴纸**默认**本地 asset 跟随锚点，**不循环冒充**舞台托盘。
2. **不开相机不跑分析**：`CameraFxGrantedContent` 仅在 composable 进入树且已授权时 bind lifecycle（无全局后台相机）。
3. **无 pointerInput** 在相机叠层 / LottieFxLayer（捏合仲裁一处）。
4. **手势**：lab 已画 Pose 腕→指尖扇区 + HandLandmarker 双手 21 点（橙）+ HUD 张/拳/捏启发式；**emitStageGesture 分类→舞台 fx 仍为二期**。
5. **入口**：全屏 lab = 抽屉实验室；通话内 = **debug 开关** `CompanionFaceCameraPrefs.enabled`（默认 **false**），将来可与 `onToggleCamera` 对齐。

## 数据流（v2 通话内）

```
用户开「人脸贴纸」开关 → 授权 CAMERA
  → CameraFxGrantedContent(overlayPolicy)
  → FrameFx → stickerAtHeadTop + 可选 drawFxWireframe
舞台托盘 pat_head / wave
  → emitStageGesture → LottieFxLayer（数字人上固定锚点）
```

相机贴纸与舞台 Lottie **可同 assetId**，坐标系不同（相机 FILL_CENTER vs 舞台 Norm 锚点）。

## 特效目录（仓库自制）

| assetId | 舞台 | 相机头贴（可选） |
|---------|------|------------------|
| `fx_pat_head` | pat_head / 特效测试 | 默认 |
| `fx_wave` | wave 托盘 | 二期手腕锚点 |
| `fx_connected` | 接通氛围 | 不用在相机 |

选择器 UI：**二期**；v1 lab 内 `CameraFxLabPreferences`（assetId + showWireframe）。

## 实现阶段

- [x] 关通话页自动小窗
- [x] `CameraFxOverlayPolicy` + lab SharedPreferences
- [ ] 通话 debug 菜单「人脸贴纸」接 `labFaceCameraPip` + prefs
- [x] Pose 腕连指尖（默认开）；可选开关 **二期**
- [x] Hand landmark + lab HUD 手势启发式
- [ ] Hand/Pose 手势 → `emitStageGesture`（舞台托盘）
