# VPN 客户端 升级与架构重构方案

> 基于 `myandroidTest` 现状勘察（324 个 Java 文件 / 约 34k 行）与本次已落地的构建升级。
> 核对日期：2026-11-01

---

## 0. 现状基线（已核实）

| 项 | 现状 |
|---|---|
| 包名 / 应用 ID | `com.openapi.ks.free1` |
| 版本 | versionCode `1001001031`，versionName `1.1.1.31` |
| 编译 | AGP 8.13.2 / Gradle 8.13 / JDK 17 源级 / compileSdk 37（本次已升） |
| strictMode | 无 |
| 原生库 | strongSwan **6.0.3dr1**（预编译 `.so`，`app/libs/{arm64-v8a,armeabi-v7a}`，共 32 MB） |
| VPN 方案 | IKEv2 / ESP，`kernel-libipsec` 用户态 IPsec（Android 无内核 IPsec） |
| 路由 | 全流量：`addRoute("0.0.0.0",0)` + `addRoute("::",0)` |
| 分流 | **无**。`addDisallowedApplication` 调用数 = 0；`SPLIT_TUNNELING_*` 只用于"整族封禁"，不用于选择性代理 |
| 混淆 | release `minifyEnabled true` + 资源压缩 |
| 明文流量 | `network_security_config` 全局 `cleartextTrafficPermitted="true"`，API 基址 `http://api.ok123find.top` |
| 状态管理 | 4 套并存：Service bind + EventBus + 静态 HashMap(`StaticDataUtil`) + 广播 |
| 视图绑定 | ButterKnife 10.2.3（31 文件 / 113 个 `@BindView`），ViewBinding 已开启但**使用率 0%** |
| 持久化 | GreenDAO 3.3.0，仅 1 个实体 `ConnLog`；SharedPreferences 55 处调用、13 个文件 |

---

## 1. 依赖与构建升级（本次已完成，构建已验证通过）

### 1.1 已升级

| 依赖 | 旧 | 新 |
|---|---|---|
| AGP | 8.13.0 | **8.13.2** |
| compileSdk / targetSdk | 36 | **37**（okhttp 5.5.x 的 AAR 元数据要求） |
| androidx.appcompat | 1.7.1 | **1.8.0** |
| androidx.activity | 1.9.3 | **1.13.0** |
| com.google.android.material | 1.13.0 | **1.14.0** |
| androidx.annotation | 1.9.1 | **1.11.0** |
| androidx.recyclerview | 无版本号 | **1.4.0**（显式钉版本，原为隐式传递解析） |
| androidx.cardview | 无版本号 | **1.0.0**（同上） |
| Glide (+compiler, okhttp3-integration) | 5.0.5 | **5.0.9** |
| OkHttp / logging-interceptor | 5.1.0 | **5.5.1** |
| Gson | 2.13.2 | **2.15.0** |
| GreenDAO runtime | 3.3.0 | 3.3.0（**3.3.1 只存在于插件，运行时无该版本，不可升**） |
| play-services-ads | 24.6.0 | **25.5.0** |
| 新增 | — | androidx.fragment 1.9.1、lifecycle-viewmodel 2.9.4、lifecycle-livedata 2.9.4 |

### 1.2 已移除（均已确认零引用）

- `androidx.legacy:legacy-support-v4` / `legacy-support-v13`（无 `android.support.*` 引用）
- `androidx.palette` / `androidx.percentlayout`（代码与布局均无引用）
- `androidx.multidex`（minSdk 28 已内置 multidex，`MyApplication` 改继承 `android.app.Application`）
- `renderscriptTargetApi` + `renderscriptSupportModeEnabled`（RenderScript 已废弃，无 `R2.` 引用）
- `com.neenbedankt.gradle.plugins:android-apt:1.8`（2015 年的旧插件）
- `repositories { maven(...) }` 4 份重复声明（已收敛到 `settings.gradle` 的 `pluginManagement` + `dependencyResolutionManagement`）

### 1.3 构建安全修复

- **签名配置外置**：原先 `app/build.gradle` 内硬编码 `keyAlias 'themass'` / `keyPassword 'themass'` / `storePassword 'themass'` 和绝对路径 `/Users/liguoqing/soft/myapp/key`——这份密钥在多台机器上不可复用，且口令已泄露在版本历史里。现改为读取 `keystore.properties`（已加入 `.gitignore`），模板见 `keystore.properties.example`。
- **⚠️ 必须做的事**：该 keystore 口令已随源码外泄，**请立刻更换 keystore 与密码**，而不是只改配置读取方式。

### 1.4 仓库镜像

`dl.google.com` / `maven.google.com` 在当前网络下 DNS 不可达（已实测超时），构建原先直接失败。已改为阿里云 Google/Public/Gradle-Plugin 镜像优先、官方源兜底。

### 1.5 已验证

```
./gradlew :app:compileFreeDebugJavaWithJavac   → BUILD SUCCESSFUL
./gradlew :app:assembleFreeDebug               → BUILD SUCCESSFUL（APK 55 MB）
```

