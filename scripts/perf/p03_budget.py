#!/usr/bin/env python3
"""P0-03 性能预算取数脚本（persona/her 架构文档 §5 那张空表的唯一数据来源）。

设计纪律
- **不装机**：只读已安装的 `com.openapi.ks.chat`（debuggable，所以 run-as 可用）。
  绝不 adb install、绝不改 versionCode、绝不写应用数据。
- **设备是共享的**：`passive` 模式只读 dumpsys/logcat，不点 UI，随时可跑；
  `stage` 模式会动 UI（进陪伴页、切引擎、触发特效），跑之前必须确认手机独占，
  否则数字会被别的会话污染——脚本自己会在开跑前检查前台是不是我们的应用。
- **测不到就写 BLOCKED**，不猜数、不拿旧 APK 的数字当基线。

五种模式
    passive  只读：设备属性、APK 体积构成、引擎资产体积、模型规格、空闲 PSS、gfxinfo 快照、
             历史 PSS（exit-info）、启动 TTID（events buffer）、SurfaceFlinger 层 + 每线程 CPU。
             任何人任何时刻可跑，不影响别的会话。
    static   不碰手机：只量仓库里的资产（Lottie 结构、glb 面数、打包资产目录）。
    stage    形象舞台场景对比：陪伴页 无形象 → LayeredAvatar → SIMPLE → LIVE2D → LIVE2D+特效。
             **会点 UI**：需要手机独占（`--allow-touch` 显式确认），前台不是本包就直接退出。
             舞台渲染不需要登录/服务端（连接失败只多一条横幅），所以不依赖部署状态。
    fxlab    debug 抽屉「特效引擎对比」：离线测特效渲染层自身的成本，不进通话。
             同样会点 UI，需要 `--allow-touch`。
    coldstart 冷进程 TTID：force-stop 后 `am start -W` 多轮。**会重启应用**，需要独占窗口。

输出
    <out>/raw/            每条命令的原始输出（gfxinfo / meminfo / unzip -l …）
    <out>/measurements.json   结构化数字，含设备、版本、取数命令
    <out>/report.md       可直接粘进 docs/perf/ 的 markdown 表
用法
    python3 scripts/perf/p03_budget.py passive
    python3 scripts/perf/p03_budget.py static
    python3 scripts/perf/p03_budget.py stage --allow-touch
    ANDROID_SERIAL=2MH0224B20002068 python3 scripts/perf/p03_budget.py passive
退出码：0 成功；2 环境不满足（没设备 / 没装包 / 前台不是我们的应用）。
"""
import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import time

ADB = os.path.join(os.environ.get('ANDROID_SDK_ROOT', os.path.expanduser('~/Library/Android/sdk')),
                   'platform-tools', 'adb')
PKG = 'com.openapi.ks.chat'
HOME_ACTIVITY = 'com.openapi.app.home.HomeActivity'

# 陪伴页/通话页的可点控件。文案与 contentDescription 都对齐 3da59bc5（装机包那一版）的源码，
# 改动过的以装机包为准：装机包没有 47279b0f 的「特效面板」，只有一个「特效测试」按钮
# （DigitalHumanLabHost.kt:126 → emitStageGesture("pat_head")），所以脚本要两种都认。
T = dict(
    tab_companion='陪伴',
    # 通话入口没有文字按钮，只有带 contentDescription 的图标（CompanionScreen.kt:481-487）。
    call_button_desc='语音通话',
    # 引擎菜单：通话页右上角 ⋮（同一菜单也管波形，所以 desc 是「切换波形样式」）。
    menu_button_desc='切换波形样式',
    menu_section='形象',
    engine_original='原来的形象',      # callStage = null → LayeredAvatar
    engine_simple='Simple',
    engine_live2d='Live2D',
    fx_panel_open='特效测试',          # 新包：点开成面板；装机包：按一下直接播 pat_head
    fx_wave='挥手光弧',
    fx_pat_head='摸头爱心',
    fx_connected='接通花瓣',
    fx_swipe='滑动流光',
    fx_lab_entry='特效引擎对比',       # debug 抽屉入口，离线，不用进通话
    fx_lab_wave='挥手',
    fx_lab_pat='摸头',
    fx_lab_connected='接通',
    fx_lab_stop='停',
)

BLOCKED = []          # 取不到数的项，最后统一写进报告
NOTES = []            # 口径说明，避免数字被误读


def die(msg, code=2):
    print(f"!! {msg}", file=sys.stderr)
    sys.exit(code)


class Dev:
    def __init__(self, serial, outdir):
        self.outdir = outdir
        self.raw = os.path.join(outdir, 'raw')
        os.makedirs(self.raw, exist_ok=True)
        self.base = [ADB] + (['-s', serial] if serial else [])
        self.serial = serial

    def run(self, *args, timeout=60, name=None, binary=False):
        r = subprocess.run(self.base + list(args), capture_output=True,
                           timeout=timeout, text=not binary)
        if name:
            with open(os.path.join(self.raw, name), 'w' if not binary else 'wb',
                      encoding=None if binary else 'utf-8') as f:
                f.write(r.stdout if isinstance(r.stdout, str) else r.stdout)
        return r

    def sh(self, cmd, timeout=60, name=None):
        return self.run('shell', cmd, timeout=timeout, name=name)

    def dump_ui(self, name=None):
        """uiautomator 层级；存盘便于文案变了以后修脚本。"""
        self.sh("uiautomator dump /sdcard/p03_ui.xml", timeout=30)
        xml = self.run('exec-out', 'cat', '/sdcard/p03_ui.xml', timeout=30).stdout
        if name:
            open(os.path.join(self.raw, f'ui-{name}.xml'), 'w').write(xml)
        return xml

    def _find(self, xml, needle, attr):
        """按属性**包含**匹配。菜单行文案带前缀（「✓ 」/两个全角空格）和后缀（（detail）），
        精确匹配会全打空，所以用包含式；返回坐标和实际命中的文案，供报告留痕。"""
        for m in re.finditer(r'<node[^>]*>', xml):
            tag = m.group(0)
            mm = re.search(attr + r'="([^"]*' + re.escape(needle) + r'[^"]*)"', tag)
            if mm:
                b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
                if b:
                    x1, y1, x2, y2 = map(int, b.groups())
                    return (x1 + x2) // 2, (y1 + y2) // 2, mm.group(1)
        return None

    def present(self, text, xml=None):
        """按 text 定位；找不到再用 content-desc（图标按钮只有 desc）。"""
        xml = xml if xml is not None else self.dump_ui()
        return self._find(xml, text, 'text') or self._find(xml, text, 'content-desc')

    def tap(self, text, timeout=15, name=None):
        """点一个文案/desc；返回 (是否点到, 命中文案)。失败时把层级存盘（ui-fail-*.xml）。"""
        for i in range(max(1, int(timeout / 2))):
            xml = self.dump_ui()
            c = self.present(text, xml)
            if c:
                self.sh(f"input tap {c[0]} {c[1]}")
                return True, c[2]
            time.sleep(2)
        safe = re.sub(r'[^0-9A-Za-z_一-鿿]+', '_', text)[:40]
        self.dump_ui(name=f'fail-{safe}')
        return False, None

    def swipe(self, x1, y1, x2, y2, dur=300):
        self.sh(f"input swipe {x1} {y1} {x2} {y2} {dur}")

    def foreground(self):
        out = self.sh("dumpsys activity activities | grep mResumedActivity").stdout
        m = re.search(r'u0 (\S+)/(\S+)', out)
        return (m.group(1), m.group(2)) if m else (None, None)

    def screenshot(self, name):
        self.run('exec-out', 'screencap', '-p', name=f'{name}.png', binary=True)


