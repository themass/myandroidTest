# 多应用共享图片直传与 TOS 存储设计

> 状态：**设计提案，未实施**。日期：2026-11-17。
> 适用：当前 Android 应用，以及计划复用流程的另外三个应用（按四个应用容量设计；实际应用清单待确认）。
> 范围：解题图片、聊天图片、账号头像。暂不启用 CDN，读取直接到 TOS；**直读桶不等于把桶开放为公共读**。
> 本文不部署桶、不修改业务代码、不配置凭据。所有接口、表、限额和生命周期中标为“建议”的内容均不是现有能力。

## 1. 结论与关键决策

采用 **testserverV 统一媒体控制面 + 客户端直传 TOS + 服务端校验与规范化 + 业务保存媒体 ID + 私有签名读取**。

1. 四个应用共用同一套协议、媒体服务模块和客户端上传组件，不各写一套上传接口。
2. testserverV 签发仅能向一个临时对象写入的短期凭证；客户端不能决定 bucket、object key、owner 或访问权限。
3. 客户端上传完成后回报 `uploadId` 和服务端曾签发的 `objectKey`；服务端仍以数据库记录为准，**不接受任意 key 认领**。
4. 上传临时对象后，服务端读取并检查真实字节、规范化图片，再写入另一个不可变的正式对象。`READY` 才能绑定业务，不能把 TOS PUT 200 当成发送成功。
5. 数据库长期保存 `mediaId` 和内部 object key，不保存手机绝对路径或会过期的签名 URL 作为跨设备附件标识。
6. **账号头像复用媒体服务，但头像更新是独立业务事务**；聊天与解题同理。图片存在和谁有权查看是两回事。
7. 每环境独立桶，桶内按应用与业务分前缀。前缀用于组织和最小权限，**真正的用户授权在服务端**。
8. 暂不做全应用、全用户内容哈希去重；避免泄露“别人是否上传过同一张图”，也避免跨应用删除耦合。

不选择“继续 multipart 中转”作为新链路：图片上行字节不必经过 AppServer 的 HTTP 上传入口。但服务端仍需读取临时图片做校验/处理，模型也可能需要服务端下载或转发；**不能承诺所有服务端图片流量归零，也不能用固定‘流量减半’计算节省**。

## 2. 当前代码事实与纠正

### 2.1 本地消息里的图片现在是不是本地路径？

**当前客户端自己发送并缓存的图片，是本地文件路径。不是一个已经可靠持久化到桶的附件 ID。**

| 代码依据（当前 Android 工作树） | 当前行为 | 影响 |
|---|---|---|
| `feature-chat/.../domain/model/ChatMessage.kt`：`ImageAttachment(path: String)` | 模型注释明确为应用私有目录中的压缩图片路径 | 没有独立的云端媒体身份 |
| `feature-chat/.../presentation/AttachmentStager.kt`：`DIR = "chat_attachments"` | 压缩后保存到 `filesDir/chat_attachments` | 不是相册原始 URI；清应用数据/卸载会丢失 |
| `feature-chat/.../data/mapper/ChatLogMapper.kt` | JSON 的 `images` 数组读为 `ImageAttachment.path` | 字符串格式也能承载 URL，但不代表已接入云存储 |
| `feature-chat/.../data/remote/MessageCache.kt`：`cacheRemotePage` | 特意保留本地富附件，注释说明服务端历史只有文字 | 换设备、清数据之后，不能依靠当前服务端历史找回图片 |
| `feature-chat/.../data/remote/AttachmentUploader.kt` | 通过 `recognize.json` / `local/file.json` multipart 上传给旧业务接口 | 上传用于识别不等于长期附件已入桶 |
| `feature-photosolve/.../data/NetworkPhotoSolveRepository.kt` | 压缩图片临时存 cache，multipart 发送，finally 删除临时文件 | 这个请求用的临时文件不构成云端解题图存档；历史截图等其他文件需另行盘点 |

这些结论针对上述发送和缓存链路，**不是说所有历史数据、所有返回图片都必然只有本地路径**。迁移要按每条记录实际内容区分本地路径、旧 URL 和新 `mediaId`。

### 2.2 压缩参数不是固定 q60

当前 `common/.../image/ImageCompressor.kt` 的真实参数：

- 长边最大 1600px；JPEG 质量从 **82** 开始，逐步降低，最低 **60**。
- 目标不超过 **800 KiB（819,200 字节）**；超过后继续缩图，最小目标长边 640px。
- 代码在达到最小边时可能返回仍超过 800 KiB 的结果，因而目前不是硬上限保证。

设计中保留现有视觉基线，不擅自把所有图片改为固定 q60；新上传前必须检查最终字节数，超限给出明确重压缩/拒绝结果，服务端也独立硬校验。解题图优先保留细字可读性，不能为上传通过无限降清晰度。

### 2.3 头像缺口：客户端缺字段，后端已有 `photo` 骨架

已查代码及原调查记录：

- Android `core/.../model/UserInfoVo.java` 当前没有 `photo` / `avatar` 字段。
- testserverV 实际返回 VO 是 **`UserVo`**，不是客户端同名的 `UserInfoVo`。调查定位到 `UserVo.photo`、`UserPo.photo` 已存在。
- `UserController` 的 `/api/user/info.json` 经 `UserServiceImpl.info` 构造返回；登录态 Redis 缓存包含用户快照。
- 原调查未找到头像更新端点或写入 `photo` 的 Mapper SQL。`vpn.user` 的**实际数据库 DDL/生产列是否存在，尚未查询证实**，不能仅凭 Java 字段推断。

