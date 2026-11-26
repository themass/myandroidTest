# Spine skin-swap spike (2026-11-22/23)

零元技术 spike：验证 spine-ts 官方 runtime + 官方免费示例骨架的 skin 换装管线。
结论与报告见共享目录 `spine-spike.md`；证据在 `evidence/`（不入库）。

## 复现
```bash
python3 -m http.server 8731 --bind 127.0.0.1
open "http://127.0.0.1:8731/index.html?asset=mix-and-match&anim=idle&poseT=0.45"
```
浏览器里直接调 `__spike.combo("B")`、`__spike.fullSkin("full-skins/girl")`、
`__spike.mouth("mouth-smile")`、`__spike.pose(1.2,"dance")`。

## 目录
- `index.html` — spike 页（官方 SpineCanvas 引导 + 驱动 API `window.__spike`）
- `lib/spine-webgl.js` — @esotericsoftware/spine-webgl@4.2.120 iife bundle（自带 spine-core，单文件即可运行）
- `assets/` — 官方示例骨架 mix-and-match / goblins（license.txt 同目录，spike 专用，图片禁商用）
- `capture.js` / `capture2.js` / `mouth-proof.js` — Playwright 证据采集（需本机 playwright-core + Chromium）

未进主线：本目录只在 worktree 分支 `claude/hopeful-pare-a449b1`，不 merge、不 push。
