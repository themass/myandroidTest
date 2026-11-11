# 2D 虚拟形象：渲染技术选型与交互设计

> 状态：**提案**。本文只做技术选型与设计，不含实现。
> 核对日期：**2026-11-11**。所有外部事实都附了来源 URL 和核对日期；凭经验推断、没有官方来源支撑的，标了「未验证」。
> **已确认的产品前提（2026-11-11）**：第一阶段**只做 Android**、**不做换装/配饰**，目标是「模拟跟人互动」的陪伴感。iOS 与换装均推迟。结论已按此重写（见 §0）。

## 0. 结论先行

> **前提（2026-11-11 由产品确认）**：第一阶段**只做 Android**，**不做换装/配饰**，目标是「模拟跟人互动」——即让角色看起来是活的、会对你有反应。iOS、换装、UGC 都推迟。

### 这个前提把结论改了

我此前推荐 Spine，主因是两条：**一套资产双端复用** 和 **换装是 skin 字典**。在这个前提下，**这两条都不成立了**：不做 iOS，跨端复用没意义；不做换装，skin/attachment 这套换装模型也用不上。Spine 剩下的优势（骨骼、网格、头发物理）对一个「不做换装」的单角色场景，性价比明显下降——它要 $379、要美术做完整的绑骨+权重+网格（约为 Live2D 的 1.5–2 倍工时），换来的主要是「骨骼级」的细腻度。

### 第一阶段推荐：不买任何运行时，先把现有的分层立绘做厚（零授权成本）

**理由**：在「不做换装」的前提下，「像个人」需要的其实只有六件事——呼吸、眨眼、视线跟随、嘴型、表情、以及一点随机的微动作。**这六件事没有一件必须靠骨骼**。现有的 `SimpleAvatarRenderer` 已经做了呼吸、眨眼、两帧嘴型；再加「瞳孔独立层」（做视线）、「眉毛层」（做表情）和随机微动作，就能覆盖大部分观感，而且：

- **零成本、零授权风险**（自有代码 + 自有美术图）
- **零新增依赖**（不加 `.so`、不加 JVM 库，包体不变）
- **架构已经在了**：`AvatarRenderer` / `AvatarCommand` / `AvatarEventMapper` 都不用改，只加层和参数
- **失败成本极低**：做两周就知道够不够

**代价（必须说清楚）**：分层立绘有「剪纸感」——图层各自平移/缩放，没有整体形变，头发不会随头摆动，转头时脸不会跟着弧面变化。如果美术图和动作幅度控制得保守（小幅呼吸、瞳孔位移 ±3 px、眨眼换图），观感可以很自然；一旦动作大了就会露馅。**这是第一阶段的真实上限，不是实现不到位。**

### 第二阶段（只有第一阶段验证「不够像人」才做）

按优先级：

1. **Live2D**（质量最优）——它是为「二次元立绘呼吸/眨眼/物理」而生的，这一阶段的目标正是它的强项。**但授权要重新判定**：见下方「Live2D 在新前提下的判定」。
2. **Spine**（可控性最优）——如果后续又要加换装，Spine 的 skin 模型仍然是唯一正解，那时再上。$379 可以推迟到那天再花。

**Live2D 在新前提下的判定（比上一版宽松，但仍有不确定性）**：Live2D 的 Expandable Application 条款针对的是「uses and generates **any indefinite numbers of models** by adding or combining files or data (e.g. avatar)」。如果第一阶段**只上线固定数量的官方模型，用户自建角色复用同一批模型**，模型数量是有限的，**按条款字面不构成 expandable**。但官方把 `avatar` 单独作为例子写在那里，无法排除「只要是 avatar 就命中」的解读。**结论：不换装 + 不做 UGC 模型的前提下，Live2D 大概率可以用普通发布授权（年销 < ¥10M 免授权、免费用），但必须在动工前拿到 Live2D 的书面确认。** 这一条我标为**推论，未获官方确认**。

### 与原推荐的关系（避免误解）

本文后面 §1–§5 的技术对比、交互设计、数据模型、架构都没有作废——它们是**第二阶段及以后**的选型依据，也是「为什么第一阶段先这么做」的背景。特别是 §2.2–§2.4（换装、配饰）在现阶段**不实施**，保留是因为产品明确说「前期」不做，不是砍掉。

**一句话**：**现在别花钱。用两周把分层立绘做到「像个人」，拿真机和真人去测；测不过再花钱，那时你也更清楚缺的到底是画质还是动作。**

---



## 1. 方案全景：先分清三类东西

上一版文档只比了「国外商业运行时」。加上你要求的**国内外 + Android/iOS + 免费**三个维度后，候选从 6 个变成 20 多个。但把它们摊开看，其实只有三类，混在一起比会得出错误结论：

| 类别 | 是什么 | 代表 | 能不能做「2D 立绘 + 换装」 |
|---|---|---|---|
| **A. 2D 骨骼/立绘运行时** | 吃分层美术资产，在端上驱动骨骼、网格、皮肤 | Spine、Live2D、Rive、DragonBones、Inochi2D | **能**，这是我们真正需要的 |
| **B. 国内云厂商「数字人」** | 真人视频/照片克隆成会说话的数字人，用来做播报、客服、直播 | 百度曦灵、腾讯云、阿里云、火山、相芯、魔珐… | **不能**。它们是「定制时选一套服装」，不是运行时换装；2D 路线全部是**真人克隆**而非二次元立绘 |
| **C. 免费兜底** | 不引入第三方运行时，用序列帧或自己写一层 | Compose 分层立绘、动画 WebP/APNG、自研骨骼 | **部分能**，能力上限明确但要自己付工程成本 |

**这张分类表本身就是本轮调研最重要的发现**：B 类的八家国内厂商里，**没有一家在卖「移动端 2D 立绘 + 换装」**。把它们当成「语音驱动的嘴型/播报方案」来评估是合理的，但指望它们交付形象方向就是错的。

---

### 1.1 体积基线（先纠正上一版的论证）

上一版我把「体积」当成 Spine 胜出的决定性理由，用的是「Rive 要 5 MB」这个数字（实际增量是 3.68 MB，见下）。**这个论证不成立**，因为我当时没量这个 App 本身有多大。

我实测了仓库现有的 debug APK（`app/build/outputs/apk/chat/debug/app-chat-debug.apk`，2026-11-02 构建）：

| 项目 | 实测值 |
|---|---|
| APK 总大小 | **280 MB**（293,565,503 字节） |
| `lib/arm64-v8a` | **88.1 MB** |
| `lib/armeabi-v7a` | **57.0 MB** |
| 最大单个 native 库 | `libspeechengine.so` 25.3 MB、`libonnxruntime.so` 25.2 MB、`libopencv_java4.so` 20.4 MB |

在这样一个已经有 145 MB native 库的包里，**Rive 的增量只有 `librive-android.so` 的 3.68 MB（arm64 单 ABI）**——因为 `libc++_shared.so` 已经被现有依赖带进来了（我核过包内确实有 `lib/arm64-v8a/libc++_shared.so` 1.23 MB）。3.68 MB 占 280 MB 的 **1.3%**。所以正确的问法不是「5 MB 能不能接受」，而是「这 3.68 MB 换来的画质值不值」——答案可能仍然是值。

同理，Godot 的增量也不是 76 MB，因为它的 4 个 ABI 里我们只用两个，且 `libc++_shared` 已存在；但 `libgodot_android.so` 单 arm64 就 **69.1 MB**，仍是数量级上的不同。

> 注意这是 **debug** 包，`minifyEnabled = false`、`shrinkResources = false`。release 打开 R8 后会小不少（`app/build.gradle` 的 release 配置里两项都是 `true`）。但 native 库不受 R8 影响，**release 的 native 部分仍是这 145 MB**。要严谨，应该以 release/AAB 实测为准，我这里没有构建 release 包（构建耗时长且需要签名），所以标为「debug 实测，release 未测」。

### 1.2 运行时体积（实测 AAR/jar，2026-11-11）

| 运行时 | 版本 | 实测包体 | native `.so` | 备注 |
|---|---|---|---|---|
| `spine-android` | **4.3.5** | AAR **39.0 KB** | **无** | 最新版（上一版记的 4.2.9 已过时） |
| + `gdx` | 1.14.2 | 2.18 MB | 无 | Spine 的传递依赖 |
| + `spine-libgdx` | 4.3.5 | 330 KB | 无 | 同上 |
| **Spine 合计** | 4.3.5 | **≈ 2.54 MB（未混淆）** | **0** | R8 后会进一步缩小 |
| `lottie` | 6.7.1 | 433 KB | 无 | |
| `rive-android` | 10.2.0 | 7.47 MB | arm64 ≈ **4.97 MB**（含 `libc++_shared` 1.29 MB，本包已有，**实际增量 3.68 MB**） | 最新是 11.16.3，我**没有**实测 11.16.3 |
| `godot` | 4.4.1.stable | 102.8 MB | arm64 ≈ **76 MB** | |
| Live2D Cubism Core | — | **未实测** | 有（`android/arm64-v8a`） | 官方只分发二进制、未在公开页面给字节数；我拿不到官方分发包，**不猜** |

### 1.3 资产体积（实测）

运行时要和资产一起算才公平。我用官方 sample 和自造样本各测了一遍：

| 资产 | 实测 | 说明 |
|---|---|---|
| Live2D 官方 sample（Haru，2048 贴图） | **4.28 MB** | 贴图 2.72 MB + `.moc3` 385 KB + 27 个 motion 498 KB + 表情/配置 18 KB |
| Spine 官方 sample（spineboy 全套） | **1.22 MB** | 贴图 0.83 MB + `.skel`/`.json` + atlas |
| 序列帧（自造 1080×1440 透明角色，WebP q82） | **≈ 16 KB/帧** | 2 秒 @12fps ≈ **0.37 MB** |

序列帧看着很省，但**组合爆炸是真的**（同样是实测外推）：

