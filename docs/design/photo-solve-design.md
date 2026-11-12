# 拍照解题端到端设计：检测 / 对齐 / 裁剪 + 服务端解题

日期：2026-11-11
分支：`claude/happy-fermi-300550`（基线 `weifu-refactor`）
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
| 额度、限流、全站预算、幂等去重 | **已实现**（可编译，未联调） | 第 4.7 节；`PhotoSolveQuota.java` |
| 客户端拿到真实 errno | **已修复**（之前所有错误都显示成 50001） | 第 4.7.5 节 |
| 多题切换 UI | **设计但未实现** | 第 5 节风险 8 |
| 端上 provider 直接吐四点 | **设计但未实现**（建议） | 第 3.3 节 |
| 四角 + 四边拖动（保持凸四边形） | **已实现**（14 个单测） | `imaging/.../quad/QuadEdit.kt`、3.5 节 |
| LaTeX 渲染（Markwon + jlatexmath） | **已实现** | `markdown/MathText.kt`、3.6 节 |
| 服务端 LaTeX 反斜杠修复 | **已实现**（15 个单测） | `PhotoSolveJsonRepair.java`、4.8 节 |
| 思考过程（可折叠） | **已实现** | 3.7 节 |
| 解题历史（增删改查 + 详情） | **已实现** | `history/`、3.8 节 |
| 分辨率误报修复 | **已实现** | 3.9 节 |

### 本轮修复的 6 个问题（2026-11-12）

| # | 问题 | 根因 | 修法 |
|---|---|---|---|
| 1 | 只能拖四个角，边不能拖 | `QuadAdjustScreen` 只做了 `nearestCorner` | 新增 `QuadEdit.hitTest/moveEdge`，角优先、整条边可抓；拖边沿法线平移且两端角沿相邻边滑动（矩形仍是矩形，梯形仍是梯形） |
| 2 | 不支持 LaTeX | 客户端用 `Text` 直接显示源码；**服务端 JSON 解析还会静默吃掉 `\frac` 的反斜杠** | 客户端改 Markwon + jlatexmath；服务端解析前过 `PhotoSolveJsonRepair`；提示词改为公式直接写在 text/thinking/finalAnswer 里 |
| 3 | 每次提交都提示分辨率偏低 | 判据是「裁剪后短边 < 600px」，而一道题裁出来常是宽矮长条（如 1800×500） | 改为「长边 < 800 **或** 总像素 < 25 万」，并加日志输出实际尺寸与指标 |
| 4 | 结果页无法回主界面 | 结果页只有「再拍一题」 | 顶栏加关闭按钮，底部加「退出」；`onExit` 回首页状态而不是 finish Activity |
| 5 | 没有解题历史 | 未实现 | 新增历史模块（文件存储，见 3.8） |
| 6 | 没有思考过程 | 提示词没要求，模型也没这个字段 | 服务端加 `thinking` 字段与要求；客户端默认折叠展示 |

### 一处与你描述不同的地方（请确认）

你写的是「走 `:common` 的挂起封装 + NetConfig 注入」，**这一点在合并后已经成立**：
`com.openapi.common.net.awaitUpload`（`common/src/main/java/com/openapi/common/net/BaseServiceAwait.kt`）。
合并前它在 `:network`，`weifu-refactor` 的 `b20814bf` 把 `:network`/`:uikit`/`:ads` 并回了 `:common`，
本次合并提交 `90dbfcb3` 已把调用点改到新包名。
`:common` 只有压缩、EXIF 解码这类纯工具。`NetConfig` 是给**请求头名**用的（token 头、设备头、Referer），
由 `MyApplication` 在启动时 `init` 一次；**拍照解题不需要自己的注入点**，因为 `:common` 的
`BaseRequest` 已经统一带上了。如果你的原意是「拍照解题要有独立的 baseUrl 注入」，请说一声，
我加一个 `PhotoSolveConfig.baseUrl()` 并走 `Constants.getUrl()` 之外的路径。

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

### 3.5 四角 / 四边拖动（`imaging/.../quad/QuadEdit.kt`）

几何全部在像素空间计算（归一化坐标在非正方形图片上 x、y 量纲不同，直接算会让边拖歪）：

