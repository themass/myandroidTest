# 各模块逐个分析（按 module-standard.md 第 5 版的标准）

基于 `weifu-refactor` 的 `debdf79f`。
方法：逐文件列出包、行数、种类和使用方（`/tmp/modscan/inv.py`），再人工核对 import。类名相同会误报（例如 `Color`、`Theme`、`Constants`），下面的结论都已用 import 核对过。

`:core` 见 [core-split-plan.md](core-split-plan.md)，`:feature-content`（原 `:feature-channel`）见 module-standard.md 第 3 节，`:commonutil` 的去向见 module-standard.md 的 `:common` 部分。本文覆盖其余模块。

---

## 总览

| 模块 | 文件 | 目标 | 主要问题 |
|---|---|---|---|
| `:feature-chat` | 34 | 保留，并入 roles | 历史包名两处；多余依赖 `:feature-video` |
| `:feature-roles` | 7 | 并入 `:feature-chat/roles` | 无 |
| `:feature-user` | 10 | 保留 | 深度依赖旧框架；为一个类依赖 `:feature-video`；登录状态被 3 个模块直接读 |
| `:feature-photosolve` | 18 | 保留 | 自带一套主题 `PaiZhaoTheme`，和应用主题重复 |
| `:feature-companion` | 15 | 不动（角色扮演分支） | 无 |
| `:feature-video` | 33 | 保留，移出字幕和音乐服务 | 名不副实的包（`ui/sound` 里全是视频页）；字幕引擎、音乐服务放错位置 |
| `:chatkit` | 16 | 保留 | `ImageViewer` 是通用的看图组件 |
| `:asr` | 6 | 并入 `:media/speech` | 依赖 `:core`（违反「第 1 层不依赖 `:core`」） |
| `:mediaview` | 2 | 并入 `:media/viewer` | 用了 `com.openapi.core` 包名 |
| `:sharekit` | 1 | 并入 `:common/util/share` | 用了 `com.openapi.core` 包名 |
| `:ads` | 27 | 保留 | 包名 `com.openapi.core.ads.yewu`；统计 `MobAgent` 放在广告里；自己也有一个 `Constants` |
| `:app` | 18 | 保留，大幅瘦身 | 60 个第三方库、24 个 feature 的 Activity、21 MB assets、66 个无引用资源、10 个别的模块的测试都堆在这里；对话、角色、频道列表的实现也在这里 |

---

## `:feature-chat`（34 个文件）

**现状**：`domain/`、`data/`、`presentation/` 分层清楚，是所有 feature 里最接近模板的。

| 包 | 内容 |
|---|---|
| `chat/` | `ChatDependencies`（依赖入口，由 `:app` 注册） |
| `chat/domain/` | 模型、`ChatRepository` 接口、用例、`SpeechToText` 接口、`LoginGate` 接口 |
| `chat/data/` | `ChatMessageActions`、`mapper/ChatLogMapper` |
| `chat/presentation/` | `ChatViewModel`（**1066 行**）、`ChatUiState`、`AttachmentStager` |
| `chat/presentation/ui/` | `ChatRoute`、`ChatScreen`、会话列表、角色选择面板、`ChatItemMapper` 等 |
| `chat/ui/markdown/`（历史包名） | `ChatMarkwon`、`FormulaInputNormalizer` |
| `com/openapi/ks/myapp/view/katexplugin/`（历史包名） | 公式渲染插件 11 个文件，约 1400 行 |

**问题**
1. Markdown 和公式渲染在两个历史包名下，和对话代码分开放。
2. `build.gradle` 声明了依赖 `:feature-video`，源码里一个类都没用。
3. `ChatViewModel` 1066 行，收发、重试、附件、语音、朗读都在里面。这次只记录，不拆。
4. 打开拍照解题已经用类名字符串（`setClassName`），不依赖 `:feature-photosolve`。这是第 3 节「feature 之间用路由」的现成例子。

**从别的模块用到的**：主题（10 处，去 `:common/ui`）、录音播放（5 处，去 `:media/speech`）、权限（3 处，去 `:common/ui`）、图像压缩（去 `:media/imaging`）、分享（去 `:common/util/share`）、`ChatLog` 和 `FeedbackContentForm`（`:core` 里的数据对象）。