| 配置 | 资产体积 |
|---|---|
| 5 套衣服 × 6 个动作 × 2 秒 | **≈ 11 MB** |
| 10 套衣服 × 8 个动作 × 2 秒 | **≈ 30 MB** |

这正是骨骼方案的价值：Spine/Live2D 的体积**不随搭配数量增长**，因为衣服是 skin 而不是重新出图的整段动画。

---

### 1.4 国外方案对比

| 维度 | Live2D Cubism | Spine | Rive | Lottie | Compose 分层 | 嵌引擎 |
|---|---|---|---|---|---|---|
| **动画质量** | **最高**（二次元立绘专用） | **高**（骨骼+网格+IK+物理） | 中高 | 低（无骨骼） | 低 | 最高（过剩） |
| **Android** | Native(C++) / Java 双变体 | `spine-android`，纯 JVM | Compose API（beta） | Compose API | 原生 Compose | Godot 可嵌 |
| **iOS** | Native(C++)，Metal ✓；**Java 变体不支持 iOS** | `spine-ios`，Swift/ObjC，**Metal**，SPM | `rive-ios` 6.29.0，SwiftUI，Metal | `lottie-ios` 4.6.1 | 需 Compose Multiplatform | Unity UaaL 可（仅全屏）；**Godot 不可** |
| **一套资产双端** | 可（同一代 Core，**官方无明文**） | **可**（官方明确：一份导出喂所有运行时） | **可**（官方主打） | 可 | 需自己定义格式 | Unity 可；Godot iOS 不可 |
| **换装模型** | Part 显隐 / 换贴图 | **Skin 字典，可运行时合并** | 状态机 + 数据绑定 | 无 | 整图替换 | 任意 |
| **包体** | 未实测（有 `.so`） | **2.54 MB，零 `.so`** | 3.68 MB/ABI | 0.4 MB | **0** | 69 MB/ABI |
| **授权** | **见 1.7，本场景无免费选项** | 买编辑器含运行时，$379 | 运行时 MIT，编辑器侧计费 | Apache-2.0 | 自有 | Godot MIT / Unity 见下 |

---

### 1.5 国内方案对比

以下全部来自各厂商官方页面（核对日 2026-11-11）。**没有一家提供「2D 立绘 + 运行时换装」。**

| 厂商 | 2D 还是 3D | 移动端 SDK | 运行时换装/配饰 | 眨眼/呼吸/视线 | 公开价格 | 结论 |
|---|---|---|---|---|---|---|
| **相芯 FaceUnity** | **3D 为主** | Android + iOS + Unity/Web/Windows | **有**（官网写「发型、服饰、配饰」） | **官网无这三项** | **未公开**（只有试用表单） | **唯一值得开试用**，但是 3D，且三项命门能力官网没写 |
| **百度曦灵** | 2D = **真人克隆** | Android/iOS/Windows | 无（整包切换形象） | 未提及 | **全公开**：端渲染应用授权 **36,000 元/个**（限单一系统 20 台） | 跨端要买两份，成本失控 |
| **腾讯云数智人** | 2D = 真人克隆 | Android/iOS/大屏/小程序 | 无 | 未提及 | 端渲染按应用 **800,000 元/年**；按设备 5,000 元/年 | 单价对 To C 不可行；**不含 TTS** |
| **火山引擎** | 2D = 单图生成真人 | Android/iOS/Web | **无** | 无 | **42 万元/年/并发**（云端推流 480P@25fps） | 定位「陪聊」很贴，但云端渲染 + 画质/单价不成立 |
| **阿里云** | 2D = 真人克隆 | Android/iOS | 无 | 未提及 | 端渲染按应用 30 万/年或 0.06 元/分 | **2026-10-11 停止新购，文档停止维护**，直接排除 |
| **魔珐 Xmov** | **全 3D** | Android + 浏览器 JS（**无 iOS 原生**） | 3D 妆造/服饰 | 3D 表情生成 | 积分制，**单价未公开** | 官网 FAQ 与开发文档自相矛盾，风险高 |
| **网易伏羲** | 3D 捏脸 | **无** | 无（「配饰估计」是识别不是换装） | 无 | 无 | 已无数字人产品页，**不构成可采购方案** |
| **硅基智能 / DUIX** | 2D 真人克隆 | Android/iOS（云端版走 WebRTC） | 无 | 无 | 云端 $29–$4999/月 | 开源版要求常驻 "Powered by Duix.com" 且 **1000 MAU 以上须授权**；推荐骁龙 8 Gen 2 + 8GB |

**国内方案的两条共性结论：**
1. **技术上不对口**：它们解决的是「让一个真人形象开口说话」，我们要的是「一个可换装、可被摆弄的二次元角色」。2D 路线在八家里**全部是真人克隆**。
2. **成本模型不匹配**：云端方案按并发/时长计费（火山 42 万/年/并发），端渲染按设备或应用授权（腾讯 80 万/年/应用）。To C 陪伴产品按这个单价无法成立。

---

### 1.6 Android + iOS 双端

这一栏回答了「一个方案能不能同时覆盖两端」——这决定了美术是否要重做一遍。

| 方案 | Android | iOS | 一套资产双端 | 官方依据 |
|---|---|---|---|---|
| **Spine** | ✓ 纯 JVM | ✓ Swift/ObjC + Metal，iOS 13+，SPM | **✓ 官方明确**：「一份导出喂所有 Spine Runtimes，只要求编辑器与运行时版本对齐」 | spine-export、spine-ios README |
| **Rive** | ✓ Compose beta | ✓ SwiftUI，Metal，iOS 14+ | **✓ 官方主打**：「Change the file, and every platform gets the same change」 | rive.app/runtimes |
| **Live2D** | ✓ Java / Native | ✓ **仅 Native**（C++，Metal/OpenGL） | 可，但**官方没有一句「同一份 moc3 跨 OS 复用」的明文**，只能从「导出选的是 SDK 版本不是 OS」+「同一代 Core」推断——我标为**推论** | cubismsdk-compare、platform |
| **Lottie** | ✓ 6.7.1 | ✓ 4.6.1 | ✓ | 两端 README |
| **自研 Compose** | ✓ | 需 Compose Multiplatform | 需自己定格式 | — |
| **Godot** | 可嵌（Android library 官方文档） | **不可嵌**：LibGodot 官方 FAQ 写「目前支持 Windows/macOS/Linux，Android 和 iOS 计划在未来版本」 | — | Godot FAQ |
| **Unity** | 可嵌 | 可嵌，但官方限制：**只能全屏渲染、不能局部渲染**、单实例、Swift 工程不支持 | — | Unity UaaL 文档 |

**两条对产品的硬约束：**

1. **Live2D 的 Android 与 iOS 是两套不同语言的 SDK**（Java vs Native C++）。这意味着渲染层要做两份实现，不能共享代码——只是资产可以共享。这一点上一版完全没提。
2. **Unity 的「不能局部渲染」直接判死**：我们想把角色嵌在聊天页里，不是全屏盖住。Godot 在 iOS 上根本嵌不进去。

**推荐仍然成立，但理由要换。** Spine 在这个矩阵里是唯一同时满足「Android 纯 JVM 零 native」「iOS 有官方 Swift+Metal 运行时」「一套资产双端且官方明文」「换装是 skin 字典」四项的。体积只是附带优点，不是主因。

---

### 1.7 免费路线到底能走到哪

这是你特别问的一栏。先给结论：**「免费」在这件事上不是一个二元条件，而是分层的**——运行时免费、编辑器免费、商用授权免费是三件不同的事。

| 方案 | 运行时代码 | 编辑器/工具 | **商用授权** | 免费能做到什么 |
|---|---|---|---|---|
| **Compose 分层立绘**（现有） | 免费（自有） | 免费（PS 出图） | **免费** | 呼吸缩放、两帧眨眼、两帧嘴型。**做不到视线跟随、骨骼、任意换装** |
| **序列帧 / 动画 WebP** | 免费（`ImageDecoder`，API 28+，仓库 `minSdk=28` 正好够） | 免费（AE/PS 导出） | **免费** | 观感可以很好，但**每个搭配都要重新出帧**：实测 10 套 × 8 动作 ≈ 30 MB，且无法实时视线跟随 |
| **自研骨骼 + Compose Canvas** | 免费（自己写） | 免费 | **免费** | 理论最强，但**要自己写蒙皮、IK、物理**。`DrawScope.drawVertices` 是 **Cmn 跨平台 API**，但 **1.10.0 才有**；仓库当前解析到 **1.9.3**（BOM 2025.09.00）。要升到 BOM 2025.12.00 才拿到 ui 1.10.0，或用 `drawIntoCanvas { it.nativeCanvas }` 绕过 |
| **Lottie** | Apache-2.0 | 免费（AE + Bodymovin） | **免费** | 状态切换动画可以，**无骨骼**，换装只能整图切 |
| **DragonBones** | **MIT**（`DragonBonesCPP`，437★） | 免费 | **免费** | **有现成的 Android Compose 封装**：仓库内 `AndroidCompose/` 提供 `DragonBonesViewCompose`，C++ core + **OpenGL ES** + JNI，`minSdk 26`，README 明确「Composable function / 点击可返回 slot 名」。**这是免费方案里唯一自带骨骼 + Compose 集成 + 点击槽位的一手代码**。但：无 Maven 制品（要作为本地 module 引入），最后一次提交 **2025-07-03**，无 tag/release，成熟度需自己验证 |
| **Inochi2D** | **BSD-2-Clause**（1,844★，2026-11-10 仍有提交） | 免费 | **免费** | 开源 Live2D 替代，但**仓库内搜不到任何 `android`/`ios`/`mobile` 路径**（199 个文件全命中 0），定位是桌面 VTuber。最新正式版 **v0.8.7（2024-10-02）**，之后只有 nightly。**移动端不可用** |
| **LayaAir** | **MIT**（2,234★，2026-11-10 活跃） | 免费 | **免费** | 引擎本身 MIT，但它是完整游戏引擎/编辑器，嵌进现有 App 的成本与 Godot 同级；未找到「嵌入既有 Android App」的官方路径 |
| **pixi-live2d-display** | **MIT**（1,517★） | 免费 | 免费，但**仍需 Live2D 的发布授权** | 在 WebView 里放 Live2D 的常见做法。**但 Live2D 的授权义务不因换运行时而消失**；且 WebView + WebGL 在移动端性能与内存都差，最后提交 **2024-08-20** |
| **Cocos Creator** | 引擎免费开源 | 免费 | 未见抽成 | 用户协议以「developing games」界定用途；`cocos/engine-native` 是**修改版 MIT**（禁止用于造同类引擎）。**未找到允许/禁止非游戏商业用途的明确条款**（未验证） |
| **Rive** | **MIT** | Free 档免费，但**导出带 Rive splash + 每月 50 次发布限额** | 运行时免费、无抽成 | 免费档能做原型，量产要买 Cadet/Voyager |
| **Spine** | 运行时随编辑器授权 | **$379**（Essential $69 不含网格/IK/物理） | 见下 | **不是免费方案** |
| **Live2D** | 运行时需发布授权 | Cubism PRO Indie $89.75/年（FREE 档限制 30 参数/30 部件/100 ArtMesh） | **本项目无免费选项**，见 §1.8 | — |

