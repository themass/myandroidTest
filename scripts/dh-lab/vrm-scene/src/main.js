// HER · VRM 舞台场景页源码。
//
// 产物是 feature-companion/src/main/assets/dh-vrm/scene.bundle.js（IIFE，
// `node build.mjs` 生成）；页面只提供 canvas 与 window.vrmCommand 转发。
//
// 与上一代（d309e1c0 起的 esbuild 手产物，源码从未入库）的关系：
// 渲染、取景（bbox→头顶骨对焦）、指令时序、点按分区反应全部保持不动；
// 这一版新增两件事（用户 2026-11-23：「支持旋转」+ 描边可见）：
// - orbit 指令：Kotlin 手势层（avatarStageTransform 的 onOrbit 档）把单指横拖
//   累成绝对 yawDeg 发进来，写进 [pivot] 的 rotation.y —— 真 360° 转一圈。
//   旧的「页面内单指拖动 = 视线 ±40° 跟随」退役：旋转归 Kotlin 判，页面不再
//   自己吃拖动（两边同吃一笔会一划两变）。
// - 描边：模型自带 MToon 的 outlineWidthMode=worldCoordinates/0.0008——世界坐标
//   亚毫米，实际不可见。enableOutline 把有描边通道的材质统一换成
//   screenCoordinates（屏幕像素宽，任意距离/缩放都看得见）并给一个可读的宽与色。
//   相机与取景完全不动（「不要改原来的UI」）。

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { VRMLoaderPlugin, VRMUtils } from "@pixiv/three-vrm";

const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

/**
 * 描边参数：屏幕坐标 0.4% 屏宽（~3px），去饱和靛灰。
 * 深色（近黑）在通话页近黑背景上等于没画——真机像素探边证实（描边 lum≈10
 * vs 背景 lum≈1-2）；这个色对背景（lum 1-2）和肤色/发色（lum 55-210）都成立。
 */
const OUTLINE_WIDTH = 0.004;
const OUTLINE_COLOR = 0x454a63;

function showStatus(message) {
  const el = document.getElementById("status");
  if (!el) return;
  el.textContent = String(message);
  el.style.display = "block";
}

/**
 * 把模型里所有 MToon 材质的描边打到可见。
 * 只碰自带 outline 通道的材质（outlineWidthMode!=="none"）：没有描边通道的
 * （眼线/高光这类贴图层）保持原样，免得整张脸描黑。
 */
function enableOutline(vrm) {
  let touched = 0;
  for (const mat of vrm.materials.values()) {
    if (!mat || mat.outlineWidthMode === "none") continue;
    mat.outlineWidthMode = "screenCoordinates";
    mat.outlineWidthFactor = OUTLINE_WIDTH;
    if (mat.outlineColorFactor) mat.outlineColorFactor.setHex(OUTLINE_COLOR);
    mat.needsUpdate = true;
    touched++;
  }
  console.log("[dhvrm] outline on materials=" + touched + " w=" + OUTLINE_WIDTH);
  return touched;
}

