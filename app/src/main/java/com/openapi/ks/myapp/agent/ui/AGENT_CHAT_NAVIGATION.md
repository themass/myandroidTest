# 新对话页的接入点

新对话页（`/api/agent/v1`）不再有独立入口，已经并进首页抽屉「常用」里的「陪伴」：

- `HomeTab.Companion` → `CompanionRoute(chatContent = { 角色, 形象状态回调 -> AgentRoleChat(...) })`。
- 上面是形象（`feature-companion` 的 `SimpleAvatar`，下载的形象包优先、内置默认兜底），下面是 `AgentRoleChat`：
  当前角色的会话，没有就创建；换角色自动换会话；右上「新对话」重开一段。会话 id 只在本地按角色记一份。
- 角色列表、「她记得的」（事实与事件）、设置仍是陪伴页自己的页面。记忆与事件在合并后改读新服务
  （`HttpCompanionApi(roleIdProvider=…)`），按当前角色读写，和对话里记下的是同一份。
- 语音通话：陪伴页顶部的电话图标进入 `CompanionPage.VOICE`，仍是旧的 LiveKit 链路。
  **它的对话历史和记忆与文字对话是分开的**（服务端语音 worker 还没迁到新服务）。
- 待确认的动作（例如「明天 15:00 提醒你开会」）显示在对话上方，点确认才执行。

没有传 `chatContent` 时，`CompanionRoute` 保持旧行为（旧服务、LiveKit 对话）。
