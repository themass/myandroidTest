// Live2D Cubism 4 官方样例模型 Hiyori,离线本地渲染。
// 运行时:live2dcubismcore.min.js(Live2D 专有,Redistributable Code,经 <script> 先行加载挂全局)
//         + pixi-live2d-display/cubism4(MIT)+ pixi.js v6(MIT)
// 模型:Live2D 官方 CubismWebSamples Hiyori(Free Collection:学习/评估可用,不得商用)。
//
// P4:状态→表现映射(state-grammar.js)+ 待机行为(眨眼/视线漂移/呼吸/Idle 组轮换)。
// P5:口型升级(lipsync-driver.js 元音权重法)+ 程序合成语音(speech-sample.js),
//     旧的 sin 幅度演示保留做对比。
import { Application } from 'pixi.js';
import * as PIXI from 'pixi.js';
window.PIXI = PIXI; // pixi-live2d-display/cubism4 运行时引用全局 PIXI
import { Live2DModel } from 'pixi-live2d-display/cubism4';
import { LipsyncDriver } from './lipsync-driver.js';
import { synthesizeUtterance } from './speech-sample.js';
import { stateGrammar, resolve } from './state-grammar.js';

const statusEl = document.getElementById('status');
const readoutEl = document.getElementById('readout');
const stageEl = document.getElementById('stage');
// 关键坑:不给 width/height 时,Pixi 6 把 canvas 属性与内联样式都定死为默认 800x600,
// CSS 的 width:100% 被内联压过 —— 真机视口只有 390 多 CSS px,800px 画布溢出容器,
// 角色在画布正中,屏幕露出的恰好是它右半边(「贴右被裁」的根因)。resizeTo 在华为
// WebView 上实测未生效,所以尺寸完全手动管:渲染器缓冲 = 容器 CSS px × dpr,内联样式压死为 100%。
const dpr = window.devicePixelRatio || 1;
const app = new Application({ view: stageEl, backgroundAlpha: 0, antialias: true, autoDensity: true, resolution: dpr });

