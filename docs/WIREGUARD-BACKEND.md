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

```json
{ "code": 0, "msg": "ok" }
```

| code | 含义 | 客户端行为 |
|---|---|---|
| 0 | 成功 | 本地标记已注册，后续连接不再重复提交 |
| 非 0 | 失败 | 本次 WireGuard 连接放弃，自动回退 IKEv2 |

### 幂等要求（重要）

同一 `deviceId` 重复提交应**覆盖**旧公钥，而不是报错。场景：
- 用户重装 App（本地密钥重新生成）
- 用户手动重置身份
- 客户端存储损坏后自动重建

如果后端对重复 deviceId 返回冲突，这些场景下客户端会永久无法注册。

---

## 接口二：下发节点参数（现有接口扩展）

现有的 `/host/server/list.json` 返回 `hostList`，每个节点需要新增以下**可选**字段，客户端据此判断该节点是否支持 WireGuard：

```json
{
  "gateway": "1.2.3.4",
  "port": 51820,
  "protocols": "ikev2,wireguard",
  "peerPublicKey": "<服务端 WireGuard 公钥，Base64>",
  "clientAddress": "10.8.0.5/32",
  "dns": "1.1.1.1",
  "mtu": 1420
}
```

| 字段 | 必填（若支持 WG） | 说明 |
|---|---|---|
| `protocols` | 是 | 逗号分隔。为空或缺失时客户端按"仅 IKEv2"处理，保持向后兼容 |
| `peerPublicKey` | 是 | 服务端公钥。客户端校验不了对端，这个值必须来自可信下发 |
| `clientAddress` | 是 | 分配给该客户端的隧道内地址，需与服务端配置的 AllowedIPs 匹配 |
| `dns` | 否 | 隧道内 DNS；缺省时用系统 DNS |
| `mtu` | 否 | 建议 1420（IPv4 over WireGuard 的安全值）；缺省用协议默认 |

### 客户端侧已有的行为

- `HostVo.isWireGuardReady()` 检查 `protocols` 含 `wireguard` **且** `peerPublicKey`、`clientAddress` 均非空
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

每个客户端公钥需要在服务端的 WireGuard 配置里有一行 peer：

```ini
[Peer]
# deviceId: a1b2c3d4e5f6a7b8
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
AllowedIPs = 10.8.0.5/32
```

`AllowedIPs` 必须与下发给客户端的 `clientAddress` 一致，否则握手成功但流量不通——
这是最常见的排错点。

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
| 建表脚本 | `src/main/resources/sql/2026110100_wireguard.sql` |

**响应格式已对齐**：本项目统一用 `{errno, data, error}` 包装，`errno == 0` 为成功，
不是本文早期草案里写的 `{code, msg}`。客户端已改为走统一的 `errno` 判定链路
（`BaseRequest.parserData()`），失败会自动走 `onErrorResponse`。
