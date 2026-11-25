# LiveKit 极致性能专项 · 阶段二：生产调优落地记录（2026-11-25，104）

授权：用户 2026-11-24 深夜当面给出——「给你一切权力，就要极致的体验优化，性能优化，网络优化，linux 参数也可以调整」「继续，改完直接重启验证」。
所有改动均有备份文件与回滚说明，备份目录 `104:/home/web/backups/perf-20261125/`。

## 1. 根因证据链（动手前取证，全部只读）

1. **不是连不上**：发牌 200、两跳信令 WS 101、进房、mediaTrack published 全部实证
   （见 side-notes `livekit-call-ice-failure-rca-20261124.md`）。失败在媒体层：
   连接建立后 5–40s 内 ICE 会话被拆（`connect timeout after ICE connected`、
   `short ice connection`、三条 data channel 意外关闭 → job JS_FAILED）。
2. **ICE candidate pair stats 实锤**（17:06 通 PA_bMkkKUf9M6sS，SUBSCRIBER）：
   服务端 `requestsSent=8` 到客户端 prflx 候选 → `responsesReceived=0`；
   同 pair 服务端 `responsesSent=19` 全部送达（`lastRequestReceivedAt` 有值）。
   **入站通、回程丢**——蜂窝 CGNAT 对非对称 UDP 会话的表现。
3. **`TCP ICE connect timeout`**（transport.go:713）：UDP 与 ICE-TCP(7881) 同时超时
   → 单核 CPU 饥饿（每通冷 spawn prewarm ~9.5s 撞 livekit `high cpu load 0.94–1.0`）
   与网络路径是两个并存嫌疑，互相放大。
4. **内置 TURN 已在发**：v1.13.11 源码（`iceServersForParticipant`，roommanager.go:1064）
   证实 `turn.enabled+udp_port` 时 JoinResponse 下发
   `turn:104.160.191.87:3478?transport=udp` + 每参与者派生凭证（`turn.go:292/319`，
   sha256(secret|pid|exp)）；17:58 重启日志 `Starting TURN server {portUDP:3478,
   relay_range 30000-40000, quota 12}`。STUN Binding 探针得到应答（40B）——服务器活着。
   **18:32 真实通话端到端实证**（room `companion-yDceZIBoIZvjC83U5OI34w`，
   HUAWEI ADY-AL00 Wi-Fi）：SUBSCRIBER `selected udp relay 104.160.191...:31158`、
   `connectionType=turn`，PUBLISHER 走直连 srflx；inbound audio 持续
   （frames=1000 peak_max=0.99）、v2 协商 `caps=7`、会话 1m07s 后
   **干净 `job ended`**（故障期是 `JS_FAILED "agent worker left the room"`）。
   Allocate→relay 选中→媒体过 relay 全链成立。
5. **内核层干净**：conntrack 208/65536、UdpInErrors=0、UdpRcvbufErrors=0、ufw 未启用
   → 排除服务器内核丢包。

## 2. 已落地改动（按回滚难度排序）

### a) sysctl（新文件 `/etc/sysctl.d/99-companion-perf.conf`；回滚：删文件+`sysctl --system`）
| 参数 | 旧 | 新 | 理由 |
|---|---|---|---|
| vm.swappiness | 60 | **10** | swap 已用 1.1–1.4GB 且通话期 si/so 活跃；1vCPU 不能把媒体热页扫进 swap |
| kernel.sched_autogroup_enabled | 1 | **0** | autogroup 按登录会话归组，voice worker 跟着 ssh 会话漂移，CPUWeight 不可控 |
| net.core.rmem_default/wmem_default | 212992 | **1048576** | UDP 媒体接收缓冲默认值翻倍×5 |
| tcp_congestion_control | cubic | 保持 | fq 已是默认；BBR 收益待压测再议 |

### b) 服务减负 + CPU 优先级
- `disable --now pmcd pmie pmlogger pmproxy ModemManager`（PCP 四件套+ModemManager
  在这台 1vCPU 上纯属闲杂；监听端口 4330/44321-3 一并消失）。回滚：`systemctl enable --now ...`。
- `livekit.service.d/perf.conf`: **CPUWeight=1000**（systemd 默认 100 上限；1vCPU 上
  SFU 必须压过邻居）。已验证 `systemctl show livekit -p CPUWeight` = 1000。
