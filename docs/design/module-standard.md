# 模块标准与归属设计（第 5 版）

状态：**设计已基本确认，代码未动**。基于 `weifu-refactor` 的 `debdf79f`。
数据来源：按文件的 import 归类（`/tmp/deadscan/classify.py`），按类名统计使用方（`/tmp/clsusers.py`）。脚本有误判，每一步搬迁都以编译为准。
`:core` 的逐文件拆分见 [core-split-plan.md](core-split-plan.md)。

第 5 版相对第 3 版的变化：
- `:media` 不再收投屏；投屏放回 `:feature-video`。`:media` 改为语音、字幕翻译、图像、媒体查看 4 个子包。
- 视频和频道**不合并**。两者之间的依赖通过搬对位置和路由去掉。
- `:feature-channel` 改名为 `:feature-content`（内容），内部按内容类型分子包（第 5 版新增）。注意：第 3 版里的 `:feature-content` 指「视频 + 频道合并」，第 5 版只指原来的频道模块。
- 写入 `:core` 的定义。

---

## 0. 现在乱在哪里

| 问题 | 事实 |
|---|---|
| 工具、视图、网络不分 | `:commonutil` 同时有 41 个工具类（含缓存、权限）、23 个自定义 View、10 个字幕翻译类、27 个网络类、9 个布局 |
| 底层反向依赖业务 | `LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 都读 `CommonConstants`（里面是接口域名、Token 头名）；`BaseRequest` 直接发登录事件 `UserNeedLogin`；收藏的数据库实体 `FavoriteVo` 放在工具层 |
| 底层用 `api` 暴露十几个重库 | GSY 播放器（带原生 so）、PictureSelector、Room、Glide、PhotoView 等。`:asr`、`:ads` 这种小模块也被动带上了播放器。其中 7 个库没人用 |
| 视图散在 6 处 | `:commonutil/view`、`:core/design`、`:core/view`、`:core/ui/gsy/compose`、`:core/ui/channel`、`:mediaview` |
| 同一件事多份实现 | 权限 3 套、`FileUtils` 2 份、屏幕尺寸 3 处（`DensityUtil`、`DisplayUtil`、`Utils`）、分享 2 套（`ShareUtil`、`:sharekit`） |
| 模块太碎 | `:sharekit` 1 个文件，`:mediaview` 2 个，`:asr` 6 个，`:feature-roles` 7 个 |
| `:core` 没有定义 | 130 个文件，约 80 个只有一个模块用 |
| feature 互相依赖 | `:feature-channel → :feature-user, :feature-video`；`:feature-user → :feature-video`；`:feature-chat → :feature-roles, :feature-video` |
| 死代码 | `util/cache/` 4 个磁盘缓存类在模块外没有使用方 |

---

## 1. 先分种类：每个类必须是下面 6 种之一

种类决定**放进哪个模块的哪个子包**。

| 种类 | 定义 | 判断标准 | 放到 |
|---|---|---|---|
| **A. 纯工具** | 无状态的辅助函数 | 不认识业务名词；不画界面；不发网络请求 | `:common/util` |
| **B. 网络** | HTTP 收发和图片加载的传输层 | 依赖 OkHttp、Volley、Glide 的网络部分 | `:common/net` |
| **C. 通用视图** | 与业务无关的 UI 部件 | View、Compose 组件、主题、通用对话框，换个 App 也能用，不带重库 | `:common/ui` |
| **D. 独立能力** | 自成体系、与业务无关的一整块功能 | 有自己的对外接口；或者带重库、模型 | `:media`、`:chatkit`、`:ads` |
| **E. 业务公共** | 多个功能共用的业务概念 | 含业务名词或依赖接口配置，**并且**至少两个 feature 用 | `:core` |
| **F. 功能专属** | 只服务一个功能 | 只有一个模块用 | 那个模块 |

**判定顺序**（从上往下，命中即停）：
1. 只有一个模块用？ → **F**。
2. 含业务名词或依赖业务配置？ → **E**。
3. 是独立能力？ → **D**。
4. 是界面？ → **C**。
5. 是网络传输？ → **B**。
6. 剩下的 → **A**。

---

## 2. 什么时候单独建模块

**默认用子包。** 只有满足下面任一条，才单独建模块：
1. **带重的第三方库或原生库**，不想让不用它的模块也带上（Media3、ONNX、LiveKit、广告 SDK）。
2. **构建边界有意义**：需要强制「别人不能反向依赖它」。
3. **独立的开发分支**：例如角色扮演的 Android 端单独维护。

子包之间的依赖规则 Gradle 不检查，靠架构测试检查（第 6 节第 2 步）。

---

## 3. 模块结构（12 个）

```
第 4 层  :app                    装配
第 3 层  :feature-chat           对话（含 roles/ 子包）
         :feature-video          视频
         :feature-content        内容：视频/图片/文字/声音频道、收藏、推荐、捐赠（原 :feature-channel）
         :feature-user           账户与设置
         :feature-photosolve     拍照解题
         :feature-companion      角色扮演伴侣
