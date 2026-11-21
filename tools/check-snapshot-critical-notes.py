#!/usr/bin/env python3
"""Release gate: the bundled snapshot's critical notes and note links must equal an independent Python recompute.

The snapshot is built by Kotlin (`./gw :core:data:buildSnapshot`) from .cache/fmhy-edit. This re-reads the same docs
with the Python implementation (pipeline/src/fmhy_pipeline/critical.py), so a Kotlin/Python parsing difference in
the website rule (front matter, title, hash) or in the note-link scan fails the release.

  tools/check-snapshot-critical-notes.py [--snapshot PATH] [--docs DIR]
"""

import argparse
import gzip
import re
import sqlite3
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "pipeline" / "src"))

from fmhy_pipeline import critical

LOOSE_RE = re.compile(r"\.vitepress/notes/([^)\s]+?)(?:\.md)?\)")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--snapshot", type=Path, default=ROOT / "app/src/main/assets/dataset/snapshot.gzbin")
    ap.add_argument("--docs", type=Path, default=ROOT / ".cache/fmhy-edit/docs")
    args = ap.parse_args()

    want_schema = int(re.search(r"user_version\s*=\s*(\d+)", (ROOT / "schema/dataset.sql").read_text()).group(1))
    with tempfile.TemporaryDirectory() as tmp:
        db = Path(tmp) / "snapshot.db"
        db.write_bytes(gzip.decompress(args.snapshot.read_bytes()))
        con = sqlite3.connect(db)
        schema = int(con.execute("SELECT value FROM meta WHERE key='schema'").fetchone()[0])
        snap_notes = {r[0]: r for r in con.execute("SELECT id, title, text_md, hash FROM critical_notes")}
        snap_links = set(con.execute("SELECT entry_id, note_id, section_id, line, ord FROM note_links"))
        entries = con.execute("SELECT id, section_id, line, url, original_url FROM entries").fetchall()
        con.close()

    errors: list[str] = []
    if schema != want_schema:
        errors.append(f"snapshot meta.schema {schema} != schema/dataset.sql user_version {want_schema}")

    # expected note_links: raw-line scan joined to the snapshot's own entry lines
    by_section: dict[str, list[dict]] = {}
    for eid, sec, line, url, original_url in entries:
        by_section.setdefault(sec, []).append(
            {"id": eid, "line": line, "url": url, "original_url": original_url}
        )
    exp_links: set[tuple] = set()
    loose_ids: set[str] = set()
    for sec, items in by_section.items():
        path = args.docs / f"{sec}.md"
        if not path.exists():
            errors.append(f"docs file for section {sec} not found at {path}")
            continue
        text = path.read_bytes().decode("utf-8", errors="replace")
        for ln in text.splitlines():
            loose_ids.update(m.group(1) for m in LOOSE_RE.finditer(ln))
        exp_links.update(critical.note_links(sec, text, items))
    if exp_links != snap_links:
        errors.append(
            f"note_links differ: only in snapshot {len(snap_links - exp_links)}, only expected {len(exp_links - snap_links)}"
        )

    linked = {n for _, n, *_ in exp_links}
    notes_dir = args.docs / ".vitepress" / "notes"
    exp_notes: dict[str, dict] = {}
    for nid in sorted(linked):
        f = notes_dir / f"{nid}.md"
        if f.exists():
            row = critical.parse_note(nid, f.read_bytes().decode("utf-8", errors="replace"))
            if row:
                exp_notes[nid] = row
    for nid in sorted(set(exp_notes) | set(snap_notes)):
        e, s = exp_notes.get(nid), snap_notes.get(nid)
        if e is None or s is None:
            errors.append(f"critical note {nid}: {'missing in snapshot' if s is None else 'not expected'}")
        elif (e["title"], e["text_md"], e["hash"]) != (s[1], s[2], s[3]):
            errors.append(f"critical note {nid}: row differs (hash snapshot {s[3]} vs expected {e['hash']})")

    by_name = [
        p.stem for p in notes_dir.glob("*-critical.md") if p.stem in linked and p.stem not in exp_notes
    ]
    for nid in by_name:
        print(f"note: {nid} is named -critical but a front matter override makes it non-critical")
    if len(loose_ids) > len(linked):
        print(f"warning: loose regex finds {len(loose_ids) - len(linked)} more linked note ids than the strict one")

    if errors:
        for e in errors[:30]:
            print("FAIL:", e)
        return 1
    print(f"{len(exp_notes)} critical notes, {len(exp_links)} note links, schema {schema}")
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
