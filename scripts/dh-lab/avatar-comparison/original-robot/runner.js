import { Application } from 'pixi.js';
import * as PIXI from 'pixi.js';
import { Live2DModel } from 'pixi-live2d-display/cubism4';
import {
  AssetManager, ManagedWebGLRenderingContext, SceneRenderer, ResizeMode,
  AtlasAttachmentLoader, SkeletonJson, Skeleton, AnimationStateData, AnimationState, Physics,
} from '@esotericsoftware/spine-webgl';

window.PIXI = PIXI;
const status = document.getElementById('status');
const base = new URL('./', location.href);
const join = (root, leaf) => new URL(leaf, new URL(root, base)).href;
const ctx = { smile: false, cape: false, motion: 'idle' };
const dpr = window.devicePixelRatio || 1;

async function startLive2D() {
  const canvas = document.getElementById('stage-a');
  const app = new Application({ view: canvas, backgroundAlpha: 0, antialias: true, autoDensity: true, resolution: dpr });
  const model = await Live2DModel.from(join('live2d/', 'layout.model3.json'), { autoInteract: false });
  const fit = () => {
    const box = canvas.parentElement;
    if (!box.clientWidth || !box.clientHeight) return;
    app.renderer.resize(box.clientWidth, box.clientHeight);
    const bounds = model.getLocalBounds();
    if (!bounds.width || !bounds.height) return;
    const scale = Math.min(box.clientWidth / bounds.width, box.clientHeight / bounds.height) * 0.94;
    model.scale.set(scale);
    model.x = box.clientWidth / 2 - (bounds.x + bounds.width / 2) * scale;
    model.y = box.clientHeight / 2 - (bounds.y + bounds.height / 2) * scale;
  };
  new ResizeObserver(fit).observe(canvas.parentElement);
  app.stage.addChild(model);
  fit();
  model.internalModel.on('beforeModelUpdate', () => {
    const core = model.internalModel.coreModel;
    core.setParameterValueById('ParamFace_smile', ctx.smile ? 1 : 0);
    core.setParameterValueById('ParamWear_cape', ctx.cape ? 1 : 0);
  });
  return { model, app };
}

async function startSpine() {
  const canvas = document.getElementById('stage-b');
  const context = new ManagedWebGLRenderingContext(canvas);
  const renderer = new SceneRenderer(canvas, context);
  renderer.resize(ResizeMode.Expand);
  const assets = new AssetManager(context, join('spine/', ''));
  assets.loadJson('original-robot.json');
  assets.loadTextureAtlas('original-robot.atlas');
  await assets.loadAll();
  if (assets.hasErrors()) throw new Error(JSON.stringify(assets.getErrors()));
  const atlas = assets.get('original-robot.atlas');
  const data = new SkeletonJson(new AtlasAttachmentLoader(atlas)).readSkeletonData(assets.get('original-robot.json'));
  const skeleton = new Skeleton(data);
  const stateData = new AnimationStateData(data);
  stateData.defaultMix = 0.25;
  const state = new AnimationState(stateData);
  window.__robotBlindTest = { skeleton, state, renderer, atlas, data, context };
  const fit = () => {
    const box = canvas.parentElement;
    if (!box.clientWidth || !box.clientHeight) return;
    renderer.resize(ResizeMode.Expand);
    renderer.camera.position.set(320, 448, 0);
    renderer.camera.zoom = 920 / canvas.height;
  };
  new ResizeObserver(fit).observe(canvas.parentElement);
  fit();
  const applyAppearance = () => {
    skeleton.setAttachment('mouth', ctx.smile ? 'mouth_smile' : 'mouth_neutral');
    skeleton.setAttachment('cape', ctx.cape ? 'cape' : null);
  };
  const play = name => {
    ctx.motion = name;
    state.setAnimation(0, name, name !== 'nod');
  };
  applyAppearance();
  play(ctx.motion);
  let last = performance.now();
  const frame = now => {
    const dt = Math.min((now - last) / 1000, 1 / 30); last = now;
    state.update(dt); skeleton.update(dt); state.apply(skeleton); skeleton.updateWorldTransform(Physics.update);
    applyAppearance(); renderer.begin(); renderer.drawSkeleton(skeleton); renderer.end();
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
  return { play };
}

async function main() {
  try {
    const [live, spine] = await Promise.all([startLive2D(), startSpine()]);
    const setAppearance = state => {
      ctx.smile = state === 'smile' || state === 'smile-cape';
      ctx.cape = state === 'cape' || state === 'smile-cape';
      document.querySelectorAll('[data-state]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.state === state)));
    };
    document.querySelectorAll('[data-state]').forEach(button => button.addEventListener('click', () => setAppearance(button.dataset.state)));
    document.querySelectorAll('[data-motion]').forEach(button => button.addEventListener('click', () => {
      const name = button.dataset.motion;
      spine.play(name);
      const groups = { idle: 'Idle', nod: 'Nod', talk: 'Talk' };
      const motion = groups[name];
      if (motion && live.model.internalModel.motionManager.groups[motion]) live.model.motion(motion);
      document.querySelectorAll('[data-motion]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    }));
    status.textContent = 'A/B 已加载 · 可同步切换表情、披风与动作';
  } catch (error) {
    console.error(error);
    status.textContent = '加载失败：' + error.message;
  }
}
main();
