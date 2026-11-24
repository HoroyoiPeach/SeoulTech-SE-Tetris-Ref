"""Run from the project root after Gradle build desktopSmoke."""

import csv
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
from xml.etree import ElementTree
import zipfile

import bundle_candidate as candidate


class ReportTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.build = Path(temp.name)
        for name in ("test-results/test", "reports"):
            shutil.copytree(Path("build") / name, self.build / name)

    def test_accepts_fresh_gradle_evidence(self):
        totals = candidate.read_reports(self.build)
        self.assertGreater(totals["tests"], 0)
        self.assertEqual(totals["skipped"], 0)
        self.assertGreater(totals["lines_covered"], 0)

    def test_rejects_missing_junit_suite(self):
        next(self.build.glob("test-results/test/TEST-*.xml")).unlink()
        with self.assertRaises(ValueError):
            candidate.read_reports(self.build)

    def test_rejects_one_skipped_test_even_when_gradle_agrees(self):
        suite = next(self.build.glob("test-results/test/TEST-*.xml"))
        tests = ElementTree.parse(suite)
        tests.getroot().set("skipped", "1")
        ElementTree.SubElement(tests.find("testcase"), "skipped")
        tests.write(suite, encoding="utf-8")

        index = self.build / "reports/tests/test/index.html"
        html = ElementTree.parse(index)
        box = html.find(".//div[p='skipped']/div[@class='counter']")
        box.text = "1"
        html.write(index, encoding="utf-8")
        with self.assertRaises(ValueError):
            candidate.read_reports(self.build)

    def test_rejects_different_html_and_xml_line_counts(self):
        report = self.build / "reports/jacoco/test/jacocoTestReport.xml"
        coverage = ElementTree.parse(report)
        lines = coverage.find("counter[@type='LINE']")
        lines.set("missed", str(int(lines.get("missed")) + 1))
        coverage.write(report, encoding="utf-8")
        with self.assertRaises(ValueError):
            candidate.read_reports(self.build)

    def test_rejects_missing_desktop_pass(self):
        (self.build / "reports/desktop/result.txt").write_text("NOT RUN", encoding="utf-8")
        with self.assertRaises(ValueError):
            candidate.read_reports(self.build)

    def test_rejects_distribution_with_insufficient_samples_despite_pass(self):
        report = self.build / "reports/req2/block-distribution.csv"
        with report.open(encoding="utf-8", newline="") as stream:
            rows = list(csv.DictReader(stream))
        for row in rows:
            row.update(samples="7", count="1", **{"pass": "true"})
        with report.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        with self.assertRaises(ValueError):
            candidate.read_reports(self.build)


class PackageTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.name = "candidate.zip"
        self.manifest = {"commit": "abc", "attempt": 1}

    def stage(self, label):
        ready = self.root / label
        ready.mkdir()
        package = ready / self.name
        with zipfile.ZipFile(package, "w") as archive:
            archive.writestr("manifest.json", json.dumps(self.manifest))
            archive.writestr("build-marker", label)
        (ready / (self.name + ".sha256")).write_text(
            f"{candidate.sha256(package)}  {self.name}\n", encoding="ascii")
        return ready

    def test_rejects_zip_missing_game_and_runtime(self):
        package = self.root / "app.zip"
        with zipfile.ZipFile(package, "w") as app:
            app.writestr("SeoulTech Tetris.app/Contents/Info.plist", "placeholder")
        with self.assertRaises(ValueError):
            candidate.check_app_archive(package)

    def test_retry_preserves_published_zip_and_checksum(self):
        target = self.root / "published"
        first = candidate.publish_candidate(self.stage("first"), target, self.name, self.manifest)
        original = candidate.sha256(first)
        repeated = candidate.publish_candidate(self.stage("second"), target, self.name, self.manifest)
        self.assertEqual(original, candidate.sha256(repeated))
        checksum = (target / (self.name + ".sha256")).read_text(encoding="ascii")
        self.assertEqual(checksum.split(), [original, self.name])

    def test_retry_detects_damaged_existing_candidate(self):
        target = self.root / "published"
        result = candidate.publish_candidate(self.stage("first"), target, self.name, self.manifest)
        result.write_bytes(b"incomplete")
        with self.assertRaises(ValueError):
            candidate.publish_candidate(self.stage("second"), target, self.name, self.manifest)

    def test_interrupted_publication_leaves_no_partial_output(self):
        ready = self.stage("first")
        target = self.root / "published"
        with patch.object(Path, "rename", side_effect=OSError("interrupted")):
            with self.assertRaises(OSError):
                candidate.publish_candidate(ready, target, self.name, self.manifest)
        self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