> 注：`assembleRelease` 需要 `keystore.properties`，且本次未跑 R8 全量混淆验证，release 出包前需单独回归。

### 1.6 仍需处理（本次未动，避免影响现有逻辑）

1. **ButterKnife 必须移除**。10.2.3 自 2020 年起无维护，其注解处理器在 AGP 8.x 上属于高危项。ViewBinding 开关已就绪，迁移路径明确：31 个文件、113 个绑定，逐个替换后删除 `butterknife` / `butterknife-compiler` / `butterknife-gradle-plugin` 三条依赖。
2. **删除 `app/libs/armeabi-v7a/`**（15 MB）。`abiFilters` 已只保留 `arm64-v8a`，该目录是死重量，且会误导后续判断。
3. `app/build.gradle` 中残留的 `packagingOptions`（已被新版 `packaging {}` 取代）与已注释的 Mobvista/腾讯广告依赖块，清理后约减 40 行。
4. 打开 lint：目前 `abortOnError false` + `checkReleaseBuilds false`，等于完全关闭。建议改为 `checkReleaseBuilds true`、`abortOnError true`，先 `baseline` 一次性收敛存量问题。

---

## 2. 架构重构：领域划分

### 2.1 现在的问题（按严重度）

| # | 问题 | 证据 |
|---|---|---|
| 1 | **无领域层**。`BaseService` 只是 Volley 包装，方法签名是"传 URL 进来"，不是"我要服务器列表"。UI 自己拼 URL。 | `VpnRadFragment.java:320`、`LocationItemFragment.java:85`、`LoginActivity.java:92` |
| 2 | **4 套状态通道并存**，没有单一事实来源。VPN 连接态同时存在于 `VpnStateService` bind、`StaticDataUtil` 静态 HashMap、EventBus 事件、`UPDATE_STATUS_ACTION` 广播。 | `StaticDataUtil.add(Constants.VPN_STATUS)`；`MainFragmentViewPage:92` 每次 tab 切换重建整个 tab |
| 3 | **上帝类**。`VpnRadFragment`（578 行）一个类里做了：布局、服务绑定、HTTP 请求、`ping` 子进程、广告、EventBus、SharedPreferences、模拟器检测、`DialogFragment` 定义、两套 AsyncTask/Runnable 状态机。 | `VpnRadFragment.java` 全文 |
| 4 | **网络层反向依赖 UI**：401 时 HTTP `Request` 直接 `startActivity(LoginActivity)`。 | `yewu/net/request/BaseRequest.java` |
| 5 | **UI 反向依赖持久化**：23 处 SharedPreferences 调用直接在 `ui/` 和 `ks/` 里。 | `VpnRadFragment:230-234`、`BaseDrawerActivity:208` |
| 6 | **实体与 DTO 混居**：GreenDAO 生成物落在 `myapp/bean/form`（网络表单包）内，DAO 与会话同包。 | `greendao { daoPackage 'com.openapi.myapp.bean.form' }` |
| 7 | **依赖注入为零**，全靠 `MyApplication.getInstance()` 静态单例。 | `MyApplication.java:42` |

### 2.2 目标分层

```
┌──────────────────────────────────────────────────────────┐
│ presentation    ui/{vpn,location,user,feedback}           │
│                 Activity/Fragment/ViewModel + ViewBinding │
├──────────────────────────────────────────────────────────┤
│ domain          domain/model   (VpnProfile, Server, Session)│
│                 domain/repo    (VpnSessionRepository,      │
│                                 ServerRepository,          │
│                                 UserRepository)   ← 接口   │
│                 domain/usecase (Connect/Disconnect/         │
│                                 SelectServer/RuleMatch)    │
├──────────────────────────────────────────────────────────┤
│ data            data/remote  (ApiClient + ApiService)      │
│                 data/local   (PrefsStore, Dao, Migrations) │
│                 data/repo    (实现 domain/repo)            │
├──────────────────────────────────────────────────────────┤
│ platform        vpn/          CharonVpnService, TunBuilder,│
│                               VpnStateService, 状态机       │
│                 core/         日志、线程、错误模型、常量     │
└──────────────────────────────────────────────────────────┘

依赖方向：presentation → domain ← data
                              ↑
                          platform（只依赖 domain 模型）
```

关键约束（可写进 `CONSTRAINTS.md`，用 lint/ArchUnit 卡住）：

- `ui/` 不允许出现 `okhttp3`、`com.android.volley`、`DaoSession`、`SharedPreferences`
- `data/remote` 不允许出现 `android.app.Activity`、`androidx.fragment`
- `domain/` 是纯 Java/Kotlin，不依赖 android.*（便于单元测试）
- `platform/` 不允许依赖 `ui/`

### 2.3 分阶段落地（每步都可独立提交、独立回归）

**阶段 A — 抽状态（✅ 已完成，见第 2.4 节）**

