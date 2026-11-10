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
| `:app` | 18 | 保留 | 基本符合；`ConfigActionJump` 硬编码各频道页的类 |

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
- 主题：**需要你决定**。改用 `AppMaterialTheme` 会改变拍照页的配色；保留 `PaiZhaoTheme` 就接受两套主题并存。

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
  list/      GSYAutoVideoListFragment、AutoVideoListFragment、GSYVideoListAdapter、VideoListItemCompose
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

**需要确认**：`AutoVideoListFragment` 只有 10 行，是 `GSYAutoVideoListFragment` 的空子类，`VideoActivity`、`VideoListShowActivity` 在用。可以把这两处改成直接用父类，然后删掉它。

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

## `:app`（18 个文件）

**现状**
| 包 | 内容 |
|---|---|
| `home/` | `HomeActivity`、`HomeDependencies`、`domain/HomeModels`、`presentation/` 首页、频道列表、我的 |
| `ks/chatfree/ui/main/` | `LaunchActivity`（启动页） |
| `ks/myapp/base/` | `MyApplication`、`AppEventHandler`、`AppHooksImpl` |
| `ks/myapp/data/chat/` | `ChatRepositoryImpl`（767 行）、`BackendSpeechToText`、`LegacyLoginGate` |
| `ks/myapp/data/roles/` | `RolesRepositoryImpl`、`BundledRoleCatalog` |
| `ks/myapp/data/home/` | `LegacyHomeEntries`（频道列表数据、「我的」页的入口） |
| `ks/myapp/data/config/` | `ConfigActionJump`（按 URL 类型打开频道页） |

符合「装配层」的定义。

**问题**
1. `ConfigActionJump` 和 `LegacyHomeEntries` 直接引用 `:feature-content` 的 9 个 Activity/Fragment 类。`:app` 依赖所有 feature 是正常的，但这张「URL 类型 → 页面类」的表应该由 `:feature-content` 自己提供，`:app` 只负责调用。
2. 去掉 `AppHooks` 后 `AppHooksImpl` 删除。

**目标**
```
com.openapi.app
  App.kt / LaunchActivity       进程入口、启动页
  di/       各 feature 依赖的注册（原 base/ 的注册部分、data/chat、data/roles 的实现）
  home/     首页外壳（不变）
  route/    路由表的汇总（各 feature 提供自己的部分）
```

---

## 需要你决定的

1. **拍照解题的主题**：改用应用统一的 `AppMaterialTheme`（拍照页配色会变），还是保留 `PaiZhaoTheme`？
2. **`AutoVideoListFragment`**：改为直接用父类，然后删掉这个 10 行的空子类，可以吗？
3. **`:ads` 改包名 `com.openapi.ads`、`Constants` 改名 `AdsIds`**：放到最后统一改包名那一步，可以吗？

## 不在这次范围
- `ChatViewModel`（1066 行）、`ChatRepositoryImpl`（767 行）、`DetailGSYVideoPlayer`（721 行）的拆分。
- `:feature-companion` 的任何改动。
- `:auxio_music_ui`、`:retromusic`。
