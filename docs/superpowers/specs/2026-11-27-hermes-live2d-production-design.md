# Hermes 驱动的 Live2D 角色资源生产工作台

> 日期：2026-11-27
> 状态：可行性设计提案，未实现；Hermes 的远程集成边界仍需 P0 验证。
> 目标读者：产品、Agent/后端工程、美术与 Live2D 产线维护者。

## 1. 目标与背景

建设一个可部署在远程服务器上的“角色创作与运营工作台”。用户以文字、参考图和约束描述角色，工作台将需求拆解为可追踪的生产任务；专职 Agent 协助整理设定、检查交付、解释错误和组织复核；确定性工具负责图层清点、清单校验、Layout 生成、导出检查和模型包版本化。每个阶段产物可预览、可追溯、可重试，关键门槛由用户或有权限的制作人员确认。

第一阶段交付的是原创、可验收的 Live2D 模型包与发布记录。后续才将通过验收的角色接入现有 Android/陪伴产品，最终模型的对话和语音运行时继续使用产品自己的 Companion Agent / LiveKit 通道。制作岗位 Agent 与最终发布的数字人是两个不同对象：前者完成生产工作，后者才面向终端用户交互。

“herms”按此前讨论理解为 NousResearch Hermes Agent。本文选择 Hermes 作为受控生产任务的 Agent 执行器候选，不假设它单独提供完整美术管线、Cubism 自动绑定能力或生产级多租户 API。

## 2. 成功标准

1. 用户能创建角色项目，提交文字 Brief 与参考素材，并看到每个阶段的状态、负责人、产物和待确认事项。
2. 系统能对已分层 PNG + `source.json` 执行可重复的结构检查、Layout 生成和模型导出检查；失败可从失败阶段重试，不覆盖已验收版本。
3. 设定稿、分层源文件、Live2D 工程和导出模型均有项目、版本、哈希、许可来源及验证记录。
4. 未通过人工复核和真实 Cubism runtime 验收的模型不能进入 `production` 或客户端可见目录。
5. 原创内容来源、生成模型/API、参考图授权、Live2D SDK/Editor 发布条款均可审计；身份、素材访问和发布操作有权限边界。
6. 系统故障、Hermes 进程中断或工作站离线后，任务状态仍可恢复，已完成产物不会丢失。

## 3. 范围与非目标

### 本期目标

- 角色项目、版本和持久化任务状态。
- 文字/参考图 Brief、角色设定、分层美术交付与验收记录。
- Hermes Agent 执行和岗位化提示/技能配置的可行性验证。
- 确定性 Live2D 资产工具链的任务封装与产物留存。
- 用户确认、制作复核、授权元数据、预览和发布门禁。
- 一套可部署的远程 API/工作台，支持一个受控生产团队先行使用。

### 不承诺

- 输入任意照片或描述后，一键自动生成可直接商用、具有专业绑定质量的 `.moc3`。
- Hermes 自身绘图、分层、补全遮挡、Cubism Rig 或商业许可能力。
- Cubism Editor MCP 现阶段已支持无桌面远程部署。当前证据只证明在受控环境完成过 Editor MCP 流程。
- 多租户 SaaS 的强隔离、容量、SLA、计费和公网开放能力已验证。
- 自动批准发布、自动绕过 Live2D runtime 验收，或把结构自检当作视觉验收。
- 本期改造 Android 聊天 UI、LiveKit 语音链路或现有角色渲染协议。

## 4. 方案选型

