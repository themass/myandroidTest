# 拍照解题端到端设计：检测 / 对齐 / 裁剪 + 服务端解题

日期：2026-11-11
分支：`claude/happy-fermi-300550` → 已合并进 `claude/vigorous-goldwasser-584d3f`（基线 `weifu-refactor`）
服务端：`/Users/gqli/work/testserverV`（分支 `testjava_springboot`）
相关：`docs/design/permissions-design.md`、`docs/design/photo-solve-api.md`（历史草案，本文取代其接口部分）、`docs/design/architecture.md`

---

## 0. 状态总览

| 项 | 状态 | 证据 |
|---|---|---|
| 端到端流程设计（第 2 节） | **已完成** | 本文 |
| `QuadNorm` / `QuadWarp`（透视矫正） | **已实现** | `imaging/.../quad/` |
| 端上「是不是题目」几何预筛 | **已实现** | `QuestionFrameCheck.kt`、`QuestionQuadDetector.kt` |
| 四角微调页 | **已实现** | `feature-photosolve/.../ui/QuadAdjustScreen.kt` |
| ViewModel 状态机重写 | **已实现** | `feature-photosolve/.../presentation/PhotoSolveViewModel.kt` |
| 客户端调用层（走 `:common` 挂起封装） | **已实现** | `data/NetworkPhotoSolveRepository.kt` |
| 服务端 `solve.json` / `health.json` | **已实现**（可编译） | testserverV `vvv-link/.../photosolve/` |
| 提示词 + 豆包调用 | **已实现**（未联调） | `PhotoSolvePrompt.java`、`DoubaoPhotoSolveProvider.java` |
| 端到端调豆包 | **未验证** | 本机无 `photosolve.doubao.apiKey` |
| 额度、幂等去重 | **设计但未实现** | 第 5 节风险 1 |
| 多题切换 UI | **设计但未实现** | 第 5 节风险 8 |
| 端上 provider 直接吐四点 | **设计但未实现**（建议） | 第 3.3 节 |

### 合并时校正的两处（已按定稿实现）

1. **挂起封装的位置**：设计稿写的是 `:network` 的 `com.openapi.network.awaitUpload`，但第二轮重构已把
   `:network` 并入 `:common`，现在是 `com.openapi.common.net.awaitUpload`（`common/.../net/BaseServiceAwait.kt`）。
2. **错误码被吞掉**：`awaitUpload` 失败时返回 `null`，并在 `HttpClient.showVolleyError` 里弹一句通用 toast，
   服务端 `errno` 传不到界面——40002「不是题目」会被当成「服务不可用」，用户永远回不到取景页。
   合并时为此加了两处小改动：
   - `NetError.BusinessError` 现在带 `errno`（`BaseRequest.parserData` 从信封里传进来），所有既有调用方行为不变；
   - 新增 `awaitUploadOrThrow`，失败时抛 `NetError` 而不弹 toast；`NetworkPhotoSolveRepository` 用它，
     把 errno 映射成 `PhotoSolveError`，未知错误仍按可重试处理。

客户端**没有新增配置项**（不走独立的 `PhotoSolveConfig.baseUrl()`）：`Constants.getUrl(PHOTOSOLVE_SOLVE_URL)`
复用既有的 `BASE_IP` + `/vvv/api` 前缀，服务端 `PhotoSolveController` 的映射是 `/api/photosolve/v1/solve.json`。
旧的 `HttpPhotoSolveRepository` 与 `app/build.gradle` 的 `photosolveBaseUrl` 已在合并时删除。

---

## 1. 现状

### 1.1 客户端

| 模块 | 已有能力 | 文件 |
|---|---|---|
| `:feature-photosolve` | 完整流程：首页 → 拍照（CameraX）/相册/ML Kit 扫描 → 裁剪 → 清晰度提示 → 识别中 → 结果 | `PhotoSolveActivity.kt`、`ui/*.kt` |
| | 自绘裁剪页：四角 + 边中点手柄、旋转、镜像、自动识别对比 | `ui/PhotoCropScreen.kt`、`ui/CropOverlay.kt`、`ui/CropBitmapOps.kt` |
| | 解题仓库抽象 + Mock（按顺序回放 40001/40002/40003/42901/50001） | `data/PhotoSolveRepository.kt` |
| | 旧的 HTTP 实现（直连 myconf 的 `/photosolve/upload` + `/solve`，从未部署）——**已删除** | ~~`data/HttpPhotoSolveRepository.kt`~~ |
| `:imaging` | DocQuadNet 角点检测（ONNX，`corner_heatmaps` 1×4×64×64）、OpenCV 文档增强、Real-ESRGAN 超分、质量分析 | `cropassist/`、`image/`、`quality/` |
| `:common`（`net/`） | `BaseService` + 挂起扩展 `awaitPost` / `awaitUpload` / `awaitGet`；token、设备头、AES 数据解码统一在 `BaseRequest`/`DataBuilder` | `common/src/main/java/com/openapi/common/net/` |

