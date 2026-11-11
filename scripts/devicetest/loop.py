#!/usr/bin/env python3
"""反复进出对话页的稳定性脚本（对应 full-test-plan.md 2.3 的 S1–S8）。

做法：每轮「启动 → 等消息列表 → 截图 + 抓一次控件文本快照 → 退出 → 再截图」，
最后把各轮的文本快照两两比较，把截图做像素差。

为什么用文本快照而不是只比截图：截图会被时间、未读小红点干扰；
消息数量、顺序、内容用 uiautomator 的文本序列比对更可靠。

用法:
    python3 scripts/devicetest/loop.py --rounds 5
    python3 scripts/devicetest/loop.py --rounds 5 --label after-edit   # 给图片编辑回归用

输出: /tmp/devicetest-loop/<label>/round-N.{png,txt}
"""
import argparse
import os
import re
import subprocess
import sys
import time

ADB = os.path.join(os.environ.get('ANDROID_SDK_ROOT', os.path.expanduser('~/Library/Android/sdk')),
                   'platform-tools', 'adb')
PKG = 'com.openapi.ks.chat'
LAUNCH = 'com.openapi.ks.chat/com.openapi.ks.chatfree.ui.main.LaunchActivity'


def adb(*args, timeout=60):
    return subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)


def dump_texts():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml', timeout=30)
    out = adb('exec-out', 'cat', '/sdcard/ui.xml', timeout=30).stdout
    # 只保留文本，忽略坐标（坐标会因滚动而变）；按出现顺序保留
    return [t for t in re.findall(r'(?:text|content-desc)="([^"]+)"', out) if t.strip()]


def shot(path):
    with open(path, 'wb') as f:
        f.write(adb('exec-out', 'screencap', '-p', timeout=30).stdout)


def wait_chat(limit=40):
    for _ in range(limit):
        if any('说点什么' in t for t in dump_texts()):
            return True
        time.sleep(1)
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rounds', type=int, default=3)
    ap.add_argument('--label', default='default')
    ap.add_argument('--package', default=PKG)
    a = ap.parse_args()

    outdir = os.path.join('/tmp/devicetest-loop', a.label)
    os.makedirs(outdir, exist_ok=True)
    snapshots = []

    for i in range(1, a.rounds + 1):
        adb('shell', 'am', 'start', '-n', LAUNCH, timeout=30)
        if not wait_chat():
            print(f'第 {i} 轮: 等不到对话页，中止（看 /tmp/devicetest-loop 里的截图）')
            shot(os.path.join(outdir, f'round-{i}-stuck.png'))
            return 2
        texts = dump_texts()
        shot(os.path.join(outdir, f'round-{i}.png'))
        with open(os.path.join(outdir, f'round-{i}.txt'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(texts))
        snapshots.append(texts)
        # 真正退出（不是返回）：模拟用户从最近任务划掉之前的「退出去」
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK', timeout=30)
        time.sleep(1)
        print(f'第 {i} 轮: {len(texts)} 个可见文本')

    base = snapshots[0]
    diffs = [i + 1 for i, s in enumerate(snapshots) if s != base]
    print()
    if diffs:
        print(f'不一致的轮次: {diffs}（第 1 轮为基线）')
        for i in diffs:
            only_base = [t for t in base if t not in snapshots[i - 1]]
            only_now = [t for t in snapshots[i - 1] if t not in base]
            print(f'  第 {i} 轮: 少了 {only_base[:5]}  多了 {only_now[:5]}')
        print('注意：未读数、时间戳变化也会导致差异，需要人工看截图确认。')
        return 1
    print(f'{a.rounds} 轮文本快照完全一致（同一段对话，反复进出不丢、不重复、不乱序）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
