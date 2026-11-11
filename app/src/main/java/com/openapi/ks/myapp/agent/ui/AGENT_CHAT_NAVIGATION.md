# 新对话页的接入点

入口：首页抽屉「测试」分组里的「角色对话（新）」（`HomeTab.AgentChat`），与「频道」并列。首页底部标签未改动。

- `AgentChatEntry`：先选角色（内置 `linche`、`sudai`），再进入该角色的会话。
- 会话 id：每个角色记住最近一次的会话（`AgentSessionMemory`，SharedPreferences，只存 id）；没有就 `POST /api/agent/v1/sessions` 创建。顶栏「新对话」为当前角色开一段新会话。
- 每个角色的记忆在服务端互相独立。

稳定后再决定是否用它替换底部的「对话」标签。
