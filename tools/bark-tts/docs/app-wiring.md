# 情绪 TTS（Suno Bark）服务端 + App 接线设计

状态：PoC 落地，App 侧只接线不开启。本文件是设计稿 + 已提交骨架的说明，不是已完成功能。

## 0. 结论先行

- Bark 的情绪（`[laughs]` / `[sighs]` / `[gasps]`）**只能整套服务端部署**，没有端侧可能：小集权重 4.68 GB，生成比实时还慢。
- 情绪标记**不是特殊 token**。`bark/generation.py:413` 把整段文本（含方括号标记）过普通 BERT tokenizer，是 text→semantic GPT 在训练里学会了「这里该笑」。因此全链路唯一要守的规矩是：**一个字节都别改**。
- App 侧接法：在 `:speech` 加一个编译期开关 + 一个 Bark 客户端骨架，插在现有朗读链路的「设备 TTS」和「后端 tts.json」之间。**默认 false，现有行为一字不变。**

## 1. 现有链路（已核对代码，非推测）

对话页「朗读」这条是唯一存在的 服务端文字→音频 HTTP TTS：

```
ChatViewModel.onSpeakMessage                     feature-chat/.../presentation/ChatViewModel.kt:355
  └─ SendActions.speak(context, message)         feature-chat/.../presentation/SendActions.kt:194
       1. DeviceSpeech.speakChinese(...)         speech/.../tts/DeviceSpeech.kt:29   设备优先
       2. repository.synthesizeSpeech(id, text)  domain/repository/ChatRepository.kt:173
            └─ ChatRepositoryImpl:143 → SpeechRemote.synthesize   AttachmentUploader.kt:46
                 POST Constants.getUrl("/chat/ai/tts.json")       Constants.java:205
                 body  SpeechParam(id, content)                   ChatRequestParams.kt:25
                 resp  TtsResponseVo{data=base64, fileName}       TtsResponseVo.java:9
                 → filesDir/tts/tts_<id>.mp3 → "file://" 路径
       3. AudioPlayer.play(id, uri)              speech/.../audio/AudioPlayer.kt（单例 ExoPlayer）
```

要点：

- `DeviceSpeech` 成功即 `return`。设备 TTS 几乎总是成功 → **不重排顺序，Bark 永远不会被走到**。这是骨架里 `SendActions` 也改了一行的原因。
- HTTP 走项目自己的 `BaseService.awaitPost`（`common/net/http/HttpClient`，OkHttp 打底、Gson 解析、返回 base64 JSON）。Bark 返回**裸 WAV 字节**，不是这套 JSON envelope，所以不能复用 `awaitPost`——骨架用直连 OkHttp（和 `HttpCompanionApi`、`speech` 的 ASR 同一风格）。
- `AvatarCommand`（feature-companion/.../avatar/AvatarCommand.kt:10）里**没有任何 speak/TTS 指令**。`AvatarState.SPEAKING` 只是视觉状态，嘴型 `Mouth(openness)` 来自**播放中音频的振幅**，不来自文字。所以 Bark 不经过 AvatarCommand；换 TTS 引擎不用动形象协议。
- 实时通话（`LiveKitCompanionTransport`）的语音由服务端 LiveKit agent 合成，客户端只收 PCM（`PcmConvert.toMono16k`，16 kHz 单声道 PCM16LE）。这条链路**不接 Bark**（见第 6 节）。

## 2. Bark 服务的接口约定

服务端实现见 `tools/bark-tts/app/server.py`，PoC 形态：

```
POST /tts
  { "text": "...[laughs]...", "speaker": "v2/zh_speaker_3",
    "text_temp": 0.7, "waveform_temp": 0.7 }
  -> 200 audio/wav（24 kHz / mono / 16-bit PCM，裸文件，不是 JSON）
     X-Generation-Seconds / X-Audio-Seconds / X-Realtime-Factor

GET /healthz   GET /stats
```

App 侧必须知道的硬限制（都在 `bark/generation.py`）：

| 限制 | 出处 | 客户端后果 |
| --- | --- | --- |
| 一次最多 768 个 semantic token ÷ 49.9 Hz ≈ **15.4 s 音频** | generation.py:447 | 超过必须分段合成，服务端会静默截断 |
| 输入文本超过 256 个 BERT token 被截尾 | generation.py:417 | 骨架先按 120 汉字拦一道 |
| `speaker` 实际是 `history_prompt`，只认 `ALLOWED_PROMPTS` | generation.py:71,356 | 名字写错 = 400，不是 500 |
| 模型状态存在模块全局 `models` 字典 | generation.py:36 | 服务端必须串行；并发请求排队 |

