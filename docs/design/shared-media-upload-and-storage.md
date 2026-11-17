# 多应用共享图片直传与 TOS 存储设计

> 状态：**部分已实施**（v4）。§8 的 TOS 凭据修复、图片进模型、聊天图片走 `/v1` 已落地；
> 客户端直传（§5）、头像与解题闭环（§7.2/§7.3）、签发记录表（§9）仍是提案。
> 日期 2026-11-17（v3 引入登录态鉴权；v2 的应用级 HMAC 已撤销，见 git 历史）。
> 适用：当前 Android 应用，以及计划复用流程的另外三个应用。
> 范围：解题图片、聊天图片、账号头像。

## 1. 结论

1. 客户端自己压缩后**直传 TOS**；服务端只签发一个短期、单 key、**只能写**的 PUT 凭证。
2. **上传成功即成功**。服务端不读回、不校验、不做隔离区转正式区、不做图片规范化。写进去的字节就是最终字节。
3. 签名接口用**登录态校验，和普通接口同一条路**：同一个 token 头、同一套校验，验出 user_id。没有登录态就不能传图。
4. 服务端只保留一件事：**记录签发了什么**。用于配额、归属校验（谁能把哪个 key 绑到自己身上）和清理。不做内容校验。
5. 消息里的图片**只存本地路径，不跨设备**。objectKey 只是给模型看的那一份。

## 2. 与 v1 的差别

v2 只是中间稿，没实施过；下表直接对比 v1 和现在（v3）。

| v1 | 现在 | 原因 |
|---|---|---|
| 隔离区桶 + 校验 worker + 转正式区 | 无，直接写目标位置 | 上传成功即成功 |
| 服务端读回字节、验 magic、解码、去 EXIF | 无 | 同上 |
| 服务端重新编码成规范化图片 | 无，客户端压缩结果就是最终图 | 端上已有 ImageCompressor |
| 登录 token 鉴权 | **仍是登录 token**，复用普通接口的校验路径 | v2 曾改为应用级 HMAC（不登录也能传），已撤销：没有这个需求 |
| 消息存 `mediaId`、可跨设备恢复 | 消息存本地路径，不跨设备 | 你的决定 |
| 5 个桶（prod/nonprod × staging/private） | `sspacee` 一个桶 + 前缀 | 桶是扁平命名空间，没有子桶，见 §3 |
| 状态机 ISSUED→VERIFYING→READY→BOUND | ISSUED→BOUND | 没有校验步骤了 |
| init / complete / status 三次调用 | sign 一次，PUT 一次 | 没有 complete 的必要 |

**必须知道的代价**：没有人检查传上去的是不是图片。一个被改过的客户端（已登录的）可以把任意字节塞进桶，最多到单文件上限。缓解见 §10。

## 3. 桶与前缀

```text
provider:        volcengine_tos
endpoint:        https://tos-s3-cn-beijing.volces.com   （S3 兼容协议）
bucket:          sspacee
region:          cn-beijing
forcePathStyle:  false  → 虚拟主机寻址 https://sspacee.tos-s3-cn-beijing.volces.com/<key>
```

AK/SK 由环境变量注入（`JC_S3_ACCESS_KEY` / `JC_S3_SECRET_KEY`），**不进仓库、不进本文、不进 APK**。服务端用它签 presigned URL、也用它读图。

### 3.1 「子桶」就是前缀，不是桶

TOS/S3 的桶是**扁平命名空间**，不存在嵌套桶。所以「sspacee 下面我们的那块」在技术上只能是 `sspacee` 桶里的一个 **key 前缀**，靠它划地盘，不靠桶。

现实已经在这么干了：companion-agent 今天就用这个桶，对象放在前缀 `companion/` 下（测试环境 `companion-test/`，`config.py` 的 `tos_key_prefix`）。本方案的新对象统一放 `media/` 前缀，两个前缀互不重叠，清理任务各扫各的、**硬编码自己的前缀，绝不遍历桶根**。

另外两点因为「桶是已有的、共享的」而必须先确认：