**关键发现（决定了本次改动的形状）**：
1. `DocQuadNetCropAssistProvider` 的模型**本来就输出四个角的热力图**，但 `CropAssistResult.SuggestedBox` 只保留了外接矩形 → 斜拍的题目会被裁进大量背景。端上检测已经能给出「有没有像纸面/屏幕的四边形」，只是被压扁成了矩形。
2. 现有裁剪页只支持**轴对齐矩形**，没有「拖动四个角 + 透视矫正」。
3. 旧 `HttpPhotoSolveRepository` 自己 new 了 `OkHttpClient`，绕开 `:common` 的登录态与信封解析；它对接的 myconf 接口从未部署，**本次已删除**（连同 `app/build.gradle` 的 `photosolveBaseUrl`）。

### 1.2 服务端（testserverV）

| 项 | 现状 |
|---|---|
| 工程 | Spring Boot 3 多模块 Maven（`vvv-common` / `vvv-link` / ...），`vvv-link` 是 Web 层 |
| 统一响应 | `JsonResult{errno, data, error}`；业务错误通过 `ApiException(status, msg)` 映射 errno |
| 鉴权 | `@UserInfo BaseQuery` 解析 token 头；`DevAppContext` 提供每个 App 的配置 |
| 大模型 | 已集成火山方舟 Ark SDK（`volcengine-java-sdk-ark-runtime`），已有 `ChatDoubaoHandler`、`VisionDoubaoHandler`（base64 data URL + `multiContent` 的图片消息） |
| 密钥 | `Secrets.get(key)` 从 jar 外的 `secrets.properties` 读取，模板是 `secrets.properties.example` |
| 拍照解题 | **不存在**（只有 `docs/photo-solve-api.md` 的草案） |

---

## 2. 端到端流程（改造后）

```
首页
 └─ 拍照 / 选图
     └─ 端上检测（DocQuadNet）──────── 不可用 ──→ 手动框选（旧裁剪页）
         ├─ 检测到四边形 → 四角微调页（拖动 4 个角） → 透视矫正（OpenCV）
         └─ 未检测到 → 取景页提示「没找到题目」+「手动框选」退路
             └─ 质量检查（清晰度/曝光）→ 不合格弹窗（重拍 / 仍然识别）
                 └─ Recognizing（上传 + 解题）
                     └─ Result / 错误码分支（40001/40002/40003/42901/50001/50002）
```

与旧流程的差异点：
- **新增「是不是题目」的预筛**：端上做几何判断（没检测到四边形 → 直接提示），服务端做语义判断（模型返回 `questionFound=false` → errno 40002）。
- **透视矫正**取代了「外接矩形裁剪」写死的做法：斜拍的题目会被拉正，OCR/模型识别的准确率显著提高。
- **四角可拖**：检测结果不总是完美（桌面边缘、书页弯曲），用户能微调。

---

## 3. 客户端实现

### 3.1 新增文件

