#!/usr/bin/env python3
"""Verify default/English Android resource key and format parity."""

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
    return {
        element.attrib["name"]: element.text or ""
        for element in root.findall("string")
        if element.attrib.get("name")
    }


def load_plurals(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        raise FileNotFoundError(f"missing plurals resource: {path}")
    root = ET.parse(path).getroot()
    values: dict[str, dict[str, str]] = {}
    for plurals in root.findall("plurals"):
        name = plurals.attrib.get("name")
        if name:
            values[name] = {
                item.attrib["quantity"]: item.text or ""
                for item in plurals.findall("item")
                if item.attrib.get("quantity")
            }
    return values


def format_arguments(text: str) -> tuple[int, ...]:
    return tuple(sorted(int(position) for position in ARGUMENT_PATTERN.findall(text)))


def check_strings(default: dict[str, str], english: dict[str, str], failures: list[str]) -> None:
    for name in sorted(set(default) - set(english)):
        failures.append(f"missing en string: {name}")
    for name in sorted(set(english) - set(default)):
        failures.append(f"missing default string: {name}")
    for name in sorted(set(default) & set(english)):
        if not english[name].strip():
            failures.append(f"empty en string: {name}")
        if format_arguments(default[name]) != format_arguments(english[name]):
            failures.append(f"format argument mismatch for {name}")


def check_plurals(
    default: dict[str, dict[str, str]],
    english: dict[str, dict[str, str]],
    failures: list[str],
) -> None:
    for name in sorted(set(default) - set(english)):
        failures.append(f"missing en plurals: {name}")
    for name in sorted(set(english) - set(default)):
        failures.append(f"missing default plurals: {name}")
    for name in sorted(set(default) & set(english)):
        for quantity in sorted(set(default[name]) - set(english[name])):
            failures.append(f"missing en plurals quantity {quantity}: {name}")
        for quantity in sorted(set(english[name]) - set(default[name])):
            failures.append(f"missing default plurals quantity {quantity}: {name}")
        for quantity in sorted(set(default[name]) & set(english[name])):
            if not english[name][quantity].strip():
                failures.append(f"empty en plurals quantity {quantity}: {name}")
            if format_arguments(default[name][quantity]) != format_arguments(english[name][quantity]):
                failures.append(f"format argument mismatch for plurals {name}/{quantity}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--values", type=Path, default=Path("app/src/main/res/values/strings.xml"))
    parser.add_argument("--values-en", type=Path, default=Path("app/src/main/res/values-en/strings.xml"))
    parser.add_argument("--plurals", type=Path, default=Path("app/src/main/res/values/plurals.xml"))
    parser.add_argument("--plurals-en", type=Path, default=Path("app/src/main/res/values-en/plurals.xml"))
    args = parser.parse_args(argv)
    failures: list[str] = []
    try:
        default = load_strings(args.values)
        english = load_strings(args.values_en)
        default_plurals = load_plurals(args.plurals) if args.plurals.is_file() else {}
        english_plurals = load_plurals(args.plurals_en) if args.plurals_en.is_file() else {}
    except (FileNotFoundError, ET.ParseError) as exc:
        print(f"localization resource error: {exc}", file=sys.stderr)
        return 1
    check_strings(default, english, failures)
    check_plurals(default_plurals, english_plurals, failures)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Localization resources: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
