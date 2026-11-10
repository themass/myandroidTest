# 拍照解题接口方案

状态：方案整理，未改代码。
范围：App 的拍照解题（`feature-photosolve`）如何获得真实解题结果。

## 1. 现状

| 项 | 情况 |
|---|---|
| 真实 OCR / 大模型 | 没有密钥，没有接入 |
| 服务端 | 没有部署。`myconf/python/ocr/app.py` 里的 `/photosolve/upload`、`/photosolve/ocr`、`/photosolve/solve` 只在本地桩环境跑过（commit `9c091b62`，分支 `testpython`，仅本地，未推送） |
| App 端 | `HttpPhotoSolveRepository` 已实现「压缩 → upload → solve」，地址来自 `-PphotosolveBaseUrl`，未配置时走 `MockPhotoSolveRepository` |
| 本地桩 | 5055 端口的 Python 桩已停止（本次操作）；adb reverse 规则已不存在 |

## 2. 已有的现成解题接口（aixue 项目）

路径：`/Users/gqli/work/ai-math/aixue`。这里已经有一条「拍讲」（拍照讲题，`photoExplanation`）的完整链路，正在线上使用，比我们自建的 `/photosolve/*` 更成熟。

| 步骤 | 接口 | 说明 |
|---|---|---|
| 1 预上传 | `POST aiclasstoc/file/preUpload` | body `bussinessId = "ax_pe"`；返回 `uploadUrl`（对象存储预签名地址）、`dirId`、`fileName` |
| 2 上传原图 | `PUT {uploadUrl}`，`Content-Type: image/jpeg` | 直接传到对象存储（TOS），不经过业务服务 |
| 3 提交解题 | `POST aiclasstoc/photoExplanation/mutiImagesApply` | body：`cameraTraceId`（每次拍照一个 UUID）、`bussinessId`、`images[]`（每项 `originalImage` + `croppedImage`，各为 `dirId`/`fileName`），可选 `answerSheetImages`、`externalServiceContext` |
| 3 返回 | `PhotoExplanationApplyData` | `status`、`mainTitle`、`subTitle`、`photoExplanationId`、`solvingTaskId` |
| 4 轮询结果 | `GET aiclasstoc/photoExplanation/generation/query` | 参数 `cameraTraceId`、`photoExplanationId`；返回 `finished`、`stages`、`result`、`selectionQuestions` 等 |
| 可选 | `selectionQuestion/confirm`、`selectionQuestion/cancel`、`history/unStudied/count` | 选择题确认、取消、未学历史计数 |

客户端参考实现：`core/data/.../repository/photo/PhotoExplanationRepository.kt`（`preUploadAndPutJpeg` → `applyPhotoExplanation` → `queryGeneration`）；`feature/cropper/.../CropperViewModel.kt` 为每次会话生成 `cameraTraceId`。

### 与我们当前方案的差异

| 对比 | aixue「拍讲」 | 我们的 `/photosolve/*`（未部署） |
|---|---|---|
| 上传 | 预签名直传对象存储 | 经业务服务 multipart 上传 |
| 解题 | 异步：提交后轮询 | 同步：solve 一次返回 |
| 返回 | 结构化：标题、阶段、选择题 | 文本：最后一行为答案，其余为步骤 |
| 鉴权 | 复用 aixue 的登录与网络层（`NetworkModule`） | 无 |
| 域名 | 测试 `aienglish-live.test.aitutor100.com`，正式 `aienglish-live.aitutor100.com` | 配置里的 `photosolveBaseUrl`（未确定） |

结论：应复用 aixue 的拍讲接口，而不是继续自建 `/photosolve/*`。

## 3. 待确认

1. 解题服务的环境与鉴权：本 App（`com.openapi.ks.chat`）的网关和 aixue 的 `aienglish-live.*` 不是同一个域名，需要确认能否调用、用哪套 token/登录态。
2. 业务编号：aixue 使用 `bussinessId = "ax_pe"`，本 App 是否复用，还是另申请。
3. 是否保留 `myconf` 的 `/photosolve/*`：建议不再推进，`9c091b62` 留在本地分支不推送。

## 4. 错误码处理（沿用既定设计，待映射到拍讲接口）

| 码 | 含义 | 客户端处理 |
|---|---|---|
| 40001 | 图片不清晰 | 提示重拍；允许「仍然提交」 |
| 40002 | 不是题目 | 弹框，引导回到裁剪页框选 |
| 40003 | 图片问题（过大、格式等） | 自动压缩后重试一次 |
| 42901 | 额度用完 | 显示升级入口 |
| 50001 | 服务失败 / 网络异常 | 保留图片，提供重试 |

aixue 拍讲的错误码与上表不同，接入时需要以 aixue 的错误码为准重新映射。

## 5. 临时方案（已确认）

拍照解题暂时当作「图片上传」调用，走普通的图片消息通道（和聊天里发图片一样：压缩、上传、发送），不单独走解题接口。解题的专门接口和界面以后再改。

这意味着：
- 在接入真实解题接口之前，`HttpPhotoSolveRepository` 和 `/photosolve/*` 不会被使用。
- 蓝色主题、错误分支界面，放到解题专题一起处理。

## 6. 下一步（需要确认后再做）

1. 确认第 3 节的三个问题。
2. 确认后，把 `HttpPhotoSolveRepository` 替换为调用拍讲接口的实现（预上传 → PUT → mutiImagesApply → 轮询），并把错误码映射到 aixue 的码。
3. 删除或保留 `ImageUploadEncoder`（当前无生产调用方，属于清理项）。