function init(canvas, modelUrl, options) {
  const bridge = (options && options.bridge) || {};

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  // 轨道旋转的枢轴：模型挂这里，orbit 只转 pivot —— 取景、点按分区都不受影响。
  const pivot = new THREE.Group();
  scene.add(pivot);

  const camera = new THREE.PerspectiveCamera(35, 1, 0.1, 10);
  camera.position.set(0, 1.45, 1.6);
  camera.lookAt(0, 1.35, 0);
  scene.add(new THREE.HemisphereLight(0xffffff, 0x444766, 2));
  const dir = new THREE.DirectionalLight(0xffffff, 1.4);
  dir.position.set(0.6, 2, 1.2);
  scene.add(dir);

  let vrm = null;

  // 视线（lookAt）与嘴型（aa）的缓动状态——与旧版一致。
  const camHome = new THREE.Vector3(0, 1.45, 1.6);
  let gazeYaw = 0, gazePitch = 0;
  let targetYaw = 0, targetPitch = 0;
  let pitchImpulse = 0;
  let mouth = 0, mouthNow = 0;

  function emit(name, payload) {
    try { if (bridge[name]) bridge[name](payload); } catch (_e) { /* 桥断了不拖渲染 */ }
  }

  function setLook(nx, ny) {
    targetYaw = clamp01(nx) * 80 - 40;
    targetPitch = clamp01(ny) * 50 - 25;
  }

  function resetLook() {
    targetYaw = 0;
    targetPitch = 0;
  }

  function handleSceneCommand(json) {
    let msg;
    try { msg = JSON.parse(json); } catch (_e) { return; }
    if (msg.type === "expression") {
      if (!vrm || !vrm.expressionManager) return;
      const em = vrm.expressionManager;
      em.resetValues();
      if (msg.name !== "neutral") em.setValue(msg.name, 1);
    } else if (msg.type === "mouth") {
      mouth = clamp01(msg.openness);
    } else if (msg.type === "look") {
      setLook((msg.x + 1) * 0.5, (msg.y + 1) * 0.5);
    } else if (msg.type === "orbit") {
      // 绝对累计角（Kotlin VrmSceneController 累加；VrmBridgeTest 钉死线格式）。
      if (pivot) pivot.rotation.y = (msg.yawDeg * Math.PI) / 180;
    }
  }

  function resize() {
    const w = window.innerWidth || canvas.clientWidth || 1;
    const h = window.innerHeight || canvas.clientHeight || 1;
    canvas.style.width = w + "px";
    canvas.style.height = h + "px";
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  let last = performance.now();
  let seenW = 0, seenH = 0;
  function animate(now) {
    requestAnimationFrame(animate);
    const dt = Math.min((now - last) / 1000, 0.1);
    last = now;
    if (window.innerWidth !== seenW || window.innerHeight !== seenH) {
      seenW = window.innerWidth;
      seenH = window.innerHeight;
      console.log("[dhvrm] resize " + seenW + "x" + seenH);
      resize();
    }
    if (vrm) {
      gazeYaw += (targetYaw - gazeYaw) * 0.15;
      gazePitch += (targetPitch - gazePitch) * 0.15;
      if (vrm.lookAt) {
        vrm.lookAt.autoUpdate = false;
        vrm.lookAt.yaw = gazeYaw;
        vrm.lookAt.pitch = gazePitch + pitchImpulse;
      }
      pitchImpulse += (0 - pitchImpulse) * 0.08;
      mouthNow += (mouth - mouthNow) * 0.4;
      if (vrm.expressionManager) vrm.expressionManager.setValue("aa", mouthNow);
      vrm.update(dt);
    }
    renderer.render(scene, camera);
    if (Math.random() < 0.005) {
      const info = renderer.info.render;
      console.log("[dhvrm] tick calls=" + info.calls + " tris=" + info.triangles + " canvas=" + canvas.width + "x" + canvas.height);
    }
  }

  const loader = new GLTFLoader();
  loader.register((parser) => new VRMLoaderPlugin(parser));
  loader.load(
    modelUrl,
    (gltf) => {
      try {
        vrm = gltf.userData.vrm;
        if (!vrm) {
          showStatus("这个文件里没有 VRM 数据");
          emit("onError", "no vrm");
          return;
        }
        VRMUtils.removeUnnecessaryVertices(gltf.scene);
        VRMUtils.removeUnnecessaryJoints(gltf.scene);
        if (vrm.springBoneManager) {
          vrm.springBoneManager.ground = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
        }
        if (vrm.lookAt) vrm.lookAt.autoUpdate = false;

        enableOutline(vrm);

        // 模型进 pivot（pivot 此刻是恒等变换）：取景计算看到的是同一世界坐标，
        // 之后 orbit 只转 pivot，相机与点按分区都不受影响。
        pivot.add(vrm.scene);

        const box = new THREE.Box3().setFromObject(vrm.scene);
        console.log("[dhvrm] bbox", JSON.stringify(box.min.toArray()), JSON.stringify(box.max.toArray()));
        const focus = new THREE.Vector3(0, Math.min(1.2, (box.min.y + box.max.y) * 0.5), 0);
        const head = vrm.humanoid && vrm.humanoid.getNormalizedBoneNode("head");
        if (head) head.getWorldPosition(focus);
        console.log("[dhvrm] focus", JSON.stringify(focus.toArray()));
        camera.position.set(focus.x, focus.y + 0.12, focus.z + 0.9);
        camera.lookAt(focus);
        resize();
        console.log("[dhvrm] ready inner=" + window.innerWidth + "x" + window.innerHeight +
          " css=" + document.documentElement.clientWidth + "x" + document.documentElement.clientHeight +
          " canvasCss=" + canvas.clientWidth + "x" + canvas.clientHeight);
        emit("onReady", "");
        requestAnimationFrame(animate);
      } catch (e) {
        showStatus("模型初始化失败: " + (e && e.message ? e.message : e));
        emit("onError", String(e));
      }
    },
    undefined,
    (e) => {
      showStatus("模型加载失败: " + (e && e.message ? e.message : "download error"));
      emit("onError", String(e));
    },
  );

  // 触点：只剩「轻触分区反应」（上半屏摸头 happy+低头缓冲 / 下半屏碰身体 surprised）。
  // 旧版的页面内单指拖动视线跟随退役——旋转手势归 Kotlin（avatarStageTransform
  // 的 onOrbit 档，slop 仲裁唯一在那边）；拖动一旦被 Kotlin 消费，页面也收不到
  // move，两边同吃只会一划两变。
  const SLOP_PX = 14;
  const TAP_MS = 350;
  let tracking = false;
  let downX = 0, downY = 0, downAt = 0, slopBroken = false;
  let reactionTimer = 0;

  function tapReaction(region) {
    if (!vrm || !vrm.expressionManager) return;
    const em = vrm.expressionManager;
    const exp = region === "head" ? "happy" : "surprised";
    em.setValue(exp, 1);
    if (region === "head") pitchImpulse = -0.18;
    clearTimeout(reactionTimer);
    reactionTimer = setTimeout(() => em.resetValues(), 1400);
    console.log("[dhvrm] tap region=" + region + " exp=" + exp);
    emit("onReaction", region);
  }

  canvas.addEventListener("touchstart", (e) => {
    if (e.touches.length !== 1) return;
    tracking = true;
    const t = e.touches[0];
    downX = t.clientX;
    downY = t.clientY;
    downAt = performance.now();
    slopBroken = false;
    e.preventDefault();
  }, { passive: false });

  canvas.addEventListener("touchmove", (e) => {
    if (!tracking || e.touches.length !== 1) return;
    const t = e.touches[0];
    if (!slopBroken && Math.hypot(t.clientX - downX, t.clientY - downY) > SLOP_PX) slopBroken = true;
    e.preventDefault();
  }, { passive: false });

  const endTouch = () => {
    if (!tracking) return;
    tracking = false;
    if (!slopBroken && performance.now() - downAt < TAP_MS) {
      tapReaction(downY < window.innerHeight * 0.45 ? "head" : "body");
    }
    resetLook();
  };
  const cancelTouch = () => {
    if (!tracking) return;
    tracking = false;
    resetLook();
  };
  canvas.addEventListener("touchend", endTouch);
  canvas.addEventListener("touchcancel", cancelTouch);

  window.addEventListener("resize", resize);

  return {
    command: handleSceneCommand,
    dispose: () => {
      if (vrm) VRMUtils.deepDispose(vrm.scene);
      renderer.dispose();
    },
  };
}

window.DhVrmScene = { init };
