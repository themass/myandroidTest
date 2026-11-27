# 超越 OLLMV — 设计汇总（2026-11-28）

> 收口文档。输入：`2026-11-28-open-llm-vtuber-deepread.md`（全貌）、`2026-11-28-ollmv-agent-architecture-deepread.md`（agent/工具/正确性）、`2026-11-28-ollmv-ui-interaction-deepread.md`（UI/互动面）、`2026-11-28-oss-companion-landscape.md`（AIRI/桌宠/生态）、2026-11-24 AIRI+OLLV 前作；外加 2026-11-28 真机验收结果（fx-sticker-acceptance 共享笔记）。waifu/桌宠线深读由 oss-waifu-research 会话补写 `2026-11-28-waifu-desktop-pet-deepread.md`（本文件预留引用位）。
> 用法：这不是第四份调研，是把三份深读 + 全景 + 真机证据压成**一套可执行的设计结论**：抄什么、改什么、我们已经赢哪、今天暴露输哪。

## 0. 一句话立场

OLLMV 教给我们的是「紧致」：**0–1 跳直达互动、反应写在数据里、呈现管线与 agent 解耦、打断是一等公民**。它的结构性天花板（桌面单机、零测试、persona/呈现不分层、无产线、互动单向）正是我们 mobile-first + 资产产线 + 双向 cue 回路的产品定位能吃掉的份额。**赢面不在「更像它」，在「它能做的我们都对等或更好，它能做的一切都是单人桌面」。**

## 1. 抄（机制直接拿走，我方件已有落点）

| # | 抄什么（OLLMV 证据） | 我方设计改法 | 落点 / 状态 |
|---|---|---|---|
| C1 | 四装饰器呈现管线（transformers.py:12-217，纯 async 生成器 + dict 事件穿透） | 呈现管线定为服务端纯函数层，输出结构化 cue 而非让端上解析文本；每层单独 TDD | `cmp.cue` + PresentationCueTest：协议在跑，纯决策层 TDD 纪律已有 |
| C2 | 打断记忆手术（heard + "[Interrupted by user]"，basic_memory_agent.py:195-223） | 打断回传**精确已播文本前缀**（Android 播放器进度精确，不靠前端 VAD 猜）；audio/text 双轨记忆；契约测试禁「静默无效」 | 通话会话状态机：cancel 契约 v2 已有，**heard 回传缺**（UI 深读 §6.1 同判） |
| C3 | 并行合成按序交付 TTS + 回合背压（tts_manager.py:70-111、conversation_utils.py:162-190） | 原生流式音频（废整段 base64），per-task timeout + 队列 drain，wait_for_response 带超时降级 | 通话管线：今天实测 TTS 首帧 13.3s / LLM 首包 2.3–15.8s，**这是第一优先级体验项** |
| C4 | 能力词表从模型 manifest 自动注入 LLM（live2d_model.py:47-53） | 词表来源=`ca_her_pack.payload` 的 effects/stickers 目录（id+hash+锚点），发布时 `validate_asset_pack` 硬门；白名单执法在 cue mapper 不在提示词 | her_pack M4 已定义；T-10 质量门在 Hermes 产线设计里 |
| C5 | 「角色=一份增量配置」热切换的用户体感（service_context.py:471-558） | 可切换但**有版本、可回滚、可审计**（HerRelease pin + ca_character_her），路径守卫抄正则白名单写法 | `/v1/hers` 已上线（形象列表页已装机验收） |
| C6 | tapMotions 触摸反应声明化 + 两级回落（model_dict.json + use-live2d-model.ts:354-380） | `touchReactions: {region:{motionId:weight}}` 进 wardrobe-spec；tap 从「开托盘」变「她立刻回应」 | **未开工**：Live2dTouchReaction.kt:49-110 仍是硬编码；touch_zones payload 映射第二刀待办 |
| C7 | 一键两义举手键（说话=打断，空闲=催她主动说话，use-footer.ts:35-44） | 通话页补常驻键，同时补掉「无打断键」「无主动触发」两个缺口 | **未开工**：CallControlBar 现只有 静音/文字/挂断 |
| C8 | 表情随音频同包下发 + idle 回落默认表情 | 表情 cue 搭音频侧带（cmp.cue 预算 ≤1.2KiB/8条每秒，单发会抢序）；回 idle 一行 effect 复位 | 小改，归入通话管线片 |
| C9 | 按句出字不做打字机；连续 AI 文本合并一条 | 字幕粒度绑定语音粒度（真停契约已有），补合并规则 | 小改 |
| C10 | 错误态做成按钮（红徽标=Click to Reconnect） | CallDisconnectedBanner 已有重连/登录两条，减重成徽标形态 | 小改 |

## 2. 已经赢的（真机为证，不是纸面）

