// Effects Lab 的 Three.js 叠加场景：全部几何体自建，无 GLTFLoader、无 three-vrm、无网络请求。
// 与 Kotlin 的接缝是 window.FxLab：start / stop / command(json)。
// command 处理 {"type":"cue","name":"pat_head"|"wave"|"connected"}。
import * as THREE from "three";

const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
renderer.setClearColor(0x000000, 0); // 透明底：叠在数字人/摄像头上，不是盖住它们
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
camera.position.z = 2;

// 自发光圆环 + 光扫：与 Lottie/Rive 的形态明显不同，肉眼可分辨引擎差异。
const ring = new THREE.Mesh(
  new THREE.TorusGeometry(0.55, 0.06, 24, 96),
  new THREE.MeshBasicMaterial({ color: 0x33d6ff, transparent: true, opacity: 0.85 }),
);
scene.add(ring);

const sweep = new THREE.Mesh(
  new THREE.PlaneGeometry(2.0, 0.12),
  new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.25 }),
);
sweep.rotation.z = Math.PI / 6;
scene.add(sweep);

let running = false;
let rafId = 0;
let cueUntil = 0;
let cueName = null;

function resize() {
  const w = window.innerWidth || 1;
  const h = window.innerHeight || 1;
  renderer.setSize(w, h, false);
  const aspect = w / h;
  camera.left = -aspect; camera.right = aspect;
  camera.updateProjectionMatrix();
}

function frame(nowMs) {
  if (!running) return;
  const t = nowMs / 1000;
  ring.rotation.z = t * 0.8;
  sweep.position.y = ((t * 0.35) % 1.4) - 0.7;
  if (cueName && t > cueUntil) { cueName = null; }
  if (cueName === "pat_head") {
    ring.scale.setScalar(1.0 + 0.25 * Math.sin(t * 10));
  } else if (cueName === "wave") {
    ring.rotation.x = 0.5 * Math.sin(t * 6);
  } else if (cueName === "connected") {
    ring.scale.setScalar(1.15);
  } else {
    ring.scale.setScalar(1.0);
  }
  renderer.render(scene, camera);
  rafId = requestAnimationFrame(frame);
}

window.FxLab = {
  start() {
    if (running) return;
    running = true;
    resize();
    rafId = requestAnimationFrame(frame);
  },
  stop() {
    running = false;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = 0;
  },
  command(json) {
    let msg;
    try { msg = JSON.parse(json); } catch (e) { return; }
    if (msg && msg.type === "cue") {
      cueName = String(msg.name || "");
      cueUntil = performance.now() / 1000 + 1.5;
    }
  },
};
window.addEventListener("resize", resize);
