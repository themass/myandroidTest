# Google Play 上架合规审计

日期：2026-11-12　被测：`claude/vigorous-goldwasser-584d3f`　targetSdk 37 / minSdk 28
原则：**权限非用不申请；会被拒审的隐患一律挖出来。** 每一条都写明证据；没查过的写「未查」。

---

## 0. 结论

| | 数量 | 说明 |
|---|---|---|
| 已修复 | 8 | 见第 1 节 |
| **阻塞上架，需要你决定** | 4 | 见第 2 节（成人内容频道、隐私同意、账号删除、AI 内容举报） |
| 需要在 Play Console 声明 | 5 | 见第 3 节 |
| 按你的要求没动 | 2 | SEC1、SEC2，见第 4 节 |
| 未查 | 若干 | 见第 6 节 |

---

## 1. 已修复（本轮）

| # | 隐患 | 为什么会被拒 | 修复 |
|---|---|---|---|
| F1 | `UnsafeOkHttpClient`：信任**所有**证书，主机名校验恒为 `true`，所有图片加载（Glide）都用它 | Play 的 Security 政策：自定义的「信任全部」`TrustManager` / `HostnameVerifier` 会收到安全告警并拒审；用户在公共 Wi-Fi 上图片可被中间人替换 | 改为 `GlideHttpClient`：系统默认校验，只换 DNS。`api`/`file` 域名证书有效，无需保留 |
| F2 | 所有接口走 `http://`（`Constants.getUrl`），token 和聊天内容明文传输 | 数据安全表「传输中加密」必须如实回答；明文传 token/密码属于高危 | `getUrl` 改 `https://`；`help`/`about` 页同改；伴侣页 baseUrl 同改。真机日志确认请求都是 `https://api.ok123find.top/...`，响应 `errno 0` |
| F3 | `usesCleartextTraffic="true"`（全局放行明文） | 同上 | 改为网络安全配置：**默认禁止明文**，仅放行没有证书的 `www.ok123find.top`（只用于 Referer 头）。有了证书后删掉这一条 |
| F4 | 应用内自更新：`ApkDownloader` 下载 APK 并安装 | Play 设备与网络滥用政策**明确禁止**应用用商店以外的方式更新自己。另外它用 `Uri.fromFile` 安装，targetSdk 24+ 会抛 `FileUriExposedException`，本来就不能用 | 删除；更新提示改为跳转 `market://details?id=<本包名>`，无商店时回退到网页 |
| F5 | `Constants.downloadUrl` 指向**另一个应用**（`...moviefree1`） | 更新入口跳到别的应用 | 删除，改为 `playStoreUrl(packageName)` |
| F6 | 声明了代码里**没人用**的权限：`SYSTEM_ALERT_WINDOW`（悬浮窗）、`PACKAGE_USAGE_STATS`、`ACCESS_COARSE_UPDATES`（根本不是 Android 权限，旧模板遗留）、`DISABLE_KEYGUARD`、`USE_FULL_SCREEN_INTENT`（受限权限）、`CHANGE_WIFI_STATE` | 悬浮窗、使用情况访问、全屏意图都需要在 Play Console 单独声明用途；声明了又说不出用途会被拒 | 在 `:app` 清单用 `tools:node="remove"` 移除。**没改 `:retromusic` 的任何文件** |
| F7 | `requestLegacyExternalStorage="true"` | targetSdk 30+ 已被忽略，留着只会让审核问「为什么要全盘存储」 | 删除 |
| F8 | 3 个 WebView 开着 `setAllowFileAccess(true)` | 页面脚本可读应用沙盒里的 `file://` 资源；这些 WebView 只加载远程页面，用不到 | 改为 `false` |

另：`夜色` 频道默认值由开改为**关**（见 2.1，这只是缓解）。

合并后的权限清单（23 项）：见 `app/build/intermediates/merged_manifest/.../AndroidManifest.xml`。
其中本应用自己声明 9 项，其余来自 `:retromusic`（音乐播放）、广告 SDK。

---

## 2. 阻塞上架 —— 需要你决定

### 2.1 「夜色」频道：成人内容，来源是第三方站点
**证据**：频道页「夜色」标签下，真机列出 `hsex最好的自拍`、`黄色工厂HSCK` 等；「电影」标签下有 `同性片`。
服务端 `recommend/movie.json` 20 条全部 `baseurl = https://xiaoyakankan.com`（第三方），客户端只负责展示，内容由服务端下发。
应用内还有 `SEX`/`SEX_TEMP` 的渠道名、西班牙语的 `Video sexual` / `Imagen sexual` 字符串。

**风险**：Play 对露骨性内容、以及聚合第三方盗版影视内容（版权）都是拒审乃至封号级别。这不是改个开关能解决的。

**已做**：`AREA_SWITCH_DEFAULT = false`，新用户默认看不到「夜色」。评审人员能在设置里打开，所以**这不算合规**。

**选项**（我没有替你选，这是产品决定）：
1. 上架版本彻底去掉该入口，且服务端对上架版本不下发这类频道；
2. 保持现状，不走 Google Play（用其他渠道）；
3. 做成成人内容分级的独立应用（Play 仍有严格限制，不建议）。

### 2.2 首次启动没有隐私政策 / 同意
**证据**：`pm clear` 后首次启动，直接进首页并开始联网（`version.json`、`sessions.json` 等），没有任何同意弹窗。代码里搜不到 `privacy` / `隐私` / `consent`（只有清单里一句注释「隐私权」）。
**要求**：Play 要求有隐私政策链接，且收集个人数据前要有明确披露/同意。
**需要你提供**：隐私政策 URL。我可以接首启同意页，但文案属合规内容，不能代写。

