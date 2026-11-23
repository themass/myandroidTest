# 角色与对话重构设计

## 目标

1. 引入 [agency-agents](https://github.com/msitarzewski/agency-agents)（MIT）的约 280 个专业角色，让用户按领域挑选，而不是面对一个很短的扁平列表。
2. 角色与对话各有独立模块，服务端和客户端都新增，**旧接口一个都不改**，老版本客户端继续可用。
3. 对话页的布局和视觉重做：角色成为对话的一等公民（头部、空状态、会话入口），而不是顶部一个下拉。
4. 为之后的 LiveKit agent 通话留好位置：角色就是 agent 的人设来源，通话页直接读同一份角色目录。

## 非目标

- 不翻译 280 份完整提示词。只翻译展示用的名称和简介（分批做，缺失时回退英文）。
- 不改动旧的 `setting_character` 表、`/user/setting/character*.json`、`characterId` 字段。
- 不做角色商店、评分、付费角色。

## 总体结构

```
agency-agents (MIT, 源数据)
        │  tools/roles/build_catalog.py（可重复生成，离线运行）
        ▼
vvv-roles/src/main/resources/roles/catalog.json      英文原文压缩后的提示词 + 元数据
vvv-roles/src/main/resources/roles/catalog-zh.json   中文名称/简介覆盖（可缺省）
        │
        ▼
服务端 vvv-roles 模块（AutoConfiguration，参照 vvv-poetry）
   GET /api/roles/v1/divisions
   GET /api/roles/v1/roles?division=&q=&featured=&limit=&offset=
   GET /api/roles/v1/roles/{id}
        │
        ▼
客户端 com.openapi.roles 包（OkHttp 直连，不走 Volley 加密层）
   RolesRepository → RolePickerScreen / RoleHeader
        │
        ▼
对话：/chat/stream.json 新增可选字段 roleId（字符串）
   服务端 getPrompt：roleId 命中目录 → 用目录提示词；否则走 characterId；再否则走旧逻辑
```

## 关键决定

| 决定 | 理由 |
|---|---|
| 新建 Maven 模块 `vvv-roles`，不放进 `vvv-link` | 和 `vvv-poetry` 同样的隔离方式，目录与旧对话无依赖，可单独测试 |
| 目录随 jar 发布（classpath JSON），不进 MySQL | 角色是只读内容，不需要手工跑 SQL 迁移；更新角色等于发版，可回滚 |
| 路径不带 `.json` | 旧拦截器只匹配 `/**/*.json`（登录、版本、黑名单、加密）。目录是公开只读内容，不需要这些 |
| 用户自建角色仍用旧接口 | 不迁移用户数据；新界面把"我的角色"和目录并排展示，来源不同、调用不同 |
| 对话字段 `roleId` 是新增的字符串字段 | 旧 `characterId` 是整数，不能复用；老客户端不传 `roleId`，行为不变 |
| 提示词在入库时压缩 | 原文中位数约 14.5 KB，最大 34 KB。每条消息都带 system prompt，直接用会放大成本和延迟。只保留身份、使命、关键规则、沟通风格，丢掉长代码示例 |
| 角色提示词不再追加旧的"不使用代码块" | 工程类角色需要代码块；客户端 Markwon 能渲染代码块 |
| 角色正文不经过 `String.format` | 角色文本可能含 `%`，旧写法会抛异常 |

## 许可

agency-agents 使用 MIT 许可。目录文件旁放 `NOTICE`，保留原版权声明和许可文本。

## 分阶段

1. 生成脚本 + 目录 + 服务端模块 + 单测。
2. 对话接入 `roleId`（服务端）。
3. 客户端 `com.openapi.roles`：数据层、选择页、对话头部。
4. 对话页视觉重做，沿用 `AppColors` / `AppTypography`。
5. 中文名称与简介分批补齐。
6. 之前推迟的重构（见 `docs/` 之外的计划，单独推进）。

## 留给实现的判断

- 精选（featured）角色的初始名单：按受众挑选，之后可调。
- 压缩时每段长度上限：先用 1500 字符，抽样检查后再调。
