# Camera-FX demo：能力、性能与许可审查（2026-11-24）

## 结论

1. 当前 `:feature-camera-fx` 已有可复现的 debug demo：CameraX 前摄 + MediaPipe Face/Pose/Hand、线框 HUD、头顶 Lottie 贴纸和本地 hand gesture cue。当前代码在脸上每帧推理，Pose/Hand 每两帧推理；JVM 纯函数测试和 app debug 构建通过。
2. 现有设备数据只能证明相机分析管线能运行。V2279A 上 9.3–16.7 fps 的截图轮次没有脸、姿态或手的检测目标，不能当作三模型有目标负载时的速度，也不能证明贴纸跟随、手识别或手势 cue 的体验。ADY-AL00 尚无本功能真机结果。
3. 适合当前 demo 的路线是设备内关键点 + Canvas/Compose 叠加层。手指框可用双手拇指/食指四点构造裁切多边形，在框内播放本地素材；不需要先接图像生成模型。
4. “真人单图换衣/帽”和“保留真人身体的换头”是独立的图像/视频生成工作流。先做用户主动提交的单图异步服务端验证，不进入实时预览帧流。尚无已完成代码、权重、人体解析器、训练数据和部署服务全链许可核准的候选方案。
5. 数字人自己的装扮继续以 [`wardrobe-spec.json`](../../../scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json) 为唯一事实源。当前帽子是 `pending-param`，要先在编辑器确认参数和显隐，再激活；不要将真人 VTON 结果伪装成 Live2D 槽位状态。

## 当前可运行演示

### 代码能力

- `:feature-camera-fx` 提供 CameraX + MediaPipe Tasks Vision 0.10.35。FaceLandmarker 每帧执行，PoseLandmarker 和 HandLandmarker 每 `poseEvery=2` 帧执行；模型在设备上推理。
- HUD 可画脸部、姿态和双手关键点，手势启发式分类为 OPEN / FIST / PINCH / UNKNOWN；贴纸可跟头顶锚点、倾角和前摄镜像坐标。
- 关闭页面时停止分析并关闭模型。当前模块说明相机帧不上传、不落盘；须由用户主动进入 debug Lab、阅读同意说明并授权 Camera 后才开始分析。详见 [`feature-camera-fx/README.md`](../../../feature-camera-fx/README.md) 与 [`camera-fx-app-lab-wiring.md`](2026-11-23-camera-fx-app-lab-wiring.md)。
- 主线 `cbe826e1` 已把 CameraGestureCue 接到通话路由的 `emitStageGesture`：稳定张手映射 `wave`，捏合映射 `pat_head`，拳头不触发舞台 cue。对应架构记录 [`camera-fx-stage-integration.md`](2026-11-23-camera-fx-stage-integration.md) 已同步更新为代码现状，并保留真机端到端验收待补的说明。

### 复现构建

本工作树基线为 `d715707d`。2026-11-24 在该基线上执行：

```bash
./gradlew :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug
```

结果：命令返回 `BUILD SUCCESSFUL`；`testDebugUnitTest` 为 `FROM-CACHE`，对应 XML 汇总 28 项、0 failures / 0 errors（测试报告时间为 2026-11-23），不是本轮重新执行的测试。debug APK 产物为 `app/build/outputs/apk/chat/debug/app-chat-debug.apk`。这验证构建成功并复用最近一次通过的纯函数测试结果，不等于当前设备性能或视觉验收。

### 真机证据与限制

| 设备 / 证据 | 已证实 | 尚未证实 |
|---|---|---|
| vivo V2279A，1.1.0.71，2026-11-23；主线验收记录 `docs/qa/camera-lab-device-checklist-20261123.md`，commit `26948d43`，后续补记 `cdd2126a` | 冷启动、Lab 抽屉入口、同意/权限流、相机管线 HUD、9.3–16.7 fps、lab-b3 标记、无脸 fail-soft、控件切换、退出释放均 PASS | 该轮镜头没有人脸/人体/手；脸部关键点、手橙线/手势、头顶贴纸跟随与倾角均待正对镜头实测。设备随后由其他通话验收占用，worker 没有抢占或继续点按 |
| V2279A 更早轮次 | `fx 9–16 fps · 602x1280` 管线 HUD 可刷新 | 同样没有目标检测，不能作为完整识别吞吐结论 |
| 华为 ADY-AL00 | 无 | 设备当晚未上线；无模型、相机、热稳定性或耗电数据 |

注意：9.3–16.7 fps 是本项目在 V2279A 某一 debug 包、无目标入镜时的整条相机分析 HUD 观测。它包括采集和空场景分析开销，但不能外推至多人/双手、手势事件、连续贴纸和热稳态。它也不是“HandLandmarker 独立推理耗时”。

