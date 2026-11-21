"""
Tests for APP-08 critical notes parsing, build integration, and delta sync.

Covers:
- test_parse_note_fixture_matches_expected_json
- test_js_trim_set
- test_front_matter_true
- test_front_matter_false
- test_both_flags_true_wins
- test_crlf_note_hash_preserved
- test_note_ids_in_line_ordered_distinct
- test_note_links_fixture_catches_bold_link
- test_build_full_writes_critical_tables
- test_delta_add_change_remove_critical_note
- test_delta_roundtrip_includes_new_tables
"""

from __future__ import annotations

import json
import os
import shutil
import sqlite3
import subprocess
from pathlib import Path

from fmhy_pipeline.build import build_full
from fmhy_pipeline.critical import (
    JS_WS,
    JS_WS_STRING,
    js_trim,
    note_ids_in_line,
    note_links,
    parse_note,
)
from fmhy_pipeline.delta import apply_delta, compute_delta, tables_equal
from fmhy_pipeline.ids import IdAssigner, normalize_url
from fmhy_pipeline.parse import parse_section


def _fixture_dir() -> Path:
    return Path(__file__).resolve().parent.parent.parent / "schema" / "fixtures" / "critical-notes"


def _schema_sql_path() -> Path:
    return Path(__file__).resolve().parent.parent.parent / "schema" / "dataset.sql"


def test_parse_note_fixture_matches_expected_json():
    """Fixture notes + the CRLF note from bytes equal expected.json critical_notes exactly."""
    fixtures = _fixture_dir()
    expected_data = json.loads((fixtures / "expected.json").read_text(encoding="utf-8"))

    # Read LF notes from disk
    notes_dir = fixtures / "docs" / ".vitepress" / "notes"
    rows = []
    for p in sorted(notes_dir.glob("*.md")):
        raw = p.read_bytes().decode("utf-8", errors="replace")
        row = parse_note(p.stem, raw)
        if row is not None:
            rows.append(row)

    # Read CRLF note from expected.json bytes
    crlf_raw = expected_data["crlf_note_file"]["bytes_utf8"]
    crlf_row = parse_note("crlf-critical", crlf_raw)
    assert crlf_row is not None
    rows.append(crlf_row)

    rows.sort(key=lambda r: r["id"])

    assert rows == expected_data["critical_notes"]
    assert [r["hash"] for r in rows] == ["0d09fd9ff28b", "4ce4dfa2880c", "73ec7e5dfce0"]


def test_js_trim_set():
    """U+FEFF, U+00A0, U+2028 are trimmed; U+0085 and U+180E are NOT trimmed."""
    # Trimmed characters
    assert js_trim("\ufefftext\ufeff") == "text"
    assert js_trim("\u00a0text\u00a0") == "text"
    assert js_trim("\u2028text\u2028") == "text"

    assert "\ufeff" in JS_WS
    assert "\u00a0" in JS_WS
    assert "\u2028" in JS_WS
    assert "\ufeff" in JS_WS_STRING
    assert "\u00a0" in JS_WS_STRING
    assert "\u2028" in JS_WS_STRING

    # Non-trimmed characters
    assert js_trim("\u0085text\u0085") == "\u0085text\u0085"
    assert js_trim("\u180etext\u180e") == "\u180etext\u180e"

    assert "\u0085" not in JS_WS
    assert "\u180e" not in JS_WS
    assert "\u0085" not in JS_WS_STRING
    assert "\u180e" not in JS_WS_STRING


def test_front_matter_true():
    """Front matter critical: true makes a note critical even if filename lacks -critical."""
    raw = "---\ncritical: true\n---\n# Warning Title\nWarning description here."
    row = parse_note("foo-note", raw)
    assert row is not None
    assert row["id"] == "foo-note"
    assert row["title"] == "Warning Title"
    assert row["text_md"] == "# Warning Title\nWarning description here."


def test_front_matter_false():
    """Front matter critical: false makes a note non-critical even if filename ends in -critical."""
    raw = "---\ncritical: false\n---\n# Foo\nFormer warning withdrawn."
    row = parse_note("foo-critical", raw)
    assert row is None


