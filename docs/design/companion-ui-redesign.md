# 陪伴页 UI 改造方案：五项修复 + 二次元风格演进（P1 已实施）

日期：2026-11-16
范围：`feature-companion` 的聊天主页面（[CompanionScreen.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionScreen.kt)、[CompanionRoute.kt](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt)），对 `chatkit` 做一处向后兼容的插槽扩展。
原则：先做**不依赖新素材**的结构修复（P1），二次元视觉层（P2）等内置形象包（瘦身计划 Task E/F）落地后再接。

## 实施状态（P1，提交 `2f4d4824`）

六个改动全部落地：chatkit `userAvatar` 插槽（默认 null，对话页不变）+「我」字标；长按切 Sheet 并接上分享/朗读/编辑；footer 芯片清空；输入框卡片化（方案 A）；顶栏重排（头像+名+tagline / 语音开关 / 通话 / ⋯）；角色列表行加 32dp 头像位与当前角色描边。

**未做真机验证**：长按弹层手感、分享与朗读的实际效果、新顶栏在窄屏上的排布。下面第一到第五节保留为当时的诊断与决策记录。

---

## 一、五个问题的根因与修法

### 问题 1：用户消息也要显示用户头像

**根因**：不是没传头像，而是 chatkit 根本不渲染用户侧头像。[ChatMessageItem.kt:145](../../chatkit/src/main/java/com/openapi/chatkit/message/ChatMessageItem.kt:145) 头像分支硬编码 `if (!isUser)`，`avatar` 插槽只在助手侧调用；用户消息永远右对齐无头像。

**修法（chatkit 向后兼容扩展）**：
- `ChatMessageList` / `ChatMessageItem` 增加可选参数 `userAvatar: (@Composable (ChatItem) -> Unit)? = null`。非 null 时在用户消息行右侧（气泡之外）按现有 `showAvatar` 同样的「连续同发送者只显一次」规则渲染，等宽占位保证气泡边缘对齐；默认 null → 对话页行为逐像素不变。
- 陪伴页传入一个「我」字圆形字标（与现有 `CompanionAvatar` 同款，用中性色而非品牌主色，避免和她的头像抢）。当前没有用户头像图片数据源，字标是唯一可落地形态；以后账号体系有头像 URL 了只换这个插槽的实现。
- 改动量：chatkit ~40 行，companion ~15 行。

### 问题 2：长按菜单改底部弹层；分享 / 朗读没反应

**根因（两个独立问题）**：
- 菜单样式：chatkit 自带两种形态——`MessageMenuStyle.Popup`（横排卡片，截图里那个）和 `MessageMenuStyle.Sheet`（`ModalBottomSheet`，一行一项，[MessageMenu.kt:124](../../chatkit/src/main/java/com/openapi/chatkit/message/MessageMenu.kt:124)）。对话页通过 `ChatPlatform.messageMenuStyle = Sheet` 用的是底部弹层；陪伴页调用 `ChatMessageList` 时没传，落到默认 Popup。
- 死按钮：长按菜单条目由 chatkit 的 `menuItemsFor` 统一生成（复制/朗读/分享/多选/删除，[ChatMenuTargets.kt:83](../../chatkit/src/main/java/com/openapi/chatkit/message/ChatMenuTargets.kt:83)），但陪伴页的 `onAction` 只实现了 Copy/Delete/Regenerate，其余落到 `else -> Unit`（[CompanionScreen.kt:329](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionScreen.kt:329)）。