- **点选**：先找 28dp 内的角（角优先），再找 22dp 内的边；边的**整条线段**都是热区，不只是中点。
- **拖角**：该角自由移动，夹在 `[0,1]`，结果经 `QuadNorm.ordered()` 规范化。
- **拖边**：位移取手指位移在**法线**上的投影（平行分量忽略），边两端的角分别沿相邻两条边的延长线滑动。
  即求「平移后的边所在直线」与「相邻边所在直线」的交点。对矩形结果是矩形，对梯形两条腰方向不变。
  结果必须通过 `QuadNorm.isValid` 且所有角在 `[0,1]` 内；不合法就按 100%/75%/50%/25%/10% 逐级缩小位移，
  都不行返回 null（保持原样）。**角点顺序保持不变**，拖动过程中「正在拖哪条边」的编号不会漂移。
- 界面：四条边中点画药丸形手柄暗示可拖；正在拖的边高亮加粗。

### 3.6 LaTeX 渲染（`markdown/MathText.kt`）

题目、步骤、答案里全是公式。Compose 没有成熟的 LaTeX 渲染，沿用 `:feature-chat` 的方案：

- Markwon 负责 Markdown 解析与排版，`AndroidView` 桥接进 Compose；
- `ext-latex`（jlatexmath-android）画公式；
- `SingleDollarLatexPlugin` 补官方插件不支持的行内 `$...$`（官方只认 `$$...$$`）；
- `MathMarkdown.normalize()` 在渲染前统一 `\( \)` / `\[ \]` 定界符、收敛多余反斜杠层数；
- 公式渲染失败时用 `errorHandler` 把 LaTeX 源码画成红色文字，而不是整段消失。

`MathMarkdown.plain()` 另做一份「公式 → 纯文本」的降级（`\frac{1}{2}` → `1/2`、`\sqrt{3}` → `√3`、`x^2` → `x²`），
给历史列表预览用——列表里每行都起一个公式渲染成本太高。

### 3.7 思考过程

服务端 `PhotoSolveResponse.Question.thinking`（Markdown + LaTeX），提示词要求 80–250 字，只写审题思路不重复计算。

客户端 `ThinkingSection`：

- **默认折叠**。理由：学生第一眼要看答案对不对、步骤怎么写；全部展开会把答案挤出屏幕。
- 折叠时标题右侧显示一行摘要（正文第一句，超过 80 字截断），不展开也能判断要不要看。
- 整块可点切换，箭头有旋转动画；展开状态不持久化。
- 旧服务端没有 `thinking` 字段时整块隐藏，不显示空框。

### 3.8 解题历史（`history/`）

| 文件 | 作用 |
|---|---|
| `SolveRecord.kt` | 记录模型 + `SolveRecordCodec`（步骤列表编解码、默认标题） |
| `SolveHistoryStore.kt` | 接口：`recent/get/save/rename/setFavorite/delete/clear` |
| `FsSolveHistoryStore.kt` | 文件实现（见下） |
| `SolveHistoryViewModel.kt` | 列表、搜索、收藏过滤、重命名/删除/清空 |
| `SolveDetailViewModel.kt` | 详情、改名、收藏、删除 |
| `ui/SolveHistoryScreen.kt` | 列表页 |
| `ui/SolveHistoryDetailScreen.kt` | 详情页 |

**存储为什么用文件而不是 Room**：记录里最大的数据是题目图，图本来就得放文件系统；索引很小（几百条几十 KB）；
写一条记录 = 两张图 + 一次索引更新，Room 也要自己处理「库里有记录但图片丢了」。另外 `:feature-photosolve`
现在没有 ksp 插件，引 Room 要改构建配置。

```
filesDir/photosolve_history/
  index.json        索引（先写 .tmp 再 rename）
  img/<id>.jpg      长边 ≤ 1280
  thumb/<id>.jpg    长边 ≤ 240
```

- 写操作全部串行（`Mutex`）；索引损坏时备份成 `index.corrupt-<ts>.json` 并从空开始，不让用户一进页面就崩。
- 同一个 `requestId` 只保留一条：重试成功更新原记录，不会出现两条。
- 用户改过标题就保留，不被题干覆盖。
- **自动保存**：解题成功即写入，不要求用户手动「保存」（否则没人会存）。保存失败只记日志，不影响结果展示。

