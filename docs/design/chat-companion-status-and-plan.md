# 对话与陪伴两条链路的现状澄清与重构计划

日期：2026-11-15　状态：问题澄清完成，待确认后开工

这份文档先把「现在是怎样」写清楚，再列计划。原因是这轮反馈里有一半的问题，
根因不在界面，而在**两条链路本身就指向不同的后端**，以及**一个接口现在是坏的**。

---

## 一、两条链路分别打到哪

| 页面 | 客户端入口 | 传输 | 服务端 | 鉴权 |
|---|---|---|---|---|
| 「对话」Tab | `ChatRoute` → `ChatRepositoryImpl` | HTTP + SSE | **Java testserverV**（`/chat/stream.json`） | 老 `BaseRequest` 的 UA/Token |
| 「陪伴」Tab | `CompanionRoute` → `LiveKitCompanionTransport` | LiveKit 房间（数据流 + 音轨） | **Python companion-agent**（`/v1`） | Bearer 登录态 |

链路详情：

- chat：`https://api.ok123find.top/vvv/api/chat/stream.json`
  → nginx `location ^~ /vvv/api/chat` → `upstream chat_server` → **`innerip:8888`**
  （`innerip` 在 nginx 机器上解析成 `127.0.0.1`，即 nginx 本机上的 Java 进程，pid 22372）
- 陪伴：`https://api.ok123find.top/v1/...` → `upstream ca_api` → `104.160.191.87:8081`（Python）

**已经写好但进不去的第三条**：`AgentChatEntry`（走 `/v1`，带记忆/提醒）在 `HomeTab` 里有枚举，
但底部栏只渲染 `Chat / Companion / Profile`，抽屉里也已经在 `53ab9ef6` 被摘掉。
所以现在**没有任何入口**能到达 `/v1` 的文字对话。它不是当前问题的一部分，但影响了「该往哪收敛」的判断。

---

## 二、逐条澄清

### 1. chat 发消息失败概率很高，不知道哪失败、为什么

**已确认的部分**

- chat 打的是 **Java SSE**，不是 `/v1`。
- 未带有效登录态时，`POST /chat/stream.json` 返回
  `{"errno":10000,"error":"System error，please try again"}`，
  Java 侧日志是 `java.lang.RuntimeException: welcome`
  at `UserCheckHandlerInterceptor.preHandle(UserCheckHandlerInterceptor.java:44)`。
  即登录校验把请求拦下，但抛的是一个业务方看不懂的异常。
- `chat_server` 与 `chat_server1` 两个 upstream 都存在，`/vvv/api/chat` 走的是
  `chat_server`（`innerip:8888`），`/vvv/api/chat/ai/local` 与 `ai/file.json`/`ai/ocr.json`
  走 `chat_server1`（`104.160.191.87:8888`）。**同一个功能横跨两个实例**。

**还没确认的部分（不猜）**

失败那一次的真实信号我还没拿到。要定位必须同时抓两样：

1. 真机上 `adb logcat` 里 `ChatStreamClient` 的 `onFailure` 或非 2xx 分支；
2. 同一时刻 Java 侧 `app.log` 里对应的 `error url=/api/chat/stream.json`。

已知界面对用户**几乎没有提示**：`ReplyStreamer` 把错误写进 `state.error`，
占位气泡撤回，用户只看到「回复失败，请重试」这类一句话，看不到 HTTP 码、也看不到服务端原因。
**所以「不知道哪里失败」本身就是需要修的问题**——这是第 1 条里最确定的部分。

### 2. 切角色混乱，选了「客服专员」回答的不是客服

**根因已确认，是两段 id 对不上，叠加一个坏掉的接口。**

1. 角色目录接口现在是**坏的**：
   `GET /vvv/api/roles/v1/roles` 与 `/divisions` 都返回
   `400 {"code":400,"message":"Name for argument of type [java.lang.String] not specified,
   and parameter name information not available via reflection. Ensure that the compiler uses
   the '-parameters' flag."}`
   → 服务端 `RoleController` 用了 `@RequestParam(defaultValue="en") String lang`，
   但编译时没开 `-parameters`，Spring 拿不到参数名。
   → 客户端 `RolesRepositoryImpl` 在服务端失败时**静默退回 APK 里打包的离线目录**，
   所以选择器看起来正常，实际用的是另一份数据。

