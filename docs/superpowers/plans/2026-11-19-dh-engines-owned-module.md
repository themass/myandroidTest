# DH Engines Owned Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land one in-repo `:dh-engines` library with Duix / Live2D / Tao call-page runtimes under our package names, on-demand asset download, and call-menu switching that actually shows a face—without external `~/work/android` includes or reflection stubs.

**Architecture:** Single Android library `:dh-engines` owns `AssetStore` + three runtime facades (`DuixRuntime`, `Live2dRuntime`, `TaoRuntime`) and vendored/adapted engine sources under `com.openapi.ks.dh.{duix,live2d,tao}`. `:feature-companion` keeps `DigitalHumanEngine` / call-menu / `callStage`, but adapters call the facades directly. Large models and most `.so` packs download into app private storage when the user picks an engine; Cubism Core arm64 is the only third-party `.so` committed under `jniLibs`.

**Tech Stack:** Android library module, Kotlin, existing OkHttp in companion/app, NDK 27 + CMake 3.22.1, Robolectric unit tests, adb device checks for “has pixels”.

**Spec:** [docs/superpowers/specs/2026-11-19-dh-engines-owned-module-design.md](../specs/2026-11-19-dh-engines-owned-module-design.md)

## Global Constraints

- One Gradle module only (`:dh-engines`); three source partitions, not three modules.
- Packages: `com.openapi.ks.dh.duix` / `live2d` / `tao` (+ top-level facades under `com.openapi.ks.dh`).
- No git commits of giant model zips or Duix/Tao prebuilt trees; Cubism Core arm64 `libLive2DCubismCore.so` is the sole approved `.so` in-module exception.
- Default call stage remains original avatar (`callStage == null`); engines switch only from the existing top-right menu.
- Duix default model: Oliver — `https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip`; base `gj_dh_res` — `https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip`.
- Tao v1 acceptance: NNR / `AvatarTextureView` on screen; mouth/A2BS not required.
- Remove `-Pdh.lab.native` external includes before declaring done.
- Internal test only; ignore license productization this phase (keep upstream LICENSE files when copying).
- Work in this worktree; after green commits: merge `weifu-refactor` + push per standing rule (preserve dirty `app/build.gradle` there).

## Review Focus

- Cold cache: picking Duix/Live2D/Tao must download or explain failure—never silent no-op or blank “变成了文字” stage.
- Switching away from an engine always `release()` before the next create (no dual native overlap).
- `assembleChatDebug` without `-Pdh.lab.native` and without `~/work/android` must compile.
- Git status after import must not contain multi‑hundred‑MB binaries under `dh-engines/`.
- 32-bit / low-RAM: Tao unavailable reason still surfaces; Live2D/Duix degrade with explicit reason if ABI missing.

---

### Task 1: Scaffold `:dh-engines` and wire companion

**Files:**
- Create: `dh-engines/build.gradle`, `dh-engines/src/main/AndroidManifest.xml`, `dh-engines/THIRD_PARTY.md`
- Modify: `settings.gradle` (always `include ':dh-engines'`)
- Modify: `feature-companion/build.gradle` (`implementation project(':dh-engines')`; keep optional external includes for now)
- Modify: root `build.gradle` only if `:dh-engines` needs the same NDK/CMake hooks as the old external modules
- Test: `dh-engines/src/test/java/com/openapi/ks/dh/DhEnginesModuleSmokeTest.kt`

**Interfaces:**
- Consumes: none
- Produces: compilable empty library `namespace = "com.openapi.ks.dh"`; companion depends on it

- [ ] **Step 1: Write the failing smoke test**

```kotlin
@Test
fun assetStore_class_is_on_classpath() {
    // fails until AssetStore exists in Task 2; for Task 1 use:
    assertEquals("com.openapi.ks.dh", DhEngines.NAMESPACE)
}
```

Put `object DhEngines { const val NAMESPACE = "com.openapi.ks.dh" }` in the module as the minimal surface for this test, or skip classpath assert and instead run `:dh-engines:compileDebugKotlin` as the gate in Step 4 with a trivial `DhEngines.kt`.

Preferred Task 1 test:

