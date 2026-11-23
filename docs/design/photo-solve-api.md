# 拍照解题接口方案（历史草案 · 已搁置）

状态：**搁置**（2026-11-23 用户裁定）。
范围：仅作历史记录。正式端到端设计见 [`photo-solve-design.md`](photo-solve-design.md)。

## 0. 当前结论（以这个为准）

1. **不是接 aixue**。本文第 2–3、6 节里「复用 aixue 拍讲」的推荐作废，不要按它推进。
2. **正式后端搁置**，域名/鉴权/服务部署以后一起做。
3. **临时方案仍然有效**：拍照解题暂时当普通图片消息走（压缩 → 上传 → 聊天发图），不单独打解题接口。
4. 后续真实解题以 `photo-solve-design.md` + testserverV `/api/photosolve/v1/*` 为准；旧的 `HttpPhotoSolveRepository` / myconf `/photosolve/*` 已删除或不再推进。

## 1. 现状（整理当时）

| 项 | 情况 |
|---|---|
| 真实 OCR / 大模型 | 当时没有密钥，没有接入 |
| 服务端 | 当时没有部署。`myconf/python/ocr/app.py` 里的 `/photosolve/upload`、`/photosolve/ocr`、`/photosolve/solve` 只在本地桩环境跑过（commit `9c091b62`，分支 `testpython`，仅本地） |
| App 端 | 旧 `HttpPhotoSolveRepository` 已删除；现走 `NetworkPhotoSolveRepository` / Mock，见 `photo-solve-design.md` |
| 本地桩 | 5055 端口 Python 桩已停止；adb reverse 规则已不存在 |

## 2. 曾调研的现成解题接口（aixue「拍讲」）——仅存档

路径：`/Users/gqli/work/ai-math/aixue`。线上有「拍讲」链路（`photoExplanation`）。**本 App 明确不接这条链路。**

| 步骤 | 接口 | 说明 |
|---|---|---|
| 1 预上传 | `POST aiclasstoc/file/preUpload` | body `bussinessId = "ax_pe"`；返回 `uploadUrl`、`dirId`、`fileName` |
| 2 上传原图 | `PUT {uploadUrl}`，`Content-Type: image/jpeg` | 直传对象存储（TOS） |
| 3 提交解题 | `POST aiclasstoc/photoExplanation/mutiImagesApply` | `cameraTraceId` + 原图/裁剪图对 |
| 4 轮询结果 | `GET aiclasstoc/photoExplanation/generation/query` | `finished`、`stages`、`result` 等 |

客户端参考（aixue，不是本仓库）：`PhotoExplanationRepository.kt`、`CropperViewModel.kt`。

### 与自建 `/photosolve/*` 的差异（历史对比）

| 对比 | aixue「拍讲」 | 当时的 `/photosolve/*`（未部署） |
|---|---|---|
| 上传 | 预签名直传对象存储 | 经业务服务 multipart 上传 |
| 解题 | 异步：提交后轮询 | 同步：solve 一次返回 |
| 返回 | 结构化：标题、阶段、选择题 | 文本：最后一行为答案，其余为步骤 |
| 鉴权 | 复用 aixue 登录与网络层 | 无 |
| 域名 | `aienglish-live.*` | 未确定的 `photosolveBaseUrl` |

~~结论：应复用 aixue 的拍讲接口。~~ → **已否决。**

## 3. 待确认（搁置前留下的问题，暂不推进）

1. ~~解题服务环境与鉴权是否能走 aixue 域名~~ → 不走 aixue。
2. ~~业务编号是否复用 `ax_pe`~~ → 不适用。
3. 是否保留 myconf `/photosolve/*`：不再推进；正式方案见 `photo-solve-design.md` / testserverV。

## 4. 错误码处理（设计意图，正式映射以 photo-solve-design 为准）

| 码 | 含义 | 客户端处理 |
|---|---|---|
| 40001 | 图片不清晰 | 提示重拍；允许「仍然提交」 |
| 40002 | 不是题目 | 弹框，引导回到裁剪页框选 |
| 40003 | 图片问题（过大、格式等） | 自动压缩后重试一次 |
| 42901 | 额度用完 | 显示升级入口 |
| 50001 | 服务失败 / 网络异常 | 保留图片，提供重试 |

## 5. 临时方案（仍有效）

拍照解题暂时当作「图片上传」调用，走普通的图片消息通道（和聊天里发图片一样：压缩、上传、发送），不单独走解题接口。解题的专门接口和界面以后再改。

这意味着：
- 在正式解题接口联调之前，聊天入口的拍照解题可按图片消息处理。
- 蓝色主题、错误分支界面，放到解题专题一起处理。

## 6. 下一步（搁置）

~~1. 确认第 3 节。~~
~~2. 把仓库改成调用拍讲接口。~~
~~3. 删除 ImageUploadEncoder。~~（清理项已做过）

恢复工作时：打开 `photo-solve-design.md`，按 testserverV `/api/photosolve/v1/solve.json` 联调，不要回到 aixue。