| 方案 | 做法 | 优缺点 |
|---|---|---|
| A. Hermes 独立承担产品后端 | 直接以 Hermes Gateway/消息入口接用户请求、保存项目并运行产线 | 起步轻；但公开文档未证明稳定的产品 API、账户/租户隔离、任务状态契约和审计能力，不适合作为当前产品边界。 |
| **B. 自有工作台编排 + Hermes 执行器（推荐）** | 自有 API/数据库持有身份、任务状态、文件权限、审批和发布；将限定任务交给 Hermes 进程/worker；确定性工具由受控工具服务执行 | 能复用 Hermes 的 Agent/技能/MCP 能力，同时让产品控制面可替换、可审计；要额外实现任务适配器与隔离，且先验证 Hermes 调用和恢复模式。 |
| C. 不用 Hermes，全部确定性工作流 | 自有状态机直接调工具，模型只用于设定文本或图像 provider | 行为最可控、易测试；复杂创作协助较弱。作为故障降级和后续替代路径保留。 |

选择 B 作为提案。Hermes 适合承担“围绕明确任务调用工具、整理上下文并形成交付说明”的工作，不应当成为项目事实、权限授权或发布状态的唯一持久化来源。若 P0 无法以可维护的方式从 API 启动并约束 Hermes，保持控制面与工具层不变，改用自建 Agent loop 或方案 C。

### 试点默认假设

- P1 面向一个内部制作团队/单一组织，不开放公众注册，也不承诺 SaaS 多租户；数据模型保留 `owner/team`，但首版授权只需组织内项目成员。
- 身份通过可替换的 OIDC/现有账号适配器接入；只有在核实目标服务的 token 验签和稳定用户 subject 后才复用现有 Companion Agent 登录态。工作台不依赖 Companion Agent 作为在线运行前提。
- API、worker 和数据层按独立服务部署；仅在实际服务器已有且已审计的基础设施可满足接口、安全和保留要求时复用 PostgreSQL/Redis/对象存储。否则新增服务仍保持相同契约，不将 companion-agent 的代码状态误当成此仓库已有能力。
- 首版 release candidate 是内部审核包：Cubism 导出文件（如 `model3.json`、moc3、贴图、动作/表情配置）及 manifest、哈希、QA 证据和授权记录；不规定外部 CDN/应用商店协议。发布审批必须由非该版本发起人完成；Brief 和概念稿确认可以由同一创作者完成。
- 在明确个人素材保留期限、备份删除时限和审计保留规则前，P1 仅使用内部授权或合成测试素材，不面向公众收集个人参考图。数据策略获业务/法务批准后，产品 UI 需展示对应期限并支持项目删除；备份在约定到期周期内清除，审计只保留最少必要元数据。

## 5. 系统边界与拓扑

```text
用户 / 制作团队
      │ HTTPS：项目、上传、确认、预览
      ▼
工作台 API + Web UI（自有控制面）
  ├─ Auth / 项目权限 / 确认与发布授权
  ├─ Workflow DB：项目、任务、阶段、审计、版本
  ├─ Queue：可重试任务、租约、超时与取消
  └─ Artifact API：签名上传/下载、哈希、保留策略
      │ 领取 task envelope（项目身份由服务端注入）
      ▼
Hermes Worker（每任务/项目受限执行上下文）
  ├─ 角色导演 Agent：澄清 Brief、生成角色设定草案
  ├─ 产线协调 Agent：分解任务、汇总 QA 证据、提示下一步
  ├─ 资产检查 Agent：解释确定性检查结果，不替代检查程序
  └─ 仅暴露任务允许的 MCP/工具
      ├─ Image Provider Adapter（可替换的受许可图像生成服务）
      ├─ Asset Tools：清单校验、Layout 生成、导出扫描/哈希
      └─ Cubism Executor：受控美术工作站或人工打开 Editor 完成 rig/export
      │
      ▼
隔离对象存储：brief / source layers / cmo3 / moc3 package / evidence
      │ 通过门禁后的版本引用
      ▼
发布登记 → 测试分发/Android 资源入口（后续单独接线）
```

### 控制面与 Agent 的职责

