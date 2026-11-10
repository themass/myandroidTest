# 模块标准与归属设计（第 3 版，待确认）

状态：**设计稿，未动代码**。基于 `weifu-refactor` 的 `debdf79f`。
数据来源：按文件的 import 归类（`/tmp/deadscan/classify.py`），按类名统计使用方（`/tmp/clsusers.py`）。脚本有误判，每一步搬迁都以编译为准。

第 3 版的变化：按你的意见**少建模块，多用子包**。同一类的东西放进同一个模块，用子包分开。模块从现在的 15 个减到 11 个（`:auxio_music_ui`、`:retromusic` 不计）。

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
| feature 互相依赖 | `:feature-channel → :feature-user, :feature-video`；`:feature-user → :feature-video`；`:feature-chat → :feature-roles, :feature-video` |
| 死代码 | `util/cache/` 4 个磁盘缓存类在模块外没有使用方 |

---

## 1. 先分种类：每个类必须是下面 6 种之一

种类决定**放进哪个模块的哪个子包**，不再一种一个模块。

| 种类 | 定义 | 判断标准 | 放到 |
|---|---|---|---|
| **A. 纯工具** | 无状态的辅助函数 | 不认识业务名词；不画界面；不发网络请求 | `:common` 的 `util/` |
| **B. 网络** | HTTP 收发和图片加载的传输层 | 依赖 OkHttp、Volley、Glide 的网络部分 | `:common` 的 `net/` |
| **C. 通用视图** | 与业务无关的 UI 部件 | View、Compose 组件、主题、通用对话框，换个 App 也能用 | `:common` 的 `ui/` |
| **D. 独立能力** | 自成体系、与业务无关的一整块功能 | 见第 2 节 | `:media`、`:chatkit`、`:ads` 之一 |
| **E. 业务公共** | 多个功能共用的业务概念 | 含业务名词（收藏、登录、VIP、角色），或依赖接口域名配置 | `:core` |
| **F. 功能专属** | 只服务一个功能 | 只有一个 feature 用 | 那个 feature |

**判定顺序**（从上往下，命中即停）：
1. 含业务名词或依赖业务配置？只有一个 feature 用 → **F**；多个用 → **E**。
2. 是独立能力？ → **D**。
3. 是界面？ → **C**。
4. 是网络传输？ → **B**。
5. 剩下的 → **A**。

---

## 2. 什么时候单独建模块，什么时候用子包

**默认用子包。** 只有满足下面任一条，才单独建模块：
1. **带重的第三方库或原生库**，不想让不用它的模块也带上（例如 Media3、ONNX、LiveKit、广告 SDK）。
2. **构建边界有意义**：需要强制「别人不能反向依赖它」，子包做不到。
3. **独立的开发分支**：例如角色扮演的 Android 端单独维护，不合入 `weifu-refactor`。

同一个模块里的子包之间也有依赖规则（第 3 节），Gradle 不会检查，靠一个架构测试检查（第 6 节第 2 步）。

---

## 3. 模块结构（11 个）

```
第 4 层  :app                                      装配
第 3 层  :feature-chat       对话（含角色）
         :feature-content    内容（视频 + 频道）
         :feature-user       账户与设置
         :feature-photosolve 拍照解题
         :feature-companion  角色扮演伴侣
第 2 层  :core               业务公共
第 1 层  :media              媒体能力：语音、图像、投屏、媒体查看
         :chatkit            对话 UI 组件
         :ads                广告
第 0 层  :common             工具 + 网络 + 通用视图
```

### 模块间的依赖规则
1. 只能依赖下层。
2. 同层模块不互相依赖。例外：`:chatkit` 可以依赖 `:media`（用里面的媒体查看组件）。
3. **第 0、1 层不能依赖 `:core`**。业务配置、业务事件只能从上往下传参或回调。
4. **feature 之间不互相依赖**。
5. **第三方库谁用谁声明**，用 `implementation`。只有对外接口里出现了库的类型，才用 `api`。

