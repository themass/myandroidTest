# Camera + Lottie 集成 Implementation Plan

> **For agentic workers:** Use executing-plans or native implementation per task.

**Goal:** 摄像头锚点驱动 Lottie 贴纸，先 lab 后通话。

**Architecture:** feature-camera-fx 保持隐私红线；Companion 舞台特效仍走 gesture 链。竖切一在 lab 叠 Lottie；竖切二在通话页 debug 门控下小窗 CameraX 或系统相机权限后叠 sticker（待做）。

**Tech Stack:** MediaPipe 0.10.35, CameraX, lottie-compose 6.7.1, FxAnchors.CanvasMap.

**Spec:** feature-camera-fx/README.md, architecture memory overlay+cmp.cue.

## Global Constraints

- 人脸数据不上传不落盘；debug 包 lab 入口。
- 特效层不接 pointerInput 仲裁；camera 不进 GL。

## Review Focus

- 无脸时贴纸消失；mirrorX 与 HUD 一致；多脸各一贴纸。

---

### Task 1: Lab 头顶 Lottie MVP — **DONE** (`CameraStickerPlacement`, CameraFxScreen overlay)

### Task 2: 真机 lab 验收（独占 adb）

### Task 3: 通话页 debug 小窗相机 → headTop 锚 Lottie（companion 依赖 camera-fx，DhLabGate）

### Task 4: Lottie C 收尾 + LiveKit（设备队列，并行会话）
