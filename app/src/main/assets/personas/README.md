# 角色离线兜底目录（agency-agents）

本目录的 `agency-agents-import.json` 是 8 个专业角色的服务端导入文件，同时也被 App 打包进 APK，用作角色选择器的离线兜底。

## 来源

- 仓库：https://github.com/msitarzewski/agency-agents
- 读取的提交：`b859c515d813efcfc4755d3471dd9e7686c7706d`（读取时 `main` 分支 HEAD，通过 GitHub API 获取，提交时间 2026-11-06）
- 文件（均位于仓库 `specialized/` 下）：
  - `customer-service.md`
  - `healthcare-aging-parent-care-companion.md`
  - `hospitality-guest-services.md`
  - `hr-onboarding.md`
  - `language-translator.md`
  - `recruitment-specialist.md`
  - `resume-tailor.md`
  - `study-abroad-advisor.md`
- 许可证：MIT，见 https://github.com/msitarzewski/agency-agents/blob/b859c515d813efcfc4755d3471dd9e7686c7706d/LICENSE
- 版权声明：Copyright (c) 2025 AgentLand Contributors

MIT 许可证要求在副本中保留版权声明和许可声明。本目录的 JSON 中 `source` 字段保留了上述信息，分发时请一并保留。

## 文件结构

```
{
  "schema": "roles-catalog-import/v1",
  "source": { repo, commit, license, copyright, license_url, note },
  "divisions": [ { id, label, icon, color, count } ],
  "roles": [ { id, division, name, description, name_en, description_en,
               emoji, color, vibe, featured, source_path, prompt } ]
}
```

- `id`、`division`、`name`、`description`、`emoji`、`color`、`vibe`、`featured` 与 `RolesParser` 解析的服务端字段同名。
- `name`、`description` 是给中文界面的显示文本（本项目新增）。
- `name_en`、`description_en` 是源文件 frontmatter 中的英文原文。
- `prompt` 是源文件去掉 frontmatter 后的英文正文，逐字保留，未做修改。
- `id` 统一加 `agency-` 前缀，避免与服务端已有角色冲突。
- `vibe` 保留英文原文。
- `source_path` 记录对应的源文件路径，便于核对。

## 导入方式

导入由后端管理员完成，App 不调用任何写接口。

1. 在后端角色库中按上述字段建立角色记录，`prompt` 作为系统提示词存储，服务端返回的摘要字段与 `RolesParser` 一致。
2. 为 `agency-*` 的 id 保持不变，端上离线选中的角色才能被服务端识别。
3. 导入后用 `/api/roles/v1/roles` 检查 8 个角色都能查到，并确认 `lang=zh` 时返回中文名称。

## 端上兜底行为

- 服务端分类或角色列表请求失败，或服务端返回 0 条时，才使用本文件。服务端结果始终优先。
- 兜底结果的 `RoleSummary.local` 为 `true`，UI 可据此标注"离线"。
- 当前 `featured` 全部设为 `true`，即兜底时“精选”筛选会显示全部 8 个。
- 选中兜底角色后，端上仍只把 `id` 发给服务端。如果服务端还没导入这些 id，会话将无法使用该角色，需要后端确认。
