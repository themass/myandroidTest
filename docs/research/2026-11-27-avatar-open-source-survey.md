# 数字人全周期开放项目调研

日期：2026-11-27。范围：Live2D、Spine、Lottie、Three.js/VRM 的制作、运行、换装、特效、优化和验证。仓库许可与活动状况随版本变化；下列建议是选型研究，不代表已经完成产品接入或真机验收。代码、SDK、编辑器、样例模型、贴图、声音和字体须分别核对许可。

## 结论与优先级

1. **优先利用已有能力**：Android 舞台已通过 `DigitalHumanEngine` 接入原生 Live2D 和 WebView Spine；Lottie 已有独立覆盖层与 `cmp.cue` 映射。优先提升真实角色资产、换装映射和特效质量，不需要为采用新库重做 UI。
2. **优先试点 Konfetti、three.quarks、VRM Blender 插件、glTF Validator、glTF Transform**：分别验证 Android 简单事件粒子、Web 复杂粒子、美术产线和资源检查优化。它们解决不同环节；功能和性能都仍需验收。
3. **按素材需求对比 Effekseer、libpag、Rive**：它们分别引入新的特效、动画或角色资产工作流，不能直接播放现有其他格式。libpag 官方说明预编译 Android 包含 Beacon 使用统计、源码构建不采集，实际产物和网络行为需核对。
4. **研究 OpenBone、Inochi2D、Umamo、Ayatsuri**：前二者用于新资产管线，后两者自称支持部分 Live2D 互通，但尚无本项目往返兼容证据；切勿用唯一源文件实验。
5. **许可边界**：Spine Runtime 为专用许可而非 MIT；Live2D Framework 虽公开源码，Cubism Core 单独授权且为专有组件。引入 MIT 包装层不会改变底层许可。

现有事实依据：`feature-companion/.../dh/DigitalHumanLabHost.kt` 承载引擎 View；`LottieFxLayer.kt` 是独立覆盖层；`PresentationCue.kt` 和 `StageEffectMapper.kt` 提供 cue 路径。`scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json` 已区分已验证、待美术和待参数的变体；未验证条目不能直接标为可用。现有 FX Lab 有特定 WebView 透明 canvas 合成问题及图片搬运变通的历史记录，不构成本次真机结论。

## 生命周期地图和候选

表中许可为仓库所述代码许可或授权性质，实际使用应按固定版本及依赖重新核对。近期推送仅说明有仓库活动，不代表正式发布或生产成熟。

### 角色绘制、绑定、制作