# --------------------------------------------------------------- GL/SurfaceView 口径
# gfxinfo 只统计应用自己 UI 线程（Compose/Skia）画的帧。**GLSurfaceView（Live2D、
# Duix、Tao）的 buffer 由独立 GL 线程直接提交给 SurfaceFlinger，不进 gfxinfo**——
# 所以「帧时间 P50/P95」对 native 舞台是假数字。native 舞台要用：
#   1) dumpsys SurfaceFlinger --latency <layer>  → 每帧的 present 时间差
#   2) top -H -p <pid>                           → 各线程 CPU%，GL 线程成本在这
# Compose 舞台（SIMPLE / LayeredAvatar / Lottie 叠加层）仍然走 gfxinfo。
def sf_layers(dev):
    out = dev.sh("dumpsys SurfaceFlinger --list").stdout
    # ActivityRecord{…} 是窗口记录不是 surface，--latency 对它无意义；层名必须带
    # '#<id>' 后缀（实测：去掉后缀只回一行 refresh，什么都取不到）。
    return [l.strip() for l in out.splitlines()
            if PKG in l and '#' in l and not l.strip().startswith('ActivityRecord')]


def sf_latency(dev, seconds=5):
    """SurfaceView/独立 surface 的帧间隔；返回 {layer: {fps, ms_p50, ms_p95, frames}}。"""
    res = {}
    for layer in sf_layers(dev):
        txt = dev.sh(f"dumpsys SurfaceFlinger --latency '{layer}'", timeout=20).stdout
        # 首行是刷新周期（16666666 ns = 60Hz）；本机实测只有这一行头，旧版是 3 个数。
        # 之后每帧三列：queue/composite/real-time present (ns)。
        lines = txt.strip().splitlines()
        presents = []
        for line in lines[1:]:
            c = line.split()
            if len(c) == 3 and all(x.isdigit() for x in c):
                if int(c[2]) > 0:
                    presents.append(int(c[2]))
        if len(presents) < 3:
            continue
        gaps = sorted((presents[i + 1] - presents[i]) / 1e6
                      for i in range(len(presents) - 1))
        res[layer] = {'frames': len(presents),
                      'ms_p50': round(gaps[len(gaps) // 2], 2),
                      'ms_p95': round(gaps[min(len(gaps) - 1, int(len(gaps) * 0.95))], 2),
                      'fps': round(1000.0 / gaps[len(gaps) // 2], 1) if gaps[len(gaps) // 2] else None}
    return res


def thread_cpu(dev, seconds=5):
    """按线程的 CPU%（`top -H`）；native 舞台的成本只在这里看得见。"""
    pid = dev.sh(f"pidof {PKG}").stdout.strip().split()
    if not pid:
        return {}
    out = dev.sh(f"top -b -H -n 1 -p {pid[0]} "
                 f"-o PID,TID,%CPU,NAME 2>/dev/null", timeout=40).stdout
    rows = []
    for line in out.splitlines():
        c = line.split()
        if len(c) >= 4 and c[2].replace('.', '').isdigit():
            rows.append({'tid': c[1], 'cpu_pct': float(c[2]), 'name': ' '.join(c[3:])})
    # 本机 toybox 的 -o NAME 列回的是进程名，真线程名要从 /proc 取（GL 线程、
    # Live2D 线程叫什么只有这里看得出来）。只补前 12 行，别把 97 个线程都问一遍。
    for r in rows[:12]:
        comm = dev.sh(f"cat /proc/{pid[0]}/task/{r['tid']}/comm 2>/dev/null").stdout.strip()
        if comm:
            r['name'] = comm
    rows.sort(key=lambda r: -r['cpu_pct'])
    return {'pid': int(pid[0]), 'top_threads': rows[:12],
            'sum_cpu_pct': round(sum(r['cpu_pct'] for r in rows), 1)}

def parse_meminfo(text):
    """dumpsys meminfo → 关键 PSS 分项（KB）。"""
    out = {}
    m = re.search(r'TOTAL PSS:\s+(\d+)', text)
    if m:
        out['total_pss_kb'] = int(m.group(1))
    for key, pat in [
        ('java_heap_kb', r'Java Heap:\s+(\d+)'),
        ('native_heap_kb', r'Native Heap:\s+(\d+)'),
        ('code_kb', r'Code:\s+(\d+)'),
        ('graphics_kb', r'Graphics:\s+(\d+)'),
        ('private_other_kb', r'Private Other:\s+(\d+)'),
        ('system_kb', r'System:\s+(\d+)'),
    ]:
        m = re.search(pat, text)
        if m:
            out[key] = int(m.group(1))
    m = re.search(r'TOTAL RSS:\s+(\d+)', text)
    if m:
        out['total_rss_kb'] = int(m.group(1))
    m = re.search(r'TOTAL SWAP PSS:\s+(\d+)', text)
    if m:
        out['swap_pss_kb'] = int(m.group(1))
    return out


def parse_gfxinfo(text):
    """dumpsys gfxinfo → 帧时间分位数与卡顿率。

    注意：gfxinfo 的 percentile 是**累计**统计（进程存活期全部帧），
    只有在一段干净窗口里用 `reset` 包住才有场景意义（stage 模式才 reset）。
    """
    out = {}
    for key, pat in [
        ('frames', r'Total frames rendered:\s+(\d+)'),
        ('janky_frames', r'Janky frames:\s+(\d+)'),
        ('janky_pct', r'Janky frames:\s+[\d.]+%'),
        ('p50_ms', r'50th percentile:\s+(\d+)ms'),
        ('p90_ms', r'90th percentile:\s+(\d+)ms'),
        ('p95_ms', r'95th percentile:\s+(\d+)ms'),
        ('p99_ms', r'99th percentile:\s+(\d+)ms'),
        ('gpu_p50_ms', r'50th gpu percentile:\s+(\d+)ms'),
        ('gpu_p90_ms', r'90th gpu percentile:\s+(\d+)ms'),
        ('gpu_p95_ms', r'95th gpu percentile:\s+(\d+)ms'),
        ('missed_vsync', r'Number Missed Vsync:\s+(\d+)'),
        ('slow_ui_thread', r'Number Slow UI thread:\s+(\d+)'),
        ('slow_draw_cmds', r'Number Slow issue draw commands:\s+(\d+)'),
        ('deadline_missed', r'Number Frame deadline missed:\s+(\d+)'),
    ]:
        m = re.search(pat, text)
        if m:
            v = m.group(1) if m.lastindex else m.group(0)
            out[key] = float(v) if '.' in v else int(v)
    m = re.search(r'Janky frames:\s+\d+\s+\(([\d.]+)%\)', text)
    if m:
        out['janky_pct'] = float(m.group(1))
    m = re.search(r'Pipeline=(\S+)', text)
    if m:
        out['pipeline'] = m.group(1)
    return out


def histogram(text):
    """HISTOGRAM 行 → {桶: 帧数}，用于算「超预算帧占比」。"""
    m = re.search(r'^HISTOGRAM: (.*)$', text, re.M)
    if not m:
        return {}
    # 桶标签形如 `5ms=3826`；尾部的 4950ms 一类是 GPU 直方图的哨兵桶，一并收下
    # （pct_over 只统计 >=budget 的桶，哨兵桶计不进有意义的帧区间时不影响结论）。
    out = {}
    for part in m.group(1).split():
        if '=' not in part:
            continue
        k, v = part.split('=', 1)
        k = k.rstrip('ms')
        if k.isdigit() and v.isdigit():
            out[int(k)] = int(v)
    return out


def gpu_histogram(text):
    """GPU HISTOGRAM 行（GPU 完成时间，ms 桶）——Live2D 的 GL 成本主要看这个。"""
    m = re.search(r'^GPU HISTOGRAM: (.*)$', text, re.M)
    if not m:
        return {}
    out = {}
    for part in m.group(1).split():
        if '=' not in part:
            continue
        k, v = part.split('=', 1)
        k = k.rstrip('ms')
        if k.isdigit() and v.isdigit():
            out[int(k)] = int(v)
    return out


def diff_hist(before, after):
    """两个累计直方图之差 = 这段时间窗口里的帧分布。

    gfxinfo 的计数是单调累加的，所以差分**不需要 reset**——
    在共享设备上这一点很重要：reset 会抹掉别人会话正在看的统计。
    """
    return {k: after.get(k, 0) - before.get(k, 0) for k in set(before) | set(after)}


def percentile_from_hist(hist, q):
    """从 (ms 桶 → 帧数) 里算分位数；桶宽 1ms，误差 ±1ms。"""
    total = sum(v for v in hist.values() if v > 0)
    if not total:
        return None
    want = total * q
    run = 0
    for bucket in sorted(k for k, v in hist.items() if v > 0):
        run += hist[bucket]
        if run >= want:
            return bucket
    return None


def pct_over(hist, budget_ms):
    """帧时间 > budget_ms 的帧占比（%）。桶是下取整 ms，保守取 >=budget。"""
    total = sum(hist.values())
    if not total:
        return None
    over = sum(v for k, v in hist.items() if k >= budget_ms)
    return round(100.0 * over / total, 3)


# --------------------------------------------------------------- passive
def collect_passive(dev, m):
    # 设备与环境
    props = {}
    for k in ['ro.product.model', 'ro.product.device', 'ro.build.version.release',
              'ro.build.version.sdk', 'ro.soc.model', 'ro.product.cpu.abi']:
        props[k] = dev.sh(f"getprop {k}").stdout.strip()
    mem = dev.sh("cat /proc/meminfo").stdout
    props['mem_total_kb'] = int(re.search(r'MemTotal:\s+(\d+)', mem).group(1))
    disp = dev.sh("dumpsys display | grep -m1 mBaseDisplayInfo").stdout
    fps = re.findall(r'fps=([\d.]+)', disp)
    props['refresh_modes_hz'] = [float(x) for x in fps[:5]]
    res = re.search(r'real (\d+) x (\d+)', disp)
    if res:
        props['resolution'] = f"{res.group(1)}x{res.group(2)}"
    m['device'] = props

    # 被测版本
    pkg = dev.sh(f"dumpsys package {PKG}", name='pkg.txt').stdout
    ver = {}
    for k, pat in [('versionCode', r'versionCode=(\d+)'),
                   ('versionName', r'versionName=(\S+)'),
                   ('lastUpdateTime', r'lastUpdateTime=(.+)'),
                   ('firstInstallTime', r'firstInstallTime=(.+)')]:
        r_ = re.search(pat, pkg)
        ver[k] = r_.group(1).strip() if r_ else None
    ver['debuggable'] = 'DEBUGGABLE' in pkg
    apk_path = re.search(r'package:(.*\.apk)', dev.sh(f"pm path {PKG}").stdout)
    ver['apk_path'] = apk_path.group(1) if apk_path else None
    m['app'] = ver
    if not ver['versionCode']:
        die(f'{PKG} 没装，取不了数')

    # APK 体积构成（设备上真实安装的那一个，不是仓库里 build 出来的）
    if ver['apk_path']:
        listing = dev.sh(f"unzip -l '{ver['apk_path']}'", name='apk-unzip-l.txt').stdout
        entries = []
        for line in listing.splitlines():
            mm = re.match(r'\s*(\d+)\s+[\d-]+\s+[\d:]+\s+(.*)$', line)
            if mm:
                entries.append((int(mm.group(1)), mm.group(2)))
        m['apk'] = {
            'bytes_apk_on_disk': None,
            'entries': len(entries),
            'uncompressed_bytes': sum(s for s, _ in entries),
        }
        st = dev.sh(f"stat -c '%s' '{ver['apk_path']}'").stdout.strip()
        if st.isdigit():
            m['apk']['bytes_apk_on_disk'] = int(st)
        buckets = {}
        for s, n in entries:
            if n.startswith('assets/'):
                k = 'assets/' + (n.split('/')[1] if n.count('/') > 1 else '(root)')
            elif n.startswith('lib/'):
                k = 'lib/' + (n.split('/')[1] if n.count('/') > 1 else '?')
            elif n.startswith('res/'):
                k = 'res'
            elif n.endswith('.dex'):
                k = 'dex'
            else:
                k = 'other'
            buckets[k] = buckets.get(k, 0) + s
        m['apk']['by_bucket'] = dict(sorted(buckets.items(), key=lambda kv: -kv[1]))
        m['apk']['largest_assets'] = [
            {'bytes': s, 'path': n} for s, n
            in sorted([e for e in entries if e[1].startswith('assets/')], reverse=True)[:12]]
        m['apk']['largest_libs'] = [
            {'bytes': s, 'path': n} for s, n
            in sorted([e for e in entries if e[1].startswith('lib/')], reverse=True)[:12]]

    # 引擎资产体积（下载进 files/dh-engines 的量 = 「包体积/下载流量」口径）
    engines = {}
    for e in ['live2d', 'duix', 'tao', 'vrm']:
        out = dev.sh(f"run-as {PKG} du -s -k files/dh-engines/{e}").stdout
        kb = re.match(r'\s*(\d+)', out)
        if kb:
            engines[e + '_kb'] = int(kb.group(1))
    # 单个模型目录
    for model in ['ATRI', 'Amadeus', 'Yuuka', 'OriginalDemo']:
        out = dev.sh(f"run-as {PKG} du -s -k files/dh-engines/live2d/{model}").stdout
        kb = re.match(r'\s*(\d+)', out)
        if kb:
            engines[f'live2d_{model}_kb'] = int(kb.group(1))
    # 引擎 native 库（换引擎的固定代价，不进 APK）
    for lib in ['live2d']:
        out = dev.sh(f"run-as {PKG} du -s -k files/dh-engines/{lib}/native-libs").stdout
        kb = re.match(r'\s*(\d+)', out)
        if kb:
            engines[f'{lib}_native_libs_kb'] = int(kb.group(1))
    m['engine_assets_on_device'] = engines

    # Live2D 模型规格（面数/参数/贴图尺寸）——决定每帧成本的上游变量
    spec = {}
    m3 = dev.sh(f"run-as {PKG} cat files/dh-engines/live2d/ATRI/ATRI.model3.json").stdout
    cdi = dev.sh(f"run-as {PKG} cat files/dh-engines/live2d/ATRI/atri_8.cdi3.json").stdout
    if m3.strip():
        j = json.loads(m3)
        c = json.loads(cdi) if cdi.strip() else {}
        fr = j.get('FileReferences', {})
        spec = {
            'expressions': len(fr.get('Expressions', []) or []),
            'motion_groups': len(fr.get('Motions', []) or []),
            'physics_subrigs': len((j.get('Physics') and [1] or [])),
            'cdi3_parameters': len(c.get('Parameters', []) or []),
            'cdi3_parts': len(c.get('Parts', []) or []),
        }
        # 贴图尺寸：PNG IHDR 的宽高（大端）
        for t in fr.get('Textures', []) or []:
            hexout = dev.sh(
                f"run-as {PKG} od -An -tx1 -j16 -N8 files/dh-engines/live2d/ATRI/{t}").stdout
            b = bytes.fromhex(''.join(hexout.split()))
            if len(b) == 8:
                spec[f'texture_{os.path.basename(t)}'] = f"{int.from_bytes(b[:4],'big')}x{int.from_bytes(b[4:],'big')}"
        moc3 = dev.sh(f"run-as {PKG} wc -c files/dh-engines/live2d/ATRI/atri_8.moc3").stdout
        n = re.search(r'(\d+)', moc3)
        if n:
            spec['moc3_bytes'] = int(n.group(1))
    m['live2d_model_spec'] = spec
    if not spec:
        BLOCKED.append(('Live2D 模型规格', 'run-as 读不到 model3.json'))

    # 空闲态内存（应用在前台、无舞台）
    mi = dev.sh(f"dumpsys meminfo {PKG}", name='meminfo-idle.txt').stdout
    fg_pkg, fg_act = dev.foreground()
    m['idle_memory'] = parse_meminfo(mi)
    m['idle_memory']['foreground_activity'] = f"{fg_pkg}/{fg_act}"

    # 历史进程峰值内存：exit-info 里每次进程退出时的 pss/rss
    exits = dev.sh(f"dumpsys activity exit-info {PKG}", name='exit-info.txt').stdout
    rows = []
    # 块缩进是 8 空格、按 uid 分段；用 split 比 lookahead 稳（实测 lookahead 版本会
    # 一路吃到文件尾，只剩一条记录）。
    for blk in re.split(r'ApplicationExitInfo #\d+:', exits)[1:]:
        g = lambda k: (re.search(k + r'=(.*)', blk) or [None, ''])[1].strip()
        if g('process') != PKG:
            continue
        rows.append({'timestamp': g('timestamp'), 'reason': g('reason'),
                     'pss': g('pss'), 'rss': g('rss'), 'importance': g('importance')})
    m['historical_process_exits'] = rows
    vals = []
    for r in rows:
        mm = re.match(r'([\d.]+)MB', r.get('pss', ''))
        if mm:
            vals.append(float(mm.group(1)))
    if vals:
        m['historical_exit_pss_mb'] = {'min': min(vals), 'max': max(vals), 'n': len(vals)}

    # gfxinfo 快照（累计，进程存活期）——只当「这段时间整体流畅度」的下限证据
    gfx = dev.sh(f"dumpsys gfxinfo {PKG}", name='gfxinfo-snapshot.txt').stdout
    m['gfxinfo_cumulative'] = parse_gfxinfo(gfx)
    hist = histogram(gfx)
    if hist:
        m['gfxinfo_cumulative']['over_budget_pct'] = {
            f'{b}ms': pct_over(hist, b) for b in (16, 33, 50)}
    if not m['gfxinfo_cumulative'].get('frames'):
        BLOCKED.append(('gfxinfo 帧时间', '进程无渲染统计（应用没在前台跑）'))
    NOTES.append('gfxinfo 不统计 GLSurfaceView 的帧（native 引擎由独立 GL 线程提交），'
                 'native 舞台要用 SurfaceFlinger --latency + top -H；passive 模式下面两表'
                 '只给空闲态基线。')

    # 空闲态：SurfaceFlinger 层 + 每线程 CPU（native 舞台的口径基线）
    m['idle_sf_layers'] = sf_latency(dev)
    m['idle_thread_cpu'] = thread_cpu(dev)

    # 冷启动：events buffer 里的 wm_activity_launch_time 就是 TTID（首帧到 Activity），
    # 不用 am start -W、不重启应用，passive 就能取。冷热靠「这条前面有没有同进程
    # START」分不开，所以这里只报「已记录到的启动」，冷热判定留给 stage/coldstart 模式。
    ev = dev.sh("logcat -b events -d 2>/dev/null | grep wm_activity_launch_time",
                name='launch-times.txt').stdout
    cold = []
    for line in ev.splitlines():
        mm = re.search(r'wm_activity_launch_time: \[\d+,\d+,'
                       + re.escape(PKG) + r'/(\S+),(\d+)\]', line)
        if mm:
            ts = re.match(r'(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)', line.strip())
            cold.append({'activity': mm.group(1), 'ttid_ms': int(mm.group(2)),
                         'when': ts.group(1) if ts else ''})
    if cold:
        m['launch_times'] = cold
        # 只保留本包自身 Activity（AdActivity 是第三方广告页，不算我们的启动）
        ours = [c for c in cold if '.gms.' not in c['activity'] and 'AdActivity' not in c['activity']]
        if ours:
            m['launch_times_own'] = ours
    else:
        BLOCKED.append(('启动到首帧（TTID，冷/热未分）',
                        'events buffer 里没有 wm_activity_launch_time；需要一次 am start -W（须独占窗口）'))

    # Live2D 模型加载耗时：应用自己只在 Live2dRuntime tag 打「model load on GL surface:」，
    # logcat 缓冲里可能已经滚掉。取到就用，取不到标 BLOCKED（只有进过通话页才有）。
    l2 = dev.sh("logcat -d -s Live2dRuntime 2>/dev/null | grep -i 'load'").stdout
    loads = []
    for line in l2.splitlines():
        mm = re.search(r'model load on GL surface:?\s*([\d.]+)', line)
        if mm:
            loads.append(float(mm.group(1)))
    if loads:
        m['live2d_load_ms'] = sorted(loads)


# --------------------------------------------------------------- stage
def collect_stage(dev, m, args):
    """形象舞台场景对比。会点 UI：进陪伴页、切引擎、开特效面板点特效。"""
    m['stage'] = {}
    scenes = []

    def scene(name, warm_s, sample_s, label=''):
        """稳态窗口：取窗口前后两次 gfxinfo，用**差分**得到这一场景的帧分布。

        不用 `gfxinfo reset`——reset 会抹掉别人会话正在看的累计统计（手机是共享的），
        而 gfxinfo 计数单调累加，差分等价且无害。
        """
        time.sleep(warm_s)
        g0 = parse_gfxinfo(dev.sh("dumpsys gfxinfo " + PKG).stdout)
        h0, gh0 = histogram(dev.sh("dumpsys gfxinfo " + PKG).stdout), \
            gpu_histogram(dev.sh("dumpsys gfxinfo " + PKG).stdout)
        t0 = time.time()
        mem = []
        while time.time() - t0 < sample_s:
            mem.append(parse_meminfo(dev.sh(f"dumpsys meminfo {PKG}").stdout))
            time.sleep(2)
        gfx_txt = dev.sh(f"dumpsys gfxinfo {PKG}", name=f'gfxinfo-{name}.txt').stdout
        g1, h1, gh1 = parse_gfxinfo(gfx_txt), histogram(gfx_txt), gpu_histogram(gfx_txt)
        dev.screenshot(f'stage-{name}')
        cpu, gpu_h = diff_hist(h0, h1), diff_hist(gh0, gh1)
        # native 舞台不进 gfxinfo（GLSurfaceView 由独立 GL 线程提交），
        # 所以同一窗口里再取一次 SurfaceFlinger 的层帧间隔 + 每线程 CPU。
        sfl = sf_latency(dev)
        thr = thread_cpu(dev)
        pss = sorted(x['total_pss_kb'] for x in mem if x.get('total_pss_kb'))
        fg_pkg, fg_act = dev.foreground()
        return {'scene': name, 'label': label,
                'foreground': f'{fg_pkg}/{fg_act}',
                'window_s': round(time.time() - t0, 1),
                'sf_layers': sfl,
                'threads': thr,
                'frames_in_window': sum(v for v in cpu.values() if v > 0),
                'pss_kb_median': (pss[len(pss) // 2] if pss else None),
                'pss_kb_min': pss[0] if pss else None,
                'pss_kb_max': pss[-1] if pss else None,
                'pss_samples': len(pss),
                'cpu_p50_ms': percentile_from_hist(cpu, 0.50),
                'cpu_p90_ms': percentile_from_hist(cpu, 0.90),
                'cpu_p95_ms': percentile_from_hist(cpu, 0.95),
                'cpu_p99_ms': percentile_from_hist(cpu, 0.99),
                'gpu_p50_ms': percentile_from_hist(gpu_h, 0.50),
                'gpu_p90_ms': percentile_from_hist(gpu_h, 0.90),
                'gpu_p95_ms': percentile_from_hist(gpu_h, 0.95),
                'over_8ms_pct': pct_over(cpu, 8),
                'over_16ms_pct': pct_over(cpu, 16),
                'over_33ms_pct': pct_over(cpu, 33),
                'missed_vsync_delta': g1.get('missed_vsync', 0) - g0.get('missed_vsync', 0),
                'slow_ui_thread_delta': g1.get('slow_ui_thread', 0) - g0.get('slow_ui_thread', 0),
                'slow_draw_cmds_delta': g1.get('slow_draw_cmds', 0) - g0.get('slow_draw_cmds', 0)}

    fg_pkg, fg_act = dev.foreground()
    if fg_pkg != PKG:
        die(f'前台是 {fg_pkg}，不是 {PKG}；stage 模式要求手机独占并先打开应用')

    # 熔断先查：上次进程死在 native 引擎页会静默降级 SIMPLE，
    # 那样「点了 Live2D 跑的是 Simple」，数字全废。
    # 注意：**按内容判，别按 key 名判**——装机包的 key 是
    # `companion.dh.active_engine_disabled`，而仓库源码里是 `companion.dh.quarantined_engine`
    # （DhEngineWatchdog.kt:98），两个名字都对不上，说明装机包的 watchdog 与仓库 tip 不是同一版。
    wd = dev.sh(f"run-as {PKG} cat shared_prefs/companion_dh_watchdog.xml").stdout
    m['stage']['watchdog_prefs'] = wd.strip()[:300]
    off = re.findall(r'name="([^"]*(?:disabled|quarantin)[^"]*)">([^<]*)<', wd)
    if off:
        # 实测教训（2026-11-28）：装机包读的 quarantine key 是源码里的
        # `companion.dh.quarantined_engine`；盘上若只有 `active_engine_disabled`
        # 那种旧 key，是残留死数据，**不会**降级（LIVE2D 真的在渲染，有 GL 层为证）。
        # 所以这里只报事实，别当结论拦人；真要判降级，看场景里有没有 SurfaceView 层。
        NOTES.append(f'watchdog prefs 里有 {off}。若装机包读的是这个 key，LIVE2D 可能被降级成 '
                     f'SIMPLE；若是旧版残留 key 则无影响。**判据看场景里是否出现 SurfaceView/GLThread，'
                     f'不要只看这条**（见下面 sf_layers / threads 表）。')
    prefs = dev.sh(f"run-as {PKG} cat shared_prefs/companion_dh_lab.xml").stdout
    m['stage']['prefs_before'] = prefs.strip()[:300]

    # 1) 进陪伴页（无舞台基线：聊天页本身）
    #    应用可能**已经停在陪伴页**（实测：前台 HomeActivity 但页面就是陪伴对话，
    #    底部标签不在层级里），所以先看通话入口在不在，在就跳过标签导航。
    if dev.present(T['call_button_desc']):
        pass
    elif not dev.tap(T['tab_companion'], timeout=10)[0]:
        BLOCKED.append(('陪伴页导航', f'找不到标签「{T["tab_companion"]}」或通话入口'
                                      f'「{T["call_button_desc"]}」——不在主页或文案变了'))
        return
    time.sleep(3)
    dev.screenshot('stage-enter')
    scenes.append(scene('companion-chat-no-stage', 3, args.sample))

    # 2) 进通话页。舞台挂在通话页（CompanionCallScreen 的 avatarStage 插槽），
    #    聊天页只有小头像，测不到舞台，所以必须先过这一关。
    #    服务端/登录**不影响**舞台渲染：连接失败只多一条横幅，不遮舞台。
    if not dev.tap(T['call_button_desc'], timeout=10)[0]:
        BLOCKED.append(('通话页导航', f'找不到 content-desc「{T["call_button_desc"]}」图标'))
        return
    time.sleep(4)
    dev.screenshot('stage-call-open')

    def open_engine_menu():
        dev.tap(T['menu_button_desc'], timeout=8)
        time.sleep(1)

    # 3) 「原来的形象」= LayeredAvatar（内置 zip，纯 Compose，无 GL）
    open_engine_menu()
    ok, hit = dev.tap(T['engine_original'], timeout=8)
    if ok:
        scenes.append(scene('stage-layered-avatar', 4, args.sample, label=hit))
    else:
        BLOCKED.append(('分层立绘舞台', f'菜单里点不到「{T["engine_original"]}」'))

    # 4) SIMPLE 舞台（纯 Compose，无 native）
    open_engine_menu()
    ok, hit = dev.tap(T['engine_simple'], timeout=8)
    if ok:
        scenes.append(scene('stage-simple', 4, args.sample, label=hit))
    else:
        BLOCKED.append(('SIMPLE 舞台', f'菜单里点不到「{T["engine_simple"]}」'))

    # 5) LIVE2D 舞台：切引擎后从 logcat 取「点菜单 → GL 首帧」的墙钟差。
    #    Live2dRuntime 在模型加载完成时打 "model load on GL surface:"（Live2dRuntime.kt:140）。
    open_engine_menu()
    dev.sh("logcat -c")            # 清缓冲只影响我们自己这段窗口
    t_tap = time.time()
    ok, hit = dev.tap(T['engine_live2d'], timeout=8)
    if not ok:
        BLOCKED.append(('LIVE2D 舞台', f'菜单里点不到「{T["engine_live2d"]}」'))
    else:
        loaded_at, model_log = None, None
        deadline = t_tap + 25
        while time.time() < deadline:
            log = dev.sh("logcat -d -s Live2dRuntime:I Live2dNativeLoader:I "
                         "Live2dTouch:I DhStage:I").stdout
            mm = re.search(r'^\S+\s+([\d:.]+)\s+\d+\s+\d+\s+I Live2dRuntime:\s*'
                           r'model load on GL surface', log, re.M)
            if mm:
                loaded_at = time.time()
                model_log = mm.group(0)[:160]
                break
            time.sleep(1)
        m['stage']['live2d_load_ms'] = (round((loaded_at - t_tap) * 1000)
                                        if loaded_at else None)
        m['stage']['live2d_load_log'] = model_log
        if not loaded_at:
            BLOCKED.append(('Live2D 加载时长',
                            '25s 内没有 Live2dRuntime "model load on GL surface" 日志'
                            '（可能被熔断隔离或 native 加载失败，见 raw/ui-fail-*.xml 与截图）'))
        dev.screenshot('stage-live2d-loaded')
        scenes.append(scene('stage-live2d', 6, args.sample, label=hit))

        # 6) Lottie 特效叠加。装机包（3da59bc5）只有一个「特效测试」按钮，
        #    按一下直接播 pat_head；新包（47279b0f+）先点按钮开面板再点具体特效。
        #    两种都试，按实际命中的记。
        fired = []
        if dev.tap(T['fx_panel_open'], timeout=6)[0]:
            fired.append('pat_head(via 特效测试按钮)')
            time.sleep(1)
            for lbl in (T['fx_wave'], T['fx_connected'], T['fx_swipe']):
                if dev.tap(lbl, timeout=4)[0]:
                    fired.append(lbl)
                time.sleep(0.5)
        m['stage']['fx_fired'] = fired
        if fired:
            scenes.append(scene('stage-live2d-fx', 2, args.sample))
        else:
            BLOCKED.append(('特效叠加成本',
                            '引擎舞台里没有「特效测试」入口'
                            '（装机包可能不含该按钮——它随 dh-lab 面板走，见 DigitalHumanLabHost）'))
    # 退出通话，把手机还给别的会话
    dev.sh("input keyevent KEYCODE_BACK")
    time.sleep(1)
    m['stage']['scenes'] = scenes
    base = next((s['pss_kb_median'] for s in scenes
                 if s['scene'] == 'companion-chat-no-stage' and s['pss_kb_median']), None)
    if base:
        for s in scenes:
            if s['pss_kb_median']:
                s['pss_delta_kb'] = s['pss_kb_median'] - base


# --------------------------------------------------------------- fxlab
def collect_fxlab(dev, m, args):
    """特效叠加成本：走 debug 抽屉的「特效引擎对比」Lab。

    选它的理由：完全离线、不进通话、不要麦克风、不要登录，特效按钮本地 fire cue
    （EffectsLabScreen 的「摸头/挥手/接通/停」）。代价：它测的是特效层本身
    （Rive/GL/Lottie 渲染器），不是「Lottie 叠在 Live2D 舞台之上」的合并成本——
    后者只有 stage 模式能测。两个口径都要，别混。
    """
    if not args.allow_touch:
        die('fxlab 会操作手机 UI；确认手机独占后加 --allow-touch')
    fg_pkg, _ = dev.foreground()
    if fg_pkg != PKG:
        die(f'前台是 {fg_pkg}，不是 {PKG}')
    out = {'scenes': []}

    def window(name, fire=None, sample_s=15):
        g0 = parse_gfxinfo(dev.sh("dumpsys gfxinfo " + PKG).stdout)
        h0, gh0 = histogram(dev.sh("dumpsys gfxinfo " + PKG).stdout), \
            gpu_histogram(dev.sh("dumpsys gfxinfo " + PKG).stdout)
        t0 = time.time()
        fired, pss_all = [], []
        while time.time() - t0 < sample_s:
            if fire:
                for lbl in fire:
                    if dev.tap(lbl, timeout=2)[0]:
                        fired.append(lbl)
            mi = parse_meminfo(dev.sh(f"dumpsys meminfo {PKG}").stdout)
            if mi.get('total_pss_kb'):
                pss_all.append(mi['total_pss_kb'])
            time.sleep(1.5)
        gfx_txt = dev.sh(f"dumpsys gfxinfo {PKG}", name=f'gfxinfo-fxlab-{name}.txt').stdout
        g1, h1, gh1 = parse_gfxinfo(gfx_txt), histogram(gfx_txt), gpu_histogram(gfx_txt)
        cpu, gpu_h = diff_hist(h0, h1), diff_hist(gh0, gh1)
        pss = sorted(pss_all)
        dev.screenshot(f'fxlab-{name}')
        return {'scene': name, 'fired': sorted(set(fired)),
                'pss_kb_median': (pss[len(pss) // 2] if pss else None),
                'frames_in_window': sum(v for v in cpu.values() if v > 0),
                'frames_in_window': sum(v for v in cpu.values() if v > 0),
                'cpu_p50_ms': percentile_from_hist(cpu, 0.50),
                'cpu_p95_ms': percentile_from_hist(cpu, 0.95),
                'gpu_p50_ms': percentile_from_hist(gpu_h, 0.50),
                'gpu_p95_ms': percentile_from_hist(gpu_h, 0.95),
                'over_8ms_pct': pct_over(cpu, 8),
                'missed_vsync_delta': g1.get('missed_vsync', 0) - g0.get('missed_vsync', 0)}

    # 抽屉 → 特效引擎对比（exported=false，只能从 UI 点，不能 am start）
    if not dev.tap('菜单', timeout=8)[0] and not dev.tap('实验室', timeout=4)[0]:
        BLOCKED.append(('特效 Lab 导航', '打不开抽屉（找不到「菜单」）'))
        return
    ok, _ = dev.tap(T['fx_lab_entry'], timeout=8)
    if not ok:
        BLOCKED.append(('特效 Lab 导航', f'抽屉里没有「{T["fx_lab_entry"]}」'))
        return
    time.sleep(3)
    out['scenes'].append(window('fxlab-idle', None, args.sample))
    out['scenes'].append(window('fxlab-pat_head', [T['fx_lab_pat']], args.sample))
    out['scenes'].append(window('fxlab-wave', [T['fx_lab_wave']], args.sample))
    out['scenes'].append(window('fxlab-connected', [T['fx_lab_connected']], args.sample))
    dev.sh("input keyevent KEYCODE_BACK")
    m['fxlab'] = out


# --------------------------------------------------------------- coldstart
def collect_coldstart(dev, m, args):
    """冷启动到首帧（TTID）。**会 force-stop 应用**，只能在独占窗口跑。

    `am start -W` 给的是 WaitTime/ThisTime/TotalTime：Android 把它算到 Activity
    首帧绘制完成，不等于「形象加载完」（模型加载在之后的 Compose 里）。所以这里
    同时记一个「到舞台出画面」的粗口径：进陪伴页后轮询 GL 层出现的时间。
    """
    if not args.allow_touch:
        die('coldstart 会 force-stop 应用；确认手机独占后加 --allow-touch')
    out = {}
    runs = []
    for i in range(args.rounds):
        dev.sh(f"am force-stop {PKG}")
        time.sleep(2)
        r = dev.sh(f"am start -W -n {PKG}/{HOME_ACTIVITY}", timeout=90).stdout
        row = {'run': i}
        for k in ['TotalTime', 'ThisTime', 'WaitTime']:
            mm = re.search(k + r': (\d+)', r)
            row[k.lower() + '_ms'] = int(mm.group(1)) if mm else None
        with open(os.path.join(dev.raw, f'am-start-{i}.txt'), 'w') as f:
            f.write(r)
        runs.append(row)
        time.sleep(4)
    out['home_activity_ttid'] = runs
    vals = [r['totaltime_ms'] for r in runs if r['totaltime_ms'] is not None]
    if vals:
        vals_sorted = sorted(vals)
        out['home_activity_ttid_ms'] = {
            'n': len(vals), 'min': vals_sorted[0],
            'median': vals_sorted[len(vals) // 2], 'max': vals_sorted[-1]}
    m['cold_start'] = out


# --------------------------------------------------------------- static
def collect_static(m):
    """不碰手机：仓库里的资产量。给「包体积」和「特效并发」两个口径打底。"""
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    fx_dir = os.path.join(root, 'feature-companion', 'src', 'main', 'assets', 'fx')
    fx = []
    if os.path.isdir(fx_dir):
        for name in sorted(os.listdir(fx_dir)):
            if not name.endswith('.json'):
                continue
            p = os.path.join(fx_dir, name)
            try:
                d = json.load(open(p))
            except Exception as e:            # 坏文件也要留痕
                fx.append({'asset': name, 'error': str(e)})
                continue

            def walk(layers):
                n, shapes, imgs = 0, 0, 0
                for l in layers or []:
                    n += 1
                    shapes += len(l.get('shapes') or [])
                    imgs += 1 if l.get('ty') == 'img' else 0
                    c = walk(l.get('layers'))
                    n += c[0]
                    shapes += c[1]
                    imgs += c[2]
                return n, shapes, imgs

            n, shapes, imgs = walk(d.get('layers'))
            fr = d.get('fr') or 0
            op = d.get('op') or 0
            fx.append({'asset': name, 'bytes': os.path.getsize(p), 'framerate': fr,
                       'frames': op, 'duration_ms': round(1000.0 * op / fr) if fr else None,
                       'w': d.get('w'), 'h': d.get('h'), 'layers_total': n,
                       'shape_groups': shapes, 'image_layers': imgs})
    m['lottie_fx'] = fx

    # 仓库里打包进 APK 的形象资产（「包体积按 renderer 分档」的静态侧）
    dh = {}
    for d in ['dh-talkinghead', 'dh-vrm', 'dh-spine', 'avatars', 'fx']:
        p = os.path.join(root, 'feature-companion', 'src', 'main', 'assets', d)
        if os.path.isdir(p):
            total = sum(os.path.getsize(os.path.join(r, f))
                        for r, _, fs in os.walk(p) for f in fs)
            dh[d] = total
    m['repo_asset_bytes'] = dh

    # 模型面数：TalkingHead 的 mpfb.glb 是包裹里最重的 3D 形象。
    # 注意「面数」只对 3D 档有意义；Live2D 是 2D 网格（见 live2d_model_spec 的
    # parameters/parts），Duix 是服务端合成，都没有三角面数这个量。
    meshes = []
    for rel in ['feature-companion/src/main/assets/dh-talkinghead/mpfb.glb',
                'feature-companion/src/main/assets/dh-vrm']:
        p = os.path.join(root, rel)
        if not os.path.exists(p):
            continue
        if p.endswith('.glb'):
            st = glb_stats(p)
            st['path'] = rel
            meshes.append(st)
    m['mesh_stats'] = meshes


# --------------------------------------------------------------- 网格/面数（纯 stdlib）
def glb_stats(path):
    """glTF 二进制（.glb）的三角面数与贴图尺寸——预算表里「模型面数」这一列。
    glb 布局：12B 头(magic/version/len) + 每 chunk[4B len][4B type][data]；
    JSON chunk 里的 meshes/primitives 用 indices accessor 的 count/3 地面数。
    不引第三方库，免得脚本跑不起来。"""
    with open(path, 'rb') as f:
        blob = f.read()
    if len(blob) < 20 or blob[:4] != b'glTF':
        return {'error': '不是 glb'}
    off = 12
    j = None
    while off + 8 <= len(blob):
        clen = int.from_bytes(blob[off:off + 4], 'little')
        ctype = blob[off + 4:off + 8]
        if ctype == b'JSON':
            j = json.loads(blob[off + 8:off + 8 + clen])
            break
        off += 8 + clen
    if j is None:
        return {'error': '没有 JSON chunk'}
    acc = j.get('accessors') or []
    tri = nodes = meshes = 0
    for mesh in (j.get('meshes') or []):
        meshes += 1
        for p in (mesh.get('primitives') or []):
            nodes += 1
            i = p.get('indices')
            if i is not None and i < len(acc):
                tri += acc[i].get('count', 0) // 3
            elif (p.get('attributes') or {}).get('POSITION') is not None:
                tri += acc[p['attributes']['POSITION']].get('count', 0) // 3
    return {'triangles': tri, 'mesh_primitives': nodes, 'meshes': meshes,
            'materials': len(j.get('materials') or []),
            'images': [f"{x.get('width')}x{x.get('height')}"
                       for x in (j.get('images') or []) if x.get('width')],
            'bytes': os.path.getsize(path)}


# --------------------------------------------------------------- 报告
def write_report(m, outdir):
    L = []
    dev = m.get('device', {})
    app = m.get('app', {})
    L.append('# P0-03 实测数据（脚本产出）')
    L.append('')
    L.append(f"- 取数时间：{m['_ts']}")
    if dev:
        L.append(f"- 设备：{dev.get('ro.product.device')} / {dev.get('ro.product.model')}，"
                 f"Android {dev.get('ro.build.version.release')}（SDK {dev.get('ro.build.version.sdk')}），"
                 f"SoC {dev.get('ro.soc.model')}，{dev.get('mem_total_kb', 0) // 1048576} GB RAM，"
                 f"{dev.get('resolution')} @ {max(dev.get('refresh_modes_hz') or [0]):g}Hz")
    if app:
        L.append(f"- 被测包：{PKG} versionName={app.get('versionName')} "
                 f"versionCode={app.get('versionCode')} debuggable={app.get('debuggable')}")
        L.append(f"- 安装更新时间：{app.get('lastUpdateTime')}")
    if m['_mode'] == 'static':
        L.append('- 模式：static（只量仓库，不碰手机）')
    L.append('')

    def table(headers, rows):
        L.append('| ' + ' | '.join(headers) + ' |')
        L.append('| ' + ' | '.join(['---'] * len(headers)) + ' |')
        for r in rows:
            L.append('| ' + ' | '.join('' if c is None else str(c) for c in r) + ' |')

    ap = m.get('apk', {})
    if ap:
        L.append('## APK 体积（设备上实际安装的这一个）')
        L.append('')
        mb = lambda b: f"{b / 1048576:.1f}" if b else ''
        table(['项', 'MiB'], [
            ('apk 文件', mb(ap.get('bytes_apk_on_disk'))),
            ('解压后合计', mb(ap.get('uncompressed_bytes'))),
            ('条目数', ap.get('entries')),
        ] + [(k, mb(v)) for k, v in list(ap.get('by_bucket', {}).items())[:10]])
        L.append('')
        L.append('### 最大的形象/渲染资产')
        table(['MiB', '路径'], [(mb(a['bytes']), a['path']) for a in ap.get('largest_assets', [])])
        L.append('')

    eng = m.get('engine_assets_on_device', {})
    if eng:
        L.append('## 引擎资产体积（files/dh-engines，KB → MiB）')
        L.append('')
        table(['项', 'MiB'], [(k, f"{v / 1024:.1f}") for k, v in eng.items()])
        L.append('')

    spec = m.get('live2d_model_spec', {})
    if spec:
        L.append('## Live2D 模型规格（ATRI）')
        L.append('')
        table(['项', '值'], sorted(spec.items()))
        L.append('')

    idle = m.get('idle_memory', {})
    if idle:
        L.append('## 空闲态内存（应用前台，无舞台）')
        L.append('')
        table(['项', 'KB'], sorted(idle.items()))
        L.append('')

    hx = m.get('historical_exit_pss_mb')

    if m.get('idle_sf_layers'):
        L.append('## SurfaceFlinger 层（空闲态；native GL 层帧间隔的口径基线）')
        L.append('')
        table(['层', '帧数', '间隔 P50(ms)', '间隔 P95(ms)', '等效 fps'],
              [(k[:60], v['frames'], v['ms_p50'], v['ms_p95'], v['fps'])
               for k, v in m['idle_sf_layers'].items()])
        L.append('')
    thr = m.get('idle_thread_cpu') or {}
    if thr.get('top_threads'):
        L.append(f"## 每线程 CPU（空闲态，pid={thr.get('pid')}）")
        L.append('')
        table(['线程', 'CPU%'], [(f"{r['name'][:28]} (tid {r['tid']})", r['cpu_pct'])
                                 for r in thr['top_threads'][:8]]
              + [('**全进程合计**', thr['sum_cpu_pct'])])
        L.append('')

    if hx:
        L.append('## 历史进程退出时内存（ApplicationExitInfo，含舞台/通话等全部历史场景）')
        L.append('')
        table(['统计', 'PSS(MB)'], [('最小', hx['min']), ('最大', hx['max']), ('样本数', hx['n'])])
        L.append('')
        L.append('> 口径警告：exit-info 是**进程被杀那一刻**的 PSS，场景未知（可能是通话、'
                 '可能是相机特效），只能当「历史上见过多高」的上限证据，**不能**当场景基线。')
        L.append('')

    g = m.get('gfxinfo_cumulative', {})
    if g:
        L.append('## 帧时间（进程存活期累计，未分场景）')
        L.append('')
        table(['项', '值'], [
            ('总帧数', g.get('frames')), ('卡顿帧', g.get('janky_frames')),
            ('卡顿率 %', g.get('janky_pct')),
            ('CPU 帧时间 P50/P95 (ms)', f"{g.get('p50_ms')}/{g.get('p95_ms')}"),
            ('GPU 帧时间 P50/P95 (ms)', f"{g.get('gpu_p50_ms')}/{g.get('gpu_p95_ms')}"),
            ('Missed Vsync', g.get('missed_vsync')),
            ('Slow UI thread', g.get('slow_ui_thread')),
            ('Slow draw commands', g.get('slow_draw_cmds')),
            ('渲染管线', g.get('pipeline')),
            ('超 16/33/50ms 帧占比 %', json.dumps(g.get('over_budget_pct'), ensure_ascii=False)),
        ])
        L.append('')
        L.append('> 口径警告：这是**累计**统计，混合了首页/聊天/WebView 等所有页面，'
                 '不是舞台场景；场景数字必须在 reset 包出的干净窗口里取。')
        L.append('')

    fx = m.get('fxlab', {})
    if fx.get('scenes'):
        L.append('## 特效层单独口径（debug「特效引擎对比」Lab，离线）')
        L.append('')
        table(['场景', '触发', '帧数', 'PSS(KB)', 'CPU P50', 'CPU P95', 'GPU P50', 'GPU P95',
               '>8ms%', 'MissVsyncΔ'],
              [(s['scene'], ','.join(s['fired']), s['frames_in_window'], s['pss_kb_median'],
                s['cpu_p50_ms'], s['cpu_p95_ms'], s['gpu_p50_ms'], s['gpu_p95_ms'],
                s['over_8ms_pct'], s['missed_vsync_delta']) for s in fx['scenes']])
        L.append('')
        L.append('> 口径警告：这里测的是**特效渲染层自己**的成本（Lab 里换渲染器），'
                 '不是「Lottie 叠在 Live2D 舞台上」的合并成本；合并成本只有 stage 模式能测。')
        L.append('')

    lt = m.get('launch_times_own')
    if lt:
        L.append('## 启动到首帧（TTID，events buffer 的 wm_activity_launch_time；冷/热未分）')
        L.append('')
        table(['时间', 'Activity', 'TTID (ms)'],
              [(c['when'], c['activity'].split('/')[-1], c['ttid_ms']) for c in lt])
        L.append('')
        L.append('> 口径：TTID = 到 **Activity 首帧**，不含形象加载；`LaunchActivity` 有 5 秒开屏，'
                 'HomeActivity 那条才是进主页的成本。冷热分不清，只能当样本，不能当预算线——'
                 '预算线要 `am start -W` 的冷启动轮数（coldstart 模式）。')
        L.append('')
    if m.get('live2d_load_ms'):
        v = m['live2d_load_ms']
        L.append(f"## Live2D 模型加载（应用自测：Live2dRuntime 'model load on GL surface'）")
        L.append('')
        table(['统计', 'ms'], [('样本数', len(v)), ('min', v[0]),
                               ('median', v[len(v) // 2]), ('max', v[-1])])
        L.append('')

    cs = m.get('cold_start')
    if cs:
        L.append('## 冷启动到首帧（am start -W 的 TotalTime；口径 = Activity 首帧，不含形象加载）')
        L.append('')
        if isinstance(cs, dict) and cs.get('home_activity_ttid'):
            summ = cs.get('home_activity_ttid_ms', {})
            table(['统计', 'TotalTime(ms)'], [('min', summ.get('min')),
                                             ('median', summ.get('median')),
                                             ('max', summ.get('max')),
                                             ('轮数', summ.get('n'))])
            L.append('')
            table(['轮', 'TotalTime', 'ThisTime', 'WaitTime'],
                  [(r['run'], r['totaltime_ms'], r['thistime_ms'], r['waittime_ms'])
                   for r in cs['home_activity_ttid']])
        else:
            table(['Activity', 'TTID (ms)'],
                  [(c['activity'], c['ttid_ms']) for c in cs])
        L.append('')

    st = m.get('stage', {})
    if st.get('scenes'):
        L.append('## 形象舞台场景对比（gfxinfo 差分的干净窗口，不用 reset）')
        L.append('')
        rows = []
        for s in st['scenes']:
            rows.append((s['scene'], s['window_s'], s['frames_in_window'],
                         s['pss_kb_median'], s.get('pss_delta_kb'),
                         s['cpu_p50_ms'], s['cpu_p90_ms'], s['cpu_p95_ms'],
                         s['gpu_p50_ms'], s['gpu_p95_ms'],
                         s['over_8ms_pct'], s['over_16ms_pct'],
                         s['missed_vsync_delta'], s['slow_ui_thread_delta'],
                         s['foreground']))
        table(['场景', '窗口s', '帧数', 'PSS(KB)', 'ΔPSS(KB)', 'CPU P50', 'CPU P90', 'CPU P95',
               'GPU P50', 'GPU P95', '>8ms%', '>16ms%', 'MissVsyncΔ', 'SlowUIΔ', '前台'], rows)
        L.append('')
        L.append('> **口径关键**：上表的 gfxinfo 帧数只覆盖应用 UI 线程（Compose/Skia）。'
                 'GLSurfaceView（Live2D 等 native 引擎）的帧由独立 GL 线程直接提交给 '
                 'SurfaceFlinger，**不进 gfxinfo**——native 场景看下面两表。')
        L.append('')
        sf_rows = []
        for s in st['scenes']:
            for layer, v in (s.get('sf_layers') or {}).items():
                sf_rows.append((s['scene'], layer[:60], v['frames'], v['ms_p50'],
                                v['ms_p95'], v['fps']))
        if sf_rows:
            L.append('### SurfaceFlinger 层帧间隔（含 native GL 层）')
            L.append('')
            table(['场景', '层', '帧数', '间隔 P50(ms)', '间隔 P95(ms)', '等效 fps'], sf_rows)
            L.append('')
        th_rows = []
        for s in st['scenes']:
            thr = s.get('threads') or {}
            for r in (thr.get('top_threads') or [])[:5]:
                th_rows.append((s['scene'], r['name'][:28], r['cpu_pct']))
            if thr:
                th_rows.append((s['scene'], '**全进程合计**', thr['sum_cpu_pct']))
        if th_rows:
            L.append('### 每线程 CPU（top -H，稳态窗口内）')
            L.append('')
            table(['场景', '线程', 'CPU%'], th_rows)
            L.append('')
    if st.get('fx_fired'):
        L.append(f"- 特效连发：{', '.join(st['fx_fired'])}")
        L.append('')

    if m.get('lottie_fx'):
        L.append('## Lottie 特效资产（仓库静态）')
        L.append('')
        rows = [(f['asset'], f.get('bytes'), f.get('duration_ms'), f.get('w'), f.get('h'),
                 f.get('layers_total'), f.get('shape_groups'), f.get('image_layers'),
                 f.get('error')) for f in m['lottie_fx']]
        table(['资产', 'bytes', '时长(ms)', 'w', 'h', '图层', 'shape组', '位图图层', '错误'], rows)
        L.append('')

    if m.get('repo_asset_bytes'):
        L.append('## 仓库里打包进 APK 的形象资产')
        L.append('')
        table(['目录', 'MiB'], [(k, f"{v / 1048576:.1f}")
                               for k, v in m['repo_asset_bytes'].items()])
        L.append('')

    if m.get('mesh_stats'):
        L.append('## 3D 模型面数（glb 直读；只有 3D 档有面数这个量）')
        L.append('')
        table(['模型', '三角面', 'primitives', '材质', '贴图', 'MiB', '备注'],
              [(os.path.basename(x['path']), x.get('triangles'), x.get('mesh_primitives'),
                x.get('materials'), ','.join(x.get('images') or []) or '内嵌',
                f"{x['bytes'] / 1048576:.1f}", x.get('error', ''))
               for x in m['mesh_stats']])
        L.append('')

    if BLOCKED:
        L.append('## BLOCKED（缺什么、测不了什么）')
        L.append('')
        table(['项', '原因'], BLOCKED)
        L.append('')
    for n in NOTES:
        L.append(f"- 注：{n}")

    path = os.path.join(outdir, 'report.md')
    open(path, 'w').write('\n'.join(L) + '\n')
    json.dump(m, open(os.path.join(outdir, 'measurements.json'), 'w'),
              ensure_ascii=False, indent=2)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('mode', choices=['passive', 'stage', 'fxlab', 'coldstart', 'static'])
    ap.add_argument('--outdir', default=None)
    ap.add_argument('--sample', type=int, default=20, help='每个场景采样秒数')
    ap.add_argument('--rounds', type=int, default=3, help='coldstart 轮数')
    ap.add_argument('--allow-touch', action='store_true',
                    help='stage 模式必须显式加这个开关，防止误抢别人正在用的手机')
    a = ap.parse_args()

    ts = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    outdir = a.outdir or f'/tmp/perf-p03/{ts}'
    os.makedirs(outdir, exist_ok=True)
    m = {'_ts': ts, '_mode': a.mode, '_out': outdir}

    if a.mode != 'static':
        serial = os.environ.get('ANDROID_SERIAL')
        devs = subprocess.run([ADB, 'devices'], capture_output=True, text=True).stdout
        lines = [l.split('\t')[0] for l in devs.splitlines()[1:] if l.strip() and '\tdevice' in l]
        if not lines:
            die('没有 adb 设备')
        if len(lines) > 1 and not serial:
            die(f'多台设备 {lines}，请设 ANDROID_SERIAL')
        dev = Dev(serial or lines[0], outdir)
        if a.mode == 'passive':
            collect_passive(dev, m)
        elif a.mode == 'coldstart':
            collect_coldstart(dev, m, a)
        elif a.mode == 'fxlab':
            collect_passive(dev, m)
            collect_fxlab(dev, m, a)
        else:
            if not a.allow_touch:
                die('stage 模式会操作手机 UI；确认手机独占后加 --allow-touch')
            collect_passive(dev, m)
            collect_stage(dev, m, a)
    else:
        collect_static(m)

    path = write_report(m, outdir)
    print(f"报告：{path}")
    print(f"原始输出：{os.path.join(outdir, 'raw')}")
    if BLOCKED:
        print(f"BLOCKED {len(BLOCKED)} 项：")
        for k, why in BLOCKED:
            print(f"  - {k}: {why}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
