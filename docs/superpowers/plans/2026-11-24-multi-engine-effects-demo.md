# Multi-Engine Effects Comparison Lab — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A debug-only Android Lab page that runs the same four scenes (数字人互动 / 数字人教学 / 相机全屏 / 相机小窗) through five real effect backends — Lottie, Rive, self-written GLES3 particles, Three.js in WebView, AGSL RuntimeShader — so the differences can be compared on one screen.

**Architecture:** A pure Kotlin decision layer (engine ids, metadata, availability gate, teaching script) is written first and unit-tested on the JVM. Renderers are thin Compose/View adapters behind one slot each: the stage scenes compose a renderer chosen by engine id; the camera path gains one default-null composable injection point in `:feature-camera-fx` so the camera keeps ownership of anchoring/EMA/hold while `:app` debug injects other backends. Everything except that one injection lives in `app/src/debug` (code, assets, `debugImplementation`), so a release APK physically cannot contain it.

**Tech Stack:** Kotlin + Jetpack Compose (BOM), CameraX 1.4.2, Lottie Compose 6.7.1 (already present), `app.rive:rive-android:11.16.3` (debug only), OpenGL ES 3 via TextureView+EGL (platform), AGSL `RuntimeShader` (API 33+), three.js 0.187.0 bundled offline by esbuild in `scripts/fx-lab/`.

**Spec:** [docs/superpowers/specs/2026-11-24-multi-engine-effects-demo-design.md](../specs/2026-11-24-multi-engine-effects-demo-design.md)

## Global Constraints

- minSdk 28, targetSdk/compileSdk 37 (`version.gradle`). AGSL requires API 33: below it the page states the reason; never fake a fallback.
- New code, assets and dependencies live only in `app/src/debug/` and `debugImplementation` (plus unit tests in `app/src/testDebug/`). Release must not contain demo classes, assets or the Rive native libs.
- `:app` main source may only gain a debuggable-guarded navigation entry addressed by explicit class name (`setClassName`), never a direct reference to a debug-only class.
- Feature modules must not depend on each other (`app/src/test/java/com/openapi/app/architecture/ModuleGraphTest.kt`). No new feature→feature dependency, no new architecture exemption.
- Production behaviour is unchanged: `LottieFxLayer`'s existing call sites, `CameraFxOverlayPolicy` fields, `fx/` asset vocabulary and default parameters stay exactly as today.
- Privacy boundary unchanged: camera frames stay in device memory; no upload, no disk write, no face-data logging. Closing the page releases analysis and native models.
- Effect renderers never add `pointerInput`/`clickable` — gesture arbitration exists in exactly one place (`avatarStageTransform` / camera page chips).
- Cue vocabulary unchanged: `gestureId` + monotonically increasing `gestureSeq` (`wave`, `pat_head`, `connected`). No new event bus.
- Camera anchoring unchanged: `CanvasMap(fill = true, mirrorX = true)`, `stickerAtHeadTop`, `PlacementSmoother` EMA and `StickerHoldTracker` stay owned by `:feature-camera-fx`.
- The Lab page is deterministic and offline: the teaching scene is a fixed 4-step script with manual prev/next, no server, no TTS, no simulated teaching content.
- No unlicensed third-party media. Rive `.riv` files come from the official `rive-app/rive-android` 11.16.3 sample app; a README beside them records that the repo's MIT license covers "the Software" and gives no asset-level grant, so they are debug-only comparison fixtures pending replacement with self-exported assets.
- Worker discipline: build only (`assembleChatDebug`); no `adb install`, no `versionCode` bump — device acceptance belongs to the coordinator session on V2279A.
- Git: work on the current branch `claude/lottie-device-accept`; every task ends with a commit ending in `Co-Authored-By: Claude Code <noreply@anthropic.com>`; final task does the mainline ritual (fetch → merge `origin/weifu-refactor` → tests → merge out → push → verify both `rev-list --count` directions are 0).

## Review Focus

- **Engine availability honesty** — when an asset is missing, the API is below 33, or a scene is unsupported, the status line must name the specific reason and the screen must not silently draw a different backend's output.
- **Stop / replay semantics** — pressing 停 ends every backend (Rive pause, GL clear, WebView rAF stop, AGSL animation stop), and re-triggering the same cue restarts it rather than leaving a frozen frame.
- **Camera placement plumbing reused, not re-derived** — non-Lottie camera stickers receive the exact `StickerPlacementPx` the Lottie path uses (same EMA-smoothed center, size, rotation); they must not compute their own landmark mapping.
- **Release containment** — the injection parameter added to `CameraFxGrantedContent` defaults to null and the production call sites are untouched; a release assemble plus a grep must show no demo class, no `fx-lab` asset, no Rive `.so`.
- **Small-window legibility** — in the 132×176 dp PIP the effect is still visible and sized off face width rather than viewport fractions, and no extra `SurfaceView` is introduced (GL uses a translucent TextureView).

---

