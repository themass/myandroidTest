# Android 开源图片美化、编辑与特效项目调研

调研日期：2026-11-24

## 摘要

按当前仓库现状，最实用的路线是把功能拆成可替换的层：基础编辑 UI 采用传统 View 可嵌入库，静态滤镜采用 GPUImage 类 GL 管线；裁剪单独接成熟裁剪器；实时人脸贴纸作为后续独立功能验证；AI 修复/超分只作为异步、可选的原生推理能力。当前应用使用 Java、minSdk 21、compileSdk 23 与 Android Support Library 23.2.0，完全没有 Compose 依赖。现代 AndroidX/Compose 库不能直接视为“加一条依赖就能用”，应先升级构建链、AndroidX 和 compileSdk，或者挑选老式 View/Java API 并做设备验证。

此前任务背景提及的较窄图片专项文档在此 checkout 中不存在；本报告因此单独覆盖编辑器、滤镜、贴纸、特效与修复，并不试图复述该专项范围。

## 候选项目与适用层

| 项目 | 能力与平台 | 维护信号（截至调研日） | 许可证 | 对本仓接入成本与判断 |
|---|---|---|---|---|
| [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) | Android View 编辑器：绘画、滤镜、文字、emoji/贴纸、缩放旋转、撤销重做、导出；不是完整专业调色器 | GitHub API 显示最近 push 2026-09-27；README 提供 Maven 依赖 `3.1.1` | MIT | **首选编辑器候选**。API以 `PhotoEditorView`/Builder 为主，比 Compose 组件更适合传统 View；但项目已迁 AndroidX、核心为 Kotlin，旧 support 库工程需要迁移和依赖冲突验证。 |
| [android-gpuimage](https://github.com/wasabeef/android-gpuimage) | OpenGL ES 2.0 图片滤镜，含色彩调整、模糊、边缘、畸变、混合等；支持离屏处理与预览 | 最近 push 2022-08-03；维护偏旧 | GitHub API 未识别 SPDX 许可证，仓库 `LICENSE` 链接返回 404；视为**许可证未核实** | API 简单且 Java 友好，技术上适合静态滤镜/预览；生产采用前必须先确认代码许可。此状态下不作为可直接商用推荐。 |
| [android-gpuimage-plus](https://github.com/wysaid/android-gpuimage-plus) | C++/Java OpenGL 图像、相机和视频滤镜；自定义 shader/滤镜链，README 提及 CameraX | 最近 push 2026-07-28；提供 `3.2.0` 系列依赖 | MIT | **需要实时滤镜时优先评估**。支持 CameraX，渲染和相机回调需处理 GL 线程；包含 native/CMake 路径和多个 artifact flavor，集成复杂度高于纯 View 控件。老项目须验证 NDK ABI、设备和当前相机栈。 |
| [compose-image-editor](https://github.com/robeshell/compose-image-editor) | Compose 编辑器：裁剪/旋转、绘画、马赛克、文字、贴纸；内部通过 AndroidView 桥接 PhotoEditor 等 | 最近 push 2026-06-17；仓库体量很小（GitHub API 显示 1 star），活跃提交不代表成熟度 | MIT；依赖含 Android-Image-Cropper（Apache-2.0）和 PhotoEditor（MIT） | **不适合直接接入当前仓**：要求 Jetpack Compose/AndroidX，且组件与依赖应做源码审查、版本锁定及导出/内存测试。可作新 Compose 页面原型参考。 |
| [CanHub Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) | 聚焦裁剪、旋转及裁剪结果；传统 Android UI 集成 | 最近 push 2026-04-28；GitHub 发布页显示 4.7.0 | Apache-2.0 | 能力边界明确，适合作为独立裁剪步骤；当前版本走 AndroidX/较新 Gradle 生态，旧工程需先评估最低 SDK 与 AndroidX 迁移成本。不要把它当完整编辑器。 |
| [VrFace](https://github.com/oleg-sta/VrFace) | Android 实时人脸 AR 效果库；仓库指向面具/美妆示例 | 最近 push 2021-07-03，规模较小，维护信号弱 | Apache-2.0 | **只建议研究/原型**。公开说明不足以确定当前 Android 版本、设备覆盖和可直接调用 API；需在设备上验证帧率、跟踪稳定性、相机接入与效果资源管线。 |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe) + [Face Landmarker Android sample](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/face_landmarker/android) | 人脸关键点/表情系数/变换矩阵，可用于自行绘制贴纸、美妆叠加；是感知能力与示例，不是完整滤镜编辑 SDK | 主仓持续维护；示例采用 Android/Kotlin 与 Tasks API | MediaPipe Apache-2.0；模型文件、第三方依赖需分别核对 | **自研实时贴纸的可控基础**。需自行实现渲染、遮挡、贴纸资源、相机生命周期和性能管理；比直接集成一个 SDK 工作量大，但能力边界清楚。 |
| [RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android) | 完整 Android 超分/插值应用参考，包含 RealSR、SRMD、RealCUGAN、Real-ESRGAN、Waifu2x、Anime4KCPP 等模型/引擎 | GitHub API 查询请求超时，无法在本次核实最新 push | 本次未能从仓库元数据确认；**不得假设可商用** | 仅作 native 推理与模型选择的架构参考，许可未核实前不要复制代码、模型或打包产物。模型的许可证和训练数据条款也要逐一审阅。 |
| [Real-ESRGAN-ncnn-vulkan](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan) | 命令行超分工具：Real-ESRGAN、动漫模型、倍率、切片等；基于 ncnn/Vulkan | 最近 push 2024-05-10；维护活跃度中低 | GitHub 显示 `NOASSERTION`，不能仅凭项目名或论文推断代码/模型许可 | 不是 Android SDK；移植需 Vulkan/ncnn native 构建、模型分发、JNI、耗时任务和低内存策略。适合技术验证，不是直接接入方案。 |

## 分层推荐

### 第一阶段：低风险基础编辑

1. 在独立 Activity/Fragment 中做图片选择与编辑流程，不把图片编辑器耦合进 VPN 核心页面。
2. 以 `burhanrashid52/PhotoEditor` 验证画笔、文字、贴纸、撤销与导出；先解决 AndroidX 迁移和 Java 调用方式，再评估是否 fork/封装。
3. 裁剪能力单独评估 CanHub Cropper；不要重复实现裁剪手势。若暂时不升级 AndroidX，先保持自有裁剪 UI 或选兼容版本并逐项核对许可证/SDK要求。
4. 滤镜原型先明确许可：`wasabeef/android-gpuimage` 虽然使用门槛低，但当前许可证状态没有核实；可对比 MIT 的 `android-gpuimage-plus`，评估 native 构建与运行时表现后再定。

### 第二阶段：实时贴纸与人脸特效

先用 MediaPipe Face Landmarker Android sample 做人脸关键点验证，再将预览帧与 landmark overlay 接入独立 GL/Canvas 渲染层。贴纸素材、定位、遮挡、肤色/美妆、导出录制都需产品自有实现。`VrFace` 可作为旧项目案例，不建议未经维护性和设备验证就依赖。

商业 SDK（例如 Banuba）可以提供更完整的现成效果，但其公开 Android samples 不等同于 SDK 本体的开源许可；通常需试用 token/商业授权，应按供应商合同单独评估，不纳入“开源可商用”推荐。

### 第三阶段：修复与增强

把超分/修复设计成离线任务：缩小预览、后台执行、可取消、显示进度、导出前控制分辨率与内存。Real-ESRGAN/ncnn 只是候选技术栈，不等于 Android 可直接用，也不意味着模型权利已清楚。若需求是擦除、补全或人像修复，应分别评估模型和训练数据授权，不能仅凭推理代码仓库的许可证作结论。

## 与本仓的兼容性核对

本仓只有 `:app` 一个模块；`app/build.gradle` 使用 Android Gradle 插件旧 DSL、`compileSdkVersion 23`、`minSdkVersion 21`、Java 项目和 Support Library 23.2.0，还含旧 Glide 3、ButterKnife 7 等依赖。未发现 Compose、AndroidX、CameraX 或图像编辑模块。因此：

- **最低接入门槛**：建立单独图片编辑入口；图片读取/保存通过 Uri 与 ContentResolver；编辑耗时放后台；控制大图解码和 Bitmap 生命周期。
- **当前最大兼容风险**：现代依赖要求 AndroidX、较新 compileSdk/Gradle/AGP；迁移可能牵动整个旧工程，不能假设局部依赖可独立升级。
- **性能与 ABI**：GPU 滤镜、相机特效和 ncnn/Vulkan 属不同渲染/原生栈；不要同时引入多个重型滤镜框架。当前仓内 native 库只见 `armeabi`，新 SDK 的 ABI/NDK 支持需要单独验证。
- **UI 选择**：当前仓不适合优先引入 Compose-only 组件；经典 View API 或把编辑器封装成独立子模块更现实。

## 许可证与商用注意事项

- MIT、Apache-2.0 通常允许商用，但仍需保留版权与许可证文本；Apache-2.0 另含专利授权与 NOTICE 等要求。将实际分发版本的 LICENSE/NOTICE 纳入合规清单。
- “GitHub 可见”“有 Maven 包”“README 写 open source”都不能代替明确的 LICENSE 文件。`wasabeef/android-gpuimage` 和 `Real-ESRGAN-ncnn-vulkan` 本次查询分别显示未识别 SPDX 与 `NOASSERTION`，须先确认权利再采用。
- 图片编辑仓库依赖的裁剪器、滤镜、模型、贴纸/字体资源可能各有不同许可；逐项锁定版本并核对传递依赖与资产授权。
- MediaPipe 仓库代码的 Apache-2.0 不自动覆盖独立模型文件及所有第三方素材。AI 模型还应检查模型卡、使用限制、训练数据来源与商业授权。
- 本文是工程选型调研，不构成法律意见。正式发布前对确切 commit/tag、包版本、依赖树、NOTICE 和模型资产做一次许可证审查。

## 来源与核验方法

维护日期来自 GitHub Repository API 字段 `pushed_at`（查询日期 2026-11-24）；仓库星数只作规模参考，不代表质量。许可证以 GitHub API license 元数据/仓库许可证页面为准；缺失或 `NOASSERTION` 明确标为待核实。功能与接入信息以各项目 README、发布页、示例代码为主。

- [PhotoEditor README](https://github.com/burhanrashid52/PhotoEditor)
- [android-gpuimage README](https://github.com/wasabeef/android-gpuimage)
- [android-gpuimage-plus README](https://github.com/wysaid/android-gpuimage-plus)
- [Compose Image Editor](https://github.com/robeshell/compose-image-editor)
- [CanHub Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper)
- [VrFace](https://github.com/oleg-sta/VrFace)
- [MediaPipe](https://github.com/google-ai-edge/mediapipe)
- [MediaPipe Face Landmarker Android sample](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/face_landmarker/android)
- [RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)
- [Real-ESRGAN-ncnn-vulkan](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan)
- [Banuba Android samples](https://github.com/Banuba/banuba-sdk-android-samples)
- 本仓依赖和 SDK 配置：[app/build.gradle](../../app/build.gradle)