- API 是身份、权限和任务状态的权威来源；模型不能传入或覆盖 `user_id`、`project_id`、目标环境等身份/授权字段。
- Hermes 收到服务端构造的任务信封，只能访问该任务许可的工具和文件。不能直接拿对象存储长期凭据或任意 shell/网络权限。
- Agent 的自然语言输出是建议、草稿和证据说明，不是成功状态。只有工具返回的结构化结果和用户/审核人确认才能推进状态机。
- 所有工具由 API/worker 侧按白名单注册；审批要求由服务端检查，不能靠提示词约束。
- 每个任务携带 `task_id`、`project_id`、`input_version`、`allowed_actions`、超时和取消令牌；写产物以新版本/临时目录进行，成功后原子登记。

### Hermes 集成边界

官方公开资料描述 Hermes 可运行于 VPS/服务器，具备技能、MCP、工具配置和子 Agent 等能力；所查资料没有给出可据以承诺的稳定通用 HTTP 产品 API、多租户账户隔离或生产作业队列契约。因此：

1. MVP 以本服务自有队列驱动 Hermes CLI/受控 worker 子进程，或 Hermes 官方明确支持的本地集成入口；具体接口先做 P0 验证，不在设计中虚构函数、API 或配置项。
2. Hermes 版本固定并记录；每个 worker 任务有独立临时工作目录、只读 skill/工具配置和受限文件挂载。确认 Hermes 的 session/memory 是否会跨任务共享；默认不能共享项目间对话记忆。
3. Hermes 子 Agent 只用于无副作用的并行分析（如 Brief 检查、交付清单初审）。写文件、调用图像 provider、导出、发布等能力通过单独白名单工具执行。
4. 若 Hermes 进程/API 契约不适合服务端调用，不把 Hermes 的 gateway 暴露为产品 API；改为在 worker 内调用其支持的执行入口，或用方案 C 替换 Agent 层。

## 6. 角色与职责

| 参与者 | 负责内容 | 不负责内容 |
|---|---|---|
| 用户 / 创作者 | 目标受众、角色性格与外观边界、参考资料来源、Brief 和设定稿确认、是否授权继续生成 | 不需要了解 psd2live/Cubism 工具细节 |
| 制片人/工作流协调 Agent | 将请求拆为阶段任务，识别缺少的信息，解释失败原因，列出交付和待办 | 不决定许可，不把未验证产物标记为通过 |
| 角色导演 Agent | 整理设计 Brief、角色设定和视觉方向草案，维护设计一致性 | 不擅自把他人 IP/角色复刻为原创，不提交正式 PSD |
| 美术/素材制作 Agent（逐步引入） | 生成或处理概念图、协助形成分层素材清单和绘制说明 | 不保证图像生成可直接满足完整分层和遮挡修复 |
| Live2D 装配岗位 | 检查分层、坐标和遮挡；调用 layout/rig/export 工具；留存工程与导出 | 不自行启用未经 runtime 验证的参数或素材 |
| QA Agent + 确定性检查器 | 汇总引用完整性、配置、哈希、截图和测试结果；指出缺项 | Agent 不能以文字判断覆盖真实运行时验收 |
| 人类审核者 | 对视觉质量、模型变形、原创性/授权和 production 发布作最终签核 | 不因自动检查通过而失去审核权 |
| 最终 Live2D 数字人 | 被发布产品加载并与用户互动 | 不参与角色资源的创建/审批 |

不必一开始启动多个常驻 Agent。MVP 可由一个 Hermes 协调 Agent 按任务选择受限技能/工具；当角色设定、素材、装配、QA 的输入输出稳定后，再拆岗位，减少多 Agent 互相转述和维护成本。

## 7. 端到端工作流与状态机

