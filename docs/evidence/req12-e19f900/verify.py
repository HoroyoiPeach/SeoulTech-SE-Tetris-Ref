#!/usr/bin/env python3
"""Validate the archived CI evidence and write a compact deterministic summary."""

from __future__ import annotations

import csv
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
SOURCE_ARCHIVE = ROOT / "source" / "test-reports-macos-26.zip"
EXPECTED_ARCHIVE_SHA256 = "a610e5d20d26402a7cbc1719aef839d5bfdc3a786a54efe5e52fed38bef4ce38"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_csv(name: str) -> list[dict[str, str]]:
    with (ROOT / "distribution" / name).open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def assert_same_as_archive(archive: zipfile.ZipFile, local: Path, member: str) -> None:
    assert local.read_bytes() == archive.read(member), f"archive mismatch: {local.relative_to(ROOT)}"


tests = failures = errors = skipped = 0
for report in sorted((ROOT / "junit").glob("TEST-*.xml")):
    suite = ET.parse(report).getroot()
    tests += int(suite.attrib["tests"])
    failures += int(suite.attrib["failures"])
    errors += int(suite.attrib["errors"])
    skipped += int(suite.attrib["skipped"])
assert (tests, failures, errors, skipped) == (95, 0, 0, 0)

jacoco = ET.parse(ROOT / "coverage" / "jacocoTestReport.xml").getroot()
line_counter = next(counter for counter in jacoco.findall("counter") if counter.attrib["type"] == "LINE")
missed = int(line_counter.attrib["missed"])
covered = int(line_counter.attrib["covered"])
assert (covered, missed) == (1519, 169)

blocks = read_csv("block-distribution.csv")
items = read_csv("item-distribution.csv")
assert len(blocks) == 21 and len(items) == 5
assert {row["samples"] for row in blocks} == {"100000"}
assert {row["difficulty"] for row in blocks} == {"Easy", "Normal", "Hard"}
weights = {
    "Easy": {"IBlock": 12, "JBlock": 10, "LBlock": 10, "ZBlock": 10, "SBlock": 10, "TBlock": 10, "OBlock": 10},
    "Normal": {name: 1 for name in ["IBlock", "JBlock", "LBlock", "ZBlock", "SBlock", "TBlock", "OBlock"]},
    "Hard": {"IBlock": 8, "JBlock": 10, "LBlock": 10, "ZBlock": 10, "SBlock": 10, "TBlock": 10, "OBlock": 10},
}
for difficulty, configured in weights.items():
    selected = [row for row in blocks if row["difficulty"] == difficulty]
    assert sum(int(row["count"]) for row in selected) == 100000
    denominator = sum(configured.values())
    for row in selected:
        count = int(row["count"])
        samples = int(row["samples"])
        expected = configured[row["block"]] / denominator
        observed = count / samples
        relative_error_percent = abs(observed - expected) / expected * 100
        assert abs(float(row["expected_probability"]) - expected) <= 5e-9
        assert abs(float(row["observed_probability"]) - observed) <= 5e-9
        assert abs(float(row["relative_error_percent"]) - relative_error_percent) <= 5e-5
        assert row["pass"] == "true" and relative_error_percent <= 5
assert {row["samples"] for row in items} == {"100000"}
assert sum(int(row["count"]) for row in items) == 100000
for row in items:
    count = int(row["count"])
    samples = int(row["samples"])
    expected = 0.2
    observed = count / samples
    relative_error = abs(observed - expected) / expected
    assert abs(float(row["expected"]) - expected) <= 1e-12
    assert abs(float(row["observed"]) - observed) <= 1e-12
    assert abs(float(row["relative_error"]) - relative_error) <= 1e-12
    assert row["pass"] == "true" and relative_error <= 0.05

performance = ET.parse(ROOT / "junit" / "TEST-seoultech.se.tetris.component.PerformanceRegressionTest.xml").getroot()
performance_text = performance.findtext("system-out", default="")
assert "operations=400000 elapsedMs=21 budgetMs=5000" in performance_text

desktop_result = (ROOT / "desktop" / "result.txt").read_text(encoding="utf-8")
assert desktop_result.startswith("PASS ")
assert "OS=Mac OS X 26.6.2 arch=aarch64 Java=21.0.13" in desktop_result

archive_sha = sha256(SOURCE_ARCHIVE)
assert archive_sha == EXPECTED_ARCHIVE_SHA256
with zipfile.ZipFile(SOURCE_ARCHIVE) as archive:
    archive_matches = 0
    for report in sorted((ROOT / "junit").glob("TEST-*.xml")):
        assert_same_as_archive(archive, report, f"test-results/test/{report.name}")
        archive_matches += 1
    assert_same_as_archive(archive, ROOT / "coverage" / "jacocoTestReport.xml", "reports/jacoco/test/jacocoTestReport.xml")
    archive_matches += 1
    for name in ["block-distribution.csv", "item-distribution.csv"]:
        assert_same_as_archive(archive, ROOT / "distribution" / name, f"reports/req2/{name}")
        archive_matches += 1
    assert_same_as_archive(archive, ROOT / "desktop" / "result.txt", "reports/desktop/result.txt")
    archive_matches += 1
    for name in ["cross-item-and-preview.png", "final-600x500-normal-classic.png", "final-750x625-protanopia-item.png", "final-hard-item-score.png"]:
        assert_same_as_archive(archive, ROOT / "desktop" / name, f"reports/desktop/{name}")
        archive_matches += 1
assert archive_matches == 30
main_tree = subprocess.check_output(["git", "rev-parse", "62a035f^{tree}"], cwd=REPO, text=True).strip()
tested_tree = subprocess.check_output(["git", "rev-parse", "e19f900^{tree}"], cwd=REPO, text=True).strip()
assert main_tree == tested_tree

preserved = [path for path in ROOT.rglob("*") if path.is_file() and path.name not in {"summary.json"}]
summary = {
    "source": {
        "workflow_run": 40315760987,
        "job": 121547834723,
        "tested_commit": "e19f9009d0639c3d6458f6e42f8ac30ad43094da",
        "main_commit": "62a035fba8897bc00d735cb2efb078e504654971",
        "identical_git_tree": main_tree,
        "artifact": "test-reports-macos-26",
        "artifact_sha256": archive_sha,
        "extracted_files_byte_identical": archive_matches,
    },
    "environment": {"os": "macOS 26.6.2", "architecture": "aarch64", "java": "21.0.13"},
    "junit": {"tests": tests, "failures": failures, "errors": errors, "skipped": skipped},
    "coverage": {"type": "LINE", "covered": covered, "missed": missed, "percent": round(covered / (covered + missed) * 100, 2)},
    "distribution": {
        "blocks": {"samples_per_difficulty": 100000, "rows": len(blocks), "all_pass": True, "seed": 20261024},
        "items": {"samples": 100000, "rows": len(items), "all_pass": True, "seed": 20261025},
        "maximum_relative_error_percent": max(max(float(row["relative_error_percent"]) for row in blocks), max(float(row["relative_error"]) * 100 for row in items)),
    },
    "core_performance": {"operations": 400000, "elapsed_ms": 21, "budget_ms": 5000},
    "desktop_smoke": "PASS",
    "preserved_files": {str(path.relative_to(ROOT)): sha256(path) for path in sorted(preserved)},
}
(ROOT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"result": "PASS", **{key: summary[key] for key in ["junit", "coverage", "distribution", "core_performance", "desktop_smoke"]}}, ensure_ascii=False, indent=2))
