# 2D 虚拟形象：渲染技术选型与交互设计

> 状态：**提案**。本文只做技术选型与设计，不含实现。
> 核对日期：**2026-11-11**。所有外部事实都附了来源 URL 和核对日期；凭经验推断、没有官方来源支撑的，标了「未验证」。

## 0. 结论先行

**推荐技术路线：Spine（`spine-android`）作为主渲染器，Compose 分层立绘（现有 `SimpleAvatarRenderer`）作为兜底与降级路径，两者都藏在我们已有的 `AvatarRenderer` 接口后面。**

三个决定性的取舍：

1. **Spine 的 Android 运行时是纯 JVM，AAR 只有 39 KB，没有任何 `.so`。** 我实测下载了 `com.esotericsoftware.spine:spine-android:4.2.9`，AAR 里只有 `classes.jar`（43.8 KB），`jni/` 目录不存在；渲染走 Android 原生 `Canvas.drawVertices`，在硬件加速 Canvas 上即 GPU 三角网格绘制（源码见 §1.2 的说明）。相比 Rive（arm64 光 `.so` 就 3.68 MB，还要带 `libc++_shared.so` 1.29 MB）和 Godot（arm64 `libgodot_android.so` **69 MB** + `libc++_shared.so` 6.9 MB），Spine 的体积代价几乎为零。这对一个用户是中文/英语/阿拉伯语三语、要覆盖大量中低端安卓机的陪伴类 App 是决定性优势。
2. **Spine 的运行时授权是「买编辑器就带运行时」，没有收入抽成、没有按量计费、没有 MAU 门槛。** 编辑器 Professional 一次性 $379（现价），只有年收入/融资超过 **$500,000 USD** 才必须买 Enterprise（$2,499 基础 + 每人 $379/年）。Live2D 的商务授权是**按年销售额分档的持续付费**，中型企业（年销 < 1 亿日元）每月每平台每区域 $125.68，大型企业 $628.42/月；且 AI/chatbot 场景还要先过「Expandable Application」审核，本产品的形态（用户可创建角色、可换装、可扩展）**有相当概率被判定为 expandable**，那是一条要单独签协议、无法在文档里给出确定价格的路径。
3. **Spine 的 skin / attachment 模型天然就是「换装 + 配饰」的数据模型。** `Skin` 本质是「槽位名 → Attachment」的字典，运行时可以把多个「单品 skin」合并成一个 skin，不需要为每种搭配预授权美术。这正是我们要的「换衣服、戴眼镜、加帽子」，而不是事后硬凑。Live2D 的对应能力是 Part 显隐 + 换贴图，做单一配件可以，做「任意组合的衣橱」要美术为组合出图。

**代价（说清楚）：** Spine 美术管线比 Live2D 重——绑骨、权重、网格、IK 全要美术手工做，一个可动角色大约是 Live2D 同水平角色的 1.5–2 倍工时（**未验证**，行业经验值，不是官方数据）。Live2D 在「一次性高质量二次元立绘、细腻的呼吸与头发物理」上仍然更强，它的缺点在商务条款和体积，不在画质。如果最终产品定位是「少量精修官方角色、不做 UGC 换装」，Live2D 值得重新评估；如果定位是「大量角色 + 用户可换装」，Spine 明显更合适。

**现状对齐：** 仓库里 `feature-companion/.../avatar/` 已经有 `AvatarRenderer`、`AvatarCommand`、`AvatarEventMapper`、`SimpleAvatarRenderer`（v1 分层立绘），`capabilities` 协商机制也在。本文的设计**不推翻这套抽象**，而是指出：在这套抽象不变的前提下，接 Spine 只需要新增一个 `SpineAvatarRenderer` + 一个 `SpineAvatar` Composable，上层的指令协议、状态机、事件映射都不动。这正好验证了当初把渲染放在接口后面的价值。

---

## 1. 候选技术对比

### 1.1 体积实测

