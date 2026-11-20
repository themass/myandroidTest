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

### 开发机代下载（推荐，绕开设备网络）

设备到 GitHub 的下载可能很慢或超时。用脚本在开发机下载（缓存在
`.superpowers/dh-packs/`，不进 git）再 adb 推到手机，布局和 marker 与
AssetStore 下载路径完全一致：

```bash
scripts/dh-lab/seed-assets.sh            # 三家全部
scripts/dh-lab/seed-assets.sh duix tao   # 只推指定引擎
```

要求 debuggable 包（经 `run-as` 写入应用私有目录）。推完冷启动 App 即认。

## Duix

清单（自动下载）：

| 包 | URL | 解压到 |
|----|-----|--------|
| `gj_dh_res` 基础配置 | `https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip` | `…/dh-engines/duix/model/gj_dh_res/` |
| `Oliver` 角色模型 | `https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip` | `…/dh-engines/duix/model/Oliver/` |

运行时读取路径即上表（`DuixRuntime` 不再读上游
`getExternalFilesDir("duix")`）。调试期用 `scripts/dh-lab/seed-assets.sh duix`
代替设备端下载（见上）。

native 绑定：vendored Java 保持我们自己的包名，而官方 `libgjduix.so` 只导出
`Java_ai_guiji_duix_DuixNcnn_*` 符号，所以走窄桥接类 `ai.guiji.duix.DuixNcnn`
（上游 ABI 逐字镜像，`DuixNativeBridgeTest` 钉住 18 个签名）。三个 `.so`
（libncnn / libonnxruntime / libgjduix）由 `DuixNcnnLoader` 从
`…/dh-engines/duix/native-libs/arm64-v8a/` 按绝对路径加载（依赖顺序），缺失时
显示「native 库缺失」。`.so` 来自本机 Duix-Mobile 源码构建产物：

```
~/work/android/Duix-Mobile/duix-android/dh_aigc_android/duix-sdk/build/intermediates/library_and_local_jars_jni/debug/copyDebugJniLibsProjectAndLocalJars/jni/arm64-v8a/
```

用 adb 推到上面的 native-libs 目录即可（已真机验证：选 Duix 后 Oliver 实时渲染出画面）。

音频策略：适配器 `setVolume(0)`，只驱动口型；LiveKit 扬声器出声。

## Live2D

Cubism Core 是**唯一**随仓库提交的 `.so` 例外：
`dh-engines/src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so`（上游字节一致）。

ATRI 模型无公开下载 URL（清单故意留空，`ensure()` 报明确原因）。开发路径：把
模型目录放到 `.superpowers/dh-packs/ATRI/` 后运行 `scripts/dh-lab/seed-assets.sh live2d`
（推到 `cacheRoot(LIVE2D)/ATRI/`）。

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
开发路径：把两个模型目录放到 `.superpowers/dh-packs/` 下后运行
`scripts/dh-lab/seed-assets.sh tao`，推到 `cacheRoot(TAO)/<模型名>/`，
`TaoRuntime.modelsReady()` 直接检查这两个目录存在。

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
