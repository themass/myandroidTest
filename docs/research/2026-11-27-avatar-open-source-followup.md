# 数字人、摄像头特效和通话视频：开源项目补充调研

2026-11-27；本报告补充 [第一轮全周期调研](2026-11-27-avatar-open-source-survey.md)，重点寻找此前未列出的项目及其与现有工程的具体连接。所有候选均为公开资料筛选，尚未逐一安装、构建或进行真机性能、视觉与授权验收；许可证以最终固定的版本及各资产文件为准。

## 先看结论

| 优先级 | 项目 | 针对什么问题 | 第一项验证 |
|---|---|---|---|
| P0 | [GPUPixel](https://github.com/pixpark/GPUPixel) | Android 实时美颜、肤色及 GPU 滤镜 | 固定版本和模型许可，用同一相机素材比较画质、ABI、帧时间及温升；需自行验证 CameraX 和发送帧适配。 |
| P0 | [Character Studio](https://github.com/M3-org/CharacterStudio) | VRM 衣服部件的组合、遮挡与导出 | 离线用一套授权角色和两件兼容服装试验；核对表情、骨架和 VRM 扩展，再考虑映射 `wardrobe-spec`。 |
| P0 | [HeadAudio](https://github.com/met4citizen/HeadAudio) | 音频驱动三维角色口型 | 把输出 viseme 显式映射到 VRM 表情，以中文语音测试同步与观感；验证 Android WebView 离线 AudioWorklet。 |
| P0 | [Perfetto](https://github.com/google/perfetto) | 找到预览、识别和特效带来的帧调度及 CPU 开销 | 同设备、分辨率和场景采集效果开关的系统轨迹；记录前台 P50/P95、线程与温升。 |
| P1 | [RootEncoder](https://github.com/pedroSG94/RootEncoder) | 相机 → OpenGL → 编码器实现参考 | 验证可否复用处理边界，避免和现有 CameraX/LiveKit 同时占用相机或 EGL。 |
| P1 | [AndroidX Media3](https://github.com/androidx/media) Effect / Transformer | 文件预览、视频滤镜及导出 | 用与工程一致的 1.10.0 评估效果；独立确认实时 CameraX 和 LiveKit 可否接入。 |
| P1 | [Android GPU Inspector](https://github.com/google/agi)、[RenderDoc](https://github.com/baldurk/renderdoc)、[Spector.js](https://github.com/BabylonJS/Spector.js) | GPU 合成、draw call、纹理与 WebGL 排查 | 先确认目标设备、驱动、图形 API 和 WebView 调试条件，再捕获问题帧。 |
| P1 | [libyuv](https://chromium.googlesource.com/libyuv/libyuv/) | YUV/RGB 格式桥接、旋转及缩放 | 先检查现有媒体依赖有无等效实现；在真实 CameraX 格式上核对 stride、色彩、旋转和拷贝开销。 |

## 项目现状和实际接口

- `feature-camera-fx` 的 debug spike 已有 CameraX → MediaPipe → 锚点 → Canvas/Lottie；README 记录当前帧仅本机处理、不上传、不落盘。其空场景吞吐记录不能外推到有目标时的识别质量或性能。通话 PiP 和 gesture cue 的真机端到端仍待验证。
- `feature-companion/build.gradle` 锁定 LiveKit Android **2.29.0**。该版本上游 [LocalParticipant](https://github.com/livekit/client-sdk-android/blob/v2.29.0/livekit-android-sdk/src/main/java/io/livekit/android/room/participant/LocalParticipant.kt) 提供接受自定义 `VideoCapturer` 的 `createVideoTrack` 和 `publishVideoTrack`；[VideoFrameCapturer](https://github.com/livekit/client-sdk-android/blob/v2.29.0/livekit-android-sdk/src/main/java/io/livekit/android/room/track/video/VideoFrameCapturer.kt) 可向 observer 推入 WebRTC `VideoFrame`。此项是源码接口核对，不是接通证明；线程、帧持有与释放、颜色格式、延迟、音画同步和对端可见均需验证。当前 transport 搜索到麦克风发布控制，未找到上述自定义视频轨调用。
- 向对端发送摄像头画面会改变现有本地处理的数据边界；若实现，需明确产品用途、用户同意和相应数据处理说明。不要把本地预览叠加说成已推流。
- `gradle/libs.versions.toml` 已定义 Media3 **1.10.0** 及播放器模块；不能由此认定 `media3-effect` / `media3-transformer` 已引入。
- 仓库已有 TalkingHead 离线样件与源码，也已有 VRM WebView Lab；因此 TalkingHead 属于已有能力而非新发现。WebView 加载、Worklet 离线资源、模型与 bridge 仍应基于固定版本检查。

## 角色、动作与口型

| 项目 | 可用方式 | 许可与风险 |
|---|---|---|
| [Character Studio](https://github.com/M3-org/CharacterStudio) | VRM 角色部件组装；先作为离线制作工作台，确认可输出兼容模型，再研究抽取组件组合思路。 | MIT；模型和衣服资产另审。跨体型、权重、遮挡、morph 和扩展兼容均无保证；应用继续以 `wardrobe-spec` 为唯一事实源。 |
| [MPFB2](https://github.com/makehumancommunity/mpfb2) | Blender 生成人物、衣服和骨架，配合已有 VRM 插件建立离线产线。 | 代码 GPLv3；[仓库资产声明 CC0](https://github.com/makehumancommunity/mpfb2/blob/master/LICENSE.ASSETS.md)，外部服饰和纹理另审。偏写实，风格适配成本需评估。 |
| [Rigger](https://github.com/asu37/Rigger) | 离线骨骼动画重定向和烘焙，复用已授权动作。 | GPL-3.0-or-later；脚滑、手部和骨架比例需要修正，VRM/VRMA 导出不保证保真。 |
| [VideoIntoBlenderModel](https://github.com/s0ck3t/VideoIntoBlenderModel) | 从授权单人视频制作离线动作草稿，Blender 纠错后迁移至目标角色。 | 代码 Apache-2.0；上游素材许可说明需逐项核对。遮挡、抖动、面部和手指质量待测，不是手机实时捕捉。 |
| [HeadAudio](https://github.com/met4citizen/HeadAudio) | 音频到 viseme；映射到 VRM `aa/ih/ou/ee/oh` 等表达，再以真实播放时钟控制。 | MIT；不是中文音素识别。AudioWorklet、静音、打断、延迟和中文观感需测试；映射会丢失信息。 |
| [LighterSync](https://github.com/andersdn/LighterSync) | 根据已准备好的音频与台词生成口型关键帧，用于欢迎语等固定内容。 | MIT；文档的词典工作流针对英语，中文效果未知；不是实时通话方案，词典及数据另审。 |
| [glTF Sample Viewer](https://github.com/KhronosGroup/glTF-Sample-Viewer) / [Sample Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) | 用同一 GLB 对照材质、动画与 morph，分辨资产问题和渲染器问题。 | Viewer Apache-2.0；每个样本资产各自授权。glTF 对照不能证明 VRM、MToon 和弹簧骨骼正确。 |

## 摄像头、滤镜与视频帧

| 项目 | 可用方式 | 许可与风险 |
|---|---|---|
| [GPUPixel](https://github.com/pixpark/GPUPixel) | C++/OpenGL ES 实时滤镜与美颜；适合与现有 MediaPipe 定位方案组合或做独立对照。 | Apache-2.0；模型、依赖和样例素材另核。验证 Android ABI、16 KB 页对齐、EGL、内存生命周期与真实美颜画质，不能假定零拷贝。 |
| [GPUPixel Android fork](https://github.com/webrtcsdk/gpupixel-android) | 研究 RGBA/I420 回调接口的历史实现。 | 仓库标 MIT，不覆盖全部上游与资源；README 的 I420 说明和示例有矛盾，须核源码，较久未更新，不作为优先生产依赖。 |
| [libfacedetection / YuNet](https://github.com/ShiqiYu/libfacedetection) | 人脸框检测备选，可对比轻量预检测。 | 仓库许可证文本 BSD-3-Clause、GitHub 标记 NOASSERTION；权重另核。它不是稠密关键点、跨帧跟踪或表情驱动的完整替代。 |
| [face-ml](https://github.com/ducanh3005/face-ml) | 参考 Android/TFLite Face Mesh 数据流。 | Apache-2.0；较久未更新，模型许可和系统兼容性需核验，替换现有 MediaPipe Tasks 的收益未知。 |
| [Media3 Effect / Transformer](https://developer.android.com/media/media3/transformer/transformations) | GL 效果组合、文件编辑/导出及播放器预览。 | AndroidX Apache-2.0；各模块版本一致，部分 API 不稳定；实时 CameraX 和 LiveKit 输出需独立适配。 |
| [libyuv](https://chromium.googlesource.com/libyuv/libyuv/) / [Android wrapper](https://github.com/crow-misia/libyuv-android) | CPU 端帧格式转换、缩放与旋转。 | wrapper Apache-2.0；libyuv 本体、专利文件和 ABI 按固定版本核对。逐帧复制和 JNI 可能产生明显开销。 |
| [RootEncoder](https://github.com/pedroSG94/RootEncoder) | 研究相机输入、GL 滤镜、文字图片覆盖与编码流程。 | Apache-2.0；完整采集推流栈与现有模块可能冲突，先验证可抽取边界和相机资源竞争。 |
| [CameraFilter](https://github.com/nekocode/CameraFilter) | 旧 Android 示例里的像素化、边缘等 shader 参考。 | Apache-2.0；项目长期未更新，部分 shader 源头和归属需逐项核对。 |
| [Grafika](https://github.com/google/grafika) | 学习 SurfaceTexture、EGL、相机与编码时序。 | Apache-2.0；已归档、实验性质，适合作参考而非生产 SDK。 |

## 特效制作、性能诊断和回归

| 项目 | 用途与边界 |
|---|---|
| [Proton](https://github.com/drawcall/Proton)（MIT） | Web 粒子发射器，可与 three.quarks 对照爱心、花瓣和背景；WebView 透明合成及移动端性能待测。 |
| [PixiJS Particle Emitter](https://github.com/pixijs-userland/particle-emitter)（MIT） | 配置驱动的 2D 粒子；v5 文档推荐 PixiJS 6，版本兼容需实测；单为粒子引入完整 Pixi 的成本需比较。 |
| [libGDX](https://github.com/libgdx/libgdx)（Apache-2.0） | 跨平台游戏框架及粒子工具；图形上下文、透明覆盖、包体和生命周期意味着它不一定适合轻量特效。 |
| [LibreSprite](https://github.com/LibreSprite/LibreSprite)（GPL-2.0） | 桌面逐帧像素动画创作；导出素材与角色风格是否匹配需验收。 |
| [oxipng](https://github.com/oxipng/oxipng)（MIT） | 无损压缩 PNG 资源；减少文件大小不意味着减少解码后的纹理内存。 |
| [Perfetto](https://github.com/google/perfetto)（Apache-2.0） | 采集 Android 线程、调度与帧轨迹；指标可用性随设备不同，不证明视觉正确。 |
| [Android GPU Inspector](https://github.com/google/agi)（Apache-2.0） / [RenderDoc](https://github.com/baldurk/renderdoc)（MIT） | 捕获 GPU 帧排查透明、混合与纹理；需核查设备、驱动和 API 支持，单帧不能代表长时间性能。 |
| [Spector.js](https://github.com/BabylonJS/Spector.js)（MIT） | 检查 WebGL 状态、shader 和 draw calls；不是 WebGPU 调试器，WebView 使用方式待测。 |
| [Paparazzi](https://github.com/cashapp/paparazzi)（Apache-2.0） | 本地 Android View/Compose 截图回归；不能证明 SurfaceView、WebView、实时动画及真机 GPU 合成正确。 |

## 可复现的下一轮验收

1. **先核许可和资产**：固定候选代码版本，分别登记运行时、模型、服装、纹理、音频与字体许可；GPL 桌面工具与分发到应用的运行时代码分开检查。
2. **美颜对照**：相同设备、前后摄、光照、分辨率和素材，对比现有预览与 GPUPixel；记录效果、帧时间、设备温升和恢复，特别检查纹理线程与 ABI。
3. **角色与口型**：一套已授权 VRM、两件兼容服饰、一段中文语音；离线组合前后分别检查表情、遮挡、骨骼、MToon 和扩展，再测 Android WebView 口型同步、静音与打断。
4. **视频输出**：先在受控实验中用真实 CameraX 输入验证合成输出格式、旋转镜像、引用生命周期和帧时间戳；再通过 LiveKit 的自定义轨验证连接、发布、对端画面和音画同步。真实摄像头发送前须满足用户同意与产品隐私约束。
5. **特效和性能**：用同一 `cmp.cue` 和独立覆盖层比较 Lottie、Konfetti、three.quarks 或 Proton；Perfetto 采轨迹，必要时 AGI/RenderDoc/Spector 抓帧。固定分辨率、持续时间、内容与设备，记录 P50/P95、内存、温度及故障。

本报告没有声称任何新增项目已接入或真机通过；本轮没有安装 APK、修改版本号或运行 Docker。
