# 第二轮重构：评审结论与执行计划

日期：2026-11-11 分支：`weifu-refactor`（基线 `89f3fbdd`）
本文取代 [architecture.md](architecture.md) 中与之冲突的条目（第 3 节模块表、第 7 节决定 2、3、7）。冲突的原因见第 2 节。

---

## 1. 回顾：目标和原则

| 目标 | 具体要求 |
|---|---|
| 组件可被别的项目复用 | 一个模块只做一件事；拿走它时，**只带上它真正需要的库**；不认识业务名词 |
| 依赖干净 | 第三方库谁用谁声明；`api` 只用于对外接口里出现的类型；不借道传递依赖 |
| 高内聚、低耦合 | 同一件事的代码在一个模块；模块之间通过接口和路由通信；feature 之间不互相依赖 |
| 简洁 | 包名 = 模块名；没有跨模块的同名包；没有死代码 |
| 行为不变 | 每一步都能编译、能跑测试；包名改动不能破坏 Manifest、字符串类名、混淆规则、桌面快捷方式 |

判断代码放哪的规则（沿用 architecture.md 第 2 节，补充一条）：
**新增：带重的第三方库或原生库的能力，单独成模块。** 否则用它的人会被迫带上所有重库。

---

## 2. 评审：已经做的哪些是合理的，哪些要调整

### 2.1 合理、保留的

- 分层方向（common → 能力 → core → feature → app）和架构测试 `ModuleGraphTest`。
- feature 之间用路由（类名字符串）打开页面，不直接依赖。
- EventBus 换成 Flow，网络层加挂起函数，`AppHooks` 删除。
- 大类拆分：`ChatViewModel`、`ChatRepositoryImpl`、`DetailGSYVideoPlayer`、`SoundItemsMusicFragment` 等。
- `:retromusic`、`:feature-companion` 不动；GSY 和 4 个引擎不动。

### 2.2 不合理、必须调整的（按严重程度）

| # | 问题 | 证据 | 后果 |
|---|---|---|---|
| P1 | **最底层 `:commonutil` 用 `api` 导出重库** | `api` GSY 播放器 3 个包（含原生 so）、PictureSelector、PhotoView、Room、Glide、OkHttp | 任何模块（包括别的项目）只想用 `LogUtil`，也会带上视频播放器和原生库。实际只有 `:feature-video` 用 GSY，只有 `:feature-content` 用 PictureSelector / PhotoView，`:commonutil` 自己不用 Room |
| P2 | **`:media` 是一个大杂烩** | 同一个模块里有 ONNX Runtime + OpenCV（拍照增强）、Media3（录音播放、实时字幕）、Coil（媒体查看）。四组代码之间**零 import** | `:chatkit` 只为了 `MediaCarousel` 依赖 `:media`，结果带上 ONNX 和 OpenCV。按“带重库单独成模块”的规则，这四组应当是四个模块 |
| P3 | **Glide 的 `AppGlideModule` 写在库模块里** | `MyGlideModule extends AppGlideModule` 在 `:commonutil`；`:retromusic` 因此不得不把自己的 Glide 模块改成普通函数（注释里写了冲突） | 违反 Glide 规定：`AppGlideModule` 只能在应用模块，库只能用 `LibraryGlideModule`。别的项目引入 `:commonutil` 会和自己的 Glide 配置冲突 |
| P4 | **`:core` 用 `api` 导出 `:ads` 和 `:commonutil`；所有 feature 用 `api` 导出 `:commonutil`** | `core/build.gradle`、7 个 feature 的 `build.gradle` | 依赖借道传递：`:feature-content`、`:feature-user` 用广告却没有声明 `:ads`；feature 没有下游，`api` 毫无意义 |
| P5 | **包名混乱，有跨模块的同名包** | 15 种前缀：`com.openapi.ks.myapp.*`、`com.aimath.paizhao.*`、`chat.ui.data.model`、`com.openapi.core.cast`（在 `:feature-video`）、`com.openapi.core.ads.yewu`（在 `:ads`）、`com.openapi.core.image`（在 `:media`）……；`com.openapi.home.*` 同时出现在 `:app`、`:feature-content`、`:feature-user`；`com.openapi.ks.myapp.translation.realtime` 同时在 `:media` 和 `:feature-video` | 看包名不知道代码在哪个模块；同名包会让类在模块间“隐形”互访（同包不需要 import），绕过模块边界 |
| P6 | **投屏协议（DLNA）埋在 `:feature-video`** | `com.openapi.core.cast` 共 11 个文件、约 735 行，只在包内互相引用，与视频页无关 | 自成体系的协议实现，是典型的可复用组件 |
| P7 | **`:feature-chat` → `:feature-roles`** | 唯一的 feature 间依赖（测试里写成“已批准”） | architecture.md 决定 4 已定“roles 并入 chat”，一直没做 |
| P8 | **`:commonutil` 名不副实，资源成了公共垃圾桶** | 模块名还是 `commonutil`；1.4 MB 资源中约 117 个无人使用，约 30 个只被某一个模块使用 | 用的人和定义的地方分离；别的项目引入会带上无关资源 |
| P9 | **死代码和错放的类** | `common/util/android/ImageUtils`（无人用，且让 util 依赖 OkHttp）、`AndroidFileUtils`（无人用）；`core/legacyui/gsy/ListShell`（与 GSY 无关）；`core` 里的 `com.openapi.ks.myapp.data`（版本更新）；Manifest 里一条值为 `MyGlideModule` 的无效 meta-data；`commonutil` 的 `fileTree('libs')` 指向不存在的目录；空的遗留目录 `auxio_music_ui/`、`messagekit/` | 增加理解成本 |
| P10 | **每个模块重复约 35 行 Android 配置** | 所有库模块的 `android {}` 几乎一样 | 改一个编译选项要改 14 个文件 |