### Task 1: Pure engine decision layer

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/FxEngineId.kt`
- Create: `app/src/debug/java/com/openapi/app/fxlab/FxEngineGate.kt`
- Create: `app/src/debug/java/com/openapi/app/fxlab/FxTeachingScript.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/FxEngineGateTest.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/FxTeachingScriptTest.kt`

**Interfaces:**
- Consumes: nothing (leaf).
- Produces:
  - `enum class FxEngineId { LOTTIE, RIVE, GL_PARTICLE, THREE_WEBGL, AGSL_SHADER }`
  - `enum class FxScene { DH_INTERACT, DH_TEACH, CAMERA_FULL, CAMERA_PIP }`
  - `data class FxEngineMeta(val id: FxEngineId, val label: String, val dependency: String, val assetNote: String, val minApi: Int, val readsLottieAssets: Boolean)`
  - `object FxEngineCatalog { val meta: Map<FxEngineId, FxEngineMeta>; fun all: List<FxEngineId> }` — order is the chip order: LOTTIE, RIVE, GL_PARTICLE, THREE_WEBGL, AGSL_SHADER.
  - `sealed interface GateOutcome { data object Enabled : GateOutcome; data class Unavailable(val reason: String) : GateOutcome }`
  - `fun FxEngineGate.decide(engine: FxEngineId, scene: FxScene, sdkInt: Int, assetReady: Boolean, reasonWhenMissing: String): GateOutcome`
  - `data class TeachingStep(val cue: String, val anchor: String, val caption: String)`
  - `object FxTeachingScript { val steps: List<TeachingStep>; fun next(index: Int): Int; fun prev(index: Int): Int }` — index is 0-based, clamped, never out of range.

- [ ] **Step 1: Write the failing gate tests**

`app/src/testDebug/java/com/openapi/app/fxlab/FxEngineGateTest.kt` — JUnit 4 (repo uses `junit:junit:4.13.2`), pin these behaviours:

```kotlin
@Test fun agslUnavailableBelowApi33WithReasonNamingBothVersions() {
    val o = FxEngineGate.decide(FxEngineId.AGSL_SHADER, FxScene.DH_INTERACT, 28, true, "")
    assertTrue(o is GateOutcome.Unavailable)
    val r = (o as GateOutcome.Unavailable).reason
    assertTrue(r.contains("33") && r.contains("28"))
}

@Test fun agslAvailableOn33() {
    assertEquals(GateOutcome.Enabled, FxEngineGate.decide(FxEngineId.AGSL_SHADER, FxScene.DH_INTERACT, 33, true, ""))
}

@Test fun missingAssetReportsGivenReasonNotGenericFailure() {
    val o = FxEngineGate.decide(FxEngineId.RIVE, FxScene.DH_INTERACT, 37, false, "缺 .riv 资产")
    assertEquals("缺 .riv 资产", (o as GateOutcome.Unavailable).reason)
}

@Test fun lottieAlwaysEnabledEveryScene() {
    FxScene.entries.forEach { assertEquals(GateOutcome.Enabled, FxEngineGate.decide(FxEngineId.LOTTIE, it, 28, true, "")) }
}

