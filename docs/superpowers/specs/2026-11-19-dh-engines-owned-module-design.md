# DH Engines 自有总模块设计

状态：**已批准**（2026-11-19）。建造核对缺口已关闭；结论已写入下文。  
范围：**内部 Lab / 测试**。本阶段明确忽略各家协议与授权审查；**不**声称可上架、可商用、可对外分发。若以后要产品化，必须另开一轮授权与合规设计，本文件不覆盖那一步。

关联：现有 Lab 对比壳见 `2026-11-19-digital-human-lab-design.md`；通话页下拉切换已在 `feature-companion`（`callStage` + `DhEngineMenu`）。本设计回答的是「如何把三家**收进仓库、改成我们的代码**」，不是再做一套评测 UI。

## 1. Intent & Why

- **要什么**：仓库内一个总模块，收 Duix / Live2D / Tao 的**通话页运行时**，包名改成我们的；模型和预编译 native 按需下载；通话页右上角能切到三家并**真的出画面**。
- **给谁**：我们自己做内部测试与后续改造；目标是代码完全在工程下，以后能升级、能改，有机会变成产品一部分。
- **成功**：不依赖本机 `~/work/android/...` 外链 include；clone 本仓库 + 能访问下载源后，debug 通话页可切换 Duix / Live2D / Tao 并看到渲染画面（缺资源时显示下载进度/失败原因，不回到「变成文字却点不动」）。
- **为什么现在**：外链 + 反射只能评测，不能当「我们工程里的引擎」改；用户明确要拷贝改造、三家都能跑、一个总模块。

## 2. Goals / Non-goals / Anti-goals

**Goals**

1. 新建单一 Android library 模块 `:dh-engines`（名称可微调，但必须是**一个**模块，不是三个独立 module）。
2. 模块内三个子包（源码树分区，不是三个 Gradle 工程）：`duix/`、`live2d/`、`tao/`，只收通话页出画面所需运行时。
3. Java/Kotlin 包名改为：
   - `com.openapi.ks.dh.duix`
   - `com.openapi.ks.dh.live2d`
   - `com.openapi.ks.dh.tao`
4. `:feature-companion` **直接依赖** `:dh-engines`（测试阶段可始终依赖 chat debug）；现有 `DuixDigitalHumanEngine` / `Live2dDigitalHumanEngine` / `TaoDigitalHumanEngine` 改为调用本模块 API，**去掉反射与 settings 外链 include**。
5. 大模型与预编译 `.so`：**不进 git**；用户进通话页并选中某引擎时按需下载，缓存到应用私有目录，下次复用。
6. 第一版验收：通话页下拉切三家都能出画面（在已下载完成的前提下）；保留「原来的形象」选项。
7. 上游来源与版本写进模块内 `THIRD_PARTY.md`（路径、commit/tag、下载 URL），方便以后对照升级。

**Non-goals**

- 搬入各家完整 Demo App / Activity / 本地 LLM·ASR·VITS 产品链路。
- 第一版重做一套新的通话 UI（可参考官方视口，但 chrome 继续用现有通话页）。
- 把 Windows/iOS Cubism 库、多余 Live2D 模型、Duix 评测 Demo 资源整树进仓库。
- 商用授权、上架合规、对用户展示开源声明页（本阶段「只是测试」）。
- 用 git submodule 挂外部仓库当长期方案（允许短期脚本从本机树拷贝生成模块内容）。

**Anti-goals（做成这样也算失败）**

- 继续依赖 `DH_ANDROID_HOME` / `-Pdh.lab.native=true` 才能编过或跑通。
- 三家仍是反射空壳，「能切但不能出画面」算未完成。
- 把数百 MB 的第三方 prebuilt / 模型 commit 进 git 历史。
- 为三家再拆三个可独立发布的 Gradle 模块（违背「一个总模块」）。
- 选引擎时静默失败或整页只剩占位文字且无原因。

## 3. Constraints

