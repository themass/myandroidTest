# 整理计划：一个包一个包地改

目标结构和判断规则见 [architecture.md](architecture.md)。本文是执行计划。
数据基于 `weifu-refactor`（2026-11-10）：本项目自己的代码 461 个文件、53,463 行（不含 `:retromusic`、`:auxio_music_ui`），其中 Java 319 个。
统计用的脚本在 `/tmp/modscan/`。文本扫描会误报，**每个结论都以编译为准**。

---

## 0. 开工前要你决定的

| # | 问题 | 影响 | 我的建议 |
|---|---|---|---|
| 1 | **视频播放引擎**：现在 GSY 带 4 个引擎（Exo2、IJK/FFmpeg、阿里、系统），设置页能切换。改成只用 Media3，会失去 IJK 对一些冷门格式（如部分 rmvb、flv 编码）的支持 | 决定视频播放器是重写还是保留 | 只用 Media3。项目里 Media3 已经在用；GSY 和 IJK 的原生库占 APK 体积大头 |
| 2 | **旧页面要不要重写成 Compose**：`:feature-content` 的 14 个 Java Fragment、8 个 Activity，`:feature-video` 的 6 个 Activity | 决定 `:core/legacyui`（19 个基类）能不能删 | 重写，但放在后面做（第 7 阶段），一种内容一种内容地换 |
| 3 | **两个 GPL 音乐模块**：`:retromusic`（57,302 行，比本项目其余代码加起来还多）、`:auxio_music_ui`（1,683 行）都是 GPL-3.0。它们和频道里自己写的音乐页（`SoundItemsMusicFragment`）是三套音乐界面 | GPL-3.0 有传染性：发布带这些模块的 APK，可能要求整个应用按 GPL 开源。`retromusic/build.gradle` 里也写着「只能自用」 | 请你先确认发布方式是否允许。如果不允许，用 Media3 写一个简单的本地音乐页替换，删掉两个模块 |
| 4 | 旧页面框架暂留 `:core/legacyui`；对话的 Room 实体留 `:core/db`（另一种做法是数据库整体移到 `:app`） | `:core` 的拆法 | 两个都按「暂留」做 |

---

## 1. 方法

### 1.1 每个包都走这 6 步
1. **盘点**：列出每个文件、行数、谁在用。
2. **删**：没人用的类、方法、资源、依赖。以编译为准。
3. **移出**：不属于这里的，按 architecture.md 第 2 节的种类，移到它该在的模块。
4. **审视大类**：超过 400 行，或者一个类管好几件事的，写一个拆分或重写方案（第 5 节）。
5. **执行**：一个包一个提交。
6. **验证**：`:app:assembleChatDebug` 编译；`:app:testChatDebugUnitTest` 单元测试；手机上走一遍这个包涉及的页面，截图存证。

### 1.2 重构还是重写
| 情况 | 做法 |
|---|---|
| 逻辑复杂、业务核心、最近还在修 bug（如对话） | **原地重构**：先拆成几个类，行为不变，每拆一块验证一次 |
| 技术陈旧（Java View、GSY、MediaPlayer），功能边界清楚 | **重写**：按下面 1.3 的步骤 |
| 已经是 Compose/Kotlin、结构清楚 | **只搬家**，不改 |
| 上游都要被重写 | **只搬家，不打磨**。例如 `legacyui` 基类：等用它的页面重写完，直接删 |

### 1.3 重写的步骤（先写新的，再删旧的）
1. **写清旧功能**：页面上有什么、能做什么、调哪些接口、有哪些边界情况。写进本文对应的小节，作为验收标准。
2. **写新实现**：放在目标位置，用目标技术（Compose、协程、Media3）。旧代码不动。
3. **切换入口**：路由改指向新页面。
4. **手机验证**：按第 1 步的清单逐项对，截图。
5. **删旧代码**：旧类、只有它用的资源、布局、依赖一起删。
不保留新旧切换开关，回退用 git。

### 1.4 顺序原则
- **从低风险到高风险**：先删，再搬，再重构，最后重写。
- **不打磨要被替换的东西**：重写之前，旧代码只搬家，不优化。
- **包名最后统一改**：搬文件时包名不变，所以 import 不用改；最后一次性改包名。
- **和 UI 会话错开**：UI 会话在 `weifu-refactor` 上改对话和 `:chatkit` 的界面。动这两处之前先确认它空闲，避免冲突。

---

## 2. 阶段

