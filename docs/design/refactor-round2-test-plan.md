# 第二轮重构：整体测试方案与执行计划

日期：2026-11-11　基线：`weifu-refactor` @ `849f8146`　被测版本：Chat debug（`applicationId com.openapi.ks.chat`）

本文是**方案和计划**；执行结果和证据写在 [refactor-round2-report.md](refactor-round2-report.md)。
规则：任何“通过”都必须有这一轮的原始输出为证；没跑的写“未执行”，不写“应该没问题”。

---

## 1. 范围

第二轮重构改了：模块拆分（20 个模块）、依赖声明、包名（全部改名）、资源归属、启动页组件名（`activity-alias`）、Glide 模块位置、混淆规则、以及若干类的拆分和两个原本失败的测试。

**风险最高的四件事**（测试重点）：
1. **包名全部改了**：按类名打开的页面（服务端下发的 `config` 跳转、`Routes`、`ChatRoute`）如果在字符串里漏改，编译不会报错，运行时才找不到页面。
2. **启动页类名改了**：桌面上的旧快捷方式、`UPDATE_STATUS` 通知点击、快捷方式（retromusic app shortcuts）。
3. **Glide 配置换了位置**：图片加载全部依赖它，包括 OkHttp + 自定义 DNS 的加载器。
4. **模块边界变了**：某个库没被正确声明时，编译能过（因为别处传递），但运行时会 `NoClassDefFoundError`。运行时依赖清单已逐项比对，仍需真机确认。

---

## 2. 测试分层

| 层 | 测什么 | 手段 | 是否可自动 | 执行环境 |
|---|---|---|---|---|
| L1 架构 | 分层、包名、底层不引用业务代码、依赖必须用到 | `ModuleGraphTest`（JUnit） | 是 | JVM |
| L2 单元 | 纯逻辑：播放控制、历史合并、角色解析、图片质量、DAO/迁移 | JUnit + Robolectric | 是 | JVM |
| L3 构建 | 每个模块编译、debug 与 release（R8） | Gradle | 是 | 本机 |
| L4 打包产物 | Manifest 中的类都存在、字符串里的类名都存在、混淆后仍在 | 脚本读 dex / R8 输出 | 是 | 本机 |
| L5 冒烟 | 每个页面能创建、渲染，不崩溃 | Robolectric（`ScreenSmokeTest`）+ 真机 | 部分 | JVM / 设备 |
| L6 UI 功能 | 用户可见的主流程 | adb + uiautomator 截图、手工对照 | 部分 | 真机 `2MH0224B20002068` |
| L7 手工专项 | 音视频、投屏、字幕、拍照等需要硬件和第三方服务 | 手工清单 | 否 | 真机 |

原则：L1–L4 每次提交都跑；L5–L7 在阶段末跑。L6 尽量用脚本（`adb shell uiautomator dump`）留证据，截图存档。

---

## 3. 执行计划

### 阶段 A：自动检查（预计 25 分钟）
| 步骤 | 命令 | 通过标准 |
|---|---|---|
| A1 | `./gradlew clean` | 退出码 0 |
| A2 | `./gradlew :app:assembleChatDebug` | 退出码 0 |
| A3 | 全部模块单元测试（见附件脚本） | 0 失败 |
| A4 | `./gradlew :app:minifyChatReleaseWithR8` | 退出码 0（签名证书不在本机，只验证 R8） |
| A5 | Manifest 类存在性脚本 | 0 缺失（`applicationId` 派生项除外） |
| A6 | 字符串类名 → dex 存在性脚本 | 0 缺失 |
| A7 | 架构测试（含 A3） | 4 条规则通过 |

### 阶段 B：真机安装与冒烟（预计 15 分钟）
| 步骤 | 操作 | 通过标准 |
|---|---|---|
| B1 | `adb install -r app-chat-debug.apk` | Success |
| B2 | 启动：`adb shell am start -n com.openapi.ks.chat/com.openapi.ks.chatfree.ui.main.LaunchActivity`（旧组件名，验证 alias） | 页面出现，无 `FATAL EXCEPTION` |
| B3 | `adb logcat -d` 过滤本包 | 无崩溃、无 `NoClassDefFoundError`、无 `ClassNotFoundException` |
| B4 | 切到后台再回来，点通知 | 回到首页，不崩溃 |

