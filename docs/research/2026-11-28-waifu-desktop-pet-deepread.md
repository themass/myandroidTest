# waifu / 桌宠 / Live2D companion 开源线深读

调研日期：2026-11-28。**源码级**调研，所有结论带 `path:line`；引用格式是「仓库内相对路径:行号」。没读到代码只做了推断的地方显式写「推断」；找了一圈确认不存在的写进「硬负面」而不是略过。

与已有调研的分工（不重复其内容）：
- [`2026-11-28-open-llm-vtuber-deepread.md`](2026-11-28-open-llm-vtuber-deepread.md) 读的是 OLLV **后端**（FastAPI / agent / TTS / 打断协议）。本篇读 **前端呈现层与桌宠本体**（模型加载、动作与物理、互动命中区、窗口叠加）。
- [`2026-11-28-ollmv-agent-architecture-deepread.md`](2026-11-28-ollmv-agent-architecture-deepread.md)、[`2026-11-28-ollmv-ui-interaction-deepread.md`](2026-11-28-ollmv-ui-interaction-deepread.md) 覆盖 OLLV 的 agent 与 UI；本篇 §5 只补 **Open-LLM-VTuber-Web 的 Live2D/Electron 层**，并纠正其中一条流传的依赖说法（§5 开头与 §10 末）。
- [`2026-11-28-android-her-stack-audit.md`](2026-11-28-android-her-stack-audit.md) 审计的是**我们自己的**引擎栈；本篇所有外部结论都对着它的口径来判「可抄 / 已有 / 不适用」。

## 0. 读的是什么代码，以及看不到的部分

所有仓库都在 `/tmp/waifu-research/`（本机克隆，只读）。**除 ChatWaifu_Mobile 外全部是 shallow clone（`.git/shallow` 存在，`git rev-list --count HEAD` = 1）**，所以「最近提交日期」是真的，「提交数 / 迭代速率 / 一年内活跃度」这类判断**在这些克隆里不可计算**，我不给这类数字。

| 仓库 | HEAD | tip 提交日期 | license（读文件确认） |
|---|---|---|---|
| ChatWaifu_Mobile | `14092ac6` | 2023-06-19 | MIT（上游 LICENSE） |
| alive2d | `f9488363` | 2025-04-30 | **无 LICENSE 文件** |
| Live2D/CubismNativeFramework | `45de2122` | 2026-06-26 推送 | Live2D Open Software License |
| shimeji（Shimeji-ee fork，`com.wishes`） | `27f795d1` | 2026-05-09 | GPL-3.0 |
| Shijima-Qt | `57723f1d` | 2026-04-29 | GPL-3.0（已声明停更） |
| dsh-pet | `3ea54400` | 2026-11-24 | 见 §10（含 Piapro 角色权保留说明） |
| dsh-live2d-pets | `6af6f021` | 2026-09-24 | 声明 MIT 但**无 LICENSE 文件** |
| live2d-widget | `34b27cc8` | 2026-07-06 | MIT |
| pixi-live2d-display | `31317b37` | 2023-12-15 | MIT |
| BongoCat | `47a73ed1` | 2026-10-28 | Apache-2.0 |
| NyaDeskPet | `a402fcd` | 2026-02-27 | MIT |
| Open-LLM-VTuber-Web | `d176e7df` | 2025-09-05 | MIT |

三处**结构性缺口**，先说清楚，免得后文被当成已验证：
1. **Shijima-Qt 的 XML 解析器不在该 checkout 里**：`libshijima` / `libshimejibfinder` / `cpp-httplib` 三个 submodule 是空目录（`Shijima-Qt@57723f1 .gitmodules:1-8`，`git submodule status` 前缀 `-`）。所以 §3 里所有 schema 级结论都引自 **Java 引擎**，而 Shijima-Qt 自己声明与 Java 引擎完全兼容（`com.pixelomer.ShijimaQt.metainfo.xml:10-11`）。
2. **ChatWaifu_Mobile 我读的是上游 SHA，不是本地 fork**。本机 `/Users/gqli/work/android/ChatWaifu_Mobile` 带本地补丁（Cubism Core 升到 5-r.5、`CubismModel.cpp`、`CubismRenderer_OpenGLES2::DoDrawModel` 里一个越界写防御修复，共 10 文件 357 行新增）。所有引用都用 `git show 14092ac66c…:<path>`，避免把 fork 补丁当成上游事实。
3. **PrprLive / VTube Studio 替代 / 近 12 个月活跃的开源引擎替代（brief 里的目标 ③）本节仍在并行调研**，见 §9 的「缺口」条目；本节其余部分不受影响。

## 1. 结论先行

三条最重要的可抄结论（其余在 §10 逐条对着 payload 判定）：

1. **命中区必须自己带稳定 id，永远不要复用 Cubism `HitAreas[].Name`。** 全生态的 hit test 都是「art-mesh 顶点的 AABB」（pixi `src/cubism-common/InternalModel.ts:195-244`；官方 SDK sample `LAppModel.cpp` 的 `IsHit` 同语义），而命中表**按 Name 建 key**，空 Name 或重名会静默塌成一个条目（pixi `InternalModel.ts:170-176`）。NyaDeskPet 为这个补丁写了整段 `patchHitAreas`（`renderer/js/live2d-manager.ts:1445-1477`），因为它自带模型的 `HitAreas` 就是 `{"Id":"HitAreaHead","Name":""}`（`models/live2d/mao_pro_zh/runtime/mao_pro.model3.json:90-99`）。我们的 `touch_zones[]` 要自己命名 + 自己定义重叠优先级：现存的每一家都是「第一个匹配赢」，而且是**未声明的**第一个（pixi 返回全部匹配按对象 key 序；dsh-pet 用 `Array.find` 半开区间，重叠靠 manifest 顺序，`src/gameplay.ts:938-941`）。
2. **`touch_zones` 的可抄形状只有一个：dsh-pet 的 `gameplay.touch.zones[]`**（`src/gameplay.ts:28-45` + 样本 `assets/miku/pet.json:144-235`）——归一化分数、**只用 y 的分带（band）而不是矩形**、每区挂 `probability / effects / state / stateMs / phrases`、并且**分支概率和 ≤ 1，余量是故意的「什么都不做」质量**（`src/gameplay.ts:460`）。它唯一的致命限制是 `gameplay` 块被 gate 成只有 `renderer: "frames2d"` 才能用（`src/manifest-v2.ts:719-722`，逐字读过），也就是说**今天没有任何一个 live2d 桌宠带声明式分区**——这个位置是空的。
3. **pack 交付的「版本 + hash」这一段，全生态都没有人做，不是我们过度设计。** BongoCat 有最硬的包防御（limits、稳定诊断码、原子 rename），但 `INDEX_SCHEMA_VERSION` 只写进内存索引、**从不落盘**，包级没有 version / hash / manifest / provenance（`crates/bongocat-model/src/limits.rs:10`、`package.rs:360`，且无 model 包的 JSON Schema）；NyaDeskPet 三个 manifest 的 `version` 纯展示、无任何比较逻辑；shimeji 靠文件名猜（`Main.java:217-247`）；OLLV-Web 靠 URL（`ModelInfo.url`，无版本无 hash）。所以 `model_ref{asset,version,hash}` + `pack_version` 是**净新增能力**，同时记住 BongoCat 的教训：**zip 导入是被主动撤回的**（`docs/adr/0036-model-archive-import-boundary.md:3` 已接受 2026-09-16 → 已撤回 2026-09-22）， folder-only 导入 + 预校验 + 单 rename 才是它验证过的路径。

## 2. ChatWaifu_Mobile —— 我们生产上真正参考的那条 waifu 线

为什么它是「我们参考的 waifu」而不是又一个候选：我们自己把上游 SHA 钉在 `docs/design/2026-11-24-vtuber-extension-plan.md:19`，APK 里带 `libchatwaifu-live2d.so`，ATRI / Amadeus / Yuuka 三个模型在白名单里。所以这一节的价值不是「它好不好」，而是**它哪些地方是坏的、坏在哪一行**——因为那些坏会顺着我们的参考链传下来。

C++ 侧是官方 Cubism Native Framework 的 sample app（`LApp*`）加 JNI 壳，Java/Kotlin 侧是薄壳。文件规模：`Live2D/src/main/cpp/LAppModel.cpp` 741 行。

### 2.1 模型加载：`LoadAssets` 失败软退出，motion 全量预载

- `LAppModel.cpp:87-127` `LoadAssets`：失败时回滚 `_modelHomeDir`（软失败），继续往下走。
- `LAppModel.cpp:130-337` `SetupModel`：moc3 载入失败会 abort + 回滚（`:142-172`）；expression **逐项跳过**坏条目并通过 JNI 回调 `OnLoadOneExpression`（`:175-208`，回调在 `:196`）；physics `:211-231`；pose `:234-254`；EyeBlink 组 `:257-260`；layout `:317-320`；**所有 motion 组在加载时全量预载** `:325-329`（`PreloadMotionGroup` `:339-381`）。
- 纹理：`SetupTextures` `:705-731`，`:726-730` 打开 `PREMULTIPLIED_ALPHA_ENABLE`。

可抄的一点（也是这一节唯一值得直接抄的加载设计）：`JniBridgeJava.java:83-107` `LoadFile` 是一个**双路径加载器**——字符串以 `/` 开头走 `FileInputStream`，否则走 `assets.open`；配合 `JniBridgeJava.java:125-145` 的 `OnLoadOneMotion` / `OnLoadOneExpression` / `Live2DLoadInterface`，把「包里到底有哪些 motion / expression」**枚举到 Java 层**再决定。这正是我们 `AvatarPackStore` + registry 需要的清单枚举形态：native 不自己猜文件，Java 侧拿清单。

### 2.2 每帧：呼吸是硬编码常量，idle 被注释掉了

`LAppModel.cpp:427-532` `Update()`：
- **`:443-445` idle 重启被注释掉**（`StartRandomMotion(MotionGroupIdle, PriorityIdle)` 在注释里）。逐字读到，不是我推断：模型播完一个动作后不会自己回到 idle。
- `:454-461` 眼睛只在 motion 没更新本帧时才交给 EyeBlink；`:463-468` 手动睁眼参数；`:471` mouthForm。
- `:473` `_model->AddParameterValue(_idParamMouthOpenY, parameters.mouthOpenY);` —— Java 灌进来的口型值走的是 **AddParameterValue**（叠加），不是 Set。
- `:480-486` expression；`:490-499` 拖拽→角度（±30 / ±10 / ±1）；`:502-505` breath；`:508-511` physics；`:525-528` pose。
- **`:516` native lip-sync 分支里 `csmFloat32 value = 0;` 写死 0**（注释说「实时口型应从系统取音量」）。也就是说 SDK 层的口型通道是死的，只等 Java 灌值。
- breath 参数常量硬编码在 `SetupModel` `:263-275`：AngleX 0/15/6.5345/.5，AngleY 0/8/3.5345/.5，AngleZ 0/10/5.5345/.5，BodyAngleX 0/4/15.5345/.5，ParamBreath .5/.5/3.2345/.5。**同一组常量在 pixi 和 OLLV-Web 里一模一样**（pixi `src/cubism4/Cubism4InternalModel.ts:87-93`；OLLV-Web `WebSDK/src/lappmodel.ts:286-319`）——它是 Cubism sample 的公版默认，不是任何人的调校，别当基准。

`LAppLive2DManager.cpp`：`OnTap` `:96-122`（头→随机 expression，身→`StartRandomMotion(MotionGroupTapBody, PriorityNormal)`）；`OnUpdate` `:124-162`；**`ChangeModelTo` `:234-289` 里 `ReleaseAllModel()` 在 `:244`，发生在 `LoadAssets` 成功之前**——换模型失败就得到一个空场景。另有一个潜伏编译错误 `:269`（`modelJsonName` 在未启用的 `USE_RENDER_TARGET` 分支里未定义）。

`LAppView.cpp:186-198` `Render()`：`_changeModel` 是**在 GL 渲染循环里消费**（`:195-199`），而 `LAppView.cpp:387-397` 的 `ChangeModelTo` / `ApplyExpression` 只是普通无同步字段。`LAppDelegate.cpp:83-97` `Run()` 里 `glClearColor(0,0,0,1)` + clear —— 这是我们 `Live2dStageBackground.kt` 那条备注的根因。

**线程纪律：`JniBridgeC.cpp:112-221` 每个 native 方法直接调 `LAppDelegate` 单例，没有 GL 线程队列。** 与我们 `JniDispatcher = GLSurfaceView.queueEvent` 的纪律正好相反，是反面教材。

### 2.3 硬负面 A：`OnTap` / `HitTest` 在出货的 app 里不可达

- `nativeOnTouchesBegan/Ended/Moved` 只有声明（`JniBridgeJava.java:45,47,49`）和实现（`JniBridgeC.cpp:151,157,163`），**全仓零调用点**（`git grep nativeOnTouches` 在 `14092ac6` 上只有这 6 处 + C++ 内部 `_view->OnTouchesBegan`）。
- `ChatFragment.kt:56-60` 把整个 Live2D view 的 touch 全部转给 `onLive2DViewTouchEventHandler` → `fragmentViewModel.handleTouchEvent` → `Live2DTouchManager.handleTouch`（`Live2DTouchManager.kt:60+`）。
- 于是 Cubism 的 `OnTap` / `HitTest`（`LAppModel.cpp:637-654`：`_opacity<1` 直接不判定，然后线性扫 hit area 名字调 `IsHit`）**在这条产品路径上永远不被调用**。桌宠的「点头/点身体」交互在 ChatWaifu 里根本不存在，只有拖拽与捏合缩放（`Live2DTouchManager.kt:37-43` 捏合→`nativeProjectScale`；`:98` 拖拽→`nativeProjectTransformX/Y`，坐标 `xDiff/width, -yDiff/height`；`:111-113` 持久化 `[translateX,translateY,scale]`，`:124-131` `setInitParams` 恢复）。`:34` 建了一个空 `GestureDetector`（无 listener）。

### 2.4 硬负面 B：三个随包 manifest 什么都没声明

`Live2D/src/main/assets/Live2DModels/ATRI/ATRI.model3.json`（上游路径，逐字）只有 `Version`、`FileReferences{Moc,Textures,Physics,DisplayInfo}` 和 `Groups[EyeBlink,LipSync]`：**没有 `HitAreas`、没有 `Motions`、没有 `Expressions`**。Amadeus（kurisu）只声明了 1 个 expression `fix.exp3.json`，`LipSync.Ids` 还是空数组；Yuuka 三项皆无。与此同时 `expression1..16.exp3.json`、`dec-l/dec-r/model.motion3.json` 就躺在旁边**不被引用**。`LAppDefine.cpp:43-49` 的 `ModelDir[]={"Hiyori"}` 也只是 sample 默认（Hiyori 不随包）。

结论：**别把「模型包里带了 x 个表情 / y 组动作」当成能力**。带了 ≠ 声明了 ≠ 可用。这一条直接支撑 §10 对 `model_ref` + 清单校验的要求。

### 2.5 口型：作者自己承认效果不行，默认关闭

`LipsValueHandler.kt`：`:26` `USE_REAL_LIP_SYNC = false`；作者注释在 `:19`「由于各种值对应以及处理的延时对应等问题，实际感觉体验不咋地，先默认就播个循环动画算了....」。`:28` `SAMPLE_SIZE = 22050`；OVRLipSync→viseme 映射表在 `:34-49`（旁边写着「值是随便写的」）；`:96-118` `playDefaultAnimation` 用 `data.size*1000/22050` 估时长，`:104-105` `delay(10)` × 0.8 的硬编码 21 级爬升（`:132-154`），`:93` / `:114` 把嘴归零。

对照 §5.4（OLLV-Web 的 RMS 链）与 §8.4（NyaDeskPet 的 RMS + 噪声门 + 非对称 attack/release）：**ChatWaifu 是唯一一个连 RMS 都没接通的**，而它的 Java 通道（`nativeProjectMouthOpenY`）是我们已经在用的。

### 2.6 模型包管理：只有「从 assets 拷到 app 目录」

`LocalModelManager.kt:34-90`：`copyAssets2Local(deleteIfExists = true)`（`:40-41`、`:66-67`）把内置 assets 拷到 app 目录，VITS 靠名字匹配绑定（`:61-73`）。**没有版本号、没有 hash、没有下载、没有 catalog、没有状态机。** 模型 id 就是目录名。这正是我们要用 `ca_her_pack` 取代的部分。

顺带一个真实的产品耦合：`ChatFragment.kt:189-192` 对 Amadeus 特判 `needRenderBack(false)` + `nativeApplyExpression("fix")`（注释 `//fix kurisu live2d bug..`）。**具体模型的具体 bug 被写进 app 代码**——我们的 `her_id = <renderer>-<asset>` 加 payload 就是把这类特判挪进数据的机会。

## 3. alive2d 与 Cubism Native Framework R5

### 3.1 alive2d（`f9488363`，2025-04-30，**无 LICENSE 文件**）

结构上最像我们 `dh-engines` 的一家：handle 化、调用方自己组帧。

- `Live2D.java:4-41`：init / dispose / glRelease 三段生命周期。
- `Live2DModel.java:7-477`：`long nativeHandle` 句柄；`nativeUpdate / UpdateMotion / UpdateDrag / UpdateBreath / UpdateBlink / UpdateExpression / UpdatePhysics / UpdatePose`（`:24-38`）——**每帧调用序列由调用方拼**，这正是我们 `AvatarEngine` 接口的形状。参数读写带 weight（`:41-69`）；`startMotion(group, no, priority, BiConsumer onStart, BiConsumer onFinish)`（`:83`）带回调；`loadExtraMotion`（`:89`）、`getMotions()`。
- **命中族最值得抄**（`:94-104`）：`hitPart(x,y,topOnly) -> List<String>`、`hitDrawable(...) -> List<String>`、`isAreaHit / isPartHit / isDrawableHit`。原生侧 `Live2DModel.cpp:338-397` 把 id 收集进 Java `ArrayList`。它把「命中了什么」暴露成**可枚举的名字列表**，比 sample 的 `anyhitTest` 只返回第一个更强。
- `createRenderer(maskBufferCount)` `:107`（原生 `Live2DModel.cpp:400-403`）、part opacity 与 screen/multiply 着色 `:114-120`、drawable 颜色 `:123-127`、expression add/remove/set/setRandom/reset `:130-142`。
- `MotionPriority.java:3-7`、`StandardParams.java:4-32`。