第 2 层  :core                   应用配置 + 共享业务数据 + 旧页面框架
第 1 层  :media                  speech/ subtitle/ imaging/ viewer/
         :chatkit                对话 UI 组件
         :ads                    广告
第 0 层  :common                 util/ net/ ui/
```

### 模块间的依赖规则
1. 只能依赖下层。
2. 同层模块不互相依赖。例外：`:chatkit` 可以依赖 `:media`（用 `viewer/`）。
3. **第 0、1 层不能依赖 `:core`**。业务配置、业务事件只能从上往下传参或回调。
4. **feature 之间不互相依赖**。需要打开别的功能的页面时，用路由（Intent action）。
5. **第三方库谁用谁声明**，用 `implementation`。只有对外接口里出现了库的类型，才用 `api`。

### 模块内子包的依赖规则
| 模块 | 规则 |
|---|---|
| `:common` | `util/` 不能 import `net/`、`ui/`；`net/` 和 `ui/` 只能 import `util/`，彼此不 import |
| `:media` | `subtitle/` 可以 import `speech/`（实时字幕用识别）；其余子包互不 import |
| `:feature-chat` | `roles/` 不能 import 对话的其它子包 |

---

### `:common` `com.openapi.common`（由 `:commonutil` 改造，并入 `:sharekit`）
```
util/
  lang/       纯 JVM：StringUtils、DateUtils、CollectionUtils、ObjectUtils、Md5、AES2、VersionCompare
  android/    需要 Context：PreferenceUtils、FileUtils（合并两份）、PathUtil、PackageUtils、DeviceInfoUtils、
              ScreenSize（合并 DensityUtil、DisplayUtil、Utils 的屏幕尺寸）、ToastUtil、AudioMngHelper、Background
  share/      分享（合并 :sharekit 的 ShareKit 与 ShareUtil）
  log/        LogUtil（DEBUG 开关由 :app 启动时传入，不再读 CommonConstants）
  json/       GsonUtils
  event/      EventBusUtil（只放总线封装，不放具体事件类）
net/
  http/       NetQueue、NetRequest、NetResponse、Response、SyncFuture、BaseService、HttpUtils、NetUtils、NetError
  request/    BaseRequest、GsonRequest、MultipartRequest、StringRequest、CommonResponse、JsonResult、InfoListVo
  dns/        HttpDNSUtil、DnsRequestInterceptor、OkHttpDns
  glide/      MyGlideModule、OkHttpUrlLoader、OkHttpStreamFetcher、MyGlideDiskCacheFactory
  unsafe/     UnsafeOkHttpClient（只搬位置，不改行为）
