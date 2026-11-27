# OSS competitive landscape brief — mobile AI-companion ("her")

**Research date:** 2026-11-27 (web research current as of 2026-11-28). Builds on, and does not repeat, the 2026-11-24 note `ai-vtuber-research-airi-ollmv-20261124.md` (AIRI + Open-LLM-VTuber source-level read). This brief covers: what changed since that note, the Live2D "widget" interactivity ecosystem, newer 2025–2026 companion projects, and the custom-avatar production-pipeline tooling landscape. All counts point-in-time; web-sourced claims are marked when secondary.

**Framing bar:** "we can only do better than these, or there's no point." Each entry ends with the concrete gap we can beat.

---

## 1. AIRI (moeru-ai/airi) — delta since 2026-11-24

Repo: https://github.com/moeru-ai/airi · ~51.8k stars, 5.2k forks, 127 open issues, 175 open PRs (crawl ~2026-11). Releases page: https://github.com/moeru-ai/airi/releases · versions index: https://airi.moeru.ai/docs/en/docs/overview/versions

### Release cadence and direction (Nov 2026)

- v0.15.1 (Nov 4) → v0.15.2 (Nov 9) → v0.15.3/0.15.4 (Nov 12) → v0.15.5 pre-release (Nov 14). Still ~weekly shipping. No December release found as of 2026-11-28.
- **Voice calls landed in v0.14.0 (2026-10-29)**: started from the composer "+" menu, continuous listening with each utterance sent immediately, a "Use microphone" kill-switch that fully closes the mic and blocks voice calls, plus fixes for three cases where AIRI opened the mic on its own. Design ADR: `docs/ai/adr/2026-10-29-web-voice-composer-and-call.md`. (https://whatsnew.fyi/product/airi/releases/v0.14.0)
- **v0.15.0 (2026-11-03)**: phone-friendly **Live2D editor** (model stays on screen; Model/Placement/Motion/Look/Display bottom tabs; drag-to-move; modified-value indicator dots), Live2D touch reactions, **separate MAGIC motion profiles for idle vs speaking**, foldable-phone support, built-in character renamed AIRI → Iru. (https://whatsnew.fyi/product/airi/releases/v0.15.0, https://newreleases.io/project/github/moeru-ai/airi/release/v0.15.0)
- **v0.15.2**: 30s voice-input timeout and 10s speculative-rephrasing cutoff; 512 MiB display-model uploads with progress; per-device sync settings; new `@proj-airi/extension-protocol` package; **voice plugin `onOutput`/`output` audio side so plugins can observe AIRI's own speech** (self-echo awareness as a plugin contract). (https://github.com/moeru-ai/airi/releases)
- **v0.15.5**: PostHog analytics alongside own server (chat text never recorded, replay hides text); dev-facing `core-agent` `SpeechInputPorts.transcriber` signature change. Related ADR `server/docs/ai/adr/2026-11-09-product-analytics-posthog.md`.
- Recipes (skills) + long-term memories moved from one `localStorage` blob into **IndexedDB, one record per entry** — confirms the memory ADR direction from the 11-24 note is landing in shipped code, not just design.
- They dogfood their own agent-skill system in-repo: `.agents/skills` directory; a third-party index lists **31 agent skills** for the repo (https://skillsmp.com/creators/moeru-ai). Example: an `enforce-rules-for-render-loops` skill and a `release-note-writer` skill routing releases (commit 2026-11-03). Their "skill" pattern = name+description listed to the model, instructions revealed on invoke (see 11-24 note ADR) — now visibly applied to their own dev workflow.

### The decisive mobile weakness (new, hard signal)

- **AIRI's own DevLog 2026-03-23 "Mobile performance and game engine exploration"** (https://airi.moeru.ai/docs/en/blog/DevLog-2026.03.23/): `stage-pocket` is "essentially a direct copy of the main Vue.js application packaged using Capacitor." Stated problems: **high memory rendering Live2D/VRM, frequent crashes on iOS and low-end Android, degradation after long runtime**; suspected WebView memory leaks and weak Three.js on mobile processors. They are prototyping a **Unity Android renderer for Live2D** as the fix.
- Independent third-party measurement (unofficial, treat as data point not benchmark): https://github.com/pocket-nexus/pocket-live/blob/main/REPORT.md — AIRI Live2D default ≈ **1742 MB RSS / ~90.7% CPU**; AIRI VRM mode ≈ **2184 MB RSS**; a single-process alternative ≈ 118 MB RSS / ~3.9% CPU. Consistent with the DevLog's self-report.
- Known open issues include Discord plugin message drops after cold start ("no consumer registered for event delivery") (https://archestack.com/best-of-ai/projects/airi/) and Android Live2D load failures in older stage-pocket builds (issue #1552).

**What's genuinely good:** release velocity; voice-call turn semantics in the web composer; skills/automations/memory substrate converging into storage primitives; a phone-usable Live2D model editor (first of its kind in this space); plugin contract for observing agent speech output.

**Gap we beat:** their mobile surface is a Capacitor WebView with 1.7–2.2 GB RSS and crashes on low-end devices — they are actively exploring re-platforming the renderer to Unity. A native Android (Compose + Cubism Native GL) renderer with tens-of-MB avatar budget, stable long-session performance, and a production call page is a category they cannot reach from their current stack.

---

## 2. Open-LLM-VTuber — community & trajectory angle (source-read runs in parallel)

Repo: https://github.com/Open-LLM-VTuber/Open-LLM-VTuber (~15k stars, 1.8k forks, **112 open issues, 51 open PRs** at crawl). Docs: https://docs.llmvtuber.com/en/docs/development-guide/overview/

- **Default branch last pushed 2026-05-15** (from 11-24 note) — still effectively frozen. The project has declared **v2.0 as a complete rewrite**, in "early discussion and planning"; maintainers ask people **not to open new issues/feature PRs against v1**; v1 gets bug fixes only. v2 discussion/contribution moved to **Zulip** with weekly meetings. Five months into that notice with no v2 code visible = planning stall risk. (https://docs.llmvtuber.com/en/docs/development-guide/overview/)
- Community signals corroborate the classic complaints (from README warnings + issue tracker, https://github.com/Open-LLM-VTuber/Open-LLM-VTuber/issues): "NOT easy to install," "features unstable, code messy," disproportionate **Windows dependency pain**, recurring FAQ around `openai_compatible_llm` base-URL/key configuration, broken lip sync from TTS encoding (`pcm_f32le`, issue #412), opaque "Error in conversation chain" backend failures (#384), dependency pinning fragility (#411), and an open **"不更新了吗?" ("is this no longer being updated?")** issue (#399). Firsthand Reddit threads did not surface in this search; these are README/issue-tracker signals.
- **Licensing churn ahead:** README states backend stays MIT for v1.2.0 but is expected to move to a custom **"Open-LLM-VTuber License 1.0" around v1.3/v1.4** (fork Warashi documents its bundled frontend under "Open-LLM-VTuber License 1.0 (Apache-2.0 + extra conditions)"). Watch this: it will push some users and forkers to alternatives.
- **The last rewrite was disruptive:** v1.0 was "basically a new app," direct upgrades impossible, plus CORS breakage when hosting the web frontend separately (https://github.com/Open-LLM-VTuber/Open-LLM-VTuber/releases).
- **Fork ecosystem is where the activity moved:**
  - **Warashi** (https://github.com/inni918/warashi) — OLLV fork with Live2D avatar, voice chat, **long-term memory and proactive conversation** (the two things OLLV README says are removed/pending).
  - **Free-LLM-VTuber** (https://github.com/youyouhe/Free-LLM-VTuber) — OLLV-lineage rebrand, adds visual perception framing.
  - Various `Open-LLM-VTuber-x` forks. The Live2D 2D lineage in this space is mostly OLLV derivatives.

**What's genuinely good (unchanged from 11-24 note):** modular provider factories (ASR/VAD/LLM/TTS), sentence-level streaming with ordered playback, explicit interrupt contract, transparent always-on-top desktop pet mode.

**Gap we beat:** frozen core + stalled rewrite + install pain + license drift. A maintained product with one-command onboarding on Android, and a Live2D interactivity layer richer than their emotionMap, is straightforwardly better. Their community's unmet wants (memory, proactive speech) validate our roadmap items.

---

## 3. Live2D widget ecosystem — what makes the character feel alive

Prior reference points: live2d-widget (steax-e8), PLabe Shirase (live2d-widget model), Live2D Cubism sample models.

### Interaction grammar that makes it "alive" (observed patterns)

- **Native hit areas → Tap motion groups.** Cubism `.model3.json` `HitAreas` map artmesh regions (Head/Body/Legs/Arms) to `Tap<Area>` motion groups. dsh-pet's manifest (`pet.json` v2) formalizes this: a tap landing on a listed hit area plays `TapBody`, then returns to the current phase's motion group (https://github.com/zhu1090093659/dsh-pet/blob/main/README.md). Official Cubism sample models ship only `Idle` + `TapBody` — the interaction budget is small unless you author more.
- **Hit-area authoring semantics** (Live2DViewerEX editor manual, https://live2d.pavostudio.com/doc/en-us/exstudio/live2d-editor/): per-hit-area actions for **Click / Press / Release / Enter / Exit** (hover!), Sorting for overlapping regions ("highest order receives events"), "Clickable When Invisible." That event set is a good spec for our touch layer: tap, press-and-hold, drag-enter/exit give more vocabulary than most companion apps use.
- **Graceful fallback when models lack HitAreas:** dsh-live2d-pets falls back to **five AABB "spatial tap" rectangles** (head/legs/arms/body), with normalized-coordinate override and a developer "Show tap zones" visualization; part-specific reactions give each part its own line + motion (https://github.com/cyanfish-x/dsh-live2d-pets). Practical trick for us: user-supplied models without hit areas still get believable part-aware touch.
- **Idle/expression orchestration** (TonyNa-code/desktop-pet docs, https://github.com/TonyNa-code/desktop-pet/blob/main/docs/live2d.md): automatic mode = pointer tracking (eye follow) + local touch reactions + **idle motions every ~45–60 s**; a click-expression mode cycles native expressions; requires official Cubism Core 5.2. Confirms the alive-ness recipe: gaze follow + periodic idle motion + expression cycling + tap response, all model-asset-gated.
- **State mirroring as companion affordance:** dsh-live2d-pets is built as an "agent state mirror" — the pet visibly reflects what the coding agent is doing (phase→motion-group mapping, expressions per state), plus petting/feeding interactions with an **affinity score** (dsh-pet). This is the cheapest proven bridge between agent runtime state and avatar behavior, and it matches our explicit-event-contract recommendation from the 11-24 note.

### Mobile / Compose equivalents

- **No Compose-native Live2D library exists** (search 2026-11). The only supported path: Cubism Core + Framework (C++/OpenGL ES) embedded in AndroidView/SurfaceView. Official samples: https://github.com/Live2D/CubismNativeSamples (prebuilt ARM64/x86 static+shared libs, standalone OpenGL sample apps).
- Closest architectural prior art is Flutter wrappers over Cubism Native: **live2d_flutter** (https://pub.dev/packages/live2d_flutter — transparent background, reactive `ValueNotifier` controller, async lifecycle `whenAttached`, multi-view, **built-in touch + eye tracking**, runtime motion speed, pausable render loop) and **flutter_live2d** (https://pub.dev/packages/flutter_live2d — explicitly Cubism Native + OpenGL ES 2). A clean Compose wrapper with typed expression/motion/gaze/mouth commands would itself be a differentiator (and potentially open-sourceable).
- **Cubism Native platform facts** (https://docs.live2d.com/en/cubism-sdk-manual/platform/, updated 2026-09-29): Android 64-bit only; OpenGL yes, Vulkan no (Windows/Linux only); latest **Cubism 5 SDK for Native R5 (2026-04-02)** (https://www.live2d.com/en/sdk/download/native/).
- **The key mobile perf lever** (https://docs.live2d.com/en/cubism-sdk-manual/ow-sdk-mask-premake/): mask preprocessing — draw all mask shapes into a single mask buffer; per-masked-drawable render-target switching "may cause slow rendering speeds on smartphones." Multiple mask buffers trade memory. This is table stakes our renderer should already exploit.

---

## 4. Newer (2025–2026) companion/vtuber projects worth knowing

- **AILIS** (https://github.com/haowenGuo/AILIS) — MIT, Windows desktop: resident VRM character + chat/control panel, voice, long-term memory, **computer use**, and an "**evaluated general Agent Harness**" for long-horizon tasks (mentions 272k-token context with compaction). The strongest agent-harness framing found in this space. Lacks: mobile, production avatar pipeline, effects/stickers.
- **xiaokeai** (https://github.com/liupig/xiaokeai, Apache-2.0) — local-first 3D companion + transparent desktop pet; imports **VRM, MMD/PMX, GLB, and Live2D/Cubism** in one renderer; **SillyTavern character cards & lorebooks** compatibility; voice, lip-sync, motion/dance, camera, tarot, memory; CN/EN/JA/KO; **actively released Sep–Nov 2026**. Notable: format breadth + character-card ecosystem interop is a user-retention play we should copy at the persona-schema level (SillyTavern card format is the de facto lingua franca). Lacks: mobile, voice-call quality, creation pipeline.
- **open-vrm-companion** (https://github.com/trisanap/open-vrm-companion) — VRM companion with an "agentic research mode" (multi-step reasoning, Tavily web-search injection, file read/write, PDF analysis, per-session history), any OpenAI-compatible backend. **Archived 2026-09-24** — reference codebase only.
- **Warashi** (above) — proves demand for memory + proactive conversation on the Live2D/OLLV lineage.
- **pocket-nexus/pocket-live** — third-party experiment measuring/optimizing AIRI-style stage performance on mobile; useful comparison baseline (118 MB / 3.9% CPU single-process alternative) and evidence that AIRI's mobile numbers motivate exactly our native-renderer bet.

**Pattern:** the OSS field splits into (a) Live2D 2D lineages derived from OLLV, (b) 3D VRM/MMD/GLB desktop pets, (c) agent-harness-first projects with a decorative avatar. **Nobody combines all three of: strong agent tool architecture + tight interactive avatar UI + custom-avatar creation pipeline — on mobile.** That intersection is the product.

---

## 5. Production pipeline for custom animated avatars

### PSD → Live2D auto-rigging (the big 2026 development)

- **psd2live** (https://github.com/tsunehimatoi/psd2live) — created **2026-09-03**, already **1,193 stars / 128 forks**, pushed 2026-11-26 (very hot; GPL-3.0). Turns layered PSDs into **editable Cubism Modeler 5 `.cmo3` projects + full `.moc3` runtime families** (`.model3.json`, `cdi3.json`, `physics3.json`, `idle.motion3.json`, texture atlases): automatic rigging **derived from PSD layer names**, mesh/deformer generation, physics, animation, "three-stage geometric integrity gates," hair/face shadow layer generation. Ships a desktop app (Windows ARM64 portable; own PSD2Live preview runtime where official Cubism Core native preview is unavailable). Crucially it exposes an **MCP server** (project/layer/parameter reads, object & keyform editing, parameter CRUD, PNG views, resumable tasks) — i.e. designed so coding agents (Claude Code/Codex style) drive rigging end-to-end. Sources: repo README, https://hysenlabs.com/projects/tsunehimatoi-psd2live.
  - Implications for us: (1) auto-rig quality is now credible enough that "layered PSD in → editable Cubism project out" is a pipeline stage, not manual art; (2) GPL-3.0 means study the approach, don't link it; (3) the MCP surface is the template for our own avatar-lab tooling contract.
- **zeikar/iki** (MIT) — an open "Live2D alternative": a web 2D puppet engine with **AI rigging from layers, one finished picture, or AI-drawn parts**, open `.iki` model format; geometry measured from layer pixels (runs in-browser, no model download); ships a Claude Code/Codex plugin. (https://github.com/zeikar/iki — verify PSD import / Live2D compat directly; third-party summary was found via mirror.) Strategically interesting as an escape hatch from Live2D's proprietary Cubism Core licensing, but ecosystem/motion-asset maturity is far behind Cubism.

### AIGC → rigged 3D (VRM/GLB)

- **Tripo Auto Rig** (https://developers.tripo3d.com/en/docs/animations-rig, https://developers.tripo3d.com/en/models/rig, https://www.tripo3d.ai/features/ai-auto-rigging): one API call to skeleton a generated character; bone presets include Mixamo, ActorCore, UE5 Mannequin, Unity Humanoid-compatible, and **VRM 1.0 Humanoid**; humanoid-specialized model `v3.0-20260909` recommended; **90+ animation presets** retarget onto the rig; rigged `.glb` out. Reported ~¥1.75 per auto-rig (docs pricing; verify currency/plan). A text/image → VRM-rigged avatar pipeline is a few API calls today.
- **Meshy Auto Rigging** (https://docs.meshy.ai/en/webapp/guides/3d-model/rigging): rigs generated characters; **>300k faces must be remeshed first**; humanoid rigs ship with walk/run. No VRM export mentioned.
- Unity-side integration plugins exist for both (e.g. https://muddyterrain.com/docs/genai-modelgenerator/getting-started/: Meshy/Tripo generate → auto-rig (experimental) → retarget a preset animation).
- Mobile trade-off (from Tripo/VRM docs + general knowledge, flag as judgment): VRM-1.0-rigged GLBs run acceptably on mid-range Android via three-vrm/UniVRM-class runtimes, but AIGC meshes are heavy (remesh/decimation step mandatory) and auto-rig quality on stylized anime geometry is visibly worse than hand-tuned VRM rigs. Live2D remains cheaper for stylized faces; VRM wins for full-body/dance/danceable avatars.

### Effects / stickers / motion overlays

- **Rive** (https://rive.app/features, runtime docs https://github.com/rive-app/help-center) — state machines as the interactive primitive: runtime-driven inputs, 1D animation blending, **events emitted back to app code**, dynamic text, tiny `.riv` files, first-class Android/Flutter/iOS/Web runtimes. Aug 31 2026 runtime update: Android gains Semantics support; **state machine input/text APIs are deprecated in favor of data binding** (https://community.rive.app/c/announcements/rive-runtime-updates-august-31-2026) — i.e. the platform is converging on exactly our pattern: agent state drives a declarative animation graph. Best-fit layer for stickers, reaction effects, and UI animation; not for the character body itself.

### Live2D licensing constraints (pipeline-relevant)

- Cubism Editor is paid (individual subscriptions); **SDK downloads free but Cubism Core is proprietary binary** under the Live2D Proprietary Software License + Open Software License, with separate Individual vs Enterprise download paths (https://www.live2d.com/en/sdk/about/, https://www.live2d.com/en/sdk/download/native/). Commercial model redistribution has per-model terms (sample models carry their own licenses; OLLV's LICENSE carves out its Live2D samples). Any user-generated-content avatar store must respect Cubism Editor EULA limits on automated/batch use — legal review before mirroring psd2live's approach in production.

---

## 6. Where we must be better to exist (synthesis)

The OSS field proves the demand shape (alive avatar + real conversation + user ownership) and simultaneously shows the ceiling of what OSS ships: AIRI has breadth (Live2D+VRM+skills+memory+voice calls) but its mobile path is a Capacitor WebView burning 1.7–2.2 GB with crash reports, and they are exploring a Unity rewrite just to get there; Open-LLM-VTuber owns the interrupt/streaming grammar but its core is frozen mid-"v2 rewrite" with install pain and license churn; the desktop pets (xiaokeai, AILIS, Warashi) each hold one piece — format breadth, agent harness, memory — on no platform we ship on. Our defensible line is: **native-Android rendering economics** (Cubism Native GL with mask-buffer preprocessing, tens-of-MB avatar budget, stable long sessions — beating AIRI by an order of magnitude, which their own devlog admits they cannot reach), **a richer interactivity contract than emotionMap/tap-area toys** (state-driven expression/motion/gaze grammar + press/hover/exit events + fallback spatial tap zones for user models), and **the production pipeline nobody else closes**: PSD→Live2D (psd2live has proven auto-rig from layer names is credible and MCP-drivable — we beat it with quality gates integrated into our wardrobe-spec pipeline) and image→VRM via Tripo-class auto-rig with decimation and retarget presets, feeding the same provider-neutral renderer commands. If a weekend hacker with AIRI + OLLV + psd2live can approximate us, we lose; the moat is per-frame cost on real mid-range devices, barge-in turn quality on the call page, and a creation pipeline a non-artist completes in minutes — all three are absent from every project surveyed.

---

## Sources (primary)

- AIRI: https://github.com/moeru-ai/airi/releases · https://airi.moeru.ai/docs/en/docs/overview/versions · https://airi.moeru.ai/docs/en/blog/DevLog-2026.03.23/ · https://whatsnew.fyi/product/airi/releases/v0.14.0 · https://whatsnew.fyi/product/airi/releases/v0.15.0 · https://whatsnew.fyi/product/airi/releases/v0.15.2 · https://newreleases.io/project/github/moeru-ai/airi/release/v0.15.0 · https://skillsmp.com/creators/moeru-ai · https://archestack.com/best-of-ai/projects/airi/ · https://github.com/moeru-ai/airi/issues/1552 · https://github.com/pocket-nexus/pocket-live/blob/main/REPORT.md
- Open-LLM-VTuber: https://docs.llmvtuber.com/en/docs/development-guide/overview/ · https://github.com/Open-LLM-VTuber/Open-LLM-VTuber/releases · https://github.com/Open-LLM-VTuber/Open-LLM-VTuber/issues · https://docs.llmvtuber.com/en/docs/faq/
- Live2D interactivity: https://github.com/zhu1090093659/dsh-pet/blob/main/README.md · https://github.com/cyanfish-x/dsh-live2d-pets · https://github.com/TonyNa-code/desktop-pet/blob/main/docs/live2d.md · https://live2d.pavostudio.com/doc/en-us/exstudio/live2d-editor/
- Live2D SDK/mobile: https://docs.live2d.com/en/cubism-sdk-manual/platform/ · https://docs.live2d.com/en/cubism-sdk-manual/ow-sdk-mask-premake/ · https://www.live2d.com/en/sdk/download/native/ · https://www.live2d.com/en/sdk/about/ · https://github.com/Live2D/CubismNativeSamples · https://pub.dev/packages/live2d_flutter · https://pub.dev/packages/flutter_live2d
- Newer projects: https://github.com/haowenGuo/AILIS · https://github.com/liupig/xiaokeai · https://github.com/trisanap/open-vrm-companion · https://github.com/inni918/warashi · https://github.com/youyouhe/Free-LLM-VTuber
- Pipeline: https://github.com/tsunehimatoi/psd2live · https://hysenlabs.com/projects/tsunehimatoi-psd2live · https://github.com/LeonaDavinci/psdtolive2d · (iki: repo referenced via third-party summary — verify) · https://developers.tripo3d.com/en/docs/animations-rig · https://developers.tripo3d.com/en/models/rig · https://www.tripo3d.ai/features/ai-auto-rigging · https://docs.meshy.ai/en/webapp/guides/3d-model/rigging · https://muddyterrain.com/docs/genai-modelgenerator/getting-started/ · https://rive.app/features · https://community.rive.app/c/announcements/rive-runtime-updates-august-31-2026

## Caveats

- Web-search summaries are secondary sources; release-note content above matches multiple independent listings but exact wording unverified against every ADR file.
- pocket-nexus/pocket-live figures are unofficial third-party measurements.
- zeikar/iki details come from a mirror/third-party summary — confirm PSD import claims directly.
- AIRI stars/issue counts are crawl-time; no December 2026 release existed as of 2026-11-28.
