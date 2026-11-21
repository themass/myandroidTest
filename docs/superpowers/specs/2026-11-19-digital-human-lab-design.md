# 数字人对比 Lab UI 套件设计

状态：**已定稿（产品负责人授权按作者选择执行）**。
意图：在真机上横向对比三家端侧数字人渲染效果，统一走我们现有 LiveKit 交互通道，挑完收口成一家；**不是**长期产品内保留三套可切换形象。

## 1. Intent & Why

- **要什么**：一个可切换的「数字人 Lab」UI 套件，通话时能在 `Simple`（现有基线）/ `Live2D(ChatWaifu)` / `Duix` / `TaoAvatar(MNN)` 之间切换，看嘴型、情绪、打断、画质与机型代价。
- **给谁**：开发与产品选型；入口默认只在 debug / 内部 Lab，不进正式设置。
- **成功**：同一角色、同一通话通道下，一分钟内切引擎看完差异；对比记录能支撑「留哪一家」；赢家可沿同一接口毕业进产品通话页，其它引擎整模块删除。
- **为什么现在**：`AvatarRenderer` 契约已在，通话页仍是 `SimpleAvatarRenderer`；三家源码已在 `/Users/gqli/work/android/{ChatWaifu_Mobile,Duix-Mobile,MNN}`，需要统一壳而不是三套分叉 App。

## 2. Goals / Non-goals / Anti-goals

**Goals**

1. 统一交互通道：进房、hello、speech.*、`control.barge_in`、字幕、挂断不变；只换画面层。
2. 可切换：通话中或进房前可选引擎；选择持久化到本地（Lab 偏好），下次默认上次所选。
3. 界面贴近各家官方 demo 的**视口**（全幅 GL / TextureView / Duix RenderSink），外圈 chrome（状态、字幕、底部控制）用我们的通话套件，避免三套控件。
4. 实用设计模式：Strategy（引擎）+ Factory（创建）+ Facade（音频/指令桥）+ Adapter（三方 SDK）。
5. 能力协商：沿用 `AvatarCapability`；引擎不支持的指令静默忽略；设备不达标时该引擎不可选并说明原因。

**Non-goals**

- 正式版设置里给用户长期切三家。
- 替换文字页 `LayeredAvatar`。
- 把各家自带的本地 LLM / ASR / VITS 接到产品路径（通道仍是我们的云端 STT/TTS/agent）。
- 第一次交付就商业级 Live2D 素材管线或 TaoAvatar 全模型打包进 CI。

**Anti-goals（做成这样也算失败）**

- 为每家复制一份 `CompanionRoute` / 通话状态机。
- 引擎自己播 TTS 与 LiveKit 远端音频双声道抢麦。
- 选型未完成就把三套 native 打进正式 release APK 默认依赖。

## 3. Constraints

| 项 | 约束 |
|----|------|
| 现有接口 | 优先实现 / 扩展 `AvatarRenderer` + `AvatarCommand`；GL 引擎若必须持有 `View`，用 `DigitalHumanSurface` 补一层「可挂 View」而不拆掉指令契约 |
| 通道 | `feature-companion` LiveKit；嘴型优先用现有 `mouthLevel`；Duix 需要 PCM 时从远端音频抽 16kHz mono PCM 喂 SDK，**仍由 LiveKit 扬声器出声**（SDK 静音播放或只驱动口型，二选一在适配里定：默认「只驱动、我们出声」） |
| 模块 | 新建 `:feature-dh-lab`（UI 套件 + 引擎接口 + Simple 实现）；各三方 SDK 以 `dh-engine-*` 可选模块引入，避免主 APK 强依赖 |
| 构建 | Lab 入口：`BuildConfig.DEBUG` 或 `companion.dhLab=true` manifest meta；release 可编进 stub，点选未编引擎时提示「未编入」 |
| 机型 | TaoAvatar：文档要求旗舰（约 SD 8 Gen 3 + 8GB）；不达标标记 `UnsupportedDevice`。Duix：Android 10+、内存紧张时降级提示。Live2D：arm64 |
| 许可 | Live2D Cubism / 各家模型禁止商用——Lab 仅内部评测；文档与 UI 角标标明「评测」 |
| 源码位置 | 评测期用 `include` 指向本机 `../android/...` 或拷贝精简模块进仓库；设计不绑定绝对路径，builder 用 gradle `settings` 属性 `dh.chatwaifu.root` 等覆盖 |