**目标**
```
com.openapi.feature.chat
  ChatDependencies.kt
  domain/  data/  presentation/        不变
  markdown/       ChatMarkwon、FormulaInputNormalizer、katex/（11 个插件文件）
  roles/          原 :feature-roles 的 7 个文件
```
**改动**：删除对 `:feature-video` 的依赖；两个历史包移进 `markdown/`。

---

## `:feature-roles`（7 个文件）

**现状**：`data/`（本地目录、解析、颜色、仓库接口）、`domain/`（模型）、`presentation/`（角色列表、选择 ViewModel）。只依赖 `:core` 的主题。仓库实现 `RolesRepositoryImpl` 在 `:app`。

**问题**：太小，只有对话和 `:app` 用。

**目标**：整体移到 `:feature-chat/roles/`，内部结构不变。子包规则：`roles/` 不 import 对话的其它子包。

---

## `:feature-user`（10 个文件）

**现状**
| 包 | 内容 |
|---|---|
| `ui/user/` | `LoginActivity`、`RegisterScreens`（文件里声明的类叫 `RegActivity`）、`FindPassActivity`、`SettingActivity`、`AddCustomeInfoActivity` |
| `ui/feedback/` | `FeedbackActivity`（482 行） |
| `ui/fragment/`、`adapter/` | 设置页里的角色设置 |
| `data/` | `UserLoginUtil`（登录状态读写） |
| `task/` | `LoginTask` |

全是 View 体系的 Activity。

**问题**
1. **深度依赖旧框架**：从 `:core` 和 `:commonutil` import 了 21 个不同的包，包括基类、广告、数据对象、事件、`urlparser`。
2. **为一个类依赖 `:feature-video`**：只用了 `PlaybackEngineHelper`（设置页选播放引擎）。
3. **登录状态被别的模块直接读**：`UserLoginUtil` 被 `:feature-content` 3 个文件、`:app` 4 个文件用。`:ads` 里也有，但已经注释掉了。
4. 文件名和类名不一致：`RegisterScreens.kt` 里是 `RegActivity`（之前的死代码扫描因此误删过一次）。

**目标**
```
com.openapi.feature.user
  login/      LoginActivity、LoginTask
  register/   RegActivity（文件改名为 RegActivity.kt）
  password/   FindPassActivity
  profile/    AddCustomeInfoActivity
  setting/    SettingActivity、SettingCharacterFragment、SettingCharacterAdapter
  feedback/   FeedbackActivity
```
**改动**
- `UserLoginUtil` 移到 `:core/account`，其他模块读登录状态都从这里读。
- 播放引擎偏好移到 `:core/prefs`，去掉对 `:feature-video` 的依赖。
- 别的模块打开登录页、设置页改用路由。

---

## `:feature-photosolve`（18 个文件）

**现状**：`PhotoSolveActivity` 入口，`photosolve/` 下是 ViewModel、UiState、仓库接口与 HTTP 实现，`photosolve/ui/` 是 9 个 Compose 页面，`ui/theme/` 是自带的主题。全 Compose，结构清楚。

**问题**
1. **两套主题**：自带 `PaiZhaoTheme`（`Color.kt`、`Theme.kt`、`Type.kt`），只在本模块用；应用里其它页面用 `:core` 的 `AppMaterialTheme`。拍照解题页面的配色和应用不一致。
2. 图像处理放在 `:core`（`com/aimath/paizhao/photosolve/` 下 14 个文件），包名却像是本模块的。

**目标**
```
com.openapi.feature.photosolve
  PhotoSolveActivity
  data/          PhotoSolveRepository、HttpPhotoSolveRepository、PhotoSolveRepositories
  presentation/  PhotoSolveViewModel、PhotoSolveUiState
  ui/            9 个页面
```
**改动**
- 图像处理移到 `:media/imaging`（见 core-split-plan.md）。
- 主题：已改用 `AppMaterialTheme`（见文末「已决定」）。

---

## `:feature-companion`（15 个文件）

**现状**：`companion/`（入口、页面、ViewModel、HTTP、LiveKit 传输、角色和记忆管理）、`avatar/`（虚拟形象渲染）。只依赖 `:core` 的工具和 `Constants`。