- **ACL 必须先确认**：如果这个桶是公共读（名字看着像图床），那么图一上传就等于公开。头像、解题图能不能接受公开，需要先定。不能公开就先改桶策略或换私有桶。
- **前缀不是安全边界**，它是地盘约定。真正的边界是：接口要求登录，签名服务只肯给当前用户签他名下的 key，业务绑定只认这个对应关系。

### 3.2 object key

```text
media/<env>/<appId>/<scene>/<yyyy>/<mm>/<dd>/<keyId>.<ext>
例：media/prod/app-a/chat-image/2026/11/17/7Kq2mZ0pXr4vB1nT8sLcWa.jpg
```

- `env`：`prod` / `dev`
- `appId`：稳定应用标识（现有接口都带的应用头即可），只用于 key 命名空间和按应用限流，**不承担鉴权**
- `scene`：`chat-image` / `solve-image` / `user-avatar`，白名单
- `keyId`：服务端生成的 22 位 URL-safe 随机串，不可猜
- `ext`：由 contentType 推出，不信客户端文件名
- key 不含邮箱、手机号、姓名、原文件名

## 4. 鉴权：就是一条普通登录接口

签名和读取接口与所有业务接口走**完全相同的登录校验**：同一个 token 头（客户端 `BaseRequest` 自动注入的那个），服务端用现有的 `CredentialVerifier` 验出 user_id。接口本身不发明第二套鉴权、不要 nonce、不要 HMAC 签名头。

companion-agent 的 `/v1/uploads` 今天就是这个形态（`Depends(current_user)`），新接口向它看齐。

由此确定几件事：

- **没有登录态就不能传图。** v2 设想的「有的接入方不用这套登录，也要能传图」不成立了；所有接入方先用同一套登录。
- 验出来的是**用户**，不只是应用。配额、归属、清理都可以按 user_id 算；`appId` / `scene` 仍在请求里，用途只有命名空间（key 前缀）和按应用限流。
- 身份就是 token 里的 user_id。v2 里 `identityRealm` / `subjectId` 这类自报字段全部删掉——不需要了。
- 未登录/过期的返回与全站一致（401 + 现有刷新逻辑），不需要新错误码。

代价：接入方必须有这套登录。这是明确接受的前提，见 §13 待确认第 2 条。

## 5. 上传流程

```text
App                          testserverV                    TOS
 | 压缩/裁剪/去 EXIF            |                              |
 |--sign(token, scene, 声明)-->|                              |
 |                             | 验登录态、查配额、写一条签发记录 |
 |<--objectKey + PUT URL-------|                              |
 |----------- PUT 二进制 ------------------------------------>|
 |<-------------------------- 200 ----------------------------|
 | 直接用 objectKey，不回报                                     |
```

**没有 complete。** objectKey 在签发时就知道，PUT 一旦 200 对象就在那儿了；没 200 就当失败。少一次往返、少一个状态机、少一类「PUT 成功但 complete 丢了」的补偿逻辑。

PUT 成功后客户端拿 key 去：

- 聊天：随消息发 `attachments: [{ "type": "image", "objectKey": "..." }]`
- 解题：`imageKeys: ["..."]`
- 头像：`POST /api/user/avatar.json { "objectKey": "..." }`

业务接口入库时校验：key 在我们的前缀里、在签发记录里、属于同一用户、且没被别人绑过。通过就标 BOUND、写引用。**这是归属校验，不是内容校验。**

服务端要读图时（模型看解题图、agent 看聊天图），直接用 SDK 凭 AK/SK 按 key 读，不经过公网签名 URL。

## 6. 接口契约

### 6.1 sign

```json
POST /api/media/v1/sign.json          （带普通登录 token 头，同其他业务接口）
{
  "scene": "chat-image",
  "contentType": "image/jpeg",
  "sizeBytes": 234567
}
```

`contentType` 只允许 `image/jpeg | image/png | image/webp`。`sizeBytes` 是声明值，服务端只按上限比对；**PUT 时不会强制**（见 §10）。