1. 新建 `VpnSessionState`（不可变数据类：`State` / `ErrorState` / `profile` / `connectedAt` / `server`）。
2. `VpnStateService` 的 `LocalBinder` 之上包一层 `VpnSessionRepository`，内部持有 `MutableLiveData<VpnSessionState>`；`VpnStateListener` 回调只负责往里塞值。
3. 删掉 `StaticDataUtil` 里的 `VPN_STATUS`、删掉 `UPDATE_STATUS_ACTION` 广播、删掉 `VpnClickEvent`/`TabChangeEvent` 之外的 EventBus 用法。
4. `VpnRadFragment` 改为 `observe(repo.state())`，578 行可降到 ~200 行；`PingTask`、`StatChangeJob` 移到 `domain/usecase/ProbeLatencyUseCase`。
5. `VpnStatusFragment` 已删除（死代码）。

**阶段 B — 抽数据访问**

6. `data/remote/ApiService`：一个接口，方法是领域语义（`suspend fun servers(location): List<Server>`），内部用 OkHttp + 一个统一的 `CallAdapter`；删除 `VolleyUtils`、`OkHttpStack`、`HttpUtils`、`Volley` 整套（三套 HTTP 并存，选 OkHttp 一套）。
7. `BaseRequest` 里的跳登录逻辑改为抛出 `AuthRequiredException`，由 `presentation` 层的统一错误处理器消费。
8. `Constants` 拆为 `ApiEndpoints`（URL 常量，按域拆分）+ `BuildConfig` 注入的 host，支持 `flavor` 覆盖 staging/prod。
9. `data/local/PrefsStore`：把 55 处 SharedPreferences 调用收敛成一个接口，内部换用 DataStore；GreenDAO 的 `DaoSession` 只从 `data/local` 暴露。

**阶段 C — 收尾**

10. ButterKnife → ViewBinding（31 文件）。
11. 引入 Hilt（或手写一个 ~50 行的 `ServiceLocator`，如果不想引 DI 框架）。`MyApplication` 只保留 `onCreate` 初始化与 DI 容器。
12. 统一错误模型 `Result<T>` / `ApiError`，替换 4 个自定义异常类。

### 2.4 阶段 A 已落地（2026-11-01）

新增 4 个文件，共 315 行；`VpnRadFragment` 从 578 行降到 526 行，且不再接触 `VpnStateService`。

| 文件 | 职责 |
|---|---|
| `domain/model/VpnSessionState.java` | 不可变状态快照（state / errorState / imcState / bound），带 `equals` 去重 |
| `domain/repository/VpnSessionRepository.java` | 仓库接口：`observeState()` / `current()` / `connect()` / `disconnect()` / `start()` |
| `data/vpn/CharonVpnSessionRepository.java` | 唯一实现：绑定 `VpnStateService`、翻译回调、发布 `LiveData`、断连自动重绑 |
| `myapp/base/AppContainer.java` | 轻量依赖容器，托管仓库单例 |

**语义上的关键改动**

1. **单一事实来源**：状态只在 `CharonVpnSessionRepository` 里产生。`Constants.VPN_STATUS` + `StaticDataUtil` 的写入口已从 UI 移除（`Constants.VPN_STATUS` 常量本身保留，因 `StaticDataUtil` 还被 `BaseRequest`/`HttpDNSUtil` 用作 DNS 缓存，属于另一件事）。
2. **bind 生命周期上移**：仓库在 `Application.onCreate()` 里绑一次，跨 Fragment 复用。UI 不再在 `onDestroyView` 里 `unbindService`，也不会因重建丢状态。
3. **连接指令可排队**：服务没绑好时 `connect()` 把 profile 挂起，`onServiceConnected` 时自动下发，不再是静默失败。
4. **错误提示与状态解耦**：`StatChangeJob` 的 `hasError()` 直接弹 Toast，导致每次状态回调都可能重复提示；现在由 `render()` 在 `hasError()` 时只显示一次，Toast 文案抽成 `showErrorToast()`。
5. **EventBus 泄漏修复**：原先 `onCreate` 里 `register`、`onDestroyView` 里漏了 `unregister`；现改为 `onDestroy` 配对。
6. **看门狗去重**：`VpnCheckTask` 改用局部 `count`，入口先 `removeCallbacks` 再 `postDelayed`，不再重复排队。

**UI 侧的验证**（已编译通过，未做真机回归）：

- `grep VpnStateService` 在 `ui/` 下只剩 `ErrorState` 类型引用，零服务绑定
- `grep "VpnStateService.class"` 全仓只剩 2 处：`CharonVpnService`（原生侧固有）与本仓库
- `domain/` 零 `android.*` 依赖（仅 `androidx.annotation` / `androidx.lifecycle` 类型）

> 阶段 A 之后仍待办：`StaticDataUtil` 的 DNS 缓存与 userIp 传递（阶段 B）、`BaseService` 收敛成 `ApiService`（阶段 B）、ButterKnife 移除（阶段 C）。

---

## 3. strongSwan 升级

### 3.1 版本现状与安全影响

