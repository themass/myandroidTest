# 模块标准与归属设计（草案，待确认）

状态：**设计稿，未动代码**。基于 `weifu-refactor` 的 `59f2bf52` 统计。
统计方法：按类名在各模块源码里的出现次数统计使用方（`/tmp/clsusers.py`）。`Message`、`Token`、`Response` 这类通用名会被误算，最终以编译结果为准。

---

## 1. 现在乱在哪里（有数据支撑）

| 问题 | 证据 |
|---|---|
| `:commonutil` 和 `:core` 用同一个包名根 | 两个模块都在 `com.openapi.core.*` 下，看包名分不出类在哪个模块 |
| `:commonutil` 里混进了业务和界面 | `view/translation`（10 个视频字幕翻译类，只有视频和频道用）、`MusicVisualizer`、`FavoriteImageView`、`IjkPlayerView`、`event/UserNeedLogin` |
| `:core` 里混进了单一功能的代码 | 投屏 `cast/`（12 个文件，只有 `:feature-video` 用）；拍照图像管线 `com/aimath/paizhao/photosolve/`（14 个，只有拍照和对话用）；`bean/form` 里的 `LoginForm`、`RegForm` 只有 `:feature-user` 用 |
| `:core` 里有只被 `:app` 用的东西 | `data/db`（Room 数据库）、`ChatSessionVo`、`ChatMessageVo`、`AsrResponseVo` 等 |
| 同名重复工具类 | `FileUtils` 两份（`core/util/` 和 `view/view/widget/util/`）；`ImageUtils` 两份（`:commonutil` 和 `:core` 拍照目录）；权限三套（`commonutil/util/permission`、`util/PermissionHelper`、`core/permission`） |
| feature 之间互相依赖 | `:feature-user → :feature-video`（只为 `PlaybackEngineHelper`）、`:feature-channel → :feature-user`（只为 `LoginActivity`、`UserLoginUtil`）、`:feature-chat → :feature-video`（源码里没找到 import，可能是多余依赖） |
| 没被引用的类 | `EmojiBean`、`GzipRequestInterceptor`、`LoggingInterceptor`、`IjkPlayerView`、`BitmapUtil`（Kotlin 顶层函数文件未计入，需编译确认） |

`:chatkit` 目前是干净的：只被 `:feature-chat` 用，不依赖业务模块。它唯一的项目内依赖是 `:mediaview`（图片/视频的叠放和轮播组件）。注意 `:mediaview` 也用 `com.openapi.core.view.media` 包名，所以 `com.openapi.core` 这个包根现在被 `:commonutil`、`:core`、`:mediaview` 三个模块共用。

`:app` 的问题在资源，代码本身是干净的（只有 `home/` 首页外壳、`base/` 进程入口与端口实现、`data/` 三个接口实现，共 16 个源文件）：
- `res/layout` 有 32 个布局，`:app` 自己的代码和 Manifest 只用到 8 个（我的脚本把 `main_launch` 误判成未使用，已人工核对，`LaunchActivity` 在用）；其余 24 个里 23 个在对应 feature 模块里有同名副本，只有 `common_banner` 在整个仓库都没有引用，其中 34 份对比后内容完全一致（`item_song` 与 `:retromusic` 的版本不同）。
- `res/drawable*` 有 153 个文件，其中 60 个在别的模块里有内容完全相同的副本。
- 同名资源由 `:app` 覆盖库里的版本，所以这些副本现在是死重，而且以后改 feature 模块里的布局可能不生效。

---

## 2. 分层标准

```
第 4 层  :app                          装配：Application、端口实现、首页外壳
第 3 层  :feature-*                    一个业务功能一个模块，含页面、ViewModel、该功能的数据
第 2 层  :core                         业务公共层：多个 feature 共用的业务类型、基类、设计系统、端口
         能力库 :chatkit :asr :mediaview :imaging(新)   与业务无关、可单独复用的能力
第 1 层  :commonutil                   纯基础：网络、日志、存储、通用工具；不认识任何业务
         :ads                          广告 SDK 封装
```

### 依赖规则
1. 只能依赖下层，不能依赖同层或上层。
2. **feature 之间不互相依赖**。需要别的功能（如打开登录页、读播放器设置）时，通过 `:core` 的端口（`AppHooks`）由 `:app` 实现。
3. 能力库之间不互相依赖，只依赖 `:commonutil`（必要时 `:core`）。
4. `:commonutil` 不依赖任何项目模块。