---

## 3. 目标架构

```
第 4 层  :app                 com.openapi.app                 组合根：Application、启动页、首页、AppGlideModule
第 3 层  :feature-chat        com.openapi.feature.chat        对话（含 roles/）
         :feature-content     com.openapi.feature.content     内容频道
         :feature-video       com.openapi.feature.video       视频播放
         :feature-user        com.openapi.feature.user        账户、设置、反馈
         :feature-photosolve  com.openapi.feature.photosolve  拍照解题
         :feature-companion   （不动）
第 2 层  :core                com.openapi.core                应用配置、共享业务数据、旧页面框架
第 1 层  :chatkit             com.openapi.chatkit             对话 UI 组件            → :mediaview
         :mediaview           com.openapi.mediaview           媒体查看（Compose + Coil）
         :speech              com.openapi.speech              朗读、录音、音频播放（Media3）
         :subtitle            com.openapi.subtitle            翻译接口、字幕覆盖层、实时字幕（Media3、OkHttp、火山 ASR）
         :imaging             com.openapi.imaging             裁剪辅助、质量检测、超分（ONNX、OpenCV）
         :cast                com.openapi.cast                DLNA 投屏协议（SSDP、SOAP、HTTP）
         :ads                 com.openapi.ads                 广告、统计（GMS Ads）
         :retromusic          （不动）
第 0 层  :uikit               com.openapi.uikit               主题、对话框、通用控件、权限请求、分享（Compose、AppCompat、ZXing）
         :network             com.openapi.network             HTTP、请求封装、DNS、Glide 网络集成（OkHttp、Glide、Gson）
         :common              com.openapi.common              纯工具：lang、android、log、json、event、image（只有 Kotlin 协程和 AndroidX Core）
```

**依赖规则**（架构测试强制）：
1. 只依赖下层。同层只允许：`:chatkit → :mediaview`、`:uikit → :common`、`:network → :common`。
2. 第 0、1 层不依赖 `:core` 和任何 feature。
3. feature 之间不互相依赖（去掉“已批准”的例外）。
4. 除第 0 层和 `:core` 对外接口里出现的类型外，项目依赖一律 `implementation`。
5. 包名规则：模块内每个文件的包名必须以该模块的根包开头（`:retromusic`、`:feature-companion` 除外）。

**每个第 0、1 层模块的“拿走即用”检查**：只依赖第 0 层；不 import `com.openapi.core`、`com.openapi.feature`；不引用别的模块的 `R`。

---

## 4. 方案（按批次执行，每批编译通过后提交）

### 批次 1：死代码和构建配置
- 删除 P9 列出的死代码和无效配置。
- 新建 `gradle/android-library.gradle`，各库模块 `apply from` 它，只保留 namespace 和自己的特殊配置（`:retromusic` 不动）。
- 验证：`:app:assembleChatDebug`。