- 已知邻居（未动，需要时再议）：testserverV java(333MB, prod)、new-api、next-server、
  job-come uvicorn、v2ray。**voice worker + companion api 此前裸跑在 ssh session scope
  里（非 systemd unit）**——结构性问题，已在 c) 收口。

### c) companion api/voice 落到 systemd（agent-docs main `86fbef1a`，104 已部署）
- 背景：api/voice 此前裸跑在 ssh session scope（session-1019/691，已 abandoned）——
  机器重启不自起；进程族在 user.slice，CPUWeight/cgroup 策略对它们无效。
- 新增 `myproject/companion-agent/deploy/systemd/companion-{api,voice}.service` +
  `scripts/run-as-service.sh`（unit 前台执行器，.env 导出口径与 process.sh 一致）+
  `deploy/systemd/README.md`（装/切换/回滚）。日志改走 journal（`.data/logs/*.log` 停更）。
- `process.sh` 检测 unit 已装且 web 可免密 systemctl（sudoers 白名单，只放行这两个
  unit 的 start/stop/restart/status/is-active）时整体委托 systemctl；
  切换窗口（旧 setsid 进程未停干净）仍走 PID 文件模式。publish.sh --restart 不变。
- 切换实录（18:47–18:49 UTC 静默窗口）：process.sh stop 旧进程 → cp unit +
  daemon-reload + `enable --now` → api `/health` ok、8081/8082 重新监听、
  livekit `worker registered`（18:49:10）、`process.sh status/restart` 委托生效、
  `systemctl is-enabled`=enabled×2、CGroup 归 /system.slice/companion-*.service。
- journald 上限 `SystemMaxUse=500M`（voice 日志曾单文件 98MB/日级增长）。
- 回滚：README「回滚」节——disable --now + 删 unit/sudoers 行，process.sh 自动回落 setsid 模式。

### e) 热池：消除通话中 ~10s prewarm 冷 spawn（agent-docs main `1595d513`，104 19:44 部署）
- 根因精化（journal 实证，纠正「每通 job 付 10s」的假设）：18:52 通的
  cost_ms=9971 **不是 job 付的**——job 的 RoomIO 落在早已 init 完的热进程上；
  9971ms 那行是 **补池进程**：proc_pool `_main_task` 每 100ms 补池，热进程被
  job 吃掉后立刻冷 spawn，**import 风暴砸在通话进行中的单核上**。两个放大器：
  `preload_modules` 未设（每个进程重烧 27 项 import）；cgroup v1 假
  cpu_count=2（SDK `CGroupV1CPUMonitor` 在 Ubuntu 20.04 回退 2.0）→ 空闲池
  默认 2，峰值 1 活跃 + 2 空闲 = 3 个 ~540MB 进程并存。
- 改动（capacity.py 一个提交）：`preload_modules = list(PREWARM_IMPORTS)`——
  27 项重 import 交给 forkserver，全生命周期只付一次，job 进程 COW 继承；
  `WARM_IDLE_PROCESSES = 1` 显式压掉假默认值 2。TDD：
  tests/test_voice_warm_pool.py（HEAD 基线 22 failed/1402 passed/90 skipped，
  新增 6 条全绿、零新增失败）。
- 部署实录（19:44 静默窗口）：LiveKit API 轮询确认无真实房间 →
  `publish.sh` → `systemctl restart companion-voice`。
- 验证（104 journal + ps，19:44–19:51）：
  - 开机：**单个** "process initialized"（pid 1415371）**elapsed 0.9s**
    （改前是两个、elapsed 13.6–15.6s 成对）；"preloading plugins" 列表 =
    27 项 + SDK 两件套；无 error initializing。
  - 探针呼叫（合成房 `perf-probe-hotpool`，19:47:53）：job 由**开机热进程
    1415371 承接**——进程族谱证明：补池进程 1416366（19:47:53 spawn）在房间
    关闭后仍存活待命，而 1415371 随房间退出；job 进程必随房间退，故 job
    = 热 pid。（journal INFO 级无 `launched_warm`/RoomIO linked 字段，以
    pid 族谱替代该判据。）补池 **prewarm cost_ms=839、init elapsed 1.06s**
    （改前 9323–9971ms）——通话中冷 spawn 风暴消除，本改动直接验收指标达成。
  - 房间回收：participant closing → `closing room reason: IDLE`
    （empty_timeout=20），job 进程干净退出，无残留。
  - 内存：`free -m` available **309MB**（改前 217–241）；空闲 job 进程
    2→1；swap 用量 ~874MB（改前 ~1000+）。
