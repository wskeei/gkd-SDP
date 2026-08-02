#!/usr/bin/env python3
"""Fail when a Gradle or GitHub SBOM report contains vulnerable build dependencies."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FLOORS = ROOT / "gradle" / "security-dependency-floors.properties"
COORDINATE_RE = re.compile(
    r"(?P<group>[A-Za-z0-9_.-]+):(?P<name>[A-Za-z0-9_.-]+):(?P<version>[0-9][A-Za-z0-9.+_-]*)"
)
NETTY_41_PREFIX = "4.1."
AGP_GROUP = "com.android.tools.build"
AGP_NAME = "gradle"
BOUNCY_MODULES = {"bcpkix-jdk18on", "bcprov-jdk18on", "bcutil-jdk18on"}


def load_floors(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith(("#", ";")):
            continue
        if "=" not in line:
            raise ValueError(f"invalid floor entry at {path}:{line_number}")
        key, value = (part.strip() for part in line.split("=", 1))
        if not key or not value:
            raise ValueError(f"invalid floor entry at {path}:{line_number}")
        values[key] = value
    required = {
        "netty4_1",
        "commonsLang3",
        "httpcomponents4_5",
        "jose4j",
        "bouncycastleJdk18on",
        "jdom2",
    }
    missing = sorted(required - values.keys())
    if missing:
        raise ValueError(f"missing floor entries: {', '.join(missing)}")
    return values


def numeric_version(version: str) -> tuple[int, ...]:
    match = re.match(r"^\d+(?:\.\d+)*", version)
    parts = tuple(int(part) for part in match.group(0).split(".")) if match else ()
    if not parts:
        raise ValueError(f"version has no numeric components: {version}")
    return parts


def is_below(actual: str, floor: str) -> bool:
    actual_parts = numeric_version(actual)
    floor_parts = numeric_version(floor)
    width = max(len(actual_parts), len(floor_parts))
    padded_actual = actual_parts + (0,) * (width - len(actual_parts))
    padded_floor = floor_parts + (0,) * (width - len(floor_parts))
    if padded_actual != padded_floor:
        return padded_actual < padded_floor
    # A qualifier at the floor's numeric version is not the released floor.
    # Treat snapshots, betas, and other non-canonical spellings as below it.
    return actual != floor


def floor_for(group: str, name: str, version: str, floors: dict[str, str]) -> str | None:
    if group == "io.netty":
        # Leave netty-tcnative (2.x) and future 4.2.x releases alone.
        if version.startswith(NETTY_41_PREFIX):
            return floors["netty4_1"]
        return None
    if group == "org.bouncycastle" and name in BOUNCY_MODULES:
        return floors["bouncycastleJdk18on"]
    if group == "org.apache.commons" and name == "commons-lang3":
        return floors["commonsLang3"]
    if group == "org.apache.httpcomponents" and name in {"httpclient", "httpmime"}:
        return floors["httpcomponents4_5"]
    if group == "org.bitbucket.b_c" and name == "jose4j":
        return floors["jose4j"]
    if group == "org.jdom" and name == "jdom2":
        return floors["jdom2"]
    return None


def parse_text_report(path: Path) -> tuple[dict[str, set[str]], list[str]]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    if "SECURITY_DEPENDENCY_REPORT_COMPLETE=1" not in text:
        errors.append("missing SECURITY_DEPENDENCY_REPORT_COMPLETE=1")
    versions: dict[str, set[str]] = {}
    for line in text.splitlines():
        matches = list(COORDINATE_RE.finditer(line))
        for match in matches:
            group = match.group("group")
            name = match.group("name")
            version = match.group("version")
            # Gradle's dependencies task prints requested -> resolved versions.
            # The version after the final arrow is the one actually resolved.
            remainder = line[match.end() :]
            arrows = re.findall(r"->\s*([0-9][A-Za-z0-9.+_-]*)", remainder)
            if arrows:
                version = arrows[-1]
            coordinate = f"{group}:{name}"
            versions.setdefault(coordinate, set()).add(version)
    return versions, errors


def parse_sbom(path: Path) -> tuple[dict[str, set[str]], list[str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    packages = payload.get("sbom", {}).get("packages")
    if not isinstance(packages, list):
        return {}, ["SBOM does not contain sbom.packages"]
    versions: dict[str, set[str]] = {}
    errors: list[str] = []
    for package in packages:
        if not isinstance(package, dict):
            continue
        coordinate = package.get("name")
        version = package.get("versionInfo")
        if not isinstance(coordinate, str) or not isinstance(version, str):
            continue
        if ":" not in coordinate:
            continue
        versions.setdefault(coordinate, set()).add(version)
    return versions, errors


def audit(versions: dict[str, set[str]], errors: list[str], floors: dict[str, str]) -> list[str]:
    agp_coordinate = f"{AGP_GROUP}:{AGP_NAME}"
    if agp_coordinate not in versions:
        errors.append(f"missing Gradle/AGP sentinel coordinate: {agp_coordinate}")
    failures = list(errors)
    for coordinate, detected_versions in sorted(versions.items()):
        if ":" not in coordinate:
            continue
        group, name = coordinate.split(":", 1)
        for version in sorted(detected_versions):
            floor = floor_for(group, name, version, floors)
            if floor is None:
                continue
            try:
                below = is_below(version, floor)
            except ValueError as error:
                failures.append(f"{coordinate}:{version}: cannot compare version ({error})")
                continue
            if below:
                failures.append(f"{coordinate}:{version} is below security floor {floor}")
    return failures


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--report", type=Path, help="Gradle text dependency report")
    source.add_argument("--sbom", type=Path, help="GitHub SPDX SBOM JSON")
    parser.add_argument("--floors", type=Path, default=DEFAULT_FLOORS)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        floors = load_floors(args.floors)
        if args.report is not None:
            versions, errors = parse_text_report(args.report)
            source_name = args.report
        else:
            versions, errors = parse_sbom(args.sbom)
            source_name = args.sbom
        failures = audit(versions, errors, floors)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"security dependency audit input error: {error}", file=sys.stderr)
        return 2

    if failures:
        print(f"security dependency audit failed for {source_name}:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    checked = sorted(
        coordinate
        for coordinate in versions
        if floor_for(*coordinate.split(":", 1), next(iter(versions[coordinate])), floors)
    )
    print(f"security dependency audit passed for {source_name}")
    print(f"checked coordinates: {', '.join(checked) if checked else 'none present'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
