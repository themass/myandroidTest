# 多协议 VPN 架构与协议选型

> 落地日期：2026-11-01
> 本文记录多协议能力的架构决策、已实现内容、以及自动选优策略。未完成的部分明确标注。

---

## 1. 核心架构约束

**Android 同一时刻只允许一个 `VpnService` 持有 TUN 设备。**（[官方文档](https://developer.android.com/reference/android/net/VpnService)：*"There can be only one VPN connection running at the same time"*）

这一条决定了整个多协议设计：

- 多个协议**不能并行**运行，切换必须先停旧引擎、等其释放 TUN，再起新引擎
- 因此多协议的价值不在"同时跑"，而在"按场景自动选一个最合适的"
- 引擎接口不处理互斥，互斥统一在上层 `VpnSessionRepository` 保证

## 2. 分层设计

```
┌─────────────────────────────────────────────────────────┐
│ presentation                                            │
│   VpnRadFragment ──┐                                    │
│   SettingActivity ─┴─> VpnSessionRepository (interface) │
├─────────────────────────────────────────────────────────┤
│ domain                                                  │
│   model/       TunnelProtocol, VpnSessionState          │
│   repository/  TunnelEngine, TunnelRequest              │
│   usecase/     ProtocolSelector                         │
├─────────────────────────────────────────────────────────┤
│ data                                                    │
│   vpn/         CharonVpnSessionRepository (调度中心)     │
│                TunnelEngineRegistry                     │
│   vpn/engine/  Ikev2TunnelEngine                        │
│                WireGuardTunnelEngine                    │
│   local/       ProtocolPreferences                      │
├─────────────────────────────────────────────────────────┤
│ platform                                                │
│   strongSwan (libcharon + libipsec) ──> IKEv2           │
│   wg-go (WireGuard 官方)             ──> WireGuard      │
└─────────────────────────────────────────────────────────┘
```

**扩展点只有一个**：实现 `TunnelEngine` 接口，在 `TunnelEngineRegistry.register()` 注册。UI 列表、自动选优、错误映射全部自动生效，调用方零改动。

## 3. 已实现的协议

### 3.1 IKEv2 / IPsec（`Ikev2TunnelEngine`）

- 底层：strongSwan 6.0.3dr1 + libipsec 用户态转发
- 定位：**兼容性与回退通道**
- 优势：企业/运营商环境兼容性最好，有状态、可做 MOBIKE
- 劣势：libipsec 用户态转发无 GRO/GSO，吞吐偏低；握手与定时器带来额外耗电
- 状态来源：`VpnStateService`（通过 `StateBinder` 注入，引擎不自己管 Service 生命周期）

### 3.2 WireGuard（`WireGuardTunnelEngine`）

> ⚠️ **必须知道的坑**：`com.wireguard.android:tunnel` 的 AAR **自带一个 `GoBackend$VpnService` 组件**，会随 manifest 合并进你的应用。如果不管，应用里就会出现**两个** VpnService 争夺同一个 TUN 槽位。
> 本项目已在 `AndroidManifest.xml` 里用 `tools:node="remove"` 移除该组件（构建后合并 manifest 已验证只剩 `CharonVpnService` 一个）。`GoBackend` 的 API 在进程内直接驱动 wg-go，不需要注册 Service。


- 底层：`com.wireguard.android:tunnel:1.0.20260102`（官方 wg-go）
- 许可：**Apache-2.0**，与项目 GPL-2.0 兼容
- 定位：**快速通道**
- 优势：无状态设计（无常驻线程/定时器），待机耗电更低；Noise IK 握手 1-RTT；用户态转发比 libipsec 成熟，移动端吞吐通常高 2–4 倍
- 劣势：固定头部特征明显，UDP 易被识别与封锁
- 原生库：`libwg-go.so` 3.3MB（arm64-v8a），已随 APK 打包

**服务端要求**：节点需下发 `peerPublicKey`、`clientAddress`、`dns`。`HostVo` 已扩展这些字段，服务端不下发时自动回退 IKEv2。

## 4. 自动选优策略（`ProtocolSelector`）

按优先级打分，分数越低越优先：

| 协议 | 基础分 |
|---|---|
| WireGuard | 0 |
| IKEv2 | 1 |
| Sing-Box（未实现） | 2 |

规则：

1. **用户显式选择优先**——若选中的协议当前可用且未被判定为"频繁失败"，直接采用
2. **失败惩罚**——记录连接失败次数，达到 2 次即降权到末位
3. **失败记录 10 分钟后过期**——避免一次网络抖动让协议被永久拉黑
4. **成功清零**——一旦连上就移除该协议的失败记录

选择结果由 `CharonVpnSessionRepository.connectAuto()` 消费，返回实际选中的协议。

## 5. UI

设置页新增「Protocol」项：

- 显示当前偏好（`Auto` 或具体协议名）
- 点击弹出单选列表：Auto + 当前设备**真正可用**的协议（`availableProtocols()` 已过滤不可用项）
- 偏好持久化在 `ProtocolPreferences`，存的是协议 id 而非枚举序号（枚举顺序变化不会让历史值失效）

## 6. 关键实现细节

### 6.1 状态归一

不同协议的状态语义不同，但对 UI 统一暴露 `VpnSessionState`：

- IKEv2 的 `State`/`ErrorState`/`ImcState` 直接映射
- WireGuard 的 `Tunnel.State.UP/DOWN` 映射成 `CONNECTED`/`DISABLED`
- 引擎错误经 `TunnelEngine.ErrorCode` 归一后再映射回 `VpnStateService.ErrorState`，UI 无需感知协议差异

### 6.2 双状态源隔离

IKEv2 有 `VpnStateService` 和引擎回调两个状态源。`publishFromService()` 里有守卫：

```java
if (engine != null && engine.protocol() != TunnelProtocol.IKEV2) {
    return;  // 非 IKEv2 激活时以引擎回调为准
}
```

否则 WireGuard 连接时会被 strongSwan 的 `DISABLED` 状态覆盖。

### 6.3 协议切换

`connect(protocol, request)` 里若检测到当前有另一个协议的连接，先 `disconnect()` 旧引擎再起新的。这是 TUN 独占约束的直接体现。

## 7. 未完成 / 已知限制

| 项 | 状态 |
|---|---|
| **真机验证** | ❌ 未做。`adb devices` 为空且无 AVD，协议切换、WireGuard 握手均需真机回归 |
| **WireGuard 与 IKEv2 的 TUN 竞争** | ⚠️ 已移除 AAR 自带 Service，但**两种协议仍是"模式切换"关系**，不能同时生效（受 Android 单 VpnService 限制） |
| **WireGuard 服务端** | ❌ 未部署。需要节点下发 `peerPublicKey`/`clientAddress`，否则只会走 IKEv2 |
| **WireGuard 密钥管理** | ✅ 已实现。`WireGuardKeyStore` 生成并持久化长期密钥对，连接时注入私钥；**但公钥向服务端注册的接口尚未接**——这是上线前必须补的一环 |
| **分应用分流** | ✅ 已实现。两个引擎都支持 include/exclude，设置页可勾选应用 |
| **sing-box 引擎** | ❌ 仅占位。`TunnelProtocol.SING_BOX.isImplemented() == false`，不会出现在 UI 列表 |
| **APK 体积** | ⚠️ 从 55MB 增至 59.6MB（+3.3MB wg-go）。若要收窄，可考虑 AAB 按 ABI 拆分 |

### 7.1 分应用分流实现细节

**domain 层**：`SplitTunnelingPolicy`（模式 + 包名集合），协议无关。

- `EXCLUDE`：除名单外全部走代理（默认，适合"放过银行/内网"）
- `INCLUDE`：仅名单内走代理（适合"只让特定应用走 VPN"）

**两个引擎的落地方式**：

| 引擎 | API |
|---|---|
| WireGuard | `Interface.Builder.includeApplication/excludeApplication` |
| IKEv2 | `VpnService.Builder.addAllowedApplication/addDisallowedApplication` |

**IKEv2 侧的两个实现要点**：

1. 必须在 `createBuilder()` 里设置，且早于 `addAddress/addRoute`——顺序错了系统会忽略
2. 逐个包 `try/catch` `NameNotFoundException`，已卸载的应用跳过而不是让整次连接失败

**持久化**：`SplitTunnelingPreferences` 存包名列表 + 模式字符串；枚举已安装应用时排除本应用自身与无 launcher 入口的组件。

**UI**：设置页「Split tunneling」→ 先选模式 → 多选应用列表。

## 8. 后续路线（按投入产出排序）

1. **WireGuard 公钥注册** —— 密钥对已本地生成，但还没把公钥交给服务端。缺这一步 WireGuard 连不上真实节点
2. **真机回归** —— 协议切换、失败回退、看门狗、分应用分流四处必须验证
3. **sing-box 引擎** —— 引入 `libbox`，实现真正的规则引擎分流（按域名/GeoIP）。这会让 IKEv2 从"主通道"降为"回退通道"
4. **按域名/GeoIP 分流** —— 当前的"智能代理"只做到应用粒度；IP 层分流需要在 TUN 之后插规则引擎，属于 sing-box 的范畴

---

## 9. 技术调研结论（2026-11-01）

对现代隧道协议的横向调研，作为后续路线依据。

### 9.1 许可前提

strongSwan 源码头部是 **GPL-2.0-or-later**（已核验：`either version 2 of the License, or (at your option) any later version`），因此本项目可按 **GPLv3** 处理。这决定了可选范围：

| 组件 | 许可 | 与 GPLv3 兼容 |
|---|---|---|
| strongSwan | GPL-2.0-or-later | ✅ |
| wireguard-android | Apache-2.0 | ✅ |
| sing-box / libbox | GPL-3.0-or-later | ✅ |
| Xray-core | MPL-2.0 | ✅ |
| Hysteria2 | MIT | ✅ |
| TUIC | GPL-3.0 | ✅ |

> 若坚持 GPL-2.0-**only**，则 sing-box 与 TUIC 出局，WireGuard 本身也有争议。

### 9.2 协议对比

| 协议 | 吞吐 | 耗电 | 握手 | 移动性 | 抗封锁 | 端上集成 |
|---|---|---|---|---|---|---|
| IKEv2/IPsec（现状） | 基准 | 中 | 1-RTT | 好（MOBIKE） | 差 | 已有 |
| WireGuard（wg-go） | +10~20% | 中（wg-go 弱于内核模块） | 1-RTT | **最好**（无状态漫游） | **差**（固定头部） | **最简单**（有官方 AAR） |
| VLESS+Reality | 中 | 中 | 1-RTT | 一般 | **最好**（TCP/443，抗主动探测） | 难（需自建 AAR） |
| Hysteria2 | 高（弱网强） | 高 | 1-RTT | 一般 | 中（QUIC 特征，UDP 被封即失效） | 难（无官方库） |
| Shadowsocks-2022 | 中 | **最低** | 1-RTT | 一般 | 差（纯随机字节流） | 中 |
| MASQUE | 中 | 高（双重封装） | 1-RTT | 一般 | 好（伪装 HTTPS） | 难（无系统 API） |

### 9.3 两个关键修正

**修正一：WireGuard AAR 自带 VpnService**

调研发现的真实陷阱，已在本项目修复。详见 3.2 节的告警块。

**修正二："多协议并行"在架构上不成立**

原设计假设"多个引擎各自建连、由上层选择"。调研指出更根本的约束：

- 每个协议各自调 `VpnService.Builder.establish()` = 互相顶掉，不是共存
- 真正可行的多路复用形态是 **"一个 TUN + 内核内多 outbound"**（sing-box 的模型）：
  core 从唯一的 fd 读包 → 规则引擎分类 → 分发到各 outbound
- 本项目当前的"模式切换"是**正确但能力受限**的形态：能做到"选一个协议用"，做不到"按域名/应用同时用不同协议"

**IKEv2 的特殊困难**：libipsec 自己会再创建一个 TUN（明文 IP 给 charon）。Root 之外拿不到第二个 TUN。所以要么保留 IKEv2 作为"整隧道模式"，要么废弃 libipsec 数据面、只让 charon 做控制面——后者是大工程。

这正是 `docs/UPGRADE-AND-ARCHITECTURE.md` 里"档 C"的实质内容。

### 9.4 建议路线

1. **短期**：IKEv2 保持默认（兼容性 + 移动性 + PQC 前景最好），WireGuard 作为可选快速通道。即当前实现。
2. **中期**：引入 `libbox`（sing-box），实现"一个 TUN + 规则引擎 + 多 outbound"。这一步才能真正做到"按域名/GeoIP 智能分流"。
3. **抗封锁**：以 **VLESS+Reality** 为首选（TCP/443 可穿透 UDP 封锁），Hysteria2 仅用于"丢包高但 UDP 可用"的场景。
4. **抗量子**：strongSwan 6.x 已有 ML-KEM（IKE_INTERMEDIATE + RFC 9370），是比 WireGuard 更干净的 PQC 路径——WireGuard 至今仍需 Rosenpass 之类外挂方案。