下表的数据来自我下载对应 AAR/sources 后实测（2026-11-11，Maven Central / GitHub）：

| 运行时 | 版本 | 包体积（全部 ABI） | arm64 相关 `.so` | 说明 |
|---|---|---|---|---|
| `com.esotericsoftware.spine:spine-android` | 4.2.9 | **39.5 KB** | **无** | 纯 JVM，`classes.jar` 43.8 KB；依赖 `gdx`、`spine-libgdx`（见下） |
| `com.airbnb.android:lottie` | 6.7.1 | **433 KB** | 无 | 纯 JVM，`classes.jar` 468 KB |
| `app.rive:rive-android` | 10.2.0 | **7.47 MB** | `librive-android.so` 3.68 MB + `libc++_shared.so` 1.29 MB ≈ **4.97 MB** | 每 ABI 各一份；本仓库只打 `arm64-v8a` 和 `armeabi-v7a` |
| `org.godotengine:godot` | 4.4.1.stable | **102.8 MB** | `libgodot_android.so` **69.1 MB** + `libc++_shared.so` 6.9 MB ≈ **76 MB** | 每 ABI 各一份；本仓库两个 ABI 意味着约 140 MB 未压缩 |
| Live2D Cubism Core | — | **未验证** | 有 `.so`，按 ABI 分发 | 官方 README 确认 `android/arm64-v8a` 等预编译二进制存在，但未给出字节数；我未能取得官方分发包实测 |

> Spine 的 POM 声明了 `com.badlogicgames.gdx:gdx` 和 `com.esotericsoftware.spine:spine-libgdx` 两个 compile 依赖。也就是说 39 KB 是**运行时本身**，实际 APK 增量还要加上 libGDX 的 JVM 类（不含 native）。libGDX core 的 AAR 体积我**未实测**，但它是纯 Java、无 `.so`，量级在数百 KB 到 1 MB 之间（**未验证**）。即便按 1 MB 估，仍然比 Rive 的 5 MB 低一个量级。

**为什么体积在这里是决定性因素：** 本仓库 `version.gradle` 明确保留 `arm64-v8a` 和 `armeabi-v7a` 两个 ABI。Godot 这条路在这个前提下不可行；Rive 大约 +8–10 MB（两个 ABI）；Spine 大约 +1–2 MB。

### 1.2 全维度对比

