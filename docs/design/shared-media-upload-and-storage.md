# 多应用共享图片直传与 TOS 存储设计

> 状态：**设计提案，未实施**。日期 2026-11-17（v2，取代 v1，v1 见 git 历史）。
> 适用：当前 Android 应用，以及计划复用流程的另外三个应用。
> 范围：解题图片、聊天图片、账号头像。
> 本文不部署桶、不改业务代码、不配置凭据。

## 1. 结论

1. 客户端自己压缩后**直传 TOS**；服务端只签发一个短期、单 key、**只能写**的 PUT 凭证。
2. **上传成功即成功**。服务端不读回、不校验、不做隔离区转正式区、不做图片规范化。写进去的字节就是最终字节。
3. 签名接口用**应用级签名**（appId + appSecret 的 HMAC），不依赖登录 token——有的接入方不用那套登录，也应该能传图。
4. 服务端只保留一件事：**记录签发了什么**。用于配额、归属校验（谁能把哪个 key 绑到自己身上）和清理。不做内容校验。
5. 消息里的图片**只存本地路径，不跨设备**。objectKey 只是给模型看的那一份。

## 2. 与 v1 的差别

| v1 | v2 | 原因 |
|---|---|---|
| 隔离区桶 + 校验 worker + 转正式区 | 无，直接写目标位置 | 上传成功即成功 |
| 服务端读回字节、验 magic、解码、去 EXIF | 无 | 同上 |
| 服务端重新编码成规范化图片 | 无，客户端压缩结果就是最终图 | 端上已有 ImageCompressor |
| 登录 token 鉴权 | 应用级 HMAC 签名 | 有的接入方不用这套登录 |
| 消息存 `mediaId`、可跨设备恢复 | 消息存本地路径，不跨设备 | 你的决定 |
| 5 个桶（prod/nonprod × staging/private） | 现有 1 个桶 + 前缀 | 现有配置就一个桶 |
| 状态机 ISSUED→VERIFYING→READY→BOUND | ISSUED→BOUND | 没有校验步骤了 |
| init / complete / status 三次调用 | sign 一次，PUT 一次 | 没有 complete 的必要 |

**必须知道的代价**：没有人检查传上去的是不是图片。一个被改过的客户端可以把任意字节塞进桶，最多到单文件上限。缓解见 §9。

## 3. 桶与配置

沿用现有配置，不新建桶：

```text
provider:        volcengine_tos
endpoint:        https://tos-s3-cn-beijing.volces.com   （S3 兼容协议）
bucket:          sspacee
region:          cn-beijing
forcePathStyle:  false  → 虚拟主机寻址 https://sspacee.tos-s3-cn-beijing.volces.com/<key>
```

AK/SK 由环境变量注入（`JC_S3_ACCESS_KEY` / `JC_S3_SECRET_KEY`），**不进仓库、不进本文、不进 APK**。服务端用它签 presigned URL、也用它读图。

### 3.1 这是已有共享桶

`sspacee` 不是为本方案新建的，所以：

- **前缀纪律**：只使用和清理 `media/` 前缀下的对象，清理任务硬编码这个前缀，不遍历桶根。
- **ACL 必须先确认**：如果这个桶是公共读（名字看着像图床），那么图一上传就等于公开。头像、解题图能不能接受公开，需要先定。不能公开就先改桶策略或换私有桶。
- 4 个应用共用这一个桶，靠前缀和签名权限隔离。**前缀不是安全边界**，真正的边界是签名服务只肯签自己 appId 下的 key。

### 3.2 object key

```text
media/<env>/<appId>/<scene>/<yyyy>/<mm>/<dd>/<keyId>.<ext>
例：media/prod/app-a/chat-image/2026/11/17/7Kq2mZ0pXr4vB1nT8sLcWa.jpg
```

- `env`：`prod` / `dev`
- `appId`：服务端应用注册表里的稳定 ID，不是包名
- `scene`：`chat-image` / `solve-image` / `user-avatar`，白名单
- `keyId`：服务端生成的 22 位 URL-safe 随机串，不可猜
- `ext`：由 contentType 推出，不信客户端文件名
- key 不含邮箱、手机号、姓名、原文件名

