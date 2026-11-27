# Open-LLM-VTuber — Deep Source Read (v1.2.1)

Repo: `/Users/gqli/work/Open-LLM-VTuber` (local checkout, HEAD `992309c`, pyproject version 1.2.1, 913 commits, ~12.4k LOC of core Python under `src/open_llm_vtuber/`).
Read date: 2026-11-27/28. All paths below are absolute-relative to that repo root.

**Important scope correction:** this checkout has **no `plugins/` tree** — the old plugin/provider directory was refactored into `src/open_llm_vtuber/{tts,asr,vad,agent,translate,mcpp,live}`. There is no "realtime API" agent (OpenAI Realtime-style) and no `hitokoto` plugin in this version (`grep -r hitokoto` finds nothing). The Qt frontend is gone too: the only frontends are the **WebUI (git submodule `frontend/` → Open-LLM-VTuber-Web, prebuilt bundle)** and a tiny vanilla-JS `web_tool/`. All citations reflect what actually exists here.

---

## 0. Shape of the system (one paragraph)

A single **FastAPI/Uvicorn process** (`run_server.py` → `src/open_llm_vtuber/server.py:56`) owns everything: one WebSocket endpoint `/client-ws` for the avatar client, static mounts for Live2D models/backgrounds/avatars/frontend, a `/proxy-ws` fan-in for external input sources, and a standalone `/asr` + `/tts-ws` web tool. Each connection gets a `ServiceContext` (`service_context.py:41`) that *references shared* engine instances (ASR/TTS/VAD/agent) loaded once at startup, plus per-session MCP plumbing. One conversation turn = one `asyncio.Task` that streams: user audio/text → ASR → agent (`chat()` async generator of tokens) → decorator pipeline (sentence splitter → expression extractor → display processor → tts filter) → per-sentence TTS into a file → base64 WAV + volumes + actions pushed down the same WebSocket. Interruption = client sends `interrupt-signal` → server cancels the task and injects `"[Interrupted by user]"` into agent memory. There is no message bus; the "event system" is literally the asyncio task + generator + a small `asyncio.Event` request/response correlator (`message_handler.py:7`).

---

## 1. Agent / skill / tool design

### 1.1 Layering

Three separate abstraction layers, each with a factory:

1. **AgentInterface** (`agent/agents/agent_interface.py:9`) — three methods only: `async chat(BaseInput) -> AsyncIterator[BaseOutput]`, `handle_interrupt(heard_response)`, `set_memory_from_history(conf_uid, history_uid)`. Output is either `SentenceOutput` (display_text, tts_text, actions) or `AudioOutput` (audio_path, display_text, transcript, actions) (`agent/output_types.py:46,67`).
2. **StatelessLLMInterface** (`agent/stateless_llm/stateless_llm_interface.py:5`) — `chat_completion(messages, system, tools) -> AsyncIterator[str]` (or event dicts for Claude). Implementations: OpenAI-compatible, Claude, Ollama, llama.cpp, chatml-template wrapper (`stateless_llm_factory.py:13`).
3. **AgentFactory** (`agent/agent_factory.py:15`) — `conversation_agent_choice ∈ {basic_memory_agent, mem0_agent, hume_ai_agent, letta_agent}` (config literal at `config_manager/agent.py:202`).

**Dead code flag:** `agent/agents/mem0_llm.py` is a **0-byte file** (last touched `d2a82d9 "❓ Updated mem0 integration (untested)"`). Selecting `mem0_agent` in config raises `ImportError` at `agent_factory.py:96`. The memory story is effectively: vanilla list-in-RAM (BasicMemoryAgent), Letta server (external), Hume EVI (external, speech-to-speech).

### 1.2 The output transformer pipeline (the genuinely clever bit)

`BasicMemoryAgent._chat_function_factory` (`agent/agents/basic_memory_agent.py:581-662`) composes four decorators, innermost = raw token stream:

```python
@tts_filter(self._tts_preprocessor_config)   # transforms.py:157  -> SentenceOutput, strips emoji/bracketed text from TTS text, mutes <think> content
@display_processor()                          # transforms.py:103 -> DisplayText (+ "(" / ")" markers around <think>)
@actions_extractor(self._live2d_model)        # transforms.py:58  -> Actions.expressions from [keyword] scan
@sentence_divider(faster_first_response=…,    # transforms.py:12  -> tokens -> SentenceWithTags
                  segment_method=…, valid_tags=["think"])
async def chat_with_memory(input_data): ...
```

The same four decorators are re-applied in `LettaAgent.__init__` (`agents/letta_agent.py:43-53`) — the presentation pipeline is genuinely reusable across agents because it operates only on the token stream.

### 1.3 Tool calling = MCP only, with three execution modes

There is **no first-class local tool registry / decorator contract**. All tools come from MCP stdio servers. The mechanism:

- **Registration/config:** `mcp_servers.json` (repo root) — `{server: {command, args, env, cwd, timeout}}`; loaded by `ServerRegistry` (`mcpp/server_registry.py:19`, default path `:13`), with runtime probes for `npx`/`uvx`/`node` (`:43`). Per-character enablement: `agent_settings.basic_memory_agent.use_mcpp` + `mcp_enabled_servers: ["time", "ddg-search"]` (`config_manager/agent.py:34-35`, `conf.yaml`).
- **Tool discovery/format:** `ToolAdapter.get_tools` (`mcpp/tool_adapter.py:219`) connects (lazily, via `MCPClient._ensure_server_running_and_get_session`, `mcpp/mcp_client.py:43`) → `list_tools` → builds **three artifacts**: a human-readable prompt string (`:85`), an OpenAI function-schema list and a Claude input_schema list (`:137-217`). Stored in `ToolManager` (`mcpp/tool_manager.py:7`).
- **Execution:** `ToolExecutor.execute_tools` (`mcpp/tool_executor.py:155`) is an async generator that yields `{"type":"tool_call_status", ...}` events (running/completed/error, incl. `browser_view` for the stagehand server, `:281`) which the conversation layer forwards straight to the UI (`conversations/single_conversation.py:93-101`), then a final `{"type":"final_tool_results"}` fed back into the LLM messages.
- **Mode selection per LLM class** (`basic_memory_agent.py:606-643`): `isinstance(self._llm, ClaudeAsyncLLM)` → Claude native tool loop (`:290`); `OpenAICompatibleAsyncLLM` → OpenAI tool loop (`:403`); **fallback "prompt mode"**: if the API errors with "does not support tools" (`stateless_llm/openai_compatible_llm.py:220-226` yields sentinel `"__API_NOT_SUPPORT_TOOLS__"`), the agent switches to injecting the MCP tool catalog into the system prompt and scraping a JSON tool call out of the text stream via `StreamJSONDetector` (`basic_memory_agent.py:438-458`, `mcpp/json_detector.py:6`). This degradation path for weak/local models is a nice pattern.
- **Note:** `construct_system_prompt` explicitly *skips* the `mcp_prompt` tool prompt (`service_context.py:461-462`); the MCP catalog prompt is only appended in prompt mode (`basic_memory_agent.py:415-419`).

### 1.4 "Skill/extension" story for a developer