- 原生库版本：**6.0.3dr1**（`strings libstrongswan.so` 实测）
- 上游最新：**6.1.0**（2026-09-07）
- **6.0.3 处于受影响区间**：`CVE-2026-78131`（x509 插件属性证书解析 use-after-free，影响 4.2.0–6.0.7）、`CVE-2026-78123`（openssl 插件 PKCS#7 崩溃，影响 5.0.2+）等共 11 个漏洞。6.0.7（2026-06-08）是 6.0 分支最后一个修复版本，6.1.0 为当前主线。
- 6.1.0 起 **IKEv1 默认关闭**。本客户端只用 IKEv2，无迁移风险。

### 3.2 ✅ 已升级到 6.1.0（2026-11-01）

源码位于 `/Users/gqli/work/strongswan`（tag `android-2.6.3`，`AC_INIT` = 6.1.0），
已成功交叉编译并替换 `app/libs/arm64-v8a/` 下的全部 8 个 `.so`。

**踩过的 4 个坑（上游脚本在 macOS 上不可直接用的原因）**

| # | 问题 | 解决 |
|---|---|---|
| 1 | `compile.sh` 写死 `toolchains/llvm/prebuilt/linux-x86_64` | 改成按 `uname -s` 推导宿主 tag（macOS 是 `darwin-x86_64`），已打补丁 |
| 2 | git 源码树缺 4 个 autoconf/gperf/flex/bison 生成文件（`oid.c`、`proposal_keywords_static.c`、`settings_parser.c`、`settings_lexer.c`） | 直接下载官方 release tarball（已预生成）取这 4 个文件复制过来；比装 bison 3.x 再 autogen 省事 |
| 3 | `Android.common.mk` 由 `configure` 生成，缺失 | `sed 's/@PACKAGE_VERSION@/6.1.0/' Android.common.mk.in > Android.common.mk` |
| 4 | libcrypto 的头文件布局与 `Android.mk` 期望不符 | 把 `jni/openssl/openssl/` 拷成 `jni/openssl/include/openssl/` |

**构建命令**

```bash
# 1) 交叉编译 libcrypto（一次性）
ANDROID_NDK_ROOT=$HOME/Library/Android/sdk/ndk/27.0.12077973 \
OPENSSL_SRC=$HOME/work/openssl-src/openssl-3.5.4 \
NO_DOCKER=1 ABIS="arm64-v8a" \
bash src/frontends/android/openssl/build.sh

# 2) 构建 strongSwan 原生库
cd src/frontends/android/app/src/main/jni
$ANDROID_NDK_ROOT/ndk-build NDK_PROJECT_PATH=. \
  APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk \
  APP_ABI=arm64-v8a APP_PLATFORM=android-28 \
  NDK_OUT=/tmp/ss-obj NDK_LIBS_OUT=/tmp/ss-libs -j8
```

**产物核对**

| 项 | 结果 |
|---|---|
| 版本号 | `6.1.0`（`libstrongswan` / `libcharon` 均已确认） |
| JNI 导出 | 7 个，与原库**完全一致** |
| 插件集合 | `android-log socket-default openssl nonce pkcs1 pem x509 xcbc kdf revocation eap-*` 与原库一致 |
| 提案 token | `aes256gcm16` / `chacha20poly1305` / `curve25519` / `ecp256` / `ecp384` / `prfsha256` / `prfsha384` 全部存在 → 第 3.3 节的新提案可以生效 |
| MOBIKE | `libcharon` 中 12 处符号，与之前一致 |
| 库体积 | `libstrongswan` 9.3MB → 5.1MB，`libcharon` 5.6MB → 0.86MB（小了约 9MB） |

**注意**：这是首次可以用源码重建原生库，`.gitignore` 里的 `app/src/main/jni/` 条目已不再需要——源码现在放在仓库外的 `/Users/gqli/work/strongswan`。

### 3.2b 原升级步骤（保留备查）

`.gitmodules` 指向 `app/src/main/jni/strongswan`，但该目录被 `.gitignore` 忽略且当前不存在——**现在仓库里的 `.so` 是无源码的预编译产物**，无法直接升级。必须重建：

```bash
# 1. 准备 strongSwan 源码
git clone --branch 6.1.0 https://github.com/strongswan/strongswan.git
cd strongswan
./autogen.sh && ./configure && make dist     # 生成 dist 产物，全局库版本匹配

# 2. 交叉编译 libcrypto（libstrongswan 依赖 libcrypto_static）
ANDROID_NDK_ROOT=$HOME/Library/Android/sdk/ndk/27.0.12077973 \
OPENSSL_SRC=$HOME/src/openssl-3.x \
src/frontends/android/openssl/build.sh

# 3. 构建 Android 客户端
cd src/frontends/android
echo "ndk.dir=$HOME/Library/Android/sdk/ndk/27.0.12077973" >> local.properties
./gradlew assembleRelease     # 或 :app:buildNative 只产 so
```

拿到新的 `arm64-v8a/*.so` 后替换 `app/libs/arm64-v8a/`，并**删除 `armeabi-v7a/`**。

> 注意：本项目的 Java 侧已大幅魔改 `CharonVpnService`（5 万字节，加了通知栏、埋点、日志上传、`safeNativeCall` 包装）。上游 6.1.0 的 `CharonVpnService` 与本地版本 API 已经分叉，**不要直接覆盖**，只替换 `.so`，然后按 JNI 签名逐个核对（`initiate`、`initializeCharon`、`bypassNextNetwork`、`setUnderlyingNetworks` 等）。

