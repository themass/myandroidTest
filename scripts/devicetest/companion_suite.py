#!/usr/bin/env python3
"""陪伴 / Agent 客户端真机测试集（只用标准库 + adb）。

设计原则
- 只操作本 App 包（默认 com.openapi.ks.chat）。**不 force-stop、不点别的包**：
  手机可能还被别的会话在用（com.openapi.ks.free1）。需要杀进程的用例必须显式 --allow-kill。
- 按文字定位控件（uiautomator dump），不猜坐标；等待用轮询，不用固定 sleep。
- 两种口径，避免把「缺功能」当成「回归」：
    degraded  服务端没部署（nginx 404）：验收「缺服务时的表现」——每页都要有明确提示，不能空白，不崩。
    live      服务端就绪：验收功能本身（偏好、事件、发消息、反复进出）。
  默认自动判断（探测 /companion/characters 是否 404），也可 --expect 强制。
- 没跑过的就是没跑过：脚本不会把 SKIP 算作通过。

用法
    python3 scripts/devicetest/companion_suite.py --list              # 只列用例，不碰手机
    python3 scripts/devicetest/companion_suite.py                     # 跑全部（自动判断口径）
    python3 scripts/devicetest/companion_suite.py --only C1,C2        # 只跑部分
    python3 scripts/devicetest/companion_suite.py --expect live --rounds 8
    ANDROID_SERIAL=xxxx python3 scripts/devicetest/companion_suite.py # 多台设备时指定

输出
    /tmp/companion-suite/<时间戳>/report.md   结果表
    /tmp/companion-suite/<时间戳>/*.png|*.txt 每个用例的截图与文本快照
退出码：0 全部通过（SKIP 不算失败但会提示）；1 有失败；2 环境不满足（没设备、没装包）。
"""
import argparse
import datetime
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error

ADB = os.path.join(os.environ.get('ANDROID_SDK_ROOT', os.path.expanduser('~/Library/Android/sdk')),
                   'platform-tools', 'adb')
DEFAULT_PKG = 'com.openapi.ks.chat'
LAUNCH_ACTIVITY = 'com.openapi.ks.chatfree.ui.main.LaunchActivity'
PROBE_URL = 'https://api.ok123find.top/companion/characters'

# 文案来自 app/src/main/res/values-zh/strings_companion.xml 与 feature-companion 的 ks_copied.xml。
# 文案一改这里就要跟着改——这是脚本的已知耦合点，所以集中放在一处。
S = dict(
    tab_chat='对话', tab_companion='陪伴', tab_me='我的',
    input_hint='说点什么', send='发送', retry='重试', empty='还没有内容', error='出了点问题',
    open_characters='角色', open_memory='她记得的', open_settings='她的主动性',
    characters_title='选择陪伴的角色', memory_title='她记得的', settings_title='她的主动性',
    tab_facts='记住的事', tab_events='重要日子',
    pause_1h='暂停 1 小时', paused_until='暂停至', resume='恢复',
    pref_events='重要日子提醒我', pref_greetings='偶尔主动跟我打个招呼', pref_call='允许她主动打电话给我',
    quiet='安静时段', add='添加', delete='忘掉', back='返回',
    mode_voice='语音', mode_text='文字',
    drawer_test='测试', agent_chat='角色对话（新）',
)
# 「页面有明确状态」的判据：内容、空态、错误态、重试，任一出现都算有交代；全都没有就是空白。
STATE_MARKERS = (S['empty'], S['error'], S['retry'])


class Skip(Exception):
    pass


class Fail(AssertionError):
    pass


