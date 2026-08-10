#!/usr/bin/env python3
"""Migrate hardcoded user-visible string literals into Android resources.

Scans app/src/main/kotlin for string literals passed to known UI calls
(Text/Button/Toast/Notification/contentDescription/...), replaces them with
stringResource(R.string.<key>) / app.getString(R.string.<key>) calls and
writes the zh baseline into app/src/main/res/values/strings.xml.

Usage:
  python3 scripts/migrate-hardcoded-text.py --dry-run [--dir <dir>]
  python3 scripts/migrate-hardcoded-text.py [--dir <dir>]
  python3 scripts/migrate-hardcoded-text.py --verify
"""

from __future__ import annotations

import hashlib
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "kotlin"
RES_VALUES = ROOT / "app" / "src" / "main" / "res" / "values"

POSITIONAL_CALLS = {
    "Text", "Button", "OutlinedButton", "TextButton", "FilledTonalButton",
    "Snackbar", "toast",
}
TOAST_INDEX_ONE = {"Toast"}
NAMED_ARG_NAMES = {
    "text", "title", "subtitle", "message", "contentDescription",
    "onClickLabel", "label", "placeholder", "supportingText", "errorText",
}

CALL_START = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*$")
NAMED_ASSIGN = re.compile(r"(?<![=!<>])([A-Za-z_][A-Za-z0-9_]*)\s*=")
TEMPLATE_ARG = re.compile(r"\$\{([^}]+)\}|\$([A-Za-z_][A-Za-z0-9_]*)")
COMPOSABLE_MARK = re.compile(r"@\s*Composable")
FUN_START = re.compile(r"fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")
CONCAT_BINARY = re.compile(r"^\s*([\"']?[^\"']*?)\s*\+\s*(.+)$", re.S)


class Literal:
    __slots__ = ("start", "end", "raw", "line")

    def __init__(self, start, end, raw, line):
        self.start = start
        self.end = end
        self.raw = raw
        self.line = line

    @property
    def is_template(self) -> bool:
        return "${" in self.raw or re.search(r"(?<!\\)\$[A-Za-z_]", self.raw) is not None


