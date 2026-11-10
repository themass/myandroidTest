# 架构标准

这份文档定义**目标结构和判断规则**。具体怎么一步步改过去，见 [refactor-plan.md](refactor-plan.md)。

基于 `weifu-refactor`（2026-11-10）。本文合并并取代了之前的 `module-standard.md`、`module-analysis.md`、`core-split-plan.md`、`module-layout.md`。

---

## 1. 目标

循序渐进地把项目改好：
1. **删除**无用的代码、资源、依赖。
2. **简化**：一件事只有一份实现，一个类只做一件事。
3. **沉淀**通用组件和库，业务代码只写业务。
4. **升级替换**陈旧的技术（见第 6 节）。

每一步都要能编译、能在手机上用，随时可以停下。

---

## 2. 代码的 6 个种类

每个类必须属于下面一种，种类决定它放在哪。

| 种类 | 判断标准 | 放到 |
|---|---|---|
| **A. 纯工具** | 不认识业务名词；不画界面；不发网络请求 | `:common/util` |
| **B. 网络** | HTTP 收发、图片加载的传输层 | `:common/net` |
| **C. 通用视图** | 与业务无关的 UI 部件，不带重库，换个 App 也能用 | `:common/ui` |
| **D. 独立能力** | 自成体系、与业务无关；有对外接口，或带重库、模型 | `:media`、`:chatkit`、`:ads` |
| **E. 业务公共** | 含业务名词或依赖接口配置，**并且**至少两个 feature 用 | `:core` |
| **F. 功能专属** | 只有一个模块用 | 那个模块 |

**判定顺序**（命中即停）：只有一个模块用 → F；含业务 → E；独立能力 → D；是界面 → C；是网络 → B；其余 → A。

---

## 3. 模块结构（12 个）

```
第 4 层  :app                   进程入口、启动页、首页框架、路由汇总
第 3 层  :feature-chat          对话（含 roles/）
         :feature-content       内容频道（视频/图片/文字/声音）、收藏、推荐（原 :feature-channel）
         :feature-video         视频播放、投屏
         :feature-user          账户、设置、反馈
         :feature-photosolve    拍照解题
         :feature-companion     角色扮演伴侣（在自己的分支开发，这次不动）
第 2 层  :core                  应用配置、共享业务数据、旧页面框架（逐步删除）
第 1 层  :media                 speech/ subtitle/ imaging/ viewer/
         :chatkit               对话 UI 组件
         :ads                   广告
第 0 层  :common                util/ net/ ui/（原 :commonutil，并入 :sharekit）
```
`:retromusic`（本地音乐）保持独立模块：可以改，但不强行统一风格、不拆包、不合并进别的模块。`:auxio_music_ui` **删除**（和频道声音页功能重复，见 refactor-plan.md 第 0 节）。

**单独建模块的条件**（默认用子包）：带重的第三方库或原生库；需要构建层面强制依赖方向；有独立的开发分支。

### 3.1 依赖规则
1. 只依赖下层。同层不互相依赖（例外：`:chatkit` → `:media/viewer`）。
2. **第 0、1 层不依赖 `:core`**。业务配置和事件从上往下传参或回调。
3. **feature 之间不互相依赖**。打开别的功能的页面用路由（Intent action 或类名字符串，`ChatRoute` 打开拍照解题已经这样做）。
4. **第三方库谁用谁声明**，用 `implementation`；只有对外接口里出现库的类型才用 `api`。
5. 去掉 `AppHooks`：`isVip` 读 `:core/account`；搜索入口由子类填进菜单槽位；设置页用路由。

### 3.2 子包规则（Gradle 不检查，靠架构测试）
| 模块 | 规则 |
|---|---|
| `:common` | `util/` 不 import `net/`、`ui/`；`net/`、`ui/` 只 import `util/` |
| `:media` | `subtitle/` 可以 import `speech/`；其余互不 import |
| `:feature-chat` | `roles/` 不 import 对话的其它子包 |
| `:feature-content` | `video/ image/ text/ sound/ config/` 只 import `common/`；`mine/` 可以 import 各类型子包，反过来不行 |

### 3.3 包名
包名 = 模块名：`com.openapi.<模块>`，feature 为 `com.openapi.feature.<名>`。`:ads` 改为 `com.openapi.ads`，其 `Constants` 改名 `AdsIds`。**放到最后一步统一改**，之前搬文件时包名保持不变，避免两种改动混在一起。

---

## 4. 各模块的目标内部结构

### `:common` `com.openapi.common`
```
util/  lang/ android/ log/ json/ event/ share/
net/   http/ request/ dns/ glide/ unsafe/
ui/    theme/ dialog/ image/ widget/ recycler/ permission/
```
**不放**：业务事件、接口地址、收藏视图、字幕覆盖层、带 Media3 的组件。

### `:media` `com.openapi.media`
```
speech/    asr/（火山引擎识别） tts/（设备朗读） record/ play/
subtitle/  api/（翻译接口） overlay/（覆盖层） realtime/（实时字幕引擎、PCM 音频源） provider/
imaging/   compress/ pipeline/ quality/ crop/ superres/
viewer/    MediaStack、MediaCarousel、MediaOverlap、MediaVideoDialog
```

### `:core` `com.openapi.core`
定义：应用配置 + 多个功能共享的业务数据 + 旧页面框架。
```
config/   Constants、BaseRes、Token
app/      AppContext、VersionUpdater
account/  登录状态、VIP（取代 UserLoginUtil 和 AppHooks.isVip）
prefs/    跨功能偏好（播放引擎等）
model/    跨功能数据对象
db/       Room 数据库、DAO、实体
event/    业务事件
favorite/ 收藏存储与收藏视图
legacyui/ View 体系的 Activity/Fragment 基类，新代码不用；旧页面非必要不换，所以会长期存在
```

