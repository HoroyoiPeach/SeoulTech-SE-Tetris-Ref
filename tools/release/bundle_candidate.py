"""Assemble an evaluator ZIP from one successful Java CI push and its Git tree."""

import argparse
import hashlib
import json
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
    suites = [ElementTree.parse(p).getroot() for p in directory.glob("test-results/test/TEST-*.xml")]
    totals = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ("tests", "failures", "errors", "skipped")}
    if not totals["tests"] or totals["failures"] or totals["errors"]:
        raise ValueError(f"Missing or failed JUnit results in {directory.name}: {totals}")
    xml = ElementTree.parse(directory / "reports/jacoco/test/jacocoTestReport.xml")
    lines = xml.getroot().find("counter[@type='LINE']")
    covered, missed = int(lines.attrib["covered"]), int(lines.attrib["missed"])
    if covered + missed == 0 or covered * 100 < 70 * (covered + missed):
        raise ValueError(f"Coverage below 70% in {directory.name}")
    if not (directory / "reports/desktop/result.txt").is_file():
        raise ValueError(f"Missing desktop smoke report in {directory.name}")
    return {**totals, "lines_covered": covered, "lines_missed": missed}


def build_candidate(run_id, output):
    run = json.loads(gh("run", "view", str(run_id), "--json",
                        "url,workflowName,event,conclusion,headSha,headBranch,jobs"))
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
        reports = {os_name: read_reports(artifacts / f"test-reports-{os_name}") for os_name in PLATFORMS}
        manifest = {"commit": commit, "branch": run["headBranch"], "ci": run["url"],
                    "app": package.name, "app_sha256": digest, "checks": reports}
        target = output / f"seoultech-tetris-r3-{commit[:12]}-{run_id}-macos-aarch64.zip"
        staged_zip = staging / target.name
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
        checksum = staging / (target.name + ".sha256")
        checksum.write_text(f"{sha256(staged_zip)}  {target.name}\n", encoding="ascii")
        staged_zip.replace(target)
        checksum.replace(output / checksum.name)
        print(target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_id", type=int)
    parser.add_argument("--output", type=Path, default=Path("build/final-candidate"))
    args = parser.parse_args()
    build_candidate(args.run_id, args.output.resolve())
