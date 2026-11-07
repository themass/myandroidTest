# Chat UI 组件库（`:chatkit`）设计

## 目标

把对话界面拆成与业务无关的组件，放进独立模块 `:chatkit`，对话页、角色扮演页以后都可以复用。

- **高内聚**：一个组件自己管好自己的状态和交互（比如图片网格自己打开全屏查看器，输入栏自己管语音/键盘模式和面板展开）。
- **低耦合**：组件只认 `chatkit` 自己的 UI 模型（`ChatItem`、`ComposerState`），不依赖 App 的领域模型、网络、数据库、播放器或主题 token。App 通过映射器把领域模型转成 UI 模型，通过回调接收用户操作。

## 模块边界

```
:chatkit  (com.openapi.chatkit)
├── theme/      ChatKitTheme：颜色、尺寸、字号，经 CompositionLocal 注入，App 传入自己的品牌色
├── model/      ChatItem / MessagePart / MessageAction / ComposerState / DraftItem / ComposerAction / VoiceStyle
├── message/    ChatMessageList → ChatMessageItem → MessageContent → parts/*，MessageFooter
│   └── parts/  TextBubble、ImageGrid、FileCard、VoiceBubble、ThinkingDots
├── media/      ImageViewer（全屏、左右翻页、双指缩放、双击放大）
└── input/      ChatComposer（输入栏）、DraftTray、VoiceControls（A/B/C 三种样式）、ActionPanel、EmojiPanel

:app 只做三件事
├── ChatItemMapper  ChatMessage（领域） → ChatItem（UI），合入播放状态、点赞状态
├── ChatScreen      组装：角色头部 + ChatMessageList + ChatComposer + 业务弹窗
└── 回调实现         复制、删除、重试、重新生成、编辑、分享、朗读、打开文件、录音
```

依赖方向只有 `app → chatkit`。`chatkit` 只依赖 Compose、Material3 和 Coil（图片加载）。

## 消息组合规则

一条消息由若干 `MessagePart` 组成，按「附件在上、文字在下」的固定顺序渲染（与豆包、ChatGPT 一致），同一条消息的各部分共用一个底部操作栏。

| 组合 | 呈现 |
|---|---|
| 纯文字 | 气泡 |
| 单图 | 无底色的圆角图片，按原图比例，最大 220×280dp |
| 2 / 4 张图 | 2 列方格 |
| 3、5–9 张图 | 3 列方格 |
| 超过 9 张 | 显示前 9 张，第 9 张上盖「+N」 |
| 图片 + 文字 | 图片块在上，文字气泡在下，同侧对齐 |
| 文件（可多个） | 文件卡片：类型色块 + 扩展名、文件名、大小；点击用系统应用打开 |
| 文件 + 文字 | 卡片在上，文字气泡在下 |
| 语音 | 语音气泡，宽度随时长变化（微信式），播放时声波跳动 |
| 语音 + 转写 | 语音气泡下方一个浅色「转文字」块 |
| 图片 + 文件 + 文字 | 依次堆叠：图片 → 文件 → 文字 |
| 助手思考中 | 气泡内三个跳动的点 |

## 状态与操作

- 用户消息状态 `SendStatus`：`Sending`（气泡左侧小转圈）、`Failed`（气泡左侧红色感叹号，点击重试）、`Sent`。
- 所有操作走一个入口：`onAction(item, MessageAction)`，组件不关心操作怎么实现。

| 操作 | 助手消息 footer | 用户消息 footer |
|---|---|---|
| 复制 | ✓ | ✓（有文字时） |
| 朗读 | ✓ | |
| 赞 / 踩 | ✓ | |
| 分享 | ✓ | |
| 重新生成 | ✓（仅最新一条） | |
| 编辑 | | ✓（有文字时，回填到输入框） |
| 重试 | | ✓（发送失败时） |
| 删除 | | ✓ |

助手 footer 参照抖音 / 豆包：一排线性图标，复制、朗读、赞、踩、分享，最新一条末尾多一个「重新生成」。两侧 footer 用同一个 `FooterIcon`，图标 18dp、点击区域 34dp、同一灰色；激活时变实心 + 主色。助手 footer 从头像右侧对齐，用户 footer 与用户气泡右缘对齐。

## 输入栏（`ChatComposer`）

- 布局参照微信：左侧语音/键盘切换，中间输入框（语音模式下是「按住说话」条），右侧表情和「+」；有文字或有附件草稿时「+」变成「发送」。
- 附件草稿托盘在输入框上方，可放多张图片和文件，每项可移除；发送时和文字组成一条消息。
- 「+」面板的入口由 App 以 `List<ComposerAction>` 传入，组件只负责排版（4 列宫格）。
- 语音三种样式（长按语音按钮切换，App 负责持久化）：A 微信式浮层、B 豆包式波纹 + 实时文字、C 录音条 + 声波。音量和实时文字由 App 通过 `ComposerState` 传入，组件不碰录音器。

## 服务端约束

附件接口只收文件不收文字，所以「图片 + 文字」在界面上是一条消息，发送时先逐个上传附件（各自的识别结果合并成一条助手回复），再把文字走流式接口；组合消息只保存一次，文字流不再重复保存用户消息。