### 每层放什么（准入规则）

| 模块 | 放什么 | 不放什么 |
|---|---|---|
| `:commonutil` | 网络底座、日志、偏好存储、文件/日期/字符串工具、事件总线封装、和业务无关的通用视图 | 任何业务名词（收藏、登录、视频、频道、聊天）；只有一个模块用的代码 |
| `:core` | **至少两个 feature 共用**的：接口地址与常量、数据对象、Activity/Fragment 基类、设计系统（主题、Token、对话框）、`AppHooks`/`AppContext` 端口、数据库 | 只有一个 feature 用的代码（下沉到那个 feature）；具体功能页面 |
| 能力库 | 一项完整能力：对话 UI（`:chatkit`）、语音识别与朗读（`:asr`）、媒体查看（`:mediaview`）、图像处理（`:imaging`） | 业务数据对象、网络接口调用 |
| `:feature-*` | 该功能的页面、ViewModel、仓库、该功能独有的数据对象和资源 | 被其他 feature 需要的东西（上移到 `:core`） |
| `:app` | `MyApplication`、端口实现、各功能接口的装配、首页外壳 | 业务逻辑 |

**判定口诀**：只有一个模块用 → 放那个模块；两个以上 feature 用 → `:core`；与业务无关且所有地方都可能用 → `:commonutil`；是一块完整的独立能力 → 能力库。

---

## 3. 统一目录模板

包名根跟模块名一致（新代码立即执行；旧代码随搬迁逐步改，Manifest 里登记的 Activity 最后改）。

```
:commonutil   com.openapi.common
                net/  log/  storage/  util/  event/  view/

:core         com.openapi.core
                app/        端口 AppHooks、AppContext
                config/     Constants、接口地址
                model/      跨 feature 的数据对象（原 bean/vo、bean/form 中共用的部分）
                db/         Room 数据库与 DAO
                ui/base/    Activity/Fragment 基类
                design/     主题、Token、通用对话框
                media/      AudioPlayer、VoiceRecorder
                permission/ 唯一一套权限

能力库        com.openapi.<库名>
                api/        对外接口（其他模块只用这里）
                internal/   实现

:feature-xxx  com.openapi.feature.xxx
                data/          仓库实现、网络接口、该功能的数据对象
                domain/        模型、仓库接口、用例（可选，逻辑简单时省略）
                presentation/  ViewModel、UiState
                ui/            Activity、Fragment、Compose 页面
                XxxDependencies.kt   依赖入口（由 :app 注册）
```

`:feature-chat` 已经接近这个模板，其余 feature 按它对齐。

---

## 4. 归属表（你点名的四个模块）

### `:commonutil` 留下的
`net/`（除 `UnsafeOkHttpClient`，见第 6 节）、`util/` 中的通用工具（`LogUtil`、`StringUtils`、`PreferenceUtils`、`DateUtils`、`FileUtils`、`DensityUtil`、`ToastUtil` 等）、`util/cache`、`view/touchhelper`、通用视图（`MarqueeTextView`、`MyEditText`、`MyScrollView`、`CircleImageView`、`StateButton`、`IndicatorView`、`DividerItemDecoration`）。

### `:commonutil` 移出的
| 内容 | 去向 | 理由 |
|---|---|---|
| `view/translation/`（10 个，字幕翻译） | `:feature-video` | 视频专属；`:feature-channel` 改为通过视频的入口使用 |
| `DraggableTranslationOverlay`、`gallery/CommonUtil` | `:feature-video` | 只有视频用 |
| `MusicVisualizer` | `:feature-channel` | 只有频道（声音）用 |
| `event/` 中的 `FavoriteVo`、`FavoriteChangeEvent`、`ConfigActionEvent`、`UserNeedLogin`，`favorite/FavoriteStore`、`FavoriteUtil`、`MyFavoriteView`、`FavoriteImageView` | `:core` | 业务名词（收藏、登录），多个 feature 共用 |
| `CommonConstants` | `:core/config` | 业务开关（如 `TTS_OPEN`） |
| `util/permission/`、`PermissionHelper` | 合并进 `:core/permission` | 三套权限合成一套 |
| `widget/util/FileUtils`、`ImageUtil`、`ImageSize`、`BitmapUtil` | 合并进 `util/FileUtils`、新的 `util/ImageUtils` | 去重 |
| `EmojiBean`、`GzipRequestInterceptor`、`LoggingInterceptor`、`IjkPlayerView` | 删除（编译确认无引用后） | 无引用 |