async function load() {
  statusEl.textContent = '加载 Hiyori 模型…';
  const model = await Live2DModel.from('./Hiyori/Hiyori.model3.json', { autoInteract: false });
  window.__fitDiag = () => {
    const v = app.view, m = model;
    return 'view=' + v.clientWidth + 'x' + v.clientHeight + ' canvasPx=' + v.width + 'x' + v.height +
      ' scale=' + m.scale.x.toFixed(3) + ' xy=' + Math.round(m.x) + ',' + Math.round(m.y);
  };
  const fit = () => {
    // 量父容器而不是 canvas 自身:Pixi 把 canvas 内联样式写成逻辑尺寸,
    // canvas.clientWidth 会读回自己(曾恒为默认 800x600,角色在超宽画布正中,
    // 屏幕只露左段 —— 正是「贴右被裁」的根因)。
    const box = stageEl.parentElement;
    const w = box.clientWidth, h = box.clientHeight;
    if (!w || !h) return; // WebView 首帧前尺寸为 0,等 ResizeObserver 再定位
    app.renderer.resize(w, h); // autoDensity:缓冲 = w×dpr,内联样式 = w/h CSS px
    // 用内容包围盒(局部坐标)居中 —— 不猜 Live2DModel 的原点在哪
    // (其绘制原点并非包围盒中心,anchor.set 对它无效)。
    const lb = model.getLocalBounds();
    if (!lb.width || !lb.height) return;
    const s = Math.min(w / lb.width, h / lb.height) * 0.95;
    model.scale.set(s);
    model.x = w / 2 - (lb.x + lb.width / 2) * s;
    model.y = h / 2 - (lb.y + lb.height / 2) * s;
  };
  new ResizeObserver(fit).observe(stageEl.parentElement);
  fit();
  app.stage.addChild(model);

  const core = model.internalModel.coreModel;
  const mm = model.internalModel.motionManager;

  // ---------- P4:状态机(读表,不写死分支)----------
  // 库内置的眨眼/呼吸对象存下来,状态可临时停用(如 interrupted 期间闭眼低垂不许眨)。
  const savedBlink = model.internalModel.eyeBlink;
  const savedBreath = model.internalModel.breath;
  let state = { name: 'idle', source: '初始' };
  let idleTimer = null, holdTimer = null;

  function setState(name, source) {
    if (!stateGrammar[name]) return;
    if (name === state.name && source === state.source) return;
    state = { name, source };
    if (holdTimer) { clearTimeout(holdTimer); holdTimer = null; }
    if (idleTimer) { clearInterval(idleTimer); idleTimer = null; }
    mm.stopAllMotions(); // 切状态时先停当前动作(idle 组自动补位只在 IDLE 优先级下发生)
    const st = resolve(name);
    // 常驻微行为开关
    model.internalModel.eyeBlink = st.behaviors.blink ? savedBlink : null;
    model.internalModel.breath = st.behaviors.breath ? savedBreath : null;
    // 动作组:idle 轮换;其他状态让位(不排 Idle 动作,表情偏移仍持续)
    if (st.motion.group) {
      model.motion(st.motion.group, undefined, st.motion.priority);
      idleTimer = setInterval(() => model.motion(st.motion.group, undefined, st.motion.priority), st.motion.intervalMs);
    }
    // interrupted 等到点自动回落(取证:页面上可见状态文本切换)
    if (st.holdMs) holdTimer = setTimeout(() => setState(st.next, state.name + ' 到期自动回落'), st.holdMs);
    // 进入说话:只有口型源=音频时才播本地合成语音(sin 对比演示不走驱动)
    if (name === 'speaking' && mouthSource === 'audio') startAudio();
    else stopAudio();
    renderStatus();
  }
  function renderStatus() {
    const st = resolve(state.name);
    statusEl.textContent = '状态=' + st.label + '(' + state.name + ') · 触发=' + state.source +
      ' · 表情=' + st.expression + ' · 动作组=' + (st.motion.group || '(让位)');
    document.querySelectorAll('[data-state]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.state === state.name)));
  }
  document.querySelectorAll('[data-state]').forEach(b => {
    b.onclick = () => setState(b.dataset.state, '按钮');
  });

  // 视线跟随触点(陪伴页同款交互的最小版);触摸后 4s 内暂停 idle 的视线漂移
  let lastTouch = 0;
  stageEl.addEventListener('pointermove', e => {
    lastTouch = performance.now();
    const r = stageEl.getBoundingClientRect();
    model.focus(e.clientX - r.left, e.clientY - r.top);
  });

  // idle 视线漂移:每 ~2.5s 换一个随机注视点,缓慢趋近(gazeDrift 开才跑)
  let driftX = 0, driftY = 0, driftAt = 0;
  app.ticker.add(() => {
    const st = resolve(state.name);
    if (!st.behaviors.gazeDrift) return;
    if (performance.now() - lastTouch < 4000) return;
    if (performance.now() > driftAt) {
      driftAt = performance.now() + 1500 + Math.random() * 2000;
      driftX = (Math.random() - 0.5) * 1.2;
      driftY = (Math.random() - 0.5) * 0.8;
    }
    const w = app.view.clientWidth, h = app.view.clientHeight;
    model.focus(w / 2 + driftX * w * 0.4, h / 2 + driftY * h * 0.4);
  });

  // ---------- P5:真音频口型(元音权重法)vs 旧 sin 演示 ----------
  // mouthSource 是显式口型所有权:'audio'=元音权重驱动写参数;'sin'=旧演示直写参数;
  // 两者互斥,避免状态机回落时把 sin 演示的口型归零(曾经的 bug)。
  const driver = new LipsyncDriver(); // 40ms 重算、attack30/release120ms 平滑
  const utter = synthesizeUtterance(); // 本地程序合成,零联网
  let audioCtx = null, audioSrc = null, audioStartAt = 0, feedIdx = 0;
  let lastWeights = null; // 最近一帧驱动输出,供 beforeModelUpdate 写参数
  let mouthSource = 'audio';

  function startAudio() {
    stopAudio();
    audioCtx = audioCtx || new AudioContext();
    audioCtx.resume();
    const buf = audioCtx.createBuffer(1, utter.pcm16.length, utter.sampleRate);
    const ch = buf.getChannelData(0);
    for (let i = 0; i < utter.pcm16.length; i++) ch[i] = utter.pcm16[i] / 32767;
    audioSrc = audioCtx.createBufferSource();
    audioSrc.buffer = buf;
    audioSrc.connect(audioCtx.destination);
    feedIdx = 0;
    audioStartAt = audioCtx.currentTime + 0.05;
    audioSrc.start(audioStartAt);
    audioSrc.onended = () => { if (state.name === 'speaking' && mouthSource === 'audio') setState('listening', '语音播完→倾听'); };
  }
  function stopAudio() {
    if (audioSrc) { audioSrc.onended = null; try { audioSrc.stop(); } catch (_) {} audioSrc = null; }
  }

  // 每个 rAF 把已播放到的 PCM 喂进驱动(帧率与播放进度锁死,不会出现音画错位)
  app.ticker.add(() => {
    if (!audioSrc) return;
    const t = Math.min(utter.pcm16.length, Math.floor((audioCtx.currentTime - audioStartAt) * utter.sampleRate));
    if (t <= feedIdx) return;
    const frames = driver.pushSamples(utter.pcm16.subarray(feedIdx, t));
    feedIdx = t;
    if (frames.length) lastWeights = frames[frames.length - 1];
  });

  // 表现层总出口:在库完成动作求值之后、模型最终 update 之前,叠加状态表偏移 + 口型门控。
  // (beforeModelUpdate 在 motion 写参数与 saveParameters 之后、physics 之前触发,
  //  所以这里的 set/add 能压过动作曲线里的 ParamMouthOpenY,又不破坏物理摆动。)
  const CAPTURE_IDS = ['ParamAngleY', 'ParamBodyAngleZ', 'ParamEyeBallY', 'ParamCheek', 'ParamShoulder', 'ParamMouthForm', 'ParamMouthOpenY'];
  let captured = null, capturedAt = 0;
  model.internalModel.on('beforeModelUpdate', () => {
    const st = resolve(state.name);
    for (const id in st.offsets) core.addParameterValueById(id, st.offsets[id]);
    if (mouthSource === 'sin') { /* sin 演示直接写参数,钩子不接管 */ }
    else if (st.mouth === 'zero' || st.mouth === 'closed') core.setParameterValueById('ParamMouthOpenY', 0);
    else if (st.mouth === 'lipsync' && lastWeights) {
      core.setParameterValueById('ParamMouthOpenY', Math.min(1, lastWeights.openness));
    }
    // 取证:Cubism 在 update 后 loadParameters 会把基值恢复,帧末读到的是基值而不是
    // 渲染值 —— 所以在钩子内(model.update 之前)抓一份实际生效参数。
    if (capturedAt === 0 || performance.now() - capturedAt > 200) {
      capturedAt = performance.now();
      captured = {};
      for (const id of CAPTURE_IDS) captured[id] = +(core.getParameterValueById(id) || 0).toFixed(2);
    }
  });

  // readout:状态 + 权重(每 ~150ms 刷一次,真机截图可取证)
  let readAt = 0;
  app.ticker.add(() => {
    if (performance.now() < readAt) return;
    readAt = performance.now() + 150;
    const st = resolve(state.name);
    if (mouthSource === 'sin') {
      readoutEl.textContent = '口型源=sin 演示(仅幅度,无元音权重)';
    } else if (lastWeights && st.mouth === 'lipsync') {
      const w = lastWeights.weights;
      readoutEl.textContent = '权重 A' + w.A.toFixed(2) + ' E' + w.E.toFixed(2) + ' I' + w.I.toFixed(2) +
        ' O' + w.O.toFixed(2) + ' U' + w.U.toFixed(2) + ' · 主=' + lastWeights.dominant +
        ' · 开度=' + lastWeights.openness.toFixed(2) + ' · rms=' + lastWeights.rms.toFixed(3);
    } else {
      readoutEl.textContent = '口型模式=' + st.mouth;
    }
  });

  // 旧 sin 幅度演示(对比用):直写 ParamMouthOpenY,无频谱信息。
  // 不进入状态机 —— 它只是口型所有权的切换,状态保持原样,避免两演示互相抢占。
  let talking = false, timer = null;
  document.getElementById('talk').onclick = e => {
    talking = !talking;
    e.currentTarget.textContent = talking ? '停止 sin 演示' : 'sin 幅度演示(对比)';
    mouthSource = talking ? 'sin' : 'audio';
    if (timer) { clearInterval(timer); timer = null; }
    if (talking) {
      stopAudio();
      timer = setInterval(() => {
        const v = Math.max(0, Math.sin(performance.now() / 90)) * (0.6 + Math.random() * 0.3);
        core.setParameterValueById('ParamMouthOpenY', v);
      }, 80);
    } else {
      core.setParameterValueById('ParamMouthOpenY', 0);
    }
  };
  // 真音频驱动开关:再点一次停止
  document.getElementById('audio').onclick = e => {
    if (talking) { talking = false; if (timer) clearInterval(timer); timer = null; document.getElementById('talk').textContent = 'sin 幅度演示(对比)'; }
    if (state.name === 'speaking' && audioSrc) { setState('listening', '手动停止音频'); e.currentTarget.textContent = '真音频口型(元音权重)'; }
    else { mouthSource = 'audio'; setState('speaking', '按钮→本地合成语音'); e.currentTarget.textContent = '停止音频'; }
  };
  document.getElementById('motion').onclick = () => {
    model.motion('TapBody'); // 官方点击身体动作组
    statusEl.textContent = '播放官方动作组 TapBody';
  };

  setState('idle', '初始');
  // 取证钩子:浏览器/真机 console 可直接读内部驱动状态(不参与渲染逻辑);
  // __lab 暴露 app,后台页 RAF 被节流时可手动 __lab.app.ticker.update(t) 驱动帧。
  window.__p45 = () => ({
    state, weights: lastWeights, feedIdx, captured, mouthSource,
    audioTime: audioCtx ? audioCtx.currentTime : null, audioStartAt,
    ctxState: audioCtx ? audioCtx.state : 'none',
    mouth: resolve(state.name).mouth,
  });
  window.__lab = { app, model, driver, utter };
  statusEl.textContent = 'Hiyori 加载完成 · 触摸移动视线 · 点状态按钮切换表现';
}
load().catch(err => { console.error(err); statusEl.textContent = '加载失败:' + err.message; });
