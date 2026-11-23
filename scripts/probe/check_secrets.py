#!/usr/bin/env python3
"""部署前的 secrets.properties 自检。

没有依赖（只用标准库），因为要能在部署机上直接跑。

用法:
    python3 scripts/probe/check_secrets.py [path/to/secrets.properties]

退出码: 0 全部通过（可能有警告）；1 有错误（不要部署）。

这里每一条规则都对应一次真实事故：
- maxBytes 单位是字节，写成 5 会让所有图片被拒（40003「不能超过 0MB」）
- model 是模板占位符时，服务端仍报告 configured:true，部署者以为配好了
- Properties 保留值前后的空格，值里带空格会导致鉴权失败
"""
import os
import re
import sys

PLACEHOLDER = re.compile(r'xxxx|your|todo|changeme|placeholder', re.I)

# (键, 检查函数) -> 错误/警告
def is_uuid(v):
    return re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', v) is not None


def check(path):
    errors, warnings = [], []
    if not os.path.isfile(path):
        return ['找不到文件: ' + path], []

    with open(path, encoding='utf-8') as f:
        raw = f.readlines()

    props = {}
    for n, line in enumerate(raw, 1):
        s = line.rstrip('\n')
        if not s.strip() or s.lstrip().startswith('#'):
            continue
        if '=' not in s:
            warnings.append(f'第 {n} 行不是 key=value，会被 Properties 忽略: {s[:60]}')
            continue
        k, _, v = s.partition('=')
        k = k.strip()
        if v != v.strip() and v.strip():
            warnings.append(f'{k}: 值前后有空格（Properties 会保留），建议去掉')
        props[k] = v.strip()

    def get(k):
        return props.get(k, '')

    # ---- 拍照解题 ----
    key = get('photosolve.doubao.apiKey')
    if not key:
        errors.append('photosolve.doubao.apiKey 为空')
    elif len(key) < 20:
        errors.append(f'photosolve.doubao.apiKey 只有 {len(key)} 个字符，疑似没填或填错')
    elif not is_uuid(key):
        warnings.append('photosolve.doubao.apiKey 不是常见的 UUID 形态，确认是火山方舟 API key')

    model = get('photosolve.doubao.model')
    if not model:
        errors.append('photosolve.doubao.model 为空')
    elif PLACEHOLDER.search(model):
        errors.append(f'photosolve.doubao.model 是占位符: {model}')
    elif model.startswith('ep-'):
        warnings.append(f'photosolve.doubao.model 是接入点 id ({model})：'
                        '若该接入点未绑定视觉模型，chat/completions 会 404 或挂起')

    ep = get('photosolve.doubao.endpoint')
    if ep and not ep.startswith('https://'):
        errors.append(f'photosolve.doubao.endpoint 必须是 https://，当前: {ep}')

    mb = get('photosolve.maxBytes')
    if mb:
        if not mb.isdigit():
            errors.append(f'photosolve.maxBytes 必须是整数（字节），当前: {mb}')
        elif int(mb) < 1024 * 1024:
            errors.append(f'photosolve.maxBytes={mb} 太小（单位是字节，5 表示 5 字节）。建议 5242880')

    ts = get('photosolve.timeoutSeconds')
    if ts:
        if not ts.isdigit():
            errors.append(f'photosolve.timeoutSeconds 必须是整数，当前: {ts}')
        elif not (10 <= int(ts) <= 120):
            warnings.append(f'photosolve.timeoutSeconds={ts} 超出建议范围 10–120（实测最慢约 13s）')

    # ---- 其它服务 ----
    for k in ('livekit.apiKey', 'livekit.apiSecretA', 'bytedance_asr.token', 'bytedance_tts.token'):
        v = get(k)
        if not v:
            warnings.append(f'{k} 为空：对应功能（陪伴语音 / 语音识别 / 朗读）不可用')
        elif PLACEHOLDER.search(v):
            warnings.append(f'{k} 看起来是占位符')

    return errors, warnings


def main():
    default = os.path.join(os.getcwd(), 'secrets.properties')
    path = sys.argv[1] if len(sys.argv) > 1 else default
    errors, warnings = check(path)

    print(f'检查: {path}')
    mode = oct(os.stat(path).st_mode & 0o777) if os.path.isfile(path) else '-'
    if os.path.isfile(path) and (os.stat(path).st_mode & 0o077):
        warnings.append(f'文件权限 {mode} 过宽，建议 chmod 600')
    for w in warnings:
        print('  WARN  ' + w)
    for e in errors:
        print('  ERROR ' + e)
    print(f'结果: {len(errors)} 个错误, {len(warnings)} 个警告')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