硬负面：**没有任何 pack 契约**——没有清单、没有 asset 管理、没有版本。README 假设模型放在 `Environment.getExternalStorageDirectory()/A/Live2D`（外部存储根，Android 10+ 已不可用）。abiFilters 只有 arm64-v8a。**没有 LICENSE 文件 ≠ 可自由用**：它依赖 Cubism Core（专有），自身代码也没有授权声明，抄它的接口设计可以，直接 vendor 它不行。

### 3.2 CubismNativeFramework `45de2122`（develop，2026-06-26 推送，Cubism 5.3 兼容）

值得记的两条：
1. CHANGELOG `[5-r.5] - 2026-04-02` 新增 `cubismlook`（视线/目标跟随）类，可由 Framework 配置——**官方给的原生 gaze 原语**。我们的「眼睛跟随说话者/指向」不必自己写 `CubismTargetPoint` 包装；但注意同版本还有 Core 改名 `csmGetDrawableRenderOrders → csmGetRenderOrders`，我们 fork 里 Core 已是 5-r.5，这条改名要在升级自己 Framework 层时对齐。
2. Cocos2d-x 支持已 EOL。
3. 授权：Framework/Samples 是 Live2D Open Software License，**不是 MIT/Apache 类**；Core 二进制是 Redistributable Code，且 Expandable Application 条款要求分发需 Live2D 事先批准（BongoCat 把这条 blocker 完整写下来，见 §7.2 末尾的 license 小节）。

## 4. touch zone / 命中区的数据格式：三家只有一家是真数据格式

### 4.1 dsh-pet：`gameplay.touch.zones[]`（唯一可序列化、可进 payload 的设计）

类型定义 `src/gameplay.ts:28-45`：

```ts
PetGameplayTouchBranch { probability; effects?; state?; stateMs?; phrases? }
PetGameplayTouchZone  { name; y0; y1; branches }
PetGameplayTouch      { zones; clickBoost: {stat, min, max} }
PetGameplayHitBox     { x0, y0, x1, y1 }   // src/gameplay.ts:99-105
```

样本 `assets/miku/pet.json:144-235`：hitBox `0.328/0.0625/0.672/0.898`；zones = head（y1 ≤ 0.55，p .05 → affection+5，state "happy" 3000ms，phrases "*我好开心!*"）、body（0.55–0.75，p .1 → +10，"shy"，"**色狼**你干嘛!"）、legs（0.75–1，+30 "flirty" / −5 "angry"）。

值得抄的细节，全部是读到的具体数字：
- **坐标是归一化分数，且是「hitBox 的分数」，hitBox 本身又是 sprite 框的分数**（`src/client/PetSprite.tsx:826-828`、`gameplay-hud.tsx:190-193`）。两层归一化让美术改构图不用改 zone。
- **只用 y 的分带（band），完全不写 x**——三行就能描述一个角色，作者成本极低，这也是它比五矩形方案更值得抄的原因。
- 校验上限：zones 1–8（`:168`、`:425`）、branches 1–8（`:434`）、probability ∈ (0,1]（`:440`）、**分支概率和 ≤ 1，余量是刻意的空操作质量**（`:460`）、stateMs ∈ [200,10000]（`:453`）、phrases ≤ 64 条 × 120 字符（`:173-174`）、`KNOWN_ZONE` fail-closed（`:182`）。
- 命中 = `Array.find` 半开区间，**第一个匹配赢，重叠由 manifest 顺序决定**（`:938-941`）——这条是我们必须显式改掉的设计缺陷。
- 分支掷点在宿主侧（`:908-917`），落库走 `POST /api/pet/gameplay/touch`（`routes.ts:630-633` → `service.ts:824-861`）。**互动结果由服务端裁定**，客户端只上报命中了哪个区——与我们的 payload/服务端裁定方向一致。
- 好感度子系统独立：默认值 `src/affinity.ts:104-110`（turnReward 1、petReward 1、petCooldownMs 10_000、feedReward 5、feedCooldownMs 30_000），`AFFINITY_MAX = 999_999_999`（`:40`），9 段阶梯（`:47-57`），冷却门控写入（`ledger.ts:254-292`）。

致命限制（逐字确认）：`src/manifest-v2.ts:719-722` —— `gameplay` 块**要求 `renderer === "frames2d"`**，报错文案是「gameplay currently requires renderer frames2d (its state references name frames2d tracks)」。而 live2d 块只允许 `hitAreas: string[]` 白名单（`contracts/pet-manifest-v2.schema.json:228-235`，解析 `manifest-v2.ts:385-389`，出参 `registry.ts:615`）。仓库里 `find -name '*.model3.json'` = 0：**它没有发布过任何 live2d 桌宠**。所以「声明式分区 + 概率分支 + 状态机」这套东西在 Live2D 语境下今天没人做过，我们把 `touch_zones` 挂在 renderer 中立的 payload 上正好是空位。

### 4.2 dsh-live2d-pets：9 个标量描述 5 个 AABB（贴在活体 bounding box 上）

- `src/models.ts:62-105` `SpatialTapConfig`：9 个标量，描述 5 个 AABB，坐标系是**当前 Pixi 实际渲染的包围盒**，不是模型画布。默认值（`:95-105`，逐字）：`headMaxNy .32`、`legMinNy .58`、`armMinNy .28`、`headMinNx 0`、`headMaxNx 1`、`bodyMinNx .38`、`bodyMaxNx .62`、`armLeftMinNx 0`、`armRightMaxNx 1`。按模型覆盖见 `presets/presets.jsonc:22-33`。
- 只有 4 种反应类别 `TapPart`（`client/index.ts:173`），对 Cubism hit-area 名字用**正则**匹配（`TAP_PART_MATCHERS :176-191`：`/head|hair|face|头/i` 等）。
- `classifyTap`（`:222-242`，逐字）：命名区（head/leg/arm）优先；几何只覆盖 body/未识别；包围盒外 → null。
- `handleTap`（`:1208-1236`）：`pointerup`，`TAP_DEBOUNCE_MS 80`、`DRAG_THRESHOLD 6`（`:39-41`）。
- 反应文案来自 persona 池按 `tapHead|tapLeg|tapArm|tapBody` 取（`persona-shared.ts:21-24,31-36`）；动作链映射 `DEFAULT_MOTION_MAP`（`models.ts:44-55`：idle/thinking/error/done/waiting/delivered + head/leg/arm/body，每条链末尾接 Idle），链走「第一个成功」（`client/index.ts:532-566`），用户自定义 map 洗牌后接默认尾（`:509-525`），优先级 idle 用 `IDLE`、其余用 **`FORCE`，代码注释写明「NORMAL cannot interrupt NORMAL」**（`:569-578`）。
- 三级覆盖（custom → preset → global）`models-host.ts:79-84`；9 个数字输入框、默认值当 placeholder（`settings.ts:80-90,244-267,1184-1194,1401-1412`）；夹到 [0,1]（`models.ts:134-168`）；废弃字段 `armLeftMaxNx/armRightMinNx` 自动重映射（`:84-89,149-156`）。

**这一家最有价值的不是 zone，是它的每帧纪律**（全部逐字读到）：`DEFAULT_MAX_FPS = 30`，注释说明不设上限时 PIXI ticker 会跑到 120–140fps，「长时间挂页会持续占满 GPU/主线程」（`:44-48`）；`app.ticker.maxFPS`（`:413-418`、`:791`）；**tab 隐藏或窗口 blur 时 ticker 完全停转**（`:405-411`、`:1316-1322`）；`antialias:false, powerPreference:'low-power', backgroundAlpha:0`（`:783-790`）；resize 合并处理，因为实测单次 resize「可达数秒」（`:368-370,706-717`）。**zone 可视化调试层是一张独立的兄弟 `<canvas>`，`pointer-events:none`，z-index:2**（`:766-771`、`:872`，绘制 `:883-911`）——这正是我们 FX 叠加层应该用的层形态，不是把特效画进模型画布。

硬负面：**没有任何语音 / sticker / 粒子**（grep 0）；没有好感度/喂食；没有 expression 映射；`_hitAreaKeys` 是死字段（`:231`）。而且它在 `package.json:26` 与 README badge 声明 MIT **但没有 LICENSE 文件**，同时随包 `assets/vendor/live2dcubismcore.min.js`（专有，2019 Redistributable 头），无第三方声明。模型一律 URL 直载（`presets.jsonc:6`「模型一律 URL 直载，不随包分发模型文件」），自带 URL 被称为「零许可风险路径」（`models.ts:84-87`）。

### 4.3 live2d-widget：**没有 zone 数据格式**（纠正一条流传说法）

- 不存在 `zones` 数组：`grep -rn "zones" src/ dist/` = 0，`git log --all -S 'zones'` = 0（在本 SHA 上）。任何说它有 `tap/click/press/hover` zone 表的设计文档都是错的。
- 它的 `dist/waifu-tips.json` 是**按宿主页面 CSS selector** 索引的 `mouseover[]` / `click[]` → `{selector, text}`（`src/widget.ts:60-71`，`dist:2-180`；文案取 `event.target.innerText` `:129-145`），外加 `seasons[]` MM/DD 区间（`:181-217`）、`time[]` 小时带（`:218-242`）、`message` 固定槽位（其中唯二的模型区域 `hoverBody` / `tapBody`，`:255-256`）。`hitokoto` 是工具条按钮抓 v1.hitokoto.cn（`src/tools.ts:50-62`），不是 zone。
- Cubism2 的 `hitTest` 有个**未文档化的兜底约定**：`getHitAreaNum()==0` 时改测 `hitAreasCustom[id+'_x'/'_y']` 矩形（`src/cubism2/LAppModel.ts:420-443`，逐字；字段名 `ModelSettingJson.ts:30-31`）。没有 schema、没有随包示例——**约定存在但无人使用**，和 shimeji 的 `<Hotspot>`（§6.2）是同一类现象。
- 真 hit area = 变换后顶点的 AABB（`Live2DFramework.ts:267-285+`）；坐标空间是 Cubism 视图逻辑 ±1（`cubism2/index.ts:9-27,253`，`LAppDefine.ts:5-6`）。重叠规则 `if head → else if body`（`LAppLive2DManager.ts:98-107`）：head 赢，head→`setRandomExpression`，body→`startRandomMotion('tap_body', NORMAL)`。
- Cubism **版本是嗅探的、不是声明的**（`checkModelVersion :209-214`）；模型选择就是 localStorage 里一对整数 `modelId/modelTexturesId`（`src/model.ts:12-15,114-133`，形状是 fghrsh/live2d_api）。Cubism3+ 路径的 `LAppDefine` 来自**用户自带的 SDK**（`src/cubism5/index.js:180-207` + `rollup.config.js:35-46` 别名）→ 这一段不可审计。
- **每帧成本最差的一家**：两个 runtime 都是无条件全速 RAF（`cubism2/index.ts:180-189`、`cubism5/index.js:134-149`），没有上限、没有 idle、没有可见性分支；`visibilitychange` 只用来播一句「欢迎回来」（`widget.ts:167-170`）。正面：opt-in 懒加载 + 24h `waifu-display` 计时器（`:239-249`）+ `waifu-disabled` 总开关（`:14,215-217`）。

## 5. Open-LLM-VTuber-Web 的 Live2D / 桌宠层（`d176e7df`，2025-09-05）

