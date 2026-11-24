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

- debug：`app-chat-debug.apk` = 351,287,986 B（≈335 MB）
- release：签名 keystore（`/Users/liguoqing/soft/myapp/key`）在本开发机不存在，
  `:app:assembleChatRelease` 在 `validateSigningChatRelease` 失败，未能产出签名 release APK；
  未签名 release 包审计见下节。

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

**未做。** 四场景 × 五引擎没有在 V2279A 上看过；按装机纪律，装机与真机验收由协调会话安排
（本任务只 build、不 adb install、不 bump versionCode）。本文写的是「编译与单测已过、真机未验」。
