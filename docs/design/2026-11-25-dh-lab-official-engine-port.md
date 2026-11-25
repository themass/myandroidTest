# 数字人 Lab 官方引擎移植（2026-11-25）

用户指令（原话）：「数字人实验对比demo，我已经明确要求了，很多的github官方你都没有加进来，是吧代码移植进我们的项目，然后展示出来」「继续，把能移植的全部移植进来」。

对比矩阵（docs/design/2026-11-24-课程场景-引擎对比.html）列了七条引擎线：Live2D、Spine、VRM+three.js、TalkingHead、DUIX/TaoAvatar、Rive、Inochi2D。此前官方引擎的示例只活在独立 demo app（demos/avatar-lab，applicationId com.openapi.avatar.lab），生产通话页的 Lab 注册表里从来没有它们。本轮把**能移植的全部**接进生产 Lab（feature-companion），全部离线、零联网、走引擎注册表 + 通话页右上角菜单。

## 已移植（本轮进 mainline）

| 引擎 | 接入方式 | 素材 | 许可 |
|---|---|---|---|
| Spine（spine-webgl 4.3 官方运行时 + spineboy 官方样例） | `SpineDigitalHumanEngine`：WebView 伪源 `https://dh-spine.local/`，白名单五文件 | `feature-companion/src/main/assets/dh-spine/`（随 APK，约 520KB） | Spine Runtime 为 Esoteric Software 专有许可（三档：评估免费）。**内部评估 OK；上生产必须书面许可确认**。素材 spineboy-pro 为官方示例。 |
| TalkingHead（met4citizen，MIT，vendored 源码打进 bundle） | `TalkingHeadDigitalHumanEngine`：WebView 伪源 `https://dh-talkinghead.local/`，白名单四文件（含 audio worklet——vendored 源码顶层用 import.meta.url 定位它） | `dh-talkinghead/`：bundle + mpfb.glb（36.8MB，CC0，ARKit/Oculus blendshape）+ playback-worklet.js + LICENSE | MIT（TALKINGHEAD-LICENSE 随包）。不接在线 TTS（lipsyncModules 置空）。APK 体积 +36.8MB 是 lab 评估代价，release 前再议。 |
| Rive（app.rive:rive-android 11.16.3 官方运行时） | **debug 变体专属**：引擎与注册器在 feature-companion 的 debug 源集，main 只按类名反射挂钩（`DhLabBootstrap.DEBUG_ENGINE_REGISTRAR`）；release 里类不存在 = 菜单「未编入」 | 样本 .riv 按许可结论留在 :app debug res（README-fxlab-rive.md：.riv 样本 redistribution 权利未确立，不得挪出 debug；引擎按资源名运行时查找） | 运行时 MIT。androidx.startup 被摘（tools:node="remove"），构造 SDK 对象前必须 `Rive.init`——fxlab 真机踩过的 JNI 坑，引擎同款 gate。 |

公共桥：`AvatarSceneBridge`（线格式 `window.dhCommand('<json>')`，与页面侧一字不差，AvatarSceneBridgeTest 钉死）。
- Spine：只接官方动作组（walk/run/jump→官方同名，wave→idle-turn）；词表外一律不发声。
- TalkingHead：情绪→内置 mood（joy→happy、surprised→fear…词表外保持当前）；口型→jawOpen 固定值通道；注视→eyesRotateX/Y 聚合通道（y 上正 = eyesRotateX 负，官方 eyesRotateX 正方向是向下看）。

打包脚本：`scripts/dh-lab/avatar-comparison/build.mjs` 现在同时产出 demos/avatar-lab 的五个场景页与生产 Lab 的 `dh-spine` / `dh-talkinghead` assets。改页面 JS 后重跑 `node build.mjs`。

## Inochi2D：不移植，结论记档

**结论：2026 年内没有可用于 Android 的官方运行时，不移植。**

- 官方工具只有 Inochi Creator（桌面绑定）与 Inochi Session（桌面直播）：https://inochi2d.com ——无任何移动 SDK / Android 库。
- 项目维护者 LunaTheFoxgirl 在 Inochi2D/inochi-creator#502（2026-02-13）明确表态："iPad support is planned down the line, Android is not for the time being. Android devices are too unreliable to develop for, for me." issue 至今 open、无 assignee、无里程碑。
- 社区 issue（Inochi2D/inochi2d#73）同样确认无官方 Android 路径；官方文档自述「still heavily work-in-progress」，不承诺游戏级可用性。
- 无可信第三方 Android 运行时。对比矩阵里 Inochi2D 一列在 Android 语境下只有自研 Core 一条路（成本高、绑 Inochi 社区格式生态），与「把官方代码移植进来展示」的诉求不符。

若将来 Inochi2D 出官方移动运行时，重新评估入口就在本文件 + 引擎对比矩阵。

## 遗留 / 风险

- **Spine 许可**：spine-webgl 打进 APK 属内部评估用途（官方三档许可评估免费）；任何对外分发前必须买断 runtime 许可并书面确认。THIRD_PARTY 台账补记归协调会话统一做（本轮不动 dh-engines/THIRD_PARTY.md 的未提交 duix hunk）。
- **APK 体积**：mpfb.glb 36.8MB 直接进 assets。release 化之前要么改下载包（AssetStore），要么砍 TalkingHead 出 release——lab 阶段随 debug 走。
- **真机验证**：三引擎（Spine/TalkingHead/Rive）在 V2279A 上的舞台展示未装机验证——装机单写者纪律，等协调会话用 weifu-refactor 最新 tip 现场 assemble 后验证。
