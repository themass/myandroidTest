# 权限请求去重：方案

状态：**方案，未改代码。**
基线：`weifu-refactor` @ `7b414878`。

---

## 1. 现状

### 1.1 代码层面

| 项 | 情况 |
|---|---|
| 现存的统一入口 | `:common` 的 `com.openapi.common.ui.permission`：`PermissionRequester`（142 行，链式 + 回调）、`PermissionState`/`PermissionStates`、`Permissions`（常量） |
| 已删除的旧代码 | `PermissionHelper`、`CompatiblePermissionManager`、`ModernPermissionManager`、`PermissionUtils`、`BasePermissionActivity`、`PermEvent`（第 5.10 批，提交 `73363dcf`） |
| 目前真正调用统一入口的地方 | **只有 2 处**：`ChatRoute.kt:95`（相机）、`ChatRoute.kt:157`（录音） |
| 自己直接调系统 API 的地方 | `PhotoSolveActivity.kt:123` 用 `ActivityResultContracts.RequestPermission()` 自己注册，没有走 `PermissionRequester` |
| 用法形态 | Java 友好的回调式：`PermissionRequester.with(activity).permissions(...).rationale(...).request(callback)` |

**判断**：权限代码没有「多套实现」重复了，重复问题已经从代码层解决。现在的重复在**用法层**：
1. `ChatRoute` 每次都要先 `PermissionStates.isGranted(...)` 再决定是否请求，这两步每个调用点都要写一遍；
2. 只有一个入口接受**回调**，没有挂起版，Compose 里写 `PermissionRequester.with(...)` 需要拿 Activity 并手写回调；
3. `PhotoSolveActivity` 绕过了它；
4. 「拒绝 → 解释 → 再请求 → 永久拒绝 → 去设置」的流程写在 `PermissionRequester` 里，但**提示文案由每个调用点传**，同一权限在不同页面文案不同（如录音在对话页和别处的措辞）。

### 1.2 权限声明层面（Manifest）

`app/src/main/AndroidManifest.xml` 里声明了 30+ 权限，其中大量是注释掉的旧权限（`CALL_PHONE`、`GET_ACCOUNTS`、`READ_PHONE_STATE`、定位等），仍然被注释保留；同时留着 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` 的痕迹。真正在用的运行时权限只有：`CAMERA`、`RECORD_AUDIO`、`READ_MEDIA_IMAGES`（选图走系统 Photo Picker，实际不需要）、通知（若有）。

---

## 2. 方案

### 2.1 统一入口：一个「权限门」+ 三种用法

保留 `PermissionRequester` 作为内部实现，新增一个薄门面 `PermissionGateway`，提供三种形态的同一能力：

```kotlin
object PermissionGateway {

    /** 挂起版（协程 / Compose LaunchedEffect / ViewModel 首选）。返回结果，不弹解释框。 */
    suspend fun request(activity: ComponentActivity, vararg permissions: String): PermissionOutcome

    /** Compose 版：声明式，结果通过回调。内部用 rememberLauncherForActivityResult。 */
    @Composable
    fun rememberRequester(): PermissionCall

    /** Java / 旧 View 页面用：保留现有回调式 API，签名不变。 */
    fun requester(activity: ComponentActivity): PermissionRequester
}