def test_both_flags_true_wins():
    """When front matter contains both critical: false and critical: true, true wins."""
    raw1 = "---\ncritical: false\ncritical: true\n---\n# Danger\nAlert text."
    row1 = parse_note("danger-note", raw1)
    assert row1 is not None

    raw2 = "---\ncritical: true\ncritical: false\n---\n# Danger\nAlert text."
    row2 = parse_note("danger-note", raw2)
    assert row2 is not None


def test_crlf_note_hash_preserved():
    """Carriage returns are excluded from title but preserved in text_md and hash."""
    expected_data = json.loads((_fixture_dir() / "expected.json").read_text(encoding="utf-8"))
    crlf_raw = expected_data["crlf_note_file"]["bytes_utf8"]

    row = parse_note("crlf-critical", crlf_raw)
    assert row is not None
    assert row["title"] == "Crlf Warning"
    assert "\r" not in row["title"]
    assert row["text_md"] == "# Crlf Warning\r\nline two"
    assert "\r\n" in row["text_md"]
    assert row["hash"] == "4ce4dfa2880c"


def test_note_ids_in_line_ordered_distinct():
    """note_ids_in_line returns distinct note IDs in order and requires proper link syntax."""
    line = (
        "* [Foo](https://foo.example) - test / "
        "[W1](.vitepress/notes/note-a.md) and "
        "[W2](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/note-b) and "
        "[W1-again](.vitepress/notes/note-a.md)"
    )
    ids = note_ids_in_line(line)
    assert ids == ["note-a", "note-b"]

    # Requires closing parenthesis and valid syntax
    assert note_ids_in_line("[X](.vitepress/notes/invalid.md-foo)") == []
    assert note_ids_in_line("[X](.vitepress/notes/unclosed") == []


def test_note_links_fixture_catches_bold_link():
    """note_links extracts 5 rows for audio.md including bold Bar link and non-critical notes."""
    audio_content = (_fixture_dir() / "docs" / "audio.md").read_text(encoding="utf-8")
    _headings, entries, _notes, _unparsed = parse_section("audio", "Audio", audio_content)

    id_assigner = IdAssigner()
    entry_records = []
    for e in entries:
        eid = id_assigner.assign(normalize_url(e.url), e.section_id)
        entry_records.append({"id": eid, "section_id": e.section_id, "line": e.line, "url": e.url})

    links = note_links("audio", audio_content, entry_records)
    assert len(links) == 5

    # Map entry url to note_id
    url_by_eid = {r["id"]: r["url"] for r in entry_records}
    links_by_url = {url_by_eid[l[0]]: l[1] for l in links}

    expected_data = json.loads((_fixture_dir() / "expected.json").read_text(encoding="utf-8"))
    assert links_by_url == expected_data["note_links_note_ids_by_entry_url"]

    # All links must have ord == 0
    assert all(l[4] == 0 for l in links)
    # Bold Bar link caught
    assert links_by_url["https://bar.example"] == "bar-note"


