# Her 角色与可替换资源契约（设计草案）

日期：2026-11-27。本文是目标设计，不表示接口已上线。目标是让用户从角色列表选择 Her，看到姓名、介绍与能力，并为自己的角色更换模型实际支持的帽子、头饰、围巾、上衣、裤子、裙子、鞋、首饰等资源；同一设计应支持 Live2D、Spine 和 VRM，同时保持现有 UI 与特效链。

## 现状与模型实验

- 现有角色摘要/详情在 `feature-companion/.../companion/CompanionModels.kt`；聊天与陪伴分别使用不同角色 ID，须先建立可追溯映射。角色 `skill` 目前是字符串，不是版本化技能清单；客户端会话请求传 `character_id`，不能据此断言服务端的 prompt、声音和技能组合已满足本设计。
- ATRI 的 `scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json` 已用于生产工具；Android 当前仍由 `Live2dTouchReaction` 的 ATRI 专属枚举及 `Live2dDigitalHumanEngine` 的模型名分支控制。规格中 pending 的资源仍不可选。
- 2026-11-27 本地浏览器：现有 `demos/avatar-lab/app/src/main/assets/scene/` 的 VRoid HairSample_Female 加载成功，切换全身视角、微笑成功；`live2d/` 的 Hiyori 加载成功，切换思考状态并触发 TapBody 动作组，控制台无错误、无失败网络请求；动作按钮报告已播放，未逐帧评价动作效果。这些是已有实验场景的浏览器验证，不是新接入、真机性能、服装替换或真实对话语音验收。VRoid 样本许可见 `demos/avatar-lab/models/SOURCES.md`；Hiyori 页面标注仅学习评估、不可商用，发布前应核对各资产具体授权。
- 原创机器人多槽输入及编辑器实验见 `scripts/dh-lab/psd2live-eval/ROBOT-WARDROBE-VALIDATION.md`；存在笑脸缺损和导出结构丢失，不能当成运行时通过。

## 角色封装

把可发布定义与用户个人状态分开：

| 对象 | 身份与责任 |
| --- | --- |
| `HerCharacter` | 稳定角色 ID、姓名、多语言介绍、封面、可见性与当前发布版本；角色列表和详情使用。 |
| `HerRelease` | 不可变发布版本：角色信息、服务端人设配置引用、技能配置引用、声音配置引用、模型包和 wardrobe-spec 引用及其版本/hash。升级创建新 release。 |
| `HerInstance` | 用户与角色的实例；记录选中的 release、已保存衣橱、用户偏好和迁移状态。用户数据与角色默认配置隔离。 |
| `HerSession` | 创建时固定实例、release 和会话配置；服务端按受控引用解析人设、技能、声音及用户授权信息，记录实际使用的版本。 |

概念示例（字段为设计，不是现有接口；引用须由可信发布流程解析）：

```json
{
  "characterId": "her.example",
  "releaseId": "1.0.0",
  "profile": {"name": "小禾", "intro": "喜欢倾听的伙伴", "portraitRef": "portrait/default"},
  "personaProfileRef": "persona/example/v1",
  "voiceProfileRef": "voice/example/v1",
  "skillProfileRef": "skills/example/v1",
  "avatar": {"engine": "live2d", "packageRef": "avatar/example/v1", "sha256": "<sha256>"},
  "wardrobeSpecRef": {"path": "wardrobe-spec.json", "version": 1, "sha256": "<sha256>"}
}
```

`personaProfileRef` 指向服务端持有的角色人设、表达风格、背景和行为边界；系统规则、角色、授权记忆及当前对话按服务端既有安全边界组合，不由客户端提交完整系统 prompt。角色专属 skill 用稳定 ID、版本、配置和授权要求引用；能力是否可执行以服务端策略及用户授权为准，角色描述不自动授予工具权限。声音配置应包括 provider、voice ID、语言/地区、可用性和版本；音色授权、不可用回退及会话中途变更由服务端契约明确。列表只返回展示所需摘要，详情返回可公开的介绍与能力；秘密、人设内部策略和凭证不放进资源包。

## 衣橱：单一规格、独立实例

**每个模型发布包只有一份版本化 `wardrobe-spec` 作为槽位、候选、默认值、约束及引擎绑定的事实源。** 编辑工具、资源打包、客户端目录、纯决策层、引擎适配器都消费该规格；不在 Kotlin 枚举、UI、表达式生成器中维护另一份服装表。角色 release 以 hash 固定具体规格，用户状态仅保存其引用和选择，不复制绑定。

建议规格包含：`schemaVersion`、`modelId`、`modelVersion`、`engine`、`slots`、`items`、`constraints`、`bindings` 和模型/资源校验信息。槽位是可扩展 ID，不硬编码为固定总数：`hat`、`head_accessory`、`scarf`、`top`、`bottom`、`dress`、`shoes`、`jewelry` 等；同类多个首饰可以通过 `capacity`、位置标签或子槽位表达。展示分类与实际占用槽位分离：连衣裙可占用上装和下装，项链与围巾可有冲突规则。可定义 `none`，但只在模型支持卸下且基础身体素材完整时开放。