因此不是简单地“后端再加一个头像字符串”就完成：要核查 schema、增加媒体关联和头像更新事务、补客户端字段，并处理用户快照缓存更新。

### 2.4 已有后端资源与边界

testserverV 原调查文件定位（未重新读取任何生产凭据）：

| 区域 | 文件/事实 |
|---|---|
| TOS SDK | `vvv-common/pom.xml` 已声明 `com.volcengine:ve-tos-java-sdk:2.6.6` |
| 配置对象 | `vvv-common/.../common/config/OssConfig.java` 已有存储配置对象 |
| 当前 TOS 使用 | `vvv-link/.../service/impl/handle/tts/OssServiceImpl.java` 用于 TTS 服务端写桶；未发现直传签名实现 |
| 用户模型 | `vvv-link/.../model/vo/UserVo.java`、`model/po/UserPo.java` |
| 头像写入扩展位置 | `UserController`、`UserServiceImpl`、`UserDao`、`UserMapper.xml` |
| 旧文件路径 | `RecognitionController` 部分文件分支将文件存本机，再交给 Python sidecar；不是通用媒体平台 |
| 认证 | `.json` 拦截器链根据应用 channel 对应的 token header 读取 Redis 登录态，再由 `@UserInfo(required=true)` 获取用户 |

companion-agent 另有 `/v1/uploads`：当前接收原始图片字节，服务端处理后写私有 TOS，返回 upload ID，读取使用约 5 分钟签名 GET。**它不是客户端预签名 PUT 流程**。新方案上线时要保留旧数据解析/迁移适配，不让 Java 和 Python 各自定义一套新媒体身份。

## 3. 服务划分与多应用复用

### 3.1 一个平台模块，而不是再建四个上传服务

第一阶段在 testserverV 内实现 `media` 业务模块，代码按职责分层：

```text
四个 App 的公共上传组件
  ├─ AppServer API：登录认证、上传申请、状态、业务绑定、读取授权
  └─ 独立 HTTPS 传输客户端：按签名 URL PUT / GET 到 TOS

 testserverV / media
  ├─ ApplicationRegistry / IdentityResolver
  ├─ UploadSessionService / QuotaService
  ├─ MediaValidator / ImageNormalizer（有界后台工作队列）
  ├─ MediaRepository / MediaReferenceService
  ├─ ObjectStore（TOS adapter）
  └─ ReadGrantService / GarbageCollector / Audit

聊天、解题、用户头像业务 → 引用 READY mediaId
companion-agent → 通过服务身份调用媒体内部接口，不接受客户端任意 URL
```

以后若容量需要，把 `media` 模块抽成独立服务；对 App 的媒体 ID 和业务 API 不变。不需要一开始引入新的大规模微服务体系。

### 3.2 身份与隔离不能依赖一个可伪造的 appId

统一身份键为：`(environment, appId, identityRealm, subjectId)`。

- `appId` 是服务端应用注册表中的稳定 ID，不是包名显示文字；具体四个值待确认。
- `identityRealm + subjectId` 标识登录身份来源及用户，防止不同应用都叫 `userId=123` 而串数据。
- 现有 User-Agent channel、请求 `appId`、包名、内置在 APK 的 app key 都不是强客户端身份凭据。
- 现有登录系统接入时，服务端应在登录/换票时生成**带 audience/app 授权范围的会话**，Redis 服务端会话元数据或签名令牌必须绑定应用。上传服务从已认证会话推导 `appId`；请求里带 `appId` 只可用于一致性检查。
- 如果现有 token 在多个应用均可使用，未加应用 audience 前，只能宣称“业务命名空间分开”，不能宣称“应用安全边界已完成”。该鉴权改造是多应用正式接入的前置条件。
- 服务到服务调用使用独立身份和 app scopes；不让 agent 持有所有用户的全桶通行证。
- 默认同账号跨应用也**不共享私有图片**。将来共享必须引入用户授权的显式复制/关联流程，不做通配 ACL。

## 4. 桶设计（不启用 CDN）

### 4.1 建议四个新私有桶

下列名称是模板，**不是已经创建、也不是确认可注册的真实桶名**：

| 桶模板 | 用途 | 读取/写入者 | 自动生命周期 |
|---|---|---|---|
| `<org>-media-prod-staging-<suffix>` | 生产直传隔离区 | 客户端仅凭单 key PUT；校验任务读取 | 全部对象 2 天后过期 |
| `<org>-media-prod-private-<suffix>` | 生产正式媒体 | 服务端写，客户端仅签名 GET | 不对活跃业务前缀配置固定过期 |
| `<org>-media-nonprod-staging-<suffix>` | dev/test 直传隔离区 | 非生产身份 | 全部对象 2 天后过期 |
| `<org>-media-nonprod-private-<suffix>` | dev/test 正式媒体 | 非生产身份 | 可按测试数据约定统一清理，不接生产数据 |

理由：隔离区短期清理规则与聊天/头像长期保存规则不同，分桶降低误删风险；生产与非生产不能共享同一凭据。dev/test 在非生产桶再分前缀；如需严格 dev/test 安全隔离，再各拆一对桶。

