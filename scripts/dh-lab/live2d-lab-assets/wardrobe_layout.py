"""Build a psd2live Layout; never enable runtime variants or rewrite the source spec.

Layout contract: psd2live v3.8.4 docs/zh/spec/LAYOUT_MANIFEST.md.
PNG headers are checked here; the exporter performs full image decoding.
"""
import argparse
import copy
import json
import math
from pathlib import Path
import re
import struct


def build_layout(spec, source, asset_root):
    """Validate artist layers and bind explicitly declared production variants."""
    root = Path(asset_root).resolve()
    canvas = source['canvas']
    if any(type(canvas[k]) is not int or not 1 <= canvas[k] <= 8192
           for k in ('width', 'height')) or canvas['width'] * canvas['height'] > 16_000_000:
        raise ValueError('invalid canvas dimensions')
    layers = copy.deepcopy(source['layers'])
    if not 1 <= len(layers) <= 4096:
        raise ValueError('invalid layer count')
    slots = {s['slot']: s for s in spec['slots']}
    for layer in layers:
        image = layer['image']
        path = image['path']
        if (not isinstance(path, str) or not path or '\\' in path or ':' in path
                or path.startswith('/') or any(p in ('', '.', '..') for p in path.split('/'))):
            raise ValueError('invalid relative image path')
        resolved = (root / path).resolve()
        if not resolved.is_relative_to(root) or not resolved.is_file():
            raise ValueError('missing image or image outside asset root')
        with resolved.open('rb') as stream:
            header = stream.read(24)
        if len(header) != 24 or header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR':
            raise ValueError('pipeline requires PNG images')
        width, height = struct.unpack('>II', header[16:24])
        if (width < 1 or height < 1 or width * height > 16_000_000
                or (width, height) != (image['width'], image['height'])):
            raise ValueError('PNG dimensions differ from manifest or exceed limit')
        rect = layer['rect']
        for key in ('left', 'top', 'width', 'height'):
            v = rect[key]
            if type(v) not in (int, float) or not math.isfinite(v):
                raise ValueError('invalid rectangle')
        if rect['width'] <= 0 or rect['height'] <= 0:
            raise ValueError('invalid rectangle size')
        binding = layer.pop('wardrobe', None)
        if binding is not None:
            slot = slots.get(binding['slot'])
            variant = next((v for v in slot['variants'] if v['token'] == binding['token']), None) if slot else None
            contract = slot.get('productionBinding') if slot else None
            if not variant or not contract or 'variant' in layer or layer.get('reference'):
                raise ValueError('invalid wardrobe variant or binding override')
            token = variant['token']
            if not re.fullmatch(r'[A-Za-z0-9_]+', token):
                raise ValueError('invalid wardrobe variant token')
            layer['variant'] = {'type': 'toggle', 'parameter': contract['parameterPrefix'] + token}
    result = {'format': 'psd2live-layout', 'version': 1,
              'canvas': copy.deepcopy(canvas), 'layers': layers}
    for field in ('name', 'groups'):
        if field in source:
            result[field] = copy.deepcopy(source[field])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='artist source manifest with wardrobe slot/token per variant layer')
    parser.add_argument('--spec', type=Path, default=Path(__file__).with_name('wardrobe-spec.json'))
    parser.add_argument('--output', type=Path, required=True, help='new Layout JSON beside the source manifest')
    args = parser.parse_args()
    if args.output.resolve().parent != args.source.resolve().parent:
        parser.error('output must be beside source so relative PNG paths remain valid')
    try:
        result = build_layout(json.loads(args.spec.read_text()), json.loads(args.source.read_text()), args.source.parent)
        # Refuse overwrites (including the input), and write only after full validation.
        with args.output.open('x') as stream:
            stream.write(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + '\n')
    except (ValueError, KeyError, TypeError, OSError) as exc:
        parser.exit(1, f'Layout generation failed: {exc}\n')
    print(f'Layout ready: {args.output}; runtime spec remains unchanged')


if __name__ == '__main__':
    main()
