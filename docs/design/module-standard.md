# 模块标准与归属设计（第 2 版，待确认）

状态：**设计稿，未动代码**。基于 `weifu-refactor` 的 `debdf79f`。
数据来源：按文件的 import 归类（`/tmp/deadscan/classify.py`），按类名统计使用方（`/tmp/clsusers.py`）。脚本有误判，每一步搬迁都以编译为准。

---

## 0. 对第 1 版的评审

第 1 版只回答了「哪个类放哪个模块」，没有回答「一个类是什么」。所以 `:commonutil` 里的东西还是一锅端：工具、视图、网络、业务事件、数据库实体、第三方库全在里面。具体问题：

| 问题 | 事实 |
|---|---|
| 没有区分工具、视图、网络 | `:commonutil` 同时有 41 个工具类（含缓存、权限）、23 个自定义 View、10 个字幕翻译类、27 个网络类、9 个布局 |
| 工具层反向依赖业务 | `LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 都 import `CommonConstants`（里面是接口域名、Token 头名这些业务配置）；`BaseRequest` 依赖 `event/UserNeedLogin`（登录事件） |
| 数据库实体在工具层 | `event/FavoriteVo` 是 Room 实体，放在 `:commonutil` |
| 工具层用 `api` 暴露十几个重库 | GSY 播放器（带原生 so）、PictureSelector 全家、Room、Glide、PhotoView、BRVAH 等。结果 `:asr`、`:ads` 这样的小模块也被动依赖了播放器 |
| 其中有 7 个库没人用 | PictureSelector 的 `compress`、`ucrop`、`camerax`，`BaseRecyclerViewAdapterHelper`，`android-shape-imageview`，`circleimageview`（项目自己写了一个同名类），`swiperefreshlayout` |
| 视图散在 6 处 | `:commonutil/view`、`:core/design`、`:core/view`、`:core/ui/gsy/compose`、`:core/ui/channel/WebviewScreen`、`:mediaview` |
| 同一件事多份实现 | 权限 3 套；`FileUtils` 2 份；屏幕尺寸 3 处（`DensityUtil`、`DisplayUtil`、`Utils`）；分享 2 套（`ShareUtil` 和 `:sharekit`） |
| 「能力库」没有准入标准 | 为什么 `:asr` 是库、投屏不是库、图像处理要不要成库，第 1 版靠感觉定 |
| 死代码 | `util/cache/` 4 个磁盘缓存类在模块外没有任何使用方 |

**结论**：要先给「类的种类」定标准，再按种类定模块。下面按这个顺序重写。

---

## 1. 先分种类：每个类必须是下面 6 种之一

| 种类 | 定义 | 判断标准 | 例子 |
|---|---|---|---|
| **A. 纯工具** | 无状态的辅助函数 | 不认识任何业务名词；不画界面；不发网络请求 | `StringUtils`、`DateUtils`、`Md5`、`PreferenceUtils` |
| **B. 网络** | HTTP 收发和图片加载的传输层 | 依赖 OkHttp/Volley/Glide 的网络部分 | `NetQueue`、`BaseService`、`HttpDNSUtil`、`MyGlideModule` |
| **C. 通用视图** | 与业务无关的 UI 部件 | 是 View、Compose 组件、主题、通用对话框，换个 App 也能用 | `CircleImageView`、`StateButton`、`AppMaterialTheme`、`ListShell` |
| **D. 独立能力** | 一整块自成体系的功能，对外有清晰接口 | 满足第 2 节的「独立包准入条件」 | 语音、图像处理、投屏、对话 UI、媒体查看、分享、广告 |
| **E. 业务公共** | 多个功能共用的业务概念 | 含业务名词（收藏、登录、VIP、角色），或依赖接口域名配置 | `Constants`、`FavoriteVo`、`RecommendVo`、Activity 基类 |
| **F. 功能专属** | 只服务一个功能 | 只有一个 feature 用 | `LoginForm`、`ImgItemsVo`、`VideoTranslationProvider` |

**判定顺序**（从上往下，命中即停）：
1. 含业务名词或依赖业务配置？只有一个 feature 用 → **F**；多个用 → **E**。
2. 满足独立包准入条件？ → **D**。
3. 是界面（View、Compose、主题、布局资源）？ → **C**。
4. 是网络传输？ → **B**。
5. 剩下的 → **A**。

---

## 2. 独立包准入条件（D 类）

满足**全部**三条，才单独成模块：
1. **不含业务**：不 import `:core` 和任何 feature。
2. **自成体系**：有自己的对外接口，内部实现可以整体替换。
3. **值得隔离**：至少满足一条：两个以上模块用；带重的第三方库或原生库；有自己的资源或模型文件。

不满足的，作为包放进使用它的模块里。

按这个标准核对：

| 候选 | 不含业务 | 自成体系 | 值得隔离 | 结论 |
|---|---|---|---|---|
| 语音（识别 + 朗读 + 录音 + 播放） | 是 | 是 | 对话、视频字幕都用 | **`:speech`**（`:asr` 改名，并收进 `core/media` 的录音和播放） |
| 图像处理（压缩、质量、裁剪、超分） | 是 | 是 | 拍照、对话都用；超分和自动裁剪带推理模型（`RealEsrganUpscaler`、`DocQuadNetCropAssistProvider`） | **`:imaging`**（新建） |
| 投屏（DLNA + Google Cast） | 是 | 是 | 只有视频用，但带 Cast SDK 和本地 HTTP 服务器 | **`:cast`**（新建）。注意：Cast SDK 现在只在 `:app` 的 build.gradle 里声明，`:feature-video` 编译时是从别处间接拿到的，搬迁时要在 `:cast` 里显式声明 |
| 对话 UI | 是 | 是 | 已经是模块 | **`:chatkit`**（保留） |
| 媒体查看（叠放、轮播、视频对话框） | 是 | 是 | 对话、频道都会用 | **`:mediaview`**（保留） |
| 分享 | 是 | 是 | 多处用 | **`:sharekit`**（保留，合并 `ShareUtil`） |
| 广告 | 是 | 是 | 带两套广告 SDK | **`:ads`**（保留） |
| 字幕翻译覆盖层 | 否（视频专属概念） | — | — | 不成库，放 `:feature-video` |
| 磁盘缓存 | — | — | 没人用 | 删除 |
| 权限 | 是 | 是 | 但只有几个类，没有重依赖 | 不成库，放 `:uikit/permission` |

---

## 3. 模块结构

```
第 5 层  :app
第 4 层  :feature-chat  :feature-roles  :feature-photosolve  :feature-user
         :feature-video :feature-channel :feature-companion
