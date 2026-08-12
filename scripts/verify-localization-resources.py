#!/usr/bin/env python3
"""Verify Chinese baseline and English resource key/format parity."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ARGUMENT_PATTERN = re.compile(r"%(\d+)\$[a-zA-Z%]")


def load_strings(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"missing string resource: {path}")
    root = ET.parse(path).getroot()
    values = {}
    for element in root.findall("string"):
        name = element.attrib.get("name")
        if name:
            values[name] = element.text or ""
    return values


def load_plurals(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        raise FileNotFoundError(f"missing plurals resource: {path}")
    root = ET.parse(path).getroot()
    values: dict[str, dict[str, str]] = {}
    for plurals in root.findall("plurals"):
        name = plurals.attrib.get("name")
        if not name:
            continue
        quantities = {
            item.attrib["quantity"]: item.text or ""
            for item in plurals.findall("item")
            if item.attrib.get("quantity")
        }
        values[name] = quantities
    return values


def format_arguments(text: str) -> tuple[int, ...]:
    return tuple(sorted(int(position) for position in ARGUMENT_PATTERN.findall(text)))


def check_strings(baseline: dict[str, str], english: dict[str, str], failures: list[str]) -> None:
    for name in sorted(set(baseline) - set(english)):
        failures.append(f"missing en string: {name}")
    for name in sorted(set(english) - set(baseline)):
        failures.append(f"missing baseline string: {name}")
    for name in sorted(set(baseline) & set(english)):
        if not english[name].strip():
            failures.append(f"empty en string: {name}")
        baseline_args = format_arguments(baseline[name])
        english_args = format_arguments(english[name])
        if baseline_args != english_args:
            failures.append(
                f"format argument mismatch for {name}: "
                f"baseline={baseline_args}, en={english_args}"
            )


def check_plurals(
    baseline: dict[str, dict[str, str]],
    english: dict[str, dict[str, str]],
    failures: list[str],
) -> None:
    for name in sorted(set(baseline) - set(english)):
        failures.append(f"missing en plurals: {name}")
    for name in sorted(set(english) - set(baseline)):
        failures.append(f"missing baseline plurals: {name}")
    for name in sorted(set(baseline) & set(english)):
        for quantity in sorted(set(baseline[name]) - set(english[name])):
            failures.append(f"missing en plurals quantity {quantity}: {name}")
        for quantity in sorted(set(english[name]) - set(baseline[name])):
            failures.append(f"missing baseline plurals quantity {quantity}: {name}")
        for quantity in sorted(set(baseline[name]) & set(english[name])):
            if not english[name][quantity].strip():
                failures.append(f"empty en plurals quantity {quantity}: {name}")
            baseline_args = format_arguments(baseline[name][quantity])
            english_args = format_arguments(english[name][quantity])
            if baseline_args != english_args:
                failures.append(
                    f"format argument mismatch for plurals {name}/{quantity}: "
                    f"baseline={baseline_args}, en={english_args}"
                )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--values", type=Path, default=Path("app/src/main/res/values/strings.xml"))
    parser.add_argument("--values-en", type=Path, default=Path("app/src/main/res/values-en/strings.xml"))
    parser.add_argument("--plurals", type=Path, default=Path("app/src/main/res/values/plurals.xml"))
    parser.add_argument("--plurals-en", type=Path, default=Path("app/src/main/res/values-en/plurals.xml"))
    args = parser.parse_args(argv)

    failures: list[str] = []
    try:
        baseline = load_strings(args.values)
        english = load_strings(args.values_en)
        baseline_plurals = load_plurals(args.plurals) if args.plurals.is_file() else {}
        english_plurals = load_plurals(args.plurals_en) if args.plurals_en.is_file() else {}
    except (FileNotFoundError, ET.ParseError) as exc:
        print(f"localization resource error: {exc}", file=sys.stderr)
        return 1

    check_strings(baseline, english, failures)
    check_plurals(baseline_plurals, english_plurals, failures)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Localization resources: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
