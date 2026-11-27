# Open-LLM-VTuber — UI / 互动面全量深读

日期：2026-11-28 ｜ 对象：`/Users/gqli/work/Open-LLM-VTuber`（只读，该仓库零改动）
范围限定：frontend / 交互 / 辅助 UI / 与 UI 相关的服务端契约。**agent 后端架构不在本篇**（另有 worker 深读）。
同主题已有文档 `docs/research/2026-11-28-open-llm-vtuber-deepread.md` 的 §3 只到「协议 + manifest + 点击机制」骨架层；本篇是把互动面**逐个元素拆到行**，补上状态机、紧致度量化、对照表与 persona/her 双页设计启示。

---

## 0. 出处约定（先读这段，否则行号会错）

`frontend/` 在本仓库里是 **submodule 的构建产物**，不是源码：

- `.gitmodules` 声明 `frontend` → `github.com/Open-LLM-VTuber/Open-LLM-VTuber-Web`，跟踪 **`build` 分支**；当前 pin `06a659b`（提交信息 "Deploying to build from …@3d57a9a"）。
- 仓库里的 `frontend/` 只有 `index.html` + `assets/main-nu7uwxNJ.js`（1,895,632 B）+ `assets/main-QEkl09-0.css`（41,060 B）+ `libs/`（Live2D Core、VAD worklet、silero onnx、ort wasm）。
- 前端**真实源码 blob 存在于 submodule 的 git objects 里**（commit `3d57a9a`）。我把它导出到**仓库外**的临时目录做行号引用（对原仓库只读）：

```bash
mkdir -p /tmp/ollmv-web-src-3d57a9a
git -C /Users/gqli/work/Open-LLM-VTuber/frontend archive 3d57a9a | tar -x -C /tmp/ollmv-web-src-3d57a9a
```

本文行号前缀：

| 前缀 | 实际路径 |
|---|---|
| `web:` | `/tmp/ollmv-web-src-3d57a9a/src/renderer/src/` |
| `sdk:` | `/tmp/ollmv-web-src-3d57a9a/src/renderer/WebSDK/src/` |
| `FW:` | `/tmp/ollmv-web-src-3d57a9a/src/renderer/WebSDK/Framework/src/` |
| `srv:` | `/Users/gqli/work/Open-LLM-VTuber/src/open_llm_vtuber/` |

⚠️ 若在主仓库 `frontend/assets/main-*.js` 里找这些行号，找不到——那是 minified bundle。

---

## 1. frontend 结构、变体与技术栈

### 1.1 变体：一份 React 代码，两个构建目标，一个独立试验页

「几个前端」的诚实答案 = **1 个 React 应用 + 2 个构建目标 + 1 个手写 vanilla 页面**：

| 入口 | 怎么来 | 技术 | 位置 |
|---|---|---|---|
| Web 版（服务端挂在 `/`） | `npm run build:web` → `vite build --mode web`（`package.json:24`） | React 18.3 + TS ~5.5 + Vite 5.3 | `web:` |
| 桌面版（Electron） | `build:win/mac/linux`（`package.json:19-22`）+ `electron-builder.yml` | electron 31 / electron-vite 2.3 | `src/main/`、`src/preload/` |
| 开发者试验页 web_tool | **不在 submodule**，主仓库手写 vanilla JS | 无框架 | `web_tool/`（见 §5） |

**没有 `default` / `advanced` 两套前端**（这是对任务书预设的更正）。submodule 里另有 `R4-test`、`close-button`、`dev`、`mic-test`、`neo-mac` 等**分支**，都不是发布变体。

同一份代码在运行时有**两种形态**（不是两个前端）：

- `window` 模式：侧栏 + 舞台 + footer 的完整窗口。
- `pet` 模式：模型全屏（`web:App.tsx:85-92`，zIndex 15），侧栏/footer 完全不渲染（`web:App.tsx:153`），只留一张可拖的浮层输入卡（`web:components/electron/input-subtitle.tsx`）。**仅 Electron 可用**：web 下点 Pet 弹 toast 拒绝（`web:context/mode-context.tsx:19-27`），菜单项也 disable（`web:components/sidebar/sidebar.tsx:64-76`）。

### 1.2 技术栈实测

- **渲染**：**没有 PixiJS**，也没有 pixi-live2d-display。直接 vendor Live2D Cubism Web SDK：`WebSDK/Core/`（Cubism Core，CHANGELOG 最新条目 2024-03-26）+ `WebSDK/Framework/`（**Framework 5-r.1**，同日期）+ `WebSDK/src/lapp*.ts`（官方 Sample 层：`lappdelegate` / `lappview` / `lappmodel` / `lapplive2dmanager` / `lappadapter` / `lappglmanager`），GL 走 `FW:rendering/cubismrenderer_webgl.ts`。
- 还带 `public/libs/live2d.min.js`（**Cubism 2 旧运行时**）——源码里无人 import，是遗留物；真正加载的 Core 由 `vite-plugin-static-copy` 拷到 `libs/`（`vite.config.ts:26`）。
- **UI 库**：Chakra UI v3（`@chakra-ui/react ^3.2.3`）+ `@chatscope/chat-ui-kit-react ^2.0.3`（仅聊天面板）+ `react-icons` + `framer-motion`。
- **状态**：React Context 为主（provider 嵌套 11 层，`web:App.tsx:173-201`）+ `zustand` 只用于鼠标穿透一处（`web:hooks/utils/use-force-ignore-mouse.ts:12-15`）+ `rxjs` Subject 做 WS 消息分发（`web:services/websocket-service.tsx:113-115`）。i18n = i18next（zh/en）。
- **端侧 VAD**：`@ricky0123/vad-web ^0.0.24` + `onnxruntime-web 1.14.0` + `silero_vad_v5.onnx`（`web:context/vad-context.tsx:278-296`）。
- **Live2D SDK 版本口径**：Framework 5-r.1 / Core 构建日期 2024-03-26。Core 的精确 semver 小版本在 minified 文件里没读到可读字符串（诚实：未确认）。

### 1.3 构建产物怎么被服务端挂载

FastAPI 静态挂载，**catch-all 放最后**：

| 路径 | 目录 | file:line |
|---|---|---|
| `/cache` | TTS 音频缓存 | `srv:server.py:114-118` |
| `/live2d-models` | 模型 | `srv:server.py:121-125` |
| `/bg` | `backgrounds/` | `srv:server.py:126-130` |
| `/avatars` | 头像（**只允许图片扩展名**，否则 403） | `srv:server.py:131-135`、helper `:43-53` |
| `/web-tool` | `web_tool/`（html=True） | `srv:server.py:138-142` |
| `/` | `frontend/`（html=True，SPA catch-all） | `srv:server.py:145-149` |

全部走自定义 `CORSStaticFiles`（`Access-Control-Allow-Origin: *`，`.js` 强制 Content-Type，`srv:server.py:23-40`）。默认 `host=localhost port=12393`。`enable_proxy` 时额外挂 `/proxy-ws`（`srv:server.py:100-109`）。WS 端点 `/client-ws`（`srv:routes.py:29`）。服务端**无任何 i18n 挂载逻辑**（确认不存在）。

前端连的地址有写死默认 + 设置可改：`ws://127.0.0.1:12393/client-ws`、`http://127.0.0.1:12393`（`web:context/websocket-context.tsx:6-7`）。连上即发四条拉取：`fetch-backgrounds` / `fetch-configs` / `fetch-history-list` / `create-new-history`（`web:services/websocket-service.tsx:126-139`）。