### 阶段 C：UI 主流程（真机，预计 40 分钟）
逐页操作并截图（`adb exec-out screencap -p`），每步查一次 logcat 崩溃。清单见第 4 节。

### 阶段 D：专项（需要条件，能测则测）
- **服务端下发的页面跳转**（风险 1 的核心）：首页配置入口逐个点开，确认跳到 WebView、声音、文字、图片、视频、列表页，而不是“版本过低”提示。
- **图片加载**：所有列表页滚动一遍，确认无灰色占位卡死（Glide 配置生效）。
- **音视频**：频道声音页播放、切歌、倍速、后台播放、通知；视频页播放、全屏、投屏。
- **拍照解题**：拍照、裁剪、质量检测。
- **release 包**：证书不在本机，本轮无法打包；记为未执行。

### 阶段 E：报告
汇总每项的**原始输出**（命令、退出码、关键行、截图文件名），失败的写明现象和复现步骤。

---

## 4. 真机 UI 清单

| # | 页面/动作 | 检查点 | 关联风险 |
|---|---|---|---|
| U1 | 桌面旧图标启动 | 能打开，不出现两个图标 | 启动页 alias |
| U2 | 开屏广告 | 展示后进入首页 | 广告模块依赖 |
| U3 | 首页框架、侧边栏 | 各入口存在、可滚动 | :app home |
| U4 | 对话：打开会话、发一条文字、收到回复 | 消息顺序、流式输出 | :feature-chat |
| U5 | 对话：图片附件、语音输入 | 图片上传、录音转写 | :common/image、:speech |
| U6 | 对话：公式 `$x$` 与 `$$x$$` | 两种都渲染 | katex 清理 |
| U7 | 角色选择 | 列表、切换、服务端角色页 | roles 合并 |
| U8 | 内容频道：视频/图片/文字/声音列表 | 图片加载、下拉刷新、分页 | :feature-content |
| U9 | 声音频道：播放、暂停、下一曲、模式、进度条 | 与控制类拆分前后一致 | SoundPlaybackControls |
| U10 | 声音详情页：翻译/字幕 | 字幕随曲目 | :subtitle |
| U11 | 收藏页 | 收藏、取消、翻译按钮 | :core favorite |
| U12 | 视频页：播放、全屏、倍速、换引擎 | 与重构前一致 | :feature-video（GSY 未动） |
| U13 | 视频页：投屏搜索 | 能搜到设备（有电视时） | :cast |
| U14 | 拍照解题：拍照、裁剪、保存 | 不崩溃 | :imaging |
| U15 | 我的页、设置页 | 各入口、广告位 | :feature-user |
| U16 | 登录、注册、找回密码 | 打开、返回 | 包名 + Routes |
| U17 | 全局搜索、配置入口 | 跳转到对应频道 | 字符串类名 |
| U18 | 分享面板、二维码卡片 | 面板出现 | :uikit |
| U19 | 版本更新检查 | 提示或静默 | :core update |
| U20 | 切网络（飞行模式）后刷新 | 错误提示、重试 | :network |

---

## 5. 判定标准

- **通过**：阶段 A 全部退出码 0；A5/A6 无阻塞性缺失；C 阶段 U1–U20 无崩溃且与重构前行为一致。
- **有条件通过**：只有“需要外部条件”的项未测（投屏无电视、release 无证书），其余全过。
- **不通过**：出现崩溃、`NoClassDefFoundError`、页面跳转到错误位置。

---

## 6. 已知问题（重构前就存在，不在本次判定内，但记录在报告里）

- 声音页空列表提示文案 `"该频道没有sex love"`。
- `SoundItemsMusicFragment` 的 `pause` / 进度条拖动 / `play` 在服务未绑定时可能 NPE。
- `SoundDetailActivity` 与列表页各有一份播放广播接收器。
- 声音页播放控制已拆出（`SoundPlaybackControls` + 25 个单元测试），但页面仍保留视图接线。

---

## 7. 第三轮增补：本次三项功能的验收项

第二轮之后又落了三个功能（权限统一、对话图片编辑、拍照解题端到端）。它们在阶段 C/D 之外有自己的验收点：

### 7.1 权限统一（`PermissionGateway`）

