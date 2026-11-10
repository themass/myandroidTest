# 整理计划：一个包一个包地改

目标结构和判断规则见 [architecture.md](architecture.md)。本文是执行计划。
数据基于 `weifu-refactor`（2026-11-10）：本项目自己的代码 461 个文件、53,463 行（不含 `:retromusic`、`:auxio_music_ui`），其中 Java 319 个。
统计用的脚本在 `/tmp/modscan/`。文本扫描会误报，**每个结论都以编译为准**。

---

## 0. 已确认的前提（2026-11-10）

| # | 问题 | 决定 | 对计划的影响 |
|---|---|---|---|
| 1 | 视频播放引擎 | **保留 GSY 和全部 4 个引擎**（Exo2、IJK/FFmpeg、阿里、系统），不换 Media3 | 视频只整理结构：拆大类、移对位置。`PlaybackEngineHelper` 和设置页的引擎切换都保留 |
| 2 | 旧 Java 页面换 Compose | **非必要不换** | 旧页面只搬家、拆大类。只有要改它的功能或者它挡住了别的整理时才重写。`:core/legacyui` 会长期存在 |
| 3 | 音乐模块 | `:retromusic` 可以改，不强行统一风格、不拆包、不合并。`:auxio_music_ui` **删除** | 删除步骤在阶段 1；`:retromusic` 的改动只在 5.9 方案确认后做 |
| 4 | 旧页面框架、对话的数据库实体 | 暂留 `:core/legacyui`、`:core/db` | 按此拆 `:core` |
| 5 | 工具类 | `ObjectUtils`、`StringUtils` 等**要整理** | 见第 5.6 节 |
| 6 | 网络层 | **要整理** | 见第 5.8 节 |
| 7 | 公式渲染 | 先比较自定义插件和 Markwon 官方 `ext-latex`，再决定 | 见第 5.5 节 |
| 8 | 对话的两个大类 | 按第 5.1、5.2 节拆；两个 bug 拆完先补单元测试再修 | 见第 5.1、5.2 节 |

**关于音乐模块**：
- `:retromusic`：本地音乐，从「我的」→ 本地音乐进入。可以改，但不强行统一风格、不拆包、不合并。
- `:auxio_music_ui`：音乐频道的另一套界面，和频道声音页功能重复（同一份服务器歌单、同一个接口、同一个入口）。**删除**。频道声音页保留自己的 Auxio 风格布局开关。
- 删除后音乐只剩两处：本地音乐（`:retromusic`）和频道声音页（服务器歌单）。能否只用 `:retromusic` 承载频道歌单，见 5.9 节。

---

## 1. 方法

### 1.1 每个包都走这 6 步
1. **盘点**：列出每个文件、行数、谁在用。
2. **删**：没人用的类、方法、资源、依赖。以编译为准。
3. **移出**：不属于这里的，按 architecture.md 第 2 节的种类，移到它该在的模块。
4. **审视大类**：超过 400 行，或者一个类管好几件事的，写一个拆分方案（第 5 节）。
5. **执行**：一个包一个提交。
6. **验证**：`:app:assembleChatDebug` 编译；`:app:testChatDebugUnitTest` 单元测试；手机上走一遍这个包涉及的页面，截图存证。

### 1.2 重构还是重写
| 情况 | 做法 |
|---|---|
| 逻辑复杂、业务核心、最近还在修 bug（如对话） | **原地重构**：先拆成几个类，行为不变，每拆一块验证一次 |
| 技术陈旧（Java View、MediaPlayer），但能用 | **非必要不重写**：只搬家、拆大类。只有要改它的功能或者它挡住了别的整理时，才按 1.3 重写 |
| GSY 播放器 | **不换**，只整理结构（第 5.3 节） |
| 已经是 Compose/Kotlin、结构清楚 | **只搬家**，不改 |
| 被很多旧页面继承的基类（`legacyui`） | **只搬家**，不改继承结构。旧页面不重写，所以它们会一直在 |

### 1.3 重写的步骤（先写新的，再删旧的）
1. **写清旧功能**：页面上有什么、能做什么、调哪些接口、有哪些边界情况。写进本文对应的小节，作为验收标准。
2. **写新实现**：放在目标位置，用 Compose、协程。旧代码不动。
3. **切换入口**：路由改指向新页面。
4. **手机验证**：按第 1 步的清单逐项对，截图。
5. **删旧代码**：旧类、只有它用的资源、布局、依赖一起删。
不保留新旧切换开关，回退用 git。

### 1.4 顺序原则
- **从低风险到高风险**：先删，再搬，再重构，最后重写。
- **能用的不重写**：陈旧但能用的代码，拆大类、删冗余就够了。
- **包名最后统一改**：搬文件时包名不变，所以 import 不用改；最后一次性改包名。
- **和 UI 会话错开**：UI 会话在 `weifu-refactor` 上改对话和 `:chatkit` 的界面。动这两处之前先确认它空闲，避免冲突。

---

## 2. 阶段

| 阶段 | 内容 | 风险 | 预计提交数 |
|---|---|---|---|
| 1 | **全局清理**：删无用的第三方库、资源、assets、死类；拆 Manifest（各模块登记自己的 Activity） | 低 | 6–8 |
| 2 | **`:common`**：`:commonutil` 按 `util/ net/ ui/` 整理，业务类上移 `:core`，并入 `:sharekit`，去重；工具类瘦身（5.6）；网络层整理（5.8） | 中 | 10–12 |
| 3 | **`:media`**：建模块，并入 `:asr`、`:mediaview`，移入图像、字幕翻译、录音播放 | 中 | 4–5 |
| 4 | **`:core`**：单一使用方的类下沉，内部按 `config/ model/ db/ event/ legacyui/` 整理 | 中 | 5–6 |
| 5 | **feature 逐个整理**：对话 → 用户 → 拍照 → 伴侣（不动）。对话的大类按 5.1、5.2 拆；公式渲染按 5.5 比较 | 中 | 每个 3–6 |
| 6 | **视频整理**：保留 GSY 和 4 个引擎；`DetailGSYVideoPlayer` 按 5.3 拆；字幕、音乐服务移出；DLNA 移入 | 中 | 3–5 |
| 7 | **内容频道整理**：`:feature-channel` 改名 `:feature-content`，按内容类型分子包；`SoundItemsMusicFragment` 按 5.4 拆。旧页面不重写 | 中 | 5–8 |
| 8 | **`:app` 瘦身**：实现类回到 feature，频道列表和「我的」移出，去掉 `AppHooks` | 中 | 4–5 |
| 9 | **收尾**：删 EventBus；统一包名；加架构测试 | 中 | 3–4 |

