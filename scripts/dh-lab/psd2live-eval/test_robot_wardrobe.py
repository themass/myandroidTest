"""Pure decision and generated-input contract tests; no runtime acceptance."""
import importlib.util
import itertools
import json
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
specification = importlib.util.spec_from_file_location('robot_wardrobe', HERE / 'robot_wardrobe.py')
robot = importlib.util.module_from_spec(specification)
specification.loader.exec_module(robot)


class RobotWardrobeTest(unittest.TestCase):
    def setUp(self):
        self.spec = robot.load_spec()

    def test_all_combinations_are_exclusive_and_complete(self):
        slots = self.spec['slots']
        all_params = {v['param'] for s in slots for v in s['variants']}
        self.assertEqual(len(all_params), sum(len(s['variants']) for s in slots))
        for choices in itertools.product(*(s['variants'] for s in slots)):
            selection = {s['slot']: v['token'] for s, v in zip(slots, choices)}
            params = robot.decide(self.spec, selection)
            self.assertEqual(set(params), all_params)
            for slot, chosen in zip(slots, choices):
                self.assertEqual(sum(params[v['param']] for v in slot['variants']), 1)
                self.assertEqual(params[chosen['param']], 1)

    def test_invalid_selections_rejected(self):
        with self.assertRaises(ValueError):
            robot.decide(self.spec, {})
        selection = {s['slot']: s['variants'][0]['token'] for s in self.spec['slots']}
        for bad in ({**selection, 'top': 'missing'}, {**selection, 'extra': 'x'}):
            with self.assertRaises(ValueError):
                robot.decide(self.spec, bad)

    def test_generated_layout_and_expressions_match_spec(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest('Pillow required for generation')
        with tempfile.TemporaryDirectory() as root:
            output = Path(root) / 'fixture'
            robot.generate(self.spec, output)
            layout = json.loads((output / 'layout.json').read_text())
            layers = {layer['id']: layer for layer in layout['layers']}
            variants = [v for s in self.spec['slots'] for v in s['variants']]
            for variant in variants:
                layer = layers[variant['layer']]
                self.assertEqual(layer['variant'], {'type': 'toggle', 'parameter': variant['param']})
            for layer in layers.values():
                with Image.open(output / layer['image']['path']) as image:
                    self.assertEqual(image.size, (layer['image']['width'], layer['image']['height']))
                    self.assertIsNotNone(image.getbbox())
            expressions = list((output / 'expressions').glob('*.exp3.json'))
            self.assertEqual(len(expressions), 8)
            for path in expressions:
                exp = json.loads(path.read_text())
                params = {p['Id']: p['Value'] for p in exp['Parameters']}
                self.assertEqual(len(params), len(variants))
                selection = dict(zip((s['slot'] for s in self.spec['slots']), path.name.removesuffix('.exp3.json').split('__')))
                self.assertEqual(params, robot.decide(self.spec, selection))
            self.assertFalse(json.loads((output / 'fixture-summary.json').read_text())['runtime_verified'])


if __name__ == '__main__':
    unittest.main()