---

## 2. 互动面逐个拆

### 2.1 骨架与可交互元素总表（window 模式，桌面 ≥md）

```
┌───────────────┬──────────────────────────────────────────────┐
│ Sidebar 440px │  Stage                                       │
│ (折叠→24px)   │   ┌ ws 状态徽标(左上 20/20) ┐                 │
│ ┌ 5 头部钮 ┐  │   │                        │                 │
│ │ 聊天流   │  │   │   Live2D 画布（全幅）  │ ← 可点/可拖/可滚轮│
│ │(chatscope)│  │   │                        │                 │
│ └ 3 tab ───┘  │   └ 字幕(bottom 135px, 60% 宽)┘              │
│               │  ┌ Footer 120px（折叠→24px）───────────────┐  │
│               │  │ 状态徽标 麦 举手 │ 📎 输入框            │  │
│               │  └─────────────────────────────────────────┘  │
└───────────────┴──────────────────────────────────────────────┘
```

| # | 元素 | 类型 | file:line | 备注 |
|---|---|---|---|---|
| 1 | 侧栏折叠把手 | button | `web:components/sidebar/sidebar.tsx:151` | 把手宽 24px 全高（`sidebar-styles.tsx:52-67`） |
| 2 | 设置 | button | `web:components/sidebar/sidebar.tsx:87` | 打开后**整栏替换**聊天面板（`:153-169`） |
| 3 | 群聊抽屉 | button→Drawer | `web:components/sidebar/sidebar.tsx:91-95` | |
| 4 | 历史抽屉 | button→Drawer | `web:components/sidebar/sidebar.tsx:97-101` | |
| 5 | 新建会话 | button | `web:components/sidebar/sidebar.tsx:103-105` | |
| 6 | 模式菜单 | menu（2 项） | `web:components/sidebar/sidebar.tsx:48-81` | Live / Pet；web 下 Pet 禁用 |
| 7-9 | 相机 / 屏幕 / 浏览器 tab | tabs | `web:components/sidebar/bottom-tab.tsx:20,24,28` | 内容 `:34-44` |
| 10 | 相机面板（点击开摄像头） | 整块热区 | `web:components/sidebar/camera-panel.tsx:21-38,60+` | 占位文案「Click to start camera」 |
| 11 | WS 状态徽标 | badge，断连时可点重连 | `web:components/canvas/ws-status.tsx:26-36`、`web:hooks/canvas/use-ws-status.ts:14-18` | 绿/黄/红（`:20-44`） |
| 12 | Footer 折叠把手 | button | `web:components/footer/footer.tsx:41-52,128` | |
| 13 | 麦克风开关 | icon button 50×50 | `web:components/footer/footer.tsx:58-64` | 绿=开，红=闭（直接换 `bg`） |
| 14 | 「举手」打断 | icon button 50×50 | `web:components/footer/footer.tsx:65-72` | 黄底，`aria-label="Raise hand"` |
| 15 | 附件 📎 | icon button | `web:components/footer/footer.tsx:90-96` | **死控件：有 `aria-label="Attach file"` 但无 onClick** |
| 16 | 文本输入 | Textarea h80 font18 | `web:components/footer/footer.tsx:97-105`、样式 `footer-styles.tsx:48-72` | |
| 17 | AI 状态徽标 | 只读 110×30 紫块 | `web:components/footer/ai-state-indicator.tsx:11-15`、样式 `footer-styles.tsx:86-105` | 文案 = `t('aiState.'+state)` |
| 18 | 字幕 | 只读 | `web:components/canvas/subtitle.tsx:22-33`、挂载 `web:App.tsx:127-136` | 黑底 70%、1.5rem、居中 |
| 19 | Live2D 画布 | 触摸/鼠标热区 | `web:components/canvas/live2d.tsx:96-123` | tap / drag / wheel 三合一 |

**常驻可交互 = 18 个**（含 1 个死控件；侧栏折叠后 17）。**舞台区内只有 5 个**（ws 徽标、footer 折叠、麦、举手、输入框）+ 画布本身。
布局度量：侧栏 440px → 折叠 24px（`web:layout.tsx:26`、`web:App.tsx:115`）；footer 100px(mobile)/120px(md) → 折叠 20/24px（`web:layout.tsx:55`、`:87-90`）；画布宽 `calc(100% - 440px↔24px)`（`:73-75`）；标题栏 30px（`:91-104`）；移动端断点 base 改纵向堆叠（`web:layout.tsx:21`，UA sniff `:4-8`）。

Pet 模式常驻可交互 = **6 个**：画布 + 关闭 ✕ + 麦 + 举手 + 输入 + 发送（`web:components/electron/input-subtitle.tsx:99-165`）。卡片宽 400px、`bottom:120px`、`backdropFilter blur(8px)`（`web:components/electron/electron-style.tsx:4-27`），可拖动（`web:hooks/electron/use-draggable.ts:18-47`），右键出原生菜单（`web:components/canvas/live2d.tsx:84-94` → `src/main/menu-manager.ts:35-90`）。

### 2.2 上行通道（6 类）

| 通道 | 消息 type | file:line |
|---|---|---|
| 文本 | `text-input {text, images[]}` | `web:hooks/footer/use-text-input.tsx:32-36`；Enter 发送 / Shift+Enter 换行 / 输入法 composing 保护 `:42-49` |
| 麦克风（端侧 VAD 切句） | `mic-audio-data {audio:float[]}` 分块 4096 float + `mic-audio-end {images}` | `web:hooks/utils/use-send-audio.tsx:9-29` |
| 相机帧 | 随 `text-input` / `mic-audio-end` / `ai-speak-signal` 附带 | `web:hooks/utils/use-media-capture.tsx:55-131`（`grabFrame` → canvas 缩放 → jpeg；压缩率与最大宽度可在设置调） |
| 屏幕帧 | 同上（`source:'screen'`） | `web:hooks/utils/use-media-capture.tsx:116-126` |
| 主动说话触发 | `ai-speak-signal {idle_time, images}` | `web:hooks/utils/use-trigger-speak.ts:9-18` |
| 打断 | `interrupt-signal {text: fullResponse}` ← **把用户已听到的部分回传服务端写 memory** | `web:hooks/utils/use-interrupt.ts:27-31` |

播放侧还回传 `audio-play-start`（`web:hooks/utils/use-audio-task.ts:93-98`）与 `frontend-playback-complete`（`:237`），服务端靠这两条做前后端节奏握手。

**触摸不进上行**：点击模型不产生任何 WS 消息（见 §2.3）。

### 2.3 Live2D 区域点击（hit area）

**配置在哪**（三层）：

1. 模型自带 `.model3.json` 的 `HitAreas`。实测 `live2d-models/mao_pro/runtime/mao_pro.model3.json`：`[{"Id":"HitAreaHead","Name":""},{"Id":"HitAreaBody","Name":""}]`（**Name 全是空串**）；shizuku：`HitAreas: []`（空）。
2. 服务端 manifest `model_dict.json`（根目录，**是一个 list**，实测 1 条）给每个模型配 **`tapMotions`**：`{"HitAreaHead":{"":1},"HitAreaBody":{"":1}}` —— key 是 **Cubism Id**（不是 Name），value 是「动作组名 → 权重」。同文件还有 `kScale`、`initialXshift/Yshift`、`idleMotionGroupName`、`emotionMap`、`description`，另有 `kXOffset` —— 它在 `ModelInfo` 里没有对应字段，属于**服务端有、前端不读**的废字段。manifest 里**没有** `pointerInteractive` / `scrollToResize` / `defaultEmotion` 键。
3. 前端装进 `ModelInfo`（`web:context/live2d-config-context.tsx:35-74`：`url / kScale / initialXshift / initialYshift / idleMotionGroupName / defaultEmotion / emotionMap / pointerInteractive / tapMotions / scrollToResize / initialScale`），由服务端 `set-model-and-conf` 推送填充（`web:services/websocket-handler.tsx:102-124`），相对 url 用 baseUrl 前缀补全（`:117-121`）。

