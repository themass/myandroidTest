# 域名与路径：按服务分开整理（20261126）

用户要求：「域名和 path 分开整理，我们有 java 后端有 agent 是多个服务的，我整理这个是想看下是否合理，以及如何部署」。
下面按**服务**分开列（客户端证据全部来自本仓代码；服务端来自 agent-docs 仓库与 8.217.122.83 现场探测）。

## 0. 总拓扑（现状）

```
手机 App
  │  https
  ▼
api.ok123find.top ──→ 8.217.122.83 (nginx, TLS 终止, Let's Encrypt)
                          │
                          ├── /vvv/api/**   → 本机 Java 后端 (uher)
                          │
                          └── /v1/**        → 反向代理跨机 → 104.160.191.87:8081 (Python agent, uvicorn)

file.ok123find.top ──→ 8.217.122.83 (nginx 静态站：uher_help.html / about.html / index2.html …)
livekit.ok123find.top ─→ 8.217.122.83 (LiveKit SFU；URL 由服务端 /v1/voice/sessions 下发，不在 App 常量里)
www.ok123find.top ──→ 64.32.22.150 (旧 Web，http only，network_security_config 有明文例外)
ok123find.top ─────→ 64.32.22.150
```

注意：**两个后端服务共用同一个域名 `api.ok123find.top`，靠 path 前缀分流**。这是「是否合理」的核心判定点（见 §4）。

## 1. 服务一：Python agent（新，主力）

- **Base**：`https://api.ok123find.top` + `/v1`（`AgentChatRemote`；debug 构建可用 `companion_base_url` 覆盖）
- **实际主机**：nginx 反代到 104.160.191.87:8081（跨机；**8081 绝不能绑 loopback**——nginx 在另一台机器上）
- **路径清单**（客户端已在用）：
  - 会话：`POST/GET /v1/sessions`、`/v1/sessions/{id}`(GET/PATCH/DELETE)、`/v1/sessions/{id}/messages`
  - 回合：`POST /v1/sessions/{id}/submit`（A40 插话也走它，响应带 `disposition`: merged/steered/queued/started）、`POST /v1/sessions/{id}/cancel`、`GET /v1/sessions/{id}/events`(SSE, Last-Event-ID)、regenerate、`PATCH/DELETE /v1/sessions/{id}/messages/…`
  - 上传：`POST /v1/uploads/sign`、`POST /v1/uploads`、`GET /v1/uploads/{id}`
  - 角色/人格：`/v1/roles`、`/v1/divisions`
  - 语音通话：`/v1/voice/sessions`、`/v1/voice/script-sessions`（LiveKit grant 里下发 livekit URL）
  - 其它：`/v1/inbox`、`/v1/actions/pending`、`/v1/actions/{id}/confirm`、`/v1/memory/{events,facts,profile,profile_doc}`、`/v1/prefs`

## 2. 服务二：Java 后端（旧 uher，存量业务）

- **Base**：`https://api.ok123find.top` + `/vvv/api`（`core/Constants.java` `BASE_IP` + `getUrl`）
- **实际主机**：8.217.122.83 本机（与 nginx 同机）
- **路径清单**（客户端在用）：
  - 聊天遗留：`/chat/ai/{asr,tts,feedback,recognize}.json`、`/chat/ai/local/{ocr,file}.json`、`/chat/{sessions,messages,nostream,stream,transword}.json`、`/chat/session/{create,delete,rename}.json`、`/chat/message/{delete,edit}.json`
  - 数据：`/data/{appinfo,donation,version}.json`（升级检查/装机验证都靠 version.json）
  - 监控：`/monitor/{bug,connlog,leak}.json`
  - 定位：`/host/server/location/cache.json`
  - 用户：`/user/{login,reg,findpass,logout,info,code,setemail}.json`、`/user/custome/{add,del}.json`、`/user/setting/character{,/create,/update,/delete}.json`

## 3. 静态/媒体/其它域名

| 域名 | 现解析 | 用途 | 代码位置 |
|---|---|---|---|
| file.ok123find.top | 8.217.122.83 | helpUrl(uher_help.html)、about.html、收藏 index2.html | Constants.java、MyFavoriteView.java |
| livekit.ok123find.top | 8.217.122.83 | 语音通话 SFU（URL 服务端下发，App 不硬编码拨号） | 服务端 LIVEKIT_URL env |
| www.ok123find.top | 64.32.22.150 | MAIN_URL/DEFAULT_REFERER，**http 明文**（network_security_config 例外行） | CommonConstants.java、network_security_config.xml |
| (声音馆/情绪TTS) | 拼 ok123find 域名 | PlayService.kt、BarkEmotionalTts.kt 也引用 ok123find 常量 | feature-content、speech |

