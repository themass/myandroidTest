# feature-camera-fx —— 摄像头五官/肢体定位 spike

数字人产品的摄像头定位与叠加层 spike：前摄图像只在设备内存处理，debug Lab 提供线框与头顶 Lottie 贴纸预览；不进入 release 生产路径。选型背景见共享笔记
`sticker-motion-tech-options-20261123.md`。

## 数据去向（隐私红线，硬性）

- 摄像头帧**只在设备内存里流动**：CameraX ImageAnalysis → MediaPipe 推理 → 关键点 → Canvas 画完即弃。
- **不上传任何帧**（模块内没有任何网络调用，只依赖 CameraX / MediaPipe / Compose）。
- **不落盘**（不写文件、不截图缓存、不保存视频）。
- **不 log 人脸数据**（异常信息只有异常类名，无画面内容）。
- 首次使用弹明示同意文案：「画面仅在本机实时处理，不上传服务器、不保存、不记录任何人脸数据」。
- 关闭页面 = 分析线程 shutdown + native 模型 close，全部数据消失。

## 模型文件（assets/）

| 文件 | 用途 | 来源 | 许可 |
|---|---|---|---|
| `camerafx/face_landmarker.task` | FaceLandmarker 478 关键点 | https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task (3.7 MB) | Apache-2.0（MediaPipe models） |
| `camerafx/pose_landmarker_lite.task` | PoseLandmarker 33 关节（lite） | https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task (5.8 MB) | Apache-2.0（MediaPipe models） |
| `camerafx/hand_landmarker.task` | HandLandmarker 双手各 21 点 + 启发式手势（张/拳/捏） | https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task (~7.6 MB) | Apache-2.0（MediaPipe models） |

推理栈 `com.google.mediapipe:tasks-vision:0.10.35`（Apache-2.0），**纯离线**：
运行时不联网、不依赖 Google Play Services——华为 ADY-AL00（无 GMS）可用。

## 锚点纯函数（spike 的核心交付物）

`FxAnchors.kt`，全部归一化坐标（0..1，y 向下、正立画面语义），零 Android 依赖，
JVM 单测钉死（`FxAnchorsTest`）：

- `faceBounds(points)` —— 人脸外接矩形（贴 sticker 的定位框）。
- `headTopAnchor(outline, bounds, ratio)` —— 头顶锚点：轮廓最高点再向上抬 12% 框高（头饰挂点）。
- `eyeTiltDegrees(left, right)` —— 两眼（虹膜中心）连线倾角，sticker 随头旋转用；两点重合返回 0 不返回 NaN。
- `CanvasMap` —— 归一化 → 画布像素的 FILL_CENTER 映射 + **前摄镜像 x 翻转**（CameraX 1.4.2 前摄预览默认镜像，1.5 才有 MirroringMode API；关键点来自未镜像分析图，所以在归一化空间翻 x）。真机已验证对齐。

sticker 后续接法：`graphicsLayer { translationX/Y = map(headTop); rotationZ = tiltDeg }`，
贴纸本体（Lottie）挂在上面。

**已落地（竖切 MVP）：** `CameraFxScreen` 在头顶锚点叠 `fx/fx_pat_head.json`（Lottie），
位置/旋转由 [CameraStickerPlacement] + [CanvasMap] 与 HUD 一致。debug 通话页已接贴纸 PiP（`CompanionFaceCameraPrefs` 控制，默认关闭）。主 App 接线见 [camera-fx-app-lab-wiring.md](../docs/superpowers/specs/2026-11-23-camera-fx-app-lab-wiring.md)。

## 快速启动

`:feature-camera-fx` 是 Android library demo，不单独产出 APK；由 `:app` 的 `chatDebug` 变体承载。构建命令：

```bash
./gradlew :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug
```

APK 输出到 `app/build/outputs/apk/chat/debug/app-chat-debug.apk`。也可在 Android Studio 选择 `app` 模块、`chatDebug` build variant 后点击 Run。启动后从抽屉进入「实验室 → 摄像头定位 Demo」。入口只在 debuggable 包注册；`CameraFxActivity` 为 `exported=false`，请从抽屉启动，不要用外部 Activity 启动命令。

首次进入且尚未授予 CAMERA 时，先显示本机处理说明，再请求 Android 相机权限；同意并授权后开始预览。若权限此前已授予，进入 Lab 会直接预览。拒绝权限时不绑定相机，可点「重试」重新请求，或返回。

