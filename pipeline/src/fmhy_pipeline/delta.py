"""
Delta computation and application.

A delta transforms one version of the full SQLite database into the next.
See schema/README.md for the full specification.
"""

from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path
from typing import Any


# Tables in the non-FTS schema (excluding meta and FTS)
_DATA_TABLES = ["sections", "headings", "entries", "notes", "posts", "unsafe_domains"]

# Primary keys for each table
_PKS = {
    "sections": "id",
    "headings": "id",
    "entries": "id",
    "notes": "id",
    "posts": "id",
    "unsafe_domains": "domain",
    "changes": "id",
    "meta": "key",
}


def _read_all_rows(con: sqlite3.Connection, table: str) -> list[dict]:
    """Read all rows from a table as list of dicts, sorted by primary key."""
    pk = _PKS.get(table, "rowid")
    cur = con.execute(f"SELECT * FROM {table} ORDER BY {pk}")
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, row)) for row in cur.fetchall()]


def compute_delta(old_db: str, new_db: str, from_ver: int, to_ver: int) -> dict:
    """
    Compute a delta between two SQLite databases.
    Returns a dict conforming to schema/README.md format.
    """
    old_con = sqlite3.connect(f"file:{old_db}?mode=ro", uri=True)
    new_con = sqlite3.connect(f"file:{new_db}?mode=ro", uri=True)

    try:
        delta: dict[str, Any] = {
            "schema": 1,
            "from": from_ver,
            "to": to_ver,
            "upsert": {},
            "delete": {},
        }

        for table in _DATA_TABLES:
            pk = _PKS[table]
            old_rows = {r[pk]: r for r in _read_all_rows(old_con, table)}
            new_rows = {r[pk]: r for r in _read_all_rows(new_con, table)}

            # Upserts: rows in new that differ from old
            upserts = []
            for key, row in new_rows.items():
                if key not in old_rows or old_rows[key] != row:
                    upserts.append(row)

            # Deletes: rows in old not in new
            deletes = [key for key in old_rows if key not in new_rows]

            if upserts:
                delta["upsert"][table] = upserts
            if deletes:
                delta["delete"][table] = deletes

        # Changes: append rows with id > max(old changes.id)
        old_max_id_row = old_con.execute("SELECT MAX(id) FROM changes").fetchone()
        old_max_id = old_max_id_row[0] or 0
        new_changes = new_con.execute(
            "SELECT * FROM changes WHERE id > ? ORDER BY id ASC", (old_max_id,)
        ).fetchall()
        if new_changes:
            cols = [d[0] for d in new_con.execute("SELECT * FROM changes LIMIT 0").description]
            delta["changes_append"] = [dict(zip(cols, r)) for r in new_changes]
        else:
            delta["changes_append"] = []

        # Prune time: now - 400 days
        prune_before = int(time.time()) - 400 * 86400
        delta["changes_prune_before"] = prune_before

        # Meta updates
        new_meta = {r[0]: r[1] for r in new_con.execute("SELECT key, value FROM meta")}
        delta["meta"] = {
            "version": new_meta.get("version", str(to_ver)),
            "created": new_meta.get("created", ""),
            "source_commit": new_meta.get("source_commit", ""),
        }

        return delta

    finally:
        old_con.close()
        new_con.close()


def apply_delta(db_path: str, delta: dict) -> None:
    """
    Apply a delta dict to a SQLite database, following schema/README.md order.
    Modifies the database in place.
    """
    con = sqlite3.connect(db_path)
    try:
        con.execute("PRAGMA foreign_keys = OFF")

        with con:
            # 1. Deletes in order: notes, entries, headings, sections, posts, unsafe_domains
            delete_order = ["notes", "entries", "headings", "sections", "posts", "unsafe_domains"]
            deletes = delta.get("delete", {})
            for table in delete_order:
                keys = deletes.get(table, [])
                if not keys:
                    continue
                pk = _PKS[table]
                placeholders = ",".join("?" * len(keys))
                con.execute(f"DELETE FROM {table} WHERE {pk} IN ({placeholders})", keys)

            # 2. Upserts in order: sections, headings, entries, notes, posts, unsafe_domains
            upsert_order = ["sections", "headings", "entries", "notes", "posts", "unsafe_domains"]
            upserts = delta.get("upsert", {})
            for table in upsert_order:
                rows = upserts.get(table, [])
                if not rows:
                    continue
                pk = _PKS[table]
                # Determine conflict target
                if table == "entries":
                    conflict_target = "id"
                else:
                    conflict_target = pk
                for row in rows:
                    cols = list(row.keys())
                    vals = [row[c] for c in cols]
                    set_clause = ", ".join(
                        f"{c}=excluded.{c}" for c in cols if c != conflict_target
                    )
                    placeholders = ",".join("?" * len(cols))
                    col_list = ",".join(cols)
                    sql = (
                        f"INSERT INTO {table}({col_list}) VALUES ({placeholders}) "
                        f"ON CONFLICT({conflict_target}) DO UPDATE SET {set_clause}"
                    )
                    con.execute(sql, vals)

            # 3. changes_append (plain INSERT)
            changes_append = delta.get("changes_append", [])
            for cr in changes_append:
                cols = list(cr.keys())
                vals = [cr[c] for c in cols]
                placeholders = ",".join("?" * len(cols))
                col_list = ",".join(cols)
                con.execute(
                    f"INSERT OR IGNORE INTO changes({col_list}) VALUES ({placeholders})", vals
                )

            # 4. DELETE FROM changes WHERE ts < changes_prune_before
            prune_before = delta.get("changes_prune_before", 0)
            if prune_before:
                con.execute("DELETE FROM changes WHERE ts < ?", (prune_before,))

            # 5. meta updates
            meta = delta.get("meta", {})
            for key, value in meta.items():
                con.execute(
                    "INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (key, str(value))
                )

        con.execute("PRAGMA foreign_keys = ON")
    finally:
        con.close()


def tables_equal(db1: str, db2: str) -> tuple[bool, str]:
    """
    Compare all non-FTS tables between two databases.
    Returns (True, '') if equal, (False, reason) otherwise.
    """
    all_tables = _DATA_TABLES + ["changes", "meta"]

    con1 = sqlite3.connect(f"file:{db1}?mode=ro", uri=True)
    con2 = sqlite3.connect(f"file:{db2}?mode=ro", uri=True)
    try:
        for table in all_tables:
            rows1 = _read_all_rows(con1, table)
            rows2 = _read_all_rows(con2, table)
            if rows1 != rows2:
                return False, f"Table {table} differs: {len(rows1)} vs {len(rows2)} rows"
        return True, ""
    finally:
        con1.close()
        con2.close()
