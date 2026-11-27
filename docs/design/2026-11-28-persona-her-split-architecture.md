# persona 与 her：两条线，一条流程

日期：2026-11-28｜状态：架构决定。本文回答「chat 那一套角色管理能否跟 her 分开」，并与 2026-11-27 的四份设计对账：[全球蓝图](2026-11-27-her-global-blueprint.md)、[角色与可替换资源契约](2026-11-27-her-character-wardrobe-contract.md)、[pack/相机/特效](2026-11-27-her-pack-camera-effects.md)、[Hermes 生产工作台设计](../superpowers/specs/2026-11-27-hermes-live2d-production-design.md)。契约文档的 `HerCharacter / HerRelease / HerInstance / HerSession` 是权威命名；本文决定它们在服务端与客户端映射到哪些对象和表，以及 her 实体放在哪里。

服务端事实基于已上线的 `ecbfe48a`（`ca_character_pack` 库优先链 + `/v1/characters` renderer 出参）；Android 事实基于 `weifu-refactor` tip `27f8afc9`。

## 1. 决定：分开，分界点在「呈现实体的所有权」

**分开，而且地基已经打了一半。** 自 2026-11-27 起 `ca_character_pack` 是 persona 封装的权威来源（库优先、文件兜底），renderer 与 pack_asset 已退化为卡内「指针」。剩下的真正问题只有一个：**呈现（her）是角色卡里的一个指针，还是独立实体？**

决定：**独立实体（her 注册表）**。三条理由：

1. **用户自定义 her 必须是独立对象。** 它属于用户而非系统角色，生命周期（生成→审核→发布→撤回）独立于任何 persona。「卡内指针」装不下带所有权的对象。
2. **renderer 中立是法务必需**（§6.4）。用户内容不能走 Live2D，系统角色可以。引擎判别只能长在呈现实体上，不能长在角色身份上——否则换形象必然连带换人格记录。
3. **衣橱、特效、贴纸绑定在模型（her）上，不是 persona。** `wardrobe-spec` 单一事实源要挂在一个具体模型包版本上，her 成为实体它才有挂载点。

同时**流程与逻辑同构**：两条线共享同一条生命周期状态机 `list → download → verify → install → activate → 进房`（§4），共享同一套列表/卡片 UI 组件。persona 的「安装」是部署播种，her 的「安装」是端上装包——状态机是一个实现，store 不同。这正是用户说的「角色分开但是流程和逻辑可以是一样的」的严格化。

## 2. 对象模型（与契约文档对账）

| 契约对象 | 责任 | 服务端落点 | 客户端落点 |
| --- | --- | --- | --- |
| `HerCharacter` | persona 线身份：稳定 ID、姓名、多语言介绍、封面 | `ca_character_pack` 行（已有）+ 文件兜底 + `ca_role_card` 用户覆盖 | `CharacterCatalog`（已有） |
| `HerRelease` | 不可变发布版本：**一组 pin** — personaProfileRef、voiceProfileRef、skillProfileRef、herRef、wardrobeSpecRef | 现阶段 = character pack_version + `ca_character_her` 绑定；pin 语义先行 | 会话请求的 character_id（现状） |
| `HerPack`（本文新增实体） | 呈现实体：renderer、模型包（asset/version/hash）、默认音色、特效/贴纸目录、触摸区、缩略图 | `ca_her_pack`（新，§3） | `AvatarPackStore`（已有校验-安装基础） |
| 绑定 | role ↔ her，默认一对一，可扩展多候选 | `ca_character_her`（新） | — |
| `HerInstance` | 用户×角色：pin 的 release + 衣橱选择 + her 覆盖（用户自定义或换系统 her） | 后一步（`ca_user_her`，P1-01）；先端上本地 | `WardrobeSelection`（契约方向） |
| `HerSession` | 进房时固定实例/release/配置，记录实际使用版本 | 现有会话与 voice entrypoint，逐步收紧 | 会话状态机 |