| 文件 | 作用 |
|---|---|
| `imaging/.../quad/QuadNorm.kt` | 归一化四边形；`ordered()` 规范化成 TL→TR→BR→BL（先取最左上/最右下作对角，再按叉积分侧；退化时按质心极角排序）；`isValid` 面积 8%–98.5% 且不自交；`outputSizePx()` 按边长估算输出尺寸 |
| `imaging/.../quad/QuadWarp.kt` | `QuadWarp.warp(bitmap, quad)`：OpenCV `getPerspectiveTransform` + `warpPerspective`；OpenCV 不可用或失败时退化为外接矩形裁剪，**不会抛异常** |
| `feature-photosolve/.../presentation/QuestionFrameCheck.kt` | 端上预筛结果：`Found(quad)` / `NotFound(reason)` / `DetectorUnavailable(reason)` |
| `feature-photosolve/.../presentation/QuestionQuadDetector.kt` | 把 `CropAssistProvider` 的矩形结果转成四边形；检测不到时给一个中间 80% 的默认框 |
| `feature-photosolve/.../ui/QuadAdjustScreen.kt` | 四角微调页：图片 Fit 显示、四边形填充/描边、四角 28dp 触摸热区、拖动实时更新、「重新检测」「裁剪并识别」 |
| `feature-photosolve/.../presentation/PhotoSolveError.kt` | 错误码常量 + 面向用户的文案 + `isRetryable`（与服务端 `PhotoSolveErrno` 一一对应） |
| `feature-photosolve/.../data/PhotoSolveApiModels.kt` | 服务端响应的 Gson 模型（`questionFound` / `questions[].steps[]` / `confidence`） |
| `feature-photosolve/.../data/NetworkPhotoSolveRepository.kt` | 真实实现：`ImageCompressor` 压缩 → 写临时文件 → `BaseService.awaitUpload(Constants.getUrl("/photosolve/v1/solve.json"))` → 映射成 `PhotoSolveResult`；失败按 errno 抛 `PhotoSolveException` |

### 3.2 修改

| 文件 | 改动 |
|---|---|
| `PhotoSolveViewModel.kt` | 重写状态机：`onPhotoCaptured` 先检测（`detecting` 状态）→ `QuadAdjust` 或留在取景页并给出 `QuestionFrameCheck`；`onQuadConfirmed` 做透视矫正 → 质量检查 → 解题；`onManualCropRequested` 走旧裁剪页；错误按 `PhotoSolveError` 分支，`onRetry` 复用同一张裁剪图 |
| `PhotoSolveUiState.kt` | 新增 `QuadAdjust` 步骤、`detecting`、`quad`、`frameCheck`、`errorCode`；`PhotoSolveResult` 增加 `latex` / `requestId` / `providerUsed` / `questionCount`（都有默认值，Mock 不用改） |
| `PhotoSolveActivity.kt` | 接 `QuadAdjustScreen`；相机权限改走 `PermissionGateway`（见 permissions-design）；取景页显示检测提示 |
| `PhotoCaptureScreen.kt` | 新增 `detecting` / `notice` / `onManualCrop` 参数：检测中显示「检测中…」，没找到题目时显示黄色提示 + 「手动框选」 |
| `PhotoSolveRepository.kt` | `PhotoSolveException` 移到 `presentation/PhotoSolveError.kt`（错误码属于界面契约） |
| `core/.../Constants.java` | 新增 `PHOTOSOLVE_SOLVE_URL = "/photosolve/v1/solve.json"`、`PHOTOSOLVE_HEALTH_URL` |
| `feature-photosolve/build.gradle` | 新增 `:core` 依赖（挂起封装已随模块合并进入 `:common`，而 `:common` 本来就是它的依赖；`ModuleGraphTest` 通过） |

### 3.3 为什么复用 `DocQuadNetCropAssistProvider` 而不是改它的返回类型

`CropAssistProvider.suggest()` 的返回类型 `CropAssistResult.SuggestedBox(CropNorm)` 同时被裁剪页（A27 手动框选流程）使用，改它会牵动一整条已经稳定的交互。本次把「四点」这层单独放在 `:imaging/quad`，`QuestionQuadDetector` 内部做「矩形 → 四边形」的转换；**等 DocQuadNet 的输出改成四点后，只需要改 `QuestionQuadDetector.detect()` 一行**，接口不变。

> 后续优化（推荐）：让 `DocQuadNetCropAssistProvider` 额外暴露 `detectQuad(bitmap): QuadNorm?`，把热力图的四个峰值直接返回（代码里已经有 `points` 的计算，只是被 `min/max` 压成了矩形）。这样斜拍时四边形是真实的梯形，透视矫正效果会好很多。本次没有改，是因为它属于 `imaging` 的既有能力重构，超出了「三项设计」的范围。

### 3.4 从对话里进入

`ChatRoute` 的「拍照解题」入口现在用显式组件名启动 `PhotoSolveActivity`（feature 之间不互相依赖）。保持不变。

