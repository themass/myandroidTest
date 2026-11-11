# 模块与目录总览、依赖关系图

基线：`weifu-refactor` @ `849f8146` 之后（第二轮重构完成）。规则见 [architecture.md](architecture.md) 第 3 节，由 [ModuleGraphTest.kt](../../app/src/test/java/com/openapi/app/architecture/ModuleGraphTest.kt) 强制。
本文是只读的现状记录，改结构时同步更新。

---

## 1. 依赖关系图

### 1.1 模块依赖（箭头 = 依赖，生成自各模块 `build.gradle`）

```
第 3 层    :app
           │
           ├─► 所有 feature（chat / content / video / user / photosolve / companion）
           ├─► :chatkit、:core、:common
           └─► :retromusic

第 2 层    :feature-chat      ─► :chatkit ─► :mediaview
                               ─► :speech
                               ─► :core  :common

           :feature-content   ─► :core  :speech  :common  :retromusic
           :feature-video     ─► :core  :cast  :speech  :common
           :feature-user      ─► :core  :common
           :feature-photosolve─► :imaging  :common
           :feature-companion ─► :core  :common

第 1 层    :core             ─► :speech  :common

第 0 层    :common            util/ event/ image/ net/ ui/ ads/（无项目依赖）
           :retromusic        （无项目依赖，独立）
```

**同层边（白名单只有 1 条）**：`chatkit → mediaview`。
**包内分区**：`:common` 里 `net/`、`ui/`、`ads/` 只依赖 `util/`、`event/`、`image/`，反过来不依赖（靠评审保证）。
**铁律**：feature 之间没有依赖；第 0、1 层不 import `com.openapi.core/feature/app`；声明的项目依赖必须用到。

### 1.2 第三方库（谁用谁声明）

| 库 | 声明的模块 | 说明 |
|---|---|---|
| OkHttp（api） | `:common` 的 `net/` | 对外接口里有 OkHttp 类型 |
| Gson（api） | `:common` | `GsonUtils` 对外返回 Gson 类型 |
| Glide（api） | `:common` 的 `net/glide` | 网络图片加载；`AppGlideModule` 在 `:app` |
| Compose BOM / UI / Material3 | `:common` 的 `ui/`、`:mediaview`、`:chatkit`、各 feature、`:app` | 用到的模块各自声明 |
| Coil | `:mediaview`、`:chatkit` | Compose 图片加载 |
| Media3 | `:speech`、`:core`、`:feature-video`、`:app` | 音频播放、实时字幕 PCM 源、视频页、播放引擎偏好 |
| ONNX Runtime、OpenCV | `:imaging` | 只此一家；`:feature-photosolve` 通过它使用 |
| NanoHTTPD | `:cast` | 投屏的本地 HTTP 分发 |
| GMS Ads（api）、Wallet | `:common` 的 `ads/` | 广告对外接口里有 GMS 类型 |
| Room（api） | `:core` | 实体和 DAO 是对外接口 |
| GSY（3 个包） | `:feature-video` | 按约束锁定版本、不改 |
| PhotoView、PictureSelector | `:feature-content` | 图片大图、选图 |
| Koin | `:feature-content` | `:retromusic` 的播放器服务需要 |
| Markwon（8 个包） | `:feature-chat` | 对话 Markdown / KaTeX |
| CameraX、ML Kit 扫描、Guava、easycrop | `:feature-photosolve` | 拍照与裁剪 |
| LiveKit | `:feature-companion` | 伴侣实时通道（该模块不动） |
| Google Cast framework | `:feature-video` | 投屏按钮与会话 |
| ZXing | `:common` 的 `ui/share` | 分享二维码 |
| Shimmer、RippleBackground | `:core`；Ripple 另在 `:app` | 骨架屏、启动雷达动画 |
| multidex、Material、Fragment、Activity | `:app` | 组合根 |

---

## 2. 模块清单与目录作用

行数 = 该模块 `src/main` 下 Java+Kotlin 源码行数（不含资源、测试）。

### 第 0 层：可单独拿走复用的基础模块

#### `:common` — `com.openapi.common`（106 文件 / 7714 行）
纯工具、网络、通用界面和广告合在一个模块里，用目录分开。**不依赖任何项目模块**。
依赖：协程、core-ktx；DocumentFile、ExifInterface；OkHttp/Gson/Glide（api，对外接口里有这些类型）；Compose、AppCompat、Activity、RecyclerView、ZXing；GMS Ads（api）、Wallet。

