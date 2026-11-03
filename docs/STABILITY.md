# 稳定性优先工程（2026-11-01）

> 背景：用户明确"稳定性优先于速度"，抗封锁（Xray/Reality）相应延后。
> 本文记录本轮稳定性工程的具体改动、一处自我纠正、以及遗留项。

---

## 1. 断线自动重连：从"固定重试"改成"指数退避"

### 原状态

`VpnRadFragment.VpnCheckTask`：固定 6 秒检查一次，失败两次后直接报错放弃，不区分"网络抖动"和"账号密码错"。而且这套重试和仓库层本应有的重连完全脱节。

### 改动

新增 `domain/usecase/ReconnectPolicy.java`：协议无关、不依赖 Android，可单测。

- 退避序列：2s/4s/8s/16s/30s（封顶），最多 8 次
- 匹配常见移动网络抖动恢复窗口（多数 Wi-Fi/4G 切换 10 秒内恢复）

接入 `CharonVpnSessionRepository`：

- 引擎报错时先判断**是否值得重试**——`AUTH_FAILED`/`PEER_AUTH_FAILED`/`CONFIG_INVALID` 这类"重试结果不会变"的错误直接上报，不浪费电量重试
- 可恢复错误（网络不可达、协商失败等）按退避延迟重新发起连接，重连期间 UI 保持"连接中"而不是闪一次错误
- 连接成功或用户主动断开时清零退避计数

### 顺带修的一个冲突

Fragment 的 `VpnCheckTask` 原本自己也有一套重试（固定延时 + 重新走服务器选择），和仓库层的退避同时存在会互相打架——两套逻辑都在各自判断"要不要重连"，谁先触发谁说了算，行为不可预测。

现在 Fragment 层简化为**总超时看门狗**：只负责"仓库层退避了 75 秒还没连上就彻底放弃"，不再自己发起重试。75 秒 = 退避序列总和（60s）+ 余量，确保不会在仓库还在重试时就被误判为卡死。

## 2. IKEv2 网络切换监听——核实后发现无需改动（自我纠正）

### 我之前的判断（错误）

上一轮我告诉用户："现在 `setUnderlyingNetworks` 没有被调用，Wi-Fi 切 4G 时连接大概率会断"，并计划手动接入 `NetworkManager`。

### 核实后的结论

`org.strongswan.android.logic.NetworkManager.java` 确实存在，但我只搜索了 **Java 源码**里谁调用了它的 `Register()`，没有搜到就下结论"没被调用"。

实际核实（反汇编 `libandroidbridge.so`）发现：

```
strings libandroidbridge.so | grep NetworkManager
org/strongswan/android/logic/NetworkManager
failed to build NetworkManager object
```

这两个字符串是原生代码里 JNI `FindClass("org/strongswan/android/logic/NetworkManager")` 的证据——**`initializeCharon()` 内部的 `network_manager_create()` 会在原生层自动反射构造这个 Java 类并调用 `Register()`**，这是 strongSwan 官方架构本身的设计，根本不需要 `CharonVpnService.java` 手动接线。

`libcharon.so` 里 MOBIKE 相关符号齐全（`peer supports MOBIKE`、`IKE_MOBIKE` 等），默认开启。

**结论：这项工作不存在，我没有去碰已经在正常工作的代码。** 记录这次纠正是因为：如果不说清楚，用户会以为这是我"修好的"，而实际上是我上一轮的判断本身就错了，只是现在改口不会造成什么损失（没有先改错代码）。

## 3. Kill Switch：用系统能力，不自建

### 决策（已记录，未经讨论直接定）

不自己实现流量拦截逻辑。原因：自建 Kill Switch 极容易在 DNS、IPv6 等路径上留漏洞，而 `VpnService` 的系统级"始终开启 + 阻止非 VPN 连接"是 Google 测试过的方案，一旦开启，VPN 掉线时系统会直接断网而不是让流量裸奔。

### 实现

设置页新增「Kill switch」行：

- 检测当前是否已设为系统级 always-on app（读 `Settings.Secure.always_on_vpn_app`）
- 点击弹出说明对话框，确认后跳转系统 `Settings.ACTION_VPN_SETTINGS`，由用户在系统界面里完成"始终开启"和"阻止未经 VPN 的连接"两个开关

**已知限制**：Android 没有公开 API 能让应用直接查询"lockdown（阻止无 VPN 连接）是否开启"，只能查到"是否是 always-on app"。所以状态展示不是 100% 精确——显示"已启用"只代表 always-on 已设置，不保证 lockdown 也开了。这个限制没法绕过，已经是能拿到的最好信号。

## 4. 验证状态

- `./gradlew :app:compileFreeDebugJavaWithJavac` 通过
- **真机验证仍然缺失**——退避重连的时序行为、Kill Switch 的系统设置跳转、NetworkManager 的网络切换行为，都需要真实设备才能确认。这是本项目从一开始就有的阻塞项，本轮没有解决。

## 5. 对"稳定性优先"路线的下一步建议

| 项 | 状态 | 说明 |
|---|---|---|
| 断线退避重连 | ✅ 已做 | 本轮 |
| IKEv2 网络切换 | ✅ 本来就有 | 核实后发现无需改动 |
| Kill Switch | ✅ 已做（系统能力） | 本轮 |
| 连接质量监控 | ❌ 未做 | 握手耗时、丢包率等指标目前没有采集，无法判断"稳定"与否只能靠用户反馈 |
| 真机回归 | ❌ 阻塞 | 仍然是最大的未知数 |
| Xray/Reality（抗封锁） | 延后 | 按用户要求，稳定性/速度优先 |