# ---------------------------------------------------------------- adb 封装
class Dev:
    def __init__(self, pkg, outdir, serial=None):
        self.pkg, self.outdir = pkg, outdir
        self.base = [ADB] + (['-s', serial] if serial else [])
        self._xml = ''

    def adb(self, *args, timeout=40):
        return subprocess.run(self.base + list(args), capture_output=True, text=True, timeout=timeout)

    def dump(self):
        self.adb('shell', 'uiautomator', 'dump', '/sdcard/cs_ui.xml', timeout=30)
        self._xml = self.adb('exec-out', 'cat', '/sdcard/cs_ui.xml', timeout=30).stdout
        return self._xml

    def nodes(self):
        """[(文字, x中心, y中心, 可点击)]，按 dump 顺序。"""
        out = []
        for m in re.finditer(r'<node [^>]*?>', self.dump()):
            tag = m.group(0)
            t = re.search(r'\btext="([^"]*)"', tag)
            d = re.search(r'content-desc="([^"]*)"', tag)
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
            if not b:
                continue
            x1, y1, x2, y2 = map(int, b.groups())
            clickable = 'clickable="true"' in tag
            for label in (t.group(1) if t else '', d.group(1) if d else ''):
                if label.strip():
                    out.append((label, (x1 + x2) // 2, (y1 + y2) // 2, clickable))
        return out

    def texts(self):
        return [n[0] for n in self.nodes()]

    def has(self, needle):
        return any(needle in t for t in self.texts())

    def has_any(self, needles):
        ts = self.texts()
        return any(n in t for t in ts for n in needles)

    def wait(self, needle, limit=20):
        """轮询到出现；返回是否出现。needle 可以是 str 或 tuple（任一）。"""
        needles = (needle,) if isinstance(needle, str) else tuple(needle)
        end = time.time() + limit
        while time.time() < end:
            if self.has_any(needles):
                return True
            time.sleep(1)
        return False

    def tap(self, needle, prefer='first', exact=False):
        """按文字点击。prefer='bottom' 取屏幕最下面的那个（底部标签栏与页面标题同名时用）。"""
        hits = [n for n in self.nodes() if (n[0] == needle if exact else needle in n[0])]
        if not hits:
            raise Fail(f'找不到可点击的文字: {needle!r}')
        label, x, y, _ = max(hits, key=lambda n: n[2]) if prefer == 'bottom' else hits[0]
        self.adb('shell', 'input', 'tap', str(x), str(y))
        return label

    def type_ascii(self, s):
        # input text 只可靠支持 ASCII；空格要写成 %s
        self.adb('shell', 'input', 'text', s.replace(' ', '%s'))

    def key(self, name):
        self.adb('shell', 'input', 'keyevent', name)

    def shot(self, name):
        path = os.path.join(self.outdir, f'{name}.png')
        with open(path, 'wb') as f:
            f.write(self.adb('exec-out', 'screencap', '-p', timeout=30).stdout)
        return path

    def snap(self, name):
        ts = self.texts()
        with open(os.path.join(self.outdir, f'{name}.txt'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(ts))
        self.shot(name)
        return ts

    def launch(self):
        self.adb('shell', 'am', 'start', '-n', f'{self.pkg}/{LAUNCH_ACTIVITY}')

    def fatal_count(self):
        out = self.adb('logcat', '-d', '-b', 'crash', timeout=30).stdout
        return len(re.findall(r'FATAL EXCEPTION', out)), out

    def anr_count(self):
        out = self.adb('logcat', '-d', '-b', 'events', timeout=30).stdout
        return len([l for l in out.splitlines() if 'am_anr' in l and self.pkg in l])


# ---------------------------------------------------------------- 公共步骤
def to_companion(dev):
    """从任意状态进到陪伴标签，返回当时的文本快照。"""
    dev.launch()
    if not dev.wait((S['input_hint'], S['tab_companion']), 40):
        raise Fail('启动后等不到主界面（可能卡在登录/权限/升级弹窗，看截图）')
    # 底部标签栏的「陪伴」与页面标题同名，取屏幕最下面那个
    dev.tap(S['tab_companion'], prefer='bottom')
    time.sleep(1)
    return dev.texts()


def assert_has_state(dev, where, limit=15):
    """页面必须有交代：有内容（非 marker 的文本不少于若干条）或明确的空态/错误态/重试。"""
    if dev.wait(STATE_MARKERS + (S['input_hint'],), limit):
        return
    ts = dev.texts()
    # 兜底：页面上有较多文字也算有内容（例如角色列表）
    if len(ts) >= 6:
        return
    raise Fail(f'{where}: 既没有内容也没有空态/错误态/重试，疑似空白页或无限转圈。可见文本: {ts[:8]}')


def go_back(dev):
    dev.key('KEYCODE_BACK')
    time.sleep(1)


# ---------------------------------------------------------------- 用例
CASES = []


def case(cid, title, needs='any'):
    """needs: any | live | degraded —— 该用例只在对应口径下有意义。"""
    def deco(fn):
        CASES.append((cid, title, needs, fn))
        return fn
    return deco


@case('C1', '打开陪伴标签：不崩，页面有交代')
def c1(dev, ctx):
    ts = to_companion(dev)
    dev.snap('C1-companion')
    assert_has_state(dev, '陪伴主页')
    if ctx['expect'] == 'degraded' and dev.has(S['input_hint']) and not dev.has_any(STATE_MARKERS):
        # 服务端缺失时还像正常聊天一样给输入框，用户发出去会石沉大海——记为问题但不阻断
        ctx['notes'].append('C1: 服务端缺失时仍显示输入框，且没有任何不可用提示')


@case('C2', '陪伴各子页：角色 / 她记得的 / 她的主动性，各自有交代')
def c2(dev, ctx):
    to_companion(dev)
    for key, title in (('open_characters', 'characters_title'),
                       ('open_memory', 'memory_title'),
                       ('open_settings', 'settings_title')):
        if not dev.has(S[key]):
            raise Fail(f'主页上找不到入口 {S[key]!r}')
        dev.tap(S[key])
        if not dev.wait(S[title], 15):
            dev.snap(f'C2-{key}-stuck')
            raise Fail(f'点了 {S[key]!r} 后没进到标题 {S[title]!r}')
        assert_has_state(dev, S[title])
        dev.snap(f'C2-{key}')
        go_back(dev)
        if not dev.wait((S['input_hint'], S[key]), 10):
            raise Fail(f'从 {S[title]!r} 返回后回不到陪伴主页')


@case('C3', '偏好：暂停 1 小时 → 显示「暂停至」→ 恢复', needs='live')
def c3(dev, ctx):
    to_companion(dev)
    dev.tap(S['open_settings'])
    if not dev.wait(S['pause_1h'], 15):
        raise Fail('偏好页没有「暂停 1 小时」')
    dev.tap(S['pause_1h'])
    if not dev.wait(S['paused_until'], 15):
        dev.snap('C3-pause-fail')
        raise Fail('点了暂停后没有出现「暂停至 …」')
    dev.snap('C3-paused')
    dev.tap(S['resume'])
    time.sleep(2)
    if dev.has(S['paused_until']):
        raise Fail('点了恢复后仍显示「暂停至」')
    dev.snap('C3-resumed')


@case('C4', '偏好开关：每个开关切换后重进页面，状态保持', needs='live')
def c4(dev, ctx):
    to_companion(dev)
    dev.tap(S['open_settings'])
    dev.wait(S['pref_events'], 15)
    before = dev.snap('C4-before')
    dev.tap(S['pref_greetings'])
    time.sleep(2)
    changed = dev.snap('C4-toggled')
    go_back(dev)
    dev.tap(S['open_settings'])
    dev.wait(S['pref_events'], 15)
    after = dev.snap('C4-reentered')
    # 文本快照里开关状态不一定可见；至少保证重进后页面完整
    if S['pref_events'] not in ' '.join(after):
        raise Fail('重进偏好页后内容不完整')
    ctx['notes'].append('C4: 开关状态靠截图 C4-toggled / C4-reentered 人工对比（uiautomator 文本里没有开关值）')
    # 还原：再点一次
    dev.tap(S['pref_greetings'])


@case('C5', '重要日子：添加 → 出现在列表 → 忘掉', needs='live')
def c5(dev, ctx):
    stamp = datetime.datetime.now().strftime('%H%M%S')
    title = f'cs-{stamp}'
    to_companion(dev)
    dev.tap(S['open_memory'])
    dev.wait(S['tab_events'], 15)
    dev.tap(S['tab_events'])
    time.sleep(1)
    ctx['notes'].append(f'C5: 输入框焦点依赖界面，若自动输入失败请手工添加标题 {title}')
    dev.tap(S['add'])  # 打开添加表单或直接添加；以快照为准
    dev.snap('C5-add')
    dev.type_ascii(title)
    time.sleep(1)
    dev.snap('C5-typed')
    raise Skip('添加表单的输入框定位需要先看一次真机界面（C5-add.png）再固化，避免凭猜测点错')


@case('C6', '发一条文字消息：有回复，不出现错误态', needs='live')
def c6(dev, ctx):
    to_companion(dev)
    if not dev.wait(S['input_hint'], 15):
        raise Fail('主页没有输入框')
    dev.tap(S['input_hint'])
    dev.type_ascii('hello')
    before = set(dev.texts())
    dev.tap(S['send'], prefer='bottom')
    # 回复是流式的，最多等 60 秒；判据：出现了不在发送前快照里的新文本，且不是错误提示
    end = time.time() + 60
    grew = False
    while time.time() < end:
        now = dev.texts()
        new = [t for t in now if t not in before and 'hello' not in t.lower()]
        if new:
            grew = True
            break
        time.sleep(2)
    dev.snap('C6-after-send')
    if dev.has(S['error']):
        raise Fail('发送后出现「出了点问题」')
    if not grew:
        raise Fail('60 秒内没有出现任何新文本（既没回复也没错误提示）')


@case('C7', '反复进出：陪伴 ↔ 对话 来回切换，文本序列保持一致')
def c7(dev, ctx):
    rounds = ctx['rounds']
    to_companion(dev)
    dev.wait(STATE_MARKERS + (S['input_hint'],), 15)
    base = dev.snap('C7-r0')
    diffs = []
    for i in range(1, rounds + 1):
        dev.tap(S['tab_chat'], prefer='bottom')
        time.sleep(1)
        dev.tap(S['tab_companion'], prefer='bottom')
        dev.wait(STATE_MARKERS + (S['input_hint'],), 15)
        cur = dev.snap(f'C7-r{i}')
        if cur != base:
            only_base = [t for t in base if t not in cur][:4]
            only_now = [t for t in cur if t not in base][:4]
            diffs.append((i, only_base, only_now))
    if diffs:
        raise Fail('文本序列不一致（时间戳/未读数变化也会触发，需看截图确认）: ' +
                   '; '.join(f'第{i}轮 少{a} 多{b}' for i, a, b in diffs))


@case('C8', '切后台再回来：仍在陪伴页，内容不丢')
def c8(dev, ctx):
    to_companion(dev)
    dev.wait(STATE_MARKERS + (S['input_hint'],), 15)
    before = dev.snap('C8-before')
    dev.key('KEYCODE_HOME')
    time.sleep(4)
    dev.launch()
    if not dev.wait(STATE_MARKERS + (S['input_hint'],), 20):
        raise Fail('回到前台后陪伴页没有出现')
    after = dev.snap('C8-after')
    if len(after) + 3 < len(before):
        raise Fail(f'回到前台后内容明显变少: {len(before)} → {len(after)} 条文本')


@case('C9', '杀进程后重进：不白屏、不崩（需 --allow-kill）')
def c9(dev, ctx):
    if not ctx['allow_kill']:
        raise Skip('未加 --allow-kill。只会 force-stop 本包，但手机被别的会话占用时请不要加')
    dev.adb('shell', 'am', 'force-stop', dev.pkg)
    time.sleep(2)
    to_companion(dev)
    assert_has_state(dev, '杀进程后的陪伴页', limit=25)
    dev.snap('C9-after-kill')


@case('C10', '发布前检查：抽屉里的「测试」分组与 AgentChat 入口（记录现状）')
def c10(dev, ctx):
    dev.launch()
    dev.wait(S['input_hint'], 40)
    # 抽屉入口位置因版本而异：只在文本里已可见时记录，不盲点
    present = dev.has(S['drawer_test']) or dev.has(S['agent_chat'])
    ctx['notes'].append('C10: 当前包里' + ('能看到' if present else '没有看到') +
                        '「测试」分组/「角色对话（新）」入口（只检查首页可见文本；抽屉内需手工打开确认）')
    raise Skip('抽屉需要手势打开，见手工清单 M12；发布包必须没有该入口')


@case('C11', '语音模式切换：弹出麦克风解释框，且不崩', needs='live')
def c11(dev, ctx):
    to_companion(dev)
    if not dev.has(S['mode_voice']):
        raise Skip('主页没有「语音」切换，可能该版本隐藏了语音入口')
    dev.tap(S['mode_voice'])
    time.sleep(3)
    dev.snap('C11-voice')
    # 不判断权限框内容（系统框文案因 ROM 而异），只要求没崩、界面还在
    if dev.fatal_count()[0]:
        raise Fail('切到语音后出现崩溃')
    ctx['notes'].append('C11: 授权后的通话/字幕/回声需要手工，见 M5–M8')


# ---------------------------------------------------------------- 口径探测与主流程
def probe_server():
    """不带签名直接请求：nginx 没配置时是 404；服务在时会是 401/403/200。只用于选口径，不当作健康检查。"""
    try:
        req = urllib.request.Request(PROBE_URL, headers={'User-Agent': 'companion-suite-probe'})
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:  # noqa: BLE001
        return f'unreachable: {e.__class__.__name__}'


def main():
    ap = argparse.ArgumentParser(description='陪伴/Agent 真机测试集')
    ap.add_argument('--list', action='store_true', help='只列出用例，不碰手机')
    ap.add_argument('--only', default='', help='逗号分隔的用例 id，如 C1,C2')
    ap.add_argument('--expect', choices=['auto', 'live', 'degraded'], default='auto')
    ap.add_argument('--package', default=DEFAULT_PKG)
    ap.add_argument('--rounds', type=int, default=5, help='C7 反复进出轮数')
    ap.add_argument('--allow-kill', action='store_true', help='允许 force-stop 本包（C9）')
    a = ap.parse_args()

    if a.list:
        for cid, title, needs, _ in CASES:
            print(f'{cid:4} [{needs:8}] {title}')
        return 0

    if not os.path.exists(ADB):
        print(f'找不到 adb: {ADB}')
        return 2
    serial = os.environ.get('ANDROID_SERIAL')
    devs = subprocess.run([ADB, 'devices'], capture_output=True, text=True).stdout
    attached = [l.split()[0] for l in devs.splitlines()[1:] if l.strip().endswith('device')]
    if not attached:
        print('没有连接的设备')
        return 2
    if len(attached) > 1 and not serial:
        print(f'有多台设备 {attached}，请设置 ANDROID_SERIAL')
        return 2

    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    outdir = f'/tmp/companion-suite/{stamp}'
    os.makedirs(outdir, exist_ok=True)
    dev = Dev(a.package, outdir, serial)

    pkgs = dev.adb('shell', 'pm', 'list', 'packages', a.package).stdout
    if f'package:{a.package}' not in pkgs.split():
        print(f'设备上没有安装 {a.package}')
        return 2
    version = re.search(r'versionName=(\S+)', dev.adb('shell', 'dumpsys', 'package', a.package).stdout)

    code = probe_server()
    expect = a.expect
    if expect == 'auto':
        expect = 'degraded' if code == 404 else 'live'
    print(f'设备: {serial or attached[0]}  包: {a.package} {version.group(1) if version else "?"}')
    print(f'服务端探测: {PROBE_URL} → {code}   口径: {expect}')
    print(f'输出目录: {outdir}\n')

    dev.adb('logcat', '-c')
    selected = {s.strip() for s in a.only.split(',') if s.strip()}
    ctx = dict(expect=expect, rounds=a.rounds, allow_kill=a.allow_kill, notes=[])
    results = []
    for cid, title, needs, fn in CASES:
        if selected and cid not in selected:
            continue
        if needs != 'any' and needs != expect:
            results.append((cid, title, 'SKIP', f'只在 {needs} 口径下有意义，当前是 {expect}'))
            print(f'{cid:4} SKIP  {title}')
            continue
        t0 = time.time()
        try:
            fn(dev, ctx)
            status, detail = 'PASS', ''
        except Skip as e:
            status, detail = 'SKIP', str(e)
        except Fail as e:
            status, detail = 'FAIL', str(e)
            dev.shot(f'{cid}-FAIL')
        except Exception as e:  # noqa: BLE001  脚本自身的错也要记成失败，不能吞掉
            status, detail = 'FAIL', f'脚本异常 {e.__class__.__name__}: {e}'
            try:
                dev.shot(f'{cid}-ERROR')
            except Exception:  # noqa: BLE001
                pass
        results.append((cid, title, status, detail))
        print(f'{cid:4} {status:5} {title}  ({time.time() - t0:.0f}s)' + (f'\n       {detail}' if detail else ''))

    fatals, crash_log = dev.fatal_count()
    anrs = dev.anr_count()
    mine = [l for l in crash_log.splitlines() if 'Process:' in l and a.package in l]
    print(f'\n崩溃(FATAL EXCEPTION): 全设备 {fatals} 次，其中本包 {len(mine)} 次；本包 ANR: {anrs}')

    lines = [f'# 陪伴 / Agent 真机测试报告 {stamp}', '',
             f'- 包：`{a.package}` {version.group(1) if version else ""}',
             f'- 服务端探测：`{PROBE_URL}` → `{code}`，口径：**{expect}**',
             f'- 本包崩溃：{len(mine)}，ANR：{anrs}', '',
             '| 用例 | 结果 | 说明 |', '|---|---|---|']
    for cid, title, status, detail in results:
        lines.append(f'| {cid} {title} | {status} | {detail.replace("|", "/")} |')
    if ctx['notes']:
        lines += ['', '## 备注（需要人工确认）'] + [f'- {n}' for n in ctx['notes']]
    with open(os.path.join(outdir, 'report.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    print(f'报告: {outdir}/report.md')

    failed = [r for r in results if r[2] == 'FAIL'] or mine or anrs
    skipped = [r for r in results if r[2] == 'SKIP']
    if skipped:
        print(f'注意：{len(skipped)} 个用例被跳过，跳过不等于通过。')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
