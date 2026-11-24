# GitHub 图像能力调研（去马赛克 / 异物去除 / 二次元头像 / 换装换脸）· 2026-11-24

调研人：happy-fermi。目的：评估用户提的四类图像功能在开源生态里的成熟度、许可证能不能商用、
以及接进我们这套栈（Spring Boot 服务端 + 端上 ONNX Runtime + 禁止 docker）的现实路径。

## 总览

| 能力 | 首选开源方案 | 许可证风险 | 跑在哪 | 结论 |
|------|--------------|-----------|--------|------|
| 去马赛克（打码区恢复） | lada / DeepMosaics | AGPL-3.0（lad）| 服务端 GPU | 技术可行但**幻觉重建**；AGPL + 内容合规双重风险 |
| 画面异物去除（污点/杂物） | **IOPaint + LaMa(big-lama)** | Apache-2.0，商用安全 | 服务端（CPU 也能跑）| ✅ 最成熟、最干净的一条 |
| 衣物透视人体 | — | **不做**（见第 3 节） | — | ❌ 法律红线，不调研实现 |
| 照片→二次元头像 | AnimeGANv3（端上）；SD img2img + IP-Adapter/InstantID（服务端） | AnimeGANv3 非商用需授权函；InstantID 码 Apache 但 InsightFace 权重非商用 | 端上 ONNX 可跑 / 服务端 GPU | 原型可当天出；商用要解决授权 |
| 换衣（虚拟试衣） | IDM-VTON / OOTDiffusion / CatVTON 全是 **CC BY-NC-SA**；商用只看 FASHN v1.5（Apache-2.0 权重） | 主流模型几乎全部非商用 | 服务端 GPU（8–28GB VRAM） | 商用许可是最大门槛 |
| 换脸 | roop / Deep-Live-Cam（AGPL-3.0）+ InsightFace inswapper_128（**权重仅非商用研究**） | 代码 AGPL、权重 NC，双卡 | 服务端 GPU | 法律义务重（标识/备案/单独同意），权重许可挡商用 |

## 1. 去马赛克 / 打码恢复

