# 聊天与角色扮演图片消息设计

> 状态：**设计已批准要点，计划编制中**。日期 2026-11-21（经独立 builder 审阅一轮，修订见文内）。
> 已确认决定：① chat 与 companion 全局默认收纳盒，**单图也接受 190×140 centerCrop 裁切**；
> ② 图片内容审核 **P1 不做**，作为已知风险挂账（见 §7）。
> 范围：feature-chat、feature-companion、chatkit/mediaview、companion-agent 协议。
> 前置阅读：`docs/design/shared-media-upload-and-storage.md`（媒体上传链路，v5 部分已实施）。

## 1. 意图与为什么

角色扮演（companion）的消息要支持"图片+文字"一条消息：用户发图给她（她要看懂并回复），
她的消息也可能带图。展示样式与 chat 一致；并且按用户决定，**chat 与 companion 全局统一**：
无论一张还是多张，一律用"三张图那种收纳盒"（`MediaStack` 牌堆）样式，同时把这个布局
沉淀为样式库里的显式选项。

不做的事：
- 不走 LiveKit byte stream 传图（房间数据阅后即焚、无审核点、蜂窝大图不可靠；
  ID+通道复用 chat 链路的决定已在对话中确认）。
- 不做跨设备图片迁移承诺（维持"消息只存 upload_id，本地路径不跨设备"的现状决定）。

## 2. 现状事实（已核实，2026-11-21）

| 能力 | 位置 | 现状 |
|---|---|---|
| 图片气泡 | `chatkit/…/message/parts/MessageParts.kt:156` `ImageGrid` | 1 张单图等比（≤168×200dp）；2–4 张 `MediaStack` 牌堆（卡 190×140dp、错开 14dp、露 3 层、圆角 16、角标「当前/总数」、顶卡横拖翻页）；≥5 张 `MediaCarousel` 轮播 |
| 样式库 | `chatkit/…/model/ChatItem.kt:24` `MessagePart.Images(sources: …)` | 无布局枚举，按张数硬分支（唯一多态枚举是语音输入的 `VoiceStyle`，`ComposerModels.kt:33`，与消息无关） |
| 附件模型 | `feature-chat/…/domain/model/ChatMessage.kt:32` | `ImageAttachment(path, source FILE\|REMOTE, uploadId)`；DB 存 `{content, uploadId}` JSON，REMOTE 只落 upload_id |
| 选图/拍照 | `feature-chat/…/ui/ChatRoute.kt:72-121` | `PickMultipleVisualMedia` / `TakePicture` / `PermissionGateway`；相机权限与 FileProvider 已在 app 清单 |
| 压缩 | `common/…/image/ImageCompressor.kt` | 长边 1600、JPEG 82 起、≤800KB，失败降质降边 |
| 上传/发送 | `AgentChatRemote.kt:167-199`、`AttachmentSender.kt` | **发送时上传**（POST /uploads 中转，每图每消息一个新 id；直传凭证 10 分钟过期、一 id 只绑一条消息）；文字+`attachments:[{upload_id,kind:"image"}]` 一个回合 |
| AI 图渲染 | `AgentBackedChatRepository.kt:576-590`、`hydrateImages :352-366` | assistant 附件 → REMOTE → 逐 id `GET /uploads/{id}` 换签名 URL → Coil 渲染 |
| companion 列表 | `CompanionScreen.kt:527-609, 895-908` | 复用 chatkit `ChatMessageList`，但只映射 `MessagePart.Text` |
| companion 历史 | `CompanionModels.kt:43-49` | 读历史跳过图片附件；与对话页共用服务端同一消息表（`GET /v1/sessions/{id}/messages`） |
| companion 发送 | `LiveKitCompanionTransport.kt:400-403` | 文字走 LiveKit 文本流 `lk.chat` → agent 回合 → `submit(role=user)` 落库；`control.input`（`ProtocolCatalog.kt:54-80`）服务端**只认 kind=touch**（`touch_input.py`），走 generate_reply，**不落库、不带 upload_ids** |
| 服务端读图 | companion-agent `turn_runner.py` `_attachment_ids` → `TurnInputMessage.upload_ids` | 已有 `get_owned` → 喂模型全链路，chat 的 HTTP submit 已用 |

## 3. 方案与被否掉的备选

- **选定：媒体服务 + 消息带 upload_id**（chat 同款链路搬到 companion）。
  持久化、鉴权、历史回放、跨端恢复都免费；服务端读图管线零新接口（`get_owned → 喂模型` 现成）。
  注意：这条链路**没有图片内容审核**，见 §7 风险挂账。
- 备选 1：LiveKit byte stream——否。服务端过不了手、房间即焚、大图不可靠。
- 备选 2：companion 独立图片表/接口——否。companion 与对话页本就共用服务端消息表，
  再造一份必然漂移。