```text
DRAFT
  → BRIEF_REVIEW
  → BRIEF_APPROVAL                 用户确认目标、参考图和原创边界
  → CONCEPT_DRAFT
  → CONCEPT_APPROVAL               用户确认立绘方向
  → LAYERED_ASSET_PREP             美术/生成 provider 产素材，人类修整与分层
  → DELIVERY_CHECK                 source.json / PNG / 坐标 / 声明槽位自检
  → LAYOUT_READY
  → CUBISM_RIGGING                  受控 Editor/MCP 或人工执行
  → EXPORT_CHECK                   引用、文件、参数、版本检查
  → RUNTIME_QA                     真实 Cubism runtime 显隐/遮挡/动作验收
  → HUMAN_RELEASE_REVIEW
  → RELEASED                       版本不可变，建立发布指针
```

任一阶段可转 `BLOCKED`（记录原因/责任人/所需输入）或 `FAILED_RETRYABLE`；失败重试从最近成功检查点开始。取消任务停止后续动作，但保留已生成产物为未发布版本。重新生成必须创建新 run/version，不覆盖源文件或已发布包。

### 7.1 输入与设定

1. 用户创建 project，填写用途、角色年龄设定（明确成年）、性格、外观、语言、动作/表情需求、目标设备和商业用途。
2. 上传参考图和参考链接时声明素材来源、权利状态和允许用途。服务端按 project ACL 存储，签名 URL 不写日志。
3. Hermes 产出结构化 Brief 草案、需要澄清的问题和风险项。用户确认后锁定 Brief 版本。
4. 生成概念图只接经许可审查的 provider；输出标记 provider/model/version/seed（若提供）、prompt 版本和 source refs。概念图不是模型或最终美术交付。

### 7.2 分层素材与 Live2D

5. 美术或图像工作流将选定方向处理为分层 PNG 与 `source.json`。AI 图像通常不能直接成为高质量可绑定资产；预计需要遮挡补全、图层拆分、边缘修复、坐标统一和美术复核。
6. 运行仓库现有 `check_delivery_ready.py` 和 `wardrobe_layout.py`。检查通过只代表清单/路径/尺寸满足生成 Layout 的条件，不表示图像质量、遮挡或可上架。
7. 按当前已验证路径，在官方 Cubism Editor/MCP 环境执行 `project_import_layout → rig_generate → project_export_target moc3`。受控工作站执行器若暂时不能被服务端安全调用，工作台为该步骤创建人工任务并收回工程、导出与证据。
8. 检查 `model3.json` 引用、纹理、`moc3`/`cdi3`/exp3 参数和文件完整性，生成 SHA-256 清单。结构检查结果不设 `runtime_verified=true`。
9. 在真实 Cubism runtime 逐项验收显隐、0/0.5/1 参数画面、遮挡、动作变形、基线状态和已有参数兼容性；记录 runtime/设备/模型版本、截图/录屏、测试人和日期。
10. 用户或有权限审核者确认最终角色、授权清单和发布目标后，工作台才登记不可变 release version。之后 Android 端接入另设适配任务，不能从中间态自动同步至 active 资产。

## 8. 项目数据与产物管理

建议第一阶段采用既有后端风格可兼容的 PostgreSQL + Redis（队列可先用现有服务设施或 DB 队列，按 companion-agent 仓库实际依赖决定）；二进制文件放隔离对象存储。不得把大图、PSD 或模型包放在任务数据库行里。

核心实体：

- `project`: owner/team、用途、状态、保留策略。
- `project_member`: 用户、角色、权限。
- `brief_version`: 结构化需求、参考资料来源、审批人和时间。
- `workflow_run` / `workflow_task`: 阶段、输入版本、重试次数、租约、错误码、执行器版本、开始/结束时间。
- `artifact`: 类型、版本、对象 key、SHA-256、大小、来源、许可记录、父产物、扫描状态。
- `approval`: 审批点、请求快照、用户决定、时间、幂等键。
- `qa_evidence`: 检查器版本、命令/配置摘要、结果、runtime 和设备信息、截图/录屏引用。
- `release`: 不可变模型包 manifest、签核、渠道、撤回/替换关系。