每项至少包含稳定 `itemId`、展示信息和缩略图引用、占用槽位、模型/体型兼容范围、资源包引用与 hash、依赖、冲突、默认状态、可用状态及原因、适用模型版本和引擎绑定。支持 `available`、`pending_art`、`pending_binding`、`unverified`、`unavailable`；只有资源完整、兼容且绑定通过该发布版本验证的项才可应用。用户可替换**所有已为该模型制作、授权、兼容并验证的资源**；新增一件衣服要先制作或适配其模型资产，不能只增加一个目录项就声称任意模型能穿。

概念例子（绑定值仅说明结构，不代表真实模型参数）：

```json
{
  "schemaVersion": 1,
  "modelId": "example",
  "modelVersion": "1",
  "engine": "live2d",
  "slots": [{"id": "top", "capacity": 1}, {"id": "bottom", "capacity": 1}, {"id": "shoes", "capacity": 1}],
  "items": [
    {"id": "dress.blue", "occupies": ["top", "bottom"], "status": "unverified", "bindingRef": "dress.blue"},
    {"id": "shoes.white", "occupies": ["shoes"], "status": "unverified", "bindingRef": "shoes.white"}
  ],
  "bindings": {"dress.blue": {"type": "live2d_expression", "mappingRef": "verified-combination-table"}}
}
```

具体引擎分别解析同一规格：Live2D 使用模型真实参数/部件与完整组合表达式；当前表达式管理器一次只激活一个表达式，切换任一槽须保留其他槽参数，组合规模应评估并避免无限预生成。Spine 使用对应 skeleton、skin/slot/attachment，应用后按 runtime 规范恢复并重应用动画。VRM 使用与目标骨架、体型和版本兼容的 mesh、材质、权重及 blendshape；遮挡和穿模须实测。引擎绑定和资源可用性必须来自实际模型验证，不能把 ATRI 参数迁给原创角色。

## 操作、状态与错误

`WardrobeCatalog` 从已验证规格构造；`WardrobeSelection` 是按实例保存的 item ID 集合及规格版本。纯决策 API 建议提供 `resolve(spec, selection, command) -> decision`，命令包括选择、移除、试穿、取消和确认；结果包含新选择、被替换项、冲突原因与需要加载的资源。试穿状态与已保存状态分开，取消恢复上次保存，失败不覆盖已保存状态；确认在资源就绪、引擎应用成功后持久化。切换一个槽位保留其他不冲突选择；跨槽冲突按照规格产生确定性替换或显式冲突提示，禁止静默遗留重叠。进程重启恢复已保存选择；角色或 release 更新时基于稳定 ID、兼容映射和已验证默认值迁移，丢失项展示原因，不把旧绑定套到新模型。

引擎适配器只接收决策层解析后的完整目标外观并报告成功/失败；下载失败、hash 不符、不兼容、缺资源、未验证、渲染失败都返回可显示的原因，并保留当前可用外观。用户实例服务端同步如采用，需明确版本/并发冲突规则、离线重试和账号隔离；未上线前仅作为目标契约。衣橱和表现特效分离，特效继续走独立叠加层与 `cmp.cue`。

## 资源发布与验收

模型包 manifest 固定角色/release、引擎与 runtime 兼容范围、资源清单、hash、规格及授权记录。先在暂存区下载、校验大小/hash/路径和完整依赖，再原子切换活动版本，保留至少一个已验证可回滚版本；现有 `AvatarPackStore` 的校验能力可参考，但其旧版本清理方式不能直接视为此回滚保证。按实际分发用途分别核验模型、衣服、纹理、动作、语音和 SDK 权利。

依次交付：

1. 固定角色、release、用户实例和会话的端服务契约，核对聊天与陪伴角色 ID 映射、声音及技能真实实现。
2. 以 TDD 建立可版本化 wardrobe-spec 校验和纯决策层，覆盖单槽互斥、连衣裙占多个槽、多个首饰容量、冲突、缺资源、试穿/取消/保存、恢复和迁移。
3. 接入现有 UI 与引擎中立适配器，消除 ATRI 模型名判断与重复枚举，同时用回归测试保护现有 ATRI 所有组合。
4. 选一套实际拥有多件素材且授权清楚的非 ATRI 模型，验证每件资源、独立槽保持、切换/恢复、动画、遮挡和损坏资源降级；分别记录编辑器、Cubism/Spine/VRM 运行时、Android 构建与真机证据。
5. 做资源发布、完整性及回滚和跨设备同步验收；真机由协调会话从主线最新 tip 现场构建安装。