2. 两份目录的 id 空间完全不同：

   | 来源 | id 样例 |
   |---|---|
   | APK 打包（`feature-chat/src/main/assets/personas/agency-agents-import.json`） | `agency-customer-service` |
   | 服务端（`vvv-roles/src/main/resources/roles/catalog-zh.json`，283 条） | `academic-anthropologist` |

   在服务端 `catalog-*.json` 里 `grep -c agency-customer-service` = **0**。

3. 于是「客服专员」发出的是 `roleId=agency-customer-service`，
   服务端按文档「有 roleId 但目录查不到 → 记 error，回退旧人设 → 再回退默认」处理
   → **回答的是默认助手，不是客服**。

   这不是"切换逻辑写错了"，是**选中的角色在服务端根本不存在**。

**顺带**：`switchTo()` 的会话匹配、`enter()` 的状态落库这几段逻辑本身是完整的，
有 `RoleSessionSwitchTest` 覆盖。它做的是「换角色 = 换会话」，方向对；
坏的是喂给它的角色 id 是错的。

### 3. AI 回复的 footer 去掉收纳效果，平铺展示

`chatkit/message/MessageFooter.kt` 里 `BoxChip` + `AnimatedVisibility(expandHorizontally)`
把按钮收进一个小盒子，点击才横向展开。改成常驻平铺。

### 4. 内容条目显示头像，小一点

`ChatMessageItem` 已经有一个 `avatar` 插槽参数，但**函数体里从未调用它**
（`Row` 里直接是 `Column { ... }`），`ChatMessageList` 默认传 `{}`。
注释写着「App 不再传（消息不显示头像）」——这是当初主动去掉的，现在要加回来，
并且只能显示助手侧的头像（用户自己的头像不显示，避免两列都占宽度）。

### 5. 陪伴页整体重构

现状逐条对照：

| # | 用户说的 | 代码位置 | 判断 |
|---|---|---|---|
| 1 | toolbar 和 chat 不一样 | `CompanionRoute` 自绘 `TopAppBar`（只有三个图标），没接抽屉、没有角色入口 | 属实，要统一 |
| 2 | 「文字/语音」切换没意义 | `CompanionScreen.ModeToggle` + `SingleChoiceSegmentedButtonRow` | 属实，这不是用户要的开关 |
| 3 | 应该能选「语音输出」还是「文字输出」 | 现在 `CompanionMode` 是 TEXT/VOICE 二选一，语义是"进不进语音房间"，不是"怎么回" | 概念错位，要拆成两个独立维度 |
| 4 | 内容区抄袭 chat | 陪伴消息是自绘 `MessageBubble`，不是 chatkit | 属实，应复用 chatkit |
| 5 | 等待样式太丑 | 无「正在输入」指示 | 属实 |
| 6 | 消息上面那个大框是什么 | `Box(Modifier.weight(0.42f))` 的 `SimpleAvatar` 形象区，占屏 42% 且常驻 | 是形象区，设计上应该可收起 |
| 7 | 想要豆包「电话」那种通话效果 | 无 | 新功能 |

**这里有一个概念必须先拆开，否则怎么做都是错的：**

现在 `CompanionMode`（TEXT / VOICE）混了三件事：

- **传输**：文字走数据流，语音要进 LiveKit 房间（这是技术细节，用户不该看见）
- **输出形式**：她说的话是「文字气泡」还是「语音条 + 文字」
- **界面形态**：普通聊天页 / 全屏通话页（豆包那种）

建议拆成：

- `outputMode: TEXT | VOICE` —— 她要**怎么回**（语音时同时出语音条 + 文字）
- `callActive: Boolean` —— 是否在全屏通话形态（点电话按钮进，不是常驻）

传输由这两个自动决定：只要需要语音（outputMode=VOICE 或 callActive），就必须在房间里；
纯文字也可以在房间里（复用同一条 LiveKit 通道），但**不必**——这是实现选择，见计划。

---

## 三、计划

按「先修坏的，再改丑的，最后加新的」排序。每项可独立交付。

### T1　修角色目录接口（问题 2 的前置）

- 给 `vvv-roles` 加 `-parameters` 编译参数（Maven `maven-compiler-plugin` 的 `<parameters>true</parameters>`），
  重新构建部署 testserverV 的 roles 模块。
