# 新对话页的接入点（待确认）

路由已就绪：`AgentChatRoute(sessionId, isRtl)`。当前没有挂到任何页面，首页与现有 Tab 未改动，便于单独评审。

接入时需要决定的两件事：
1. 入口放在哪：新增一个 Tab（改 `HomeTab` 与 `HomeScreen` 的分发），或在现有对话页里切换。
2. 会话 id 从哪来：先 `POST /api/agent/v1/sessions` 创建（`AgentApi.createSession`），拿到 id 后传给路由。

HomeScreen 当前的分发（参考）：`HomeTab.Chat -> ChatRoute`、`HomeTab.Companion -> CompanionRoute()`。