**修法**：
1. `ChatMessageList(..., menuStyle = MessageMenuStyle.Sheet)`，一行参数，视觉立即与对话页统一。
2. **分享**：feature-companion 内写一个 10 行的 `shareText(context, text)`（`ACTION_SEND` + `createChooser`）。对话页的 `ChatPlatform.share` 是 feature-chat 的 `internal object`，跨模块不可见，不值得为一个 Intent 建公共模块。
3. **朗读**：`feature-companion/build.gradle` 加 `implementation project(':speech')`，直接调 `DeviceSpeech.speakChinese(context, text)`——离线、免费、设备自带中文 TTS，对话页走的也是它（[SendActions.kt:144](../../feature-chat/src/main/java/com/openapi/feature/chat/presentation/SendActions.kt:144)）。返回 false 时 Toast 提示「设备不支持朗读」。服务端 TTS 回退陪伴侧暂时没有接口，不做。
4. **顺带修两个同类死按钮**：用户消息长按菜单里还有「编辑」（`MessageAction.Edit`），现在同样 no-op。给 `CompanionViewModel` 加 `editMessage(id)`：把原文载入输入框并从本地列表移除（陪伴消息本来就只存本地，服务端无单条删除接口），与对话页编辑语义一致。「多选」由 `ChatMessageList` 内部处理，不需要接线。
5. 改动量：gradle 1 行 + companion ~70 行。

### 问题 3：消息 item 不再显示 footer 操作图标，只保留长按

**根因**：陪伴页显式传了 `footerActions = { listOf(Copy, Delete) }`（[CompanionScreen.kt:312-320](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionScreen.kt:312)），截图里每条气泡下方那两个方形图标就是它。

**修法**：改成 `footerActions = { emptyList() }`。chatkit 在 `footerActions.isEmpty()` 时本就不渲染 footer（[ChatMessageItem.kt:159](../../chatkit/src/main/java/com/openapi/chatkit/message/ChatMessageItem.kt:159)），复制/删除通过问题 2 的底部弹层全部保留，能力不丢。
**测试影响**：[CompanionMessageAreaRenderTest.kt:135](../../app/src/test/java/com/openapi/app/ui/CompanionMessageAreaRenderTest.kt:135) 有一条用例断言 footer 复制/删除可见，需要改写为「footer 不存在 + 长按菜单仍可触发操作」。
改动量：1 行 + 测试改写 ~30 行。

### 问题 4：输入框换成对话页那套

**现状**：陪伴页用裸 `OutlinedTextField` + 灰色发送 IconButton（[CompanionScreen.kt:499-512](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionScreen.kt:499)），描边圆角框，和对话页的白色卡片式输入栏不是一个世代。

**两个选项**：

- **方案 A（推荐）：视觉对齐，不引重型组件**。把 `CompanionComposer` 重写为对话页同款视觉语言：白色圆角卡片（surface 底、轻阴影、20-24dp 圆角）+ `BasicTextField` 无边框 + 有文字时右侧出现品牌色圆形发送按钮（`ArrowUpward` 白色箭头），无文字时隐藏。只保留文字输入一条路径，~120 行。
- 方案 B：直接复用 chatkit 的 `ChatComposer`。它是带录音/转写/草稿/「+」面板的完整组件，入参要 `ComposerState/ComposerAction` 和一整套 `VoiceActions` 回调；而陪伴传输层目前**连麦克风轨都没发布**（见通话页修复文档问题 2），接进来等于搬一个按不动的语音按钮。建议等语音链路（通话页 P3）落地后再评估整体复用。

### 问题 5：语音回答 / 通话移到右上角，重设计顶栏

**现状**：两个入口挤在输入框下方第二行（[CompanionScreen.kt:514-546](../../feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionScreen.kt:514)），占掉一整行且视觉权重很低；顶栏则是汉堡 + 标题 + 三个语义含糊的图标（展开形象的上下箭头、人物、星星）。

**新顶栏布局**（浅色暖底，与对话页 RoleHeader 同家族但不照搬它的品牌色满铺）：

```
┌──────────────────────────────────────────────┐
│ ☰   [头像] 苏黛▾              [♪] [✆] [⋯]    │
│            在线 · 随时可以找我聊               │
└──────────────────────────────────────────────┘
```