**点击后发生什么**（React 层主路径）：

```
onPointerDown → 屏幕坐标→模型坐标（view._deviceToScreen.transformX/Y）
                anyhitTest + isHitOnModel 命中判定，记下按下时刻/坐标
                (web:hooks/canvas/use-live2d-model.ts:205-242)
onMouseMove   → 位移 > 5px，或 (时长 > 200ms 且位移 > 1px) ⇒ 判为拖拽，取消 tap
                (阈值 web:hooks/canvas/use-live2d-model.ts:25-26, 244-270)
onMouseUp     → 时长 < 200ms 且位移 < 5px ⇒ tap
                → 用「按下点」重做命中，取 hitAreaName
                → if (modelInfo?.pointerInteractive !== false)
                     model.startTapMotion(hitAreaName, modelInfo.tapMotions)
                (web:hooks/canvas/use-live2d-model.ts:336-385；开关判定 :363)
```

`startTapMotion`（`sdk:lappmodel.ts:1163-1253`）是**加权随机**：命中具名区 → 用该区动作表（`:1179-1183`）；未命中具名区 → 把所有区权重相加（`:1188-1200`）；然后轮盘赌选一个动作组，用 **PriorityForce(3)** 播（`:1246`）。命中判定 `anyhitTest`（`sdk:lappmodel.ts:868-900`）返回的是 Cubism **Id 字符串**（从私有字段 `hitAreaIdHandle._id.s` 取，`:888`）——这就是 `tapMotions` 的 key 为什么是 `HitAreaHead`。opacity < 1 的部件不算命中（`hitTest :844-860`）。

⚠️ **两条 tap 路径同时在跑**（重要，且是坑）：SDK Sample 层自己在 canvas 上注册监听（`sdk:lappdelegate.ts:91-105`：`'ontouchend' in canvas` 则注册 touch*，否则注册 mousedown/mousemove/mouseup，处理体 `:363-420`）→ `lappview.onTouchesBegan/Moved/Ended`（`sdk:lappview.ts:187-238`）→ `lapplive2dmanager.onTap`（`sdk:lapplive2dmanager.ts:108-138`），后者按 Sample 老规则：命中 `Head` → `setRandomExpression()`，命中 `Body` → 播 `TapBody` 组（PriorityNormal）。与 React 层的 `tapMotions` 并行。对 mao_pro 恰好都不炸：它 `Expressions: []` 使 `setRandomExpression` 早退（`sdk:lappmodel.ts:814`），无 `TapBody` 组使 `startRandomMotion` 静默返回无效 handle（`:759-771`），真正生效的只有 `tapMotions` 里权重 1 的空名组 `""`（实测 mao_pro 的 MotionGroups = `['Idle','']`）。同时桌面端拖拽也走两条变换：Sample 层 pan 的是 Cubism **view**，React 层改的是 **model matrix**（见 §2.5）。

**点击会不会进 prompt？——不会。** 服务端全仓 grep `hit|click|user action` 无命中（只有 VAD 的 `required_hits`）；协议里没有把触摸上行的消息类型。**触摸是纯端侧呈现层**，与 LLM 完全无关。

**表情是怎么进的**：走 **prompt 内嵌方括号关键词**，不是 `<expression>` / `<motion>` 标签：

- manifest `emotionMap`（如 `{"neutral":0,"anger":2,"disgust":2,"fear":1,"joy":3,"smirk":3,"sadness":1,"surprise":3}`）→ `emo_map`（`srv:live2d_model.py:48-50`）→ 拼成 `emo_str = "[joy], [anger], ..."`（`:51-53`）
- 拼进 system prompt：`prompts/utils/live2d_expression_prompt.txt`（要求模型在回复正文里直接写 `[keyword]`），占位符 `[<insert_emomap_keys>]` 在 `srv:service_context.py:458` 被替换（整段注入 `:436-468`）。⚠️ 该文件当前**有别人留下的未提交改动**（mtime 2026-11-27 20:05，非本会话所为），HEAD 版本里该行在 459，行号会 ±1。
- 流式扫描：`extract_emotion`（`srv:live2d_model.py:146-172`）逐字符扫 `[key]`，返回表情 index/文件名列表；`remove_emotion_keywords`（`:174-194`）把它从可见字幕里剥掉
- 时机在输出 transformer 链的 `actions_extractor`（`srv:agent/transformers.py:58-100`），按句切分时只对**不在标签内的句子**提取；标签系统本身只有 `<think>`（`srv:agent/agents/basic_memory_agent.py:592`）
- 表情随音频下发：`audio` 消息的 `actions.expressions`，前端只取**第一个**（`web:hooks/utils/use-audio-task.ts:128-136`）

### 2.4 表情 / 动作的「手动覆盖」——结论：UI 里没有

诚实结论，别按「它肯定有」写：

- 设置页 Live2D tab **只有两个开关**：`pointerInteractive`、`scrollToResize`（`web:components/sidebar/setting/live2d.tsx:38,44`）。没有表情选择器、没有动作列表、没有模型列表、没有缩放滑杆、没有位置输入框。
- ⚠️ **这两个开关的默认值前后端不一致**（实测）：服务端**从不**下发 `pointerInteractive`（全仓 grep `src/` 0 命中），前端开关用 `checked={modelInfo.pointerInteractive ?? false}` 显示为**关**（`live2d.tsx:39`），但 tap 逻辑用 `modelInfo?.pointerInteractive !== false` 判定（`use-live2d-model.ts:363`）⇒ **未手动设置时触摸其实是生效的，界面显示却是关**。同类问题：`scrollToResize` 只在设置 hook 的本地兜底对象里给 `true`（`use-live2d-settings.ts:7-13`），服务端同样不下发；`defaultEmotion` 在 `ModelInfo` 里有字段但 manifest 里没有该键，所以「默认表情」永远走 expression[0] 兜底。抄这套「manifest 驱动 + 前端兜底」时，**默认值必须有单一事实源**，否则会复现这个显示与实际相反的 bug。
- 唯一的手动覆盖入口是**浏览器 console 调试对象** `window.Live2DDebug`：`getMotionInfo() / playMotion(group,index,priority) / playRandomMotion(group,priority)`（`web:hooks/canvas/use-live2d-model.ts:410-530`）。给开发者，不给用户。
- 说话时的动作是**硬编码组名约定**：每句音频播放前 `startRandomMotion("Talk", PriorityNormal)`（`web:hooks/utils/use-audio-task.ts:138-147`）。mao_pro 没有 `Talk` 组 ⇒ 早退无效（`sdk:lappmodel.ts:759-771`）——**官方自带模型其实没有说话动作，只有口型**。
- 表情回落：`aiState === idle` 时复位到 `modelInfo.defaultEmotion`，没有则用第 0 个表达式（`web:components/canvas/live2d.tsx:49-56` → `web:hooks/canvas/use-live2d-expression.ts:43-80`）。这是「她回到默认脸」的唯一机制。
- 口型：`model._wavFileHandler.start(dataUrl)` + 把 RMS ×2.0 增强灵敏度（`web:hooks/utils/use-audio-task.ts:165,181-201`）。

