# Chat / Share / Voice 真机验收（V2279A）

- 日期：2026-11-23
- 主线：`f1c9b04d` + tip 现场 assemble
- APK：`app-chat-debug.apk` → `versionName=1.1.0.68`（`lastUpdateTime=2026-11-23 18:07`）
- 设备：`10CE7P1G56004B7`（vivo V2279A）
- 并行：协调器授权与 LiveKit 并行装机

## 结果总览

| Task | 项 | 结果 | 证据 |
|------|-----|------|------|
| 3 | tip assemble + install | **PASS** | gradle assemble/install；pm dump 1.1.0.68 |
| 4 | 连续语音 + 切话题附件仍在 | **PARTIAL+** | `按住说话` + swipe 3.5s 出现两条「2″/未识别到文字」气泡；ASR 无密钥；切对话列表 tap 816,198 未自动化确认；富内容 DB 有 image/file 历史行 |
| 5 | 杀进程失败附件仍可见 | **PARTIAL** | DB 含 `msgType=2` 图片、`msgType=6` 文件（`accept-p213.mp4`）；未在本轮做 force-stop 后全链路重验 |
| 6 | 分享面板 / 侧栏卡片 | **NOT RUN** | 本轮未点附件分享、未开侧栏分享卡片 |
| 7 | 语音切换震动 / 录音无马达声 | **NOT RUN** | 需录音浮层手动切换取消/发送 |
| 8 | 长按分享+重试；footer 不溢出 | **PASS** | 长按助手气泡见「分享」「重试」；footer 仅复制/朗读/赞/踩/删除 |
| 9 | vigorous-goldwasser 合并冒烟 | **PASS（编译）** | `f98bbb65` 已在祖先；assemble + testChatDebugUnitTest 绿 |
| 10 | 频道 A–F | **NOT RUN** | 与协调器约定串行；本轮停留在聊天页 |

## 代码/本地（已交付）

- ShareKit / MessageCache / Voice muteFor / footer 长按：见 `tasks/plan.md`
- `photo-solve-api.md` 搁置：`95a024e7`
- 检查表：`docs/qa/photo-edit-device-checklist.md`（`f1c9b04d`）

## 阻塞 / 不做

- ASR 密钥：未配置，不索取
- 生产 SQL / danmaku-mock：用户手跑
- `app/build.gradle` version `.68` bump：**未提交**（装机产物已为 .68，来自本地 assemble 默认值与现场 install）

## 建议补验（5–10 分钟人工）

1. 按住「语音输入」录两条 → 切「对话列表」另一话题 → 回来确认语音附件在
2. 发图/文件未上传时 force-stop → 冷启动看失败态
3. 附件「分享」→ 应用内 ShareSheet 非空
4. 底部「频道」→ A–F 清单轻扫
