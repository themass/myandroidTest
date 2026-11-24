# 二次元头像 MVP（拍照/选图 → 生成 → 预览确认 → 保存头像）

日期：2026-11-24 · 线：Her 视觉体验整合 worker · 状态：客户端已落地，服务端契约已定义待实现

## 1. 用户拍板的需求

「用户自己上传/拍照生成二次元头像」。今天用户侧没有任何头像入口：
「我的」页是静态 drawable（`LegacyProfileEntries` 按性别出 `ic_default_nan/nv`），
聊天/陪伴里的用户头像是「我」字标（`CompanionScreen.UserAvatar` 注释写明
「现在没有账号头像图片源，字标是唯一能落地的形态」）。

**入口只在用户资料侧**（「我的」页头像/新增入口），不进陪伴角色选择器——陪伴角色是
「她」，头像是「我」，两条线不混。

## 2. 最小闭环

```
选图/拍照 → 本地压缩暂存 → 上传(/v1/uploads 复用) → 生成(/v1/avatars/generate)
  → 预览(签名 URL) → 用户确认 → 应用(/v1/avatars/apply) → 本地落盘 + 头像指针切换
```

关键纪律：

- **失败不覆盖旧头像**：只有「确认应用」成功后才写头像指针（pref + 本地文件），
  生成失败/网络失败/用户中途退出都不动旧头像。旧文件在新文件完整落盘前不删。
- **可重试**：失败态记住已完成的阶段（source_upload_id 还在），重试从「生成」续跑，
  不重复上传；暂存文件丢了才回到选图。
- **复用 `/v1/uploads`**：不新造上传通道。`common/media/MediaUploader`（原始字节 POST，
  不是 multipart）+ `MediaStager`（EXIF 摆正 + `ImageCompressor` 长边 1600/JPEG82/≤800KB）。

## 3. 服务端契约（待服务端实现；客户端按此钉死）

生成侧 provider 可切换是服务端职责，客户端只见一个稳定契约：

| 调用 | 形状 |
|---|---|
| `POST /v1/avatars/generate` | body `{"source_upload_id": "<uploads 的 id>", "style": "anime"}` → 200 `{"avatar_upload_id": "...", "style": "anime", "provider": "<调试可见>"}` |
| `POST /v1/avatars/apply` | body `{"avatar_upload_id": "..."}` → 200 `{"ok": true}` |
| `GET /v1/uploads/{id}` | 已有：换 300s 签名 GET URL（预览用） |

- 鉴权与 `/v1` 全线一致：`Authorization: Bearer <token>`。
- 错误沿用 `/v1` 形状：非 2xx + `{"detail": {"code": "..."}}`；客户端把 status/code
  翻成话术（同 photosolve 的 `decode()` 纪律：**不外泄网关异常、URL、provider 原始 payload**）。
- 生成是同步语义（MVP）：一次请求内完成，客户端 readTimeout 放宽到 120s；
  以后改异步任务（job id + 轮询）时只换 `AvatarGenRemote`，状态机不动。
- **provider 抽象在服务端**：`AnimeStyleProvider` 接口（generate(source_key, style) → result_key），
  实现可以是自托管模型、也可用许可明确的商用 API；选型见
  `docs/design/2026-11-24-try-on-and-avatar-licensing.md`（风格素材与模型必须自有或许可明确）。
  客户端不感知 provider 类型。

## 4. 隐私与保留策略（写进设计，不是实现后补）

- 用户照片**只走 `/v1/uploads`**（与聊天图片同一条私密通道），scene 建议服务端记
  `user-avatar`；不新增第三条上传路。
- 签名 URL 是 bearer secret：不打印、不进日志、不落缓存（与 `MediaUploader` 同纪律）。
- 生成结果确认后在本地 `files/avatar/` 存一份，展示不依赖签名 URL（300s 会过期）；
  旧头像文件在新文件写完整之前不删除。
- **服务端保留策略（需服务端落地时定死）**：源图与生成图都是人像——
  源图在生成完成后 TTL（建议 ≤24h）清理；生成图直到用户再次更换前保留。
  首次进入生成页要有一句人话的隐私说明（上传的是这张照片，用于生成头像，不会做别的）。
- 相机权限走全应用唯一入口 `PermissionGateway`（不自己写 registerForActivityResult）。

## 5. 客户端落点（本仓已实现）

- `feature-user/.../avatar/AvatarFlow.kt` —— 纯状态机（JVM 单测）：阶段、失败分类、
  retryFrom 语义、apply 成功前不动旧头像指针。
- `feature-user/.../avatar/AvatarGenRemote.kt` —— `/v1` OkHttp + Gson（photosolve 同款形状；
  用 Gson 是为了单测不依赖 Robolectric 的 org.json stub）。
- `feature-user/.../avatar/UserAvatarStore.kt` —— 本地指针与文件（`avatar_store.xml` pref
  + `files/avatar/current.jpg`），写新成功后才换指针。
- `feature-user/.../avatar/AvatarStudioActivity/Screen` —— 选图/拍照（`PickVisualMedia` +
  `TakePicture` + `AppFileProvider`）→ 进度 → 预览确认（使用/重试/重拍）。
- 入口：「我的」页头像可点（登录后）→ `ProfileAction.Avatar`；头像显示处
  `ProfileCard` 支持 `avatarFilePath`（Coil 读本地文件，缺省回落旧 drawable）。

不做（本轮明确边界）：头像裁切编辑器（mediaview PhotoEditor 已存在，接进来是后续一片）；
聊天/陪伴里「我」字标替换成真实头像（契约就绪后只换渲染槽位，见
`ChatScreen` 注释「账号头像 URL 到位后只换这里」）。

## 6. 验证

- `./gradlew :feature-user:testDebugUnitTest`：状态机转移/失败重试/不覆盖旧头像/
  remote 请求形状（MockWebServer）全部钉死。
- 真机验收待协调窗口（装机单写者纪律）：入口在「我的」页头像，走完闭环需要服务端
  `/v1/avatars/*` 先上线 —— 端侧对未实现的服务会以「服务暂不可用」话术失败，
  这条失败路径本身也有单测。