| 目录 | 文件 / 行 | 作用 |
|---|---|---|
| `util.lang` | 8 / 630 | `StringUtils`、`CollectionUtils`、`DateUtils`、`Md5` 等 |
| `util.android` | 15 / 1275 | `PreferenceUtils`、`ToastUtil`、`MediaUtil`、`ScreenSize`、`StaticDataUtil`、`FileUtils` 等 |
| `util.json` / `util.log` | 2 / 106 | `GsonUtils`、`LogUtil` |
| `event` | 1 / 43 | `EventFlows`：应用级事件流（`observe`、`observeJava`、`appScope`） |
| `image` | 2 / 208 | `ImageUtils`（EXIF 方向解码、缩放）、`ImageCompressor` |
| `net.http` | 10 / 1045 | `HttpClient`、`ApiRequest`、`Response`、`BaseService`、`NetConfig`、`NetError`、`NetUtils`、`NetResponse`、`HttpUtils`、`HttpDNSUtil` |
| `net.request` | 8 / 474 | `GsonRequest`（含 `Decoder`、`infoList`）、`MultipartRequest`、`StringRequest`、`BaseRequest`、`ApiResult`、`DataBuilder`、`InfoListVo` 等 |
| `net.glide` | 5 / 285 | Glide 网络组件：`OkHttpUrlLoader`、`OkHttpDns`、`MyGlideDiskCacheFactory`、`OkHttpStreamFetcher`、`GlideLoggingListener` |
| `net.dns` / `net.unsafe` | 2 / 86 | `DnsRequestInterceptor`、`UnsafeOkHttpClient` |
| `net` 根 | 2 / 195 | `BaseServiceAwait`（挂起封装）、`BaseServiceAsync`（CompletableFuture 桥接） |
| `ui.theme` | 2 / 146 | `AppMaterialTheme`、`Tokens`（颜色、间距、字号） |
| `ui.dialog` | 1 / 211 | `AppDialogs`、`AppDialogHost` |
| `ui.widget` | 8 / 760 | `SlidingLayout`、`CircleImageView`、`SiteAutoCompleteTextView`、`MarqueeTextView`、`MyEditText`、`MyScrollView`、`MyWebView`、`DividerItemDecoration` |
| `ui.permission` | 3 / 271 | `PermissionRequester`、`PermissionState`、`Permissions`（统一权限入口） |
| `ui.share` | 4 / 550 | `ShareKit`、`ShareSheet`、`ShareCard`（二维码）、`ShareKitFileProvider` |
| `ads.base` | 10 / 474 | `AdsContext`、`AdsManager`、`AdsPopStrategy` 等 |
| `ads.admob` / `ads.adview` | 11 / 765 | 两套广告 SDK 适配 |
| `ads.config` / `ads.analytics` | 4 / 128 | `AdsIds`、开关配置；`MobAgent` 统计 |
| `ads` 根 | 2 / 107 | `AdsEvents`、`AdsPopStrategy` 入口 |

**目录间规则（评审保证，测试不检查）**：`util/`、`event/`、`image/` 是最底层；`net/`、`ui/`、`ads/` 可以依赖它们，它们不反向依赖。

#### `:mediaview` — `com.openapi.mediaview`（2 文件 / 293 行）
媒体查看组件：`MediaCarousel`、`MediaOverlap`、`MediaComponents`。Compose + Coil，零项目依赖。

#### `:speech` — `com.openapi.speech`（31 文件 / 3170 行）
语音与字幕。识别、朗读、录音、翻译接口和实时字幕合成一个模块：实时字幕本来就是在识别之上组合翻译和覆盖层，拆开只会多一条依赖边。依赖：`:common`、Media3、OkHttp。

| 包 | 文件 | 作用 |
|---|---|---|
| `asr` | 5 | 火山引擎流式识别：`VolcengineStreamingAsrClient`、协议、配置、错误、响应解析（语音输入和实时字幕共用） |
| `tts` | 1 | `DeviceSpeech`（系统朗读） |
| `audio` | 2 | `AudioPlayer`（语音消息播放）、`VoiceRecorder`（录音） |
| `subtitle.api` | 10 | 翻译接口：`TranslationOverlaySession`、`VideoTranslationRequest/Registry/Provider/Result/Callback`、`StreamingSubtitleOverlayPresenter`、`TranslationOverlayPresenter` |
| `subtitle.overlay` | 1 | `DraggableTranslationOverlay`（可拖动的字幕覆盖层） |
| `subtitle.realtime` | 9 | 实时字幕：`RealtimeSubtitleEngine`、`VideoRealtimeSubtitleSession`、`RealtimeSubtitleSession`、PCM 音频源（Media3）、回调 |
| `subtitle` 根 | 3 | `DemoVideoTranslationProvider` 和两个 Stub 翻译提供者 |

