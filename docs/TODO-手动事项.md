# 需要你亲自做的事项

更新于 2026-11-05。代码侧能做的都已经做完并提交到本地（未推送）；下面是需要真机、你的素材、你的账号权限或你的决定的部分。

## 1. 真机验证 sing-box（最优先）

sing-box 替换了 Xray，TUN、路由、DNS、默认网络回调目前只在编译层面验证过，没有跑过。

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
- [ ] `bash vpn.sh wireguard install`
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

## 3.5 WireGuard（需要你拍板，我之前说错了一点）

**更正**：我之前说后端没有 `/host/wg/register.json`，这是错的——接口、`wg_device_key` 表、`unregister` 都已经实现（见 `docs/WIREGUARD-BACKEND.md`）。我当时信了客户端 `Constants.java` 里一条过时的注释，没去后端核实。

**真正缺的是两件事**，所以现在 WireGuard 最多只能手工配通一台设备：

1. **公钥只存进了数据库，没有同步到 WireGuard 服务器**。服务器不认识这个公钥就不会接受握手。目前只能手工 `bash vpn.sh wireguard add-peer <名字> <公钥>`。
2. **`clientAddress` 是每个节点一个固定值**（`host_v2.clientAddress`），所有设备拿到同一个隧道地址。两台设备同时连同一个节点，地址冲突，流量会串。`2026110400_node_data_template.sql` 里写的"服务端用同网段 AllowedIPs 下发"对 WireGuard 不成立：WireGuard 靠 AllowedIPs 把包路由到对应的 peer，每个 peer 必须有自己的 /32。

**可行方案（需要你确认后我再做）**：

- 后端：`register` 时给设备从节点网段里分配一个唯一 /32 地址，存进 `wg_device_key`；节点列表下发时把该设备自己的地址填进 `clientAddress`
- 同步到服务器：服务器上定时（或登录后）从后端拉取"设备公钥 + 地址"列表，用 `wg syncconf` 热更新，不断开已有连接。后端不直接登录服务器，由服务器主动拉，避免后端持有各节点的 SSH 权限
- 服务器脚本：新增 `vpn.sh wireguard sync`，与后端的一个受保护的只读接口对接（需要一个服务器专用的访问令牌）

这块跨后端和服务器，改动不小，我没有在你确认前动手。

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
