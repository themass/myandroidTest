// TalkingHead 离线本地示例:vendored 源码(samples/talkinghead/modules,MIT)+ CC0 mpfb.glb。
// showAvatar/setMood/setView 按 vendored talkinghead.mjs 接口核对。
// 无在线 TTS:用随包的 lipsync-en 做「词→Oculus viseme」换算,本页按时间轴用
// setFixedValue 写入 viseme 形变通道 —— 这正是 TalkingHead 自身的驱动 API(mtAvatar.fixed),
// 不碰内部状态,也不请求任何网络接口。(真实语音的 speakText→TTS 链路需要服务端,属后续工作。)
import { TalkingHead } from './samples/talkinghead/modules/talkinghead.mjs';
import { LipsyncEn } from './samples/talkinghead/modules/lipsync-en.mjs';

const status = document.querySelector('#status');

(async () => {
  status.textContent = '初始化 TalkingHead…';
  const th = new TalkingHead(document.getElementById('stage'), {
    ttsEndpoint: '', // 不提供 TTS
    lipsyncLang: 'en',
    // 引擎默认会 import './lipsync-{fi,en,lt}.mjs'(path=页面根目录)——本页换算走
    // 上面静态 import 的 LipsyncEn,禁用那三个运行时动态 import,保证零网络请求。
    lipsyncModules: [],
    avatarMood: 'neutral',
    cameraView: 'full',
    cameraRotateEnable: true,
  });
  status.textContent = '加载 mpfb.glb(36.8MB,本地)…';
  await th.showAvatar({ url: './mpfb.glb' });
  status.textContent = 'mpfb 加载完成 · 点「口型演示」观察 viseme 形变(无声音)';

  // —— 离线口型:lipsync-en 换算 + setFixedValue 时间轴 ——
  const VIS_UNIT = 110; // lipsync 相对时长 1 单位 ≈ 110ms(演示经验值)
  const VIS_NAMES = ['aa', 'E', 'I', 'O', 'U', 'PP', 'SS', 'TH', 'DD', 'FF', 'kk', 'nn', 'RR', 'CH', 'sil'];
  let lipsPlan = null, lipsEnd = 0;
  function buildPlan(text) {
    const lips = new LipsyncEn();
    const plan = [];
    let t = performance.now() + 150;
    for (const w of text.split(/[ ]+/)) {
      const cleaned = lips.preProcessText(w);
      if (!cleaned.length) { t += VIS_UNIT; continue; }
      const v = lips.wordsToVisemes(cleaned);
      for (let i = 0; i < v.visemes.length; i++) {
        plan.push({ at: t + v.times[i] * VIS_UNIT, dur: v.durations[i] * VIS_UNIT, vis: v.visemes[i] });
      }
      const total = v.visemes.length ? v.times[v.visemes.length - 1] + v.durations[v.visemes.length - 1] : 0;
      t += (total + 0.8) * VIS_UNIT; // 词间停顿
    }
    lipsPlan = plan; lipsEnd = t;
  }
  function applyLips(now) {
    if (!lipsPlan) return;
    let hit = null;
    for (const p of lipsPlan) { if (now >= p.at && now <= p.at + p.dur) { hit = p; break; } }
    for (const name of VIS_NAMES) {
      th.setFixedValue('viseme_' + name, hit && hit.vis === name ? (name === 'PP' || name === 'FF') ? 0.9 : 0.6 : 0);
    }
    if (now > lipsEnd) {
      lipsPlan = null;
      for (const name of VIS_NAMES) th.setFixedValue('viseme_' + name, 0);
      status.textContent = '口型演示结束(全程未联网、无语音)。';
    }
  }

  document.getElementById('speak').onclick = () => {
    buildPlan('The quick brown fox jumps over the lazy dog. Hello, this is a local lip sync demo.');
    status.textContent = '口型演示:词→Oculus viseme 时间轴驱动嘴部形变。';
  };
  document.getElementById('stop').onclick = () => {
    lipsPlan = null;
    for (const name of VIS_NAMES) th.setFixedValue('viseme_' + name, 0);
    status.textContent = '已停止口型';
  };
  const moods = ['neutral', 'happy', 'sad', 'angry', 'fear', 'disgust'];
  let mi = 0;
  document.getElementById('mood').onclick = e => {
    mi = (mi + 1) % moods.length;
    th.setMood(moods[mi]);
    e.currentTarget.textContent = '表情:' + moods[mi];
  };
  const views = ['full', 'mid', 'upper', 'head'];
  let vi = 0;
  document.getElementById('view').onclick = e => {
    vi = (vi + 1) % views.length;
    th.setView(views[vi]);
    e.currentTarget.textContent = '镜头:' + views[vi];
  };

  // 帧率观察 + 本页口型时间轴(vendored animate 内部自跑 rAF 管表情/眨眼/头部)
  let frames = 0, since = performance.now();
  (function tick() {
    requestAnimationFrame(tick);
    applyLips(performance.now());
    frames++;
    const now = performance.now();
    if (now - since > 1000) { document.getElementById('fps').textContent = Math.round(frames * 1000 / (now - since)) + ' fps'; frames = 0; since = now; }
  })();
})().catch(err => { status.textContent = '加载失败:' + err.message; });