def test_build_full_writes_critical_tables(tmp_path: Path):
    """build_full creates critical_notes (3 rows) and note_links (5 rows) with meta.schema == 5."""
    repo = tmp_path / "repo"
    repo.mkdir()

    subprocess.run(["git", "init", "-b", "main"], cwd=repo, check=True, capture_output=True)
    subprocess.run(["git", "config", "user.email", "test@test.com"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.name", "Test"], cwd=repo, check=True)

    # Set up docs from fixture
    fixtures = _fixture_dir()
    docs_dir = repo / "docs"
    notes_dir = docs_dir / ".vitepress" / "notes"
    transformer_dir = docs_dir / ".vitepress" / "transformer"
    notes_dir.mkdir(parents=True, exist_ok=True)
    transformer_dir.mkdir(parents=True, exist_ok=True)

    shutil.copyfile(fixtures / "docs" / "audio.md", docs_dir / "audio.md")
    for f in (fixtures / "docs" / ".vitepress" / "notes").glob("*.md"):
        shutil.copyfile(f, notes_dir / f.name)

    # Write CRLF note from bytes
    expected_data = json.loads((fixtures / "expected.json").read_text(encoding="utf-8"))
    crlf_bytes = expected_data["crlf_note_file"]["bytes_utf8"].encode("utf-8")
    (notes_dir / "crlf-critical.md").write_bytes(crlf_bytes)

    (transformer_dir / "headers.json").write_text(
        json.dumps({"audio.md": {"title": "Audio", "description": ""}}),
        encoding="utf-8",
    )

    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    env = os.environ.copy()
    env["GIT_COMMITTER_DATE"] = "1700000000"
    env["GIT_AUTHOR_DATE"] = "1700000000"
    subprocess.run(
        ["git", "commit", "-m", "init", "--date", "1700000000"],
        cwd=repo,
        check=True,
        env=env,
        capture_output=True,
    )
    rev = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()

    out_sqlite = str(tmp_path / "build_test.sqlite")
    stats = build_full(
        repo=str(repo),
        rev=rev,
        rentry_html="<html></html>",
        unsafe_txt="",
        reasons_json="{}",
        out_path=out_sqlite,
        schema_path=_schema_sql_path(),
    )

    assert stats["critical_notes"] == 3

    con = sqlite3.connect(out_sqlite)
    meta_schema = con.execute("SELECT value FROM meta WHERE key = 'schema'").fetchone()[0]
    assert meta_schema == "5"

    crit_rows = con.execute("SELECT id, title, text_md, hash FROM critical_notes ORDER BY id").fetchall()
    cols = ["id", "title", "text_md", "hash"]
    crit_dicts = [dict(zip(cols, r, strict=True)) for r in crit_rows]
    assert crit_dicts == expected_data["critical_notes"]

    links_rows = con.execute("SELECT entry_id, note_id, section_id, line, ord FROM note_links").fetchall()
    assert len(links_rows) == 5
    con.close()


def test_delta_add_change_remove_critical_note(tmp_path: Path):
    """compute_delta and apply_delta handle add, text/hash change, and delete in critical tables."""
    ddl = _schema_sql_path().read_text(encoding="utf-8")
    db1_path = str(tmp_path / "db1.sqlite")
    db2_path = str(tmp_path / "db2.sqlite")

    # DB1 setup
    con1 = sqlite3.connect(db1_path)
    con1.executescript(ddl)
    for k, v in [
        ("schema", "5"),
        ("version", "1"),
        ("created", "1700000000"),
        ("source_commit", "sha1"),
        ("history_start", "1600000000"),
    ]:
        con1.execute("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (k, v))
    con1.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec1', 'Sec 1', '', 'sec1.md', 0)"
    )
    con1.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (1, 'e1', 'sec1', 0, 'Entry 1', 'https://e1.com', 100, 100)"
    )
    con1.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (2, 'e2', 'sec1', 1, 'Entry 2', 'https://e2.com', 100, 100)"
    )
    con1.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (3, 'e3', 'sec1', 2, 'Entry 3', 'https://e3.com', 100, 100)"
    )
    con1.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-keep', 'Keep', 'Keep text', '111111111111')"
    )
    con1.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-mod', 'Mod', 'Mod text', '222222222222')"
    )
    con1.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-del', 'Del', 'Del text', '333333333333')"
    )
    con1.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'note-keep', 'sec1', 1, 0)"
    )
    con1.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e2', 'note-mod', 'sec1', 2, 0)"
    )
    con1.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e3', 'note-del', 'sec1', 3, 0)"
    )
    con1.commit()
    con1.close()

    # DB2 setup: note-keep unchanged, note-mod updated, note-del removed, note-add added
    con2 = sqlite3.connect(db2_path)
    con2.executescript(ddl)
    for k, v in [
        ("schema", "5"),
        ("version", "2"),
        ("created", "1700000000"),
        ("source_commit", "sha2"),
        ("history_start", "1600000000"),
    ]:
        con2.execute("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (k, v))
    con2.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec1', 'Sec 1', '', 'sec1.md', 0)"
    )
    con2.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (1, 'e1', 'sec1', 0, 'Entry 1', 'https://e1.com', 100, 100)"
    )
    con2.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (2, 'e2', 'sec1', 1, 'Entry 2', 'https://e2.com', 100, 100)"
    )
    con2.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (4, 'e4', 'sec1', 3, 'Entry 4', 'https://e4.com', 200, 200)"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-keep', 'Keep', 'Keep text', '111111111111')"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-mod', 'Mod New', 'Mod text new', '222222222223')"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('note-add', 'Add', 'Add text', '444444444444')"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'note-keep', 'sec1', 1, 0)"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e2', 'note-mod', 'sec1', 2, 0)"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e4', 'note-add', 'sec1', 4, 0)"
    )
    con2.commit()
    con2.close()

    delta = compute_delta(db1_path, db2_path, from_ver=1, to_ver=2)

    # Verify delta content
    upsert_crit = {r["id"]: r for r in delta.get("upsert", {}).get("critical_notes", [])}
    assert "note-mod" in upsert_crit
    assert upsert_crit["note-mod"]["hash"] == "222222222223"
    assert "note-add" in upsert_crit
    assert delta.get("delete", {}).get("critical_notes", []) == ["note-del"]

    assert ["e3", "note-del"] in delta.get("delete", {}).get("note_links", [])

    # Apply delta to applied_db
    applied_path = str(tmp_path / "applied.sqlite")
    shutil.copyfile(db1_path, applied_path)
    apply_delta(applied_path, delta)

    eq, reason = tables_equal(applied_path, db2_path)
    assert eq, f"Tables differ after delta application: {reason}"


