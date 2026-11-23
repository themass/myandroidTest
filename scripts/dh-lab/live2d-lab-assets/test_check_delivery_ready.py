"""Run with other live2d-lab-assets tests."""
import json
import subprocess
import sys
from pathlib import Path
import unittest

from check_delivery_ready import check_delivery_ready

SPEC = json.loads(Path(__file__).with_name("wardrobe-spec.json").read_text())
EXAMPLE = Path(__file__).resolve().parent / "examples" / "underwear-delivery-minimal"


class CheckDeliveryReadyTest(unittest.TestCase):
    def test_minimal_example_passes_after_placeholders(self):
        subprocess.run(
            [sys.executable, str(EXAMPLE / "materialize_placeholders.py")],
            check=True,
            cwd=EXAMPLE,
        )
        summary = check_delivery_ready(SPEC, EXAMPLE / "source.json")
        self.assertTrue(summary["ok"], summary["errors"])
        self.assertEqual(summary["missing_tokens"], [])
        self.assertFalse(summary["runtime_verified"])

    def test_fails_when_token_missing(self):
        source = json.loads((EXAMPLE / "source.json").read_text())
        source["layers"] = [l for l in source["layers"] if l.get("wardrobe", {}).get("token") != "cat"]
        tmp = EXAMPLE / ".test-missing-cat.json"
        tmp.write_text(json.dumps(source))
        try:
            subprocess.run(
                [sys.executable, str(EXAMPLE / "materialize_placeholders.py")],
                check=True,
                cwd=EXAMPLE,
            )
            summary = check_delivery_ready(SPEC, tmp)
            self.assertFalse(summary["ok"])
            self.assertIn("cat", summary["missing_tokens"])
        finally:
            tmp.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