**进度（2026-11-10）**
- 阶段 1：完成。死代码和依赖（`573857e2`）、assets（`76a8c73a`）、资源（`f1a891bf`）、Manifest 拆分（`26017480`）。
- 阶段 2：基本完成。业务类上移（`6190f080`）、网络层配置注入 `NetConfig`（`0b16f041`）、工具类瘦身（`d3eee5ea`）、分享合一（`567ac077`，`34dfade2`）、屏幕尺寸合一 `ScreenSize`（`4b44eb15`）、`:sharekit` 并入 `:commonutil`（`160f5189`）。两项未做：权限去重（搁置，见 5.10）；`:commonutil` 的目录和子包整理，推迟到阶段 9 和包名统一一起做，因为 Java 要求目录和包名一致，现在只改目录会改两次 import。
- 另外修复了分支合并遗留的 FileProvider 重复 authority（`14510889`）。
- 所有提交都只做了编译验证，没有在手机上测。

**进度（2026-11-10，续）**
- 阶段 3（`:media`）：`:asr` 和 `:mediaview` 合并为 `:media`（`a5c385a3`）；录音播放（`a2fa1f86`）、图像处理（`16e6ca94`）、字幕翻译（`490049c1`）迁入。`:media` 不依赖 `:core`。`viewer` 留在 `com.openapi.core.view.media` 目录下，包名统一放到阶段 9。
- 阶段 4（拆 `:core`）：用户专属的表单、数据对象、事件迁到 `:feature-user`（`f108b303`）；频道专属的类迁到 `:feature-channel`（`5ac377d1`、`e71eb517`，含 showtype 组和推荐适配器，`ImagePhotoLoad.loadPhoto` 去掉）；DLNA 投屏迁到 `:feature-video`（`9aa0b098`）；设计系统和权限（`design`、`permission` 包）迁到 `:commonutil`（`550d67ab`）。
- 阶段 4 的计划修改：
  - `ImagePhotoLoad` 的通用方法（`loadCommonImg`、`options`、`getBuilder`、`getCountryImage`）**不再移到 `:common`**。它们用到 `Constants`、`BaseRes`（映射到 `:core` 的 drawable），而且被 app、用户、视频、频道多个模块共用，按标准应属于 `:core`，留在 `:core`。
  - `data/urlparser` 和 `CustomeAddForm` **留在 `:core`**，因为 `:core` 的 `WebViewActivity` 在用它们，不是只给用户模块用的。
  - `DelImageTask` 已删除（没有使用方）。
- 上面每一步都只做了编译验证，没有在手机上测。统一手机测试放在阶段 4 结束后做。
- 阶段 6（部分）：`PlayService` 迁到 `:feature-channel`（`d022b8f3`）；`feature-chat` 去掉对 `:feature-video` 的依赖（`ff4f6fe3`）；登录页通过 `Routes.login` 打开（`e903b88a`）；音乐字幕绑定 `MusicSubtitleBindings` 迁到 `:feature-channel`（`7c818aec`）；视频页打开经 `Routes.videoShow` / `Routes.videoShowLazyUrl`，`:feature-channel` 去掉对 `:feature-video` 的依赖（`d0e3853b`）。`:feature-channel` 现在只依赖 `:core`、`:commonutil`、`:media` 等，不再依赖其他 feature 模块。编译通过，未在手机上测。
- 阶段 6（5.3）：`DetailGSYVideoPlayer` 721 → 444 行（`d62ea891`）。进度条（`SeekProgressController`）、换内核（`EngineFallback`）、「更多」菜单（`PlayerMoreMenu`）、投屏按钮（`CastButtonBinder`）、刘海避让（`PlayerInsets`）拆出，行为不变。GSY 的覆盖方法（触摸、全屏、返回、`setUp`、`onError`）留在原类，因为它们直接读写 GSY 的 protected 字段。仅编译验证，手机验证（播放、拖动、全屏、倍速、出错换引擎、投屏）待统一测试。
- 阶段 6/7（5.4，部分）：`SoundItemsMusicFragment` 662 → 587 行（`6ea3e5ac`）。服务绑定拆为 `MusicServiceConnection`，播放模式拆为 `PlayModeController`，两处相同的播放探针合并为一个。未做：`MusicSubtitleBinder`（与 `SoundDetailActivity` 中的字幕绑定合并）、`SoundDetailActivity` 瘦身、`PlayService` 移到 `sound/`。仅编译验证，手机验证待统一测试。


阶段 1 到 4、6、7 是**搬、删、拆**，功能不变。阶段 5、8 有少量行为变化（对话 bug 修复、路由替换）。

---

## 3. 逐包清单

表里「处理」一栏：**留**（不动）、**移**（移到别的模块）、**删**、**拆**（拆成几个类）、**重构**、**重写**。
标「待编译确认」的，是脚本找不到引用，但可能通过反射、布局、拼接字符串使用的。

### 3.1 `:commonutil` → `:common`（阶段 2）

