# 角色离线兜底目录（agency-agents）

本目录的 `agency-agents-import.json` 是 14 个角色的服务端导入文件（8 个原样收录 + 6 个改编），同时也被 App 打包进 APK，用作角色选择器的离线兜底。

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
- 改编角色的来源文件（`adapted: true`）：
  - `marketing/marketing-xiaohongshu-specialist.md` → 小红书笔记助手
  - `marketing/marketing-douyin-strategist.md` → 短视频脚本助手
  - `marketing/marketing-wechat-official-account.md` → 公众号写作助手
  - `marketing/marketing-zhihu-strategist.md` → 知乎回答助手
  - `academic/academic-historian.md` → 历史讲解员
  - `academic/academic-narratologist.md` → 故事教练
- 许可证：MIT，见 https://github.com/msitarzewski/agency-agents/blob/b859c515d813efcfc4755d3471dd9e7686c7706d/LICENSE
- 版权声明：Copyright (c) 2025 AgentLand Contributors

MIT 许可证要求在副本中保留版权声明和许可声明。本目录的 JSON 中 `source` 字段保留了上述信息，分发时请一并保留。

## 原样收录与改编

- **原样收录（8 个，`specialized/` 下）**：`prompt` 是源文件去掉 frontmatter 后的英文正文，逐字保留。
- **改编（6 个，`adapted: true`）**：上游的营销类、学术类角色是写给品牌运营方和小说创作者的顾问
  （KPI、内容日历、世界观把关），直接放进面向个人用户的聊天里并不合适。这 6 个保留了上游的专业方法
  （例如短视频的「前 3 秒钩子」与合规红线，历史学家的「标注可信程度、区分事实与争议」，
  叙事学家的「主题—人物—节奏—伏笔」检查），把对象改成个人用户，用中文重写，不是上游原文。
  公众号、知乎两个只参考了上游文件的身份与使命部分，细节为本项目撰写。
- **未收录**：`academic/academic-psychologist.md`。它面向小说角色塑造，且没有任何危机干预约束；
  用户拿它倾诉自己的问题会错位。若要提供心理类角色，需要改写成「心理知识科普与自我觉察」，
  明确非诊断、非治疗、并带危机转介，建议产品或法务确认后再做。

## 文件结构

```
{
  "schema": "roles-catalog-import/v1",
  "source": { repo, commit, license, copyright, license_url, note },
  "divisions": [ { id, label, icon, color, count } ],
  "roles": [ { id, division, name, description, name_en, description_en,
               emoji, color, vibe, featured, source_path, prompt, adapted? } ]
}
```

- `id`、`division`、`name`、`description`、`emoji`、`color`、`vibe`、`featured` 与 `RolesParser` 解析的服务端字段同名。
- `name`、`description` 是给中文界面的显示文本（本项目新增）。
- `name_en`、`description_en` 是源文件 frontmatter 中的英文原文。
- `prompt`：原样收录的是源文件英文正文；改编角色是中文改写稿（见上）。
- `id` 统一加 `agency-` 前缀，避免与服务端已有角色冲突。
- `vibe` 保留英文原文。
- `source_path` 记录对应的源文件路径，便于核对。

## 导入方式

导入由后端管理员完成，App 不调用任何写接口。

1. 在后端角色库中按上述字段建立角色记录，`prompt` 作为系统提示词存储，服务端返回的摘要字段与 `RolesParser` 一致。
2. 为 `agency-*` 的 id 保持不变，端上离线选中的角色才能被服务端识别。
3. 导入后用 `/api/roles/v1/roles` 检查 14 个角色都能查到，并确认 `lang=zh` 时返回中文名称。

## 端上兜底行为

- 服务端分类或角色列表请求失败，或服务端返回 0 条时，才使用本文件。服务端结果始终优先。
- 兜底结果的 `RoleSummary.local` 为 `true`，UI 可据此标注"离线"。
- `featured` 为 `true` 的共 12 个：原样收录的 8 个，加上小红书笔记助手、短视频脚本助手、历史讲解员、故事教练；
  兜底时“精选”筛选只显示这 12 个。
- 选中兜底角色后，端上仍只把 `id` 发给服务端。如果服务端还没导入这些 id，会话将无法使用该角色，需要后端确认。