## 4. Approaches（已选）

| 方案 | 要点 | 取舍 |
|------|------|------|
| **A. 官方壳视口 + 统一通话 chrome（已选）** | 各引擎画面贴近官方 demo；共享 LiveKit 与控制条；Lab 切换器 | 最快对比真实画质；产品页接线二次，但接口预留 |
| B. 统一通话页三个完整 AvatarRenderer | 全部塞进现圆头像槽 | 对比的是「塞进现 UI」而非官方画质；适配周期长 |
| C. 三 flavor / 三 APK | 隔离最好 | 切换要重装，违背「横向对比」 |

选 A 的理由：目标是选型不是量产三套；官方视口 + 我们通道最能暴露画质与接入成本；TaoAvatar 当「旗舰 3D 参考」而不是和 2D 同台硬指标。

## 5. UI 套件结构（实用模式）

### 5.1 组件树

```
DigitalHumanLabScaffold          // 通话页 Lab 变体，或覆盖在现有 Call 上
├── DigitalHumanHost             // 全幅视口：当前引擎 Surface / Compose
│   ├── engine.surface()         // Strategy 产出
│   └── DigitalHumanStatusBadge  // Loading / Error / 评测水印
├── CallChromeOverlay            // 复用现有：字幕、波形、静音、挂断、时长
└── DigitalHumanSwitcher         // Lab：分段控件或 chips（Simple | Live2D | Duix | Tao）
```

**Switcher 交互（作者决定）**

- 位置：通话页顶部安全区下、居中横滑 chips；仅 Lab 可见。
- 切换：先 `release()` 旧引擎 → 显示 Loading → `create`+`bind` 新引擎；通话不挂断。
- 不可用引擎：灰显 + 短因（未编入 / 机型不足 / 缺模型）。
- 默认：`Simple`（零依赖基线，永远可用）。

### 5.2 核心类型

```kotlin
enum class DhEngineId { SIMPLE, LIVE2D, DUIX, TAO_AVATAR }

data class DhEngineAvailability(
    val id: DhEngineId,
    val available: Boolean,
    val reason: String? = null, // 不可用时人话原因
)

/** Strategy：一个可切换的数字人后端 */
interface DigitalHumanEngine : AvatarRenderer {
    val id: DhEngineId
    /** 需要 Android View 时非 null；Compose 引擎可空，由 Host 只画 Compose */
    fun createView(context: Context): View? = null
    fun availability(context: Context): DhEngineAvailability
}

interface DigitalHumanEngineFactory {
    fun available(context: Context): List<DhEngineAvailability>
    fun create(id: DhEngineId): DigitalHumanEngine
}

/** Facade：把通话音频与协议指令接到当前引擎 */
class DigitalHumanBridge(
    private val engine: DigitalHumanEngine,
) {
    fun onAvatarCommand(cmd: AvatarCommand) = engine.dispatch(cmd)
    fun onMouthLevel(level: Float) = engine.dispatch(AvatarCommand.Mouth(level))
    /** Duix 等需要 PCM 时使用；其它引擎空实现 */
    fun onRemotePcm(pcm16leMono16k: ByteArray) {}
}
```

### 5.3 Adapter 要点（各家）

| 引擎 | 来源 | 画面 | 驱动 | 备注 |
|------|------|------|------|------|
| SIMPLE | 现有 `SimpleAvatarRenderer` | Compose | `Mouth` / `Emotion` / blink | 基线，必选 |
| LIVE2D | ChatWaifu `Live2D` 模块 `GLRenderer` | GLSurfaceView | Mouth→参数或循环口型；Emotion→exp | 不接其 VITS/Sherpa |
| DUIX | `duix-sdk` `DUIX` + `RenderSink` | SDK 视图 | 优先 PCM 驱动口型；Gesture→motion | 模型需预置/下载到 app 私有目录 |
| TAO_AVATAR | MnnTaoAvatar `AvatarTextureView` + A2BS/NNR | TextureView | 音频→A2BS→NNR | 仅旗舰；模型体积大，按需下载 |

### 5.4 与现有通话页关系

