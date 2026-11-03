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
        WHERE ts >= ? AND commit_sha NOT IN (
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