| 阶段 | 内容 | 风险 | 预计提交数 |
|---|---|---|---|
| 1 | **全局清理**：删无用的第三方库、资源、assets、死类；拆 Manifest（各模块登记自己的 Activity） | 低 | 6–8 |
| 2 | **`:common`**：`:commonutil` 按 `util/ net/ ui/` 整理，业务类上移 `:core`，并入 `:sharekit`，去重 | 中 | 8–10 |
| 3 | **`:media`**：建模块，并入 `:asr`、`:mediaview`，移入图像、字幕翻译、录音播放 | 中 | 4–5 |
| 4 | **`:core`**：单一使用方的类下沉，内部按 `config/ model/ db/ event/ legacyui/` 整理 | 中 | 5–6 |
| 5 | **feature 逐个整理**：对话 → 用户 → 拍照 → 伴侣（不动）。对话的大类按第 5 节拆 | 中 | 每个 3–6 |
| 6 | **视频重写**（看第 0 节第 1 条的决定）：播放器换 Media3，删 GSY | 高 | 4–6 |
| 7 | **内容频道重写**（看第 0 节第 2、3 条的决定）：一种内容一种内容地换成 Compose；音乐页换 Media3；最后删 `legacyui` | 高 | 10–15 |
| 8 | **`:app` 瘦身**：实现类回到 feature，频道列表和「我的」移出，去掉 `AppHooks` | 中 | 4–5 |
| 9 | **收尾**：删 EventBus；统一包名；加架构测试 | 中 | 3–4 |

阶段 1 到 4 是**搬和删**，每个包改完功能不变。阶段 5 到 8 才会改行为。

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
| `~.ui.base`、`~.ui.base.app`、`~.ui.base.features` | 17/2504 | 留 `legacyui/`，**只搬不改**。`VideoWebViewActivity`、`BaseBannerAdsActivity` 只有 `:app` 直接用，但被继承，一起留。阶段 7 后删 |
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
| `~.ui.user` | 5/1332 | 留，按 `login/ register/ password/ profile/ setting/` 分子包。登录、注册、找回密码、设置已经是 Compose。`RegisterScreens.kt` 改名为 `RegActivity.kt`（文件名和类名不一致）。`AddCustomeInfoActivity` 是 Java View，**重写**为 Compose |
| `~.ui.feedback` | 1/482 | 留 `feedback/` |
| `~.ui.fragment`、`~.adapter` | 2/177 | 设置页的角色设置，Java Fragment + Adapter，**重写**为 Compose，放进 `setting/` |
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

### 3.10 `:feature-video`（阶段 6，看第 0 节第 1 条）

| 包 | 文件/行 | 处理 |
|---|---|---|
| `~.ui.gsy` | 4/1138 | **重写**：`DetailGSYVideoPlayer`（721 行）换成 Media3 播放器，见第 5.3 节 |
| `~.video` | 1/151 | `PlaybackEngineHelper`：只用 Media3 后删除；偏好移 `:core/prefs` |
| `~.ui.sound` | 6/777 | 移 `page/`（包名叫 sound，里面全是视频页）。Java View，随播放器一起**重写**。`VideoActivity` 只在 Manifest 登记、代码里没有入口，**删**（待确认） |
| `~.ui.localvideo` | 1/305 | 留 `local/`（已是 Compose） |
| `~.cast` | 4/267 | 留 `cast/googlecast/`；DLNA 从 `:core` 移来 `cast/dlna/` |
| `~.service` | 1/496 | `PlayService` 是**音乐**播放服务，移 `:feature-content/sound`（阶段 7 重写） |
| `~.translation` | 4/116 | 3 个翻译提供者移 `:media/subtitle/provider`；`VideoTranslationInstaller` 移 `:app` |
| `~.translation.realtime` | 10/1187 | 引擎和 7 个 PCM 音频源移 `:media/subtitle/realtime`；`RealtimeSubtitleBindings` 拆：视频部分留，音乐部分移 `:feature-content/sound` |
| `~.data` | 1/82 | 留 |

### 3.11 `:feature-channel` → `:feature-content`（阶段 7，看第 0 节第 2、3 条）

现在 39 个文件、约 6,000 行；从 `:core` 下沉 20 个后约 59 个。子包见 architecture.md 和下表。

| 现包 | 文件/行 | 去向 | 处理 |
|---|---|---|---|
| `~.ui.fragment` | 15/2686 | `video/ image/ text/ sound/ mine/` | 14 个 Java Fragment，**重写**为 Compose。`SoundItemsMusicFragment` 675 行，见第 5.4 节 |
| `~.ui.fragment.body` | 5/301 | 各类型子包 | 随所属类型一起重写 |
| `~.ui.sound` | 8/791 | 各类型子包 | 7 个频道外壳 Activity，重写后合并成一个带参数的入口。`TeleplayActivity` 标题是「电视剧」，实际显示的是图片频道，**确认后删** |
| `~.ui.sound.compose` | 2/343 | `sound/` | 留（已是 Compose） |
| `~.ui.channel` | 3/730 | `common/` | 留（已是 Compose）。`ChannelBodyScreen` 479 行，审视 |
| `~.adapter` | 4/432 | 各类型子包 | RecyclerView 适配器，重写为 Compose 后删 |
| `~.auxio` | 1/60 | `sound/` | 看第 0 节第 3 条 |
| `~.ui.config` | 1/47 | `config/` | `QuickBrowserConfigActivity` |

**重写顺序**：文字（最简单，3 个页面）→ 图片 → 视频频道列表 → 声音（最复杂，含播放服务）→ 收藏等「我的」页 → 删 `legacyui`。

