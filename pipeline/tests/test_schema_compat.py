"""
Schema compatibility tests for APP-08 critical notes.

Covers Proof Layers 1 and 2 from BATCH-03-design.md.
"""

from __future__ import annotations

import shutil
import sqlite3
import subprocess
import types
from pathlib import Path

import pytest
from fmhy_pipeline.delta import compute_delta


def test_dataset_schema_is_additive_over_released_4():
    """
    Build git show v1.2.1:schema/dataset.sql and current schema/dataset.sql in memory.
    Every table/index/trigger/virtual table of the old DDL has identical sqlite_master.sql
    and identical PRAGMA table_info; the only additions are critical_notes, note_links,
    note_links_note, note_links_section. Skip if tag v1.2.1 is missing.
    """
    res = subprocess.run(
        ["git", "show", "v1.2.1:schema/dataset.sql"],
        capture_output=True,
        text=True,
    )
    if res.returncode != 0:
        pytest.skip("Tag v1.2.1 is missing")
    old_ddl = res.stdout

    repo_root = Path(__file__).resolve().parent.parent.parent
    new_ddl = (repo_root / "schema" / "dataset.sql").read_text(encoding="utf-8")

    c_old = sqlite3.connect(":memory:")
    c_old.executescript(old_ddl)

    c_new = sqlite3.connect(":memory:")
    c_new.executescript(new_ddl)

    old_objects = {
        r[0]: (r[1], r[2])
        for r in c_old.execute(
            "SELECT name, type, sql FROM sqlite_master WHERE sql IS NOT NULL"
        )
    }
    new_objects = {
        r[0]: (r[1], r[2])
        for r in c_new.execute(
            "SELECT name, type, sql FROM sqlite_master WHERE sql IS NOT NULL"
        )
    }

    # Every table/index/trigger/virtual table of old DDL has identical sqlite_master.sql
    for name, (obj_type, old_sql) in old_objects.items():
        assert name in new_objects, f"Object {name} missing from new schema"
        new_sql = new_objects[name][1]
        assert old_sql == new_sql, f"SQL mismatch for {name}:\nOld: {old_sql}\nNew: {new_sql}"
        if obj_type == "table":
            old_info = c_old.execute(f"PRAGMA table_info({name})").fetchall()
            new_info = c_new.execute(f"PRAGMA table_info({name})").fetchall()
            assert old_info == new_info, f"PRAGMA table_info mismatch for {name}"

    # The only additions are critical_notes, note_links, note_links_note, note_links_section
    added = set(new_objects.keys()) - set(old_objects.keys())
    expected_added = {"critical_notes", "note_links", "note_links_note", "note_links_section"}
    assert added == expected_added, f"Unexpected additions to schema: {added - expected_added}"


def test_old_delta_applier_ignores_new_tables(tmp_path: Path):
    """
    Load v1.2.1 delta.py via git show and importlib/types.ModuleType.
    Build two new-schema DBs with critical_notes/note_links differences, compute a delta
    with the NEW code, apply with the OLD apply_delta.
    Assert no exception, entries equal new DB, critical tables unchanged.
    """
    res = subprocess.run(
        ["git", "show", "v1.2.1:pipeline/src/fmhy_pipeline/delta.py"],
        capture_output=True,
        text=True,
    )
    if res.returncode != 0:
        pytest.skip("Tag v1.2.1 is missing")
    old_delta_src = res.stdout

    old_mod = types.ModuleType("fmhy_pipeline_delta_v121")
    exec(old_delta_src, old_mod.__dict__)
    old_apply_delta = old_mod.apply_delta

    repo_root = Path(__file__).resolve().parent.parent.parent
    ddl = (repo_root / "schema" / "dataset.sql").read_text(encoding="utf-8")

    db1_path = str(tmp_path / "db1.sqlite")
    db2_path = str(tmp_path / "db2.sqlite")

    # Set up db1 with new schema
    con1 = sqlite3.connect(db1_path)
    con1.executescript(ddl)
    con1.execute("INSERT INTO meta(key, value) VALUES ('schema', '5'), ('version', '1')")
    con1.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec1', 'Sec 1', '', 'sec1.md', 0)"
    )
    con1.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (1, 'e1', 'sec1', 0, 'Entry 1', 'https://e1.com', 100, 100)"
    )
    con1.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n1', 'N1', 'T1', 'h1')"
    )
    con1.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'n1', 'sec1', 1, 0)"
    )
    con1.commit()
    con1.close()

    # Set up db2 with changes in entries, critical_notes, note_links
    con2 = sqlite3.connect(db2_path)
    con2.executescript(ddl)
    con2.execute("INSERT INTO meta(key, value) VALUES ('schema', '5'), ('version', '2')")
    con2.execute(
        "INSERT INTO sections(id, title, description, file, ord) VALUES ('sec1', 'Sec 1', '', 'sec1.md', 0)"
    )
    con2.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (1, 'e1', 'sec1', 0, 'Entry 1 Updated', 'https://e1.com', 100, 200)"
    )
    con2.execute(
        "INSERT INTO entries(num, id, section_id, ord, name, url, first_seen, last_changed) "
        "VALUES (2, 'e2', 'sec1', 1, 'Entry 2', 'https://e2.com', 200, 200)"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n1', 'N1 Updated', 'T1 Updated', 'h1_new')"
    )
    con2.execute(
        "INSERT INTO critical_notes(id, title, text_md, hash) VALUES ('n2', 'N2', 'T2', 'h2')"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e1', 'n1', 'sec1', 1, 0)"
    )
    con2.execute(
        "INSERT INTO note_links(entry_id, note_id, section_id, line, ord) VALUES ('e2', 'n2', 'sec1', 2, 0)"
    )
    con2.commit()
    con2.close()

    # Compute delta with NEW code
    delta = compute_delta(db1_path, db2_path, from_ver=1, to_ver=2)
    assert "critical_notes" in delta["upsert"]
    assert "note_links" in delta["upsert"]

    # Copy db1 to applied_path and apply with OLD apply_delta
    applied_path = str(tmp_path / "applied.sqlite")
    shutil.copyfile(db1_path, applied_path)

    # Must apply cleanly without exception
    old_apply_delta(applied_path, delta)

    # Check entries equal new DB
    con_app = sqlite3.connect(applied_path)
    con_new = sqlite3.connect(db2_path)
    app_entries = con_app.execute("SELECT * FROM entries ORDER BY id").fetchall()
    new_entries = con_new.execute("SELECT * FROM entries ORDER BY id").fetchall()
    assert app_entries == new_entries

    # Check critical tables are unchanged (equal db1)
    con_old = sqlite3.connect(db1_path)
    app_crit = con_app.execute("SELECT * FROM critical_notes ORDER BY id").fetchall()
    old_crit = con_old.execute("SELECT * FROM critical_notes ORDER BY id").fetchall()
    assert app_crit == old_crit

    app_links = con_app.execute("SELECT * FROM note_links ORDER BY entry_id, note_id").fetchall()
    old_links = con_old.execute("SELECT * FROM note_links ORDER BY entry_id, note_id").fetchall()
    assert app_links == old_links

    con_app.close()
    con_new.close()
    con_old.close()
