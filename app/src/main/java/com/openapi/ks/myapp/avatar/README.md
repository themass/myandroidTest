# avatar — 2D 形象层

上层只通过**指令**驱动形象；渲染技术可换，协议和状态机不动。设计见
`docs/superpowers/specs/2026-11-06-roleplay-design.md` 第 5 节。

## 指令契约

```kotlin
AvatarCommand.SetState(AvatarState.IDLE | LISTENING | THINKING | SPEAKING)
AvatarCommand.Emotion("joy")        // 名字由 AvatarSpec.expressions 解析
AvatarCommand.Mouth(openness)       // 0..1，来自播放音频的振幅
AvatarCommand.Gaze(x, y)            // v1 不实现
AvatarCommand.Gesture(id)           // v1 不实现
```

`AvatarEventMapper.map(type, payload)`（或 `mapJson(raw)`）把 agent 数据通道的
JSON 事件转成指令。只认 `state` 与 `emotion`；未知类型、未知取值一律 `null`，
不抛异常。协议只在这一个文件里出现。

## 能力协商

渲染器用 `capabilities` 声明支持哪些 `AvatarCapability`（`STATE`、`EMOTION`、
`MOUTH`、`GAZE`、`GESTURE`、`TOUCH_DRAG`）。不在集合里的指令**静默忽略**，
不记日志、不崩溃——新渲染器可以逐步补齐。

v1：`STATE`、`EMOTION`、`MOUTH`。`onTouch` 恒返回 `Ignored`，**没有拖动**。
拖动跟随、点击部位、视线、手势都在 P3b 由新渲染器实现，不改这里的接口。

## 加一个渲染器（Spine / Live2D / Rive）

1. 实现 `AvatarRenderer`：`bind(spec)` 建资源、`dispatch(command)` 更新状态、
   `release()` 停掉所有定时器和纹理。
2. 状态用 `StateFlow` 暴露，不要自己持有 Compose 动画时钟——插值留给 Composable。
3. `capabilities` 按实际能力填；上层据此决定发不发 `Gaze` / `Gesture`。
4. 写一个和 `SimpleAvatar` 同形的 `@Composable`，用 `by renderer.xxx.collectAsState()`
   读取状态。上层代码只替换这两处实例化。

`AvatarSpec` 只描述素材（立绘、情绪图、嘴型两帧、眼睛两帧），资源 id 可为
`null`：表情缺退到立绘，立绘缺退到占位块，眼睛/嘴缺就少那一层。

## v1 实现要点

- `SimpleAvatarRenderer`：纯状态，无第三方运行时。
- 呼吸：`RememberInfiniteTransition`，说话时 1800ms / 1.2%，其余 3200ms / 0.6%。
- 眨眼：3–6 秒随机间隔，闭眼约 120ms。
- 嘴型：振幅 ≥ 0.35 为张嘴；非有限值丢弃，其余 clamp 到 0..1；非 `SPEAKING`
  状态强制闭嘴。
