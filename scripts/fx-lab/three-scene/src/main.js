// Effects Lab 的 Three.js 叠加场景：全部几何体自建，无 GLTFLoader、无 three-vrm、无网络请求。
// 与 Kotlin 的接缝是 window.FxLab：start / stop / command(json)。
// command 处理 {"type":"cue","name":"pat_head"|"wave"|"connected"}。
import * as THREE from "three";

// 真机（华为 hwschromium WebView）：canvas 元素整层不参与合成——2D/GL 都一样，
// getImageData 有内容（blit 成功）但屏幕上不可见；普通 DOM div 可见。
// 合成路径：WebGL 离屏渲染 → 2D canvas 搬运 → toDataURL 喂给可见的 <img>。
const glCanvas = document.createElement("canvas");
const renderer = new THREE.WebGLRenderer({
  canvas: glCanvas, alpha: true, antialias: true, preserveDrawingBuffer: true,
});
const buffer2d = document.createElement("canvas");
const ctx2d = buffer2d.getContext("2d");
const shown = document.createElement("img");
shown.alt = "";
// 真机判定（四标记实验）：position:fixed 的小 img/canvas 都能合成，唯一不显示的是
// 普通流里的全尺寸 img —— 把搬运目标钉成 fixed。
shown.style.cssText = "position:fixed;top:0;left:0;";
document.body.appendChild(shown);
renderer.setClearColor(0x000000, 0); // 透明底：叠在数字人/摄像头上，不是盖住它们

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
  lastW = window.innerWidth; lastH = window.innerHeight;
  const w = lastW || 1;
  const h = lastH || 1;
  renderer.setSize(w, h, false);
  buffer2d.width = w; buffer2d.height = h;
  shown.style.width = w + "px"; shown.style.height = h + "px";
  const aspect = w / h;
  camera.left = -aspect; camera.right = aspect;
  camera.updateProjectionMatrix();
}
let lastW = 0, lastH = 0;

function frame(nowMs) {
  if (!running) return;
  // Android WebView 在 loadUrl 时往往还没被布局：首帧 innerWidth 是 0，
  // canvas 只有 1px（真机踩过）。每帧核对窗口尺寸，布局到达后立即重排。
  if (window.innerWidth !== lastW || window.innerHeight !== lastH) resize();
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
  // GL→2D→img 搬运：这就是本页唯一的合成路径（canvas 元素本身不可见）。
  ctx2d.clearRect(0, 0, buffer2d.width, buffer2d.height);
  ctx2d.drawImage(glCanvas, 0, 0);
  shown.src = buffer2d.toDataURL("image/png");
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