页面流：首页右上角时钟图标 → 列表 → 点条目进详情。系统返回键：详情 → 列表 → 首页。
列表支持搜索（标题/题干/答案）、只看收藏；长按或「⋯」菜单可收藏、重命名、删除；顶部可清空。

### 3.9 分辨率误报

旧判据 `短边 < 600px` 的问题：一道题裁出来是宽矮长条（1800×500），短边只有 500，但字很清楚，
于是几乎每次提交都被「分辨率偏低」打断。

新判据（`ImageQualityAnalyzer.isLowResolution`）：**长边 ≥ 800 且总像素 ≥ 25 万**，两项都不满足才提示。
字的大小取决于整页宽对应多少像素，与短边无关。`QuestionImagePreparer.qualityOf` 现在传真实宽高，
并且 ViewModel 打一行日志（裁剪尺寸、清晰度、亮度、命中的问题项），下次再有反馈可以直接定位。

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
| 42901 | 用户今日额度用完 / 一分钟内请求过多 | 回首页弹窗，用服务端文案（带「每天 N 次」），不提供重试 |
| 42902 | 全站当日预算用完（熔断） | 同上，文案「今日识别服务已满」 |
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
| 幂等 | `clientRequestId` 由 ViewModel 生成；**同一张图重试沿用同一个 id**，服务端命中缓存直接回放（见 4.7） |

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

客户端配置：**不需要额外配置**。`Constants.getUrl(Constants.PHOTOSOLVE_SOLVE_URL)` 复用项目既有的 `BASE_IP` + `/vvv/api` 前缀，和服务端 `PhotoSolveController` 的 `/api/photosolve/v1/solve.json` 对得上。旧的 `HttpPhotoSolveRepository` 与 `photosolveBaseUrl` / `PHOTOSOLVE_BASE_URL` 已删除，`MyApplication` 现在直接安装 `NetworkPhotoSolveRepository(this)`。

### 4.7 额度、限流与幂等（`PhotoSolveQuota.java`）

#### 4.7.1 为什么必须登录

每次 `solve.json` 都是一次付费的模型调用。原来是 `@UserInfo`（`required=false`），**空 token 也能调**，
既无法按人计额也无法定位滥用者。现在改成 `@UserInfo(required = true)`，未登录由 `UserInfoResolver`
抛 token 过期（errno 2，客户端走既有的重新登录流程）。`health.json` 不碰模型，仍免登录。

#### 4.7.2 四道闸（从外到内，全部 Redis 计数）

| 闸 | Redis 键 | 默认 | 超限返回 | 作用 |
|---|---|---|---|---|
| ① 幂等回放 | `photosolve:idem:{userId}:{clientRequestId}`，TTL 600s | 10 分钟 | —（直接返回上次结果） | 重试不重复调模型、不重复扣费 |
| ② 分钟限流 | `photosolve:rl:m:{yyyyMMdd}:{userId}:{epochMinute}`，TTL 2min | 3 次/分 | 42901「请求过于频繁」 | 挡脚本 |
| ③ 用户日额度 | `photosolve:quota:d:{yyyyMMdd}:{userId}`，TTL 2 天 | 免费 10 / VIP 50 | 42901「每天 N 次」 | 单账号成本上限 |
| ④ 全站日预算 | `photosolve:budget:d:{yyyyMMdd}`，TTL 2 天 | 2000 | 42902 | 熔断：小号刷也有单日账单硬上限 |

VIP 判断用 `UserPo.level >= Constant.UserLevel.LEVEL_VIP`（项目已有的等级字段，token 缓存里就有，不额外查库）。
日期按服务器本地时区切日。

#### 4.7.3 扣费时机与退还

```
参数/图片校验（40000/40003，不扣） → ①幂等命中？直接返回 → ②③④ 占用额度 → 调模型
                                                              ├ 成功 → 写幂等缓存 → 返回
                                                              ├ 40002 不是题目 → 不退（模型已经算过、钱已花）
                                                              └ 50001 超时/失败、或意外异常 → 退还 ③④
```

- **先占后调**：用 `INCR` 原子占位，超限再 `DECR` 回去；避免「先查后加」在并发下超发。
- **只缓存成功**：失败不写幂等缓存，用户点重试会真的再调一次（并重新扣一次）。
- **④ 超限时同时退 ③**：没调模型，不该占用户的次数。

#### 4.7.4 配置（`secrets.properties`，都有默认值，留空即可）

