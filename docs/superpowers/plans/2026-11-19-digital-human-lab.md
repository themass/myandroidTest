# Digital Human Lab UI Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a switchable Digital Human Lab UI kit on the companion call screen (P0): engine Strategy/Factory/Bridge + Simple engine + Switcher with grey placeholders for Live2D/Duix/TaoAvatar, without changing the LiveKit channel.

**Architecture:** New `:feature-dh-lab` library holds `DigitalHumanEngine` (extends `AvatarRenderer`), `DhEngineRegistry`, `DigitalHumanBridge`, and Compose Host/Switcher. `feature-companion` depends on it and, when Lab is enabled (debuggable app), replaces the call avatar slot with `DigitalHumanLabHost`. Optional engines register later; P0 only registers `SIMPLE` and reports others as unavailable with reasons.

**Tech Stack:** Kotlin, Jetpack Compose, existing `AvatarRenderer`/`AvatarCommand` in `:feature-companion`, JUnit4 + Robolectric (same as companion avatar tests).

**Spec:** [docs/superpowers/specs/2026-11-19-digital-human-lab-design.md](../specs/2026-11-19-digital-human-lab-design.md)

## Global Constraints

- Intent is compare-and-pick, not long-term triple engines in product settings.
- Do not fork `CompanionCallScreen` / LiveKit transport; only swap the avatar viewport.
- Do not wire ChatWaifu VITS/Sherpa or TaoAvatar on-device LLM into the product path.
- Lab chrome visible only when `ApplicationInfo.FLAG_DEBUGGABLE != 0` (same pattern as companion transport debug), or when SharedPreferences key `companion.dh_lab` is true.
- Default engine `DhEngineId.SIMPLE`. Persist selection in SharedPreferences `companion.dh_engine`.
- Package root for new module: `com.openapi.ks.myapp.dh`.
- Namespace for gradle module: `com.openapi.feature.dh.lab`.
- End commits with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## Review Focus

- Switching engines mid-call must `release()` the previous engine exactly once before creating the next (leak / double-GL).
- Selecting an unavailable engine must leave the current engine running and show the reason (no blank viewport).
- Non-debuggable builds with no `companion.dh_lab` pref must look exactly like today's SimpleAvatar path.
- `DigitalHumanBridge.onMouthLevel` must keep feeding Mouth commands after a switch to the new engine.
- Registry must never return a Live2D/Duix/Tao factory in P0 that throws on create — unavailable means not creatable.

---

### Task 1: `:feature-dh-lab` module + engine API + registry

**Files:**
- Create: `feature-dh-lab/build.gradle`
- Create: `feature-dh-lab/src/main/AndroidManifest.xml` (empty `<manifest />`)
- Create: `feature-dh-lab/src/main/java/com/openapi/ks/myapp/dh/DhEngineId.kt`
- Create: `feature-dh-lab/src/main/java/com/openapi/ks/myapp/dh/DigitalHumanEngine.kt`
- Create: `feature-dh-lab/src/main/java/com/openapi/ks/myapp/dh/DhEngineRegistry.kt`
- Create: `feature-dh-lab/src/main/java/com/openapi/ks/myapp/dh/DigitalHumanBridge.kt`
- Create: `feature-dh-lab/src/test/java/com/openapi/ks/myapp/dh/DhEngineRegistryTest.kt`
- Modify: `settings.gradle` — `include ':feature-dh-lab'`
- Modify: `feature-companion/build.gradle` — `implementation project(':feature-dh-lab')`

