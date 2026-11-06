# 需要你亲自做的事项

更新于 2026-11-05。代码侧能做的都已经做完并提交到本地（未推送）；下面是需要真机、你的素材、你的账号权限或你的决定的部分。

## 0. 服务器实测结果（2026-11-05，Ubuntu 18.04 / 内核 4.15 / 1 核 1G，公网 38.244.10.172）

已在该服务器上实际安装并验证（不是只看语法）：

| 项目 | 结果 |
|---|---|
| sing-box 1.13.5 安装 | 通过 |
| sing-box vmess-ws / trojan / shadowsocks / hysteria2 | 通过：本机 sing-box 客户端分别经这四个服务端端口访问外网，均 HTTP 204 |
| sing-box 的 VLESS+Reality 服务端 | **不可用**：1.13.5 的 Reality 服务端拒绝所有客户端（用全新密钥也一样，Xray 客户端也连不上它）。已从服务器删除，脚本改为 `add all` 不再包含它，单独添加会提示用 Xray |
| sing-box 客户端 -> 现有 Xray Reality 服务端（443） | 通过：HTTP 204。**App 里 sing-box 客户端连 VLESS+Reality 是通的**，服务端 Reality 用 Xray |
| `xray check`（现有 Xray，只读） | 通过（配置、服务、443 监听、Reality 握手、经代理访问外网） |
| `xray links`（读取现有配置） | 通过，链接里的公钥由私钥推导，与服务端一致 |
| WireGuard 安装 + 内核模块 | 通过（内核 4.15 自带 wireguard 模块） |
| WireGuard 同步（模拟后端，严格校验） | 通过：写入 1 台设备，热更新接口 |
| WireGuard 真实握手（模拟客户端，内核 wg 命名空间） | 通过：双方 latest handshake 正常，双向有流量 |
| 手工 peer（保留段 10.66.255.x） | 通过 |
| `system net` / `system bbr`（重复运行） | 通过，不会重复追加 |

**没有验证、原因**：
- `xray install`：这台机器已经有在跑的 Xray（443，你的服务），install 的最后一步会重启 `xray.service`，会断开现有连接。没有在这台机器上执行。新机器上的全新安装路径没有测过。
- `strongswan`：机器上已有 `swanctl`，并且有 500/4500 的防火墙规则，很可能正在用 IKEv2。编译安装会替换它，有风险，没有执行；1 核 1G 编译也很慢。
- `ssr`、`system init`（大量 apt 包）、`system iptables-ipsec`：未执行。

**脚本修正（已在 myconf 本地提交）**：`singbox add all` 去掉了 sing-box 的 vless-reality；单独添加会报错并指向 `xray install`。`singbox remove` 仍可删除老的 vless-reality 安装。

**已修复**：`wireguard sync-install` 先做一次同步，成功后才启用每分钟定时器；首次同步失败会关闭定时器并退出。

## 服务器要求（实测）

- 系统：**Debian/Ubuntu（apt）+ systemd**。不支持 CentOS/RHEL（脚本依赖 apt 和 systemctl）。已在 Ubuntu 18.04 上验证；bash 4.4 和 python 3.6 都够用。
- 内核：
  - WireGuard 需要内核模块（4.15 的 Ubuntu 内核自带；5.6+ 主线内核也自带）。没有模块的内核：`WG_BACKEND=auto`（默认）会改用 wireguard-go（drop-in `WG_QUICK_USERSPACE_IMPLEMENTATION`），需要系统 apt 源里有 `wireguard-go` 包，否则请装 wireguard-dkms 或升级内核。
  - BBR 需要 4.9 以上。
  - sing-box 和 Xray 是 Go 静态二进制，对 glibc 版本不敏感。
- 资源：只跑协议，1 核 1G 够用；编译 strongSwan 需要更多时间和内存。
- 架构：脚本支持 amd64、arm64、armv7，实测只有 amd64。
- 网络：需要能访问 GitHub（下载 sing-box、Xray）、download.strongswan.org（strongSwan 源码）、apt 源。

## 1. 真机验证 sing-box（最优先）