### `:core` 移出的
| 内容 | 去向 | 理由 |
|---|---|---|
| `cast/`（DLNA，12 个） | `:feature-video/cast` | 只有视频用；和视频里已有的 Google Cast 放一起 |
| `com/aimath/paizhao/photosolve/`（图像管线、质量分析、自动裁剪，14 个） | 新能力库 `:imaging` | 拍照和对话都用，又和业务无关；放进 `:feature-photosolve` 会让 `:feature-chat` 依赖另一个 feature |
| `core/image/ImageCompressor` | `:imaging` | 同上 |
| `bean/form` 的 `LoginForm`、`RegForm`、`IwannaForm`、`CustomeAddForm`，`bean/vo` 的 `UserInfoVo`、`IWannaVo`，`data/urlparser/`，`data/config` 的 `TabChangeEvent`、`UserLoginEvent`、`CustomeAddEvent` | `:feature-user` | 只有用户模块用 |
| `bean/vo` 的 `AppInfo`、`ImgItemsVo`、`TextItemVo`、`TextItemsVo`，`data/showtype/`，`task/`，`adapter/IndexRecommendAdapter`、`BasePhotoFlowRecycleViewAdapter`，`HistoryUtil`，`ui/inte/` | `:feature-channel` | 只有频道用 |
| `bean/vo` 的 `ChatSessionVo`、`ChatMessageVo`、`ChatMessagePageVo`、`Choice`、`HistoryVo`、`AsrResponseVo`、`TtsResponseVo`，`bean/form` 的 `ChatSessionLog`、`AsrContentForm`、`ConnLog`，`ConnLogUtil` | `:app/data/chat` | 只有 `:app` 的对话仓库实现用 |
| `chat/ui/data/model/MessageType` | `:feature-chat` | 历史包名；`:app` 本来就依赖 `:feature-chat` |
| `ui/base/VideoWebViewActivity`、`BaseBannerAdsActivity`、`MyRadarScanView` | `:app` | 只有 `:app` 用 |

### `:core` 留下的
`app/`、`design/`、`media/`、`permission/`（合并后）、`constant/Constants`、多 feature 共用的数据对象（`RecommendVo`、`SoundItemsVo`、`CharacterVo`、`NullReturnVo`、`FeedbackContentForm`、`ChatLog`）、`data/db`（数据库在 `:core`，DAO 随表走）、`ui/base` 的通用基类、`ImagePhotoLoad`、`VersionUpdater`。

### `:feature-video` 的边界
**放**：播放器（GSY、ExoPlayer/Media3）、视频列表与视频页、本地视频、投屏（Google Cast + DLNA）、`PlayService`、实时字幕与翻译（含从 `:commonutil` 移来的 `view/translation`）、`PlaybackEngineHelper`。
**不放**：语音识别协议（在 `:asr`）。
**对外**：`:feature-user` 读写播放引擎设置、`:feature-channel` 打开视频页，都改为通过 `AppHooks` 端口，去掉这两条 feature → feature 依赖。

### `:app` 的边界
**放**：`MyApplication`、`AppHooksImpl`、`AppEventHandler`、`LaunchActivity`、首页外壳（`home/`）、对话/角色/首页的接口实现（`data/chat`、`data/roles`、`data/home`）、`ConfigActionJump`（URL 路由到各页面）、Manifest 与签名/渠道配置。
**不放**：任何 feature 的页面资源。
**要做的清理**（见第 5 节第 0 步）：
1. 删除 23 个在 feature 模块里有同名副本的布局，其中内容一致的 `:app` 副本直接删；`item_song` 与 `:retromusic` 不同，单独核对后再决定。
2. 删除 60 个在别的模块有完全相同副本的 drawable。
3. 其余 `:app` 独有但没有任何引用的资源，用编译（含 `lint` 的 UnusedResources）确认后删。
4. `ChatRepositoryImpl.kt`（767 行）是 `:app` 里最大的文件，它实现的是 `:feature-chat` 的接口，按本标准属于装配层，可以留；如果之后想让 `:app` 更薄，可以把它下沉到 `:feature-chat/data`，但这需要 `:feature-chat` 依赖网络层，不在这次范围。