| 包 | 文件/行 | 处理 | 说明 |
|---|---|---|---|
| `core`（根） | 1/33 | 移 `:core/config` | `CommonConstants` 是接口域名、请求头名等业务配置 |
| `core.net` | 11/1044 | 留 `net/http` | 实际是 OkHttp，只是类名仿 Volley。阶段 9 前加挂起函数版本 |
| `core.net.request` | 9/486 | 留 `net/request`；改 | `BaseRequest` 直接发登录事件 `UserNeedLogin`，改成回调，由 `:core` 注册 |
| `core.net.netglide` | 6/385 | 留 `net/glide`；`UnsafeOkHttpClient` 移 `net/unsafe` | 不改行为 |
| `core.net.interceptor` | 1/26 | 留 `net/dns` | |
| `core.util` | 34/5218 | **拆**成 `util/lang android log json event share` | 见下表 |
| `core.util.cache` | 4/612 | **删**（待编译确认） | 4 个磁盘缓存类只在本包内互相引用 |
| `core.util.permission` | 3/848 | **删**，合并进 `ui/permission` | 三套权限之一，`:core` 里已有 Compose 版 |
| `core.event` | 5/132 | 移 `:core/event`；`FavoriteVo` 移 `:core/db` | 业务事件；`FavoriteVo` 是 Room 实体 |
| `core.favorite` | 1/24 | 移 `:core/favorite` | |
| `core.view.translation` | 10/607 | 移 `:media/subtitle/api`、`overlay` | 视频和音乐共用的字幕翻译框架 |
| `core.view.touchhelper` | 4/262 | 移 `:feature-content/common` | 只有频道的列表拖拽用 |
| `core.view.view` | 10/1102 | 分 | `MyWebView`、`MarqueeTextView`、`MyEditText`、`MyScrollView`、`SiteAutoCompleteTextView`、`SlidingLayout`、`DividerItemDecoration` → `ui/widget`；`MyFavoriteView`、`FavoriteImageView` → `:core/favorite`（**拆**：`MyFavoriteView` 287 行，同时管收藏和翻译入口）；`MyTextView` → 只有频道用，移过去 |
| `core.view.view.widget` | 4/675 | 分 | `CircleImageView` → `ui/widget`；`DraggableTranslationOverlay` → `:media/subtitle/overlay`；`StateButton`（306 行）、`IndicatorView` **删**（待编译确认，只在本模块里出现） |
| `core.view.view.widget.util` | 3/1330 | **删** `FileUtils`（1244 行，照搬自第三方工具库，无人使用）；`ImageUtil` 只有 `:retromusic` 用，随它走；`ImageSize` 移 `:feature-chat` | |
| `core.view.view.gallery` | 1/80 | 移 `:feature-video` | 只有视频用 |
| `core.view.view.widgets` | 1/161 | 移 `:feature-content/sound` | `MusicVisualizer` |