### 官方基准（不可外推为本项目实测）

Google MediaPipe 官方 Pixel 6 基准：Hand Landmarker Full FP16 CPU 平均 17.12 ms，GPU 12.27 ms；Selfie Segmenter square CPU 33.46 ms，GPU 35.15 ms。前者只测手任务，后者只测分割任务，设备、模型与调用路径也不同于本 app。单看分割 CPU 数字已接近 30 fps 的 33.3 ms 单帧总预算；不能将这些数直接相加，也不能声称本项目可达实时分割。

| 帧率目标 | 每帧总预算 | 参考用途 |
|---:|---:|---|
| 60 fps | 16.7 ms | 低延迟叠加；当前无证据 |
| 30 fps | 33.3 ms | 常见实时交互目标；三模型未证明达到 |
| 15 fps | 66.7 ms | 当前低端设备的观察量级；并非产品验收指标 |

## 方案评估

### A. 人脸 / 人体 / 手指框实时效果

**可做：设备内关键点 + 轻量合成。** Face/Pose/Hand 已在模块中；手指框可用左右手拇指与食指的四个指尖坐标，做归一化坐标映射、四边形平滑和 Canvas clip，在多边形内部显示本地视频/贴图。坐标、素材和状态留在设备内存，沿用显式进入 Lab + 同意/权限闸门。这基本是几何合成，不需要扩散模型，不需要持续上传相机帧，也避免把 sample repo 作为依赖。遮挡、两手交叉、四边形翻转、低光漏检和持续帧率仍要单独做真机验收。

`finger-frame-effect-ai` 名称对应多个仓库，许可证并不一致：