### 3.3 加密参数修正（✅ 已完成，2026-11-01）

#### 核查结论（与最初的判断有出入，以下是最终确认的版本）

| 判断 | 结论 | 依据 |
|---|---|---|
| `charon.plugins.ikev2.*` 是无效键 | **成立** | 上游 `src/libcharon/plugins/` 下**没有 ikev2 插件**；这类键被静默忽略 |
| 应改用 `connection.ike_proposal` / `connection.esp_proposal` | **成立** | `libandroidbridge/backend/android_service.c` 的 `parse_proposal()` 只读这两个键 |
| 字段名是复数 `proposals` | **不成立** | 实际键名是**单数** `ike_proposal` / `esp_proposal` |
| 值可以是逗号分隔的多提案列表 | **不成立** | `proposal_create_from_string()` 用 `enumerator_create_token(algs, "-", " ")`，只认 `-`，不认逗号 |
| 同类算法候选用 `/` 分隔 | **不成立**（我一度写错） | `/` 只出现在 `print_alg()` 的**输出**里；写进输入会让整条提案解析失败 |

**最终确认的语法**：
- 每个键的值是一条**单一提案**；`-` 分隔其中所有算法，同类算法的多个候选也用它串联，按优先顺序排列
- AEAD 与传统 CBC 不能混在同一条提案里（上游测试用例 `aes128gcm16-aes128-sha256-modp3072` 期望解析失败）
- 解析失败**不会报错**，只会打一条 debug 日志并回退到内置默认算法——这正是原配置的问题所在

#### 原配置的实际情况

```java
writer.setValue("charon.plugins.ikev2.proposals",
    "aes256gcm16-sha256-modp2048,aes256-sha256-modp2048,aes256-sha1-modp2048,...");
```

三重失效：键名不存在（无 ikev2 插件）、即使键存在值也是逗号列表（解析器不认）、且混了 AEAD 与 CBC（会被 check_proposal 拒绝）。**结果是协商一直在用 strongSwan 的内置默认提案，这行配置从未生效过。**

#### 修改后的实现

`CharonVpnService` 新增 `applyProposals(SettingsWriter)`，分两档：

```java
// tier 0 现代档：AEAD + ECP/Curve25519，无 SHA-1，无 MODP
connection.ike_proposal = aes256gcm16-prfsha384-ecp384-prfsha256-ecp256-prfsha256-curve25519
connection.esp_proposal = aes256gcm16-ecp384-ecp256-chacha20poly1305-curve25519

// tier 1 兼容档：服务端不认 AEAD/ECP 时回退；保留 CBC+MODP 但去掉 SHA-1
connection.ike_proposal = aes256-sha256-modp2048-aes128-sha256-modp2048
connection.esp_proposal = aes256-sha256-aes128-sha256
```

> **2026-11-02 修正：新增第 3 档（原生默认）。** 前两档都不含 SHA-1，且第 1 档 ESP 强制 DH（PFS）。
> 老服务端（实测环境为 strongSwan 5.6.3，`esp=` 未配 DH）可能一档都协商不上，IKEv2 全部失败。
> 改提案之前客户端实际一直在用 androidbridge 内置默认（IKE 两组、ESP 四组含 SHA-1 且 DH 带/不带都提供，
> 见 `android_service.c` 的 `parse_proposal` 调用处），这才是能连上老服务端的原因。
> 现在降档链为 0 现代 → 1 兼容 → 2 不写提案键（回到原生默认）。代价：第 3 档含 SHA-1，
> 服务端升级后应优先让连接停在前两档。

**自动回退**：`STATE_GENERIC_ERROR` 分支里，若尚未降档则调用 `retryWithFallbackProposal()` 重新发起连接，成功则不向 UI 报错——否则用户会看到一次假失败。`onStartCommand` 在用户主动发起新连接时把档位重置回 0，避免一次失败让进程永久停留在兼容档。

#### 验证

- 用原生库的算法注册表逐 token 校验：tier0/tier1 全部合法；旧配置产生非法 token `modp2048,aes256`；`/` 写法产生 `ecp384/aes256gcm16`
- 新增 `app/src/androidTest/.../ProposalFormatTest.java`，绑定原生 `Utils.isProposalValid()`（走同一套 `proposal_create_from_string`）做断言，覆盖两档合法性 + 旧配置/`/`/AEAD+CBC 三种反例
- `./gradlew :app:assembleFreeDebug :app:assembleFreeDebugAndroidTest` 通过
- ⚠️ **仪器测试未在设备上执行**（本机无连接设备/模拟器），需真机 `connectedAndroidTest` 才能得到原生解析器的最终确认

#### 遗留的算法强度问题（原分析仍然有效）