**`core.util` 的 34 个文件**
| 去向 | 文件 | 改动 |
|---|---|---|
| `util/lang` | `StringUtils`、`DateUtils`、`CollectionUtils`、`ObjectUtils`、`Md5`、`AES2`、`VersionCompare`、`BeanUtil` | **瘦身**：`ObjectUtils` 901 行，全项目只用 3 个方法；`StringUtils` 313 行，外部只用 `hasText`（42 处）、`isEmpty`（13 处）、`upLow`（1 处）。留用到的，其余删；新代码直接用 Kotlin 的 `isNullOrBlank` |
| `util/android` | `PreferenceUtils`、`FileUtils`、`PathUtil`、`PackageUtils`、`DeviceInfoUtils`、`ToastUtil`、`AudioMngHelper`、`Background`、`SystemUtils`、`IpUtil`、`MediaUtil`、`StaticDataUtil`、`ViewUtils`、`Tools` | **合并**：`DensityUtil`、`DisplayUtil`、`Utils` 三处屏幕尺寸合成 `ScreenSize`。`FileUtils` 468 行，外部只用 8 个方法，其余删 |
| `util/log` | `LogUtil` | 去掉对 `CommonConstants` 的依赖，DEBUG 开关启动时传入 |
| `util/json` | `GsonUtils`、`ModelUtils` | `ModelUtils` 509 行，外部只用 `json`、`entry`。瘦身 |
| `util/event` | `EventBusUtil` | 阶段 9 删除 |
| `util/share` | `ShareUtil` | 和 `:sharekit` 的 `ShareKit` 合并成一套 |
| `ui/permission` | `PermissionHelper` | 和 `core.util.permission`、`:core/permission` 合并 |
| `:core/favorite` | `FavoriteUtil` | 业务 |
| `:core/config` | — | `LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 读 `CommonConstants` 的地方改为启动时注入 |

### 3.2 `:sharekit`、`:asr`、`:mediaview`（阶段 2、3）

| 模块 | 文件/行 | 处理 |
|---|---|---|
| `:sharekit` | 1/104 | 移 `:common/util/share`，和 `ShareUtil` 合并；删模块 |
| `:asr` | 6/886 | 移 `:media/speech/asr`、`tts`。`VolcengineAsrConfig` 读 `:core` 的 `Constants`、`DeviceSpeech` 用 `AppContext`，都改成构造参数 |
| `:mediaview` | 2/333 | 移 `:media/viewer`；删模块 |

### 3.3 `:core`（阶段 3、4）

130 个文件：留 49，移出 79，删 1（`AppHooks`），拆 1（`ImagePhotoLoad`）。

| 包 | 文件/行 | 处理 |
|---|---|---|
| `com.aimath.paizhao.photosolve`（含 `image`、`quality`、`cropassist`） | 14/1028 | 移 `:media/imaging`。包名像拍照模块的，但内容是通用图像处理 |
| `core.image` | 1/63 | 移 `:media/imaging/compress` |
| `core.media` | 2/300 | 移 `:media/speech/record`、`play` |
| `core.design.theme`、`core.design.dialog` | 3/357 | 移 `:common/ui/theme`、`dialog` |
| `core.permission` | 3/271 | 移 `:common/ui/permission`，作为合并后的唯一一套 |
| `core.cast`（含子包） | 11/735 | 移 `:feature-video/cast/dlna` |
| `core.app` | 2/67 | `AppContext` 留；`AppHooks` 阶段 8 删 |
| `~.constant` | 2/339 | 留 `config/`。`Constants` 有 82 个接口地址，以后可按功能拆，这次不拆 |
| `~.bean.vo` | 22/621 | 分：`RecommendVo`、`SoundItemsVo`、`CharacterVo`、`NullReturnVo`、`StateUseVo`、`VersionVo`、`VipDescVo`、`Token` 留 `model/`；`AppInfo`、`ImgItemsVo`、`TextItemVo`、`TextItemsVo` 移 `:feature-content`；`UserInfoVo`、`IWannaVo` 移 `:feature-user`；`ChatSessionVo`、`ChatMessageVo`、`ChatMessagePageVo`、`Choice`、`Message`、`HistoryVo`、`AsrResponseVo`、`TtsResponseVo` 移 `:feature-chat/data`（随 `ChatRepositoryImpl`） |
| `~.bean.form` | 10/412 | 分：`ChatLog`、`ChatSessionLog`、`ConnLog` 留 `db/`（Room 实体）；`CustomeAddForm` 留；`LoginForm`、`RegForm`、`IwannaForm` 移 `:feature-user`；`FeedbackContentForm` 移 `:feature-chat`；`AsrContentForm`、`ConnLogForm` 移 `:feature-chat/data` |
| `~.data.db` | 8/322 | 留 `db/` |
| `~.data` | 4/643 | `VersionUpdater`（332 行）留 `app/`，**重构**：检查更新、弹窗、发两个 EventBus 事件混在一起；`ImagePhotoLoad` **拆**；`HistoryUtil` 移 `:feature-content`；`ConnLogUtil` 移 `:feature-chat/data` |
| `~.data.config` | 7/80 | 分：`PlayCoreEvent`、`StateUseEvent`、`VipDescEvent`、`HindBannerEvent` 留 `event/`；其余 3 个移 `:feature-user` |
| `~.data.showtype` | 6/275 | 移 `:feature-content/common` |
| `~.data.urlparser` | 4/107 | 移 `:feature-user` |
| `~.task` | 4/214 | 移 `:feature-content/image`、`text` |
| `~.adapter`、`~.adapter.base` | 3/575 | `IndexRecommendAdapter`、`BasePhotoFlowRecycleViewAdapter` 移 `:feature-content/common`；`BaseRecyclerViewAdapter` 留 `legacyui/` |
| `~.ui.base`、`~.ui.base.app`、`~.ui.base.features` | 17/2504 | 留 `legacyui/`，**只搬不改**。`VideoWebViewActivity`、`BaseBannerAdsActivity` 只有 `:app` 直接用，但被继承，一起留。旧页面非必要不换，所以长期保留 |
| `~.ui.channel`、`~.ui.gsy.compose` | 2/423 | `WebviewScreen`、`ListShell` 留 `legacyui/`（只被旧基类用） |
| `~.ui.inte` | 3/40 | `FabOpListener` 留 `legacyui/`；其余 2 个移 `:feature-content` |
| `~.view` | 1/102 | `MyRadarScanView` 移 `:app`（启动页） |
| `chat.ui.data.model` | 1/9 | `MessageType` 留 `db/`（被 `ChatLog` 实体用） |

### 3.4 `:ads`（阶段 1、9）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `ads.base` | 10/484 | 留 `api/`。`AdsContext` 里对 VIP 的判断已注释掉，删注释代码 |
| `ads.admob`、`ads.adview`、`ads.config` | 14/809 | 留。`admob` 用 EventBus 通知开屏关闭，阶段 9 换 Flow |
| `ads`（根） | 2/107 | `Constants` 阶段 9 改名 `AdsIds` |
| `ads.um` | 1/62 | `MobAgent` 暂留 |
阶段 1 先删 `AdsContext` 里注释掉的代码。

### 3.5 `:chatkit`（阶段 5，和 UI 会话错开）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `input` | 6/1392 | 留。`VoiceControls` 495 行：录音手势、浮层、波形在一个文件，**审视**是否拆 |
| `message` | 5/1129 | 留。`ChatMessageList` 449 行，审视 |
| `message.parts`、`model`、`theme`、`media` | 5/770 | 留 |

### 3.6 `:feature-chat`（阶段 5）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `chat.presentation` | 3/1296 | **拆** `ChatViewModel`（1066 行），见第 5.1 节 |
| `chat.presentation.ui` | 8/1624 | 留。`ChatScreen` 433 行，审视 |
| `chat.domain.*`、`chat.data.*`、`chat`（根） | 10/671 | 留 |
| `chat.ui.markdown` | 2/151 | 移 `markdown/`。`ChatMarkwon` 借用了 GSY 播放器的一张错误图，改用自己的图，去掉对 GSY 的依赖 |
| `~.view.katexplugin` | 11/1392 | 移 `markdown/katex/`。**审视**：Markwon 自带 `ext-latex` 插件，而这里是一份 11 个文件的自定义版本（`MyJLatexMathPlugin` 547 行）。先写清它比官方插件多做了什么，如果差别不大就**重写**为官方插件加少量配置 |
| `build.gradle` | — | 删对 `:feature-video` 的依赖（没有使用） |
| 新增 `roles/` | — | 并入 `:feature-roles`（7 个文件，418 行），结构不变 |
| 新增 `data/` | — | 从 `:app` 移入 `ChatRepositoryImpl`、`BackendSpeechToText`、`LegacyLoginGate`、`RolesRepositoryImpl`、`BundledRoleCatalog`（阶段 8），`ChatRepositoryImpl` 按第 5.2 节拆 |

### 3.7 `:feature-user`（阶段 5）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `~.ui.user` | 5/1332 | 留，按 `login/ register/ password/ profile/ setting/` 分子包。登录、注册、找回密码、设置已经是 Compose。`RegisterScreens.kt` 改名为 `RegActivity.kt`（文件名和类名不一致）。`AddCustomeInfoActivity` 是 Java View，非必要不换 |
| `~.ui.feedback` | 1/482 | 留 `feedback/` |
| `~.ui.fragment`、`~.adapter` | 2/177 | 设置页的角色设置，Java Fragment + Adapter，移进 `setting/`，不重写 |
| `~.task` | 1/64 | `LoginTask` 改为挂起函数 |
| `~.data` | 1/77 | `UserLoginUtil` 移 `:core/account`；登录事件改 Flow |
| `build.gradle` | — | 去掉对 `:feature-video` 的依赖：播放引擎偏好移到 `:core/prefs` |

### 3.8 `:feature-photosolve`（阶段 5）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `com.aimath.paizhao` | 1/272 | 留。主题已改为应用主题 |
| `photosolve` | 5/346 | 分 `data/`、`presentation/` |
| `photosolve.ui` | 9/1299 | 留。`PhotoCropScreen` 400 行，审视 |

### 3.9 `:feature-companion`

不动。角色扮演 Android 端在自己的分支开发。只在阶段 9 统一改包名。

### 3.10 `:feature-video`（阶段 6）

保留 GSY 和全部 4 个引擎。只整理结构。

| 包 | 文件/行 | 处理 |
|---|---|---|
| `~.ui.gsy` | 4/1138 | 移 `player/`、`list/`。`DetailGSYVideoPlayer`（721 行）按第 5.3 节**拆** |
| `~.video` | 1/151 | `PlaybackEngineHelper` 留 `player/`，引擎切换逻辑不变；只把「当前选的哪个引擎」这个偏好值移到 `:core/prefs`，让 `:feature-user` 的设置页不用依赖视频模块 |
| `~.ui.sound` | 6/777 | 移 `page/`（包名叫 sound，里面全是视频页）。Java View，不重写。`VideoActivity` 只在 Manifest 登记、代码里没有入口，**删**（待确认）。`VideoShowActivity`、`VideoShowActivityLazyUrl` 里 ButterKnife 遗留的空方法 `unbindButterViews` 删 |
| `~.ui.localvideo` | 1/305 | 留 `local/` |
| `~.cast` | 4/267 | 留 `cast/googlecast/`；DLNA 从 `:core` 移来 `cast/dlna/` |
| `~.service` | 1/496 | `PlayService` 是**音乐**播放服务，视频模块自己不用，移 `:feature-content/sound`，不重写 |
| `~.translation` | 4/116 | 3 个翻译提供者移 `:media/subtitle/provider`；`VideoTranslationInstaller` 移 `:app` |
| `~.translation.realtime` | 10/1187 | 引擎和 7 个 PCM 音频源移 `:media/subtitle/realtime`；`RealtimeSubtitleBindings` 拆：视频部分留，音乐部分移 `:feature-content/sound` |
| `~.data` | 1/82 | 留 |

### 3.11 `:feature-channel` → `:feature-content`（阶段 7）

现在 39 个文件、约 6,000 行；从 `:core` 下沉 20 个后约 59 个。旧页面**不重写**，只按内容类型分子包、拆大类。

| 现包 | 文件/行 | 去向 | 处理 |
|---|---|---|---|
| `~.ui.fragment` | 15/2686 | `video/ image/ text/ sound/ mine/` | 移。`SoundItemsMusicFragment` 675 行，按第 5.4 节**拆** |
| `~.ui.fragment.body` | 5/301 | 各类型子包 | 移 |
| `~.ui.sound` | 8/791 | 各类型子包 | 移。`TeleplayActivity` 标题是「电视剧」，实际显示的是图片频道，**确认后删** |
| `~.ui.sound.compose` | 2/343 | `sound/` | 移 |
| `~.ui.channel` | 3/730 | `common/` | 移。`ChannelBodyScreen` 479 行，审视 |
| `~.adapter` | 4/432 | 各类型子包 | 移 |
| `~.auxio` | 1/60 | `sound/` | 移。`:auxio_music_ui` 本身不动 |
| `~.ui.config` | 1/47 | `config/` | 移 |

### 3.12 `:app`（阶段 1、8）

| 方面 | 处理 | 阶段 |
|---|---|---|
| 第三方库 | 声明了约 60 个，`:app` 自己只用 multidex、Media3。没有任何模块使用的（navigation、percentlayout、viewpager2、rxjava、webkit 等）删；其余移到使用它的模块。`:retromusic` 自己声明的依赖不动 | 1 |
| Manifest | 26 个 Activity 中 24 个属于别的模块，移到各模块自己的 Manifest | 1 |
| 资源 | 66 个没有引用的删；3 个只有模块用的移过去 | 1 |
| assets（约 21 MB） | 根目录 23 个字体、`emoji.db`、`markdown.css`、几个 HTML、一个 wav 没有找到引用，逐个核对后删（字体可能通过拼接路径加载，单独核对）；模型 13 MB 移 `:media`、KaTeX 移 `:feature-chat`、personas 移 `roles/` | 1 |
| 测试 | 11 个中 10 个测的是别的模块，随被测代码移走 | 1 |
| `data/chat`、`data/roles` | 移 `:feature-chat`（构造参数类型 `MyApplication` 改为 `Context`） | 8 |
| `home/presentation` 的频道列表、`LegacyHomeEntries` 的频道部分、`ConfigActionJump` | 移 `:feature-content`（`ConfigActionJump` 由频道提供路由表） | 8 |
| `ProfileScreen`、`LegacyHomeEntries` 的「我的」部分 | 移 `:feature-user` | 8 |
| `AppHooksImpl` | 删 | 8 |
| 启动页 `LaunchActivity` | 留。Java、带 ButterKnife 的空方法 `unbindButterViews`，顺手转 Kotlin | 8 |

---

## 4. 阶段 1 的具体删除清单（全部待编译确认）

| 类别 | 内容 |
|---|---|
| 第三方库（`:commonutil`） | PictureSelector 的 `compress`、`ucrop`、`camerax`；`BaseRecyclerViewAdapterHelper`；`android-shape-imageview`；`de.hdodenhof:circleimageview`（项目自己写了同名类）；`swiperefreshlayout` |
| 整个模块 | `:auxio_music_ui`（12 个文件、1683 行）：删模块、`settings.gradle`、`:app` 和 `:feature-channel` 里的依赖、频道里的 `AuxioMusicUiLauncher` 和声音页的入口按钮（两个布局文件里的 `tv_open_auxio_gpl_module`） |
| 第三方库（`:app`） | navigation、percentlayout、viewpager2、rxjava、rxandroid、webkit（先确认别的模块没有通过 `:app` 间接使用） |
| 死类 | `:commonutil` 的 `util/cache/` 4 个、`widget/util/FileUtils`（1244 行）、`StateButton`、`IndicatorView`；`:feature-video` 的 `VideoActivity`；`ButterKnife` 遗留的 `unbindButterViews` 空方法 3 处 |
| 死代码 | `AdsContext` 里注释掉的 VIP 判断；`AppHooks.openLogin`（没有调用方） |
| 资源、assets | 见 3.12 |

---

## 5. 大类的拆分和重写方案

### 5.1 `ChatViewModel`（1066 行，56 个方法）→ 原地重构

现在一个类管 7 件事。按事情拆成几个协作类，`ChatViewModel` 只保留 `UiState` 和对外的操作入口：

| 新类 | 搬入的方法 | 行数估计 |
|---|---|---|
| `SessionController` | `reloadSessions`、`onSessionSelected`、`onCreateSession`、`onDeleteSession`、`onRenameSession`、`syncSessionsList` | ~120 |
| `CharacterController` | `loadCharacters`、`onCharacterSelected`、`onRoleSelected`、`onCreate/Update/DeleteCharacter` | ~110 |
| `MessageSender` | `onSend`、`sendText`、`streamReply`、`onStopGenerating`、`finishStopped`、`revealCount`、`safeCut`、`onRetry`、`onRegenerate`、`submitEdit` | ~300 |
| `HistoryLoader` | `onLoadOlderMessages`、`fallbackReloadMessages`、本地未同步消息的合并 | ~100 |
| `AttachmentController` | `deliverAttachments*`、`onAttachmentPicked`、`addDraft`、`onRemoveDraft`、`onImagesPicked` | ~120 |
| `VoiceInputController` | `onVoiceRecord*`、`start/stopLiveTranscript`、`onVoiceDraft*`、`discardDraftFile` | ~130 |
| `ChatViewModel` | 状态、`refresh`、消息的删除/点赞/朗读等小操作，把调用转给上面的类 | ~250 |

**做法**：一次拆一个类，行为不变。`docs/design/chat-bugs-and-gallery-plan.md` 里定位的两个问题（重试按钮只剩最后一条、消息顺序乱）都在 `fallbackReloadMessages`，拆出 `HistoryLoader` 后先给它补单元测试再修。

### 5.2 `ChatRepositoryImpl`（767 行）→ 移到 `:feature-chat/data` 并拆分

| 新类 | 内容 |
|---|---|
| `ChatStreamClient` | `streamChat`、`sendMessageStream`、`regenerateStream`（流式回复，最复杂的 150 行） |
| `SessionStore` | 会话的增删改、`syncSessionsFromServer`、本地缓存 |
| `MessageCache` | `cacheTurn`、`cacheRemotePage`、`persist`、`loadHistoryPage` |
| `CharacterRemote` | 角色的增删改 |
| `AttachmentUploader` | `sendAttachment` |
| `SpeechRemote` | `synthesizeSpeech` |
| `ChatRepositoryImpl` | 只做组合，实现 `ChatRepository` 接口 |
另外：`postForOk` 等 4 处「回调转挂起函数」的重复代码，统一成 `:common/net` 的一个挂起函数。

### 5.3 `DetailGSYVideoPlayer`（721 行）→ 拆分，保留 GSY

它继承 GSY 的播放器视图，现在一个类管 6 件事。**GSY 和 4 个引擎都不动**，只把附加功能拆出去：

| 新类 | 搬入的方法 | 说明 |
|---|---|---|
| `SeekProgressController` | `setProgressAndTime`、`onStart/StopTrackingTouch`、`onProgressChanged`、`showDragProgressTextOnSeekBar`、`refreshCachedDurationForSeek`、`applySeekBarProgressToTimeViews`、`resolveDurationMsForSeekUi`、`parseTimeLabelToMs`、`touchSurfaceMove/Up` | 进度条和拖动，约 200 行，是最大的一块 |
| `EngineFallback` | `onError`、`tryFallbackAlternateEngine`、`restorePlaybackEngineIfTemporaryFallback` | 播放出错时临时换引擎再恢复，和 `PlaybackEngineHelper` 放一起 |
| `PlayerMoreMenu` | `showPlayerMoreMenu`、`wireMoreButton`、`formatSpeedLabel` | 「更多」菜单：倍速、设置入口 |
| `CastButtonBinder` | `installGoogleCastRouteButton` | 投屏按钮，放进 `cast/` |
| `PlayerInsets` | `applyPlayerChromeInsets` | 刘海、导航栏避让 |
| `DetailGSYVideoPlayer` | 布局、全屏切换、返回键、海报、loading、收藏视图的挂载 | 约 250 行 |

**做法**：一次拆一个类，行为不变。每拆一块在手机上验证：播放、拖动进度、全屏、倍速、出错换引擎、投屏按钮。
5 个视频页 Activity（Java View）不重写，只移到 `page/`。

### 5.4 `SoundItemsMusicFragment`（675 行）→ 拆分，不重写

现在一个 Fragment 同时管列表、播放服务绑定（15 处）、收藏（19 处）、字幕翻译（22 处）、进度条、播放模式、切换 Auxio 界面。

| 新类 | 搬入的内容 |
|---|---|
| `MusicServiceConnection` | 绑定 `PlayService`、`onServiceConnected/Disconnected`、`play`、`pause`、`resume`、`next`、`pre`、`isPlaying`、`syncPlayButtonFromService`、两个广播接收器 |
| `PlayModeController` | `cyclePlayMode`、`playModeLabel`、`applyPlayModeIcon` |
| `MusicSubtitleBinder` | `setupViews` 里翻译会话和实时字幕的绑定（约 100 行的匿名类），和 `SoundDetailActivity` 里的同一段**合并成一份** |
| `SoundItemsMusicFragment` | 列表、点击、进度条、收藏视图 |

`SoundDetailActivity`（339 行）用 `MusicServiceConnection` 和 `MusicSubtitleBinder` 后，预计减到 200 行以内。
`PlayService`（496 行，`MediaPlayer`）移到 `sound/`，不重写。

### 5.5 公式渲染（11 个文件、1392 行）→ 先比较再决定

依赖里已经有 Markwon 官方的 `ext-latex`，`ChatMarkwon` 也 import 了它。比较步骤：
1. 写清自定义版本做了什么：`MyJLatexMathPlugin`（547 行）、`JLatexMathTheme`（363 行）、两种块解析器（`JLatexMathBlockParser`、`JLatexMathBlockParserLegacy`）、行内处理、异步绘制。
2. 和官方插件逐项对：块公式、行内公式、异步渲染、主题（字号、颜色、内边距）、错误显示。
3. 准备一组测试公式（行内、块、矩阵、分式、错误公式、超宽公式），两边在手机上各截一张。
4. 结果写进这一节。差别小就换官方插件，删掉 11 个文件；差别大就保留，只移到 `markdown/katex/`。

### 5.10 搁置：权限去重（待定，不在当前执行范围）

**现状**：三套旧权限代码仍在：`PermissionHelper`、`CompatiblePermissionManager`、`ModernPermissionManager`（`:commonutil` 的 `util/permission`，约 1000 行），以及 `BasePermissionActivity` 里的权限入口。新的 Compose 版在 `:core/permission`，`ChatRoute` 和 `LocalVideoActivity` 在用。

**代码事实（2026-11-10 核对）**
- `initPerm()` 调用 `hasPerm(空列表)`，空列表返回 `true`，所以 `request()` 不会执行。
- `PermEvent` 只有构造函数，没有任何地方发出，`initPermEvent` 不会被调用。
- `awaitingSettingsReturn` 只在 `startAppSettings()` 里设为 `true`，而这个方法没有调用方，设置页返回的分支不会执行。
- 实际效果只剩：创建三个 helper 对象，以及 `initVosk()` 里创建一个 `PermissionHelper`（之后没有用到）。

**搁置原因**：这套代码和启动流程、`BaseToolbarMenuActivity` 绑在一起，删之前要在手机上过一遍相机、录音、存储的权限弹窗。用户决定先搁置，后面再处理。

**将来的做法（待定）**：确认权限请求只走 Compose 版后，删掉三套旧实现和 `BasePermissionActivity` 里的权限代码；保留 `BasePermissionActivity` 的其它功能（如果有的话）。

### 5.6 工具类整理（阶段 2）

**原则**：外部用到的方法留下（必要时改名），没用到的删掉；能用 Kotlin 标准库取代的，新代码不再调用。

| 类 | 行数 | 公开方法 | 外部实际使用 | 处理 |
|---|---|---|---|---|
| `widget/util/FileUtils` | 1244 | — | 0 | 删（照搬自第三方工具库，有同名类所以之前没发现） |
| `ObjectUtils` | 901 | 39 | `isEmpty`、`nullSafeEquals`（2 处）、`toObjectArray` | 留 3 个方法，约 40 行；其余 36 个删 |
| `ModelUtils` | 509 | — | `json`（4 处）、`entry` | 留 2 个，并入 `GsonUtils` |
| `FileUtils` | 468 | — | 8 个方法（`getWriteFilePath` 6 处、`readAssets` 3 处等） | 留 8 个，约 120 行 |
| `StringUtils` | 313 | 17 | `hasText`（42 处）、`isEmpty`（13 处）、`upLow`（1 处） | 留 3 个，约 30 行 |
| `DensityUtil`、`DisplayUtil`、`Utils` | 153 | — | 屏幕尺寸、dp 换算三处重复 | 合成一个 `ScreenSize` |
| `CompatiblePermissionManager`、`ModernPermissionManager`、`PermissionUtils`、`PermissionHelper` | 1068 | — | 只在本模块内互相调用 | 和 `:core/permission`（Compose 版）合成一套 |
| `ShareUtil` | 217 | — | `:app`、`:feature-user` | 和 `:sharekit` 的 `ShareKit` 合成一套 |

整理后 `core.util` 从约 5,200 行减到约 1,500 行。每个类整理完单独提交，编译确认没有遗漏的调用。

### 5.7 `VersionUpdater`（332 行）→ 重构

检查更新、下载、两种弹窗、发 `StateUseEvent` 和 `VipDescEvent` 混在一起。拆成「检查版本」（挂起函数，返回结果）和「更新弹窗」（UI），版本里带的状态和 VIP 说明改为写进 `:core/account`。

### 5.8 网络层整理（阶段 2）

**现状**：底层是 OkHttp，没有 Volley 依赖，但类名和结构照搬了 Volley（`NetQueue`、`NetRequest`、`Response.Listener`、`MyVolleyError`、`SyncFuture`）。调用链：
```
调用方 → BaseService（约 20 处使用）→ GsonRequest / MultipartRequest / StringRequest / GsonInfoListRequest
       → BaseRequest（加请求头、签名、解析 JsonResult、登录失效时发 UserNeedLogin 事件）
       → NetQueue（OkHttp 调用、主线程回调、按 tag 取消）
