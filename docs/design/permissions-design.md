# 权限请求统一入口：现状、方案与迁移

日期：2026-11-11
分支：`claude/happy-fermi-300550`（基线 `weifu-refactor`）
相关：`docs/design/architecture.md` 第 3 节（`ModuleGraphTest` 分层）、`docs/design/photo-editing-design.md`

---

## 1. 现状

### 1.1 已经清理掉的旧体系

历史上的 `PermissionHelper` / `CompatiblePermissionManager` / `ModernPermissionManager` / `PermissionUtils` / `BasePermissionActivity` / `PermEvent` 已经删除。现在仓库里只剩 `:uikit` 的 `com.openapi.uikit.permission`：

| 文件 | 作用 |
|---|---|
| `PermissionRequester.kt` | 回调式请求：解释对话框 → 系统授权框 → 永久拒绝时「去设置」引导 |
| `PermissionState.kt` | 四态判定（GRANTED / NOT_REQUESTED / DENIED / PERMANENTLY_DENIED），用 `SharedPreferences` 记录「是否申请过」，弥补 `shouldShowRequestPermissionRationale` 无法区分「从未申请」和「不再询问」的缺陷 |
| `Permissions.kt` | 版本相关的权限组合（相册/音频/视频在 Android 13 前后的差异） |

这套东西的**判定逻辑是对的**，问题在**入口形态**：`PermissionRequester` 是回调式 + 必须在 `ComponentActivity` 上调用，而 Compose 页面拿 Activity 的方式、拿不到时的降级处理，每个页面都自己写一遍。于是：

### 1.2 迁移前仍然分散的请求点（实测清单）

| # | 位置 | 形态 | 问题 |
|---|---|---|---|
| 1 | `feature-chat/.../ui/ChatRoute.kt` 相机 | `PermissionStates.isGranted` + `PermissionRequester.with(...).request(Callback)` | 用 `(activity as? ComponentActivity)`，拿不到就静默什么都不做——用户点了相机没反应 |
| 2 | `feature-chat/.../ui/ChatRoute.kt` 录音 | 同上 | 同上；且 `onGranted = Unit`（首次授权不录音是产品行为，但代码里看不出来是为了什么） |
| 3 | `feature-photosolve/.../PhotoSolveActivity.kt` 相机 | **手写** `rememberLauncherForActivityResult(RequestPermission)` + `ContextCompat.checkSelfPermission` + `Manifest.permission.CAMERA` | 完全绕开了 `PermissionRequester`：没有解释对话框、没有「去设置」引导，拒绝一次就 `permissionPermanentlyDenied = true`（把普通拒绝误判成永久拒绝） |
| 4 | `feature-video/.../local/LocalVideoActivity.kt` 读视频 | **手写** `rememberLauncherForActivityResult(RequestMultiplePermissions)` + `checkSelfPermission` | 同上；且权限结果只在组合时算一次，从设置页回来不会刷新 |
| 5 | `core/.../update/ApkDownloader.java` 通知 | 无运行时申请 | `POST_NOTIFICATIONS` 从 Android 13 起是运行时权限，没有它通知会被静默丢弃（下载进度看不到） |
| 6 | `-` 定位 | 清单里权限被注释掉，`Permissions.LOCATION` 定义了但没人用 | 定义与使用脱节；将来要定位时又得重新发明一遍入口 |
| 7 | `-` 存储 | `app/src/main/AndroidManifest.xml` 里 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 还在，`READ_MEDIA_*` 被注释 | 选图已经全部走 Photo Picker（无需权限），旧声明留着只会在上架审核时被问「为什么需要全盘存储」 |

`retromusic` 内部还有一套自己的权限处理（`AbsBaseActivity` / `PermissionActivity`），按约束**不动**；它本来就是一个独立子工程。

### 1.3 结论

重复的不是「判定逻辑」，而是**入口**：
- 三处各写了一遍 `rememberLauncherForActivityResult` / `PermissionRequester`；
- 两处把「普通拒绝」当「永久拒绝」；
- 一处根本没有运行时申请（通知）；
- 所有调用方都要自己处理「拿不到 Activity」的降级。

---

## 2. 方案

### 2.1 目标形态

一个入口，三种调用方式，行为完全一致：

```kotlin
// 1) 协程（Compose 里最常用）
scope.launch {
    when (PermissionGateway.of(activity).request(PermissionRequest(Permissions.CAMERA, "拍照需要相机权限"))) {
        PermissionOutcome.Granted -> openCamera()
        is PermissionOutcome.Denied -> toast("未授予相机权限")
        is PermissionOutcome.PermanentlyDenied -> showGoToSettingsHint()
    }
}

// 2) Activity / Fragment（Java 也能用，回调版保留）
PermissionRequester.with(this)
    .permissions(*Permissions.RECORD_AUDIO.permissions.toTypedArray())
    .rationale("发送语音消息需要使用麦克风")
    .request(object : PermissionRequester.Callback() { ... })

// 3) 只判断，不弹框
if (PermissionGateway.isGranted(context, Permissions.CAMERA)) { ... }
```

