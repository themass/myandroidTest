# 拍照 / 截图识别迁移到 agent · 2026-11-24

本次用户裁定：图片直接调用 agent 上的大模型识别，独立接口无需先创建聊天会话。本文取代 `photo-solve-design.md` 中 testserverV 识别调用层；端侧取图、四角检测、裁剪和结果界面继续复用。

## 调用与数据

`拍照 / 系统扫描 / 相册（含截图） → PhotoSolveViewModel → PhotoSolveRepository → NetworkPhotoSolveRepository → AgentPhotoSolveRemote → POST /v1/photosolve/solve`。

- 域名取自应用注入的 `CompanionConfig.baseUrl`，与现有 agent 共用；debug 地址覆盖同样生效。
- 鉴权：`Authorization: Bearer <现有登录 token>`，未登录不发网络请求。
- 请求：原始 `image/jpeg` 字节，使用现有 ImageCompressor；query `client_request_id`、`subject=math`，通过 HttpUrl 编码。
- 返回：普通 JSON，兼容 `PhotoSolveApiResponse` 的 camelCase 字段；无 Java errno/AES 外壳。
- 映射统一走 `SolveResultMapper`，保留题干、公式、步骤、答案、科目及题目数。可选 thinking 仅承载简短解题思路，不要求模型输出私有推理。
- HTTP 401/403 提示登录；413/415/422 图片问题；429 配额；其余服务错误显示可重试提示。已知 numeric `detail.code` 保留；服务端自由文本不直接展示。
- 取消协程会取消 HTTP 请求。连接 15s、上传 60s、读取 180s、总调用 210s；退出页面后不等待失去意义的推理响应。

该接口直接处理裁剪图片，不先经过聊天上传或聊天会话，不向历史消息写入题目。旧 myconf Flask 的 OCR→文本解题链路不是迁移目标。旧 testserverV 的鉴权修复也不构成本次 agent 接口的部署证明。

## 验证范围

Android 新增 HTTP 合约测试：真实本地 HTTP 服务器核对路径、query 编码、Bearer、原始图片体、普通 JSON 结果映射；未登录零请求；HTTP/业务码；非 JSON 与异常成功体；非题目返回；协程取消。

Android 已通过 `./gradlew --offline :feature-photosolve:testDebugUnitTest :app:compileChatDebugJavaWithJavac`：52 个 photosolve 测试、0 失败；合入当时最新 `origin/weifu-refactor` 后复跑通过。

服务端必须独立验证鉴权、图片验证与大小上限、视觉多模态请求、模型结构输出校验与错误映射。模型配置与生产部署、真机真实识别属于独立验证层：源码构建/假模型测试不能证明线上模型可看图或线上接口已存在。上线顺序应先部署 agent 接口和视觉模型配置，再由协调会话从最新主线构建并装机验证；worker 不执行 adb install，不 bump versionCode。

旧 testserverV 本地日额度、全站预算与幂等去重不会因为请求迁移而自动继承。`client_request_id` 用于关联结果，不代表 exactly-once 推理；端上不做自动重试，避免在响应丢失时悄悄重复计费。部署前应确认 agent 的对应配额策略。
