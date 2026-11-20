# 部署方案

日期：2026-11-12　适用：本机 `testserverV`（分支 `testjava_springboot`）、Android 客户端（`weifu-refactor` 系）
相关：[全量测试方案](full-test-plan.md)、[Play 合规](play-compliance.md)、[拍照解题接口](photo-solve-design.md)

写这份文档的直接原因：服务端已经在跑，但**配置错了两个值**（`photosolve.maxBytes=5`、模型 id 是占位符），
`health.json` 还谎报 `configured:true`，导致「部署成功」和「功能可用」是两件事。
这份方案的目标是让每次部署都能**自己证明自己可用**。

---

## 1. 现状（已部署的那台）

| 项 | 值 | 来源 |
|---|---|---|
| 域名 | `api.ok123find.top`（TLS 有效，`ssl_verify_result=0`） | 实测 |
| 网关 | nginx 1.24.0，`/vvv/` → 去掉前缀转发到 `vpn_server` | `deploy/nginx/snippets-vpn-routes.conf` |
| 应用 | `vvv-link`（Spring Boot，端口 8888） | `bootstrap.yml` |
| 密钥 | jar 外的 `secrets.properties`，`-Dsecrets.file=` 指定 | `Secrets.java` |
| 拍照解题 | `POST /vvv/api/photosolve/v1/solve.json`、`quota.json`、`health.json` | 实测 200 |
| 陪伴 / agent | **不存在**（`/companion/*`、`/api/agent/v1/*` 均 404） | 实测 |

> 请求必须带签名 UA：`DeviceUtil` 从 UA 里解析渠道、版本，并取**末 16 位作为 AES 密钥**
> （`md5(devid|time)`），响应的 `data` 是 AES-ECB 加密的 hex。所以裸 `curl` 一定得到 `errno 10000`，
> **不能用裸 curl 判断服务是否正常**。探针见 `scripts/probe/`。

---

## 2. 部署前检查（必须逐项有输出）

| # | 检查 | 命令 / 方式 | 不通过的后果 |
|---|---|---|---|
| D1 | 分支与提交对得上 | `git -C testserverV log -1 --oneline` | 部署了错的代码 |
| D2 | 工作区干净 | `git status --short` | 把别人的未完成改动带上线 |
| D3 | `secrets.properties` 不被提交 | `git ls-files --error-unmatch secrets.properties` 应失败 | 密钥进仓库 |
| D4 | 文件权限 | `stat -f %Lp secrets.properties` 应为 `600` | 同机其他用户可读 |
| D5 | 必填密钥非空且不是占位符 | `scripts/probe/check_secrets.py` | **本轮踩过**：占位符被当成已配置 |
| D6 | 编译 | `mvn -o -q -pl vvv-link -am package -DskipTests` | — |
| D7 | 单元测试 | `mvn -o -pl vvv-link test` | — |
| D8 | 数值型配置在合理范围 | `check_secrets.py` 校验 `maxBytes ≥ 1MB`、`timeoutSeconds 10–120` | **本轮踩过**：`maxBytes=5` 导致所有图片被拒 |

`check_secrets.py` 现在会检查的规则（都是本轮真实踩到的坑）：

- `photosolve.doubao.model` 若匹配 `ep-xxxx`/`your`/`TODO` → 报「占位符」
- `photosolve.doubao.apiKey` 长度 < 20 → 报「疑似未填」
- `photosolve.maxBytes` < 1 MiB → 报「单位是字节，5 表示 5 字节」
- `photosolve.doubao.endpoint` 不是 `https://` 开头 → 报错
- 所有密钥值前后的空格 → 报警（`Properties` 会保留空格）

---

## 3. 部署步骤