---

## 4. 服务端实现（testserverV）

### 4.1 新增文件

| 文件 | 作用 |
|---|---|
| `vvv-link/.../photosolve/model/PhotoSolveResponse.java` | 统一响应体：`requestId` / `providerUsed` / `subject` / `questionFound` / `questions[]`（`text`、`latex`、`steps[]`、`finalAnswer`、`finalAnswerLatex`、`confidence`）/ `extensions` |
| `vvv-link/.../photosolve/model/PhotoSolveErrno.java` | 错误码常量 + 默认文案（40000 / 40001 / 40002 / 40003 / 42901 / 50001 / 50002） |
| `vvv-link/.../photosolve/PhotoSolveConfig.java` | 从 `Secrets` 读取 `photosolve.doubao.apiKey` / `.model` / `.endpoint` / `maxImages` / `maxBytes` / `timeoutSeconds`；`requireConfigured()` 在收图前就返回 50002 |
| `vvv-link/.../photosolve/PhotoSolvePrompt.java` | 中文系统提示词（输出 JSON 契约、判断规则、解题要求、LaTeX 转义）+ 用户提示词（拼学科/年级/补充文字） |
| `vvv-link/.../photosolve/PhotoSolveProvider.java` | 模型抽象：`solve(images, mimeTypes, text, subject, grade, requestId)`；换模型只加实现 |
| `vvv-link/.../photosolve/DoubaoPhotoSolveProvider.java` | 豆包实现：图片 → base64 data URL → `ChatCompletionContentPart`（`type=image_url`）+ 文字部分；`temperature=0.2`、`maxTokens=4096`；用 `CompletableFuture.get(timeout)` 控制超时；`extractJson()` 从 ```json 围栏/前后说明里抠 JSON；解析失败时把原文塞进一条 question 兜底 |
| `vvv-link/.../photosolve/PhotoSolveController.java` | `POST /api/photosolve/v1/solve.json`（multipart，`images` 可重复）+ `POST /api/photosolve/v1/health.json` |

配置项（写到 `secrets.properties`，模板已加到 `secrets.properties.example`，**不含真实密钥**）：

```properties
photosolve.doubao.apiKey=      # 火山方舟 API Key
photosolve.doubao.model=       # 接入点 id（ep-xxxx）或视觉模型名
photosolve.doubao.endpoint=    # 默认 https://ark.cn-beijing.volces.com/api/v3/
photosolve.maxImages=          # 默认 4
photosolve.maxBytes=           # 默认 5242880（5MB）
photosolve.timeoutSeconds=     # 默认 60
```

### 4.2 请求 / 响应

**请求** `POST /api/photosolve/v1/solve.json`，`multipart/form-data`：

| 字段 | 必填 | 说明 |
|---|---|---|
| `images` | 是 | 可重复；JPEG/PNG/WebP；单张 ≤ 5MB；最多 4 张 |
| `text` | 否 | 用户补充说明（「第二问是求面积」） |
| `subject` | 否 | 学科提示：`math` / `physics` / … |
| `grade` | 否 | 年级提示 |
| `clientRequestId` | 否 | 客户端 UUID；不传时服务端生成。用于日志/去重 |

**响应（成功）**：

```json
{
  "errno": 0,
  "data": {
    "requestId": "4d9c…",
    "providerUsed": "ep-2025xxxx",
    "subject": "math",
    "questionFound": true,
    "questions": [{
      "index": 1,
      "text": "解方程 $x^2-5x+6=0$",
      "latex": "x^2-5x+6=0",
      "steps": [
        {"index": 1, "text": "因式分解", "latex": "(x-2)(x-3)=0"},
        {"index": 2, "text": "得 x-2=0 或 x-3=0", "latex": ""}
      ],
      "finalAnswer": "x=2 或 x=3",
      "finalAnswerLatex": "x_1=2,\\ x_2=3",
      "confidence": null
    }],
    "extensions": null
  },
  "error": null
}
```

> 外层 `data` 在非测试 App 下会被 `JsonResult.getData()` 做 AES 加密（项目既有行为），客户端 `DataBuilder` 会解。测试 App（`app.isTest()`）直接返回明文。

**响应（失败）**：`errno` 非 0，`error` 是可直接展示的中文。

| errno | 含义 | 客户端处理 |
|---|---|---|
| 40000 | 参数错误（缺图、图片为空） | 提示重试 |
| 40001 | 图片不清晰 | 复用模糊弹窗：「重拍」/「仍然识别」 |
| 40002 | 图片里没有题目 | 回到取景页，提示「没找到题目」 |
| 40003 | 图片过大或格式不支持 | 提示换图 |
| 42901 | 额度用完 | 提示额度 |
| 50001 | 模型失败/超时 | 显示「重试」 |
| 50002 | 服务端未配置模型 | 「服务暂时不可用」 |

### 4.3 提示词（摘要）

系统提示词要求：
1. **只输出一个 JSON 对象**，字段固定；给出完整结构示例。
2. **先判断有没有题目**：桌面/人脸/风景/空白纸 → `questionFound=false`、`questions=[]`。
3. 选择题保留选项全文，答案写选项字母 + 内容。
4. 多小题按编号拆成多个 `questions` 元素。
5. 看不清的地方写明「图片此处不清晰，按……理解」，**不许臆造条件**。
6. 每小题按「审题 → 列式 → 求解 → 检验」组织步骤，每步一件事并说明依据；保留分数/根号，最终答案才给小数近似。
7. LaTeX 写在 JSON 字符串里，反斜杠转义成两个；中文作答、不寒暄。

完整文本见 `PhotoSolvePrompt.java`。

### 4.4 超时与重试

| 层 | 策略 |
|---|---|
| 客户端 `:common`（net） | 上传走 `HttpClient.uploadClient`：connect 40s / read 180s / write 180s；失败自动重试 1 次（`MAX_ATTEMPTS=2`）。业务错误（errno 非 0）不重试 |
| 服务端调模型 | `CompletableFuture.get(photosolve.timeoutSeconds)`（默认 60s）；超时 `cancel(true)` 并返回 50001 |
| 客户端 UI | 50001 显示「重试」，`onRetry()` 用同一张裁剪图再发一次（不重新拍照） |
| 幂等 | `clientRequestId` 由客户端生成；当前服务端只用于日志，**尚未做去重扣费**（见风险） |

### 4.5 服务端「题目检测/对齐」为什么放在端上

需求里问「题目检测/对齐接口（如果服务端做）」。本设计的选择是：**端上做检测和对齐，服务端只做语义解题**。理由：

- DocQuadNet 已经在端上（13.4MB，离线可用），推理几百毫秒；再传到服务端做一遍只会增加一次往返和带宽。
- 四角微调必须是端上交互（用户拖动手感），服务端做检测也拿不到「用户想怎么调」。
- 透视矫正用 OpenCV 在端上完成，上传的就是「正好的题目」，服务端不用处理旋转/梯形。
- 服务端保留的能力：**语义判断**（是不是题目）、错误码 40001/40002，以及未来可能的「多图拼题」。

如果将来要在服务端做检测（例如端上模型包体积太大要下掉），接口层可以加 `POST /api/photosolve/v1/detect.json`，返回四个归一化角点，客户端换成调用它即可——`QuestionQuadDetector` 已经是这个形状。

### 4.6 部署

```bash
# 1. 配置密钥（不要提交到 git）
cp secrets.properties.example secrets.properties
vi secrets.properties      # 填 photosolve.doubao.apiKey / .model

