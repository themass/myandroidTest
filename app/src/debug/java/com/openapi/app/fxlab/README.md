# Effects Lab（特效引擎对比，仅 debug）

同一套 cue/锚点/触发路径下，五种渲染引擎在四种场景里的并排对比。全部代码、资产、依赖都在
`app/src/debug` 与 `debugImplementation` 里：release 构建里物理上不存在这些页面与资产。

## 怎么打开

抽屉（左上角）→「实验室」分组 →「特效引擎对比」。入口只在 `CameraFxLabGate.isEnabled(context)`
且应用 debuggable 时出现；页面 Activity 只注册在 debug manifest。

## 四个场景 × 五个引擎

场景：数字人互动 / 数字人教学（内置分层立绘 + 触发按钮）；摄像头全屏 / 摄像头小窗
（真实相机权限 + 隐私确认文案，措辞与生产一致）。

| 引擎 | 应该看到什么 | 已知边界 |
|---|---|---|
| Lottie（基线） | 生产同款贴纸/爱心弧 | 就是现状，作对照 |
| Rive | 官方样例 `loopy`/`circle_move` 循环播放 | 样例是 211/269 字节的线性时间轴（无状态机）；**素材授权未确认**，见 `app/src/debug/raw-notes/README-fxlab-rive.md`；换素材 = 自己从 Rive 编辑器导出 `.riv` 覆盖 `app/src/debug/res/raw/fxlab_*.riv` |
| GLES3 粒子 | 摸头=头顶爆发、挥手=弧带、待机=漂浮点 | 透明 TextureView + EGL，纯自研小渲染器，count ≤ 512 |
| AGSL 光环 | 青色呼吸光环叠加 | 需要 API 33+；低版本显示原因不显示画面 |
| Three.js | 透明 WebView 里的发光圆环 + 光扫 | 本机预览叠加，**不合成进相机输出/LiveKit**；WebView 只放行 `three.html`/`three.bundle.js`，其余请求一律阻断 |

触发只有一条路径：页面按钮（或教学「下一步」）写同一个 cue 槽位；「停」会让下一次
同一 cue 也从零重播（replayTick）。渲染层不接收手势（无 `pointerInput`），不采样相机像素、
不落盘、不上传任何帧。

## 重建 Three.js bundle

```bash
cd scripts/fx-lab/three-scene && npm install --no-audit --no-fund && node build.mjs
```

产物 `app/src/debug/assets/fx-lab/three.bundle.js` 随仓库提交（与 dh-lab/vrm-scene 同一约定）。

## 实测 APK 体积（本分支）

- debug：`app-chat-debug.apk` = 351,288,741 B（≈335 MB）
- release：签名 keystore（`/Users/liguoqing/soft/myapp/key`）在本开发机不存在，
  `:app:assembleChatRelease` 在 `validateSigningChatRelease` 失败，未能产出签名 release APK；
  未签名 release 包审计见下节。debug 装机/真机验收不需要 keystore（debug 签名自动）。

## release 隔离证据（命令，不是口头）

- `rg 'rive' :app chatReleaseRuntimeClasspath`（Gradle 依赖树）→ 无 Rive：`debugImplementation` 生效。
- `rg -l 'fxlab' feature-companion/src/main feature-camera-fx/src/main` → 无命中（main 源集只有
  `EffectsLabLaunch` 的 2 个常量 + 被 debuggable 闸住的抽屉项）。
- 签名 release APK 内容 grep：**未完成**——keystore 不在本机（`validateSigningChatRelease`：
  `Keystore file '/Users/liguoqing/soft/myapp/key' not found for signing config 'config'`）。
  曾尝试 `-x validateSigningChatRelease -x signChatRelease` 的未签名打包做替代审计，
  跑了 25 分钟后 BUILD FAILED（未产出 APK），不再重试、不记部分结果。最小后续条件：在有
  `/Users/liguoqing/soft/myapp/key` 的机器上跑 `./gradlew :app:assembleChatRelease` 后
  `unzip -l app/build/outputs/apk/chat/release/*.apk | rg -i 'rive|fxlab|three|agsl'`，预期无命中。

## 真机验收状态

2026-11-24 在华为 ADY_AL00（SDK 31，arm64-v8a，WebView 内核 hwschromium）完成一轮
（用户当面授权本设备直接装机，属装机单写者纪律的一次性豁免）。build 来自本分支 tip 现场 assemble。

| 引擎 | 结果 |
|---|---|
| Lottie | 通过（基线贴纸/爱心弧正常） |
| Rive | 首验失败后修复通过：app 里 Rive 的 androidx.startup 自动初始化被 `tools:node="remove"` 摘掉，JNI 报 `No implementation found for FileAssetLoader.constructor()`；`RiveRuntimeGate.ensure` 手动一次性 `Rive.init(context)` 后 loopy 正常渲染，「停」清除画面 |
| GLES3 粒子 | 通过（互动/教学场景漂浮点与爆发正常） |
| AGSL | 按设计诚实不可用：状态行显示「需要 API 33+，本机 API 31」，不出画面（互动+摄像头两场景都确认） |
| Three.js | 首验失败后修复通过，两处：① bundle 必须在 `<body>` 内加载（否则 `document.body` 为 null，页内脚本崩）；② hwschromium 里整层 canvas/普通流全尺寸 img 不参与合成——合成路径改为 WebGL 离屏 → 2D canvas `drawImage` → `toDataURL` → `position:fixed` 的 `<img>`，圆环+光扫正常上屏 |

未验的一格：摄像头场景的贴纸落脸（设备镜头前无人，`脸 0`，贴纸按设计需脸>0；adb 无法摆出真人脸，
需人工正对镜头复验）。其余四场景 × 五引擎的停/复播、教学翻页、PIP 布局均通过。
