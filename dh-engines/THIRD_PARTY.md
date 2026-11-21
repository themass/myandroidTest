# Third-party sources for `:dh-engines`

Upstream roots and download URLs used by this module. Versions/commits are filled in as code is vendored.

## Upstream roots

| Engine | Upstream | Notes |
|--------|----------|-------|
| Duix | Duix-Mobile `duix-sdk` (`ai.guiji.duix`) | JNI/GL runtime vendored into this module |
| Live2D | ChatWaifu_Mobile Live2D | JNI/GL + Framework; Cubism Core arm64 `.so` sole-in-module exception |
| Tao | MnnTaoAvatar | NNR / `AvatarTextureView` runtime + CMake |

## Asset download URLs

| Asset | URL | Engine |
|-------|-----|--------|
| Duix base pack (`gj_dh_res`) | `https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip` | Duix |
| Oliver role pack | `https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip` | Duix |
| Tao native libs (arm64-v8a) | `https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip` | Tao |

## Exceptions

- **Cubism Core arm64 `libLive2DCubismCore.so`**: the sole third-party `.so` allowed into this module's `jniLibs` (approved exception; ~385KB from ChatWaifu `SDKRoot/Core/dll/android/arm64-v8a/`). Models (e.g. ATRI) are **not** committed — final download URL slot TBD; lab/dev path is adb-push into `AssetStore.cacheRoot(LIVE2D)`.
- Other engines' large prebuilt `.so` packs stay on the download / local `build/` cache path — do not expand the in-git `.so` exception. In particular `libchatwaifu-live2d.so` (ChatWaifu's own JNI wrapper) is **not** packaged here.

## Vendored sources

### Live2D runtime (`com.openapi.ks.dh.live2d`)

- **Origin:** ChatWaifu_Mobile `Live2D/src/main/java/com/chatwaifu/live2d` (2 `.java` files: `JniBridgeJava`, `GLRenderer`). Framework/C++ lives under `src/SDKRoot` + `src/main/cpp` and is **not** vendored (no NDK/CMake in this task, same ruling as Duix).
- **Change:** package rename `com.chatwaifu.live2d` → `com.openapi.ks.dh.live2d` only.
- **jniLibs:** `src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so` only (see Exceptions). No Windows/iOS/rpi/x86 Cubism binaries, no model packs.
- **Not vendored:** `src/main/cpp` (builds `libchatwaifu-live2d.so`), Cubism Framework sources, ATRI/Amadeus/Yuuka model assets.
- **JNI note:** upstream builds export `Java_com_chatwaifu_live2d_JniBridgeJava_*` and `FindClass("com/chatwaifu/live2d/JniBridgeJava")`. The vendored class is `com.openapi.ks.dh.live2d.JniBridgeJava`, so a native rebuild for this module must export `Java_com_openapi_ks_dh_live2d_JniBridgeJava_*` and update the FindClass path. Until that `.so` exists, `Live2dRuntime.availability()` reports `native 库缺失` after models are present.
- **LIVE2D AssetManifest:** empty list; ATRI public URL TBD. `ensure(LIVE2D)` fails cleanly; adb-push into the cache root is the current lab path.

### Duix runtime (`com.openapi.ks.dh.duix`)

- **Origin:** Duix-Mobile `duix-android/dh_aigc_android/duix-sdk`, `src/main/java/ai/guiji/duix` (23 upstream `.java` files + the 2 local files listed below).
- **Change:** package/import rename `ai.guiji.duix` → `com.openapi.ks.dh.duix`, nothing else, except:
  - `sdk/client/BuildConfig.java`: hand-written shim replacing the AGP-generated class (`VERSION_CODE=1`, `VERSION_NAME="2.0.1-lab"`, `DEBUG=false`).
  - `sdk/client/DuixHome.java`: local addition. Upstream reads models from `getExternalFilesDir("duix")`; `DUIX`, `RenderThread` and `VirtualModelUtil` now ask `DuixHome.dir(context)`, which `DuixRuntime` points at the `AssetStore` cache.
