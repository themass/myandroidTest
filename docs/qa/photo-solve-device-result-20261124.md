# 拍照解题 PS1–PS5 · 真机验收结果（进行中）

| 字段 | 值 |
|------|-----|
| 设备 | vivo V2279A `10CE7P1G56004B7` |
| 装机 | `versionCode=1001000072`、`versionName=1.1.0.72`、`lastUpdateTime=2026-11-24 10:36:54`（协调会话装，公告 tip `5e17a2f5`） |
| 包内容核对 | `git merge-base --is-ancestor 8d38e82f 5e17a2f5` = **真**（PS 修在包里）；`ece403c2` 头像修同包 |
| 检查表 | `docs/qa/photo-solve-capture-flow-20261123.md` |
| 跑的人 | happy-fermi（我）；装机归协调会话单写者 |

## 结果（P/F/—）

| # | 场景 | 结果 | 备注 |
|---|------|------|------|
| PS1 | 选**屏幕里的题目**照片（`DCIM/Camera/ps_probe_1.jpg` = 用户 22:42 原失败实拍） | — | 期望：进四角微调页 + 黄色「没自动找到题目边框」说明，不卡取景页；logcat 应出现 `PhotoSolveQuad: 几何检测判负…` |
| PS2 | PS1 页面拖准四角 →「裁剪并识别」 | — | 期望：Recognizing → 结果；`files/photosolve_history/img/` 出现记录 |
| PS3 | 页内「自动识别」重跑 | — | 判负也不退出该页 |
| PS4 | 真实纸张题目 | — | 仍自动贴合、无黄色说明 |
| PS5 | 服务端 40002 | — | 回四角微调页（图还在） |
| AV | 对话页用户头像（顺带） | — | 用户行右侧 28dp 头像位 + 8dp 间距 |

## 已就绪的准备（不需要相机、不需要用户重拍）

- `ps_probe_1.jpg` / `ps_probe_2.jpg`：用户那四张失败实拍中的两张，已 push 到
  `DCIM/Camera/` 并扫库 + touch 成最新 → 相册选择器里排最前。
- 入口路径：对话页输入栏「拍照解题」（`ComposerAction("photo_solve")`）→
  PhotoSolveActivity 首页 →「从相册选择」→ 与快门同一条 `onPhotoCaptured` 路径。
- `am start` 起 PhotoSolveActivity 会被拒（not exported）——这是正确行为，别当缺陷修。

## 阻塞 / 事故记录

- **设备窗口没拿到独占**：公告开跑后前台先后出现放映场播放器（《怒鲨狂潮》，
  text-sound-screening 在用）和 vivo 应用商店（用户本人?）。
- **我的失误**：按旧坐标补发的一次 `tap(978,2263)` 落在了别人的播放页上（可能干扰那一轮）；
  之前还 force-stop 过一次 app。两条都已写进 `device-v2279a-window-20261123.md`。
- 规矩（本次学到）：**开跑前 dump 确认前台归属；不是我们的页面立即让窗**；
  坐标必须在同一轮 dump 后立刻用，不能复用旧坐标。