#### `:imaging` — `com.openapi.imaging`（12 文件 / 839 行）
拍照图像处理，唯一带 ONNX Runtime + OpenCV 的模块。零项目依赖（只用 Android API）。

| 包 | 文件 | 作用 |
|---|---|---|
| `cropassist` | 5 | 文档/题目四角检测：`CropAssistProvider`、`CropAssistRegistry`、`CropAssistResult`、`DocQuadNetCropAssistProvider`（ONNX）、`ManualCropAssistProvider` |
| `image` | 4 | `ImagePipeline`、`RealEsrganUpscaler`、`SuperResTiler`、`TileGeometry` |
| `quality` | 2 | `QuestionImagePreparer`、`ImageQualityAnalyzer`（是不是题目、清晰度问题） |

#### `:cast` — `com.openapi.cast`（11 文件 / 735 行）
DLNA 投屏协议，与播放器和界面无关。依赖：协程、OkHttp、NanoHTTPD。

| 包 | 文件 | 作用 |
|---|---|---|
| 根 | 7 | `DlnaDiscovery`、`DlnaCaster`、`DlnaDevice`、`JavaCaster`、回调接口 |
| `soap` | 2 | AVTransport 控制、设备描述解析 |
| `ssdp` | 1 | SSDP 多播发现 |
| `http` | 1 | `LocalMediaServer`：把本地文件通过 HTTP 给电视 |

#### `:chatkit` — `com.openapi.chatkit`（22 文件 / 3727 行）
对话 UI 组件，与业务模型无关。依赖：`:mediaview`、Compose、Coil。

| 包 | 文件 | 作用 |
|---|---|---|
| `input` | 9 | `ChatComposer`、`ComposerParts`、`VoiceControls`、`VoiceRippleOverlay`、`VoiceBars`、`ComposerPanels` |
| `message` | 7 | `ChatMessageList`、`ChatMessageItem`、`ChatMenuTargets`、`MessageFooter`、`MessageSelection`、`AttachmentSheet` |
| `message.parts` | 2 | `MessageParts`（文本/图片/文件气泡）、`VoiceParts`（语音气泡、波形） |
| `media` | 1 | 媒体附件入口（接 `:mediaview`） |
| `model` | 2 | `Sender`、`ComposerModels` 等纯 UI 模型 |
| `theme` | 1 | Chatkit 主题 token |

#### `:retromusic` — `com.openapi.retromusic`（439 文件 / 57337 行）
本地音乐播放器（Media3、Room、Koin）。**按约束不动**：不拆包、不统一风格、不合并。只有 `:feature-content`（以及 `:app`）依赖它。

### 第 2 层：业务公共

#### `:core` — `com.openapi.core`（64 文件 / 5676 行）
应用配置、跨功能共享的业务数据、旧页面框架。依赖：`:common`（api）、`:speech`；Room（api）。

| 包 | 文件 | 作用 |
|---|---|---|
| `config` | 5 | `Constants`、`CommonConstants`、`Routes`、`BaseRes`、`Token` |
| `model` | 14 | 跨功能数据对象：`Message`、`HistoryVo`、`UserInfoVo`、`RecommendVo`、`SoundItemsVo`、`VersionVo` 等 |
| `db` | 9 | Room：`AppDatabase`（版本 3，迁移 1→2、2→3）、各 DAO、`Converters` |
| `event` | 4 | `CoreEvents`（登录失效、收藏变化、配置跳转、播放核心事件） |
| `favorite` | 5 | 收藏存储 `FavoriteStore`、`FavoriteVo`、`FavoriteUtil`、`MyFavoriteView`、`FavoriteTranslationController` |
| `account` | 1 | `UserLoginUtil`（登录状态、VIP） |
| `app` | 1 | `AppContext`（应用 Context 与 `UPDATE_STATUS` 动作） |
| `image` | 1 | `ImagePhotoLoad`（Glide 请求选项与加载入口） |
| `update` | 3 | `VersionUpdater`、`UpdateDialogs`、`ApkDownloader`（更新提醒偏好） |
| `legacyui.app` | 9 | 旧 Activity 基类：`BaseFragmentActivity`、`BaseBannerAdsActivity`、`BaseToolbarMenuActivity`、`LogActivity` 等 |
| `legacyui.base` | 3 | `CommonFragmentActivity`（按类名打开 Fragment）、`WebViewActivity`、`VideoWebViewActivity` |
| `legacyui.features` | 3 | `BasePullLoadbleFragment`、`LoadableFragment`、`Index服务` 基类（`CompletableFuture` 加载链） |
| `legacyui.adapter` | 1 | `BaseRecyclerViewAdapter` |
| `legacyui.list` | 2 | `ListShell` / `ListShellViews`（列表骨架、加载更多、错误重试） |
| `legacyui.channel` | 1 | `WebviewScreen` |
| `legacyui.inte` | 2 | 页面接口 |

