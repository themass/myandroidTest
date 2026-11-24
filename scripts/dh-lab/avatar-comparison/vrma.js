// VRMA 全身动画:官方 pixiv test.vrma 驱动 CC0 HairSample_Female,离线本地渲染。
// 运行时:three 0.187.0 + @pixiv/three-vrm + @pixiv/three-vrm-animation(均 MIT)。
// 用法与官方示例 packages/three-vrm-animation/examples/dnd.html 一致
// (https://github.com/pixiv/three-vrm/blob/dev/packages/three-vrm-animation/examples/dnd.html)。
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { createVRMAnimationClip, VRMAnimationLoaderPlugin, VRMLookAtQuaternionProxy } from '@pixiv/three-vrm-animation';

const canvas = document.querySelector('canvas');
const status = document.querySelector('#status');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.15;
const scene = new THREE.Scene();
scene.background = new THREE.Color('#e6e9ee');
const camera = new THREE.PerspectiveCamera(30, 1, 0.01, 100);
const controls = new OrbitControls(camera, canvas);
controls.enableDamping = true;
// 与「本地 3D 场景」同款三点布光,保证观感可横向比较
scene.add(new THREE.HemisphereLight(0xf4f7ff, 0x6e737c, 2.3));
const key = new THREE.DirectionalLight(0xfff1e1, 2.4); key.position.set(-2, 3, 4); scene.add(key);
const fill = new THREE.DirectionalLight(0xdbe8ff, 1.3); fill.position.set(3, 1, 2); scene.add(fill);
const rim = new THREE.DirectionalLight(0xffffff, 1.5); rim.position.set(0, 3, -3); scene.add(rim);
const floor = new THREE.Mesh(new THREE.CircleGeometry(4, 64), new THREE.MeshStandardMaterial({ color: 0xc7cdd5, roughness: 1 }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -0.01; scene.add(floor);

const loader = new GLTFLoader();
loader.register(parser => new VRMLoaderPlugin(parser));
loader.register(parser => new VRMAnimationLoaderPlugin(parser));

let vrm = null, vrmAnimation = null, mixer = null, action = null;

// 官方 dnd.html 接线:lookAt 四元数代理挂进场景,VRMA 的 lookAt 轨道驱动它
function wireVrm(model) {
  if (vrm) { scene.remove(vrm.scene); VRMUtils.deepDispose(vrm.scene); }
  vrm = model;
  const proxy = new VRMLookAtQuaternionProxy(vrm.lookAt);
  proxy.name = 'lookAtQuaternionProxy';
  vrm.scene.add(proxy);
  vrm.scene.traverse(obj => { obj.frustumCulled = false; });
  VRMUtils.rotateVRM0(vrm);
  scene.add(vrm.scene);
  const box = new THREE.Box3().setFromObject(vrm.scene);
  const center = new THREE.Vector3(); box.getCenter(center);
  const height = Math.max(box.max.y - box.min.y, 0.1);
  controls.target.set(center.x, center.y + height * 0.1, center.z);
  camera.position.set(center.x, center.y + height * 0.15, center.z + height * 2.2);
  initClip();
}
function wireVrma(gltf) {
  vrmAnimation = gltf.userData.vrmAnimations?.[0] ?? null;
  if (!vrmAnimation) throw new Error('文件不是可识别的 VRMA 动画');
  initClip();
}
function initClip() {
  if (!vrm || !vrmAnimation) return;
  if (mixer) { mixer.stopAllAction(); mixer.uncacheRoot(vrm.scene); }
  mixer = new THREE.AnimationMixer(vrm.scene);
  const clip = createVRMAnimationClip(vrmAnimation, vrm);
  action = mixer.clipAction(clip);
  action.play();
  mixer.timeScale = 1;
  vrm.humanoid.resetNormalizedPose();
  vrm.lookAt.reset();
  vrm.lookAt.autoUpdate = vrmAnimation.lookAtTrack != null;
  status.textContent = `动画已挂载:${clip.name || 'test.vrma'}(${clip.duration.toFixed(1)} 秒,含骨骼 + 表情「happy」+ 视线三条轨道)`;
}

document.getElementById('pause').onclick = e => {
  if (!action) return;
  const paused = action.paused;
  action.paused = !paused;
  e.currentTarget.textContent = paused ? '暂停' : '继续';
};
document.getElementById('speed').onclick = e => {
  if (!mixer) return;
  const steps = [0.5, 1, 2];
  mixer.timeScale = steps[(steps.indexOf(mixer.timeScale) + 1) % steps.length];
  e.currentTarget.textContent = '速度 ' + mixer.timeScale + '×';
  status.textContent = '播放速度 ' + mixer.timeScale + '×';
};
document.getElementById('reset').onclick = () => {
  if (!vrm) return;
  if (mixer) { mixer.stopAllAction(); mixer.uncacheRoot(vrm.scene); mixer = null; action = null; }
  vrm.humanoid.resetNormalizedPose();
  vrm.lookAt.autoUpdate = false;
  status.textContent = '已停止 VRMA,回到静止姿态(角色本身与「本地 3D 场景」同一模型)';
};

function resize() {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== w || canvas.height !== h) { renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); }
}
let fpsFrames = 0, fpsSince = performance.now();
const clock = new THREE.Clock(); clock.start();
function frame() {
  requestAnimationFrame(frame);
  resize();
  const dt = clock.getDelta();
  if (mixer) mixer.update(dt);
  if (vrm) vrm.update(dt);
  controls.update();
  renderer.render(scene, camera);
  fpsFrames++;
  const now = performance.now();
  if (now - fpsSince > 1000) { document.getElementById('fps').textContent = Math.round(fpsFrames * 1000 / (now - fpsSince)) + ' fps'; fpsFrames = 0; fpsSince = now; }
}
frame();

(async () => {
  status.textContent = '加载 HairSample_Female…';
  // 与「本地 3D 场景」共用同一个 VRM 文件,避免 APK 内重复 17MB
  wireVrm((await loader.loadAsync('../scene/sample.vrm')).userData.vrm);
  status.textContent = '加载 test.vrma…';
  wireVrma(await loader.loadAsync('./test.vrma'));
})().catch(err => { status.textContent = '加载失败:' + err.message; });
