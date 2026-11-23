# camera lab 真机验收清单 — chatDebug @ weifu tip（2026-11-23 晚）

**设备**：vivo V2279A `10CE7P1G56004B7`（1080×2388，Android 13）
**装机**：协调会话独占装机（worker 一律不 `adb install`——本会话已遵守）。
**验收对象（现场核实）**：机上实际为 **1.1.0.71 / lastUpdateTime 2026-11-23 22:39:02**，非协调口径的 `fddbf38a`(.66)。经祖先链核实 `fddbf38a ⊂ 7fb3c7c7(.71 bump) ⊂ origin tip`，故 .71 **包含** fddbf38a 的全部 camera-fx 代码（lab-b3 HUD / Lottie progress / HandLandmarker），判据依然适用；版本差异已回报协调。
**执行**：cam-lab-adb 会话；证据目录 `~/Library/Application Support/Claude-3p/side-session-notes/local_0c440dde-5831-45ce-9a97-c5d3f275b91b/camera-fx-evidence/`。

## 判据来源

`feature-camera-fx` README + `CameraFxGrantedContent.kt`（HUD 三行：`fx … fps · 脸 N · 姿态 N · 手 N [· 手势 …] · 倾角 · WxH` / 隐私行 / `lab-b3 · 贴纸 OK|加载… · 资产 · 线框=开 贴=开`）+ `CameraFxScreen.kt`（标题栏「摄像头定位 Demo」+ ← + 「全开」；chip 线框/贴纸/爱心·光弧；无脸与无手 fail-soft 提示文案）。

## 结果表

| # | 项 | 判据 | 结果 | 证据 |
|---|----|------|------|------|
| 1 | 冷启动 | force-stop → LAUNCHER 拉起 → LaunchActivity→HomeActivity 正常渲染、无 crash/fatal | **PASS**（.71 轮 22:4x 实测：拉起正常、logcat 无本包 FATAL） | `v71-coldstart-*.png`；`tip-coldstart-*.png` |
| 2 | 抽屉入口 | 「实验室 → 摄像头定位 Demo」可见可点 | **PASS**（20:04 轮实测；.71 轮抽屉可见但随后设备被占用，未再点入） | `nav-lab2-161827.png`、`v71-drawer-*.png` |
| 3 | 权限流 | 明示同意弹窗文案 → 同意并开始 → 系统相机权限 → 预览出图；撤回→重授 | **PASS**（20:04 轮：弹窗文案 + 权限已授后管线跑起；.71 轮 `pm revoke`/`pm grant` 闭环完成，结束时 granted=true） | `consent-or-running-161915.png` |
| 4 | 管线 HUD | `fx N fps · … · 602x1280` 持续刷新 | **PASS**：9.3–16.7 fps | `lottie-head-a11217fa-no-face-v2279a.png` |
| 5 | lab-b3 标记 | HUD 第三行灰字 `lab-b3 · 贴纸 OK` | **待**（装机非阻塞——已证 .71 含 fddbf38a；阻塞=设备被陪伴通话占用，见下） | — |
| 6 | fail-soft 无脸 | 脸 0 时：无叠层 + 提示「未识别人脸：正对镜头…」 | **部分 PASS**：无叠层已证（截图）；提示文案行待 lab 页复验（同上阻塞） | 同上 |
| 7 | 脸=1 线框 | 黄折线/白框/绿眼线/品红头顶点；倾角 HUD | **待**（镜头无人体；需用户立机正对前摄 ~30s） | 历史旁证 `face-align-rotfix-162906.png`（旧构建） |
| 8 | 手/姿态 HUD | 手入画 → 橙线 + `手 N · 手势 …`；pose 青线 | **待**（同上） | — |
| 9 | 贴纸 Lottie | 贴纸·开 → `贴纸 OK` + 头顶爱心随倾角/镜像跟脸；遮脸→贴纸消失 | **待**（依赖 7） | 无脸不画已证 |
| 10 | chip/全开 | 线框·开/贴纸·开/爱心↔光弧 切换即时生效；「全开」恢复 | **待本轮** | — |
| 11 | 退出清理 | 返回/finish 后 camera 释放、再进正常 | **PASS**（20:04 轮：退出再进两次管线正常） | dump 序列 |

## 环境事实（防误判）

- 20:04 轮 HUD 无 `lab-b3` 行属**预期**——当时机上是我自装的 a11217fa 构建（.66 同号），不含 `da5fd7bc`。
- 本会话唯一一次 `pm install -r -d -t`（推 1.1.0.69 md5 `4470aedd…`）**挂死在 vivo PackageInterceptActivity 未生效**，已 TaskStop；此后零安装动作。
- 现场发现 vivo 安装页显示「1.1.0.71 · 继续安装」在等人工确认——**非本会话发起**，未代点；已回报协调。
- 22:39:02 机上进到 **1.1.0.71**（同样非本会话所为）；本会话此后只在这台已装包上验收。
- **轮末阻塞**：22:4x 截图 `nav-current-v71.png` 显示设备正处于**活跃陪伴语音通话**（林澈 · 计时中）——V2279A 被 LiveKit/elastic 线占用（其 CLAIM 未 RELEASED），本会话立即停手，未再输入任何点按。
- 剩余「待」项解锁只需两件事：① 设备交回（通话结束/RELEASED）；② 用户把机立起正对前摄 ~30s（脸=1 / 手 / 贴纸跟随）。**不缺构建**——.71 已含 fddbf38a 全部 camera-fx 代码。
- 华为 ADY-AL00 整晚未上线；全部真机项都在 V2279A。

## 已知坑（给后人）

- vivo：`pm install` 必弹 PackageInterceptActivity（「外部来源应用…继续安装」），**不可自动化代点**；装机=物理动作。
- vivo 聊天页长按消息会弹操作面板（复制/朗读/删除），滑动路过会误触——抽屉内滚动用列表区坐标、避消息区。
- `CameraFxActivity` exported=false，`am start` 必 SecurityException；只能走抽屉。
- uiautomator dump 偶发空（首帧未 settle），sleep 后重 dump。