- 验证：`GET /vvv/api/roles/v1/divisions` 与 `/roles` 返回 200 + 数据。
- 风险：要重新编译 Java 服务，会重启 8888 进程（影响 chat 全部接口）。**需要你确认窗口**。

### T2　让客户端的角色 id 与服务端对齐（问题 2 的主体）

- 目录恢复后，客户端应优先用服务端目录；**服务端失败时不再静默换一份数据**，
  而是明确提示「角色列表加载失败」，避免出现「列表里的角色服务端不认识」。
- 若短期内不能改 Java：把 APK 打包目录替换成服务端 `catalog-zh.json` 的子集，
  保证 id 同源。这一条**不需要动服务端**，是更安全的先行方案。

### T3　chat 失败可见（问题 1 的可修部分）

- `ReplyStreamer` 失败时保留错误原文（HTTP 码 / `errno` / 服务端 `error` 字段），
  界面给一个可展开的「详情」，而不是一句「请重试」。
- 加一条结构化日志：`sessionId / roleId / httpCode / errno / 耗时 / 是否走到 done 帧`。
- 这一步做完，**再复现一次**就能拿到 T4 需要的证据。

### T4　定位 chat 高失败率（问题 1 的根因）

- 用 T3 的日志在真机上复现 N 次，对齐 Java 侧 `app.log`。
- 已知候选：登录态过期（`RuntimeException: welcome`）、两个 upstream 实例不一致、
  流式超时、`chat_server` 单点（`innerip` 就是 nginx 本机，挂了就全挂）。
- **做完 T3 才有资格下结论**，现在不做承诺。

### T5　chat 界面微调（问题 3、4）

- `MessageFooter`：去掉 `BoxChip` 收纳，改成一行平铺。
- `ChatMessageItem`：接上 `avatar` 插槽，助手侧显示 28–32dp 小头像；
  用户侧不显示。只在「连续同一人」的第一条显示，避免刷屏。

### T6　陪伴页概念拆分 + 复用 chatkit（问题 5.1–5.6）

- 顶部统一：与 chat 同样的 `TopAppBar`（抽屉 + 角色 + 更多）。
- 去掉「文字/语音」SegmentedButton，换成：
  - 输入框（与 chat 同款 composer）
  - 一个「语音输出」开关（扬声器图标，开 = 她回语音条 + 文字）
  - 一个「通话」按钮（进全屏通话页）
- 消息区换用 `chatkit` 的 `ChatMessageList`，删掉自绘 `MessageBubble`。
- 「正在输入…」：在等待首字时显示打字指示器（三点动画）。
- 形象区（那个 42% 的大框）改成**可收起**：默认收起成顶部一个小圆头像，
  需要时展开成全屏形象。它是问题 5.6 里那个"不知道是什么的大框"。

### T7　全屏通话页（问题 5.7，新功能）

- 独立页面：进入时建/复用一个 LiveKit 房间。
- 两种形态：
  - 关摄像头：动态音频波形/心跳环（响应真实音频电平），中间是她的头像；
  - 开摄像头：本地画面小窗 + 她的形象/画面，可做画面识别。
- 底部：静音、挂断、（可选）文字输入浮层。
- 这一项最大，建议单独排期，先把 T6 做完。

---

## 四、需要你决定的

1. **T1 能不能重启 Java 服务（8888，chat 全部接口）？** 能的话角色目录从根上修好；
   不能的话走 T2 的替代方案（只在客户端对齐 id），先止血。
2. **T6 里「语音输出」和「通话」是不是你想要的两个东西？** 我的理解：
   语音输出 = 她在聊天页里回语音条；通话 = 全屏那套。请你确认，这决定 T6 的输入区怎么摆。
3. **T7 现在做还是排在 T6 之后？**

---

## 五、没被问到但同源的问题（记录，不扩大范围）

- `/roles/v1/*` 的 400 说明 Java 服务**整体没有开 `-parameters`**，
  其他用 `@RequestParam`/`@PathVariable` 不带显式名字的接口可能同样坏着。
  T1 一并解决。
- `chat_server` upstream 是单点 `innerip:8888`（nginx 本机），
  另外两个被注释掉了。chat 是当前的主对话链路，这个单点需要单独评估。