## 4. 样式统一（chat 与 companion 都改）

### 4.1 样式库

`chatkit` 的 `MessagePart.Images` 增加布局策略：

```kotlin
enum class ImageLayout { Auto, Stack }   // Auto=按张数分支（保留旧行为）；Stack=一律收纳盒
// 现有参数名是 sources，保持不变，只加默认参，不破坏调用点：
MessagePart.Images(val sources: List<String>, val layout: ImageLayout = Stack)
```

**默认值取 Stack，即 chat 与 companion 全局统一为收纳盒。** `Auto` 保留为可显式选择的旧行为，
供以后想差异化时按调用点切换。`MediaCarousel` 分支只在 `Auto` 下存活。

### 4.2 Stack 布局规则（复用 `MediaStack` 现有几何）

- **1 张**：按牌堆顶卡渲染（190×140dp、圆角 16dp、2dp 边框、8dp 阴影、centerCrop），
  无角标、不可拖拽。等比裁切，不再按原比例撑高。
- **2–N 张**：现有 `MediaStack` 几何不动（错开 14dp、圆角、边框阴影）；可见层仍最多 3 层；
  角标「当前/总数」；顶卡横拖翻页（阈值 22% 宽、倾斜 ≤6°）可翻到全部 N 张。
  ≥5 张**不再**切轮播。
- 点击：任意张数点击顶卡进入全屏查看器（现有 `Viewer` 路径），左右滑动翻全部。
- 长按：保持现有长按回调（转发/删除菜单），语义不变。

### 4.3 调用点

- chat：`ChatMessageItem.kt:253` 继续传 `MessagePart.Images`，吃默认 Stack。
- companion：`companionItems()` 映射 `MessagePart.Images(sources, Stack)`；长按接
  `onAttachmentLongPress`，与 chat 语义一致。

## 5. 用户输入：选图 + 拍照（companion 与 chat 同链路）

- `CompanionComposer`（`CompanionScreen.kt:784-858`）文本框左侧加圆形「+」：弹出
  相册（`PickMultipleVisualMedia`，图片、可多选，上限 9 张）/ 拍照（`TakePicture` +
  FileProvider，运行时相机权限走 `PermissionGateway`；清单已具备）。launcher 模式照搬 ChatRoute。
- 选中即：EXIF 转正 → `ImageCompressor` 压缩 → 输入框上方出现**待发缩略图条**（单张可删）。
- **发送时上传**（与 chat 现状一致，不引入静默提前传）：点发送才逐图
  `POST /v1/uploads` 拿 `upload_id`（中转接口每图每消息一个新 id，规避直传凭证
  10 分钟过期与"一 id 只绑一条消息"的约束）。待发条上缩略图只标本地状态，
  上传中显示发送进度；失败提示可重试，不阻塞纯文字发送。
  - *被否的替代*：选图即静默上传。凭证过期窗口与"预览角标用 id、发送时另传"的两套
    生命周期不值得，chat 都没走通的路 companion 不先趟。
- 发送：**文字 + 全部 `upload_id` 合成一条消息一个回合**。
  - chat：现有 `AttachmentSender` → `sendMessageStream(attachments)` 不变。
  - companion（**本设计的协议改造核心**）：现状是文字走 `lk.chat` 文本流 → agent 回合
    → 服务端 `submit(role=user)` 落库；`control.input` 目前只认 `kind=touch`
    （`api/livekit/touch_input.py`），走 `generate_reply`，**不落库、不带 upload_ids**。
    因此不是"把入向从 HTTP 扩到 room 事件"一句话，而是：
    1. `control.input` 新增 `kind: "attachments"`，args
       `{upload_ids: [...], client_msg_id}`；协议 caps 协商 `media.image`
       （同时写进服务端 `SERVER_CAPS`，否则握手交集落空）。
    2. 时序约定：**先发 attachments 事件、后发文本**。服务端把 ids 暂存在
       `room+participant` 维度的单槽 pending（Redis，TTL 60s，后到覆盖前值）；
       该 participant 下一次用户回合 `submit` 时把 pending ids 并入
       `TurnInputMessage.upload_ids` 并消费清空——复用既有 `get_owned → 喂模型` 管线。
    3. 幂等：`client_msg_id` 做 pending 去重（重发同 id 不叠加）；pending 超时未消费
       即丢弃，最坏退化为"只发文字"（与不带图等价，可接受）。
    4. 落库：并入后的用户消息 `attachments` 字段与 chat 同形状，历史天然可见。
- `AttachmentStager` / 上传器从 feature-chat 内部提成 `common` 的
  `MediaUploader`（媒体设计 §10 原定方向），chat 与 companion 共用，杜绝两份漂移。
  companion 不直 import feature-chat。

