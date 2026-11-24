// lipsync-driver.js —— 音频 → Live2D 元音权重驱动(P5,幅度法 → 元音权重法)。
// 纯 JS,无 DOM、无网络、无第三方依赖:Node 单测与浏览器共用同一份代码。
// 参照思路:AIRI model-driver-lipsync —— 从音频算 A/E/I/O/U 五个元音权重,
// 约 40ms 重算一次、以指数插值平滑,写进 Live2D 参数面而不是单值嘴开合。
//
// 输入:PCM16 单声道(默认 16kHz)。pushSamples() 喂任意长度样本流,内部按
// frameSize(默认 640 样本 = 40ms @16k)切帧;每满一帧返回一次分析结果。
// 输出:{ weights:{A,E,I,O,U}(softmax 归一), dominant, openness(0..1),
//        rms(0..1), recomputeMs } —— openness 供 ParamMouthOpenY,
//        weights 供将来嘴型形变(ParamMouthForm 组合或 viseme 叠加)。
//
// 平滑:两个时间常数(attackMs/releaseMs)做指数平滑,重算周期=帧长,均为构造参数。
// 通话页移植挂点:服务端 TTS 下行 PCM(16k 单声道 s16)直接 pushSamples,
// 帧到达即写参数;不需要 WebAudio,与现有原生链路的 40ms 帧节流一致。

// ---- FFT(radix-2,原地;re/im 为等长 Float32Array)----
export function fft(re, im) {
  const n = re.length;
  if ((n & (n - 1)) !== 0) throw new Error('FFT length must be power of 2, got ' + n);
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      let t = re[i]; re[i] = re[j]; re[j] = t;
      t = im[i]; im[i] = im[j]; im[j] = t;
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (-2 * Math.PI) / len;
    const wr = Math.cos(ang), wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let cur = 1, curIm = 0;
      for (let k = 0; k < len / 2; k++) {
        const ur = re[i + k], ui = im[i + k];
        const vr = re[i + k + len / 2] * cur - im[i + k + len / 2] * curIm;
        const vi = re[i + k + len / 2] * curIm + im[i + k + len / 2] * cur;
        re[i + k] = ur + vr; im[i + k] = ui + vi;
        re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi;
        const nr = cur * wr - curIm * wi;
        curIm = cur * wi + curIm * wr; cur = nr;
      }
    }
  }
}

// 元音共振峰参考表(Hayashi/Komine 汉语元音常用近似)+ 带宽,单位 Hz。
// openness = 该元音的嘴开度经验值,用于合成 openness 输出。
export const VOWELS = {
  A: { f1: 750, f2: 1400, bw: 120, openness: 1.0 },
  E: { f1: 500, f2: 1800, bw: 110, openness: 0.8 },
  I: { f1: 300, f2: 2300, bw: 90, openness: 0.45 },
  O: { f1: 450, f2: 800, bw: 90, openness: 0.75 },
  U: { f1: 350, f2: 700, bw: 80, openness: 0.3 },
};

// 元音权重法的核心特征:把频谱切成与上表共振峰对应的频带,带内能量(dB)
// 做 softmax(温度 tempDb)→ 五个权重。温度单位 dB:越小越"赢家通吃"。
const BANDS = [
  { vowel: 'A', lo: 650, hi: 900 },   // A 的 F1
  { vowel: 'A', lo: 1250, hi: 1600 }, // A 的 F2
  { vowel: 'E', lo: 420, hi: 600 },
  { vowel: 'E', lo: 1650, hi: 2000 },
  { vowel: 'I', lo: 230, hi: 380 },
  { vowel: 'I', lo: 2100, hi: 2550 },
  { vowel: 'O', lo: 380, hi: 540 },
  { vowel: 'O', lo: 700, hi: 920 },
  { vowel: 'U', lo: 280, hi: 430 },
  { vowel: 'U', lo: 600, hi: 800 },
];

export class LipsyncDriver {
  /**
   * @param opts.sampleRate   PCM 采样率(默认 16000)
   * @param opts.frameSize    每帧样本数,即重算周期(默认 640 = 40ms@16k)
   * @param opts.fftSize      FFT 点数,2 的幂(默认 512,16k 下分辨率 ≈31Hz)
   * @param opts.attackMs     权重上升平滑常数(默认 30)
   * @param opts.releaseMs    权重下降平滑常数(默认 120,参照 AIRI ~120ms)
   * @param opts.silenceRms   低于此 RMS 判为静音,权重全部衰减(默认 0.004)
   * @param opts.tempDb       softmax 温度/dB(默认 10)
   */
  constructor(opts = {}) {
    this.sampleRate = opts.sampleRate ?? 16000;
    this.frameSize = opts.frameSize ?? 640;
    this.fftSize = opts.fftSize ?? 512;
    this.attackMs = opts.attackMs ?? 30;
    this.releaseMs = opts.releaseMs ?? 120;
    this.silenceRms = opts.silenceRms ?? 0.004;
    this.tempDb = opts.tempDb ?? 10;
    this._acc = new Float32Array(this.frameSize);
    this._accLen = 0;
    this._win = hann(this.fftSize);
    this._re = new Float32Array(this.fftSize);
    this._im = new Float32Array(this.fftSize);
    this._smooth = { A: 0, E: 0, I: 0, O: 0, U: 0 };
    this._open = 0;
    this._binHz = this.sampleRate / this.fftSize;
  }

