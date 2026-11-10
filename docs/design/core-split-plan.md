# `:core` 拆分计划（草案，待确认）

基于 `weifu-refactor` 的 `debdf79f`。`:core` 现有 130 个源文件。
前提：采用 `module-standard.md` 讨论后的修正——`:media` 只含 `speech/`、`imaging/`；`:feature-video`、`:feature-channel` 不合并。

## 1. 拆完后 `:core` 是什么

**定义**：应用配置 + 多个功能共享的业务数据 + 旧页面框架。
**准入**：至少两个 feature 用，并且含业务名词或依赖接口配置。只有一个模块用的，下沉到那个模块。

```
:core  com.openapi.core
  config/     Constants、BaseRes、Token               接口地址、请求头、内置图片表
  app/        AppContext、VersionUpdater              应用上下文、版本检查
  model/      RecommendVo、SoundItemsVo、CharacterVo、NullReturnVo、CustomeAddForm、
              StateUseVo、VersionVo、VipDescVo        跨功能的数据对象
  db/         AppDatabase、5 个 DAO、Converters、AppFavoriteStore、
              实体 ChatLog、ChatSessionLog、ConnLog、HistoryVo、MessageType
  event/      PlayCoreEvent、StateUseEvent、VipDescEvent、HindBannerEvent
  legacyui/   View 体系的 Activity/Fragment 基类（19 个），新代码不用
```

拆完 `:core` 剩 **49 个**，79 个移出，1 个删除（`AppHooks`），1 个拆成两半（`ImagePhotoLoad`）。合计 130。

## 2. 移出清单

| 去向 | 数量 | 文件 |
|---|---|---|
| `:media/imaging` | 15 | `com/aimath/paizhao/photosolve/` 全部（图像管线、质量、自动裁剪、超分）、`ImageCompressor` |
| `:media/speech` | 2 | `AudioPlayer`、`VoiceRecorder` |
| `:common/ui` | 6 | `AppMaterialTheme`、`Tokens`、`AppDialogs`、`Permissions`、`PermissionRequester`、`PermissionState` |
| `:feature-video` | 11 | DLNA 投屏 `cast/` 全部 |
| `:feature-channel` | 20 | `IndexRecommendAdapter`、`BasePhotoFlowRecycleViewAdapter`、`showtype/` 6 个、`task/` 4 个、`AppInfo`、`ImgItemsVo`、`TextItemVo`、`TextItemsVo`、`HistoryUtil`、`MusicStateListener`、`OnBackKeyDownListener`、`MenuOneContext` |
| `:feature-user` | 12 | `LoginForm`、`RegForm`、`IwannaForm`、`UserInfoVo`、`IWannaVo`、`urlparser/` 4 个、`TabChangeEvent`、`UserLoginEvent`、`CustomeAddEvent` |
| `:feature-chat` | 1 | `FeedbackContentForm` |
| `:app` | 12 | 对话接口的数据对象 `ChatSessionVo`、`ChatMessageVo`、`ChatMessagePageVo`、`Choice`、`Message`、`AsrResponseVo`、`TtsResponseVo`、`AsrContentForm`；连接日志 `ConnLogUtil`、`ConnLogForm`；`MyRadarScanView`（启动页）、`VideoWebViewActivity` |
| 删除 | 1 | `AppHooks`（见第 4 步） |
| 拆分 | 1 | `ImagePhotoLoad`：`loadCommonImg`、`options` 去 `:common/ui/image`，`loadPhoto` 去 `:feature-channel` |

## 3. 为什么有些「单个模块用」的东西还留在 `:core`

| 文件 | 原因 |
|---|---|
| `ChatLog`、`ChatSessionLog`、`ConnLog`、`MessageType` | 是 Room 实体。Room 要求实体和 `AppDatabase` 在同一个模块，而数据库里还有收藏、历史这些多功能共用的表 |
| `CustomeAddForm` | 被 `legacyui/WebViewActivity` 用 |
| `BaseBannerAdsActivity` | 只有 `:app` 直接用，但 `BaseFragmentActivity`、`BaseSingleActivity` 继承它 |
| `legacyui/` 整体 | 依赖广告、`Constants`、业务事件，放进 `:common` 会把业务带进底层。频道 25 个文件、用户 4 个、视频 4 个在用；新的 Compose 功能不用。以后旧页面换成 Compose 时一起删 |

## 4. 步骤

每步单独提交，每步 `:app:assembleChatDebug` 加手机冒烟。

| 步 | 内容 | 前置条件 | 风险 |
|---|---|---|---|
| 1 | 拆 `ImagePhotoLoad`：通用加载移到 `:common/ui/image` | 无 | 低 |
| 2 | 图像、语音移到 `:media`；主题、对话框、权限移到 `:common/ui` | 无 | 低。只有对话、拍照、`:app` 引用 |
| 3 | 投屏移到 `:feature-video` | 无 | 低。只有视频引用 |
| 4 | 频道、用户、对话的专属类下沉 | 第 1 步 | 中。频道的 20 个文件互相引用，要整组一起搬 |
| 5 | 对话接口数据对象、连接日志移到 `:app` | 无 | 低 |
| 6 | `:core` 内部按第 1 节整理子包 | 1–5 | 中。包名变了，import 要改 |
| 7 | 去掉 `AppHooks`：`isVip` 读 `:core` 的账户状态；`openGlobalSearch` 改为菜单槽位由子类填；`openSetting` 改为 Intent action | 无 | 高。要改 `legacyui` 的基类和它们的子类 |

第 1–5 步可以按任意顺序做，第 4 步依赖第 1 步。第 7 步和其它步骤独立，可以最后做。

## 5. 收益

- `:core` 从 130 个文件减到 49 个，每个文件都有明确的「为什么在这里」。
- 对话、拍照这些新功能，以后只通过 `config/`、`model/`、`db/` 用到 `:core`，不再因为图像处理或主题而依赖它。
- 去掉 `AppHooks` 后，`:core` 不再需要 `:app` 在启动时注入任何东西（`AppContext` 除外）。

## 6. 待确认

1. 第 1 节对 `:core` 的定义，可以吗？
2. 旧页面框架暂时留在 `:core/legacyui`，以后随 Compose 迁移删除，可以吗？
3. 对话的 Room 实体留在 `:core/db`，可以吗？另一种做法是把 `AppDatabase` 整个移到 `:app`，各功能通过接口访问数据，但改动更大。
