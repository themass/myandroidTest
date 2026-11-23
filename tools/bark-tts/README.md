# Bark 情绪 TTS —— 服务端 PoC

Suno Bark 能把 `[laughs]` `[sighs]` `[gasps]` 这类标记念成真的笑声/叹气。这是它相对普通 TTS 的唯一价值，也是必须整套服务端部署的原因：权重小集 4.68 GB，生成比实时慢，端侧不可能。

App 侧接线设计见 [docs/app-wiring.md](docs/app-wiring.md)。

## 情绪标记不是 token

`bark/generation.py:413` 把整段文本（含 `[laughs]`）过普通 BERT tokenizer；「这里该笑」是 text→semantic GPT 在训练里学到的行为，**没有可提取的 DSP，也没有单独的 emotion 参数**。所以服务端唯一正确的做法是原样透传文本，客户端/网关都不许转义或清洗方括号。

## 目录

| 文件 | 作用 |
| --- | --- |
| `app/server.py` | FastAPI：`POST /tts` text+speaker → 裸 WAV |
| `scripts/fetch_weights.py` | 并行分片下载权重到 Bark 自己的缓存目录 |
| `scripts/run_samples.py` | 生成样本 WAV + 分段时延计时 |
| `requirements.txt` | 依赖（PoC 级） |
| `deploy/systemd/bark-tts.service` | 测试机原生部署单元（**本项目禁止 docker/容器**） |
| `out/` | 生成的样本与 `latency.json` |

## 本机（macOS / Apple Silicon）跑通

Bark 从源码目录导入，不用装 `suno-bark`（PyPI 上的 `bark` 是别的包，上游 README 专门警告过）。

```bash
python3 -m venv /tmp/barkenv
/tmp/barkenv/bin/pip install -r requirements.txt
# bark 包本体用本地 checkout（MIT，已 vendored）
cd /Users/gqli/work/sound/bark && /tmp/barkenv/bin/pip install -e .
```

装依赖走国内镜像，别直连 PyPI：torch 那 ~800 MB 走默认源在这台机器上 35 分钟没装完（和权重下载抢同一条带宽），换镜像几分钟就完。

```bash
VIRTUAL_ENV=/tmp/barkenv uv pip install -r requirements.txt \
  --index-url https://mirrors.aliyun.com/pypi/simple/
```

权重（小集 3 个文件，4.68 GB）：

```bash
python3 scripts/fetch_weights.py            # 默认 hf-mirror.com，10 路并行
```

`fetch_weights.py` 存在的原因：`hf_hub_download` 是单流，本机实测 0.3–0.6 MB/s，4.68 GB 要跑十几小时；分 10 路 range 请求能到 ~2 MB/s。文件写到 Bark 自己算出来的缓存路径（`generation.py:85`：`$XDG_CACHE_HOME/suno/bark_v0`，默认 `~/.cache/...`），Bark 见文件在就不下载了。

生成样本（含时延表）：

```bash
SUNO_USE_SMALL_MODELS=True /tmp/barkenv/bin/python scripts/run_samples.py
```

起服务：

```bash
PYTHONPATH=/Users/gqli/work/sound/bark SUNO_USE_SMALL_MODELS=True \
  /tmp/barkenv/bin/uvicorn app.server:app --host 127.0.0.1 --port 8088
```

```bash
curl -s localhost:8088/tts -H 'content-type: application/json' \
  -d '{"text":"哈哈，你这个地方说得太好了。[laughs] 我真的没想到。","speaker":"v2/zh_speaker_3"}' \
  -o laugh.wav
```

`/healthz` 看配置，`/stats` 看累计 RTF。

**别裸起服务**：权重不在缓存里时，`preload_models()`（`generation.py:210`）会就地走 `hf_hub_download` 单流重下——本机实测这条只有 0.3–0.6 MB/s，等于在请求进程里悄悄灌 4.68 GB（这次踩到了：服务一启动日志就是 `text model not found, downloading`）。`server.py` 现在启动先 `_assert_weights_present()`，缺文件直接拒绝启动并提示用 `fetch_weights.py`。

## 测试机（GPU）原生部署

**本项目任何环境禁止 docker/容器**（用户令，2026-11-23）。PoC 服务用 venv + uvicorn 原生进程跑，systemd 单元见 [`deploy/systemd/bark-tts.service`](deploy/systemd/bark-tts.service)，装法写在单元头注释里。要点：