| # | 场景 | 期望 |
|---|---|---|
| P1 | 首次点相机 | 先解释框，再系统授权框；授权后直接进相机 |
| P2 | 拒绝一次后再点 | 还能再弹（不是永久拒绝） |
| P3 | 选「不再询问」后再点 | 提示里去设置；点「去设置」跳到本应用设置页 |
| P4 | 从设置页开权限返回 | 功能可用（`LocalVideoActivity` 会刷新 `granted`；对话页下次点击即可用） |
| P5 | 连点麦克风两次 | 只出现一个解释框（同一 Activity 的请求串行化） |
| P6 | 本地视频页首次进 | 请求读视频权限；Android 13+ 是 `READ_MEDIA_VIDEO`，12 及以下 `READ_EXTERNAL_STORAGE` |
| P7 | 拍照解题首次进相机 | 不再出现「普通拒绝被当成永久拒绝」的旧行为 |

### 7.2 对话图片编辑（`PhotoEditor`）

| # | 场景 | 期望 |
|---|---|---|
| E1 | 选一张图 | 先全屏进编辑器；取消则不进草稿；完成则草稿里是编辑后的图 |
| E2 | 涂鸦一笔后撤销 | 笔画消失；重做逐步恢复（每次一步） |
| E3 | 旋转 90° 两次 | 预览与最终导出一致 |
| E4 | 文字标注 | 位置跟随手指，导出位置一致 |
| E5 | 马赛克 | 只影响框选区域，不溢出 |
| E6 | 4000×3000 大图 | 不 OOM；导出长边 ≤ 2048 |
| E7 | 编辑后发送 | 服务端收到的就是编辑后的图 |
| E8 | 多选图片 | 只有第一张进编辑器，其余直接进草稿 |

### 7.3 拍照解题（testserverV + 豆包）

服务端地址走 `Constants.getUrl()`（`BASE_IP` + `/vvv/api`），**不需要额外配置**；
模型密钥只在服务端 `secrets.properties` 的 `photosolve.doubao.apiKey`，客户端不含任何密钥。

| # | 场景 | 期望 |
|---|---|---|
| S1 | 清晰正对题目 | 检测到四角 → 微调页 → 矫正 → 识别中 → 结构化结果（题目/步骤/答案） |
| S2 | 斜拍（梯形） | 四角检测成功，矫正后文字不歪、没有大片背景 |
| S3 | 桌面/风景照 | 端上提示「未检测到题目边缘」，停在取景页，可重拍或手动框选 |
| S4 | 清晰度差 | 弹清晰度提示，可选择仍然提交 |
| S5 | 服务端返回 40002 | **回到取景页**并提示「不是题目」（依赖本次修正的 errno 透传，旧实现在这里会显示「服务不可用」） |
| S6 | 服务端未配置模型（50002） | 提示「解题服务尚未配置好」，不重试风暴 |
| S7 | 断网 | 提示网络异常，可重试，不崩溃 |
| S8 | 服务端 `health.json` | 返回 `configured:true` 且 provider 与实际部署一致 |

> S5 是这次合并里修掉的真实缺陷：`awaitUpload` 吞掉 errno 只弹通用 toast，
> 现在 `NetError.BusinessError` 带 errno、`awaitUploadOrThrow` 不吞错误，
> `NetworkPhotoSolveRepository` 把它映射成 `PhotoSolveError`。

### 7.4 本轮已执行（JVM，可在本机复现）

| 命令 | 结果 |
|---|---|
| `./gradlew --offline :app:assembleChatDebug` | 通过，产出 `app-chat-debug.apk` |
| `./gradlew --offline testDebugUnitTest testChatDebugUnitTest` | **151 个测试全部通过，0 失败**（app 10 / core 16 / feature-chat 54 / feature-companion 15 / feature-content 33 / imaging 14 / mediaview 9） |
| 架构规则（`ModuleGraphTest`） | 4 条全部通过（分层、包名、底层不引用业务代码、依赖必须用到） |

**未执行**：阶段 B/C（真机）、7.1–7.3 的真机项、release 打包（签名证书不在本机）、
端到端调豆包（`secrets.properties` 未填 `photosolve.doubao.apiKey`）。

---

## 8. 第四轮：本轮实际执行的检查（2026-11-12）