```properties
photosolve.quota.freePerDay=          # 默认 10
photosolve.quota.vipPerDay=           # 默认 50
photosolve.quota.perMinute=           # 默认 3
photosolve.quota.totalPerDay=         # 默认 2000，按豆包单价换算成你能接受的日账单
photosolve.quota.idempotentSeconds=   # 默认 600，0 关闭
```

> 定 `totalPerDay` 的方法：一次解题 ≈ 1 张图（视觉 token 约 1–2k）+ 系统提示词 ~1k + 输出 ≤4k token。
> 按你在方舟控制台看到的实际单价乘一下，得出单次成本，再除你接受的日上限。**数字请以控制台账单为准**，这里不写死。

新增 `POST /api/photosolve/v1/quota.json`（需登录）返回 `{limit, used, remaining}`，供首页显示「今日剩余 N 次」
（客户端展示尚未做，见风险 11）。

#### 4.7.5 客户端：之前 errno 根本到不了界面

排查时发现：`awaitUpload` 失败时只返回 `null`，`NetError.BusinessError` 也只带文案不带码，
所以**服务端返回的 40002 / 42901 在客户端一律变成 50001「服务暂时不可用」**——`PhotoSolveViewModel`
里按错误码分支的代码全是死代码。本次修复：

| 文件 | 改动 |
|---|---|
| `common/.../net/http/NetError.java` | `BusinessError` 增加 `errno` 字段（旧的单参构造保留，码为 `NO_CODE=-1`） |
| `common/.../net/request/BaseRequest.java` | 构造 `BusinessError` 时带上 `data.errno` |
| `common/.../net/BaseServiceAwait.kt` | 新增 `awaitUploadOrThrow`：失败**抛出** `NetError`、不弹默认 toast；取消协程时取消请求。原 `awaitUpload` 不变，其它调用方不受影响 |
| `NetworkPhotoSolveRepository.kt` | 改用 `awaitUploadOrThrow`，`BusinessError.errno` → `PhotoSolveException.code`；超时/断网 → 50001 |
| `PhotoSolveRepository.kt` | `solve(image, requestId)`：幂等键由调用方传入 |
| `PhotoSolveViewModel.kt` | 持有 `requestId`：新图换新值，`onRetry()` 沿用；42901/42902 回首页并显示服务端文案、不提供重试 |
| `PhotoSolveError.kt` | 新增 `BUDGET_EXHAUSTED = 42902` |

### 4.8 LaTeX 反斜杠修复（`PhotoSolveJsonRepair.java`）

模型要在 JSON 字符串里写 `\frac{1}{2}`，按规范必须写成 `\\frac{1}{2}`，但它经常只写一个反斜杠。两种后果：

| 写法 | JSON 解析结果 |
|---|---|
| `\sqrt`、`\pi`、`\(` | 非法转义，**解析直接抛异常**，整题退化成纯文本 |
| `\frac`、`\text`、`\beta`、`\right`、`\neq`、`\nu` | 命中 `\f \t \b \r \n`，**不报错但公式被静默破坏**（`\frac` → 换页符 + `rac`） |

第二种更危险，因为没有任何异常。`PhotoSolveJsonRepair.repair()` 在 `MAPPER.readTree` 之前逐字符扫描字符串内部：
已经是 `\\` 的保留；`\" / \/ / \uXXXX` 保留；其他合法转义字母看后面是不是已知 LaTeX 命令
（`frac`、`text`、`right`、`neq`…，约 100 个），是就补一个反斜杠；非法转义一律补。
已知歧义（`\nu` vs「换行 + u」）按最长匹配已知命令处理，并在类注释里写明取舍。

同时提示词改掉了重复：**公式只写在 `text`/`thinking`/`finalAnswer` 里**（`$...$` / `$$...$$`），
`latex` / `finalAnswerLatex` 要求填空字符串。客户端 `SolveResultMapper.merge()` 仍兼容旧服务端：
文本里没有该公式时把 latex 字段并回去，已有则不重复追加。

---

## 5. 风险