**先纠正 brief 里的一条前提：这条线没有 `piyo` / `piyosago` 依赖。** 在前端 `Open-LLM-VTuber-Web` 全仓（`*.json/*.ts/*.tsx/*.js/*.md`）grep `piyo` = **0 命中**；在后端 `Open-LLM-VTuber` 检出里也只有 2 处二进制噪声命中，`git log --all -S piyosago` 与 `-S piyo`（限定 `*.py/*.md/*.txt`）**都无结果**；前端是 submodule `frontend` → `https://github.com/Open-LLM-VTuber/Open-LLM-VTuber-Web`（`.gitmodules`，pin `06a659b`）。公开检索也找不到名为 piyosago 的项目（[Open-LLM-VTuber 仓库](https://github.com/Open-LLM-VTuber/Open-LLM-VTuber)、[GitHub vtube topics](https://github.com/topics/vtube-studio)）。**OLLV 的「桌宠模式」是 Electron 自己实现的，不是外部 piyo 库**——下面读的就是它。

### 5.1 渲染栈：不是 pixi，是官方 Web SDK sample 的 fork

- `src/renderer/WebSDK/Core/` 专有 `live2dcubismcore.js`（+ `RedistributableFiles.txt`）；`WebSDK/Framework/src/**` Framework **5-r.1**（`Framework/CHANGELOG.md:8` `## [5-r.1] - 2024-03-26`）；`WebSDK/src/` 是 `LApp*` sample，改动很大。
- 别名 `@framework`、`@cubismsdksamples`（`tsconfig.web.json`、`vite.config.ts`）。
- 运行时靠 `<script>` 注入 `./libs/live2dcubismcore.js`（`src/renderer/src/main.tsx:36`）**后才** `createRoot`（`:33-63`）。
- Canvas 是 React 拥有的 DOM 节点（`components/canvas/live2d.tsx:112-122`），`lappglmanager.ts:40-48` 绑上去，`:50` 取 **webgl2**。`lappdelegate.ts:192` `clearColor(0,0,0,1)` 但 `:205` 只 `clear(DEPTH_BUFFER_BIT)` —— 颜色缓冲不清，帧之间叠加，这是透明桌宠不闪的原因（从代码推断）。
- **死重量**：`src/renderer/public/libs/live2d.min.js` 是 **Cubism 2** SDK（129KB，混淆的 `_$MT` API），零引用；同仓库同时躺着 Cubism2 与 Cubism3 两套 core，读代码时极易踩。

### 5.2 换模型 = 全拆 + 盲等 500ms

`hooks/canvas/use-live2d-model.ts:112-141`：`needsUpdate` → `updateModelConfig`（改 `lappdefine.ts:41-51` 的模块级变量）→ `setTimeout(500)` → `LAppLive2DManager.releaseInstance()` + `initializeLive2D()`。构造函数 `lapplive2dmanager.ts:222-227` 立刻 `changeScene(0)` → `releaseAllModel(); pushBack(new LAppModel()); loadAssets(...)`（`:186-211`）。窄一点的 `LAppAdapter.setChara()`（`WebSDK/src/lappadapter.ts:85-96`）**没有任何调用点**（死代码）。保留的只有 DOM canvas 与 WebGL context（`lappglmanager.ts:8-9`，`release(){}` 是空的）。没有任何就绪信号，只有一对盲 `setTimeout(...,500)`（`:130` 与 `:175-183`）。

对照 §2.2 ChatWaifu `LAppLive2DManager.cpp:244` 同样是「先 release 再 load」——**两边都在成功之前销毁**。我们的 `AvatarSceneBridge` 走「先建新的、成功再换」才对，这一条是两边共同的教训。

### 5.3 呈现清单 `model_dict.json`：字段一半是死的

类型 `src/renderer/src/context/live2d-config-context.tsx:11-74`（`ModelInfo`：`url`、`kScale`、`initialXshift/initialYshift`、`idleMotionGroupName?`、`defaultEmotion?`、`emotionMap`、`pointerInteractive?`、`tapMotions?`、`scrollToResize?`、`initialScale?`）。逐字段消费情况（grep 全 `src/`）：

| 字段 | 是否被读 | 证据 |
|---|---|---|
| `url` | 是，且就是加载地址（无版本/无 hash） | `use-live2d-model.ts:113,125` → `parseModelUrl :28-54` |
| `kScale` | 是（`*2` 魔法加倍） | `live2d-config-context.tsx:129` → `lappdefine.ts:46-47` → `lappmodel.ts:139` → `cubismusermodel.ts:151` |
| `idleMotionGroupName` | **从未读**，idle 组硬编码 `'Idle'` | `lappdefine.ts:71`、用于 `lappmodel.ts:561` |
| `emotionMap` | **从未读** | 只剩声明 `:61` 与一处 CSS key |
| `tapMotions` | 是（唯一有丰富消费者的字段） | `use-live2d-model.ts:365,377` → `lappmodel.ts:1163-1253` |
| `defaultEmotion` | 是 | `use-live2d-expression.ts:58-63` |
| `pointerInteractive` | 是（只门控 tap→motion，不门控命中/拖拽） | `:363` |
| `initialXshift/Yshift/initialScale/name/description` | **全是死的** | 见 `use-live2d-resize.ts:212,328-349`（`resetModelPosition` 无调用点） |

**这是「payload 字段一旦发布就养不活」的最强证据**：一个真实在跑的开源项目，清单里 11 个字段有 7 个没人读，而且 idle 组名写死意味着**换包时改了 idle 组名的模型会静默失去待机动画**。我们 payload 里每个字段都要有「谁读它」的落点，且要有 fail-closed 校验（dsh-pet 的 `KNOWN_ZONE` 那种）。

服务端下行只有三个 action 字段：`Actions { expressions?: string[]|number[]; pictures?: string[]; sounds?: string[] }`（`services/websocket-service.tsx:45-49`）——**`pictures` / `sounds` 在全仓只出现在这两行类型里**，`websocket-handler.tsx:159` 打日志后只转发 `expressions`（`:165`）。**没有任何 overlay/cue 协议**。同样，`volumes[]` 与 `sliceLength` 也被完整管道化却无人消费（`websocket-handler.tsx:162-163` → `use-audio-task.ts:22-23`，无 reader）。对照我们的 `effects[]` + `cmp.cue → AvatarEventMapper → StageEffectMapper → LottieFxLayer`：**这套东西在 OLLV 全栈没有对应物**，不是我们多加的。

表情落地路径与优先级：`use-audio-task.ts:128-136` 在**音频开始播放之前**取 `expressions[0]` → `use-live2d-expression.ts:14-36` → `lappadapter.ts:76-78` → `lappmodel.ts:790-808`，以 **`PriorityForce`** 起表情 motion（`:801`）。Talk 动作组名 `"Talk"` 硬编码（`use-audio-task.ts:141`）。优先级常量 `lappdefine.ts:81-84`。

### 5.4 口型：客户端重取一遍音频算 RMS，开环、无平滑

- `volumes[]` 死；实际驱动是 `use-audio-task.ts:182-201` 调 `model._wavFileHandler.start(audioDataUrl)`，**把同一个 data URI 再 fetch 一遍**（`WebSDK/src/lappwavfilehandler.ts:105-118`）并解 RIFF/PCM。音频字节被解析两次。
- 每帧 `lappmodel.ts:614-630`：`getRms()` → `min(1, rms*1.5)` → `lipSyncWeight = 4.0` 叠加。
- **`*2` 是靠 monkey-patch SDK 实例方法实现的**：`use-audio-task.ts:164-193` 用 `_initialized` 标志（不属于 SDK 类）包一层 `update`，`this._lastRms = Math.min(2.0, this._lastRms * 2.0)`。总增益 ≈3× 后饱和，**没有任何 attack/release 或 EMA**。
- **开环**：`_userTimeSeconds` 由 RAF 的 `deltaTimeSeconds` 累加（`lappwavfilehandler.ts:55-58`、`lapppal.ts:45-52`），完全不看 `audio.currentTime`，无时钟校正。可预期症状：每句开头几帧嘴是闭的（WAV 异步解析未完，`_lastRms = 0`）——这条是推断，不是实测。
- LipSync id 兜底（fork 补丁，非 stock SDK）：`lappmodel.ts:374-406`，无 LipSync 组时回退 `ParamMouthOpenY`（`:382-400`），先查 `getParameterIndex !== -1`。**这个兜底值得抄**，和 NyaDeskPet 的 `DEFAULT_LIP_SYNC_PARAM`（§8.4）是同一思路。

### 5.5 交互：**两套系统同时在跑**，同一次点击会触发两遍

- (A) React/DOM 层：`use-live2d-model.ts:205-401` 挂在 wrapper div（`live2d.tsx:108-110`）。阈值 `TAP_DURATION_THRESHOLD_MS = 200`、`DRAG_DISTANCE_THRESHOLD_PX = 5`（`:24-26`）。命中判定 `model.anyhitTest(x,y)` **或** `model.isHitOnModel(x,y)`（`:225-226`）。
- `isHitOnModel`（`lappmodel.ts:1066-1141`）是 fork 加的兜底：逆 `_modelMatrix` 后**对所有 drawable 顶点求 AABB**（`:1105-1137`），O(drawables×vertices) **每个指针事件一次**，而且 pet 模式下**每次 pointermove 都调**（`:326`）。这是真实的每帧成本陷阱——空处点也算命中。
- `tapMotions`（`lappmodel.ts:1163-1253`，也是 fork 加的）：按区名取；取不到则**合并所有区的权重求和**（`:1188-1202`）；加权随机（`:1213-1238`）；`startRandomMotion(group, 3)` = `PriorityForce`（`:1245-1247`）。**key 必须等于模型 hit-area 的 draw id**，因为 `anyhitTest` 靠 `hitAreaIdHandle._id.s` 取私有字段（`:883-885`）。
- (B) SDK sample 层仍在跑：`lappdelegate.ts:90-105` 直接在 canvas 上 addEventListener → `lappview.ts:187-248` → `lapplive2dmanager.ts:108-138`，硬编码 `HitAreaNameHead` → `setRandomExpression()`、`HitAreaNameBody` → `startRandomMotion(MotionGroupTapBody, PriorityNormal)`（`lappdefine.ts:71-72,76-77`）。
- 两套都挂在重叠元素上（A 在 wrapper div、B 在 canvas），**所以一次点击可能既走 manifest 驱动的 tapMotions、又走硬编码的 Head→随机表情 / Body→TapBody**（已确认监听点都附着）。眼睛跟随只来自 (B)，且被 `_captured` 门控（`lappdelegate.ts:380`）——**只有按住鼠标时才跟随**，`onTouchesEnded` 里 `onDrag(0,0)` 弹回（`lappview.ts:222`）。
- 抄的：阈值对（200ms/5px）与加权 tapMotions；**不抄的**：两套并存 + per-move 全 drawable bbox 命中。

### 5.6 桌宠窗口（Electron）：hover 注册表 + 点击穿透 —— 本节最该抄的一段

`src/main/window-manager.ts:56-78` 建窗口：`transparent: true`、`frame: false`、`hasShadow: false`、`paintWhenInitiallyHidden: true`（**从一开始就是无边框透明**，不是切换出来的；`nodeIntegration: true` + `sandbox: false` `:71-73` 是安全坏味道）。

- 模式切换两阶段 + opacity 淡入 + 500ms 定时器（`setWindowMode :139-150` → `setWindowModePet :190-199`：`setBackgroundColor('#00000000')`、`setAlwaysOnTop(true, 'screen-saver')`、`setPosition(0,0)`；`continueSetWindowModePet :204-241`：窗口尺寸 = **所有显示器并集**（`:219-224`）以便跨屏拖拽，`setSkipTaskbar(true)`、`setFocusable(false)`，mac `setIgnoreMouseEvents(true)` + `setVisibleOnAllWorkspaces(...)`，**Win `setIgnoreMouseEvents(true, { forward: true })`** `:237`）。
- **hover 是「按组件的 hover 注册表」而不是全局开关**：`use-live2d-model.ts:314-332` 每次 pointermove 命中判定，**仅在状态变化时**发 IPC；主进程 `window-manager.ts:284-307` 用 `hoveringComponents: Set<string>`（`:19`），`shouldIgnore = size === 0` 才恢复穿透，`forceIgnoreMouse` 覆盖一切（`:288`）。另一个注册组件是 `'input-subtitle'`（`hooks/electron/use-draggable.ts:33-47`）。**这套「hover 注册表 + forward:true」就是窗口叠加最该抄的配方**：Windows 下鼠标事件穿过透明窗口落到下面的应用，只有指针在模型命中区上方时本 app 才声明交互。
- `setFocusable(false)`（`:229`）与 `updateComponentHover` 里翻回 `true`（`:303-305`）构成真实竞态。
- 桌宠模式**不持久化位置**：每次 `setPosition(0,0)` + 全屏并集 `setBounds`；只有窗口模式的几何存在内存里（`windowedBounds :12-17`、`:190`、`:166-173`），无落盘（无 electron-store）。
- 一条**已失效的实现**值得警惕：`WebSDK/src/main.ts:50-79` 整套 hover→穿透逻辑挂在 `document.getElementById("live2d")`，但元素是 `id="canvas"`（`live2d.tsx:113`），全仓没有 `id="live2d"` → `parent` 为 null，监听从未挂上（grep 确认）。**读 OLLV 代码时务必确认代码路径真的活着。**

### 5.7 barge-in（前端侧）：三层门控 + VAD 参数

- VAD：`context/vad-context.tsx:278-296` `MicVAD.new({ model: "v5", preSpeechPadFrames: 20, ... })`，UI 默认 `positive 50 / negative 35 / redemption 35`（`:81-85`）→ 生效 0.50/0.35；`onnxruntime-web` **精确 pin `1.14.0`**（`package.json:44`）为 vad-web 兼容；`@ricky0123/vad-web ^0.0.24`（`:34`）。
- **故意不在 VAD 假触发时打断**：`handleSpeechStart :197-203` 只快照 `previousAiStateRef`、不进 `listening`；`handleSpeechRealStart :208-217` 才 `interruptRef.current()` + 进 `listening`；`handleVADMisfire :251-260` 回滚到前态。
- `useInterrupt.ts:16-39`：`aiState !== 'thinking-speaking'` 直接 return；停音频+口型、清队列、置 `interrupted`、发 `interrupt-signal`。**不停 RAF、不停正在播的 motion/expression**——打断后动作/表情会继续跑完，只有音频与口型死。
- 三层丢弃门控：WS 入口 `websocket-handler.tsx:156-158`；入队 `use-audio-task.ts:253-258`；播放前两次 `:76-81`、`:169-173`。控制流确认：`:86-99` 已把文字追加进 chat，若随后在 `:169` 失败，会出现**有字幕无语音**（已确认）。
- 队列 `utils/task-queue.ts`：`new TaskQueue(20)`（`:76`，类默认 3000）；`clearQueue()`（`:23-27`）不取消在飞的 promise。
- 对照我们已验证的 F16-B1 结论（prewarm 收益未验证）：OLLV 这套前端门控是**状态一致性**的，不是延迟优化的，别把它当延迟证据。

### 5.8 每帧成本与 idle

- 单条全速 RAF（`lappdelegate.ts:168-221`，`:218` 自重排），**有 60fps 上限**：`lappdefine.ts:104-105` `ENABLE_LIMITED_FRAME_RATE = true / LIMITED_FRAME_RATE = 60`，实现 `:179-185`（`updateTime(false)` 跳过不累计 delta 是 fork 补丁，`lapppal.ts:45-52`）。**但这是上限不是 idle 节流**：无 `visibilitychange`/`document.hidden`/`IntersectionObserver` 处理（grep 0），Electron 桌宠模式下永远 60fps。
- **第二条永不停止的 RAF**：`use-live2d-resize.ts:96-110` `animateEase` 无条件自重排（`:109`，无 epsilon 退出），一旦滚轮缩放就活到组件卸载。常量 `:11-15`：`MIN_SCALE 0.1 / MAX_SCALE 5.0 / EASING_FACTOR 0.3 / WHEEL_SCALE_STEP 0.03`。并且 `applyScale` 是**覆写**不是累加（`CubismMatrix44.scale()` 赋值 `_tr[0]/_tr[5]`，`Framework/src/math/cubismmatrix44.ts:250-253`），和 `kScale`、`setupFromLayout`（`lappmodel.ts:417-418`）、拖拽写的平移分量共享同一个矩阵 —— 最后一个写的人赢（API 已确认，后果是推断）。
- idle 动作纯反应式：`lappmodel.ts:558-563` `isFinished()` 就 `startRandomMotion('Idle', PriorityIdle)`，无 dwell、无加权。"活着"的感觉全靠 Cubism 效果：CubismBreath 硬编码 `:286-319`、CubismEyeBlink `:275-283`（只在 motion 未更新本帧时生效 `:574-579`）、physics `:610-612`。

### 5.9 SDK/前端接缝：完全没有类型契约

`@ts-nocheck` 覆盖所有真正做 Live2D 的文件：`use-live2d-model.ts:6`、`WebSDK/src/main.ts:1`、`lapplive2dmanager.ts:1`、`lappmodel.ts:1`、`lappview.ts:1`、`lappwavfilehandler.ts:1`、`lappadapter.ts:1`；`any` 跨界访问 `model._wavFileHandler`、`model._modelMatrix`、`view._deviceToScreen`、`model._modelSetting._json`；跨边界靠 `window.getLAppAdapter()` / `window.getLive2DManager()` 全局（`main.ts:41,44-48`、`main.tsx:30`）。**且 `window.LAppDefine` 与 `window.LAppLive2DManager` 从未被赋值**（grep 无赋值）→ `use-live2d-model.ts:114` 的 `sdkScale` 恒 undefined → kScale 变化重载分支（`:119`）**不可达**；`playAudioWithLipSync`（`:56-89`）调用即抛。**这条是「靠全局变量做模块接缝」的代价，是我们 `AvatarEngine` 接口必须保持强类型的直接理由。**

### 5.10 关于「OLLV-Web 是否值得抄」的一句话判定

它是**负例参考 + 两处可抄**：可抄的是 hover 注册表 + `forward: true` 穿透（`window-manager.ts:284-307`）、多层 barge-in 门控（§5.7）、tap-vs-drag 阈值对与加权 tapMotions（§5.5）。其余（清单字段一半是死的、开环双取音频的口型、拆了再建的换模型、per-move 全 drawable bbox 命中、完全没有 overlay 协议）都是「别这样做」的证据。**它的 manifest / 状态机 / 口型没有任何一处能映射到我们的 `renderer / model_ref{asset,version,hash} / effects[] / stickers[] / touch_zones[]`。**

## 6. shimeji / Shijima-Qt：桌宠的「行为语法」线

Java 引擎（Shimeji-ee fork `com.wishes`，`27f795d1`）与 Qt 重写（Shijima-Qt，`57723f1d`，已声明停更）。**它是这一整个调研里唯一把「动作/行为」做成可声明 DSL 的一家**，也是唯一有真物理的一家。前面 §4 的 Live2D 桌宠全部没有这层。

**结构性限制先声明**：Shijima-Qt 的 `libshijima` / `libshimejifinder` / `cpp-httplib` submodule 是空目录（`.gitmodules:1-8`），它的 XML 解析器不在 checkout 里（只有调用点 `MascotData.cc:67-68`、`Makefile:60 -Ilibshijima`）。因此下面所有 schema 级结论引自 Java 引擎；Shijima-Qt 自己声明目标是「fully compatible with Shimeji-ee」（`com.pixelomer.ShijimaQt.metainfo.xml:10-11`）。

### 6.1 行为格式：schema 是外置的，这是它最可抄的一点

- **`conf/schema.properties` 外置 schema**：`Configuration.java:58` 用 `ResourceBundle "conf.schema"` 读标签名与属性名；仓库里有 22 份该文件副本（各语言包各带一份）。含义是**标签名本身是数据**，换 schema 不用改解析器；连 xmlns 都只是装饰（`Entry.java:34-37`）。语言嗅探用「動作リスト」这类本地化名（`Configuration.java:48-56`）。
- `actions.xml` 元素族（从解析器读，不是 README）：`<Constant>`；`<Action Name Type Class>`——**除 Name/Type 外任何属性都变成运行时变量**（`ActionBuilder.java:54`）；`<Animation Condition IsTurn>`；`<Pose Image ImageRight ImageAnchor "x,y" Velocity "dx,dy"(px/tick) Duration(ticks) Sound Volume>`（`AnimationBuilder.java:92-93,183-187`）；`<Hotspot Shape Origin Size Behaviour>`（`:271-274`，`Shape` 只接受 `Rectangle|Ellipse`，否则抛错 `:296`）。
- Type 是**封闭集**：`ActionBuilder.java:124-133` = `Move / Stay / Animate / Sequence / Select`，另有 `Type="Embedded" Class=…` 反射入口（`:98-122`，XSD 正则限定 `com\.group_finity\.mascot\.action\.[a-zA-Z][a-zA-Z0-9]+`，`Mascot.xsd:65-71`）。
- 每个 action 的属性可以从类里穷举出来（这就是可抄的清单）：`ActionBase.java:25-39` `Condition / Duration(默认 MAX_INT) / Draggable(true) / Affordance`；`BorderedAction:22-30` `BorderType Ceiling|Wall|Floor`；`Move:21-27` `TargetX/Y`；`Jump:21-32` `VelocityParam 20.0`；`Fall:22-40` `InitialVX/VY, ResistanceX .05, ResistanceY .1, Gravity 2`；`Dragged:28-38` `OffsetX 0 / OffsetY 120 / OffsetType`；`Sequence:17` `Loop`；`Breed:28-47`；`Transform:24-28`；另有 Interact / Scan* / Complex* / FallWithIE / WalkWithIE / ThrowIE / Offset / Look / Turn / Mute / Regist。
- **值语法**：`${...}` 只在初始化求值一次，`#{...}` **每帧重新求值**（`Variable.java:14-24`、`Script.java:50-55`），跑在 Nashorn + `ScriptFilter` 沙箱（`Script.java:19,34`）。例：`actions.xml:639` `TargetX="${ mascot.anchor.x+(mascot.environment.cursor.x-mascot.anchor.x)*Math.random()/2 }"`。XSD 对 Parameter 的模式 `:193-197`。
- `behaviors.xml`：`<Behavior Name Action(默认=Name) Frequency(必填 int) Hidden Condition Toggleable>`（`BehaviorBuilder.java:51-67`），嵌套 `<Condition>` 是 AND 链，`<NextBehavior Add>` 决定替换还是追加（`:82-89`），`<BehaviorReference>`（`:115-118`）。**加权随机 + 终态兜底**已验证（`Configuration.java:210-225`：落不到任何行为时 `setAnchor(随机 x, top-256)` → `Fall`）；`Frequency 0` 视为无效（`isEffective()`，`:146-147`）。图闭包校验 `BehaviorBuilder.java:125-128` + `ActionRef.java:56-59`，由 `Configuration.validate() :153-161` 在 `Main.java:256` 跑；重复名致命（`:73-76`）。
- **硬要求的名字是编译进代码的**：`UserBehavior.java:35,37,39`（Fall / Dragged / Thrown），使用点 `:159,187,266,295` 与 `Configuration.java:222,259`。这是「包必须提供某些能力」的最小契约——对应我们 payload 里「包必须声明哪些 motion 组」的门控。
- `<Information>` 侧车（`Configuration.java:88-92,125-151`：Name / PreviewImage / SplashImage / Artist / Scripter / Commissioner / Support + URL）——**格式存在但没有任何随包使用它**。这是元数据字段「有格式、无实践」的又一例。
- **不存在**的标签（我按猜测清单逐个查过，`schema.properties` 与所有解析器都无）：`<motion>`、`<frame>`、`<idle>`、`<insert>`、`<wait>`，属性 `frame_width/frame_height` 也没有。任何把 shimeji 描述成有这些标签的文档都是错的。

### 6.2 命中区：**代码里具名分区是完整的，随包一个都没用**

- 一只宠物一个窗口（`Mascot.java:68` 透明窗口，`:142` alwaysOnTop，`:145-158` press/release）。非矩形输入靠**逐像素 alpha**：`GenericTranslucentWindow.java:102` 每帧 `setWindowMask`；`WindowsTranslucentWindow.java:105-108` `UpdateLayeredWindow(ULW_ALPHA)`。
- **`<Hotspot>` 是功能完整的**：`Hotspot.java:26-35`（带镜像翻转，已逐字读）、`UserBehavior.java:121-130` 触发行为、`ActionBase.java:131-146` 每帧刷新、`Mascot.java:581-600` hover 时改光标（affordance）。但 **`grep "<Hotspot" demo/` = 0** ——两个仓库所有包零实例。`Affordance` 同样接线完整（`ActionBase.java:93-97`、`Manager.java:356-368`）且零使用。
- **`Dragged` 会清掉 hotspots**（`:110-114`）。区域身份 = **行为名，不是身体部位名**。
- 于是这一家对我们 `touch_zones[]` 的启示是精确的两条要改的缺陷：(a) 区域无名、只挂在一个行为字符串上，服务端/LLM 无法寻址「头」；(b) 区域挂在**动画**上、每帧重建，因此活不过一次换帧。我们的 `touch_zones[]` 必须**具名、几何化（不止 rect/ellipse）、附着在模型而不是动作上**。
- 拖拽/投掷：press→Dragged、release→Thrown（`UserBehavior.java:182-187`）；初速直接取系统光标位移 `actions.xml:323` `InitialVX="${mascot.environment.cursor.dx}"`；dx/dy 每 tick 计算（`Environment.java:95-99,113-118`）。**完全没有 tap 手势**——它是「抓起来扔」的桌宠，不是「点一下有反应」的桌宠。
- Shijima-Qt 有一个显式的 app 级 hit test（`ShijimaManager.cc:1054-1062`）：**按 `std::list` 插入顺序 first-wins（z-order bug）**；`pointInside` 采样 alpha 并带 `//FIXME`（`ShijimaWidget.cc:236-240`）；`event->ignore()` 做点击穿透（`:344-346`）。全仓无 hotspot（grep 0）。

### 6.3 物理：40ms tick + 质点，没有旋转

- `TICK_INTERVAL = 40`（`Manager.java:30`）。Shijima-Qt 用 `SHIJIMAQT_SUBTICK_COUNT 4` 把墙钟 tick 降到 10ms 但**保留同一批常数**（`ShijimaManager.cc:66,738,862`）。
- Fall 已逐字读（`:86-91`）：阻尼 + 重力 ≈1250 px/s²。**扫掠式碰撞** `Fall.java:104-129`，窗口顶部探针硬编码 −80..0。
- **任何地方都没有旋转**；朝向是布尔 + 自动翻转（`ImagePairLoader.java:34`）。`Move` 不是操舵（`actions.xml:615-640` 直接算目标）。
- 一个很好笑的证据：包里的键名有**永久拼写错误** `RegistanceX/RegistanceY`（`demo/conf/schema.properties:27-28`）——schema 外置让错拼成了事实契约。fork 加了 `Wall.java:61 TOLERANCE=2`。carry 上限 80/20；传送 −256。`Dragged.java:94` 临界阻尼 lerp `(dx + (newX-footX)*0.1)*0.8`，并**故意**掺 `Math.random()` 破坏确定性（`:105-106`）。「无活动窗口」哨兵 `{-50,-50,-50,-50}`（`ShijimaManager.cc:858`）。

### 6.4 窗口叠加：桌面做法，以及哪些**不能**搬到 Android

- Qt flags：`WindowStaysOnTopHint | FramelessWindowHint | WindowDoesNotAcceptFocus | NoDropShadowWindowHint | WindowOverridesSystemGestures`（`ShijimaWidget.cc:66-68`）；`WA_TranslucentBackground`（`:62-64`）；窗口 mask 只在 Linux 生效（`Platform/macOS/Platform.mm:35-37`、`Platform/Windows/Platform.cc:39-41`、`KWin.cc:76`）；mac `CanJoinAllSpaces | FullScreenAuxiliary`（`Platform.mm:31-32`）。
- 多显示器：每块 `QScreen` 一个环境对象（`ShijimaManager.cc:607-616`），热插拔 `:710-713`，**拖拽中途跨屏交接** `:1008-1012`。Java 侧是所有显示器虚拟边界并集，由 `MIN_PRIORITY` 线程每 5000ms 刷一次（`Environment.java:39-56,64-83`）。Windows 侧每只宠物每 tick 一次 `EnumWindows` + DWM cloak 判断（`WindowsEnvironment.java:141-169,310-319`）。
- **Android 对应关系（重要）**：`TYPE_APPLICATION_OVERLAY + FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`；alpha 采样命中 ≈ 「view 尺寸等于 sprite」。但 **「一只宠物一个 OS 窗口」不成立**——Android 只能一个（或少量）overlay window，多宠物必须在同一 view 内部分区，这正是 `touch_zones[]` 必须存在而不是可选的原因。前台 app 几何需要 `PACKAGE_USAGE_STATS` / 无障碍权限。

### 6.5 包格式与版本化：**两边都没有 version / hash / integrity**

- Java：`img/<SetName>/` + `conf/`；启用集以 `/` 连接存在 `conf/settings.properties:3 ActiveShimeji=妹红/芙兰朵露`（读于 `Main.java:151`）。**靠文件名猜的级联**在 `Main.java:217-247`（配合编码/乱码回退）。
- Shijima-Qt：一个真一点的包契约 `<name>.mascot/` 含 `actions.xml`、`behaviors.xml`、`img/`。
- **两边都没有版本字段、没有 hash、没有签名、没有安装事务**（硬负面）。ChatWaifu（§2.6）→ shimeji → BongoCat（§7.2）→ NyaDeskPet（§8.2）在这条轴上毫无进步，只有 BongoCat 把「防御 + 原子提交」做严了。

## 7. pixi-live2d-display 与 BongoCat：一个给 Web 语义，一个给工程纪律

### 7.1 pixi-live2d-display（`31317b37`，2023-12-15，MIT，**事实冻结**）

- **8 段 middleware 加载链**（`src/factory/Live2DFactory.ts:151-160`）：`ZipLoader, FileLoader, urlToJSON, jsonToSettings, waitUntilReady, setupOptionals, setupEssentials, createInternalModel`。文件级解析位置：`urlToJSON` `model-middlewares.ts:15-31`；moc `:183-194`；textures `:143-148`；pose `:86-103`；physics `:105-124`；motion **惰性** `:242-308`（按 `MotionManager` 存 `WeakMap`，`:166-169`）；expression 惰性 `:317-375`。
- **Cubism2/4 分派是注册表不是 if/else**：`registerRuntime` 按 `version` 降序（`:179-184`），`findRuntime` 依次 `runtime.test(source)`（`:191-197`）→ **两者都匹配时 Cubism 4 赢**。版本判定是**结构嗅探**不是声明字段（`src/cubism4/Cubism4ModelSettings.ts:16-24`：有 `FileReferences.Moc` 字符串 + `Textures` 非空且全是字符串）。moc 魔数校验 `"MOC3"`（`cubism4/factory.ts:21-29`）。
- **`ZipLoader` 是它唯一的「包」抽象**：喂虚拟文件列表给 `ModelSettings.validateFiles`（`src/cubism-common/ModelSettings.ts:138-162`），**只对 moc + textures 报错**，pose/physics/expression/motion 视为可选。`replaceFiles(replacer)`（`:93-107`，Cubism4 覆写 `Cubism4ModelSettings.ts:41-67`）是上游唯一能改写所有路径的钩子——最接近我们 `model_ref` 间接层的东西。
- **命中**：三层。世界→模型画布（`Live2DModel.ts:227-233` + `toModelPosition :242-263`，采样空间是 moc 自身画布 `originalWidth/Height`，`InternalModel.ts:67-73`）；`HitAreas[].Id → drawable index`（`cubism4/Cubism4InternalModel.ts:151-159`）；测试 = 当前顶点 AABB（`InternalModel.ts:195-210` + `getDrawableBounds :218-244`）。`index >= 0` 过滤 + **按 Name 建 map**（`:170-176`）。`hitTest` 返回**全部**匹配（`:184-186`）→ 重叠时**未文档化的对象 key 序**。`Live2DModel.tap()` 只 `emit("hit", names)`（`:211-219`）——**名字→动作组的映射不是库的职责**。调试工具 `src/tools/HitAreaFrames.ts` 画每个区的 bbox，只在 `pixi-live2d-display/extra` 里发布。
- **注视/gaze**：`focus(x,y,instant)` `:189-202` 归一化到 `[-1,1]`；`FocusController`（`src/cubism-common/FocusController.ts`）是**临界阻尼式速度限制器**，不是缓动：`EPSILON 0.01 :4`、`MAX_SPEED = 40/7.5 :6`、`ACCELERATION_TIME = 1/(0.15*1000) :9`；`update(dt) :53-86` 夹加速度（`:66-71`）并夹「不失速到达」速度 `maxV = 0.5*(sqrt(maxA²+8*maxA*d) - maxA)`（`:77-82`）。写参数 `cubism4/Cubism4InternalModel.ts:239-249`：EyeBallX/Y、AngleX/Y = ±30、AngleZ = x*y*-30、BodyAngleX = x*10，**全部 `addParameterValueById`**（在 `saveParameters()` 之后叠加），这就是动作与注视能共存的原因。**这套 gaze 平滑是我们直接可抄的数值方案。**
- **动作仲裁（可抄的核心）**：`MotionPriority { NONE, IDLE, NORMAL, FORCE }`（`src/cubism-common/MotionState.ts:7-19`）。规则 `reserve() :83-147`：`IDLE` 在 `currentPriority !== NONE` 或已有 idle 预约时拒绝（`:102-121`，idle 用独立槽 `:67-74`）；`NORMAL`+ 在 `priority <= currentPriority`（`:124`）或 `<= reservePriority`（`:133`）拒绝；**`FORCE` 绕过两项检查**（`:123`）。同组同序号已在播/已预约 → 拒绝（`:89-100`）。**没有队列**，只有 reserve/start 两段握手：`MotionManager.startMotion() :212-288` 先 `reserve`（`:217`）→ 异步 `loadMotion`（`:253`）→ `start`（`:266`）再确认这个 motion 仍持有预约（`MotionState.start :157-189`，不匹配则报 "Cannot start motion because another motion has taken the place."），落败者的音频被 dispose（`:267-270`）。idle 自动重排 `:338-358`。expression 交互 `shouldOverrideExpression() :257-259` = `!preserveExpressionOnMotion && currentPriority > IDLE`。淡入默认 `motionFadingDuration 500 / idleMotionFadingDuration 2000`（`MotionManager.ts:38-41`、`src/config.ts:36/:41`），优先级注释 `model.json > motion.json > config`（`cubism4/Cubism4MotionManager.ts:80`）。
- **口型：这里根本没有实现**（硬负面，也是它最大的坑）。`Cubism4InternalModel.ts:33` `lipSync = true` 是死旗；`:221-228` 是**注释掉的 TODO**（逐字 `// TODO: Add lip sync API`）。LipSync 参数 id **会**从 `Groups` 解析（`Cubism4MotionManager.ts:35`）并交给 motion 当 effect ids（`:95`）——即 Cubism Framework 会在 motion 期间**压制**这些参数，而没人写它们。任何基于 pixi 做口型的计划都不成立。
- **每帧成本**：`Live2DModel.update()` `:290-295` 只累加时间（注释 `:294` 说明需要 GL context，故意）；真正的工作在 `_render :297-367`：四次 renderer reset（`:299-302`）、**每帧重新绑定所有纹理**（`:315-346`，原因注释 `:335-338`）+ 手动 GC 计数 touch（`:345`）、`internalModel.update` 仅在 deltaTime 非零时（`:352-355`）。**无 dirty flag、无最小间隔、无可见性判断、无 idle 路径**；帧率完全由 `PIXI.Ticker` 决定。离屏模型照样跑全链。
- 另外两个**常被误归于本库的选项其实不存在**：`hitMotionAreas`、`hitMotions` 在全树（README、docs、src、test）grep 均 0——它们是 OLLV/shirakawa 系前端叠上去的。
- 活跃度：2023-12-15 最后提交，`package.json:3` 是 `v0.5.0-beta`，peer dep `pixi.js ^7.0.0`。**按事实冻结对待**：Cubism 4 时代、无口型、`autoInteract` 已废弃、`utils.url.resolve` 标着 "FIXME: deprecated API"（`ModelSettings.ts:73`）。

### 7.2 BongoCat（`47a73ed1`，2026-10-28 v2.4.1，Apache-2.0）—— 包防御与每帧纪律的最强样本

Rust，GPUI 设置 UI + 原生 D3D11(DirectComposition) / Metal 叠加窗口（理由写在 `docs/adr/0003-native-overlay-renderers.md:8`：GPUI 没有为双平台 Live2D 外部纹理提供稳定公共合成接口）。

**包格式（`crates/bongocat-model/`）**
- 规范形状作为兼容清单钉在 `docs/technical-design.md:1646-1656`：`.model3.json .moc3 .motion3.json .exp3.json .physics3.json .pose3.json .cdi3.json texture_*.png audio resources/left-keys resources/right-keys resources/background.png resources/cover.png`，并写明约束（`:1656`）：**「新增 manifest 只能是可选元数据，不能破坏现有模型」**。
- 名字/上限常数 `limits.rs`：`INDEX_SCHEMA_VERSION = 1 (:10)`、`MODEL_ID_MAXIMUM_LENGTH = 64 (:14)`、`PACKAGE_RESOURCES_DIRECTORY = "resources" (:18)`、`PACKAGE_COVER_FILE = "cover.png" (:23)`（文档 `:20-22`：没封面是「没东西可展示」而非非法包）、`MOTION_TIME_TOLERANCE = 1e-6 (:25)`。
- **入口唯一性**：包根的直接子项里**恰好一个** `*.model3.json`（`package.rs:379-418`）；0 个 → `ModelEntryMissing`（`:406-410`），≥2 → `ModelEntryAmbiguous`（`:412-416`），候选按字典序（`:404`）。`FileReferences.Version` 必须**正好等于 3**（逐字 `package.rs:249-255`）。
- **对抗性包络**（`limits.rs:49-58`，逐字）：`maximum_texture_dimension 8192`、`maximum_json_bytes 16MiB`、`maximum_json_depth 64`、`maximum_file_bytes 512MiB`、`maximum_package_bytes 1GiB`、`maximum_file_count 4096`、`maximum_directory_depth 32`。
- **路径安全只有一个原语** `normalize_reference`（`reference.rs:14-56`：拒绝空、NUL、绝对、`..`、`RootDir`、`Prefix`、任何含 `:` 的组件，`\`→`/`），外加 canonicalize + `starts_with(root)`（`package.rs:46-55`），以及**直接拒绝包内符号链接目录**（`:506-512`）。清单从磁盘构建而非从 index 声明构建（`:177-214`），并回报 `unreferenced_files`。
- **导入只接受文件夹——archive 导入是被主动撤回的**：`docs/adr/0036-model-archive-import-boundary.md:3` 状态 已接受(2026-09-16) → **已撤回(2026-09-22)**，撤回内容枚举 `:11-25`，`:29` 警告恢复需要新功能而非 revert。被撤的规范里仍有可抄材料：magic bytes `PK\x03\x04 / PK\x05\x06 / PK\x07\x08`（`:69-70`）、剥外层目录（`:107-121`）、先规划再解压的 zip-bomb 防御（`:94-105`）、明确拒绝 `.rar/.7z/.tar.gz`（`:219-220`）。现行导入 `store/mod.rs:338-344`（"The source is a folder and nothing else"）。
- **导入事务**（`store/mod.rs:509-588`）：先在**源**上 prepare（`:544-548`）→ `AlreadyExists` 预检（`:550-557`）→ staging 目录 `.importing-<id>-<pid>-<seq>` mode 0700（`:559`、`staging.rs:11-13`）→ `copy_package`（`:567-575`）→ `normalize_legacy_key_image_names(&staging)`（**唯一的文件名改写**，绝不动用户副本 `:576-579`）→ `commit_installed_staging` 重新校验后**一次 `fs::rename`**（`:596-655`，rename 在 `:638`）；`StagingCleanup: Drop` 解除前删除 staging 树（`staging.rs:34-54`），启动时清扫孤儿 staging（`store/mod.rs:762-822`）。
- 身份：模型 id 是与目录名无关的随机 UUIDv4（`store/mod.rs:236-256`）；可移植规则 `limits.rs:66-90`（1–64 字节、ASCII alnum+`-_.`、不得首尾点、拒绝 Windows 保留名 `:92-104`）；JSON pattern `^[A-Za-z0-9_-](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9_-])?$`（`shared/config/config.schema.json:25,:101,:360`）。预置包在签名 bundle 里只读，用户替换封面写到 `<model-overrides>/<id>/resources/cover.png`（`preset_covers.rs:1-14,:69-71`）。列表**故意宽容**：坏包以 `ModelCatalogEntry::Invalid` 出现而不是整批隐藏（`store/mod.rs:157-213`、`catalog.rs:10-26`）。
- 校验规则值得直接搬（`validate.rs`）：cdi3 的 `Parameters/ParameterGroups/Parts` 非空且唯一 + 声明的 `GroupId`（`:27-70`）+ Cubism5 独有 `CombinedParameters` 引用已声明参数（`:71-81`）；exp3 `Type == "Live2D Expression"`（`:98-100`）；motion3 的 `Meta.CurveCount/UserDataCount/TotalUserDataSize` **必须与实际数组吻合**（`:156-178`）、UserData 时间在 `[0, duration+1e-6]`（`:164-172`）、曲线 ≥2 段且全部有限（`:179-190`）；pose3 `Type == "Live2D Pose"`（`:208-210`）、id 唯一、链接不自指（`:220-243`）。诊断码是**封闭稳定协议**：22 个码 `error.rs:12-35` + 稳定字符串 `:38-63`（`model_entry_missing`、`model_reference_symlink_escape`、`model_texture_dimension_exceeded`…），文档 `:5-6` 明说「每个码都稳定，因为设置协议按名字映射本地化文案」；store 层 12 个（`store/diagnostic.rs:13-27`）。跨语言 fixture 契约 `shared/fixtures/model-fixtures/cases.json`（`schemaVersion: 1`，8 例）由 `fixture_contract.rs:162-235` 走产品解析器复跑，并断言导入后源码树字节不变（`:228-233`）。
- **硬负面（对我们最关键）**：**没有包级版本字段、没有 hash、没有签名、没有 manifest、没有 provenance**。`INDEX_SCHEMA_VERSION` 只 stamp 进内存索引（`package.rs:360`），从不落盘；读到的 `Version` 全是 Cubism 自己的（model3 == 3、cdi3 == 3 `validate.rs:23-25`、motion3 == 3 `:143-145`，physics3 解析但**从不检查** `raw.rs:232-233`）。**没有 model 包的 JSON Schema**（`shared/config/` 只有 config 与 window-state 两个 schema）。所以 BongoCat **无法区分一个包的两个版本、也无法校验已装包的完整性**。

**「部位」契约其实是 20 个硬编码参数 id**
- grep `GroupMouse|GroupKeyboard|GroupGamepad|PartKeyboard|PartMouse|PartArm|PartTail` 在 `crates/ resources/ shared/ docs/` **零产品命中**（`PartArm` 只作为 pose3 fixture 字符串出现在测试里）。运行时查的组名只有三个：`Breath / EyeBlink / LipSync`（`crates/bongocat-live2d/src/lib.rs:111-113` 逐字），解析按 `Target=="Parameter" && Name==name`，**只取第一个匹配**，上限 `MAX_MODEL_EFFECT_TARGETS = 64`（`load.rs:80-95`）。
- 真正的部位是 20 个写死 id：`parameter.rs:10-30`（`AngleX/Y/Z`、`EyeBallX/Y`、`CatParamLeftHandDown/RightHandDown`、`ParamMouseX/Y`、`ParamMouseLeftDown/RightDown`、`CatParamStick*`），写表 `rendering/model_input.rs:84-120`。**唯一真实的「模型声明能力」机制**：摇杆猫爪要求模型**有** `CatParamStickShow*`（`model_input.rs:73-82`）。
- 随包预置声明了**零个部位组**：`resources/models/keyboard/cat.model3.json:54-75` 只有 `Breath/EyeBlink/LipSync`（且 LipSync `Ids: []`）；gamepad 同样三个但顺序不同。预置 expression 用**文件名当名字**（`Name == File`，`:11-24`），motion 组 `CAT_motion` 与 `CAT_motion_lock` 指向**同样的两个文件**（`:25-52`）——`docs/adr/0088-concurrent-motion-playback.md:19-21` 正因如此说「按组名排序是错的」。每个预置还都带着没人引用的 `exp_1/exp_2.exp3.json`（呼应 §2.4 ChatWaifu 同现象）。
- 输入→视觉走 **PNG 文件名词表**，不是 schema：只有 `resources/left-keys` / `right-keys` 直下的 `.png` 有效（`bongocat-live2d-render/src/lib.rs:184-199`），asset id = **文件 stem**（`:203`）；按键没有对应图就完全 inert（门控 `KeyImageInventory :113-158`，策略 `docs/adr/0042-key-press-requires-its-key-image.md:56`「每模型绑定 = 静态 hand 表 ∩ 该模型的键位图」）。
- **「模式切换」是从包内 stem 推断的**（`store/input_mode.rs:100-120`）：有 gamepad 图 → Gamepad；否则有 right-keys → Keyboard；否则 left-keys → Standard；否则报 `InvalidPackage`。优先级 gamepad > keyboard > standard；24 个 gamepad stem 是硬编码表（`:52-57`）——**推断风险**：表里有 `Select/Start/South`，所以带这类 stem 的键盘包会被误判成 gamepad。解析结果在导入时落盘一次并连同标题存储，使「改名、换美术、重启都不能悄悄改变显示的模式」（`config_schema/model.rs:371-376`）。
- 行为 id 的规范 wire 形式：`behavior_id()`（`shortcuts/command.rs:103-108`）= `motion:<group>:<index>` / `expression:<name>`。随机调度器 `random_behavior.rs`：默认 30s、min 1、max 3600（`:11-15`）；`#[default = Off] enum {Off, Expressions, Motions, MotionsAndExpressions}`（`:41-48`）；**均匀、无权、无近期惩罚**（`:150-153`）；自动动作插 `Idle` 优先级、永不切用户动作（`worker/random_behavior.rs:71-76,:96-101`）；**被挡住的到期 tick 是被消费而不是重试**（`random_behavior.rs:143`）。
- 行为契约写成跨语言文档，值得当协议模板读：`shared/behavior/input-semantics.md`（184 行，版本 1；pointer→model 比例 `:32-38`、平滑 `alpha = 1 - 0.75^(elapsed*60)` `:45-48`、**两套互不兼容的键名词表** `:77-82`、左右手由作者把 PNG 放哪个目录决定 `:111-112`、reset 原因 `:174-182`）；`shared/behavior/animation-semantics.md`（86 行，版本 1；优先级 `idle|normal|force` `:9`、**触发的 motion 即使 `Meta.Loop = true` 也只播一个循环** `:11`、breath 用 `current + value * 0.5` 且施加在 Core clamp 之前 `:58-70`、eyeblink 是 5s 周期内确定性的 180ms 闭合窗）。

