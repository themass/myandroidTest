# 虚拟试衣 & 二次元头像：授权核实结论（2026-11-24）

核查方式：对候选项目的 LICENSE/模型卡**逐条取一手来源**（GitHub license 端点、HF cardData、
raw 文件、提交历史）；「未核实」都明确写出。两个前提交正：IDM-VTON 属主是 `yisol`（不是
yumingjia）；**FLUX.1-dev 许可证里没有任何 $10M 营收门槛**（grep 过该文件全部历史版本）——
它对所有人一律非商业。

## 结论速览

| 用途 | 可上架路径 | 绝不进主线依赖 |
|---|---|---|
| 虚拟试衣 | FASHN VTON v1.5（Apache-2.0 代码+权重，自带架构）或托管 API（FASHN / AWS Bedrock Nova Canvas） | CatVTON、IDM-VTON、OOTDiffusion、一切 FLUX[dev] 衍生（含 Apache 标签的 LoRA）、SDXL-Turbo |
| 二次元头像（服务端） | OpenRAIL-M/++ 动漫检查点：`cagliostrolab/animagine-xl-3.1`、`hakurei/waifu-diffusion`（+`lcm-lora-*` 提速） | AnimeGANv2/v3（要作者授权信）、FLUX.1-dev 及其微调、HF 上 Apache 标签的 AnimeGAN 转载（假标签陷阱） |
| 二次元头像（端侧小模型） | **没有干净的小模型**——见 B 节四个选项 | AnimeGAN ONNX（权重训练自电影帧且仓库无 LICENSE） |
| 滑动交互参考 | finger-frame-effect-ai：只读设计参考 | 其任何代码/素材（从未有过 LICENSE 文件） |

## A. 虚拟试衣（研究线：离线原型 only，已核实）

**三个候选全部非商业，维持「离线原型研究、不进主线」定性：**
- **CatVTON**：权重 CC BY-NC-SA 4.0（HF 卡）；**代码仓库已 404**（四种方式核过），
  代码许可只剩二手证据；MaskFree 版 gated 条款明文禁商用禁分享；仓内还再分发 DensePose
  权重（CC BY-NC 4.0）。~1.4 GB。
- **IDM-VTON**（yisol, ECCV 2024）：代码+权重 **CC BY-NC-SA 4.0**（LICENSE.txt 首行 +
  README + HF tag 三处一致）。质量最高的学术选项，但商业不可用。
- **OOTDiffusion**：代码+权重 CC BY-NC-SA 4.0。15.9 GB。

**干净可上架的一条链：FASHN VTON v1.5**（Apache-2.0 代码+权重， ungated）。
关键优点：**不是** SDXL/FLUX 微调——自带 972M MMDiT，Apache 标签底下没有藏着 NC 基础模型。
两个前置条件：
1. **不碰它自动下载的 fashn-human-parser**（继承 NVIDIA SegFormer 许可 §3.3「仅非商业」）——
   走 maskless 推理路径或换 Apache 解析器；
2. 向 FASHN 要**训练数据权属的书面确认**——上传者给的 Apache 标签治愈不了来源问题。

**FLUX 陷阱（点名避雷）**：Apache 代码/LoRA 挂在 FLUX.1 [dev] 上仍要 FLUX 商业授权
（OmniTry、nftblackmagic/catvton-flux、xiaozao/catvton-flux-beta、各种 dev-LoRA 全部中招）。
SDXL-Turbo 是 `sai-nc-community`，也不是免费商用。
**反面样板（可对照）**：DSR-Tryon（Apache，基座 Qwen-Image 也是 Apache——与 FLUX 陷阱相反），
但 58.1 GB 只能托管。Leffa（MIT+MIT）大概可行，但它捆绑 SD inpainting 组件目录，
OpenRAIL 使用限制会流下来，要法务签字。

**托管 API（涉隐私口径）**：FASHN 与 AWS Bedrock Nova Canvas 都**合同承诺不用输入训练**；
OpenAI 默认留 30 天（合格客户可 ZDR）；Google 付费层不训练；**Kling 默认存储并用于训练**（要邮件
opt-out）；Revery 条款无法核实。
⚠️ 「摄像头帧永远不离开设备」这句话与**任何**托管试衣 API 都不兼容——若走托管，隐私文案必须改写。
VITON-HD / DressCode 数据集条款本轮未能核实（历来研究用途）——Apache 检查点的残留暴露在这里。

## B. 照片→二次元头像（头像 MVP 的 provider 选型依据）

端侧调研结论先行：**不存在许可干净、够小进 APK 的动漫风格化模型。**
- AnimeGANv2/v3（8.6 MB ONNX，技术上完美）：仓库**从未有 LICENSE 文件**；README 明文
  「非商业用途…商用请邮件索取授权信」（作者 Asher Chan）；权重训练自《起风了》《你的名字》
  帧提取，无数据集许可。走通只有一条：拿授权信。
- HF 上 `apache-2.0` 标签的 AnimeGAN 转载（如 vumichien/*）：**自声明标签与其卡片正文矛盾**
  （正文仍是非商业条款）——假标签陷阱，不可采信。
- onnx/models 里真正干净的手机尺寸风格化只有 `fast_neural_style` 四件套
  （BSD-3-Clause，6.6 MB）：油画风，**不是**动漫风。

**服务端可商用（无营收上限、无注册）的一族：OpenRAIL-M/++。**
- `hakurei/waifu-diffusion`（OpenRAIL-M）、`cagliostrolab/animagine-xl-3.1/4.0`（openrail++，
  卡片明文「✅ Permitted: Commercial use」）、`latent-consistency/lcm-lora-*`（openrail++，4 步推理）。
- 义务是文书活：APK 声明里放 OpenRAIL 文本；把 Attachment-A 使用限制写成 EULA 可执行条款
  并向下游传递；应用代码本身可闭源。
- 残留风险（选型改变不了）：OpenRAIL 明文「The Data is not licensed」——动漫图板训练数据的
  来源风险不由许可证解决。
- FLUX.1-schnell 是 Apache-2.0（可商用），但 23.8 GB——服务端级，端侧免谈。

**与头像 MVP 设计的关系**（docs/design/2026-11-24-anime-avatar-mvp.md）：
服务端 `AnimeStyleProvider` 可切换抽象与此结论一致——首个可上架 provider 用 OpenRAIL 系
动漫检查点（Animagine/waifu + LCM 加速）；隐私口径「生成在服务端、源图 TTL≤24h」保持真实。
如果产品坚持「端侧动漫风」，四个选项：(a) 拿 AnimeGAN 授权信；(b) 用已清权数据自训
Apache 架构风格化器；(c) 退到 BSD-3 油画风；(d) 维持服务端 + 改写隐私文案。默认取 (d)。

## C. sophiamyang/finger-frame-effect-ai（滑动交互参考）

**今天没有 LICENSE 文件，历史上也从来没有**（`/commits?path=LICENSE` 零提交；四个文件名 raw
404；仓库 license 字段 null）。README 无任何许可/商用表述。默认版权保留：
**只读设计参考，不抄一行代码、不用一个素材**——与既定纪律一致，现已有一手证据。
顺带：该仓库自己的实现是把视频送托管 API（Gemini Omni Flash），并不是端侧效果——
「复现其端侧体验」本来就是我们从零做。

## 悬而未决（记录用）

- VITON-HD / DressCode 数据集条款未核实（要查原始发布页）。
- FASHN 训练数据权属书面确认（走托管/上架前必须拿到）。
- AnimeGAN 授权信（只有产品坚持端侧动漫风才需要去谈）。
