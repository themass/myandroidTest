"""钉死 items.json 每一行的 id 形状：有没有行缺 id、缺 id 时 item.json?id=<小数字> 服务端给什么。

对应 ScreeningFeedStore.toTextListEntry 的 `itemId = id ?: index` 兜底——
如果真有行缺 id，App 会把 0/1/2 这种行号当文章 id 发给 item.json，正文「没加载出来」就是这么来的。
只读 GET。用法: python3 text_ids_probe.py [channel]
"""
import sys, os, json, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from text_chain_probe import unwrap

channel = sys.argv[1] if len(sys.argv) > 1 else '性趣闲谈'

items = unwrap('items', f'/text/items.json?{urllib.parse.urlencode({"start": 0, "limit": 60, "channel": channel, "keyword": ""})}')
rows = (items.get('voList') or items.get('rows') or []) if items else []
print(f'channel={channel} rows={len(rows)}')

missing = [(i, r) for i, r in enumerate(rows) if not r.get('id')]
present = [(r.get('id'), r.get('title')) for i, r in enumerate(rows) if r.get('id')]
print(f'rows with id: {len(present)}, rows WITHOUT id: {len(missing)}')
for i, r in missing[:8]:
    print(f'  NO-ID row idx={i}: {json.dumps(r, ensure_ascii=False)[:240]}')

if present:
    good_id = present[0][0]
    body = unwrap('item:good', f'/text/item.json?id={good_id}')
    f = body.get('file') if isinstance(body, dict) else None
    print(f'good id={good_id} -> file len {len(f) if isinstance(f, str) else f}')

# 模拟缺 id 行被按行号兜底的请求：id=0/1/2 服务端到底回什么
for probe_id in (0, 1, 2):
    body = unwrap(f'item:bogus{id}', f'/text/item.json?id={probe_id}')
    if body is None:
        print(f'bogus id={probe_id} -> envelope fail')
    elif isinstance(body, dict):
        f = body.get('file')
        print(f'bogus id={probe_id} -> keys={list(body.keys())} file len {len(f) if isinstance(f, str) else f}')
    else:
        print(f'bogus id={probe_id} -> {type(body)} {str(body)[:120]}')
