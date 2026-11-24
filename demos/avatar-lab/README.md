# 数字人对比实验室

这是一个独立的 Android 应用工程，不依赖主应用模块。原生 Java Activity 提供中文目录和 Android WebView 页面容器；没有把网页源码页或作品库描述成应用内引擎运行时。

目录状态含义：

- **本地集成**：本地场景入口固定从 `https://appassets.androidplatform.net/assets/scene/index.html` 加载。场景文件由集成方提供到 `app/src/main/assets/scene/`。缺少文件时页面会显示加载错误，不会用静态截图冒充运行时。
- **在线官方示例**：在 WebView 中打开 TalkingHead、Spine、three-vrm 官方在线示例页面。这些是在线网页参考，尚未集成到本应用。
- **在线作品库**：Rive Community 是在线作品库，不是 Rive Runtime 集成。
- **资料，不计入运行验收**：Inochi2D 提供项目主页和文档入口。

目录为每项附来源链接、状态和观察建议，可用于比较陪伴头像（表情、口型）与讲师/全身角色（骨骼动作、站姿和构图）。页面加载成功只代表网页就绪，并非原生引擎集成验收。WebView 不暴露 JavaScript bridge，禁用 file/content 访问、明文 HTTP 和混合内容；网页媒体播放要求用户手势。VRM 导入通过系统文档选择器，仅授予用户挑选的 URI，不申请存储权限。

应用标识 `com.openapi.avatar.lab`，版本 `1.0 (1)`，最低 Android API 26，目标及编译 API 35。仅依赖 AndroidX WebKit 1.12.1 和 Android 插件，不引用生产应用依赖。Activity 在配置变更/重建时保存所选目录项；提供“返回目录”和“重试”，兼容横竖屏。

## 构建与测试

从仓库根目录运行：

```sh
./gradlew -p demos/avatar-lab :app:testDebugUnitTest :app:assembleDebug
```

APK 输出路径：`demos/avatar-lab/app/build/outputs/apk/debug/app-debug.apk`。运行测试和构建不等于设备安装或真机验收。仓库 Gradle wrapper 使用 Gradle 8.14.3。