### 2.2 新增/修改的文件（都在 `:uikit`）

| 文件 | 内容 |
|---|---|
| `PermissionRequest.kt`（新增） | `PermissionRequest(permissions, rationale)`：一次请求 = 权限集合 + 给用户看的理由 |
| `PermissionOutcome.kt`（新增） | `Granted` / `Denied(denied)` / `PermanentlyDenied(denied)`；`combineStates()` 纯函数把多个权限状态合并成一个结论（可单测） |
| `PermissionGateway.kt`（新增） | 唯一入口。`of(activity)` 返回 `PermissionGatewayHandle`；`Mutex` 按 Activity 串行化请求，连点两次不会叠出两个对话框；排队后发现已被别的请求授予，直接返回 `Granted` |
| `PermissionRequester.kt`（改） | 新增 `await()`（协程版）；`Callback` 新增 `onPermanentlyDenied()`（默认退化为 `onDenied`）；从设置页回来仍没开时改为回调 `onPermanentlyDenied`（之前错误地回调 `onDenied`） |
| `Permissions.kt`（改） | 每个预设从 `Array<String>` 变成 `PermissionRequest`（自带理由），新增 `NOTIFICATIONS`（Android 13+ 才需要，低版本返回空集合 → 直接视为已授权）；保留 `camera(rationale)` 等带参工厂供自定义文案 |

**分层**：全部在 `:uikit`（第 0 层），只依赖 `androidx.activity` / `appcompat` / 协程，不 import 任何业务包，符合 `ModuleGraphTest`。

### 2.3 为什么用动态注册的 `ActivityResultRegistry`

`registerForActivityResult` 必须在宿主 `STARTED` 之前注册；在 Compose 里这意味着只能固定在组合期注册，然后把结果「接回」某个点击事件——这就是现在每个页面各写一套 launcher 的根源。

`PermissionRequester` 原本就用 `activityResultRegistry.register(key, …)` 在**调用时**注册，回调后 `unregister()`。`PermissionGateway` 在这个基础上加：

- **串行化**：`ConcurrentHashMap<identityHashCode(activity), Mutex>`，同一宿主上的请求排队；排队期间先检查是否已被授予，避免重复弹框。
- **销毁检查**：`isFinishing || isDestroyed` 时直接返回 `Denied`，不抛异常。
- **明确的宿主契约**：只接受 `ComponentActivity` / `FragmentActivity`；老式 `android.app.Activity` 直接抛 `IllegalArgumentException` 并指出类名——与其偷偷降级成「什么都不做」，不如在迁移时暴露出来（`feature-video` 的 `VideoShowActivity` 等就是老基类，将来要用权限时会被这个错误提示推到正确做法）。

### 2.4 拒绝 / 永久拒绝 / 去设置

| 场景 | 判定 | 行为 |
|---|---|---|
| 已授权 | `GRANTED` | 直接执行 |
| 从未申请 | `NOT_REQUESTED` | 直接弹系统框 |
| 拒绝过一次（还能再问） | `DENIED` | 先弹解释框（`rationale`），用户点「继续」再弹系统框；拒绝后返回 `Denied` |
| 「不再询问」/ 系统策略 | `PERMANENTLY_DENIED` | 回调式：弹「去设置」对话框，用户点「去设置」→ 跳到本应用设置页 → 回来后**重新检查**，开了就继续；没开回调 `onPermanentlyDenied`。协程式：不弹对话框，直接返回 `PermanentlyDenied`，由调用方决定提示文案 |
| 跳设置后仍没开 | `PERMANENTLY_DENIED` | **不再谎称「还能再问」**（这是修复的 bug：旧代码此时回调 `onDenied`，调用方会以为下次点还能弹框） |

「是否申请过」记在 `core_permission_requested` 这个 `SharedPreferences` 里（`PermissionState.kt`），key 是权限字符串本身。

### 2.5 Compose / Activity / Fragment 用法

- **Compose**：`val activity = LocalActivity.current`（`androidx.activity.compose`），`rememberCoroutineScope()` 里 `launch { gateway.request(...) }`。请求跨越重组也不会丢（协程在 scope 里），页面销毁自动取消。
- **Activity（Java/Kotlin）**：`PermissionRequester.with(this)…request(callback)`，不需要改基类。
- **Fragment**：`PermissionRequester.with(requireActivity())`；Fragment 自己的 `registerForActivityResult` 不用再碰。
- **ViewModel**：不直接请求权限（没有 Activity）；由 UI 层请求后把结果作为事件传给 ViewModel——这是 Android 的常规做法，也是现在 `ChatRoute` 的做法，保留。