```bash
# 1. 构建
cd /Users/gqli/work/testserverV
mvn -o -pl vvv-link -am package -DskipTests

# 2. 配置自检（不要跳过）
python3 scripts/probe/check_secrets.py

# 3. 上传 jar 与配置（配置留在 jar 外）
#    jar: vvv-link/target/vvv-link-*.jar -> /home/web/app/
#    secrets: -> /home/web/config/secrets.properties   (chmod 600)

# 4. 启动（密钥在 jar 外，改配置只需重启）
java -Dsecrets.file=/home/web/config/secrets.properties -jar /home/web/app/vvv-link.jar

# 5. 就绪探测
curl -s -o /dev/null -w '%{http_code}\n' https://api.ok123find.top/vvv/api/version.json
```

> 第 5 步只能证明 nginx 和进程活着（返回体是 `errno 10000` 也算「活着」）。
> **真正的可用性判断必须走第 4 节的探针**。

---

## 4. 部署后验收（区分「服务活着」与「功能可用」）

| # | 项 | 方式 | 本轮结果 |
|---|---|---|---|
| V1 | 进程与端口 | `version.json` 有响应 | 通过 |
| V2 | 带签名的请求能进业务 | `scripts/probe/photosolve_probe.py health` | 通过（`errno 0`） |
| V3 | 拍照解题真实可用 | 同一脚本 `solve`，用一张真题目图 | **本轮才通过**：正确题图 3/3 答对，8–13s |
| V4 | 非题目图片 | 同一脚本，用非题目照片 | 通过（`questionFound=false`） |
| V5 | 额度 | 同一脚本连续调用，或直接查 `quota.json` | 未执行（不想消耗你的额度） |
| V6 | 幂等 | 同一 `clientRequestId` 发两次 | 未执行 |
| V7 | 未配置模型时的表现 | 临时改 model 为占位符 | 未执行（会打断线上） |

**`health.json` 目前不可信**：`isConfigured()` 只判断非空，填了占位符也返回 `configured:true`。
在它修好之前，`V3`（真解一道题）才是唯一可信的就绪信号。
建议服务端把 `isConfigured()` 改成「排除占位符 + 至少一次轻量校验」，这一处我没改（属于服务端仓库的代码改动）。

---

## 5. 配置项与环境

| 键 | 生产建议值 | 说明 |
|---|---|---|
| `photosolve.doubao.apiKey` | 火山方舟 API key（36 位 UUID 形态） | 与现有 `vision_doubao` 同一把 |
| `photosolve.doubao.model` | `doubao-seed-2-1-lite-260915` | 本轮实测：支持图片输入、返回合法 JSON、8–13s。**旧的 `doubao-vision-lite-32k-241015` 不接受 chat/completions**（请求挂起 40s+）；`doubao-seed-2-1-turbo` 未开通（404 `ModelNotOpen`） |
| `photosolve.doubao.endpoint` | `https://ark.cn-beijing.volces.com/api/v3/` | — |
| `photosolve.maxBytes` | `5242880` | **单位是字节** |
| `photosolve.maxImages` | `4` | — |
| `photosolve.timeoutSeconds` | `60` | 实测最慢 12.5s，还有余量 |
| `photosolve.quota.*` | 用默认值 | `freePerDay=10`、`vipPerDay=50`、`perMinute=3`、`totalPerDay=2000`、`idempotentSeconds=600` |

> 聊天用的 `doubao.appKey+appKey2` 那把 key 能调通 `/models`，但它配的接入点 `ep-20240527113904-mrr8p`
> 返回 404 `InvalidEndpointOrModel.NotFound`。**聊天主链路如果用豆包，可能已经是坏的**——这一项我没验证，
> 列进待查。

---

## 6. 回滚

| 场景 | 动作 | 验证 |
|---|---|---|
| 新版本启动失败 | 用上一版 jar 重启 | V1 |
| 拍照解题模型不可用 | 改 `secrets.properties` 的 model，重启 | V3 |
| 额度被误设成 0 | 改 `quota.*`，重启 | 调一次 `quota.json` |
| 密钥泄露 | 轮换 key、重启、检查 access log | V2 |
| 数据库迁移出问题 | 按 `vvv-common` 的迁移脚本回退（本轮没改过 schema） | — |

