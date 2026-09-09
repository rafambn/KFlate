import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import benchmark_suite as suite


class StoredBaselineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.performance = self.root / "performance"
        self.patch_root = patch.object(suite, "ROOT", self.root)
        self.patch_performance = patch.object(suite, "PERFORMANCE", self.performance)
        self.patch_root.start()
        self.patch_performance.start()
        corpus = self.root / "kflate/src/jvmTest/resources"
        corpus.mkdir(parents=True)
        for name in suite.CORPORA:
            (corpus / name).write_bytes(name.encode())
        self.fixture = self.performance / "kompress-baseline/jvm/fixtures/6/simpleText.deflate"
        self.fixture.parent.mkdir(parents=True)
        self.fixture.write_bytes(b"fixed stream")
        self.baseline = {"platform": "jvm", "smoke": True, "runId": "baseline",
                         "corpusSha256": suite.corpus_hashes(),
                         "fixtureSha256": {"6/simpleText.deflate": suite.digest(self.fixture)}}

    def tearDown(self):
        self.patch_root.stop()
        self.patch_performance.stop()
        self.temp.cleanup()

    def test_saved_baseline_skips_gradle(self):
        suite.write_json(self.performance / "kompress-baseline/jvm/smoke.json", self.baseline)
        with patch.object(suite.subprocess, "run") as run:
            suite.capture("jvm", baseline=True, smoke=True, run_id="new")
        run.assert_not_called()

    def test_fixture_changes_are_rejected(self):
        self.fixture.write_bytes(b"different stream")
        with self.assertRaisesRegex(ValueError, "Missing or changed"):
            suite.validate_baseline(self.baseline, "jvm", smoke=True)

    def test_corpus_changes_are_rejected(self):
        (self.root / "kflate/src/jvmTest/resources/text").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Corpus changed"):
            suite.validate_baseline(self.baseline, "jvm", smoke=True)

    def test_platform_and_smoke_are_not_interchangeable(self):
        for target, smoke in [("wasmJs", True), ("jvm", False)]:
            with self.assertRaisesRegex(ValueError, "configuration does not match"):
                suite.validate_baseline(self.baseline, target, smoke)

    def test_missing_fixture_matrix_is_rejected(self):
        self.baseline["fixtureSha256"] = {}
        with self.assertRaisesRegex(ValueError, "Incomplete baseline"):
            suite.validate_baseline(self.baseline, "jvm", True)

    def test_history_preserves_every_run(self):
        baseline = {**self.baseline, "smoke": False}
        suite.write_json(self.performance / "kompress-baseline/jvm/baseline.json", baseline)
        for run_id in ("first", "second"):
            suite.write_json(self.performance / "runs" / run_id / "jvm/result.json",
                             {"runId": run_id, "platform": "jvm", "baselineRunId": "baseline"})
        with patch.object(suite, "validate_baseline"):
            suite.publish()
        history = json.loads((self.performance / "history.json").read_text())
        self.assertEqual(["first", "second"], [run["runId"] for run in history["runs"]])

    def test_missing_baseline_reference_cannot_be_published(self):
        suite.write_json(self.performance / "runs/new/jvm/result.json",
                         {"platform": "jvm", "baselineRunId": "unavailable"})
        with self.assertRaisesRegex(ValueError, "unavailable baseline"):
            suite.publish()


if __name__ == "__main__":
    unittest.main()