### 批次 2：依赖卫生（P1、P3、P4）
- GSY → `:feature-video`；PictureSelector、PhotoView → `:feature-content`；Room → 只在 `:core`（`api`，因为 DAO 和实体是对外接口）；从 `:commonutil` 删除这些 `api`。
- Glide：网络集成改成 `NetworkGlideModule extends LibraryGlideModule`（留在网络层）；缓存配置移到 `:app` 的 `AppGlideModule`。
- `:core` 对 `:ads`、`:media` 改 `implementation`；用广告的 feature 自己声明 `:ads`；所有 feature 对 `:commonutil` 改 `implementation`。
- 风险：XML 布局里直接用的库控件（已扫描，只有 `:core` 的 shimmer/ripple、`:feature-content` 的 cardview 等，都在各自模块已声明）。

### 批次 3：拆 `:commonutil` → `:common`、`:network`、`:uikit`（P8、P1）
- 按子包整体搬：`util/*`、`event` → `:common`；`net/*` → `:network`；`design/*`、`ui/widget`、`permission`、`share` → `:uikit`。子包之间已确认没有反向 import（util 不 import net/ui）。
- 包名同时改为 `com.openapi.common.*` / `com.openapi.network.*` / `com.openapi.uikit.*`。
- 资源：只被一个模块用的移到那个模块；多个模块共用的尺寸、颜色、文字（设计令牌）放 `:uikit`；无人用的删除（以 lint `UnusedResources` 为准，不手工猜）。
- Manifest：`ShareKitFileProvider` 和 `queries` 随分享代码到 `:uikit`；网络权限到 `:network`。

### 批次 4：拆 `:media` → `:mediaview`、`:speech`、`:subtitle`、`:imaging`（P2）
- 四组代码之间零 import，可以整组搬。
- 轻量的图片工具（`ImageUtils` 的 EXIF/缩放、`ImageCompressor`）属于纯工具，移到 `:common` 的 `image/`，这样 `:feature-chat` 不必依赖 ONNX/OpenCV。
- 每个新模块只声明自己的库：`:mediaview`（Compose、Coil）、`:speech`（Media3）、`:subtitle`（Media3、OkHttp）、`:imaging`（ONNX、OpenCV）。

### 批次 5：投屏协议成为 `:cast`（P6）
- `com.openapi.core.cast.*` → `:cast`，包名 `com.openapi.cast.*`。`:feature-video` 里和视频页相关的投屏界面（`AppCastOptionsProvider` 等）留在 feature。

### 批次 6：`:feature-roles` 并入 `:feature-chat`（P7）
- 包名 `com.openapi.feature.chat.roles.*`；资源、Manifest 合并；删除模块；`:app` 去掉对它的依赖。

### 批次 7：统一包名（P5）
- `:core`、`:ads`、`:chatkit`（已规范，跳过）、各 feature、`:app` 改为第 3 节的根包。消除所有跨模块同名包。
- `:ads` 的 namespace 改为 `com.openapi.ads`；`:app` 的 namespace 改为 `com.openapi.app`（`applicationId` 不变）。
- 必须同步改的地方（脚本统一处理，改完逐项 grep 核对）：Java/Kotlin import 和全限定名；所有 `AndroidManifest.xml`；布局 XML 里的自定义控件；字符串里的类名（`Routes`、`ConfigActionJump`、`ApkDownloader`、`ChatRoute`、`ProfileScreen`）；Google Cast 的 `OPTIONS_PROVIDER_CLASS_NAME`；混淆规则。
- **桌面快捷方式**：启动页类名会变。用 `<activity-alias>` 保留旧组件名 `com.openapi.ks.chatfree.ui.main.LaunchActivity`，避免用户桌面图标失效。
- **混淆**：release 开了 R8，现有 keep 规则按旧包名写（`com.openapi.ks.**` 等）。改为 `-keep class com.openapi.** { *; }`，范围不小于原来。

### 批次 8：架构测试和文档
- 重写 `ModuleGraphTest`：第 3 节的 5 条规则，外加“第 0、1 层不引用 `com.openapi.core`/`com.openapi.feature`”的源码扫描。
- 更新 architecture.md 和 refactor-plan.md。

### 批次 9：整体验证
- `:app:assembleChatDebug`、全部单元测试、架构测试；`assembleChatRelease`（检验 R8 和混淆规则），若签名配置缺失则说明原因。
- 手机测试清单（与你一起执行）：见第 6 节。

---

## 5. 不做的事

- `:retromusic`、`:feature-companion` 的代码和包名不动（companion 只跟着上游包名改 import）。
- GSY 和 4 个引擎不动。
- 不改业务行为；第 6 节以外的已知问题（如声音页的 NPE、文案）单独处理。
- 不引入 Hilt、Retrofit、convention plugin（共享配置用一个 `apply from` 脚本就够）。

---

## 6. 手机测试清单（全部批次完成后一起测）