**如果「免费」是硬约束，可选路线有四条：**

1. **维持 Compose 分层立绘 + 序列帧增强**（零授权风险，工程量最小，能力上限最低）。
2. **DragonBones**（MIT，且有现成的 Compose + OpenGL ES 封装）。**这是免费方案里唯一「今天就能画出骨骼动画」的一条**，但成熟度要自己验：无 Maven 制品、无 tag/release、最后一次提交 2025-07。
3. **自研骨骼层**（零授权风险，能力上限高，但要自己写一个 Spine 的简化版，工期以人月计，且有长期维护负担）。
4. **Rive 免费档起步**（运行时 MIT 最干净，但量产要付费，且要接受编辑器侧的限制）。

**排除掉的免费方案及原因（都核过官方来源）：** Inochi2D（BSD-2-Clause，但仓库里搜不到任何 android/ios 路径，是桌面 VTuber 工具）；pixi-live2d-display（MIT，但**换运行时不能免除 Live2D 的发布授权**，且 WebView+WebGL 在移动端性能差，最后提交 2024-08）；LayaAir/Cocos（引擎免费但都是完整游戏引擎，嵌入成本与 Godot 同级；Cocos 的 `engine-native` 还是修改版 MIT）。

**我的建议**：如果「免费」是为了**先验证产品、暂不投钱**，走第 1 条，把 §6 的 P0 缩成一个「序列帧 + 现有分层」的 demo，用最小成本验证用户是否真的会去换装。**如果验证结果是「会」，那免费路线就撑不住了**——因为序列帧的组合爆炸（实测 10 套 × 8 动作 ≈ 30 MB）和换装的实时性要求，最终一定会把你推回 Spine 或 Rive。先花 $379 还是先花两周自研，取决于你更缺钱还是更缺人。

如果你想在**零预算**下也要「骨骼 + 换装」的效果，第 2 条（DragonBones）是唯一候选，但请把它当成**一次技术验证**而不是确定方案：先花 2–3 天把它的 AndroidCompose demo 跑起来，确认（a）能否作为本地 module 接进本仓库的 Gradle 配置、（b）它的 skin/换装能力是否够用、（c）无 release 维护的项目能否长期用。

---

### 1.8 授权条款（逐条核对，2026-11-11）

**Live2D —— 本项目大概率没有免费选项，这是本轮最重要的发现**

官方把 **avatar** 明确列为 Expandable Application 的示例。原文（`https://www.live2d.com/en/sdk/license/expandable/`）：

> "uses and generates any indefinite numbers of models by adding or combining files or data (e.g. avatar)"

这类应用：

- **不享受**个人/小规模企业豁免。原文：reaches all publishers "including General Users and Small-Scale Enterprises who are normally exempted from the Publication License Agreement for non-Expandable Applications"
- 必须**送审 + 签特别协议**，且「Approval is never guaranteed even when all conditions are met」
- 分成（同页）：

  | 企业规模 | 分成 |
  |---|---|
  | 一般用户 / 小规模（年销 < ¥10M） | **$1.88/份 或 20% 销售额，取高者** |
  | 中型（< ¥100M） | 5% 销售额 |
  | 大型（> ¥100M） | 5% 销售额 |

- 审查标准原文：「as a general rule, fully free of charge is not eligible for approval」——**完全免费的应用原则上不予批准**。

> **对本产品的判断（推论，非官方结论）**：分两种情况，取决于产品范围——
>
> - **若做换装 / 用户自建模型**（本文上一版的前提）：产品「adds or combines files or data」，**基本命中** expandable，Live2D 就是「默认按 expandable 收费、抽 20%」，实操上按**不可用**处理。
> - **若只上线固定数量的官方模型、不做换装**（**当前已确认的前提**）：模型数量有限，按条款字面**不构成** expandable，走普通发布授权即可（年销 < ¥10M 时个人/小规模免授权、免费用）。但官方把 `avatar` 单独列为例子，**仍建议动工前书面确认**。

其他 Live2D 条款（若不走 expandable）：发布前须签 Publication License Agreement，且**发布前至少一个月**完成；商务门槛为年销 ¥10M（按**整个公司**口径）；Running Royalty 中型 $125.68/月/平台/区域、大型 $628.42。Cubism Editor FREE 档限制：30 参数 / 30 部件 / 100 ArtMesh / 1 张 2048 贴图。

**Spine**

- 买编辑器即含运行时，**无抽成、无按量、无 MAU**。Professional **$379**（列表价 $449）；Essential $69 但**不能保存含网格/IK 的项目**，做不了我们需要的能力；Enterprise **$2,499 + $379/人/年**。
- **收入/融资 > $500,000 USD 必须用 Enterprise**（不能再用 Professional）。
- 运行时许可原文有一句有歧义：「each user of the Products must obtain their own Spine Editor license」。我读了 Editor License 第 2 节：触发动作是 "integrating, modifying, or creating derivative works"，主体是**做产品的人**，不是最终玩家；且 2.3/3.3 让分发权在授权失效后仍存续。**结论：不要求每个用户买 Spine，但这是释读，不是原文直述**，金额大时建议书面问 Esoteric。
- 授权失效后，已发布产品中的运行时**可以继续分发**，但新的集成需要有效授权。

**Rive**

- 运行时 **MIT**（`github.com/rive-app/rive-android/blob/master/LICENSE`，Copyright 2020-2024 Rive）。
- 官方定价页原话：「No revenue share, no runtime fees, no royalties, and MIT open-source runtimes」。计费在**编辑器侧**：$9 / $32 / $120 per seat/mo；Free 档导出带 Rive splash、每月 50 次发布限额；Enterprise 要求 $10M+ 年收入。

**Lottie** —— `airbnb/lottie-android` **Apache-2.0**（`lottie-ios` 同）。注意 LottieFiles 的 **dotLottie** 播放器是另一个库，其许可我**在官方文档页未查到**。

**Unity**

- Runtime Fee **已于 2024-09-12 取消，立即生效**，声明「will not apply to any games made with Unity 6 or any other version of Unity」。
- Personal 免费，上限 **$200,000 收入/融资**（2024-10-17 起，从 $100K 提高）；超 $200K 须 Pro（2026 年 $2,310/年/seat）；超 $25M 须 Enterprise。

**Godot** —— MIT。但移动端嵌入不可行，见 §1.6。

**国内厂商** —— 价格见 §1.5 表。共性：**2D 端渲染 SDK 授权普遍不含 TTS**（腾讯原文：「该 SDK 授权不包含 TTS（语音合成服务）」）。

---

### 1.9 第一阶段：分层立绘怎么做到「像个人」（当前要动手的部分）

既然不做换装、不上 iOS，「像个人」就是全部目标。把它拆成可验证的六件事，每件都给出**在现有 `SimpleAvatarRenderer` 上的具体做法**和**美术要求**。这些全部不需要任何第三方运行时。

| # | 要素 | 现状 | 要做到「像个人」需要的 | 美术出图 |
|---|---|---|---|---|
| 1 | **呼吸** | ✅ 已有（缩放 0.6%/1.2%） | 从「整图缩放」改成**分部位**：胸腔层轻微上移 + 肩部层 1–2 px 位移。整图缩放会连脚一起动，看着像镜头在推，不像呼吸 | 把身体拆成「胸腔」和「下半身」两层 |
| 2 | **眨眼** | ✅ 已有（两帧） | 保留，但把间隔改成**非均匀分布**（3–6 秒随机 → 偶尔连续眨两次）。真人眨眼不是等间隔的 | 闭眼图（已有） |
| 3 | **视线跟随** | ❌ 未实现 | **瞳孔单独成层**，在眼白层上做 ±3～4 px 的位移（不是旋转）。这是分层方案里性价比最高的一项——**一个 20 行的 `graphicsLayer` 位移，观感提升最大** | 左右瞳孔各一层（透明底，能叠在眼白上） |
| 4 | **嘴型** | ✅ 两帧 | 保留两帧即可，但**加一个「闭嘴弧线」的微调**：说话时头部随语句轻微点动（±1 px），比只换嘴图自然得多 | 闭嘴/半开/张开三帧（比两帧自然） |
| 5 | **表情** | ✅ 整图替换 | 改成**局部层**：眉毛层、眼睛高光层、脸颊（脸红）层。整图替换会「换脸」，局部层是「表情变了」 | 眉毛 3–4 种、高光 2 种、腮红 1 层 |
| 6 | **微动作（关键）** | ❌ 未实现 | **这一个是「像人」和「像立绘」的分水岭**：每隔 6–15 秒随机做一个小动作——歪头、视线飘走再回来、轻微前倾。不需要新素材，只是现有层的组合位移 | 无（用现有层） |

**为什么第 6 项最重要**：静止的人看起来像照片，一直做同一个循环动画的人看起来像 GIF，**只有「不规律的小动作」才会被大脑读成「有生命」**。这一项纯代码可控，不依赖美术。