第一阶段四个应用共享每环境的一对桶，通过服务端授权和前缀隔离。若后续应用属于不同客户、法域或运维团队，再配置每应用独立桶；接口不变。**同一正式桶的应用前缀不等同于物理租户隔离**。

暂不把聊天图片放进已有公开桶、旧 TTS/CDN 路径或不清楚权限边界的共享桶。现有桶是否私有、哪些前缀被其他应用使用需运维确认，不从名字推断权限。

### 4.2 地域与网络

- 优先同 testserverV、校验 worker 和主模型接入点所在地域，减少跨地域下载和处理流量。
- 现有代码配置默认指向北京不代表本次已决定北京；最终 region 需结合真实部署与数据驻留要求确认。
- 对公网 App 使用官方 HTTPS TOS 域名；服务端可在支持且同地域时使用内网 endpoint。
- SDK endpoint 与实际 bucket 虚拟主机 URL 按所选 SDK 生成，禁止手工改写签名 URL 的 host/path/query。
- 不新建 CDN 域名、不要求客户端进行 CDN 切换。未来加 CDN 只改读授权实现，业务继续引用 `mediaId`。

### 4.3 Object key 规范

服务端生成、不含邮箱/手机号/姓名/原文件名；示例仅用于说明：

```text
隔离桶：v1/<env>/<appId>/<purpose>/<yyyy>/<mm>/<dd>/<uploadId>/source.jpg
正式桶：v1/<env>/<appId>/<purpose>/<yyyy>/<mm>/<dd>/<mediaId>/image.jpg
派生图：v1/<env>/<appId>/<purpose>/<yyyy>/<mm>/<dd>/<mediaId>/thumb-256.jpg
```

- `purpose` 首期只允许 `chat-image | solve-image | user-avatar`，不能传任意路径。
- ID 采用服务端生成的高熵不透明 ID；**ID 不可猜也不能代替鉴权**。
- 时间为服务端 UTC；桶内日期只用于检索和运维，不是业务保留期限。
- 使用数据库中的身份与引用关系授权，不靠解析 key 中的用户信息授权。
- 正式对象只写新 key，不复用 `avatar.jpg` 覆盖旧头像。用户换头像是业务指针切换。
- `Content-Disposition` 使用服务端安全文件名；源文件名如有业务必要另存受限元数据，不拼进路径。

### 4.4 桶与凭据策略

- 禁止公共读、公共写、匿名 List；关闭静态网站入口。服务端签名 GET 是短期访问凭证。
- 开启可用的服务端静态加密和访问审计；KMS、自带密钥等高阶要求按预算另定，不能把手机内置密钥当加密方案。
- 分离签名、校验/写正式对象、清理身份；签名身份只可 PUT 指定 staging 前缀，不可写正式桶。
- 正式读取签名身份仅允许获批前缀 GET，无 List；删除身份独立，不和 AppServer 常规进程共用广泛删除权限。
- 密钥由环境密钥管理/外部 secret 配置注入，不能写进仓库、App、日志。排查既有仓库明文凭据由负责人安排轮换，本文不读取或展示密钥。
- Android 原生传输不依赖 CORS。未来 Web 客户端才增加精确 origin、方法和所需 header 的 CORS；不为方便调试设置无条件 `*`。
- 对象版本控制是否启用由运维确认；启用后需设计非当前版本和删除标记清理，否则账面“已删除”仍可能占空间。不可把版本控制当作用户删除已完成。

## 5. 完整直传流程

### 5.1 正常路径

```text
App                testserverV                  TOS             校验 worker
 | 选择/裁剪/纠正方向/压缩/移除元数据 |              |                    |
 |--申请上传（用途/大小/哈希/幂等键）-->|              |                    |
 |              鉴权、配额预占、生成 uploadId/key     |                    |
 |<--单 key 签名 PUT、必带 headers、有效期--|          |                    |
 |---------------- PUT 原始二进制 ------------------>|                    |
 |<-------------------- 2xx -------------------------|                    |
 |--complete(uploadId, objectKey)----->|              |                    |
 |<--202 VERIFYING--------------------|--持久化任务------------------------>|
 |                                   |              |<---GET 临时图片-----|
 |                                   |              |   验证实际字节、解码|
 |                                   |              |   规范化、去 EXIF   |
 |                                   |              |<---PUT 正式图片-----|
 |                                   |<-------------READY / REJECTED------|
 |--查询上传状态--------------------->|              |                    |
 |<--READY + mediaId------------------|              |                    |
 |--保存消息/解题/头像（mediaId）------>|              |                    |
 |              业务权限复核 + 保存引用 + outbox       |                    |
 |<--业务已保存-----------------------|              |                    |
 |--请求该业务附件的读授权------------>|              |                    |
 |<--短期 GET URL---------------------|              |                    |
 |--------------------- GET ------------------------>|                    |
```

TOS 回调（若后续启用）仅作为加速触发信号，必须验签并复核实际对象；不能替代 `complete` 的幂等语义和归属校验。第一阶段不依赖回调。

### 5.2 为什么必须临时区转正式区

预签名 PUT 在有效期内通常可以重放。若 App PUT 完后服务端只 HEAD 一次就将原 key 标为 READY，旧凭证仍可能把已经审核过的内容替换掉。

因此：

