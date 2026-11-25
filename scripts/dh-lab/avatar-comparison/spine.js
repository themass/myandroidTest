// Spine 官方样例 spineboy,离线本地渲染。
// 运行时:@esotericsoftware/spine-webgl 4.3(Spine Runtimes 专有许可,内部评估用)。
// 素材:spineboy-pro.json / spineboy-pma.atlas / spineboy-pma.png(esotericsoftware.com 官方示例)。
// API 依据:node_modules/@esotericsoftware/spine-{core,webgl}/dist/*.d.ts 与 spine-webgl 官方 example。
import {
  AssetManager, ManagedWebGLRenderingContext, SceneRenderer, ResizeMode,
  AtlasAttachmentLoader, SkeletonJson, Skeleton, AnimationStateData, AnimationState, Physics,
} from '@esotericsoftware/spine-webgl';

const canvas = document.getElementById('stage');
const statusEl = document.getElementById('status');
const context = new ManagedWebGLRenderingContext(canvas);
const renderer = new SceneRenderer(canvas, context);

async function load() {
  statusEl.textContent = '加载 spineboy 素材…';
  const assets = new AssetManager(context, './');
  assets.loadJson('spineboy-pro.json');
  assets.loadTextureAtlas('spineboy-pma.atlas');
  await assets.loadAll();
  if (assets.hasErrors()) throw new Error(JSON.stringify(assets.getErrors()));

  const atlas = assets.get('spineboy-pma.atlas');
  const skeletonData = new SkeletonJson(new AtlasAttachmentLoader(atlas)).readSkeletonData(assets.get('spineboy-pro.json'));
  const stateData = new AnimationStateData(skeletonData);
  stateData.defaultMix = 0.25; // 动作切换混合,官方示例同款做法
  const skeleton = new Skeleton(skeletonData);
  const state = new AnimationState(stateData);

  // 相机:正交;zoom>1 拉远。骨架数据 bounds x:-188.6 y:-7.9 w:418 h:686 → 中心约 (21,335)
  renderer.resize(ResizeMode.Expand);
  renderer.camera.position.set(21, 335, 0);
  const fit = () => { if (canvas.height) renderer.camera.zoom = 860 / canvas.height; }; // 首帧尺寸为 0 时跳过,等 Expand/下帧再算
  fit();
  new ResizeObserver(fit).observe(canvas);

  const anims = skeletonData.animations.map(a => a.name);
  const nav = document.getElementById('anims');
  let current = 'idle';
  const play = name => {
    current = name;
    state.setAnimation(0, name, name !== 'death' && name !== 'jump');
    statusEl.textContent = '播放:' + name + '(共 ' + anims.length + ' 组官方动作)';
    nav.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.name === name)));
  };
  for (const name of anims) {
    const b = document.createElement('button');
    b.textContent = name; b.dataset.name = name;
    b.setAttribute('aria-pressed', String(name === 'idle'));
    b.onclick = () => play(name);
    nav.appendChild(b);
  }
  play('idle');

  let last = performance.now(), fpsFrames = 0, fpsSince = last;
  const fpsEl = document.getElementById('fps');
  function frame(now) {
    const dt = Math.min((now - last) / 1000, 1 / 30); last = now;
    skeleton.update(dt);
    state.apply(skeleton);
    skeleton.updateWorldTransform(Physics.update); // 官方 4.3 渲染循环
    renderer.begin();
    renderer.drawSkeleton(skeleton);
    renderer.end();
    fpsFrames++;
    if (now - fpsSince > 1000) { fpsEl.textContent = Math.round(fpsFrames * 1000 / (now - fpsSince)) + ' fps'; fpsFrames = 0; fpsSince = now; }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  statusEl.textContent = 'spineboy 加载完成 · 点按切换官方动作';

  // 生产 Lab 桥（feature-companion SpineDigitalHumanEngine）：JSON 线格式与
  // AvatarSceneBridge.encode 一字不差；未知动画名只报状态、不瞎演（不回落 idle——
  // Kotlin 侧 SpineSceneBridge 只发词表内的名字，走到这里说明两边没同步）。
  window.dhCommand = (payload) => {
    try {
      const c = JSON.parse(payload);
      if (c.type !== 'animation') return; // spineboy 没有表情/口型通道
      if (anims.includes(c.name)) play(c.name);
      else statusEl.textContent = '未知动作:' + c.name;
    } catch (e) { statusEl.textContent = 'dhCommand 解析失败:' + e.message; }
  };
  if (window.DhSceneNative) DhSceneNative.onReady();
}
load().catch(err => { statusEl.textContent = '加载失败:' + err.message; });