## 4. 鉴权

两条路，按接入方能力选。

### 4.1 A 路：服务端代签（首选）

接入方后端用服务间凭据调签名接口，客户端完全不接触 appSecret。
适用：有自己后端的应用。

### 4.2 B 路：应用级 HMAC（给没有统一登录的接入方）

App 内置 appId + appSecret，直接调。

```http
POST /api/media/v1/sign.json
X-Media-App:       <appId>
X-Media-Timestamp: <unix 秒>
X-Media-Nonce:     <uuid v4>
X-Media-Signature: hex(HMAC_SHA256(appSecret,
                     "POST\n/api/media/v1/sign.json\n" + appId + "\n" + timestamp + "\n" + nonce + "\n" + sha256hex(body)))
```

服务端依次校验：

1. `appId` 在注册表里 → 取 appSecret
2. `|now − timestamp| ≤ 300s`
3. `SETNX media:nonce:<appId>:<nonce>`（TTL 600s）成功才继续 —— 防重放
4. 重算签名，常量时间比较
5. `scene`、`contentType` 在白名单，`sizeBytes` ≤ 该 scene 上限
6. 配额：每 appId 的签发速率、每日次数、每日字节

**这个秘密能被反编译出来。** 它证明「调用方是这个 app 的副本」，不证明「这是某个用户」。所以：

- 只给**写一个 key** 的权限：不能 list、不能读、不能删、不能写别的前缀
- appSecret 可独立轮换，轮换期同时接受新旧两把
- 该 appId 的签发速率与失败率单独告警，可以只封这一个 app
- 最坏情况：别人往 `media/<env>/<appId>/` 塞垃圾，不是读别人的图

### 4.3 身份（可选，不属于鉴权）

有的接入方有登录、有的没有，所以签名请求可以带：

```json
{ "identityRealm": "app-a-account", "subjectId": "12345" }
```

`identityRealm` 说明「哪个账号体系」，`subjectId` 是那个体系里的用户 id。没有登录的接入方传设备级匿名 id（`identityRealm: "app-d-device"`）。

服务端**原样记录，不当作授权依据**。用途只有三个：按用户统计配额、排查滥用、业务绑定时校验「这个 key 是签给这个身份的吗」。真正的用户授权仍由业务侧自己的登录态负责。

### 4.4 为什么不用登录 token

登录体系不覆盖所有接入方，硬套会让新接入方为了传图去接一套用不上的账号系统。代价是：B 路接口没有「用户」概念，只有「应用」概念。所以 **quota、前缀、TTL、单文件上限就是这个接口的主要防线**。

## 5. 上传流程

