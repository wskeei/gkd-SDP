#!/usr/bin/env python3
"""Verify Kover XML report against the single version-controlled scope."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def load_patterns(path: Path) -> list[str]:
    if not path.is_file():
        raise FileNotFoundError(f"missing pattern file: {path}")
    patterns = [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if len(patterns) != len(set(patterns)):
        raise ValueError(f"duplicate pattern in {path}")
    if not patterns:
        raise ValueError(f"empty pattern file: {path}")
    return patterns


def pattern_regex(pattern: str) -> re.Pattern[str]:
    out: list[str] = []
    i = 0
    while i < len(pattern):
        if pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^.]*")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("^" + "".join(out) + "$")


def parse_report(path: Path) -> tuple[list[dict[str, object]], int, int]:
    root = ET.parse(path).getroot()
    classes: list[dict[str, object]] = []
    for package in root.iter("package"):
        package_name = package.attrib.get("name", "").replace("/", ".")
        for cls in package.iter("class"):
            class_name = cls.attrib.get("name", "").replace("/", ".")
            fully_qualified = (
                class_name
                if package_name and class_name.startswith(package_name)
                else f"{package_name}.{class_name}" if package_name else class_name
            )
            counters = {}
            for counter in cls.findall("counter"):
                counter_type = counter.attrib.get("type", "")
                missed = int(counter.attrib.get("missed", "0"))
                covered = int(counter.attrib.get("covered", "0"))
                counters[counter_type] = (missed, covered)
            classes.append(
                {
                    "name": fully_qualified,
                    "counters": counters,
                }
            )
    return classes


def coverage(counters: dict[str, tuple[int, int]], counter_type: str) -> float:
    missed, covered = counters.get(counter_type, (0, 0))
    total = missed + covered
    if total == 0:
        return 100.0
    return covered / total * 100.0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", required=True, type=Path)
    parser.add_argument("--includes", required=True, type=Path)
    parser.add_argument("--excludes", required=True, type=Path)
    parser.add_argument("--min-line", type=float, default=80.0)
    parser.add_argument("--min-branch", type=float, default=70.0)
    args = parser.parse_args(argv)

    failures: list[str] = []
    try:
        includes = load_patterns(args.includes)
        excludes = load_patterns(args.excludes)
        classes = parse_report(args.xml)
    except (FileNotFoundError, ValueError, ET.ParseError) as exc:
        print(f"Kover report error: {exc}", file=sys.stderr)
        return 1

    for broad in ("li.songe.gkd.sdp.**", "li.songe.gkd.sdp.*", "li.songe.gkd.sdp.ui.**"):
        if broad in excludes:
            failures.append(f"Kover exclude is too broad and hides required classes: {broad}")

    include_regexes = [(pattern, pattern_regex(pattern)) for pattern in includes]
    exclude_regexes = [(pattern, pattern_regex(pattern)) for pattern in excludes]
    included: list[dict[str, object]] = []
    for cls in classes:
        name = str(cls["name"])
        matched = any(regex.fullmatch(name) for _, regex in include_regexes)
        excluded = any(regex.fullmatch(name) for _, regex in exclude_regexes)
        if matched and not name.endswith("Kt"):
            if excluded:
                failures.append(f"included class is also excluded: {name}")
            else:
                included.append(cls)

    for pattern, regex in include_regexes:
        if not any(regex.fullmatch(str(cls["name"])) for cls in classes):
            failures.append(f"Kover include pattern matches no report class: {pattern}")

    total_line_missed = 0
    total_line_covered = 0
    total_branch_missed = 0
    total_branch_covered = 0
    for cls in included:
        counters = dict(cls["counters"])
        missed, covered = counters.get("LINE", (0, 0))
        total_line_missed += missed
        total_line_covered += covered
        missed, covered = counters.get("BRANCH", (0, 0))
        total_branch_missed += missed
        total_branch_covered += covered
        if coverage(counters, "LINE") == 0.0 and "$" not in str(cls["name"]):
            failures.append(f"included class has 0% line coverage: {cls['name']}")

    line_total = total_line_missed + total_line_covered
    branch_total = total_branch_missed + total_branch_covered
    line_percent = (
        total_line_covered / line_total * 100.0 if line_total else 0.0
    )
    branch_percent = (
        total_branch_covered / branch_total * 100.0 if branch_total else 0.0
    )
    if line_percent < args.min_line:
        failures.append(f"Kover line coverage {line_percent:.2f}% below {args.min_line:.2f}%")
    if branch_percent < args.min_branch:
        failures.append(f"Kover branch coverage {branch_percent:.2f}% below {args.min_branch:.2f}%")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(
        f"Kover policy: OK (line {line_percent:.2f}%, branch {branch_percent:.2f}%)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