**分层方案的物理上限（诚实说）**：
- 头发**不会**随头摆动（没有骨骼，只能做整层平移，平移到一半就会穿帮）
- 转头时脸**不会**有弧面变化（只能整层平移/缩放）
- 大幅度动作（挥手、点头超过 10°）**会明显穿帮**

所以美术和动效要**主动回避**这些：把动作幅度控制在「小」的范围里。这不是妥协——真实的人类在对话时，微动作本来也是小幅的。

**这一阶段的验收方式（建议）**：不是看 FPS，而是**盲测**——录两段 10 秒的循环（一段是现在，一段是做完六项之后），给 5–10 个同事看，问「哪个更像活的」。如果分不出，就不要投入更多。

---

## 2. 交互设计

> **适用范围**：本章描述的是**骨骼运行时（Spine / Live2D）**下的交互做法，属于第二阶段及以后。其中 **§2.1 拖动、§2.5 点击反应、§2.6 视线跟随**在分层立绘下也能做（且成本很低，建议提前到 P0）；**§2.2–2.4 换装**在现阶段**不实施**，保留作为后续设计。

以下以 Spine 为实现目标描述。参数名是 Spine 的术语（`Skin`、`Slot`、`Attachment`、`AnimationState`、`Bone`），对应的 Spine API 我都在官方运行时文档里核对过。分层立绘下的等价做法在每个小节里单独说明。

### 2.1 拖动与重新摆放

**做法：** 拖动不是渲染器内部的事，而是**容器层**的事。Spine 渲染器只负责在一个固定尺寸的画布里画角色；位置、缩放、旋转由外层的 Compose 状态持有：

```kotlin
// 概念示意，不是最终代码
data class AvatarPlacement(val x: Float, val y: Float, val scale: Float, val facing: Facing)

@Composable
fun DraggableAvatar(renderer: AvatarRenderer, modifier: Modifier = Modifier) {
    var placement by rememberSaveable(stateSaver = PlacementSaver) { mutableStateOf(...) }
    Box(
        modifier
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    placement = placement.translatedBy(delta)
                }
            }
    ) { AvatarHost(renderer) }
}
```

要点：
- **坐标系**：拖动发生在容器坐标系；渲染器收到的是「角色在画布内的归一化位置」，与 `AvatarTouch` 已有的 `x/y` 归一化约定一致。
- **边界**：角色不能拖出可视区，但允许部分越界（比如只露出头和肩），用 `constrainTo` 做软约束。
- **和聊天滚动冲突**：如果形象在可滚动的聊天页里，拖动要用 `awaitEachGesture` + 长按阈值（约 300ms）区分「拖角色」和「滚列表」，否则一拖就滚。
- **持久化**：每个角色记住自己的摆放位置，存 SP（用户级偏好），不存服务端——这是设备相关的展示偏好。

### 2.2 把配饰拖到角色身上（drag-and-drop items）

这是「换装」的入口交互。Spine 侧的关键是：**每个配饰是一个 `Slot` + 若干 `Attachment`，而不是一张贴图叠加**。

1. 用户从衣柜面板拖出一个「眼镜」item（Compose 的 `draggable`）。
2. 拖到角色区域时，容器层做**命中测试**：把落点坐标传给渲染器，渲染器反查 Spine 骨架的槽位（`Skeleton.findSlot("glasses")`，或对若干候选槽位做包围盒测试），返回命中了哪个挂点。
3. 命中后，渲染器把该槽位的 attachment 设成这件物品的 attachment；未命中则回退到「最近的可挂载槽位」或拒绝。
4. 视觉反馈：拖动中在候选挂点显示高亮（Compose 层画，不需要渲染器支持）。

```kotlin
// Spine 运行时 API（官方文档核对）
skeleton.setAttachment("glasses", "glasses_round")   // 槽位名, attachment 名
skeleton.skeleton.updateWorldTransform()             // 之后立刻更新
```

- 来源：Spine 运行时文档的 slot/attachment 与 skin 章节，https://esotericsoftware.com/spine-runtime-skins ，核对日期 2026-11-11。

**为什么不用「贴图叠在整图上」：** 角色会呼吸、会转动，贴图叠加不会跟着动，一动就穿帮。走槽位，配饰天然跟着骨头走。

### 2.3 换整套衣服（outfits）

Spine 的 `Skin` 是「槽位占位名 → Attachment」的字典，这正是商品化的衣橱模型：

```kotlin
val outfit = Skin("casual")
outfit.addSkin(skeletonData.findSkin("shirt/pink"))
outfit.addSkin(skeletonData.findSkin("pants/jeans"))
skeleton.skin = outfit
skeleton.setSlotsToSetupPose()
animationState.apply(skeleton)      // 重新应用当前动画的 attachment
```

**顺序很重要**（官方明确写的）：先 `setSkin`，再 `setSlotsToSetupPose()` 复位基础 attachment，再 `animationState.apply(skeleton)` 重新应用当前动画的 attachment，最后重新应用你手动改过的 attachment。如果 `setSkin` 发生在同一帧的 `animationState.apply` 之前，第三步可以省。

- 来源：https://esotericsoftware.com/spine-runtime-skins ，核对日期 2026-11-11。

**这解决了组合爆炸：** 不为「粉衬衫 + 牛仔裤 + 球鞋」出授权/出图，而是三个单品 skin 在运行时合并。官方文档明确把「把多件单品 skin 合成一个新 skin，而不是为每种排列组合都做 skin」列为 skin 的头号用途。

**约束（要写进数据模型）：** 一个骨架同时**只有一个** active skin（`SkeletonData` 的 `default` skin 作为兜底）。所以「多件单品」要靠**合并成一个 skin**，而不是设置多个 skin。合并顺序 = 绘制/覆盖顺序。

### 2.4 加/减配饰（眼镜、帽子）

两种做法，按配饰的「刚性」区分：

| 配饰类型 | 做法 | 理由 |
|---|---|---|
| 刚性、跟着某根骨头走（眼镜、耳环、发卡） | **Slot attachment 切换**：一个 `glasses` 槽位，`none` / `round` / `square` 三个 attachment | 只影响一个槽位，最轻；切换不会打乱其他 skin |
| 会覆盖身体、需要参与皮肤组合（帽子压头发、外套盖衬衫） | **做成单品 skin**，和衣服一起合并 | 需要和其他 skin 一起决定覆盖顺序 |
| 需要独立物理（长发的尾巴、耳坠摆动） | 在 Spine 里给配饰单独加 **physics 约束** | Spine 4.x 的 physics 约束；但要控制数量，物理是 CPU 开销 |

- physics 约束的存在：Spine Professional 功能列表里列了 "physics"（见 §1.8 价格/授权），具体 API 名称我**未逐个核对**，实现时以对应版本的运行时文档为准（未验证）。
- **移除配饰** = 把 attachment 设回 `none`（或设回该槽位的 setup pose attachment）。注意 §2.3 的顺序规则：改完要重新 `apply` 当前动画的 attachment，否则该槽位可能停在旧状态。

### 2.5 点击部位做反应

**做法：** 用 Spine 骨架的**包围盒/槽位包围盒**做命中测试，而不是手写矩形：

```kotlin
// 概念示意：把触点从画布坐标映射到骨架坐标，再问哪个槽位/包围盒命中
val hit = skeleton.boundingBoxes
    .filter { b -> b.name in interactiveRegions }   // "head", "hand", "cheek"
    .firstOrNull { b -> containsPoint(b, localPoint) }
```

- 美术在 Spine 编辑器里为「可点区域」画 包围盒（bounding box），命名 `head` / `hand` / `cheek` 等。这是 Spine 的标准特性，不需要额外代码。
- 命中后发 `AvatarCommand.Gesture("head_pat")`，渲染器播放对应动画。
- **降级**：素材没配包围盒时，退到「归一化坐标落在头部矩形内」的粗略判断。

**这一条要在接口上加东西。** 现有 `AvatarRenderer.onTouch` 返回 `TouchResult.Handled/Ignored`，但**没法回传「点到了哪里」**。建议扩展：

```kotlin
sealed interface TouchResult {
    data object Ignored : TouchResult
    /** 渲染器消化了这个事件，并告知命中了什么，供上层决定反应与埋点。 */
    data class Handled(val region: String? = null) : TouchResult
}

// 新增一条上行指令，供渲染器向状态层报事件
sealed interface AvatarEvent {
    data class RegionTapped(val region: String, val x: Float, val y: Float) : AvatarEvent
    data object DragStarted : AvatarEvent
    data object DragEnded : AvatarEvent
}
```

> 这是**对现有接口的一次破坏性改动**（`Handled` 从 `data object` 变成带参数的 `data class`）。改动很小，但要在同一个提交里改完 `SimpleAvatarRenderer` 和所有实现，否则编译不过。也可以选择只加 `AvatarEvent`、不动 `TouchResult`——我倾向后者，侵入最小。

### 2.6 视线跟手指

**Spine 做法：** 两只眼睛的骨骼（或眼球图层）做**参数化旋转**，把目标点从世界坐标转到头部骨骼的局部坐标，算出角度，再限制在合理范围内：

```kotlin
val eyeBone = skeleton.findBone("eye_L")
val targetLocal = eyeBone.worldToLocal(targetWorld)     // 手指位置的骨架坐标
val angle = atan2(targetLocal.y - eyeBone.worldY, targetLocal.x - eyeBone.worldX)
val clamped = angle.coerceIn(-MAX_GAZE, MAX_GAZE)        // 约 ±25°
eyeBone.rotation = lerp(eyeBone.rotation, degrees(clamped), 0.2f)
```