### 2.3 没有「删除账号」入口
**证据**：应用有注册 / 登录，代码里搜不到 `deleteAccount` / `注销`。
**要求**：Play 要求有账号创建的应用，必须提供应用内删除账号的入口，并在 Play Console 填写删除网页。
**需要**：服务端先有删除接口，我再接客户端。

### 2.4 AI 生成内容没有举报入口
**证据**：对话（含伴侣/角色扮演）是 AI 生成内容；代码里搜不到举报入口。
**要求**：Play 的 AI 生成内容政策要求应用内有举报/反馈 AI 输出的途径。
**我可以做**：在消息长按菜单加「举报」，上报到现有反馈接口。接口格式需要你确认。

---

## 3. 需要在 Play Console 声明 / 填写

| 项 | 内容 |
|---|---|
| **精确闹钟 `SCHEDULE_EXACT_ALARM`** | 来自 `:retromusic` 的睡眠定时器（`setExact` 定时停止播放）。没有移除，因为移除后睡眠定时器会坏。Android 13+ 对该权限要求申请表，用途选「闹钟/定时器」。**如要免声明，需要改 retromusic 代码用非精确闹钟，这是你说过不动的范围** |
| **`WRITE_SETTINGS`** | 来自 `:retromusic` 的「设为手机铃声」。同样没有移除（移除后该功能会崩/失效）。属于特殊权限，需说明用途 |
| **`BLUETOOTH_CONNECT`** | `:retromusic` 蓝牙耳机自动播放。运行时权限 |
| **广告 ID `AD_ID` + `ACCESS_ADSERVICES_*`** | AdMob SDK 带入。数据安全表必须声明「收集广告 ID，用于广告」；如面向 13 岁以下需另行处理 |
| **数据安全表** | 至少：账号信息（用户名/密码/邮箱）、聊天内容、语音录音、上传的照片、设备 ID（`Settings.Secure.ANDROID_ID` 拼进 `Devid` 请求头）、广告 ID。均传输到服务端，需如实填「收集 + 共享」 |

---

## 4. 按你的要求没有动

| 代号 | 内容 | 为什么提在这里 |
|---|---|---|
| SEC1 | 密码提示泄露 | 你明确说「不动」。提醒：这是审核可能直接发现的账号安全问题 |
| SEC2 | 服务端后门：请求头带 `testtesttest: hahaha` 时，`HostFilter` 把请求当作测试身份放行，`UserCheckHandlerInterceptor` 还会用硬编码账号 `themass/111111` 取用户。**客户端没有发这个头**（已搜过，无）；问题在服务端，且已部署的服务端是线上可用的 | 你明确说「不动」。这不是 Play 审核能看到的东西（审核看的是 App），但它是真实的鉴权绕过。建议上架前让服务端对生产环境关闭 |

---

## 5. 其他风险（记录，不是本轮能解决的）

| # | 内容 |
|---|---|
| R1 | 渠道名/请求头里有 `SEX`、`VPN`（`VVV`）等字样，服务端按它分流。不是违规，但审核人员看到会产生联想；建议上架版本换成中性渠道名（需要服务端同步） |
| R2 | `:retromusic` 导出了 `MainActivity` / `RestoreActivity` / `ErrorActivity` 等（带 `intent-filter` 导致的 `exported=true`）。其中 `RestoreActivity` 接收外部文件，需确认不会被恶意文件触发 |
| R3 | debug 包 315 MB。Play 的 AAB 基础模块上限 200 MB（含按需模块可更大），上架前要看 release AAB 的实际大小；`libs` 里 OpenCV / ONNX / LiveKit 的原生库是大头 |
| R4 | 原生库 16 KB 页大小对齐（Android 15+ 设备必须）：`imaging/build.gradle` 注释说 OpenCV 4.12.0 已对齐；**LiveKit(WebRTC) 与 GSY(ijkplayer) 的 `.so` 未验证** |
| R5 | AgentChat（约 15 个文件）仍在 `:app` 用旧包名 `com.openapi.ks.myapp.agent`，`ModuleGraphTest` 里按目录豁免。另一个会话正在改，搬完要删豁免 |
| R6 | `HttpUtils.ping()` / `pingVal()` 用 `Runtime.exec("ping ... " + ip)` 拼接字符串。**搜遍全仓没有任何调用方**（死代码），不是可利用的注入点；但留着会被静态扫描标出，建议删除 |
| R7 | `IpUtil.getInNetIp()` 用明文请求 `ip.chinaz.com`、`pv.sohu.com` 查公网 IP，会把用户 IP 发给第三方。**没有调用方**（只有 `getDomain` 被 `BaseWebViewFragment` 用），建议删除这个方法 |

---

## 6. 未验证

- **本轮新构建在真机上没有完整验证**：手机被另一个会话（`Android VPN客户端升级重构`，在用 `com.openapi.ks.free1`）持续占用，`uiautomator` 反复被打断。已验证：构建通过、186 个单测通过、**API 请求都走 https 且返回 `errno 0`**、无 `FATAL EXCEPTION`。**未验证**：关闭明文后频道页的图片/视频是否都能加载、`夜色` 默认隐藏的实际表现、更新弹窗跳转商店。
- **频道封面一直是空白**：放开明文后依旧空白，服务端返回的条目里**根本没有图片字段**，所以不是本轮改动导致，是既有数据问题。
- 视频播放用的第三方 `http://` 流在禁止明文后是否还能播：**未测**。若有播放失败，需要把对应域名加进 `network_security_config.xml`，但要接受它们是明文的事实并在数据安全表披露。
- release 包（R8 后）运行时行为、AAB 体积、原生库对齐：**未验证**。
- 代码里 `Settings.Secure.ANDROID_ID` 的使用范围：只确认了被拼进 `Devid`，未确认是否用于广告。
