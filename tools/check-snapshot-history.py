#!/usr/bin/env python3
"""Release gate: the bundled snapshot's What's New history must equal the published history feed.

The snapshot is built locally (`./gw :core:data:buildSnapshot`) from .cache/state/history.sqlite. A stale or
wrongly built state file once put hundreds of changes that never happened into a release APK. This compares
(commit_sha, kind, url) of the snapshot's `changes` table with the feed over the window both cover and exits 1
on any real difference.

  tools/check-snapshot-history.py                       # against the live feed
  tools/check-snapshot-history.py --feed out/changes.json.gz
"""

import argparse
import gzip
import json
import sqlite3
import sys
import tempfile
import urllib.request
from pathlib import Path

LIVE_FEED = "https://omeg4-dev.github.io/fmhy-android/history/changes.json.gz"
DEFAULT_SNAPSHOT = Path(__file__).resolve().parent.parent / "app/src/main/assets/dataset/snapshot.gzbin"
WINDOW_DAYS = 90  # inside the 120-day windows of both the snapshot and the feed


def load_feed(source: str) -> dict:
    if source.startswith(("http://", "https://")):
        with urllib.request.urlopen(source, timeout=60) as r:
            raw = r.read()
    else:
        raw = Path(source).read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return json.loads(raw)


def load_snapshot_rows(path: Path) -> list[tuple]:
    with tempfile.TemporaryDirectory() as tmp:
        db = Path(tmp) / "snapshot.db"
        db.write_bytes(gzip.decompress(path.read_bytes()))
        con = sqlite3.connect(db)
        try:
            return con.execute(
                "SELECT ts, commit_sha, kind, url FROM changes WHERE kind != 'edited' AND commit_sha NOT LIKE 'sync:%' AND url != ''"
            ).fetchall()
        finally:
            con.close()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    ap.add_argument("--feed", default=LIVE_FEED, help="path or URL of changes.json.gz (default: live feed)")
    ap.add_argument("--max-diff", type=float, default=0.005, help="largest allowed share of differing rows")
    args = ap.parse_args()

    feed = load_feed(args.feed)
    col = {name: i for i, name in enumerate(feed["fields"])}
    feed_rows = [(r[col["ts"]], r[col["commit_sha"]], r[col["kind"]], r[col["url"]]) for r in feed["changes"] if r[col["url"]]]
    snap_rows = load_snapshot_rows(args.snapshot)
    if not feed_rows or not snap_rows:
        print(f"FAIL: empty history (snapshot {len(snap_rows)} rows, feed {len(feed_rows)} rows)")
        return 1

    hi = min(max(r[0] for r in snap_rows), max(r[0] for r in feed_rows))
    lo = hi - WINDOW_DAYS * 86400
    snap = {r[1:] for r in snap_rows if lo <= r[0] <= hi}
    live = {r[1:] for r in feed_rows if lo <= r[0] <= hi}
    only_snap, only_feed = snap - live, live - snap
    diff = len(only_snap) + len(only_feed)
    share = diff / max(len(snap | live), 1)
    print(f"window {WINDOW_DAYS} days up to ts {hi}: snapshot {len(snap)} rows, feed {len(live)} rows, "
          f"only in snapshot {len(only_snap)}, only in feed {len(only_feed)} ({share:.2%})")
    for label, rows in (("only in snapshot", only_snap), ("only in feed", only_feed)):
        for sha, kind, url in sorted(rows)[:5]:
            print(f"  {label}: {sha[:8]} {kind} {url}")
    if share > args.max_diff:
        print(f"FAIL: more than {args.max_diff:.2%} of the history differs; rebuild .cache/state with "
              "`fmhy-build feed` and run buildSnapshot again")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