| 项目 | 价值和使用方式 | 许可及限制 |
|---|---|---|
| [Krita](https://github.com/KDE/krita) | 桌面绘画、分层素材及部分动画；适合制作角色部件。 | GPL；导出 PSD 图层、混合模式进入目标编辑器需验收。 |
| [VRM Add-on for Blender](https://github.com/saturday06/VRM-Addon-for-Blender) | 导入导出 VRM，编辑 humanoid、MToon；适合三维角色生产。 | [MIT 或 GPL-3.0-or-later](https://raw.githubusercontent.com/saturday06/VRM-Addon-for-Blender/main/LICENSE_MAIN.txt)；具体 VRMA 制作与往返需验证。 |
| [spine-scripts](https://github.com/EsotericSoftware/spine-scripts) | 设计软件到 Spine 的素材及部分层级导出，减少人工摆放。 | 每个脚本目录单独核对许可、所需软件和导出保真。 |
| [OpenBone](https://github.com/UXCentury/open-bone) | 开源 2D 骨骼编辑器和多平台运行时，可评估自主新角色产线。 | MIT；与 Spine 的导入和保真不能假定，Android 及资源成熟度需试验。 |
| [Inochi Creator](https://github.com/Inochi2D/inochi-creator) / [SDK](https://github.com/Inochi2D/inochi2d) | 开源 2D puppet 制作和运行，适合作为新资产路线对照。 | BSD-2-Clause；与 Live2D 格式不兼容，Android/Web 路线待核验。 |
| [Umamo](https://github.com/umamoorg/umamo) | 声称支持部分 `.cmo3` / `.moc3`，可做隔离互通实验。 | GPL-3.0，alpha；项目警告可能损坏数据，功能未完成；官方编辑器往返及许可边界待验证。 |
| [Ayatsuri 2D](https://github.com/Ayatsuri2D/ayatsuri_2d) | 新兴编辑器，声称支持多版本 `.moc3`；可在复制资产上研究。 | 编辑器 GPL-3.0，运行时组件另有许可；Cubism Editor 往返未验证，不能直接替代正式产线。 |

### Runtime、表情和口型

| 项目 | 价值和使用方式 | 许可及限制 |
|---|---|---|
| [Live2D Native Framework](https://github.com/Live2D/CubismNativeFramework) / [Samples](https://github.com/Live2D/CubismNativeSamples) | 官方 C++ 与 Android 示例，支撑现有原生 Live2D 路线。 | Framework 使用 Live2D Open Software License；[Cubism Core](https://docs.live2d.com/en/cubism-sdk-manual/cubism-core/) 单独授权，素材另审。 |
| [Spine runtimes](https://github.com/EsotericSoftware/spine-runtimes) | 官方 Android、Web 等运行时；[skins](https://esotericsoftware.com/spine-runtime-skins) 可组合衣服附件。 | [专用授权](https://raw.githubusercontent.com/EsotericSoftware/spine-runtimes/4.2/LICENSE)：按 Editor License 第 2 节或协议所列条件使用，分发含版权和许可声明；结合分发情形审查。导出与 runtime 主次版本需匹配。 |
| [pixi-live2d-display](https://github.com/guansss/pixi-live2d-display) / [WebGAL fork](https://github.com/OpenWebGAL/pixi-live2d-display-webgal) | Web 预览和交互适配层；适合工作台实验。 | MIT 包装层仍依赖 Cubism Core；示例素材另审。原项目较久未更新，fork 对具体 Pixi/Cubism 版本需核对。 |
| [DragonBonesJS](https://github.com/dragonbones/DragonBonesJS) | DragonBones 格式的 Web 播放。 | MIT；不是 Spine 或 Live2D 格式的通用替代。 |
| [three-vrm](https://github.com/pixiv/three-vrm) / [VRM Animation](https://github.com/pixiv/three-vrm/tree/dev/packages/three-vrm-animation) | VRM 加载、表情、MToon、弹簧骨骼与 VRMA 动作；与现有 Three.js 样件直接相关。 | MIT；模型和动画资产许可另审，目标 WebView 支持和性能待验证。 |
| [three-vrm-lip-sync](https://github.com/vlapky/three-vrm-lip-sync) | 基于音频处理驱动实时口型的候选。 | MIT；AudioWorklet 的安全上下文、中文口型质量、延迟及 WebView 性能均待验证。 |
| [Rhubarb Lip Sync](https://github.com/DanielSWolf/rhubarb-lip-sync) | 离线音频生成口型时间线，可用于预制台词。 | 主程序 MIT，[第三方组件另有条款](https://github.com/DanielSWolf/rhubarb-lip-sync/blob/master/LICENSE.md)；中文效果需试听，不等于实时通话方案。 |
| [Rive Android](https://github.com/rive-app/rive-android) | 状态机驱动交互动画，适合小角色与交互组件对照。 | runtime MIT；编辑器/服务条款另计；需重新制作 `.riv`，不能直接播放 Lottie JSON。 |

Spine 换装以美术预先配置的骨架、slot、附件和图集为前提；组合 skin 后要按官方流程恢复 setup pose、应用动画及手工附件。Live2D 当前参数或部件切换只作用于模型里真实存在的资源，不能凭 `.exp3` 凭空生成新衣服。两个引擎统一消费 `wardrobe-spec`，各自解析为真实、已验证的资产绑定。

### 动效和粒子

| 项目 | 用途 | 许可及限制 |
|---|---|---|
| [lottie-android](https://github.com/airbnb/lottie-android) / [lottie-web](https://github.com/airbnb/lottie-web) | 现有 Android 独立特效层的基线；Web 可用于素材预览。 | 分别 Apache-2.0 / MIT；[支持功能](https://github.com/airbnb/lottie/blob/master/supported-features.md)按播放器、版本和素材核验。 |
| [Bodymovin](https://github.com/airbnb/lottie-web) | After Effects 导出 Lottie JSON 的工具链。 | 仓库 MIT；AE 商业授权，复杂表达式和遮罩跨端渲染须验收。 |
| [dotlottie-android](https://github.com/LottieFiles/dotlottie-android) / [dotlottie-web](https://github.com/LottieFiles/dotlottie-web) | 评估 dotLottie 容器、资源打包和播放。 | MIT；容器版本、所需功能和各端播放兼容需逐项确认。 |
| [Konfetti](https://github.com/DanielMartinus/Konfetti) | Android Compose/View 事件彩纸、爱心等粒子；优先接现有 cue 的独立层实验。 | ISC；图形定制、粒子数和低端机开销待测。 |
| [three.quarks](https://github.com/Alchemist0823/three.quarks) | Three.js 粒子发射器与编辑器，可试制作雪、雨、花瓣。 | MIT；对现有 Three.js 版本兼容、WebView 合成及移动性能待测。 |
| [Effekseer](https://github.com/effekseer/Effekseer) | 美术主导的复杂粒子、轨迹与材质；比较 Android 原生路线。 | 主仓库 MIT，依赖另审；NDK、后端、透明叠加、包体和生命周期待测。 |
| [tsParticles](https://github.com/tsparticles/tsparticles) | Web 粒子与背景动画；适合 Web 场景研究。 | MIT；WebView 离线资源、叠加、兼容和性能待测。 |
| [PixiJS](https://github.com/pixijs/pixijs) | Web 2D 精灵、滤镜和交互。 | MIT；部分社区粒子插件针对 Pixi 6，不能默认支持新版本。 |
| [libpag](https://github.com/Tencent/libpag) | AE → PAG 复杂动画；适合对比 Lottie 不支持的素材。 | Apache-2.0；需 PAG 专用导出及 runtime；预编译包统计行为须按实际版本核对。 |
| [postprocessing](https://github.com/pmndrs/postprocessing) | Three.js 的后处理效果。 | zlib；额外 GPU 及离屏缓冲成本，非默认移动端方案。 |
| [Filament](https://github.com/google/filament) | 原生 Android 实时 3D；适合需要原生 3D 的长期比较。 | Apache-2.0；不是即插即用粒子覆盖层，需评估 3D 管线和包体。 |

[LottieFiles lottie-player](https://github.com/LottieFiles/lottie-player) 与 [lottie-interactivity](https://github.com/LottieFiles/lottie-interactivity) 已归档；[Samsung rlottie](https://github.com/Samsung/rlottie) 官方声明弃用，不建议作为新项目默认依赖。LottieFiles 在线创作和优化服务可作补充，但服务条款、隐私、导出与商业限制独立核对；敏感素材上传前需另行确认。

### 校验、压缩与质量保障

| 项目 | 作用 | 验证边界 |
|---|---|---|
| [glTF Validator](https://github.com/KhronosGroup/glTF-Validator) | glTF/GLB 结构、引用、缓冲及支持扩展检查。 | Apache-2.0；通过不代表所有 VRM 扩展、表情和材质正确。 |
| [glTF Transform](https://github.com/donmccurdy/glTF-Transform) | glTF 检查、变换与资源优化。 | MIT；选定操作能否保留全部 VRM 扩展须实测。 |
| [meshoptimizer](https://github.com/zeux/meshoptimizer) | mesh 压缩、gltfpack 优化。 | MIT；确认运行端扩展及解码器，测解码耗时、画质和动画。 |
| [Draco](https://github.com/google/draco) | 几何压缩候选。 | Apache-2.0；同样需要对应解码器和加载耗时评估。 |
| [KTX-Software](https://github.com/KhronosGroup/KTX-Software) | KTX 纹理制作与处理。 | 各组件许可不同；需检查设备纹理格式支持和转码成本。 |
| [Playwright](https://github.com/microsoft/playwright) | 自动验证 Web 预览加载、换装交互和截图回归。 | Apache-2.0；截图环境需固定，不能代替 Android 性能与生命周期验证。 |

## 实施试点和通过标准

1. **固定验收素材**：一套真实 Live2D、可组合 skin 的 Spine、可商用 VRM、四种特效（爱心/彩纸、雪、雨、花瓣）；记录各自权利、源文件、导出版本和渲染器版本。素材必须与最终视觉目标接近，点粒子占位不能证明美术达标。
2. **换装**：以 `wardrobe-spec` 为唯一事实源，分别映射 Live2D 已验证参数和 Spine skin；对互斥、缺资源、恢复和持久化等纯决策逻辑先写测试。比较真实画面、遮挡和动画切换；不把待美术/待参数写为已完成。
3. **特效 A/B**：同一个 `cmp.cue` 触发 Lottie、Konfetti 和 three.quarks 原型，Effekseer 在资源与 NDK 允许时加入。记录素材制作成本、画面质量、启动耗时、前台帧时间 P50/P95、CPU/GPU、内存、温升和电量；单独验证透明、角色前后层级、触控及退后台恢复。
4. **VRM 产线**：Blender 导出 → Validator → glTF Transform/meshoptimizer 的单操作对照 → 重新加载。对比表情、口型、材质、骨骼、扩展、文件大小、加载和解码耗时；任何丢失即该优化路径不通过。
5. **多端验收**：Web 截图和自动化检查覆盖加载、状态与事件；Android 真机覆盖设备 WebView、WebGL 2、图形驱动与生命周期。Three.js r163 起 WebGLRenderer 需要 WebGL 2；同样资源、分辨率和场景才可比较性能。
6. **通话与摄像头单独验收**：本地屏幕覆盖层不会自动进入 LiveKit 视频轨；若需要对端看见，必须实现并验证帧合成、发送、对端画面、音画同步和性能。CameraX 和媒体链也需独立验证。

## 已有证据与未完成事项

- 已有浏览器样件中 Live2D 与 Spine 原创机器人均可显示、操作服饰与动作；不代表 Android 真机或最终画质达标。
- 另一工作区的 Three.js 0.187.0 / three-vrm 3.5.5 天气样件在桌面浏览器验证过雪、雨、花瓣切换、关闭、减密和旋转。雨和花瓣只是点粒子占位，不能作为最终美术结论。减密只减少 draw range，更新循环仍处理全部 600 个点，不能推断 CPU 同比例降低。
- 上述候选项目本次为资料调研，未逐一安装、构建或在目标 Android 设备实测。Android 透明合成、摄像头、对端视频、设备性能与热表现均为 BLOCKED / 未验证；不得视为 PASS。
- 引入任何依赖前，固定版本和上游源码，复核直接与传递依赖、许可证及 NOTICE；单独确认每个模型、贴图、字体、音频、动作和服务的使用权。