- **左侧**：汉堡（抽屉）；角色头像（AvatarPackStore 有素材用素材，没有就用现有字标）+ 角色名 + 小下拉箭头，点按整块 = 现在的「角色选择」入口；头像下方一行 12sp 副文案（在线状态/一句话人设，数据已有 `CharacterSummary.tagline`）。
- **右侧**：
  - 语音回答开关：`VolumeUp/VolumeOff` 小图标，开启时品牌色填充圆形底 + 下方不出现文字（当前态用色彩表达，长按或开关时有 Toast「语音回答：开/关」）；
  - 通话：品牌色描边圆形电话按钮，是右侧视觉重心；
  - 「⋯」收纳：记忆/个性化（现星星）、展开/收起形象区（现上下箭头）收进 `DropdownMenu`。上下箭头这个入口本来就没人认得（用户原话级别的困惑），收进溢出菜单。
- 收起的形象区改为点头像触发（和角色选择冲突——用头像点按进角色选择、形象区只从 ⋯ 菜单进，两个入口语义不再打架）。
- 改动量：~150 行，纯 `CompanionScreen.kt` 内部。

## 二、同类产品调研：二次元陪伴 App 的主流范式

| 产品 | 形态 | 可借鉴点 | 不照搬的点 |
|---|---|---|---|
| Talkie（海外，二次元 SFW 头部） | 深色主题、立绘先行、抽卡角色卡、5 tab 底部导航；聊天页深色气泡 + 左侧角色头像 + 麦克风/发送 | 角色头像作为聊天页视觉锚点；圆形发送按钮；角色发现页大卡网格 | 深色满铺与我们现有浅色暖色系冲突，硬切代价大 |
| 星野（MiniMax） | 发现/角色/对话/情境多页面；分类标签筛选；语气切换器；气泡式对话 | 角色页标签化分类；语气/情境一键切换（对应我们的 output 开关思路） | 六页面导航对当前功能量过重 |
| 猫箱（字节） | 竖滑角色信息流、动漫背景图、括号动作描写输入工具、AI 主动话题 | 括号动作快捷输入（零成本的沉浸感增强，可放输入框「+」位）；主动消息已有数据通道 | 竖滑信息流与 IM 骨架是两套产品 |

结论：三家共同范式 = **二次元立绘视觉 + 类 IM 气泡 + 角色卡发现 + 轻量沉浸输入工具**。我们的差异化选择是**保留浅色暖调**（与对话页同家族、安卓原生感更强、开发成本低），把二次元浓度放在角色头像/立绘、圆角语言和点缀色上，而不是整体翻深色。