### 2.5 拖拽与缩放

- **拖拽移动模型**：改写 `_modelMatrix` 的平移分量 `[12]/[13]`（`web:hooks/canvas/use-live2d-model.ts:244-312`，矩阵读写 `setModelPosition :158-173`）。位置**不持久化**（localStorage 里的 modelInfo 把 url 过滤掉，`web:context/live2d-config-context.tsx:112-118`）。
- **滚轮缩放**：每格 ±0.03（`web:hooks/canvas/use-live2d-resize.ts:14`），夹在上下限之间，带缓动（`:96-110,116-135`），监听 `passive:false`（`:227-237`）；受 `scrollToResize` 开关控制（`:118`）。**React 路径上没有 pinch。**
- **另有一套 Sample 层 view 手势**（`sdk:lappview.ts:187-238` + `sdk:touchmanager.ts` 双指缩放/平移），与上面并存。
- Pet 模式「鼠标穿透」是窗口级能力：托盘/右键菜单切 `forceIgnoreMouse`（`src/main/menu-manager.ts:65-78` → `web:hooks/utils/use-force-ignore-mouse.ts` → 落到 wrapper/canvas 的 `pointerEvents:none`，`web:components/canvas/live2d.tsx:103,118`），并在 hover 到模型时才让窗口接收鼠标（`web:hooks/canvas/use-live2d-model.ts:315-332`、`:397-400` 发 `update-component-hover`）。

### 2.6 背景与「换装」

- 背景列表由 WS `fetch-backgrounds` 下发（`srv:websocket_handler.py:550-557`、`srv:config_manager/utils.py:174-181`），响应 `background-files` 写进 `bgurl-context`（`web:services/websocket-handler.tsx:150-154`）。
- UI 在 **General tab**：`backgroundImage` 下拉 + `customBgUrl` 自定义 URL（`web:components/sidebar/setting/general.tsx:102-117`，映射 `/bg/${filename}` `:28-34`）；另有 `useCameraBackground` 把相机当背景（`:90-94`，镜像 `web:components/canvas/canvas-styles.tsx:19-28`）。当前选择落 localStorage（`web:context/bgurl-context.tsx:48-51`）。
- **「换装」不存在**：没有服装槽位、没有皮肤列表、没有 wardrobe 概念。唯一改变外观的入口就是 §2.7 的角色预设下拉（它连带换模型）。

### 2.7 设置页：6 个 tab / 21 个控件

形态：**Drawer 抽屉，宽 440px，placement=start**（`web:components/sidebar/setting/setting-ui.tsx:98-104`、`setting-styles.tsx:74`），打开时整栏替换（`web:components/sidebar/sidebar.tsx:153-169`）；底部全局 Save/Cancel（`setting-ui.tsx:166-173`）；默认停在 General（`:117-118`）；tab 清单 `:123-160`。字段 label 宽 120px、gap 8，一屏约 6–8 行。

| tab | 控件数 | 明细（`web:components/sidebar/setting/`） |
|---|---|---|
| General | **10** | language select `general.tsx:82-88`；useCameraBackground switch `:90-94`；showSubtitle switch `:96-100`；backgroundImage select `:102-110`；customBgUrl input `:112-117`；**characterPreset select `:121-127`**；wsUrl input `:129-134`；baseUrl input `:136-141`；imageCompressionQuality `:143-154`（手动校验 0.1–1.0）；imageMaxWidth `:156-167` |
| Live2D | **2** | pointerInteractive `live2d.tsx:38`；scrollToResize `:44` |
| ASR | **6** | autoStopMic `asr.tsx:44-48`；autoStartMicOnConvEnd `:50-54`；autoStartMicOn `:56-60`；positiveSpeechThreshold / negativeSpeechThreshold / redemptionFrames 三个 NumberField（min1 max100，`:62-87`；默认 50/35/35 `web:context/vad-context.tsx:81-85`；HelpIcon tooltip `common.tsx:24-54`） |
| TTS | **0** | `tts.tsx:3-5` 返回空 `<Box/>` — **纯占位空壳** |
| Agent | **3** | allowProactiveSpeak `agent.tsx:24-28`；idleSecondsToSpeak（开关开才显示）`:30-39`；allowButtonTrigger `:41-45` |
| About | 0 配置 | 版本**硬编码 `'1.2.1'`**（`about.tsx:22`）+ 三个外链按钮 `:46-68` |

**保存通道（关键）**：除了角色，**没有任何「写配置到服务端」的 WS/HTTP 请求**。语言/字幕/背景/压缩率/VAD/主动说话全落 localStorage（`web:hooks/sidebar/setting/use-general-settings.ts:131-132`、`web:context/vad-context.tsx:114-133`、`web:context/proactive-speak-context.tsx:28-31`），Live2D 两个开关也只写 localStorage 的 modelInfo（`web:hooks/sidebar/setting/use-live2d-settings.ts:32-36`）。打开设置页不发请求，当前值从各 context 现读（`use-general-settings.ts:95-109`）。
唯一真正的服务端写：**`switch-config {file}`**（`web:hooks/utils/use-switch-character.tsx:18-36`）→ 服务端深合并 yaml + 热重载引擎（`srv:service_context.py:471-566`）→ 回 `set-model-and-conf` + `config-switched`。

即：**「她是谁」在服务端；「她怎么显示、我听不听得见」在本机 localStorage。**

### 2.8 聊天流的呈现

用 `@chatscope/chat-ui-kit-react`（`web:components/sidebar/chat-history-panel.tsx:11-12`）：气泡 model{message,sentTime,sender,direction}（`:100-112`），AI 头像 `${baseUrl}/avatars/${msg.avatar}`，**加载失败回退首字母色块**（`:114-135`），human sender 硬编码 "Me"（`:26`）。
**没有逐字打字机**：AI 文本随音频**按句**到达，连续 AI 文本合并进最后一条气泡，直到收到 `force-new-message` 才断开（`web:context/chat-history-context.tsx:83-111`，合并 `:99-108`；flag 由 `web:services/websocket-handler.tsx:259-261` 置位）。观感 = 一句一句长出来，与她实际开口节奏一致。
tool 调用不进气泡，走独立指示行：工具图标 + 「X is using tool Y / used tool Y」（`:58-96`，文案 `:72`）+ Spinner/FaCheck/FaTimes，同 `tool_id` 原地更新（`chat-history-context.tsx:117-158`）。
死代码：`chat-bubble.tsx`（无引用）、`invite-dialog.tsx`（**0 字节空文件**）、`MessageEvent.live2d_model?` 字段（`web:services/websocket-service.tsx:80`，全库无人使用）。

---

## 3. 互动状态机

状态定义 `web:context/ai-state-context.tsx:17-49`，共 6 态（注意 `thinking-speaking` 是**合一**的，不是 thinking / speaking 分开）：`idle / thinking-speaking / interrupted / loading / listening / waiting`。展示文案 = `t('aiState.'+aiState)`（`web:components/footer/ai-state-indicator.tsx:13`）。

