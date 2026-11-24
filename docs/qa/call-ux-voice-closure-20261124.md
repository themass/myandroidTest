# 通话断连重试 UX + 语音输入真机交互 · 收口卡（2026-11-24）

按协调会话指令收口：**不做 adb install、不改版本、不用真实账号触发可能不可恢复的登出**。
主线核对点：`origin/weifu-refactor` `f4ea577f`（本卡含测试提交）。设备：V2279A 已离线（见 `s2-ps4-ready-to-run-20261124.md`），
所以本轮把能钉的全钉进单测，真机项列明确 BLOCKED。

## 一、通话断连重试 / 未登录 401 UX

### 代码实现（主线已有，逐处取证）

| 行为 | 位置 |
|------|------|
| 横幅只在 ENDED 出现 | `feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionCallScreen.kt:510` |
| 文案分流：401→「请先登录，再开始通话」，否则 `statusHint` 或「连接失败或已断开」 | 同文件 `:512` |
| 「去登录」/「重新连接」互斥（`loginRequired` 走前者，否则后者） | `:2603–2634`（`CallDisconnectedBanner`，`if (loginRequired && onLogin != null)` / `else if (onRetry != null)`） |
| 401 → `callLoginRequired = true`（建会话异常按 statusCode 判定） | `CompanionViewModel.kt:158`（`catch` 里 `(e as? CompanionApiException)?.statusCode == 401`） |
| 「重新连接」回调在 401 时置 null | `CompanionRoute.kt:547` |
| 点「重新连接」= 干净断开旧房间 + 按同一角色/case 重建 | `CompanionViewModel.kt:573`（`retryConnection`） |
| 断线瞬间波形/呼吸环归零（不留最后一帧） | `CompanionRoute.kt:499–507`（`audioLevel`/`selfLevel`/`selfBands` 在非 CONNECTED 归零） |
| DISCONNECTED 归 ERROR 而非 IDLE（要显示原因） | `CompanionViewModel.kt:608–630`（`observeConnection`） |

### 本轮补的测试收口（commit `f4ea577f`，`feature-companion` 7 条 + `chatkit` 3 条全绿）

`feature-companion/.../CompanionCallStateTest.kt` 新增三条，把原先只能上真机才能看的三条分支钉死：

1. `a 401 at session creation marks loginRequired instead of a plain disconnect`
   —— 假 API 建会话抛 401 → `connection == ERROR` **且** `callLoginRequired == true`。
   这条锁住「横幅走『去登录』、不出现『重新连接』」的唯一判据；真机验它要 logout，凭证不可恢复，**永远不该靠真机**。
2. `a non-401 start failure keeps loginRequired false so the retry button applies`
   —— 503 不落登录分支（反向锁，防「所有错误都当未登录」的回归）。
3. `retry after disconnect rebuilds the same room and the error state clears`
   —— 断线 → ERROR → `retryConnection()`：`disconnect` 被调用恰好 1 次（不泄漏旧 Room），重建连上后 ERROR 解除、`callLoginRequired` 清掉。
   即断线横幅的整条点击链路到传输层边界为止不需要真机、不烧额度。

配套：`FakeCompanionApi` 加 `sessionFailure` 旋钮（`CompanionViewModelTest.kt:1465`、`:1486–1494`），
`RecordingTransport` 加 `disconnectCalls` 计数（`CompanionCallStateTest.kt:160+`）。

原有覆盖仍在：`room reconnecting and disconnecting reach the ui instead of staying connected`
（RECONNECTING/DISCONNECTED 都到界面）、`HttpCompanionApiTest.no token fails as 401 before any request is made`
（无 token 连请求都不发）、`a failed confirmation is shown as an error instead of silently succeeding`。

### 真机缺口（BLOCKED，不自行触发）

| 项 | 状态 | 为什么 BLOCKED | 解锁条件 |
|----|------|----------------|----------|
| 401 横幅（文案 + 「去登录」跳转 + 无「重新连接」） | **BLOCKED** | 需 logout；themass 凭证不在验收会话手里，可能不可恢复，会连带废掉拍照解题/LiveKit 额度账号 | 用户当面授权且确认能重新登录；或做一个只影响通话会话的假 token（建议后者，端上可测） |
| 断线横幅出图 + 点击真的重新进房 | **BLOCKED（设备）** | V2279A 离线；且进房烧 LiveKit 额度 | 设备回线 + 下次装含最新 tip 的包，与 happy-fermi 旋转修复验（`040f580c`）同车 |
| 断线瞬间波形/呼吸环回落 | **BLOCKED（设备）** | 是渲染层，单测无法覆盖 Compose 绘制；adb 也测不出动画 | 同上，肉眼验收（可配合录屏） |

## 二、语音输入完整真机交互

链路（**live 路径是 chatkit + feature-chat + speech**，`AgentChatScreen.kt` 里那套是无语音的遗留壳）：