**全局输入捕获（桌宠/键鼠可视化的核心难点）**
- **不用 rdev**，每家手写。macOS `crates/bongocat-platform/src/macos.rs`：`CGEvent::tap_create(HIDEventTap, HeadInsertEventTap, ListenOnly, …)`（`:519-528`），理由带测量记录 `:513-518`（在 session tail 挂 tap 时 Right-Shift 的 `FlagsChanged` release 永远收不到，HID head 才有完整配对）；mask 13 种事件类型（`:1193-1209`）；tap 死亡（`TapDisabledByTimeout|TapDisabledByUserInput`）清状态并**重建 tap**（`:1224-1234`、`:799-822`）；**Caps Lock 没有物理 key-up**，平台合成一个（`CAPS_LOCK_KEY_CODE = 57 :1569`、`CAPS_LOCK_AUTO_RELEASE = 100ms :199`，测量 `:183-198`）；TCC 用 `CGPreflightListenEventAccess` 只读 + 仅在用户明确动作时 `CGRequestListenEventAccess`（`:664-675`，策略 `docs/adr/0024-macos-tcc-capability-boundary.md:15-23`），并诚实记下 gap：重新授权后没有触发重启输入服务（ADR-0078 `:121-123`）。
- Windows：**Raw Input 而非低级钩子**（`SetWindowsHookEx/WH_KEYBOARD_LL/WH_MOUSE_LL` 在任何 Rust 源里都不存在；ADR-0081 `:142` 明确拒绝钩子）。`RegisterRawInputDevices`（`:1052-1056`）用 `RIDEV_INPUTSINK | RIDEV_DEVNOTIFY`（`:1141-1149`）——`RIDEV_INPUTSINK` 是别的前台 app 时仍能捕获的关键；隐藏消息窗（`:69`）+ 工作线程 `GetMessageW`（`:1085-1099`），每轮 `service_tick()`，定时器 `SERVICE_TICK 16ms / RECONCILIATION 250ms`（`:71-74`）。
- Linux：**不存在代码路径**（`grep -rn 'target_os = "linux"'` 零命中），文档 `docs/adr/0006-linux-post-launch-strategy.md:14` 明写 Linux 不进首发，`:18` 禁止承诺 Wayland 等效捕获，`:25` 要求显式降级而非静默丢输入。
- **最可复用的设计：可调和的输入状态**（`docs/adr/0004-reconcilable-input-state.md`）：三类信号（边沿 / 系统状态校正 / 生命周期 `Reset`）`:12-16`；边沿走可靠有序队列，鼠标移动与摇杆轴走 latest-value 合并且**绝不能阻塞 release** `:18`；逐字 `:22`：默认校正间隔 **250ms**，一个按下键必须在**连续 2 次**系统快照里都缺席才合成 release，`Reset` 不等阈值。**按键 release 超时被刻意移除了** `:24-26`——「用时长区分等于把一帧变慢翻译成一个猫不再相信的按键」；并作为**必须拒绝的 fixture** 固化（schema 无 keyboard 命名空间 `config.schema.json:123-135`、`config_schema/input.rs:1-5`、`fixtures/invalid-key-release-timeout.json:25-32`）。两击规则在每层都有实现（`macos.rs:178/:313`、`windows.rs:74-75`、`input_state/mod.rs:14`，策略文档 `input_state/policy.rs:1-9`）。**对 Android**：没有 `GetAsyncKeyState` 之类等价物，所以 `Reset` 路径（灭屏、keyguard、焦点丢失）要承担更重的责任。