| 项 | 命令 / 方式 | 结果 |
|---|---|---|
| 构建 | `./gradlew --offline :app:assembleChatDebug` | 通过；`app-chat-debug.apk` 产出 |
| 全部单元测试 | `./gradlew testDebugUnitTest testChatDebugUnitTest` | 166 个通过（新增 `QuadNormTest` 10 个、`PermissionOutcomeTest` 5 个） |
| 架构规则 | `ModuleGraphTest` | 4 条通过 |
| 清单合并 | 读 `merged_manifest/chatDebug/AndroidManifest.xml` | 运行时权限只剩 26 项；注释掉的旧权限全部移除 |
| 设备 | `adb devices` | **无设备连接**，阶段 B/C 未执行 |

### 8.1 本轮修的两个真实缺陷

1. **下载进度通知被静默丢弃**（Android 13+）：`ApkDownloader` 直接 `notify()`，没有申请 `POST_NOTIFICATIONS`。
   现在 `UpdateDialogs` 在开始下载前通过 `PermissionRequester` 申请；用户拒绝也照常下载，只是没有进度通知。
   老式 `Activity`（拿不到 `ActivityResultRegistry`）直接下载，不抛异常。
2. **可选表单字段被序列化成字面量 `"null"`**：`BeanUtil.transBean2Map` 用 `String.valueOf(val)`，
   服务端把 `subject`/`grade` 写进提示词后变成「学生提示的学科：null」。现在跳过 null 字段。

### 8.2 清单清理的具体改动

| 动作 | 权限 |
|---|---|
| 保留 | `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、`ACCESS_COARSE_UPDATES`、`SYSTEM_ALERT_WINDOW`、`CAMERA`、`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、`WAKE_LOCK`、`POST_NOTIFICATIONS`（新增）、`PACKAGE_USAGE_STATS` |
| 加 `maxSdkVersion` | `READ_EXTERNAL_STORAGE`（≤32）；`WRITE_EXTERNAL_STORAGE` 只在 `:retromusic`（≤29） |
| 删除（注释行 + 重复项） | `GET_ACCOUNTS`、`READ_PHONE_STATE`、`CALL_PHONE`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`GET_TASKS`、`REAL_GET_TASKS`、`USE_CREDENTIALS`、`MANAGE_ACCOUNTS`、`SET_DEBUG_APP`、`INSTALL_SHORTCUT`、`READ_HISTORY_BOOKMARKS`、`EXPAND_STATUS_BAR`、`MOUNT_UNMOUNT_FILESYSTEMS`、`READ_LOGS`、`READ_CALENDAR`、`WRITE_CALENDAR`、`MANAGE_EXTERNAL_STORAGE`（注释）、重复的 `ACCESS_NETWORK_STATE` |

> `GET_TASKS` / `EXPAND_STATUS_BAR` 原本是生效的（未注释），但代码里已无调用方：
> `GET_TASKS` 自 API 21 起对第三方应用无效，`EXPAND_STATUS_BAR` 需要系统签名。一并删除。

---

## 9. 第五轮：真机执行与方案 review（2026-11-12）

执行结果与证据见 [refactor-round2-report.md](refactor-round2-report.md)。对本方案的修订：

1. **阶段 A 必须有「真实 Android 行为」层**。JVM 单测 180 个全绿时，真机上「编辑后发送」「对话发图」两条主流程都是坏的。
   新增 Robolectric `NATIVE` 图形模式（拒绝不可变位图）和依赖装配测试。
2. **所有命令的退出码单独取**：`cmd > log 2>&1; echo $?`，不要 `cmd | tail`（取到的是 `tail` 的退出码）。
3. **UI 自动化用控件文字，不用坐标**：`scripts/devicetest/ui.sh` 的 `wait_text` / `tap_text`。
4. **记录设备 Android 版本**：Android 13 以下测不到 `READ_MEDIA_*` / `POST_NOTIFICATIONS`，结论不能外推。
5. **服务端自检要带 UA 签名**：`scripts/devicetest/photosolve_probe.py`；裸 `curl` 得到 `errno 10000` 不代表服务端故障。
6. **release 验证要么装包运行，要么明确写「未验证」**：只跑 R8 不等于 release 可用。
