"""Assemble an evaluator ZIP from one successful Java CI push and its Git tree."""

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
from xml.etree import ElementTree
import zipfile


REPOSITORY = "HoroyoiPeach/SeoulTech-SE-Tetris-Ref"
PLATFORMS = ("macos-26", "windows-2025")


def gh(*args):
    return subprocess.check_output(["gh", *args, "--repo", REPOSITORY], text=True)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_reports(directory):
    report_dir = directory / "reports"
    suites = [ElementTree.parse(p).getroot() for p in directory.glob("test-results/test/TEST-*.xml")]
    totals = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ("tests", "failures", "errors", "skipped")}
    if totals["tests"] == 0 or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise ValueError(f"Missing, failed or skipped JUnit tests in {directory.name}: {totals}")
    index = ElementTree.parse(report_dir / "tests/test/index.html")
    expected = {key: totals[key] for key in ("tests", "failures", "skipped")}
    counters = {}
    for box in index.findall(".//div[@class='summary']//div[@class='counter']/.."):
        label = box.findtext("p")
        if label in expected:
            counters[label] = int(box.findtext("div[@class='counter']"))
    if counters != expected:
        raise ValueError(f"Gradle HTML and JUnit XML totals differ in {directory.name}")

    jacoco = report_dir / "jacoco/test"
    xml = ElementTree.parse(jacoco / "jacocoTestReport.xml")
    lines = xml.getroot().find("counter[@type='LINE']")
    if lines is None:
        raise ValueError(f"Missing JaCoCo line totals in {directory.name}")
    covered, missed = int(lines.attrib["covered"]), int(lines.attrib["missed"])
    if min(covered, missed) < 0 or covered + missed == 0 or covered * 100 < 70 * (covered + missed):
        raise ValueError(f"Coverage below 70% in {directory.name}")
    html = ElementTree.parse(jacoco / "html/index.html")
    table = html.find(".//{*}table[@id='coveragetable']")
    if table is None:
        raise ValueError(f"Missing JaCoCo HTML coverage table in {directory.name}")
    headers = [cell.text for cell in table.findall("{*}thead/{*}tr/{*}td")]
    footer = table.findall("{*}tfoot/{*}tr/{*}td")
    if "Lines" not in headers or len(footer) != len(headers):
        raise ValueError(f"Missing JaCoCo HTML line totals in {directory.name}")
    column = headers.index("Lines")
    html_counts = [int(cell.text.replace(",", "")) for cell in footer[column - 1:column + 1]]
    if headers[column - 1] != "Missed" or html_counts != [missed, covered + missed]:
        raise ValueError(f"JaCoCo HTML and XML line totals differ in {directory.name}")

    if not (report_dir / "desktop/result.txt").read_text(encoding="utf-8").startswith("PASS "):
        raise ValueError(f"Missing desktop smoke PASS in {directory.name}")
    blocks = {(difficulty, shape + "Block")
              for difficulty in ("Easy", "Normal", "Hard") for shape in "IJLOSTZ"}
    check_distribution(report_dir / "req2/block-distribution.csv", ("difficulty", "block"), blocks)
    check_distribution(report_dir / "req2/item-distribution.csv", ("label",), {(label,) for label in "LOCBP"})
    return {**totals, "lines_covered": covered, "lines_missed": missed}


def check_distribution(path, columns, expected):
    with path.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    recorded = {tuple(row.get(column) for column in columns) for row in rows}
    if recorded != expected or len(rows) != len(expected) or any(row.get("pass") != "true" for row in rows):
        raise ValueError(f"Missing or failed distribution results in {path.name}")
    # The Java checks draw 100,000 times per difficulty and for the item pool.
    groups = {}
    for row in rows:
        samples, count = int(row["samples"]), int(row["count"])
        probability = float(row.get("expected_probability") or row["expected"])
        if not (samples >= 100_000 and 0 < count <= samples and 0 < probability <= 1):
            raise ValueError(f"Insufficient samples or out-of-range distribution in {path.name}")
        observed = count / samples
        relative_error = abs(observed / probability - 1)
        reported_observed = float(row.get("observed_probability") or row["observed"])
        reported_error = (float(row["relative_error_percent"]) / 100 if "relative_error_percent" in row
                          else float(row["relative_error"]))
        # The block CSV rounds probabilities to eight decimals and percent errors to four.
        if (relative_error > 0.05 + 1e-7 or not math.isclose(observed, reported_observed, abs_tol=1e-8)
                or not math.isclose(relative_error, reported_error, abs_tol=1e-6)):
            raise ValueError(f"Inconsistent distribution probabilities in {path.name}")
        groups.setdefault(row.get("difficulty"), []).append((samples, count))
    for group in groups.values():
        total = sum(count for _, count in group)
        if any(samples != total for samples, _ in group):
            raise ValueError(f"Distribution counts do not add up in {path.name}")