```
                     连接 · 切角色 · 换模型
   ┌──────────────────────────────────────────────────────────┐
   │                                                          ▼
   │   (set-model-and-conf)  ┌──────────┐  (到齐)      ┌──────────┐
   └────────────────────────►│ loading  │─────────────►│   idle   │◄──┐
                             └──────────┘              └──┬───┬───┘   │
              打字（每次按键）                             │   │       │
   ┌─────────┐                                            │   │  2s 超时自动回落
   │ waiting │◄───────────────────────────────────────────┘   │   │
   └────┬────┘  （若当前是 thinking-speaking ⇒ 忽略 waiting）    │   │
        └──────────────────────────────────────────────────────┘   │
                                                                    │
   端侧 VAD 确认人声 onSpeechRealStart                              │
   （上一态若是 thinking-speaking ⇒ 先本地打断）                     │
   ┌───────────┐                                                    │
   │ listening │◄───────────────────────────────────────────────────┤
   └─────┬─────┘                                                    │
         │ onSpeechEnd：清队列→(可选)停麦→发音频→                    │
         ▼                                                          │
   ┌──────────────────┐   control: conversation-chain-start         │
   │ thinking-speaking│◄────────────────────────────────────────────┘
   └─────┬────────┬───┘
          │        │ 「举手」/ VAD 抢话 / 发送新文本
          │        ▼
          │   ┌─────────────┐  停当前音频+清播放队列+发 heard_response
          │   │ interrupted │  （粘滞：见下方结论）
          │   └──────┬──────┘
          │          │ 任何新输入（文本 / VAD→listening / chain-start）
          │          └───────────────►（回到上面相应态）
          │ control: conversation-chain-end（排在播放队列尾部才执行）
          └───────────────────────────► idle
```

转移表（谁改的状态）：

| 触发 | 从 → 到 | file:line |
|---|---|---|
| 收到 `set-model-and-conf` | any → loading → **idle（同一 handler 内立刻置 idle）** | `web:services/websocket-handler.tsx:103,123` |
| 输入框每次按键 | any(非 thinking-speaking) → waiting | `web:hooks/footer/use-footer.ts:28` |
| waiting 计时器 2s | waiting → idle | `web:context/ai-state-context.tsx:96-108` |
| `control: conversation-chain-start` | any → thinking-speaking（同时 `clearQueue()` + `clearResponse()`） | `web:services/websocket-handler.tsx:69-73` |
| `control: conversation-chain-end`（作为播放队列尾任务） | thinking-speaking → idle（+ 可选自动开麦） | `:74-88`；另一处消息版 `:249-258` |
| VAD `onSpeechStart` | **不改状态**，只把当前态存进 `previousAiStateRef` | `web:context/vad-context.tsx:197-203` |
| VAD `onSpeechRealStart` | 若前态是 thinking-speaking ⇒ 打断；然后 → listening | `:208-217` |
| VAD `onSpeechEnd` | listening → thinking-speaking（并 `audioTaskQueue.clearQueue()`） | `:231-246` |
| VAD 误触发 misfire | 回滚到 previousAiState + 字幕提示 | `:251-260` |
| 举手按钮（thinking-speaking 时） | thinking-speaking → interrupted | `web:hooks/footer/use-footer.ts:35-38` → `web:hooks/utils/use-interrupt.ts:16-38` |
| 举手按钮（非 speaking 时） | **不改状态**，发 `ai-speak-signal` 催她主动说话 | `web:hooks/footer/use-footer.ts:41-43` |
| 发送文本时正在 speaking | 先 interrupt（→ interrupted），再发 text-input | `web:hooks/footer/use-text-input.tsx:23-27` |
| 麦关闭且态为 listening | listening → idle | `web:hooks/utils/use-mic-toggle.ts:12` |
| `config-switched` / `new-history-created` | any → idle | `web:services/websocket-handler.tsx:136,181` |
| 群聊收到别人 `interrupt-signal` | 本地 interrupt(**不回发**服务端) | `:262-265` |

**粘滞态结论（实测，抄的时候要注意）**：grep 全仓，**没有任何路径把 `interrupted` 直接送回 `idle`** —— `conversation-chain-end` 只在 `currentState === 'thinking-speaking'` 时才置 idle（`:76-85`、`:251-256`）。能离开 `interrupted` 的只有：新输入触发别的态、`set-model-and-conf`(`:123`)、`config-switched`(`:136`)、`new-history-created`(`:181`)、WAITING 计时器(`:105`)、关麦(`use-mic-toggle.ts:12`)。所以「已打断」会一直挂在徽标上。期间**所有下行音频被丢弃**（`websocket-handler.tsx:156`；`use-audio-task.ts:77,169,255`）—— 这是「打断立刻生效」的实现方式，代价是状态文案停在过去。

打断的完整语义（端侧三停 + 一条回传）：`stopCurrentAudioAndLipSync()` + `audioTaskQueue.clearQueue()` + `setAiState('interrupted')` + 发 `interrupt-signal {text: fullResponse}`（把已听到的内容回传，服务端 `task.cancel()` + `agent_engine.handle_interrupt(heard_response)` + history 追加 heard 与 `"[Interrupted by user]"`，`srv:conversation_handler.py:112-143`，入口 `srv:websocket_handler.py:369-392`；群聊版广播 `:146-213`；VAD 自动打断走 `<|PAUSE|>` → control/interrupt，`srv:websocket_handler.py:497-500`）。**本地立刻真停，不等服务端** —— 与我方 `companion-interaction-protocol-v2` 的取向一致。

---

## 4. 「紧致」的可量化定义

把「紧致」拆成可数的指标，用 OLLMV 实测值当基线：

| 指标 | OLLMV window 模式（桌面） | 说明 |
|---|---|---|
| **① 常驻可交互元素数** | **18**（含 1 个死控件；侧栏折叠后 17） | §2.1 表 |
| **② 舞台区 chrome 常驻控件数** | **5**（ws 徽标 / footer 折叠 / 麦 / 举手 / 输入框）+ 画布热区 1 | 其余全在侧栏 |
| **③ chrome 面积（折叠态）** | 两条 24px 把手（左 + 下），舞台≈全屏 | `web:layout.tsx:73-90` |
| **④ 到核心动作的点击深度** | 说话 **1**（Enter）；打断 **1**；开/关麦 **1**；触摸她 **0**；换背景 **3**；换角色 **3**；调 VAD 灵敏度 **3**；**换模型 ∞（无入口）**；**换表情/服装 ∞（不存在）** | 互动动作全在 0–1；配置动作在 3 |
| **⑤ 状态呈现密度** | 2 个常驻徽标（AI 态 110×30 紫块 `#7C5CFF` + 连接态绿/黄/红可点重连）+ 1 条字幕（60% 宽、bottom 135px / 折叠时 39px） | 进行态不靠 toast 表达 |
| 参考：设置页控件密度 | 6 tab / **21** 个真实控件（10+2+6+0+3+0），TTS tab 0 控件 | 配置面小且不对称 |
| 参考：上行通道数 | **6** | 触摸不上行 |
| 参考：Pet 模式控件数 | **6**（400px 浮卡内） | 极简形态的下限样本 |

由此得到「紧致」的四条操作性定义：

1. **动作贴脸，配置离脸。** 0–1 跳全部留给「和她有关」的动作（说话 / 打断 / 摸她 / 开麦），需要 3 跳的全是「关于系统」的配置。
2. **控件少且角色明确，无重复入口。** 麦、举手各只有一个；折叠把手两个；配置只有一个入口。反面：它也有重复——两套 tap 路径、两套 view 手势（§2.3、§2.5），这部分**不**紧致。
3. **可折叠的边栏换全屏人物。** 440px/120px → 24px/24px，一处把手，0.2–0.3s cubic-bezier(0.4,0,0.2,1)（`web:layout.tsx:33,41,57`）。
4. **状态用常驻徽标 + 字幕表达，不用弹层。** 全窗口只有一条字幕；toast 只用于「已经发生的事」（切换成功 / 历史载入 / 报错，`websocket-handler.tsx:139-143,174-178,223-228`，duration 2000ms）。