### feature 模块
```
com.openapi.feature.<名>
  <Name>Dependencies.kt     依赖入口，由 :app 注册
  data/                     仓库实现、网络接口、本功能的数据对象
  domain/                   模型、仓库接口、用例（逻辑简单时省略）
  presentation/             ViewModel、UiState
  ui/                       页面；功能专属的视图放 ui/widget/
```
功能大时先按功能分子包（如 `:feature-content` 的 `video/ image/ text/`），每个子包里再用上面的模板。

### `:app` `com.openapi.app`
```
App（原 MyApplication）  LaunchActivity  AppEventHandler
home/    首页框架、侧边栏
route/   汇总各 feature 的路由表
```
Manifest 只登记启动页和首页，其余由各 feature 自己的 Manifest 声明。不直接声明 feature 用的第三方库。

---

## 5. 类的大小和职责

| 规则 | 说明 |
|---|---|
| 一个类只做一件事 | ViewModel 负责状态和用户操作的入口；网络、缓存、录音这些交给专门的类 |
| 超过 400 行要审视 | 不是硬上限。超过时要能说清它为什么不能拆 |
| 不新建 `XxxUtils` 杂物类 | 一个主题一个文件，名字说清做什么（`ScreenSize`，不是 `Utils.getScreenWidth`） |
| 同一件事只留一份 | 新写之前先查有没有现成的 |
| 优先用 Kotlin 标准库 | `isNullOrEmpty`、`orEmpty` 这类能取代的工具方法不再自己写 |

---

## 6. 技术选型

| 领域 | 现在 | 目标 | 说明 |
|---|---|---|---|
| 界面 | View + Fragment（Java）与 Compose 并存 | 新页面用 **Compose** | 旧页面**非必要不换**。只有在要改它的功能、或者它挡住了别的整理时才重写 |
| 语言 | Java 319 个文件，Kotlin 142 个 | **Kotlin** | 改到哪个文件，顺手转；不做批量转换 |
| 异步 | 回调、`Handler`、`AsyncTask` | **协程** | |
| 事件 | EventBus（10 种事件） | **Flow / StateFlow** | 一种事件一种事件地换，换完删依赖 |
| 网络 | OkHttp + 仿 Volley 命名的回调封装（`BaseService` → `NetQueue` → OkHttp） | OkHttp + **挂起函数**，类名去掉 Volley 痕迹 | 不引入 Retrofit。整理方案见 refactor-plan.md 第 5.8 节 |
| 视频播放 | GSY 播放器，带 4 个引擎（Exo2、IJK/FFmpeg、阿里、系统） | **GSY 不变，4 个引擎都保留** | 不换成 Media3。只整理代码结构（拆大类、移对位置），不改播放引擎 |
| 音乐播放 | 频道音乐：`MediaPlayer` 的 `PlayService`；`:retromusic`：Media3 | 频道音乐不变；`:retromusic` 不强行改 | `:auxio_music_ui` 删除 |
| 图片 | Glide | Glide（不变） | Compose 里用 Glide 的 Compose 集成或 Coil，二选一，不两个都用 |
| 页面传参 | `Serializable` | `Parcelable` 或只传 id | |
| 数据库 | Room | Room（不变） | |
| 依赖注入 | 手写 `XxxDependencies` | 手写（不变） | 规模不需要 Hilt |

---

## 7. 已确认的决定（2026-11-10）

1. `:app` 的重复资源先清：已完成（`eaf374ba`）。
2. 工具、网络、通用视图合成 `:common`，用子包分开。
3. 相似的独立能力合并：`:media` = 语音、字幕翻译、图像、媒体查看。投屏放 `:feature-video`。
4. `:chatkit`、`:ads` 保持独立；`:feature-roles` 并入 `:feature-chat`。
5. 视频和频道不合并；`:feature-channel` 改名 `:feature-content`，按内容类型分子包。
6. 第 0、1 层不依赖 `:core`；feature 之间不互相依赖；去掉 `AppHooks`。
7. `:asr` 改名 `:speech`：改为 `:media/speech/`。
8. `UnsafeOkHttpClient` 只搬位置，不改行为。
9. 包名统一放最后；`:ads` 改为 `com.openapi.ads`，`Constants` 改名 `AdsIds`。
10. 拍照解题用应用统一主题：已完成（`db9633bf`）。
11. 删除 `AutoVideoListFragment`：已完成（`db9633bf`）。
13. 旧页面框架暂留 `:core/legacyui`；对话的 Room 实体留 `:core/db`。
14. **视频播放保留 GSY 和全部 4 个引擎**，不换 Media3。
15. 旧 Java 页面**非必要不换**成 Compose。
16. `:retromusic` 可以改，但不强行统一风格、不拆包、不合并。GPL 协议不作为考虑因素。`:auxio_music_ui` 删除。
17. `ObjectUtils`、`StringUtils` 等工具类要整理（瘦身、去重）。
18. 网络层要整理：去掉仿 Volley 的命名，加挂起函数接口。
19. 公式渲染：先比较自定义插件和 Markwon 官方 `ext-latex` 的差别，再决定是否换。
20. `ChatViewModel`、`ChatRepositoryImpl` 按 refactor-plan.md 第 5.1、5.2 节拆分；两个对话 bug 拆完先补单元测试再修。
