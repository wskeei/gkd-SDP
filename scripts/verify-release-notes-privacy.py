#!/usr/bin/env python3
"""Reject sensitive payloads from public release notes."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
import sys


FORBIDDEN = [
    re.compile(r"(?i)\b(bearer|basic)\s+[a-z0-9+/=._-]{12,}\b"),
    re.compile(r"(?i)\bauthorization\s*[:=]"),
    re.compile(r"(?i)\bcookie\s*[:=]"),
    re.compile(r"(?i)\b(?:/Users|/home|/data/user)/[^\s]+"),
    re.compile(r"(?i)\b(?:申请理由|理由样例|reason\s+example)\b"),
    re.compile(r"(?i)\b(?:rule\s+pattern|selector\s+text|node\s+text|联系人|device\s+id)\b"),
    re.compile(r"(?i)\bhttps?://(?!github\.com/wskeei/gkd-SDP|developer\.android\.com|gkd\.li)\S+"),
]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("file", type=Path)
    args = parser.parse_args(argv)
    text = args.file.read_text(encoding="utf-8")
    failures = [pattern.pattern for pattern in FORBIDDEN if pattern.search(text)]
    if failures:
        print("\n".join(f"forbidden release-note content: {item}" for item in failures), file=sys.stderr)
        return 1
    print("Release notes privacy policy: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