def check_app_archive(package):
    contents = "SeoulTech Tetris.app/Contents/"
    required = ("Info.plist", "MacOS/SeoulTech Tetris", "app/SeoulTech Tetris.cfg",
                "app/seoultech-tetris.jar", "runtime/Contents/Home/release",
                "runtime/Contents/Home/lib/modules")
    with zipfile.ZipFile(package) as app:
        entries = set(app.namelist())
        if any(contents + name not in entries or app.getinfo(contents + name).file_size == 0
               for name in required):
            raise ValueError("Incomplete macOS app, launcher, game JAR or bundled runtime")
        launcher = app.getinfo(contents + "MacOS/SeoulTech Tetris")
        if not (launcher.external_attr >> 16) & 0o111:
            raise ValueError("The macOS launcher has no executable permission")
        bad_entry = app.testzip()
        if bad_entry:
            raise ValueError(f"Corrupt macOS ZIP entry: {bad_entry}")


def publish_candidate(directory, destination, filename, manifest):
    # A directory rename exposes the finished ZIP and its checksum together.
    try:
        directory.rename(destination)
    except OSError:
        if not destination.is_dir():
            raise
        previous = destination / filename
        checksum = (destination / (filename + ".sha256")).read_text(encoding="ascii").split()
        if checksum != [sha256(previous), filename]:
            raise ValueError(f"Checksum mismatch in existing candidate: {destination}")
        with zipfile.ZipFile(previous) as bundle:
            if json.loads(bundle.read("manifest.json")) != manifest:
                raise ValueError(f"Existing candidate has different inputs: {destination}")
    return destination / filename


def build_candidate(run_id, output):
    run = json.loads(gh("run", "view", str(run_id), "--json",
                        "url,workflowName,event,conclusion,headSha,headBranch,jobs,attempt"))
    if (run["workflowName"], run["event"], run["conclusion"]) != ("Java CI", "push", "success"):
        raise ValueError("Choose a successful Java CI push run for the candidate commit")
    jobs = {job["name"]: job["conclusion"] for job in run["jobs"]}
    if any(jobs.get(f"Build and test ({os_name})") != "success" for os_name in PLATFORMS):
        raise ValueError("Both Windows and macOS build jobs must have succeeded")
    commit = run["headSha"]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != commit:
        raise ValueError(f"Checked out {head}, but CI tested {commit}")

    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output) as temp:
        staging = Path(temp)
        artifacts = staging / "artifacts"
        names = ["package-macos-26", *(f"test-reports-{os_name}" for os_name in PLATFORMS)]
        gh("run", "download", str(run_id), "--dir", str(artifacts),
           *(arg for name in names for arg in ("--name", name)))
        package, = (artifacts / "package-macos-26").glob("*-macos-aarch64.zip")
        digest = sha256(package)
        if Path(str(package) + ".sha256").read_text().split() != [digest, package.name]:
            raise ValueError("The macOS ZIP differs from its CI checksum")
        check_app_archive(package)
        reports = {os_name: read_reports(artifacts / f"test-reports-{os_name}") for os_name in PLATFORMS}
        attempt = run["attempt"]
        latest = json.loads(gh("run", "view", str(run_id), "--json", "attempt,conclusion"))
        if latest["attempt"] != attempt or latest["conclusion"] != "success":
            raise ValueError("The CI run changed while downloading its artifacts. Retry this run.")
        manifest = {"commit": commit, "branch": run["headBranch"], "ci": run["url"], "attempt": attempt,
                    "app": package.name, "app_sha256": digest, "checks": reports}
        name = f"seoultech-tetris-r3-{commit[:12]}-{run_id}-a{attempt}-macos-aarch64.zip"
        ready = staging / "ready"
        ready.mkdir()
        staged_zip = ready / name
        subprocess.run(["git", "archive", "--format=zip", "--prefix=project/",
                        "-o", str(staged_zip), commit], check=True)
        with zipfile.ZipFile(staged_zip, "a", compression=zipfile.ZIP_DEFLATED) as bundle:
            bundle.write(package, f"app/{package.name}", compress_type=zipfile.ZIP_STORED)
            bundle.writestr("manifest.json", json.dumps(manifest, indent=2) + "\n")
            for os_name in PLATFORMS:
                report_dir = artifacts / f"test-reports-{os_name}"
                for path in sorted(report_dir.rglob("*")):
                    if path.is_file():
                        bundle.write(path, f"checks/{os_name}/{path.relative_to(report_dir).as_posix()}")
        (ready / (name + ".sha256")).write_text(f"{sha256(staged_zip)}  {name}\n", encoding="ascii")
        target = publish_candidate(ready, output / f"{commit[:12]}-{run_id}-a{attempt}", name, manifest)
        print(target)
        return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_id", type=int)
    parser.add_argument("--output", type=Path, default=Path("build/final-candidate"))
    args = parser.parse_args()
    build_candidate(args.run_id, args.output.resolve())