```kotlin
@Test
fun module_namespace_constant() {
    assertEquals("com.openapi.ks.dh", DhEngines.NAMESPACE)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :dh-engines:testDebugUnitTest --tests com.openapi.ks.dh.DhEnginesModuleSmokeTest --offline`
Expected: FAIL module/project not found or unresolved `DhEngines`

- [ ] **Step 3: Add `:dh-engines` skeleton + `DhEngines.NAMESPACE` + companion `implementation project(':dh-engines')` + `THIRD_PARTY.md` stub listing upstream roots and the Oliver / gj_dh_res / Tao CDN / Cubism exception notes from the spec**

- [ ] **Step 4: Run tests / compile**

Run: `./gradlew :dh-engines:testDebugUnitTest --tests com.openapi.ks.dh.DhEnginesModuleSmokeTest :feature-companion:compileDebugKotlin --offline`
Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add settings.gradle dh-engines feature-companion/build.gradle docs/superpowers/specs/2026-11-19-dh-engines-owned-module-design.md
git commit -m "$(cat <<'EOF'
build(dh-engines): scaffold in-repo engines module

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `AssetStore` (download, cache, readiness)

**Files:**
- Create: `dh-engines/src/main/java/com/openapi/ks/dh/AssetStore.kt`
- Create: `dh-engines/src/main/java/com/openapi/ks/dh/AssetManifest.kt` (engine → list of `AssetSpec`)
- Test: `dh-engines/src/test/java/com/openapi/ks/dh/AssetStoreTest.kt`

**Interfaces:**
- Consumes: `DhEngines` module from Task 1; OkHttp (add dependency on `:dh-engines` if not transitive)
- Produces:

```kotlin
data class AssetSpec(
  val id: String,
  val url: String,
  val relativePath: String, // under engine cache root
  val sha256: String? = null,
  val kind: Kind = Kind.ZIP, // ZIP | FILE
)
enum class EngineAssets { DUIX, LIVE2D, TAO }

class AssetStore(context: Context, client: OkHttpClient = OkHttpClient(), baseUrlOverride: String? = null) {
  fun cacheRoot(engine: EngineAssets): File
  fun isReady(engine: EngineAssets): Boolean
  suspend fun ensure(engine: EngineAssets, onProgress: (Float) -> Unit = {}): Result<Unit>
}
```

Manifest pins (v1):
- Duix: `gj_dh_res.zip` → extract to `model/gj_dh_res` + touch `model/tmp/gj_dh_res`; `Oliver.zip` → `model/Oliver` + `model/tmp/Oliver`
- Live2D: ATRI model zip or files under `models/ATRI/` (URL: builder hosts from ChatWaifu assets packaged as downloadable zip **or** documents adb-push path + a reachable URL once chosen; must record final URL in `THIRD_PARTY.md`—if no public URL, ship a gradle/task that zips ATRI from the developer machine into GitHub release is out of scope; **Ruling default:** first Live2D model fetch may copy from `assets` only if files are small enough already in module—ATRI is ~8.5MB; **approved path:** keep ATRI out of git; require URL in manifest; for local red-green tests use a tiny fixture zip in `src/test/resources`)
- Tao: `https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip` plus NNR model URL from ModelScope/docs (record exact URL in `THIRD_PARTY.md` when extracting)

- [ ] **Step 1: Write failing tests**

```kotlin
fun isReady_false_on_empty_cache()
fun ensure_downloads_and_extracts_zip_into_relative_path()
fun ensure_skips_when_marker_present()
fun ensure_fails_cleanly_on_http_error()
fun baseUrlOverride_prefixes_relative_urls()
```