1. 签名只能写 staging key。
2. 校验任务读取一份**有界的真实字节快照**，对这份字节计算校验和、识别 MIME、解码并生成规范化输出。
3. 将实际验证并规范化的输出写入新的正式 key；不能先校验 staging 再不带版本约束地复制“此刻最新对象”。
4. 正式写入成功后发布数据库 READY；客户端从未拿到该 key 的写凭证。签名仍可重放也不会修改已发布媒体。
5. 正式写成功而数据库发布失败，由重试任务恢复或按孤儿处理；不得返回伪 READY。

小图片采用整图有界读取足够；若后续支持大文件，再设计分片上传及版本/条件复制。不要把图片方案直接扩成无限文件上传。

### 5.3 校验层次

- HEAD：核对对象存在、元数据大小；**HEAD、扩展名、Content-Type、ETag 都不能证明内容是合法图片**。
- 流式读取设硬上限，不把未受控图片全部加载内存；哈希以服务端实际读到的字节为准。
- 检查 magic、解码格式、像素总数、边长、内存预算和解码超时，防图片炸弹。
- 默认只处理静态 JPEG/PNG/WebP；拒绝 SVG、可执行内容、动画和未支持 HEIC。HEIC 如从系统选取，客户端先转为允许的静态格式。
- 应用 EXIF 方向后重新编码，移除 GPS、设备标识及其他非必要元数据。输出 MIME 和扩展名由服务端决定。
- 头像裁剪/缩放；聊天/解题生成规范化图片和可选缩略图。输出后再验证尺寸、大小、哈希。
- 内容安全检查与文件安全是不同步骤。需按应用用途制定审核策略；第三方审核涉及图片出端及数据处理，应有明确告知/服务协议。
- 未通过检查保持不可绑定、不可签名读取；向客户端返回稳定原因码，不返回 SDK 堆栈或内部 bucket 信息。

## 6. 接口契约 v1（建议）

### 6.1 接入约定

沿用 testserverV 的 `.json` 接口以进入现有拦截器链；外部 URL 可能带 nginx `/vvv` 前缀，应用内部仍为 `/api/...`。

示例展示**解密后的 data**。真实响应继续使用现有 `JsonResult` 信封和项目既有的数据解码方式，不能让 App 以裸 JSON 绕过认证/加密约定。若未来采用 `/api/media/v1/...` 无 `.json` 路径，必须先显式覆盖认证拦截器。

客户端传 `clientRequestId` 作为业务幂等键，不依赖某个可能未被现有网关保留的自定义幂等 header。建议错误用稳定 `code`；HTTP 状态和既有 errno 的映射在实施前定表，不能改变旧客户端的错误解析行为。

### 6.2 申请上传

`POST /api/media/v1/uploads/init.json`

```json
{
  "purpose": "chat-image",
  "clientRequestId": "c940dac2-4152-4e42-b3d6-73ae0d266191",
  "file": {
    "sizeBytes": 234567,
    "contentType": "image/jpeg",
    "sha256": "<64位小写十六进制>",
    "width": 1280,
    "height": 960
  }
}
```

`sizeBytes/contentType/sha256` 必填；尺寸可选且仅为声明，不是校验依据。哈希针对客户端最终准备 PUT 的字节，不是相册原文件。owner、appId、region、bucket 不由客户端传入。

```json
{
  "uploadId": "up_example",
  "status": "ISSUED",
  "objectKey": "v1/prod/app-a/chat-image/2026/11/17/up_example/source.jpg",
  "upload": {
    "method": "PUT",
    "url": "https://<staging-bucket>.<tos-domain>/<key>?<redacted-signature>",
    "headers": { "Content-Type": "image/jpeg" },
    "expiresAt": "2026-11-17T08:10:00Z",
    "declaredSizeBytes": 234567
  },
  "constraints": { "maxSizeBytes": 819200, "policyVersion": "images-v1" }
}
```

- 同一身份、用途、幂等键和相同声明返回同一上传会话；参数不同返回 `IDEMPOTENCY_CONFLICT`。
- 过期凭证通过专用 `renew.json` 获取，重新检查状态/配额/尝试次数；不悄悄改变 init 幂等含义。
- 默认 PUT 有效期建议 **10 分钟**，服务端会话最长建议 24 小时。TTL 是产品策略，不是 SDK 保证。
- `headers` 是真实签名要求的完整必带集合；SDK如支持并验证了 checksum/header 签名，再加入对应字段。

**大小限制重要边界**：不能假设普通预签名 PUT 自带 POST policy 的 `content-length-range` 能力。实施时核对 TOS SDK 2.6.6 实际支持哪些签名约束、长度/checksum 校验和防覆盖能力。未验证前，PUT 只有“客户端按声明上传 + 服务端完成时硬拒绝”，仍存在恶意超大对象造成存储/流量费用的风险。若无法在 TOS 入口限制且风险不可接受，改用已验证支持长度条件的预签名 POST policy；`upload.method` 及 `fields` 可扩展，三步业务流程不变。不能用“先 PUT 后删除”声称入口已限流。

### 6.3 完成、状态与取消

`POST /api/media/v1/uploads/complete.json`

```json
{ "uploadId": "up_example", "objectKey": "<init返回的完整key>" }
```

服务端查该用户/应用的 upload 会话，key 必须和记录完全一致；不会按传来的 key 查桶并替用户创建记录。原始 SHA-256 必须与 init 一致。发起后台校验，返回 `VERIFYING`；重试返回现有状态，不重复处理/扣配额。

`POST /api/media/v1/uploads/status.json`

```json
{ "uploadIds": ["up_example"] }
```

