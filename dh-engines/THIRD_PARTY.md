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