**窗口/叠加 flags（这一节最高价值）**
- Windows（`crates/bongocat-overlay/src/windows/window.rs:113-127` 重读）：桌宠模式 = `taskbar_ex_style(...) | WS_EX_NOACTIVATE | WS_EX_NOREDIRECTIONBITMAP`，点击穿透时**必须成对** `WS_EX_TRANSPARENT | WS_EX_LAYERED`；注释 `:121-126` 是不显眼的关键——**`WS_EX_TRANSPARENT` 单独用**时 DirectComposition 顶层窗口仍在桌面输入路径上，`WM_NCHITTEST` 返回 `HTTRANSPARENT` 但点击仍会选中该 HWND。任务栏抑制 `WS_EX_TOOLWINDOW` vs `WS_EX_APPWINDOW` 二选一（`:19-24`）。非客户区：桌宠 `WS_POPUP`；窗口模式 `WS_OVERLAPPEDWINDOW & !WS_MAXIMIZEBOX`（`:146-165`）。置顶但不激活：`SetWindowPos(..., HWND_TOPMOST|HWND_NOTOPMOST, SWP_NOACTIVATE)`（`:201-215`）；**用 `SW_SHOWNOACTIVATE` 显示而非 `SW_SHOW`**（`:228-248`，理由 `:241-245`）；每次 z-order 变更都带 `SWP_NOACTIVATE|SWP_NOMOVE|SWP_NOSIZE`（`:431-449`）。运行时切穿透：先重贴 style 再 `SWP_FRAMECHANGED` 让 shell 重读（`:505-533`）。`WM_NCHITTEST`（`window_proc.rs:203-229`）：style 要求则 `HTTRANSPARENT`；插件命中矩形 → `HTCLIENT`；窗口模式 → `DefWindowProcW`；否则**整个客户区返回 `HTCAPTION`**（这就是无边框 popup 能被「拽着模型」拖动的原因，`:229`）；插件点击在 user32 之前被截走（`handle_plugin_click :111-137`，否则插件按钮会去拽猫）。全屏置顶等价物（ADR-0096）是**逐帧修复实际层序**而非只设 style 位：`ensure_topmost_above(foreground)` `:413-424` + `window_is_above` `:536-546` + 同步 `EnumWindows` 走到两者中第一个即停（`:547-568`）；ADR `:25-41` 禁止无脑高于所有 topmost（菜单/tooltip 必须在猫之上），禁止用 worker/timer/WinEvent 钩子；诚实限制 `:56-58`。
- macOS（`macos/renderer.rs:102-125` 重读）：`NSPanel` + `Borderless | NonactivatingPanel`（后者 = 不抢焦点）、`setOpaque(false)`、`setHasShadow(false)`、`setAnimationBehavior(None)`、`setAlphaValue(...)` **一次性作用在整块 panel surface**（注释 `:114-117`，防鬼影的决策）、`setCollectionBehavior(CanJoinAllSpaces | FullScreenAuxiliary)`、`setMovableByWindowBackground(true)`、`setIgnoresMouseEvents(click_through)`；运行期切换就是 `setIgnoresMouseEvents`，仅在变化时写（`apply_presentation :194-203`）；hover/idle 隐藏时**强制打开穿透**（`:187-193`、`macos/session.rs:594-598`）。层级数字 `macos/geometry.rs:12-28`（always-on-top 时 `NSStatusWindowLevel`）来自测量记录 `docs/adr/0045-overlay-placement-screen-bounds.md:9-15`：menu bar 24、Dock 20、AppKit 弹出菜单 101，且 `constrainFrameRect` 只在 level < 24 才钳制——**拦路的是 level 不是拖拽约束**。Metal 让透明真正生效的配置 `renderer.rs:127-152`：`setWantsLayer(true)`、layer 非 opaque、`presents_with_transaction(false)`、`framebuffer_only(false)`、`setAllowsNextDrawableTimeout(true)`（`:149`，无头/被遮挡合成器不能卡在 `nextDrawable`）、`setMaximumDrawableCount(3)`（`:150`）。
- 放置策略（`docs/adr/0045…`）：约束区 = 所有 display frame 并集，允许负坐标、允许盖任务栏/Dock（`:32-34`）；跨两屏合法（`:35-36`）；纠正目标是重叠最大的那块且**保持尺寸**（`:37-38`）；纠正**延迟** `PLACEMENT_SETTLE_DELAY = 1s`（因为原生拖拽循环会阻塞帧源，`:25-28,:39-41`）；复查缓存 500ms 而不用 display 变更通知（`:42-44`、代码 `placement.rs:37,45,52`），后果写在 `:65-67`。放置只移动不缩放。几何持久化**与 config 分文件**：`window-state.json`（`schema_version: 1`，`settings_window`/`overlay_window`，`shared/config/window-state.schema.json:80-111`），`scale_percent` 留在 `config.json`。
- 右键拖拽缩放（`resize_drag.rs:18-31` 重读）：`RESIZE_DRAG_THRESHOLD 3.0`、`RESIZE_DRAG_PERCENT_PER_PIXEL 0.5`、min 25 / max 400；阈值用欧氏距离（`:13-17`）；Windows 传 DPI 换算后的物理像素、macOS 传 point，状态机因此 DPI 无关（`:28-29`）。ADR-0057：不再需要 Shift（`:37-39`）；起始 scale 从窗口**当前宽度**推导而非从 config（两者常不同步 `:45-48`）；锚点左上（`:49-51`）；实时 resize 不重建窗口不重载纹理（`ResizeBuffers` / `setFrame:display:`，`:52-55`）；叠加层只**请求**新 scale，由 app 写 config（`:60-63`）；**点击穿透意味着既不能拖也不能弹菜单**（`:68-70`）；每次拖拽 tick 都要 GPU resize，拖时掉帧是预期（`:84-86`）。
- 选项面 `bounds.rs:29-83`：`window_mode / window_background_color / click_through / hold_modifier_to_interact / always_on_top / scale_percent / opacity_percent / corner_radius_percent / hide_on_pointer_hover(+delay) / hide_on_idle(+delay) / keep_inside_screen / maximum_fps / window_bounds`（Windows 专属 `taskbar_icon_visible :76-82`）；`requires_window_recreation :131-141`（Windows 只有 window_mode 需要重建；macOS 是圆角 + 屏幕约束），注释 `:128-130` 说明 hold-modifier 故意不在其中（它在帧 tick 内读）。schema `config.schema.json:411-509`：`corner_radius_percent` 0–50、`hide_on_idle_delay_seconds` 默认 10（0–600）、`maximum_fps` 15–240、`opacity_percent` 1–100、`scale_percent` 25–400；`required :500-508` **故意省略 hover/idle 与 window-mode 字段**，让旧 config 文档仍可读。

