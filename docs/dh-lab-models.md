# DH Lab 模型与可选 SDK

Lab 引擎适配器通过反射探测 classpath。编入 native SDK：

```bash
./gradlew :app:assembleDebug -Pdh.lab.native=true
```

| 属性 / 默认路径 | 模块 |
|----------------|------|
| `DH_ANDROID_HOME` 或 `~/work/android` + `Duix-Mobile/.../duix-sdk` | `:dh-engine-duix` |
| 同上 + `ChatWaifu_Mobile/Live2D` | `:dh-engine-live2d` |

覆盖示例：

```
-Pdh.lab.native=true -Pdh.duix.root=/absolute/path/to/duix-sdk -Pdh.live2d.root=/path/to/Live2D
```

未加 `-Pdh.lab.native=true` 时 Switcher 对三家显示「未编入」（适配器已注册，classpath 无类）。

## Duix 模型目录

官方 SDK 从 `context.getExternalFilesDir("duix")` 读取：

```
…/files/duix/
  model/
    gj_dh_res/          # 基础配置（解压 gj_dh_res.zip）
    tmp/gj_dh_res/      # 完成标记目录（空目录即可）
    duix_lab/           # 角色模型（默认名，见 DuixModelPaths.DEFAULT_MODEL）
    tmp/duix_lab/
```

基础包 URL（SDK `Constant.BASE_DOWNLOAD_URL`）：
`https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip`

推到设备：

```bash
adb push gj_dh_res /sdcard/Android/data/<appId>/files/duix/model/gj_dh_res
adb shell mkdir -p /sdcard/Android/data/<appId>/files/duix/model/tmp/gj_dh_res
```

音频策略：适配器 `setVolume(0)`，只驱动口型；LiveKit 扬声器出声。

## Live2D

ChatWaifu `Live2D` 模块 assets 自带 `ATRI` 等模型。Lab 默认加载 `ATRI`。
不接 VITS / Sherpa。

## TaoAvatar

旗舰门闸：约 ≥8GB RAM + arm64。完整 A2BS/NNR 模块尚未精简进仓库；未编入时 Switcher 显示「未编入」。