| 环节 | 位置 | 本轮真机证据（1.1.0.72，V2279A） |
|------|------|--------------------------------|
| 「按住 说话」/「语音输入」↔「键盘」切换 | `chatkit/.../input/VoiceControls.kt:175`、`ChatComposer.kt:211–219` | **PASS**：点 `语音输入` 后按钮变 `键盘`、主键变「按住 说话」（uiautomator dump 前后对照） |
| 按住录 → 松手发 | `VoiceControls.kt:131–154` → `ChatComposer.kt:191–198` → `feature-chat/.../VoiceActions.kt` | **PASS**：两条 2″ 语音连发出气泡（`voice-ui-evidence-20261124/V1`、`V2`） |
| ASR 结果合并 / 无密钥降级 | `VoiceActions.kt:162`（气泡已存，识别失败只停在气泡）；`chatkit/.../message/parts/VoiceParts.kt:121–124`（转文字中… / 文本 / 「未识别到文字」） | **PASS**：第一条「未识别到文字」（无密钥路径）、第二条 ASR 出「不见了」，AI 正常接话 |
| 录音前进录音器静音窗口（马达声不入库） | `chatkit/.../input/VoiceBars.kt:151`（`vibrateTick`）、`speech/.../VoiceRecorder.kt:44–53`（`muteFor`，默认 250ms，`:95–97` 同时罩写文件与实时 ASR 两路） | **无法机判**：adb 采不到震动与马达噪音，听感归用户 |
| 失败态可见（发送失败条 / 待发重试） | `ChatUiState.error` → `ChatScreen.kt:274–279`；`DeliveryStatus{SENT,SENDING,FAILED}`；`HistoryActions.kt:56–70`、`AttachmentSender.kt:117–121`、`ResendActions.kt:23–36`、`LocalAttachmentKeep.kt:29–42` | **本轮未跑**（Task 5 需制造上传失败 + 杀进程，与窗口排队冲突） |

### 本轮补的测试（commit `f4ea577f`）

`chatkit/src/test/java/com/openapi/chatkit/input/VoiceIntentTest.kt` 钉 `intentFor()` 三种样式的去向阈值
（此前完全无测试，Floating 的「左滑取消 / 右滑转文字」写反只能靠人肉点出）：
Ripple 上滑 >80dp 取消；Floating 左取消 / 右转文字 / 居中发送；Bar 左滑取消。

### 发现的两个行为缺口（登记，不当场改）

1. **不足 1 秒的录音被静默丢弃**：`VoiceRecorder.stop()`（`:115–129`）删文件返回 null，
   `VoiceActions.kt:150` 直接 `return@launch`——用户误触后**没有任何提示**，不知道是没录上还是被吞了。
2. **ASR 失败的语音气泡没有重试路径**：语音行按默认 `delivery = SENT` 存
   （`ChatMessage.kt:128`），所以它既不是 FAILED、也没有重试入口（`ResendActions.retry` 只在
   `text.isNotBlank()` 时重新 stream）。气泡留着但转文字失败，用户无路可走。

这两条是行为问题不是测试问题，需要产品/交互先定口径（要不要提示、重试是重发音频还是只重跑 ASR）。

### 真机缺口（BLOCKED）

| 项 | 状态 | 解锁条件 |
|----|------|----------|
| Task 5 杀进程后失败附件仍可见 | **BLOCKED（设备）** | 设备回线，专项窗口：制造上传失败（断网）→ 杀进程 → 复启看 FAILED 条与重试 |
| Task 7 录音震动无马达噪音 | **BLOCKED（不可自动化）** | 只能人耳/录像判定；建议装机验收时由用户判 |
| 长按切三种语音样式（Ripple/Floating/Bar）的手感与滑动手势 | **BLOCKED（设备）** | 手势几何已钉单测；屏上实际反馈需真机 |
| ASR 真机字幕（火山密钥） | **BLOCKED（凭证）** | `local.properties` 两行由用户本人填，不代写 |

## 三、顺手发现：既有单测在 tip 上红

`feature-companion/.../dh/EngineAdaptersAvailabilityTest.live2d_without_cache_is_unavailable_with_download_reason_and_menu_still_switches`
失败：`expected NOT_READY_START_DOWNLOAD but was UNAVAILABLE_REASON`。

- 与本轮改动**无关**：把本轮改动 stash 掉后在本地旧 tip 上跑同样红；rebase 到 `origin/weifu-refactor` 后仍红。
- 本会话**未动**它（不在我的收口范围）。跑全套两模块单测：`total=593 fails=1`，唯一失败就是这条。
- 需要 dh/engine 线的人认领：看起来是 `482a6716 fix(companion): recheck engine availability before switching stages` 一系改了可用性判定，测试期望没跟上（也可能是反向）。

## 四、结论一句话

通话断线重试与 401 两条分支的**判据已全部有单测**（`f4ea577f`，两模块 593 条中唯一失败是既有的 dh 那条，非本线）；
剩余项全是**必须真机或必须凭证**的，逐条 BLOCKED 写清解锁条件，不擅自 logout、不擅自烧额度、不装机。