回滚只需要换 jar 或改外部配置，**不需要重新打包**——这是把密钥放在 jar 外的直接收益。

---

## 7. 监控与告警（建议，未实现）

| 项 | 建议 |
|---|---|
| 拍照解题成功率 | 按 `errno` 分组计数；`50002` 只可能是配置问题，`50001` 是模型/网络 |
| 模型耗时 | p50/p95；本轮 p50 约 10s，p95 约 13s |
| 额度熔断 | `totalPerDay` 触顶时告警（现在是静默拒绝） |
| 陪伴 / agent | 服务不存在，先不要建告警 |

---

## 8. 未验证 / 阻塞

- **陪伴、agent、LiveKit、memory、deerflow 的部署方案写不了**：仓库里没有它们的服务，也没有配置指向它们。
  需要你提供：仓库、镜像或机器，以及 `/api/agent/v1` 与其的关系。
- 额度（V5）、幂等（V6）、未配置模型的表现（V7）本轮没执行，原因写在表里。
- 生产 HTTPS 证书续期、nginx 配置版本管理不在本文范围。

---

## 9. 陪伴 / agent 服务（companion-agent）的部署

### 9.1 现状修正

前文第 1 节写的「陪伴 / agent 不存在」需要修正：**服务端代码是有的**，在
`/Users/gqli/work/deepagents/docs/myproject/companion-agent`（Python 3.12 + FastAPI + LiveKit Agents + DeerFlow）。
缺的是**把它部署到 `api.ok123find.top`**——nginx 上没有它的 location，所以客户端打到的是 404。

路径约定（实测，与 Android 客户端一致）：

| 客户端 | 路径 | 说明 |
|---|---|---|
| 陪伴（`HttpCompanionApi`） | `/companion/sessions`、`/companion/characters`、`/companion/memory`、`/companion/events`、`/companion/prefs`、`/companion/inbox`、`/companion/actions/*` | 角色目录、记忆、事件、偏好、收件箱 |
| Agent（`AgentApi`） | `/api/agent/v1/sessions`、`/api/agent/v1/sessions/{id}/messages`、`/api/agent/v1/sessions/{id}/events`（SSE）、`/api/agent/v1/roles` | 会话、消息、流式回合、角色 |

### 9.2 本轮修掉的两个「一上生产就起不来」

| # | 问题 | 为什么本地测不出来 |
|---|---|---|
| S1 | `serve.py` 在 `main()` 里同步 `build_app()`，而 `AsyncPostgresSaver.__init__` 调用 `asyncio.get_running_loop()`，直接抛 `RuntimeError: no running event loop` | 只有配了 Postgres checkpoint 才触发；不配库时走的是降级分支 |
| S2 | `routing.yaml` 里的 `openai/deepseek-v4-pro` / `-flash` 在可用网关上取不到（agicto 报 `invalid_api_key`） | 模型名只在真正调用时才校验 |

修复：新增 `LoopBuiltServer`，在 uvicorn 的 `_serve` 协程里才构建 app；模型改为
火山方舟实测可用的 `doubao-seed-2-1-pro` / `doubao-seed-2-1-lite`（OpenAI 兼容）。

### 9.3 依赖

| 依赖 | 用途 | 本轮怎么起 |
|---|---|---|
| Postgres + pgvector | 会话、角色、记忆、checkpoint | `pgvector/pgvector:pg16`，端口 55432 |
| Redis | 登录态校验、额度、幂等 | `redis:7-alpine`，端口 56379 |
| LiveKit server | 语音房间 | `livekit/livekit-server --dev`（生产用 LiveKit Cloud 或自建 + TURN） |
| Python 包 | `livekit` 只带 `rtc`，**建房要另装 `livekit-api`** | `uv pip install livekit-api` |
| 模型网关 | OpenAI 兼容 | 火山方舟 `https://ark.cn-beijing.volces.com/api/v3` |