```json
{
  "objectKey": "media/prod/app-a/chat-image/2026/11/17/7Kq2mZ0pXr4vB1nT8sLcWa.jpg",
  "upload": {
    "method": "PUT",
    "url": "https://sspacee.tos-s3-cn-beijing.volces.com/media/...?X-Amz-Signature=...",
    "headers": { "Content-Type": "image/jpeg" },
    "expiresAt": "2026-11-17T08:10:00Z"
  },
  "limits": { "maxSizeBytes": 819200, "putTtlSeconds": 600 }
}
```

`headers` 必须原样带，签名算进去了。PUT 有效期建议 10 分钟。

### 6.2 读

只有客户端需要看图时才签 GET，同一个登录态：

```json
POST /api/media/v1/read.json
{ "objectKeys": ["media/..."] }
```

返回每个 key 的短期 GET URL + `expiresAt`，TTL 建议 10 分钟。服务端校验 key 签发给了当前用户。

**聊天图片不需要这条路**：消息存本地路径、不跨设备，图是给模型看的。头像和解题历史才用得上。

### 6.3 错误码

业务错误：`SCENE_NOT_ALLOWED`、`CONTENT_TYPE_NOT_ALLOWED`、`SIZE_NOT_ALLOWED`、`QUOTA_EXCEEDED`、`KEY_NOT_FOUND`、`KEY_NOT_OWNED`、`KEY_ALREADY_BOUND`、`KEY_EXPIRED`。

鉴权失败**不自造错误码**，用现有接口未登录的同一套（401 + 现有刷新/登出流程）。

## 7. 三类业务

### 7.1 图片消息

- 客户端用本地文件立即显示；objectKey 随消息发给服务端。
- 服务端校验归属后落库，模型按 key 直接读桶。
- 发送失败可重试，key 不变，不重传图。
- 换手机不带图（你的决定）。历史接口不把 objectKey 返给别的设备渲染。

### 7.2 解题

- 只传裁剪/透视校正后的图，不传整张相册（减少无关人脸、住址）。
- `saveHistory` 决定解完之后 key 留不留；不留的按 24 小时清理。
- **细字清晰度是这条链路的真问题**：1600px / 800KiB 是聊天图参数，解题图很可能糊。建议给 `solve-image` 单独一条上限（如 2000px / 1.5MiB），而不是放宽所有图。

### 7.3 头像

- 方形裁剪，长边 ≤ 512，≤ 256KiB。
- `POST /api/user/avatar.json { objectKey }` 校验归属后写 `avatar_media_id`。
- 客户端 `UserInfoVo` 当前没有头像字段，需要补；返回 `avatarKey` + 短期 `avatarUrl` + `expiresAt`，UI 用加载器取图并有字母/默认图兜底。
- 旧头像 key 释放引用，进 GC。
- 服务端现有 `UserVo.photo` / `UserPo.photo` 已存在但未证实库里有数据；先保留读取，新字段 `avatar_media_id` 为真相来源。

## 8. 图片怎么进模型：已改了什么

「客户端压缩 → 上传 → 服务端读桶 → 模型看图」这条链原来**只通了一半**：图在桶里，模型从来没见过。现在三段都接上了。

### 8.1 先修那个挡住一切的 bug

`POST /v1/uploads` 一直 500，`SignatureDoesNotMatch`。2026-11-17 实测确认了根因，**不是代码问题**：

| 检查 | 结果 |
|---|---|
| 用 job-come 的 `JC_S3_*` 直连 `sspacee`（head/put/get/delete） | 全部 200 |
| job-come 的 AK 与 companion 的 AK | 同一个（sha 前缀相同，47 字符） |
| job-come 的 SK 与 companion 的 SK | **不同**：60 字符 vs **58 字符** |
| 用 companion 原 `.env` 跑 `UploadService.accept` | 复现 `SignatureDoesNotMatch` |
| 换成 job-come 的 SK 再跑同一段代码 | 上传、签名 GET、删除全通 |

