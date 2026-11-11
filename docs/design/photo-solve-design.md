# 拍照解题端到端：方案

状态：**方案 + 待实现的服务端参考实现，未改客户端代码。**
客户端基线：`weifu-refactor` @ `7b414878`；服务端：`/Users/gqli/work/testserverV`（Spring Boot，Maven 多模块）。

---

## 1. 目标流程

```
拍照 → 判断是不是题目（不是 → 提示重拍/框选）
     → 四角自动对齐题目边框
     → 用户拖动四角微调
     → 裁剪
     → 上传 testserverV
     → 豆包大模型解题
     → 结构化结果（识别出的题目 + 步骤 + 最终答案 + 错误码）
```

---

## 2. 客户端现状

| 环节 | 现状 | 位置 |
|---|---|---|
| 拍照 | CameraX 预览与拍照 | `feature-photosolve/.../ui/PhotoCaptureScreen.kt` |
| 质量判断 | `ImageQualityAnalyzer.analyze(gray, w, h, shortSide)` → `QualityReport(issues)`；`QualityIssue` 枚举（模糊、过暗等） | `:imaging` 的 `quality/` |
| 是否题目 | `QuestionImagePreparer.prepare(bitmap)` → `Prepared(source, cropped, cropBox, detector, quality)`；先用 `DocQuadNetCropAssistProvider`（ONNX）检测，失败退回手工框选 | `:imaging` 的 `quality/QuestionImagePreparer.kt` |
| 四角对齐 | `CropAssistProvider.suggest(bitmap)` → `CropAssistResult.SuggestedBox / NoSuggestion`；`CropNorm` 归一化坐标 | `:imaging/cropassist/` |
| 手动微调 | `CropOverlay` + `CropBitmapOps`（四角拖动、旋转、镜像） | `feature-photosolve/ui/` |
| 裁剪 | `CropBitmapOps.cropTo` | 同上 |
| 上传与解题 | `HttpPhotoSolveRepository`：压缩 → `POST /photosolve/upload` → `POST /photosolve/solve`；走 mulipart + JSON；错误码 40001/40002/40003/42901/50001 | `feature-photosolve/data/` |
| 状态机 | `PhotoSolveViewModel`：`Home → Capture → Crop → Recognizing → Result`，`PhotoSolveStep` 枚举 | `presentation/` |
| 服务端 | `/photosolve/*` 未部署；`docs/design/photo-solve-api.md` 记着 aixue「拍讲」是更成熟的替代 | — |

**结论**：客户端的检测、对齐、手动微调、裁剪都已经有了，缺的是三件事：
1. 「不是题目」的判定目前只来自服务端的 40002 和 `QualityReport`，客户端没有前置判断；
2. 上传与解题的服务端没有落地；
3. 结果展示只按「最后一行是答案」拆分，没有结构化步骤。

---

## 3. 客户端设计

### 3.1 是不是题目：两层判断

| 层 | 判断 | 成本 | 处置 |
|---|---|---|---|
| 客户端（拍照后立即） | `QuestionImagePreparer.prepare()` 的检测结果 + `QualityReport` | 本地，<300ms | 没有检测框 → 提示「没拍全题目，请对准重拍」，同时给「手动框选」入口；有框但质量差 → 提示但不阻断 |
| 服务端（上传后） | 大模型判断 | 网络 + 模型 | 返回 `40002 不是题目`，客户端弹框回到裁剪页 |

客户端不做「是不是题目」的模型推断，只做「有没有找到一个合理的四边形区域」。这是有意的：本地做语义判断要再引入一个模型（包体和维护都不划算），而几何检测已经能过滤掉空白桌面、风景这类画面。

### 3.2 四角对齐与微调

现有 `CropAssistProvider.suggest()` 已经给出 `CropNorm`（归一化四角），`CropOverlay` 支持拖动。要补的是：
- 检测失败或 `NoSuggestion` 时，默认框设成「画面内缩 8% 的矩形」，而不是空白；
- 拖动四角的命中半径按屏幕密度换算（现在固定值，手机上偏小）；
- 框选完成后**先本地裁一次预览**（不重新编码），确认没有切到题目边缘再上传。

### 3.3 上传接口（保持现有形状，服务端补齐）

客户端已有的约定（`HttpPhotoSolveRepository`）：

```
POST {base}/photosolve/upload   multipart，字段 image（JPEG）
  → { code: 0, message, data: { imageId } }

POST {base}/photosolve/solve    JSON { imageId }
  → { code: 0, message, data: { question, answer } }
```

建议**扩展而不是推翻**：