### 9.4 必需的环境变量

```bash
COMPANION_ENV=prod
COMPANION_AUTH_VERIFIER=redis_token          # 生产必须；allow_all 会被启动检查拒绝
AUTH_REDIS_URL=redis://...                   # 读 Java 登录态
COMPANION_DATABASE_URL=postgresql+psycopg://...   # checkpoint 必须 Postgres
COMPANION_REDIS_URL=redis://...
COMPANION_LLM_API_BASE=https://ark.cn-beijing.volces.com/api/v3
COMPANION_LLM_API_KEY=<火山方舟 key>
LIVEKIT_URL=wss://...   LIVEKIT_API_KEY=...   LIVEKIT_API_SECRET=...
```

> `COMPANION_AUTH_VERIFIER=allow_all` 时 dev 用户 id 是 `dev-user`（非数字），
> `/api/agent/v1/roles` 会因为 `_owner_id()` 要求数字而返回 404。测试时用
> `Authorization: Bearer <数字>` 即可。

### 9.5 nginx

配置已经写进 testserverV 仓库：`deploy/nginx/snippets-vpn-routes.conf`（两个 location）和
`deploy/README.md`（上游定义）。要点（这几条在第一版文档里写错过，已更正）：

- **路径没有 `/vvv` 前缀**。陪伴和 agent 客户端的 baseUrl 都是 `https://api.ok123find.top`，
  直接请求 `/companion/...` 和 `/api/agent/v1/...`；现有接口才是 `/vvv/api/...` 并 rewrite 掉 `/vvv`。
  所以这两个 location 不能 rewrite，原样转给 Python 服务。
- **`upstream` 不能写在 snippet 里**（只允许出现在 `http {}`）。需要在服务器的 `nginx.conf` 手动加：
  `upstream companion_server { server 127.0.0.1:8081; keepalive 16; }`，否则 `nginx -t` 直接失败。
- **SSE 必须 `proxy_buffering off`**，否则流式回复会攒到最后一次性吐出，客户端表现为「一直转圈然后突然出全文」。
- 80 端口目前仍对旧版本 App 开放明文；README 里「确认用户都已更新后再打开 301 跳转」这一步不要提前做。

### 9.6 启动

```bash
cd companion-agent
COMPANION_HOST=127.0.0.1 COMPANION_PORT=8081 .venv/bin/python -m companion.serve
```

### 9.7 本轮实测结果

| 项 | 结果 |
|---|---|
| `/health` | 200 `{"status":"ok"}` |
| `/companion/characters` | 200，返回角色（林澈等） |
| `/companion/characters/{id}`、`memory`、`events`、`prefs`、`inbox`、`actions/pending` | 全部 200 |
| `POST /companion/sessions` | 200，返回 `session_id` / `room_name` / LiveKit `token` / `livekit_url` / `expires_at` |
| `POST /api/agent/v1/sessions` | 201 |
| `POST /api/agent/v1/sessions/1/messages` | 202，`turn_id` |
| 模型真实回复 | **通过**：助手按角色设定回复，历史里写入 user + assistant 两条 |
| SSE `/sessions/{id}/events` | 未单独抓包验证（消息已通过历史接口确认落库） |

### 9.8 还没做的

- **没有部署到公网**：需要服务器的 SSH 访问与部署权限；我没有动线上。
- LiveKit 现在是本地 dev 实例（`devkey`/`secret`），生产要换成 Cloud 或自建 + TURN，并改 `LIVEKIT_URL`。
- 语音链路（Volcengine TTS/ASR）默认关闭（`COMPANION_VOICE_ENABLED=false`），未验证。
- 主动性推送（`/companion/inbox` 由定时任务产生）未验证：需要 `events` 里有到期事件。