建议每批最多 20 个，本机以约 1/2/4 秒退避、最长 10 秒间隔查询；切后台停轮询，恢复后重查。服务端同时返回 `retryAfterMs`。完成后不持续轮询。

```json
{
  "uploads": [{
    "uploadId": "up_example",
    "status": "READY",
    "media": {
      "mediaId": "med_example",
      "purpose": "chat-image",
      "contentType": "image/jpeg",
      "sizeBytes": 210000,
      "width": 1280,
      "height": 960,
      "sha256": "<规范化后的实际哈希>"
    }
  }]
}
```

`POST /api/media/v1/uploads/cancel.json`：仅取消本人未绑定上传，幂等；不会撤回已经发给 TOS 的 URL，凭证到期前 staging 仍可能收到重放。取消后 complete 被拒，正式媒体不发布，临时生命周期最终清理。

状态机：

```text
ISSUED → VERIFYING → READY（等待业务绑定）
    ├→ EXPIRED      └→ REJECTED
    └→ CANCELLED

READY 媒体 → 多条独立业务引用；最后引用解除后 → GC_PENDING → DELETED
```

上传会话和媒体引用不能混成一个简单 `referenced: Boolean`：一张图片可能有多条业务引用，任何一个业务删除都不能提前删掉其他引用。

建议稳定错误码：`AUTH_REQUIRED`、`APP_SCOPE_MISMATCH`、`PURPOSE_NOT_ALLOWED`、`QUOTA_EXCEEDED`、`UPLOAD_EXPIRED`、`OBJECT_NOT_FOUND`、`OBJECT_KEY_MISMATCH`、`SIZE_MISMATCH`、`HASH_MISMATCH`、`UNSUPPORTED_IMAGE`、`PIXEL_LIMIT_EXCEEDED`、`CONTENT_REJECTED`、`VERIFY_FAILED_RETRYABLE`、`MEDIA_NOT_READY`、`MEDIA_IN_USE`。越权 ID 统一按不存在返回，避免枚举。

### 6.4 私有读取

业务接口先返回稳定 `mediaId` 及描述，可附短期 read grant 作为优化。刷新使用：

`POST /api/media/v1/read-grants.json`

```json
{
  "items": [{
    "mediaId": "med_example",
    "variant": "image",
    "context": { "kind": "chat-message", "id": "msg_example" }
  }]
}
```

- 服务端根据 context 查真实业务关系：是否有权读这条消息/解题记录/该头像，不信任 context 本身；用户本人访问尚未绑定的 READY 媒体另走 owner 权限。
- 同一身份内媒体存在不等于任何业务上下文都能使用；上传时的 purpose 限制绑定种类。
- 返回官方 TOS HTTPS 签名 GET、`expiresAt`、实际内容哈希、variant。建议 TTL **5 分钟**，一次最多 20 项。
- 不返回长期可用的全桶密钥、目录 List 权限或宽泛 STS policy。
- 签名 URL 是 bearer secret；获取后他人持有也能在到期前读取，禁日志、埋点、公开导出。HTTP Referer 不是授权边界。
- 客户端以 `mediaId + variant + 内容版本/哈希` 缓存，不以签名 query 为缓存身份；TTL 快到期或 GET 明确因过期失败时只刷新重试一次。
- 删除/权限撤销会阻止新的 read grant；已签发 URL 在到期前可能仍有效，离线缓存也不能远程强制收回。对外承诺必须包含这个窗口。
- 不通过“返回任意 URL，由服务端下载”实现读取；防 SSRF、域名跳转及签名外泄。

## 7. 数据模型与业务绑定

建议媒体模块使用 testserverV 的事务型数据库（与现有 MySQL/MyBatis 体系一致），不为图片本身引入另一套数据库。逻辑表如下，完整 DDL 在实施时匹配当前数据库版本。

### 7.1 `media_upload`

`upload_id`、`env`、`app_id`、`identity_realm`、`owner_subject_id`、`purpose`、`client_request_id`、声明大小/MIME/SHA-256、`staging_bucket_alias`、`staging_key`、`status`、`credential_expires_at`、`session_expires_at`、`media_id`、`attempts`、`policy_version`、`error_code`、`created_at/updated_at`。

唯一约束：`(env, app_id, identity_realm, owner_subject_id, purpose, client_request_id)`；状态更新带版本或 CAS，complete 和 GC 任务不能同时发布/删除同一会话。

### 7.2 `media_object`

`media_id`、身份与用途、`bucket_alias`、`object_key`、规范化 MIME/大小/宽高/SHA-256、审核状态、处理版本、`created_at`、`unbound_expires_at`、`gc_not_before`、`status`、`version`。

- `object_key` 唯一；不存签名 URL。
- 使用逻辑 bucket alias 屏蔽迁桶配置；数据库记录真实存储映射的版本。
- 原始哈希在 upload 表，规范化输出哈希在 object 表，二者可能不同。

### 7.3 `media_reference`

`reference_id`、`app_id`、`business_type`、`business_id`、`slot`、`media_id`、`state`、`created_at/released_at`。

业务槽唯一：`(app_id, business_type, business_id, slot)`。一条消息多图以稳定 slot 排序；头像用 `profile-avatar` 单槽。存储删除以实际有效引用为准，引用计数如有只能是可重建缓存，不能成为唯一真相。

### 7.4 原子性与跨服务一致性

