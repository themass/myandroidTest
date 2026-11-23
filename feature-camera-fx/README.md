# feature-camera-fx —— 摄像头五官/肢体定位 spike

数字人产品的「打开摄像头 → 识别五官和肢体 → 贴 sticker」能力的第一步：**只做定位与可视化**，
不渲染 sticker、不进生产路径。选型背景见共享笔记
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

## 运行方式

debug 包：抽屉「实验室」分组 → 摄像头定位 Demo。release 包没有这个入口
（与 `DhLabGate` 同样的 debuggable 判定，入口注册在 `:app` 的 HomeDrawer，见 `CameraFxLabGate`）。

## 已知边界（spike 不做）

- 前摄镜像的 x 翻转靠 `CanvasMap.mirrorX` 常量对齐，未做逐帧配准验证（真机目测对齐）。
- `detectForVideo` 同步调用在单线程 executor；帧率见屏上 fps 计数（ADY-AL00 实测值在交接笔记）。
- 无红外/暗光优化；模型 lite 档，精度换速度。