**延迟感知手段清单**（它用什么填满等待）：

- 开场「Thinking...」占位字幕；打断时若字幕还是 "Thinking..." 会被清空（`web:hooks/utils/use-interrupt.ts:35-37`）。
- 服务端 `faster_first_response`：第一句遇逗号就先出音频，压首包延迟。
- 播放队列间隔 20ms（`web:utils/task-queue.ts:76` `audioTaskQueue = new TaskQueue(20)`）——句间几乎无缝。
- 表情随音频同包到达（`actions.expressions`），不单独发消息 ⇒ 表情与语音同帧。
- `loading` 态专用于换角色/换模型窗口期，配字幕 "New Character Loading..."（`use-general-settings.ts:214-228` → `use-switch-character.tsx:18-36`）。
- 前后端握手 `backend-synth-complete` ↔ `frontend-playback-complete`（`use-audio-task.ts:230-247`），避免「她说完但界面还在说话」。
- 断连时徽标变红且文案变成「Click to Reconnect」——把错误态做成按钮（`use-ws-status.ts:20-44`）。

---

## 5. web_tool 与 mcp_servers.json 编辑器

### 5.1 web_tool：给开发者的引擎试验台，不是用户功能

自述见 `web_tool/README.md:9-13`（"backend 内的专用网页"，`http://localhost:12393/web-tool`，用的就是 conf.yaml 那套模型，**运行时不可切换**）。规模：`index.html` 142 行 + `main.js` 397 行 + `recorder.js` 131 行，零框架，与 frontend bundle 零复用（bundle 是另一份 React）。

**2 个 section、5 个按钮**：

- Speech Recognition：`startRecording :116`、`stopRecording :117`、`audioFileInput :120-122`、`uploadAudio :123`、状态 `asrStatus :126`、只读结果 `transcription :127`
- Text to Speech：`ttsInput :132`、`generateSpeech :133`、`ttsStatus :134`、`audioPlayer :135`、`downloadAudio :136`

接口：`POST /asr`（multipart，`main.js:49` 上传 / `:95` 录音；服务端 `srv:routes.py:141-199` 剥 44 字节 WAV 头、int16→float32、`asr_engine.async_transcribe_np`）；`WS /tts-ws`（发 `{text}`，服务端按 `.` 逐句合成回 partial/complete/error，`srv:routes.py:201-252`；前端再 `GET /cache/<file>` 取回，`main.js:149`，`API_BASE_URL = location.origin` `main.js:1`）。录音链：`getUserMedia` 16kHz 单声道（`recorder.js:11-16`）→ MediaRecorder 分块 → `decodeAudioData` + 手写 16-bit PCM WAV（`:11-93`）。**无 VAD**，纯手动 Start/Stop（`isActive() :129` 定义了但没被用）。WAV 编码在 `recorder.js:55-127` 与 `main.js:262-333` **重复实现两份**。

**给谁用**：调 ASR/TTS 参数、验证引擎通不通、抓样本音频。它顺带暴露一个产品事实——**ASR/TTS 能力在主流程之外可以单独调**。（我方通话页有「试听」两行 `heroDrawerRows:88-91`，但没有这种参数级试验台。）

### 5.2 mcp_servers.json：**没有任何编辑 UI**

全仓 grep `mcp_servers` 只有 3 处：`.gitignore:76`（用户本地文件）、`srv:mcpp/server_registry.py:13`（`DEFAULT_CONFIG_PATH = "mcp_servers.json"`）、`:50`（读 `config["mcp_servers"]`）。**frontend bundle 里 `mcp` 命中 0 次**。

- **谁编辑**：用户手改根目录 `mcp_servers.json`（command/args 形式）；开关在 conf.yaml `use_mcpp` / `mcp_enabled_servers`（`srv:config_manager/agent.py:34-35`）。只在启动时加载（`srv:mcpp/server_registry.py:19-60`）。
- **CRUD 路由不存在**（`srv:routes.py` 全部路由：`/client-ws :29`、`/proxy-ws :61`、web-tool 重定向 `:86,:91`、`GET /live2d-models/info :96-139`、`/asr :141`、`/tts-ws :201`）；WS 的 CONFIG 组只有 `fetch-configs` / `switch-config`（切角色 conf，不是 MCP）。
- `doc/` 只有 README + `sample_conf/`（sherpa-onnx 遗留示例，已废弃），**没有任何 UI 文档**。bundle 里也没有 VRM 残留（0 命中）。

结论：OLLMV 的「辅助 UI」= 一个 ASR/TTS 试验页 + 一个只读 REST 端点（`/live2d-models/info` 返回 `{type,count,characters:[{name,avatar,model_path}]}`）+ 手改 json。**没有面向用户的能力管理界面。**

---

## 6. 与我方对照

我方现状取自 companion 代码线（`focused-mclean-1bfa86` @ `c4b3419f` 所在分支；下列路径为仓库相对路径 —— 注意**本会话自己的 worktree 基线是 `95a277ec`，不含这些文件**，锚点在主线/companion 线上读）。

### 6.1 OLLMV 有 / 我们没有

| 能力 | OLLMV 实现 | 我方现状 |
|---|---|---|
| **触摸即动作（0 跳）** | hit area → `tapMotions` 加权随机，PriorityForce（§2.3） | 有触摸区（`dh/AvatarStageTransform.kt:125-280`），但 tap 打开的是**动作托盘**（`AvatarActionTray.kt:35`）——多一跳；反应动作硬编码在 `dh/Live2dTouchReaction.kt:49-110` |
| **触摸反应是数据不是代码** | `model_dict.json` 的 `tapMotions` 权重表 | ❌ 硬编码；wardrobe-spec 里没有 touchReactions 区 |
| **表情词表同源**（manifest emotionMap ⇄ prompt） | `srv:live2d_model.py:48-53` + prompt 注入 | 部分：表情参数在 wardrobe-spec，但没喂给 agent prompt |
| **常驻打断键** | footer 举手键（thinking-speaking 时=打断，否则=催她说话）一键两义 | ❌ `CallControlBar:2642` 只有 静音 / 摄像头(生产禁用 `CompanionRoute.kt:587`) / 文字 / 挂断，**没有打断键**；barge-in 只在协议层 |
| **端侧 VAD + 三阈值可调** | vad-web v5 + 50/35/35 可调（§2.7） | ❌ 无端侧 VAD 设置面（走 LiveKit/服务端；端侧实现未确认） |
| **「催她主动说话」入口** | `allowButtonTrigger` + `ai-speak-signal` | ❌ 无 |
| **把「她看到什么」做成常驻面板** | 相机/屏幕/浏览器 3 tab，帧随消息附带（§2.2） | 部分：相机贴纸是小窗，不是「她看到的」常驻通道 |
| **回传 heard_response 给 memory** | `interrupt-signal {text: fullResponse}` | 有 cancel；「已播出部分」是否回传写 memory **未确认** |
| **错误态做成按钮** | 红徽标 + 「Click to Reconnect」 | 有 `CallDisconnectedBanner:2606`（去登录/重连两条），形态更重 |
| **折叠把手换全屏人物** | 440/120 → 24/24 | 部分：有 `HeroSideDrawer.kt:125-131` 把手，footer 不折叠 |

