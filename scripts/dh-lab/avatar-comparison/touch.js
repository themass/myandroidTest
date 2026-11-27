import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { chooseRegion, springStep } from './touch-response.mjs';

const canvas = document.querySelector('canvas');
const status = document.querySelector('#status');
const renderer = new THREE.WebGLRenderer({canvas, antialias: true});
renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene(); scene.background = new THREE.Color(0xe6e9ee);
scene.add(new THREE.HemisphereLight(0xffffff, 0x777777, 2));
const light = new THREE.DirectionalLight(0xffffff, 2); light.position.set(2, 3, 4); scene.add(light);
const camera = new THREE.PerspectiveCamera(30, 1, .01, 100);
const ray = new THREE.Raycaster(); const pointer = new THREE.Vector2();
let model, height, bones, originals, active = null, owner = null;
const states = {head: {value: 0, velocity: 0}, left: {value: 0, velocity: 0}, right: {value: 0, velocity: 0}};
new GLTFLoader().load('./model.glb', gltf => {
  model = gltf.scene; model.rotation.y = -Math.PI / 2; scene.add(model); model.updateMatrixWorld(true);
  bones = Object.fromEntries(['Head', 'LeftHand', 'RightHand', 'LeftForeArm', 'RightForeArm'].map(name => [name, model.getObjectByName(name)]));
  if (Object.values(bones).some(b => !b)) { status.textContent = '模型缺少必要骨骼，无法进行触碰实验'; return; }
  originals = Object.fromEntries(Object.entries(bones).map(([name, b]) => [name, b.quaternion.clone()]));
  const box = new THREE.Box3().setFromObject(model), center = box.getCenter(new THREE.Vector3());
  height = box.max.y - box.min.y; camera.position.set(center.x, center.y, center.z + height * 2.5); camera.lookAt(center);
  model.updateMatrixWorld(true); status.textContent = '模型已加载：试着触碰头部或手部';
}, undefined, error => { status.textContent = `模型加载失败：${error.message}`; });
canvas.addEventListener('pointerdown', e => {
  if (!model || !bones || !originals || owner !== null) return;
  const r = canvas.getBoundingClientRect(); pointer.set((e.clientX-r.left)/r.width*2-1, -((e.clientY-r.top)/r.height)*2+1);
  ray.setFromCamera(pointer, camera); const hit = ray.intersectObject(model, true)[0];
  if (!hit) { status.textContent = '没有碰到角色'; return; }
  const pos = new THREE.Vector3();
  const distances = {head: hit.point.distanceTo(bones.Head.getWorldPosition(pos)), left: hit.point.distanceTo(bones.LeftHand.getWorldPosition(pos)), right: hit.point.distanceTo(bones.RightHand.getWorldPosition(pos))};
  active = chooseRegion(distances, height * .16);
  if (!active) { status.textContent = '这里暂未绑定反应，请触碰头部或手部'; return; }
  owner = e.pointerId; canvas.setPointerCapture(owner);
  status.textContent = active === 'head' ? '触碰头部：轻轻偏头' : '触碰手部：收手';
});
function release(e) { if (e.pointerId === owner) { owner = null; active = null; status.textContent = '松开：恢复自然姿态'; } }
canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release); canvas.addEventListener('lostpointercapture', release);
const clock = new THREE.Clock();
renderer.setAnimationLoop(() => {
  const dt = clock.getDelta(), w = canvas.clientWidth, h = canvas.clientHeight;
  if (w && h && (canvas.width !== Math.floor(w*renderer.getPixelRatio()) || canvas.height !== Math.floor(h*renderer.getPixelRatio()))) { renderer.setSize(w,h,false); camera.aspect=w/h; camera.updateProjectionMatrix(); }
  if (originals) {
    for (const key of Object.keys(states)) states[key] = springStep(states[key], active === key ? 1 : 0, dt);
    bones.Head.quaternion.copy(originals.Head); bones.Head.rotateZ(states.head.value * .22);
    bones.LeftForeArm.quaternion.copy(originals.LeftForeArm); bones.LeftForeArm.rotateZ(states.left.value * .45);
    bones.RightForeArm.quaternion.copy(originals.RightForeArm); bones.RightForeArm.rotateZ(-states.right.value * .45);
  }
  renderer.render(scene,camera);
});
