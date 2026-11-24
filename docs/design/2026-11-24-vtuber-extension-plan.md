# vtuber 方案拓展:AIRI / Open-LLM-VTuber / ChatWaifu 对我们的可落地项

日期:2026-11-24。调研全文见共享笔记 `ai-vtuber-research-airi-ollmv-20261124.md`;
本文只回答一件事:**这些项目里哪些东西能直接拓展我们的通话页/陪伴方案,按什么顺序做**。

## 0. 一句话结论

两个明星项目(AIRI 5.2 万星、Open-LLM-VTuber 1.5 万星)打动人的不是引擎,而是三件工程事:
**角色状态是显式事件契约、打断是「取消并清空」而不是静音、语音按句流式开播**。
这三件都能落到我们现有的 Live2D 通话页 + companion-agent 服务端上,不需要换渲染引擎。
avatar-lab(五个离线示例页,今日已全部随包可跑)是验证 richer 动作/VRM 路线的沙盒。

## 1. 参考项目各自的价值定位

| 项目 | 我们看到什么 | 对我们的用法 |
|---|---|---|
| AIRI (moeru-ai) | Live2D+VRM 双渲染、`model-driver-lipsync`(A/E/I/O/U 元音权重 40ms 重算/120ms 插值)、音频管线 queue/interrupt/replace 意图、角色卡+条目化记忆 ADR | 状态契约与口型驱动的设计参照;不引入其代码栈(TypeScript/web,与我们 Android 原生不同构) |
| Open-LLM-VTuber | 逐句 SentenceOutput→并行 TTS→按序号有序播放;打断=停音频+停口型+清队列+只保留已说话前缀;emotionMap 表情映射 | 打断语义与服务端流式分句的最贴近参照(MIT,可细读源码) |
| ChatWaifu_Mobile | 我们生产 Live2D 路线的同构参考(Android + Live2D Cubism);其模型加载/参数驱动与通话页原生链路同类 | 已确认:生产参考的 Live2D 项目就是它;维持现有原生渲染路线 |
| avatar-lab(我们) | 五页离线:VRM 三模式 / Hiyori / spineboy / VRMA / TalkingHead mpfb | 官方样例即验收样本;VRMA 证明「动画文件与角色分离」可复用;mpfb 66 个 ARKit/Oculus blendshape 是 3D 口型上限参照 |

## 2. 按优先级的拓展项(成本 S/M/L)

**P1 · 角色状态显式事件(成本 M,通话页 + 服务端)**
把 listening / thinking / speaking / interrupted / idle 作为服务端→端的最小事件契约,
带 emotion 与说话段元数据。两个项目都把「生成 vs 播放 vs 打断」做成显式状态;
我们现在是散点推断。这条也直接修复 F16 里「静默误判」类问题的可观测性。

**P2 · 真取消式打断(成本 M,通话页 + 服务端)**
打断 = 停本地音频 + 停口型动画 + 取消队列中/进行中的 TTS + 服务端按回合 ID 取消 +
历史只存已说话前缀。OLLV 的 use-interrupt/use-audio-task 是可直接对照的实现样本。
我们现有 barge-in 只做了「不再播」,没做「清队列 + 取消生成」,这是体感差距的第一来源。

**P3 · 首句流式开播(成本 L,服务端为主)**
LLM 流式文本按句切分,首句完成即 TTS,并发生成、按序播放。体感延迟立减。
OLLV/AIRI 管线同构;需要服务端 companion-agent 改动 + 端上播放队列,属服务端线工作。

**P4 · 表情/待机动作语法(成本 S;做素材则 M,通话页)**
把 P1 的状态机映射到现有模型可用 expression/motion 名;无语音时跑眨眼/视线漂移/呼吸,
有动作时让位。Hiyori 离线页已演示同一参数面(Idle 组轮换 + focus() + ParamMouthOpenY)。

**P5 · 口型驱动升级(成本 S 幅度法 / M 元音权重)**
现状:直写 ParamMouthOpenY。下一步照 AIRI model-driver-lipsync 思路加 A/E/I/O/U 权重
+平滑;avatar-lab 的 TalkingHead 页已验证 viseme 时间轴(setFixedValue + lipsync-en 词→viseme)
在无网络下可行,是服务端音素对齐到来前的兜底。

**P6 · 角色卡 + 窄记忆(成本 M/L,服务端)**
身份/声音偏好/关系边界做成可版本化角色卡;记忆=每条一个事实+紧凑索引,可观可改可忘。
AIRI 的 ADR(2026-10-10)是设计参照,其自身标注「真模型效果未验证」——我们按
确定性策略起步,不追其复杂度。

## 3. 明确不做的

- 不换生产渲染引擎:通话页维持 Android 原生 Live2D(ChatWaifu 同构路线已验证)。
- 不引入 AIRI/OLLV 代码栈:参照设计,不搬运 web/TS 实现。
- VRM 全身进生产暂缓:avatar-lab 的 VRMA 页先验证「动画资产复用」价值,再议。

## 4. 验收挂点

- P1/P2 落地后:通话页真机拨测一轮,取证口径含「打断后 2s 内本地完全静默、历史无未说话尾巴」。
- P4/P5 先在 avatar-lab 取证(五页现已零联网、逐页 60fps+),再移植通话页。
- avatar-lab 本身:五页 = 本地集成验收面(EngineCatalog 5 LOCAL + 校验测试已在主线 b0a1736e)。
