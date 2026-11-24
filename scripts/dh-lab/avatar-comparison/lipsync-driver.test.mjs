// lipsync-driver.test.mjs —— node 原生 assert,无测试框架。
// 运行:node lipsync-driver.test.mjs
// 合成 PCM:每个元音用其 F1/F2 共振峰的正弦叠加(带渐变包络),
// 断言:权重输出方向正确(dominant=目标元音)、平滑生效(attack/release)、静音衰减、流式切帧。
import assert from 'node:assert/strict';
import { LipsyncDriver, VOWELS, fft } from './lipsync-driver.js';

const SR = 16000;

// 合成一个元音帧:F1/F2 正弦叠加,前 1/4 帧渐入(模拟浊音起始)。
function synthVowel(vowel, n = 640, amp = 0.25) {
  const v = VOWELS[vowel];
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const env = Math.min(1, i / (n / 4));
    out[i] = env * amp * (0.7 * Math.sin(2 * Math.PI * v.f1 * i / SR) + 0.5 * Math.sin(2 * Math.PI * v.f2 * i / SR));
  }
  return out;
}
function toPcm16(f) { const p = new Int16Array(f.length); for (let i = 0; i < f.length; i++) p[i] = Math.max(-32767, Math.min(32767, Math.round(f[i] * 32768))); return p; }

let passed = 0;
function t(name, fn) { fn(); passed++; console.log('ok - ' + name); }

// 0) FFT  sanity:纯正弦在对应 bin 出现峰值
t('fft peaks at the sine frequency', () => {
  const N = 512, f0 = 1000;
  const re = new Float32Array(N), im = new Float32Array(N);
  for (let i = 0; i < N; i++) re[i] = Math.sin(2 * Math.PI * f0 * i / SR);
  fft(re, im);
  let best = 0, bv = -1;
  for (let k = 0; k < N / 2; k++) { const m = re[k] * re[k] + im[k] * im[k]; if (m > bv) { bv = m; best = k; } }
  assert.equal(best * SR / N, f0);
});

// 1) 五个元音各自方向正确(帧足够长,平滑已收敛:先喂 10 帧再断言)
for (const v of ['A', 'E', 'I', 'O', 'U']) {
  t(`dominant weight = ${v} for synthesized ${v}`, () => {
    const d = new LipsyncDriver();
    const frame = synthVowel(v);
    let last;
    for (let k = 0; k < 10; k++) last = d.pushFrameFloat(frame);
    assert.equal(last.dominant, v, JSON.stringify(last.weights));
    assert.ok(last.weights[v] >= 0.5, v + ' weight should dominate: ' + JSON.stringify(last.weights));
    assert.ok(last.openness > 0.15, 'openness should rise: ' + last.openness);
  });
}

// 2) 平滑:attack 快(≤~90ms 到位),release 慢(≥~150ms 才落半);用帧数换算
t('smoothing: attack fast, release slow', () => {
  const d = new LipsyncDriver({ attackMs: 30, releaseMs: 120 });
  const A = synthVowel('A'), silence = new Float32Array(640);
  // attack:喂 A,数多少帧后 A 权重 ≥0.45(起始 0)
  let framesToAttack = -1;
  for (let k = 0; k < 30; k++) {
    const r = d.pushFrameFloat(A);
    if (r.weights.A >= 0.45) { framesToAttack = k + 1; break; }
  }
  assert.notEqual(framesToAttack, -1, 'A never reached 0.45');
  assert.ok(framesToAttack <= 3, 'attack too slow: ' + framesToAttack + ' frames (' + framesToAttack * 40 + 'ms)');
  // release:切静音,A 的原始平滑值(非归一)应在 1~2 帧内仍明显 >0
  // 用 openness 观察更直接:静音后第一帧 openness 不应瞬间归零
  const rSilence1 = d.pushFrameFloat(silence);
  assert.ok(rSilence1.openness > 0.2, 'openness dropped instantly, smoothing broken: ' + rSilence1.openness);
  let r;
  for (let k = 0; k < 3; k++) r = d.pushFrameFloat(silence); // 再 3 帧(120ms)
  assert.ok(r.openness < rSilence1.openness, 'openness should keep decaying');
});

// 3) 静音:持续静音 → 权重全 0、openness→0
t('silence drives weights and openness to zero', () => {
  const d = new LipsyncDriver();
  let last;
  for (let k = 0; k < 20; k++) last = d.pushFrameFloat(new Float32Array(640));
  assert.deepEqual(last.weights, { A: 0, E: 0, I: 0, O: 0, U: 0 });
  assert.ok(last.openness < 1e-3, 'openness=' + last.openness);
});

// 4) 流式:pushSamples 任意长度缓冲跨帧切分,帧数 = floor(total/frameSize)
t('pushSamples splits arbitrary streams into frames', () => {
  const d = new LipsyncDriver({ frameSize: 640 });
  const pcm = toPcm16(synthVowel('O', 1500));
  const frames = d.pushSamples(pcm);
  assert.equal(frames.length, 2);
  const more = d.pushSamples(toPcm16(synthVowel('O', 200)));
  assert.equal(more.length, 0); // 残 60+200=... 440 <640,不产帧
  const rest = d.pushSamples(toPcm16(synthVowel('O', 300))); // 补满下一帧
  assert.equal(rest.length, 1);
});

// 5) 元音切换:O→I 过渡中,两权重都出现过(不是单值开合能表达的)
t('vowel transition exposes both weights', () => {
  const d = new LipsyncDriver();
  for (let k = 0; k < 10; k++) d.pushFrameFloat(synthVowel('O'));
  const mid = [];
  for (let k = 0; k < 4; k++) mid.push(d.pushFrameFloat(synthVowel('I')));
  const O = Math.max(...mid.map(r => r.weights.O));
  const I = mid[mid.length - 1].weights.I;
  assert.ok(O > 0.1, 'O lingered as expected (release): ' + O);
  assert.ok(I > 0.4, 'I rising: ' + I);
  // 幅度法做不到:同一 openness 无法区分 O/I 形状 → 权重向量必须不同
  const Oend = d.pushFrameFloat(synthVowel('O'));
  assert.notDeepEqual(Oend.weights, mid[mid.length - 1].weights);
});

// 6) 输出契约:权重和为 1(有声时)、无 NaN
t('weights sum to 1 when voiced, no NaN', () => {
  const d = new LipsyncDriver();
  let last;
  for (let k = 0; k < 6; k++) last = d.pushFrameFloat(synthVowel('E'));
  const s = Object.values(last.weights).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(s - 1) < 1e-6, 'sum=' + s);
  for (const v of Object.values(last.weights)) assert.ok(Number.isFinite(v));
  assert.ok(Number.isFinite(last.openness) && last.openness >= 0 && last.openness <= 1);
});

// 7) 非 2 的幂 FFT 尺寸应报错(参数校验)
t('non power-of-two fftSize throws', () => {
  assert.throws(() => { const d = new LipsyncDriver({ fftSize: 500 }); d.pushFrameFloat(new Float32Array(640)); }, /power of 2/);
});

console.log(`\n${passed} test groups passed`);
