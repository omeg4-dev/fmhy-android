"""
Build the full SQLite dataset from parsed FMHY content.
"""

from __future__ import annotations

import json
import os
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Optional

from .base64links import Base64Resolver
from .ids import IdAssigner, normalize_url
from .parse import parse_section
from .posts import load_posts_from_repo
from .sections import get_file_content, load_sections
from .unsafe import load_unsafe


def _get_commit_time(repo: str, rev: str) -> int:
    """Get the committer unix timestamp of a revision."""
    res = subprocess.run(
        ["git", "log", "-1", "--format=%ct", rev],
        capture_output=True, text=True, cwd=repo, check=True,
    )
    return int(res.stdout.strip())


def _get_commit_sha(repo: str, rev: str) -> str:
    """Get the full SHA of a revision."""
    res = subprocess.run(
        ["git", "rev-parse", rev],
        capture_output=True, text=True, cwd=repo, check=True,
    )
    return res.stdout.strip()


def _get_history_start(repo: str) -> int:
    """Get the committer time of the first commit in the repo."""
    res = subprocess.run(
        ["git", "log", "--reverse", "--first-parent", "--format=%ct"],
        capture_output=True, text=True, cwd=repo, check=True,
    )
    lines = [l.strip() for l in res.stdout.splitlines() if l.strip()]
    return int(lines[0]) if lines else 0


def _load_schema(schema_path: Path) -> str:
    """Read the DDL from dataset.sql."""
    return schema_path.read_text(encoding="utf-8")