**处理**：按之前的约定，角色扮演的 Android 端只在自己的分支开发，这次**不动**。最后改包名时一起改。

---

## `:feature-video`（33 个文件）

**现状**
| 包 | 内容 | 问题 |
|---|---|---|
| `ui/gsy/` | `DetailGSYVideoPlayer`（721 行）、`GSYAutoVideoListFragment`、`GSYVideoListAdapter`、`VideoListItemCompose` | |
| `ui/sound/` | `VideoActivity`、`VideoShowActivity`、`VideoShowActivityLazyUrl`、`VideoListShowActivity`、两个 Helper | 包名叫 sound，里面全是视频页 |
| `ui/fragment/` | `AutoVideoListFragment`（10 行，空壳） | |
| `ui/localvideo/` | `LocalVideoActivity` | |
| `cast/` | Google Cast 4 个 | 和 `:core` 里的 DLNA 分开放 |
| `service/` | `PlayService`（496 行） | **音乐**播放服务，视频模块自己不用，只有频道的声音页用 |
| `translation/` | 安装器和 3 个翻译提供者（演示、百度占位、火山占位） | 安装器依赖 `Constants`，是装配代码 |
| `translation/realtime/` | 实时字幕引擎、PCM 音频源 7 个、`RealtimeSubtitleBindings` | 引擎是通用能力；`Bindings` 的音乐部分只有频道用 |
| `video/` | `PlaybackEngineHelper` | 被 `:feature-user` 依赖 |
| `data/` | `VideoUtil` | |

**目标**
```
com.openapi.feature.video
  player/    DetailGSYVideoPlayer、PlaybackEngineHelper（引擎切换；偏好值移到 :core/prefs）
  list/      GSYAutoVideoListFragment、GSYVideoListAdapter、VideoListItemCompose
  page/      VideoActivity、VideoShowActivity、VideoShowActivityLazyUrl、VideoListShowActivity、两个 Helper
  local/     LocalVideoActivity
  cast/      googlecast/（现有 4 个）、dlna/（从 :core 移来 11 个）
  subtitle/  RealtimeSubtitleBindings 的视频部分
  data/      VideoUtil
```
**移出**
| 内容 | 去向 |
|---|---|
| `PlayService` | `:feature-content/sound` |
| `RealtimeSubtitleBindings` 的音乐部分 | `:feature-content/sound` |
| 字幕引擎、PCM 音频源 | `:media/subtitle/realtime` |
| 3 个翻译提供者 | `:media/subtitle/provider`（只实现接口，不含业务） |
| `VideoTranslationInstaller` | `:app`（启动时注册提供者） |

`AutoVideoListFragment` 已删除（见文末「已决定」）。另外，`VideoActivity` 只在 Manifest 里登记，代码里没有任何地方打开它，可能是死页面。

---

## `:chatkit`（16 个文件）

**现状**：`input/`（输入栏、语音控件、录音浮层）、`message/`（消息列表、菜单、底栏、多选、附件面板、`parts/` 消息内容块）、`media/`（`ImageViewer`）、`model/`、`theme/`。全 Compose，只依赖 `:mediaview`，符合标准。

**问题**：`media/ImageViewer`（全屏看图，可缩放）只依赖 Compose，和对话无关。频道的图片列表计划用 `:media/viewer`，到时候也需要全屏看图。

**目标**：结构不变。`ImageViewer` 等频道真的要用时再移到 `:media/viewer`，现在不动。

---

## `:asr`（6 个文件）

**现状**：火山引擎流式识别 5 个（客户端、二进制协议、响应解析、配置、错误码），`tts/DeviceSpeech` 1 个（设备朗读，已合入）。包名仍是 `com.openapi.ks.myapp.translation.realtime`（历史包名）。

**问题**：违反「第 1 层不依赖 `:core`」：
- `VolcengineAsrConfig` 读 `:core` 的 `Constants`（接口地址、密钥配置）。
- `DeviceSpeech` 用 `:core` 的 `AppContext` 取 Context。

**目标**：并入 `:media/speech/`，分 `asr/`、`tts/`。
**改动**：配置改成构造参数，由 `:app` 从 `Constants` 读出后传入；`DeviceSpeech` 改为接收 `Context` 参数。