| 维度 | Live2D Cubism SDK for Native/Java | Spine (`spine-android` / libGDX) | Rive | Lottie (airbnb) | Compose 分层立绘（现有方案） | 嵌游戏引擎（Godot / Unity as a Library） |
|---|---|---|---|---|---|---|
| **动画质量**（呼吸、眨眼、视线、头发物理） | **最高**。为二次元立绘而设计：变形器、物理演算、参数化表情、呼吸/眨眼是 SDK 内置范式 | **高**。骨骼 + 网格变形 + 权重 + IK + 物理约束（Spine 4.x 的 `physics` 约束）。细腻度接近 Live2D，但立绘的「整张变形」不如 Live2D 自然 | **中高**。骨骼、网格、状态机、数据绑定都很强，但美术资产在 Rive 编辑器里做，二次元立绘的柔和变形不如前两者 | **低**。本质是 After Effects 关键帧导出的矢量/栅格动画，没有骨骼，做互不相关的多图层联动很别扭；交互只能切状态 | **低**。实测 v1 只有呼吸缩放（1.2%/1.8%）+ 两帧眨眼 + 两帧嘴型。做不了头发、做不了视线 | **最高**（本质是全引擎），但绝大部分能力我们不需要 |
| **运行时体积** | 中（有 `.so`，量级未验证） | **极低**（39.5 KB + libGDX 类） | 高（arm64 ≈ 5 MB） | 低（433 KB） | **零** | **不可接受**（arm64 76 MB） |
| **中端机性能** | 官方提供 benchmark 场景但「并非每个 SDK 变体都有」，且没有 arm64 实测数字（**未验证**）。掩码默认上限 36 个，影响 GPU 开销 | 走 `Canvas.drawVertices(VertexMode.TRIANGLES, …)` 提交三角网格，在硬件加速的 Canvas 上是 GPU 路径（我读了 `SkeletonRenderer.java` 确认，见下）。没有自定义 shader，混合模式和裁剪受 Canvas 能力限制。**仍需 P0 实测**，但风险低于原先的估计 | 原生 C++ 运行时 + 自绘渲染器，性能通常是三者中最好的 | 轻，6.1 起多线程渲染 | 最轻（就是几张 `Image`） | 最重，启动开销以秒计 |
| **Compose 集成** | 需 `AndroidView` 包一层 GLSurfaceView；官方 Java SDK 是 View 体系 | `AndroidView` 包 `SpineController` / 自绘 `Canvas`。也可以直接在 Compose `Canvas` 里画（因为它是纯 JVM + Skia Canvas） | **Compose API 已在 beta**，官方称「功能完整、可用于生产」，入口是 `Rive` composable（2026-11 核对） | 官方提供 Compose 的 `rememberLottieComposition` / `animateLottieCompositionAsState` | **原生 Compose** | `AndroidView` 包 `GodotFragment`；官方警告「一个进程只支持一个 Godot 实例」、「不支持自动 resize/旋转，可能导致崩溃」 |
| **资产管线** | PSD 分层 → Cubism Editor 打网格、加变形器、设参数、配物理。**专为 Live2D 优化，美术上手最快** | PSD → Spine 编辑器（内置 Import PSD，比旧 Photoshop 脚本快最多 5 倍）→ 绑骨、权重、网格、皮肤。**美术门槛最高** | 在 Rive 编辑器内画/导入、做骨骼与状态机。云端协作强，但离线与自托管弱 | AE → Bodymovin 导出 JSON | 直接出 PNG 分层图，**零工具链** | Blender/Godot/Unity 内做 |
| **工具成本** | Cubism PRO Indie $89.75/年（首年，逐年递减）或 $13.07/月 | Spine Professional **$379 一次性**（现价 $379，列表价 $449），升级免费；Essential $69 但**不含网格/IK/物理**，做不了我们要的效果 | Free / Cadet $9 per seat/mo / Voyager $32 / Enterprise $120（要求年收入 $10M+） | 免费 | 免费 | 免费 |
| **授权与商业条款** | **按年销售额分档持续付费**，详见 §1.3 | **买编辑器即含运行时**，无抽成、无按量、无 MAU；> $500,000 收入/融资须用 Enterprise | **运行时 MIT 开源、无收入抽成、无运行时费用**（官方原话）；限制在编辑器侧：Free 档导出会带 Rive 水印，且发布次数按月限额（Free 50 次/月） | Apache-2.0，无限制 | 自有代码 | Godot MIT；Unity 见 §1.3 |
| **适合本产品的程度** | 画质最好，但商务与「可扩展应用」审核是硬风险，且资产要为每种搭配出图 | **最匹配**：体积、条款、换装模型三者都对 | 技术很好，体积是主要问题；编辑器按 seat 计费但运行时免费 | 不适合做主形象 | 保留为兜底/降级/首屏 | 不适合 |

### 1.3 授权条款细节（逐条核对）

**Live2D Cubism**

- 发布前需要签 **Publication License Agreement（SDK Release License）**，且**必须在发布前至少一个月**完成。个人与小规模企业**豁免**（Expandable Application 除外）。
  - 来源：https://www.live2d.com/en/sdk/license/ ，核对日期 2026-11-11。
- **商务授权门槛：最近一个财年销售额 1,000 万日元以上**即属「for business」，必须用 PRO for business，且该阈值按**整个公司**计算，不按使用 Live2D 的部门计算；个人事业主同标准；子公司若实质是大公司的一个部门，即便销售额为零也不能算小规模。
  - 来源：https://help.live2d.com/en/license/license_24/ ，核对日期 2026-11-11。
