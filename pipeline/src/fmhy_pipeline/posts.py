"""
Posts loader for FMHY monthly update posts.

Reads docs/posts/*.md (except search.md) from a git revision.
"""

from __future__ import annotations

import re
import subprocess


_FRONT_MATTER_RE = re.compile(r"^---\s*$")
_FENCE_RE = re.compile(r"^:::")
_HTML_LINE_RE = re.compile(r"^\s*<")


def _parse_front_matter(lines: list[str]) -> tuple[dict, int]:
    """
    Parse YAML front matter from the beginning of the file.
    Returns (dict of keys, index of first content line).
    """
    if not lines or not _FRONT_MATTER_RE.match(lines[0]):
        return {}, 0

    fm: dict[str, str] = {}
    i = 1
    while i < len(lines):
        if _FRONT_MATTER_RE.match(lines[i]):
            return fm, i + 1
        # Simple YAML key: value parsing (no nesting)
        m = re.match(r"^(\w[\w-]*):\s*(.*)", lines[i])
        if m:
            key = m.group(1)
            val = m.group(2).strip()
            # Strip surrounding quotes
            if (val.startswith('"') and val.endswith('"')) or \
               (val.startswith("'") and val.endswith("'")):
                val = val[1:-1]
            fm[key] = val
        i += 1
    return fm, i


def _date_to_ts(date_str: str) -> int:
    """Convert YYYY-MM-DD to unix timestamp at 00:00 UTC."""
    import datetime
    try:
        d = datetime.date.fromisoformat(date_str.strip())
        return int(datetime.datetime(d.year, d.month, d.day, tzinfo=datetime.timezone.utc).timestamp())
    except Exception:
        return 0


def _clean_body(lines: list[str]) -> str:
    """Remove lines starting with < and ::: fenced blocks."""
    result = []
    in_fence = False
    for line in lines:
        if _FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if _HTML_LINE_RE.match(line):
            continue
        result.append(line)
    # Strip leading/trailing blank lines
    text = "\n".join(result).strip()
    return text


def load_posts_from_repo(repo: str, rev: str) -> list[dict]:
    """
    Load posts from a git revision.
    Returns list of dicts: id, title, date, body_md.
    """
    # List docs/posts/ files
    try:
        result = subprocess.run(
            ["git", "ls-tree", "--name-only", f"{rev}:docs/posts"],
            capture_output=True, text=True, cwd=repo, check=True,
        )
        filenames = [f.strip() for f in result.stdout.splitlines() if f.strip()]
    except subprocess.CalledProcessError:
        return []

    posts = []
    for fname in filenames:
        if not fname.endswith(".md"):
            continue
        stem = fname[:-3]
        if stem == "search":
            continue

        # Read file content
        try:
            res = subprocess.run(
                ["git", "show", f"{rev}:docs/posts/{fname}"],
                capture_output=True, text=True, cwd=repo, check=True,
            )
            content = res.stdout
        except subprocess.CalledProcessError:
            continue

        lines = content.splitlines()
        fm, content_start = _parse_front_matter(lines)
        body_lines = lines[content_start:]
        body_md = _clean_body(body_lines)

        title = fm.get("title", stem)
        date_str = fm.get("date", "")
        date_ts = _date_to_ts(date_str)

        posts.append({
            "id": stem,
            "title": title,
            "date": date_ts,
            "body_md": body_md,
        })

    return posts