**效果与合成（对我们 FX 层的直接参考）**
- **两端都没有深度缓冲**，提交顺序就是 z 序：background quad → Live2D drawables（按 Cubism `render_order` 再按 id 排，`windows/textures.rs:235`、`macos/textures.rs:226,308`）→ key overlays → plugin HUD。Windows `windows/renderer.rs:504-507` `OMSetRenderTargets(..., None::<&ID3D11DepthStencilView>)`，background `:513-558`，drawables `:560-589`（opacity = `mesh.opacity * model.model_opacity` `:581`），key overlays `:590-648`（注释 `:590-591`：最上层以保证按键图可见），plugin 层 `:649-698`（注释：插件面板是用户自己的前景元素，**绝不能落到猫后面**）。Metal 镜像 `macos/renderer.rs:394-400/419-472/477-510/511-562/563-608/609-632`。
- **混合表平台中立且单一来源** `blend.rs:50-71`：Normal `One/OneMinusSrcAlpha`，Additive `One/One`（alpha `Zero/One`），Multiplicative `DestColor/OneMinusSrcAlpha`，并由测试 `premultiplied_blend_contract_covers_all_cubism_modes` 钉住（`src/tests/blend.rs:6-31`）；镜像模型反转 culling `:39-48`。像素格式（ADR-0063）：模型/键纹理 `R8G8B8A8_UNORM`/`RGBA8Unorm`，back buffer `B8G8R8A8_UNORM`/`BGRA8Unorm`，premultiplied alpha，**两个 shader 都不做 sRGB 编解码**（`docs/adr/0063-legacy-mver-color-pipeline.md:37-50`）；不透明度只在最终 surface 施加一次（`:45-47`）。premultiply 阶段两端不同（Win 上传时、Metal 在 shader 里），跨后端像素一致是**未完成关卡**（`:68-80`）。
- 音频两条独立路径：**motion sound** 用 rodio 0.23（只开 `playback`+`flac`），固定容量 16 的有序队列且**恰好一个 voice**（「同一时刻最多一个 motion voice，不混音、不排队延后播放」`docs/adr/0012-ordered-motion-audio-service.md:25-27`；容量常数 `crates/bongocat-app/src/lib.rs:133`）；`stop()` 连 mixer sink 一起丢，注释 `crates/bongocat-audio/src/lib.rs:97-103`：空 mixer 仍在喂静音给设备、可能把蓝牙多点耳机占住；worker 文档「late 的声音比 skipped 的声音更糟」。**格式硬约束：motion 音频必须 FLAC**（`bongocat-model/src/formats.rs:10-15` 扩展名、`fLaC` magic `:24-34`、强制 34 字节 STREAMINFO `:47-52`、采样率/声道/位深上下界 `:72-80`），mp3/wav/ogg 直接拒。**key sounds** 在插件里自己混（`plugins/key-sounds/src/main.rs:122-132`：`MAX_CONCURRENT_SOUNDS 256`、`DEVICE_IDLE 10s`）。
- **sticker / 自定义特效：诚实负面**。没有 sticker 系统，没有用户自定义特效概念——图上叠的只有（a）包内 key 图、（b）模型自带 `background.png`、（c）插件 HUD。存在的每模型定制只有：key 图清单 `can_draw`、`allow_motion_overlap`（默认 false，ADR-0088 `:11-30`：按插入顺序最老先评估、冲突写入不相加、**不引入新模型格式**、音频仍单流 `:39-40`）、`show_all_pressed_keys`、每模型忽略开关、随机行为、自定义行为显示名（ADR-0085 `:99`「runtime 永远看不到它们」，上限 64 字符）。ADR-0093 `:15-16` 说逐边音效选择「刻意推迟到自定义音效扩展时再做」。
- **插件 manifest 是唯一真做版本化的地方**（对我们 payload 有直接参考意义）：文件 `plugin.json` = `{"metadata": {...}}`（`plugin-packaging/src/workspace.rs:11`）；schema 就是 Rust 类型 `plugin-sdk/src/lib.rs:158-174`（`id, name, description, version, app_versions, protocol_version: u32, entry, icon, capabilities: Vec<Capability>, localizations`），手写 `Deserialize` + `#[serde(deny_unknown_fields)]` ⇒ **未知字段即错误**（`:176-193`）。真实 manifest `plugins/key-sounds/plugin.json:1-19` 含 `"app_versions": ">=2.3.1"`、`"protocol_version": 1`、`capabilities: ["configuration","input_events_v1","localized_views_v1"]`。校验：`PROTOCOL_VERSION = 1`（`:8`）；安装要求 semver `version` **且** semver-range `app_versions` **且** `protocol_version == 1`（`install.rs:399-419`）；握手必须回显 id + protocol（`:718-727`）；发现阶段跳过 protocol 不匹配（`source.rs:138-139`）；**catalog 条目含 `sha256`**（`catalog.rs:32-55`）。能力名是封闭枚举（`:93-120`），ADR-0090 `:92-93`「未来原语/能力要靠 protocol minor/version 门控，不是造新插件 ID」。**注意：插件有 sha256，模型包没有**——同一个仓库对插件做完整性、对模型不做，这正是我们要补齐的不对称。
- 插件进程边界（值得整体照搬的模式）：`docs/adr/0090-independent-plugin-processes.md:14-19` 拒绝 `libloading` 与 `abi_stable`——「**原生进程 + 标准库管道 + 现成 serde + 原子写文件，是最小可用边界**」；长度前缀 JSON + **256KiB 消息上限**（`:45-50`，代码 `plugin-sdk/src/lib.rs:682-708`，`MAX_MESSAGE_BYTES = 256*1024`）；stdout 只走协议、stderr 丢弃不记（`:51-54`）；spawn `loader.rs:26-38`，`sync_channel(2)` + `try_send` 使无请求输出不能阻塞（`:44-63`），写超时 750ms 后 kill（`:100-107`）；HUD 合成规则 `:59-67`「**模型视图是一个专用叠加层，不属于 Live2D 模型绘制**」；空闲成本 `:128-130`：插件服务**只在视口或插件帧真正变化时**重发合成后的 HUD 层（与渲染路径不对称——这个不对称就是可抄的模式）。插件只能画一棵封闭树（`UiNode::{Text,IconText,Button,Toggle,Integer,Select}` `:326-378`，节点 ≤16、文本 ≤1024B、label ≤128、`SELECT_OPTION_LIMIT = 64`），**插件不能产生 host 没排过版的一个像素**（`bongocat-plugin-hud/src/lib.rs:3-24`）；一张 bitmap → 一个 quad（`hud/src/layer.rs:1-12`），target 存**窗口设备像素**而非 bitmap 相对坐标（`:115-121`），光栅化失败的面板被丢弃而非致命（`:241-243`），`MAX_HUD_DIMENSION = 4096`（`hud/src/paint.rs:22`）。

**每帧成本与 idle**
- 连续、deadline-paced，**绘制没有 dirty-flag 门**：帧源 `crates/bongocat-app/src/main.rs:1206-1233` `frame_interval_for_runtime(scheduling.maximum_fps, scheduling.overlay_visible)`，然后 `overlay.tick()`（`:1339`）。pacing `crates/bongocat-runtime/src/pacing.rs:12-23`（重读）；常数 `runtime/src/lib.rs:72-75`（重读）：`DEFAULT_MAXIMUM_FPS 60 / MINIMUM_FPS 15 / MAXIMUM_FPS 240 / HIDDEN_OVERLAY_FRAME_INTERVAL 100ms`——**隐藏 ≠ 停止，隐藏降到 10Hz**。`FramePacer`（`pacing.rs:59-90`）维护 deadline 网格以免配置 fps 被工作时长拖低（注释 `main.rs:1220`），fps 或可见性变化时重建网格（`:77`）；工作预算 = 半个帧间隔（`pacing.rs:25-28`），兜底 8ms。idle 的**视觉隐藏**是另一个特性（`hide_on_idle` 默认 false、默认 10s，`runtime/src/lib.rs:148,174,180`），并且会强制打开穿透以免不可见叠加层吞点击。输入捕获节奏独立：Windows 每消息一次 `service_tick()` + 16ms 定时器、调和 250ms；macOS 10ms run-loop 片（`macos.rs:177,763`）；手柄 `MAX_EVENTS_PER_DRAIN 256`、`MAX_GAMEPADS 4`（`gilrs_gamepad.rs:16-17`）；摇杆死区 0.15（`input/gamepad.rs:11-20`）。
- DPI/HiDPI 单一策略（`docs/adr/0089-windows-window-mode.md:34-42`）：数值与手势百分比用当前 DPI 与 100% 下 350 逻辑像素宽，**绝不把保存的宽高乘以相对比例**；`HudViewport{width,height,scale}` 用设备像素（`hud/src/layer.rs:42-79`）。
- 可读的基准文档存在：`docs/benchmark/macos-overlay-frame-timing-90e0aa7.md`（62 行）、`docs/benchmark/windows-window-mode-2026-10-08.md`（119 行）、帧率语义迁移说明 `docs/migration/bongo-cat-mver-frame-rate-semantics.md`（181 行）。

**MVER（遗留包格式）与 Cubism 事实**
- `MVER` = 上游 `MMmmmoko/Bongo-Cat-Mver` 格式，钉在 commit `4da0b9468ad3b6ffaa096eba3f080501d6ab0b5c`，**只作行为考古，从不作为依赖**（`docs/migration/bongo-cat-mver-reference.md:9-12`）。必需布局 `mver/mod.rs:7-14`，名字硬编码在 `mver/layout.rs:10-33`。检测要**两个独立证据且永不报错**（`mver/source.rs:100-105` + `mver/inspect.rs:38-49`，否则 `Ok(None)`）。`config.json` 是**按键表不是配置**（`mver/config.rs:1-6`）；**注释是格式的一部分**（先剥 `//` 与 `/* */` 再严格解析，`:113-130`，因为上游用 JsonCpp `allowComments = true`）。一个源 → 最多三个包，各自 UUID 各自原子提交，**无全局回滚**（`mver/mod.rs:31-34`）。遗留图按**位置索引**命名（`mver/plan.rs:63-65`）。合成：画布取两层尺寸的 **min**（裁，不缩放，`mver/compose.rs:23-24`），整数 straight-alpha Porter-Duff（`:79-102`），无损 oxipng 且有回退（`:113-122`）；pngquant 因许可（GPL-3.0 vs Apache `deny.toml` 白名单）被拒（ADR-0037 `:130-144`）。转换产物是逐字节抬到根的 model3 包（**不合成 `.model3.json`** `:29-30`）+ `resources/background.png` + `cover.png` + 合成键图——**无 manifest、无 provenance、无版本**。唯一字节级例外是 `without_dangling_sounds`（`:125-155`，剥掉指向不存在文件的 `MotionGroups[*][*].Sound`）。
- Cubism 事实：**Cubism 5 SDK for Native R5（`5-r.5`），Core `06.00.0001`**（`docs/cubism/cubism-sdk-source-and-license.md:10`），归档 SHA-256 记录在 `:16`；编译期常数 `bongocat-live2d/src/lib.rs:53` `CUBISM_CORE_VERSION = 0x0600_0001`、`:64` `CUBISM_LATEST_MOC_VERSION = 6`，载入时强制（`core/load.rs:30-35`）。Core 二进制 git-tracked 在 `vendor/cubism/5-r.5/` 并**静态链接**（`build.rs:5-27`）；bindings 由 `bindgen 0.72.1` 生成后签进仓库，不在构建时重新生成（`docs/cubism/binding-generation.md:21-42`）。

**License 与活跃度（诚实记录）**
- 应用代码 Apache-2.0 未改（`LICENSE:1-3`，`Copyright 2025-present ayangweb` `:189`），`Cargo.toml:38`。**LICENSE 里没有任何排除条款**——既不排除 Cubism 也不排除模型资产；所有排除只在散文里：`docs/cubism/cubism-sdk-source-and-license.md:60-64`（Core = Live2D Proprietary；Framework/Samples = Live2D Open Software License，「不是 MIT/Apache/BSD 类宽松许可证」；**模型资产各自有自己的许可**），`vendor/cubism/5-r.5/LICENSE.md:45-62` 展示 Free Material License 只覆盖八个具名样例模型。再分发限于 `RedistributableFiles.txt` 列出的文件、按原样形式、不得改或重新打包（`:56`）；仓库自己标记：把从 Core 头派生的 Rust bindings 发布**需要 Live2D 单独答复**，因为该头不在那份清单里（`docs/cubism/cubism-framework-behavior-sources.md:92`）。**未解决的 blocker（引用）**：发布需事先申请 + Live2D 批准 + 单独的 Publication License Agreement，一般用户/教育/<1000 万日元豁免均不适用（`:68-77`），因此现行结论是 **「不得发布包含 Cubism Core/Framework 的 BongoCat 安装包」**（`:81-84`）。另有 `crates/bongocat-packaging/src/main.rs:894` + ADR-0033 `:162`：**当前安装器里没有 EULA/许可页**。
- 社区模型生态：gallery `Awesome-BongoCat`（`README.md:68`、`README.zh-CN.md:63`、发布注记常数 `main.rs:1339-1340`）。**该 gallery 分享模型没有任何公开许可条款**。代码透露的约定：社区压缩包几乎总带一层目录且目录名不必与包匹配（`docs/adr/0036…:58`，真实样本表 `:53-56`）。第三方模型数据按政策不进仓库（`docs/adr/0036…:210-212`；env 门控测试 `BONGOCAT_MODEL_ARCHIVE_SAMPLES` / `BONGOCAT_MVER_SAMPLE`；`CONTRIBUTING.md:108`）。作者权利表述是行为性的不是法律性的：键图词表「是与模型作者的契约」（`docs/technical-design.md:921`）、补全缺失美术「会越过模型作者的」（ADR-0082 `:80`）、第三方模型的 `ParamBreath` 视为作者意图（ADR-0061 `:103`）。
- **风险发现（推断）**：没有 disclaimer、没有对用户模型的担保限制、没有 takedown 章节，而且**随包的三个预置模型（`resources/models/{standard,keyboard,gamepad}/`）没有 provenance / license 记录**（这些目录下 find LICENSE/README 为空），尽管它们被强制进每一个产物（`main.rs:102-103` `PRESET_MODELS: [&str; 3]`）且明显是重新贴皮的 Cubism demo 模型（`demomodel{,2,3}.moc3`）。对照音频侧反而有完整资产许可链（逐文件 SHA-256/来源/许可 URL + 自动测试拒绝专有集：`plugins/key-sounds/resources/PROVENANCE.md:3-53`、`tools/tests/test_key_sounds_assets.py`）。

## 8. NyaDeskPet（`a402fcd`，2026-02-27，Electron + pixi-live2d-display + LLM agent + Python 插件）

这一家是全场**离我们的目标形态最近**的一个：LLM 驱动的桌宠，有具名分区点击、有 sidecar 别名表、有真口型、有响应优先级抢占。同时也是全场 **license/资产风险表述最不诚实**的一个（§8.5）。

### 8.1 分区点击：标准 Cubism `HitAreas` + 一张名字补丁 + 一张用户可编辑的开关表

- 自带包 `models/live2d/mao_pro_zh/runtime/mao_pro.model3.json:90-99`（重读）就是公版样例状态：`HitAreas: [{"Id":"HitAreaHead","Name":""}, {"Id":"HitAreaBody","Name":""}]`。**空 `Name` 会直接打坏 vendored SDK**，因为它的命中表按 `Name` 建 key（vendored `renderer/lib/pixi-live2d-cubism4.min.js` 的 `setupHitAreas(){…this.hitAreas[e.name]=e}`，两个空名塌成一条）。于是 app 写了整段补丁 `renderer/js/live2d-manager.ts:1445-1477`（`patchHitAreas`，调用点 `:248`）：空或重名时 `Name → Id` 回退，然后替换 `internalModel.hitAreas`。**这个补丁等于替我们确认了 §1 结论 1：稳定 key 只能是自己的 id。**
- 入口是 `#interaction-area` 上的普通 DOM `click`（`renderer/js/renderer.ts:284-291`），不是 pixi hit；pixi 的 drag handler 刻意**不**触发 tap（`dragThreshold = 3` `live2d-manager.ts:359-360`，之后只打日志 `:411-417`）。`tap()` `:610-643`：`internalModel.hitTest(x,y)` → 取 `hitAreaNames[0]`（**first-wins，未声明的优先序**，`:621`）→ `isTapEnabled(name)` 门（`:872-879`，除非 `config['default'].enabled === false` 否则默认开）→ IPC。
- 每模型的用户配置是 `touch_zones[]` 最接近的类比：`interface TapConfig { [hitArea: string]: { enabled: boolean; description?: string } }`（`renderer/types/global.d.ts:965-970`），按 `modelPath` 持久化（`TapConfigRecord :466-468`）。设置 UI 从模型**真实**区域构建，保存时丢弃模型里已不存在的区域（`renderer.ts:3928-3938, :3983-4014`，丢弃在 `:3998-4001`）——**「区域表跟着模型走、不跟配置走」**这个纪律值得抄。
- **点击本身不触发任何本地反应**，只发一条消息让后端决定（`live2d-manager.ts:899-912`）：`{type:'tap_event', data:{hitArea, position:{x,y}, timestamp}}` → `src/agent/pipeline.ts:188-190`（优先级 8，`:136`）→ `src/agent/handler.ts:1700-1747` → `agent-plugins/core-agent/main.js:188-234`。**一次点击 = 一次 LLM 回合 + 再一次 LLM 回合（表情）+ TTS。** 实际用到的 motion 组名只有 `Idle` 和**空字符串 `""`**（对外别名成 `Default`：`live2d-manager.ts:519-520`，同常量 `:695, :703`；`mao_pro.model3.json:46-51` 里字面有 `"": [...]`）。
- 一个**坏样板**：它们自己的兜底反应表按 `{'Head','Body','Face'}` 建 key（`src/agent/handler.ts:1737-1739`），而真实 key 是 `HitAreaHead/HitAreaBody` → **永远匹配不上，是死代码**。这就是「区域命名靠约定、不做校验」的下场，正好支持我们 payload 里把 zone id 做成受控词表。

### 8.2 三套插件子系统，**全场唯一一个真做过 sidecar 版本化的地方，也全场唯一没有任何版本比较逻辑的地方**