### 6.2 我们有 / OLLMV 没有

| 能力 | 我方 | OLLMV |
|---|---|---|
| **特效叠加层** | `cmp.cue`（`companion/protocol/ProtocolCatalog.kt:22`）→ `fx/StageEffectMapper.kt:12` → `fx/LottieFxLayer.kt`（挂载处刻意不加 pointerInput）；面板「她·舞台特效」4 项 /「我·相机贴纸」3 项（`dh/DigitalHumanLabHost.kt:519-583`，debug 门 `dh/DhLabGate.kt:8`） | ❌ 完全没有特效层概念 |
| **换装（槽位/候选/默认/约束）** | `scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json` 单一事实源（状态词 active / pending-param / pending-art），消费方 `dh/Live2dTouchReaction.kt:49-110`、`gen-expressions.py:39`、`check_delivery_ready.py:20` | ❌ 无 |
| **多引擎** | `CharacterEngineType { LAYERED, LIVE2D, RIVE, DUIX, TAO }`（`avatar/CharacterAssetPack.kt:11`） | 仅 Live2D（Cubism 2 运行时只躺在 libs 里没接） |
| **版本化资产包 + 校验安装** | `avatar/CharacterAssetPackStore.kt:52`（staging + 逐文件 SHA-256 + 原子 rename，`filesDir/character-assets/{packId}/v{version}`，100MB 上限） | ❌ 模型是裸目录 |
| **分层收纳（抽屉 + 长按托盘 + 右上菜单）** | `HeroSideDrawer.kt:112`（换形象 / 换装轴×N / 试听×2 / 字幕 / 背景 / 声音 只读，纯函数 `heroDrawerRows:60-105`）+ `WaveStyleMenu:969`（9 项） | 只有一个设置抽屉 6 tab |
| **两段式挂断确认** | `HangUpButton:2783` + `pointerInput(armed):2798` | ❌ 无挂断概念 |
| **主题/配色选择** | `CallThemePickerDialog:1093` | ❌ 固定深色 |
| **包生命周期状态词** | 下载中 / 待安装 / 已就绪 / 损坏 / 更新 / 不兼容（`docs/design/2026-11-27-her-pack-camera-effects.md:9`） | ❌ 只有 loading 一个瞬间 |
| **取消契约（speech_id 单调作废 / 墓碑 FIFO）** | `companion-interaction-protocol-v2` §10.3；`2026-11-24-state-cancel-contract-v2` | 有 cancel，无 ID 级作废 |
| **相机小窗可拖动** | `dh/DraggableCameraPreview.kt:25` | 有（Pet 浮层可拖），用途不同 |

### 6.3 一句话对照

**OLLMV 赢在「动作贴脸 + 反应是数据」，我们赢在「她可被换、可被装、可被管」。** 它的紧致不是控件少，而是 **0–1 跳直达互动 + 触摸反应写在 manifest 里**；我们的面板强在资产管理，但**打断这个核心动作在 UI 上是缺的**，触摸还多一跳。

---

## 7. 直接可抄的 UI 模式清单（按性价比排序）

1. **触摸反应声明化：`tapMotions` 加权表**（`model_dict.json` + `sdk:lappmodel.ts:1163-1253` + `web:hooks/canvas/use-live2d-model.ts:354-380`）。
   抄法：在 her pack manifest / wardrobe-spec 里加 `touchReactions: { region: { motionId: weight } }`，把 `Live2dTouchReaction.kt:49-110` 的硬编码搬进数据，照搬两级回落（命中具名区 → 该区权重表；未命中 → 全区权重求和），播放用 Force 优先级。代价低（已有同源纪律），收益大（触摸从「开菜单」变「她立刻回应」，点击深度从 1 → 0）。
2. **tap / drag 判定的双阈值口径**：时长 200ms、位移 5px，且「时长 > 200ms 且位移 > 1px」也算拖拽（`web:hooks/canvas/use-live2d-model.ts:25-26,257`）。我方 `AvatarStageTransform.kt` 采用同组常数即可跨端手感一致。
3. **打断 = 端侧三停 + 一条 heard_response 回传**（`web:hooks/utils/use-interrupt.ts:16-39` + `srv:conversation_handler.py:112-143`）。三停：停当前音频与口型 / 清播放队列 / 置态。回传让 LLM 知道「她被打断前说到哪」。我方 cancel 契约已有本地真停，**缺「已播出部分」回传**。
4. **一键两义的举手键**（`web:hooks/footer/use-footer.ts:35-44`）：正在说话时=打断，空闲时=催她主动说话（受 `allowButtonTrigger` 门控）。给通话页补这一键，同时补掉「没有打断键」和「没有主动触发」两个缺口。
5. **表情随音频同包下发**（`actions.expressions` → `web:hooks/utils/use-audio-task.ts:128-136`）。表情不单独发消息，避免与语音抢序/错位。我方 `cmp.cue` 预算紧（≤1.2KiB、8 条/秒），**把表情 cue 搭在音频侧带**是正解。
6. **idle ⇒ 回落默认表情**（`web:components/canvas/live2d.tsx:49-56`）：状态机回 idle 就复位表情，不等服务端发「恢复」指令。一行 effect，值得照搬。
7. **按句长出来、不做打字机**（`web:context/chat-history-context.tsx:83-111` + `force-new-message`）：文本粒度绑定语音粒度。我方字幕按 chars 截断的真停契约（`state-cancel-contract-v2:30-34`）已是同族做法，补上「连续 AI 文本合并成一条」即可。
8. **错误态做成按钮**：断连徽标变红、文案变「Click to Reconnect」、点击即重连（`web:hooks/canvas/use-ws-status.ts:14-44`）。
9. **折叠把手度量**：440→24px、120→24px、0.3s cubic-bezier(0.4,0,0.2,1)（`sidebar-styles.tsx:42-45`、`layout.tsx:57`）。
10. **头像回退链**：`msg.avatar → confName → 首字母色块`（`chat-history-panel.tsx:114-135`），做 persona 卡片时直接用，省掉缺图处理。

**不要抄**（诚实的反面清单）：

- 全局 Save/Cancel 却只对部分字段生效（角色即时生效、其余落 localStorage）——语义混乱（`setting-ui.tsx:166-173`）。
- `interrupted` 粘滞、无回落计时器（§3 结论）。
- 两套 tap 路径 / 两套 view 手势并存（§2.3、§2.5）。
- 死控件：附件 📎（`footer.tsx:90-96`）、空 TTS tab（`tts.tsx:3-5`）、0 字节 `invite-dialog.tsx`、死字段 `MessageEvent.live2d_model`。
- 角色选择无头像、无预览、无版本（`general.tsx:121-127`）——正是我们 persona/her 双页要超越的地方。
- 版本号硬编码在 UI（`about.tsx:22`）。
- **开关默认值三头写**（服务端不下发 / 设置页显示 `?? false` / tap 逻辑 `!== false`），导致「界面显示关闭但功能实际开启」（§2.4）。我方 `interaction` 段的每个布尔都必须有单一默认值源。

---

## 8. 对 persona / her 双页 UI 的设计启示

前提纪律（已在 `docs/design/2026-11-28-persona-her-split-architecture.md:58` 定）：**选角色 = 她是谁 / 选 her = 她长什么样**，同一组件两条数据源，**不改原 UI**。