sing-box 替换了 Xray，TUN、路由、DNS、默认网络回调还没在 App 里跑过（服务器侧的协议链路已在第 0 节验证）。

- [ ] 装 debug 包，连一个 **VLESS 服务器节点**，确认状态栏有 VPN 钥匙、网页能打开
- [ ] 连一个 **用户节点**（Private 标签里粘贴链接），同样确认能上网
- [ ] 断开、再连、切换节点各一次
- [ ] 开分应用代理后再连一次
- [ ] 抓日志：

```bash
adb logcat | grep -E "SingBox|singbox|libbox"
```

连不上时把日志发给我。重点看 `sing-box 启动失败`、`连通性探针`、`protect(` 这几类行。

## 2. 服务端协议（脚本已写好，需要你上机器跑）

脚本在 `/Users/gqli/work/myconf/shell/`，用法见同目录 `README.md`。我没法在真实服务器上跑，**先在一台新机器或快照上试**。

- [ ] `bash vpn.sh xray install`（443）
- [ ] `bash vpn.sh singbox add all`（vless-reality 444 / vmess-ws 8880 / trojan 8443 / shadowsocks 8388 / hysteria2 8444）
- [ ] `bash vpn.sh wireguard install`，然后按第 3.5 条配置设备同步（`sync-install`）
- [ ] 云厂商控制台安全组放行上述端口（脚本结束时会列出）
- [ ] 把生成的链接粘到 App 的 Private 标签里逐个试
- 已决定：trojan / hysteria2 先用自签证书（链接带 `insecure=1`）。以后有域名再换：`SB_DOMAIN` + `SB_CERT_FILE` + `SB_KEY_FILE`
- [ ] 把新协议放进官方节点列表（后端和客户端代码已就绪，见第 3 条）：在服务器上跑 `bash vpn.sh singbox sql <host_v2 行 id>`，把输出的 UPDATE 语句在后端 MySQL 上执行。**代理协议要放在单独的节点行上**，`protocols` 里不要写 `ikev2`，否则客户端按原来的 WireGuard / IKEv2 规则连接，不会用到这些链接

## 3. 后端：用户节点端到端

- [ ] 执行数据库迁移 `2026110900_invite_code.sql`、`2026111000_user_node.sql`、`2026111100_proxy_links.sql`（官方节点的代理链接列）
- [ ] 部署后端
- [ ] 用 App 登录后：Private 标签里保存、退出重进后能拉取、编辑、删除各一次
- [ ] 换账号登录，确认看不到上个账号的节点
- [ ] 试 vless / ss / hy2 三种新协议的链接能被服务端接受
- [ ] 官方节点：执行第 2 条的 UPDATE 后，App 里该节点能连上（注意：只取优先级最高的一个协议，顺序 vless-reality > trojan > vmess > shadowsocks > hysteria2，连接失败不会自动换下一个）

没有写 curl 冒烟脚本：服务端响应有 AES 加密、请求头有签名，用 curl 复刻容易出错，直接用 App 验证更可靠。

## 3.5 WireGuard 地址分配与服务器同步（代码已写完，按顺序上线）

方案已按你的确认实现：后端给每台设备分配唯一 /32 地址，服务器每分钟主动拉取设备公钥并热更新。
契约和限制见 `docs/WIREGUARD-BACKEND.md`。上线顺序不能乱：

1. [ ] 后端执行迁移 `2026111200_wg_addr_index.sql` 和 `2026111300_wg_owner.sql`（**先于部署新版后端**）。它给 `wg_device_key` 加 `addrIndex` 列并回填，
       回填后看脚本里那条 `SELECT ... addrIndex > 65279` 应该返回空
2. [ ] 后端 `secrets.properties` 加一行 `wg.syncToken=<至少 24 位的随机串>`（`openssl rand -hex 24` 生成）。不配或太短时同步接口一律 403
3. [ ] 部署后端
4. [ ] 每个 WireGuard 服务器上（**每台都要设线路等级**，免费线路填 0，VIP 线路填对应等级）：