---

## 3. 已完成的本轮迁移

| 文件 | 改动 |
|---|---|
| `feature-chat/.../ui/ChatRoute.kt` | 相机、录音改走 `PermissionGateway`；拿不到 Activity 时不再静默（协程里自然返回，不再吞掉） |
| `feature-photosolve/.../PhotoSolveActivity.kt` | 删掉手写的 `RequestPermission` launcher + `ContextCompat` 判断；改用 `PermissionGateway`，只有 `PermanentlyDenied` 才显示「去设置」 |
| `feature-video/.../local/LocalVideoActivity.kt` | 删掉手写的 `RequestMultiplePermissions` launcher；改用 `PermissionGateway`，从设置页回来会刷新 `granted` |
| `uikit/.../permission/Permissions.kt` | 预设改为 `PermissionRequest`；新增通知权限 |
| `uikit/.../permission/PermissionRequester.kt` | 新增 `await()`、`onPermanentlyDenied()`；修复设置页返回的错误回调 |

验证：`./gradlew --offline :uikit:compileDebugKotlin :feature-chat:compileDebugKotlin :feature-video:compileDebugKotlin :feature-photosolve:compileDebugKotlin` 全部通过。

---

## 4. 剩余迁移清单

| # | 位置 | 动作 | 优先级 |
|---|---|---|---|
| 1 | `core/.../update/ApkDownloader.java` | 下载前请求 `Permissions.NOTIFICATIONS`（协程版需要 Activity，下载器是 Application 上下文 → 在发起下载的界面里先请求，再把结果告诉下载器；或在下载器里用回调版并传入当前 Activity） | 高（Android 13+ 通知会被静默丢弃） |
| 2 | `app/src/main/AndroidManifest.xml` | 删除 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`（选图已走 Photo Picker、保存到相册走 MediaStore 无需权限）；需要扫全相册时再按需声明 `READ_MEDIA_IMAGES` | 高（审核风险） |
| 3 | `app` 里首次启动的隐私弹窗 | 确认隐私政策文案覆盖「相机/麦克风/相册/通知」的实际用途（合规要求先于权限弹框） | 高 |
| 4 | `feature-video` 的 `VideoShowActivity` 等老式 `Activity` | 如果将来要用权限，先把基类换成 `AppCompatActivity`/`ComponentActivity`；否则 `PermissionGateway.of()` 会给出明确异常 | 中 |
| 5 | 定位 | `Permissions.LOCATION` 已就绪；等「附近的人」需求落地时直接用，不要再新写一套 | 低 |
| 6 | `retromusic` | 不动（独立子工程，有自己的 `PermissionActivity`） | — |

---

## 5. 风险

| 风险 | 缓解 |
|---|---|
| `Mutex` 按 `identityHashCode(activity)` 存，Activity 重建后旧锁条目残留 | 每次请求结束不主动清理也不会有功能问题（锁对象很小）；如需清理，在 `request` 的 `finally` 里当 `locks.size > 32` 时修剪不在执行的条目 |
| 协程版不弹「去设置」对话框，调用方忘了处理 `PermanentlyDenied` | 所有已迁移调用点都显式 `when`；`Permissions` 的工厂函数默认带理由文案，减少「忘了传 rationale」 |
| 从设置页返回的时序（某些 ROM 返回后 `checkSelfPermission` 有延迟） | 现有实现是 `ActivityResult` 回调里立刻检查；如果遇到个别 ROM 的延迟，在协调器里加一次 300ms 重试即可（不改接口） |
| 通知权限在 Android 13 以下返回空集合 → 判定为 `Granted` | 这是有意为之：低版本没有这个权限，视为已具备；调用方不需要判断版本 |
| 迁移期间两套 API 并存（`PermissionRequester` 回调 + `PermissionGateway`） | `PermissionRequester` 保留是因为 Java/老页面还要用；`PermissionGateway` 只是它的协程包装，行为一致，不存在双份判定逻辑 |

---

## 6. 验证

```bash
./gradlew --offline :uikit:compileDebugKotlin
./gradlew --offline :feature-chat:compileDebugKotlin
./gradlew --offline :feature-video:compileDebugKotlin
./gradlew --offline :feature-photosolve:compileDebugKotlin
./gradlew --offline :app:compileDebugKotlin
```

全部通过（2026-11-11，离线）。`combineStates()` 是纯函数，后续可在 `:uikit` 加 `src/test` 做表驱动单测（本模块目前没有测试目录）。
