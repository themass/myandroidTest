// Live2D Cubism 4 官方样例模型 Hiyori,离线本地渲染。
// 运行时:live2dcubismcore.min.js(Live2D 专有,Redistributable Code,经 <script> 先行加载挂全局)
//         + pixi-live2d-display/cubism4(MIT)+ pixi.js v6(MIT)
// 模型:Live2D 官方 CubismWebSamples Hiyori(Free Collection:学习/评估可用,不得商用)。
import { Application } from 'pixi.js';
import * as PIXI from 'pixi.js';
window.PIXI = PIXI; // pixi-live2d-display/cubism4 运行时引用全局 PIXI
import { Live2DModel } from 'pixi-live2d-display/cubism4';

const statusEl = document.getElementById('status');
const app = new Application({ view: document.getElementById('stage'), backgroundAlpha: 0, antialias: true, autoDensity: true });

async function load() {
  statusEl.textContent = '加载 Hiyori 模型…';
  const model = await Live2DModel.from('./Hiyori/Hiyori.model3.json', { autoInteract: false });
  model.motion('Idle'); // 官方待机动作组(9 条 motion3 随机取一)
  const fit = () => {
    const scale = Math.min(app.screen.width / model.width, app.screen.height / model.height) * 0.95;
    model.scale.set(scale);
    model.x = (app.screen.width - model.width) / 2;
    model.y = (app.screen.height - model.height) / 2;
  };
  fit();
  app.stage.addChild(model);

  // 视线跟随触点(陪伴页同款交互的最小版)
  const stageEl = document.getElementById('stage');
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