第 3 层  :core                                   E 类：业务公共
第 2 层  :speech :imaging :cast :chatkit :mediaview :sharekit :ads   D 类：独立能力
第 1 层  :uikit                                  C 类：通用视图
         :network                                B 类：网络
第 0 层  :common                                 A 类：纯工具
```

`:commonutil` 拆成 `:common`、`:network`、`:uikit` 三个；`:core` 里的设计系统和通用视图移到 `:uikit`。

### 依赖规则
1. 只能依赖下层。
2. 同层不互相依赖。例外：`:chatkit` 可以依赖 `:mediaview`（都是 UI 能力，现状如此）。
3. **第 0–2 层不能 import `:core`**。这是让底层干净的关键：业务配置、业务事件只能从上往下传参。
4. **feature 之间不互相依赖**。
5. **第三方库谁用谁声明**，用 `implementation`。只有对外接口里出现了库的类型，才用 `api`。

### 各模块的内容

#### `:common`（A 类）`com.openapi.common`
```
lang/      纯 JVM：StringUtils、DateUtils、CollectionUtils、ObjectUtils、Md5、AES2、VersionCompare
android/   需要 Context：PreferenceUtils、FileUtils（合并两份）、PathUtil、PackageUtils、DeviceInfoUtils、
           DensityUtil（合并 DisplayUtil、Utils 的屏幕尺寸）、ToastUtil、AudioMngHelper、Background
log/       LogUtil（去掉对 CommonConstants 的依赖，DEBUG 开关由 :app 启动时传入）
json/      GsonUtils
event/     EventBusUtil（只放总线封装，不放具体事件类）
```
**不放**：任何 View 子类、布局资源、业务事件、接口地址。
**第三方库**：Gson、EventBus。

#### `:network`（B 类）`com.openapi.network`
```
http/       NetQueue、NetRequest、NetResponse、Response、SyncFuture、BaseService、HttpUtils、NetUtils、NetError
request/    BaseRequest、GsonRequest、MultipartRequest、StringRequest、CommonResponse、JsonResult、InfoListVo
dns/        HttpDNSUtil、DnsRequestInterceptor、OkHttpDns
glide/      MyGlideModule、OkHttpUrlLoader、OkHttpStreamFetcher、MyGlideDiskCacheFactory
unsafe/     UnsafeOkHttpClient（只搬位置，不改行为）
```
**要改的耦合**：`BaseRequest` 收到「需要登录」的响应时，现在直接发 `UserNeedLogin` 事件。改为回调接口 `AuthFailureListener`，由 `:core` 注册后再发业务事件。请求头里的 Token 名、域名改为启动时注入，不再读 `CommonConstants`。
**第三方库**：OkHttp、Glide（含 okhttp3 集成）。

#### `:uikit`（C 类）`com.openapi.uikit`
```
theme/        AppMaterialTheme、Tokens（从 :core/design 移来）
dialog/       AppDialogs
compose/      ListShell、WebviewScreen（从 :core 移来）
widget/       CircleImageView、StateButton、IndicatorView、MarqueeTextView、MyEditText、MyScrollView、
              SlidingLayout、MyWebView、SiteAutoCompleteTextView、MyRadarScanView