- `--workers 1` 写死不是调优：bark 把模型存在模块全局 dict（`generation.py:36`），多 worker 就是多份 4.68 GB 权重再互相踩。
- `SUNO_ENABLE_MPS=False`（CUDA 机器）；`HOME`/`XDG_CACHE_HOME` 指到服务账号目录，权重缓存才找得到。
- 服务只绑 `127.0.0.1`，对外走 nginx 反代（和 api.ok123find.top 现有拓扑同构），不裸绑公网。

## 设备与显存

**我没有 GPU 测试机，下面区分「上游声明」和「我实测的」，不混着说。**

上游 `bark/README.md:238` 声明：

| 配置 | 上游声称 |
| --- | --- |
| 完整版（`text_2/coarse_2/fine_2`） | ~12 GB VRAM |
| 小集（`SUNO_USE_SMALL_MODELS=True`） | ~8 GB VRAM |
| 小集 + `SUNO_OFFLOAD_CPU=True` | 低到 ~2 GB VRAM |

权重的**磁盘**体积（我从 HF API 实测，不是猜的）：

| 文件 | 小集 | 完整版 |
| --- | --- | --- |
| text | 2.32 GB | 5.35 GB |
| coarse | 1.25 GB | 3.93 GB |
| fine | 1.11 GB | 3.74 GB |
| **合计** | **4.68 GB** | **13.02 GB** |

所以「小集 4.68 GB 权重、约 8 GB VRAM」是自洽的：fp32 权重常驻 + 激活。**给 GPU 部署至少留 8 GB 空闲显存；要跑完整版留 12 GB+。**

实测（见下「时延实测」）：本机 M5 16 GB，small 权重 CPU/MPS 能装下并跑通。

`server.py` 里的开关必须在 `import bark` 之前设好（`generation.py:92-94` 在导入时读环境变量）：

- `SUNO_USE_SMALL_MODELS` — 默认 True（PoC 一律用小集）
- `SUNO_ENABLE_MPS` — Mac 上 True；CUDA 机器设 False
- `SUNO_OFFLOAD_CPU` — 显存紧张时 True，代价是每次生成都要在 CPU/GPU 之间搬权重

**torch 版本钉在 2.5.1 是有原因的，别随手升。** `generation.py:212` 是
`torch.load(ckpt_path, map_location=device)`，没传 `weights_only`；torch 2.6 起
`weights_only` 默认改成 True，加载 bark checkpoint 会抛 `UnpicklingError`。
升到 2.6+ 必须自己加 `weights_only=False`（checkpoint 来自 HF 官方仓库，
但这是「信任来源」的假设，升级时重新评估）。

MPS 这条路**已实测跑通**（2026-11-23，见「时延实测」）：`SUNO_ENABLE_MPS=True` + torch 2.5.1 出数正常。encodec 在 mps 上仍会主动退回 cpu 解码（`generation.py:288-290`），这是 waveform 段慢的一部分原因。

## 时延实测

**2026-11-23，本机 MacBook Air M5 / 16 GB，MPS，small 权重，`run_samples.py` 原始输出（`out/latency.json` 同）。没有 GPU 实测数据，别外推。**

冷启动加载权重：**53.6 s**（一次进程只付一次）。

| 样本 | 音频时长 | semantic 段 | waveform 段 | 合计 | RTF |
| --- | --- | --- | --- | --- | --- |
| `01-laugh`（[laughs]） | 9.59 s | 49.6 s | 94.0 s | **143.6 s** | 15.0 |
| `02-sigh`（[sighs]） | 9.45 s | 31.6 s | 66.7 s | **98.3 s** | 10.4 |
| `03-gasp`（[gasps]） | 11.76 s | 32.8 s | 111.9 s | **144.8 s** | 12.3 |

读数：

- **比实时慢 10–15 倍**：一句 ~10 s 的中文要 1.5–2.5 分钟才出得来。这是「朗读」场景勉强能忍（点一下等一分钟）、实时通话绝对不可能的直接依据。
- **大头是 waveform 段**（semantic→coarse→fine 三段 GPT + EnCodec 解码），占总时长 66–77%，不是文本段。
- 三条 RTF 在 10–15 之间波动，和 semantic token 数不成比例（479/472/587 tokens）——别拿单条数字做容量规划，按最差 15 算。
- MPS 这条路**跑通了**（上面就是 MPS 数据）：`SUNO_ENABLE_MPS=True` + torch 2.5.1 正常出数，`Killed`/OOM 没出现（16 GB 机器 RSS 峰值远低于内存线）。encodec 在 mps 上仍会退回 cpu 解码（`generation.py:288-290`），所以 waveform 段慢有一部分是这个原因。

## 为什么这个 PoC 服务不能上生产