```
调用方式有三种：回调（`getData(url, listener, errorListener)`）、同步阻塞（`SyncFuture`，只有 `:feature-user` 1 处）、以及 `:app` 里 4 处自己用 `suspendCancellableCoroutine` 包回调的写法（`ChatRepositoryImpl` 3 处、`BackendSpeechToText` 1 处）。

**问题**
1. 名字误导：读代码的人会以为用的是 Volley。
2. 协程包装各写各的，`ChatRepositoryImpl` 里就有 3 份几乎相同的代码。
3. `BaseRequest` 直接发业务事件 `UserNeedLogin`，网络层依赖了业务。
4. 请求类分 4 种（Gson、Multipart、String、InfoList），差别只是解析方式。

**目标**
```
:common/net/http/
  HttpClient        原 NetQueue：持有 OkHttpClient，执行请求，按 tag 取消
  ApiRequest        原 NetRequest + BaseRequest 的通用部分：方法、地址、参数、请求头
  ApiResult<T>      原 JsonResult + CommonResponse：成功数据或错误
  ApiError          原 NetError + MyVolleyError，合成一个
  ApiService        原 BaseService，同时提供：
                      suspend fun <T> get(url, type): ApiResult<T>
                      suspend fun <T> post(url, body, type): ApiResult<T>
                      suspend fun <T> upload(url, files, type): ApiResult<T>
                      以及保留旧的回调方法（内部调用挂起版本），供 Java 调用方继续用
  AuthFailureListener  登录失效回调，由 :core 注册，再发 UserNeedLogin