工件目录建议：`projects/<opaque-project-id>/<run-id>/<artifact-type>/<version>/`。用户文件名不作为路径；存储端禁用公共读。对外使用短期签名 URL；日志仅写 artifact id/hash，不写签名 URL、私有 prompt、参考图原文或密钥。

保留策略按用途和授权确定：原始参考图默认在项目保留期内可删除；可配置生成中间图短 TTL；已发布资产仅在合同/用户权利允许时保留。提供项目删除流程，清除数据库元数据、对象存储版本、缓存及备份到期记录；审计保留采用最少必要字段且不含图片内容。

## 9. 工具契约和权限

优先将现有脚本包成可校验、无隐式副作用的工具接口。接口只接相对 artifact id/版本，身份由 worker 上下文注入。示例是产品契约草案，不代表现有服务已实现：

| 工具 | 输入 | 返回/副作用 | 初始权限 |
|---|---|---|---|
| `get_project_brief` | project scope（由服务端绑定） | Brief 版本与审批状态 | READ |
| `validate_layer_delivery` | source artifact id | 结构化问题清单、自检报告 | READ/受控执行 |
| `generate_layout` | source artifact id、输出版本 | 新 Layout artifact；不覆盖旧文件 | AUTO（项目内部产物） |
| `request_image_generation` | 已确认 Brief version、provider preset | 创建生成任务；返回 job id | CONFIRM（会产生外部费用/发送素材） |
| `request_cubism_export` | Layout version、目标输出 | 创建人工/工作站任务 | CONFIRM |
| `inspect_model_export` | 导出 artifact id | 引用/参数/哈希报告 | READ |
| `request_runtime_qa` | 模型包版本、测试清单 | 创建 QA 任务并等待证据 | CONFIRM |
| `publish_release` | 已审批 release candidate | 设置发布指针/创建分发版本 | CONFIRM，审核者角色限定 |

危险权限默认关闭：任意 shell、任意文件路径、任意 URL 抓取、直接生产写入、自动花费 provider 额度、绕过审批和删除已发布包。工具参数需 schema 校验、大小/时长/费用限额、幂等键和审计；外部副作用失败时不伪报成功。

## 10. 远程部署（禁止 Docker）

生产部署按原生服务进程设计，不依赖 Docker/Compose：

```text
nginx/TLS
  ├─ 工作台静态资源 + /api/roles/* → API 服务
  ├─ /uploads/* → 私有上传/签名 URL 服务
  └─ 管理入口仅经认证网络访问

API service (systemd) ─ PostgreSQL / Redis / 私有对象存储
                     └─ task queue
Hermes worker(s) (systemd 或受限 supervisor) ─ MCP 工具服务
Cubism Editor/MCP 执行器 ─ 单独受控图形工作站（仅在可用时）
```

- API、Hermes worker、数据库和 Redis 不公开管理端口；服务间使用独立账户/凭据，按最小权限授权。Secrets 由部署环境注入/密钥管理提供，不能进入 Hermes prompt、session memory、任务日志或产物。
- API 负责用户认证/项目 ACL；每个任务重新校验 ACL 和审批快照。外部图像生成凭据只由 provider adapter 持有，不交给模型。
- Worker 设置并发、CPU/内存/磁盘/时间、输入体积、模型调用费用和输出体积上限；支持优雅停机、任务租约过期回收和幂等重试。
- 网络出站使用域名/协议白名单；工具不允许读取 metadata service、内网任意地址或访问其他项目对象。
- 备份覆盖 PostgreSQL 和对象元数据/产物索引；恢复演练须确保数据库引用与对象版本一致。
- Cubism Editor 图形环境可能不能在无头 Linux 上运行。若目前 MCP 依赖桌面 GUI，应把它部署在制作工作站或由操作者确认执行，不对服务器无头化作承诺。

## 11. 安全、版权与内容边界