```bash
bash vpn.sh wireguard install        # 网段改成了 10.66.0.0/16；从旧 /24 升级会重启接口，已有连接短暂中断
WG_SYNC_URL=https://api.ok123find.top/vvv/api/noapp/wg/peers.txt \
WG_SYNC_TOKEN=<与 wg.syncToken 相同> \
WG_LEVEL=<本线路等级> bash vpn.sh wireguard sync-install
bash vpn.sh wireguard info           # 服务端公钥，填到后端该节点的 peerPublicKey
```

5. [ ] 后端节点表：该节点 `protocols` 含 `wireguard`，填 `peerPublicKey` 和 `wireGuardPort`；`clientAddress` 现在不用填了
6. [ ] App 登录后连 WireGuard 节点，确认能上网；换一台设备同时连同一个节点，确认两台互不影响

注意：
- 旧版 App 注册过的设备本地没存账号信息，新版 App 会自动重新注册一次（账号变化时也会），不需要用户操作。在那之前，这些设备只在免费线路的名单里
- 刚注册的设备要等服务器下一次同步（最长约 1 分钟）才能握手，第一次连接可能失败
- 之前用 `add-peer` 手工添加、地址在 `10.66.66.x` 的 peer 不在新网段里，`install` 会警告，需要 `del-peer` 后重新添加
- 登录是注册的前提：未登录用户连不了 WireGuard，会自动回退 IKEv2

## 4. 推送与版本号

- [ ] 客户端 `global-vpn` 本地领先若干提交，确认后推送
- [ ] 后端 `testjava_springboot` 本地提交 `d9eff198`（链接解析）、`f0f65fb6`（用户节点）未推送；后端工作区还有与这些无关的未提交改动（`pom.xml`、`vvv-poetry/`），推送前看一眼
- [ ] `app/build.gradle` 里 `versionName 2.0.0.3 -> 2.0.0.4` 是你的未提交改动，我没动
- [ ] `myconf` 仓库有新增和修改的脚本，见下面第 6 条，自行审阅后提交

## 5. 需要真机或素材

- [ ] **Sites 标题**不显示：真机跑一下，`adb logcat | grep "sites row"`，把输出和截图发我
- [ ] **启动页**：需要不带文字的原图，以及艺术字体文件（放 `res/font/`），给我后才能做
- [ ] **激励广告**：华为等无 Google 服务的机型不会有广告。现在会提示"需要 Google Play 服务"而不是笼统的"暂无广告"（本次改动）。用有 GMS 的真机验证广告能放、看完后服务端能发券

## 6. myconf 脚本整理：我被拦下的部分

- [ ] 删除已被取代的旧脚本（git 里都有，可恢复）。我的删除命令被权限系统拦了两次，没有绕开：

```bash
cd /Users/gqli/work/myconf/shell
git rm vpn_setup_test.sh clean_strongswan_simple.sh cleanup_strongswan.sh force_clean_strongswan.sh \
       fix_strongswan_permissions.sh check_strongswan.sh test_minimal_strongswan.sh \
       SCRIPT_STRUCTURE.md check.sh lib/vpn_common.sh
mkdir -p docs && git mv README_strongSwan59.md README_strongSwan6.md README_strongSwan6_simple.md docs/
```

  `vpn_setup.sh` / `ssvpn_setup.sh` 已改成转发到 `vpn.sh` 的兼容入口，旧参数照常能用。
- [ ] 用 `sing-box check -C <目录>` 校验一遍渲染出的配置：我没能在本机编译 sing-box 命令行（被拦），渲染出的 JSON 只验证了是合法 JSON；脚本在服务器上写入前会自己做这一步校验，不通过不会写入
- [ ] `ipsecstatus.sh` 被 crontab 调用，它的 `status_send` 在取不到连接数时会 `sudo reboot`，并上报到写死的地址，建议你看一眼要不要保留

## 7. 其他你说过先不动的

- 邮件账号密码仍在源码里
- 后端收尾项（`user.pwd`、`del.json`、设计文档）
- WebView 页面返回键：已经是 Toolbar 的 Up 箭头，与 Compose 页一致，不需要改