def strip_comments(text: str) -> str:
    out = list(text)
    i, n = 0, len(text)
    in_str = False
    while i < n:
        c = text[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            if j == -1:
                j = n
            for k in range(i, j):
                out[k] = " "
            i = j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j == -1:
                j = n - 1
            for k in range(i, j + 2):
                out[k] = " "
            i = j + 2
            continue
        i += 1
    return "".join(out)


def find_literals(text: str) -> list[Literal]:
    literals = []
    i, n = 0, len(text)
    line = 1
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c != '"':
            i += 1
            continue
        start = i
        j = i + 1
        while j < n:
            if text[j] == "\\":
                j += 2
                continue
            if text[j] == '"':
                break
            j += 1
        if j >= n:
            break
        raw = text[start + 1:j]
        literals.append(Literal(start, j + 1, raw, line))
        i = j + 1
    return literals


def enclosing_call(masked: str, lit: Literal) -> tuple[str, str | None, int] | None:
    """Return (call_name, named_arg_name, positional_index) for the literal."""
    depth = 0
    paren = -1
    for i in range(lit.start - 1, -1, -1):
        ch = masked[i]
        if ch == ")":
            depth += 1
        elif ch == "(":
            if depth == 0:
                paren = i
                break
            depth -= 1
    if paren < 0:
        return None
    call = CALL_START.search(masked[max(0, paren - 40):paren + 1])
    if not call:
        return None
    name = call.group(1)
    between = masked[paren + 1: lit.start]
    if ";" in between or "\n\n" in between:
        return None
    named_matches = list(NAMED_ASSIGN.finditer(between))
    if named_matches:
        arg_name = named_matches[-1].group(1)
        if arg_name in NAMED_ARG_NAMES:
            return name, arg_name, -1
        return None
    idx = 0
    d = 0
    in_str = False
    for i in range(paren + 1, lit.start):
        ch = masked[i]
        if in_str:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch in "([{":
            d += 1
        elif ch in ")]}":
            d -= 1
        elif ch == "," and d == 0:
            idx += 1
    if name in POSITIONAL_CALLS and idx == 0:
        return name, None, idx
    if name in TOAST_INDEX_ONE and idx == 1:
        return name, None, idx
    return None


# Calls whose trailing/content lambdas run in a composable context.
COMPOSABLE_CONTENT_CALLS = {
    "Text", "Button", "OutlinedButton", "TextButton", "FilledTonalButton",
    "IconButton", "Surface", "Card", "ElevatedCard", "Column", "Row", "Box",
    "LazyColumn", "LazyRow", "FlowRow", "AlertDialog", "Snackbar",
    "FilterChip", "AssistChip", "SuggestionChip", "Tab", "DropdownMenuItem",
    "TextField", "OutlinedTextField", "Scaffold", "Menu",
    "AnimatedVisibility", "AnimatedContent", "FlowRow",
}
COMPOSABLE_NAMED_LAMBDAS = {
    "content", "label", "text", "title", "subtitle", "trailingIcon",
    "leadingIcon", "action", "headlineContent", "supportingContent",
    "confirmButton", "dismissButton", "button", "icon",
}


def owning_named_argument(masked: str, text: str, lit: Literal) -> str | None:
    """Find the named argument owning this literal for if/when values.

    Only fires for `name = if (...)` / `name = when (...)` values: the
    segment from the assignment to the literal must start with if/when and
    stay balanced, so plain declarations, intent extra keys and protocol
    constants are never touched.
    """
    head = masked[: lit.start]
    for match in reversed(list(NAMED_ASSIGN.finditer(head))):
        name = match.group(1)
        if name not in NAMED_ARG_NAMES:
            continue
        line_start = text.rfind("\n", 0, match.start()) + 1
        line = text[line_start:match.start()]
        if re.match(r"^\s*(private|internal|public|protected)?\s*const\s+val\b", line):
            continue
        segment = masked[match.end(): lit.start]
        if ";" in segment or "\n\n" in segment:
            continue
        if not re.match(r"^\s*(if|when)\b", segment):
            continue
        if not segment_balanced(segment):
            continue
        return name
    return None


def segment_balanced(segment: str) -> bool:
    """True when parens/brackets inside the segment close before its end."""
    d = 0
    in_str = False
    for ch in segment:
        if in_str:
            if ch == "\\":
                continue
            if ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch in "([{":
            d += 1
        elif ch in ")]}":
            d -= 1
            if d < 0:
                return False
    return d == 0


def has_top_level_comma(segment: str) -> bool:
    d = 0
    in_str = False
    for ch in segment:
        if in_str:
            if ch == "\\":
                continue
            if ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch in "([{":
            d += 1
        elif ch in ")]}":
            d -= 1
        elif ch == "," and d == 0:
            return True
    return False


def is_composable_context(masked: str, lit: Literal) -> bool:
    """True if the literal runs in a @Composable context.

    Walks the brace stack between the enclosing function body and the literal:
    direct function bodies and lambdas passed as composable content stay
    composable; plain lambdas (onClick, builders) are not.
    """
    head = masked[: lit.start]
    matches = list(FUN_START.finditer(head))
    if not matches:
        return False
    fun_match = matches[-1]
    fun_start = fun_match.start()
    if COMPOSABLE_MARK.search(head[:fun_start]) is None:
        return False
    body_open = masked.find("{", fun_match.end(), lit.start)
    if body_open < 0:
        return False
    # A fully balanced brace stack: every { pushes (blocks push None as a
    # neutral marker), every } pops. Lambdas are classified; control blocks
    # stay neutral so an if/else inside a lambda cannot escape it.
    stack: list[bool | None] = []
    i = body_open + 1
    in_str = False
    while i < lit.start:
        ch = masked[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue
        if ch == '"':
            in_str = True
            i += 1
            continue
        if ch == "{":
            stack.append(classify_lambda(masked, i))
            i += 1
            continue
        if ch == "}":
            if stack:
                stack.pop()
            i += 1
            continue
        i += 1
    return all(k is not False for k in stack)


BLOCK_KEYWORDS = {
    "else", "try", "do", "finally", "catch", "init", "get", "set",
    "companion", "object", "when", "if", "for", "while", "synchronized",
}


def enclosing_call_of(masked: str, pos: int) -> re.Match | None:
    """Call whose '(' is the last unclosed paren before [pos]."""
    depth = 0
    for i in range(pos - 1, -1, -1):
        ch = masked[i]
        if ch == ")":
            depth += 1
        elif ch == "(":
            if depth == 0:
                return CALL_START.search(masked[max(0, i - 40):i + 1])
            depth -= 1
    return None


def classify_lambda(masked: str, brace: int) -> bool | None:
    """Classify the brace at [brace].

    True: composable content lambda. False: plain lambda (onClick, builder,
    throttle). None: a plain code block (if/else/try/when) that inherits the
    context.
    """
    head = masked[max(0, brace - 120):brace].rstrip()
    if head.endswith(")"):
        # if (...)/for (...)/when (...) control blocks
        return None
    call = CALL_START.search(head)
    if call:
        name = call.group(1)
        if name in COMPOSABLE_CONTENT_CALLS:
            return True
        return False
    named = list(NAMED_ASSIGN.finditer(head))
    if named:
        arg_name = named[-1].group(1)
        named_abs = brace - len(head) + named[-1].start()
        call2 = enclosing_call_of(masked, named_abs)
        if (
            call2
            and call2.group(1) in COMPOSABLE_CONTENT_CALLS
            and arg_name in COMPOSABLE_NAMED_LAMBDAS
        ):
            return True
        return False
    tail_word = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*$", head)
    if tail_word and tail_word.group(1) not in BLOCK_KEYWORDS:
        # trailing lambda of a call written without parens, e.g. throttle {}
        return tail_word.group(1) in COMPOSABLE_CONTENT_CALLS
    return None


def decode_template(raw: str) -> tuple[str, list[str]]:
    args = []
    def repl(m):
        expr = (m.group(1) or m.group(2)).strip()
        args.append(expr)
        return "{%d}" % len(args)
    fmt = TEMPLATE_ARG.sub(repl, raw)
    fmt = fmt.replace("%", "%%")
    fmt = re.sub(r"\{(\d+)\}", r"%\1$s", fmt)
    fmt = fmt.replace("\\$", "$")
    fmt = fmt.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
    return fmt, args


def decode_concat(between: str) -> tuple[str, list[str]] | None:
    """Text('a' + expr) -> ('a', [expr]) as format string."""
    parts = re.split(r"\s*\+\s*", between.strip(), maxsplit=1)
    if len(parts) != 2:
        return None
    left = parts[0].strip()
    if not (left.startswith('"') and left.endswith('"')):
        return None
    raw = left[1:-1]
    fmt = raw.replace("%", "%%")
    return fmt, [parts[1].strip()]


def escape_xml(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "\\'")
    )


def key_for(literal: str) -> str:
    return "s_" + hashlib.sha1(literal.encode("utf-8")).hexdigest()[:10]


def needs_format(fmt: str) -> bool:
    return "%" in fmt


def inject_imports(text: str, wants: set[str]) -> str:
    if not wants:
        return text
    existing = set(re.findall(r"^import\s+([^\s;]+)", text, re.M))
    missing = sorted(wants - existing)
    if not missing:
        return text
    lines = text.split("\n")
    anchor = -1
    for i, line in enumerate(lines[:200]):
        if line.startswith("import "):
            anchor = i
        elif anchor >= 0 and not line.startswith("import ") and not line.strip() == "":
            break
    if anchor < 0:
        # no imports: insert after package line
        for i, line in enumerate(lines[:10]):
            if line.startswith("package "):
                anchor = i
                break
        if anchor < 0:
            return text
        lines.insert(anchor + 1, "")
        anchor += 1
    insert_at = anchor + 1
    lines[insert_at:insert_at] = ["import " + m for m in missing]
    return "\n".join(lines)


def migrate_file(path: pathlib.Path, dry_run: bool = False) -> tuple[dict[str, str], int]:
    text = path.read_text(encoding="utf-8")
    masked = strip_comments(text)
    literals = find_literals(text)
    candidates = []
    for lit in literals:
        if not lit.raw.strip():
            continue
        ctx = enclosing_call(masked, lit)
        if ctx:
            candidates.append((lit, ctx))
            continue
        named_owner = owning_named_argument(masked, text, lit)
        if named_owner:
            candidates.append((lit, ("__named__", named_owner, -1)))
    if not candidates:
        return {}, 0
    resources: dict[str, str] = {}
    segments = []
    wants_imports: set[str] = set()
    for lit, (call_name, named_arg, idx) in candidates:
        key = key_for(lit.raw)
        if lit.is_template:
            fmt, args = decode_template(lit.raw)
            use_composable = is_composable_context(masked, lit)
            if use_composable:
                call = f"stringResource(R.string.{key}, {', '.join(args)})"
                wants_imports.add("androidx.compose.ui.res.stringResource")
            else:
                call = f"app.getString(R.string.{key}, {', '.join(args)})"
                wants_imports.add("li.songe.gkd.sdp.app")
            wants_imports.add("li.songe.gkd.sdp.R")
            resources[key] = fmt
            segments.append((lit.start, lit.end, call))
            continue
        if call_name == "__named__":
            use_composable = is_composable_context(masked, lit)
            if use_composable:
                call = f"stringResource(R.string.{key})"
                wants_imports.add("androidx.compose.ui.res.stringResource")
            else:
                call = f"app.getString(R.string.{key})"
                wants_imports.add("li.songe.gkd.sdp.app")
            wants_imports.add("li.songe.gkd.sdp.R")
            resources[key] = lit.raw.replace("%", "%%").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
            segments.append((lit.start, lit.end, call))
            continue
        # plain literal; maybe inside a concatenation
        between = paren_text(masked, lit)
        if between and "+" in between:
            concat = decode_concat(between)
            if concat and idx == 0:
                fmt, args = concat
                use_composable = is_composable_context(masked, lit)
                if use_composable:
                    call = f"stringResource(R.string.{key}, {', '.join(args)})"
                    wants_imports.add("androidx.compose.ui.res.stringResource")
                else:
                    call = f"app.getString(R.string.{key}, {', '.join(args)})"
                    wants_imports.add("li.songe.gkd.sdp.app")
                wants_imports.add("li.songe.gkd.sdp.R")
                resources[key] = fmt
                segments.append((lit.start, lit.end, call))
                continue
        use_composable = is_composable_context(masked, lit)
        if use_composable:
            call = f"stringResource(R.string.{key})"
            wants_imports.add("androidx.compose.ui.res.stringResource")
        else:
            call = f"app.getString(R.string.{key})"
            wants_imports.add("li.songe.gkd.sdp.app")
        wants_imports.add("li.songe.gkd.sdp.R")
        resources[key] = lit.raw.replace("%", "%%").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
        segments.append((lit.start, lit.end, call))
    for start, end, call in reversed(segments):
        text = text[:start] + call + text[end:]
    text = inject_imports(text, wants_imports)
    if not dry_run:
        path.write_text(text, encoding="utf-8")
    return resources, len(segments)


def paren_text(masked: str, lit: Literal) -> str:
    depth = 0
    for i in range(lit.start - 1, -1, -1):
        ch = masked[i]
        if ch == ")":
            depth += 1
        elif ch == "(":
            if depth == 0:
                return masked[i + 1: lit.start]
            depth -= 1
    return ""


def main():
    if "--verify" in sys.argv:
        verify()
        return
    dry = "--dry-run" in sys.argv
    if "--dir" in sys.argv:
        d = pathlib.Path(sys.argv[sys.argv.index("--dir") + 1])
        if d.is_file():
            paths = [d]
        else:
            paths = [p for p in d.rglob("*.kt")]
    else:
        paths = [p for p in SRC.rglob("*.kt")]
    paths = [p for p in paths if "/test/" not in str(p) and "/androidTest/" not in str(p)]
    resources: dict[str, str] = {}
    total = 0
    for path in paths:
        res, count = migrate_file(path, dry_run=dry)
        if count:
            print(f"migrated {path}: {count} literals")
        resources.update(res)
        total += count
    print(f"total literals: {total}, unique resources: {len(resources)}")
    if not dry:
        write_resources(resources)
        verify()


def write_resources(new_resources: dict[str, str]):
    path = RES_VALUES / "strings.xml"
    existing: dict[str, str] = {}
    if path.is_file():
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S):
            existing[m.group(1)] = m.group(2)
    existing.update(new_resources)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key in sorted(existing):
        value = existing[key]
        escaped = escape_xml(value)
        if needs_format(value):
            lines.append(f'    <string name="{key}">{escaped}</string>')
        else:
            lines.append(f'    <string name="{key}" formatted="false">{escaped}</string>')
    lines.append("</resources>")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote strings.xml with {len(existing)} strings")


def verify():
    xml_text = (RES_VALUES / "strings.xml").read_text(encoding="utf-8")
    existing = set(re.findall(r'<string name="([^"]+)"', xml_text))
    missing = {}
    for path in SRC.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(r"(?<![A-Za-z0-9_.])R\.string\.([A-Za-z0-9_]+)", text):
            key = m.group(1)
            if key not in existing:
                missing.setdefault(str(path.relative_to(ROOT)), set()).add(key)
    if missing:
        for f, keys in sorted(missing.items()):
            print(f"MISSING {f}: {sorted(keys)}")
        sys.exit(1)
    print("verify: all R.string references resolve")


if __name__ == "__main__":
    main()
