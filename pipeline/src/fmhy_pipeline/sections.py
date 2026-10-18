"""
Section loader for FMHY wiki pages.

Reads docs/*.md file list from a git revision and maps them to sections
using title/description from constants.ts.
"""

from __future__ import annotations

import re
import subprocess


# Files to exclude from sections
_EXCLUDED = {
    "index.md",
    "posts.md",
    "feedback.md",
    "sandbox.md",
    "startpage.md",
    "unsafe.md",
    "beginners-guide.md",
}

# Regex to parse constants.ts headers object
_HEADER_ENTRY_RE = re.compile(
    r"'([^']+\.md)'\s*:\s*\{[^}]*title\s*:\s*'([^']*)'[^}]*description\s*:\s*'([^']*)'[^}]*\}",
    re.DOTALL,
)
_HEADER_ENTRY_RE2 = re.compile(
    r'"([^"]+\.md)"\s*:\s*\{[^}]*title\s*:\s*"([^"]*)"[^}]*description\s*:\s*"([^"]*)"[^}]*\}',
    re.DOTALL,
)


def _parse_constants(ts_content: str) -> dict[str, dict]:
    """
    Parse the TypeScript constants.ts file to extract file → {title, description, ord}.
    Returns dict mapping file stem → {title, description, ord}.
    """
    result = {}
    ord_val = 0

    # Match all entries (using single-quote variant first, then double-quote)
    for m in _HEADER_ENTRY_RE.finditer(ts_content):
        fname = m.group(1)
        title = m.group(2)
        desc = m.group(3)
        result[fname] = {"title": title, "description": desc, "ord": ord_val}
        ord_val += 1

    for m in _HEADER_ENTRY_RE2.finditer(ts_content):
        fname = m.group(1)
        if fname not in result:
            title = m.group(2)
            desc = m.group(3)
            result[fname] = {"title": title, "description": desc, "ord": ord_val}
            ord_val += 1

    return result


def _get_constants(repo: str, rev: str) -> dict[str, dict]:
    """Load and parse constants.ts from git."""
    try:
        res = subprocess.run(
            ["git", "show", f"{rev}:docs/.vitepress/transformer/constants.ts"],
            capture_output=True, text=True, cwd=repo, check=True,
        )
        return _parse_constants(res.stdout)
    except subprocess.CalledProcessError:
        return {}


def _first_heading(content: str) -> str | None:
    """Extract the first # heading from page content."""
    for line in content.splitlines():
        m = re.match(r"^#\s+(.*)", line)
        if m:
            return m.group(1).strip()
    return None


def load_sections(repo: str, rev: str) -> list[dict]:
    """
    Load sections from a git revision.
    Returns list of dicts: id, title, description, file, ord.
    Sorted by ord from constants.ts, unknown files last alphabetically.
    """
    # Get list of docs/*.md files
    try:
        res = subprocess.run(
            ["git", "ls-tree", "--name-only", f"{rev}:docs"],
            capture_output=True, text=True, cwd=repo, check=True,
        )
        filenames = [f.strip() for f in res.stdout.splitlines() if f.strip()]
    except subprocess.CalledProcessError:
        return []

    constants = _get_constants(repo, rev)
    # Max ord from constants
    max_ord = max((v["ord"] for v in constants.values()), default=0)

    sections = []
    unknown_files = []

    for fname in filenames:
        if not fname.endswith(".md"):
            continue
        if fname in _EXCLUDED:
            continue
        stem = fname[:-3]

        if fname in constants:
            info = constants[fname]
            sections.append({
                "id": stem,
                "title": info["title"],
                "description": info["description"],
                "file": fname,
                "ord": info["ord"],
            })
        else:
            unknown_files.append((stem, fname))

    # For unknown files, try to get title from first heading
    unknown_sections = []
    for stem, fname in sorted(unknown_files):
        try:
            res = subprocess.run(
                ["git", "show", f"{rev}:docs/{fname}"],
                capture_output=True, text=True, cwd=repo, check=True,
            )
            content = res.stdout
            title = _first_heading(content) or stem
        except subprocess.CalledProcessError:
            title = stem
        unknown_sections.append({
            "id": stem,
            "title": title,
            "description": "",
            "file": fname,
            "ord": max_ord + 1,
        })

    # Sort: known sections by ord, unknown alphabetically after
    sections.sort(key=lambda s: s["ord"])
    # Re-assign ord to be sequential for unknown sections
    if unknown_sections:
        next_ord = (sections[-1]["ord"] + 1) if sections else 0
        for s in unknown_sections:
            s["ord"] = next_ord
            next_ord += 1

    return sections + unknown_sections


def get_file_content(repo: str, rev: str, path: str) -> str | None:
    """Read a file from a git revision via git show."""
    try:
        res = subprocess.run(
            ["git", "show", f"{rev}:{path}"],
            capture_output=True, text=True, cwd=repo, check=True,
        )
        return res.stdout
    except subprocess.CalledProcessError:
        return None
