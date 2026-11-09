# ks/myapp 模块迁移计划

范围：`app/src/main/java/com/openapi/ks/myapp`，共 132 个文件（已删除死代码之后）。
依赖分析脚本：`/tmp/ks-deps2.py`（同包标识符匹配 + 跨包 import 精确匹配），结果 `/tmp/ks-deps.json`。

## 原则
- 只改目录和 gradle 模块，**不改包名**：Manifest、layout XML 里的类名引用保持有效。
- 每个阶段结束必须 `:app:compileChatDebugKotlin` + `:app:compileChatDebugJavaWithJavac` 通过，然后单独提交。
- 循环依赖先拆（改成依赖注入或类名 Intent），再搬迁。
- 资源：每个模块自己带一份资源（字符串、颜色、尺寸、drawable、layout、id 相关的 values），由脚本从 `:app` 复制，`:app` 保留原件。

## 分层（依赖方向只能从上往下）
`:app` → `:feature-*` → `:core`

## 分类结果

### :core（最底层，不依赖 feature）
- `ui/base/**`（CommonFragmentActivity、MenuOneContext、WebViewActivity、VideoWebViewActivity、app/*、features/*）— 13 个文件
- `ui/inte/*`（FabOpListener、MusicStateListener、OnBackKeyDownListener）— 3
- `adapter/base/*`（BasePhotoFlowRecycleViewAdapter、BaseRecyclerViewAdapter）— 2
- `data/db/*`（AppDatabase、AppFavoriteStore、ChatLogDao）+ `bean/form/ChatLog` — 4
- `data/HistoryUtil`、`data/ConnLogUtil`、`data/ImagePhotoLoad`、`data/showtype/*`（6）— 9
- `view/MyRadarScanView` — 1
- `data/VersionUpdater` — 1（只依赖 MyApplication，需先做 AppContext）

### :feature-user
- `ui/user/*`（LoginActivity、FindPassActivity、RegisterScreens、SettingActivity、AddCustomeInfoActivity）— 5
- `ui/feedback/FeedbackActivity`、`data/UserLoginUtil`、`task/LoginTask` — 3

### :feature-video
- `ui/gsy/*`（DetailGSYVideoPlayer、GSYAutoVideoListFragment、GSYVideoListAdapter、compose/ListShell）— 4
- `ui/sound/Video*`（VideoActivity、VideoChannleActivity、VideoListShowActivity、VideoShowActivity、VideoShowActivityLazyUrl、VideoPlaybackUiHelper、VideoShowPlaybackStateHelper）+ `ui/sound/TeleplayActivity`、`ui/sound/MovieChannleActivity` — 9
- `ui/localvideo/LocalVideoActivity`、`service/PlayService`、`data/VideoUtil`、`translation/realtime/RealtimeSubtitleBindings`、`cast/*`（4）— 9

### :feature-channel
- `ui/channel/*`（ChannelBodyController、ChannelBodyFragment、ChannelBodyScreen）— 3
- `ui/fragment/*`（除 AutoVideoListFragment 外的频道列表、收藏、推荐、设置角色、文字 / 图片 / 音乐条目）— 约 19
- `ui/fragment/body/*`（5）
- `ui/sound/*` 中的图片、文字、声音频道页、SoundDetailActivity、MyFullScreenImageGalleryActivity、compose/SoundDetail*（约 9）
- `adapter/*`（AppRecommendViewAdapter、FullScreenImagePagerAdapter、IndexRecommendAdapter、SettingCharacterAdapter、SoundItemsViewMusicAdapter、TextChannelListItemsViewAdapter）— 6
- `ui/config/QuickBrowserConfigActivity`、`data/config/ConfigActionJump`、`auxio/AuxioMusicUiLauncher` — 3

### :feature-companion（角色扮演，保留）
- `companion/*`（9）+ `avatar/*`（6）— 15

### :app（保留）
- `base/MyApplication`、`base/AppEventHandler`
- `data/chat/*`（BackendSpeechToText、ChatRepositoryImpl、LegacyLoginGate）— 实现 feature-chat 的接口，属于应用装配层
- `data/roles/*`（BundledRoleCatalog、RolesRepositoryImpl）— 同上
- `data/home/LegacyHomeEntries` — 首页装配

## 需要先拆的循环（阶段 0）
1. `ui/base/app/BaseBannerAdsActivity` → `ui/user/SettingActivity`：改为类名 Intent（`setClassName`），避免 core 依赖 user。
2. `ui/base/app/BaseToolbarMenuActivity` → `GlobalVideoChannelListFragment`（channel）、`UserLoginUtil`（user）：改为类名 Intent，并把 `UserLoginUtil.isVIP()` 的调用经由 core 的接口。
3. `ui/user/SettingActivity` → `ui/fragment/SettingCharacterFragment`（channel）：user 依赖 channel，所以 SettingCharacterFragment 的入口改为类名 Intent，user 不再 import channel。
4. `ui/channel/ChannelBodyFragment` → `ui/user/LoginActivity`：改为 `core` 里的 `LoginRouter`（app 启动时注册实现）或类名 Intent。
5. `MyApplication.getInstance()` / 静态 SharedPreferences 被 core 文件使用（BaseWebViewFragment、UserLoginUtil、VersionUpdater、ImgItemFragment 等）：在 core 增加 `AppContext`（由 MyApplication.onCreate 设置），core 文件改用 `AppContext`。
6. `data/db/ChatLog` → `chat.ui.data.model.MessageType`（app 旧 chat 代码）：把 `MessageType` 的常量挪到 core（`com.openapi.core.chat.MessageType`），`:app` 的旧 chat 代码改 import。
7. `data/showtype`、`data/ImagePhotoLoad`、`adapter/IndexRecommendAdapter` 之间互相依赖：整体进 core（它们只依赖彼此和 core）。`IndexRecommendAdapter` → `VersionUpdater`：随 VersionUpdater 进 core。

## 迁移顺序（依赖决定，与原提议略有不同）
1. **阶段 0**：循环拆解（上面 1–7），每项单独编译。
2. **阶段 1**：`:core` 的文件（ui/base、ui/inte、adapter/base、data/db、bean/form、data/*、view）+ 资源。
3. **阶段 2**：`:feature-user`（ui/user、feedback、UserLoginUtil、LoginTask）+ 资源。
4. **阶段 3**：`:feature-video`（gsy、ui/sound 视频页、localvideo、PlayService、VideoUtil、RealtimeSubtitleBindings、cast）+ 资源。
5. **阶段 4**：`:feature-channel`（ui/channel、ui/fragment、ui/fragment/body、adapter、ui/sound 频道页、config、auxio）+ 资源。
6. **阶段 5**：`:feature-companion`（companion、avatar）+ 资源。
7. **收尾**：`:app` 只剩应用装配层；检查 Manifest 中的 Activity 类名仍可解析。

## 风险
- 资源复制是主要工作量（约 227 个字符串引用、67 个 id、55 个 drawable、38 个 layout）。
- Manifest 与 layout XML 中的类名引用不会因目录变化失效（包名不变），但运行时才会发现错误，所以每个阶段完成后要在手机上冒烟测试（启动、登录页、视频页、频道页）。
- UI 会话（`ui-weifu-refactor`）也在 weifu-refactor 上改这些文件，合并前要和它错开。

## 执行结果（已完成）
| 阶段 | 模块 | 提交 | 说明 |
|---|---|---|---|
| 0 | :app/:core | 0eb81adf | 基类到功能的调用经 `core.app.AppHooks`（isVip、openGlobalSearch、openSetting），`:app` 的 `AppHooksImpl` 注册 |
| 1 | :core | a822b5f9 | ui/base、ui/inte、adapter/base、data/db、ChatLog、MessageType、HistoryUtil、ConnLogUtil、ImagePhotoLoad、showtype、VersionUpdater、MyRadarScanView、WebviewScreen、ListShell（计划修正：后两者被基类使用，移入 core） |
| 2 | :feature-user | b6331539 | 登录、注册、找回密码、设置、反馈、UserLoginUtil、LoginTask、SettingCharacterFragment+Adapter（计划修正：归 user） |
| 3 | :feature-video | ddf2bb42 | gsy 播放、视频列表与页面、本地视频、cast、PlayService、VideoUtil、RealtimeSubtitleBindings、AutoVideoListFragment |
| 4 | :feature-channel | 4336bd2a | 频道列表、body、频道页（Img/Sound/Text/Video/Movie/Teleplay）、SoundDetail、adapter、QuickBrowserConfigActivity、AuxioMusicUiLauncher |
| 5 | :feature-companion | 1ab15cc3 | companion、avatar |
| 收尾 | :app | — | 留 base（MyApplication、AppEventHandler、AppHooksImpl）、data/chat、data/roles、data/home、data/config（ConfigActionJump，路由分发） |

- 包名全部未改，Manifest 中的 Activity 类名无需修改；`:app:assembleChatDebug` 通过，`checkChatDebugDuplicateClasses` 通过。
- 资源：每个模块由 `/tmp/res-copy.py` 从 `:app` 复制所需 values、layout、drawable，并用 `ks_ids.xml` 声明 R.id。
- 未做：手机冒烟测试（启动、登录页、视频页、频道页）。