- testserverV 自己的业务：校验媒体权限和 READY → 锁定媒体状态 → 写消息/头像/解题记录 → 写 media reference → 提交同一事务。GC 使用相同锁/CAS 规则复核引用，避免“刚绑定就被清理”。
- 外部 agent/其他 AppServer：使用受信服务身份申请短期 binding lease，媒体服务持久化 lease 后返回 token；业务写库和 outbox 在自己的事务中完成，后台幂等提交引用。未提交前 lease 阻止 GC。
- lease 续约/到期、业务提交失败、提交后响应丢失需要补偿/对账。仅依赖“先写业务后调一次媒体服务”会留下悬空引用，不能作为上线实现。
- 读授权由所属业务服务确认关系，媒体服务负责解析对象和签名；不要求媒体服务直接读取所有业务数据库。
- 账号注销、消息删除通过业务事务/outbox 释放引用，持久化重试和审计，不以一次异步 HTTP 调用成功为前提。

## 8. 三类业务接入

### 8.1 图片消息

建议消息请求新增 `attachments`，不是用 URL 填进文本：

```json
{
  "clientMessageId": "cm_example",
  "sessionId": "session_example",
  "text": "帮我看看这道题",
  "attachments": [{ "type": "image", "mediaId": "med_example" }]
}
```

- 先显示本地缩略图及上传状态；全部所需媒体 READY 后，再原子创建服务端用户消息和引用。
- **消息保存**与**模型生成**分状态：消息已保存但模型调用失败，图片仍然保留，不能把整个用户消息和附件抹掉。
- `clientMessageId` 保证断网重发不重复创建消息；模型重试引用同一消息/媒体，不重新上传。
- 历史消息接口返回结构化 attachments，而不是仅文字；客户端按 ID 拉图。旧客户端可得到兼容文字提示，不能将私有签名 URL 固化到旧消息文本里。
- 服务端调用视觉模型时，由内部媒体 resolver 授权取图：支持短期 URL 时签发用途受限读 URL；不支持时有界下载转成模型需要的输入。不能直接允许任意用户 URL 给模型抓取。

### 8.2 拍照解题

建议为现有 solve 请求增加媒体引用模式（具体旧接口兼容字段在实施时定稿）：

```json
{
  "clientRequestId": "solve_example",
  "subject": "math",
  "imageMediaIds": ["med_solve_example"],
  "saveHistory": true
}
```

- 裁剪/透视校正后的**实际提交图**存桶，不默认上传整张原始相册照片，减少无关人脸/住址信息。
- 上传后先创建可追踪解题任务及引用，再调用模型；模型失败可在同一个任务上重试，不反复传图。
- `saveHistory=true` 的任务/结果引用持续保留；`false` 明确按临时策略到期，UI 告知不能日后恢复原图。
- 原 multipart 接口灰度保留；旧客户端可以由服务端适配到同一媒体服务，但会继续产生中转流量。不能静默改旧请求字段导致老版本不可用。
- 实测题目细字清晰度，确认 1600px/800KiB 是否够；如不足，增加独立版本的 `solve-image` 策略，而不是悄悄放宽所有图片配额。

### 8.3 账号头像

建议新增 `POST /api/user/avatar/update.json`：

```json
{ "mediaId": "med_avatar_example", "clientRequestId": "avatar_change_example" }
```

服务端事务：

1. 鉴权，确认媒体同 app、同 owner、用途 `user-avatar` 且 READY。
2. 写 `avatar_media_id`（建议新增稳定关联字段，DDL 核查后决定放用户表还是按应用 profile 表）。
3. 创建新引用、释放旧头像引用、增加 profile/avatar version。
4. 刷新/失效相关用户快照缓存及资料缓存；多 token/多设备会话不应长期持有旧头像。
5. 返回新 `avatarMediaId`、`avatarVersion` 和可选短期 `avatarUrl/expiresAt`；客户端立即换图。

兼容策略：

- 服务端现有 `photo` 若已有实际数据，先保留读取，不把历史值批量清空。新字段 `avatarMediaId` 为真相来源。
- 客户端 `UserInfoVo` 补相应字段，UI 用加载器取图并有字母/默认图兜底。
- `photo`/`avatarUrl` 如作为兼容显示字段，可返回临时 URL，但必须同时给过期时间；老客户端没有刷新机制时允许保留默认头像，不因此开放整个桶。
- 明确头像可见范围：首期建议“自己和具有资料查看权限的会话参与者”，不是公共匿名可见。若要全站公开头像，另设计审核与公开派生图策略，不把解题/聊天桶改为 public。
- 四个应用是否共用同一张账号头像尚待确认；默认按应用 profile 隔离。业务 profile 的主键必须和选定账号体系一致。

## 9. 客户端公共组件与本地消息迁移

### 9.1 公共接口

建议在共享模块提供 `MediaUploadRepository` / `MediaResolver`，三个业务只传 `purpose` 与本地文件，不能自己拼桶路径。SDK 包装暴露状态：

`LOCAL_READY → REQUESTING → UPLOADING → VERIFYING → READY → BUSINESS_SAVING → SENT`，失败带可重试阶段。

网络分两条：

- AppServer 控制面继续用现有 BaseService、认证与信封解码。
- TOS PUT/GET 使用专用传输客户端，**不能自动携带 App 登录 token、Referer、业务 AES body 包装、URL host 替换或会打印完整 URL 的拦截器**。只发签名返回要求的 headers 和二进制。
- 禁止自动跨域携带授权或对签名 URL 跟随未验证重定向；不要手工编码/解码签名 query。
- `DnsRequestInterceptor.java` 存在 host 改写和 URL 日志代码；是否被挂载需实施时追踪。独立 TOS client 不装这些拦截器，避免签名/证书问题和凭证泄露。

