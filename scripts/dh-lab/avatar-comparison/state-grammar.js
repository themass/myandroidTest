// state-grammar.js —— P4「状态→表现」映射表(纯数据,无 DOM)。
// 通话页 v2 状态词汇:idle / listening / thinking / speaking / interrupted。
//
// 每个状态 = { 显示名, expression(表情预设名 → 参数预设), motion(动作组 + 优先级 + 轮换周期),
//              behaviors(常驻微行为开关), paramOffsets(叠加在动作之上的参数偏移), mouth(口型门) }。
// 表现层读表执行,不写 if-else 分支;通话页将来直接换这张表即可。
//
// Hiyori 事实(model3.json):动作组只有 "Idle"(9 条)与 "TapBody"(1 条);
// 模型 **没有 Expressions 段**,所以这里的 expression 是本表自定义的「参数预设名」,
// 参数名全部取自 Hiyori.cdi3.json(见 note)。

export const VOWEL_STATES = ['idle', 'listening', 'thinking', 'speaking', 'interrupted'];

// 参数预设:参数名 → 偏移量(叠加在待机动作之上,不覆盖动作本身)。
// 符号约定已在浏览器预览取证:ParamAngleY 正=低头(视线低垂),ParamEyeBallY 负=向上看。
export const EXPRESSIONS = {
  neutral:   {},
  attentive: { ParamAngleZ: 6, ParamEyeLOpen: 0.12, ParamEyeROpen: 0.12 },          // 微微侧头、眼睛略睁大
  think:     { ParamAngleX: -10, ParamAngleY: -6, ParamEyeBallY: -0.55, ParamBrowLY: 0.25, ParamBrowRY: 0.25 }, // 视线上移+眉微抬
  talk:      { ParamEyeLOpen: 0.08, ParamEyeROpen: 0.08 },
  // 被打断:明显可观察 —— 短暂后仰(头后抬 BodyAngleZ)+ 闭嘴 + 视线低垂 + 颊红/耸肩
  startled:  { ParamAngleY: 12, ParamBodyAngleZ: -8, ParamEyeBallY: 0.6, ParamEyeLOpen: -0.25, ParamEyeROpen: -0.25,
               ParamCheek: 0.6, ParamShoulder: 0.5, ParamMouthForm: -0.6 },
};

// mouth 门控:none=不接管;zero=强制闭嘴;closed=闭嘴+嘴角下(被打断);lipsync=由驱动写值。
export const stateGrammar = {
  idle: {
    label: '待机',
    expression: 'neutral',
    motion: { group: 'Idle', priority: 'IDLE', intervalMs: 6000 },
    behaviors: { blink: true, gazeDrift: true, breath: true },
    mouth: 'none',
  },
  listening: {
    label: '倾听',
    expression: 'attentive',
    motion: { group: null },                 // 非 idle 状态:停止 Idle 组轮换(让位)
    behaviors: { blink: true, gazeDrift: false, breath: true },
    mouth: 'zero',
  },
  thinking: {
    label: '思考',
    expression: 'think',
    motion: { group: null },
    behaviors: { blink: true, gazeDrift: false, breath: true },
    mouth: 'zero',
  },
  speaking: {
    label: '说话',
    expression: 'talk',
    motion: { group: null },
    behaviors: { blink: true, gazeDrift: false, breath: true },
    mouth: 'lipsync',
  },
  interrupted: {
    label: '被打断',
    expression: 'startled',
    motion: { group: null },
    holdMs: 1400,                            // 到点自动回落到 next
    next: 'listening',
    behaviors: { blink: false, gazeDrift: false, breath: true },
    mouth: 'closed',
  },
};

/** 当前状态的最终表现(把 expression 预设摊平成参数偏移)。 */
export function resolve(stateName) {
  const st = stateGrammar[stateName] || stateGrammar.idle;
  return { ...st, offsets: EXPRESSIONS[st.expression] || {} };
}
