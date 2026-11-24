# 摄像头跟踪 + 舞台特效 · 架构（2026-11-23）

## 目标

用户在**主动打开**摄像头后：本机 MediaPipe → 五官/姿态锚点 →（可选）线框 HUD → 与舞台 **同一套 Lottie 词表** 的贴纸/特效；**不**自动小窗、**不**另造事件总线。

## 边界

| 层 | 模块 | 职责 |
|----|------|------|
| 感知 | `:feature-camera-fx` | CameraX + Face/Pose、纯函数锚点、**仅设备内存** |
| 呈现选项 | `CameraFxOverlayPolicy` | wireframe / stickers / assetId（纯数据） |
| 舞台特效 | `feature-companion.fx` | `LottieFxLayer` + `StageEffectMapper` ← **gesture 链** |
| 通话 UI | `CompanionRoute` / `CompanionCallScreen` | 何时开相机（用户意图）；**`cameraAvailable`** = LiveKit 视频轨（另线） |

## 纪律

1. **触发特效**：舞台动效仍只走 `emitStageGesture` / cmp.cue → `gestureSeq`；相机侧贴纸**默认**本地 asset 跟随锚点，**不循环冒充**舞台托盘。
2. **不开相机不跑分析**：`CameraFxGrantedContent` 仅在 composable 进入树且已授权时 bind lifecycle（无全局后台相机）。
3. **无 pointerInput** 在相机叠层 / LottieFxLayer（捏合仲裁一处）。
4. **手势**：lab 已画 Pose 腕→指尖扇区 + HandLandmarker 双手 21 点（橙）+ HUD 张/拳/捏启发式；`StageGestureEdge` 稳定 2 帧、冷却 24 帧后由 `CompanionRoute` 调用 `emitStageGesture`：张手→`wave`、捏合→`pat_head`、握拳不触发。代码已接通（主线 `cbe826e1`）；手/脸入镜后的真机视觉与舞台端到端验收仍待设备窗口。
5. **入口**：全屏 lab = 抽屉实验室；通话内通过 `CompanionFaceCameraPrefs.enabled` 开关小窗，默认 **false**，且只在 debuggable lab 启用；CameraFx 内容由 `:app` 注入，保持 `:feature-companion` 不反向依赖 `:feature-camera-fx`。

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
- [x] 通话 debug 菜单「人脸贴纸」接 `labFaceCameraPip` + prefs（主线 `cbe826e1`；真机体验另验）
- [x] Pose 腕连指尖（默认开）；可选开关 **二期**
- [x] Hand landmark + lab HUD 手势启发式
- [x] Hand 手势 → `emitStageGesture`（张→wave、捏→pat_head；主线 `cbe826e1`；真机链路验收待补）

## 分工（camera-fx 线与 lottie-fx-mvp，2026-11-23）

- **camera-fx 线**：cue/锚点单一事实源 = `CameraGestureCue`（词表映射 + 边沿检测 + 单测）；
  `CameraFxGrantedContent(onStageGesture)` / `CameraFxStickerPip(onStageGesture)` 由组合根注入通话页。
  **礼物层本体不在这里做。**
- **lottie-fx-mvp**：通话页礼物层（`LottieFxLayer` 侧 ff / 资产 / 渲染策略），
  消费同一 gesture 词表（`StageEffectMapper`）；不重复实现相机手势映射。新 cue id 两边同词表扩。
