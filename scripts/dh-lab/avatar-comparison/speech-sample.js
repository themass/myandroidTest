// speech-sample.js —— 程序合成的 PCM16k 单声道"伪语音"(零联网、零外部素材)。
// 每个音节用该元音的 F1/F2 共振峰正弦 + 音节包络串成句子,供 lipsync-driver 消费;
// 与真实 TTS 下行 PCM 的接口一致(sampleRate + pcm16),通话页可直接换成真音频帧。
// 纯 JS,Node 与浏览器共用。
import { VOWELS } from './lipsync-driver.js';

export const SAMPLE_RATE = 16000;

// 伪句:音节序列(元音 + 时长 ms),含词间停顿。transcript 同步给出时间轴,便于取证对照。
const SYLLABLES = [
  ['n', 0], ['a', 180], ['i', 120], ['-', 60],
  ['h', 0], ['a', 200], ['o', 140], ['-', 90],
  ['t', 0], ['i', 130], ['i', 130], ['-', 60],
  ['m', 0], ['a', 210], ['o', 150], ['u', 130], ['-', 120],
  ['sh', 0], ['e', 190], ['e', 160], ['-', 70],
  ['n', 0], ['i', 140], ['a', 180], ['o', 150],
];

/** 生成整段语音:{ sampleRate, pcm16:Int16Array, transcript:[{vowel,startMs,endMs}] } */
export function synthesizeUtterance() {
  const totalMs = SYLLABLES.reduce((s, [, d]) => s + d, 0);
  const n = Math.round(totalMs * SAMPLE_RATE / 1000);
  const f = new Float32Array(n);
  const transcript = [];
  let tMs = 0, pos = 0;
  for (const [ch, dur] of SYLLABLES) {
    const len = Math.round(dur * SAMPLE_RATE / 1000);
    if ('aeiou'.includes(ch)) {
      const v = VOWELS[ch.toUpperCase()];
      // 共振峰幅度按元音经验:A 最响,U 最闷;F2 相对弱
      const g = 0.28 * v.openness + 0.1;
      for (let i = 0; i < len; i++) {
        const tt = i / SAMPLE_RATE;
        // 音节包络:前 25% 渐入、后 30% 渐出(浊音),中段带 5Hz 声带颤动
        const env = Math.min(1, i / (len * 0.25)) * Math.min(1, (len - i) / (len * 0.3));
        const vib = 1 + 0.06 * Math.sin(2 * Math.PI * 5 * tt);
        f[pos + i] = g * env * vib *
          (0.75 * Math.sin(2 * Math.PI * v.f1 * tt) + 0.45 * Math.sin(2 * Math.PI * v.f2 * tt));
      }
      transcript.push({ vowel: ch.toUpperCase(), startMs: tMs, endMs: tMs + dur });
    } else if (ch === '-') {
      // 停顿:极低噪声底
      for (let i = 0; i < len; i++) f[pos + i] = (Math.random() - 0.5) * 0.004;
    } else {
      // 清音声母:短促带限噪声(不改变元音判定,模拟真实辅音-元音衔接)
      const cut = ch === 'h' ? 0.05 : ch === 'sh' ? 0.12 : 0.03;
      let lp = 0;
      for (let i = 0; i < len; i++) {
        const w = (Math.random() - 0.5) * (ch === 'sh' ? 0.16 : 0.1);
        lp += cut * (w - lp); // 一阶低通 → 摩擦感
        f[pos + i] = lp * Math.min(1, (len - i) / (len * 0.4));
      }
      transcript.push({ vowel: '(辅音' + ch + ')', startMs: tMs, endMs: tMs + dur });
    }
    pos += len; tMs += dur;
  }
  const pcm16 = new Int16Array(n);
  for (let i = 0; i < n; i++) pcm16[i] = Math.max(-32767, Math.min(32767, Math.round(f[i] * 32767)));
  return { sampleRate: SAMPLE_RATE, pcm16, transcript };
}