三个关键决定（正式确认）：

- **renderer 是 `HerPack` 的属性，不是角色的属性。** `/v1/characters` 现在直接吐 renderer 是**过渡出参**（= 解析该角色默认 her 包的 renderer），保留到客户端切到 `/v1/hers` 摘要为止；语义已钉测试，不会漂移。
- **音色优先级：用户 her 覆盖 > her 包默认 > persona 卡内 `voice_ref`。** 由服务端 identity resolution 强制，客户端不猜。卡是种子音色（现状不变），her 包可以覆盖（例如同一人格换 3D 形象换声音）。
- **identity resolution = 一个函数、两个消费者。** HTTP 角色目录与 voice entrypoint 现在各自读角色链，是已知重复缝；两条线定稿后共用同一 resolver，各取一半（persona 半 + her 半）。

## 3. 服务端第一刀（本次实施范围）

只做注册表与接口，**不动任何现有 persona 链**，客户端零改动风险：

1. **`ca_her_pack`**（迁移 0005，沿用 0004 的方言分支：PG JSONB/timestamptz、MySQL MEDIUMTEXT/datetime）：`her_id`（unique）、`renderer`、`pack_version`（内容变更才 +1）、`payload`（`model_ref{asset,version,hash}`、`effects[]`、`stickers[]`、`voice_ref`、`thumbnail`、`touch_zones`）、`status`（蓝图包状态机 `draft→…→published→retired/revoked` 的服务端子集）、`source`。
2. **`ca_character_her`**：`role_id ↔ her_id` 绑定 + `is_default`。
3. **`/v1/hers`** list/detail：镜像 characters 路由的鉴权、分页、裁剪风格。**教训固化：`response_model` 必须显式声明每一个要出去的字段**——同一个缺口已发生两次（`voice_ref`、`renderer`），HTTP 层测试钉死，不能只测内部对象。
4. **播种脚本**：幂等规则与 `seed_character_packs.py` 一致（内容没变不写、不顶版本）；现有 linche/sudai 的 model_ref 生成对应 her 包，绑定默认一对一。

明确不做（各有归属）：`ca_user_her` 与用户 her 生成闭环（P1-01/P2）；客户端「自动选引擎」改读 her 摘要（客户端第二刀）；衣橱服务端同步（契约文档已定为未上线目标）。

## 4. 生命周期同构与 UI（产品灵魂）

| 阶段 | persona 线 | her 线 | 共享实现 |
| --- | --- | --- | --- |
| list | `/v1/characters`（已有） | `/v1/hers`（第一刀） | 卡片组件：缩略图 + 名称 + 状态徽标 + renderer 徽标 |
| download | 随服务端部署 | 包下载（zip/glb，签名 URL） | 下载管理单实现 |
| verify | 播种时结构校验（已有） | hash + 路径 + 大小 + 依赖（`AvatarPackStore` 已有基础） | verify 契约单实现 |
| install | 播种 = 安装 | 暂存区 → 原子激活，保留上一好版本 | 状态机单实现（未装/下载中/校验失败/已装/损坏） |
| activate | 角色切换（会话级） | her 切换（用户级默认 + 会话级覆盖） | 同上 |
| 进房 | resolver persona 半 | resolver her 半 | identity resolution 一处 |

UI 纪律（沿用「不改原 UI」）：选角色页与 her/衣橱页共用列表与卡片组件和同一状态徽标集；特效面板（特效/贴纸 × Her/我，`27f8afc9`）交互不变，只把资源来源从随 app 改为 her 包目录。**「选角色 = 她是谁」「选 her/衣橱 = 她长什么样」是同一个组件的两条数据源**，这就是「紧致」的结构含义。

## 5. model、特效、sticker：效果、性能、扩展（要求 2）

三层彼此独立、各自可换：