| 项 | 约束 |
|----|------|
| 模块形态 | 一个 `com.android.library`：`:dh-engines`；内部目录分区，统一 `namespace` 可用 `com.openapi.ks.dh`，子包用上面三个 |
| 通话接线 | 沿用现有 `DigitalHumanEngine` / `DigitalHumanLabHost` / 右上角 `DhEngineMenu`；默认 `callStage == null` → 原来的形象 |
| 音频 | LiveKit 扬声器出声；引擎 `setVolume(0)` 或只驱口型；PCM 按现有 bridge，缺 tap 时允许合成占位（与现 Lab 一致），不阻塞「出画面」验收 |
| 下载 | 触发：选中引擎且本地 cache miss；Wi‑Fi/流量不在第一版做策略；失败 toast/状态文案 + 不切换舞台 |
| 机型 | Tao：保持现有门闸（约 ≥7.5GB RAM + arm64）；不达标菜单写明原因、不可切换 |
| ABI | 与 app 一致：优先 `arm64-v8a`；`armeabi-v7a` 仅当运行时包确实提供且成本低，否则 arm64-only 引擎在 32 位机显示不可用 |
| 构建 | 去掉 `settings.gradle` 对外部 `duix-sdk` / `Live2D` 的 include；根工程里为 `:dh-engines` 配置 NDK/CMake 与现 app 对齐（NDK 27、CMake 3.22.1） |
| 测试声明 | 文档与 Lab 文案可保留「内部测试」；不删除上游树里已有的 LICENSE 文件拷贝（便于以后合规），但本阶段不据此阻断实现 |
| 工作树 | 实现落在当前 session worktree；完成后按既有惯例 commit → merge `weifu-refactor` → push |

## 4. Approaches（已选）

| 方案 | 要点 | 取舍 |
|------|------|------|
| A. 整树搬进总模块 | 三家目录几乎原样拷入 | 与上游近；体积与垃圾多；三套构建难伺候 |
| **B. 只收通话页运行时（已选）** | 一个 `:dh-engines`，三子包，Demo/多余库/模型不进 git；大包下载 | 仓库可控、可改造；首次筛选成本高；与上游 diff 变远 |
| C. 继续外链 | 代码仍在 `~/work/android` | 违背「完全在我们工程下」 |

**已选 B**，因为目标是自有、可改、可升级；验收是通话页出画面，不是保真官方工程树。

包名：**改成我们的**（见 Goals），不保留 `ai.guiji` / `chatwaifu` / `taobao.meta` 作为对外 API 包名。

下载时机：**进通话页后按需**（点到引擎再下），不在 assemble 时把模型打进 APK。

授权：**本阶段忽略，仅内部测试**。产品化另议。

## 5. 模块结构（建议落盘）

```
dh-engines/                          // :dh-engines
  build.gradle
  THIRD_PARTY.md                     // 来源、版本、下载 URL
  src/main/
    AndroidManifest.xml              // 空或仅 library 需要的权限说明
    java/com/openapi/ks/dh/
      DuixRuntime.kt                 // 对外：创建 View / pushPcm / release
      Live2dRuntime.kt
      TaoRuntime.kt
      AssetStore.kt                  // 下载、校验、缓存路径
      duix/                          // 从官方 sdk 筛入并改包名的源码
      live2d/
      tao/
    cpp/                             // 各引擎必需 JNI/CMake；第三方源码按需
    jniLibs/                         // 可选：仅极小 stub；大正本走下载
  src/test/...
```

**Builder 可决**：`AssetStore` 放在 `:dh-engines` 还是 `:feature-companion`——推荐放 `:dh-engines`，companion 只问「ready? / download() / createView()」。

**从本机树筛选的原则（拷贝脚本可一次性）**

- Duix：Java `ai.guiji.duix` → 改包；`cpp` 中 dhcore/渲染相关；`cpp/third` 的巨大 prebuilt **默认不进 git**，改为下载清单中的 zip（或首次从开发机 `adb push`/文档说明手工放置，但产品路径仍是下载）。
- Live2D：ChatWaifu 的 JNI/GL 封装 + Framework 源码改包；Cubism Core android arm64 `.so` 进模块 `jniLibs`（已批准的唯一例外）；模型走下载；不进 Windows/iOS 库。
- Tao：从 `MnnTaoAvatar` 抽出 NNR/`AvatarTextureView` 相关运行时与 CMake；其 Gradle 里 CDN 的 `native-libs-arm64-v8a.zip` 改为运行时 `AssetStore` 同一套下载；不搬整套 LLM/ASR Demo UI。

## 6. 运行时与通话页行为

1. 用户打开通话页：仍是原来的形象（`callStage == null`）。
2. 打开右上角菜单 →「形象」：原来的形象 / Simple / Live2D / Duix / Tao。
3. 点某引擎：
   - 若 `AssetStore` 未就绪 → 开始下载，菜单或状态区显示进度；**不**把舞台切成空白字。
   - 就绪且门闸通过 → `callStage = id`，`DigitalHumanLabHost(engineId)` 用**直接 API**创建 View。
   - 门闸失败或下载失败 → toast/原因文案，舞台不变。
4. 切回「原来的形象」：`callStage = null`，释放引擎。

