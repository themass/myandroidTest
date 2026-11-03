# 分应用代理设计（SPLIT-TUNNELING）

## 目标

让用户选择哪些应用走 VPN、哪些直连。默认行为保持不变：所有应用都走 VPN。

## 决策

| 项 | 决定 |
|---|---|
| 范围 | 第一期只按应用（包名）分流。按域名、IP 分流另立一期。 |
| 默认模式 | `EXCLUDE`（除名单外全部走 VPN）。 |
| 默认名单 | 空。空名单等同于全部走 VPN，与旧版本行为一致。 |
| 应用列表 | 所有用户安装的应用，以及已更新的系统应用。不再要求有桌面图标。排除本应用自身。 |
| 生效时机 | 修改后，下一次连接生效。当前已连接时，页面提示"重新连接后生效"。 |
| 协议覆盖 | IKEv2、WireGuard、Xray（VLESS+Reality）三条链路都按同一份策略建立隧道。 |

## 模式语义

- **排除（EXCLUDE）**：名单内的应用直连，其余全部走 VPN。
- **仅选中（INCLUDE）**：只有名单内的应用走 VPN，其余直连。

Android 的 `VpnService.Builder` 里 include 与 exclude 互斥，所以同一时间只能用一种模式。

## 界面

- 设置页"分应用代理"一行，点开进入独立页面（Compose）。
- 页面顶部：模式切换（分段按钮：排除 / 仅选中）。
- 搜索框：按应用名或包名过滤。
- 列表：每行显示应用图标、名称、包名和勾选框。图标在后台线程加载。
- 页面底部提示："修改后，下一次连接生效。"
- 已选数量显示在标题或列表顶部。

## 数据

- 存储：SharedPreferences，`mode` 与 `packages`（逗号分隔）。沿用现有 `SplitTunnelingPreferences`。
- 没有新增数据库表，不需要升级 greenDAO 版本。

## 生效路径

1. 页面保存到 `SplitTunnelingPreferences`。
2. 连接时 `VpnRadFragment` 读取 `SplitTunnelingPolicy`，写入 `TunnelRequest`。
3. 三条引擎各自应用：
   - IKEv2：`CharonVpnService.BuilderAdapter.applySplitTunneling`
   - Xray：`XrayVpnService.applySplitTunneling`
   - WireGuard：`WireGuardTunnelEngine` 的 `includeApplication` / `excludeApplication`

## 不做的事（第一期）

- 按域名或 IP 分流。
- 预设名单（如"国内应用直连"）：需要维护包名表，等有了可靠的清单再做。
- 按连接自动切换模式。

## 验证

- 编译通过。
- 真机：
  1. 默认状态下，所有应用的流量都经过 VPN（和旧版本一致）。
  2. 排除模式：选中一个应用，连接后它直连，其他应用仍走 VPN。
  3. 仅选中模式：只有选中的应用走 VPN。
  4. 修改名单后不重连，行为不变；重连后生效。
  5. 三种协议各测一次。
