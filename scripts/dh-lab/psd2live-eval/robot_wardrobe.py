#!/usr/bin/env python3
"""Generate original robot wardrobe Layout inputs, not runtime acceptance evidence.

Run with Pillow: python -I robot_wardrobe.py OUTPUT. OUTPUT must not exist.
The sibling robot-wardrobe-spec.json is the source of slot/parameter bindings.
"""
import argparse
import itertools
import json
from pathlib import Path
import runpy

HERE = Path(__file__).resolve().parent


def load_spec():
    return json.loads((HERE / 'robot-wardrobe-spec.json').read_text(encoding='utf-8'))


def decide(spec, selection):
    """Return every toggle, including off values, for one selection per slot."""
    slots = spec['slots']
    if set(selection) != {slot['slot'] for slot in slots}:
        raise ValueError('select exactly one variant for every slot')
    result = {}
    for slot in slots:
        selected = selection[slot['slot']]
        if selected not in {variant['token'] for variant in slot['variants']}:
            raise ValueError('unknown variant for ' + slot['slot'])
        for variant in slot['variants']:
            result[variant['param']] = int(variant['token'] == selected)
    return result


def generate(spec, out):
    from PIL import Image, ImageDraw

    base = runpy.run_path(str(HERE / 'make_original_avatar_demo.py'))
    width, height = base['W'], base['H']
    original = {name.replace(' ', '_'): (tags, fn) for name, tags, _, fn in base['LAYERS']}

    def jacket(d):
        d.rounded_rectangle((231, 342, 409, 615), 28, fill='#526ac2', outline='#27364d', width=10)
        d.line((320, 365, 320, 600), fill='#e8edf2', width=8)
        d.rectangle((250, 470, 288, 502), fill='#ffbf69')
        d.rectangle((352, 470, 390, 502), fill='#ffbf69')

    def sneakers(d):
        for box in ((225, 740, 310, 800), (330, 740, 415, 800)):
            d.rounded_rectangle(box, 20, fill='#e66f51', outline='#27364d', width=8)
            x, y = box[:2]
            d.line((x + 15, y + 39, x + 69, y + 39), fill='#f4f6f8', width=8)

    original['jacket'] = (('topwear',), jacket)
    original['sneakers'] = (('body',), sneakers)
    bindings = {v['layer']: (s, v) for s in spec['slots'] for v in s['variants']}
    if len(bindings) != sum(len(s['variants']) for s in spec['slots']) or not set(bindings) <= set(original):
        raise ValueError('invalid or duplicate layer binding')
    if len({v['param'] for _, v in bindings.values()}) != len(bindings):
        raise ValueError('duplicate parameters')
    if spec['naming']['order'] != [s['slot'] for s in spec['slots']]:
        raise ValueError('slot naming order mismatch')
    if any(not s['exclusive'] or not s['variants'] or len({v['token'] for v in s['variants']}) != len(s['variants']) for s in spec['slots']):
        raise ValueError('invalid exclusive slot')
    # Preserve the reference's stacking order; alternatives occupy the same position.
    order = ['antenna', 'body', 'legs', 'boots', 'sneakers', 'left_arm', 'right_arm',
             'head', 'eye_left', 'eye_right', 'mouth_neutral', 'mouth_smile',
             'cape', 'jacket', 'chest_badge']
    out.mkdir(parents=True, exist_ok=False)
    (out / 'parts').mkdir()
    (out / 'expressions').mkdir()
    layout = {'format': 'psd2live-layout', 'version': 1, 'name': 'original-robot-wardrobe',
              'canvas': {'width': width, 'height': height}, 'layers': []}
    images = {}
    for layer_id in order:
        tags, draw = original[layer_id]
        image, (left, top, right, bottom) = base['draw_layer'](draw)
        path = f'parts/{layer_id}.png'
        image.save(out / path)
        entry = {'id': layer_id, 'name': layer_id.replace('_', ' '),
                 'image': {'path': path, 'width': image.width, 'height': image.height},
                 'rect': {'left': left, 'top': top, 'width': right-left, 'height': bottom-top},
                 'tags': list(tags)}
        if layer_id in bindings:
            entry['variant'] = {'type': 'toggle', 'parameter': bindings[layer_id][1]['param']}
        layout['layers'].append(entry)
        images[layer_id] = (image, (left, top))
    (out / 'layout.json').write_text(json.dumps(layout, indent=2) + '\n')
    for choices in itertools.product(*(s['variants'] for s in spec['slots'])):
        selection = {s['slot']: v['token'] for s, v in zip(spec['slots'], choices)}
        params = decide(spec, selection)
        name = '__'.join(selection[s['slot']] for s in spec['slots'])
        expression = {'Type': 'Live2D Expression', 'FadeInTime': 0.0, 'FadeOutTime': 0.0,
                      'Parameters': [{'Id': key, 'Value': value, 'Blend': 'Overwrite'} for key, value in params.items()]}
        (out / 'expressions' / f'{name}.exp3.json').write_text(json.dumps(expression, indent=2) + '\n')
        canvas = Image.new('RGBA', (width, height), 'white')
        for entry in layout['layers']:
            variant = entry.get('variant')
            if variant is None or params[variant['parameter']]:
                image, position = images[entry['id']]
                canvas.alpha_composite(image, position)
        canvas.convert('RGB').save(out / f'source-{name}.png')
    summary = {'kind': 'original-layered-input-only', 'runtime_verified': False,
               'note': 'Source composites and expressions are inputs, not runtime acceptance.',
               'layer_count': len(layout['layers']), 'parameters': list(params)}
    (out / 'fixture-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(generate(load_spec(), args.output.resolve()), indent=2))


if __name__ == '__main__':
    main()