Simple 可继续用现有 `SimpleDigitalHumanEngine`（不一定迁入 `:dh-engines`）；三家必须来自 `:dh-engines`。

## 7. 下载清单（第一版最小集）

具体 URL/文件名写入 `THIRD_PARTY.md`，实现时可调整，但每项必须有：**url、本地相对路径、校验（size 或 sha256）、归属引擎**。

**作者决定（建造核对后）：**

- **Prebuilt `.so` 与模型一律优先运行时下载**，经 `AssetStore` 落盘后 `System.load` / 引擎自有加载；**不**把大包打进 APK，**不** commit 进 git。若某引擎 CMake 在链接期硬依赖某 `.so` 且无法改成运行时加载：允许 Gradle `preBuild` 拉到**本机 `build/`（gitignore）**，仍禁止进 git；优先改加载方式而不是扩大例外。
- **依赖范围**：本仓库 app 仅 `chat` flavor → `:app` / `:feature-companion` 对 `:dh-engines` 的依赖按 chat debug 路径接通即可；不为不存在的 Play 多 flavor 另开开关。
- **下载源**：默认官方 URL（GitHub releases / meta.alicdn / ModelScope 等）；`AssetStore` 支持偏好里覆盖 base URL，便于内网镜像；开发机可 `adb push` 到 cache 跳过下载。
- **Tao v1「出画面」**：以 NNR/`AvatarTextureView` **能上屏**为验收；口型/A2BS 跟通话音频为加分项，不阻塞 v1。下载最小集不含整套 LLM/ASR Demo。
- **Duix 默认角色**：官方 **Oliver** 包，URL 固定为 `https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip`；解压后目录名 `Oliver`，`DUIX` 的 `modelName` 用 `Oliver`（不再用占位名 `duix_lab`）。基础包仍为 `https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip`。
- **Live2D Cubism Core**：测试阶段**唯一**允许进模块的第三方 `.so` 例外——把 android **arm64** Core 放入 `:dh-engines` 的 `jniLibs`（约 1.6MB）；**模型（ATRI 等）仍按需下载，不进 git**。其它引擎的大包 `.so` 仍走下载 / 本机 `build/` 缓存，不扩大「进 git」例外。

最小集：

| 引擎 | 内容 |
|------|------|
| Duix | 官方基础包 `gj_dh_res` + **Oliver** 角色包 + 运行所需 prebuilt `.so` 包（运行时下载） |
| Live2D | Cubism Core android arm64（**进模块 jniLibs**）+ 默认模型 ATRI（下载） |
| Tao | 官方 `native-libs-arm64-v8a.zip` 中渲染所需子集 + NNR 最小模型（下载）；不含 LLM/ASR 全量 |

开发机若已有文件，允许 `AssetStore` 识别「已推送到 cache 目录」跳过下载，便于无网调试。

## 8. 迁移步骤（实现顺序，供 plan 拆任务）

1. 建空 `:dh-engines`，companion 依赖它；外链 include 仍暂留直到三家 API 切完。
2. 落地 `AssetStore` + 假引擎「下载门闸」单测。
3. 拷贝并改包 Duix 运行时 → 通话页出 Duix 画面（可先用已 push 的模型）。
4. Live2D 同上。
5. Tao 同上（门闸 + 下载）。
6. 删除反射适配与 `settings.gradle` 外链；更新 `docs/dh-lab-models.md`。
7. 真机：无外链、冷 cache 下载、三家切换出画。

## 9. 留给 builder 的决定

- CMake 是单 `CMakeLists.txt` 分目标，还是子目录多份；只要一个 Gradle 模块。
- 下载用 OkHttp 还是现有工程网络栈。
- Tao 从 MnnTaoAvatar 精简时的类边界（以 `AvatarTextureView` 能上屏为最小）。
- 是否在 debug 菜单显示缓存清理按钮（建议有，非必须第一版）。

## 10. 成功标准（验收）

- [ ] 仓库内存在 `:dh-engines`，无需 `-Pdh.lab.native` / 本机 android 树即可编译 chat debug（下载内容可不在编译期出现）。
- [ ] 包名均为 `com.openapi.ks.dh.*`。
- [ ] 通话页：原来的形象默认可见；下拉可切 Duix / Live2D / Tao；资源齐时有画面。
- [ ] 缺资源时有下载或明确失败原因，不出现「点了没反应 / 整页只剩字」。
- [ ] git 中无巨型模型/第三方 `.so` 二进制（文档与 URL 除外）。
- [ ] 外链 include 与三家反射探测路径已删除或不再被 debug 默认走。