来源：[Talkie 评测（InamorAI）](https://inamorai.com/reviews/talkie/)、[Talkie 1:1 复刻规格（Rosebud）](https://rosebud.ai/play/talkie-ai)、[为什么 AI 陪伴产品都想抄星野（创业邦）](https://m.cyzone.cn/article/748951.html)、[猫箱 App 试用（网易/动点科技）](https://c.m.163.com/news/a/JGI5TPB00511A0MG.html)、[星野官网](https://www.ai-xingye.cn/)。

## 三、分阶段计划

### P1：结构修复（不需要任何新素材，全部可编译 + 单测验证）

| # | 内容 | 模块 | 量级 |
|---|---|---|---|
| 1 | chatkit 加 `userAvatar` 插槽 + 陪伴页「我」字标 | chatkit / companion | ~55 行 |
| 2 | 长按菜单切 Sheet；分享 Intent；朗读接 `:speech`；编辑接 VM | companion + gradle | ~70 行 |
| 3 | footer 清空 + 渲染测试改写 | companion / app-test | ~30 行 |
| 4 | 输入框卡片化重写（方案 A） | companion | ~120 行 |
| 5 | 顶栏重排：头像+名 / 语音开关 / 通话 / ⋯ 菜单 | companion | ~150 行 |
| 6 | 角色列表行加 `32dp` 头像位 + 选中态描边（**不做网格**，见第五节） | companion | ~40 行 |

P1 合计 ~460 行，无服务端改动，不依赖 TOS 或素材管线；现有 102 个单测需全绿（含改写的那一条）。

### P2：二次元视觉层（依赖素材，与瘦身计划 Task E/F 合并排期）

- 内置形象包进 `assets/avatars/<key>/` 后，顶栏头像、消息列表头像、通话页中央圆（见 [call-screen-fixes.md](call-screen-fixes.md)）三处同时出图；
- 角色选择页改为大卡网格（立绘 + 名 + 一句 tagline + 播放量式计数），对齐 Talkie/星野的发现页；**这一项单独立项，理由见第五节**；
- 气泡加非对称圆角（靠近头像一侧直角）、助理气泡换更柔的冷白底；发送按钮加品牌渐变；
- 输入框加「（动作）」快捷插入 chip（猫箱式沉浸输入，纯客户端）；
- 可选：浅色主题下的二次元点缀（樱粉/紫罗兰渐变描边、空状态插画），不做深色翻皮。

## 三·五、视觉规范（可直接落到 Compose 的 token）

可视化稿：[companion-page-mockup.html](companion-page-mockup.html)（浏览器面板已打开，含长按弹层态）。

### 配色：两套色，不混用

| 角色 | Token | 值 | 用在哪 |
|---|---|---|---|
| 她 | `HerViolet` | `#8B7CF6` | 头像环、通话按钮、语音开关「开」、选中气泡描边 |
| 她 | `HerPink` | `#FFA6C9` | 与紫色组成身份渐变 `135°` |
| 她 | `HerTint` | `#F3F0FF` | 头像内圆底、气泡描边浅色 |
| 你/行动 | `AppColors.Primary` | `#C2410C` | 发送按钮、输入框聚焦、品牌动作 |
| 开关关态 | `ToggleOff` | `#F3EFEA` | 语音开关、未激活图标底 |
| 页面 | `AppColors.ChatBackground` | `#FBF8F5` | 不动 |
| 气泡 | `ChatIncoming` / `ChatOutgoing` | `#F5F5F5` / `#FDE7D3` | 不动 |

紫色只属于「她」——这是二次元感的来源，同时不改页面主骨相；橙色继续表达「你/发送」，两种色各管一件事。**不做深色翻皮**：现有页面是浅暖白，翻深色要重做全部气泡/输入框对比度，收益不抵成本。

### 顶栏（56dp + statusBarsPadding）

- 底 `Surface` 白，底部 1dp 分隔线用渐变（紫→粉→暖，透明度 100%→0%），是页面上唯一的「装饰线」。
- 左：菜单 `44dp` 触控区；身份区整块可点（进角色选择）：`44dp` 头像（2dp 渐变环 + 内圆 `HerTint` + 字标）+ 名字 15sp/SemiBold + 10sp 下拉箭头 + 副文案 11sp `#8A8F98`（在线/人设 tagline）。
- 右，间距 2dp：语音开关 `36dp` 圆（关：`ToggleOff` 底 + `#8A8F98` 图标；开：紫渐变底 + 白图标 + 4dp 紫光晕）；通话 `38dp` 渐变圆 + 白电话，视觉重心；`⋯` 38dp 触控区，`DropdownMenu` 里放「她记得的事」「展开/收起形象」。
- 头像素材：`AvatarPackStore.load()` 命中就出图（`AvatarFiles.portrait` 圆形裁切），否则字标兜底——与通话页同一套 key 规则。
- 数据缺口：顶栏要名字/tagline，`CompanionRoute` 目前只有 `characterId`。在 Route 里 `LaunchedEffect` 拉一次 `api.characters()` 找到当前角色存进 `rememberSaveable` 的本地 state（失败则退回 `陪伴` 标题，不阻塞通话页）。

### 消息项

- 头像 `32dp`，她=渐变环字标，你=中性底（`#EDE6DC` / `#8A7C6A`）「我」字标。
- 连续同发送者只在第一条出头像，后续行用等宽透明占位——气泡边缘对齐不变。
- 气泡圆角 14dp，靠近头像那一角收成 5dp（非对称圆角），气泡内边距 10/13dp，行距 1.62。
- footer 芯片全删；长按弹层 `MessageMenuSheet` 复用它自己的按下态与红色删除，被长按气泡加 2dp `HerViolet` 28% 描边（chatkit 已有 pressed 状态可用，必要时补一个 `selected` 参数）。

### 输入框

- 外层 `pad 8dp / 12dp / 18dp`；内层白卡 `RoundedCornerShape(24dp)` + `shadow(0, 8dp, 24dp, -14dp)` + 1dp 极浅描边。
- `BasicTextField` 无边框，占位 `#B3AEA8`；`minHeight 54dp`、`maxLines 4`，`imePadding`。
- 右侧发送：`38dp` 实心橙圆 + 白色 `ArrowUpward` 18dp；`input.isBlank() || !canSend` 时变 `#E7DED6` 并禁用。
- 「语音回答」「通话」不再出现在这一行（已移顶栏）。

### 动效（Compose 原生，不引第三方）

- 发送按钮出现/消失：`AnimatedVisibility(scaleIn/scaleOut, spring(dampingRatio=0.7))`。
- 顶栏图标按压：`interactionSource` 收集 pressed → `animateFloatAsState(0.94)` 缩放 120ms（Tactile/Deformable 那条建议，触感来自缩放而不是阴影跳变）。
- 音频播放时她头像外环做 1.0↔1.06 呼吸（`rememberInfiniteTransition`），仅通话/语音条播放期间运行，避免常驻动画耗电。
- 尊重系统「移除动画」：所有装饰性动画包在 `LocalReducedMotion` 判断下（Compose 1.7 没有统一 API，用 `Settings.Global.ANIMATOR_DURATION_SCALE == 0` 判断一次即可）。

### 关于 astryx / ui-ux-pro-max 的落地边界

- **astryx 是 React 设计系统**（`@astryxdesign/core`，Avatar/BottomSheet/Banner 等），不能装进 Jetpack Compose。本项目只借用它的**交互语义**：BottomSheet 的「一张弹层 + 唯一 scrim + 返回键只关最上层」正是 chatkit `MessageMenuSheet`（`ModalBottomSheet`）已有行为，无需改造；Avatar 的 initials 兜底对应我们的字标兜底。不引入任何 npm 依赖。
- ui-ux-pro-max 的检索结果里，claymorphism（厚边框+双层阴影）和 Fredoka 圆体不适合安卓原生界面（会与系统字体、现有 token 打架），采纳的是 **Tactile Digital** 的按压变形（scale 0.94-0.95 + spring）和 **chips 触控 ≥44dp** 两条，其余按其「Pre-Delivery Checklist」自查（对比度 4.5:1、图标不用 emoji、无纯靠颜色传达状态）。
- 结论：设计资产全部落在现有 `AppColors` + 新增 3 个 `Her*` 常量，不引新依赖、不换字体。

## 四、决策点（已拍板，2026-11-16）

| # | 决策 | 结论 |
|---|---|---|
| 1 | 输入框：方案 A 视觉对齐 vs 方案 B 强接 `ChatComposer` | **方案 A**。B 要接一整套 `ComposerState/ComposerAction/VoiceActions`，而陪伴传输层的语音链路才刚补上（通话页 P3），此时搬进来等于一个按不动的语音按钮。 |
| 2 | 用户头像形态：「我」字标 vs 等账号头像 URL | **先上「我」字标**。当前账号体系没有头像 URL，等它就等于这一项永远不做；字标是一个插槽实现，以后有 URL 只换这一个 composable。 |
| 3 | 顶栏「⋯」收纳记忆/形象入口 | **接受**。三个语义含糊的图标（上下箭头、人物、星星）本来就没人认得，收纳进 `DropdownMenu` 并配文字标签。 |
| 4 | P2 角色卡网格：纳入本方案 vs 单独立项 | **单独立项**。理由见第五节——不是工作量问题，是现在没有支撑它的数据。 |

输入框方案 A 的具体形态已写在 §三·五「输入框」；B 留到语音链路稳定后再评估，届时是「换成 `ChatComposer`」而不是「再加一层」。

## 五、为什么角色卡网格单独立项

结论：**不是因为它大，而是因为现在没有能填进卡片里的数据。** 一个网格要成立需要三样东西——足够多的角色、每个角色一张立绘、一个让人愿意浏览的理由——这三样现在一样都没有，而且其中两样的阻塞点在服务端和美术，不在这个 UI 方案里。

### 5.1 现在只有 2 个角色，网格比列表更差

| 事实 | 出处 |
|---|---|
| 陪伴角色卡只有 `linche`、`sudai` 两个 | [companion-agent-architecture.md](companion-agent-architecture.md) §2.2 |
| 客户端 `/v1/characters` 只返回这两个 | 同上 §2.3 末尾 |
| 系统目录里的 283 个角色是「工作岗位」提示词，没有 skill、音色、形象 | 同上 §2.3 |

列表 vs 网格是**信息密度**的选择：列表适合「2–10 项、逐行比较文字」，网格适合「20 项以上、靠图快速扫」。现在把 2 个角色铺成网格，结果就是两个带首字圆的空卡片，纵向还多占一屏——比现在的 `Text + tagline + tags` 列表**更难选**，不是更好看。

真正决定要不要网格的，是那个至今没定的产品问题：**陪伴标签里要不要出现 283 个「软件工程师 / 营销 / GIS 专员」？** 如果答案是「不要」，网格永远用不上；如果答案是「要」，那就不只是网格——需要分类筛选、搜索、分页（现在 `CharacterListViewModel` 一次拉 50 条，无分页），以及为 283 个人物准备视觉素材。这是独立产品线，不是一次 UI 改造。

### 5.2 卡片需要的字段，数据源都不存在

| 卡片元素 | 数据源 | 现状 |
|---|---|---|
| 立绘 / 缩略图 | `CharacterSummary` | **无字段**。模型只有 `id / name / tagline / tags / voiceAvailable`，连图片 URL 都没有 |
| 立绘文件 | `feature-companion/src/main/assets/avatars/<key>/` | **目录不存在**。`AvatarPackStore` 只从 `filesDir/avatars` 读，内置种子包还没做（瘦身计划 Task E/F） |
| 「播放量式计数」 | — | **无数据**。没有对话量/使用量接口，塞个假数字就是骗人 |
| 一段人设文案 | `tagline` | 有，列表已经在用 |

`AvatarSync.syncFor(roleId)` 能按角色拉形象包，但它**一次只拉一个角色**，且只被 `CompanionScreen` 为「当前角色」调用。要铺网格就得对每个角色并发拉一次——在素材本身还没制作（架构文档原话：「`model_ref` 指向的素材没做，素材尚未制作」）的前提下，这只是把 2 次必然失败的请求变成 N 次。

另外提醒一个容易忽略的点：`feature-companion` **没有引入任何图片加载库**（Glide 只装在 `app` 模块）。列表头像是 `AvatarPortrait(file)` 自己 `produceState + decodeScaled`，够用是因为一处只有一张。网格是滚动列表里 N 张图，需要真正的图片加载与缓存——引依赖、定尺寸、定缓存策略，这些都是独立工作量。

### 5.3 所以怎么办

**P1 照常做**（五个问题 + 角色列表头像位，~460 行，不依赖任何素材）。角色选择页在 P1 里**保持列表形态**，只做两件事：

1. 每行左侧加 `32dp` 头像位，复用 `AvatarPackStore` + 字标兜底（和顶栏、消息列表**同一套 key 规则**）——这样 Task E/F 素材一落地，列表自动出图，不需要重做；
2. 选中态加一条 `HerViolet` 描边，让人看得出「当前是这个角色」。

这两处约 40 行，是**无论将来做不做网格都不浪费**的部分。

**网格单独立项**，成立的前置条件是以下三条**全部**满足：

- [ ] 已决定是否把 283 个目录角色放进陪伴标签（这是产品决策，不是设计决策）
- [ ] 每个要在网格里出现的角色都有立绘（Task E/F 或服务端形象包）
- [ ] 有一个真实的可展示指标（对话数、使用人数），或者决定不放计数

三条齐了再立项，那时它是一次纯粹的视觉与滚动性能工作；现在立项，做出来的是一个空壳，而且会挡住 P1 里真正有收益的五项修复。