全仓引用 `ok123find` 的客户端文件共 7 个：Constants、CommonConstants、MyFavoriteView、network_security_config.xml、PlayService、BarkEmotionalTts、RecommendUrlTest。

## 4. 是否合理（评估）

**合理/健康的部分**
1. **单域名 + path 前缀分流**（`/v1`→agent、`/vvv/api`→Java）：对 App 只暴露一个主机名，一套证书、一套监控，移动端业界常规做法（两个上游本来就不同进程不同机，nginx 一层薄路由即可）。
2. agent 侧 REST + SSE 语义清楚（submit 裁决、events 断点续传），上传走签名直传，语音 URL 服务端下发——客户端不需要为 livekit 换域名。
3. 旧 Java 端点全部带 `.json` 后缀、前缀 `/vvv/api`，与新服务零冲突，迁移期共存成本为零。

**不合理/要改的部分**
1. **`/vvv/api` 前缀无语义**（历史遗留），新用户/新排查者看不出是哪个服务；建议维持现状但在网关层加 `X-Upstream` 响应头，日志里一眼定位。
2. **www.ok123find.top 走 http 明文** + network_security_config 例外：应尽快 https 化后删例外（明文域还挂在 64.32.22.150 老机上）。
3. **agent 部署跨机**（nginx 在 8.217.122.83，agent 在 104.160.191.87:8081）：8081 明文 HTTP 跨公网传输，依赖 nginx 段 TLS——**8081 段没有加密**，且 8081 必须对 8.217.122.83 开放（防火墙面）。中期把 agent 搬到同机 127.0.0.1:8081 + nginx（省一跳公网、少一个暴露端口），或给 8081 配 TLS。
4. **没有版本化的旧服务**：`/vvv/api` 无版本号，改协议只能靠 `.json` 端点内部兼容——存量不动即可，新端点不要再加进它。

**部署结论（怎么部署）**
- nginx（8.217.122.83）：`location /v1/ { proxy_pass http://104.160.191.87:8081; proxy_buffering off; proxy_read_timeout ≥ 300s; }`（SSE 必须关 buffering）；`location /vvv/api/` 指本机 Java；`file.` 静态站单独 server。
- agent（104）：uvicorn 监听 0.0.0.0:8081（**不能 loopback**，nginx 在另一台机）；systemd/守护 + 重启策略；Redis/MySQL 同机。
- Java：本机部署，nginx upstream 127.0.0.1。
- 换域名（sspacee.com）：**必须先修基础设施**（见 §5），再改 7 个客户端文件；老域名证书到期前（2027-02-12）不能下线——已装机 App 全在用 api.ok123find.top。

## 5. sspacee.com 切换：现状与硬阻塞（20261126 探测）

| 项 | 状态 |
|---|---|
| api.sspacee.com DNS | ✅ → 8.217.122.83 |
| nginx 路由 | ✅ `/v1/roles`→401、`/vvv/api/data/version.json`→200，两服务都通 |
| **TLS 证书** | ❌ **自签**（subject=issuer=CN=api-sspacee）——Android 直接拒连，一切流量全挂 |
| file.sspacee.com | ❌ NXDOMAIN（没有 DNS 记录） |
| livekit.sspacee.com | ⚠️ → 64.32.22.150（**错主机**，应为 8.217.122.83）；且 livekit URL 由服务端下发，还要改服务端 LIVEKIT_URL env + SFU 证书 |
| 本会话到 8.217.122.83 的 SSH | ❌ Permission denied（没有该主机密钥授权），我无法自己修证书/加记录 |

**要用户做的**：①给 api.sspacee.com 签正式证书（老域用的是 Let's Encrypt，同样办法 certbot 即可）；②加 `file.sspacee.com` DNS → 8.217.122.83 并在 nginx 加 server 块；③把 `livekit.sspacee.com` DNS 改指 8.217.122.83 + SFU 证书 + 服务端 LIVEKIT_URL。这三项就绪后，客户端改动就是 7 个文件的常量替换 + 装机验证，随时可做。