```text
App                          testserverV                    TOS
 | 压缩/裁剪/去 EXIF            |                              |
 |--sign(appId, scene, 声明)-->|                              |
 |                             | 验签、查配额、写一条签发记录   |
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

业务接口入库时校验：key 在我们的前缀里、在签发记录里、属于同一 appId+scene、且没被别人绑过。通过就标 BOUND、写引用。**这是归属校验，不是内容校验。**

服务端要读图时（模型看解题图、agent 看聊天图），直接用 SDK 凭 AK/SK 按 key 读，不经过公网签名 URL。

## 6. 接口契约

### 6.1 sign

```json
{
  "scene": "chat-image",
  "contentType": "image/jpeg",
  "sizeBytes": 234567,
  "identityRealm": "app-a-account",
  "subjectId": "12345"
}
```

`contentType` 只允许 `image/jpeg | image/png | image/webp`。`sizeBytes` 是声明值，服务端只按上限比对；**PUT 时不会强制**（见 §9）。

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

只有客户端需要看图时才签 GET，同一套 HMAC：

```json
POST /api/media/v1/read.json
{ "objectKeys": ["media/..."] }
```

返回每个 key 的短期 GET URL + `expiresAt`，TTL 建议 10 分钟。服务端校验 key 属于本 app 前缀。

**聊天图片不需要这条路**：消息存本地路径、不跨设备，图是给模型看的。头像和解题历史才用得上。

### 6.3 错误码

`AUTH_INVALID`、`TIMESTAMP_SKEW`、`NONCE_REPLAYED`、`SCENE_NOT_ALLOWED`、`CONTENT_TYPE_NOT_ALLOWED`、`SIZE_NOT_ALLOWED`、`QUOTA_EXCEEDED`、`KEY_NOT_FOUND`、`KEY_NOT_OWNED`、`KEY_ALREADY_BOUND`、`KEY_EXPIRED`

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

## 8. 数据与清理

两张表足够。

**`media_upload`**（签发记录）：`object_key`（唯一）、`app_id`、`scene`、`identity_realm`、`subject_id`、`declared_size`、`content_type`、`issued_at`、`expires_at`、`state`、`bound_ref`、`bound_at`。

**`media_reference`**（业务引用）：`ref_type`（chat / solve / avatar）、`ref_id`、`object_key`、`created_at`、`released_at`。

`state` 只有：`ISSUED` → `BOUND` | `EXPIRED`。

清理策略：

| 情况 | 处理 |
|---|---|
| `EXPIRED` 且签发超过 7 天 | 删对象（签了没人用） |
| `BOUND` | 跟业务共存亡；业务删除后释放引用，再等 24 小时 |
| 桶里有、表里没有 | 对账任务扫 `media/` 前缀，超过 7 天无记录即删 |
| 删除前 | dry-run + 单批上限 + 只删 `media/` |

## 9. 风险与边界

照实写，这些不是可以忽略的细节：

1. **没有内容校验**。谁拿到凭证都能塞任意字节。防线只有：单文件上限、短 TTL、按 app 的签发配额、异常告警。如果有人拿这个桶当免费图床，能做的只有封 appId + 清前缀。
2. **PUT 可重放**。10 分钟有效期内同一个 key 可被覆盖。所以**每次上传都申请新 key**，不要绑定之后继续复用凭证。
3. **长度不强制**。S3 兼容的 presigned PUT 通常不能约束 Content-Length。超大文件只能在业务绑定时拒绝 + 靠生命周期清理，入口拦不住。要入口拦截得改用 POST policy。
4. **桶 ACL 未知**。`sspacee` 已存在且用途不明；若是公共读，所有 key 都能被未授权读取。上线前必须确认。
5. **图可能根本没传**。业务绑定时 HEAD 一次可以挡住；不做就接受这个风险。
6. **应用密钥可被反编译**。见 §4.2；B 路只用于没有其他选择的接入方。
7. **签名 URL 是 bearer secret**。不记日志、不进埋点、不拼到错误信息里。

## 10. 客户端

- 复用现有 `ImageCompressor`（长边 1600、q82→60、目标 800KiB）。
- 共用模块给 `MediaUploader.upload(scene, file): String?`，内部签名 → PUT → 返回 objectKey。三条业务只调它，不自己拼路径。
- PUT 用**独立 HTTP client**：不带 App 登录 token、不带业务 AES 包装、不跟随未验证重定向、不打印完整 URL（URL 里有签名）。
- 状态：`LOCAL → SIGNING → UPLOADING → DONE | FAILED`。失败保留本地文件，可重试。
- 退出账号时停止上传、丢弃未完成的签名。

## 11. 实施顺序

- **A** 确认 `sspacee` 的 ACL、前缀归属；在 dev 前缀用假图跑通 sign → PUT → 读 → 删。
- **B** 应用注册表、签名接口、配额、签发记录表。
- **C** 头像闭环（最小，且能验证归属校验）。
- **D** 解题、聊天接入。
- **E** 其余应用接入；旧的 multipart 接口保留，观察使用量再退役。

## 12. 待确认

1. `sspacee` 的 ACL、现有前缀归谁、能不能只给我们一个 prefix 的写权限。
2. 四个应用的 appId；哪些用 A 路、哪些必须用 B 路（客户端持密钥）。
3. 头像是否允许公开可见。
4. 解题图是否单独放宽到 2000px / 1.5MiB。
5. **聊天图既然不跨设备，是否还需要上传？** 只有「给模型看」这一个理由。如果模型链路能接受现有的 multipart 中转，那就只有解题和头像走新链路，省掉一半复杂度。