ui/
  theme/      AppMaterialTheme、Tokens（从 :core 移来）
  dialog/     AppDialogs（从 :core 移来）
  image/      通用图片加载（从 :core 的 ImagePhotoLoad 拆出 loadCommonImg、options）
  widget/     CircleImageView、StateButton、IndicatorView、MarqueeTextView、MyEditText、MyScrollView、
              SlidingLayout、MyWebView、SiteAutoCompleteTextView
  recycler/   DividerItemDecoration、touchhelper/*
  permission/ 权限请求（合并 :commonutil 两套和 :core 一套）
res/          通用布局（footer、layout_progress）和通用图标
```
**不放**：业务事件、接口地址、收藏视图、字幕覆盖层、带 Media3 的组件。
**要改的耦合**：`BaseRequest` 收到「需要登录」的响应时，改为调用回调 `AuthFailureListener`，由 `:core` 注册后再发业务事件；请求头里的 Token 名、域名改为启动时注入。
**第三方库**：Gson、EventBus、OkHttp、Glide、Material、Compose、RecyclerView、Shimmer、cardview。

### `:media` `com.openapi.media`（并入 `:asr`、`:mediaview`）
```
speech/
  asr/        火山引擎流式识别（原 :asr）
  tts/        设备朗读（原 :asr 的 tts/）
  record/     VoiceRecorder（从 :core 移来）
  play/       AudioPlayer（从 :core 移来）
subtitle/     字幕与翻译框架，视频和音乐共用
  api/        VideoTranslationProvider、VideoTranslationRegistry、VideoTranslationRequest、VideoTranslationResult、
              VideoTranslationCallback、TranslationOverlaySession、RealtimeSubtitleSession、RealtimeSubtitleCallbacks
              （从 :commonutil/view/translation 移来）
  overlay/    TranslationOverlayPresenter、StreamingSubtitleOverlayPresenter、DraggableTranslationOverlay
  realtime/   RealtimeSubtitleEngine、VideoRealtimeSubtitleSession、PcmAudioSource 及 6 个 PCM 实现
              （从 :feature-video 移来）
imaging/      图像处理（从 :core 移来）
  compress/   ImageCompressor
  pipeline/   ImagePipeline、ImageUploadEncoder、ImageUtils
  quality/    ImageQualityAnalyzer、QuestionImagePreparer
  crop/       CropNorm、CropAssist*（DocQuadNet 模型）
  superres/   RealEsrganUpscaler、SuperResTiler、TileGeometry
viewer/       MediaStack、MediaCarousel、MediaOverlap、MediaVideoDialog（原 :mediaview）
```
**为什么是这 4 块**：都是处理音频、图片、视频的能力，和业务无关，并且每块都有两个以上使用方：

| 子包 | 使用方 |
|---|---|
| `speech/` | 对话、`:app`、`subtitle/` |
| `subtitle/` | 视频、频道（声音页） |
| `imaging/` | 拍照、对话、`:app` |
| `viewer/` | `:chatkit`、频道（图片列表，UI 会话计划中） |

**为什么 `viewer/` 不放 `:common`**：`MediaVideoDialog` 用 Media3 播放视频。放进 `:common` 会让所有模块都带上 Media3。
**第三方库**：Media3、ONNX Runtime、OkHttp（识别用 WebSocket）。
**之前同意的 `:asr` 改名 `:speech`**：改为 `:media` 里的 `speech/` 子包。

### `:chatkit` `com.openapi.chatkit`
不变。对话 UI 组件库（消息列表、气泡、输入栏、录音浮层、附件面板），16 个文件，依赖 `:media/viewer`。
**为什么不并进 `:common/ui`**：它是一整套对话界面，有自己的数据模型（`ChatItem`），只有对话用。

### `:ads`
不变。两套广告 SDK 的封装，27 个文件。

### `:core` `com.openapi.core`
**定义**：应用配置 + 多个功能共享的业务数据 + 旧页面框架。
**准入**：至少两个 feature 用，**并且**含业务名词或依赖接口配置。只有一个模块用的，下沉到那个模块。
```
config/     Constants、CommonConstants（合并）、BaseRes、Token
app/        AppContext、VersionUpdater
account/    VIP、登录状态（取代 AppHooks.isVip）
model/      RecommendVo、SoundItemsVo、CharacterVo、NullReturnVo、CustomeAddForm 等
db/         AppDatabase、DAO、Room 实体（含收藏实体 FavoriteVo，从 :commonutil 移来）
event/      业务事件（含从 :commonutil 移来的 UserNeedLogin、FavoriteChangeEvent、ConfigActionEvent、PermEvent）
favorite/   FavoriteStore、FavoriteUtil、MyFavoriteView、FavoriteImageView（从 :commonutil 移来）
prefs/      跨功能的偏好项，例如播放引擎选择（取代 :feature-user 对 PlaybackEngineHelper 的依赖）
legacyui/   View 体系的 Activity/Fragment 基类，新代码不用；旧页面换成 Compose 后一起删
```
130 个现有文件的逐个去向见 [core-split-plan.md](core-split-plan.md)：留 49 个，移出 79 个，删 1 个，拆 1 个。

### feature 模块
| 模块 | 内容 | 变化 |
|---|---|---|
| `:feature-chat` | 对话 + `roles/` 子包 | 并入 `:feature-roles`；删除对 `:feature-video` 的依赖（源码里没有使用） |
| `:feature-video` | 播放器（GSY、Media3）、视频列表与视频页、本地视频、投屏（DLNA + Google Cast）、`RealtimeSubtitleBindings` 的视频部分 | DLNA 从 `:core` 移来；字幕引擎移到 `:media/subtitle`；`PlayService` 移到频道 |
| `:feature-content` | 内容频道（视频、图片、文字、声音）、收藏、应用推荐、捐赠、快捷浏览配置。子包见下 | 由 `:feature-channel` 改名；不再依赖 `:feature-video`、`:feature-user` |
| `:feature-user` | 登录、注册、设置、反馈 | 不再依赖 `:feature-video` |
| `:feature-photosolve` | 拍照解题流程 | 不变 |
| `:feature-companion` | 角色扮演伴侣 | 不变（Android 端按约定在自己的分支开发） |

#### `:feature-content` 的子包（由 `:feature-channel` 改名）

现在 39 个文件、约 6000 行，基本是 View 体系，只有频道主体和声音详情用了 Compose。入口：侧边栏「频道」→ `:app` 的频道列表 → `ConfigActionJump` 按 URL 类型打开频道页；「我的」页面的收藏、应用推荐、捐赠也进这里。加上从 `:core` 下沉的 20 个文件，共约 59 个。

**为什么不按内容类型拆成多个模块**：视频、图片、文字、声音频道都继承同一套列表框架（`RecommendFragment` 被 8 个类继承，频道主体被 4 个类用）。拆开就要把框架放进 `:core`，或者复制多份。

```
com.openapi.feature.content
  common/      列表框架：RecommendFragment、ChannelBodyFragment、ChannelBodyController、ChannelBodyScreen
               从 :core 下沉：IndexRecommendAdapter、BasePhotoFlowRecycleViewAdapter、showtype/ 6 个、
               ImagePhotoLoad.loadPhoto、HistoryUtil（图片、文字都用）、ImgItemsVo、TextItemsVo（收藏也用）
  video/       VideoChannleActivity、MovieChannleActivity、VideoChannleBodyFragment、MovieChannleBodyFragment、
               VideoChannelListFragment、VideoChannelUserListFragment、VideoChannelUserItemsListFragment、
               GlobalVideoChannelListFragment（全局视频搜索）。点进去用路由打开 :feature-video 的播放页
  image/       ImgChannleActivity、ImgChannleBodyFragment、ImgChannelImgListFragment、ImgItemFragment、
               MyFullScreenImageGalleryActivity、FullScreenImagePagerAdapter、TeleplayActivity（见下）
               从 :core 下沉：SaveImageTask、SaveImageCallBack、DelImageTask、OnBackKeyDownListener
  text/        TextChannleActivity、TextChannleBodyFragment、TextChannelListFragment、TextItemsFragment、
               TextItemsWebViewFragment、TextChannelListItemsViewAdapter
               从 :core 下沉：SaveTextTask、TextItemVo、MenuOneContext
  sound/       SoundChannleActivity、SoundChannleBodyFragment、SoundItemsMusicFragment、SoundDetailActivity、
               SoundDetailScreen、SoundDetailUiState、SoundItemsViewMusicAdapter、AuxioMusicUiLauncher
               从 :feature-video 移来：PlayService（音乐播放服务）、RealtimeSubtitleBindings 的音乐部分
               从 :commonutil 移来：MusicVisualizer；从 :core 下沉：MusicStateListener
  mine/        FavoriteFragment、FavoriteScreen、AppListFragment、AppRecommendViewAdapter、DonationListFragment
               从 :core 下沉：AppInfo
  config/      QuickBrowserConfigActivity（快捷浏览配置）
```

**子包规则**（已用脚本按现有代码核对，无违反）：
- `video/`、`image/`、`text/`、`sound/`、`config/` 只能 import `common/`，彼此不 import。
- `mine/` 可以 import 各类型子包：收藏页要打开对应类型的详情页（图片、文字、音乐），捐赠页要打开全屏看图。类型子包不能 import `mine/`。

**发现的可疑点**：`TeleplayActivity`（标题是「电视剧」，入口 `Constants.TELEPLAY_URL`）实际显示的是图片频道的主体 `ImgChannleBodyFragment`，像是复制粘贴遗留。暂时按实际行为放进 `image/`，需要确认这个入口是否还在用。

**去掉 feature 依赖的具体做法**：

| 依赖 | 现在用到的 | 处理 |
|---|---|---|
| 频道 → 视频 | `PlayService` | 它是**音乐**播放服务，视频模块自己不用，移到频道 |
| | 字幕引擎、翻译框架 | 移到 `:media/subtitle` |
| | `RealtimeSubtitleBindings.forMusic/attachMusic` | 只有频道调用，拆出来移到频道 |
| | 4 处打开视频页 | 改为路由 |
| | `VideoTranslationInstaller` | 是装配代码（依赖 `Constants`），移到 `:app` 启动时调用 |
| 频道 → 用户 | `LoginActivity`、`UserLoginUtil` | 打开登录页改为路由；登录状态读 `:core/account` |
| 用户 → 视频 | `PlaybackEngineHelper` | 播放引擎偏好移到 `:core/prefs`，视频模块读取 |
| 对话 → 视频 | 无 | 删除这行依赖 |

功能专属的视图放在该 feature 的 `ui/widget/`。feature 内部的目录模板：`data/`、`domain/`（可选）、`presentation/`、`ui/`。

### `:app`
进程入口、依赖装配（含 `VideoTranslationInstaller`）、首页外壳、对话接口的数据对象和连接日志（从 `:core` 移来）。

---

## 4. 模块数量对照

| 现在（15 个） | 第 4 版（12 个） |
|---|---|
| `:commonutil`、`:sharekit` | `:common` |
| `:asr`、`:mediaview` | `:media` |
| `:chatkit` | `:chatkit` |
| `:ads` | `:ads` |
| `:core` | `:core` |
| `:feature-chat`、`:feature-roles` | `:feature-chat` |
| `:feature-channel` | `:feature-content`（改名） |
| `:feature-video`、`:feature-user`、`:feature-photosolve`、`:feature-companion` | 不变 |
| `:app` | `:app` |

---

## 5. 第三方库重新归属

| 库 | 现在 | 改为 |
|---|---|---|
| GSY 播放器 | `:commonutil` api | `:feature-video` implementation。`:feature-chat` 只借用了它的一张错误图，改用自己的图 |
| PictureSelector | `:commonutil` api | `:feature-content`（只有 `IndexRecommendAdapter`、`ShowTypeHandle` 用） |
| PictureSelector `compress`、`ucrop`、`camerax` | `:commonutil` api | 删除，无使用 |
| BRVAH、shape-imageview、circleimageview、swiperefreshlayout | `:commonutil` | 删除，无使用 |
| Room | `:commonutil` api | `:core` |
| PhotoView | `:commonutil` api | `:feature-content` |
| Gson、EventBus、OkHttp、Glide | `:commonutil` api | `:common` api |
| Media3 | 分散在 `:app`、`:core`、`:feature-video`、`:mediaview` | `:media` 和 `:feature-video` 各自声明 |
| ONNX Runtime | `:app`、`:core` | `:media` |
| Cast SDK | 只在 `:app` 声明，`:feature-video` 间接拿到 | `:feature-video` 显式声明 |

XML 布局、Manifest、混淆规则里引用的库，文本扫描不一定找得到。每移一个库都跑一次完整构建。

---

## 6. 迁移顺序

每步单独提交，每步 `:app:assembleChatDebug` 加手机冒烟。

| 步 | 内容 | 风险 |
|---|---|---|
| 1 | 删 7 个无用第三方库和 `util/cache/` 4 个死类 | 低 |
| 2 | 加架构测试：检查子包规则和「第 0、1 层不 import `:core`」 | 低 |
| 3 | 底层去业务：`LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 不读 `CommonConstants`；`BaseRequest` 改回调 | 中 |
| 4 | 业务类从 `:commonutil` 上移到 `:core`：收藏、业务事件、`FavoriteVo`、`CommonConstants` | 中 |
| 5 | `:core` 拆分（见 core-split-plan.md 第 1–6 步） | 中 |
| 6 | `:commonutil` 内部按 `util/`、`net/`、`ui/` 整理；并入 `:sharekit` | 中 |
| 7 | 去重：权限、`FileUtils`、屏幕尺寸、分享 | 中 |
| 8 | 建 `:media`：并入 `:asr`、`:mediaview`，移入字幕翻译框架 | 中 |
| 9 | `:feature-roles` 并入 `:feature-chat` | 低 |
| 10 | 去掉 feature 之间的依赖（第 3 节表格） | 中 |
| 11 | 第三方库按第 5 节重新归属 | 中 |
| 12 | 去掉 `AppHooks`（见第 7 节） | 高，需要改基类 |
| 13 | 改名和包名：`:commonutil` → `:common`，包名统一为 `com.openapi.<模块>` | 高，import 改动最多 |

---

## 7. `AppHooks` 怎么去掉

| 方法 | 调用方 | 处理 |
|---|---|---|
| `isVip()` | `:core` 的 `BaseToolbarMenuActivity` | 改为读 `:core/account` |
| `openGlobalSearch()` | `:core` 的 `BaseToolbarMenuActivity` | 基类只留菜单槽位，搜索入口由 `:feature-content` 的子类填 |
| `openSetting()` | `:core` 的 `BaseBannerAdsActivity`、视频播放器 | 用路由打开（`:feature-user` 在 Manifest 里注册 Intent action） |
| `openLogin()` | 没有调用方 | 删除 |

根因是 `:core` 的旧页面基类自带功能行为。基类只保留工具栏、广告、权限这些通用部分后，`AppHooks` 就不需要了。

---

## 8. 已确认的决定（2026-11-10）

1. 先清 `:app` 的重复资源：**已完成**（`eaf374ba`，删 73 个字节级相同的副本）。
2. 包名统一为「包名 = 模块名」：同意，放最后。
3. `:asr` 改名 `:speech`：同意。改为 `:media` 的 `speech/` 子包。
4. `UnsafeOkHttpClient` 只搬位置，不改行为：同意。
5. 工具、网络、通用视图放一个模块 `:common`，用子包分开：同意。
6. 相似的独立能力合并，用子包分开：同意。
7. `:chatkit`、`:ads` 保持独立；`:feature-roles` 并入 `:feature-chat`；「第 0、1 层不依赖 `:core`」；按第 7 节去掉 `AppHooks`：同意。
8. `:media` 不收投屏、视频与频道不合并：按你的疑问修改为第 4 版的方案。
9. `:feature-channel` 改名 `:feature-content`，保持一个模块，内部按内容类型分子包：同意。

## 9. 待你确认

1. 第 4 版的 `:media`（语音、字幕翻译、图像、媒体查看）可以吗？
2. 视频和频道不合并，按第 3 节表格去掉两者之间的依赖，可以吗？
3. `:core` 的定义和 core-split-plan.md 第 6 节的 3 个问题。

## 不在这次范围
- Volley 和 OkHttp 两套网络并存：只记录，不合并。
- `:auxio_music_ui`、`:retromusic` 内部结构不动。
