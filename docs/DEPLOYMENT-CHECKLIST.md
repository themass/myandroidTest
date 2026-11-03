# 部署交付清单（2026-11-01）

> 本文档回答一个问题：**服务端代码和数据库我都做完了，你现在要做什么？**
> 结论先说：**代码层面你什么都不用改**，需要做的全是"部署 + 配置"层面的事。

---

## 一、这次做了什么（背景，可跳过）

1. 后端新增了 WireGuard 设备公钥注册接口（`/api/host/wg/register.json` /
   `unregister.json`），并给 `host_v2` 表扩展了 WireGuard **和** VLESS+Reality
   两套协议所需的字段（之前只做了 WireGuard，VLESS 字段是这次补上的）。
2. 把整条"节点数据从数据库到 App"的链路在本地用 Docker MySQL + 实际跑起来的
   后端服务**完整验证过一遍**，不是只看代码：
   - `GET /api/host/server/list.json` 返回的 `hostList` 里，`protocols` /
     `peerPublicKey` / `clientAddress` / `dns` / `mtu` 这些字段都能正确下发。
   - `POST /api/host/wg/register.json` 能把公钥写进 `wg_device_key` 表，
     重复提交会覆盖旧公钥（验证了重装 App 场景）。
3. 写了一套完整的建库 SQL（而不只是增量迁移），因为在验证过程中发现这个后端
   启动时会无条件访问十几张表（包括 RADIUS 鉴权表、设备黑名单表等），
   缺一张都起不来——这些之前都没有被系统性记录过。

验证过程中顺手发现并记录了两个和 VPN 无关的坑（给你做个心理准备，不是本次
引入的问题）：后端还挂着一个 LLM 聊天模块（Boson/OpenAI/豆包三个 Feign
Client），本地没配置对应的 `llm.config.*` 属性时**整个服务起不来**，不只是
LLM 功能报错。生产环境这些 key 应该是配好的，所以正常部署不会遇到，但如果你
以后本地调试这个后端，踩到这个坑不要怀疑是 VPN 相关改动搞坏的。

---

## 二、你需要做的事

### 1. 在生产 MySQL 上按顺序执行三个 SQL 文件

路径都在 `testserverV/vvv-link/src/main/resources/sql/`：

```bash
mysql -h<生产host> -u<用户> -p < 2026110000_baseline.sql
mysql -h<生产host> -u<用户> -p < 2026110100_wireguard.sql
mysql -h<生产host> -u<用户> -p < 2026110300_vless_reality.sql
mysql -h<生产host> -u<用户> -p < 2026110500_wireguard_port.sql
```

`2026110500_wireguard_port.sql` 是这次填数据前发现并修掉的一个真实缺陷：
`host_v2.port` 原本同时被 IKEv2（500/4500）和 WireGuard（51820）使用，一个节点
没法同时支持两种端口不同的协议。现在拆成了独立列，客户端与后端都已改并编译
通过，也能正确下发（本地验证过）。该列留 NULL 时回退到 `port` 再回退到
51820，所以纯 IKEv2 的老节点不用回填。

**如果你的生产库已经有 `vpn` / `radius` 库和部分表**（大概率如此，这套后端
显然已经跑过一阵子了）：`2026110000_baseline.sql` 里所有语句都是
`CREATE TABLE IF NOT EXISTS`，已存在的表不会被动。但请你**执行前人工过一遍
这个文件**，确认和你生产库里现有表结构没有冲突——这份 baseline 是我从代码反
推出来的，字段类型、默认值我按合理值填的，不代表就是你生产库当年的原始定义。
如果生产库已有同名表但结构不同，以生产库为准，不要用这份脚本覆盖。

`2026110100_wireguard.sql` 和 `2026110300_vless_reality.sql` 是纯增量
`ALTER TABLE ADD COLUMN`，风险低，但**同一个列名只能加一次**，如果你之前已
经手动加过这几个字段，重复执行会报错（不会破坏数据，报错后看一下是不是列已
存在就行）。

### 2. 往 `host_v2` 表里填真实节点数据

这是现在唯一卡住"客户端能跑起来"的事——**数据库表是空的**，App 连不上任何
节点是正常的，不是 bug。

> **先看这条，能省你几小时排查时间**：后端有个 `HostCheck` 定时任务会
> **ping 所有 `enable=1` 的网关**，连续两次 ping 不通的节点会被临时从下发
> 列表里剔除，客户端请求时直接返回"change another route"，看起来像是接口
> 坏了。我在本地验证时就踩到了这个。
> 如果你的节点**禁用了 ICMP**（很多云厂商默认关闭，或者只开了 UDP 51820），
> 必须把该行的 `needPing` 显式设成 `0` 来跳过检测：
>
> ```sql
> UPDATE vpn.host_v2 SET needPing = 0 WHERE id = <节点id>;
> ```
>
> `needPing` 默认值是 `1`（要检测）。WireGuard/VLESS 节点尤其容易被误判——
> 它们本来就只监听 UDP/TCP，不回 ICMP 是正常的。