`speaker` 取值：`v2/<lang>_speaker_0..9`（13 种语言，含 zh）或 `announcer`。中文女声 PoC 用 `v2/zh_speaker_3`。

## 3. 接线方案（已提交骨架）

三处改动，默认全部关闭：

1. `speech/build.gradle` — 两个 buildConfigField：

   ```groovy
   buildConfigField "boolean", "BARK_TTS_ENABLED", buildFlag('barkTtsEnabled', 'false')
   buildConfigField "String",  "BARK_TTS_BASE_URL", "\"${buildFlag('barkTtsBaseUrl', '')}\""
   ```

   取值优先级照 `volcAsrAppKey` 现成的写法：`-P` → gradle.properties / local.properties → 环境变量 → 默认。`:speech` 已经有 `buildFeatures { buildConfig = true }`，不用改；放 `:feature-companion` 就得先补 `buildConfig = true`（它只有 `compose = true`），所以选 `:speech`。

2. `speech/.../tts/BarkEmotionalTts.kt`（新）— 直连 OkHttp，`resp.body.bytes()` 落 `filesDir/bark/bark_<ts>.wav`，返回 `file://`。任何异常返回 null，交回原链路降级。

3. `AttachmentUploader.kt` 的 `SpeechRemote.synthesize` 开头插一段 gated 分支；`SendActions.speak` 里 `BarkEmotionalTts.enabled` 为真时跳过 `DeviceSpeech`。

打开方式（只在本地/测试机）：

```bash
./gradlew assembleDebug \
  -PbarkTtsEnabled=true \
  -PbarkTtsBaseUrl=https://<测试机域名>:8088
```

关闭时（默认）：`enabled == false`，`synthesize` 第一句直接落到原来的 `postForResult`，`speak` 顺序不变。骨架已编译验证：

```bash
./gradlew :speech:compileDebugKotlin :feature-chat:compileDebugKotlin   # BUILD SUCCESSFUL
```

### 为什么不用项目现有的 provider-registry 写法

项目的切换范式是 `CompanionTransports.create(context, provider)`（`companion-rtc-provider-switching.md`）：服务端在 grant 里指定 provider，客户端一个工厂分派。Bark 是 PoC，服务端不在 `api.ok123find.top` 上、没有 grant 通道，所以先用编译期开关，不去伪造一套 provider 协商。

真要产品化，正确落点是**服务端**做这件事：`/chat/ai/tts.json` 的响应里加 `engine` 字段，或按 `docs/companion-rtc-provider-switching.md` 的方式在角色配置里带 voice/engine。届时客户端只需要在 `SpeechRemote` 里按返回的 engine 选解码/播放参数，不用新增客户端开关。

但先记一笔现状，免得设计时以为有现成的 `/v1` 通道可用：`AgentBackedChatRepository.kt:412-415` 明确写着

> TTS 仍在旧 Java 链路：`/v1` 没有任何 TTS HTTP 接口，语音合成只存在于 LiveKit 语音管线内部（`api/livekit/agent.py`）。要迁得先在 companion-agent 补 `/v1/ai/tts`。

也就是说朗读这条**只有旧 Java 后端一个入口**，`/v1`（companion-agent）侧没有。Bark 要么挂在旧 Java 后面做转发，要么就在 companion-agent 补 `/v1/ai/tts` 时一并接进去——后者更顺，因为情绪音色本来就该和角色配置在同一处。

另一个可参考的现成运行时开关范式是 `dh/DhLabGate.kt:7-13`（debuggable OR SharedPreferences `companion.dh_lab`），如果希望不重新打包就能开关，走这条而不是 buildConfig。PoC 用 buildConfig 是因为它默认关得干净，不需要 UI。

顺带记一笔：设置页那个「TTS 开关」`CommonConstants.TTS_OPEN` 在本分支**只有写没有读**——`SettingActivity.kt:175/286` 读写偏好，但全仓找不到消费它的代码。`docs/tts-options.md:5` 说「新回复自动播报走这条链路」已经不成立。别把它当成现成的 TTS 引擎开关来复用。

## 4. 角色 → 音色

PoC 写死 `BARK_SPEAKER = "v2/zh_speaker_3"`。真正的映射要等角色数据里有 voice 字段：`CompanionModels` / `CharacterVo` 目前只有形象（`avatarPackId`），没有音色。建议届时形如：

