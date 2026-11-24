import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

// API sources: https://threejs.org/docs/ and https://pixiv.github.io/three-vrm/docs/
const canvas = document.querySelector('canvas');
const status = document.querySelector('#status');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.15;
const scene = new THREE.Scene();
scene.background = new THREE.Color('#e6e9ee');
const camera = new THREE.PerspectiveCamera(30, 1, 0.01, 100);
const controls = new OrbitControls(camera, canvas);
controls.enableDamping = true;
controls.minDistance = 0.3;
controls.maxDistance = 8;
scene.add(new THREE.HemisphereLight(0xf4f7ff, 0x6e737c, 2.3));
const key = new THREE.DirectionalLight(0xfff1e1, 2.4);
key.position.set(-2, 3, 4); scene.add(key);
const fill = new THREE.DirectionalLight(0xdbe8ff, 1.3);
fill.position.set(3, 1, 2); scene.add(fill);
const rim = new THREE.DirectionalLight(0xffffff, 1.5);
rim.position.set(0, 3, -3); scene.add(rim);
const floor = new THREE.Mesh(new THREE.CircleGeometry(4, 64), new THREE.MeshStandardMaterial({ color: 0xc7cdd5, roughness: 1 }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -0.01; scene.add(floor);
const gaze = new THREE.Object3D(); gaze.position.set(0, 1.4, 4); scene.add(gaze);
const loader = new GLTFLoader();
loader.register(parser => new VRMLoaderPlugin(parser));
let vrm = null, mode = 'companion', speaking = false, expression = 'neutral', turn = false;
let elapsed = 0, height = 1.6, center = new THREE.Vector3(), loadId = 0;
let fpsFrames = 0, fpsSince = performance.now();
const baseRotations = new Map();
function poseBone(name, x = 0, y = 0, z = 0) {
  const bone = vrm?.humanoid.getNormalizedBoneNode(name);
  if (!bone) return;
  const base = baseRotations.get(name);
  if (base) bone.quaternion.copy(base);
  bone.rotateX(x); bone.rotateY(y); bone.rotateZ(z);
}
function frame() {
  if (!vrm) return;
  const full = mode === 'body';
  const targetY = full ? center.y : height * (mode === 'lecture' ? 0.65 : 0.81);
  controls.target.set(center.x, targetY, center.z);
  const distance = height * (full ? 2.25 : mode === 'lecture' ? 1.35 : 0.84);
  camera.position.set(center.x, targetY + height * 0.025, center.z + distance);
  controls.update();
}
async function loadModel(url, label) {
  const request = ++loadId;
  status.textContent = `正在加载 ${label}…`;
  try {
    const gltf = await loader.loadAsync(url);
    const model = gltf.userData.vrm;
    if (!model) throw new Error('文件不是可识别的 VRM 模型');
    if (request !== loadId) { VRMUtils.deepDispose(model.scene); return; }
    if (vrm) { scene.remove(vrm.scene); VRMUtils.deepDispose(vrm.scene); }
    vrm = model; VRMUtils.rotateVRM0(vrm);
    scene.add(vrm.scene);
    vrm.scene.traverse(o => { if (o.isMesh) o.frustumCulled = false; });
    baseRotations.clear();
    for (const name of ['head', 'neck', 'spine', 'leftUpperArm', 'rightUpperArm', 'leftLowerArm', 'rightLowerArm']) {
      const bone = vrm.humanoid.getNormalizedBoneNode(name);
      if (bone) baseRotations.set(name, bone.quaternion.clone());
    }
    poseBone('leftUpperArm', 0, 0, -1.08);
    poseBone('rightUpperArm', 0, 0, 1.08);
    vrm.update(0);
    const box = new THREE.Box3().setFromObject(vrm.scene);
    height = Math.max(box.max.y - box.min.y, 0.1);
    box.getCenter(center);
    vrm.scene.position.y -= box.min.y;
    center.y -= box.min.y;
    if (vrm.lookAt) vrm.lookAt.target = gaze;
    frame();
    const available = ['happy', 'sad', 'angry', 'relaxed', 'surprised'].filter(name => vrm.expressionManager?.getExpression(name));
    document.querySelectorAll('[data-expression]').forEach(button => {
      button.disabled = button.dataset.expression !== 'neutral' && !available.includes(button.dataset.expression);
    });
    status.textContent = `${label} · 加载完成 · ${available.length} 种可用表情`;
  } catch (error) { status.textContent = `加载失败：${error.message}；可重试或导入本地 VRM`; }
}
document.querySelectorAll('[data-mode]').forEach(button => button.onclick = () => {
  mode = button.dataset.mode;
  document.querySelectorAll('[data-mode]').forEach(b => b.setAttribute('aria-pressed', String(b === button)));
  document.querySelector('#lesson').hidden = mode !== 'lecture';
  frame();
});
document.querySelectorAll('[data-expression]').forEach(button => button.onclick = () => { expression = button.dataset.expression; });
document.querySelector('#speak').onclick = event => {
  speaking = !speaking;
  event.currentTarget.textContent = speaking ? '停止口型演示' : '口型演示（无语音）';
};
document.querySelector('#turn').onclick = () => { turn = !turn; };
document.querySelector('#reset').onclick = () => { turn = false; if (vrm) vrm.scene.rotation.y = 0; frame(); };
document.querySelector('#retry').onclick = () => loadModel('./sample.vrm', 'VRoid HairSample_Female · CC0');
document.querySelector('#model').onchange = async event => {
  const file = event.target.files[0]; if (!file) return;
  const url = URL.createObjectURL(file);
  try { await loadModel(url, file.name); } finally { URL.revokeObjectURL(url); }
};
canvas.addEventListener('pointermove', event => {
  const bounds = canvas.getBoundingClientRect();
  gaze.position.set((event.clientX - bounds.left) / bounds.width * 2 - 1, height * 0.8 - ((event.clientY - bounds.top) / bounds.height - 0.5) * 0.6, 3);
});
canvas.addEventListener('webglcontextlost', event => { event.preventDefault(); status.textContent = '图形上下文丢失，请返回目录后重新进入'; });
const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const delta = Math.min(clock.getDelta(), 0.05); elapsed += delta;
  const width = canvas.clientWidth, viewportHeight = canvas.clientHeight;
  if (canvas.width !== Math.floor(width * renderer.getPixelRatio()) || canvas.height !== Math.floor(viewportHeight * renderer.getPixelRatio())) {
    renderer.setSize(width, viewportHeight, false); camera.aspect = width / Math.max(1, viewportHeight); camera.updateProjectionMatrix();
  }
  if (vrm) {
    poseBone('head', Math.sin(elapsed * 0.8) * 0.025, Math.sin(elapsed * 0.55) * 0.04, 0);
    poseBone('spine', 0, 0, Math.sin(elapsed) * 0.008);
    poseBone('leftUpperArm', 0, 0, -1.08);
    poseBone('rightUpperArm', mode === 'lecture' ? -0.25 : 0, 0, mode === 'lecture' ? 0.75 + Math.sin(elapsed * 1.8) * 0.12 : 1.08);
    poseBone('rightLowerArm', mode === 'lecture' ? -0.6 : -0.1);
    const manager = vrm.expressionManager;
    for (const name of ['happy', 'sad', 'angry', 'relaxed', 'surprised']) manager?.setValue(name, expression === name ? 0.75 : 0);
    manager?.setValue('blink', Math.pow(Math.max(0, Math.sin(elapsed * 1.1)), 36));
    manager?.setValue('aa', speaking ? (0.3 + Math.sin(elapsed * 13) * 0.25) : 0);
    if (turn) vrm.scene.rotation.y += delta * 0.5;
    vrm.update(delta);
  }
  controls.update(); renderer.render(scene, camera);
  fpsFrames++;
  if (performance.now() - fpsSince > 1000) {
    document.querySelector('#fps').textContent = `${Math.round(fpsFrames * 1000 / (performance.now() - fpsSince))} fps`;
    fpsFrames = 0; fpsSince = performance.now();
  }
});
loadModel('./sample.vrm', 'VRoid HairSample_Female · CC0');