---

## `:mediaview`（2 个文件）

**现状**：`MediaComponents.kt`（叠放、轮播、气泡）、`MediaOverlap.kt`（重叠布局、视频播放对话框）。包名 `com.openapi.core.view.media`，和 `:core`、`:commonutil` 共用 `com.openapi.core` 包根。依赖 Media3。

**目标**：并入 `:media/viewer/`，包名改为 `com.openapi.media.viewer`。

---

## `:sharekit`（1 个文件）

**现状**：`ShareKit.kt`（分享文字、文件、图片，带 FileProvider 配置）。包名 `com.openapi.core.share`。只有对话用；`:commonutil` 里还有一个老的 `ShareUtil`。

**目标**：并入 `:common/util/share/`，和 `ShareUtil` 合并成一套。

---

## `:ads`（27 个文件）

**现状**
| 包 | 内容 |
|---|---|
| `ads/base/` | `AdsContext`、`AdsManager`、`AdsEventAdaptor`、8 个广告类型接口 |
| `ads/admob/` | Admob 的横幅、插屏、开屏、管理器 |
| `ads/adview/` | Adview 的横幅、插屏、原生、开屏、视频、管理器 |
| `ads/config/` | 开屏后下一步、横幅下一步、开屏关闭事件 |
| `ads/` | `Constants`（广告位 ID）、`AdsPopStrategy` |
| `um/` | `MobAgent`（统计上报） |

只依赖 `:commonutil`，不依赖 `:core`，符合「第 1 层」规则。

**问题**
1. 包名 `com.openapi.core.ads.yewu`，看起来像 `:core` 的一部分。
2. 自己有一个 `Constants`（20 个广告位 ID），和 `:core` 的 `Constants` 重名，读代码时容易搞混。
3. `MobAgent` 是统计，不是广告，被 `:core`、`:feature-content`、`:feature-user`、`:app` 用。它依赖 `AdsContext`，所以暂时离不开这个模块。

**目标**
```
com.openapi.ads
  api/       AdsContext、AdsManager、各类型接口（原 base/）
  admob/  adview/  config/
  AdsIds     原 Constants，改名
  AdsPopStrategy
  analytics/ MobAgent（暂留，等它不再依赖 AdsContext 后再考虑移到 :common）
```

---

## `:app`（18 个源文件，但不止这些）

**第 1 版的判断错了**：我只看了源文件，说它「基本符合装配层」。把依赖、Manifest、资源、assets、测试一起看，`:app` 确实是大杂烩。源代码只是最小的一部分：

| 方面 | 现状 | 属于谁 |
|---|---|---|
| 第三方依赖 | `build.gradle` 声明了约 60 个库。`:app` 自己的代码只用到 multidex、Media3（2 个文件）、Room（只在测试里） | 其余 50 多个是给各 feature 用的：Markwon、flexmark、androidsvg、gif（对话）；CameraX、easycrop、ML Kit、ONNX、OpenCV、storage（拍照）；LiveKit（伴侣）；Cast、mediarouter、nanohttpd（投屏）；PhotoView、LabelsView、ripple、shimmer（旧页面）。还有 navigation、percentlayout、viewpager2、rxjava、webkit 在整个 `:app` 里没有找到使用 |
| Manifest | 26 个 Activity、1 个 Service、2 个 FileProvider | 24 个 Activity 属于其它模块（用户 6 个、视频 5 个、频道 9 个、拍照 1 个、`:core` 的旧框架 3 个），`PlayService` 属于频道 |
| assets（约 21 MB） | 文档裁剪模型 13 MB、公式 KaTeX 1.4 MB、字体 5.6 MB、角色 personas、HTML、wav | 模型属于图像处理，KaTeX 属于对话，personas 属于角色，`mybook.css`、`error_page.html` 属于频道。根目录 23 个字体文件、`emoji.db`、`markdown.css`、几个 HTML、一个 wav 在代码里没有找到引用 |
| 资源 | 去重后还剩 125 个非 values 资源 | 56 个 `:app` 自己用；3 个只有模块用；66 个没有任何引用 |
| 测试 | 11 个 | 只有 `ChannelsViewModelTest` 测的是 `:app` 的代码。其余是角色解析、伴侣、图像质量、超分分块、数据库的测试，被测代码都在别的模块 |