:common/net/request/   解析方式（Gson、字符串、分页列表）作为 ApiService 的参数，不再是 4 个请求子类
```

**步骤**
1. 在 `BaseService` 里加挂起函数版本，内部调用现有回调实现。`:app` 里 4 处手写的协程包装改用它。行为不变。
2. `BaseRequest` 的登录事件改为 `AuthFailureListener` 回调。
3. 合并 4 个请求子类为「一个请求 + 解析方式参数」。
4. 改名（`NetQueue` → `HttpClient` 等）。只改名不改逻辑，单独一个提交。
5. 删 `SyncFuture`：唯一调用方（`:feature-user`）改用挂起函数。
6. 调用方随所在的包整理时从回调换成挂起函数；Java 调用方保留回调接口，不强制改。

**不做**：不引入 Retrofit；不改请求头、签名、DNS、Glide 加载这些行为。

---

## 6. 技术升级的落点

| 升级 | 在哪个阶段做 | 完成标志 |
|---|---|---|
| EventBus → Flow | 每个事件在它所属的包整理时换；阶段 9 删依赖 | `EventBusUtil` 删除 |
| 回调 → 挂起函数 | 阶段 2 在 `:common/net` 提供（5.8）；调用方随所在包整理时换 | `:app` 里没有手写的 `suspendCancellableCoroutine` 网络包装 |
| 仿 Volley 命名 → 清楚的名字 | 阶段 2（5.8 第 4 步） | 没有 `Volley` 字样的类和方法 |
| Java → Kotlin | 改到哪个文件转哪个 | 不设完成标志 |
| 新页面用 Compose | 新写的页面 | 旧页面非必要不换 |
| `Serializable` → `Parcelable` 或只传 id | 改到哪个页面换哪个 | — |
| **不升级** | GSY 播放器和 4 个引擎；`PlayService`（`MediaPlayer`）；`:retromusic`；`:auxio_music_ui` | — |

---

## 7. 验证和提交

- **每个包一个提交**，提交信息写清：删了什么、移了什么、改了什么行为、手机上验证了哪些页面。
- **每个提交**：`:app:assembleChatDebug` 通过；`:app:testChatDebugUnitTest` 通过；涉及的页面在手机上走一遍并截图。
- **每个阶段结束**：完整冒烟（启动、对话收发、拍照解题、视频播放、频道浏览、登录、设置），然后合入 `weifu-refactor`。
- **阶段 9 加架构测试**：检查 architecture.md 第 3.1、3.2 节的依赖规则，防止以后又乱。
- 只在一个分支上持续做，不开多个分支。

---

### 5.9 频道歌单能否只用 `:retromusic` 承载（待评估，不动代码）

**需求**：频道声音页要播服务器歌单，支持倍速、收藏、实时字幕翻译、后台播放。

**`:retromusic` 现状**
- 播放器是 `CrossFadePlayer` + Media3（`RetroExoPlayer`），`MusicService` 管后台播放。
- 歌曲模型 `Song` 的地址字段是 `data`，播放时用 `song.uri`。
- 外部能打开的只有 `content://` 和 `file://`，Manifest 里登记的 `VIEW` 意图也只有这两种 scheme。远程 http 地址目前不能直接播。

**可行的做法**：`Song` 增加一种远程来源（地址是 http），播放器对 http 直接设置数据源；歌单用频道接口生成一组 `Song`，交给 `MusicService` 播放。这要改 `:retromusic` 的歌曲模型和播放器的数据源选择，不需要改它的界面。

**要确认的**
1. 歌单的界面用 `:retromusic` 的，还是频道自己的声音页？前者要把频道的收藏和字幕接进 `:retromusic` 的界面，工作量大；后者只换播放器。
2. 「只用 `:retromusic`」会让它同时承担本地音乐和频道歌单，两者的队列、通知、播放模式要分开，否则会互相打断。

建议：先用 `PlayService`（频道自己的）保持现状，等阶段 7 整理声音页时，再评估是否让它底层改用 `MusicService`。这一步不急。

---

## 8. 下一步

从阶段 1 开始：先删第 4 节的第三方库和死类，每批编译一次，在手机上冒烟。
