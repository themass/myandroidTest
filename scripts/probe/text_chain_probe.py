"""文字馆接口族探针：channel.json → items.json → item.json?id=（只读 GET，不经过 App）。

用途：放映场 ScreeningText 正文「加载不出来」——先钉住服务端到底给了什么：
items.json 的行里 id 是什么形状、fileUrl 长什么样、item.json?id= 拿那个 id 能不能取回 file。
算法跟旧客户端一致：UA 末 16 位是 authKey，响应 data 是 AES-ECB hex。
需要 pycryptodome。用法: python3 text_chain_probe.py [channel]
"""
import hashlib, time, urllib.request, urllib.parse, sys
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad

BASE = "https://api.ok123find.top/vvv/api"
devid = "androidimei82892812384933941456"


def ua():
    t = int(time.time() * 1000)
    # 对齐 BaseRequest：msg = time + Md5(devid + "|" + time)，authkey = UA 末 16 位
    msg = str(t) + hashlib.md5(f"{devid}|{t}".encode()).hexdigest()
    key = msg[-16:]
    return (
        f"Dalvik/2.1.0 (Linux; U; Android 11; ADY-AL00 Build/HUAWEIADY-AL00) "
        f"SEX/1.1.0.66,channel=GOOGLEMARKET,cpu=arm64-v8a,Webkit/ ,IE{msg}"
    ), key


def get(path):
    u, key = ua()
    req = urllib.request.Request(BASE + path)
    req.add_header('User-Agent', u)
    req.add_header('Devid', devid)
    req.add_header('Referer', 'http://www.ok123find.top/')
    req.add_header('lang', 'zh')
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode('utf-8', 'ignore'), key
    except Exception as e:
        return None, str(e), key


def decode(data_hex, key):
    k = key.encode()[:16]
    return unpad(AES.new(k, AES.MODE_ECB).decrypt(bytes.fromhex(data_hex)), 16).decode('utf-8', 'ignore')


def unwrap(tag, path):
    import json
    s, body, key = get(path)
    print(f"\n== {tag} GET {path}\nHTTP {s}")
    try:
        env = json.loads(body)
    except Exception:
        print('RAW', body[:400])
        return None
    print('errno', env.get('errno'), 'msg', env.get('msg'))
    data = env.get('data')
    if not data:
        return env
    try:
        plain = decode(data, key)
    except Exception as e:
        print('DECODE FAIL', e)
        return env
    obj = json.loads(plain)
    return obj


if __name__ == '__main__':
    import json
    channel = sys.argv[1] if len(sys.argv) > 1 else '小说'
    hub = unwrap('channel', f'/text/channel.json?start=0&limit=50&channel={urllib.parse.quote(channel)}')
    if hub:
        rows = hub.get('voList') or hub.get('rows') or []
        print('hub rows', len(rows))
        for r in rows[:6]:
            print('  hub row:', json.dumps(r, ensure_ascii=False))
        key2 = (rows[0].get('param') or rows[0].get('actionUrl') or '') if rows else channel
        qs = urllib.parse.urlencode({'start': 0, 'limit': 60, 'channel': key2, 'keyword': ''})
        items = unwrap('items', f'/text/items.json?{qs}')
        if items:
            irows = items.get('voList') or items.get('rows') or []
            print('items rows', len(irows), 'total', items.get('total'), 'hasMore', items.get('hasMore'))
            for r in irows[:5]:
                print('  item row:', json.dumps(r, ensure_ascii=False))
            if irows:
                first = irows[0]
                for probe_id in [first.get('id'), first.get('itemId')]:
                    if probe_id is None:
                        continue
                    body = unwrap('item', f'/text/item.json?id={probe_id}')
                    if body:
                        keys = list(body.keys()) if isinstance(body, dict) else type(body)
                        print('  item keys:', keys)
                        f = body.get('file') if isinstance(body, dict) else None
                        print('  file len:', len(f) if isinstance(f, str) else f)
                        if isinstance(f, str):
                            print('  file head:', f[:200].replace('\n', ' '))