- **New engine (TTS/ASR/VAD/translator/LLM/agent):** subclass the interface, add a branch in the corresponding factory (`tts/tts_factory.py`, `asr/asr_factory.py`, `agent/stateless_llm_factory.py`, `agent/agent_factory.py`), add a pydantic config class in `config_manager/` (they carry bilingual `DESCRIPTIONS` used by the settings UI, e.g. `config_manager/tts.py`), and list the choice in the `Literal[...]` (`config_manager/agent.py:17-30, 202-204`). Fully documented in `CLAUDE.md` ("Adding New Engines"). It's copy-paste factory dispatch — no entry points, no dynamic discovery.
- **New tool:** drop a server into `mcp_servers.json` + name it in `mcp_enabled_servers`. Zero Python. That's the whole contract.
- **New "capability prompt":** drop a txt file in `prompts/utils/`, reference it in `system_config.tool_prompts` (`config_manager/system.py:14`); `construct_system_prompt` (`service_context.py:435-469`) concatenates all of them onto the persona prompt at agent init, with `[<insert_emomap_keys>]` substituted from the Live2D model (`:456-459`). Note the hardcoded skip-list (`group_conversation_prompt`, `proactive_speak_prompt`, `mcp_prompt`) — this is prompt-plumbing, not a plugin system.
- **No versioned API, no sandbox, no permissions, no tool allow-list** beyond "enabled server names".

---

## 2. Core architecture & flow

### 2.1 Startup

`run_server.py:run()` → upgrade manager syncs `conf.yaml` against templates → `validate_config(read_yaml("conf.yaml"))` (env-var substitution `${VAR}` happens in `config_manager/utils.py:39-44`) → `WebSocketServer(config)` builds the FastAPI app (`server.py:74-149`) → `await server.initialize()` loads the **default ServiceContext** (loads engines once: live2d info, ASR, TTS, VAD, MCP, agent — `service_context.py:248-311`) → uvicorn. Default port **12393**.

### 2.2 Per-connection