要点：
- **平滑**：直接赋值会抖，用一阶低通（`lerp`，系数 0.15–0.25）或 Compose 侧的 `animateFloatAsState`。
- **不是每帧都要算**：手指不动时不需要更新，`snapshotFlow` 去重。
- **头部也要跟一点**：只动眼睛会显得诡异，头/颈骨骼加 30% 的转动量、眼睛 100%，符合真实人体的分配。
- **API 名称**：`Bone.worldToLocal` / `Bone.worldX` 这类方法是 libGDX 版 Spine 的 API，Android 运行时基于 libGDX，因此是同一套（这一点从 POM 的 `spine-libgdx` 依赖和 `SkeletonRenderer.java` 的 import 可以确认）。具体签名以运行时版本为准（**未逐个核对**）。

**降级**：分层立绘做不到，`capabilities` 里不声明 `GAZE`，上层就不发。现有的能力协商机制已经处理好了。

### 2.7 待机动画（idle）

**Spine 做法：** 用 `AnimationState` 的分层混合：
- **基础层**：`idle_breath` 循环动画（呼吸的胸/肩起伏、微微的头部晃动）。
- **叠加层**：`idle_blink` 用 `TrackEntry` 播放，`setAlpha` 控制，眨眼不需要打断呼吸。
- **随机事件**：每隔 8–20 秒随机播一个 `idle_look_around` / `idle_fidget`，权重低、播放完自动淡出。

```kotlin
val state = AnimationState(skeletonData)
state.setAnimation(0, "idle_breath", true)          // 循环，track 0
state.addAnimation(0, "idle_look_around", false, delay)  // 插入
```

- 这种「track 0 放基础循环、其他 track 放叠加动画」是 Spine 运行时的标准用法（官方 `AnimationState` 文档，**我未逐页核对具体方法签名**，但 `setAnimation(trackIndex, name, loop)` / `addAnimation` 是稳定多年的 API）。

**呼吸不应该用整图缩放。** 现有 `SimpleAvatar` 用 `graphicsLayer` 缩放整图——这在立绘上是对的（没别的办法），但在骨骼动画里必须用骨骼驱动，否则表情和嘴会跟着漂。

---

## 3. 表情与语音

### 3.1 口型同步（lip sync）

**现状：** `SimpleAvatarRenderer` 收到 `AvatarCommand.Mouth(openness)`，阈值 0.35 转成「张嘴/闭嘴」两帧。这是对的 v1，但只能做「开合」。

**Spine 做法（分层）：**

**第一层：振幅 → 张嘴参数（能立刻做）**
- 从播放中的音频取振幅（RMS 或峰值），归一到 0..1，发 `Mouth(openness)`。
- 渲染器把它映射成 Spine 的**参数动画**：如果美术做了 `mouth_open` 的 `0..1` 形变参数（deform），直接设；如果没有，用三个嘴型 attachment（`closed` / `half` / `open`）按阈值切换。
- **关键实现细节**：音频振幅要平滑（攻击快、释放慢），否则嘴会抖。建议 `openness = max(raw, prev * 0.85f)`。

**第二层：音素驱动（后期）**
- 理想做法是把 TTS 的音素序列（或文字的拼音/音标）映射到 **5–8 个嘴型**（A/I/U/E/O/闭嘴），按时间轴驱动。
- 难点：现在的语音是 LiveKit 收到的音频流，**客户端拿不到音素**。要么让服务端在 TTS 合成时把音素时间轴作为数据通道消息发过来，要么在客户端做轻量强制对齐（成本高、精度低）。
- **建议第三阶段再做**，且作为一个后端契约项（见 §4.3）。

**关于「振幅从哪来」：** `CompanionTransport` 的注释里已经规划了「语音模式另外发布麦克风音轨，并订阅 agent 音轨做振幅驱动嘴型」。实现位置上，振幅分析应该放在**传输层**（拿到 `AudioTrack` 的地方），产出一个 `Flow<Float>`，再喂给 `AvatarCommand.Mouth`。不要在渲染器里读音频——渲染器不应该知道音频怎么来的。

### 3.2 表情驱动

**现状：** `AvatarEventMapper.map("emotion", payload)` → `AvatarCommand.Emotion(name)` → `SimpleAvatarRenderer` 查 `AvatarSpec.expressions` 换整张图。

**Spine 做法：** 表情应该是**参数组合**，不是整图替换：
- 美术定义一组表情参数：`eye_smile`（0..1）、`brow_raise`（-1..1）、`mouth_smile`（0..1）、`cheek_blush`（0..1）。
- 后端发 `emotion: "joy"`，客户端维护一张 `emotion → 参数字典` 的映射（这是**客户端资产**，不是后端协议的一部分）：

```kotlin
// 客户端资产：情绪到参数的映射
"joy"  -> mapOf("eye_smile" to 1f, "mouth_smile" to 0.8f, "cheek_blush" to 0.3f)
"sad"  -> mapOf("brow_raise" to -0.4f, "mouth_smile" to -0.3f)
"calm" -> emptyMap()   // 回到中性
```

- 表情之间**要插值**，不能瞬切。用 Spine 的参数是连续值这一点，做 200–300ms 的过渡。
- **组合**：表情参数和时间轴动画（`AnimationState`）是两套东西，可以同时生效——这比整图替换强得多。

**语言无关性：** 情绪名（`joy` / `sad` / `calm`）是英文标识符，与界面语言无关。这点现有设计已经是对的——`AvatarEventMapper` 只做字符串映射，不解析显示文本。中文/英语/阿拉伯语用户看到的文字不同，但情绪标识符相同。

### 3.3 「说话中」的整体状态

现有 `AvatarState`（IDLE / LISTENING / THINKING / SPEAKING）是对的，Spine 侧建议这样映射：

| 状态 | 动画 | 参数 |
|---|---|---|
| IDLE | `idle_breath` 慢速循环 | 呼吸幅度 0.6 |
| LISTENING | `idle_listen`（头部微前倾、偶尔点头） | 视线偏向用户 |
| THINKING | `think`（视线略微上飘、手托下巴可选） | 眨眼频率提高 |
| SPEAKING | `talk_base` 循环 + 嘴型层 | 呼吸幅度 1.2，头部随语义轻微摆动 |

**打断**：用户开口要从任意状态回 `LISTENING`，动画要 crossfade（300ms），不能硬切。

---

## 4. 数据模型与后端契约

### 4.1 形象配置的数据模型

两个维度：**角色级**（这个角色长什么样）和**用户级**（我给她买/穿/解锁了什么）。

```kotlin
/** 角色级：由角色作者/官方定义，所有用户共享。只读。 */
data class AvatarDefinition(
    val id: String,                  // "role_1024_default"
    val skeletonUrl: String,         // .skel 或 .json（Spine 导出）
    val atlasUrl: String,            // .atlas + 贴图
    val version: Int,                // 用于缓存失效
    val slots: Map<String, SlotDef>, // 槽位名 → 可挂载项
    val skins: Map<String, SkinDef>, // 单品 skin（衣服）
    val animations: List<String>,    // 可用的动画名
    val boundingBoxes: List<String>, // 可点区域
    val emotionParams: Map<String, Map<String, Float>>,  // 情绪 → 参数
)

data class SlotDef(
    val name: String,                // "glasses"
    val kind: SlotKind,              // HEAD / FACE / BODY / HAND
    val defaultAttachment: String?,  // null = 空着
)

data class SkinDef(
    val id: String,                  // "shirt/pink"
    val layer: Int,                  // 覆盖顺序
    val tags: List<String>,          // ["tops"]
)

/** 用户级：每个用户对每个角色的配置。可变，需要同步。 */
data class AvatarConfig(
    val characterId: String,
    val baseSkinId: String,
    val equippedSkins: List<String>,          // 合并顺序
    val slotAttachments: Map<String, String>, // 槽位 → attachment
    val unlockedItems: Set<String>,           // 已解锁/已购买
    val updatedAt: Long,
)
```

**存储位置：**

| 数据 | 存在哪 | 理由 |
|---|---|---|
| `AvatarDefinition` | 服务端（角色卡旁边），客户端按 `version` 缓存到磁盘 | 全用户共享，体积大，要能热更新 |
| `AvatarConfig` | 服务端（用户维度），客户端 Room/SP 缓存 + 离线队列 | 跨设备同步；付费物品必须是服务端权威 |
| 角色摆放位置、界面偏好 | 仅本地 SP | 设备相关，不值得同步 |
| `unlockedItems` | **只在服务端**，客户端只读缓存 | 涉及付费，客户端不可信 |

**同步策略：**
- 读：进陪伴页时拉一次 `AvatarConfig`，与本地比较 `updatedAt`；服务端新则覆盖。
- 写：换装操作**立即本地生效**（乐观更新），异步 PATCH 到服务端；失败则回滚并提示。
- 冲突：以服务端 `updatedAt` 为准，不做 merge（换装是低频操作，冲突概率低，merge 复杂度不值得）。

**素材缓存：**
- 按 `AvatarDefinition.version` 做目录隔离：`cache/avatar/<id>/<version>/`。
- 新版本下载完再切换指针，避免半旧半新。
- 走现有 Glide/OkHttp 缓存策略不可行（`.skel` / `.atlas` 不是图片），需要一个小的文件下载器 + LRU 清理（比如上限 100 MB）。

### 4.2 后端契约提案

**注意：这是客户端侧的提案，后端在设计别处。** 现有仓库里两条通道并存：

1. **`feature-chat` 的 SSE**：`ChatStreamClient` 用 OkHttp 读 `event-stream`，帧模型是 `ChatStreamEvent`（`Delta` / `Done` / `Error`），对应后端 `/chat/stream.json` 的 `StreamChunk{id, sessionId, delta, done, error}`。
2. **`feature-companion` 的 LiveKit**：数据通道 `companion.avatar` 上发 JSON，`AvatarEventMapper.mapJson` 只认 `{"type":"state"}` 和 `{"type":"emotion"}`。

**两条通道应该用同一套形象事件 schema**，否则服务端要维护两份。建议：

```json
{"v":1, "type":"state",   "state":"speaking"}
{"v":1, "type":"emotion", "emotion":"joy", "intensity":0.8}
{"v":1, "type":"gesture", "id":"wave"}
{"v":1, "type":"gaze",    "target":"user"}          // 或 {"x":0.3,"y":-0.2}
{"v":1, "type":"mouth",   "openness":0.42}          // 仅服务端能拿到音素时
```