### 9.2 本地结构

新附件结构建议：`attachmentId`、`mediaId?`、`localFilePath?`、`contentHash?`、`width/height`、`uploadId?`、`uploadStatus`。签名 URL 及其过期时间只作可刷新缓存。

- `localFilePath` 用于立即预览、离线和重试；`mediaId` 用于跨设备恢复，二者不能互相覆盖。
- 本地任务按当前登录 app/account 分区，退出账号停止上传并清理授权和可清理缓存；切换账号不能让新账号接着提交旧账号的任务。
- WorkManager 等后台调度只处理显式待发送任务；遵守网络和系统后台限制，不把前台 UI coroutine 当持久上传任务。
- 文件在 READY/业务绑定前不随意删除；上传完成后按缓存策略清理，不能清掉仍在等待重试的唯一文件。

### 9.3 旧数据迁移

1. 先升级读模型：新 `mediaId`、旧 HTTP URL、本地路径均能识别；明确远端不可用与本地丢失的占位状态。
2. 用户授权联网且文件还在时，在后台有速率限制地补上传本地图片；按旧消息 ID 幂等绑定到所属服务端用户消息。
3. 仅能凭服务端消息归属验证建立引用，不能让客户端把文件挂到任意历史消息；没有服务端对应消息的本地记录保留本地，不伪造云端消息。
4. 旧本地图片可能与服务器文字不对应，批量迁移需抽样对账，不按列表下标匹配。
5. 文件已经丢失时无法恢复，不承诺“上线新桶即可找回全部历史图片”。
6. 历史接口正式返回附件后，再调整 MessageCache 合并规则；不能提前删掉保留本地富附件的兼容逻辑。

## 10. 配额、保留与清理策略

### 10.1 建议初始策略（需用流量和账单校准）

| 用途 | 客户端建议处理 | 服务端输入硬上限 | 正式业务保留 |
|---|---|---|---|
| `chat-image` | 长边≤1600px，q82→60，目标≤800KiB | 800KiB；边长≤4096、像素≤16MP | 消息有效期间保留 |
| `solve-image` | 校正后长边≤1600px，保细字 | 初期800KiB；边长≤4096、像素≤16MP | 有历史：记录有效期间；无历史：任务结束后24小时 |
| `user-avatar` | 方形裁剪，长边≤512px | 256KiB；边长≤2048、像素≤4MP | 当前头像及仍有效业务引用期间 |

这些是建议的 **v1 图片策略**，不是现有服务端上限。客户端尺寸和服务端安全上限分层；服务端规范化输出也必须满足策略，不允许“大输入处理后小输出”绕过输入配额。

建议每用户同时最多 3 个活跃上传会话、每应用有独立日累计字节和存储预算；具体日限额不凭空定商业套餐。申请时预占声明大小，校验完成后结算实际大小，失败释放；全局/应用并发和 worker 内存有上限。

对 PUT 入口无法强制限制的恶意超大文件，需结合短 TTL、签名发行速率、独立 staging 费用告警、存储侧能力与风控处置。应用 API 限流不能完全阻止已签发 URL 重放。

### 10.2 清理流程与误删防护

| 状态/类型 | 建议清理时间 | 谁负责 |
|---|---|---|
| staging 对象（成功/失败/取消均含） | 桶生命周期2天兜底；成功后可提前异步删除 | TOS 生命周期 + 清理任务 |
| READY 但未绑定 | 24小时后进入候选清理 | 媒体 GC |
| 最后引用释放 | 至少24小时宽限，再复核删除 | 媒体 GC |
| 删除/注销法律或产品另有要求 | 遵照明确期限，不能统一无限延迟 | 业务与隐私策略 |
| 已生成正式对象但无DB记录 | 对账确认创建超过48小时且无发布/处理中任务 | 孤儿对账任务 |
| 处理失败重试任务 | 有界退避、最长窗口与死信队列 | worker |

- staging 2天生命周期大于会话24小时有效期；校验必须在有界时间内完成，积压预警，不能让会话承诺长于对象寿命。
- 正式桶不能按“创建后30天”一刀切 TTL，否则活跃聊天图片也会消失。
- GC 分“标记、复核、删除、确认”多阶段；绑定与删除争用同一个媒体版本/锁。
- 删除任务幂等、持久化重试；TOS 删除成功但数据库更新失败要可恢复。存储侧失败时不能直接标“物理已删”。
- 保留最小审计墓碑，不保留签名 URL 或图片内容；账号删除应包含派生图、版本和可控缓存范围。
- 存储故障时禁止把未知状态当成“无引用”清空桶；对账先 dry-run，设置单批删除上限与人工审批阈值。

## 11. 运维与实施前置检查

### 11.1 可观测性

按 app/purpose 统计申请成功率、PUT完成率、校验耗时/失败码、业务绑定率、签名GET失败率、未绑定/孤儿字节、清理失败、每应用存储/请求/出网费用。

日志只记录脱敏 app、uploadId、mediaId、请求追踪 ID、稳定错误码和阶段；不记录 token、完整签名 URL、原始图片/base64、姓名或路径中的私有信息。异常率告警区分业务拒绝与 TOS/签名配置故障，避免所有失败都静默回退旧 multipart。