`routes.py:29 /client-ws` → `WebSocketHandler.handle_new_connection` (`websocket_handler.py:100`) creates a session `ServiceContext` via `load_cache` (`:178-202`), which **shares by reference** `live2d_model, asr_engine, tts_engine, vad_engine, agent_engine` from the default cache and only deep-copies config. Consequences:
- Two clients chatting simultaneously **share the same agent memory object** (multi-persona only works if each client does `switch-config`, which rebuilds *that session's* agent — `service_context.py:471-558`).
- MCP client/tool executor are per-session (`service_context.py:156-176`).
- Init handshake: server pushes `set-model-and-conf` (model_info + conf) and `control:start-mic` (`websocket_handler.py:149-176`).

### 2.3 Message routing (the "event bus")

- Client→server: JSON with `type`; a static dict `_message_handlers` maps type→handler (`websocket_handler.py:76-98`). Types: `mic-audio-data`, `mic-audio-end`, `raw-audio-data`, `text-input`, `ai-speak-signal`, `interrupt-signal`, `audio-play-start`, `switch-config`, `fetch-*`, history CRUD, `heartbeat`, `add-client-to-group`.
- Server→client: `set-model-and-conf`, `control` (start-mic / mic-audio-end / interrupt / conversation-chain-start / conversation-chain-end), `full-text`, `user-input-transcription`, `audio` (the main payload), `backend-synth-complete`, `frontend-playback-complete` (reverse), `tool_call_status`, `force-new-message`, `history-*`, `group-update`, `error`.
- There is **no queue** on the main path — one in-flight conversation task per client (`current_conversation_tasks` dict, `websocket_handler.py:69`), and a tiny request/response correlator `MessageHandler` using `asyncio.Event` keyed by `(type, request_id)` (`message_handler.py:16-54`) — used for `frontend-playback-complete` backpressure (`conversation_utils.py:173-179`).
- The **only real queue** is `ProxyMessageQueue` (`proxy_message_queue.py:7`) for the `/proxy-ws` fan-in (external sources like Bilibili danmaku): 0.1s polling consumer, gated on `conversation_active` so one logical client processes one message at a time.

### 2.4 One conversation turn (single user)

`handle_conversation_trigger` (`conversations/conversation_handler.py:19`) picks single vs group; `process_single_conversation` (`conversations/single_conversation.py:25`):

1. `send_conversation_start_signals` → `control:conversation-chain-start` + "Thinking..." (`conversation_utils.py:133`).
2. `process_user_input`: audio → `asr_engine.async_transcribe_np` (batch, whole-buffer — **not streaming ASR**), echo `user-input-transcription` (`conversation_utils.py:146-159`).
3. `context.agent_engine.chat(batch_input)` — the decorated async generator.
4. For each yielded item: `tool_call_status` dicts are forwarded to UI verbatim (`single_conversation.py:93-101`); `SentenceOutput`s go to `process_agent_output` → optional translation (`conversation_utils.py:97-100`) → `tts_manager.speak(...)`.
5. `TTSTaskManager` (`conversations/tts_manager.py:16`): assigns sequence numbers, runs TTS generation **in parallel tasks**, but a single `_process_payload_queue` sender releases payloads **in order** (`:92-110`). TTS writes a WAV file, then `prepare_audio_payload` (`utils/stream_audio.py:27`) converts it to **base64 + a per-20ms RMS `volumes` array** (`:8-24`) + `display_text` + `actions` and sends one JSON frame per sentence. File is deleted right after queueing (`tts_manager.py:162-164`).
6. End: `finalize_conversation_turn` gathers TTS tasks, sends `backend-synth-complete`, **waits for `frontend-playback-complete`** from the client (turn-level backpressure), then `force-new-message` + `conversation-chain-end` (`conversation_utils.py:162-190`).
7. Full response stored to `chat_history/<conf_uid>/<history_uid>.json` (append-read-rewrite whole JSON file per message — `chat_history_manager.py:117-137`).

### 2.5 Streaming text → lip-sync & expressions, incrementally

- **Sentence-level pipelining:** `SentenceDivider` (`utils/sentence_divider.py:301`) emits complete sentences as tokens arrive (pysbd with language detect per buffer, regex fallback, abbreviation guard — `:213-266`). `faster_first_response` splits the **first** sentence at the first comma in any of 17 comma glyphs (`:10-28, 492-507`) — TTFB optimization for audio.
- **Expressions:** `actions_extractor` runs `live2d_model.extract_emotion(sentence.text)` (`transformers.py:86`), a literal `[keyword]` scanner against the model's `emotionMap` keys (`live2d_model.py:146-172`). Result rides in the audio payload's `actions.expressions`; the frontend applies `expressions[0]` per sentence.
- **Lip-sync:** driven **client-side** from the playing audio, not from server data. The bundle wires the Live2D Cubism Web SDK's `_wavFileHandler` (RMS → mouth params; it even patches `_lastRms *= 2` for stronger motion, see §3). The server-computed `volumes` array exists in the payload (`stream_audio.py:70`) but the shipped WebUI plays base64 WAV via `Audio()` + `_wavFileHandler.start(dataURL)`.

### 2.6 Barge-in / interruption

- Client-side trigger: WebUI runs `@ricky0123/vad-web` MicVAD (silero v5 ONNX in an AudioWorklet, bundled at `frontend/libs/vad.worklet.bundle.min.js` + `silero_vad_v5.onnx`); on speech during `thinking-speaking` it calls `useInterrupt()`: stops audio + lip-sync, clears the audio task queue, sets state `interrupted`, and sends `{"type":"interrupt-signal","text":<heard-so-far>}` (bundle evidence: `useInterrupt` closure near char-offset 1365285 of `frontend/assets/main-nu7uwxNJ.js`).
- Server: `_handle_interrupt` (`websocket_handler.py:369`) cancels the conversation task and calls `agent.handle_interrupt(heard_response)` (`basic_memory_agent.py:195-223`): rewrites the last assistant memory entry to `heard + "..."`, appends `"[Interrupted by user]"` as user (or system, per `interrupt_method` llm setting); a matching system-prompt line is appended at init (`:123-124`). LLM stream sockets are closed in the `finally` of `chat_completion` (`openai_compatible_llm.py:234-240`).
- A **server-side VAD path** exists (`_handle_raw_audio_data`, `websocket_handler.py:489-511`, feeding Silero `vad/silero.py` state machine that emits `<|PAUSE|>` → `control:interrupt` to the client), but `vad_config.vad_model: null` by default (`conf.yaml`) — the acoustic barge-in loop runs in the browser, not the server. This design avoids echo feedback (headphone-free interruption is a headline feature).

### 2.7 Voice vs text vs proactive vs bilibili

Same pipeline everywhere; the difference is only the trigger message:
- **Text chat:** `text-input` (`conversation_handler.py:65`).
- **Voice chat:** client streams `mic-audio-data` into a growing numpy buffer (`websocket_handler.py:478-487`); `mic-audio-end` (manual stop or server VAD) fires ASR on the buffer. Images ride along on `mic-audio-end.images` (camera/screen/clipboard/upload, `agent/input_types.py:6-13`) → vision messages (`basic_memory_agent.py:250-266`).
- **Proactive speak:** `ai-speak-signal` injects the `proactive_speak_prompt.txt` as user input with `skip_memory/skip_history` metadata (`conversation_handler.py:35-64`).
- **Bilibili:** a separate process `scripts/run_bilibili_live.py` → `live/bilibili_live.py` (blivedm danmaku client) connects as a client to `/proxy-ws` (`bilibili_live.py:88-104`) and forwards danmaku as `text-input`; `live/live_interface.py:6` is the platform interface. `live_prompt.txt` is the streaming persona add-on.
- **Group chat:** multiple WebSocket clients join a group (`chat_group.py`); `process_group_conversation` (`conversations/group_conversation.py:30`) runs a **sequential round-robin queue** — each member AI takes a turn, `start_group_conversation` injects a "who else is here" prompt into each agent memory (`basic_memory_agent.py:677-702`, `group_conversation_prompt.txt`); audio played by one member is broadcast silently (`forwarded:true`) to others (`websocket_handler.py:559-577`).

---

## 3. UI interactivity (frontends + Live2D)

### 3.1 Frontends present

- `frontend/` = git submodule of **Open-LLM-VTuber-Web**, checked out as a *prebuilt* bundle (`index.html` + `assets/main-*.js` 1.9MB minified + css). Source repo not vendored here. React + Live2D Cubism Web + vad-web + pixi; `ipcRenderer` references indicate the same bundle powers their Electron desktop pet mode (transparent window, pet mode: bundle offsets mention `update-component-hover`, pet-mode context menu).
- `web_tool/` = plain JS page hitting `/asr` (multipart upload) and `/tts-ws` — reuses the server's loaded engines standalone (`routes.py:141-252`).
- No Qt frontend in this version.

### 3.2 Protocol & ports

Single origin: HTTP static + WebSocket on the same port (default **12393**, `conf.yaml`). Bundle defaults: `ws://127.0.0.1:12393/client-ws`, `http://127.0.0.1:12393` (`DEFAULT_WS_URL`, bundle char-offset ~705846). Endpoints: `/client-ws`, `/proxy-ws`, `/tts-ws`, `/asr`, `/live2d-models/info` (model scanner, `routes.py:96-139`), static mounts `/live2d-models /bg /avatars /cache /web-tool /` (`server.py:111-149`).

### 3.3 model_dict.json — the presentation manifest

Root `model_dict.json` is an array of entries consumed by `Live2dModel._lookup_model_info` (`live2d_model.py:84-144`), shipped *wholesale* to the client in `set-model-and-conf` (`websocket_handler.py:160-170`):

```json
{ "name":"mao_pro", "url":"/live2d-models/mao_pro/runtime/mao_pro.model3.json",
  "kScale":0.5, "idleMotionGroupName":"Idle",
  "emotionMap": {"neutral":0, "anger":2, "joy":3, ...},   // label → expression INDEX
  "tapMotions": {"HitAreaHead": {"":1}, "HitAreaBody": {"":1}} }
```

The actual model at `live2d-models/mao_pro/runtime/mao_pro.model3.json` defines 8 expressions (`exp_01..exp_08`), motion groups (`Idle`, unnamed `""`), `HitAreas: HitAreaHead/HitAreaBody`, and a `Groups → LipSync → ParamA` binding. Note `emotionMap` values are **indices into the model3.json Expressions array** — the frontend resolves index→name via `getExpressionName(index)` then `setExpression(name)` (bundle ~1332730, `useLive2DExpression`).

### 3.4 The exact agent-output → visual-state chain

1. System prompt tells the LLM to sprinkle `[joy]`-style keywords: `prompts/utils/live2d_expression_prompt.txt`, with the legal vocabulary spliced from `model_dict.json` (`service_context.py:456-459` via `live2d_model.emo_str`, `live2d_model.py:51`).
2. Per sentence, `actions_extractor` scans for `[key]` occurrences → `Actions.expressions=[3,1,...]` (`transformers.py:80-89`, `live2d_model.py:146-172`).
3. Payload frame `{"type":"audio", "audio":<b64wav>, "volumes":[...], "slice_length":20, "display_text":{...}, "actions":{"expressions":[...]}}` (`utils/stream_audio.py:72-80`).
4. Frontend `case "audio"` (bundle ~1859955): if aiState is `interrupted|listening`, the sentence is **dropped** ("Audio playback intercepted") — client-side staleness guard; else enqueue task.
5. In the audio task (bundle ~1361913–1363700): display text appended to chat; **`startRandomMotion("Talk", PriorityNormal)`** fires per sentence with audio; `actions.expressions[0]` → `setExpression`; `_wavFileHandler.start(dataURL)` drives mouth params while the `Audio()` element plays. Only the **first** expression in a sentence is applied.
6. Keywords themselves are **not stripped from display text** — `Live2dModel.remove_emotion_keywords` (`live2d_model.py:174`) is never called anywhere; they're only filtered from the *TTS* text via `ignore_brackets` (`utils/tts_preprocessor.py:39-44`). So the LLM's `[joy]` markers appear in subtitles as-is. (Frontend `pictures`/`sounds` action fields: defined in `output_types.Actions` but produced/consumed nowhere — grep confirms 0 hits server-side outside the dataclass, 0 in bundle.)

### 3.5 Click / gesture interactivity

All client-local, never touches the server: pointer-down/up on the canvas, tap threshold check, `model.anyhitTest(x,y)` against HitAreas, then `startTapMotion(hitAreaName, tapMotions)` merges motion groups + weights from `model_dict.tapMotions` (bundle ~1567322, ~1544044 `startTapMotion`). LAppModel legacy `onTap` falls back to `setRandomExpression()` for head, random motion group `TapBody` for body (bundle ~1545507). Drag → `setDragging` (eye/body follow). So: **touch reacts instantly, but the agent cannot trigger a tap-style motion or a named motion group by name** — server-side actions are limited to `expressions` (and the automatic "Talk" motion). `idleMotionGroupName` in model_dict.json is not even read by this bundle build (grep: 0 occurrences).

### 3.6 Tool-call UI

`tool_call_status` frames render as chat entries; `browser_view` payload opens a live browser-view panel (bundle ~1861517) — tied to the stagehand MCP server hack (`tool_executor.py:281-287`).

---

## 4. Character & model binding (persona vs presentation)

- **Character file** = YAML with only the overridden slice of `character_config` (`characters/zh_米粒.yaml` is 7 lines: conf_name, conf_uid, persona_prompt). Full schema `config_manager/character.py:13-29`: `conf_name, conf_uid, live2d_model_name, character_name, human_name, avatar, persona_prompt, agent_config, asr_config, tts_config, vad_config, tts_preprocessor_config`.
- **Switching:** `switch-config` message → `handle_config_switch` (`service_context.py:471-558`): path-guarded read from `characters/`, **deep-merge over the current session's config** (`deep_merge` at `:561-571`), re-validate, `load_from_config` rebuilds only changed engines (each init compares typed config, e.g. `init_asr:322-332`), then pushes `set-model-and-conf` + `config-switched`. Runtime, zero restart.
- **Binding topology:** persona (prompt) ↔ presentation (live2d model name + avatar png + background from `/bg` scanner `config_manager/utils.py:scan_bg_directory`) are **fields in the same YAML but otherwise decoupled** — one character can carry its own TTS voice, ASR language, LLM, and model. The coupling is *loose and complete*: everything about a character, including engines, is swappable per-config. The one genuine coupling point is positive: expression vocabulary injection (`emo_str` → system prompt) keeps persona instructions synced to the bound model's actual capabilities.
- **`avatars/`**: just pngs served at `/avatars` (extension-whited, `server.py:43-53`); `character_config.avatar` flows into `DisplayText.avatar` (`conversation_utils.py:55-56`) and history storage. `initial_memory` as a concept exists only implicitly (Letta/Hume keep their own; `conf_uid` keys the JSON chat history dir).
- **`prompts/`**: only `utils/*.txt` (persona dir empty). Persona text lives in the character YAML, not files.

---

## 5. Memory

- **Working memory** (`BasicMemoryAgent._memory`): flat list of `{role, content}` dicts, **unbounded** — no token windowing/trimming/summarization anywhere. `set_memory_from_history` (`basic_memory_agent.py:176-193`) loads the *entire* JSON history into RAM on session/history switch. Multi-modal images are not persisted into memory (only text, `:139-151`).
- **Persistence:** `chat_history/<conf_uid>/<history_uid>.json` — append = read whole file, push, rewrite (`chat_history_manager.py:88-137`); metadata first-entry (`:139-165`) carries `resume_id` for external agents (Hume: `hume_ai.py:122-155` maps history → `resumed_chat_group_id` on their EVI API — a clean external-memory handoff pattern).
- **Retrieval:** none built-in. `mem0_agent` (external vector memory) is the broken 0-byte path; `letta_agent` delegates memory to a Letta server. The vanilla path has zero retrieval — long sessions will blow context.
- **Interrupt-aware memory:** the heard-truncation + `[Interrupted by user]` insertion (§2.6) is the one memory mutation outside append — cheap, model-agnostic, worth copying.

---

## 6. Config system

- One root `conf.yaml`: `system_config` (host/port/`config_alts_dir`/**`tool_prompts` map**/`enable_proxy`) + `character_config` (everything per-persona, incl. engines) + `live_config.bilibili_live` (`config_manager/main.py:9-24`).
- **Pydantic everywhere**, `Literal` enums for provider choice (`config_manager/asr.py`, `tts.py` 816 lines covering 18 TTS engines), bilingual `Description` metadata per field (`i18n.py` I18nMixin) — this metadata is what drives the WebUI's settings/character editors, incl. `/live2d-models/info` model picker feed.
- **Per-plugin config:** nested bag under each provider key; factories call `getattr(config, config.tts_model.lower()).model_dump()` (`service_context.py:337-340`) — the provider name *is* the config key lookup. Simple, no schema drift detection beyond pydantic.
- **Modes:** there is no `voice_call` vs `text` pipeline switch — voice vs text is purely *input-side* (buffer+ASR vs direct text). What does change the pipeline: `use_mcpp` (wires tool loops), `faster_first_response`, `segment_method`, `interrupt_method` (system vs user role for the interrupt marker), `translate_audio` (TTS speaks a different language than displayed, `conversation_utils.py:97-100`), `tts_preprocessor_config` bracket/emoji filters.
- Versioned config migration: `conf_version: v1.2.1` + `upgrade_codes/config_sync` syncs user conf against templates on startup (`run_server.py:114-118`). `${ENV_VAR}` substitution in YAML (`config_manager/utils.py:39-44`).

---

## 7. Verdict: what's genuinely good / what's weak

### Genuinely good (adopt)

1. **The four-decorator streaming pipeline** (`agent/transformers.py`) over a single `AgentInterface.chat` generator contract. Sentence-level segmentation with first-comma TTFB hack (`sentence_divider.py:492-507`), `<think>` tag machinery (display-only, TTS-muted), expression extraction as a pure stream transform. Any agent that yields tokens gets presentation for free (Letta proves reuse). This is the heart worth cloning.
2. **Ordered-parallel TTS with turn backpressure** (`conversations/tts_manager.py:16-110` + `finalize_conversation_turn` waiting on `frontend-playback-complete`): parallel synthesis, strictly ordered delivery, clean cancellation, and a server↔client completion handshake. Plus the interrupt design: task cancel + memory surgery (`basic_memory_agent.py:195-223`) so the LLM *knows* it was cut off.
3. **Persona-vs-presentation decoupling done right**: character YAML deep-merge hot-swap of the *entire engine stack* at runtime (`service_context.py:471-571`), expression vocabulary auto-injected from the bound model's manifest, bilingual field-description metadata powering UI. And MCP-as-the-only-tool-contract (JSON file + server names in config, `mcpp/` as a small clean stdio client/registry/adapter/executor quartet) with the honest prompt-mode fallback for tool-less local models (`openai_compatible_llm.py:220-226`).

### Weak / missing (what a mobile-first product beats)

1. **Transport & audio efficiency:** every sentence = TTS-to-disk → whole WAV base64'd into one JSON frame (`stream_audio.py:69`, `tts_manager.py:166-172`); no streamed audio, no opus, no range requests (`/cache` static mount is only a fallback). Fine on localhost, bad on cellular. No continuous/streaming ASR — VAD-gated whole-utterance batch (`websocket_handler.py:478-487` → `conversation_utils.py:154`). No server-side wake-word, no duplex audio plan; server VAD path exists but is off by default (`conf.yaml vad_model: null`).
2. **Multi-tenancy & hardening:** shared agent engine across connections (`websocket_handler.py:178-202` — session contexts reference `default_context_cache.agent_engine`); group chat only "works" because each client hot-swaps its own config. No auth at all on `/client-ws`, CORS `allow_origins=["*"]` (`server.py:83-89`), API keys sit in plaintext `conf.yaml` (this checkout even has a live-looking upstream key in `openai_compatible_llm` settings), unbounded in-RAM memory with no token windowing (§5). History persistence is whole-file rewrite per message.
3. **Interactivity & tool ceiling:** the agent can only set *expressions* (`expressions[0]` per sentence, motion limited to auto "Talk"); `Actions.pictures/sounds` are dead fields; no way to address named motion groups, no `idleMotionGroupName` consumption, no gesture/hit-area feedback to the agent (taps never reach the LLM). Tool support is hardcoded to `isinstance` of two LLM wrapper classes (`basic_memory_agent.py:608-615`) — adding a provider means touching the agent; `StreamJSONDetector` brace-counting is not string-literal-safe (`json_detector.py:100-121`); tool loop caps/retries/timeouts are minimal; the mem0 agent is a 0-byte file wired into config (`agent_factory.py:96`); and the repo has **zero tests** (CI = ruff + codeql + docker, `.github/workflows/`).

### Decision-relevant one-liners

- Architecture to steal: **generator-based agent contract + stream-transform decorators + ordered TTS queue + interrupt-memory contract**, in a single-process asyncio server with one WS.
- Extension contract to beat: their MCP-only "drop JSON in mcp_servers.json" is *simpler* than most, but the code-level plugin story is factory if/elif; a real entry-point/dynamic-discovery registry is an easy win.
- The whole visual layer is **prompt-injected pseudo-commands** (`[joy]` keywords) rather than a structured action protocol — trivial to adopt, trivial to improve (structured tool for motion/exp/picture with model-manifest validation).