- **Lab 模式开**：`CompanionRoute` 通话槽用 `DigitalHumanLabScaffold` 替换圆头像 `SimpleAvatar` 槽；chrome 仍走现有 Call 覆盖层逻辑（字幕 / `mouthLevel` / `selfLevel`）。
- **Lab 模式关**：行为与今天完全一致（`SimpleAvatar`）。
- 文字聊天页的 `LayeredAvatar` **不动**。

### 5.5 视觉（作者决定，偏实用）

- 视口背景：引擎未就绪时深色占位 + 居中进度；与现通话页暗色一致。
- Switcher：Filled tonal chips，选中高亮；左侧小「Lab」徽章。
- 评测水印：右下角 10sp 半透明「DH Lab · 评测」。
- 不引入新的品牌色；用现有 companion 通话色板。

## 6. 模块与依赖

**P0 落地：** 类型与 UI 放在 `feature-companion/src/main/java/com/openapi/ks/myapp/dh/`，避免与 companion 循环依赖；`:feature-dh-lab` / `dh-engine-*` 延到 P1 native 隔离时再拆。Lab 视口挂在通话页 `avatarStage`（全幅不裁圆）；Lab 开启时 SIMPLE 也走 Lab Host，现网 `LayeredAvatar` 仅 Lab 关闭时保留。

```
:feature-dh-lab          // （P1+）套件 UI + Engine 接口 + SimpleEngine + Bridge + Switcher
:dh-engine-live2d        // optional，依赖 ChatWaifu Live2D
:dh-engine-duix          // optional，依赖 duix-sdk
:dh-engine-tao           // optional，依赖精简后的 TaoAvatar nnr/a2bs
:feature-companion       // P0 已内嵌 dh 包；P1+ api/implementation project(:feature-dh-lab)
```

Factory 用 ServiceLoader 或手动注册：`feature-dh-lab` 注册 Simple；各 engine 模块在 `CompanionDhRegistry` 里 `register`（避免反射在 release 被裁掉的话用显式注册表）。

**作者决定**：显式注册表（`DhEngineRegistry.register(id, factory)`），不用 ServiceLoader。

## 7. 分阶段交付

| 阶段 | 交付 | 验收 |
|------|------|------|
| **P0** | `:feature-dh-lab` + 接口 + SimpleEngine + Switcher UI 挂进通话 Lab + 其它引擎灰显占位 | DEBUG 下可看见 Switcher；切到未编引擎有原因；Simple 与现网一致 |
| **P1** | Duix adapter + 样例模型路径文档 | 真机选 Duix 能出画面，LiveKit 说话口型有响应 |
| **P2** | Live2D adapter | 同上 |
| **P3** | TaoAvatar adapter + 机型门闸 | 旗舰可跑；低端不可选 |
| **P4** | 选型结论文档；删除落选模块；赢家接到非 Lab 通话槽 | Lab 可关可删 |

本设计批准后的**立刻实现范围默认 P0**；P1–P3 按同一计划推进，不另开产品意图讨论。

## 8. 测试

- 单测：`DhEngineRegistry` 注册/创建；不可用原因；切换时旧引擎 `release` 被调用（mock）。
- 单测：`DigitalHumanBridge` 把 `Mouth` 转到当前引擎。
- UI：Switcher 在非 Lab 不展示（若用 flag）。
- 真机：P0 只验 Simple + 占位；P1+ 各引擎单独清单。

## 9. 留给 builder 的部分

- gradle 用源码 include 还是拷贝精简模块：按仓库洁癖与二进制体积选，优先 **gradle 属性指向外部目录**，CI 无目录则跳过 optional 模块。
- Duix「只驱动口型 vs SDK 播音频」：实现时量一下延迟，默认只驱动；若 SDK 无法静音则改为 SDK 播、LiveKit 远端音频静音（互斥，禁止双开）。
- Live2D 口型参数名按模型 `model3.json` 探测，失败则循环口型（与 ChatWaifu 现状一致）。

## 10. 作者已拍板清单（无需再问产品）

1. 意图 = 对比选型，不是长期三套。
2. 方案 = A（官方视口 + 统一 chrome）。
3. 默认引擎 = Simple；Lab 入口 = DEBUG 或 `companion.dhLab`。
4. 模块名与注册表如上。
5. 立刻落地 P0；P1 优先 Duix（SDK 边界最清晰）。
6. 不改文字页分层形象；不改服务端协议。
