# Implementation Plan: Chat/Share/Voice 收尾与真机复验

## Overview
本会话已落地的改动（分享面板、FileProvider、语音震动静音窗、附件缓存保留、AI footer 长按菜单、架构合并）都已进 `weifu-refactor` 且在 `origin` 上。尚未完成的是真机复验，以及把过时的拍照解题草案改成「搁置」状态，避免后续按错误结论推进。

## Architecture Decisions
- 真机复验只装 `weifu-refactor` tip 现场 assemble 的 APK（见 install-test-from-mainline-tip）。
- 不碰工作区里未提交的 `app/build.gradle` versionCode/versionName 改动。
- 拍照解题：用户裁定「不是接 aixue，搁置」；临时方案仍是普通图片消息。`photo-solve-design.md` 已是后续主文档，`photo-solve-api.md` 只保留历史 + 搁置说明。
- 手机冒烟在 V2279A 被其他会话 CLAIM 时不抢设备。

## Task List

### Phase 1: 文档与状态对齐（无需设备）
- [ ] Task 1: 更新 `docs/design/photo-solve-api.md` 为搁置/被取代
- [ ] Task 2: 把本计划与 todo 落盘到 `tasks/`

### Checkpoint: Docs
- [ ] 文档不再推荐接 aixue；临时图片消息路径写清楚

### Phase 2: 真机复验（需要 V2279A RELEASED）
- [ ] Task 3: tip 现场 assemble + installChatDebug
- [ ] Task 4: 连续两条语音 + 切话题回来，附件仍在
- [ ] Task 5: 杀进程重启，失败态图片/文件消息仍可见
- [ ] Task 6: 图片/文件分享面板 + 侧边栏分享卡片（含二维码）
- [ ] Task 7: 语音取消/发送切换震动，录音无马达噪音
- [ ] Task 8: AI 长按菜单有「分享」「重试」，footer 不溢出

### Checkpoint: Device
- [ ] 上述 4–8 项全部有 logcat/截图或明确失败复现

### Phase 3: 合并冒烟（等架构会话确认）
- [ ] Task 9: 按 [架构重构](#local_0c440dde-5831-45ce-9a97-c5d3f275b91b) 确认后再做 vigorous-goldwasser 合并冒烟

## Risks and Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| 设备被 lottie-fx CLAIM | High | 先做文档；CLAIM RELEASED 后再装机 |
| 重构后路径挪动导致假通过 | Med | 复验前先核对 ShareKit/VoiceRecorder/MessageCache 仍在 |
| 未提交 version bump 被误提交 | Low | 装机前不 `git add app/build.gradle` |

## Open Questions
- 架构会话是否现在要求合并冒烟，还是先做 chat/分享/语音复验？
- 拍照解题正式后端（非 aixue）何时一起做？
