# 模块列表与目录结构（weifu-refactor，2026-11-10）

依赖方向：`:app` → `:feature-*` → `:core` / `:chatkit` / `:commonutil` / `:ads` / `:asr`。`:core` 不依赖任何 feature。
包名未改：历史包名 `com.openapi.ks.myapp` 仍在大量使用，和模块名不一致（改名需要改大量 import，暂未做）。

---

## 一、基础模块

### `:commonutil` — 通用底座
封装网络请求、工具类、事件总线、收藏存储，以及一些通用视图组件。被几乎所有模块依赖（`api` 暴露）。

```
java/com/openapi/core/
  CommonConstants.java            全局常量（如 TTS_OPEN）
  event/                          事件类（登录、权限、配置动作、收藏变更）
  favorite/FavoriteStore.java     收藏存储接口
  net/                            网络：BaseService、HttpUtils、NetQueue、HttpDNS
    interceptor/                  请求拦截（DNS、Gzip、日志）
    netglide/                     Glide 的 OkHttp 加载器与磁盘缓存
    request/                      Gson 请求、统一响应、列表 VO
  util/                           工具：文件、音频、权限、缓存（34 个文件）
    cache/ permission/
  view/
    translation/                  字幕/翻译覆盖层
    touchhelper/                  RecyclerView 拖拽
    view/                         通用视图（收藏按钮、跑马灯、编辑框、滚动）
      widget/ widgets/            圆形头像、状态按钮、指示器、音乐可视化
```

### `:ads` — 广告
封装 Admob 与 Adview 两套广告 SDK，统一成 `AdsManager` 接口。
```
java/com/openapi/core/ads/yewu/
  ads/base/        AdsContext、AdsManager、AdsEventAdaptor、插屏/横幅基类
  ads/admob/       Admob 的横幅、插屏、激励、开屏
  ads/adview/      Adview 的横幅、插屏、原生、开屏
  ads/config/      开屏/横幅的下一步配置、关闭事件
  um/              MobAgent（统计）
```

### `:core` — 共享底座
被所有 feature 依赖。放三类东西：通用能力、基类、应用端口。
```
java/com/openapi/core/
  app/             AppHooks（feature 端口，由 :app 注册）、AppContext（应用上下文）
  cast/            投屏：DLNA 发现、SSDP、SOAP、本地媒体服务、投屏选择 UI
  design/          设计 token（Tokens）、主题（AppMaterialTheme）、通用对话框
  media/           AudioPlayer（单例播放器）、VoiceRecorder
  permission/      权限请求与状态
  image/           ImageCompressor
java/chat/ui/data/model/MessageType.java   消息类型常量（历史包名）
java/com/aimath/paizhao/photosolve/
  CropNorm.kt, ImageUtils.kt               归一化裁剪框、图片工具
  image/                                    图像管线、上传编码、超分（RealEsrgan、SuperResTiler、TileGeometry）
  quality/                                  图像质量分析、题目图预处理
  cropassist/                               自动裁剪提供者（DocQuadNet、手动）
java/com/openapi/ks/myapp/
  ui/base/                  基类 Activity/Fragment（工具栏、WebView、带广告基类、分页列表基类）
  ui/inte/                  回调接口（返回键、悬浮按钮、音乐状态）
  ui/channel/WebviewScreen.kt   WebView 进度组件（Compose）
  ui/gsy/compose/ListShell.kt   列表外壳（Compose，供分页列表）
  adapter/base/            RecyclerView 适配器基类
  adapter/IndexRecommendAdapter.java  首页推荐适配器
  data/db/                 Room 数据库：AppDatabase、收藏、对话记录、连接日志 DAO
  data/showtype/           推荐项展示样式（模糊、普通、文字在下）
  data/ ImagePhotoLoad, HistoryUtil, ConnLogUtil, VersionUpdater
  bean/vo/ bean/form/      数据对象（约 30 个）
  constant/                Constants（接口地址等）
  task/                    保存图片/文字的后台任务
  data/urlparser/          URL 解析（浏览器、HTTP、自定义表单）
  view/MyRadarScanView.java  雷达扫描动画
```

### `:asr` — 语音识别
火山引擎流式 ASR 的协议与客户端。被 `:feature-chat`（语音转写）与 `:feature-video`（实时字幕）共用。
```
java/com/openapi/ks/myapp/translation/realtime/
  VolcengineStreamingAsrClient.java   流式客户端
  VolcengineBinaryProtocol.java       二进制帧协议
  VolcengineAsrResponseParser.java    响应解析
  VolcengineAsrConfig.java / VolcengineAsrErrors.java
```