1. **全部用 MODP（有限域群），无一条 ECP**。modp2048 只有约 112 bit 安全强度；同等强度下 **ECP-256 / Curve25519 在移动 CPU 上快 3–10 倍**，握手耗电明显更低。手机端这是最直接的性能与安全双赢项。
2. **仍保留 `sha1` 组合**。SHA-1 已不适合作为 IKE/ESP 的 PRF/完整性算法。
3. `charon.plugins.ikev2.proposals` **不是 strongSwan 的合法配置键**。IKE 提案应通过 `connections.<name>.proposals`（或 `charon.proposals`）设置，`ikev2.` 命名空间下没有 `proposals` 项，这几行大概率是无效配置，实际协商仍走默认 proposal 列表——也就是说它现在是"看起来在加固，实际没生效"。

建议改为：

```java
// IKE 提案：AEAD + ECP/Curve25519，去掉 SHA-1
writer.setValue("connection.proposals",
    "aes256gcm16-prfsha384-ecp384,aes256gcm16-prfsha256-ecp256,"
  + "aes256gcm16-prfsha256-curve25519,aes128gcm16-prfsha256-ecp256");
// ESP 子 SA 提案：ChaCha20 系列对无 AES-NI 的 ARM 更友好
writer.setValue("connection.esp_proposals",
    "chacha20poly1305-prfsha256-ecp256,aes256gcm16-prfsha256-ecp256");
```

> 需与服务端同步放开对应算法，否则会退化为 NO_PROPOSAL_CHOSEN。建议先服务端加、客户端再切。

### 3.4 其他 IKEv2 优化

- **MOBIKE**：允许网络切换（Wi-Fi ↔ 4G）时保持 IKE_SA 不断。`CharonVpnService` 需要监听 `ConnectivityManager` 并调用 `setUnderlyingNetworks()`，当前只做了一次 `bypassNextNetwork`。
- **IKEv2 fragmentation**：UDP 分片对弱网握手成功率影响很大，6.x 默认开启，但需确认服务端 `fragmentation = yes`。
- **MTU/DNS**：`global.mtu` 取自 profile，但未见基于链路 MTU 的自适应。libipsec 用户态转发对 MTU 极敏感，建议按网络类型（Wi-Fi 1500 / LTE 1428 / 部分 3G 1280）下发。

---

## 4. 全流量 → 智能分流

### 4.1 三个层次的"分流"，成本递增

**层次 1：按应用分流（推荐立即做，零服务端改动）**

Android `VpnService.Builder` 原生支持：

```java
Builder b = new Builder(...);
for (String pkg : excludedPackages) {
    b.addDisallowedApplication(pkg);   // 这些 App 不走 VPN
}
```

价值：银行类 App、支付、内网办公、本地服务全部直连，用户体验立刻变好，同时降低服务器带宽。这是"选择性代理"里投入产出比最高的一档，大约 100 行代码 + 一个多选设置页。

**层次 2：按目的地址分流（中等成本）**

利用已有的 `BuilderCache.addRoute()` —— 框架已经支持"只把特定网段送进隧道"：

- 默认不 `addRoute("0.0.0.0", 0)`，改为下发精确路由表
- 路由表来源：`chnroutes` / GeoIP 规则（国内网段直连，其余走隧道），或反过来"仅代理白名单网段"
- 需要解决：路由表体积（国内约 8k 条，Android `addRoute` 有数量与性能上限）、更新机制、IPv6

**层次 3：按域名/规则引擎分流（这是真正的"智能代理"，但需要换数据面）**

要点：**IKEv2/IPsec 无法天然做规则引擎分流**。它工作在 `VpnService` 的 TUN 与内核 IPsec 之间，中间没有可以插 rule-based 转发的位置。要实现 Clash/Surge 那种"按域名、按 GeoIP、按进程、按规则集"的智能路由，必须在 TUN 之后加一层**用户态代理**：

```
现在：  TUN ──> libipsec ──> ESP/UDP ──> 服务器
        无分流决策点

目标：  TUN ──> tun2socks ──> 规则引擎 ──┬─> direct（本地 socket 直连）
                                        └─> proxy  ──> 加密隧道 ──> 服务器
```

这层 tun2socks + 规则引擎，社区已有成熟实现（见第 5 节），不需要自己写。

**结论**：层次 1 立刻做；层次 2 作为中期；层次 3 意味着**隧道协议也要一起换**（IPsec 不是用户态代理协议，无法作为规则引擎的 outbound）。这就自然引到第 6 节。

---

## 5. GitHub 上的开源强化项目

### 5.1 可直接集成的（规则引擎 / tun2socks）

