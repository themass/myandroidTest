# 陪伴通话 RTC provider 抽象与零成本切换协议

状态：设计定稿，Android 侧第一步已实施。
关联：`companion-agent/docs/`（服务端协议编解码由 tos 会话另行落地）、
`docs/avatar-2d-design.md`（形象事件 JSON）。

## 0. 目标与边界

目标：客户端与服务端把 RTC 依赖拆干净，定义接口与线协议；抽象之下至少一个实现是
LiveKit，之后接火山 RTC 做容灾/国内流量切换，**切换本身零成本**（用户无感、发版无需改码）。

已确认的两个决策：
- **切换权威在服务端**：`POST /v1/voice/sessions` 建会话时决定 provider 并随 grant 下发；
  客户端只照着分发，不做网络探测与自选。灰度、容灾摘除、按地域分流都是服务端配置/代码的事。
- **容灾线路接受功能降级可用**：火山线路上打断（barge-in）/回合判定先用自研简单 VAD 规则，
  体验允许明显差于 LiveKit 线路，后续再追平。理由：这两项目前完全在 livekit-agents 框架内
  （服务端调研结论），火山线上没有对应物；先保通路，不为容灾预支 1-2 人月的调优。

## 1. 分层与不变量

```
┌ Android ────────────────┐        ┌ companion-agent ──────────────┐
│ CompanionViewModel      │        │ SessionService.create()       │
│   └ transportFactory ───┼── ① ──▶│   └ provider 选择 → grant     │
│ CompanionTransports     │  grant │ VoiceTransport 端口（新）      │
│   provider → 实现分发    │        │   ├ LiveKitTransport（现有）  │
│ CompanionTransport 接口  │   ②   │   └ VolcTransport（待接）      │
│   ├ LiveKit…（现有）     │◀──────▶│ 房间内：下行统一事件信封       │
│   └ Volc…（待接）        │  媒体/  │  TurnPipeline/PlatformBrain  │
└─────────────────────────┘  事件  │ （厂商无关，已存在）           │
                                   └───────────────────────────────┘
```

**不变量（两边都必须守）：**
1. ViewModel/UI/单测只依赖 `CompanionTransport` 接口与自有类型（现状已满足：接口 12 个成员零
   LiveKit 泄漏，Fake/Scripted/Recording 三个替身已在跑）。**任何新实现不得让 LiveKit/火山
   类型出现在接口签名里。**
2. 会话创建接口只描述**意图与凭据**，不泄漏 RTC 细节：客户端拿到 `provider` 后自己决定怎么连。
3. 房间内下行消息（形象/主动/确认）收敛到**一个统一事件信封**（§3），provider 适配器只负责
   「把信封字节送到、按 topic 路由到信封解析器」。信封内容物与 RTC 厂商无关。
4. 语音回合写回历史只走 `SubmitService`（现状已是），RTC 侧永远不直接写会话历史。
5. import-linter 继续锁住自有层（`agent/text/*`、`TurnPipeline`、`PlatformBrain`）不得 import
   任何 RTC SDK。

## 2. 会话 grant 协议（①）

`POST /v1/voice/sessions` 响应新增两个字段，其余字段不动（旧客户端无感）：

```jsonc
{
  "session_id": "...",
  "room_name": "...",
  "token": "...",
  "livekit_url": "wss://...",        // 保留：livekit 线路的连接端点（旧客户端兼容）
  "provider": "livekit",             // 新增：livekit | volcengine
  "provider_config": {                // 新增：provider 特定参数；livekit 时为 {}
    // volcengine 形态（草案）：
    // "app_id": "...", "room_id": "...", "user_id": "...",
    // "token": "...", "turn_url": "wss://..."
  },
  "expires_at": "...", "identity": "...", "chat_session_id": 123
}
```