### `:chatkit` — 对话 UI 组件库
与业务无关的对话界面组件，可在多个页面复用。
```
java/com/openapi/chatkit/
  message/        消息列表 ChatMessageList、菜单 MessageMenu、底部 Footer、多选、附件面板
    parts/        消息内容块（文字、图片、语音气泡等）
  input/          输入栏 ChatComposer、语音控件、录音浮层（WeChatRecordingView 等）
  model/          ChatItem、ComposerModels（UI 数据模型）
  media/          图片查看器
  theme/          ChatKitTheme、颜色与默认值
```

### 不改动的既有模块
`:auxio_music_ui`、`:retromusic`、`:mediaview`（既有模块，本次拆分未改动）。

---

## 二、功能模块

### `:feature-chat` — 对话
会话、消息收发、流式回复、语音、附件、Markdown 与公式渲染、角色选择入口。
```
java/com/openapi/chat/
  ChatDependencies.kt              依赖注入入口（由 :app 注册仓库实现）
  domain/                          纯业务接口与模型（不依赖 Android）
    model/       ChatMessage、ChatSession、ChatStreamEvent
    repository/  ChatRepository（接口）
    usecase/     ChatUseCases
    speech/      SpeechToText（接口，实现在 :app）
    auth/        LoginGate（登录门控接口）
  data/            ChatMessageActions（收藏、反馈）、mapper/ChatLogMapper（服务端历史映射）
  presentation/    ChatViewModel、ChatUiState、AttachmentStager
    ui/            ChatRoute、ChatScreen、ChatItemMapper、AttachmentActions、ChatPlatform
java/chat/ui/markdown/     ChatMarkwon（Markdown 渲染）、FormulaInputNormalizer
java/com/openapi/ks/myapp/view/katexplugin/   公式渲染插件（11 个文件）
```

### `:feature-roles` — 角色目录
角色列表、分组、收藏、搜索，以及选择角色的面板。
```
com/openapi/roles/
  domain/        RoleModels（角色数据模型）
  data/          LocalRoleCatalog（本地角色）、RolesRepository、RolesParser、RoleColors
  presentation/  RoleCatalogPane（角色列表 UI）、RolePickerViewModel
```

### `:feature-photosolve` — 拍照解题
拍照、裁剪、识别、结果页；拍照解题的整条流程入口是 `PhotoSolveActivity`。
```
com/aimath/paizhao/
  PhotoSolveActivity.kt            入口 Activity
  photosolve/
    PhotoSolveViewModel.kt         流程状态机
    PhotoSolveUiState.kt           界面状态与步骤
    PhotoSolveRepository.kt        仓库接口
    PhotoSolveRepositories.kt      仓库装配
    HttpPhotoSolveRepository.kt    HTTP 实现（服务端识别）
  photosolve/ui/
    PhotoCaptureScreen.kt          拍照
    PhotoCropScreen.kt             裁剪
    PhotoRecognizingScreen.kt      识别中
    PhotoResultScreen.kt           结果
    PhotoQualityDialog.kt          质量提示
    PhotoSolveHomeScreen.kt        首页
    CropAssistSettingsSheet.kt     自动框设置
    CropAssistComparisonScreen.kt  自动框对比
    MlKitScanLauncher.kt           文档扫描（ML Kit）
  ui/theme/                        主题（Color、Theme、Type）
```
注：图像处理（管线、质量分析、自动裁剪）在 `:core`，本模块只负责界面和流程。

### `:feature-user` — 账户与设置
登录、注册、找回密码、个人设置、意见反馈、设置页的角色设置。
```
com/openapi/ks/myapp/
  ui/user/         LoginActivity、RegisterScreens、FindPassActivity、SettingActivity、AddCustomeInfoActivity
  ui/feedback/     FeedbackActivity
  ui/fragment/     SettingCharacterFragment（设置页的角色设置）
  adapter/         SettingCharacterAdapter
  data/            UserLoginUtil（登录状态读写）
  task/            LoginTask（登录请求）
```
依赖 `:feature-video`：设置页使用播放器引擎开关（`PlaybackEngineHelper`）。