### 5.1 数量上限

服务端 `SendBody` 现状对 attachments **无数量校验**。P1 客户端选图器处拦截（≤9 张）；
服务端校验列入 P1 收尾小项（一行断言 + 422），不单独排期。

## 6. 她的图片怎么展示

### 6.1 对话页（P1）

- agent 产图（剧情"发照片"、生成图等）→ 服务端落 TOS `companion/` 前缀 → 消息
  `attachments` 带 `upload_id` → 随 `TurnDone`/历史刷新进入列表。
- `CompanionHistoryItem` 解析不再跳过图片附件（`CompanionModels.kt:43-49`），按 chat 同款
  `{content, uploadId}` → REMOTE；进页与增量刷新时逐 id `GET /uploads/{id}` 换签名 URL
  （照抄 `hydrateImages` 模式，沿用逐 id，P1 不加批量接口）。
- 渲染走同一 `ImageGrid(Stack)`：左气泡=她的收纳盒，右气泡=用户的收纳盒，方向语义与 chat 一致。
- 用户刚发出/刚拍的图先用 `localPath` 秒显，历史刷新后收敛为 uploadId。

### 6.2 通话模式（P2）

通话页只渲染字幕，**不内嵌大图**。她的图到达时，字幕上方出现窄条胶囊
（24dp 缩略图 + 「她发来一张照片」），点击全屏，5s 自动收起；图片本体始终已落历史，
错过胶囊不丢。图片到达失败不影响语音回合（barge/cancel 路径与图片解耦，渲染只出灰占位）。

## 7. 失败态与边界

- **图片无内容审核（已知风险，用户拍板 P1 不做）**：全链只有 `check_input` 文本审核；
  用户图会直接进 TOS、进模型、进历史。与现状（解题图片同样无审核）一致，P2 评估机审接入。
- 上传失败：缩略图标红可重试；一条消息里部分图失败时，发送按钮给出"继续发成功部分/取消"提示。
- 签名 URL 过期/拉取失败：气泡内灰占位 + 重试入口；文字部分照常渲染。
- pending attachments 竞态：文本先于 attachments 事件到达（乱序）时，本轮按纯文字处理，
  pending 留在槽内给下一轮消费并记 warn——最坏"图晚一个回合"，不丢。
- 老客户端（不支持图）：companion 图片能力挂 hello caps 位 `media.image`
  （**必须同时写进服务端 `SERVER_CAPS`**，否则握手交集落空），
  协商不到就不出「+」按钮；收到带 `attachments` 的历史条目渲染占位文案「图片（当前版本不支持显示）」。
- 数量上限 9 张/条：P1 客户端拦截；服务端 `SendBody` 校验列入 P1 收尾（见 §5.1）。
- **桶私有性前置**：`sspacee` 桶若为公共读，改图即公开——上线前先确认桶策略
  （媒体设计 §3.1 已挂起此问题，本功能不解除该前置）。

## 8. 分期

**P1（打通"发图→她看懂→回图文"）**
1. chatkit：`ImageLayout` 样式库 + `ImageGrid` Stack 分支（1/2/4/7 张单测截图对比）
2. common：`MediaUploader` 提公（压缩+上传拿 id；chat 侧改造接入）
3. companion：模型/历史解析/companionItems 映射
4. companion：Composer「+」选图拍照 + 待发条 + 发送时上传
5. 协议：`control.input kind=attachments` + `client_msg_id` pending 合并 +
   caps `media.image`（双端）；companion-agent 服务端接入 `upload_ids` 管线
6. 服务端：`SendBody` attachments 数量校验（≤9 → 422）
7. 真机验收：chat 与 companion 收纳盒一致性 + 图文回合端到端

**P2**：通话页图片胶囊；AI 产图来源接入；`presentation.image` 实时事件（不等历史刷新）；
chat 历史图片跨设备恢复的收尾（属媒体设计文档范畴）。

## 9. 验收

- 单测：`ImageGrid` 在 1/2/4/7 张 × Stack/Auto 两策略下的布局断言；
  `control.input.attachments` 编解码；历史解析带附件。
- 真机：
  - chat 与 companion 各发 2 张、7 张 → 均为收纳盒、角标「n/总数」、可翻全部；
  - 发图+文字 → 她回复引用图内容（新增剧本 case `image_reply`：断言 `speech.seg` 文本含图中要素）；
  - 上传中断网重传、部分失败提示；
  - 老包收新图 → 占位文案不崩。

## 10. 协调

chatkit `ImageGrid` 默认行为变更会碰 chat 气泡。`video-channel-bugs` 会话正在该设备上做
chat 真机回归——合入前先与其对齐时间窗或在其回归后合。