| 区域 | 检查 |
|---|---|
| 启动 | 冷启动、开屏广告、旧桌面图标还能打开（`activity-alias`） |
| 首页 | 首页框架、侧边栏、“我的”页 |
| 对话 | 收发、流式回复、图片附件、语音输入、朗读、公式渲染（`$x$` 和 `$$x$$`）、角色选择（roles 并入后） |
| 拍照解题 | 拍照、裁剪辅助、质量提示、超分 |
| 视频 | 播放、换引擎、倍速、全屏、投屏（DLNA 搜索和播放）、实时字幕 |
| 内容频道 | 视频/图片/文字/声音频道、图片大图（PhotoView）、选图（PictureSelector）、收藏、翻译 |
| 声音 | 第 5.5 节 P-1 到 P-15（见 sound-playback-controls-split.md） |
| 账户 | 登录、注册、找回密码、设置、反馈 |
| 网络图片 | 列表图片加载（Glide + OkHttp + DNS，`LibraryGlideModule` 生效） |
| 分享 | 分享面板、二维码卡片 |
| 版本更新 | 检查更新、下载、安装通知点击回到首页 |
| release 包 | 混淆后的上述主流程（重点：Gson 数据解析、按类名打开的页面） |

---

## 7. 执行记录

每批都做了 `:app:assembleChatDebug`；涉及依赖的批次对比了 APK 的外部运行时依赖清单（版本精确到号）和原生库清单。

| 批次 | 提交 | 结果 | 偏离和原因 |
|---|---|---|---|
| 1 死代码、共享配置 | `e557082c` | 12 个库模块用 `gradle/android-library.gradle`；删 `ImageUtils`（让 util 依赖了 OkHttp）、`AndroidFileUtils`、空的库混淆文件、无效的 Manifest meta-data 和 `fileTree` | 无 |
| 2 依赖卫生 | `e0ec7771` | GSY、PictureSelector、PhotoView、Room 从底层的 `api` 移到真正用的模块；`:app` 的 HLS、Cast、ML Kit 移到 feature；运行时依赖只少了一个没人用的 `LabelsView` | Glide：没有改成 `LibraryGlideModule`，而是把原来的配置整体移到 `:app` 的 `AppGlideConfig`。原因：OkHttp 官方集成先注册、自定义 DNS 加载器后追加，这个顺序只有在应用模块里才是确定的；改成库模块会改变加载顺序 |
| 3 拆 `:commonutil` | `cba79ce3` | `:common`（纯工具，无资源）、`:network`、`:uikit`；删 47 个无人用的资源和 7 个已删控件的 styleable；28 个单模块资源移给使用者（其中多数在使用者里已有同名定义，原来就是使用者的值生效，直接去掉重复） | 无 |
| 4 拆 `:media` | `bcced98e` | `:mediaview`、`:speech`、`:subtitle`、`:imaging`；轻量图片工具进 `:common/image` | 火山引擎流式识别从实时字幕包移到 `:speech/asr`：`:feature-chat` 的语音输入也在用它，它是识别能力，不是字幕；`:subtitle` 依赖 `:speech` |
| 5 `:cast` | `348bebd5` | DLNA 协议独立 | 没有加 `CHANGE_WIFI_MULTICAST_STATE` 权限：原来没有，加了会改变行为 |
| 6 合并 roles | `4b36d71e` | `:feature-roles` 并入 `:feature-chat`，删除模块 | 检查了 `RolePickerSheet` 和 `RoleCatalogPane`：前者包含后者，另一页是服务端角色，不是重复实现 |
| 7 统一包名 | `41a4f7cd` | 265 个文件；没有跨模块同名包；`:ads`、`:app` 的 namespace 改名；启动页 `activity-alias`；混淆改为一条 `-keep class com.openapi.**` | 测试跟着被测代码走（`:core`、`:feature-chat`、`:imaging`、`:feature-content`、`:feature-companion`）。companion 的测试原来放在 `:app`，访问不到 companion 的 `internal` 函数，只是被增量编译掩盖了；移到 companion 模块后修复 |
| 8 架构测试、文档 | （本提交） | `ModuleGraphTest` 4 条规则：分层、包名、第 0/1 层不引用业务代码、项目依赖必须用到；人为加一条违规边确认测试会失败 | 去掉 `:feature-photosolve` 对 `:core` 的无用依赖（新规则发现的） |

**验证**：单元测试 139 个，只有原来就失败的两个：`ScreenSmokeTest`（`AppContext.init()` 没有调用）、`AppDatabaseMigrationTest`（缺 1→3 的迁移）。