### 11.2 必须验证的供应商能力

在独立非生产桶里用虚构/无敏感图片验证：

- 当前 Java TOS SDK 的预签名 PUT、有效期、必带 Content-Type/checksum header 与实际设备请求兼容。
- 普通 PUT 是否能签定长度、TOS是否实际强制该长度；POST policy 长度条件是否可用。未验证前不把能力写为已支持。
- 签名 URL 重放/覆盖、过期、错误 clock、断网半途重传的行为。
- 官方域名、TLS、虚拟主机寻址、区域错误、签名 GET 返回的缓存/内容响应头。
- 私有桶拒绝匿名读/List、仅单 key 可写、生产/非生产密钥不能交叉访问。
- 如果引入 Content-MD5 或 TOS checksum，按官方算法和SDK核对；ETag 不等同通用 MD5，也不等同本文 SHA-256。

本轮没有调用 TOS 或操作真实桶，没有验证以上能力；不能将原 `SignatureDoesNotMatch` 直接归因为某一个 secret 错误，endpoint、region、寻址、时间和签名 headers 均可能参与。

## 12. 实施顺序与验收

### 阶段 A：基础与安全边界

核定应用/账号体系、bucket地域/权限、保留策略 → 设计表迁移和应用scope会话 → 实现媒体模块、worker与供应商适配 → 非生产验收。此阶段不动旧聊天历史。

### 阶段 B：以头像打通完整闭环

核查用户表并新增稳定关联 → 单张头像直传/校验/绑定/私有读取 → 客户端字段与 UI → 多设备缓存一致性 → 替换及删除旧引用。头像是最小闭环，不代表聊天和解题已完成。

### 阶段 C：图片消息与解题

先补服务端结构化附件和历史读取 → 接入公共客户端组件 → 解题引用与任务记录 → agent内部媒体授权适配 → 按应用灰度，保留旧接口。随后才迁移旧本地附件。

### 阶段 D：多应用接入与退役旧链路

其余应用使用注册表与策略接入，不复制新端点；验证 app 隔离后推广。旧 multipart 仅在明确兼容窗口内保留，监控使用量后再退役，不自动删除历史落盘文件。

### 验收清单

- [ ] 四应用身份 scope 与真实登录系统对应；伪造 UA/appId 不能访问别的应用媒体。
- [ ] 非本人、非可读业务资源的 init/complete/read/bind 全被拒；自有媒体也不能绑定到他人消息。
- [ ] PUT成功但complete丢失、verify失败、业务保存失败均可恢复且UI状态准确。
- [ ] 同幂等请求反复提交不重复扣配额、不重复消息、不产生重复头像引用。
- [ ] 签名PUT重放无法修改READY正式内容；校验与正式写入使用同一份字节快照。
- [ ] 超大文件、伪MIME、图片炸弹、EXIF/GPS、损坏图和哈希不符被正确处理。
- [ ] GET URL过期后可刷新；URL不进入日志；登录token不发送到TOS。
- [ ] 换设备可从历史恢复新图片；旧本地图片存在/丢失分别有正确迁移或占位结果。
- [ ] 解题细字可读、上传失败不丢唯一源文件；模型失败不丢已保存消息。
- [ ] 换头像、多token登录、退出/切账号及注销均不串图。
- [ ] GC与绑定竞态、跨服务lease/outbox补偿、TOS故障及DB故障有故障注入覆盖。
- [ ] 所有桶匿名读/List关闭；未绑定图可清理，仍有效引用图不被生命周期误删。
- [ ] 各应用费用/容量可单独归集，上传滥用和临时桶增长有告警。

这是一份设计验收表；本轮未实现、未运行这些用例，也未跑构建或单元测试。

## 13. 仍需确认的产品/运维决策

以下不阻止评审方案，但在实施/建桶前必须定稿：

1. 四个应用的正式 `appId`、负责人、账号是否共用，以及登录 token audience 如何落地。
2. 服务器和模型所在地域、数据驻留要求、TOS region；新桶名及成本归属。
3. 是否同意四个新私有桶，还是因现有运维边界需要按应用拆桶；现有桶不作默认复用。
4. 头像是否跨应用共用、是否允许公开展示；默认私有/按业务授权读取。
5. 解题是否默认保存原题图，保存期限、删除入口，以及细字质量是否需要放宽800KiB策略。
6. 每应用用户配额、整体存储/出网预算，内容审核供应商及图片处理告知。
7. `vpn.user` 实际 schema、历史 `photo` 是否有数据、四应用资料表归属；本轮没有查询生产库。
8. companion-agent 与其余后端接入统一媒体服务的服务身份及跨库引用/删除责任。
9. TOS入口大小约束实测结果，以此最终选择 PUT 或 POST policy；不能未经验证直接宣传硬限制。

## 14. 与其他设计的边界

- 本文只设计存储与上传，不修改摄像头、MediaPipe、双向 action 协议或 prompt。
- 端侧摄像头画面不因本方案自动上传；相机行为识别与贴纸属于另一份交互设计，默认图片/视频不出端。
- 角色形象包、视频、音频、通用文件可未来复用媒体模块的身份/引用框架，但需要各自安全校验、大小限制和版权审核，不直接开放图片purpose任意上传。
- 先前四份研究的总汇编尚属另一项文档工作；本文不把未经重新核实的网络归因、SDK能力或进度估计当成当前事实。