PoC 有意省掉的东西，别当成可以直接部署：

1. **无鉴权、无限流。** Bark 一次请求要吃几十秒 CPU/GPU，裸端口挂公网等于送 DOS。
2. **必须串行。** `bark.generation.models` 是模块级全局字典（`generation.py:36`），并发生成会互相踩；`OFFLOAD_CPU` 还会在调用中搬设备。所以 `server.py` 用一把 `_GEN_LOCK` 把生成排成队，`uvicorn --workers 1`。**排队即时延**：两个用户同时点朗读，第二个要等第一个整段生成完。真要上线得做任务队列 + 明确的等待反馈，或者多实例 + 每实例一份权重。
3. **硬时长上限。** `generation.py:447` 的循环上限是 768 个 semantic token，49.9 Hz → **一次最多 ~15.4 s 音频**，超了静默截断；输入超过 256 个 BERT token 被截尾（`generation.py:417`）。长回复要分段合成，而分段会带来音色漂移问题（可用 `save_as_prompt` 把上一段当 history 接上，未验证）。
4. **模型会改写台词。** 上游 model-card 明说它是 generative 模型，「can deviate in unexpected ways from provided script」。陪伴产品里台词被改是可感知 bug，需要人工样本验收，不是跑个分就行。
5. **中文弱于英文。** 官方把 zh 列为支持但质量打折；`v2/zh_speaker_*` 是较新的一组，PoC 用它。

## 生产网络约束（照本项目既有规矩）

- **104 生产机绝不碰。** 这个 PoC 只部署到测试机；没有 GPU 测试机时，就在本机跑 PoC + 出文档（现在的状态）。
- 密钥不进仓库。PoC 服务本身没有密钥，但**不要把测试机地址写进 `build.gradle` 或 `local.properties` 以外的地方**；App 侧通过 `-PbarkTtsBaseUrl=` 注入（见 docs/app-wiring.md）。
- 测试机不能绑 loopback 给外部用；对外要走 nginx。

## 许可结论

**MIT，可商用。** `/Users/gqli/work/sound/bark/LICENSE` 是 MIT，Copyright (c) Suno, Inc；`README.md:39` 明确「Bark is now licensed under the MIT License, meaning it's now available for commercial use!」。

注意 `pyproject.toml` 里 `license = {file = "LICENSE"}` 上方有一行过时的 `# Apache 2.0` 注释——以 LICENSE 文件为准（MIT）。随产物保留版权声明即可。

依赖许可要一起看：`encodec` 代码 MIT、`torch` BSD、`transformers` Apache-2.0、`numpy/scipy` BSD。没有 GPL 传染项。

**但 EnCodec 的「权重」许可没查到，这是商用前必须解决的一条，别当成已放行。** 已核实的：

- EnCodec **代码**是 MIT：`facebookresearch/encodec` 的 `LICENSE` 原文「MIT License / Copyright (c) Meta Platforms, Inc. and affiliates」。
- Bark 用的不是 HF 上的 `facebook/encodec_24khz`，而是 `EncodecModel.encodec_model_24khz()` 从 Meta CDN 直接拉 `dl.fbaipublicfiles.com/encodec/v0/encodec_24khz-d7cc33bc.th`（93.17 MB，缓存到 `~/.cache/torch/hub/checkpoints/`）。那个下载点**旁边没有 LICENSE 文件**。HF 上那个 93.19 MB 的 `pytorch_model.bin` 是**另一个封装**，字节数不同——别拿 HF 的 size/checksum 去校验 CDN 这个文件（PoC 里在这上面白追过一轮「差 20 KB 下不完」，实际 CDN 文件本来就完整，`content-length` 就是 93,171,529）。
- CDN 文件名里嵌的 `d7cc33bc` 前缀**不是**该文件的 md5（实测 md5 是 `3632f80b…`），torch.hub 那条按文件名哈希校验的路子对它无效。部署脚本要自己钉 md5。
- HF 上 `facebook/encodec_24khz` / `encodec_32khz` 两个 model card 的 `license` 字段和 tags **都是空的**（查过 API），所以也没在 HF 侧给出权重许可。

也就是说：代码可商用有据，权重可商用**无据**。要在商用前找 Meta 书面确认，或换成许可明确的 codec。PoC/内部测试不阻塞。

## 还没做

- GPU 时延复测（缺 ≥8 GB VRAM 测试机）。
- EnCodec 上游权重的商用许可核实。
- 长文本分段 + 音色一致性。
- `SpeechRemote` 的标记透传回归测试（见 app-wiring.md 第 5 节）。