- **Running Royalty Content Plan（游戏/应用）的费率**（该计划页面**没有**收入分成比例，是固定费用结构）：

  | 企业规模（年销售额） | 初期费（每个发布区域） | 月费（每平台每区域） |
  |---|---|---|
  | 一般用户 < ¥10M | 免费 | 免费 |
  | 小规模企业 < ¥10M | 免费 | 免费 |
  | 中型企业 < ¥100M | $314.21 / ¥50,000 | $125.68 / ¥20,000 |
  | 大型企业 > ¥100M | $1,885.25 / ¥300,000 | $628.42 / ¥100,000 |

  ＞ 发布区域 6 个（日本；中国大陆/港澳台；亚洲其他；北美；欧洲；其他），平台 4 个（iOS、Android、HarmonyOS、Web）。上表费率**以展示 Live2D logo 并进入 Showcase 页面为条件**。
  - 来源：https://www.live2d.com/en/sdk/license/running_plan01/ ，核对日期 2026-11-11。
- **One-time Purchase Content Plan（非主机/PC）**：固定费模式为中型 $628.42 / ¥100,000、大型 $3,770.50 / ¥600,000（与发布区域、平台数量无关）；或按份数 $0.25 / ¥40 每份，可下载内容在开卖时按 1,000 份起计费。
  - 来源：https://www.live2d.com/en/sdk/license/purchase_plan02/ ，核对日期 2026-11-11。
- **AI / Chatbot 专用路径**：官方流程图里确实有 AI/chatbot 分支，但它**不是独立计划**。第一步先判断是否属于 **Expandable Application**（「significant expandability」的服务或内容），若是则**必须送审并签特别授权**，个人/小规模豁免**不适用**；若不是，才按交付形态（原生应用）落到 D/E/G 三个计划之一。
  - 来源：https://www.live2d.com/en/sdk/license/ ，核对日期 2026-11-11。
  - **对本产品的判断（未验证）**：我们支持用户创建角色、换装、加配饰、用户上传——主观上符合「高可扩展性」。这条路径无法在文档里给出确定价格，必须先向 Live2D 询价。这是选型的**实质性风险**，不是可以事后处理的细节。
- Cubism Editor 价格：PRO Indie 首年 $89.75/年（≈¥14,280），第二年起 $73.91，第三年起 $67.12；月付 $13.07。PRO Business 年付 $414.80 起。两者**功能完全相同**，差别只在资格与 SDK 授权。
  - 来源：https://store.live2d.com/en/ ，核对日期 2026-11-11。
- **不选 Live2D 的核心理由**：画质确实是四者中最好的，但（a）商务授权是按年销售额的持续支出，且要按整个公司口径；（b）AI/chatbot + UGC 换装很可能触发 Expandable Application 审核；（c）它的换装要靠美术为每种搭配出图或做 Part 显隐，不支持我们想要的「任意单品组合」。

**Spine**

- 运行时**不单独售卖**；持有有效的 Spine Editor 授权即获得在游戏和应用中使用运行时的权利。试用版**不授予**集成/分发权利。许可**按人（named user）、不可转让**。
  - 来源：https://en.esotericsoftware.com/spine-runtimes-license ；https://en.esotericsoftware.com/spine-purchase ，核对日期 2026-11-11。
- `spine-android` 的源码头注释（我实测下载 sources.jar 读取）明确写了：集成运行时须遵守 **Spine Editor License Agreement 第 2 节**，并「redistribution of the Products in any form must include this license and copyright notice」。
  - 来源：`spine-android-4.2.9-sources.jar` → `com/esotericsoftware/spine/android/SkeletonRenderer.java` 文件头，核对日期 2026-11-11。