1. 只接受用户自有、获得授权或明确可用的参考素材。写入 source metadata：作者/来源、许可类型、用途范围、证明链接或声明、审核状态。
2. 商用生产前复核 Live2D Cubism Editor、Cubism SDK、Expandable Application/UGC 模型数量适用条件，以及每件角色素材、字体、纹理和生成 provider 的商业条款。此前项目研究指出 avatar/动态组合模型可能涉及 Expandable Application 特别许可；这属于待法律/厂商书面确认项，不能据本文自动判定可商用。
3. 默认只接受明确成年角色设定；拒绝未成年人成人化、真实人物未经同意的性化/冒充生成和违反 provider 条款的请求。保留举报、审核、拒绝和删除流程。
4. 上传文件进行 MIME/魔数、像素尺寸、压缩炸弹、恶意文件、PSD 结构和存储体积检查；隔离处理，不在 API 主进程中解析不可信 PSD。
5. Prompt/图像/产物均视为用户数据，按项目隔离；防 prompt injection：参考图内文字、PSD metadata 和外部文档都只作数据，不能修改工具权限或系统指令。
6. 审计记录谁启动生成、谁确认费用/发布、哪个模型与工具版本产出该版本；不记录 API key、签名 URL 和无关个人信息。

## 12. 分阶段落地

| 阶段 | 交付 | 完成门槛 / 决策 |
|---|---|---|
| **P0：技术与授权验证** | 固定 Hermes 版本；服务端启动一个限权任务；验证任务输入/输出、退出码、超时、取消、日志脱敏、重试和会话隔离；让 `validate_layer_delivery.py` / Layout 工具由 MCP/worker 调用；确认 Cubism MCP 实际部署要求；确认目标发布授权问题清单 | 一次完整 staging run 可从 API 创建、在 Hermes worker 执行、产物回到 artifact store、失败可重试；没有未经授权外部副作用。失败则保留自有控制面，替换 Hermes 适配层。 |
| **P1：人工参与的项目闭环** | 单一内部团队身份接入；项目/Brief/上传/阶段看板/确认/审计；AI 只生成设定草案和 QA 摘要；人工分层、Editor rig/export、runtime QA；版本化内部审核包 | 一名制作人员能完成一个原创样件的全流程；所有阶段有明确 owner/产物/状态，失败不污染已审核版本；发布登记实行非发起人复核。 |
| **P2：半自动素材工作流** | 接入一个已审许可的图像 provider；生成概念稿；引导分层、美术复核与返工；成本上限、队列限流和 provider 失败恢复 | 以样本项目计量人工返工、单角色耗时和失败点；视觉质量由美术/用户评价，不以自动化率作为唯一指标。 |
| **P3：多岗位协作** | 在接口稳定后拆出导演、素材、装配、QA agent；并行只用于互不写同一产物的分析/任务；制作工作台通知与交接 | 对比单协调 Agent 基线，证明能减少人类等待/返工；否则继续单 Agent + 确定性任务编排。 |
| **P4：产品发布连接** | 接客户端资源 manifest、灰度渠道、回滚、线上崩溃/性能反馈；新模型独立配置，不借用其他角色未经验证的参数映射 | 生产授权书面确认、Cubism runtime 与 Android 真机验收、资产回滚演练均通过后才能将版本设为 active。 |

## 13. 验收指标建议

以下为建议目标，P0 结束后按实测修订：

- **可恢复性**：worker 被杀后已完成阶段可复用；重复提交不生成重复发布指针。
- **产物完整性**：每个 artifact 有 hash、父版本和生成工具版本；导出引用检查无缺项。
- **人工质量**：Brief 返工原因、分层修复次数、Cubism QA 缺陷数、每角色人时；至少记录，不预设不现实的自动通过率。
- **用户体验**：每阶段状态/阻塞说明可见；确认前可看将发生的动作、发送的数据、费用或发布范围。
- **安全**：越权项目访问、未确认 provider 调用、未审批发布、伪造 `runtime_verified` 等负向场景均拒绝；敏感链接/凭据在日志扫描中为零。
- **运行成本**：按 provider/model、Hermes tokens、worker 时长、存储量记录每个 project/run 成本；设置单任务与每日预算。

