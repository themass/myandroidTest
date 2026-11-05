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
- [ ] 决定：trojan / hysteria2 用自签证书（链接带 `insecure=1`），还是配域名和正式证书（`SB_DOMAIN` + `SB_CERT_FILE` + `SB_KEY_FILE`）
- [ ] 决定：服务器节点列表里要不要开放这些协议。目前后端节点表只有 IKEv2 / WireGuard / VLESS+Reality 的字段，其他协议只能让用户当"自己的节点"粘贴，要进官方节点列表需要扩后端表和接口

## 3. 后端：用户节点端到端

- [ ] 执行数据库迁移 `2026110900_invite_code.sql`、`2026111000_user_node.sql`
- [ ] 部署后端
- [ ] 用 App 登录后：Private 标签里保存、退出重进后能拉取、编辑、删除各一次
- [ ] 换账号登录，确认看不到上个账号的节点
- [ ] 试 vless / ss / hy2 三种新协议的链接能被服务端接受

没有写 curl 冒烟脚本：服务端响应有 AES 加密、请求头有签名，用 curl 复刻容易出错，直接用 App 验证更可靠。

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