1. **模型层（HerPack）**：引擎注册表按 renderer 分发（native Live2D / WebView / VRM / simple 现状）；wardrobe 只能引用该模型已验证的绑定（契约文档约束不变）。
2. **特效层（Her 舞台）**：`cmp.cue → StageEffectMapper →` 独立叠加层（Lottie 基线）。her 包声明特效/贴纸**目录**（id + 资源 hash + 锚点声明）；白名单、频率限制、去重属于策略（§统一命令契约，pack-camera-effects 文档），不属于包。
3. **贴纸层（用户相机）**：相机叠加，独立于 her 包，归用户所有；与 Her 舞台特效分面板已落地。

**性能预算表框架**（结构固定，数值按 P0-03 基线实测填写，不虚构——沿用执行手册第 6 条纪律）：

| 指标 | 口径 | 上限 |
| --- | --- | --- |
| 形象进程内存增量 | 进场前后 PSS，中位设备 | P0-03 实测后定 |
| 冷启动到首帧 | 包已装，冷进程 | 同上 |
| 帧时间 P50/P95 | 稳态对话场景 | 同上 |
| 同屏特效并发 | Lottie 实例数 + 资源回收 | 同上 |
| 包体积/下载流量 | 按 renderer 分档 | 同上 |

必做优化已有官方依据：Cubism mask-buffer 预渲染（官方明示智能手机上逐 mask 渲染目标切换会变慢）；低档降级路径（静态立绘）。这些数字不是装饰：§7 的 AIRI 反例说明 WebView 栈到不了这个量级。

**扩展三步曲**（不改代码的扩展是默认，改代码的扩展是例外）：

1. 新角色 = persona 包 + her 包（纯数据 + 资产）。
2. 新衣服/特效/贴纸 = wardrobe-spec 或目录项 + 资源 +（特效）白名单与 `cmp.cue` 映射。
3. 新引擎 = 注册表适配器——这是唯一需要写代码的扩展，两条线和 UI 都不动。

## 6. 生产方案（要求 3、5）

### 6.1 内部产线：Hermes 方案 B（已定），本文补输出契约

[生产工作台设计](../superpowers/specs/2026-11-27-hermes-live2d-production-design.md) 已选定自有控制面 + Hermes 受控执行器（方案 B），状态机 `DRAFT…RELEASED`（其 §7）。本文补上它与两条线的接口：**产线的最终产物 = 一个 `HerPack` 版本 + 一个 persona 包版本**。`publish_release` 落地 = 播种 `ca_her_pack`（及 persona 侧 `ca_character_pack`）+ 绑定默认 + CDN 分发。Hermes skills = 工序拆分（brief、分层素材、绑定、特效、质检、打包），tools 契约按其 §9；发布永远 CONFIRM + 非发起人复核。

### 6.2 已验证与可选的执行器

- **psd2live**（GPL-3.0，2026-11 已 1.2k stars）：分层 PSD→可编辑 Cubism 5 工程 + 完整 moc3 运行时族，且自带 MCP 服务——它已经把「coding agent 驱动绑定」这条路铺好了。11-23 已在我方验证端到端（`ParamWear_*` toggle 参数与 wardrobe-spec 槽位模型同构）。GPL 处置：**独立进程经 MCP 调用，不链接不分发其代码**；绑定质量仍需我方 RUNTIME_QA 门。
- **Tripo Auto Rig**：图→VRM-1.0 骨架 GLB（90+ 动画 preset 重映射），全身/舞蹈向 her 的 3D 路线；AIGC 网格重，decimation 是必经工序；商用条款按 provider 合同核。
- **Rive**（MIT）：特效/贴纸叠加层的下一步候选（state machine + data binding 方向与我方 `cmp.cue` 模式同构），不进角色本体。

### 6.3 用户自定义 her