# 2. 构建
mvn -pl vvv-link -am package -DskipTests

# 3. 启动（secrets 在 jar 外）
java -Dsecrets.file=/home/web/config/secrets.properties -jar vvv-link/target/*.jar

# 4. 自检
curl -s -X POST http://127.0.0.1:8080/vvv/api/photosolve/v1/health.json
# => {"errno":0,"data":{"provider":"ep-...","configured":true,"maxImages":4,...}}

# 5. 端到端（带 token）
curl -s -X POST http://127.0.0.1:8080/vvv/api/photosolve/v1/solve.json \
  -H "Authorization: <token>" \
  -F "images=@question.jpg" -F "subject=math" -F "clientRequestId=$(uuidgen)"
```

客户端配置（2026-11-11 定稿）：**走自建 testserverV，请求经 `Constants.getUrl()`**（复用项目既有 `BASE_IP` + `/vvv/api` 前缀），因此客户端**没有新增配置项**。

这条链路现在有两个实现并存，合并时已统一为一个：

| 实现 | 状态 |
|---|---|
| `NetworkPhotoSolveRepository`（`BaseService.awaitUpload`，路径常量 `Constants.PHOTOSOLVE_SOLVE_URL`） | **唯一使用中的实现**，由 `MyApplication` 安装 |
| `HttpPhotoSolveRepository`（自带 OkHttp 与 `photosolveBaseUrl`） | 连同 `app/build.gradle` 的 `PHOTOSOLVE_BASE_URL` buildConfigField 一起删除 |

注意 `Constants.PHOTOSOLVE_SOLVE_URL` 的值是 `/photosolve/v1/solve.json`（`getUrl` 已带 `/vvv/api` 前缀），服务端 `PhotoSolveController` 的映射必须与之对应。

---

## 5. 风险

| # | 风险 | 缓解 |
|---|---|---|
| 1 | **额度与鉴权未实现**：现在任何登录用户都能无限调用，模型费用不可控 | 上线前在 Controller 加按用户/天的计数（Redis，项目已有）；`clientRequestId` 做 5 分钟幂等去重 |
| 2 | **图片隐私**：题目图会发给第三方模型 | `DataBuilder`/服务端都不落盘（当前实现只用 base64 直传，不写磁盘）；隐私政策需说明「拍照解题会把图片发送给第三方 AI 服务」；后续可加 `imageRetentionDays` |
| 3 | ~~旧 `HttpPhotoSolveRepository` 与新实现并存~~ | **已解决**：旧类、`photosolveBaseUrl`、`PHOTOSOLVE_BASE_URL` 均已删除 |
| 4 | 模型偶尔不按 JSON 输出 | 提示词硬约束 + `extractJson()` 抠 JSON + 解析失败兜底成纯文本答案；`questionFound` 缺失时默认 true，避免误判「没题目」 |
| 5 | 端上检测器在线模型缺失（`assets/docquad/*.ort` 没打进包） | `QuestionQuadDetector.isAvailable()` 检测；不可用时直接进旧裁剪页并提示，不阻断流程 |
| 6 | 透视矫正对极端角度（>60°）效果差 | `QuadNorm.isValid` 限制面积；输出尺寸按边长估算；失败退化为外接矩形 |
| 7 | OpenCV 在部分 ABI/设备上初始化失败 | `QuadWarp` 内 `initLocal()` 失败即走外接矩形；`ImagePipeline.enhanceForDocument()` 同样是「失败返回原图」 |
| 8 | 多题（一张图 3 道）只展示第一道 | `questions[].index` 已在响应里；`PhotoSolveResult.questionCount` 已透传，结果页可以加「下一题」切换（本次未做 UI） |
| 9 | `maxTokens=4096` 对超长解答可能截断 | 服务端解析失败会走兜底；可配置化（后续加 `photosolve.maxTokens`） |
| 10 | 服务端 `errno` 与客户端常量双份维护 | 两处都在注释里写「不要改数值」；建议后续生成一份 `photo-solve-api.md` 作为唯一来源（本文第 4.2 节即该来源） |

---

## 6. 验证

```bash
# 客户端
./gradlew --offline :imaging:compileDebugKotlin
./gradlew --offline :feature-photosolve:compileDebugKotlin
./gradlew --offline :app:compileDebugKotlin

# 服务端（离线 Maven）
cd /Users/gqli/work/testserverV && mvn -o -q -pl vvv-link -am compile -DskipTests
```

已验证（2026-11-11）：
- 客户端三模块编译通过；`:mediaview` / `:imaging` 的 JVM 单测通过。
- 服务端 `mvn -o -pl vvv-link -am compile` 退出码 0。
- `ModuleGraphTest`（`:app` 的架构测试）通过：`:feature-photosolve` 依赖 `:common`、`:core`，符合分层。

**未验证**（需要真实环境）：
- 端到端调用豆包：本机 `secrets.properties` 没有填写 `photosolve.doubao.apiKey`，`health.json` 会返回 `configured:false`、`solve.json` 返回 50002。填好密钥后按 4.6 的 curl 验证。
- 真机上的 DocQuadNet 检测与透视矫正效果（需要 `assets/docquad/docquadnet256_trained_opset17.ort`，约 13.4MB，按既有约定不提交进仓库）。