### `:chatkit` 的边界
**放**：与业务无关的对话 UI：消息列表、气泡、输入栏、录音浮层、附件面板、图片查看。只认识 `chatkit/model` 里的 UI 模型。
**不放**：`ChatMessage` 等业务模型、网络、角色、Markdown 的业务规则。`:feature-chat` 负责把业务模型映射成 `ChatItem`（已有 `ChatItemMapper`）。
**现状**：符合标准，不需要搬。

---

## 5. 迁移顺序（每步单独提交，每步编译 + 手机冒烟）

0. **清 `:app` 的重复资源**（第 4 节 `:app` 部分）：先删内容完全一致的副本，编译 + 手机冒烟。
1. **删无引用类**：8 个已在 `c1bc47aa` 删除并合入 `weifu-refactor`（交给 UI 会话合并中）。方法级的候选（231 个）你已决定不删。
2. **去重**：权限合一套、`FileUtils`/`ImageUtils` 合一份。
3. **`:commonutil` 去业务**：收藏、登录事件、`CommonConstants` 上移到 `:core`；字幕翻译下沉到 `:feature-video`。
4. **`:core` 下沉单用户代码**到 `:feature-user`、`:feature-channel`、`:app`。
5. **新建 `:imaging`**，移入拍照图像管线和 `ImageCompressor`。
6. **投屏 `cast/` 移到 `:feature-video`**。
7. **打断 feature → feature 依赖**：`AppHooks` 增加 `openLogin`（已有）、`isLoggedIn`、`playbackEngine` 读写、`openVideo` 等端口。
8. **改包名**：`:commonutil` 改为 `com.openapi.common`；其余模块随后按模板改。这一步 import 改动最多，放最后。

---

## 6. 已确认的决定（2026-11-10）

1. 第一步先清 `:app` 的重复资源：**已完成**（`eaf374ba`，删 73 个字节级相同的副本；被 `:app` 代码直接引用的副本保留；`item_song` 与 `:retromusic` 不同，保留）。
2. `:commonutil` 包名改为 `com.openapi.common`，其他模块统一为「包名 = 模块名」：同意，放在最后（第 8 步）。
3. `:asr` 改名为 `:speech`：同意。
4. `UnsafeOkHttpClient` 只搬位置，不改行为：同意。
5. `AppHooks`：用户认为这个设计奇怪，原因是 feature 边界乱。核对结果见下节，处理方式待定。
6. `:imaging` 能力库与「feature 之间不互相依赖」两项，用户尚未明确回答。

## 7. `AppHooks` 的事实（为什么它看起来奇怪）

`AppHooks` 有 4 个方法，实际调用点只有 5 处：

| 方法 | 调用方 | 实际用途 |
|---|---|---|
| `isVip()` | `:core` 的 `BaseToolbarMenuActivity` | 决定菜单里是否显示 VIP 相关项 |
| `openGlobalSearch()` | `:core` 的 `BaseToolbarMenuActivity` | 菜单里的全局搜索，页面在 `:feature-channel` |
| `openSetting()` | `:core` 的 `BaseBannerAdsActivity`、`:feature-video` 的播放器 | 点设置入口，页面在 `:feature-user` |
| `openLogin()` | **没有调用方** | 死方法，可以直接删 |

结论：端口存在，是因为 `:core` 里的 **Activity 基类自己带了功能行为**（搜索菜单、VIP 判断、设置入口），而这些功能的页面在 feature 模块里。`:core` 反向依赖不了 feature，就只能绕一层端口。

更合理的做法是让基类不再认识这些功能：
- 基类只提供「菜单/入口槽位」，由各页面（feature 里的子类）填入搜索、设置、VIP 逻辑；
- 这样 `AppHooks` 可以整个去掉，`:feature-user`、`:feature-channel` 之间的依赖也一并理顺。

这属于重构而不是搬文件，改动面是 `BaseToolbarMenuActivity`、`BaseBannerAdsActivity` 以及继承它们的 Activity，需要单独评估后再做，不在前面 1–8 步里。

## 不在这次范围
- Volley 和 OkHttp 两套网络并存：只记录，不合并。
- `:auxio_music_ui`、`:retromusic`、`:mediaview`、`:sharekit` 内部结构不动。