  /** 喂 PCM16 数据(Int16Array/Array,单声道);返回本调用内完成的所有帧结果(通常 0 或 1 帧)。 */
  pushSamples(pcm16) {
    const frames = [];
    for (let i = 0; i < pcm16.length; i++) {
      this._acc[this._accLen++] = pcm16[i] / 32768; // → [-1,1]
      if (this._accLen === this.frameSize) {
        frames.push(this._analyze(this._acc));
        this._accLen = 0;
      }
    }
    return frames;
  }

  /** 直接喂一帧浮点样本(测试用;-1..1)。 */
  pushFrameFloat(floats) {
    return this._analyze(floats);
  }

  /** 当前平滑后的状态快照(不推进分析)。 */
  snapshot() {
    return { weights: { ...this._smooth }, dominant: argmax(this._smooth), openness: this._open, rms: null, recomputeMs: this.frameSize / this.sampleRate * 1000 };
  }

  _analyze(frame) {
    // 1) RMS(幅度;旧 sin 演示等价于只有这一维)
    let sum = 0;
    for (let i = 0; i < frame.length; i++) sum += frame[i] * frame[i];
    const rms = Math.sqrt(sum / frame.length);

    // 2) 加窗 FFT → 功率谱(dB)
    const N = this.fftSize;
    const src = frame.length >= N ? frame : pad(frame, N);
    for (let i = 0; i < N; i++) { this._re[i] = src[i] * this._win[i]; this._im[i] = 0; }
    fft(this._re, this._im);
    const half = N >> 1;
    const db = new Float32Array(half);
    for (let i = 0; i < half; i++) {
      const p = this._re[i] * this._re[i] + this._im[i] * this._im[i];
      db[i] = 10 * Math.log10(p + 1e-12);
    }

    // 3) 共振峰带能量 → softmax(温度 dB)
    const raw = { A: 0, E: 0, I: 0, O: 0, U: 0 };
    for (const b of BANDS) raw[b.vowel] += bandDb(db, b.lo, b.hi, this._binHz);
    const keys = Object.keys(raw);
    const mx = Math.max(...keys.map(k => raw[k]));
    let z = 0;
    const soft = {};
    for (const k of keys) { soft[k] = Math.exp((raw[k] - mx) / this.tempDb); z += soft[k]; }
    for (const k of keys) soft[k] /= z;

    // 4) 平滑:上升用 attack,下降用 release(按帧周期离散化的指数插值)
    const aUp = 1 - Math.exp(-(this.frameSize / this.sampleRate * 1000) / this.attackMs);
    const aDown = 1 - Math.exp(-(this.frameSize / this.sampleRate * 1000) / this.releaseMs);
    const silence = rms < this.silenceRms;
    for (const k of keys) {
      const target = silence ? 0 : soft[k];
      const a = target > this._smooth[k] ? aUp : aDown;
      this._smooth[k] += (target - this._smooth[k]) * a;
    }
    // 静音时保持 softmax 归一(全 0 时置 0,不产生负数/NaN)
    const wsum = keys.reduce((s, k) => s + this._smooth[k], 0);
    const weights = wsum > 1e-6 ? keys.reduce((o, k) => (o[k] = this._smooth[k] / wsum, o), {}) : { A: 0, E: 0, I: 0, O: 0, U: 0 };

    // 5) 嘴开度 = max(共振峰加权开度, RMS 开度):元音决定形状,RMS 保底幅度
    const formantOpen = keys.reduce((s, k) => s + weights[k] * VOWELS[k].openness, 0) * Math.min(1, rms * 12);
    const rmsOpen = Math.min(1, rms * 18);
    const openTarget = silence ? 0 : Math.max(formantOpen, rmsOpen * 0.5);
    const aO = openTarget > this._open ? aUp : aDown;
    this._open += (openTarget - this._open) * aO;

    return { weights, dominant: argmax(weights), openness: this._open, rms, recomputeMs: this.frameSize / this.sampleRate * 1000 };
  }
}

function hann(n) { const w = new Float32Array(n); for (let i = 0; i < n; i++) w[i] = 0.5 * (1 - Math.cos((2 * Math.PI * i) / (n - 1))); return w; }
function pad(a, n) { const b = new Float32Array(n); b.set(a.subarray(0, Math.min(a.length, n))); return b; }
function bandDb(db, lo, hi, binHz) {
  let s = 0, c = 0;
  for (let i = 1; i < db.length; i++) {
    const f = i * binHz;
    if (f >= lo && f <= hi) { s += db[i]; c++; }
  }
  return c ? s / c : -120;
}
function argmax(obj) { let best = null, bv = -Infinity; for (const k in obj) if (obj[k] > bv) { bv = obj[k]; best = k; } return best; }