@Test fun everyEngineHasMetaAndChipOrderIsFixed() {
    assertEquals(listOf(LOTTIE, RIVE, GL_PARTICLE, THREE_WEBGL, AGSL_SHADER), FxEngineCatalog.all)
    FxEngineCatalog.all.forEach { id -> assertEquals(id, FxEngineCatalog.meta.getValue(id).id) }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'`
Expected: compile failure / FAIL — `FxEngineGate` unresolved.

- [ ] **Step 3: Implement the three files**

Signatures exactly as in Interfaces. Gate rule: `Unavailable` when `sdkInt < meta.minApi` (reason built from both numbers), else `Unavailable(reasonWhenMissing)` when `!assetReady`, else `Enabled`. `AGSL_SHADER` carries `minApi = 33`, every other engine `minApi = 28`. Meta strings are the data source for the on-screen status line, so keep them factual: Rive `dependency = "app.rive:rive-android:11.16.3"`, `readsLottieAssets = false`, `assetNote = "官方样例 .riv（授权不确定，debug 对比用）"`; GL `dependency = "平台 GLES3（无依赖）"`; Three `dependency = "three 0.187.0（离线 bundle）"`; Lottie `dependency = "lottie-compose 6.7.1（已在包里）"`, `readsLottieAssets = true`.
Teaching script: 4 fixed steps with cues `connected`, `pat_head`, `wave`, `pat_head`, captions naming the teaching beat (讲解开始 / 指给你看 / 跟着做 / 小结), anchors `FULL`, `HEAD`, `HAND`, `HEAD`; `next`/`prev` clamp into `0..steps.lastIndex`.

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug/java/com/openapi/app/fxlab app/src/testDebug
git commit -m "feat(fx-lab): engine catalog, availability gate and teaching script"
```

---

### Task 2: Debug Lab entry and page shell

**Files:**
- Create: `app/src/debug/AndroidManifest.xml` — merge in the Activity entry (file already exists; add `<activity>` inside its `<application>`)
- Create: `app/src/debug/java/com/openapi/app/fxlab/EffectsLabActivity.kt`
- Create: `app/src/debug/java/com/openapi/app/fxlab/EffectsLabScreen.kt`
- Modify: `app/src/main/java/com/openapi/app/home/presentation/HomeScreen.kt:264-279` (the debug 实验室 drawer block)
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/EffectsLabScreenTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `FxEngineCatalog.all`, `FxEngineMeta`, `GateOutcome` from Task 1.
- Produces:
  - `data class LabUiState(val scene: FxScene, val engine: FxEngineId, val gestureId: String?, val gestureSeq: Long, val teachIndex: Int, val gate: GateOutcome)`
  - `@Composable fun EffectsLabScreen(onNavigateBack: () -> Unit = {}, cameraPipContent: (@Composable (Modifier) -> Unit)? = null)` — the shell: scene chips, engine chips, trigger buttons (摸头 / 挥手 / 接通 / 停), teaching prev/next, status line.
  - `fun labStatusLine(meta: FxEngineMeta, outcome: GateOutcome, scene: FxScene): String` — pure, in `EffectsLabScreen.kt`.
  - `object EffectsLabLaunch { const val ACTION = "com.openapi.app.action.EFFECTS_LAB"; const val CLASS = "com.openapi.app.fxlab.EffectsLabActivity" }` — used by main-source navigation and by the debug manifest.

- [ ] **Step 1: Write the failing screen tests**

Robolectric (the repo already runs Robolectric Compose tests in `app/src/test/java/com/openapi/app/ui/`; copy their `@RunWith(RobolectricTestRunner::class)` + `createComposeRule` setup). Pin:

```kotlin
@Test fun statusLineNamesEngineDependencyAndGateReason() {
    val s = labStatusLine(FxEngineCatalog.meta.getValue(FxEngineId.AGSL_SHADER),
        GateOutcome.Unavailable("本机 API 28，RuntimeShader 需 API 33"), FxScene.CAMERA_FULL)
    assertTrue(s.contains("AGSL") && s.contains("API 33"))
}

@Test fun engineChipSwitchUpdatesGateWithoutChangingScene() { /* click RIVE chip → state.engine == RIVE, state.scene unchanged */ }

@Test fun teachNextClampsAtEnds() { /* four next() clicks → teachIndex == 3; next again stays 3 */ }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'`
Expected: FAIL — `EffectsLabScreen` / `labStatusLine` unresolved.

- [ ] **Step 3: Implement shell + entry**

Activity is a `ComponentActivity` that calls `enableEdgeToEdge()` and `setContent { MaterialTheme { EffectsLabScreen(onNavigateBack = { finish() }) } }`. Register it in `app/src/debug/AndroidManifest.xml` with `android:exported="false"` and an `<intent-filter>` for `EffectsLabLaunch.ACTION` — no launcher category. In `HomeScreen.kt`, inside the existing `CameraFxLabGate.isEnabled(context)` block, add a second `NavigationDrawerItem` labelled 「特效引擎对比」 whose `onClick` builds `Intent().setClassName(context.packageName, EffectsLabLaunch.CLASS).setAction(EffectsLabLaunch.ACTION)`; the whole block stays behind the same debuggable check so release never shows it. Main source references only `EffectsLabLaunch`, which lives in `:app` **main** as a 2-line object holding the two `const`s — no debug-only type is visible from main.

- [ ] **Step 4: Run tests and assemble**

Run: `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src app/src/main/java/com/openapi/app/home/presentation/HomeScreen.kt
git commit -m "feat(fx-lab): debug-only effects lab entry and page shell"
```

---

### Task 3: Stage scene host with a real avatar plus Lottie baseline

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/StageScene.kt`
- Modify: `app/src/debug/java/com/openapi/app/fxlab/EffectsLabScreen.kt` (render the stage scene for `DH_INTERACT` / `DH_TEACH`)
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/CueTickTest.kt`

**Interfaces:**
- Consumes: `com.openapi.ks.myapp.avatar.{AvatarPackStore, BundledAvatars, LayeredAvatar, LayeredAvatarPack}` (public in `:feature-companion`), `com.openapi.feature.companion.fx.LottieFxLayer` (public), Task 1 types.
- Produces:
  - `class CueTick { var gestureId: String?; var seq: Long; fun fire(cue: String); fun stop() }` — `fire` sets `gestureId` and increments `seq`; `stop` clears `gestureId` (seq unchanged).
  - `@Composable fun StageScene(state: LabUiState, pack: LayeredAvatarPack, modifier: Modifier = Modifier)` — avatar layer + effect layer, no `pointerInput` of its own.
  - `fun teachCueFor(index: Int): String` = `FxTeachingScript.steps[index].cue`.

- [ ] **Step 1: Write the failing cue test**

```kotlin
@Test fun sameCueTwiceIncrementsSeqSoLayerRestarts() {
    val t = CueTick(); t.fire("pat_head"); val a = t.seq; t.fire("pat_head")
    assertTrue(t.seq > a && t.gestureId == "pat_head")
}

@Test fun stopClearsGestureIdWithoutBumpingSeq() {
    val t = CueTick(); t.fire("wave"); val s = t.seq; t.stop()
    assertEquals(null, t.gestureId); assertEquals(s, t.seq)
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'`, expect unresolved `CueTick`.

- [ ] **Step 3: Implement `StageScene`**

Load the avatar with `BundledAvatars.ensureDemo(context, store)` then `store.loadLayered(BundledAvatars.DEMO_KEY)`; while loading or if null, show the text `形象未安装` rather than drawing a partial figure. Draw `LayeredAvatar(pack = pack, mouthLevel = { 0f })` sized by its own aspect ratio, then for `engine == LOTTIE` call `LottieFxLayer(gestureId = state.gestureId, gestureSeq = state.gestureSeq, reducedMotion = false, particles = true, stageZoomed = false)` as a `fillMaxSize` Box sibling. Teaching mode drives cues from a `LaunchedEffect(state.teachIndex)` that calls `cueTick.fire(teachCueFor(index))` once per index change — the same slot the manual buttons use, so no second trigger path exists.

- [ ] **Step 4: Run tests, expect pass** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug/java/com/openapi/app/fxlab/StageScene.kt app/src/testDebug
git commit -m "feat(fx-lab): stage scene host with bundled avatar and Lottie baseline"
```

---

### Task 4: Camera injection point in feature-camera-fx

**Files:**
- Modify: `feature-camera-fx/src/main/java/com/openapi/feature/camerafx/CameraFxGrantedContent.kt:68-78` and the sticker block at `:211-306`
- Create: `feature-camera-fx/src/main/java/com/openapi/feature/camerafx/CameraFxStickerRenderer.kt`
- Test: `feature-camera-fx/src/test/java/com/openapi/feature/camerafx/CameraFxStickerRendererTest.kt`

**Interfaces:**
- Consumes: existing `CanvasMap`, `stickerAtHeadTop`, `StickerSlotState`, `StickerTrack`, `StickerPlacementPx`.
- Produces:
  - `typealias CameraFxStickerRenderer = @Composable (placement: StickerPlacementPx, modifier: Modifier) -> Unit`
  - `CameraFxGrantedContent(modifier, overlayPolicy, onStageGesture, stickerRenderer: CameraFxStickerRenderer? = null)` — new trailing default-null parameter; `CameraFxStickerPip(modifier, onStageGesture, stickerRenderer: CameraFxStickerRenderer? = null)`.
  - `fun stickerRendererTakesOverWhenSet(rendererSet: Boolean, compLoaded: Boolean): Boolean` — trivial pure helper that encodes the branch rule, so it is testable.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun rendererOverridesLottieBranch() {
    assertTrue(stickerRendererTakesOverWhenSet(rendererSet = true, compLoaded = true))
}
@Test fun nullRendererKeepsLottieBranch() {
    assertFalse(stickerRendererTakesOverWhenSet(rendererSet = false, compLoaded = true))
}
@Test fun placementValuesSurviveInjection() {
    val map = CanvasMap(canvasW = 400f, canvasH = 300f, imgW = 600, imgH = 800, fill = true, mirrorX = true)
    val p = stickerAtHeadTop(map, Point2d(0.5, 0.2), faceWidthNorm = 0.3f, tiltDegForDisplay = 7f, widthFractionOfFace = 1.05f)
    val seen = mutableListOf<StickerPlacementPx>()
    val renderer: CameraFxStickerRenderer = { placement, _ -> seen += placement }
    renderer(p, Modifier)   // host passes the same StickerPlacementPx it would feed LottieAnimation
    assertEquals(p, seen.single())
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :feature-camera-fx:testDebugUnitTest` — expect unresolved `CameraFxStickerRenderer`.

- [ ] **Step 3: Implement**

Compute `placeFinal` exactly as today. Where the code currently picks `LottieAnimation` vs the pink-circle fallback, branch first on `stickerRenderer != null`: when set, call `stickerRenderer(placeFinal, Modifier.zIndex(2f).offset{...}.size(stickerSize).rotate(placeFinal.rotationDeg))` — the renderer receives the already-smoothed placement and must not recompute geometry. Keep the Lottie branch untouched when null. No new fields on `CameraFxOverlayPolicy`; no new imports of runtime libraries in this module.

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew :feature-camera-fx:testDebugUnitTest :app:testChatDebugUnitTest --tests '*ModuleGraph*'` → both PASS (no new module edge).

- [ ] **Step 5: Commit**

```bash
git add feature-camera-fx
git commit -m "feat(camera-fx): optional sticker renderer injection at the composable boundary"
```

---

### Task 5: Camera scenes in the Lab (full screen + PIP)

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/CameraScenes.kt`
- Modify: `app/src/debug/java/com/openapi/app/fxlab/EffectsLabScreen.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/CameraConsentGateTest.kt`

**Interfaces:**
- Consumes: `com.openapi.feature.camerafx.{CameraFxGrantedContent, CameraFxStickerPip, CameraFxOverlayPolicy, CameraFxStickerRenderer, StickerPlacementPx}`, Task 1 types.
- Produces:
  - `fun labCameraPolicy(engine: FxEngineId): CameraFxOverlayPolicy` — PIP/full policy: `drawWireframe = (engine == LOTTIE && scene == CAMERA_FULL)`, `showDebugHud = (scene == CAMERA_FULL)`, sticker on.
  - `fun cameraNeedsConsent(accepted: Boolean, granted: Boolean): Boolean`
  - `@Composable fun CameraScene(state: LabUiState, renderer: CameraFxStickerRenderer?, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun fullSceneShowsHudPipDoesNot() {
    assertTrue(labCameraPolicy(FxEngineId.LOTTIE).showDebugHud)
}
@Test fun stickerAlwaysOnForEveryEngine() {
    FxEngineCatalog.all.forEach { assertTrue(labCameraPolicy(it).drawStickers) }
}
@Test fun consentAskedOnlyWhenNotAcceptedAndNotGranted() {
    assertTrue(cameraNeedsConsent(accepted = false, granted = false))
    assertFalse(cameraNeedsConsent(accepted = true, granted = false))
    assertFalse(cameraNeedsConsent(accepted = false, granted = true))
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*'` → unresolved `labCameraPolicy`.

- [ ] **Step 3: Implement**

Reuse `CameraFxGrantedContent` directly (it already handles CameraX binding, permission-free grant assumption, analysis release on dispose). Show the same consent wording as `CameraFxScreen` before camera start (`画面仅在本机实时处理，不上传服务器、不保存、不记录任何人脸数据。关闭本页即停止全部处理。`). `CAMERA_FULL` renders it at `fillMaxSize`; `CAMERA_PIP` renders `CameraFxStickerPip` at `Modifier.size(132.dp, 176.dp)` with a labelled dark backdrop so the small size is visible inside the Lab. Pass the engine's renderer (Task 6–9 produce them; until then pass `null` = Lottie).

- [ ] **Step 4: Run tests and assemble** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug app/src/testDebug
git commit -m "feat(fx-lab): camera full-screen and PIP comparison scenes"
```

---

### Task 6: Rive renderer with the official sample fixtures

**Files:**
- Modify: `app/build.gradle` (add `debugImplementation 'app.rive:rive-android:11.16.3'` next to the other module deps, with a comment marking it debug-only)
- Create: `app/src/debug/res/raw/fxlab_loopy.riv` and `app/src/debug/res/raw/fxlab_circle_move.riv` (fetched from `https://raw.githubusercontent.com/rive-app/rive-android/11.16.3/app/src/main/res/raw/{loopy,circle_move}.riv`, 211 B and 269 B)
- Create: `app/src/debug/res/raw/README-fxlab-rive.md` (provenance + the licensing caveat + how to replace with a self-exported `.riv`)
- Create: `app/src/debug/java/com/openapi/app/fxlab/RiveStickerRenderer.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/RiveAssetTest.kt`

**Interfaces:**
- Consumes: `CameraFxStickerRenderer` (Task 4), `app.rive.{Rive, RiveFileSource, rememberRiveFile, rememberRiveWorkerOrNull, Result}`.
- Produces: `@Composable fun RiveStickerRenderer(resId: Int, placement: StickerPlacementPx, modifier: Modifier)`, `@Composable fun RiveStageRenderer(resId: Int, modifier: Modifier)`, and `fun riveRawResFor(engineAsset: String): Int` (name → `R.raw` id, `0` when absent).

- [ ] **Step 1: Write the failing asset test**

```kotlin
@Test fun bundledRiveFixturesExistAndAreNonEmpty() {
    listOf("fxlab_loopy", "fxlab_circle_move").forEach { name ->
        val id = riveRawResFor(name)
        assertTrue("$name missing", id != 0)
        assertTrue("$name empty", context.resources.openRawResource(id).readBytes().size > 100)
    }
}
```

- [ ] **Step 2: Run to verify failure** — expect unresolved `riveRawResFor` (and dependency not yet declared).

- [ ] **Step 3: Fetch the fixtures and add the dependency**

`curl -sfL -o app/src/debug/res/raw/fxlab_loopy.riv https://raw.githubusercontent.com/rive-app/rive-android/11.16.3/app/src/main/res/raw/loopy.riv` (same for `circle_move`). Do **not** take `walle.riv` or any `.riv` whose art references external images — the official raw dir contains PNG/webp dependencies; only self-contained files are bundled. Write the README recording source commit `11.16.3`, byte sizes, and that the repo's MIT license covers "the Software" with no asset-level grant, so these are debug comparison fixtures to be replaced by self-exported assets. Add the `debugImplementation` line.

- [ ] **Step 4: Implement the renderers**

Worker + file: `val worker = rememberRiveWorkerOrNull(errorState)`, `val file = rememberRiveFile(RiveFileSource.RawRes.from(resId), worker)`; handle `Result.Loading` (draw nothing), `Result.Error` (draw a bordered box with the message so the failure is visible rather than blank), `Result.Success` → `Rive(riveFile.value)`. Apply the caller's `modifier` unchanged (that is where size/offset/rotation already live).

- [ ] **Step 5: Run tests, assemble, and check release containment**

```bash
./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug :app:assembleChatRelease
unzip -l app/build/outputs/apk/chat/release/app-chat-release*.apk | rg -i 'rive|fxlab' || echo "release clean"
```
Expected: tests PASS, `release clean`. (If release signing blocks the assemble, run `:app:assembleChatReleaseUnitTest` instead and say so — do not fabricate the grep result.)

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/src/debug/res/raw app/src/debug/java/com/openapi/app/fxlab/RiveStickerRenderer.kt
git commit -m "feat(fx-lab): Rive renderer with official sample fixtures (debug only)"
```

---

### Task 7: GLES3 particle renderer (translucent TextureView)

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/GLParticleView.kt`
- Create: `app/src/debug/java/com/openapi/app/fxlab/FxParticleSim.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/FxParticleSimTest.kt`

**Interfaces:**
- Consumes: `StickerPlacementPx`, `CameraFxStickerRenderer`, Task 1 `FxEngineId`.
- Produces:
  - `class FxParticleSim(val kind: ParticleKind, val seed: Long) { fun step(dtSeconds: Float): ParticleFrame; fun alive(): Boolean }`
  - `enum class ParticleKind { PAT_HEAD_BURST, WAVE_ARC, AMBIENT_LOOP }`
  - `data class ParticleFrame(val positions: FloatArray, val count: Int)` — normalized 0..1 in view space.
  - `@Composable fun GLParticleOverlay(kind: ParticleKind, tick: Long, modifier: Modifier)` — AndroidView of a `TextureView` + EGL (no `GLSurfaceView`, no Z-order tricks); `tick` change restarts the burst.
  - `@Composable fun GLParticleStickerRenderer(placement: StickerPlacementPx, modifier: Modifier)`

- [ ] **Step 1: Write the failing simulation tests** (pure JVM — this is where the behaviour is pinned, not in GL)

```kotlin
@Test fun burstEmitsParticlesThenDiesWithinLifetime() {
    val sim = FxParticleSim(ParticleKind.PAT_HEAD_BURST, seed = 42L)
    assertTrue(sim.step(0.016f).count > 0)
    var t = 0.016f; var guard = 0
    while (sim.alive() && guard++ < 600) t += 0.016f
    assertTrue("burst should end in <10s, ended at ${t}s", t < 10f)
}

@Test fun sameSeedGivesSameTraceSoScreensAreReproducible() {
    val a = FxParticleSim(ParticleKind.PAT_HEAD_BURST, 7L); val b = FxParticleSim(ParticleKind.PAT_HEAD_BURST, 7L)
    repeat(20) { assertEquals(a.step(0.016f).positions.toList(), b.step(0.016f).positions.toList()) }
}

@Test fun ambientLoopStaysAliveUntilStopped() {
    val sim = FxParticleSim(ParticleKind.AMBIENT_LOOP, 1L); repeat(120) { sim.step(0.016f) }; assertTrue(sim.alive())
}
```

- [ ] **Step 2: Run to verify failure** → unresolved `FxParticleSim`.

- [ ] **Step 3: Implement sim, then the GL host**

Sim: fixed-timestep Euler integration, gravity for `PAT_HEAD_BURST`, arc + fade for `WAVE_ARC`, respawn-on-expire for `AMBIENT_LOOP`; `java.util.Random(seed)` only (no `kotlin.random` global). GL host: `TextureView` with `isOpaque = false`, one EGL context on a dedicated thread, `GLES3` clear-to-transparent, `glDrawArrays(GL_POINTS, ...)` with a single shader program; upload `positions` each frame; stop the render loop on `release()`/detach so a closed page costs nothing. Size/count caps (`count <= 512`) so the small window cannot spike.

- [ ] **Step 4: Run tests and assemble** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug/java/com/openapi/app/fxlab app/src/testDebug
git commit -m "feat(fx-lab): GLES3 particle renderer on a translucent TextureView"
```

---

### Task 8: AGSL RuntimeShader overlay

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/AgslOverlay.kt`
- Create: `app/src/debug/res/raw/fxlab_agsl.agsl`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/AgslSourceTest.kt`

**Interfaces:**
- Consumes: Task 1 gate.
- Produces: `@Composable fun AgslGlowOverlay(active: Boolean, modifier: Modifier)` — draws a translucent `ShaderBrush` ring/halo over the scene; `fun agslUniforms(tSeconds: Float, intensity: Float): Map<String, Float>`; `fun agslSupported(sdkInt: Int): Boolean` (== `sdkInt >= 33`).

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun agslSupportedOnlyAt33Plus() {
    assertFalse(agslSupported(28)); assertFalse(agslSupported(32))
    assertTrue(agslSupported(33)); assertTrue(agslSupported(37))
}
@Test fun uniformsAreFiniteAndIntensityClamped() {
    val u = agslUniforms(tSeconds = 1.5f, intensity = 5f)
    assertTrue(u.values.all { it.isFinite() })
    assertTrue((u["intensity"] ?: 0f) <= 1f)
}
```

- [ ] **Step 2: Run to verify failure** → unresolved `agslSupported`.

- [ ] **Step 3: Implement**

AGSL in `fxlab_agsl.agsl`: an `outColor` ring driven by `uniform float t`, `intensity`, `centerX/centerY`; alpha falls to 0 at the edges so it reads as a halo, never an opaque sheet. `RuntimeShader(context.resources.openRawResource(R.raw.fxlab_agsl).reader().readText())` behind the API check; animate `t` with `withFrameNanos` only while `active`; `ShaderBrush` + `drawRect` in a `Canvas`. Wrap construction in `runCatching` and on failure show the shader's parse error in the status line (device GPU drivers differ; a silent blank is not acceptable).

- [ ] **Step 4: Run tests and assemble** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug
git commit -m "feat(fx-lab): AGSL RuntimeShader halo overlay gated to API 33+"
```

---

### Task 9: Three.js transparent overlay scene

**Files:**
- Create: `scripts/fx-lab/three-scene/package.json`, `build.mjs`, `src/main.js` (same shape as `scripts/dh-lab/vrm-scene`, minus every VRM/model import)
- Create: `app/src/debug/assets/fx-lab/three.html`, `app/src/debug/assets/fx-lab/three.bundle.js` (built artifact, committed like `dh-vrm/scene.bundle.js`)
- Create: `app/src/debug/java/com/openapi/app/fxlab/ThreeWebViewOverlay.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/ThreeWebViewUrlTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks except `FxEngineId.THREE_WEBGL`.
- Produces:
  - `object FxLabThreeUrls { const val START_URL = "https://fx-lab.local/three.html"; val ALLOWED: Set<String> = setOf("three.html", "three.bundle.js"); fun pathFor(url: String): String? }`
  - `@Composable fun ThreeOverlay(active: Boolean, command: String?, modifier: Modifier)` — WebView with `javaScriptEnabled = true`, `allowFileAccess = false`, transparent background, request interception limited to `ALLOWED`, everything else blocked (same pattern as `VrmDigitalHumanEngine`, which stays untouched).

- [ ] **Step 1: Write the failing URL tests**

```kotlin
@Test fun onlyWhitelistedPathsResolve() {
    assertEquals("three.html", FxLabThreeUrls.pathFor("https://fx-lab.local/three.html"))
    assertEquals("three.bundle.js", FxLabThreeUrls.pathFor("https://fx-lab.local/three.bundle.js"))
    assertEquals(null, FxLabThreeUrls.pathFor("https://evil.example/x.js"))
}
```

- [ ] **Step 2: Run to verify failure** → unresolved `FxLabThreeUrls`.

- [ ] **Step 3: Write the JS scene and build it**

`src/main.js`: `WebGLRenderer({ alpha: true, antialias: true })`, a self-made effect only (ring of emissive geometry + a light sweep), `requestAnimationFrame` loop that stops when `window.FxLab.stop()` is called, and `window.FxLab = { start, stop, command }` where `command(json)` handles `{"type":"cue","name":"pat_head"|"wave"|"connected"}`. No `GLTFLoader`, no `three-vrm`, no network fetch. Build: `cd scripts/fx-lab/three-scene && npm install --no-audit --no-fund && node build.mjs` (esbuild IIFE → `app/src/debug/assets/fx-lab/three.bundle.js`); `package.json` dependencies: `three@0.187.0`, `esbuild@^0.24.0` (devDependency), matching the existing `scripts/dh-lab/vrm-scene` conventions. Commit the built bundle; note the regen command in `three.html`'s header comment.

- [ ] **Step 4: Implement the Kotlin overlay and wire it**

Follow `VrmDigitalHumanEngine`'s interception shape (`shouldInterceptRequest` → assets read under `fx-lab/`, `BLOCKED` response for anything else), `release()` removes and destroys the WebView on dispose. Pass cues through `evaluateJavascript` guarded by a `JavascriptInterface` `onReady` handshake, so a command issued before the page is ready is dropped rather than queued forever.

- [ ] **Step 5: Run tests and assemble** — `./gradlew :app:testChatDebugUnitTest --tests '*fxlab*' :app:assembleChatDebug` → PASS.

- [ ] **Step 6: Commit**

```bash
git add scripts/fx-lab app/src/debug/assets/fx-lab app/src/debug/java/com/openapi/app/fxlab/ThreeWebViewOverlay.kt
git commit -m "feat(fx-lab): offline Three.js overlay scene (debug only, no external models)"
```

---

### Task 10: Wire every engine into both scene families, plus stop/replay

**Files:**
- Modify: `app/src/debug/java/com/openapi/app/fxlab/EffectsLabScreen.kt`
- Create: `app/src/debug/java/com/openapi/app/fxlab/FxRendererFactory.kt`
- Test: `app/src/testDebug/java/com/openapi/app/fxlab/RendererDispatchTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 3–9.
- Produces:
  - `@Composable fun LabEffectLayer(state: LabUiState, modifier: Modifier)` — one place that maps `(engine, scene)` to a renderer; unknown/`Unavailable` → status text, never another engine's pixels.
  - `fun stageRendererFor(engine: FxEngineId): RendererKind` and `fun cameraRendererFor(engine: FxEngineId): CameraFxStickerRenderer?` (null only for LOTTIE, meaning "keep the module's own Lottie branch").
  - `enum class RendererKind { LOTTIE_LAYER, RIVE, GL, AGSL, THREE }`
  - `fun replayTick(triggerSeq: Long, stopSeq: Long): Long` — the single value every stateful renderer keys on, so a stop followed by a re-trigger always restarts the effect.

- [ ] **Step 1: Write the failing dispatch and replay tests**

```kotlin
@Test fun everyEngineDispatchesToItsOwnKindOnStage() {
    assertEquals(LOTTIE_LAYER, stageRendererFor(FxEngineId.LOTTIE))
    assertEquals(RIVE, stageRendererFor(FxEngineId.RIVE))
    assertEquals(GL, stageRendererFor(FxEngineId.GL_PARTICLE))
    assertEquals(THREE, stageRendererFor(FxEngineId.THREE_WEBGL))
    assertEquals(AGSL, stageRendererFor(FxEngineId.AGSL_SHADER))
}

@Test fun cameraUsesOwnRenderersAndOnlyLottieFallsBackToModuleBranch() {
    assertEquals(null, cameraRendererFor(FxEngineId.LOTTIE))
    FxEngineCatalog.all.filter { it != FxEngineId.LOTTIE }.forEach {
        assertNotNull(cameraRendererFor(it))
    }
}

@Test fun stopBumpsReplayTickSoNextTriggerRestartsEffect() {
    val before = replayTick(triggerSeq = 5, stopSeq = 2)
    val afterStop = replayTick(triggerSeq = 5, stopSeq = 3)
    assertTrue(afterStop > before)
    assertTrue(replayTick(triggerSeq = 6, stopSeq = 3) > afterStop)
}

@Test fun unavailableGateNeverFallsBackToAnotherEngine() {
    // LabEffectLayer renders the gate reason text and no renderer node when
    // decide() returns Unavailable for the selected engine.
    assertEquals(null, rendererKindIfEnabled(FxEngineId.AGSL_SHADER, GateOutcome.Unavailable("本机 API 28，需 33")))
    assertEquals(AGSL, rendererKindIfEnabled(FxEngineId.AGSL_SHADER, GateOutcome.Enabled))
}
```

- [ ] **Step 2: Run to verify failure** → unresolved `stageRendererFor`.

- [ ] **Step 3: Implement dispatch and stop/replay**

The Lab's 停 button calls `cueTick.stop()` **and** advances a `stopTick` that every stateful renderer watches (GL: clear + stop loop; Rive: pause; Three: `FxLab.stop()`; AGSL: `active = false`). Re-triggering a cue bumps the renderer's `tick` so the same effect replays. Camera renderers receive `null` for Lottie (module keeps its own branch) and a placement-based renderer otherwise. Gate check happens once per (engine, scene) and is displayed by `labStatusLine`, not by hiding chips.

- [ ] **Step 4: Run tests, assemble, run the whole affected suite**

Run: `./gradlew :app:testChatDebugUnitTest :feature-camera-fx:testDebugUnitTest :feature-companion:testDebugUnitTest :app:assembleChatDebug`
Expected: PASS except any pre-existing failure you must name explicitly with its output (the Live2D companion unit failure reproduces on baseline — if it appears, report it, don't fix it here).

- [ ] **Step 5: Commit**

```bash
git add app/src/debug app/src/testDebug
git commit -m "feat(fx-lab): dispatch all five engines across stage and camera scenes"
```

---

### Task 11: Documentation, release-containment check, mainline merge

**Files:**
- Create: `app/src/debug/java/com/openapi/app/fxlab/README.md` (how to open the Lab, what each engine shows, how to replace the Rive fixtures, how to rebuild the Three bundle, what is not yet device-verified)
- Modify: `docs/superpowers/specs/2026-11-24-effects-rendering-architecture-and-options.md` (apply the corrections §9 of the design lists — the four overstatements — so the two docs stop contradicting each other)

**Interfaces:** none (docs + verification).

- [ ] **Step 1: Prove release containment with commands, not claims**

```bash
./gradlew :app:assembleChatRelease
unzip -l app/build/outputs/apk/chat/release/app-chat-release*.apk | rg -i 'rive|fxlab|three|agsl' && echo "LEAK" || echo "release clean"
rg -l 'fxlab' feature-companion/src/main feature-camera-fx/src/main && echo "LEAK" || echo "no main-source demo refs"
```
Expected: `release clean` and `no main-source demo refs`. Record the actual APK sizes (debug and release) in the README — measured numbers only.

- [ ] **Step 2: Write the Lab README**

Include: drawer path 「实验室 → 特效引擎对比」; the four scenes; per-engine one-liner of what you should see and what is a known gap (Rive fixtures are official sample files with unresolved asset licensing — replace by exporting your own `.riv` into `app/src/debug/res/raw/`; AGSL needs API 33; Three.js is a local transparent overlay, not composited into camera output or LiveKit); explicit statement that device acceptance has not been performed by this task.

- [ ] **Step 3: Fix the older options doc**

Edit only the five wording items §9 of the design names (Rive 「唯一」, 「相机贴纸不是 Spine 的场景」, LiveKit 出向轨 as unverified, GSAP free ≠ OSI, 「三类」→四类), plus the Lottie version note. Do not otherwise restructure that document.

- [ ] **Step 4: Mainline ritual**

```bash
git fetch origin
git merge origin/weifu-refactor
./gradlew :app:testChatDebugUnitTest :feature-camera-fx:testDebugUnitTest
git checkout weifu-refactor   # mainline worktree, never a dirty shared checkout
git merge --ff-only claude/lottie-device-accept || git merge --no-ff claude/lottie-device-accept
git push origin claude/lottie-device-accept weifu-refactor
git rev-list --count origin/weifu-refactor..claude/lottie-device-accept
git rev-list --count claude/lottie-device-accept..origin/weifu-refactor
```
Expected: both counts 0. Never stage another session's uncommitted `app/build.gradle` in the mainline worktree — check its diff hash before and after.

- [ ] **Step 5: Report honestly**

State: what compiles, which unit tests pass, which pre-existing failures were seen with their output, that the four scenes and five backends have not been viewed on a device (coordinator installs on V2279A), and the measured APK sizes.

---

## Not in this plan (deliberate)

- Spine, PAG/libpag, Effekseer, DragonBones: need editor licensing or an NDK backend; recorded in the design as excluded, not implemented.
- Camera output composition (`OverlayEffect` / `SurfaceProcessor`) and LiveKit outgoing video track work: separate project.
- fps/memory measurement harness: the Lab shows eyeball differences only.
