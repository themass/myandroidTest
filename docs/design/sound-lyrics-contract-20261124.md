# 声音馆歌词与实时翻译：数据契约与实施方案

日期：2026-11-24 ｜ 状态：**契约已定，开发已开工**（用户当日拍板「设计并开发」，超出声音馆七项迁移范围，属新工作流，决策页已留痕）
关联：`docs/design/2026-11-22-字幕与翻译方案.md`（视频字幕库+翻译方案——本契约把同一套模子套到声音馆）、
`docs/qa/sound-hall-subtitles-20261124.md`（静态歌词 BLOCKED 取证）、
`playservice-seven-acceptance-matrix` §7（实时字幕片⑥）。

## 0. 结论先行

1. **不做第三方在线歌词。** 2026-11-22 方案已核查过：OpenSubtitles 2024 起转付费、伪射手类聚合站灰色随时挂；
   且声音馆条目多为自上传音频，按标题外部匹配命中率≈0。仓内 retromusic 那条 makeitpersonal 歌词链路是死代码
   （`retromusic/.../network/LyricsService.kt:7-11` 未注册进 Koin，`MainModule.kt:24-38`），不作为依赖。
2. **「歌词」= 服务端离线 ASR 预生成的 SRT**，存库永久缓存；片⑥已上线的实时 ASR 降为**无歌词时的兜底**。
   字幕随进度高亮、seek 秒跟、零端侧成本、可人工校对——这是「亮点」的正确形态。
3. **翻译照 2026-11-22 方案 A**：歌词 cues 逐条翻一次、缓存，第一个用户触发，之后所有人白嫖。
4. 端上三级降级阶梯（UI 不改架构，只是数据源优先级）：
   **服务端歌词 SRT → 实时 ASR（片⑥已有）→ 无字幕**。

## 1. 复用面取证（不发明新链路）

### 服务端（testserverV，`vvv-link`，Spring Boot/MyBatis）
- `sound/items.json` = `SoundController.java:29-33`，响应 `SoundItemsVo.java:9-12` 仅 {url,name,file,fileDate}。
  **无 id**——任何按条目挂元数据的前提缺失（Phase 0 补，一行字段）。
  表 `vpn.sounditems` 本身有 id（PO `SoundItems.java:11-19`），只是 VO 没透出。
- **视频字幕全套先例（2026-11-21 落地）**：`SubtitleController.java:27-45`（items/item 两端点）、
  表 `vpn.video_subtitle`（DDL `sql/2026112100_video_subtitle.sql`，列表查询故意不取大字段 content）、
  测试 `SubtitleControllerTest.java`。歌词照此模子，一个模子刻两份。
- ASR 现成两条：火山流式 `handle/asr/BytedanceAsrService.java`（secrets `bytedance_asr.appid/token`）、
  Deepgram `DeepgramAsrService.java:18-19`（`api.deepgram.com/v1/listen`，secrets `deepgram.token`），
  派发 `AsrContext.java:22-40`。注意现链路吃客户端 base64，服务端从不拉音频文件。
- 翻译：服务端 grep 零命中；但 LLM 管道现成（vvv-common `llm/`：DoubaoAI、LLmServiceProxy，keys 已在 secrets.properties）。
- 异步/定时现成：`@EnableAsync/@EnableScheduling`（`VVVLinkApplication.java:29,32`）、命名线程池
  （`CommonThreadPoolConfig.java:19-56`）、跨实例锁 `vpn.lock_job`（`LockJobMapper.xml`）、
  定时任务模子 `service/job/ScheduledTasks.java:36-77` + `AbstractJob`。
- 迁移=手写 SQL `vvv-link/src/main/resources/sql/YYYYMMDDNN_topic.sql` 手工执行
  （`2026112100_video_subtitle.sql:2` 自述未进任何自动化），列名 camelCase（`:4`）。

### 客户端（本仓）
- `core/.../SoundItemsVo.java:7-10` 同三字段无 id；screening Gson 镜像 `ScreeningFeedStore.kt:272-291`。
- **cue 引擎现成**：`feature-video/.../subtitle/local/LocalSubtitleTrack.java:21` `record Cue(start,end,text)`，
  SRT/VTT 解析 `:36-83`（上限 1MiB/10000 cues），`at(positionMs,delayMs)` `:93-113` 二分查表折叠成一条显示串。
- **服务端字幕取数现成**：`SubtitleVo{id,title,lang,format,size}` / `SubtitleContentVo{id,content}`
  （`ScreeningFeedStore.kt:211-223`）+ 取数 `:181-203`——目前只服务视频（`serverIdOf` 对声音条目返回 null）。
