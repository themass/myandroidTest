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

- **Cubism Core arm64 `libLive2DCubismCore.so`**: the sole third-party `.so` allowed into this module's `jniLibs` (approved exception). Models (e.g. ATRI) still download at runtime and are not committed.
- Other engines' large prebuilt `.so` packs stay on the download / local `build/` cache path — do not expand the in-git `.so` exception.

## Vendored sources

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
