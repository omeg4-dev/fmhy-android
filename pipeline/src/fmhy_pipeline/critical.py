"""
Critical notes parsing, extraction, and loading.

Implements the APP-08 critical notes specification (BATCH-03-design.md).
"""

from __future__ import annotations

import hashlib
import re
import subprocess
from pathlib import Path
from typing import Any

# JavaScript String.prototype.trim whitespace set:
# U+0009, 000A, 000B, 000C, 000D, 0020, 00A0, 1680, 2000-200A, 2028, 2029, 202F, 205F, 3000, FEFF.
_JS_WS_CODEPOINTS = [
    0x0009,  # Horizontal Tab
    0x000A,  # Line Feed
    0x000B,  # Vertical Tab
    0x000C,  # Form Feed
    0x000D,  # Carriage Return
    0x0020,  # Space
    0x00A0,  # No-Break Space
    0x1680,  # Ogham Space Mark
    *range(0x2000, 0x200B),  # 2000..200A (En Quad .. Hair Space)
    0x2028,  # Line Separator
    0x2029,  # Paragraph Separator
    0x202F,  # Narrow No-Break Space
    0x205F,  # Medium Mathematical Space
    0x3000,  # Ideographic Space
    0xFEFF,  # Zero Width No-Break Space (BOM)
]
JS_WS_STRING: str = "".join(chr(cp) for cp in _JS_WS_CODEPOINTS)
JS_WS: frozenset[str] = frozenset(chr(cp) for cp in _JS_WS_CODEPOINTS)

_FRONT_MATTER_RE = re.compile(r"^---\n([\s\S]*?)\n---\n?")
_LINE_SPLIT_RE = re.compile(r"[\n\r\u2028\u2029]")
_TITLE_RE = re.compile(f"^#+[{re.escape(JS_WS_STRING)}]+(.*)$")
_NOTE_ID_RE = re.compile(r"\]\([^)\s]*\.vitepress/notes/([A-Za-z0-9_-]+)(?:\.md)?\)")


def js_trim(s: str) -> str:
    """Trim characters in the JS String.prototype.trim whitespace set from both ends."""
    return s.strip(JS_WS_STRING)


def parse_note(note_id: str, raw: str) -> dict[str, str] | None:
    """
    Parse a note file's content into a critical_notes row, or return None if not critical.

    Keys returned: id, title, text_md, hash.
    """
    t = js_trim(raw)
    critical = note_id.endswith("-critical")

    m = _FRONT_MATTER_RE.match(t)
    if m:
        fm_content = m.group(1)
        has_true = False
        has_false = False
        for line in _LINE_SPLIT_RE.split(fm_content):
            if line.startswith("critical:"):
                rest = line[len("critical:"):]
                val = js_trim(rest)
                if val == "true":
                    has_true = True
                elif val == "false":
                    has_false = True
        if has_true:
            critical = True
        elif has_false:
            critical = False
        body = js_trim(t[m.end():])
    else:
        body = t

    if not critical:
        return None

    title = ""
    for line in _LINE_SPLIT_RE.split(body):
        tm = _TITLE_RE.match(line)
        if tm:
            title = js_trim(tm.group(1))
            break

    h = hashlib.sha256(body.encode("utf-8")).hexdigest()[:12]
    return {
        "id": note_id,
        "title": title,
        "text_md": body,
        "hash": h,
    }


def note_ids_in_line(line: str) -> list[str]:
    """Extract distinct note IDs from links in a line in order of first appearance."""
    seen: set[str] = set()
    result: list[str] = []
    for m in _NOTE_ID_RE.finditer(line):
        nid = m.group(1)
        if nid not in seen:
            seen.add(nid)
            result.append(nid)
    return result


def note_refs_in_line(line: str) -> list[tuple[str, int]]:
    """Distinct note ids of a line with the character index of each id's first link, in order."""
    seen: set[str] = set()
    result: list[tuple[str, int]] = []
    for m in _NOTE_ID_RE.finditer(line):
        nid = m.group(1)
        if nid not in seen:
            seen.add(nid)
            result.append((nid, m.start()))
    return result


def _entry_get(entry: Any, key: str) -> Any:
    return entry.get(key) if isinstance(entry, dict) else getattr(entry, key, None)


def note_links(
    section_id: str,
    content: str,
    entry_records: list[Any],
) -> list[tuple[str, str, str, int, int]]:
    """
    Compute note_links rows for a section from its raw markdown and parsed entry records.

    Returns tuples of: (entry_id, note_id, section_id, line, ord) sorted by (section_id, entry_id, note_id).
    The website guards only the links BEFORE the note link on a line, so an entry gets a row for a note only
    when its link (url or original_url, first occurrence in the line text) starts before that note's first link
    on the line. An entry whose url cannot be found in the line text counts as before (kept).
    """
    lines = content.splitlines()
    refs: dict[int, list[tuple[str, int]]] = {}
    for line_no, line_text in enumerate(lines, start=1):
        found = note_refs_in_line(line_text)
        if found:
            refs[line_no] = found

    result: list[tuple[str, str, str, int, int]] = []
    for entry in entry_records:
        entry_id = _entry_get(entry, "id")
        entry_line = _entry_get(entry, "line")
        if entry_line not in refs:
            continue
        text = lines[entry_line - 1]
        positions = [
            i for i in (text.find(u) for u in (_entry_get(entry, "url"), _entry_get(entry, "original_url")) if u) if i >= 0
        ]
        first = min(positions) if positions else None
        for k, (nid, pos) in enumerate(refs[entry_line]):
            if first is None or first < pos:
                result.append((entry_id, nid, section_id, entry_line, k))

    result.sort(key=lambda t: (t[2], t[0], t[1]))
    return result


def read_note_bytes(repo: str | Path, rev: str, note_id: str) -> bytes | None:
    """Read a note file's raw bytes from git at rev, preserving CRLF. Returns None if absent."""
    res = subprocess.run(
        ["git", "show", f"{rev}:docs/.vitepress/notes/{note_id}.md"],
        cwd=str(repo),
        capture_output=True,
    )
    if res.returncode != 0:
        return None
    return res.stdout
