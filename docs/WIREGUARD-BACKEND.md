# WireGuard 后端接口契约

> **客户端与服务端均已实现**（2026-11-01）。本文是对接契约说明与部署注意事项。
> 后端仓库：`/Users/gqli/work/testserverV`（`vvv-link` 模块，Spring Boot + MyBatis）

---

## 背景：WireGuard 与 IKEv2 的认证模型完全不同

| | IKEv2（现有） | WireGuard |
|---|---|---|
| 身份凭证 | 用户名 + 密码（EAP） | **一对静态密钥**，服务端认识你的公钥 |
| 客户端发什么 | 账号密码 | 用自己私钥签名的握手包 |
| 服务端需要什么 | 校验账号密码 | **事先知道客户端公钥** |
| 密钥生命周期 | 每次连接协商 | 长期，除非客户端重置 |

所以要让 WireGuard 能用，必须先解决一件事：**服务端得先知道这台设备的公钥**。

---

## 接口一：注册公钥

```
POST /vvv/api/host/wg/register.json
```

### 请求

```json
{
  "deviceId": "a1b2c3d4e5f6a7b8",
  "publicKey": "xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg="
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `deviceId` | string | 设备标识，客户端取自 `DeviceInfoUtils.getDeviceId()` |
| `publicKey` | string | WireGuard 公钥，Base64（32 字节 Curve25519） |

### 响应

需要登录（请求头带 `Vpn-Token`）。成功时 `data` 里带本设备唯一的隧道内地址：

```json
{ "errno": 0, "data": { "clientAddress": "10.66.1.5/32" } }
```

| 情况 | 客户端行为 |
|---|---|
| `errno == 0` 且 `clientAddress` 是合法 IPv4 CIDR | 本地保存公钥已注册和该地址，建隧道时用它 |
| `errno != 0`，或没有 `clientAddress`（旧版后端只返回 `{"ret":true}`） | 本次 WireGuard 连接放弃，自动回退 IKEv2 |

为什么必须登录：注册一次就占用一个隧道内地址，并被同步到 WireGuard 服务器。匿名开放的话，
任何人都能耗尽地址池，或给自己开一个能进内网的 peer。

### 幂等要求（重要）

同一 `deviceId` 重复提交应**覆盖**旧公钥，而不是报错。场景：
- 用户重装 App（本地密钥重新生成）
- 用户手动重置身份
- 客户端存储损坏后自动重建

如果后端对重复 deviceId 返回冲突，这些场景下客户端会永久无法注册。

---

## 地址规划：每台设备一个唯一的 /32

所有 WireGuard 节点共用一个 `10.66.0.0/16` 内网，**设备地址与节点无关**：

| 地址 | 用途 |
|---|---|
| `10.66.0.1` | 各节点自己的接口地址（节点之间互相独立） |
| `10.66.0.2` ~ `10.66.254.255` | App 设备，由后端注册时按 `wg_device_key.addrIndex` 分配，最多约 6.5 万台 |
| `10.66.255.0/24` | 保留给运维手工添加的 peer（`vpn.sh wireguard add-peer`） |

地址只由 `addrIndex` 决定（`WgAddress.toCidr`），重装 App 重复注册时公钥被覆盖、地址不变。
设备被注销后序号会被回收复用，因为服务器每分钟按后端全量重建 peer 列表，不会残留旧设备。

改网段必须同时改后端 `WgAddress.PREFIX` 和服务器 `WG_SUBNET`，否则同步校验会把所有设备拒掉。

## 服务器同步

后端只是把公钥存进数据库，WireGuard 服务器不认识它们就不会接受握手。所以每个 WireGuard 节点
**主动**拉取设备列表（后端不持有任何节点的登录权限）：

```
GET /vvv/api/noapp/wg/peers.txt
X-Sync-Token: <wg.syncToken>
```

响应是纯文本，每行 `公钥 10.66.x.y/32`，不含设备标识。用 `.txt` 而不是 `.json` 是有意的：
`.json` 才进面向 App 的用户校验、版本校验和 AES 加密，服务器脚本既没有这些头也解不了 AES。
鉴权只靠令牌，令牌在后端 `secrets.properties` 的 `wg.syncToken`（至少 24 位，未配置时一律 403）。

服务器侧见 `myconf/shell/modules/wireguard.sh` 的 `sync` / `sync-install`：每分钟一次，
逐行严格校验（公钥格式、地址在网段内、/32、无重复），**任何一行不合法就整批放弃**；
peer 数量骤降（例如后端异常返回空）时拒绝应用，需要 `WG_SYNC_FORCE=1` 才放行；
`wg syncconf` 热更新，不断开已有连接。

## 当前的限制

- 设备被同步到服务器有最长约 1 分钟的延迟：刚注册就连，第一次可能握手失败，客户端会重试。
- 所有节点收到同一份设备列表，任何已注册设备都可以连任何 WireGuard 节点，节点的 VIP 限制
  只体现在节点列表的下发上，不在 WireGuard 层强制。
- 注册没有频率限制，登录用户可以用伪造的 `deviceId` 反复注册来占用地址。

## 接口二：下发节点参数（现有接口扩展）

现有的 `/host/server/list.json` 返回 `hostList`，每个节点需要新增以下**可选**字段，客户端据此判断该节点是否支持 WireGuard：

```json
{
  "gateway": "1.2.3.4",
  "port": 51820,
  "protocols": "ikev2,wireguard",
  "peerPublicKey": "<服务端 WireGuard 公钥，Base64>",
  "dns": "1.1.1.1",
  "mtu": 1420
}
```

| 字段 | 必填（若支持 WG） | 说明 |
|---|---|---|
| `protocols` | 是 | 逗号分隔。为空或缺失时客户端按"仅 IKEv2"处理，保持向后兼容 |
| `peerPublicKey` | 是 | 服务端公钥。客户端校验不了对端，这个值必须来自可信下发 |
| `clientAddress` | 否（已弃用） | 客户端不再使用。每台设备的地址由注册接口返回，见上文 |
| `dns` | 否 | 隧道内 DNS；缺省时用系统 DNS |
| `mtu` | 否 | 建议 1420（IPv4 over WireGuard 的安全值）；缺省用协议默认 |

### 客户端侧已有的行为

- `HostVo.isWireGuardReady()` 检查 `protocols` 含 `wireguard` **且** `peerPublicKey` 非空
- 不满足时该节点只按 IKEv2 连接，不会尝试 WireGuard
- 用户若在设置里强制选了 WireGuard 但节点参数不全，会自动回退 IKEv2 而不是报错

---

## 接口三（可选）：注销公钥

用户退出登录或重置时，客户端目前只清本地密钥（`WireGuardKeyStore.clear()`），
服务端上的旧公钥会变成孤儿记录。建议提供：

```
POST /vvv/api/host/wg/unregister.json
{ "deviceId": "..." }
```

不实现也能工作（旧公钥无人使用即为死配置），但会积累垃圾数据。

---

## 服务端配置参考

不再手工写 `[Peer]`：App 设备由 `sync` 自动写入 `/etc/wireguard/peers.d/_synced.conf`，格式如下，
`AllowedIPs` 必须是设备自己的 /32，这是"握手成功但没流量"最常见的原因：

```ini
[Peer]
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
AllowedIPs = 10.66.1.5/32
```

---

## 客户端实现位置

| 职责 | 文件 |
|---|---|
| 密钥生成与持久化 | `data/local/WireGuardKeyStore.java` |
| 注册请求 | `data/remote/WireGuardRegistrationUtil.java` |
| 建隧道 | `data/vpn/engine/WireGuardTunnelEngine.java` |
| 接口常量 | `myapp/constant/Constants.java` → `API_WG_REGISTER_URL` |

服务端实现位置：

| 职责 | 文件（`testserverV/vvv-link/`） |
|---|---|
| 接口入口 | `src/main/java/com/timeline/vpn/web/controller/api/HostController.java` |
| 表单校验 | `src/main/java/com/timeline/vpn/model/form/WgRegisterForm.java` |
| 业务逻辑 | `src/main/java/com/timeline/vpn/service/impl/WgDeviceKeyServiceImpl.java` |
| DAO | `src/main/java/com/timeline/vpn/dao/db/WgDeviceKeyDao.java` |
| SQL 映射 | `src/main/resources/mybatis/mapper/WgDeviceKeyMapper.xml` |
| 地址分配 | `util/WgAddress.java`、`util/WgAddressAllocator.java` |
| 同步接口与令牌 | `NoappController#wgPeers`、`util/WgSyncAuth.java` |
| 建表脚本 | `src/main/resources/sql/2026110100_wireguard.sql`、`2026111200_wg_addr_index.sql` |

**响应格式已对齐**：本项目统一用 `{errno, data, error}` 包装，`errno == 0` 为成功，
不是本文早期草案里写的 `{code, msg}`。客户端已改为走统一的 `errno` 判定链路
（`BaseRequest.parserData()`），失败会自动走 `onErrorResponse`。
