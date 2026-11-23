"""Run: python3 -m unittest discover -s scripts/dh-lab/live2d-lab-assets -p 'test_*.py'."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from wardrobe_layout import build_layout

SPEC = json.loads(Path(__file__).with_name('wardrobe-spec.json').read_text())


class WardrobeLayoutTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        # Valid 1x1 PNG, no third-party art.
        import base64
        png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=')
        (self.root / 'base.png').write_bytes(png)
        (self.root / 'lace.png').write_bytes(png)
        self.source = {'name': 'wardrobe-test', 'canvas': {'width': 100, 'height': 100}, 'layers': [
            {'name': 'body', 'image': {'path': 'base.png', 'width': 1, 'height': 1},
             'rect': {'left': 0, 'top': 0, 'width': 100, 'height': 100}},
            {'name': 'underwear lace', 'image': {'path': 'lace.png', 'width': 1, 'height': 1},
             'rect': {'left': 25, 'top': 30, 'width': 50, 'height': 40},
             'wardrobe': {'slot': 'underwear', 'token': 'lace'}}]}

    def test_binds_declared_pending_variant_without_activating_spec(self):
        before = copy.deepcopy(SPEC)
        result = build_layout(SPEC, self.source, self.root)
        self.assertEqual(result['format'], 'psd2live-layout')
        self.assertEqual(result['layers'][1]['variant'],
                         {'type': 'toggle', 'parameter': 'ParamWear_lace'})
        self.assertNotIn('wardrobe', result['layers'][1])
        self.assertEqual(result['layers'][0], self.source['layers'][0])
        self.assertEqual(SPEC, before)

    def test_three_variants_follow_spec_prefix_and_preserve_order(self):
        spec = copy.deepcopy(SPEC)
        slot = next(s for s in spec['slots'] if s['slot'] == 'underwear')
        slot['productionBinding']['parameterPrefix'] = 'CustomWear_'
        for token in ('sport', 'cat'):
            layer = copy.deepcopy(self.source['layers'][1])
            layer['name'] = token
            layer['wardrobe']['token'] = token
            self.source['layers'].append(layer)
        result = build_layout(spec, self.source, self.root)
        self.assertEqual([l['variant']['parameter'] for l in result['layers'][1:]],
                         ['CustomWear_lace', 'CustomWear_sport', 'CustomWear_cat'])

    def test_cli_emits_layout_and_refuses_overwrite_or_rebased_paths(self):
        import subprocess
        import sys
        source = self.root / 'source.json'
        source.write_text(json.dumps(self.source))
        output = self.root / 'layout.json'
        command = [sys.executable, str(Path(__file__).with_name('wardrobe_layout.py')),
                   str(source), '--output', str(output)]
        success = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(success.returncode, 0, success.stderr)
        original = output.read_bytes()
        self.assertEqual(json.loads(original)['layers'][1]['variant']['parameter'], 'ParamWear_lace')
        repeated = subprocess.run(command, capture_output=True)
        self.assertNotEqual(repeated.returncode, 0)
        self.assertEqual(output.read_bytes(), original)
        (self.root / 'nested').mkdir()
        relocated = subprocess.run(command[:-1] + [str(self.root / 'nested' / 'layout.json')],
                                   capture_output=True)
        self.assertNotEqual(relocated.returncode, 0)
        self.assertFalse((self.root / 'nested' / 'layout.json').exists())

    def test_rejects_undeclared_variant(self):
        self.source['layers'][1]['wardrobe']['token'] = 'invented'
        with self.assertRaisesRegex(ValueError, 'variant'):
            build_layout(SPEC, self.source, self.root)

    def test_rejects_traversal_and_symlink_outside_asset_root(self):
        for path in ('../lace.png', '/tmp/lace.png', './lace.png'):
            with self.subTest(path=path):
                self.source['layers'][1]['image']['path'] = path
                with self.assertRaises(ValueError):
                    build_layout(SPEC, self.source, self.root)
        (self.root / 'outside.png').symlink_to('/etc/hosts')
        self.source['layers'][1]['image']['path'] = 'outside.png'
        with self.assertRaises(ValueError):
            build_layout(SPEC, self.source, self.root)

    def test_rejects_missing_image_and_wrong_dimensions(self):
        self.source['layers'][1]['image']['width'] = 2
        with self.assertRaisesRegex(ValueError, 'dimensions'):
            build_layout(SPEC, self.source, self.root)
        self.source['layers'][1]['image']['path'] = 'missing.png'
        with self.assertRaises(ValueError):
            build_layout(SPEC, self.source, self.root)

    def test_rejects_override_and_nonfinite_rectangle(self):
        self.source['layers'][1]['variant'] = {'type': 'toggle', 'parameter': 'Wrong'}
        with self.assertRaises(ValueError):
            build_layout(SPEC, self.source, self.root)
        del self.source['layers'][1]['variant']
        self.source['layers'][1]['rect']['width'] = float('nan')
        with self.assertRaises(ValueError):
            build_layout(SPEC, self.source, self.root)


if __name__ == '__main__':
    unittest.main()