**源代码本身**
| 文件 | 现在 | 应该在 |
|---|---|---|
| `MyApplication`、`LaunchActivity`、`AppEventHandler` | 进程入口、启动页、全局事件 | 留在 `:app` |
| `HomeActivity`、`HomeScreen`（首页框架和侧边栏） | 首页外壳 | 留在 `:app` |
| `ChannelsScreen`、`ChannelsViewModel`、`LegacyHomeEntries` 的频道部分 | 频道列表页及其数据 | `:feature-content`：这是频道的入口页，不是外壳 |
| `ProfileScreen`、`LegacyHomeEntries` 的「我的」部分 | 我的页面 | `:feature-user` |
| `ConfigActionJump` | URL 类型 → 频道页的表 | `:feature-content` 提供，`:app` 调用 |
| `ChatRepositoryImpl`（767 行）、`BackendSpeechToText`、`LegacyLoginGate` | 对话的网络实现 | `:feature-chat/data`。它只依赖 `:core` 的网络、数据对象、数据库，`:feature-chat` 都能依赖；唯一和 `:app` 相关的是构造参数类型写成了 `MyApplication`，改成 `Context` 即可 |
| `RolesRepositoryImpl`、`BundledRoleCatalog` | 角色的实现 | `:feature-chat/roles/data` |
| `AppHooksImpl` | 端口实现 | 去掉 `AppHooks` 后删除 |

**为什么当初放在 `:app`**：拆模块时为了先编译通过，凡是要用网络、数据库的实现都留在了 `:app`，feature 只放接口。现在 `:core` 和 `:common` 的边界定清楚了，这些实现可以回到各自的 feature。

**拆完后的 `:app`**
```
com.openapi.app
  App（原 MyApplication）    初始化、依赖注册
  LaunchActivity             启动页（含 MyRadarScanView）
  AppEventHandler            全局事件
  home/                      首页框架、侧边栏（HomeActivity、HomeScreen、HomeModels）
  route/                     汇总各 feature 的路由表
build.gradle                 只依赖各模块，不直接声明 feature 用的第三方库
AndroidManifest.xml          只登记 LaunchActivity、HomeActivity；其余由各 feature 自己的 Manifest 声明，构建时合并
assets/                      只留 :app 自己用的；模型、KaTeX、personas 随模块走
```
约 8 个源文件。

**步骤**（插在 module-standard.md 迁移顺序里）
1. 删除没有引用的 66 个资源、没有引用的 assets 和第三方库。每删一批编译一次。字体文件可能通过拼接的路径加载，先单独核对，不和其它东西一起删。
2. Manifest 拆分：各 feature 在自己的 `AndroidManifest.xml` 里登记自己的 Activity，`:app` 删掉对应条目。这一步不改代码，只改 Manifest，风险低。
3. assets 和测试随所属模块移走。
4. 第三方库移到实际使用的模块（和 module-standard.md 第 5 节一起做）。
5. 对话、角色的实现移回 `:feature-chat`。
6. 频道列表页、「我的」页移到 `:feature-content`、`:feature-user`；`ConfigActionJump` 改为由频道提供。

## 已决定（2026-11-10）

1. **拍照解题改用应用统一主题**：已完成。`PaiZhaoTheme`（蓝色）删除，改用 `AppMaterialTheme`（暖橙）；答案卡用 `AppColors.Success`，新增 `AppColors.SuccessContainer`。字号随之改为 Material3 默认值（标题 28sp，原 26sp）。
2. **删除 `AutoVideoListFragment`**：已完成。两处调用改为直接用 `GSYAutoVideoListFragment`；`VideoListShowActivity` 里「小窗开关」的两个分支本来就创建同一个类，合并成一行。
3. **`:ads` 改包名 `com.openapi.ads`、`Constants` 改名 `AdsIds`**：同意，放到最后统一改包名那一步。

## 不在这次范围
- `ChatViewModel`（1066 行）、`ChatRepositoryImpl`（767 行）、`DetailGSYVideoPlayer`（721 行）的拆分。
- `:feature-companion` 的任何改动。
- `:auxio_music_ui`、`:retromusic`。