- [`aithink001/finger-frame-effect-ai`](https://github.com/aithink001/finger-frame-effect-ai)：README 声明 MIT；媒体资产可能另受 `ATTRIBUTIONS.md` 条款约束。其手指区域可用普通 MediaPipe landmark + canvas polygon clipping 复现。
- [`sophiamyang/finger-frame-effect-ai`](https://github.com/sophiamyang/finger-frame-effect-ai)：页面未发现明确 LICENSE；不应复制代码或媒体。
- AI 生成不是本地裁切本身。其独立生成流程要求用户主动选取并上传 4–10 秒视频，且使用外部生成 API；不应把它和本地手指框预览混作同一数据流。

建议先自有实现几何裁切与本地素材播放，不引入上述仓库代码或外部 API。

### B. 数字人换衣 / 换帽

**Live2D 数字人：继续走已有槽位抽象。** `top` 有 active 变体；`hair.hat` 和 `hair.ribbon` 目前 `pending-param`。Live2D exp3 只能切换 moc3 内已有 ArtMesh/参数，不能凭空生成帽子网格。必须在 Cubism 编辑器验证确切 `(parameterId, value)`、可见性和模型版本，再更新 spec 并经生成器/测试闭环。

**真人相片：独立 VTON 服务。** FASHN VTON 1.5 是可继续做服务端技术验证的候选：仓库声明 Apache-2.0；模型仓库约 2 GB，人体解析器约 244 MB；公开接口按人物图 + 衣物图处理 tops/bottoms/one-pieces，没有实时视频入口或公开时延 / VRAM 保证。第三方 parser 使用 NVIDIA SegFormer Source Code License，商业授权范围在本次审查中未定；模型训练数据来源/再分发权也未完整确认。因此不能据主仓库 Apache 就判定产品合规。

CatVTON 和 IDM-VTON 仓库声明 CC BY-NC-SA 4.0；VITON-HD 与 DressCode 数据也标有非商业研究限制，不能当作商业产品的默认依赖。帽子可以先评估一次性静态照片上的分割 + 贴图；它与全身 VTON 的衣物形变不是同一个问题。

### C. 换头并保留真人身体

这是人脸身份生成、头部遮罩/融合、身体与背景保留、时序一致性共同组成的高风险质量点。作为可行路径，先做用户主动提交的一张静态照片或短片云端异步 POC；不要在预览中连续逐帧生成或默认上传视频。

本次核查没有找到可直接推荐的“代码 + 预训练权重 + 数据 + 部署 API”全套商用许可方案：SimSwap 是 CC BY-NC 4.0；InsightFace 代码 MIT，但其预训练模型限非商业研究，InSwapper 需联系权利方授权。HunyuanVideo-Avatar 是音频驱动角色视频生成系统，不等于保留用户原身体像素的 head-only compositor；其社区许可有地区和规模限制，且官方推荐推理环境为 Linux + CUDA，704×768/129 帧最低 24 GB GPU 显存（注明 very slow），推荐 96 GB。它不是本地手机实时路线。

## 许可与数据核对表

以下为公开仓库/模型卡的初步核对，不是法律意见；必须分别审查代码、checkpoint、解析器、训练/评测数据、生成素材和在线服务条款。

| 项目 | 本次看到的公开条款 | 结论 |
|---|---|---|
| MediaPipe Tasks Vision + 当前随包 face/pose/hand 模型 | Apache-2.0；项目 README 记录离线本机使用 | 当前 demo 可继续 |
| `aithink001/finger-frame-effect-ai` | README 声明 MIT；media 有单独 attribution 提示 | 参考实现可自写；若复制媒体先读对应 notice |
| `sophiamyang/finger-frame-effect-ai` | 未找到 LICENSE 声明 | 不复制代码或媒体 |
| FASHN VTON 1.5 | repo/Hugging Face 声明 Apache-2.0；parser 是 NVIDIA Source Code License；训练数据/端到端服务条款尚未闭环 | 仅可做隔离 POC，生产使用待法务和数据来源核验 |
| CatVTON / IDM-VTON | CC BY-NC-SA 4.0 | 不用于商业产品 |
| VITON-HD / DressCode | CC BY-NC 相关限制；DressCode 明确为非商业学术用途 | 不用于商业训练/评测/发布，除非另获授权 |
| SimSwap | CC BY-NC 4.0 | 不用于商业产品 |
| InsightFace | 代码 MIT；预训练模型限非商业研究；InSwapper 需单独联系授权 | 未获授权前不采用权重 |
| HunyuanVideo-Avatar | Tencent Hunyuan Community License；区域、使用政策及规模门槛限制；依赖高显存 NVIDIA 环境 | 不作为当前手机实时或保留原身体的方案 |

## 数据流 / 产品边界

- Lab 默认不进入、不启动 Camera；只有用户主动打开该功能、完成说明/权限后才开始分析。当前 demo 只在设备内存中处理，不写入或上传视频帧。
- 未来在线 VTON 或 face/head edit 必须是独立的明确动作：用户拍照/选片 → 确认上传与用途 → 后端排队处理 → 返回预览 → 提供删除/保留说明。默认不把实时相机流送云端。
- 手势 cue 仅是交互输入；舞台演出仍按现有 `cmp.cue` / `emitStageGesture` 路由，不增加一条 camera 私有事件总线。
- 本报告没有增加新的第三方依赖、模型文件或数据集，也没有改生产 UI。

## 复现与来源

### 仓库证据

- 基线：`d715707d`；本次工作树。
- 构建/测试：`./gradlew :feature-camera-fx:testDebugUnitTest :app:assembleChatDebug` → `BUILD SUCCESSFUL`；28 tests, 0 failures/errors。
- V2279A 主线设备清单：`docs/qa/camera-lab-device-checklist-20261123.md`，commit `26948d43`；退出项补验 commit `cdd2126a`。完整脸/手入镜验收仍待设备窗与用户正对镜头约 30 秒。
- 当前 analyzer：[`FxAnalyzer.kt`](../../../feature-camera-fx/src/main/java/com/openapi/feature/camerafx/FxAnalyzer.kt)；手势词表/边沿状态机在主线 commit `cbe826e1` 的 `CameraGestureCue.kt`。
- 单一事实源：[`wardrobe-spec.json`](../../../scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json)。

### 外部来源

- [MediaPipe Hand Landmarker benchmarks](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker)
- [MediaPipe Image Segmenter benchmarks](https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter)
- [aithink001 finger-frame-effect-ai](https://github.com/aithink001/finger-frame-effect-ai)；[sophiamyang 同名项目](https://github.com/sophiamyang/finger-frame-effect-ai)
- [FASHN VTON 1.5 repository](https://github.com/fashn-AI/fashn-vton-1.5)；[FASHN model card](https://huggingface.co/fashn-ai/fashn-vton-1.5)
- [FASHN Human Parser](https://github.com/fashn-AI/fashn-human-parser)；[上游 SegFormer license](https://github.com/NVlabs/SegFormer/blob/master/LICENSE)
- [CatVTON](https://github.com/Zheng-Chong/CatVTON)；[IDM-VTON](https://github.com/yisol/IDM-VTON)
- [VITON-HD](https://github.com/shadow2496/VITON-HD)；[DressCode dataset license](https://github.com/aimagelab/dress-code/blob/main/LICENSE)
- [SimSwap](https://github.com/neuralchen/SimSwap)；[InsightFace model license notice](https://github.com/deepinsight/insightface/blob/master/README.md)
- [HunyuanVideo-Avatar](https://github.com/Tencent-Hunyuan/HunyuanVideo-Avatar)；[license](https://github.com/Tencent-Hunyuan/HunyuanVideo-Avatar/blob/main/LICENSE)