- **价格**：Essential **$69**（列表价 $99，**不能保存含网格/IK 等 Professional 功能的项目**）；Professional **$379**（列表价 $449，含网格、变形、权重、裁剪、物理、IK、约束、**skins**）；Enterprise **$2,499 基础 + 每人 $379/年**。
  - **收入阈值：年收入、投资收入、风险投资或其他融资超过 $500,000 USD 的公司或个人必须使用 Enterprise**，不能用 Essential 或 Professional。
  - 来源：https://en.esotericsoftware.com/spine-purchase ，核对日期 2026-11-11。
- 授权失效后，**已经发布的产品中含有的运行时可以继续分发**，但新的集成需要有效授权。
  - 来源：同上。
- **对本产品的含义**：如果现在还没到 $500K，是 $379 一次性的成本。到了 $500K 之后要转 Enterprise（$2,499/年 + 每人 $379/年），这个成本可预测、有上限。相比之下 Live2D 是销售额的持续性费用。

**Rive**

- 运行时**MIT 许可**，「Copyright (c) 2020-2024 Rive」。
  - 来源：https://github.com/rive-app/rive-android/blob/master/LICENSE ，核对日期 2026-11-11。
- 官方定价页原话：「No revenue share, no runtime fees, no royalties, and MIT open-source runtimes」。计费在**编辑器侧**：按 seat（$9 / $32 / $120 per seat/mo），并限制每月的 publish/push 次数（Free 50 次/月，Cadet 500，Voyager 5,000 per seat）。**Free 导出的文件会播放 Rive splash**。
  - 来源：https://rive.app/pricing ，核对日期 2026-11-11。
- Enterprise 要求 **$10M+ 年收入**。
- **对本产品的含义**：条款是所有候选里最友好的（无抽成、运行时 MIT）。真正的阻碍是体积（arm64 ≈ 5 MB）和美术必须在 Rive 编辑器里做——而我们的美术更熟悉 PSD/Spine/Cubism 管线。

**Unity**

- **Runtime Fee 已于 2024-09-12 取消，立即生效**，且声明「will not apply to any games made with Unity 6 or any other version of Unity」。因为从未实施过，任何已发布或将发布的产品都不会被收取。
  - 来源：https://unity.com/products/pricing-updates ；https://unity.com/blog/unity-is-canceling-the-runtime-fee ，核对日期 2026-11-11。
- **Unity Personal 免费，适用上限为「$200,000 in revenue and funding」**，该阈值自 2024-10-17（Unity 6 发布）起从 $100K 提高到 $200K。超过 $200K 必须用 Pro（2026 年 $2,310/年/seat 预付或 $210/月）；超过 $25M 必须用 Enterprise。
  - 来源：https://unity.com/products/pricing-updates ，核对日期 2026-11-11。
- **Unity as a Library（UaaL）**：Unity 6.x 官方手册仍有「Use Unity as a Library in other applications」页面，支持 Android/iOS/Windows。我没有找到官方弃用声明，但也没有找到官方对长期支持的承诺——**「未弃用」是从文档仍存在推断的，不是引用承诺**。
  - 来源：https://docs.unity3d.com/6000.5/Documentation/Manual/UnityasaLibrary.html ，核对日期 2026-11-11。

**Godot**

- 引擎 MIT。Android 以标准 AAR 发布在 Maven Central，嵌入现有 App 的方式是宿主 Activity 实现 `GodotHost`，包 `GodotFragment`，或用 `assets` 里的 PCK/ZIP。
  - 来源：https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html ，核对日期 2026-11-11。
- **两条官方限制**直接判死刑：「Currently only a single instance of the Godot Engine is supported per process」（变通是把宿主 Activity 放到独立进程）；「Automatic resizing / orientation configuration events are not supported and may cause a crash」。
  - 来源：同上。

**Lottie**