- 渲染现成：`CaptionOverlay` 吃单条 String（`subtitle/style/CaptionView.kt:41-46`）；声音馆已接（片⑥）。
- 实时 ASR 现成（片⑥）：`RealtimeSubtitleRunner` + 500ms `realtimeSeekJumped` 重连。
- 端上没有任何识别/翻译结果缓存（`AppDatabase` 无表、`HttpClient` 无响应缓存）——服务端永久缓存 + 端上内存即可，不新建本地库。

## 2. 契约

### Phase 0 — 透出 soundItemId（两端各一提交，向后兼容）
- 服务端 `SoundItemsVo` 加 `id` 字段（`VoBuilder` copyProperties 同名自动带出，PO 已有）。
  注意 mapper XML（`SoundChannelMapper.xml:11-31`）SELECT 列需含 `id`。
- 客户端 core `SoundItemsVo` + screening Gson 镜像加 `id`；`SoundTrackRef` 加 `serverId`。
  老客户端忽略新字段，安全。

### Phase 1 — 歌词存储与读取（照 video_subtitle 模子）

迁移 `vvv-link/src/main/resources/sql/2026MMDDNN_sound_subtitle.sql`：

```sql
CREATE TABLE IF NOT EXISTS vpn.sound_subtitle (
  id INT AUTO_INCREMENT PRIMARY KEY,
  soundItemId INT NOT NULL,
  title VARCHAR(200) NOT NULL DEFAULT '',
  lang VARCHAR(10) NOT NULL DEFAULT 'zh',
  format VARCHAR(10) NOT NULL DEFAULT 'srt',
  content MEDIUMTEXT NOT NULL,          -- SRT 正文，端上 LocalSubtitleTrack 直接解析
  sourceFileHash CHAR(64) NOT NULL,     -- 生成时音频 URL SHA-256：换文件自动失效
  genStatus VARCHAR(10) NOT NULL DEFAULT 'ready', -- pending/running/ready/failed
  engine VARCHAR(40) NOT NULL DEFAULT '',
  enabled TINYINT NOT NULL DEFAULT 1,
  createTime BIGINT NOT NULL,
  UNIQUE KEY uniq_sound_item (soundItemId)  -- MVP 一条目一轨；多语言时改 (soundItemId,lang)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

接口（照 `SubtitleController` 风格：`.json` 老链路、JsonResult/AES、构造注入）：
- `GET /api/sound/subtitle/items.json?soundItemId=` → `InfoListVo<SubtitleVo>`（**不含 content**）
  ——与视频字幕同构，客户端 `ScreeningFeedStore.kt:181-194` 的取数代码直接照搬。
- `GET /api/sound/subtitle/item.json?id=` → `{id, content, format, lang}`。
- 无轨回空列表（不是错误）——端上据此走实时 ASR 兜底。
- MVP 只认 `format=srt`（VTT 客户端也解析，但生成端统一产 SRT）。

**P1 不等 ASR**：手工导入一份 SRT 即可点亮端上显示——这是最快见效的路径。

### Phase 2 — 生成管线（按需触发为主 + 定时补量）

触发：
- **按需**（主路，零闲置成本）：端上点播无轨条目时调 `POST /api/sound/subtitle/generate.json?soundItemId=`
  （登录 + 同条目 in-flight 去重）→ 服务端置 `genStatus=running` 异步生成；本次播放走实时 ASR，下次再进就有歌词。
  「running/ready」再调直接返回状态不重复起。
- **补量**：`AbstractJob` 子类 + cron（仿 `ScheduledTasks.java:36-77`），每轮取 N 条无轨条目；跨实例用 `vpn.lock_job`。

生成步骤：
1. 音频 URL 规范化（`file`/`myFile`/`baseurl` 语义见 `DataSoundServiceImpl.java:72-74`；须公网可达）。
2. **首选 Deepgram url 模式**：`POST https://api.deepgram.com/v1/listen?url=…&model=nova-2-general&segments=true`
   ——一次 HTTP、Deepgram 自己拉流、返回 `segments[].start/.end`（秒级浮点）。现有 `DeepgramAsrService` 只需扩一个 url 入参方法。
   次选火山：现有 `BytedanceAsrService` 是流式裸协议无离线语义，做文件识别要新写客户端——不划算，除非质量测试否决 Deepgram 中文。
   **OPEN：中文质量用真实条目抽样定**（Deepgram nova-2 中文一般、火山中文更强；若否决，+1 天写火山离线客户端）。