def test_delta_roundtrip_includes_new_tables(tmp_path: Path):
    """Delta roundtrip preserves critical_notes and note_links alongside standard tables."""
    ddl = _schema_sql_path().read_text(encoding="utf-8")
    db1_path = str(tmp_path / "rt_db1.sqlite")
    db2_path = str(tmp_path / "rt_db2.sqlite")

    con1 = sqlite3.connect(db1_path)
    con1.executescript(ddl)
    for k, v in [
        ("schema", "5"),
        ("version", "1"),
        ("created", "1700000000"),
        ("source_commit", "sha1"),
        ("history_start", "1600000000"),
    ]:
        con1.execute("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (k, v))
    con1.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec', 'Section', '', 'sec.md', 0)"
    )
    con1.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n1', 'T1', 'M1', 'h1')"
    )
    con1.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'n1', 'sec', 1, 0)"
    )
    con1.commit()
    con1.close()

    con2 = sqlite3.connect(db2_path)
    con2.executescript(ddl)
    for k, v in [
        ("schema", "5"),
        ("version", "2"),
        ("created", "1700000000"),
        ("source_commit", "sha2"),
        ("history_start", "1600000000"),
    ]:
        con2.execute("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (k, v))
    con2.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec', 'Section', '', 'sec.md', 0)"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n1', 'T1', 'M1', 'h1')"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n2', 'T2', 'M2', 'h2')"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'n1', 'sec', 1, 0)"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e2', 'n2', 'sec', 2, 0)"
    )
    con2.commit()
    con2.close()

    delta = compute_delta(db1_path, db2_path, from_ver=1, to_ver=2)
    assert "critical_notes" in delta["upsert"]
    assert "note_links" in delta["upsert"]

    applied_path = str(tmp_path / "rt_applied.sqlite")
    shutil.copyfile(db1_path, applied_path)
    apply_delta(applied_path, delta)

    eq, reason = tables_equal(applied_path, db2_path)
    assert eq, reason


def test_note_links_guard_only_entries_before_the_note_link():
    """Website rule: only links before the note link on the line are guarded (CrystalDiskInfo line shape)."""
    from fmhy_pipeline.critical import note_links

    n = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes"
    line = (
        f"* [A](https://a.example/) / [Warning]({n}/a-critical.md), [B](https://b.example/), "
        f"[C](https://c.example/) - Desc"
    )
    entries = [
        {"id": "ea", "line": 1, "url": "https://a.example/", "original_url": None},
        {"id": "eb", "line": 1, "url": "https://b.example/", "original_url": None},
        {"id": "ec", "line": 1, "url": "https://missing.example/", "original_url": None},
    ]
    rows = note_links("s", line, entries)
    assert [(r[0], r[1]) for r in rows] == [("ea", "a-critical"), ("ec", "a-critical")]

    late = f"* [A](https://a.example/) or [B](https://b.example/) - Desc / [Warning]({n}/a-critical.md)"
    entries2 = [
        {"id": "ea", "line": 1, "url": "https://a.example/", "original_url": None},
        {"id": "eb", "line": 1, "url": "https://b.example/", "original_url": None},
    ]
    assert [(r[0], r[1]) for r in note_links("s", late, entries2)] == [("ea", "a-critical"), ("eb", "a-critical")]