```json
POST /photosolve/solve
{ "imageId": "…", "clientTraceId": "uuid", "locale": "zh-CN" }

{
  "code": 0,
  "message": "ok",
  "data": {
    "question": "已知 x^2 - 5x + 6 = 0，求 x。",
    "questionType": "equation",       // 便于客户端选图标/板块
    "steps": ["因式分解：…", "…"],
    "finalAnswer": "x1 = 2, x2 = 3",
    "confidence": 0.92,
    "model": "doubao-vision",
    "traceId": "…"
  }
}
```

- `answer` 保留（旧客户端兼容），服务端同时返回结构化的 `steps` / `finalAnswer`；新客户端优先用 `steps`，没有时退化为「按行拆 answer」。
- 新增 `photo` 上传也可以合并成一个 multipart + solve 接口，但**不建议**：上传和解题分开，重试时不用重传图片。

### 3.4 错误码（沿用现有，补两条）

| 码 | 含义 | 客户端处理 |
|---|---|---|
| 40001 | 图片不清晰 | 提示重拍，允许「仍然提交」 |
| 40002 | 不是题目 | 弹框 → 回到裁剪页 |
| 40003 | 图片问题（过大/格式） | 自动压缩后重试一次 |
| **40004** | 题目完全无法识别（模型返回空） | 提示换角度重拍 |
| 42901 | 额度用完 | 显示升级入口 |
| 50001 | 服务失败 / 网络异常 | 保留图片，提供重试 |
| **50002** | 模型超时 | 自动重试一次（指数退避），仍失败则提示 |

### 3.5 客户端调用层

`:network` 已有挂起封装（`BaseServiceAwait.kt` 的 `awaitPost` / `awaitUpload`）和 `NetConfig` 注入。`HttpPhotoSolveRepository` 目前**自己 new 了一个 OkHttpClient**（15s/60s 超时），这是重复实现，应当改为注入 `BaseService`，好处：
- 复用 `NetConfig` 的域名、请求头、登录失效处理；
- 复用 DNS 与证书配置（`UnsafeOkHttpClient`）；
- 便于在测试里替换。

改造点：构造函数从 `(baseUrl, OkHttpClient)` 改为 `(BaseService)`，内部用 `awaitUpload` / `awaitPost`。

---

## 4. 服务端设计（testserverV）

### 4.1 放在哪个模块

`vvv-common` 里已经有豆包的两套客户端：
- `service/impl/llm/DoubaoAI.java`：火山 MaaS SDK（`MaasServiceImpl`），文本对话；
- `vvv-link` 的 `VisionDoubaoHandler`：火山方舟 SDK（`ArkService`），**图片理解**，模型 `doubao-vision-lite-32k-241015`，读取 `Secrets.get("vision_doubao.appKey")`。

拍照解题要「看图 + 解题」，应该用**方舟的视觉模型**（`ArkService` + 多模态消息），而不是 MaaS 文本 SDK。

**建议新建模块 `vvv-solve`**，而不是塞进 `vvv-link`：解题是独立业务，和 VPN/链路无关；`vvv-link` 已经是最大的模块。

```
vvv-solve/
  pom.xml
  src/main/java/com/timeline/solve/
    config/SolveProperties.java          @ConfigurationProperties("solve")
    api/PhotoSolveController.java        /photosolve/upload、/photosolve/solve
    api/PhotoSolveModels.java            请求/响应 record
    service/PhotoSolveService.java       编排：校验 → 调模型 → 解析 → 缓存
    service/DoubaoVisionSolver.java      方舟 SDK 调用 + prompt
    service/AnswerParser.java            模型输出 → steps / finalAnswer（可单测）
    storage/UploadStore.java             临时图片：本地磁盘或 TOS
```

### 4.2 配置（留空给用户填）

```yaml
solve:
  enabled: true
  model:
    endpoint: https://ark.cn-beijing.volces.com/api/v3/   # 火山方舟
    model-name: doubao-vision-lite-32k-241015             # 可换成更强的视觉模型
    temperature: 0.2
    max-tokens: 1500
    timeout-ms: 45000
  upload:
    dir: /home/web/config/photosolve           # 临时图片目录
    max-bytes: 8388608                         # 8 MB
    ttl-minutes: 30
```

密钥**不写进 yml**，走现有的 `secrets.properties`（`Secrets.get(...)`）：

```properties
# secrets.properties
solve.doubao.apiKey=
```

`secrets.properties.example` 里加一行空值即可。没有配置时 `/photosolve/solve` 返回 `50001` 并在日志里 warn，**不影响服务启动**（和现有密钥的处理一致）。

