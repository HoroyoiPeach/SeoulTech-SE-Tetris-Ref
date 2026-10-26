#!/usr/bin/env python3
"""Recalculate the preserved macOS local results for product source 154fd09."""
import csv
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def read_csv(name):
    with (ROOT / "distribution" / name).open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))
meta = json.loads((ROOT / "source.json").read_text())
for name, expected in meta["source_files_sha256"].items():
    content = subprocess.check_output(["git", "show", meta["tested_commit"] + ":" + name], cwd=REPO)
    assert hashlib.sha256(content).hexdigest() == expected, name
reports = sorted((ROOT / "junit").glob("TEST-*.xml"))
roots = [ET.parse(p).getroot() for p in reports]
junit = {k: sum(int(r.get(k, 0)) for r in roots) for k in ["tests", "failures", "errors", "skipped"]}
assert junit == {"tests": 98, "failures": 0, "errors": 0, "skipped": 0}
counter = next(c for c in ET.parse(ROOT / "coverage/jacocoTestReport.xml").getroot().findall("counter") if c.get("type") == "LINE")
covered, missed = int(counter.get("covered")), int(counter.get("missed"))
assert (covered, missed) == (1534, 169)
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

performance = ET.parse(ROOT / "junit/TEST-seoultech.se.tetris.component.PerformanceRegressionTest.xml").getroot().findtext("system-out", "")
m = re.search(r"operations=(\d+) elapsedMs=(\d+) budgetMs=(\d+)", performance)
assert m
operations, elapsed, budget = map(int, m.groups())
assert operations == 400000 and elapsed <= budget == 5000
result = (ROOT / "desktop/result.txt").read_text()
assert result.startswith("PASS ")
assert "OS=Mac OS X 26.6.2 arch=aarch64 Java=21.0.13" in result
assert "BUILD SUCCESSFUL" in (ROOT / "build.log").read_text()
manifest = json.loads((ROOT / "files.json").read_text())
for name, expected in manifest.items():
    assert sha256(ROOT / name) == expected, name
package = json.loads((ROOT / "package.json").read_text())
assert package["jar_matches_build"] and package["classes_match_build"] > 0
assert package["runtime_included"] and package["icon_included"] and package["launcher_included"]
summary = {
    "source": {"kind": "local", "tested_commit": meta["tested_commit"], "command": meta["command"], "source_files_verified": len(meta["source_files_sha256"])},
    "environment": meta["environment"],
    "junit": junit,
    "coverage": {"type": "LINE", "covered": covered, "missed": missed, "percent": round(covered/(covered+missed)*100, 2)},
    "distribution": {"blocks": {"samples_per_difficulty": 100000, "rows": len(blocks), "all_pass": True}, "items": {"samples":100000, "rows":len(items), "all_pass":True}, "maximum_relative_error_percent": max(max(float(r["relative_error_percent"]) for r in blocks), max(float(r["relative_error"])*100 for r in items))},
    "core_performance": {"operations":operations, "elapsed_ms":elapsed, "budget_ms":budget},
    "desktop_smoke":"PASS", "package":package, "preserved_files":manifest,
}
(ROOT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2)+"\n")
print(json.dumps({"result":"PASS", **{k:summary[k] for k in ["junit","coverage","distribution","core_performance","desktop_smoke"]}}, ensure_ascii=False, indent=2))