def build_full(
    repo: str,
    rev: str,
    rentry_html: str,
    unsafe_txt: str,
    reasons_json: str,
    out_path: str,
    *,
    schema_path: Optional[Path] = None,
    history_db: Optional[str] = None,
    id_override: Optional[dict[tuple[str, str], str]] = None,
    first_seen_override: Optional[dict] = None,
    last_changed_override: Optional[dict] = None,
    starred_at_override: Optional[dict] = None,
    num_override: Optional[dict] = None,
    changes_rows: Optional[list[dict]] = None,
) -> dict:
    """
    Build a full SQLite dataset file.
    
    Args:
        repo: Path to the fmhy/edit git clone
        rev: Git revision to build from
        rentry_html: Content of the rentry HTML page
        unsafe_txt: Content of filterlist-domains.txt
        reasons_json: Content of filterlists-reasons.json
        out_path: Output path for the SQLite file
        schema_path: Optional override for schema/dataset.sql location
        history_db: Optional path to history SQLite DB (Part B)
        first_seen_override: entry_id → unix ts
        last_changed_override: entry_id → unix ts
        starred_at_override: entry_id → unix ts or None
        num_override: entry_id → integer num (from history DB)
        changes_rows: list of change row dicts to insert
    
    Returns:
        dict of build stats
    """
    start_time = time.time()

    # Find schema/dataset.sql
    if schema_path is None:
        # Look relative to this file's location
        here = Path(__file__).parent
        schema_path = here.parent.parent.parent / "schema" / "dataset.sql"
        if not schema_path.exists():
            # Try relative to repo
            schema_path = Path(repo).parent / "schema" / "dataset.sql"

    ddl = _load_schema(schema_path)

    # Get commit info
    commit_sha = _get_commit_sha(repo, rev)
    commit_time = _get_commit_time(repo, rev)
    history_start = _get_history_start(repo)

    # Load sections
    sections = load_sections(repo, rev)

    # Initialize base64 resolver
    b64_resolver = Base64Resolver(rentry_html)

    # Initialize ID assigner (Part A: fresh; Part B: history-backed)
    id_assigner = IdAssigner()
    if num_override:
        # Use existing num assignments from history
        pass

    # Load unsafe domains
    unsafe_domains = load_unsafe(unsafe_txt, reasons_json)

    # Load posts
    posts = load_posts_from_repo(repo, rev)

    # Parse all sections
    all_headings = []
    all_entries = []
    all_notes = []
    all_unparsed = []
    total_bullets = 0

    for section in sections:
        sid = section["id"]
        content = get_file_content(repo, rev, f"docs/{section['file']}")
        if content is None:
            continue

        headings, entries, notes, unparsed = parse_section(
            section_id=sid,
            section_title=section["title"],
            content=content,
        )
        all_headings.extend(headings)
        all_entries.extend(entries)
        all_notes.extend(notes)
        all_unparsed.extend(unparsed)

        # Count bullets
        for line in content.splitlines():
            stripped = line.strip()
            if stripped.startswith("* ") or stripped.startswith("- "):
                total_bullets += 1

    # Assign IDs and nums
    # Use num_override dict if provided (from history DB)
    _id_to_num: dict[str, int] = {}
    _next_num = 1

    # If num_override provides existing nums, start after the max
    if num_override:
        existing_nums = list(num_override.values())
        _next_num = max(existing_nums) + 1 if existing_nums else 1

    entry_records = []
    occ_counts: dict[tuple[str, str], int] = {}
    for e in all_entries:
        norm = normalize_url(e.url)
        base_key = (norm, e.section_id)
        occ = occ_counts.get(base_key, 0)
        occ_counts[base_key] = occ + 1
        norm_key = norm if occ == 0 else f"{norm}__dup{occ+1}"
        lookup_key = (norm_key, e.section_id)

        if id_override and lookup_key in id_override:
            eid = id_override[lookup_key]
        else:
            eid = id_assigner.assign(norm, e.section_id)

        # Resolve base64 links
        b64_result = b64_resolver.resolve_entry(e.url, e.mirrors)
        url = b64_result["url"]
        original_url = b64_result["original_url"]
        is_base64 = b64_result["is_base64"]
        mirrors = b64_result["mirrors"]

        # Timestamps
        if first_seen_override and eid in first_seen_override:
            first_seen = first_seen_override[eid]
        else:
            first_seen = commit_time

        if last_changed_override and eid in last_changed_override:
            last_changed = last_changed_override[eid]
        else:
            last_changed = commit_time

        starred_at = None
        if starred_at_override and eid in starred_at_override:
            starred_at = starred_at_override[eid]

        if num_override and eid in num_override:
            num = num_override[eid]
        else:
            num = _next_num
            _next_num += 1

        entry_records.append({
            "num": num,
            "id": eid,
            "section_id": e.section_id,
            "heading_id": e.heading_id,
            "ord": e.ord,
            "name": e.name,
            "url": url,
            "original_url": original_url,
            "description": e.description,
            "tags": e.tags,
            "heading_path": e.heading_path,
            "tier": e.tier,
            "is_inline_more": e.is_inline_more,
            "is_base64": is_base64,
            "mirrors": json.dumps(mirrors, ensure_ascii=False),
            "extra_links": json.dumps(e.extra_links, ensure_ascii=False),
            "first_seen": first_seen,
            "last_changed": last_changed,
            "starred_at": starred_at,
        })

    # Build SQLite file
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()

    con = sqlite3.connect(str(out))
    con.executescript(ddl)
    con.execute("PRAGMA journal_mode=WAL")

    # Insert meta
    version = "1"
    meta_rows = [
        ("schema", "1"),
        ("version", version),
        ("created", str(int(time.time()))),
        ("source_commit", commit_sha),
        ("history_start", str(history_start)),
    ]
    con.executemany("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", meta_rows)

    # Insert sections
    for s in sections:
        con.execute(
            "INSERT OR REPLACE INTO sections(id, title, description, file, ord) VALUES (?,?,?,?,?)",
            (s["id"], s["title"], s["description"], s["file"], s["ord"]),
        )

    # Insert headings
    for h in all_headings:
        con.execute(
            "INSERT OR REPLACE INTO headings(id, section_id, parent_id, level, title, anchor, ord) "
            "VALUES (?,?,?,?,?,?,?)",
            (h.id, h.section_id, h.parent_id, h.level, h.title, h.anchor, h.ord),
        )

    # Insert entries (triggers will update FTS)
    for rec in entry_records:
        con.execute(
            "INSERT OR REPLACE INTO entries"
            "(num, id, section_id, heading_id, ord, name, url, original_url, "
            "description, tags, heading_path, tier, is_inline_more, is_base64, "
            "mirrors, extra_links, first_seen, last_changed, starred_at) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                rec["num"], rec["id"], rec["section_id"], rec["heading_id"],
                rec["ord"], rec["name"], rec["url"], rec["original_url"],
                rec["description"], rec["tags"], rec["heading_path"],
                rec["tier"], rec["is_inline_more"], rec["is_base64"],
                rec["mirrors"], rec["extra_links"],
                rec["first_seen"], rec["last_changed"], rec["starred_at"],
            ),
        )

    # Insert notes
    note_records = []
    for idx, n in enumerate(all_notes):
        # id = '<heading_id or section_id>#n<k>'
        parent = n.heading_id if n.heading_id else n.section_id
        note_id = f"{parent}#n{idx}"
        note_records.append({
            "id": note_id,
            "section_id": n.section_id,
            "heading_id": n.heading_id,
            "ord": n.ord,
            "text_md": n.text_md,
        })

    for rec in note_records:
        con.execute(
            "INSERT OR REPLACE INTO notes(id, section_id, heading_id, ord, text_md) VALUES (?,?,?,?,?)",
            (rec["id"], rec["section_id"], rec["heading_id"], rec["ord"], rec["text_md"]),
        )

    # Insert posts
    for p in posts:
        con.execute(
            "INSERT OR REPLACE INTO posts(id, title, date, body_md) VALUES (?,?,?,?)",
            (p["id"], p["title"], p["date"], p["body_md"]),
        )

    # Insert unsafe domains
    for domain, reason in unsafe_domains:
        con.execute(
            "INSERT OR REPLACE INTO unsafe_domains(domain, reason) VALUES (?,?)",
            (domain, reason),
        )

    # Insert changes
    if changes_rows:
        for cr in changes_rows:
            con.execute(
                "INSERT OR REPLACE INTO changes(id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) "
                "VALUES (?,?,?,?,?,?,?,?,?)",
                (
                    cr["id"], cr["ts"], cr["kind"], cr["entry_id"],
                    cr["section_id"], cr["name"], cr["url"],
                    cr.get("detail", ""), cr["commit_sha"],
                ),
            )

    # Optimize FTS
    con.execute("INSERT INTO fts_words(fts_words) VALUES('optimize')")
    con.execute("INSERT INTO fts_tri(fts_tri) VALUES('optimize')")

    con.commit()
    con.execute("VACUUM")
    con.close()

    elapsed = time.time() - start_time

    # Compute stats
    unparsed_count = len(all_unparsed)
    unparsed_ratio = unparsed_count / max(total_bullets, 1)

    raw_size = out.stat().st_size

    return {
        "entries": len(entry_records),
        "sections": len(sections),
        "headings": len(all_headings),
        "notes": len(all_notes),
        "posts": len(posts),
        "unsafe_domains": len(unsafe_domains),
        "base64_resolved": b64_resolver.resolved_count,
        "base64_unresolved": b64_resolver.unresolved_count,
        "unparsed_bullets": unparsed_count,
        "unparsed_ratio": round(unparsed_ratio, 4),
        "unparsed_lines": all_unparsed[:50],
        "raw_size": raw_size,
        "gz_size": 0,  # Set by caller after compression
        "seconds": round(elapsed, 2),
    }