- `airbnb/lottie-android` **Apache-2.0**。来源：https://github.com/airbnb/lottie-android ，核对日期 2026-11-11。
- 注意：LottieFiles 的 **dotLottie Android Player**（支持 slots、交互状态机、API 24+）是**另一个库**，它的授权我在官方文档页**没有查到**（页面未声明许可或定价）。如果将来想用 slots 做换装，要单独核实它的条款，不要假定和 airbnb 的 Apache-2.0 一样。
  - 来源：https://docs.lottiefiles.com/en/runtimes/distributions/android ，核对日期 2026-11-11（未找到许可声明）。

### 1.4 为什么不是「Compose 分层立绘」直接做到位

现有 `SimpleAvatarRenderer` 已经很聪明：`AvatarSpec` 只描述素材，缺什么退什么，呼吸/眨眼/嘴型的插值都在 Composable 里，渲染器本身是纯状态、可脱离 Compose 测试。这套东西**应该保留**——它是所有新渲染器的降级目标（低端机、素材未下载、SDK 初始化失败）。

但它做不到产品的核心诉求：
- **没有骨骼**，所以「头发随头部转动」「身体呼吸带动肩膀」只能是假动作（整图缩放）。
- **没有可参数化的视线**。要跟手指，只能切几张预渲染的眼睛朝向图，过渡会跳。
- **换装只能是整图替换**。要「戴眼镜」就得预先出「戴眼镜的整张立绘」，组合数爆炸。
- **点击部位做反应**需要命中区，分层立绘只能靠手写矩形。

所以定位是：**Spine 做正片，Compose 分层做兜底**。

---

## 2. 交互设计

以下全部以 Spine 为实现目标描述。参数名是 Spine 的术语（`Skin`、`Slot`、`Attachment`、`AnimationState`、`Bone`），对应的 Spine API 我都在官方运行时文档里核对过。

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

- physics 约束的存在：Spine Professional 功能列表里列了 "physics"（见 §1.3 价格页），具体 API 名称我**未逐个核对**，实现时以对应版本的运行时文档为准（未验证）。
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
implementation 'com.esotericsoftware.spine:spine-android:4.2.9'
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

### P0：验证 Spine 在真机上的可行性（1–2 周）

**目标：回答一个技术问题——Spine 的 Canvas 光栅化在目标中端机上能不能跑满 60 FPS。**

这一步**不写产品代码**，只做一个 `.skel` 的加载与播放 demo。

要测的：
- 一个典型二次元角色（约 150–250 个顶点、8–15 个网格、2–4 张 2048×2048 贴图）在：
  - 低端：4 GB RAM、Snapdragon 6xx 级别（约 2020–2021 年机型，**具体型号待定**）
  - 中端：6–8 GB RAM、Snapdragon 7xx 级别
  - 高端：旗舰
- 同时测：空跑（只呼吸）、说话（嘴型 30Hz 更新）、拖动（每帧改位置）、视线跟随（每帧改骨骼旋转）。

**成功标准：**
| 指标 | 门槛 |
|---|---|
| 中端机 FPS | ≥ 55（P95 帧时间 ≤ 20ms） |
| 低端机 FPS | ≥ 30 |
| 单角色内存增量 | ≤ 15 MB |
| 冷启动加载时间 | ≤ 300ms |
| APK 增量（`assembleRelease` 前后对比） | ≤ 3 MB |

**如果 P0 失败：** 说明 Spine 的 CPU 光栅化在这个产品上不可行。退到 Rive（性能更好，代价是 +5 MB 和美术管线要改）。这个决策点的价值就在于**在投入美术工时之前**把它测出来。

**怎么测：** 用 `adb shell dumpsys gfxinfo` + Macrobenchmark（`androidx.benchmark`）拿帧时间分布，不要只看「感觉流畅」。本仓库已有 `app/src/test` 下的测试基础，Macrobenchmark 可以放 `:app` 的 `androidTest`。

### P1：接进现有抽象（2 周）

**目标：证明 `AvatarRenderer` 抽象真的能换实现。**

