# myandroidTest

AI 对话与视频类 Android 应用。对话页、角色目录、拍照解题已经是 Kotlin + Jetpack Compose；
视频、音乐、频道等页面仍是 Java + XML，正在逐屏迁移。

## 模块

| 模块 | 内容 |
|---|---|
| `app` | 应用本体：Compose 对话页（`com.openapi.chat`）、角色目录（`com.openapi.roles`）、拍照解题（`com.aimath.paizhao`）、通用能力（`com.openapi.core`），以及尚未迁移的旧页面（`com.openapi.ks.myapp`、`com.openapi.ks.chatfree`） |
| `commonutil` | 公共工具、网络、Room 收藏库、图片选择等（Java） |
| `ads` | 广告 SDK 封装 |
| `retromusic` | 本地音乐播放器（由 Retro Music 引入），入口是抽屉里的「本地音乐」 |
| `auxio_music_ui` | 另一套本地音乐界面（Compose），由偏好设置切换 |

新代码放在 `com.openapi.chat`、`com.openapi.roles`、`com.openapi.core`、`com.aimath` 下；
旧包不要再新增功能。Compose 代码需要旧模块里的能力时，在 `ChatDependencies` 这类接入点定义接口，
由 `MyApplication` 注入实现（例如 `LoginGate`），不要直接 import 旧包。

## 构建与测试

应用只有一个 flavor：`chat`。

```bash
./gradlew :app:assembleChatDebug
./gradlew :app:testChatDebugUnitTest
./gradlew :app:minifyChatReleaseWithR8   # 只验证 R8 和混淆规则，不需要签名
```

- 单元测试是 JVM 测试（Robolectric），不需要设备或模拟器。
- `assembleChatRelease` 需要签名密钥库；`app/build.gradle` 里的路径指向开发者本机，换机器构建前先改成自己的。
- 密钥和账号类配置不进仓库，保持 git 忽略。

## 依赖版本

多个模块共用的库在 `gradle/libs.versions.toml` 里统一声明，模块里写 `libs.xxx`。
根 `build.gradle` 的 `resolutionStrategy.force` 是运行时兜底，改版本时两处一起改。
只在单个模块用到的第三方库直接写在该模块的 `build.gradle`。

## 文档

- [`docs/roles-and-chat-design.md`](docs/roles-and-chat-design.md)：角色目录与对话页的设计。

## 还没迁移的部分

- 视频、频道、音乐详情等页面仍是 Java + XML（约 260 个 Java 文件在 `app`，150 个在 `commonutil`）。
- 两个 Room 库并存：`favorites.db`（`commonutil`）和 `app.db`（`app`），各有一个 `DBManager`。
- 部分页面仍用 `startActivityForResult` / `onActivityResult`（权限页、启动页）。
- 拍照解题的后端接口还没接，`PhotoSolveRepositories` 是注入点。