设计约束（都是为了让客户端能安全降级）：
- **未知 `type` 必须被忽略**，不能断流——现有 `AvatarEventMapper` 已经这么做了，保持。
- **`v` 字段用于演进**：将来加 `intensity` / `duration` 不破坏旧客户端。
- **情绪用英文标识符**，不用自然语言，不用 emoji。
- **不要发「渲染指令」，只发「语义」**。服务端说「她开心」，不说「播放 animation_joy_02」——动画是客户端资产。
- **SSE 侧**：建议在 `StreamChunk` 里加一个平级的 `avatar` 数组字段，而不是混进 `delta` 文本里。这样现有的 `ChatStreamClient` 只需要多解析一个字段，`ReplyStreamer` 不用动。

```json
{"id":"m1","sessionId":12,"delta":"今天……","avatar":[{"type":"emotion","emotion":"shy"}]}
```

- **LiveKit 侧**：保持 `companion.avatar` topic 不变，只是 payload 多了几个 `type`。

### 4.3 音素时间轴（远期契约）

如果要做音素级口型，服务端需要在 TTS 合成时产出：

```json
{"type":"viseme_timeline", "startMs":0, "frames":[{"t":0,"v":"A","d":80},{"t":80,"v":"M","d":60}]}
```

- 这个应该和音频**同一条时间基准**发出（在数据通道里带 `startMs`，与音频流对齐）。
- **风险**：LiveKit 的音频有抖动缓冲，音素时间轴和实际听到的声音会有偏移。需要实测对齐误差（**未验证**）。
- **建议**：第一版不做。振幅驱动的口型已经能覆盖 80% 的观感需求，音素级是「锦上添花」。

---

## 5. App 内架构

### 5.1 模块放置

现状（`settings.gradle` + `app/src/test/.../ModuleGraphTest.kt` 的约束）：
- `:feature-companion` 已存在，`avatar/` 包已在里面。
- 架构测试规定：**feature 模块之间不互相依赖**（只允许 `feature-chat → feature-roles`）；`:core`、`:common`、`:uikit` 等不依赖 feature 模块。

**建议：**

```
:feature-companion
  └── com.openapi.ks.myapp.avatar            ← 现有，保留
        ├── AvatarRenderer.kt                接口、AvatarSpec、AvatarTouch、AvatarCommand
        ├── AvatarEventMapper.kt             协议 → 指令（唯一出现协议的地方）
        ├── SimpleAvatarRenderer.kt          v1 兜底实现
        ├── SimpleAvatar.kt                  对应 Composable
        └── spine/                            ← 新增
              ├── SpineAvatarRenderer.kt     AvatarRenderer 的 Spine 实现
              ├── SpineAvatar.kt             AndroidView / Canvas 封装
              ├── SpineAssetLoader.kt        下载、解压、缓存 .skel/.atlas/贴图
              └── EmotionParams.kt           情绪 → Spine 参数映射
```

**要不要单独开一个 `:feature-avatar` 模块？** 我的判断是**现在不要**，理由：
- 目前只有 `feature-companion` 用形象。开新模块会立刻撞上 `ModuleGraphTest` 的「feature 之间不互相依赖」规则——`:feature-companion → :feature-avatar` 需要新增一条批准边。
- 什么时候该拆：当 `feature-chat` 也要显示形象（比如聊天页顶部的小头像），或者形象要被两个以上 feature 共用时。那时拆成 `:feature-avatar` 是合理的，且这种拆分是**纯移动**，成本低。
- 如果拆，Spine 相关的 native 依赖也会集中在那一个模块，避免污染其他模块。

**`:feature-companion` 的依赖增量：**
```gradle
implementation 'com.esotericsoftware.spine:spine-android:4.3.5'
```
（会连带引入 `gdx` 和 `spine-libgdx`。）

### 5.2 渲染层抽象（可换引擎）

现有 `AvatarRenderer` 已经是对的，**不需要重设计**。实现 Spine 时遵守它现有的约定：

| 约定（来自现有 README） | Spine 实现如何遵守 |
|---|---|
| `bind(spec)` 建资源 | 从 `AvatarSpec` 里取 Spine 资产路径（需要**扩展** `AvatarSpec`，见下） |
| `dispatch(command)` 更新状态 | 状态放 `StateFlow`，不发指令给 `AnimationState` 以外的东西 |
| `release()` 停掉所有定时器和纹理 | 必须释放 `Skeleton`、`Texture`、`AnimationState`，否则贴图泄漏 |
| 状态用 `StateFlow` 暴露，插值留给 Composable | Spine 的插值在 `AnimationState.update(delta)` 里，**不是** Compose 动画时钟——这是与 README 指引的一处**有意偏离**，因为骨骼动画必须由渲染器自己的时钟驱动 |
| `capabilities` 按实际能力填 | Spine 实现声明 `STATE, EMOTION, MOUTH, GAZE, GESTURE, TOUCH_DRAG` 全集 |

**`AvatarSpec` 需要扩展。** 现有字段（`portraitRes`、`expressions`、`mouthClosedRes` 等）全是 `@DrawableRes Int`，只对「本地图片」成立。Spine 需要的是磁盘上的资产路径 + 字号。建议：

```kotlin
data class AvatarSpec(
    // ... 现有字段保留，作为降级素材
    val engine: AvatarEngine = AvatarEngine.LAYERED,

    /** Spine 资产；engine != SPINE 时为 null。 */
    val rig: AvatarRig? = null,
)

enum class AvatarEngine { LAYERED, SPINE }

data class AvatarRig(
    val skelPath: String,            // 磁盘绝对路径
    val atlasPath: String,
    val defaultSkin: String,
    val defaultAnimation: String,
)
```

- 保持向后兼容：`engine` 默认 `LAYERED`，`rig` 默认 `null`，现有代码不用改。

### 5.3 交互与状态层

```
┌──────────────────────────────────────────────┐
│ UI (Compose)                                 │
│  · 拖动/缩放的 placement 状态                 │
│  · 衣柜面板、配饰拖放                         │
└──────────────┬───────────────────────────────┘
               │ AvatarCommand（下行）
               │ AvatarEvent（上行，新增）
┌──────────────▼───────────────────────────────┐
│ AvatarSession（ViewModel 层，新增）           │
│  · 持有 AvatarRenderer                        │
│  · 合并多来源指令：聊天情绪、音频振幅、触摸   │
│  · 节流：Mouth 最多 30Hz，Gaze 最多 20Hz      │
│  · 生命周期：onStart bind / onStop release    │
└──────────────┬───────────────────────────────┘
               │
    ┌──────────┼──────────┬─────────────┐
    ▼          ▼          ▼             ▼
 CompanionEvent  AudioAmplitude  Touch    AvatarConfigRepository
 (情绪/状态)     (嘴型)         (点击/拖动)  (换装持久化)
```

**为什么要一个 `AvatarSession`：** 现在有多路输入同时驱动形象（后端情绪、音频振幅、手指触摸），如果每路都直接调 `renderer.dispatch`，优先级和节流会失控。`AvatarSession` 是唯一的入口，负责：
- **优先级**：`SPEAKING` 期间忽略 `Emotion` 之外的一切？不——情绪要在说话时也生效。规则：状态指令优先于动画叠加，触摸反应可以打断一切。
- **节流**：`Mouth` 从音频 44.1kHz 降采样到 ~30Hz，否则每帧一次 StateFlow 写入会掉帧。
- **不持有 Compose 时钟**：Session 是纯 Kotlin，可单测。

### 5.4 资源加载与缓存

```
AvatarAssetRepository
  ├─ 磁盘缓存：filesDir/avatar/<id>/<version>/{model.skel, model.atlas, *.png}
  ├─ 内存缓存：加载后的 SkeletonData（LRU，最多 2 个角色）
  └─ 下载：OkHttp，支持断点续传、校验和
```

要点：
- **SkeletonData 要缓存复用**。Spine 的 `SkeletonData` 解析 `.skel` + `.atlas` + 贴图，耗时在几十毫秒量级（**未验证**，需实测）。同一角色重复进出页面时不应该重复加载。
- **`Skeleton` 要每次新建**。`SkeletonData` 是只读的共享数据，`Skeleton` 是可变的实例。一个 `SkeletonData` 可以创建多个 `Skeleton`（比如聊天列表里的小头像）。
- **贴图用 `Texture` + `TextureAtlas`**，不要每帧重新上传。
- **预加载时机**：进入陪伴页时预加载当前角色；切换角色时后台预取下一个。

### 5.5 内存与电量预算

| 项 | 预算 | 依据 |
|---|---|---|
| 单个 Spine 角色贴图 | ≤ 4 MB（2048×2048 RGBA 的一半） | 二次元角色常见的 atlas 尺寸；**未验证**，取决于美术出图 |
| SkeletonData 内存缓存 | ≤ 2 个角色，约 8–12 MB | 含贴图 |
| 渲染帧率 | 60 FPS 目标，30 FPS 下限 | 低于 30 会明显卡顿 |
| **空闲时停帧** | 无输入且动画在循环时，降到 30 FPS | 陪伴页可能长时间开着 |
| **不可见时停止** | `onStop` / `Lifecycle.State.RESUMED` 之外一律暂停 | 这是电量最大的单项 |
| 耗电 | 前台连续 1 小时 ≤ 8% 电量（中端机） | **未验证**，需要实测定基线 |

**关键的省电措施：**
1. **生命周期绑定**。`AvatarSession` 在 `ON_PAUSE` 时 `release()` 或至少暂停 `update()`。现有的 `release()` 契约已经要求停掉定时器。
2. **按需降帧**。`IDLE` 状态 30 FPS 足够（呼吸很慢）；`SPEAKING` 才需要 60 FPS（嘴型）。
3. **不要用 `invalidate()` 自驱循环**。用 `Choreographer` 或 Compose 的 `withFrameNanos`，并且在没有动画变化时主动停。

---

## 6. 分阶段原型计划