```
character.voice = { engine: "bark", prompt: "v2/zh_speaker_3" }
```

`prompt` 是 Bark 的 voice prompt 名，服务端已有 `ALLOWED_PROMPTS` 白名单校验，客户端不该自己拼。

## 5. 情绪标记的透传纪律（最容易出事的地方）

`[laughs]` 从 agent 到耳朵，经过的每一层都必须原样保留：

| 层 | 会吃掉标记的动作 |
| --- | --- |
| agent 输出 → 消息文本 | Markdown 转义 `\[laughs]`；把方括号当表情符号清洗 |
| 客户端 → 请求体 | 用 HTML sanitizer；`JSONObject.quote` 之外再自己转义 |
| 服务端 | 任何「清理输入」的中间件 |

Bark 服务端只跑 `_normalize_whitespace`（generation.py:346，折叠空白），对标记无害。App 骨架用 `JSONObject.put("text", text)`，只转义 JSON 语法字符，`[` `]` 不受影响。

需要单独留一条测试：给 `SpeechRemote` 喂 `哈哈[laughs]`，断言请求体里 `[laughs]` 原样存在。骨架先没写（PoC），产品化时要补。

## 6. 不接的地方 / 会踩的坑

- **实时通话链路不接**（`LiveKitCompanionTransport`）：Bark 生成比实时慢一个量级，边说边合成不可能。通话要情绪，得在服务端 LiveKit agent 内部换引擎，客户端不用动。
- **采样率**：Bark 出 24 kHz；DH 侧全是 16 kHz（`PcmConvert.TARGET_RATE = 16000`、Duix `playAudio` 假定 16k mono s16le、`VoiceRecorder.SAMPLE_RATE = 16000`）。如果 Bark 音频要驱动嘴型/DH 渲染，必须重采样 24k→16k。播放本身（ExoPlayer）不受影响。
  具体两处硬编码：`DUIX.playAudio(wavPath)` 直接跳过 WAV 头 44 字节并按 16000 Hz / mono / s16 解析（`dh-engines/.../duix/sdk/client/DUIX.java:165`，`AudioPlayer.java:19` `sampleRate = 16000`）——Bark 的 24 kHz WAV 喂进去会按错速率播，音调变高、时长变短，不是「音质差一点」。`A2BSService.process(index, audioData: ShortArray, sampleRate: Int)` 签名里速率是参数，但**它没有 Kotlin 调用方**（`AudioBlendShapePlayer.kt` 是显式 no-op stub）。所以「Bark 驱动数字人嘴型」这条今天根本没有可用通路，要做得连 A2BS 播放桩一起补，不只是重采样。
- **`CallAudioPreview`** 用 `TextToSpeech.synthesizeToFile` + `MediaPlayer`，并自己读 16-bit PCM 包络（非 16-bit WAV 会报错）。Bark 的 WAV 是 16-bit，能读，但这条是「试听本机音色」的界面，不是接 Bark 的地方。
- **时延**（实测 2026-11-23，M5/MPS/small：一句 ~10 s 音频要 **98–145 s** 生成，RTF 10–15；见 README「时延实测」）。UI 上必须有「合成中」状态——现在的 `speakingMessageId` 只有一个 spinner，等 1.5–2.5 分钟只给个 spinner 是会流失用户的量级，要么加进度/取消，要么把 Bark 限定在明确的「情绪朗读」入口而不是替换所有朗读。

## 7. 许可

Bark 代码与权重 **MIT**（`/Users/gqli/work/sound/bark/LICENSE`，Copyright (c) Suno, Inc；README「Bark is now licensed under the MIT License, meaning it's now available for commercial use!」）。商用可用，随产物保留版权声明。

但商用前要自己承担两件事，不是许可问题而是风险：
1. 模型卡明说它是 generative 模型，「may deviate in unexpected ways from provided script」——可能改写台词。陪伴产品里台词被改写是可感知的 bug。
2. 中文效果弱于英文（官方把 zh 列为支持但质量打折）。PoC 样本要听完再决定，别只看分数。

## 8. 下一步（不在本 PoC 范围）

1. 找一台 GPU 测试机（≥8 GB VRAM，small 权重），复测时延——见 README 的「GPU 待测」。
2. 决定引擎归属：服务端 `/chat/ai/tts.json` 加 engine，还是客户端开关。
3. 角色 → 音色字段。
4. 分段合成长文本（>15 s）+ 句间音色一致（用 `save_as_prompt` 串下一句的 history）。