## 运行验收

| 能力 | 操作 | 预期结果 |
|---|---|---|
| Face | 正对前摄、光线充足，让脸完整入镜 | HUD `脸 1`；黄脸部折线、白框、绿眼线、品红头顶锚点和倾角 |
| Pose | 上半身进入画面 | HUD `姿态 1`；青色骨架（含腕到指尖扇区） |
| Hand / gesture（Lab） | 单手清晰入镜，分别张开、握拳、做拇指食指捏合 | HUD 显示手部橙色骨架和启发式手势；全屏 Lab 只显示分类，不向舞台发送 cue |
| Stage cue（通话 PiP） | 在 debug 通话页启用「人脸贴纸」PiP，再做张手/捏合/握拳 | 代码映射为张手 `wave`、捏合 `pat_head`、握拳不映射；真机端到端仍待验，见 [app/Lab 接线说明](../docs/superpowers/specs/2026-11-23-camera-fx-app-lab-wiring.md) |
| Sticker | 保持脸入镜，确认贴纸 chip 开启 | HUD 显示 `lab-b3 · 贴纸 OK`；本地 Lottie 跟随头顶锚点和倾角；遮脸后贴纸消失 |
| 无目标 / 失败 | 移出脸或手，或拒绝 CAMERA 权限 | 无脸/无手提示；拒绝权限时不启动相机分析。相机启动失败或连续识别错误会在 HUD 显示错误 |

「全开」恢复线框与贴纸默认项；「爱心/光弧」切换本地 Lottie 资产。通话 PIP 是单独的 debug 开关，默认关闭，不是全屏 Lab 的启动前置条件。

## 权限与失败边界

- 只有用户主动进入 debug Lab 且 CAMERA 权限已授予后，`CameraFxGrantedContent` 才绑定前摄预览和分析；退出页面会停止分析并关闭 Face/Pose/Hand native 模型。
- CAMERA 在 manifest 中声明为非必需硬件能力；本模块不声明麦克风、存储或网络权限。宿主 `:app` 可能因其他功能具备网络权限，但 Lab 不上传预览帧或关键点。数据留在设备内存，不落盘、不记录关键点日志。模型随 APK 提供，推理不依赖 Google Play Services。
- 分析帧链路（含 Face/Pose 初始化或推理）连续异常 5 次后停止分析并显示错误；Hand 初始化或推理异常单独 fail-soft：禁用 Hand，Face/Pose 仍可继续，HUD 中手数为 0。
- `fx … fps` 是成功处理帧的分析吞吐 HUD，约每秒刷新；不是相机预览刷新率，也不是某个单独 MediaPipe 模型的推理 FPS。Face 每个处理帧运行，Pose/Hand 每两帧运行，慢帧通过 `KEEP_ONLY_LATEST` 丢弃而不排队。

## 实测与未测

| 设备 / 状态 | 结果与边界 |
|---|---|
| vivo V2279A，chatDebug 1.1.0.71 | 现场记录的分析 HUD 为 9.3–16.7 fps；示例 `9.8 fps · 脸 0 · 姿态 0 · 手 0 · 630x1280`。这只证明无检测目标时管线与 HUD 可运行，不能当作 Face/Pose/Hand 有目标时的吞吐或识别质量。 |
| Face / Pose / Hand 目标入镜、手势 cue、贴纸跟随 | 尚无本轮有效目标入镜证据；QA 清单对应项目仍为待验。 |
| 华为 ADY-AL00 | 未测；当前没有本功能真机结果。 |

设备验收明细见 [camera-lab-device-checklist-20261123.md](../docs/qa/camera-lab-device-checklist-20261123.md)；能力、性能限制与许可评估见 [camera-fx-capability-and-licensing-review.md](../docs/superpowers/specs/2026-11-24-camera-fx-capability-and-licensing-review.md)。本文不把空场景帧率外推为目标识别性能。

**与主 app 接线表**：`docs/superpowers/specs/2026-11-23-camera-fx-app-lab-wiring.md`。

## 已知边界（spike 不做）

- 前摄镜像的 x 翻转靠 `CanvasMap.mirrorX` 对齐；不同设备和姿态下的逐帧配准仍需验收。
- 暗光、热稳态、耗电、多人脸/双手负载以及持续贴纸观感尚无本项目实测数据。
- Pose 使用 lite 模型；帧率与识别召回受设备性能、光照和入镜姿势影响。