1. **特效叠加层：OLLMV 零概念。** 今天真机验收 16 项 HER 舞台特效全部有渲染信号（爱心/挥手光弧/接通花瓣/滑动流光/猫耳/星星眼/脸红/气泡/指尖闪光/烟花/金币雨/爱心雨/雪花/雨滴/追光…；光环弱证据待复测），走 `cmp.cue → StageEffectMapper → LottieFxLayer`，抖音/快手级观感的 Android 原生实现。
2. **资产产线与包管理：她可被换、可被装、可被管。** CharacterAssetPackStore（SHA-256 逐文件 + 原子 rename）、包状态词（下载/待装/就绪/损坏/更新/不兼容）、wardrobe-spec 单一事实源换装。OLLMV 的模型是裸目录 + 手写 model_dict.json，表情表漂移无门（mao_pro 4/8 表情不可达）。
3. **多引擎：** LAYERED/LIVE2D/RIVE/DUIX/TAO 一个抽象；OLLMV 只有 Live2D。
4. **在线多租语音链路：** LiveKit SFU + 服务端 agent（今天全链路验收：grant→进房→ASR→LLM→TTS→字幕流式；27 分钟通话存活）。OLLMV 是 localhost 单进程、断连即无身份、CORS `*` + 零鉴权。
5. **双向互动回路的协议已在：** 相机手势→舞台 cue（CameraGestureCue 单一事实源）、speech_id 单调作废/墓碑 FIFO 取消契约。OLLMV 的 tap 是前端死循环、agent 不知情。

## 3. 今天真机暴露的「别吹」清单（超越叙事里的诚实账）

- **P0-A 通话页开人脸贴纸小窗 → Live2D 舞台整块黑屏**（CameraX PreviewView COMPATIBLE-GL × Live2D 上下文合成冲突；相机 HAL 实际出帧）。贴纸线 7 项验收 BLOCKED。修复候选：PreviewView 换 PERFORMANCE/TextureView 或 Live2D 侧共享 EGL 排查；ADY-AL00(麒麟) 必现。
- **P0-B 关闭小窗 3 秒 native crash**：`FxAnalyzer.analyze → FaceLandmarker.detectForVideo → nativeCreateRgbaImage` null deref——analyzer.close() 与在途分析帧竞态。修复：close 排入 analysisExecutor + 帧入口判空 planes。
- 修复归属 feature-camera-fx worker（本会话按装机单写者纪律只取证不改码）；取证全文见共享笔记 `fx-sticker-acceptance-20261128.md`。
- 产品侧临时规避可先行：小窗入口默认关 / 仅非 Live2D 舞台开放。
- 体验账：语音回合首包 2.3–15.8s、TTS 首帧 13.3s——「紧致」叙事目前只在工作台叙事里， latency 不解决真机上就是输。

## 4. 排序（下一步 4 刀）

1. **P0 修 A+B**（camera-fx worker：Preview 合成模式 + FxAnalyzer 关闭时序），贴纸 7 项完成真机验收——这是「我们赢」清单第 5 条的落地证据。
2. **打断刀 C2+C7**：通话页常驻打断/催话键 + heard 精确前缀回传进 memory——这是 OLLMV 五强里我们唯一整体缺失的「一等公民」，也是语音产品体感的地基。
3. **延迟刀 C3**：TTS 流式 + LLM 首包治理（doubao 侧选型/预热），把 13s 打到 <2s 才有资格谈超越。
4. **触摸刀 C6**：touchReactions 进 wardrobe-spec，tap 直达反应（0 跳）；与 her pack payload 第二刀（touch_zones）并片。

## 5. 与全景线的关系（防重复）

- AIRI/桌宠/widget 生态结论在 oss-landscape + （待落盘的）waifu-desktop-pet-deepread：桌宠线给我们的不是竞争面是**形态启发**（常驻、passthrough 窗口、idle 行为库）——是否进产品 roadmap 由协调会话裁决，本文件不预设。
- persona/her 分层设计（persona-her-split-design-v0 + /v1/hers 已上线）是 OLLMV「一个 YAML」的结构化超越，UI 深读 §8 给了双页骨架细节，本文件不再展开。

## 6. 验收挂点（本汇总的可证伪口径）

- 刀 1 完成 = 真机开小窗舞台不黑 + 关窗无 crash + 7 张贴纸逐张上脸截图。
- 刀 2 完成 = 打断后 LLM 下一轮上下文含精确已播前缀（服务端日志）+ UI 键可达截图。
- 刀 3 完成 = 真机语音回合 P50 首响 < 2s（同 logcat 字段 ttft/first_frame_ms 复测）。
- 刀 4 完成 = wardrobe-spec 声明的 touchReactions 生效、tap 无托盘直达反应录屏。
