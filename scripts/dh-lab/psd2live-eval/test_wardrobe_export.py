"""Run: python3 -m unittest discover -s scripts/dh-lab/psd2live-eval -p 'test_*.py'."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from wardrobe_export import inspect_directory, load_spec, production_wear_params

SPEC = load_spec(Path(__file__).resolve().parents[1] / "live2d-lab-assets" / "wardrobe-spec.json")
EVIDENCE = Path(__file__).resolve().parent / "evidence"


def _synthetic_moc3(*param_names: str) -> bytes:
    blob = bytearray(b"MOC3\x05" + b"\x00" * 64)
    for name in param_names:
        blob.extend(name.encode("ascii"))
        blob.extend(b"\x00\x00")
    return bytes(blob)


class WardrobeExportInspectTest(unittest.TestCase):
    def test_production_params_from_spec_underwear_only(self):
        params = production_wear_params(SPEC)
        names = {p["parameter"] for p in params}
        self.assertEqual(names, {"ParamWear_lace", "ParamWear_sport", "ParamWear_cat"})

    def test_layout_evidence_cdi_has_fixture_param_not_production_tokens(self):
        cdi = json.loads((EVIDENCE / "layout.cdi3.json").read_text())
        ids = {p["Id"] for p in cdi["Parameters"]}
        self.assertIn("ParamWear_test", ids)
        self.assertNotIn("ParamWear_lace", ids)

    def test_inspect_passes_when_moc3_and_cdi_match_declared_params(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            prefix = "layout"
            shutil.copy(EVIDENCE / "layout.model3.json", root / f"{prefix}.model3.json")
            cdi = json.loads((EVIDENCE / "layout.cdi3.json").read_text())
            cdi["Parameters"].append({"Id": "ParamWear_lace", "GroupId": "ParamGroupCustom"})
            cdi["Parameters"].append({"Id": "ParamWear_sport", "GroupId": "ParamGroupCustom"})
            cdi["Parameters"].append({"Id": "ParamWear_cat", "GroupId": "ParamGroupCustom"})
            (root / f"{prefix}.cdi3.json").write_text(json.dumps(cdi))
            (root / f"{prefix}.moc3").write_bytes(
                _synthetic_moc3("ParamWear_lace", "ParamWear_sport", "ParamWear_cat", "ParamAngleX")
            )
            (root / "layout.4096").mkdir()
            for tex in ("texture_00.png", "texture_01.png", "texture_02.png"):
                (root / "layout.4096" / tex).write_bytes(b"\x89PNG\r\n\x1a\n")
            summary = inspect_directory(root, prefix=prefix, spec=SPEC)
            self.assertTrue(summary["ok"], summary["errors"])
            self.assertEqual(summary["wardrobe_params_missing"], [])
            self.assertFalse(summary["runtime_verified"])

    def test_inspect_fails_when_declared_param_missing_from_moc3(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            prefix = "layout"
            shutil.copy(EVIDENCE / "layout.model3.json", root / f"{prefix}.model3.json")
            shutil.copy(EVIDENCE / "layout.cdi3.json", root / f"{prefix}.cdi3.json")
            (root / f"{prefix}.moc3").write_bytes(_synthetic_moc3("ParamWear_test"))
            (root / "layout.4096").mkdir()
            for tex in ("texture_00.png", "texture_01.png", "texture_02.png"):
                (root / "layout.4096" / tex).write_bytes(b"\x89PNG\r\n\x1a\n")
            summary = inspect_directory(root, prefix=prefix, spec=SPEC)
            self.assertFalse(summary["ok"])
            self.assertTrue(summary["wardrobe_params_missing"])


if __name__ == "__main__":
    unittest.main()
