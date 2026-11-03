# VLESS+Reality 集成（XTLS/libXray）

> 核对日期：2026-11-01。用户已有稳定的 VLESS+Reality 服务器，本次做端上集成。

---

## 1. 为什么用 libXray，不自己写协议

VLESS 的解析和 Reality 的 TLS 1.3 握手伪装都是安全敏感代码，写错了就是漏洞。
本项目集成的是官方 **[XTLS/libXray](https://github.com/XTLS/libXray)**——Xray-core 的
gomobile 封装，**MIT 许可**，与项目 GPL-2.0-or-later 完全兼容。VLESS 协议解析、
Reality 握手、TLS 指纹伪装全部在 Xray-core 原生代码里完成，本项目不碰这部分逻辑。

## 2. 本地构建过程

没有 Maven 发布，走官方构建脚本从源码产出 AAR：

```bash
git clone --depth 1 https://github.com/XTLS/libXray.git
go install golang.org/x/mobile/cmd/gomobile@latest
export ANDROID_HOME=~/Library/Android/sdk
export ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/27.0.12077973
cd libXray && python3 build/main.py android
```

pin 到 `Xray-core v26.10.29`（脚本里的 `DEFAULT_XRAY_CORE_VERSION`）。产物
`libXray.aar`（约 50MB，含 4 个 ABI 的 `libgojni.so`）放进 `app/libs/`，由已有的
`fileTree(dir: 'libs', include: ['*.aar'])` 自动纳入构建。

## 3. 架构：libXray 不处理 TUN，需要我们自己的 VpnService

这是集成前必须搞清楚的关键事实（已用反编译/源码核实，不是猜的）：

- libXray 的 `AndroidManifest.xml` **不含任何 Service 组件**，纯函数库
- `LibXray.invoke(String)` 是唯一入口，JSON 请求/响应
- Xray-core 的 `tun` inbound 在 Android/iOS 上**不自己创建设备**，而是读取
  `XRAY_TUN_FD` 环境变量里的 fd——这个 fd 必须由我们通过 `VpnService.Builder.establish()`
  先建立好

所以新增了 `XrayVpnService extends VpnService`：建 TUN → `Os.setenv("XRAY_TUN_FD", fd, true)`
→ 调 `LibXray.invoke()` 的 `runXray` 方法。

## 4. 发现并修正了一个自己引入的严重 bug

**过程中发现：之前为了"解决 WireGuard 和 CharonVpnService 抢 TUN"的问题，我把
`GoBackend$VpnService` 从 manifest 里整个删除了——这个"修复"实际上把 WireGuard 弄坏了。**

反编译 `GoBackend.setState()` 的字节码确认：它内部用
`startService(new Intent(context, GoBackend$VpnService.class))` 来真正建立 TUN。
删掉这个组件声明后，WireGuard 在运行时会直接抛 `ComponentNotFoundException`——
表现为"编译通过，但协议完全不能用"，而这类错误静态编译检查不出来。

**根本原因是我对 Android VPN 限制的理解有误**：Android 的限制是"运行时同一时刻
只能有一个 TUN 处于建立状态"，不是"一个 App 的 manifest 里只能声明一个 VpnService
组件"。多个 VpnService 组件在 manifest 里共存完全合法，只要不同时 `establish()`。
真正的互斥早就在 `CharonVpnSessionRepository.connect()` 里做了（切换协议前先停
旧引擎），manifest 层面的"移除"是多余且有害的。

已恢复 `GoBackend$VpnService` 的声明，现在 manifest 里三个 VpnService 组件
（`CharonVpnService` / `GoBackend$VpnService` / `XrayVpnService`）共存，合并后
的 manifest 已核实。**这意味着在这次集成之前，WireGuard 很可能从来没有真正可用过**——
没有真机测试，这个 bug 一直没暴露。

## 5. 用户如何配置：粘贴 vless:// 链接，不用等后端

VLESS+Reality 服务器几乎都以 `vless://` 分享链接导出节点（Xray 官方工具、各类面板
都用这个格式，社区事实标准）。新增 `VlessLinkParser` 解析标准格式：

```
vless://{uuid}@{host}:{port}?encryption=none&flow=xtls-rprx-vision
        &security=reality&sni={serverName}&fp={fingerprint}
        &pbk={publicKey}&sid={shortId}&type=tcp#{remark}
```

设置页新增「VLESS+Reality node」入口，粘贴链接即可保存（`VlessProfileStore`，
本地 SharedPreferences）。这条通道**独立于应用自己的节点列表**——选中后直接连
用户自己的服务器，不走 `/host/server/list.json` 那套流程。

`HostVo` 也同步扩展了 Reality 相关字段（`vlessUuid`/`realityPublicKey`/
`realityShortId`/`realityServerName`/`realityFingerprint`），为将来后端下发
Reality 节点预留了路径，与 WireGuard 当初的扩展方式一致——但**这条路径目前没有
后端支持，当前可用的只有手动粘贴链接这一条**。

## 6. 加密套件参数来源

| 字段 | 来源 |
|---|---|
| `flow` | 链接里的 `flow` 参数，缺省 `xtls-rprx-vision` |
| `fingerprint` | 链接里的 `fp` 参数，缺省 `chrome` |
| `security` | 固定 `reality`（引擎不支持裸 TLS，解析器也会拒绝非 reality 的链接） |
| TUN 地址 | 固定 `172.19.0.1/30`（无路由冲突风险，Xray 的 `tun.gateway` 与
  `VpnService.Builder.addAddress` 用同一个值保持一致） |

## 7. 验证状态

```
./gradlew clean :app:assembleFreeDebug → BUILD SUCCESSFUL
```

- 合并 manifest 核实：3 个 VpnService 组件（Charon/GoBackend/Xray）共存，无冲突
- `libgojni.so`（34MB，含完整 Xray-core + quic-go + wireguard-go 等依赖）已打入 APK
- APK 体积：**46.7MB → 80.8MB**（+34MB，Xray-core 原生库体积所致，libXray 没有
  裁剪未用到的传输协议，这是已知的体积代价）

**没有验证的部分**（和之前所有协议一样，卡在同一个阻塞项）：
- 没有真机测试，没有一次真实握手被观察到
- `XRAY_TUN_FD` 在 Android 沙箱环境下通过 `Os.setenv` 设置、同进程内 Go 运行时能否
  正确读到，这一点在真机上才能确认
- 你的服务器参数（UUID/公钥/shortId/SNI）需要你亲自粘贴链接才能测试

## 8. 已知权衡

- **APK 体积 +34MB**：Xray-core 官方封装没有按需裁剪，这是用标准开源方案的代价，
  比自己精简实现更安全但更重
- **VLESS+Reality 节点独立于应用节点列表**：这是有意设计，不是遗漏——你的服务器
  和应用自己的节点farm是两个不同信任域，不应该混在一起选优
- **自动选优不会挑它**：`ProtocolSelector` 里 Reality 落在默认优先级（最低），
  需要用户在设置里显式选择。原因见 `docs/MULTI-PROTOCOL.md` 的讨论——它是抗封锁
  取向，不是速度取向，混进自动选优会在"连上但很慢"和"没连上"之间做出不符合用户
  预期的选择