> **计划已按「仅 Android、不做换装」重排。** 下面的 P0–P1 是**现在要做的**；P2 之后属于「如果第一阶段验证不够」的后续选项，现阶段不启动。

### P0：把分层立绘做到「像个人」（2 周，零成本，现在做）

**目标：回答一个产品问题——不引入任何运行时，靠分层立绘能不能让用户觉得「她在跟我互动」。**

这一步**只用现有技术栈**：`SimpleAvatarRenderer` + Compose `graphicsLayer` + 美术出图。**不加任何依赖**。

要做的六件事（详细规格见 §1.9）：

| # | 要素 | 工作量 | 依赖 |
|---|---|---|---|
| 1 | 呼吸改为分部位（胸腔/下半身分层） | 1 天 | 美术拆层 |
| 2 | 眨眼间隔非均匀化 + 偶发连眨 | 0.5 天 | 无 |
| 3 | **瞳孔独立层 + 视线跟随** | 2 天 | 美术出瞳孔层 |
| 4 | 嘴型三帧 + 说话时头部微点动 | 1 天 | 美术出第三帧 |
| 5 | 表情改为局部层（眉/高光/腮红） | 3 天 | 美术出层 |
| 6 | **随机微动作调度器**（歪头/移开视线/前倾） | 2 天 | 无 |

**成功标准（这是本阶段唯一重要的指标）：**

| 指标 | 门槛 | 怎么测 |
|---|---|---|
| **盲测「更像活的」** | ≥ 7/10 人选择新版 | 录两段 10 秒循环，给 10 个同事二选一 |
| 拖动/滚动掉帧 | 无（现有分层就是几张 Image） | 手动 |
| 新增包体 | ≈ 美术资源大小（预计 < 2 MB） | 对比构建前后 |

**如果 P0 不过**（盲测分不出，或用户仍说「像贴图」）：说明缺的是**形变能力**，不是素材。这时才进入 P1。

**为什么把「像不像人」当验收而不是 FPS**：FPS 在分层方案里不可能是瓶颈（就是几张图），真正的风险是**观感不够**。用错指标会做出一个流畅但没人愿意看的角色。

### P1：形变升级（仅当 P0 判定「不够」时启动，2–3 周验证）

按 §0 的顺序选一个：

- **优先 Live2D**：它的强项正是「立绘的呼吸/眨眼/物理」，与本阶段目标完全重合。**动工前必须先拿到 Live2D 的书面授权确认**（见 §0 对该条款的判定）。
- **其次 Spine**：如果后续又要做换装，Spine 仍是最优；否则它的绑骨工时是为用不上的能力付费。

**如果 P1 选 Live2D**：沿用现有 `AvatarRenderer` 抽象新增 `Live2DRenderer`，上层不动。性能实测项沿用下面这套指标：

| 指标 | 门槛 |
|---|---|
| 中端机 FPS | ≥ 55（P95 帧时间 ≤ 20ms） |
| 低端机 FPS | ≥ 30 |
| 单角色内存增量 | ≤ 15 MB |
| 冷启动加载时间 | ≤ 300ms |
| APK 增量 | ≤ 3 MB |

**如果 P1 选 Spine 且性能不达标**：退到 Rive（原生 C++ 渲染器；代价是 +3.68 MB/ABI 和美术管线要重学）。

**怎么测**：用 `adb shell dumpsys gfxinfo` + Macrobenchmark（`androidx.benchmark`）拿帧时间分布，不要只看「感觉流畅」。本仓库已有 `app/src/test` 下的测试基础，Macrobenchmark 可以放 `:app` 的 `androidTest`。

### P2：交互与换装（**现阶段不做**，产品明确「前期不换装」）

以下内容保留在文档中作为后续设计，**不在当前排期内**：

- **拖动与摆放**（§2.1）：容器层手势 + 持久化。——**这一项可以考虑提前**，因为「能拖动她」本身就是互动感的一部分，且零成本。
- **换装**（§2.2–2.4）：衣柜面板、Skin 合并、Slot attachment 切换。——**推迟**。
- **点击部位**（§2.5）：包围盒命中 + 反应动画。——**建议提前**，理由同拖动：这是「互动」而非「换装」。
- **视线跟随**（§2.6）。——**已在 P0 第 3 项实现**。

### P3：表情与语音（**部分现在做**）

- 情绪参数映射 + 插值（§3.2）。——P0 第 5 项已覆盖基础版。
- 音频振幅 → 嘴型（§3.1 第一层）。——**这一项建议现在做**：`Mouth(openness)` 接口已经在了，缺的只是从音频取振幅。它直接决定「说话时像不像真的在说」。

**成功标准：**
- 情绪切换无跳变（主观）。
- 嘴型与音频的**感知同步**：主观测试「嘴型和声音对不上」的比例 < 20%。
- 端到端延迟：后端发 `speaking` 到动画开始 ≤ 150ms。

### P4：打磨与量化（持续）

- 音素级口型（§3.3，可选）。
- 动画的细节：头发物理、衣服摆动。
- 长时间运行的稳定性：连续 1 小时的内存曲线是否平稳（排查泄漏）。

### 每个阶段的验收方式

| 维度 | 怎么量 |
|---|---|
| FPS | Macrobenchmark `FrameTimingMetric`，看 P50/P90/P99 |
| 内存 | Android Studio Profiler + `dumpsys meminfo`，对比有无形象 |
| APK 增量 | `./gradlew :app:assembleRelease` 前后对比 APK 与 AAB 大小 |
| 电量 | Battery Historian，连续 1 小时陪伴页 |
| 启动 | `ActivityManager` 的 `Displayed` 时间 |

**建议加一个 CI 门槛：** APK 增量超过约定值时构建失败。这个仓库已经有 `ModuleGraphTest` 这种架构测试，加一个体积检查是同一思路。

---

## 7. 风险与未决问题

> 已按「仅 Android、前期不换装」重新标定。**当前阶段真正的风险只有一个**：分层立绘的观感上限（「像贴图」），其余都是后续阶段的。

| 风险 | 影响 | 缓解 |
|---|---|---|
| **分层立绘「不够像人」** | **高（当前阶段唯一的关键风险）**——剪纸感、头发不摆动、大动作穿帮 | P0 用盲测判定；美术把动作幅度控制在「小幅」；不过则进 P1 换 Live2D |
| **美术拆层工作量** | 中——瞳孔/眉毛/腮红/胸腹拆层，比出一张整图多 | 先只拆「瞳孔 + 眉毛」两层（性价比最高的两项） |
| Live2D 的 avatar 条款 | **中**（降级）——不做换装、不做 UGC 模型时，按条款字面**不构成** expandable | 若 P1 选 Live2D，**动工前拿书面确认**；否则按年销 < ¥10M 的普通发布授权即可 |
| Spine 性能 | 低（降级）——现阶段不引入 Spine | 若后续要做换装再评估 |
| Spine Enterprise 阈值 $500K | 低（降级） | 可预测，可预算 |
| iOS 迁移成本 | 低（已明确推迟） | 目前是 100% Android 仓库，iOS 是全新工程；产品已接受「先走通 Android」 |
| 音素口型的音画同步 | 低 | 第一版只做振幅驱动，不做音素 |
| APK 体积 | 低——分层方案增量 ≈ 美术资源（预计 < 2 MB） | 注意当前包已 280 MB（native 145 MB），但与本方案无关 |
| 用户上传/自定义形象 | 未评估 | 已推迟；若要做需要单独的资产审核与安全设计 |

**需要业务方决定的（已按新前提收敛）：**
1. **P0 的判定期望**：盲测「更像活的」要达到多少才继续？建议 ≥ 7/10；如果产品期望更高，就应该直接从 P1（Live2D）起步，跳过 P0。
2. **「前期」大概多久？** 如果换装会在 3 个月内回来，就不该在分层方案上投太多美术，应该直接上 Live2D/Spine。这是现在最值得确认的一条。
3. **角色数量级**：10 个精修 vs. 500 个。分层方案对数量不敏感（每个角色多几张图），但 Live2D 每个角色都要重做一遍绑定。
4. ~~是否现在就要为 iOS 做准备~~ → **已决定：先走通 Android，iOS 推迟。**
5. ~~换装的商业模型~~ → **已决定：前期不做换装。**

---

## 8. 附：本文所有外部来源

