"""
History feed: a small gzip JSON file with the recent change log, for the app's What's New screen.

The app has no git history of its own. The pipeline walks fmhy/edit (history.py) and publishes
`changes.json.gz` (+ `changes-meta.json`) on a schedule; the app merges the rows on every sync, so
existing installs get history too and it never depends on the month the app was installed in.

Rows are compact arrays in `fields` order. `entry_id` is left out on purpose: the app resolves it
against its own entries (ids can differ between the Python and Kotlin parsers).
"""

from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

from .history import HistoryDB

FEED_SCHEMA = 1
FEED_DAYS = 120
FIELDS = ["ts", "kind", "section_id", "name", "url", "detail", "commit_sha"]

# A single commit touching more rows than this restructured the wiki (a file split, a section
# rewrite) instead of people adding links; one 13k-row "added" commit would bury everything else.
MAX_ROWS_PER_COMMIT = 400

# A new feed with fewer than this share of the previous feed's rows is treated as a bug, not data.
MIN_KEPT_SHARE = 0.5


def build_feed(
    history_db: HistoryDB,
    now: int,
    days: int = FEED_DAYS,
    max_rows_per_commit: int = MAX_ROWS_PER_COMMIT,
) -> dict:
    since = now - days * 86400
    history_db.flush()
    rows = history_db.con.execute(
        f"""
        SELECT ts, kind, section_id, name, url, detail, commit_sha
        FROM change_log
        WHERE ts >= ? AND url != '' AND commit_sha NOT IN (
            SELECT commit_sha FROM change_log WHERE ts >= ?
            GROUP BY commit_sha HAVING COUNT(*) > ?
        )
        ORDER BY ts ASC, id ASC
        """,
        (since, since, max_rows_per_commit),
    ).fetchall()
    return {
        "schema": FEED_SCHEMA,
        "generated_at": now,
        "source_commit": history_db.get_last_commit() or "",
        "days": days,
        "fields": FIELDS,
        "changes": [list(r) for r in rows],
    }


def write_feed(feed: dict, out_dir: str) -> dict:
    """Write changes.json.gz and changes-meta.json into out_dir; returns the meta dict."""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    meta_path = out / "changes-meta.json"

    count = len(feed["changes"])
    if meta_path.exists():
        try:
            previous = json.loads(meta_path.read_text())["count"]
        except (ValueError, KeyError):
            previous = 0
        if previous > 0 and count < previous * MIN_KEPT_SHARE:
            raise RuntimeError(
                f"feed collapsed: {count} rows, previous feed had {previous}; refusing to publish"
            )

    payload = gzip.compress(
        json.dumps(feed, separators=(",", ":"), ensure_ascii=False).encode("utf-8"), compresslevel=9, mtime=0
    )
    tmp = out / "changes.json.gz.tmp"
    tmp.write_bytes(payload)
    os.replace(tmp, out / "changes.json.gz")

    meta = {
        "schema": feed["schema"],
        "generated_at": feed["generated_at"],
        "source_commit": feed["source_commit"],
        "count": count,
        "size": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
    }
    meta_path.write_text(json.dumps(meta, indent=1) + "\n")
    return meta


# --- verification against the raw git history ---------------------------------------------------------
# Independent of the parser that produced the feed: a row only counts as backed when the commit's docs diff
# really contains the lines it claims (a link added, removed, starred, renamed, ...). Not every row can be
# backed by a naive reading of a diff (a site listed in two places, an old name that moved), so the check
# is a share, not all-or-nothing; the real feed sits near 98 %.

VERIFY_MIN_SHARE = 0.95

_SHA = re.compile(r"[0-9a-f]{40}")
_LINK = re.compile(r"\[([^\]]*)\]\((https?://[^)\s]+)")


def _norm_url(u: str) -> str:
    u = re.sub(r"#.*$", "", u.strip())
    return re.sub(r"^https?://(www\.)?", "", u, flags=re.I).rstrip("/").lower()


def _clean_name(t: str) -> str:
    return t.replace("\u2060", "").strip().strip("*").strip().lower()


def _commit_diff(repo: str, sha: str):
    if not _SHA.fullmatch(sha):  # the feed is untrusted input: never let it put an option into the git command line
        return [], [], []
    out = subprocess.run(
        ["git", "-C", repo, "show", "--format=%ct %at", "-U0", sha, "--", "docs"],
        capture_output=True, text=True, errors="replace",
    ).stdout.split("\n")
    times = out[0].split()
    plus = [line[1:] for line in out[1:] if line.startswith("+") and not line.startswith("+++")]
    minus = [line[1:] for line in out[1:] if line.startswith("-") and not line.startswith("---")]

    def ents(lines):
        return [(_clean_name(m.group(1)), _norm_url(m.group(2)), "\u2b50" in line)
                for line in lines for m in _LINK.finditer(line)]

    return times, ents(plus), ents(minus)


def verify_feed(repo: str, feed: dict, max_bad: int = 20) -> dict:
    """Check every feed row against the diff of its commit in `repo` (a clone of fmhy/edit)."""
    fields = feed["fields"]
    col = {name: fields.index(name) for name in FIELDS}
    cache: dict[str, tuple] = {}
    backed = 0
    bad: list[list] = []
    rows = feed["changes"]
    for r in rows:
        ts, kind, name, url = r[col["ts"]], r[col["kind"]], r[col["name"]], r[col["url"]]
        detail, sha = r[col["detail"]], r[col["commit_sha"]]
        if sha not in cache:
            cache[sha] = _commit_diff(repo, sha)
        times, plus, minus = cache[sha]
        nu, nm = _norm_url(url), _clean_name(name)
        pu = {e[1] for e in plus}
        mu = {e[1] for e in minus}
        if kind == "added":
            ok = nu in pu
        elif kind == "removed":
            ok = nu in mu
        elif kind == "moved":
            ok = nu in pu and nu in mu
        elif kind == "starred":
            ok = any(e[1] == nu and e[2] for e in plus) and any(e[1] == nu and not e[2] for e in minus)
        elif kind == "unstarred":
            ok = any(e[1] == nu and not e[2] for e in plus) and any(e[1] == nu and e[2] for e in minus)
        elif kind == "renamed":
            ok = (nu in pu and nu in mu and _clean_name(detail) in {e[0] for e in minus if e[1] == nu}
                  and nm in {e[0] for e in plus if e[1] == nu})
        elif kind == "url_changed":
            ok = nu in pu and _norm_url(detail) in mu
        else:
            ok = False
        ok = ok and str(ts) in times  # the row must carry the commit's own time
        if ok:
            backed += 1
        elif len(bad) < max_bad:
            bad.append(r)
    total = len(rows)
    return {"rows": total, "backed": backed, "share": (backed / total) if total else 1.0, "unbacked_sample": bad}