### 第 3 层：功能模块

#### `:feature-chat` — `com.openapi.feature.chat`（68 文件 / 6622 行）
依赖：`:chatkit`、`:speech`、`:core`、`:common`。

| 包 | 文件 | 作用 |
|---|---|---|
| `presentation` | 15 | `ChatViewModel`（组合）+ 拆出的协作类：`HistoryActions`、`HistoryMerge`、`SendActions`、`SessionActions`、`VoiceActions`、`ComposerActions`、`MessageRemoval`、`ResendActions`、`ReplyStreamer`、`AttachmentSender/Stager`、`CharacterActions`、`RevealPacer`、`ChatUiState` |
| `presentation.ui` | 11 | `ChatScreen`、`ChatDialogs`、`EmptyChatView`、`RolePickerSheet`、`ClassicRoleList`、`ChatRoute` 等 |
| `data.remote` | 18 | `ChatRepositoryImpl` 组合：`ChatRemote`、`MessageCache`、`SessionStore`、`CharacterRemote`、`ChatStreamClient`、`AttachmentUploader`、`SpeechRemote`，以及请求/响应 VO |
| `data.mapper` | 1 | `ChatItemMapper` |
| `data.log` | 1 | `ConnLogUtil` |
| `domain.*` | 7 | 仓库接口、模型、鉴权、语音、用例 |
| `markdown` | 2 | `ChatMarkwon`（单一渲染入口）、`InputMsgWebView` |
| `markdown.katex` | 2 | `SingleDollarLatexPlugin`（单 `$` 行内公式）、`JLatexMathInlineProcessor` |
| `roles.data` | 6 | 角色目录：`BundledRoleCatalog`、`LocalRoleCatalog`、`RolesParser`、`RolesRepositoryImpl`、`RoleColors` |
| `roles.domain` / `roles.presentation` | 3 | 角色模型、`RoleCatalogPane`、`RolePickerViewModel` |

#### `:feature-content` — `com.openapi.feature.content`（77 文件 / 8300 行）
依赖：`:core`、`:speech`、`:common`、`:retromusic`。按内容类型分包。

| 包 | 文件 | 作用 |
|---|---|---|
| `video` | 8 | 视频频道列表页、全局视频搜索 |
| `image` | 9 | 图片频道、大图（PhotoView）、选图（PictureSelector） |
| `text` | 10 | 文字频道 |
| `sound` | 14 | 声音频道：`SoundChannleActivity`、`SoundItemsMusicFragment`、`PlayService`（频道播放，基于 `:retromusic`）、`SoundPlaybackControls`（已测）、`SoundPlaybackReceivers`、`MusicServiceConnection`、`PlayModeController`、`MusicSubtitleBindings`、`SoundDetailActivity` |
| `sound.compose` | 3 | 声音详情页 Compose：`SoundDetailScreen`、`SoundDetailParts` |
| `mine` | 6 | 收藏页、「我的」相关列表 |
| `common` | 17 | 频道页公共：`ChannelBodyScreen`、`ChannelItemCard`、`ChannelCardLoaders`、`ChannelListLayouts`、`ShowTypeHandle` 系列 |
| `common.touch` | 4 | 列表触摸辅助 |
| `config` | 2 | `ConfigActionJump`（服务端配置入口 → 页面类名路由） |
| `home` | 4 | 频道首页入口与依赖注入点 `ChannelsDependencies` |

#### `:feature-video` — `com.openapi.feature.video`（25 文件 / 3050 行）
依赖：`:core`、`:cast`、`:speech`、`:common`；GSY 3 个包、Cast framework。

