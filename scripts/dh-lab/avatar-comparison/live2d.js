// Live2D Cubism 4 官方样例模型 Hiyori,离线本地渲染。
// 运行时:live2dcubismcore.min.js(Live2D 专有,Redistributable Code,经 <script> 先行加载挂全局)
//         + pixi-live2d-display/cubism4(MIT)+ pixi.js v6(MIT)
// 模型:Live2D 官方 CubismWebSamples Hiyori(Free Collection:学习/评估可用,不得商用)。
import { Application } from 'pixi.js';
import * as PIXI from 'pixi.js';
window.PIXI = PIXI; // pixi-live2d-display/cubism4 运行时引用全局 PIXI
import { Live2DModel } from 'pixi-live2d-display/cubism4';

const statusEl = document.getElementById('status');
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
  model.motion('Idle'); // 官方待机动作组(9 条 motion3 随机取一)
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

  // 视线跟随触点(陪伴页同款交互的最小版)
  stageEl.addEventListener('pointermove', e => {
    const r = stageEl.getBoundingClientRect();
    model.focus(e.clientX - r.left, e.clientY - r.top);
  });

  // 待机动作轮换:官方 Idle 组
  setInterval(() => { model.motion('Idle'); }, 6000);

  // 口型:直写 Cubism 参数 ParamMouthOpenY(与通话页原生链路同一参数面)
  let talking = false, timer = null;
  document.getElementById('talk').onclick = e => {
    talking = !talking;
    e.currentTarget.textContent = talking ? '停止口型' : '口型演示(无语音)';
    if (timer) { clearInterval(timer); timer = null; }
    if (!talking) model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', 0);
    else timer = setInterval(() => {
      const v = Math.max(0, Math.sin(performance.now() / 90)) * (0.6 + Math.random() * 0.3);
      model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', v);
    }, 80);
  };
  document.getElementById('motion').onclick = () => {
    model.motion('TapBody'); // 官方点击身体动作组
    statusEl.textContent = '播放官方动作组 TapBody';
  };
  statusEl.textContent = 'Hiyori 加载完成 · 触摸移动视线';
}
load().catch(err => { statusEl.textContent = '加载失败:' + err.message; });