## 14. 风险与待验证

| 风险/未知 | 影响 | 处理 |
|---|---|---|
| Hermes 是否有适合稳定服务端任务调用的受支持入口，及其版本升级兼容性 | 决定 worker 适配成本 | P0 小型集成验证；固定版本和适配接口，避免依赖未文档化内部 API |
| Hermes session/memory 的隔离、清除和续跑语义 | 项目间数据泄漏或错误上下文 | 默认每任务独立上下文；先验证实际机制，不把全局记忆当项目存储 |
| Cubism Editor MCP 依赖 GUI/用户会话 | 远程全自动流程可能卡在桌面操作 | P1 保持人工交接；先实测工作站执行器后再自动化 |
| 文本到分层 PSD 的质量不足 | 绑定失败、人工返工高 | 先以“概念图 + 美术处理”为基线，不宣传一键生成生产模型 |
| Live2D 发布授权/UGC 条款 | 商业模式不可用或需分成/审批 | 扩大生产前取得厂商书面确认和专业法律审核 |
| 多 Agent 增加成本与协调错误 | 生产成本/返工反增 | MVP 单 Agent 协调；有基线数据后逐岗拆分 |
| 用户上传图片隐私和删除 | 个人信息泄露/保留超期 | 私有对象存储、项目 ACL、短期签名、明确 TTL、删除与备份到期政策 |

## 15. 已有仓库证据与适配关系

本文借用以下现有产物作为设计输入；这些证据只代表其文档记录的范围，不扩张为线上服务能力承诺：

- `scripts/dh-lab/live2d-lab-assets/UNDERWEAR-PIPELINE.md`：`source.json`、PNG 分层、`check_delivery_ready.py`、`wardrobe_layout.py` 和导出门槛。
- `docs/design/psd2live-wardrobe-evidence-20261123.md`：psd2live v3.8.4、Editor MCP 流程证据，以及 **Android Cubism runtime 尚未验收** 的记录。
- `docs/design/2026-11-23-换装抽象与特效层设计.md`：`wardrobe-spec.json` 单一事实源及 Live2D 参数只能切换模型已有 ArtMesh 的约束。
- `docs/design/2026-11-24-anime-avatar-mvp.md`：用户确认、签名 URL、失败不覆盖旧资源、重试和私有图片处理模式。
- `docs/design/companion-agent-architecture.md`：参考现有 Companion Agent 的控制面、工具授权和部署思路；该文档自述针对另一个 companion-agent 目录/分支，需落地时对照目标仓库代码再确认。
- Hermes 官方项目资料：<https://github.com/NousResearch/hermes-agent>；官方文档入口：<https://hermes-agent.nousresearch.com/docs/>。截至本文日期，公开资料支持服务器部署与 MCP/技能/子 Agent 能力描述；未在查阅内容中找到稳定通用多租户产品 API 的契约，需按 P0 验证。

## 16. 留给实施者的决定

- P0 选用的 Hermes 执行入口及版本固定方式；若官方支持的入口与 CLI 进程的恢复能力差异明显，优先选择有明确维护承诺的一种。
- 对象存储采用当前服务器已有设施还是新增兼容 S3 的私有存储；必须维持 ACL、版本、TTL 和删除语义。不得因此阻塞 P0：先通过接口隔离，并仅复用已核实设施。
- Cubism MCP 执行器部署在办公室工作站还是专用制作机；若工作站不在线，任务应进入人工待办，不应反复失败重跑。
- 首个图像生成 provider、预算上限、数据使用与训练条款；接入前完成许可检查。
- Live2D Expandable Application、UGC 和商用发布许可的书面结论。
- 第一批模型包的目标格式和客户端分发协议；客户端接入属于 P4，不是 P1 的隐式范围。