### 模块内子包的依赖规则
| 模块 | 规则 |
|---|---|
| `:common` | `util/` 不能 import `net/`、`ui/`；`net/` 和 `ui/` 只能 import `util/`，彼此不 import |
| `:media` | 四个子包互不 import，都只依赖 `:common` |
| `:feature-content` | `video/` 不能 import `channel/`；`channel/` 可以 import `video/`（打开视频页） |
| `:feature-chat` | `roles/` 不能 import 对话的其它子包 |

---

### `:common` `com.openapi.common`（由 `:commonutil` 改造）
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
  theme/      AppMaterialTheme、Tokens（从 :core/design 移来）
  dialog/     AppDialogs
  compose/    ListShell、WebviewScreen（从 :core 移来）
  widget/     CircleImageView、StateButton、IndicatorView、MarqueeTextView、MyEditText、MyScrollView、
              SlidingLayout、MyWebView、SiteAutoCompleteTextView、MyRadarScanView
  recycler/   DividerItemDecoration、touchhelper/*、BaseRecyclerViewAdapter
  permission/ 权限请求（合并三套）
res/          通用布局（footer、layout_progress）和通用图标
```
**不放**：业务事件、接口地址、收藏视图、音乐可视化、字幕覆盖层。
**要改的耦合**：`BaseRequest` 收到「需要登录」的响应时，改为调用回调 `AuthFailureListener`，由 `:core` 注册后再发业务事件；请求头里的 Token 名、域名改为启动时注入。
**第三方库**：Gson、EventBus、OkHttp、Glide、Material、Compose、RecyclerView、Shimmer。

### `:media` `com.openapi.media`（合并 `:asr`、`:mediaview` 和两块搬出来的代码）
```
speech/
  asr/        火山引擎流式识别（原 :asr）
  tts/        设备朗读（原 :asr/tts）
  record/     VoiceRecorder（从 :core/media 移来）
  play/       AudioPlayer（从 :core/media 移来）
imaging/      图像处理（从 :core 的 com/aimath/paizhao/photosolve 和 core/image 移来）
  compress/   ImageCompressor
  pipeline/   ImagePipeline、ImageUploadEncoder、ImageUtils
  quality/    ImageQualityAnalyzer、QuestionImagePreparer
  crop/       CropNorm、CropAssist*（含 DocQuadNet 模型）
  superres/   RealEsrganUpscaler、SuperResTiler、TileGeometry
cast/
  dlna/       从 :core/cast 移来
  googlecast/ 从 :feature-video/cast 移来
viewer/       MediaStack、MediaCarousel、MediaOverlap、MediaVideoDialog（原 :mediaview）
```
**为什么合成一个**：四块都是「处理音频、图片、视频」，都和业务无关，使用方也重叠（对话用语音和图像，视频用语音和投屏）。它们带的重库（Media3、ONNX、Cast SDK）在 App 里本来就都要打包，分开不能减少 APK 体积。
**第三方库**：Media3、ONNX Runtime、Cast SDK、OkHttp（识别用 WebSocket）。Cast SDK 现在只在 `:app` 里声明，`:feature-video` 是间接拿到的，搬迁时要在 `:media` 里显式声明。
**之前同意的 `:asr` 改名 `:speech`**：改为成为 `:media` 里的 `speech/` 子包，不再单独建模块。

### `:chatkit` `com.openapi.chatkit`
不变。对话 UI 组件库（消息列表、气泡、输入栏、录音浮层、附件面板），16 个文件，依赖 `:media` 的 `viewer/`。
**为什么不并进 `:common/ui`**：它是一整套对话界面，有自己的数据模型（`ChatItem`），只有对话用。

### `:ads`
不变。两套广告 SDK 的封装，27 个文件。单独成模块是因为广告 SDK 很重。

### `:core` `com.openapi.core`
```
config/     Constants、CommonConstants（合并）、接口地址
model/      多个 feature 共用的数据对象：RecommendVo、SoundItemsVo、CharacterVo、NullReturnVo、ChatLog 等
db/         Room：AppDatabase、DAO、FavoriteVo（实体从 :commonutil 移来）
event/      业务事件：UserNeedLogin、FavoriteChangeEvent、ConfigActionEvent、PermEvent 等
favorite/   FavoriteStore、FavoriteUtil、MyFavoriteView、FavoriteImageView
account/    VIP、登录状态（取代 AppHooks.isVip）
ui/base/    Activity、Fragment 基类（带广告、工具栏）
app/        AppContext
```

### feature 模块
| 模块 | 内容 | 变化 |
|---|---|---|
| `:feature-chat` | 对话 + `roles/` 子包（角色目录、选择面板） | 并入 `:feature-roles`（7 个文件，只有对话和 `:app` 用） |
| `:feature-content` | `video/` 子包（播放器、视频页、本地视频、字幕翻译）+ `channel/` 子包（推荐、频道、收藏列表、搜索、声音） | 合并 `:feature-video`、`:feature-channel`。频道打开视频页是同一个模块内的调用，去掉一条 feature 依赖 |
| `:feature-user` | 登录、注册、设置、反馈 | 不变 |
| `:feature-photosolve` | 拍照解题流程 | 不变（带 ML Kit、easycrop） |
| `:feature-companion` | 角色扮演伴侣 | 不变（带 LiveKit；Android 端按约定在自己的分支开发） |

功能专属的视图放在该 feature 的 `ui/widget/`，例如 `video/ui/widget/` 放字幕覆盖层，`channel/ui/widget/` 放 `MusicVisualizer`。

feature 内部的目录模板：`data/`、`domain/`（可选）、`presentation/`、`ui/`。

### `:app`
不变：进程入口、依赖装配、首页外壳。

---

## 4. 模块数量对照

| 现在（15 个） | 第 3 版（11 个） |
|---|---|
| `:commonutil`、`:sharekit` | `:common` |
| `:asr`、`:mediaview`（+ `:core` 的图像、投屏、录音播放） | `:media` |
| `:chatkit` | `:chatkit` |
| `:ads` | `:ads` |
| `:core` | `:core` |
| `:feature-chat`、`:feature-roles` | `:feature-chat` |
| `:feature-video`、`:feature-channel` | `:feature-content` |
| `:feature-user`、`:feature-photosolve`、`:feature-companion` | 不变 |
| `:app` | `:app` |

---

## 5. 第三方库重新归属

| 库 | 现在 | 改为 |
|---|---|---|
| GSY 播放器 | `:commonutil` api | `:feature-content` implementation。`:feature-chat` 只借用了它的一张错误图，改用自己的图 |
| PictureSelector | `:commonutil` api | `:feature-content`（只有频道的 `IndexRecommendAdapter`、`ShowTypeHandle` 用，这两个会下沉到频道） |
| PictureSelector `compress`、`ucrop`、`camerax` | `:commonutil` api | 删除，无使用 |
| BRVAH、shape-imageview、circleimageview、swiperefreshlayout | `:commonutil` | 删除，无使用 |
| Room | `:commonutil` api | `:core` |
| PhotoView | `:commonutil` api | `:feature-content` |
| Gson、EventBus、OkHttp | `:commonutil` api | `:common` api |
| Glide | `:commonutil` api | `:common` api（对外接口有 Glide 类型） |
| cardview | `:commonutil` | `:common` |
| Media3、ONNX、Cast SDK | 分散在 `:app`、`:core`、`:feature-video`、`:mediaview` | `:media`（`:feature-content` 的播放器也要 Media3，自己声明） |

XML 布局、Manifest、混淆规则里引用的库，文本扫描不一定找得到。每移一个库都跑一次完整构建。

---

## 6. 迁移顺序

每步单独提交，每步完整构建加手机冒烟。

| 步 | 内容 | 风险 |
|---|---|---|
| 1 | 删 7 个无用第三方库和 `util/cache/` 4 个死类 | 低 |
| 2 | 加一个架构测试：检查第 3 节的模块内子包规则和「第 0、1 层不 import `:core`」 | 低 |
| 3 | 底层去业务：`LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 不读 `CommonConstants`；`BaseRequest` 改回调 | 中 |
| 4 | 业务类从 `:commonutil` 上移到 `:core`：收藏、业务事件、`FavoriteVo`、`CommonConstants` | 中 |
| 5 | `:commonutil` 内部按 `util/`、`net/`、`ui/` 整理子包；并入 `:sharekit`；`:core` 的设计系统和通用视图移进 `ui/` | 中 |
| 6 | 去重：权限、`FileUtils`、屏幕尺寸、分享 | 中 |
| 7 | 建 `:media`：并入 `:asr`、`:mediaview`，移入图像、投屏、录音播放 | 中 |
| 8 | `:feature-roles` 并入 `:feature-chat` | 低 |
| 9 | `:feature-video`、`:feature-channel` 合并为 `:feature-content` | 中 |
| 10 | 第三方库按第 5 节重新归属 | 中 |
| 11 | `:core` 里单一 feature 的东西下沉 | 中 |
| 12 | 去掉 `AppHooks`，打断剩下的 feature 依赖（见第 7 节） | 高，需要改基类 |
| 13 | 改名和包名：`:commonutil` → `:common`，包名统一为 `com.openapi.<模块>` | 高，import 改动最多 |

---

## 7. `AppHooks` 怎么去掉

`AppHooks` 有 4 个方法，5 处调用：

| 方法 | 调用方 | 处理 |
|---|---|---|
| `isVip()` | `:core` 的 `BaseToolbarMenuActivity` | 改为读 `:core/account` |
| `openGlobalSearch()` | `:core` 的 `BaseToolbarMenuActivity` | 基类只留菜单槽位，搜索入口由 `:feature-content` 的子类填 |
| `openSetting()` | `:core` 的 `BaseBannerAdsActivity`、视频播放器 | 用 Intent action 打开（`:feature-user` 在 Manifest 里注册），调用方不认识页面类 |
| `openLogin()` | 没有调用方 | 删除 |

合并 `:feature-content` 后，剩下的 feature 依赖只有 `:feature-content → :feature-user`（打开登录页、读登录状态）和 `:feature-user → :feature-content`（设置页读播放引擎）。登录状态放 `:core/account`，打开登录页和读播放引擎设置都用 Intent action 或 `:core` 里的偏好项，两条都能去掉。

---

## 8. 已确认的决定（2026-11-10）

1. 先清 `:app` 的重复资源：**已完成**（`eaf374ba`，删 73 个字节级相同的副本）。
2. 包名统一为「包名 = 模块名」：同意，放最后。
3. `:asr` 改名 `:speech`：同意。第 3 版改为 `:media` 的 `speech/` 子包。
4. `UnsafeOkHttpClient` 只搬位置，不改行为：同意。
5. 工具、网络、通用视图放一个模块，用子包分开：同意（第 3 版采纳）。
6. 相似的独立能力合并，用子包分开：同意（第 3 版采纳）。

## 9. 待你确认

1. 语音、图像、投屏、媒体查看合成一个 `:media`，可以吗？
2. `:chatkit`、`:ads` 保持独立，可以吗？
3. `:feature-roles` 并入 `:feature-chat`；`:feature-video`、`:feature-channel` 合并为 `:feature-content`，可以吗？
4. 「第 0、1 层不能依赖 `:core`」这条规则要改基础代码（`LogUtil`、`BaseRequest` 等），可以吗？
5. `AppHooks` 按第 7 节去掉，可以吗？

## 不在这次范围
- Volley 和 OkHttp 两套网络并存：只记录，不合并。
- `:auxio_music_ui`、`:retromusic` 内部结构不动。