| 项目 | 语言 | 适配度 | 说明 |
|---|---|---|---|
| **[sing-box](https://github.com/SagerNet/sing-box)** | Go | ★★★★★ | 自带 `libbox` Android 绑定；内置 tun 模式、规则集、GeoIP/GeoSite、DNS 分流（fake-ip）、按进程分流、多 outbound 负载/故障转移。出站协议覆盖 WireGuard / Shadowsocks / VLESS / Trojan / Hysteria2 / TUIC / QUIC。**最贴合"智能代理"需求**。 |
| **[mihomo (clash-meta)](https://github.com/MetaCubeX/mihomo)** | Go | ★★★★☆ | 规则生态最丰富（rule-providers、proxy-groups、fallback/url-test）。有 Android 集成先例，但官方 Android 壳不如 sing-box 完整。 |
| **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** | C | ★★★★☆ | 轻量 TUN→SOCKS5，官方支持 Android（含 per-app、DNS 处理、`hev-socks5-tunnel` 静态库）。**如果只想加"分流能力"而不想引入整套运行时，用这个**：TUN → socks5 → 你自己的转发进程。 |
| **[Xray-core](https://github.com/XTLS/Xray-core)** | Go | ★★★☆☆ | VLESS + Reality 是当前抗封锁最强组合之一。协议侧优秀，规则引擎不如 sing-box。 |
| **[AmneziaWG](https://github.com/amnezia-vpn/amneziawg-go)** | Go/C | ★★★☆☆ | WireGuard 混淆分支，可绕过基于 WG 指纹的识别。 |
| **[WireGuard-Android](https://github.com/WireGuard/wireguard-android)** | Kotlin | ★★★☆☆ | 官方客户端，其 `tun` 与 `VpnService` 封装是很好的参考实现，`wg-go` 可直接编进 APK。 |

**给这套项目的具体建议**：`hev-socks5-tunnel` 是你现有架构下**侵入最小**的选择——保留 `CharonVpnService` 的 IPsec 数据面不动（或逐步替换），把 `Builder` 接管过来交给 hev-socks5-tunnel，规则引擎侧用 socks5 链到上游。代价是 TUN→socks5→IPsec 的封装需自行设计，不如直接换 sing-box 干净。

### 5.2 strongSwan 生态

- **[strongswan/strongswan](https://github.com/strongswan/strongswan)** 本身：`src/frontends/android` 就是官方客户端源码，2.6.3 为当前 App 版本。**建议以它为基线做 diff，而不是继续在 2016 年的分叉上堆补丁。**
- 社区增强：`strongswan-android` 一系列 fork 活跃度都很低（多为 single-commit），**不建议依赖**。
- 服务端侧可考虑 **[libreswan](https://github.com/libreswan/libreswan)**（与 strongSwan 客户端互通），或继续用 strongSwan 服务端。

---

## 6. 更强的端上 VPN 方案分析

先明确一个物理约束：**Android 上任何 VPN 都要走 `VpnService` + TUN，没有真正的"内核直通"捷径**。差别只在 TUN 之后走什么。

| 方案 | 吞吐 | 握手 | 耗电 | 抗封锁 | 端上成熟度 | 服务端改造 |
|---|---|---|---|---|---|---|
| **IPsec/IKEv2**（现状） | 中低（libipsec 用户态转发，无 GRO/GSO） | 1-RTT | 中 | 差（IPsec/ESP 特征明显，UDP 500/4500 易被封） | 高（strongSwan 十年打磨） | 无 |
| **WireGuard**（wg-go / 内核） | **高** | 1-RTT（Noise IK） | **低**（无状态、无常驻线程、每 2 分钟一次握手） | **差**（固定头部特征，UDP 易识别/封锁） | 高（官方 Android 客户端） | 需新增 WG 服务端 |
| **WireGuard + 混淆**（AmneziaWG） | 高 | 1-RTT | 低 | 中 | 中 | 需新增 |
| **QUIC / MASQUE**（HTTP/3 CONNECT-UDP） | 高 | 1-RTT | 中 | **好**（伪装成常规 HTTPS/HTTP3 流量） | 中（Cloudflare 系实现为主） | 需新增 |
| **Hysteria2 / TUIC** | **很高**（BBR 类拥塞控制，弱网优势大） | 1-RTT | 中高 | 中好（QUIC 特征，需 SNI 伪装） | 中 | 需新增 |
| **VLESS + Reality / Trojan** | 中高（TCP） | 1-RTT | 中 | **最好**（完全模仿真实 TLS 站点） | 高 | 需新增 |
| **OpenVPN** | 低 | 高（TCP-over-TCP 灾难） | 高 | 差 | 高 | 不推荐 |
| **Shadowsocks-2022** | 中高 | 1-RTT | 低 | 中好 | 高 | 需新增 |

### 6.1 建议的三档路线

**档 A — 原地强化（1–2 周，零服务端风险）**
- 原生库升 6.1.0（修 11 个 CVE）
- 提案改 ECP/Curve25519 + AEAD（见 3.3）
- 打开 MOBIKE，优化 MTU
- 加 per-app 分流（层次 1）
- **收益**：安全性追上上游、握手更快、流量省下来、用户体验改进。**不解决"抗封锁"和"规则引擎"两个根本问题。**

**档 B — 双栈并存（2–4 周，推荐作为主路线）**
- 保留 IKEv2 作为"兼容/回退"通道
- 新增 **WireGuard** 作为默认快速通道（吞吐与耗电明显优于 libipsec；服务端 `wireguard-tools` 部署极其简单）
- 客户端做**自动选择**：先探测 UDP 可达性，WG 可用走 WG，被阻断回退 IKEv2
- **收益**：速度、耗电、可靠性三项实打实提升；实施成本可控。**仍不解决规则引擎。**

**档 C — 智能代理架构（1–2 个月，回答"智能分流"的唯一完整解）**
- 数据面替换为 **sing-box** 的 tun 模式（或 hev-socks5-tunnel + 自研转发）
- 出站协议至少配两种：**WireGuard**（快）+ **VLESS/Reality 或 Hysteria2**（抗封锁）
- 规则引擎接管全部路由决策：`geosite:cn` → direct、`geoip:cn` → direct、其余 → proxy group（url-test 自动选优）
- **收益**：真正的智能分流 + 抗封锁 + 自动选节点。**这是把产品从"VPN 客户端"升级为"代理客户端"的路线**，但 IKEv2 在这条路线里会被替换掉，strongSwan 从核心技术变成过渡技术。

### 6.2 一句话结论

> 如果目标是"更快的隧道"，答案是 **WireGuard**（比 libipsec 快 2–4 倍，耗电更低）。
> 如果目标是"更强的抗封锁"，答案是 **VLESS/Reality 或 MASQUE**。
> 如果目标是"智能分流"，**答案不是某个 VPN 协议，而是一个带规则引擎的用户态代理内核（sing-box）**——因为 IPsec 架构里没有做路由决策的位置。
> 三者可以同时满足，路径就是**档 C**；但那条路上 strongSwan 会退居为可选的回退通道。

---

## 7. 功能升级设计

按"用户可感知价值 / 实现成本"排序：

| 优先级 | 功能 | 说明 |
|---|---|---|
| P0 | **分应用代理** | 多选列表 + "仅代理/全部直连"白名单。`addDisallowedApplication` 实现，~100 行。 |
| P0 | **自动选优节点** | 现在的 ping 在 UI 线程旁路做（`VpnRadFragment:462`）。改为 `domain/usecase`：并发 TCP 握手 + HTTP HEAD 延迟采样，取 P50，按 EWMA 排序，缓存 5 分钟。 |
| P0 | **快速重连 + 网络切换保持** | MOBIKE + `setUnderlyingNetworks()`；断线后指数退避重连（1s/2s/4s/8s，上限 4 次，`MAX_RETRY_COUNT` 已有常量但未被正确使用）。 |
| P1 | **Kill Switch** | 引导用户开启系统级"始终开启的 VPN"+"无 VPN 时阻止连接"，比自研更可靠（`VpnService.isAlwaysOn()`）。 |
| P1 | **规则订阅** | 支持导入规则集（geosite/domain-list），本地规则优先、远程增量更新。 |
| P1 | **连接诊断** | 一键诊断：UDP 可达性、IKE 握手阶段、DNS 解析、MTU 探测。现有的 `ConnLog` 实体可直接扩展。 |
| P1 | **配置向安全收敛** | 全站 HTTPS（当前 API 基址是 `http://`，且全局允许明文），加证书 pinning。这是上架 Play 的合规风险点。 |
| P2 | **多协议并存 UI** | "智能/极速/兼容"三档，对应 6.1 的档 B。 |
| P2 | **本地化** | `bundle { language { enableSplit = true } }` 已开，但只有 `values/strings.xml` 一份，**没有任何翻译**；且有中文字符串硬编码在 `HttpUtils` 里（"上传成功"等）。 |
| P2 | **可观测性** | 崩溃率/连接成功率/握手耗时埋点。当前 `MobAgent` 全空（已删）、`LogUploadService` 只上传原始日志，没有结构化指标。 |

### 7.1 版本管理

`versionCode 1001001031` / `versionName "1.1.1.31"` 是手工拼接，无法追溯。建议：

- `versionCode = major*10000 + minor*100 + patch`（如 1.1.1 → 10101）
- `versionName` 由 git tag 驱动：`./gradlew -PversionName=1.2.0 assembleFreeRelease`
- 构建产物名带 commit 短哈希，便利用户反馈定位

---

## 8. 多协议能力（已在后续迭代中落地）

见 `docs/MULTI-PROTOCOL.md`。摘要：

- 抽象出 `TunnelEngine` 接口，已实现 **IKEv2** 与 **WireGuard** 两个引擎
- 协议注册表 + 自动选优（失败降权、10 分钟过期）
- 设置页可选协议；分应用分流两种协议都支持
- 已修复 WireGuard AAR 自带 VpnService 与 CharonVpnService 争抢 TUN 的问题

## 8. 建议的执行顺序

```
第 0 步（已完成）  依赖升级 + 死代码清理 + 签名外置 + 构建验证
第 1 步           换 keystore（口令已泄露）
第 2 步           阶段 A：VpnSessionRepository + 单一状态源        ← 收益最大
第 3 步           原生库升 6.1.0 + 提案改 ECP/AEAD（服务端同步）
第 4 步           per-app 分流 + 自动选节点 + Kill Switch
第 5 步           阶段 B/C：ApiService 收敛 + ButterKnife 移除
第 6 步           决策点：是否走档 B（WireGuard 双栈）/ 档 C（sing-box 架构）
```