规则：
- `provider` 缺省/为空 ⇒ 按 `"livekit"` 解释（新旧互不破坏）。
- 客户端拿到**不认识**的 provider ⇒ 不崩溃，降级按 livekit 尝试并打日志（新版本服务端+旧版本
  客户端的共存窗口内这是唯一合理行为）。
- 服务端 `provider` 取值来源：`Settings.companion_voice_provider`（默认 livekit），后续可换成
  按用户/地域/健康探测的策略函数；选择逻辑收在 `SessionService.create` 一处。
- token 语义：各 provider 的 token 都是**房间级短期凭据**，`expires_at` 语义不变；
  服务端按 provider 调对应的 `TokenIssuer` 实现（现有 livekit JWT 已是零依赖自实现）。

## 3. 房间内下行信封（②）

所有 provider 线路上的非媒体下行消息统一为 JSON 信封（与 tos 会话正在落的
`cmp.cue/cmp.ctl/cmp.in/cmp.tel` topic 编码对齐，避免两套协议）：

```jsonc
{ "v": 1, "topic": "avatar|proactive|action", "seq": 42, "ts": "...", "body": { ... } }
```

- 传输：livekit 线路 = `publish_data(topic=...)`；火山线路 = 数据通道消息。适配器负责送达与
  顺序（RELIABLE 语义），**不解析 body**。
- 客户端解析在 `CompanionTransport` 实现内部完成后映射成自有 `CompanionEvent`
  （TextDelta/AvatarJson/Proactive/NeedsConfirmation/TurnDone/Error），与现在一致。
- 上行：文本/触摸继续走自有 `sendText`/`sendTouch` 接口方法，各实现映射到自家通道。
- 语音媒体：livekit = 房间音轨；火山 = RTC 音频流 + 服务端 gateway 直连火山
  ASR/TTS HTTP（gateway 通路已有实现与测试，未接线——容灾线路的起点）。

## 4. Android 侧实施（本次已完成）

- `SessionResponse` 增加 `provider: String`（默认 "livekit"）与解析；
- 新增 `CompanionTransports`：`provider → transport` 工厂，唯一分发点；未知 provider
  记日志降级 livekit；
- `CompanionViewModel`：删掉 3 行 `is LiveKitCompanionTransport` 具体类判断；
  `observeConnection()/listen()` 统一顺序（先订阅后 connect，两家实现都成立）；
  connect 前按 `session.provider` 经工厂解析/重建 transport（provider 变化即换实现重连）；
- 测试：provider 解析、工厂回退、按 provider 换 transport 接入。

火山实现（待接）：新增 `VolcCompanionTransport implements CompanionTransport`，
在工厂注册一行；UI/ViewModel/测试不动。

## 5. 服务端实施（下一批）

1. `Settings` 增 `companion_voice_provider`（默认 livekit）；`SessionService.create` 按 provider
   组装 grant（§2），token 签发按 provider 分派；
2. 把 `worker/entrypoint` 的「房间进程装配」收进 `VoiceTransport` 端口（建房间/进房/收发/断开），
   LiveKit 实现包装现有 `entrypoint.py`；这是 (c) 层的隔离墙，先只给 LiveKit 一家实现；
3. 下行统一走 §3 信封（与 tos 的 protocol codec 合并，不重复造）；
4. 容灾开关：`companion_voice_provider` 热切（新会话生效，在途通话挂断重呼即换线）——
   这是 MVP 的「零成本切换」形态；自动摘除（健康探测）后置。
5. 火山实现清单（P1）：房间 REST + token、音频流进/出、数据通道接信封、
   gateway 接线做 ASR/TTS、简单 VAD 规则做降级打断。

## 6. 验收

- 现网行为零变化（provider 恒为 livekit，灰度字段全默认）；
- 单测：Android provider 分发 + ViewModel 无具体类依赖；服务端 grant 形状 + provider 选择；
- 真机：现有 LiveKit 通话回归一遍；
- 切换演练（火山接入后）：改一个服务端配置 → 新通话走火山 → 挂断重呼回 livekit，
  客户端不发版。