3. segments → SRT：秒 → `HH:MM:SS,mmm`；<0.6s 的碎段并入前条（防闪字）。
4. 存 `content` + `sourceFileHash=SHA-256(音频URL)` + `engine`；失败 `genStatus=failed` 可重试。

成本口径：Deepgram nova-2 公开价 ~$0.0043/分钟，1000 条 ×10 分钟 ≈ **$4.3 一次性**、永久缓存。
（火山离线文件识别同量级，见 OPEN。）

### Phase 3 — 翻译（照 2026-11-22 方案 A 原样套声音馆）
- 表 `vpn.sound_subtitle_translation(soundItemId, langSource, langTarget, contentHash, cuesJson MEDIUMTEXT, engine, createTime)`，
  `UNIQUE(soundItemId, langTarget, contentHash)`，contentHash=歌词正文 SHA-256（改歌词自动重翻，没变永远命中）。
- `POST /api/sound/subtitle/translate.json?soundItemId&lang=`：命中回译文；未命中短轨同步翻、长轨回「翻译中」端上轮询。
- 引擎藏接口后：首选现成 LLM（Doubao/DeepSeek keys 已有；按 cue 分批 ≤30 条或 ≤4KB，单批失败只重试该批）；
  备选自托管 LibreTranslate（**禁 docker** → 裸进程部署，标 OPEN）。
- 双语显示：`CaptionOverlay` 现吃单串——MVP 用 `译文 + "\n" + 原文`（仓内先例 retromusic `LrcEntry` text+secondText），
  二期给 `CaptionOverlay` 加 `secondary` 参数做小字附行（`CaptionStyle.kt:6-9` 已预留服务端样式绑定意图）。

## 3. 客户端消费改造（本仓，TDD）

1. Phase 0 id 透出 + `SoundTrackRef.serverId`。
2. 取轨：仿 `ScreeningFeedStore.kt:181-203` 加 sound 版（`SubtitleVo`/`SubtitleContentVo` 直接复用）。
3. **纯函数决策层钉测试**：`soundCaptionSource(serverTrackLoaded, realtimeConfigured, urlUsable) → SERVER | REALTIME | NONE`。
4. 声音页播放循环接 `LocalSubtitleTrack.at(positionMs, delayMs)`——现在声音页没有 per-frame 字幕源，
   仿视频页 `ScreeningPlayback.kt:315-320` 补一条；`LocalSubtitleStore` 的偏移机制（±60s）后置再接。
5. 无轨条目 fire-and-forget 调 `generate.json` + 内存状态缓存，本次仍实时 ASR。
6. 测试：JSON 解析、决策纯函数、（服务端侧）segments→SRT 纯函数——照 `SubtitleControllerTest`/`PhotoSolveJsonSalvage` 风格。

## 4. 分期与验收

| 期 | 交付 | 上线门槛 |
|---|---|---|
| P0 | id 透出（两端各一提交） | 独立上线，无行为变化 |
| P1 | 表 + 两个读接口 + 端上取轨/渲染 + 手工导入 1 条 SRT | **歌词点亮**（真机：随进度高亮、seek 秒跟） |
| P2 | generate.json + Deepgram 生成 + 补量 job | 抽样质量验收（中文质量 OPEN） |
| P3 | 翻译表 + translate.json + 双语 | 首个用户 ≤2 分钟、后续 ≤1 秒（沿 11-22 方案口径） |

真机验收并入放映场装机窗口：无歌词条目 → 实时 ASR 兜底（片⑥行为不变）；有歌词条目 → 秒出、高亮跟进度。

## 5. 风险与纪律
- **生产库要手跑迁移**（该仓惯例，且基线不会自动建新表）；上线前备份 + 回滚 `DROP TABLE` 语句随 SQL 文件走。
- 火山**流式**（端上实时兜底）与 Deepgram**离线**（服务端生成）是两条不同 API，别混用别互相替换。
- Deepgram url 模式要求音频 URL 公网可达——声音条目本来就公网串流，满足；`myFile` 国内镜像的可达性 P2 实测。
- 服务端代码在独立仓库 testserverV，独立分支独立会话；Android 侧不等服务端（无轨=照旧实时 ASR，可先合）。
- 决策留痕：本特性由用户 2026-11-24 16:4x 直接拍板，见共享笔记 `playservice-scope-decision-20261124.md` 追加节。