对每个要支持 WireGuard 的节点，在 `host_v2` 对应行上填：

| 列 | 怎么来 |
|---|---|
| `protocols` | 写 `"ikev2,wireguard"`（逗号分隔，继续支持 IKEv2 的话两个都写） |
| `peerPublicKey` | 你服务器上 WireGuard 配置的 `[Interface] PrivateKey` 对应的公钥——`wg pubkey < /etc/wireguard/privatekey` |
| `clientAddress` | 分配给这个客户端段的地址，比如 `10.8.0.5/32`；必须和服务端 `AllowedIPs` 对应，这是最容易踩的坑，见下 |
| `dns` | 建议填 `1.1.1.1` 或你自己的 DNS |
| `mtu` | 建议 `1420` |
| `wireGuardPort` | WireGuard 监听端口，惯例 `51820`。留 NULL 则回退到 `port` 再回退到 `51820` |

对每个要支持 VLESS+Reality 的节点（你说已经有稳定服务器了）：

| 列 | 怎么来 |
|---|---|
| `protocols` | 追加 `"vless-reality"`，比如 `"ikev2,wireguard,vless-reality"` |
| `vlessUuid` | 你 Xray 服务端配置里这个用户的 UUID |
| `vlessFlow` | 通常是 `"xtls-rprx-vision"` |
| `realityServerName` | Reality 配置里 `serverNames` 的那个借用域名 |
| `realityPublicKey` | 服务端 Reality 密钥对的公钥 |
| `realityShortId` | 服务端 `shortIds` 列表里的一个 |
| `realityFingerprint` | 通常填 `"chrome"` |

### 3. 服务端 WireGuard 配置要和下发的 `clientAddress` 对应

每注册一个客户端公钥，你需要在服务器 WireGuard 配置里加一行 peer：

```ini
[Peer]
PublicKey = <该客户端注册上来的公钥，从 wg_device_key 表里查>
AllowedIPs = <和 host_v2.clientAddress 完全一致>
```

客户端公钥是**动态**注册上来的（每台设备连接前自动调用
`/api/host/wg/register.json`），不是你预先知道的，所以这一步没法在部署时
一次性做完——需要你起一个小脚本/定时任务，轮询 `wg_device_key` 表里的新记
录，自动生成/追加 WireGuard 配置并 reload。这块我没有帮你写，因为具体怎么
做取决于你的服务器是用 `wg-quick`、`wg quick` + systemd，还是别的管理方式，
这是部署细节，不是代码问题。

### 4. 确认生产环境的 `spring.datasource.*` 和 `llm.config.*` 配置

现有的 `bootstrap-local.yml` / `bootstrap-prod.yml` 是空文件——数据源配置
应该是通过外部注入的（环境变量、`-D` 参数，或者你们有自己的配置中心）。部署
时确认：

- `spring.datasource.url` / `username` / `password` 指向生产 MySQL
- `llm.config.bosonAI.endpoint`、`llm.config.openAI.endpoint` /
  `appKey`、`llm.config.doubaoAI.host` / `region` / `appKey` /
  `secretKey` 都有值——哪怕 LLM 聊天功能你们不用了，这三个 Feign Client
  在启动时会无条件初始化，缺配置应用直接起不来（我本地验证时就是被这个
  卡住的，见上面"背景"部分）

---

## 三、客户端这边不需要你做任何事

- `host_v2` 表里哪怕一行 WireGuard/VLESS 字段都不填，客户端会自动按纯
  IKEv2 处理，不会报错也不会崩，这是设计好的向后兼容行为。
- 节点数据全部来自 `/api/host/server/list.json`，客户端代码里没有写死
  任何网关地址——"VPN 数据从后端接口取"这件事本来就是现状，不是这次改的。

---

## 四、已知遗留（不在本次范围内，供你排期参考）

1. **真机验证从未做过**——这是目前最大的空白。模拟器在这台机器上跑不起来
   （底层沙箱限制，和这次工作无关），所有协议切换、断线重连的行为目前只
   经过了代码审查和本地后端联调，没有在真实 Android 设备上连过真实服务器。
2. **Keystore 密码历史上泄露过**（在 git 历史里），建议找时间轮换签名密钥。
3. WireGuard 注册接口目前没有鉴权/限流，理论上任何知道 `deviceId` 格式的
   客户端都能注册公钥。如果担心被刷，建议后面加个简单的频率限制。