### 3.12 `:app`（阶段 1、8）

| 方面 | 处理 | 阶段 |
|---|---|---|
| 第三方库 | 声明了约 60 个，`:app` 自己只用 multidex、Media3。没有任何模块使用的（navigation、percentlayout、viewpager2、rxjava、webkit 等）删；其余移到使用它的模块 | 1 |
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

### 5.3 视频播放器（`DetailGSYVideoPlayer` 721 行 + 5 个视频页 Activity）→ 重写为 Media3

**先写清旧功能**（重写前补全）：全屏、小窗、倍速、投屏（Google Cast + DLNA）、收藏、实时字幕与翻译、设置入口、续播、Exo 缓存、列表小窗播放。
**新实现**：一个 Compose 播放页 + 一个 `VideoPlayerController`（封装 ExoPlayer），投屏和字幕作为独立组件挂上去。
**删除**：GSY 全部（含 IJK、阿里播放器、ex_so 原生库）、`PlaybackEngineHelper`、设置页的引擎选择、5 个旧 Activity。
**前提**：第 0 节第 1 条你同意只用 Media3。

### 5.4 音乐（`SoundItemsMusicFragment` 675 行 + `SoundDetailActivity` 339 行 + `PlayService` 496 行）→ 重写

现在一个 Fragment 同时管列表、播放服务绑定（15 处）、收藏（19 处）、字幕翻译（22 处）、进度条、切换 Auxio 界面。
**新实现**：`MusicPlaybackService`（Media3 `MediaSessionService`，支持通知栏和锁屏控制）+ `MusicPlayerController` + Compose 的列表页和详情页。收藏、字幕作为组件挂上去。
**删除**：`PlayService`（`MediaPlayer`）、两个旧页面、3 个适配器；按第 0 节第 3 条的决定，可能连同 `:auxio_music_ui`、`:retromusic`。

### 5.5 公式渲染（11 个文件、1392 行）→ 先比较再决定

写清自定义版本比 Markwon 官方 `ext-latex` 多了什么（`MyJLatexMathPlugin` 547 行、`JLatexMathTheme` 363 行）。差别小就换官方插件，删掉 11 个文件。

### 5.6 工具类瘦身（阶段 2）

| 类 | 行数 | 外部实际使用 | 处理 |
|---|---|---|---|
| `widget/util/FileUtils` | 1244 | 0 | 删 |
| `ObjectUtils` | 901 | 3 个方法 | 留 3 个，其余删 |
| `ModelUtils` | 509 | 2 个方法 | 留 2 个 |
| `FileUtils` | 468 | 8 个方法 | 留 8 个 |
| `StringUtils` | 313 | 3 个方法 | 留 3 个 |
| `CompatiblePermissionManager` 等 3 个 | 848 | 只有本模块的 `PermissionHelper` | 合并进一套权限后删 |

### 5.7 `VersionUpdater`（332 行）→ 重构

检查更新、下载、两种弹窗、发 `StateUseEvent` 和 `VipDescEvent` 混在一起。拆成「检查版本」（挂起函数，返回结果）和「更新弹窗」（UI），版本里带的状态和 VIP 说明改为写进 `:core/account`。

---

## 6. 技术升级的落点

| 升级 | 在哪个阶段做 | 完成标志 |
|---|---|---|
| EventBus → Flow | 每个事件在它所属的包整理时换；阶段 9 删依赖 | `EventBusUtil` 删除 |
| GSY → Media3 | 阶段 6 | GSY、IJK、阿里播放器依赖删除 |
| `MediaPlayer` → Media3 | 阶段 7 | `PlayService` 删除 |
| View/Fragment → Compose | 阶段 5（用户）、6（视频）、7（内容） | `:core/legacyui` 删除 |
| 回调 → 挂起函数 | 阶段 2 在 `:common/net` 提供；调用方随所在包整理时换 | `NetQueue` 的回调接口没有调用方 |
| Java → Kotlin | 改到哪个文件转哪个 | 不设完成标志 |
| `Serializable` → `Parcelable` 或只传 id | 随页面重写 | — |

---

## 7. 验证和提交

- **每个包一个提交**，提交信息写清：删了什么、移了什么、改了什么行为、手机上验证了哪些页面。
- **每个提交**：`:app:assembleChatDebug` 通过；`:app:testChatDebugUnitTest` 通过；涉及的页面在手机上走一遍并截图。
- **每个阶段结束**：完整冒烟（启动、对话收发、拍照解题、视频播放、频道浏览、登录、设置），然后合入 `weifu-refactor`。
- **阶段 9 加架构测试**：检查 architecture.md 第 3.1、3.2 节的依赖规则，防止以后又乱。
- 只在一个分支上持续做，不开多个分支。

---

## 8. 下一步

1. 你回答第 0 节的 4 个问题。
2. 我从阶段 1 开始：先删第 4 节的第三方库和死类，每批编译一次，在手机上冒烟。