- 三条加载路径（`src`、`renderer/js`、`renderer/types`、`agent-plugins`、`plugins`、`assets/docs` 全部 grep）里**没有 semver、没有 `schemaVersion`、没有 `apiVersion`、没有 `minVersion`、没有任何版本比较**；唯一命中是 SQLite 迁移辅助（`src/agent/database.ts:181,208,219`），`semver` 不在 `package.json:45-60`。三个 manifest 的 `version` 字段纯展示。
- Python/系统插件（`plugins/*`）带**三份** manifest：`metadata.json`（`plugins/file-editor-plugin/metadata.json:2-8` `id/name/version/author/type:"system"/url:"ws://localhost:8769"/autoStart`；`permissions[]` 带 `id`+`dangerLevel`+i18n `:9-52`；`capabilities[]` `:53-58`；按 OS 的 `command` `:71-78`）、`tools.json`（LLM function schema + `action` 路由字段 `:2-15`）、`config.json`（**JSON-Schema 味的定义而不是值**，每项 `type/default/description/hint/i18n` `:1-82`；类型联合在 `src/agent/agent-plugin.ts:85`）。
- 发现/生命周期：平铺扫 `<appPath>/plugins` 只认目录（`src/main.ts:977-998`）；启动是「平台选命令 + **按空格裸切**」（`:1016-1042`）、`spawn stdio:['ignore','pipe','pipe']`（`:1053-1058`）；renderer 之后**死等 3000ms** 再连 WebSocket（`renderer/js/plugin-connector.ts:165-169`，`:241`；重连 3000ms × 5 次 `:52`）。**没有健康检查**：子进程崩了只清 main 侧 map（`main.ts:1081-1089`），永不重启。注册表按 `manifest.name` 建 key（`plugin-connector.ts:100-109`）——**重名静默覆盖**。
- 权限模型是**建议性**的：`checkPermission` 返回已存授权并对 `safe` 自动放行（`renderer/js/plugin-permission-manager.ts:38-58`，行 `:47, :51, :57`）；唯一闸门 `plugin-connector.ts:770-774` 只在插件自己主动发 `permission_request`（`:381-384`）时才跑；`callPlugin` 对任何 `action` 无预检直接分发（`:438-495`）。file-editor 的 `readFile/listDirectory` 从不申请 `file.read`（`plugins/file-editor-plugin/main.py:213,219`），目录白名单默认关（`:197-200`）。文档写的「high/critical = 每次确认」（`assets/docs/Permissions.md:33-34, :62-64`）没实现，而 `docs/PLUGINS.md:326-332` 又把它改成 `medium` = 每次确认。**唯一被强制的闸门在 agent 侧**且按来源标签：`return toolDef?.source === 'plugin';`（`src/agent/handler.ts:1253, :1598`）→ `requestToolConfirm :1406-1446`，`DEFAULT_TOOL_CONFIRM_TIMEOUT = 30000`（`:51`）超时自动拒绝，拒绝文案 `TOOL_REJECTED_MESSAGE`（`:64`）。
- agent 插件（JS）是**进程内 `require()`、无沙箱**：manifest 类型就是全部 schema（`src/agent/agent-plugin.ts:60-81`），必填校验 `:559-562`，加载只有 `typeof PluginClass !== 'function'` 一关（`:641`），拓扑排序有环检测但失败时退回目录序（`:497-536` / `:477-481`）。
- 随包数据里的**坏契约样本**（对我们定 payload 校验很有用）：ui-automation 的 `tools.json` 声明的 `screenshot|click|typeText`（`plugins/ui-automation-plugin/tools.json:5,20,37`）在它自己的 Python 分发器里不存在（`main.py:186-213` 用的是 `captureScreen|mouseClick|…`）→ **三个工具全部只能返回 unknown-action**；端口在 metadata 与代码里硬编码两遍（`main.py:34,550 / :714 / :27,623`）；文档宣传 `"url": "ws://localhost:0"` 自动分配（`assets/docs/PluginDevelopment.md:113`）代码做不到。**没有任何 `scripts/` 校验任何 manifest；连接时的清单核对是一行注释**（`plugin-connector.ts:389` 「这里可以验证插件服务器返回的元数据是否与本地本地清单匹配」）。
- **`param-map.json` 是这个仓库对我们最有价值的单个产物**（`models/live2d/mao_pro_zh/runtime/param-map.json`）：一张手写、面向 LLM 的别名词表，随 `.model3.json` 同目录发布，也是**仓库里唯一带版本号的 sidecar**：`"version": 1`（`:2`）、`parameters[]` 27 条真 id + `alias` + 中文 `description`（`:3-139`）、`expressions[]` `exp_01..exp_08 → normal/happy/sleepy/excited/sad/shy/surprised/angry`（`:140-148`）、`motions[]` `Idle[0]=idle`、`Default[0..5]=nod/sway/hat_transform/heal_magic/explosion_magic/summon_rabbit`（`:150-157`）。TS 契约 `renderer/types/global.d.ts:241-259`（`version: number` 必填）。加载与门控 `live2d-manager.ts:649-678`（从 `<modelDir>/param-map.json` 取，文件名常量 `:80`）：**`config.version !== 1` 就整表置 null（精确等值、硬拒）** ——仓库里唯一的版本门。富化 `_enrichModelInfoWithParamMap :773-845` 把别名与**从 moc 读出的真实 min/max/default** 合并（`:783-803`），expression 别名只在 id 存在时保留（`:806-819`），motion 别名校验到 group+index（`:822-844`），结果作为 `model_info` 下发（`:850-867`）。**结论：sidecar 逐包手写 + 对着真模型校验 + 一个整数版本号。抄它，但把 `!== 1` 换成范围，并且把它变成必需项**——现在缺了它会静默降级成「把每个原始参数倒进 prompt」（`agent-plugins/expression-generator/main.js:186-190`）。

### 8.3 表情/动作怎么决定：**不是 tool call，也不是内联标签，而是第二次独立 LLM 调用**

- 没有 `set_expression` / `play_motion` 工具（全仓 grep 无），并且对话 LLM 被**明文禁止**输出标记（`src/agent/handler.ts:1089-1093` 逐字：「只输出纯文字对话，绝对禁止使用任何 XML 标签（如 `<expression>`、`<motion>`、`<parameter>`、`<action>` 等）／禁止在回复中描述或指定具体的表情名称、动作名称或参数值」）；能力清单里**故意不给** motion/expression/param 列表，只给 hit areas（`:1077-1081`，镜像在 `agent-plugins/personality/main.js:165-178`，注释 `:176`）。插件头写明理由「解决了 XML 标签方案的可靠性问题」（`agent-plugins/expression-generator/main.js:5`）。
- 协议字符串 `agent-plugins/expression-generator/main.js:196-229`：`{"expression": "简短描述当前情感", "actions":[{"type":"parameter","parameterId":…,"value":…},{"type":"expression","expressionId":…},{"type":"motion","group":…}]}`；规则逐字：id 必须来自 prompt 给的列表（`:222`）、`value` 必须在参数 min~max 内（`:223`）、**过渡时长由系统计算**（`:224`）、优先用 `parameter` 组合而不是预制表情（`:226`）。调参：`MAX_RETRIES = 1`（`:22`）、`temperature 0.7`（`:28`）、`maxTokens 300`（`:29`）。校验 `_parseAndValidate :314-471`：剥 markdown fence（`:319-322`）、括号切片（`:325-329`）、建 id+alias 映射（`:351-381`）、alias→真 id、`Math.max(min, Math.min(max, Number(value)))` 夹紧（`:409`）、输**真** id（`:412, :436, :447-451`，默认 `priority: 2` `:462`）；**未知 id 丢弃并告警，从不致命**（`:405,430,454`）。触发点在文本已存在之后（`agent-plugins/core-agent/main.js:448-465`，调用点 `:215` tap、`:401` 用户输入），注入为 `parsed.actions`（`:216`）。
- wire 形状在一个 `protocol-adapter` 插件里：`:47-49` 「对话 LLM 现在只输出纯文本，无需提取任何标签」，`toOutgoingMessages :58-100` 三选一——无 action → `{type:'dialogue'}`；有 action 无文本 → 每 action 一条 `{type:'live2d', data:{command,…}}`；两者都有 → 一条 `{type:'sync_command', data:{actions:[…]}}`。默认值 `:105-109`：`DEFAULT_MOTION_INDEX = 0`、`DEFAULT_MOTION_PRIORITY = 2`、`DEFAULT_PARAM_WEIGHT = 1.0`、`DEFAULT_PARAM_DURATION = 0`（注释「0 = 前端自动计算」）、`DEFAULT_EXPRESSION_ID = 'default'`。气泡存活 `durationPerChar 80 / min 3000 / max 30000`（`:21-23`，公式 `:150-152`，后端重复一份 `src/agent/handler.ts:45-47`）。
- 消费侧 `renderer/js/backend-client.ts:185-243` 分发；`handleLive2DCommand :349-380` 里 **`DEFAULT_PARAM_DURATION_MS = 3000`（`:368`）与 protocol-adapter 的「0 = 自动」互相矛盾**；`executeAction`（`live2d-manager.ts:939-973`）**同一个 action 接受两种格式**（array `parameters` `:956-959` 与 flat `parameterId/value/weight` `:960-968`，两种都在 `global.d.ts:317-342` 有类型）。**这两处是「协议字段没有单一事实源」的活样本**，正是我们用 `AvatarCommand`/`cmp.cue` 单一事实源要防的东西。
- **抢占模型（对我们 cue 协议最直接的参考）**：服务端 `PreProcessStage.PRIORITY_MAP`（`src/agent/pipeline.ts:131-143`：`user_input/file_upload/command_execute = 10`、`tap_event = 8`、`plugin_message = 6`、状态类 = 0、默认 `?? 5`），应用点 `:154`；前端规则一句话写在 `renderer/js/response-controller.ts:18-19`「新响应优先级 >= 当前响应优先级 → 中断当前」，实现 `shouldAccept :50-83` 带 `discardedIds` 缓存（`MAX_DISCARDED_CACHE = 50` `:41`）过滤已在流的 chunk（`isActive :89-103`）；打断时停音频 + 清气泡 `:135-153`；chunk 级过滤在 `backend-client.ts:173-179`。
- **参数覆盖引擎（做 cue 时序就直接抄这组常数）**：`live2d-manager.ts:48-66` 解释了为什么 `setParameterValueById` 存不住（SDK 顺序 Motion → saveParameters → Expression → Physics → beforeModelUpdate → render → loadParameters），解法是**每帧注入** `internalModel.on('beforeModelUpdate', handler)`（`:1150-1171`，订阅 `:1165`）。常数：`HOLD_AFTER_TRANSITION_MS = 2000`（`:1049`）、`RELEASE_DURATION_MS = 500`（`:1052`）、`MIN_AUTO_DURATION_MS = 200 / MAX_AUTO_DURATION_MS = 900`（`:1055-1056`）、`FALLBACK_AUTO_DURATION_MS = 400`（`:1058`）；自动时长 = `MIN + (|Δ| / 参数量程) * (MAX - MIN)`（`_computeTransitionDuration :1301-1310`，理由逐字「微小调整（眨眼）≈200ms … 大幅变化（转头）≈900ms」）；缓动 `easeInOutCubic`（`:1354-1358`）；三段状态机 `_calculateOverrideState :1229-1265`；只在 `weight > 0.001` 时写（`:1201`）；条目过期 `:1191-1194`、清理 `:1211-1218`。**注视在 motion/参数动画存活期间让位**（`:1015` 判定、`:1081` 置位、`:1216-1218` 清除）——和 pixi 的 `addParameterValueById` 叠加式（§7.1）是两种不同的共存策略：**pixi 靠叠加，NyaDeskPet 靠让位。** 我们 FX/表情层需要明确选一种；叠加更安全（不会因新事件抹掉旧事件），但需要一个「谁还在生效」的表。
- 一个真实成本提醒：眼球跟随的循环是 `requestAnimationFrame` + **每帧一次跨进程 `getCursorScreenPoint`**（`:1010-1039`）。Android 上等价物（每帧读 pointer/location）不要照抄这个做法。

### 8.4 口型：**全场唯一真实现的**（时域 RMS + 噪声门 + 非对称 attack/release）

- 流式：`synthesizeAndStream`（`src/agent/handler.ts:1014-1061`）= `audio_stream_start {mimeType, text}`（`:1026-1032`）→ 每块 `audio_chunk {chunk: base64, sequence}`（`:1035-1043`）→ `audio_stream_end {complete:true}`（`:1046-1050`）或 `{complete:false, error}`（`:1053-1060`）。TTS 一律 non-blocking/best-effort（每个调用点都 `.catch(...)`：`:1175,1187,1728,1744,1984,1995`）。
- 消费端 `renderer/js/audio-player.ts`：`startStreamingAudio :205-274` 建 `MediaSource` + `<audio>`（`:213-216`）+ `AnalyserNode` 链，**`fftSize = 256`**（`:227`）；`appendAudioChunk :280-297`，`processQueue` 一次一块、以 `sourceBuffer.updating` 为门（`:302-314`）；`endStream` 每 100ms 重试至多 `MAX_END_RETRIES = 50`（≈5s）后强关（`:319-365`）；base64→bytes `backend-client.ts:294-311`。
- **口型是实打实的 RMS，不是布尔**（`audio-player.ts:134-166` 重读）：`LIP_SYNC_FPS = 60`（`:134`）、`TIME_DOMAIN_CENTER = 128`（`:136`）、`LIP_SYNC_AMPLITUDE_SCALE = 35`（`:138`，注释「语音通常在 20-60 范围」）、`LIP_SYNC_NOISE_GATE = 5`（`:140`）；用 `getByteTimeDomainData`（**时域不是频域** `:148`）→ `rms = sqrt(sum/N)`（`:156`）→ `gated = rms < 5 ? 0 : rms`（`:159`）→ `lipValue = min(gated/35, 1.0)`（`:160`）→ `setLipSync(lipValue)`（`:164`，interval `1000/60` `:166`）。
- 每帧平滑是**非对称**的，施加在 `beforeModelUpdate` 钩子里（`live2d-manager.ts:1364-1385` 重读）：`LIP_SYNC_ATTACK = 0.6`（注释「张嘴快（跟上音节）」）、`LIP_SYNC_RELEASE = 0.3`（「闭嘴适中」）、`smoothing = target > value ? ATTACK : RELEASE`，然后 `value += (target - value) * smoothing`；`DEFAULT_LIP_SYNC_PARAM = 'ParamMouthOpenY'`，目标参数 = `lipSyncParams.length ? lipSyncParams : [DEFAULT]`。参数 id 来源：`detectLipSyncParams` 读 `model3.json → Groups → Name === "LipSync" → Ids`（`:1484-1503`，组名常量 `:1493`）。**它自带的那个包 LipSync 组里只有 `ParamA`**（`mao_pro.model3.json:83-87` 重读），所以在这套包上嘴是由「A」viseme 驱动的、`ParamMouthOpenY` 兜底从不触发——**这就是为什么兜底要存在，也说明光有兜底不够：manifest 声明错照样跑偏**。
- idle：app 侧**完全没有** idle/blink/breath 定时器，100% 交给资产 + vendored SDK（唯一设置是 `autoUpdate: true` + `motionPreload: MotionPreloadStrategy.IDLE`，`live2d-manager.ts:238-239`）；`Idle` motion 是 `Loop: true / Duration 5.57 / Fps 30 / 132 curves`（`motions/mtn_01.motion3.json`），呼吸/物理来自 `mao_pro.physics3.json` 与 `pose3.json`（`model3.json:8-9`）。唯一的 motion 记账是 `isPlayingMotion` + `MOTION_FALLBACK_DURATION = 2000`（`:525, :528-542`）。ticker：`sharedTicker: true`（`:194`），Windows 上 `ticker.maxFPS = 60`（`:198-204`）。它们自己的文档是**过时的**：`assets/docs/RendererModules.md:43` 与 `docs/DEVELOPMENT.md:411` 写 30 FPS + `ParamMouthOpenY`，代码是 60 FPS + 组驱动。
- 半截协议要知道：`TimelineItem` 带语义标记 `'start'|'early'|'middle'|'late'|'end'` → 比例 `0/0.15/0.5/0.85/0.98`（`audio-player.ts:386-398`）、消费者 `executeTimelineAction`（`backend-client.ts:326-344`），但**没有任何生产者发 `timeline`**（`src/`、`agent-plugins/` grep 零命中）。**它们的「按音频时间轴发特效」通道今天是死代码**——这件事我们的 `cmp.cue → AvatarEventMapper` 已经在做了，说明这条通道不是过度设计，但也说明它需要有人生产才有价值。

### 8.5 活跃度、license 与资产风险（这一节必须原样记住）

- MIT（`LICENSE:1-2`），版本 `1.0.2`（`version.json:2-7`，buildTime 2026-02-20；`package.json:3`），最新 changelog `## 1.0.2 - 2026.02.20`（`docs/CHANGELOG.md:16-33`，`assets/docs/CHANGELOG.md:16` 是逐字节副本）；最后一次提交 2026-02-27 **没有对应 changelog 条目**。Electron ^28、TS ^5.3（`package.json:41,43`）。
- `renderer/lib/live2dcubismcore.min.js` 是 **Live2D 专有 Redistributable Code**（头 7 行原文含 「This file corresponds to the "Redistributable Code" in the agreement」+ EULA URL）——**与 MIT 不兼容的源码再分发**。`renderer/lib/pixi-live2d-cubism4.min.js` = 上游 `pixi-live2d-display` cubism4 UMD（`package.json:56` 声明 `^0.4.0`，**minified 文件里没有 banner 也没有版本串，具体构建是推断**）；另外 vendored：`pixi.min.js` v7.3.2 MIT、`highlight.min.js` v11.11.1 BSD-3、`lucide.min.js` v0.563.0 ISC。
- **随包模型 `mao_pro_zh` 就是 Live2D 免费样例「虹色Mao（PRO版）」，不是无条件商用安全**：`models/live2d/mao_pro_zh/ReadMe.txt:22-24` 逐字「普通用户以及小规模企业在同意授权协议的情况下可用于商业用途。中/大规模的企业只能用于非公开的内部试用。」——**中/大型企业只能非公开内部试用**，这条对我们（商业 app）是直接排除。同一文件 `:15` 还警告该模型含「魔术效果和面部表情动作」**不能与普通面部表情动作组合使用**——对我们的特效/motion 叠加设计直接相关（`special_01..03` 就是那些魔法 motion）。署名 `:29-31`（插画/模型：Live2D Inc.），许可指向 `:26`；`models/README.md:75-77` 写「版权归原作者所有」，zip 从 `https://cubism.live2d.com/sample-data/bin/mao_pro/mao_pro_zh.zip` 拉（`models/README.md:19`）。而 `README.md:133-135` 只宣称「MIT License」，**完全不提模型/Core 的例外**——仓库低报了自己的资产许可风险。
- 给我们的直接推论：**payload 里有一个 `hash` 字段不等于有权分发。** 包契约需要一个权利/licence 字段（见 §10）。

## 9. 缺口清单（本节刻意留空，不当作已验证）

brief 目标 ③ 的三块**没有在这一版里完成**，下面是精确状态与已排除项：

| 项 | 状态 | 已确立的事实 / 下一步 |
|---|---|---|
| **PrprLive** | 未读 | 已知它是 C# 桌面 Live2D 桌宠（VTube Studio 之前的主流替代品之一），但**没有在本轮 clone 里**，因此本文不给它任何结论。需要单独一轮（读其模型加载 + hit area + 窗口 flags，与 §7.2 对照）。 |
| **VTube Studio 的开源替代（openreverse 一类）** | 未读 | 本轮只确认了 GitHub `topics/vtube-studio` 这个入口存在（搜索证据），没有落到具体仓库的代码。**注意 `openreverse` 是跟踪 VTube Studio 协议的逆向项目，不是桌宠**——若要抄的是「协议 + 插件 API」而不是渲染层，那是另一份调研。 |
| **近 12 个月活跃的 Live2D 开源引擎替代** | 部分完成 | 已确认可排除项：`pixi-live2d-display` 事实冻结（2023-12-15，`v0.5.0-beta`，§7.1）；`alive2d` 无 LICENSE 文件（§3.1）；官方 Web Framework 已到 5-r.1、Native Framework 到 5-r.5（§3.2，这是「活跃」的那个，但它是 SDK 不是替代引擎）。**没有找到一个可替换 Cubism Core 的开源实现——本轮没有任何证据支持「存在开源 moc3 解码器」这一假设。** |