- 实现 `SpineAvatarRenderer` + `SpineAvatar` Composable。
- 只用现有的 `AvatarCommand`（`SetState` / `Emotion` / `Mouth`），先不做 GAZE / GESTURE / TOUCH_DRAG。
- 用现有的 `FakeCompanionTransport` 驱动（它已经会发 `state` 和 `emotion` 事件）。
- **不改任何上层 UI 代码**，只把 `SimpleAvatar` 的实例化换成 `SpineAvatar`。

**成功标准：**
- 上层代码改动 ≤ 5 行（证明抽象成立）。
- 现有的 `AvatarEventMapper` 测试全绿（不用改）。
- 一个角色能完整跑通：thinking → speaking（嘴动）→ idle（呼吸）。

**这一阶段的产出是「架构验证」而不是「产品功能」。** 如果这里要改大量上层代码，说明抽象是错的，应该早点知道。

### P2：交互（3–4 周）

- **拖动与摆放**（§2.1）：容器层手势 + 持久化。
- **换装**（§2.2–2.4）：衣柜面板、Skin 合并、Slot attachment 切换。
- **点击部位**（§2.5）：包围盒命中 + 反应动画。
- **视线跟随**（§2.6）。

**成功标准：**
- 换装后**无穿帮**：所有姿势下配饰跟着走（这一条要靠美术在每个动画上都检查）。
- 拖动 60 FPS 不掉帧（这是最容易掉的，因为拖动时每帧都在改 transform）。
- 点击命中率主观测试 ≥ 90%（10 个用户，每人点 20 次）。

### P3：表情与语音（3 周）

- 情绪参数映射 + 插值（§3.2）。
- 音频振幅 → 嘴型（§3.1 第一层）。
- 后端契约落地（§4.2）：SSE 侧加 `avatar` 字段，LiveKit 侧加 `intensity`。

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

| 风险 | 影响 | 缓解 |
|---|---|---|
| **Spine 在 Android 上的性能** | 高——若中端机跑不满 30 FPS，整个方案要换 | P0 先测；备选 Rive |
| **美术工时** | 高——绑骨比 Cubism 重 | 先用 1 个角色做完整流程，量出工时再排期 |
| **Live2D 的 Expandable Application 判定** | 高——若选 Live2D，可能被要求签特别协议 | 若倾向 Live2D，**先询价再决策**，不要假设 |
| Spine Enterprise 阈值 $500K | 中——增长后会突然多一笔年费 | 可预测，可预算 |
| 音素口型的音画同步 | 中——LiveKit 抖动缓冲会引入偏移 | 第一版不做；需要时实测 |
| 多 ABI 下的 APK 体积 | 中——Spine 影响小，但换其他引擎影响大 | 用 AAB 按设备拆分（现有配置已支持） |
| 用户上传/自定义形象 | 未评估 | 本文不涉及；若要做需要单独的资产审核与安全设计 |

**需要业务方决定的：**
1. 形象的**角色数量级**：10 个精修官方角色 vs. 500 个 UGC 角色？前者 Live2D 可接受，后者必须 Spine。
2. **换装的商业模型**：免费换装，还是付费单品？付费则 `unlockedItems` 必须服务端权威，且要接支付。
3. **是否接受「先无形象、后加形象」**：如果形象是核心卖点，P0/P1 应该提前到其他功能之前。

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
| 21 | Maven Central `com.esotericsoftware.spine:spine-android:4.2.9`（AAR + sources + POM） | 实测 39.5 KB、无 `.so`、依赖 gdx/spine-libgdx、许可头 | 2026-11-11 |
| 22 | Maven Central `app.rive:rive-android:10.2.0`（AAR） | 实测 7.47 MB、arm64 `.so` 4.97 MB | 2026-11-11 |
| 23 | Maven Central `org.godotengine:godot:4.4.1.stable`（AAR） | 实测 102.8 MB、arm64 `.so` 76 MB | 2026-11-11 |
| 24 | Maven Central `com.airbnb.android:lottie:6.7.1`（AAR） | 实测 433 KB | 2026-11-11 |
