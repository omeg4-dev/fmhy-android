"""
CLI entry point for fmhy-build.

Commands:
  fmhy-build build  -- Full build and publish
  fmhy-build check  -- Build into temp dir, print stats + unparsed, write nothing
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
import time
from pathlib import Path


def _read_file(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def _build_core(args, out_sqlite: str, history_db_path: str | None) -> dict:
    """Run the full build pipeline, return stats dict."""
    from .build import build_full
    from .history import HistoryDB, walk_history

    rentry_html = _read_file(args.rentry)
    unsafe_txt = _read_file(args.unsafe)
    reasons_json = _read_file(args.reasons)

    # Part B: use history DB for stable IDs
    first_seen_override = None
    last_changed_override = None
    starred_at_override = None
    num_override = None
    changes_rows = None

    if history_db_path:
        Path(history_db_path).parent.mkdir(parents=True, exist_ok=True)
        history_db = HistoryDB(history_db_path)
        walk_history(args.repo, args.rev, history_db)

        # Load overrides from history
        id_override = history_db.get_all_aliases()
        all_facts = history_db.get_all_entry_facts()
        all_nums = history_db.get_all_nums()

        first_seen_override = {eid: f["first_seen"] for eid, f in all_facts.items()}
        last_changed_override = {eid: f["last_changed"] for eid, f in all_facts.items()}
        starred_at_override = {eid: f.get("starred_at") for eid, f in all_facts.items()}
        num_override = all_nums

        # Changes for last 400 days
        since_ts = int(time.time()) - 400 * 86400
        raw_changes = history_db.get_change_log(since_ts=since_ts)
        changes_rows = raw_changes

        history_db.close()
    else:
        id_override = None

    stats = build_full(
        repo=args.repo,
        rev=args.rev,
        rentry_html=rentry_html,
        unsafe_txt=unsafe_txt,
        reasons_json=reasons_json,
        out_path=out_sqlite,
        id_override=id_override,
        first_seen_override=first_seen_override,
        last_changed_override=last_changed_override,
        starred_at_override=starred_at_override,
        num_override=num_override,
        changes_rows=changes_rows,
    )
    return stats


def cmd_check(args) -> None:
    """Build into temp dir, print stats + first 50 unparsed, write nothing to OUT."""
    with tempfile.TemporaryDirectory(prefix="fmhy_check_") as tmpdir:
        out_sqlite = os.path.join(tmpdir, "check.sqlite")
        history_db_path = os.path.join(args.state, "history.sqlite") if args.state else None

        stats = _build_core(args, out_sqlite, history_db_path)

        # Print stats JSON
        unparsed_lines = stats.pop("unparsed_lines", [])
        print(json.dumps(stats))

        # Print first 50 unparsed
        if unparsed_lines:
            print(f"\n--- First {len(unparsed_lines)} unparsed lines ---")
            for line in unparsed_lines:
                print(repr(line))


def cmd_build(args) -> None:
    """Full build and publish."""
    from .publish import check_guards, load_manifest, publish

    out_dir = args.out
    state_dir = args.state
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    Path(state_dir).mkdir(parents=True, exist_ok=True)

    history_db_path = os.path.join(state_dir, "history.sqlite")

    with tempfile.TemporaryDirectory(prefix="fmhy_build_") as tmpdir:
        out_sqlite = os.path.join(tmpdir, "full.sqlite")
        stats = _build_core(args, out_sqlite, history_db_path)

        # Publish
        manifest = load_manifest(out_dir)
        try:
            result = publish(
                new_db_path=out_sqlite,
                stats=stats,
                out_dir=out_dir,
                state_dir=state_dir,
                force=getattr(args, "force", False),
            )
        except RuntimeError as e:
            print(json.dumps({"error": str(e)}), file=sys.stderr)
            sys.exit(1)

        # Compress stats for output
        unparsed_lines = stats.pop("unparsed_lines", [])
        output = {**stats, **result}
        # Ensure zst_size is from publish result
        print(json.dumps(output))


def main() -> None:
    parser = argparse.ArgumentParser(prog="fmhy-build")
    sub = parser.add_subparsers(dest="cmd", required=True)

    def add_common_args(p):
        p.add_argument("--repo", required=True, help="Path to fmhy/edit git clone")
        p.add_argument("--rev", required=True, help="Git revision")
        p.add_argument("--rentry", required=True, help="Path to rentry HTML file")
        p.add_argument("--unsafe", required=True, help="Path to filterlist-domains.txt")
        p.add_argument("--reasons", required=True, help="Path to filterlists-reasons.json")
        p.add_argument("--state", required=True, help="State directory for history.sqlite")
        p.add_argument("--out", required=True, help="Output directory")

    build_p = sub.add_parser("build")
    add_common_args(build_p)
    build_p.add_argument("--force", action="store_true", help="Skip publish guards")
    build_p.set_defaults(func=cmd_build)

    check_p = sub.add_parser("check")
    add_common_args(check_p)
    check_p.set_defaults(func=cmd_check)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