| # | 来源 | 用于 | 核对日期 |
|---|---|---|---|
| 1 | https://www.live2d.com/en/sdk/license/ | Live2D 发布授权、个体/小规模豁免、Expandable Application、AI/chatbot 路径 | 2026-11-11 |
| 2 | https://help.live2d.com/en/license/license_24/ | Live2D 商务授权 ¥10M 阈值、整个公司口径 | 2026-11-11 |
| 3 | https://www.live2d.com/en/sdk/license/running_plan01/ | Live2D Running Royalty 费率表 | 2026-11-11 |
| 4 | https://www.live2d.com/en/sdk/license/purchase_plan02/ | Live2D One-time Purchase 费率 | 2026-11-11 |
| 5 | https://store.live2d.com/en/ | Cubism Editor PRO 价格、Indie/Business 功能相同 | 2026-11-11 |
| 6 | https://docs.live2d.com/en/cubism-sdk-manual/cubismsdk-compare/ | Java SDK 仅支持 Android；掩码上限 36 | 2026-11-11 |
| 7 | https://en.esotericsoftware.com/spine-purchase | Spine 价格、$500K Enterprise 阈值、运行时权利 | 2026-11-11 |
| 8 | https://en.esotericsoftware.com/spine-runtimes-license | Spine 运行时授权协议 | 2026-11-11 |
| 9 | https://esotericsoftware.com/spine-runtime-skins | Skin/Attachment 模型、换装用法与顺序 | 2026-11-11 |
| 10 | Spine 官方 Import PSD（经 https://github.com/EsotericSoftware/spine-scripts 引用） | Spine 支持直接导入 PSD，比旧脚本快最多 5 倍 | 2026-11-11 |
| 11 | https://github.com/rive-app/rive-android/blob/master/LICENSE | Rive 运行时 MIT | 2026-11-11 |
| 12 | https://rive.app/pricing | Rive 定价、无抽成、Free 档水印与发布限额 | 2026-11-11 |
| 13 | https://rive.app/docs/runtimes/android/android | Rive Compose API（beta）、初始化方式 | 2026-11-11 |
| 14 | https://github.com/rive-app/rive-android/releases | rive-android 版本（11.16.3） | 2026-11-11 |
| 15 | https://unity.com/products/pricing-updates | Unity Runtime Fee 取消、Personal $200K 阈值、Pro 价格 | 2026-11-11 |
| 16 | https://unity.com/blog/unity-is-canceling-the-runtime-fee | Runtime Fee 取消公告 | 2026-11-11 |
| 17 | https://docs.unity3d.com/6000.5/Documentation/Manual/UnityasaLibrary.html | Unity as a Library 仍然有文档 | 2026-11-11 |
| 18 | https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html | Godot Android 库；单实例与 resize 限制 | 2026-11-11 |
| 19 | https://github.com/airbnb/lottie-android | Lottie Apache-2.0 | 2026-11-11 |
| 20 | https://docs.lottiefiles.com/en/runtimes/distributions/android | dotLottie Android Player（许可**未查到**） | 2026-11-11 |
| 21 | https://www.live2d.com/en/sdk/license/expandable/ | **Expandable Application 定义、`(e.g. avatar)` 示例、分成费率、免费应用不予批准** | 2026-11-11 |
| 22 | https://help.live2d.com/en/sdk/sdk_001/ | 小规模豁免范围；Expandable Application 不适用豁免 | 2026-11-11 |
| 23 | https://www.live2d.com/en/cubism/comparison/ | Cubism Editor FREE 档限制（30 参数 / 30 部件 / 100 ArtMesh） | 2026-11-11 |
| 24 | https://docs.live2d.com/en/cubism-sdk-manual/platform/ | Native 支持 iOS + Metal/OpenGL；**Java 仅 Android** | 2026-11-11 |
| 25 | https://esotericsoftware.com/spine-ios | spine-ios 是 Swift/ObjC + **Metal**，iOS 13+，SPM | 2026-11-11 |
| 26 | https://esotericsoftware.com/spine-export | **「一份导出喂所有运行时，只要求版本对齐」**（跨端复用依据） | 2026-11-11 |
| 27 | https://en.esotericsoftware.com/spine-editor-license | 第 2 节：集成/分发权利，主体是开发者不是终端用户 | 2026-11-11 |
| 28 | https://docs.godotengine.org/en/stable/about/faq.html | **LibGodot 目前仅 Windows/macOS/Linux，Android/iOS「计划中」** | 2026-11-11 |
| 29 | https://docs.unity.com/en-us/engine/6000.6/manual/platform-specific/iphone/ios-developing/unityasa-library-ios | UaaL iOS **只支持全屏渲染**、单实例限制 | 2026-11-11 |
| 30 | https://rive.app/runtimes | 「Change the file, and every platform gets the same change」 | 2026-11-11 |
| 31 | https://github.com/rive-app/rive-ios | rive-ios 6.29.0，MIT，Metal，SwiftUI | 2026-11-11 |
| 32 | https://github.com/airbnb/lottie-ios | lottie-ios 4.6.1，Apache-2.0 | 2026-11-11 |
| 33 | https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/Canvas | `DrawScope.drawVertices` 标记 **Cmn**，**1.10.0** 起 | 2026-11-11 |
| 34 | https://dl.google.com/android/maven2/androidx/compose/compose-bom/maven-metadata.xml | BOM 2025.09.00 → ui **1.9.3**；2025.12.00 → ui **1.10.0**（实测 POM） | 2026-11-11 |
| 35 | https://developer.android.com/reference/android/graphics/ImageDecoder | `ImageDecoder`/`AnimatedImageDrawable` API 28+（仓库 `minSdk=28` 满足） | 2026-11-11 |
| 36 | https://support.google.com/googleplay/android-developer/answer/9859372 | Google Play：base module 500 MB、全部模块+安装期资源包 4 GB | 2026-11-11 |
| 37 | https://developer.huawei.com/consumer/cn/doc/app/agc-help-releaseapkrpk-0000001106463276 | 华为 AppGallery 支持 AAB；APK/RPK 上限 4 GB | 2026-11-11 |
| 38 | https://www.faceunity.com/avatarxsdk.html | 相芯：端侧+云端、**服饰/配饰**、51 维表情；**无眨眼/呼吸/视线** | 2026-11-11 |
| 39 | https://xiling.cloud.baidu.com/doc/AI_DH/s/Vlysgbz88 | 百度曦灵价格：端渲染应用授权 36,000 元/个（单系统 20 台） | 2026-11-11 |
| 40 | https://cloud.tencent.cn/document/product/1240/101944 | 腾讯云 2D 端渲染：按设备 5,000 元/年、按应用 800,000 元/年；**不含 TTS** | 2026-11-11 |
| 41 | https://docs.volcengine.com/docs/large-model-of-image-generation/real-time-interactive-digital-human-sdk-product-introduction | 火山 FlowAct-R1：云端推流 480P@25fps、42 万元/年/并发 | 2026-11-11 |
| 42 | https://help.aliyun.com/zh/avatar/product-overview/ | 阿里云数字人：**2026-10-11 停止新购**、文档停止维护 | 2026-11-11 |
| 43 | https://www.xingyun3d.com/support/51-182 | 魔珐星云计费（积分制、单价未公开）；开发文档仅 Android + JS，无 iOS 原生 | 2026-11-11 |
| 44 | https://fuxi.163.com/ | 网易伏羲：无数字人产品页、无定价、无移动端说明 | 2026-11-11 |
| 45 | https://github.com/duixcom/Duix-Mobile | Duix Mobile：社区许可证要求常驻 "Powered by Duix.com"，**1000 MAU 以上须授权** | 2026-11-11 |
| 46 | 实测：`app/build/outputs/apk/chat/debug/app-chat-debug.apk` | APK 280 MB；arm64 88.1 MB、armeabi-v7a 57.0 MB | 2026-11-11 |
| 47 | 实测：Maven Central `spine-android:4.3.5` + `gdx:1.14.2` + `spine-libgdx:4.3.5` | 39.0 KB + 2.18 MB + 330 KB ≈ **2.54 MB**，零 `.so` | 2026-11-11 |
| 48 | 实测：Live2D 官方 sample `Haru.2048`（GitHub API 逐个文件取 size） | 合计 **4.28 MB**（贴图 2.72 MB、moc3 385 KB、motions 498 KB） | 2026-11-11 |
| 49 | 实测：Spine 官方 sample `examples/spineboy/export` | 合计 **1.22 MB** | 2026-11-11 |
| 50 | 实测：自造 1080×1440 透明角色序列帧，`cwebp -q 82` | **≈16 KB/帧**；2 秒@12fps ≈ 0.37 MB；10 套×8 动作 ≈ 30 MB | 2026-11-11 |
| 51 | https://github.com/DragonBones/DragonBonesCPP | **MIT**；`AndroidCompose/` 提供 Compose + OpenGL ES + JNI 封装（`DragonBonesViewCompose`），`minSdk 26`；最后提交 2025-07-03，无 tag/release | 2026-11-11 |
| 52 | https://github.com/DragonBones/DragonBonesCPP/blob/HEAD/AndroidCompose/README.md | 「Jetpack Compose components for rendering DragonBones…uses C++ and OpenGL ES」「Interaction Support: getting the name of a tapped slot」 | 2026-11-11 |
| 53 | https://github.com/Inochi2D/inochi2d | **BSD-2-Clause**，1,844★；仓库内无 android/ios/mobile 路径；最新正式版 v0.8.7（2024-10-02） | 2026-11-11 |
| 54 | https://github.com/layabox/LayaAir | **MIT**，2,234★，2026-11-10 活跃 | 2026-11-11 |
| 55 | https://github.com/guansss/pixi-live2d-display | **MIT**，1,517★，最后提交 2024-08-20；用于 WebView 方案，**不免除 Live2D 授权** | 2026-11-11 |
| 56 | https://download.cocos.com/CocosUdc/agreement/Cocos_User_Service_Agreement_en_20220901.html | Cocos 用户服务协议以「developing games」界定用途；`cocos/engine-native` 为修改版 MIT。**非游戏商业用途未见明确条款（未验证）** | 2026-11-11 |
| 57 | https://github.com/esotericsoftware/spine-runtimes/tree/4.3/spine-ios | spine-ios 4.3 分支存在；README 写明与 Spine 4.3.xx 导出的数据配套 | 2026-11-11 |
| 58 | https://repo1.maven.org/maven2/com/esotericsoftware/spine/spine-android/maven-metadata.xml | `latest/release = 4.3.5`，lastUpdated 2026-07-10 | 2026-11-11 |
| 59 | https://repo1.maven.org/maven2/app/rive/rive-android/maven-metadata.xml | `latest = 11.16.3`，lastUpdated 2026-11-11 | 2026-11-11 |
| 60 | https://repo1.maven.org/maven2/com/airbnb/android/lottie/maven-metadata.xml | `latest = 6.7.1`，lastUpdated 2025-10-31 | 2026-11-11 |
| 61 | Maven Central `com.esotericsoftware.spine:spine-android:4.2.9`（AAR + sources + POM） | 初版实测 39.5 KB、无 `.so`、依赖 gdx/spine-libgdx、许可头（4.3.5 见第 47 条） | 2026-11-11 |
| 62 | Maven Central `app.rive:rive-android:10.2.0`（AAR） | 实测 7.47 MB、arm64 `.so` 4.97 MB | 2026-11-11 |
| 63 | Maven Central `org.godotengine:godot:4.4.1.stable`（AAR） | 实测 102.8 MB、arm64 `.so` 76 MB | 2026-11-11 |
| 64 | Maven Central `com.airbnb.android:lottie:6.7.1`（AAR） | 实测 433 KB | 2026-11-11 |