| 包 | 文件 | 作用 |
|---|---|---|
| `page` | 6 | `VideoShowActivity`、`VideoShowActivityLazyUrl`、`VideoListShowActivity` |
| `player` | 6 | `DetailGSYVideoPlayer`（已拆出进度条、换内核、更多菜单、投屏按钮、刘海避让）、`CastButtonBinder`、`EngineFallback`、`PlayerMoreMenu` 等 |
| `cast` | 4 | Google Cast 接入：`AppCastOptionsProvider`、`CastFrameworkUtil`、`GoogleCastSessionController` |
| `list` | 3 | 视频列表适配器与 Fragment |
| `local` | 2 | `LocalVideoActivity`、`LocalVideoActions`（本地视频） |
| `subtitle` | 2 | 视频实时字幕接线（用 `:speech` 的 `subtitle.api`） |
| `data` | 1 | `VideoUtil` |

#### `:feature-user` — `com.openapi.feature.user`（22 文件 / 2649 行）
依赖：`:core`、`:common`。

| 包 | 文件 | 作用 |
|---|---|---|
| `login` / `register` / `password` / `profile` | 4 | 账户页面 |
| `setting` | 4 | 设置页：`SettingActivity`、`SettingRows`、设置项适配器与 Fragment |
| `feedback` | 2 | 反馈页：`FeedbackActivity`、`FeedbackCards` |
| `data.form` / `data.vo` / `data.task` / `data.urlparser` | 9 | 表单、VO、登录任务、URL 解析器 |
| `home` | 3 | 「我的」页入口与依赖注入点 `ProfileDependencies` |

#### `:feature-photosolve` — `com.openapi.feature.photosolve`（18 文件 / 2111 行）
依赖：`:imaging`、`:common`；CameraX、ML Kit、Guava、easycrop。

| 包 | 文件 | 作用 |
|---|---|---|
| `ui` | 12 | `PhotoSolveActivity`、`PhotoCaptureScreen`、`PhotoCropScreen` 及拆出的 `CropOverlay`、`CropBitmapOps`、`CropChrome`、`CropAssistSettingsSheet`、`CropAssistComparisonScreen`、`PhotoQualityDialog`、`MlKitScanLauncher` |
| `presentation` | 2 | `PhotoSolveViewModel`、`PhotoSolveUiState` |
| `data` | 3 | `HttpPhotoSolveRepository`（服务端调用） |
| 根 | 1 | `PhotoSolveActivity` 入口（旧 Activity，保留） |

#### `:feature-companion` — `com.openapi.ks.myapp.companion`（15 文件 / 2274 行）
依赖：`:core`、`:common`；LiveKit。**该模块在自己的分支开发，本轮不动**，包名保持旧形式。

**未完成事项**：`com.openapi.ks.myapp.companion` 与 `com.openapi.ks.myapp.avatar` 尚未改成 `com.openapi.feature.companion`；改的时候要同步 `ChatRoute` 里的组件名和 Manifest。

### 第 4 层：组合根

#### `:app` — `com.openapi.app`（15 文件 / 1285 行）
依赖：全部 feature、全部可复用模块、`:retromusic`；Glide 注解处理器（`AppGlideConfig`）。

| 包 | 文件 | 作用 |
|---|---|---|
| 根 | 3 | `MyApplication`、`LaunchActivity`、`AppGlideConfig`（唯一的 `AppGlideModule`） |
| `home.presentation` | 3 | `HomeScreen`、`HomeDrawer`、`HomeActivity`、`HomeDependencies` |
| `home.domain` | 1 | `HomeEntries` 等组合接口 |
| `data.home` | 3 | `LegacyHomeEntries` 及其两半（频道 / 我的） |
| `data.chat` | 1 | 对话入口装配 |
| `view` | 1 | `MyRadarScanView`（启动动画） |
| `data` | 1 | `AppEventHandler` |

---

## 3. 被验证过的约束

| 约束 | 验证方式 | 结果 |
|---|---|---|
| 分层、同层白名单 | `ModuleGraphTest.layeringRulesHold` | 通过 |
| 包名 = 模块根包 | `ModuleGraphTest.packagesMatchModules` | 通过（`:retromusic`、`:feature-companion` 例外） |
| 第 0、1 层不 import 业务代码 | `ModuleGraphTest.reusableModulesDoNotImportBusinessCode` | 通过 |
| 声明的项目依赖必须用到 | `ModuleGraphTest.declaredProjectDependenciesAreUsed` | 通过 |
| `:chatkit` / `:common` 不再带重库 | `:chatkit:dependencies`、`:common:dependencies` | `:chatkit` 只剩 Coil 自带的 OkHttp；`:common` 只有 6 个小库 |