- **Not vendored:** `src/main/cpp` (JNI + prebuilt trees, ~278MB). `libgjduix.so` is not packaged by this module; it is loaded lazily, and `DuixRuntime.availability()` reports `native 库缺失` while it is absent.
- **JNI note:** upstream builds export `Java_ai_guiji_duix_DuixNcnn_*`. The vendored class is `com.openapi.ks.dh.duix.DuixNcnn`, so a native build for this module must export `Java_com_openapi_ks_dh_duix_DuixNcnn_*`.

### `libs/resource_loader.jar`

- **Size / origin:** 13,872 bytes, copied unmodified from the SDK's `libs/resource_loader.jar` (Duix-owned, obfuscated; 8 classes in `ai.guiji.duix.sdk.client.loader.*`: `ModelInfo`, `ModelInfoLoader`, ...).
- **Why in git:** `DUIX` and `RenderThread` import `ModelInfo` / `ModelInfoLoader` from it and no source form is published. It is small enough to be an accepted exception (the ban is on large prebuilt trees and models).
- **Caveats:** its classes keep the upstream `ai.guiji` package (not renamed). `InfoRequest` reflectively reads `ai.guiji.duix.sdk.client.Constant.VERSION_NAME` (absent here, so that call fails inside its own try/catch) and POSTs device properties to `vshow.guiji.ai`; revisit before shipping outside the lab.

### Tao NNR runtime (`com.openapi.ks.dh.tao`)

- **Origin:** MnnTaoAvatar `app/src/main/java/com/taobao/meta/avatar` — vendored subset: `nnr/AvatarTextureView.kt`, `nnr/NnrAvatarRender.kt`, `camera/Camera.kt` + `camera/CameraControlData.kt`, plus a minimal `MHConfig.kt` (avatar dirs only).
- **Changes:** package rename `com.taobao.meta.avatar.*` → `com.openapi.ks.dh.tao.*`. Task 6 strips: the `MainActivity` cast/`activityRef` and the debug PixelCopy blend-shape frame-dump (`FileUtils.saveBitmapToFile`, copy HandlerThread, `doFrameDebug`, `audioBsToSave`) — the dump was gated by `MHConfig.DebugConfig.DebugWriteBlendShape`, which is removed here. `AudioBlendShapePlayer` is a deliberate idle-safe no-op stub (v1 = mesh on screen, mouth/A2BS not required).
- **Not vendored:** `app/src/main/cpp` (LLM/ASR/TTS/A2BS JNI trees), `download/` (ModelScope SDK downloader), `settings/`, sherpa-mnn Kotlin bindings, the upstream Gradle `downloadAndUnzipNativeLibs` preBuild task (runtime download via `AssetStore` instead, per spec).
- **Native (`libtaoavatar.so`):** upstream builds it from `app/src/main/cpp` against `libMNN.so` + `libnnrruntime.so` (from the CDN native-libs zip, verified 2026-11-19: zip ≈4MB, contains `libs/MNN/lib/arm64-v8a/libMNN.so`, `libs/NNR/lib/arm64-v8a/libnnrruntime.so`, `jniLibs/arm64-v8a/libsherpa-mnn-jni.so`). Not built or packaged by this module; `TaoRuntime.availability()` reports `native 库缺失` while it is absent.
- **JNI note:** upstream `libtaoavatar.so` exports `Java_com_taobao_meta_avatar_nnr_NnrAvatarRender_*`. The vendored class is `com.openapi.ks.dh.tao.nnr.NnrAvatarRender`, so a rebuild must export `Java_com_openapi_ks_dh_tao_nnr_NnrAvatarRender_*` and its `FindClass` must target `com/openapi/ks/dh/tao/nnr/NnrAvatarRender` (`CameraControlData` field access included). Task 7/8 device checks must verify symbol resolution, not just `loadLibrary` success.
- **Model assets:** upstream ships `TaoAvatar-NNR-MNN` + `UniTalker-MNN` only via the ModelScope downloader SDK (repos `MNN/TaoAvatar-NNR-MNN`, `MNN/UniTalker-MNN`; see app README "Models Collection" `https://modelscope.cn/collections/TaoAvatar-68d8a46f2e554a`). No stable single-zip URL was found in the upstream tree, so none is invented here: `TaoRuntime.modelsReady()` checks `cacheRoot(TAO)/{TaoAvatar-NNR-MNN,UniTalker-MNN}` directories (adb-push dev path), and only the native-libs zip is pinned in `AssetManifest`.
