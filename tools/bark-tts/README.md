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
| `requirements.txt` / `Dockerfile` | 依赖与镜像（PoC 级） |
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

MPS 这条路 PoC 里没验证过：encodec 在 mps 上会主动退回 cpu（`generation.py:288-290`），
`SUNO_ENABLE_MPS` 上游默认也是 False。跑不通就把 `BARK_MPS=False` 退回 CPU 出数。

## 时延实测

（本机实测数据见 `out/latency.json`，下表在跑完后填写。）

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

依赖许可要一起看：`encodec`（MIT）、`torch`（BSD）、`transformers`（Apache-2.0）、`numpy/scipy`（BSD）。没有 GPL 传染项。EnCodec 权重是 Meta 的 CC-BY-NC 吗？——Bark 用的是 `EncodecModel.encodec_model_24khz()`，会从 Meta 的仓库下载权重（非 HF 托管），**这一条要在正式商用前单独核实**，PoC 阶段没查。

## 还没做

- GPU 时延复测（缺 ≥8 GB VRAM 测试机）。
- EnCodec 上游权重的商用许可核实。
- 长文本分段 + 音色一致性。
- `SpeechRemote` 的标记透传回归测试（见 app-wiring.md 第 5 节）。