/** 三种结果，调用点需要显式处理每一种。 */
sealed interface PermissionOutcome {
    object Granted : PermissionOutcome
    /** 本次拒绝，可以再次询问（系统框还会出现）。 */
    data class Denied(val missing: List<String>) : PermissionOutcome
    /** 「不再询问」或策略拒绝，只能去设置页。 */
    data class Blocked(val missing: List<String>) : PermissionOutcome
}
```

**关键决定**：
- **挂起版不弹解释框**，只返回 `Outcome`。解释和「去设置」的界面交给调用方（Compose 的 `AlertDialog` / 页面的提示），因为对话框样式各页面不同，统一在一个工具类里画会冲突。
- **解释文案集中定义**：`Permissions.CAMERA` 对应 `R.string.permission_camera_rationale`，每个权限一条，放在 `Permissions` 旁边；调用方不再自己写文案。特殊页面可以覆盖，但默认统一。
- `PermissionRequester` 保留，`PermissionGateway.requester()` 只是转调它——Java 旧页面不用改。

### 2.2 Compose 用法

```kotlin
val gate = PermissionGateway.rememberRequester()
...
if (gate.granted(Manifest.permission.RECORD_AUDIO)) {
    startRecording()
} else {
    gate.request(Permissions.RECORD_AUDIO) { outcome ->
        when (outcome) {
            PermissionOutcome.Granted -> startRecording()
            is PermissionOutcome.Denied -> toast(getString(Permissions.rationaleOf(permission)))
            is PermissionOutcome.Blocked -> showGoToSettingsDialog()
        }
    }
}
```

`ChatRoute` 现在「先 `isGranted` 再 `request`」的两行合并成上面的写法。

### 2.3 页面被销毁时的处理

`PermissionRequester` 现有实现已经判断 `isFinishing/isDestroyed`；挂起版要额外处理：Activity 在请求过程中被销毁时，`suspendCancellableCoroutine` 的续体会被取消，返回 `Denied` 而不是挂住。这条要写进实现和测试。

### 2.4 Manifest 清理

| 动作 | 权限 |
|---|---|
| 保留 | `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、`CAMERA`、`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、`WAKE_LOCK`、`READ_EXTERNAL_STORAGE`（≤ Android 12 的旧设备读图） |
| 删除注释掉的旧权限 | `CALL_PHONE`、`GET_ACCOUNTS`、`READ_PHONE_STATE`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`GET_TASKS`、`REAL_GET_TASKS`、`USE_CREDENTIALS`、`MANAGE_ACCOUNTS`、`READ_CALENDAR`、`WRITE_CALENDAR`、`SET_DEBUG_APP`、`READ_LOGS`、`EXPAND_STATUS_BAR`、`MOUNT_UNMOUNT_FILESYSTEMS`、`browser.permission.READ_HISTORY_BOOKMARKS`、`launcher.permission.*`（只删注释行，不改行为） |
| 按需确认 | `MANAGE_EXTERNAL_STORAGE`、`READ_MEDIA_*`、`SYSTEM_ALERT_WINDOW`：现在是否仍需要，逐项确认后再删；没有确认的保持现状 |

只删注释行是零风险的；真正会影响行为的删除（`READ_MEDIA_*`、`MANAGE_EXTERNAL_STORAGE`）必须先确认代码里有没有判断分支，方案里列为「待确认」。

---

## 3. 迁移步骤

1. 新增 `PermissionOutcome`、`PermissionGateway`（含 Compose 版和挂起版），保留 `PermissionRequester` 不动。
2. 给 `Permissions` 加每个权限的默认解释文案（资源放 `:common` 的 `ui/permission`）。
3. 迁移调用点（当前只有 3 个）：
   - `ChatRoute.kt:95` 相机、`:157` 录音 → `PermissionGateway`；
   - `PhotoSolveActivity.kt:123` → `PermissionGateway`（它是 `ComponentActivity`，可以直接用）。
4. 写 JVM 测试：
   - `Outcome` 映射：授权 / 拒绝 / 永久拒绝三种状态组合；
   - 挂起版在 Activity 销毁时返回 `Denied` 而不是挂住；
   - `Permissions.rationaleOf` 每个权限都有文案（防止漏配）。
5. Manifest 删注释掉的旧权限（零风险部分）。
6. 手机验证：对话拍照、录音、拍照解题各自第一次请求、拒绝一次、永久拒绝、去设置后返回。

---

## 4. 为什么不再做成多套管理器

历史上重复的根因是「每个页面自己管理权限生命周期」。现在只保留一层，并且**唯一的入参是 `ComponentActivity`**，原因是：
- Activity Result API 要求 `registerForActivityResult` 在 `onCreate` 之前注册，`ActivityResultRegistry` 的动态注册规避了这一点，但前提是能拿到 Activity 的 registry；
- Fragment / View 都通过 `requireActivity()` 取得同一个入口，不需要第二套实现。

---

## 5. 风险

| 风险 | 应对 |
|---|---|
| Compose 版和挂起版行为不一致 | 两者都转调同一个 `PermissionRequester`，只有结果映射不同；用同一组单测覆盖 |
| 解释文案统一后，某些页面措辞不合适 | `request()` 允许传入覆盖文案，默认值来自 `Permissions` |
| Manifest 删权限导致旧设备异常 | 只删注释行；会改变行为的删除单独确认 |

## 6. 工作量

新增门面与测试 0.5 天；迁移 3 个调用点 0.5 天；Manifest 清理 0.5 天；手机验证 0.5 天。合计约 2 天。
