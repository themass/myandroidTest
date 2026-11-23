# 内衣交付最小样例（仅结构）

**不是正式美术。** 用于验证 `check_delivery_ready.py` → `wardrobe_layout.py` 与 `wardrobe-spec` 三 token 对齐。

```bash
cd scripts/dh-lab/live2d-lab-assets/examples/underwear-delivery-minimal
python3 materialize_placeholders.py
python3 ../../check_delivery_ready.py source.json
python3 ../../wardrobe_layout.py source.json --output layout.json
```

正式交付替换 PNG 与 rect，勿把本目录产物接入 APK 或标 active。