Use MockWebServer or a fake `OkHttpClient` + temp cache dir (Robolectric context).

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :dh-engines:testDebugUnitTest --tests com.openapi.ks.dh.AssetStoreTest --offline`
Expected: FAIL unresolved `AssetStore` / assertions

- [ ] **Step 3: Implement `AssetStore` + `AssetManifest` minimally**

- [ ] **Step 4: Run tests — expect PASS**

Run: same command
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add dh-engines
git commit -m "$(cat <<'EOF'
feat(dh-engines): AssetStore download and cache readiness

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Call-menu download gate (before engines land)

**Files:**
- Modify: `feature-companion/.../dh/DuixDigitalHumanEngine.kt` (availability can stay)
- Modify: `feature-companion/.../companion/CompanionRoute.kt` (`onEngineMenuClick`)
- Modify: `feature-companion/.../dh/DhEngineMenu.kt` if needed for “下载中” label
- Test: `feature-companion/src/test/java/com/openapi/ks/myapp/dh/DhEngineDownloadGateTest.kt`

**Interfaces:**
- Consumes: `AssetStore.isReady` / `ensure`
- Produces: selecting an engine when not ready starts download and **does not** set `callStage`; on failure toast; on success then set `callStage`

- [ ] **Step 1: Failing test for gate helper**

```kotlin
// pure function or small class
fun decideEngineClick(ready: Boolean, switches: Boolean): EngineClickAction
// NOT_READY_START_DOWNLOAD | UNAVAILABLE_REASON | SELECT
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement helper + wire `CompanionRoute.onEngineMenuClick` to call `AssetStore.ensure` on IO, show toast on failure, only then `callStage = row.id`**

Keep UI chrome unchanged; progress may be toast / statusHint string for v1.

- [ ] **Step 4: Run unit tests PASS**

Run: `./gradlew :feature-companion:testDebugUnitTest --tests com.openapi.ks.myapp.dh.DhEngineDownloadGateTest --tests com.openapi.ks.myapp.dh.DhEngineMenuTest --offline`

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(companion): download assets before switching dh engines

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Import Duix runtime into `:dh-engines`

**Files:**
- Create: copy+rewrite script or manual tree under `dh-engines/src/main/java/com/openapi/ks/dh/duix/` from `~/work/android/Duix-Mobile/.../duix-sdk` Java sources (`ai.guiji.duix` → `com.openapi.ks.dh.duix`)
- Create: needed `cpp/` for Duix JNI (link/load strategy per spec: prefer runtime load of prebuilts from `AssetStore` cache; if link-time required, Gradle `preBuild` into `dh-engines/build/` only)
- Create: `dh-engines/src/main/java/com/openapi/ks/dh/DuixRuntime.kt`
- Modify: `feature-companion/.../DuixDigitalHumanEngine.kt` — drop reflection; call `DuixRuntime`
- Modify: `DuixModelPaths.kt` — default model name `Oliver`; paths under `AssetStore.cacheRoot(DUIX)`
- Test: `dh-engines/src/test/.../DuixRuntimeAvailabilityTest.kt` + update companion `EngineAdaptersAvailabilityTest`

**Interfaces:**

```kotlin
class DuixRuntime(context: Context) {
  fun availability(): DhEngineAvailability // or module-local type mapped by adapter
  fun createView(): View?
  fun setVolume(v: Float)
  fun pushPcm(pcm16leMono16k: ByteArray)
  fun startMotion(id: Int, loop: Boolean)
  fun release()
}
```

- [ ] **Step 1: Failing test** — `createView_null_when_models_missing`; `package_is_ours` (class name starts with `com.openapi.ks.dh.duix`)

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Copy runtime sources, rename packages, implement `DuixRuntime`, rewire adapter, update manifest paths for Oliver**

Source filter: Java client + rendering JNI; **do not** commit `cpp/third` multi‑hundred‑MB trees.

- [ ] **Step 4: Unit tests PASS; `:app:assembleChatDebug` without `-Pdh.lab.native` still configures (Duix native may be stubbed until cache present)**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(dh-engines): vend Duix call runtime as com.openapi.ks.dh.duix

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Import Live2D runtime + Cubism Core jniLibs

**Files:**
- Create: `dh-engines/.../live2d/` from ChatWaifu Live2D Java/JNI (package → `com.openapi.ks.dh.live2d`)
- Create: `dh-engines/src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so` (copy from ChatWaifu `SDKRoot/Core/dll/android/arm64-v8a/`)
- Create: `Live2dRuntime.kt`
- Modify: `Live2dDigitalHumanEngine.kt` — no reflection
- Update: `THIRD_PARTY.md` Cubism exception note
- Test: `Live2dRuntimeAvailabilityTest.kt`

**Interfaces:** same shape as DuixRuntime (createView / mouth / expression / release).