另外两条本文内部已声明的结构性缺口（不重复展开）：Shijima-Qt 的 XML 解析器不在 checkout（§0 第 1 条）；除 ChatWaifu 外全是 shallow clone，因此**活跃度只能报 tip 日期，不能报提交数/迭代速率**（§0 前言）。

## 10. 对我们 payload 契约的逐字段判定

对照 `docs/design/2026-11-28-persona-her-split-architecture.md:40`（`ca_her_pack`：`her_id`(unique)、`renderer`、`pack_version`、`payload{model_ref{asset,version,hash}, effects[], stickers[], voice_ref, thumbnail, touch_zones}`、`status`、`source`）与 `feature-companion/.../companion/HerRegistry.kt:15,18`（客户端只读 `renderer` / `pack_version` / `effect_count` / `sticker_count` / `voice_ref.voice_id`）。

| 我们的选择 | 判定 | 依据（本轮读到的代码） |
|---|---|---|
| **`renderer` 中立（长在 her 包上，不长在角色上）** | **确认，且是全场唯一做法** | 只有 dsh-pet 有 renderer 判别字段（`src/manifest-v2.ts`），而它同时演示了反面：`gameplay` 块被 gate 成只有 `renderer: "frames2d"` 才能用（`src/manifest-v2.ts:717-724` 逐字：`gameplay currently requires renderer frames2d (its state references name frames2d tracks)`）——**能力按 renderer 硬门控会让 payload 的一半字段随引擎失效**。我们的 `effects/stickers/touch_zones` 必须对所有 renderer 都可声明（引擎不支持时降级而不是报错）。另外 renderer 中立同时是法务前提（Expandable Application 条款，见 §7.2 末与 §8.5）。 |
| **`her_id = <renderer>-<asset>`** | **需要调整（降级为约定，不可解析）** | 没有任何一家把引擎或版本编进 id：BongoCat 用随机 UUIDv4、与目录名彻底解耦（`crates/bongocat-model/src/store/mod.rs:236-256`），并把显示标题**单独存**，理由是「改名、换美术、重启都不能悄悄改变显示的模式」（`store/input_mode.rs` + `config_schema/model.rs:371-376`）；NyaDeskPet 按 `modelPath` 键控点击配置（`global.d.ts:466-468`）。风险很具体：`HerEngineChoiceTest.kt:62,82` 里 `simple-linche-v1` / `live2d-linche-v1` 是**同一个 linche 换引擎**，若任何代码从 id 里解析 renderer/asset/version，换引擎就必须换 `her_id`，而 `ca_character_her` 的绑定（设计文档 `:41`）会随之断链；id 里的 `-v2`（`HerListChoiceTest.kt:72`）也与 `pack_version` 重复计数。**建议**：`her_id` 按不透明串处理（只做字符集与长度校验，借 BongoCat 的可移植规则：1–64 字节、`[A-Za-z0-9._-]`、不得首尾点、拒绝 Windows 保留名，`limits.rs:66-104` 与其 JSON pattern `shared/config/config.schema.json:25,101,360`），引擎判别只走 `renderer` 列，版本只走 `pack_version` 列。命名风格可以继续用 `<renderer>-<asset>` 但**写进文档而不是写进解析**。 |
| **`model_ref{asset,version,hash}` + `pack_version`** | **确认（净新增能力），并补一个权利字段** | 全生态无一对包做 version/hash：BongoCat 的 `INDEX_SCHEMA_VERSION` 只进内存索引不落盘（`limits.rs:10`、`package.rs:360`），读到的 `Version` 全是 Cubism 自己的（model3==3 `package.rs:249-255`；cdi3==3 `validate.rs:23-25`；motion3==3 `:143-145`；**physics3 解析但从不检查** `raw.rs:232-233`），且**没有 model 包的 JSON Schema**；NyaDeskPet 三个 manifest 的 `version` 纯展示、无比较（§8.2）；shimeji 靠文件名猜（`Main.java:217-247`）；OLLV-Web 靠 URL。同仓库的**不对称**值得记住：插件 catalog 有 `sha256`（`plugin-packaging/src/catalog.rs:32-55`），模型包没有。**要补的字段：`rights`/licence + provenance**——`hash` 只证明完整性、不证明有权分发（§8.5 虹色Mao「中/大规模企业只能非公开内部试用」`ReadMe.txt:22-24`；BongoCat 随包三个预置模型无任何 provenance/license 记录，尽管它们被强制进每个产物 `crates/bongocat-packaging/src/main.rs:102-103`）。安装侧我们的 `AvatarPackStore` 已对齐 BongoCat 的验证过的路径：sha256（`:60`）→ staging → 一次 `renameTo`（`:67`）+ zip-slip 守卫（`:99-107`）+ 上限 `MAX_ENTRIES = 200 / MAX_TOTAL_BYTES = 20 MiB`（`:139-140`）；对照 BongoCat 的 `8192/16MiB json/64 深度/512MiB 单文件/1GiB 包/4096 文件/32 深度`（`limits.rs:49-58`），我们缺**单文件尺寸、解压后总尺寸、json 深度、目录深度**四项，且它的 zip 导入是被撤回的这一条要写进我们的下载链纪律（folder/单包 + 预校验 + 单 rename）。 |
| **`touch_zones[]`** | **方向确认，形状必须自己定（现成形状只有一个且被门控）** | 见 §1 结论 1/2 + §4.1 + §8.1。必改四点：① 每区自带稳定 id，永不复用 Cubism `HitAreas[].Name`（空/重名会塌表：pixi `InternalModel.ts:170-176`；NyaDeskPet 整段 `patchHitAreas:1445-1477` 就是为此）；② **显式定义重叠优先序**——现存每一家都是未声明的 first-wins（NyaDeskPet `live2d-manager.ts:621` 取 `[0]`；pixi 返回全部按对象 key 序；dsh-pet `Array.find` 半开区间靠 manifest 顺序，`src/gameplay.ts:938-941`）；③ 区域附着在**模型**而不是动画/动作上（shimeji `<Hotspot>` 挂在 `<Animation>` 上、每帧重建，`AnimationBuilder.java:271-274` + `ActionBase.java:131-146`）；④ 几何要超出 rect（全场只有 `Rectangle|Ellipse`（`AnimationBuilder.java:296`）与 AABB；Android 上「view 尺寸 = sprite」就是 rect 的极端后果，见 §6.4）。可抄的具体形状：dsh-pet 归一化分数 + y 分带 + 每区 `probability/effects/state/stateMs/phrases`，**分支概率和 ≤ 1、余量是故意的「什么都不做」**（`src/gameplay.ts:28-45, :460`；样本 `assets/miku/pet.json:144-235`）；加上 NyaDeskPet 的「设置页从模型真实区域构建、保存时丢弃已不存在的区域」（`renderer.ts:3928-4014`）。 |
| **`effects[]`** | **确认，且有现成的能力上限可抄** | 唯一真做过的可序列化特效目录是 dsh-pet 的 status decoration：`contracts/status-decoration-v1.schema.json`（`"hide"` 是合法 phase 值）+ 硬上限 cell ≤256px / ≤16 列 / ≤2000ms / **只收 `.webp`/`.png`，「SVG/CSS are not accepted」**（`src/decoration.ts:26-29, :75-87`），并且**逐 binding 失败只丢那一个 binding**（`src/decoration.ts:6-8`）。这正是我们「白名单/频率限制/去重属于策略不属于包」之外的另一半：**资源侧要有可判定的上限**。 |
| **`stickers[]`** | **字段成立但生态无先例——我们必须自己定义** | BongoCat 明确没有 sticker/自定义特效概念（只有包内 key 图、包内 `background.png`、插件 HUD，§7.2 效果小节）；dsh-live2d-pets `grep -rniE "voice|audio|sticker|particle" src/` 零命中；NyaDeskPet 没有。唯一可借的纪律来自 BongoCat 插件 HUD：一棵**封闭节点树**（`UiNode::{Text,IconText,Button,Toggle,Integer,Select}`，节点 ≤16、文本 ≤1024B、label ≤128、`SELECT_OPTION_LIMIT = 64`），「插件不能产生 host 没排过版的一个像素」（`crates/bongocat-plugin-hud/src/lib.rs:3-24`），**一张 bitmap → 一个 quad**（`hud/src/layer.rs:1-12`），target 存窗口设备像素（`:115-121`），光栅化失败的面板被丢弃而不是致命（`:241-243`），`MAX_HUD_DIMENSION = 4096`（`hud/src/paint.rs:22`）。 |
| **FX 叠加层的做法** | **确认为独立叠加层，并补三条硬纪律** | ① **叠加层不进引擎 draw order**：dsh-live2d-pets 的 zone overlay 是 WebGL canvas 的 **DOM 兄弟 canvas**、`position:absolute`、`pointer-events:none`、`z-index:2`（`src/client/index.ts:766-771`，重贴于 `:872`）；BongoCat 的插件 HUD 是「**模型视图是一个专用叠加层，不属于 Live2D 模型绘制**」（`docs/adr/0090…:59-67`）。② **叠加层的重绘要 change-gated，渲染主路径不是**：BongoCat 插件服务只在视口或插件帧真正变化时重发合成 HUD 层（`bongocat-plugin-service/src/service.rs:1055-1061`），而 overlay 绘制本身无 dirty flag（`bongocat-runtime/src/lib.rs:72-75`）——这个不对称是我们 `LottieFxLayer` 该抄的模式。③ **混合与不透明度只在一个地方定义**：BongoCat 的混合表单源且被测试钉住（`crates/bongocat-overlay/src/blend.rs:50-71`：Normal `One/OneMinusSrcAlpha`、Additive `One/One`、Multiplicative `DestColor/OneMinusSrcAlpha`；测试 `src/tests/blend.rs:6-31`），premultiplied alpha、两个 shader 都不做 sRGB 编解码、不透明度只在最终 surface 施加一次（`docs/adr/0063-legacy-mver-color-pipeline.md:37-50`）；两端都**没有深度缓冲**，提交顺序就是 z 序（`windows/renderer.rs:504-507`）。 |
| **barge-in / 中断质量** | **有可抄的分层，但没有任何一家在 Android 上验证过** | OLLV-Web 三层门控 + `@ricky0123/vad-web` 参数（§5.7）；NyaDeskPet 的数值优先级 + 前端「新响应优先级 >= 当前 → 中断当前」（`src/agent/pipeline.ts:131-143`、`renderer/js/response-controller.ts:18-19, :50-83`，带 `discardedIds` 缓存过滤已在流的 chunk）；ChatWaifu 完全没有 barge-in（§2）。**没有可比数据的项**：本轮没有任何一个项目给出中端 Android 的每帧成本或打断时延——我能给的只是结构性成本（pixi 每帧重绑全部纹理 `Live2DModel.ts:315-346`；NyaDeskPet 每帧一次跨进程 `getCursorScreenPoint` `live2d-manager.ts:1010-1039`；OLLV-Web 60fps 上限 + 一个永不结束的第二个 RAF `lappdefine.ts:104-105`）与节流旋钮（BongoCat 15–240、默认 60、隐藏降到 10Hz `bongocat-runtime/src/lib.rs:72-75`）。**我们自己的 ΔPSS/包体数据在设计文档 `:76, :80`，本轮不改它，也不拿桌面项目的数字冒充它。** |

### 10.1 活跃度与许可风险汇总（诚实标注）

| 仓库 | tip | 代码许可 | 必须记住的那一条 |
|---|---|---|---|
| ChatWaifu_Mobile | 2023-06-19 | MIT | 上游已 2.5 年不动；本地 fork 的 Cubism 5-r.5 + 越界写防御是我们的改动，不是上游事实（§0 第 2 条）。随包三个模型（ATRI/Amadeus/Yuuka）是公版样例，manifest 什么都没声明（§2.4）。 |
| alive2d | 2025-04-30 | **无 LICENSE 文件** | 无授权声明即不可依赖；只能读语义，不能借代码。 |
| CubismNativeFramework | 2026-06-26 推送 | Live2D Open Software License | 不是宽松许可；Core = Redistributable Code；Expandable Application 需 Live2D 事先批准（§3.2）。 |
| shimeji（`com.wishes`） | 2026-05-09 | GPL-3.0 | **GPL-3.0 不能进我们的 APK**；只抄语法与物理思想（§6）。schema 里 `RegistanceX/Y` 错拼已是事实契约（`demo/conf/schema.properties:27-28`）。 |
| Shijima-Qt | 2026-04-29 | GPL-3.0（已声明停更） | 同上，且解析器在空 submodule 里（§0）。它的 hit test 是 list 插入序 first-wins（`ShijimaManager.cc:1054-1062`）。 |
| dsh-pet | 2026-11-24 | 见 `THIRD_PARTY_NOTICES.md:53-63` | **代码可宽松用，Piapro 角色权保留**；只抄 `gameplay.touch.zones[]` 的 schema 形状，绝不取随包素材。 |
| dsh-live2d-pets | 2026-09-24 | **声明 MIT 但没有 LICENSE 文件** | 加上随包专有 core，许可状态实际上不可依赖（§4.2）。它的 sibling-canvas 叠加与 30fps/模糊停转纪律仍可读。 |
| live2d-widget | 2026-07-06 | MIT | 没有 zone 格式（§4.3）；`window.Image` 被 monkey-patch 成 `crossOrigin="anonymous"`（`dist/autoload.js:42-48`）是 canvas 截图类需求真实的坑。 |
| pixi-live2d-display | 2023-12-15 | MIT | 事实冻结 + 无 `v0.5.0` release + 口型是注释掉的 TODO（§7.1）。可抄的是 `MotionPriority` 与 `FocusController` 数值。 |
| BongoCat | 2026-10-28（v2.4.1） | Apache-2.0 | 最硬的工程纪律来源；但**它自己声明「不得发布包含 Cubism Core/Framework 的安装包」**（`docs/cubism/cubism-sdk-source-and-license.md:81-84`，blocker 在 `:68-77`），且随包三个预置模型无 provenance。 |
| NyaDeskPet | 2026-02-27 | MIT（README 只说 MIT） | 随包虹色Mao **中/大型企业只能非公开内部试用**（`models/live2d/mao_pro_zh/ReadMe.txt:22-24`），且其魔法 motion 不能与普通表情组合（`:15`）；vendored `live2dcubismcore.min.js` 是专有 Redistributable（§8.5）。设计（`param-map.json`、RMS 口型、优先级抢占）可抄，**资产与许可表述不可抄**。 |
| Open-LLM-VTuber-Web | 2025-09-05 | MIT | 它是官方 Web SDK sample 的 fork（§5.1），不是独立引擎；Electron 窗口/hover 注册表与 VAD barge-in 是它最可抄的两段。 |

**关于 brief 里那句「piyo / piyosago 依赖」的更正**：`grep -ri piyo` 在 Open-LLM-VTuber-Web 全树 **0 命中**；后端 `/Users/gqli/work/Open-LLM-VTuber` 只有二进制噪声命中；`git log --all -S piyosago` 与 `git log --all -S piyo -- '*.py' '*.md' '*.txt'` **均为空**；`.gitmodules` 里唯一 frontend 是 `Open-LLM-VTuber/Open-LLM-VTuber-Web`（branch `build`，pin `06a659b`）。外部搜索也没找到叫 piyosago 的项目。**结论：不存在这个依赖，目标 ② 的正确读法是读 OLLV-Web 自己的 Live2D/Electron 层（即 §5）。**

### 10.2 本轮新增/改写的四条纪律（写下来，避免下次重复调研）

1. **`hit_test` 语义统一 = art-mesh 顶点的 AABB**（pixi `InternalModel.ts:195-244`；官方 sample `LAppModel.cpp:637-654` 的 `HitTest` + `IsHit`；NyaDeskPet 继承同一 vendored SDK）。**没有任何一家用 polygon 或 alpha 命中**；唯一用逐像素 alpha 的是 shimeji（`GenericTranslucentWindow.java:102` 的 `setWindowMask`），而它是贴图桌宠不是 Live2D。我们的 `touch_zones` 若想要非矩形命中，只能自己实现，没有现成实现可抄。
2. **「有格式、无实践」在这条线上是常态**：shimeji `<Hotspot>`/`<Information>`/`Affordance` 全部接线完整、随包零实例（`grep "<Hotspot" demo/` = 0）；live2d-widget 的 `hitAreasCustom[id+'_x'/'_y']` 兜底约定存在无人用（`src/cubism2/LAppModel.ts:420-443`）；NyaDeskPet 的 `timeline` 通道有消费者无生产者（`audio-player.ts:386-398` + `backend-client.ts:326-344`）；BongoCat 每个预置包都带着没人引用的 `exp_1/exp_2.exp3.json`。**推论：payload 里每加一个字段，就必须同时有一个生产者、一个校验点和一个测试，否则它一定变成死代码。**
3. **能力要靠版本门控，不是靠新 id**：BongoCat 插件侧写得很明确（ADR-0090 `:92-93`「未来原语/能力要靠 protocol minor/version 门控，不是造新插件 ID」；安装要求 semver `version` + semver-range `app_versions` + `protocol_version == 1`，`install.rs:399-419`；握手必须回显 id + protocol `:718-727`）。对照反面：NyaDeskPet 的 `config.version !== 1` 精确硬拒（`live2d-manager.ts:649-678`）是全场唯一版本门，**范围式（`>=`）而不是等值**，这条要写进我们的 sidecar 校验。
4. **`hash` 不是权利**：见 §10 表格的 `model_ref` 行。发布链上的 EULA 缺口是真实存在的——BongoCat 安装器里**没有许可页**（`crates/bongocat-packaging/src/main.rs:894` + ADR-0033 `:162`）。