- 回滚：revert `1595d513` + restart companion-voice 即回现状态
  （prewarm_fnc 本体未动）。遗留：**开机仍付一次 forkserver preload**
  （boot 一次性 ~15s，预算内），真实 Wi-Fi 通话的第三方案例待下一通。

### d) livekit 配置（备份 `config.yaml.orig` + `config.yaml.bak-20261125`）
- `prometheus: port 9090 (basic auth metrics/<rand32hex>，只绑 127.0.0.1)` ——
  SLO 指标数据面落地（C8 ICE 分布、CPU/内存 capacity 数据从这里开始攒），
  已验证 /metrics 返回 67 条 livekit_* 指标。
- `room: empty_timeout 20`（默认 300s 空房占坑，1vCPU 上白占 job 进程内存）、
  `departure_timeout 10`（瞬时断线重连宽限，配合端上 reconnect 路径）。
- TURN 保持：udp 3478（已下发凭证）、tls_port 继续关闭——域名是 LE 签给
  livekit.ok123find.top，而 TURN 走 node_ip 直连签不上（config 注释原话，成立）。
- 重启验证：`active`、/rtc=401（正确）、worker registered（17:58:58）、
  公网两跳复测 301→401 正常、重启窗口选在房间清零时刻（LiveKit API 轮询确认无真实通话）。

## 3. 未验证 / 待收尾（诚实清单）

1. **relay 端到端：已证**（见 §1.4，18:32 真实通话 SUBSCRIBER `selected udp relay`、
   `connectionType=turn`、媒体过 relay、1m07s 干净 `job ended`）。
   遗留：**蜂窝网络下 relay 是否被选中仍未观察到**（该通是 Wi-Fi；蜂窝 prflx 回程丢的
   那批通话发生在调优前）——下一通蜂窝通话看 `connectionType` 即可收口。
   旁证：18:33:50 房间 agent 进房后用户未进，20s `empty timeout` 即关闭——
   新 `room.empty_timeout=20` 生效，空房不再占坑 5 分钟。
2. **设备不在手**：adb 掉线（V2279A 2MH0224B20002068 一度连上后消失），端侧
   MouthLevel logcat 取证待设备接回。
3. **Mac 端到端呼叫验证**：livekit python 客户端 SDK（livekit-rtc pip 包名不存在，
   应为 `livekit`/dev 版）未装成；下一通真实通话代替。
4. **hot pool：已落地并验证**（见 §2 e）——补池 prewarm 9323–9971ms → **839ms**，
   通话中冷 spawn 风暴消除；真实 Wi-Fi 通话的复验案例待下一通（验收判据已换为
   pid 族谱，journal INFO 级无 launched_warm 字段）。
5. 机器仍是 1vCPU/2GB——C9 容量红线（升配/拆机）**用户已拍板（2026-11-25）：
   先不迁邻居、先不升配，按现状凑合测试**。口径：单通已验证稳定（24m49s 干净
   通话）；测试期若出现第二路并发、或 `free -m` available 跌破 ~150MB / swap
   继续爬（现 1.5GB）/ livekit 再现 `high cpu load ≥0.9`，红线议题重开。
   （当日实测：语音栈 ~1.4GB、邻居 testserverV 267 + next-server 111 +
   job-come 58 ≈ 440MB，available 272MB。）

## 4. 复核命令（104）

```bash
systemctl is-active livekit; systemctl show livekit -p CPUWeight -p NRestarts
journalctl -u livekit --since '-1h' | grep -E 'Starting TURN|worker registered|high cpu'
ss -lnt | grep -E '3478|7880|7881|9090'
curl -s -u metrics:$(awk '/password:/ {print $2}' /home/web/etc/livekit/config.yaml) http://127.0.0.1:9090/metrics | grep -c '^livekit'
sysctl vm.swappiness kernel.sched_autogroup_enabled
```