### 8.1 OLLMV 给我们最重要的一条：它是反面教材，但机制可拆用

OLLMV 把「她是谁」和「她长什么样」**焊在同一个下拉框里**（General tab 的 `characterPreset`，`general.tsx:121-127`）——模型 url、缩放、表情表、触摸反应全部由所选角色预设连带推送（`websocket-handler.tsx:102-124`）。**它没有独立的换模型入口**（`MessageEvent.live2d_model` 是死字段）。服务端结构也印证：`characters/*.yaml` 的 `character_config` 里 `live2d_model_name` 与 `persona_prompt` 写在同一文件（`conf.yaml:34-46`）。

教训：**焊死让「换个她但保留性格」不可能**——它自己也没意识到该拆。可拆用的是它切换那一下的**闭环机制**，不是它的形态。

### 8.2 页面骨架

**persona 页（她是谁）**

```
┌─────────────────────────────────────────┐
│ 她是谁                          [+ 新建] │
├─────────────────────────────────────────┤
│ (avatar) 米粒                  ●使用中  │  ← 行卡：头像 + 名字 + 一句话人设
│          尖酸刻薄、自信、危险            │
├─────────────────────────────────────────┤
│ (A) 辩论者 Mili                         │
│     前参议员，就核武问题与你辩论          │
└─────────────────────────────────────────┘
```
- 形态：**行卡列表**（不是网格）——persona 是文字性资产，一行读得完。参考 OLLMV 下拉的「一行一项 + 当前项选中态」，但补头像（走首字母回退链）与人设摘要。
- 点一行 = **立即激活**，反馈三件套照抄 OLLMV：徽标进 loading → 服务端推新的呈现 manifest → toast「已切换」。**并明确告知「换角色会开新会话」**（OLLMV 换角色必然 `fetch-history-list` + `create-new-history`，`websocket-handler.tsx:147-148`，它没告知用户——我们必须告知）。
- 不加 Save/Cancel（它的混合语义是反面）。

**her 页（她长什么样）**

```
┌──────────────┬──────────────┬───────────┐
│ [预览缩略图] │ [预览缩略图]  │ [损坏图标] │
│ 日常·夏装    │ 和服（试运行）│ 樱花·冬   │
│ LIVE2D v3    │ RIVE v1      │ LIVE2D v2 │
│ ●已就绪      │ ◐待安装       │ ✕损坏     │  ← 状态徽标 + renderer 徽标
└──────────────┴──────────────┴───────────┘
```
- 形态：**网格卡片**——her 是视觉资产，必须能看到。OLLMV 这里什么都没有（角色下拉无预览），所以卡片形态无对象可抄，**只有缺口可对标**：用户无法预期切过去长什么样。
- 点卡片 = 详情/安装；长按 = 试穿（试穿与保存分离，来自 `her-character-wardrobe-contract.md:69`）。
- 卡片三行信息：缩略图 / 名字 / **徽标行（状态 + renderer + 版本）**。

### 8.3 生命周期徽标：OLLMV 没有，但要抄的是它对「瞬间」的处理

OLLMV 全生命周期只有两个瞬间：`loading` 态 + `config-switched` toast（`websocket-handler.tsx:103,123,135-149`）。徽标缺口就是我们的机会。建议：

| 徽标 | 词 | 视觉 | 依据 |
|---|---|---|---|
| **包状态** | 草稿 / 下载中 / 待安装 / 已就绪 / 损坏 / 更新可用 / 不兼容 | 下载中用进度条（唯一允许常驻动效的徽标） | `her-pack-camera-effects.md:9` 已有这套词；persona/her §L40 `ca_her_pack.status` 七态流程 list→download→verify→install→activate→进房 |
| **renderer** | LIVE2D / RIVE / LAYERED / DUIX / TAO | 中性灰 chip，不带颜色情绪 | `CharacterEngineType`（`avatar/CharacterAssetPack.kt:11`）；注意端上枚举**无 VRM**（待决），徽标别提前放 VRM |
| **使用中** | ●使用中 | 单页最多一个，用实心点而非文字 | OLLMV 用 `Menu.RadioItemGroup value` 表达当前态（`sidebar.tsx:59-77`）——用「选中态」而不是文字 |
| **wardrobe 变体** | active / pending-param / pending-art | pending 行灰显 + **给原因** | `wardrobe-spec.json` 状态词；`fx/StageFxOptions.kt:64-81` 已有「置灰给原因」先例，保持一致 |

两页共用同一套徽标组件（persona/her §L51、L58 已定），差别只在数据源。
配色沿用既有约束：挂断红 `#E5484D` 是页面唯一暖色（`companion-ui-design.md §3` L97-105）、紫粉=她 / 橙=你（`companion-ui-redesign.md` L116-127）——所以「损坏 / 更新可用」这类警示徽标必须避免抢暖色，用中性 + 图标区分。
**一屏只允许一组运动元素**（`companion-ui-design.md §3` L124-129）：列表页里进度条动画与呼吸环不能同时出现。

### 8.4 通话页入口：照抄它的「0 层设置」纪律

- 通话页里**不放**任何 persona/her 管理控件；只保留「换形象」「换装」两条跳转行（收纳盒 `heroDrawerRows:71-87` / 长按托盘行同源），点开是页外跳转或半屏 sheet。这就是 OLLMV 舞台区只有 5 个控件的原因。
- 通话页 **补一个打断键**（§7 第 4 条），并像它一样一键两义（说话中=打断，空闲=催说话）。这是把「紧致」落到我方通话页最便宜的一刀。
- 触摸反应改成 §7 第 1 条的声明式加权表后，**tap 不再需要开托盘**：轻触她 = 她回应；长按 = 打开动作/换装托盘。两层用时长区分（沿用 200ms 阈值 + 长按已在 `AvatarStageTransform.kt:265`）。

### 8.5 manifest 该带上什么（可直接落成字段）

参考 `ModelInfo`（`web:context/live2d-config-context.tsx:35-74`）+ `model_dict.json`，建议我方 her pack manifest 新增 `interaction` 段：

```
interaction: {
  defaultEmotion, idleMotionGroup, talkingMotionGroup,
  emotionMap:   { emotionName → expressionParam },  // 同时注入 agent prompt（抄 emotionMap→emo_str 同源）
  tapReactions: { regionName: { motionId: weight } },
  hitRegions:   [ { id, name } ],                   // id 与 name 都必填
  pointerInteractive: bool, pinchZoom: bool, defaultScale, initialOffset
}
```

理由：OLLMV 证明了「把反应写在 manifest 里」= 加一个模型不改代码。它同时暴露了坑：`HitAreas` 的 `Name` 是空串、`anyhitTest` 只返回 Id（`sdk:lappmodel.ts:888` 甚至反射私有字段），所以 `tapMotions` 只能用 Id 当 key。我们把 region 的 `id` 与 `name` 都定为必填、tapReactions 的 key 用 `name`，就避开它踩的坑。

---

## 9. 待确认（诚实清单）

1. Cubism Core 的精确 semver 小版本（只拿到 Framework 5-r.1 与 Core 构建日期 2024-03-26）。
2. 两套 tap/手势路径在**触屏**上的实际叠加表现（静态确认两条都在注册，未实机验证触发顺序与是否重复播放）。
3. mao_pro 空名动作组 `""` 的实际动作内容（未逐一打开 `.motion3.json`）。
4. 我方侧：端侧是否已有 VAD 实现；cancel 时「已播出部分」是否回传服务端写 memory（§6.1 两条，未确认）。