- **[lada](https://github.com/ladaapp/lada)**（约 6.3k star，活跃）：图片/视频打码区恢复，
  mosaic 检测 + Real-ESRGAN 风格修复。**AGPL-3.0** —— 若做成联网服务需开源衍生品代码。
- **[DeepMosaics](https://github.com/HypoX64/DeepMosaics)**（及其分叉 [DeepMosaic](https://github.com/DanielArang/DeepMosaic)）：
  语义分割 + img2img，有 Windows GUI 和预训练 `mosaic_position.pth`；学术项目，维护一般。
- **[demosaic_project](https://github.com/rekaXua/demosaic_project)**：ESRGAN + 绿幕检测，AGPL。
- **必须说清的技术事实**：这类工具不是「还原」被遮内容，而是**用模型猜一个看起来合理的内容**。
  打码人脸/证件恢复出来的是幻觉，不能当真实信息用；对涉黄打码图做恢复在监管口径下
  等同于处理/生成淫秽内容，风险极高。

## 2. 人物表面异物去除（污点、杂物、路人）

这是四件事里**最干净**的一条：
- **LaMa / big-lama**（[advimman/lama](https://github.com/advimman/lama)）：SOTA 修补模型，
  ~51M 参数，**Apache-2.0**（码与权重均可商用）。
- **[IOPaint](https://github.com/Sanster/IOPaint)**（原 lama-cleaner）：封装 LaMa/PowerPaint/BrushNet
  的自托管修补服务 + Web UI，**Apache-2.0**，CPU/GPU/Apple Silicon 都能跑（CPU 可用，慢些）。
- 视频修补 **[ProPainter](https://github.com/sczhou/ProPainter)** 是 **S-Lab 1.1 非商用**，不要用。
- 接入方式：IOPaint 起 HTTP 服务（官方二进制 + systemd，符合我们的禁 docker 纪律），
  Spring Boot 转发蒙版和图即可；端上只做涂抹交互。

## 3. 「去掉衣物透视人物躯体」——不做

这一项我明确不做，也不去调研现成实现。「透视衣物看躯体」即强迫性裸体影像（NCII）：
- 《民法典》第 1019 条：不得以「利用信息技术手段伪造」等方式侵害他人肖像权
  （[全文](https://www.gskanglexian.gov.cn/klx/zfxxgk/jczwgk/cwgk/swx/dgc/art/2025/art_9a8fffeed70e4c709139222bd4ba9dfe.html)）；
- 《互联网信息服务深度合成管理规定》（2023-01-10 施行）明确列举**人脸生成、人脸替换、姿态操控**
  为重点服务：单独同意 + 显著标识 + 安全评估/备案
  （[原文](https://www.cac.gov.cn/2022-12/11/c_1672221949354811.htm)）；
- 《生成式人工智能服务管理暂行办法》第 4 条：不得侵害肖像权、隐私权等
  （[原文](https://cqca.miit.gov.cn/zwgk/zcwj/flfg/art/2026/art_27b7613aa2b8419384caa6c31fbdd7f0.html)）；
- 制作、传播此类影像另涉治安管理处罚乃至刑法（侮辱罪、制作传播淫秽物品罪）。

第 2 节的「异物去除」与这一项的界线：去污点、去杂物、去路人 = 正常修补；
脱衣/透视 = NCII，无论用什么开源模型都在红线内。

## 4. 一张照片 → 二次元头像

- **[AnimeGANv3](https://github.com/TachibanaYoshino/AnimeGANv3)**：照片/视频转动漫，
  风格多（Hayao、新海诚、迪士尼、皮克斯…）。关键优势：**有 ONNX/CoreML/TFLite 导出，
  手机 CPU 就能跑**（tiny 版 CoreML 512×512 约 50 FPS）——与我们端上已有的
  ONNX Runtime（docquadnet 就是这么跑的）完全同一套路，头像可在端上本地生成，不用服务器。
  许可证：学术与免费非商用；**商用要写信拿授权函**。
- **Stable Diffusion img2img / IP-Adapter / LoRA 路线**：保脸度和质量更高，但需要服务端 GPU。
  **[InstantID](https://github.com/instantx-research/InstantID)** 代码 Apache-2.0（明确可商用），
  但它依赖的 **InsightFace 人脸编码权重仅非商用研究**——商用必须换掉或自训这一环。
- 建议路径：端上 AnimeGANv3 ONNX 做原型（当天可验证效果）→ 用户反馈好再决定
  拿授权函 / 自训 LoRA（用我们自己有授权的图）两条商用路线哪条。

## 5. 换衣（虚拟试衣 VTON）与换脸

### 换衣

许可证是最大变量，按 **[按许可证排序的 VTON 目录](https://github.com/XQ0820/open-virtual-try-on-models)**（2026-10 核对）：

| 模型 | 码 / 权重许可证 | 备注 |
|------|-----------------|------|
| IDM-VTON（ECCV 2024，效果最好之一） | CC BY-NC-SA 4.0 | 只能研究用 |
| OOTDiffusion / CatVTON / FitDiT | CC BY-NC-SA 4.0 | CatVTON 8GB VRAM 可跑（门槛最低） |
| **FASHN VTON v1.5** | **Apache-2.0（码+权重）** | 商用首选，权重 ~2GB |
| Leffa | MIT（码+权重），但底层 checkpoint 要自查 | 可评估 |
| OmniTry | Apache-2.0，但底层 FLUX.1-Fill-dev 非商用 | 需要 28GB+ VRAM |

也就是说：**效果最好的都非商用；能商用的要么自建要么评估 FASHN**。
硬件：这类都是 SDXL 级 diffusion，服务端需要 8–28GB VRAM 的 GPU，
用官方进程 + systemd 部署（禁 docker）可行，但这是一台新的 GPU 机器决策。

### 换脸

- 技术栈 [Deep-Live-Cam](https://github.com/deep-live-cam/) / roop 均为 **AGPL-3.0**，
  核心权重 `inswapper_128` 与 `buffalo_l` 均为 **InsightFace 非商用研究许可**——
  商用直接部署基本无路，除非自训换脸模型。
- 法律义务（若真做）：属《深度合成规定》列举的「人脸替换」重点服务——
  **单独同意 + 显著标识 + 算法备案 + 安全评估**；未经同意换熟人/公众人物脸极易触发
  民法典 1019 与诽谤/侮辱类刑责。产品上除非「只换用户自己同意的人脸 + 水印 + 备案」，
  否则不建议进主线。

## 6. 接进我们这套栈的建议优先级

1. **异物去除（IOPaint/LaMa）**：许可干净、CPU 可起步、服务端一个进程就能起，最省事。
2. **二次元头像（端上 AnimeGANv3 ONNX）**：不动服务端，原型最快；商用先要授权函。
3. **换衣**：先定商用许可（FASHN 或自训）再买 GPU；IDM-VTON 只能内部演示。
4. **换脸**：法务先行，技术其次——当前不建议做；若做，只走「用户本人 + 标识 + 备案」。
5. **去马赛克**：技术上用 lada 可行，但 AGPL + 内容风险，除非有明确正当场景，不进主线。
