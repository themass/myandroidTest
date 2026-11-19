# DH Lab 模型与素材下载

三家数字人引擎（Duix / Live2D / TaoAvatar）已全部 vendoring 进仓库内模块
`:dh-engines`（包名 `com.openapi.ks.dh.*`），随主工程常编，**不再**有
`-Pdh.lab.native` 外部 include，也没有反射探测 classpath 的 `*SdkReflect`。
大模型与大部分 native 库不打进 APK，由 `AssetStore` 在进通话页时按需下载。

## 素材缓存路径（AssetStore）

每台引擎的素材根目录是应用私有目录：

```
<filesDir>/dh-engines/<engine>/        # engine = duix | live2d | tao
```

（`Context.getFilesDir()/dh-engines/...`，卸载即清；不是 externalFilesDir。）

下载清单在 `dh-engines/src/main/java/com/openapi/ks/dh/AssetManifest.kt`，
每个包解压完后写 marker 文件才算就绪（原子安装）。

## Duix

清单（自动下载）：

| 包 | URL | 解压到 |
|----|-----|--------|
| `gj_dh_res` 基础配置 | `https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip` | `…/dh-engines/duix/model/gj_dh_res/` |
| `Oliver` 角色模型 | `https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip` | `…/dh-engines/duix/model/Oliver/` |

运行时读取路径即上表（`DuixRuntime` 不再读上游
`getExternalFilesDir("duix")`）。adb 推送代替下载：

```bash
adb push gj_dh_res /sdcard/Android/data/<appId>/files/dh-engines/duix/model/gj_dh_res
```

注意：直接 adb 推送的目录没有 marker，`AssetStore.isReady` 会判未就绪——
调试期以菜单内「立即下载」走下载链路为准；native 库（`libgjduix.so`，需按
`Java_com_openapi_ks_dh_duix_*` 符号重编，见 `THIRD_PARTY.md`）当前未打包，
缺它时引擎显示「native 库缺失」。

音频策略：适配器 `setVolume(0)`，只驱动口型；LiveKit 扬声器出声。

## Live2D

Cubism Core 是**唯一**随仓库提交的 `.so` 例外：
`dh-engines/src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so`（上游字节一致）。

ATRI 模型无公开下载 URL（清单故意留空，`ensure()` 报明确原因）。开发路径为
adb 推送：

```bash
adb push ATRI /sdcard/Android/data/<appId>/files/dh-engines/live2d/ATRI
```

目录内需有 `ATRI.model3.json`（`Live2dRuntime.DEFAULT_MODEL_DIR = "ATRI"`）。
不接 VITS / Sherpa。

## TaoAvatar（MNN）

旗舰门闸先判（约 ≥8GB RAM + arm64，`TaoDeviceGate`），机型不足下完素材也不可用。

清单（自动下载）只钉 native 依赖包：

| 包 | URL | 解压到 |
|----|-----|--------|
| `tao-native-libs-arm64-v8a` | `https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip` | `…/dh-engines/tao/native-libs/`（含 libMNN.so / libnnrruntime.so / libsherpa-mnn-jni.so） |

NNR 网格与 UniTalker A2BS 模型上游只提供 ModelScope downloader SDK（repo
`MNN/TaoAvatar-NNR-MNN`、`MNN/UniTalker-MNN`），无稳定单包 URL，因此清单不钉；
开发路径 adb 推送到模型目录，`TaoRuntime.modelsReady()` 直接检查这两个目录：

```bash
adb push TaoAvatar-NNR-MNN /sdcard/Android/data/<appId>/files/dh-engines/tao/TaoAvatar-NNR-MNN
adb push UniTalker-MNN /sdcard/Android/data/<appId>/files/dh-engines/tao/UniTalker-MNN
```

JNI owner `libtaoavatar.so` 同样需按 `Java_com_openapi_ks_dh_tao_nnr_*` 符号
重编（见 `THIRD_PARTY.md`），当前未打包：缺它时显示「native 库缺失」。

## 编 APK 与装真机

```bash
./gradlew :app:assembleChatDebug --offline
adb push app/build/outputs/apk/chat/debug/app-chat-debug.apk /data/local/tmp/app-chat-debug.apk
adb shell pm install -r /data/local/tmp/app-chat-debug.apk
```

通话页右上角下拉菜单可切换三家引擎；素材未下载的行可点「立即下载」（状态
「未下载」），native 缺失显示「native 库缺失」，机型不足显示「机型不足」。
