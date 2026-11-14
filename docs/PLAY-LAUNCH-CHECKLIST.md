# ShieldWing 上架材料

> 包名 `com.openapi.ks.free1` · 当前版本 2.0.0.5 (versionCode 20004) · 更新于 2026-11-14

---

## 一、商店文案

### 应用名称（30 字符内）

```
ShieldWing — Fast & Secure VPN
```

### 简短说明（80 字符内）

```
One-tap VPN with a 3D server map. Encrypted tunnel, multiple protocols, no setup.
```

### 完整说明（4000 字符内）

```
ShieldWing is a lightweight VPN client that protects your connection with an
encrypted tunnel between your device and our servers.

WHY SHIELDWING

• One tap to connect — pick a server on the 3D globe or just hit Connect
• Multiple protocols — IKEv2/IPsec, WireGuard and VLESS-Reality, so you keep
  working even when one protocol is blocked on your network
• See what is happening — a live session timer, traffic counter and a daily
  usage chart, all stored on your device
• Quick Settings tile — connect or disconnect from the notification shade
  without opening the app
• Split tunneling — choose which apps go through the tunnel

HOW IT WORKS

ShieldWing uses Android's VpnService API to create an encrypted tunnel between
your device and our servers. Traffic inside the tunnel is encrypted end to end.
You must accept the system VPN permission prompt before the first connection.

PRIVACY

We do not log the websites you visit or the contents of your traffic. We collect
only what is needed to run the service: a device identifier, your IP address,
connection duration and data volume, and crash diagnostics. See our privacy
policy for the full list and how to delete your account:
https://www.ok123find.top/privacy.html

FREE AND PREMIUM

The free tier includes ads and a daily data allowance. Upgrading removes ads
and raises the limit.

Note: VPN availability and speed depend on your network and location. Some
services may still detect VPN usage.
```

> **审核要点**：第一段必须出现 VpnService，第三段直接说明"用了 Android 的 VpnService API"——这是 Play 拒审的常见原因（"Use of VpnService is not documented in the app description"）。上面这段已满足。

> **不要写**：`100% anonymous`、`untraceable`、`no logs at all`、`unlimited free`。这些词受 Deceptive Behavior 条款约束，而我们确实收集设备 ID、IP 和连接元数据。

---

## 二、截图

规格：2–8 张，PNG 或 JPEG，短边 ≥ 320px，长边 ≤ 3840px。竖屏 1080×1920 或 1242×2208。

| # | 画面 | 叠加文案（英文） | 中文（如做中文商店页） |
|---|---|---|---|
| 1 | 首页地球，已连接（绿线、Conn OK、计时与流量） | One tap to protect your connection | 一键加密你的连接 |
| 2 | 节点列表：国旗 + 延迟 + 收藏星标 | 140+ servers, real-time latency | 140+ 节点，实时延迟 |
| 3 | 连接中，地球上的动态连线 | See your traffic route in 3D | 3D 地球看清流量走向 |
| 4 | 用量统计页（柱状图 + 今日/本周） | Know how long you're protected | 用了多久，一目了然 |
| 5 | 网络体检结果 | Check speed, DNS leaks and exit IP | 测速、DNS 泄露、出口 IP |
| 6 | 分应用代理列表 | Choose which apps use the VPN | 选择哪些应用走 VPN |
| 7 | 通知栏磁贴（蓝色激活态） | Connect without opening the app | 下拉通知栏一键连接 |
| 8 | 设置页：协议选择、Kill Switch | IKEv2, WireGuard, VLESS — your choice | 三种协议自由切换 |

**拍摄方式**：真机运行后 `adb exec-out screencap -p > shot.png`，再用任意图片工具叠文字。不要用启动广告页、不要出现真实的服务器 IP、不要把第三方品牌图标留在画面里。

---

## 三、Play Console 必填项

| 项 | 填什么 |
|---|---|
| **隐私政策 URL** | `https://www.ok123find.top/privacy.html`（需先发布 `docs/PRIVACY-POLICY.md`，改为 HTTPS 静态页） |
| **账号删除 URL** | `https://www.ok123find.top/account-delete.html`（需新建，见第四节） |
| **应用分类** | Tools |
| **内容分级** | 问卷里"是否含用户生成内容"选否；"是否含广告"选是。预期分级：3+ / Everyone |
| **目标受众** | 18 岁及以上（含 VPN 功能的工具类应用，不要选含儿童） |
| **广告** | 是 |
| **应用访问权限说明** | 提供测试账号：用户名与密码，并说明"登录后才能看到节点列表与连接按钮" |
| **Trader 状态**（EU） | 有广告/订阅收入 → 选 **Trader**，需公开主体名称、地址、联系方式（2027-03-01 起强制公开） |

### Data Safety 表单（逐项照抄）

**收集的数据**