### 4.3 接口

```
POST /photosolve/upload        multipart: image
  → { code, message, data: { imageId } }
  imageId = 内容 sha256 前 16 位（天然去重），文件存到 solve.upload.dir

POST /photosolve/solve         application/json
  → { code, message, data: { question, questionType, steps[], finalAnswer, confidence, model, traceId } }
```

幂等：同一 `imageId` 10 分钟内重复请求直接返回缓存结果（模型调用贵且慢）。

### 4.4 Prompt

System：

```
你是一名中小学数学老师。用户会给你一张题目照片。请：
1. 先判断图片是否包含一道可以作答的题目。不是题目（空白、风景、人脸、无关文字）时只输出 {"type":"not_question"}。
2. 是题目时：抄写题干到 question（保留公式，用普通文本，不要 LaTeX 渲染语法之外的额外说明）。
3. 分步骤解答，每步一行，中文，简洁。最后一行只写最终答案。
4. 不确定时在 confidence 给 0~1 的小数。

只输出一个 JSON 对象，不要 Markdown 代码块，不要额外解释：
{"type":"question"|"not_question","question":"…","questionType":"equation|geometry|arithmetic|other","steps":["…"],"finalAnswer":"…","confidence":0.0}
```

User：图片 + 「请解答这道题。」（若客户端有用户补充文字，拼在其后）

**解析**：服务端先 strip 首尾的 ``` 代码围栏，再 `ObjectMapper.readTree`；解析失败时退回「按行拆分」并把原文放 steps，避免整单失败（解析失败也记 `50002` 便于排查）。

### 4.5 客户端契约

- 请求头带现有的应用 token（与网关一致）；服务端从 token 取用户 id 做额度与审计。
- 响应严格保持 `{code, message, data}`，和客户端 `HttpPhotoSolveRepository.execute()` 现已解析的结构一致，客户端改动最小。

---

## 5. 实施顺序

| 步骤 | 内容 | 依赖 |
|---|---|---|
| 1 | `vvv-solve` 模块骨架 + `/photosolve/upload`（本地存储、去重、大小校验）+ 单测 | 无 |
| 2 | `AnswerParser` + prompt + `DoubaoVisionSolver`（读取 `Secrets`），先用 curl 验证 | 1 |
| 3 | `/photosolve/solve`（缓存、错误码映射、结构化响应） | 1、2 |
| 4 | 客户端 `HttpPhotoSolveRepository` 改走 `BaseService`，解析结构化结果 | 3 |
| 5 | 客户端「无检测框」提示与手动框选入口、拖动命中半径 | 无（可与 1–3 并行） |
| 6 | 手机联调：拍照 → 检测 → 框选 → 解题 → 结果 | 4、5 |

---

## 6. 验证

| 层 | 验证方式 |
|---|---|
| 服务端 | `mvn -pl vvv-solve test`：`AnswerParser` 单测（正常 JSON、带代码围栏、非题目、空输出）；上传去重与大小限制 |
| 联调 | `curl -F image=@question.jpg localhost:8080/photosolve/upload` → 取 imageId → `solve`；检查 code/steps/finalAnswer |
| 客户端 | 手机：清晰题目、模糊题目、非题目（桌面/风景）、超大图、断网各一次 |
| 失败路径 | 未配置 apiKey → 返回 50001 且服务不崩；模型超时 → 50002 并自动重试一次 |

---

## 7. 风险与待确认

| 风险 | 应对 |
|---|---|
| 方舟视觉模型对数学公式的识别精度 | 先用 20–30 张真实题目测，必要时换更强模型（只改配置） |
| 模型输出不是严格 JSON | 服务端解析失败时退化到按行拆分，不整单失败 |
| 图片存储与合规 | 只保留 30 分钟；不落用户标识到文件路径 |
| 现有 `docs/design/photo-solve-api.md` 建议复用 aixue「拍讲」 | **两条路都留着**：本方案是自建（可控、可改 prompt），aixue 是已上线链路（更成熟但鉴权与业务号未确认）。需要你决定走哪条；如果走 aixue，第 3 节的客户端契约要按它的预上传 + 轮询接口重写 |

**需要你确认**：
1. 解题走自建 `testserverV`（本方案）还是复用 aixue「拍讲」？
2. 是否同意新建 `vvv-solve` 模块，而不是放进 `vvv-link`？
3. 临时图片存本地磁盘还是 TOS（现有 `oss` 配置已接入 TOS）？
4. 豆包视觉模型用 `doubao-vision-lite-32k-241015` 还是更强的版本（成本不同）。