| # | 风险 | 缓解 |
|---|---|---|
| 1 | ~~额度与鉴权未实现~~ | **已实现**（4.7）。剩余风险：批量注册小号——由全站预算 42902 兜住单日账单上限，但会让正常用户当天也用不了；需要时再加按设备 id 的额度 |
| 1b | Redis 故障时额度失效（选择放行） | 日志里 `photosolve quota check failed` 的 WARN 需要接告警 |
| 2 | **图片隐私**：题目图会发给第三方模型 | `DataBuilder`/服务端都不落盘（当前实现只用 base64 直传，不写磁盘）；隐私政策需说明「拍照解题会把图片发送给第三方 AI 服务」；后续可加 `imageRetentionDays` |
| 3 | ~~旧 `HttpPhotoSolveRepository` 与新实现并存~~ | **已解决**：旧类、`photosolveBaseUrl`、`PHOTOSOLVE_BASE_URL` 均已删除 |
| 4 | 模型偶尔不按 JSON 输出 | 提示词硬约束 + `extractJson()` 抠 JSON + 解析失败兜底成纯文本答案；`questionFound` 缺失时默认 true，避免误判「没题目」 |
| 5 | 端上检测器在线模型缺失（`assets/docquad/*.ort` 没打进包） | `QuestionQuadDetector.isAvailable()` 检测；不可用时直接进旧裁剪页并提示，不阻断流程 |
| 6 | 透视矫正对极端角度（>60°）效果差 | `QuadNorm.isValid` 限制面积；输出尺寸按边长估算；失败退化为外接矩形 |
| 7 | OpenCV 在部分 ABI/设备上初始化失败 | `QuadWarp` 内 `initLocal()` 失败即走外接矩形；`ImagePipeline.enhanceForDocument()` 同样是「失败返回原图」 |
| 8 | 多题（一张图 3 道）只展示第一道 | `questions[].index` 已在响应里；`PhotoSolveResult.questionCount` 已透传，结果页可以加「下一题」切换（本次未做 UI） |
| 9 | `maxTokens=4096` 对超长解答可能截断 | 服务端解析失败会走兜底；可配置化（后续加 `photosolve.maxTokens`） |
| 12 | 历史记录只存在本机，换机/重装会丢 | 用户预期"历史"可能跨设备 | 记录结构与 `:core` 的接口无关，后续可以上报服务端；现在先在页面上说明是本地记录 |
| 13 | 历史图片占空间（每条 ≤ 1280 长边 JPEG） | 长期使用后占用增长 | `clear()` 已实现；后续可按"只看文字不留图"或超过 N 条自动清理最早的 |
| 14 | 只能用 jlatexmath 支持的 LaTeX 子集 | 极端公式渲染失败 | `errorHandler` 降级显示源码；`MathMarkdown.plain` 在列表里本来就用纯文本 |
| 11 | 首页还没展示「今日剩余 N 次」，用户要拍完才知道额度没了 | 接 `quota.json`，在首页拍照按钮下显示，`remaining=0` 时置灰 |
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
- `ModuleGraphTest`：`layeringRulesHold` / `reusableModulesDoNotImportBusinessCode` / `declaredProjectDependenciesAreUsed` 通过；
  `packagesMatchModules` **失败**，原因与本功能无关：`app/.../com/openapi/ks/myapp/agent/**`（15 个文件，来自 `weifu-refactor` 上的 agent 聊天）
  包名不在 `com.openapi.app` 下。
- 2026-11-12 本轮（四边拖动/LaTeX/思考过程/历史）：`:imaging` 32 个单测、`:feature-photosolve` 34 个、`:mediaview` 27 个全部通过；
  服务端 `PhotoSolveJsonRepairTest`(15) + `DoubaoPhotoSolveParseTest`(5) 通过；`:app:compileChatDebugKotlin` 退出码 0。
- 2026-11-12 额度改动：服务端 `mvn -o -pl vvv-link -am compile` 退出码 0；客户端 `:common` / `:feature-photosolve` / `:app:compileChatDebugKotlin` 退出码 0，`:mediaview` 单测通过。

**未验证**（需要真实环境）：
- 端到端调用豆包：本机 `secrets.properties` 没有填写 `photosolve.doubao.apiKey`，`health.json` 会返回 `configured:false`、`solve.json` 返回 50002。填好密钥后按 4.6 的 curl 验证。
- 真机上的 DocQuadNet 检测与透视矫正效果（需要 `assets/docquad/docquadnet256_trained_opset17.ort`，约 13.4MB，按既有约定不提交进仓库）。