recycler/     DividerItemDecoration、touchhelper/*、BaseRecyclerViewAdapter
permission/   PermissionRequester（合并三套权限）
res/          通用布局（footer、layout_progress）和通用图标
```
**不放**：收藏按钮（`MyFavoriteView`、`FavoriteImageView`，是业务）、音乐可视化（`MusicVisualizer`，只有频道用）、字幕覆盖层（视频专属）。
**第三方库**：Material、Compose、RecyclerView、Shimmer。

#### 独立能力（D 类）
| 模块 | 包名 | 内容 |
|---|---|---|
| `:speech` | `com.openapi.speech` | `asr/`（火山引擎流式识别）、`tts/`（设备朗读）、`record/`（`VoiceRecorder`）、`play/`（`AudioPlayer`） |
| `:imaging` | `com.openapi.imaging` | `compress/`（`ImageCompressor`）、`pipeline/`、`quality/`、`crop/`、`superres/`（含推理模型） |
| `:cast` | `com.openapi.cast` | `dlna/`（从 `:core/cast` 移来）、`googlecast/`（从 `:feature-video/cast` 移来）、`ui/` |
| `:chatkit` | 不变 | 不动 |
| `:mediaview` | 改为 `com.openapi.mediaview` | 不动内容 |
| `:sharekit` | 改为 `com.openapi.sharekit` | 合并 `ShareUtil` |
| `:ads` | 不变 | 不动 |

#### `:core`（E 类）`com.openapi.core`
```
config/     Constants、CommonConstants（合并）、接口地址
model/      多个 feature 共用的数据对象：RecommendVo、SoundItemsVo、CharacterVo、NullReturnVo、ChatLog 等
db/         Room：AppDatabase、DAO、FavoriteVo（实体从 :commonutil 移来）
event/      业务事件：UserNeedLogin、FavoriteChangeEvent、ConfigActionEvent、PermEvent、data/config 下的共用事件
favorite/   FavoriteStore、FavoriteUtil、MyFavoriteView、FavoriteImageView（收藏是业务，视图跟着业务走）
account/    VIP、登录状态（供基类和 feature 读取，取代 AppHooks.isVip）
ui/base/    Activity、Fragment 基类（带广告、工具栏）
app/        AppContext
```
**不放**：通用视图（去 `:uikit`）、单一 feature 的东西（下沉）。

#### `:feature-*`（F 类）
目录模板不变：`data/`、`domain/`（可选）、`presentation/`、`ui/`。功能专属的视图放在该 feature 的 `ui/widget/` 下，例如 `:feature-video/ui/widget/` 放字幕覆盖层，`:feature-channel/ui/widget/` 放 `MusicVisualizer`。

#### `:app`
不变：进程入口、依赖装配、首页外壳。

---

## 4. 第三方库重新归属

| 库 | 现在 | 改为 |
|---|---|---|
| GSY 播放器 | `:commonutil` api | `:feature-video` implementation。`:feature-chat` 只借用了它的一张错误图，改用自己的图 |
| PictureSelector | `:commonutil` api | `:core` implementation（只有 `IndexRecommendAdapter`、`ShowTypeHandle` 用，这两个最终会下沉到 `:feature-channel`，库跟着走） |
| PictureSelector `compress`、`ucrop`、`camerax` | `:commonutil` api | 删除，无使用 |
| Room | `:commonutil` api | `:core` implementation |
| Glide | `:commonutil` api | `:network` api（对外接口有 Glide 类型） |
| PhotoView | `:commonutil` api | `:feature-channel` implementation |
| BRVAH、shape-imageview、circleimageview、swiperefreshlayout | `:commonutil` | 删除，无使用 |
| cardview | `:commonutil` | `:uikit` |
| Gson、EventBus | `:commonutil` api | `:common` api |
| OkHttp | `:commonutil` api | `:network` api |

注意：XML 布局里直接写类名引用的库（比如 PhotoView 写在布局里），文本扫描能找到，但 Manifest 合并或混淆规则里引用的不一定能找到。每移一个库都跑一次完整构建。

---

## 5. 命名规则

1. 不再新建 `XxxUtils` 杂物类。一个主题一个文件，名字说清做什么：`ScreenSize` 而不是 `Utils.getScreenWidth`。
2. 同一件事只保留一份。要合并的：`FileUtils` × 2、屏幕尺寸 × 3、权限 × 3、分享 × 2、`ImageUtils`（`:common` 与 `:imaging`）。
3. 包名 = 模块名：`com.openapi.<模块>`。放在最后一步改。

---

## 6. 迁移顺序

每步单独提交，每步完整构建加手机冒烟。

| 步 | 内容 | 风险 |
|---|---|---|
| 1 | 删 7 个无用第三方库和 `util/cache/` 4 个死类 | 低 |
| 2 | 去掉工具层对业务的依赖：`LogUtil`、`FileUtils`、`SystemUtils`、`MyWebView` 不再读 `CommonConstants`；`BaseRequest` 改为回调 | 中，改的是基础代码 |
| 3 | 拆 `:commonutil` 为 `:common`、`:network`、`:uikit`（包名暂不改） | 中，依赖关系变化大 |
| 4 | 业务类从 `:commonutil` 上移到 `:core`：收藏、业务事件、`FavoriteVo`、`CommonConstants` | 中 |
| 5 | `:core` 的设计系统和通用视图移到 `:uikit` | 低 |
| 6 | 去重：权限、`FileUtils`、屏幕尺寸、分享 | 中，调用点多 |
| 7 | `:asr` 改名 `:speech`，收进录音和播放 | 低 |
| 8 | 新建 `:imaging`、`:cast` | 低 |
| 9 | 第三方库按第 4 节重新归属 | 中 |
| 10 | `:core` 里单一 feature 的东西下沉 | 中 |
| 11 | 打断 feature 之间的依赖，去掉 `AppHooks`（见第 7 节） | 高，需要改基类 |
| 12 | 包名统一为「包名 = 模块名」 | 高，import 改动最多 |

---

## 7. `AppHooks` 怎么去掉

`AppHooks` 有 4 个方法，5 处调用：

| 方法 | 调用方 | 处理 |
|---|---|---|
| `isVip()` | `:core` 的 `BaseToolbarMenuActivity` | 改为读 `:core/account`，VIP 状态本来就是业务公共数据 |
| `openGlobalSearch()` | `:core` 的 `BaseToolbarMenuActivity` | 基类只留菜单槽位，搜索入口由 `:feature-channel` 的子类填 |
| `openSetting()` | `:core` 的 `BaseBannerAdsActivity`、`:feature-video` 的播放器 | 用 Intent action 打开（`:feature-user` 在 Manifest 里注册），调用方不认识页面类 |
| `openLogin()` | 没有调用方 | 删除 |

根因是 `:core` 的 Activity 基类自己带了功能行为。基类只保留工具栏、广告、权限这些通用部分后，`AppHooks` 就没有存在的理由了。

---

## 8. 已确认的决定（2026-11-10）

1. 先清 `:app` 的重复资源：**已完成**（`eaf374ba`，删 73 个字节级相同的副本）。
2. 包名统一为「包名 = 模块名」：同意，放最后。
3. `:asr` 改名 `:speech`：同意。
4. `UnsafeOkHttpClient` 只搬位置，不改行为：同意。
5. `AppHooks`：用户认为设计奇怪，根因是 feature 边界乱。第 7 节给出去掉的方案，待确认。

## 9. 待你确认

1. 第 1 节的 6 种分类和判定顺序是否可以作为标准？
2. `:commonutil` 拆成 `:common`、`:network`、`:uikit` 三个模块，可以吗？
3. 独立包准入条件（第 2 节），以及据此新建 `:imaging`、`:cast`，可以吗？
4. 第 2 条依赖规则「第 0–2 层不能 import `:core`」会让第 6 节第 2 步要改基础代码，可以吗？
5. `AppHooks` 按第 7 节去掉，可以吗？

## 不在这次范围
- Volley 和 OkHttp 两套网络并存：只记录，不合并。
- `:auxio_music_ui`、`:retromusic` 内部结构不动。
