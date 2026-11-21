"""Dump the reference parse of every section as JSON, for the Kotlin parser's parity test.

    uv run --offline python -m fmhy_pipeline.parity --repo ../.cache/fmhy-edit --rev HEAD \
        --rentry ../.cache/rentry-FMHYB64.html --out ../.cache/parity

Writes one <section_id>.json per section plus inputs/ (the exact markdown, constants.ts and rentry
HTML that were parsed), so the Kotlin test parses the same bytes.
"""

import argparse
import json
import os

from .base64links import Base64Resolver
from .critical import note_links, parse_note, read_note_bytes
from .ids import IdAssigner, normalize_url
from .parse import parse_section
from .sections import get_file_content, load_sections


def dump(repo: str, rev: str, rentry_html: str, out: str) -> dict:
    os.makedirs(os.path.join(out, "inputs"), exist_ok=True)
    with open(os.path.join(out, "inputs", "rentry-FMHYB64.html"), "w") as f:
        f.write(rentry_html)
    # Section metadata: headers.json upstream, constants.ts on older revisions (same input name).
    constants = (
        get_file_content(repo, rev, "docs/.vitepress/transformer/headers.json")
        or get_file_content(repo, rev, "docs/.vitepress/transformer/constants.ts")
        or ""
    )
    with open(os.path.join(out, "inputs", "constants.ts"), "w") as f:
        f.write(constants)

    resolver = Base64Resolver(rentry_html)
    sections = load_sections(repo, rev)
    totals = {"sections": 0, "entries": 0}
    index = []
    all_referenced_note_ids: set[str] = set()
    for section in sections:
        content = get_file_content(repo, rev, f"docs/{section['file']}")
        if content is None:
            continue
        with open(os.path.join(out, "inputs", section["file"]), "w") as f:
            f.write(content)
        headings, entries, notes, unparsed = parse_section(section["id"], section["title"], content)
        ids = IdAssigner()
        out_entries = []
        for e in entries:
            eid = ids.assign(normalize_url(e.url), e.section_id)
            b64 = resolver.resolve_entry(e.url, e.mirrors)
            out_entries.append({
                "id": eid, "name": e.name, "url": b64["url"], "original_url": b64["original_url"],
                "is_base64": b64["is_base64"], "tier": e.tier, "kind": e.kind, "line": e.line, "is_inline_more": e.is_inline_more,
                "mirrors": b64["mirrors"], "extra_links": e.extra_links, "description": e.description,
                "tags": e.tags, "heading_id": e.heading_id, "heading_path": e.heading_path,
                "ord": e.ord,
            })
        sec_note_links = note_links(section["id"], content, out_entries)
        for nl in sec_note_links:
            all_referenced_note_ids.add(nl[1])
        doc = {
            "section": {k: section[k] for k in ("id", "title", "description", "file", "ord")},
            "headings": [{"id": h.id, "parent_id": h.parent_id, "level": h.level, "title": h.title,
                          "anchor": h.anchor, "ord": h.ord} for h in headings],
            "entries": out_entries,
            "notes": [{"heading_id": n.heading_id, "ord": n.ord, "text_md": n.text_md} for n in notes],
            "note_links": [
                {
                    "entry_id": nl[0],
                    "note_id": nl[1],
                    "section_id": nl[2],
                    "line": nl[3],
                    "ord": nl[4],
                }
                for nl in sec_note_links
            ],
            "unparsed": len(unparsed),
        }
        with open(os.path.join(out, f"{section['id']}.json"), "w") as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
        index.append(section["id"])
        totals["sections"] += 1
        totals["entries"] += len(out_entries)
    with open(os.path.join(out, "index.json"), "w") as f:
        json.dump(index, f)

    # Write inputs/notes/<id>.md as bytes, and write critical_notes.json
    notes_dir = os.path.join(out, "inputs", "notes")
    os.makedirs(notes_dir, exist_ok=True)
    critical_rows = []
    for nid in sorted(all_referenced_note_ids):
        note_bytes = read_note_bytes(repo, rev, nid)
        if note_bytes is None:
            continue
        with open(os.path.join(notes_dir, f"{nid}.md"), "wb") as f:
            f.write(note_bytes)
        raw = note_bytes.decode("utf-8", errors="replace")
        parsed = parse_note(nid, raw)
        if parsed is not None:
            critical_rows.append(parsed)

    critical_rows.sort(key=lambda r: r["id"])
    with open(os.path.join(out, "critical_notes.json"), "w") as f:
        json.dump(critical_rows, f, ensure_ascii=False, indent=1)

    return totals


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--repo", required=True)
    p.add_argument("--rev", default="HEAD")
    p.add_argument("--rentry", required=True)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    with open(a.rentry) as f:
        print(json.dumps(dump(a.repo, a.rev, f.read(), a.out)))


if __name__ == "__main__":
    main()