**Interfaces:**
- Consumes: `AvatarRenderer`, `AvatarCommand`, `AvatarSpec`, `AvatarCapability` from `com.openapi.ks.myapp.avatar` (companion must `api` those to dh-lab **or** dh-lab `implementation project(':feature-companion')` — **author call:** dh-lab `implementation project(':feature-companion')` is wrong (cycle). Move is heavier. **Do this:** keep engine API in `feature-dh-lab` but depend on companion via `implementation project(':feature-companion')` only if no cycle; companion already would depend on dh-lab → **cycle**. Fix: put shared avatar types dependency the other way — dh-lab does **not** depend on companion; instead duplicate is forbidden. **Author call for P0:** place Lab types **inside** `feature-companion` under package `com.openapi.ks.myapp.dh` (no new gradle module), matching "kit" without cycle. Skip `:feature-dh-lab` module; files live under `feature-companion/src/main/java/com/openapi/ks/myapp/dh/`. Spec's module split deferred to P1 when optional native engines need isolation.
- Produces: `enum class DhEngineId { SIMPLE, LIVE2D, DUIX, TAO_AVATAR }`; `data class DhEngineAvailability(val id: DhEngineId, val available: Boolean, val reason: String? = null)`; `interface DigitalHumanEngine : AvatarRenderer { val id: DhEngineId; fun createView(context: Context): View? = null; fun availability(context: Context): DhEngineAvailability }`; `object DhEngineRegistry { fun register(id: DhEngineId, factory: (Context) -> DigitalHumanEngine); fun available(context: Context): List<DhEngineAvailability>; fun create(context: Context, id: DhEngineId): DigitalHumanEngine }`; `class DigitalHumanBridge(private var engine: DigitalHumanEngine) { fun swap(engine: DigitalHumanEngine); fun onAvatarCommand(cmd: AvatarCommand); fun onMouthLevel(level: Float); fun onRemotePcm(pcm16leMono16k: ByteArray) }`

- [ ] **Step 1: Write failing tests** in `feature-companion/src/test/java/com/openapi/ks/myapp/dh/DhEngineRegistryTest.kt`

```kotlin
@Test fun registered_simple_is_available_and_creatable()
@Test fun unregistered_engines_report_not_built_in_reason()
@Test fun create_unregistered_throws()
@Test fun bridge_forwards_mouth_to_current_engine()
@Test fun bridge_swap_releases_old_and_forwards_to_new()
```

Assertions: unavailable reason contains `未编入`; swap calls `release` on old (use fake engine recording calls).

- [ ] **Step 2: Run tests — expect FAIL** (types missing)

Run: `./gradlew :feature-companion:testDebugUnitTest --tests com.openapi.ks.myapp.dh.DhEngineRegistryTest`

- [ ] **Step 3: Implement API + `SimpleDigitalHumanEngine` wrapping `SimpleAvatarRenderer`**

Files:
- `.../dh/DhEngineId.kt`, `DigitalHumanEngine.kt`, `DhEngineRegistry.kt`, `DigitalHumanBridge.kt`, `SimpleDigitalHumanEngine.kt`

`SimpleDigitalHumanEngine` delegates bind/dispatch/onTouch/release/capabilities to an inner `SimpleAvatarRenderer`; `availability` always available; `id = SIMPLE`. On registry init (static block or `DhLabBootstrap.ensureDefaults()`), register SIMPLE only; for other ids `available()` returns `available=false, reason="未编入"` without a factory.

`DigitalHumanBridge.swap`: if same instance return; else `old.release(); engine = new`.

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add feature-companion/src/main/java/com/openapi/ks/myapp/dh feature-companion/src/test/java/com/openapi/ks/myapp/dh
git commit -m "$(cat <<'EOF'
feat(dh-lab): engine registry, bridge, SimpleDigitalHumanEngine

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Compose Host + Switcher

**Files:**
- Create: `feature-companion/src/main/java/com/openapi/ks/myapp/dh/DigitalHumanLabHost.kt`
- Create: `feature-companion/src/main/java/com/openapi/ks/myapp/dh/DhLabPreferences.kt`
- Create: `feature-companion/src/test/java/com/openapi/ks/myapp/dh/DhLabPreferencesTest.kt`

**Interfaces:**
- Consumes: Task 1 types; `SimpleAvatar` composable for SIMPLE path; `AvatarSpec` / mouthLevel
- Produces: `@Composable fun DigitalHumanLabHost(spec: AvatarSpec?, mouthLevel: StateFlow<Float> or Float, modifier, labEnabled: Boolean)`; chips UI; prefs `DhLabPreferences(context).engine: DhEngineId` / `setEngine` / `labForced: Boolean`

- [ ] **Step 1: Failing test for prefs round-trip + default SIMPLE**

```kotlin
@Test fun prefs_default_simple()
@Test fun prefs_persist_engine()
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement prefs + `DigitalHumanLabHost`**

Host behavior:
- `remember` current engine from prefs; `DhEngineRegistry.create`
- bind spec when present
- `LaunchedEffect(mouthLevel)` → bridge.onMouthLevel
- Switcher chips for all `DhEngineId.entries`; disabled chips for `!available`; on click available → bridge.swap(create); prefs.setEngine; on click unavailable → snackbar/status text with reason (local `var status`)
- Watermark Text `DH Lab · 评测` bottom-end 10.sp alpha 0.45
- For SIMPLE: reuse `SimpleAvatar(renderer = engine as/delegating, mouthLevel=...)`
- For engines with `createView != null`: `AndroidView(factory = { engine.createView(it)!! })` (P0 no such engine)
- Loading: when swapping, brief `CircularProgressIndicator` until bind returns

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit** `feat(dh-lab): Lab Host + Switcher + prefs`

---

### Task 3: Wire Lab into companion call route

**Files:**
- Modify: `feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt` (avatarSlot region ~266–286)
- Create: `feature-companion/src/main/java/com/openapi/ks/myapp/dh/DhLabGate.kt`
- Test: `feature-companion/src/test/java/com/openapi/ks/myapp/dh/DhLabGateTest.kt`

**Interfaces:**
- Consumes: Host, prefs, `ApplicationInfo.FLAG_DEBUGGABLE`
- Produces: `object DhLabGate { fun isEnabled(context: Context): Boolean }` — true if debuggable OR prefs.labForced

- [ ] **Step 1: Test gate true when forced pref; false when neither**

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement gate; in CompanionRoute when `DhLabGate.isEnabled` use `DigitalHumanLabHost` as avatarSlot (full size); else keep existing SimpleAvatar path unchanged**

Also call `DhLabBootstrap.ensureDefaults()` once (register SIMPLE).

- [ ] **Step 4: Unit tests PASS; compile `:feature-companion:compileDebugKotlin`**

- [ ] **Step 5: Commit** `feat(dh-lab): wire Lab host into call avatar slot when enabled`

---

### Task 4: Spec note + merge hygiene

**Files:**
- Modify: `docs/superpowers/specs/2026-11-19-digital-human-lab-design.md` — add under §6: "P0 落地：类型放在 `feature-companion/.../dh`，避免与 companion 循环依赖；`:feature-dh-lab` / `dh-engine-*` 延到 P1 native 隔离时再拆。"

- [ ] **Step 1: Edit spec note**
- [ ] **Step 2: Commit** `docs(dh-lab): note P0 package lives under feature-companion`
- [ ] **Step 3: Merge branch into `weifu-refactor` via `git -C` worktree rules; push both (standing project instruction)

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| Strategy/Factory/Bridge | 1 |
| Switcher + watermark + unavailable reason | 2 |
| Lab gate / default Simple / persist | 2–3 |
| Wire call viewport only | 3 |
| Optional modules Live2D/Duix/Tao | deferred P1+ (placeholders via unavailable) |
| No dual TTS | N/A P0 |
| Module `:feature-dh-lab` | deferred with spec note Task 4 |