- [ ] **Step 1: Failing test** — Core library file exists in module jniLibs path when packaged OR `Live2dRuntime` reports available when class+core present in test fixture

- [ ] **Step 2: FAIL then implement copy+rename+facade+adapter**

- [ ] **Step 3: Ensure Windows/iOS Cubism libs are **not** copied**

- [ ] **Step 4: Tests PASS; confirm `git` only adds the arm64 Core `.so` (~1.6MB), not full model packs**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(dh-engines): vend Live2D runtime + Cubism Core arm64

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Import Tao NNR on-screen runtime

**Files:**
- Create: `dh-engines/.../tao/` subset from `MnnTaoAvatar` (`AvatarTextureView`, `NnrAvatarRender`, required JNI/CMake)
- Create: `TaoRuntime.kt`
- Modify: `TaoDigitalHumanEngine.kt` + keep `TaoDeviceGate`
- Manifest: native-libs + NNR model via `AssetStore`
- Test: `TaoRuntimeAvailabilityTest.kt` (gate + missing assets)

**Interfaces:**

```kotlin
class TaoRuntime(context: Context) {
  fun availability(): … // device gate + assets
  fun createView(): View? // idle NNR mesh OK
  fun release()
}
```

- [ ] **Step 1: Failing tests** for low-mem unavailable + assets-not-ready

- [ ] **Step 2: Implement extract; strip MainActivity/LLM/ASR UI dependencies**

- [ ] **Step 3: Tests PASS**

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(dh-engines): vend Tao NNR runtime for on-screen avatar

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Remove reflection + external includes; docs

**Files:**
- Modify: `settings.gradle` — delete `dh.lab.native` external include block
- Modify: `feature-companion/build.gradle` — remove conditional `dh-engine-duix` / `dh-engine-live2d`
- Modify: root `build.gradle` — remove `beforeProject` hooks solely for external dh-engine-*
- Delete or gut: `*SdkReflect` helpers inside companion adapters
- Modify: `docs/dh-lab-models.md` — document `:dh-engines`, AssetStore paths, Oliver URL, Cubism exception, no `-Pdh.lab.native`
- Test: update `EngineAdaptersAvailabilityTest` / registry tests for direct availability reasons (`缺模型` / `未下载` / `机型不足`)

- [ ] **Step 1: Write failing test that `settings.gradle` no longer references `dh.lab.native` (simple text assert in a JVM test or script check run in gradle)**

Or: unit test adapters no longer mention reflective class names for Duix/Live2D.

- [ ] **Step 2: Remove includes + reflection; fix compile**

- [ ] **Step 3: Run**

`./gradlew :feature-companion:testDebugUnitTest --tests 'com.openapi.ks.myapp.dh.*' :dh-engines:testDebugUnitTest :app:assembleChatDebug --offline`

Expected: all PASS / BUILD SUCCESSFUL **without** `-Pdh.lab.native`

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(dh-lab): drop external native includes and reflection

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Device verification checklist + merge

**Files:**
- Modify: `docs/dh-lab-models.md` with adb push fallback paths matching `AssetStore.cacheRoot`
- Optional: note in side-session notes

- [ ] **Step 1: On device (when adb present)**

```bash
./gradlew :app:assembleChatDebug
adb push app/build/outputs/apk/chat/debug/app-chat-debug.apk /data/local/tmp/app-chat-debug.apk
adb shell pm install -r /data/local/tmp/app-chat-debug.apk
```

Manual: call page → original avatar visible → menu Live2D/Duix/Tao → download → face appears; failure paths toast.

- [ ] **Step 2: If device absent, document commands; do not claim device PASS**

- [ ] **Step 3: Merge to `weifu-refactor` + push both branches (preserve dirty `app/build.gradle`)**

- [ ] **Step 4: Final commit only if doc tweaks remain**

---

## Self-review notes

- Spec coverage: one module, three runtimes, our packages, on-demand download, call-menu UX, remove external include, Oliver/Cubism/Tao decisions — all tasked.
- Live2D ATRI download URL may need a one-line Ruling during Task 5 if no public URL exists (fixture for tests + documented cache layout for adb push); do not put ATRI in git.
- Proportion: tasks are import-sized; engine bodies are not transcribed—filter rules and facades are the decisions.