### `:feature-video` — 视频播放
播放器（GSY）、视频列表、视频页、本地视频、投屏、播放服务、实时字幕与翻译。
```
com/openapi/ks/myapp/
  video/           PlaybackEngineHelper（播放引擎选择）
  ui/gsy/          DetailGSYVideoPlayer（播放器）、GSYAutoVideoListFragment、GSYVideoListAdapter、VideoListItemCompose.kt
  ui/sound/        VideoActivity、VideoShowActivity、VideoShowActivityLazyUrl、VideoListShowActivity、
                   VideoPlaybackUiHelper、VideoShowPlaybackStateHelper
  ui/fragment/     AutoVideoListFragment
  ui/localvideo/   LocalVideoActivity.kt（本地视频列表）
  cast/            投屏：CastFrameworkUtil、GoogleCastSessionController、CastPlaySource、AppCastOptionsProvider
  service/         PlayService（后台播放）
  data/            VideoUtil
  translation/     视频翻译：VideoTranslationInstaller、演示与占位实现（Baidu、Volcano）
  translation/realtime/   PCM 音频源（ExoPlayer、媒体文件、采集）
```

### `:feature-channel` — 频道
推荐、频道列表、频道详情、图片/文字/声音/视频/电影频道页、收藏、搜索、声音条。
```
com/openapi/ks/myapp/
  ui/channel/      ChannelBodyFragment、ChannelBodyController、ChannelBodyScreen（频道主体）
  ui/fragment/     列表页：RecommendFragment、AppListFragment、DonationListFragment、FavoriteFragment、
                   FavoriteScreen.kt、GlobalVideoChannelListFragment（全局搜索）、
                   Img/Text/Video 频道列表与用户列表、TextItemsFragment、SoundItemsMusicFragment、ImgItemFragment
  ui/fragment/body/ 各频道的主体 Fragment（图片、文字、声音、视频、电影）
  ui/sound/        频道页：ImgChannleActivity、TextChannleActivity、SoundChannleActivity、VideoChannleActivity、
                   MovieChannleActivity、TeleplayActivity、SoundDetailActivity、MyFullScreenImageGalleryActivity
  ui/sound/compose/ SoundDetailScreen.kt、SoundDetailUiState.kt
  adapter/         推荐、全屏图片翻页、音乐条目、文字频道条目
  ui/config/       QuickBrowserConfigActivity（快捷浏览配置）
  auxio/           AuxioMusicUiLauncher（打开音乐界面）
```

### `:feature-companion` — 角色扮演伴侣
伴侣页、角色记忆与设置、虚拟形象渲染。Android 端按角色扮演分支维护。
```
com/openapi/ks/myapp/
  companion/   CompanionRoute（入口）、CompanionScreen、CompanionApi（HTTP）、CompanionModels、
               CompanionViewModel、CompanionTransport、LiveKitCompanionTransport（LiveKit 传输）、
               CompanionManagementScreens / ViewModels（角色、记忆、设置页）
  avatar/      SimpleAvatar、SimpleAvatarRenderer、AvatarRenderer、AvatarCommand、AvatarEventMapper、README.md
```

---

## 三、应用壳 `:app`
只保留装配层和首页外壳，不放业务功能。
```
com/openapi/ks/myapp/
  base/          MyApplication（进程入口，注册 AppHooks/AppContext）、AppEventHandler、AppHooksImpl（端口实现）
  data/chat/     对话仓库实现、语音转写实现、登录门控实现（实现 :feature-chat 的接口）
  data/roles/    角色内置数据与仓库实现（实现 :feature-roles 的接口）
  data/home/     首页入口装配
  data/config/   配置动作分发（URL 路由到各页面）
com/openapi/home/
  HomeActivity.kt, HomeDependencies.kt       首页外壳
  presentation/  HomeScreen（首页与抽屉）、ChannelsScreen/ViewModel（频道页）、ProfileScreen（我的）
  domain/        HomeModels
com/openapi/ks/chatfree/ui/main/LaunchActivity.java   启动页
chat/ui/data/model/                        （已迁到 :core，这里不再有）
app/src/test/                              单元测试（11 个）
```

---

## 四、依赖关系（简图）
```
:app
 ├─ :feature-chat ─┬─ :chatkit ─┐
 │                 ├─ :asr      │
 │                 └─ :core ────┴─ :commonutil ─ :ads
 ├─ :feature-roles ─ :core
 ├─ :feature-photosolve ─ :core
 ├─ :feature-user ─ :core, :feature-video
 ├─ :feature-video ─ :core, :asr
 ├─ :feature-channel ─ :core, :feature-user, :feature-video, :auxio_music_ui
 └─ :feature-companion ─ :core
```
（`:feature-chat` 依赖 `:feature-roles`、`:feature-video`，与上面的简图对应。）

## 五、已知遗留
- 包名 `com.openapi.ks.myapp` 与模块名不一致；改包名需要大量 import 改动，未做。
- `:feature-chat` 里的 `com/openapi/ks/myapp/view/katexplugin` 是历史包名下的 Markdown 公式插件。
- `:app` 的 `data/chat`、`data/roles` 是接口实现，属于装配层，按计划保留。