即：companion 的 `TOS_SECRET_KEY` 被截断/过期，与 AK 不配对，而 `companion/` 前缀下 **0 个对象**（`companion-test/` 有 2026-11-09 的残留），说明这条路径从没成功过。

**修了三处**：`.env` 的 SK 换成 job-come 那一把（两个项目本来就共用同一对凭据）；`assert_storage_reachable` 在装配时 HEAD 一次桶，配错就**拒绝启动**；`.env.example` 写明这套变量与 job-come 的对应关系。

> 顺带排除了两个常见误诊：region 填错（`cn-north-1`、`us-east-1` 都返回 200）和路径风格（path style 返回 403，virtual 才是对的）。所以只有 SK 这一种解释成立。

### 8.2 图片真的到模型了

原来 `TurnInputMessage` 只有 `message_id` 和 `text`，`_load_inputs` 只取 `record.content`，附件躺在 `meta["attachments"]` 里没人读。现在：

```text
消息 meta["attachments"]
  → _load_inputs 取出 upload_id（_attachment_ids）
  → TurnInputMessage.upload_ids
  → TurnRunner._load_images：逐个 get_owned 校验归属 → 读字节 → data URL
  → TurnRequest.images {消息主键: (data URL, ...)}
  → ImageAttachmentMiddleware：只改这一次模型请求的消息内容
```

三个设计决定，都不是随意的：

- **走 base64 data URL，不签 GET URL。** 模型网关只吃 URL 或 base64，而桶是私有的。签名 URL 塞进模型请求等于把 bearer secret 交给第三方，还要担心 5 分钟过期。服务端有 AK/SK，直接读字节最省事也最安全。
- **只改 `request.messages`，不碰 `state`。** 对话线程由 LangGraph checkpoint 持久化，每轮把历史重放给模型。一张 800 KiB 的图转 base64 约 1 MB；几轮之后 checkpoint 会涨到几十 MB，每轮还要重新序列化和重发。中间件只改写这一次请求，checkpoint 里存的始终是纯文字。
- **先 `get_owned` 再读。** 否则猜中一个 ULID 就能让模型看别人的图。别人的 `upload_id` 走的是「不存在」那条路，与读失败一样只跳过这一张并记日志——文字照常进模型，一张图坏了不拖垮整轮。

### 8.3 三类图同一条链路

头像、消息图片、解题图片**共用**同一条通道，不各自发明：

| | 场景 | 现状 |
|---|---|---|
| 消息图片 | `chat-image` | **已改**：`sendAttachment` 的 IMAGE 分支走 `/v1/uploads` + `submitWithAttachments` |
| 头像 | `user-avatar` | 待接（§7.3 的 `avatar_media_id` 闭环） |
| 解题图片 | `solve-image` | 待接（§7.2，建议单独放宽到 2000px / 1.5MiB） |

底层只有一份：`UploadService`（校验类型与像素、去 EXIF、统一转 JPEG、写 `TOS_KEY_PREFIX` 下的对象）+ `ObjectStore`。

**文件（非图片）仍走 legacy。** `/v1/uploads` 只认四种图片格式，`ChatRepository.sendAttachment` 的 FILE 分支继续委托旧 multipart 接口。这是能力缺口，不是绕路——要迁得先在 `/v1` 上补文件类型。

### 8.4 agentkit 的改动

`agentkit/storage/object_store.py` 加了 `presigned_put_url`（只签单 key 的写权限），给后续「客户端直传」用；今天 chat 走的是服务端中转，还不依赖它。

companion-agent 自己的 `TosObjectStore` 是另一份实现（它不走 agentkit 的 `ObjectStore`），本次加了 `get`（读字节给模型）和 `check_bucket`（启动自检）。

## 9. 数据与清理

两张表足够。

**`media_upload`**（签发记录）：`object_key`（唯一）、`app_id`、`scene`、`user_id`、`declared_size`、`content_type`、`issued_at`、`expires_at`、`state`、`bound_ref`、`bound_at`。

**`media_reference`**（业务引用）：`ref_type`（chat / solve / avatar）、`ref_id`、`object_key`、`created_at`、`released_at`。