| Play 类别 | 对应本应用 | 是否共享 | 是否必需 | 用途 |
|---|---|---|---|---|
| Device or other IDs | devId、广告 ID | 是（广告 ID → AdMob） | 是 | App functionality, Advertising |
| Approximate location | 由 IP 推断的国家/城市 | 否 | 是 | App functionality |
| App interactions | 连接次数、时长、功能使用 | 否 | 是 | Analytics, App functionality |
| Diagnostics | 崩溃日志 | 否 | 是 | App functionality |
| User IDs | 注册用户名 | 否 | 否（仅登录用户） | App functionality |
| Email address | 注册邮箱 | 否 | 否 | App functionality |

**必答项**

- 传输过程是否加密：**是**
- 是否提供数据删除请求机制：**是**（应用内 + 网页 URL）
- 是否有独立的安全审查：**否**（如做 MASA L2 可改是，以获得 Verified 徽章）

---

## 四、VpnService 声明表（Play Console → App content → VPN）

三个必须同时满足，缺一项会拒审：

1. **表单本身**：VPN 是否为核心功能 → 选"是"；数据收集类别按第三节勾选
2. **商店描述**写明使用 VpnService —— 见第一节完整说明
3. **两段视频（各 ≤90 秒）**
   - 视频 A：打开 App → 点击连接 → 系统 VPN 授权弹窗 → 连接成功 → 状态栏出现钥匙图标
   - 视频 B：首次启动时的数据说明弹窗 → 点"同意" → 继续；再演示点"拒绝"会发生什么，以及如何重新触发（设置 → 隐私）

> 视频 B 目前还缺一段"prominent disclosure"弹窗，见第五节待办。

---

## 五、仍需人工完成的事

按阻塞程度排序。

### 会直接导致上传失败

1. **注册组织开发者账号 + 申请 D-U-N-S 号**
   Play 把"使用 VpnService 的应用"列入强制组织账号类别，个人账号提交会被拒。D-U-N-S 申请通常几天到两周。

2. **生成并备份上传密钥**
   ```bash
   bash tools/make_keystore.sh
   ```
   生成后立刻把 `app/release.jks` 和 `keystore.properties` 备份到密码管理器。丢失即无法再更新本应用。

3. **发布隐私政策与删除请求页到 HTTPS**
   `docs/PRIVACY-POLICY.md` 需转成静态页发布；`account-delete.html` 需要新建一个不依赖登录的表单页（邮箱 + 说明 + 提交按钮，后端落库或转发邮件）。

4. **重新部署后端**
   本次改了 `UserServiceImpl.del()` 为真正删除，并新增 `user_node` 的按用户级联删除。需要执行编译产物部署。

### 会导致审核打回

5. **实现 prominent disclosure（首启数据说明弹窗）**
   在做任何数据收集之前，用一个独立弹窗说明"我们会收集什么、为什么"，按钮必须是"同意 / 不同意"两个选项，不能只有"知道了"，不能预勾选。这是视频 B 的拍摄前提。

6. **调整广告触发点**
   - `AdsPopStrategy.clickAdsShowBtn` 会在用户点击按钮时随机弹插屏 —— 如果这个按钮包含"连接"，违反 AdMob 的"每两次操作最多一个插屏"和"不打断核心功能"。
   - 建议：连接按钮路径完全不弹插屏；插屏只在浏览类页面切换之间出现。
   - 启动页用的是 AppOpenAd 格式，这个是合规的，保留。

7. **检查 `AES2` 的 ECB 模式**（安全加固，非 Play 硬性）
   服务端与客户端必须同时改，否则协议不兼容。建议排期做，不阻塞上架。

### 建议但非阻塞

8. Android 16 大屏/横屏适配（`screenOrientation="portrait"` 在 ≥600dp 屏幕失效）
9. 把 `cleartextTrafficPermitted` 白名单清空（当前 6 个域名还没有 HTTPS）
10. 给 `delete_account` 之外的其他设置项补中文本地化
11. 考虑 MASA Level 2 安全评估以获取 Play "Verified VPN" 徽章（要求 ≥10000 安装 + ≥250 评价，属后期目标）

---

## 六、本次已完成的技术项

| 项 | 处理 |
|---|---|
| 16 KB 页对齐 | 用 NDK r28 重编 `libbox.aar`（sing-box 1.13.5），并把 `graphics-path` 升到 1.1.0。13 个 `.so` 全部 `align=0x4000` |
| NDK 版本 | `27.0.12077973` → `28.2.13676358` |
| 签名配置 | 新增 `.gitignore` 规则（`*.jks`、`*.keystore`、`keystore.properties`）+ `tools/make_keystore.sh` |
| 明文流量 | 从"全部允许"改为 6 个域名白名单 |
| 账号删除 | 客户端新增 `DeleteAccountActivity` + 设置页入口；服务端 `del()` 从"改密码"改为真正删除，并级联删用户节点 |
| UMP 同意 | 新增 `AdConsent`，在 AdMob 初始化完成后按官方顺序拉取并展示同意表单 |
| 自适应图标 | 新增 `mipmap-anydpi-v26/ic_launcher.xml` + 前景图，避免被启动器裁切 |
| 精确闹钟 | 已有 `canScheduleExactAlarms()` 运行时判断（核对确认） |