**不做「上传任意模型」**。自由度落在参数化模板槽：用户选模板（预绑定的槽位/贴图），服务端生成填槽内容（avatar-gen MVP 挂载点，animagine-xl 系 OpenRAIL++，已建 fail-closed provider）。产物 = 一个 `ca_user_her` 实例，走与系统 her 完全相同的下载/校验/安装链。**用户线 renderer 限定非 Live2D**（自家分层 2D / Rive / VRM），直到 Expandable Application 书面许可落定。质量、安全、性能全部在模板边界内——这是 OLLMV/AIRI 都没有的产品护城河。

### 6.4 许可硬约束（决定上面两条的边界）

Live2D SDK 的「可扩展应用（Expandable Application）」条款：换装、自定义形象、多角色聚合的服务落入该定义，**小规模豁免明示不适用**，需事前审批 + 特别出版许可协议。「用户自定义 her」正是该定义本身。因此：renderer 中立不是架构洁癖而是法务前提；官方示例模型不可作主形象；发布前取得书面结论（法务跟进项）。AIGC 侧 AnimeGAN 系无干净商用授权（已证），服务端生成走 OpenRAIL 家族。

## 7. 竞争底线（要求 4）

调研文档（四路全部收口入库）：[OSS 对比简报](../research/2026-11-28-oss-companion-landscape.md)、[Open-LLM-VTuber v1.2.1 源码深读](../research/2026-11-28-open-llm-vtuber-deepread.md)（含 file:line 引证）。结论各一句：

- **AIRI**：宽度吓人（Live2D+VRM+skills+memory+语音通话周更，v0.15.x 还落了手机端 Live2D 编辑器），但移动端是 Capacitor WebView——其自家 DevLog 承认 1.7–2.2 GB RSS、低端崩、正在试 Unity 重写。我方 Cubism Native GL + 几十 MB 预算是**一个数量级、且是他们现有栈到不了的类别**。
- **Open-LLM-VTuber**：核心冻结（默认分支 2026-05 最后推送，v2 重写停在 Zulip 规划），许可证将漂移到自定义 OLLV License 1.0，用户流向 Warashi 等 fork（记忆+主动发言——恰是我方路线图）。**值得抄的三件**已在深读文档钉死：(1) 四装饰器流变换管线（token 流→句切分→表情抽取→显示处理→TTS 过滤），任何 yield token 的 agent 白得呈现层；(2) 并行合成、按序交付的 TTS 队列 + `frontend-playback-complete` 回合背压 + 打断时的记忆手术（`[Interrupted by user]`，让 LLM 知道自己被切断）；(3) 表情词表从绑定模型的 manifest 自动注入系统提示词——persona 与呈现的能力同步机制，对应我方 `cmp.cue` 白名单应有的形态。**它的天花板**：agent 只能设表情（`expressions[0]`，motion 只有自动 Talk；点击/触摸从不回传 agent），零测试、零鉴权、单进程共享内存——「紧致」它只做到了一半，另一半（双向互动的呈现契约）是我方的。
- **桌面宠物系**（xiaokeai/AILIS/Warashi）：各持一片（格式广度、agent harness、记忆），都不在我方发布的平台上。
- **没有人**在移动端同时做成：强 agent 工具架构 + 紧致可互动的形象 UI + 非美术可完成的创作管线。这个交集就是产品。

**存在性判据**：若一个周末黑客用 AIRI + OLLV + psd2live 就能凑出我们的体验，我们就输。要守的三件事：中端设备的每帧成本、通话页打断质量、非美术用户分钟级完成的产线——这三件在所有被调研项目里都不存在。

## 8. 未做与开放项

- 服务端第一刀（§3）：本会话实施；`ca_user_her` 与生成闭环归 P1-01/P2。
- 客户端第二刀（引擎自动选择改读 her 摘要、AvatarPackStore 消费 her 包 manifest）：另立任务。
- 性能预算数值：P0-03 基线实测。
- Live2D Expandable Application 书面结论、psd2live 产线化的 EULA 复核：法务项。
- 聊天与陪伴两处 character_id 映射核账：契约文档步骤 1，先行未闭环。