`state` 只有：`ISSUED` → `BOUND` | `EXPIRED`。

清理策略：

| 情况 | 处理 |
|---|---|
| `EXPIRED` 且签发超过 7 天 | 删对象（签了没人用） |
| `BOUND` | 跟业务共存亡；业务删除后释放引用，再等 24 小时 |
| 桶里有、表里没有 | 对账任务扫 `media/` 前缀，超过 7 天无记录即删 |
| 删除前 | dry-run + 单批上限 + 只删 `media/` |

## 10. 风险与边界

照实写，这些不是可以忽略的细节：

1. **没有内容校验**。已登录的客户端（或被盗的 token）能塞任意字节。防线只有：单文件上限、短 TTL、按用户与按应用的签发配额、异常告警。如果有人拿这个桶当免费图床，能做的只有封用户/appId + 清前缀。
2. **PUT 可重放**。10 分钟有效期内同一个 key 可被覆盖。所以**每次上传都申请新 key**，不要绑定之后继续复用凭证。
3. **长度不强制**。S3 兼容的 presigned PUT 通常不能约束 Content-Length。超大文件只能在业务绑定时拒绝 + 靠生命周期清理，入口拦不住。要入口拦截得改用 POST policy。
4. **桶 ACL 未知**。`sspacee` 已存在且用途不明；若是公共读，所有 key 都能被未授权读取。上线前必须确认。
5. **图可能根本没传**。业务绑定时 HEAD 一次可以挡住；不做就接受这个风险。
6. **签名 URL 是 bearer secret**。不记日志、不进埋点、不拼到错误信息里。
7. **依赖登录态**。没接这套登录的应用用不了本方案（v2 的应用级 HMAC 就是为绕过这一点设计的，已撤销）。接入前提见 §13 第 2 条。

## 11. 客户端

- 复用现有 `ImageCompressor`（长边 1600、q82→60、目标 800KiB）。
- sign / read 请求走现有 `BaseRequest`（自动带 token、自动走现有 401 与刷新逻辑），不新造网络层。
- 共用模块给 `MediaUploader.upload(scene, file): String?`，内部签名 → PUT → 返回 objectKey。三条业务只调它，不自己拼路径。
- PUT 用**独立 HTTP client**：不带登录 token（URL 已签名）、不带业务 AES 包装、不跟随未验证重定向、不打印完整 URL（URL 里有签名）。
- 状态：`LOCAL → SIGNING → UPLOADING → DONE | FAILED`。失败保留本地文件，可重试。
- 退出账号时停止上传、丢弃未完成的签名；token 失效后 sign 自然 401，走现有重登流程。

## 12. 实施顺序

- **A** 确认 `sspacee` 的 ACL、前缀归属；在 dev 前缀用假图跑通 sign → PUT → 读 → 删。
- **B** 签名接口（挂现有登录校验中间件）、按用户/应用配额、签发记录表。
- **C** 头像闭环（最小，且能验证归属校验）。
- **D** 解题、聊天接入；聊天接入时一并做 §8 的「图片进模型」三步。
- **E** 其余应用接入；旧的 multipart 接口保留，观察使用量再退役。

## 13. 待确认

1. `sspacee` 的 ACL、现有前缀归谁、能不能只给我们一个 prefix 的写权限。
2. **四个应用是否都已经有这套登录**——鉴权改回登录 token 后这是硬前提；有接不上的，要么先接登录，要么它不走新链路。
3. 头像是否允许公开可见。
4. 解题图是否单独放宽到 2000px / 1.5MiB。
5. 聊天图今天走的是**服务端中转**（客户端 → `/v1/uploads` → 服务端写桶），不是 §5 的客户端直传。要不要改成直传，取决于中转的带宽/延迟是否成为瓶颈；不改也不影响功能。
6. 部署环境的 `.env` 用的是 `companion/` 还是 `companion-qa/` 前缀，以及生产那把 SK 是否与 job-come 保持一致（本地已对齐，线上需要确认）。
