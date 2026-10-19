"""
Publish pipeline: manage versioned output files (full + deltas).

Handles:
- Version management
- Delta computation and self-check
- Publish guards (entry drop, missing sections, unparsed ratio, schema)
- manifest.json generation

Format (per schema/README.md):
- full-<v>.sqlite.gz: gzip level 9 of SQLite file
- delta-<from>-<to>.json: plain compact UTF-8 JSON (no compression)
"""

from __future__ import annotations

import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
import time
from pathlib import Path
from typing import Optional

from .delta import apply_delta, compute_delta, tables_equal


def _sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _gzip_file(src: str, dst: str, level: int = 9) -> int:
    """Compress src to dst with gzip level 9. Returns compressed size."""
    with open(src, "rb") as fi, gzip.open(dst, "wb", compresslevel=level) as fo:
        shutil.copyfileobj(fi, fo)
    return Path(dst).stat().st_size


def _gunzip_file(src: str, dst: str) -> None:
    """Decompress src (.gz) to dst."""
    with gzip.open(src, "rb") as fi, open(dst, "wb") as fo:
        shutil.copyfileobj(fi, fo)


def load_manifest(out_dir: str) -> dict | None:
    """Load manifest.json from out_dir, or None if it doesn't exist."""
    p = Path(out_dir) / "manifest.json"
    if not p.exists():
        return None
    return json.loads(p.read_text())


def _find_full_gz(out_dir: str, manifest: dict) -> str | None:
    """Find the full .sqlite.gz path from the manifest."""
    full_info = manifest.get("full")
    if not full_info:
        return None
    p = Path(out_dir) / full_info["file"]
    return str(p) if p.exists() else None


def check_guards(
    stats: dict,
    manifest: dict | None,
    *,
    force: bool = False,
) -> list[str]:
    """
    Check publish guards. Returns list of violation messages.
    If force=True, returns empty list always.
    """
    if force:
        return []

    violations = []

    if manifest:
        # Check schema match
        if manifest.get("schema") != 2:
            violations.append(
                f"Schema mismatch: previous={manifest.get('schema')}, current=2"
            )

        # Check entry drop > 5%
        prev_entries = manifest.get("entries", 0)
        cur_entries = stats.get("entries", 0)
        if prev_entries > 0 and cur_entries < prev_entries * 0.95:
            drop_pct = (prev_entries - cur_entries) / prev_entries * 100
            violations.append(
                f"Entry drop {drop_pct:.1f}% > 5% (prev={prev_entries}, cur={cur_entries})"
            )

    # Check unparsed_ratio
    if stats.get("unparsed_ratio", 0) > 0.02:
        violations.append(
            f"unparsed_ratio={stats['unparsed_ratio']} > 0.02"
        )

    return violations


def publish(
    new_db_path: str,
    stats: dict,
    out_dir: str,
    state_dir: str,
    *,
    force: bool = False,
) -> dict:
    """
    Publish a new version to out_dir.
    
    Args:
        new_db_path: Path to the freshly built SQLite file
        stats: Build stats dict
        out_dir: Output directory (data/)
        state_dir: State directory (.cache/state/)
        force: Skip guards
    
    Returns:
        Result dict with version info, or {"unchanged": True, ...}
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    state = Path(state_dir)
    state.mkdir(parents=True, exist_ok=True)

    manifest = load_manifest(out_dir)

    # Guard checks
    violations = check_guards(stats, manifest, force=force)
    if violations:
        msg = "; ".join(violations)
        raise RuntimeError(f"Publish guard failed: {msg}")

    # Determine version
    old_version = manifest.get("version", 0) if manifest else 0
    new_version = old_version + 1

    # If there's a previous full, decompress it and compute delta
    prev_full_gz = _find_full_gz(out_dir, manifest) if manifest else None
    prev_full_db = None
    delta = None
    delta_bytes = None

    with tempfile.TemporaryDirectory(prefix="fmhy_publish_") as tmpdir:
        if prev_full_gz:
            prev_full_db = os.path.join(tmpdir, "old.sqlite")
            _gunzip_file(prev_full_gz, prev_full_db)

            # Full comparison (all non-FTS tables except meta.created/version)
            equal, reason = _check_content_equal(prev_full_db, new_db_path)
            if equal:
                # Nothing changed
                return {
                    "unchanged": True,
                    "version": old_version,
                    **{k: stats[k] for k in stats if k != "unparsed_lines"},
                }

            # Compute delta
            delta = compute_delta(prev_full_db, new_db_path, old_version, new_version)

            # Self-check: apply delta to old, compare with new
            check_db = os.path.join(tmpdir, "check.sqlite")
            shutil.copy2(prev_full_db, check_db)
            apply_delta(check_db, delta)
            ok, diff_reason = tables_equal(check_db, new_db_path)
            if not ok:
                raise RuntimeError(
                    f"Delta self-check FAILED: {diff_reason}. Aborting publish."
                )

        # Compress new full with gzip level 9
        new_full_file = f"full-{new_version}.sqlite.gz"
        new_full_path = str(out / new_full_file)
        gz_size = _gzip_file(new_db_path, new_full_path)
        sha = _sha256_file(new_full_path)
        raw_size = Path(new_db_path).stat().st_size

        # Write delta file if we have one (plain JSON, no compression)
        delta_entries = []
        if delta and prev_full_gz:
            delta_file = f"delta-{old_version}-{new_version}.json"
            delta_path = str(out / delta_file)
            delta_bytes = json.dumps(delta, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            with open(delta_path, "wb") as f:
                f.write(delta_bytes)

            delta_sha = _sha256_bytes(delta_bytes)
            delta_entries.append({
                "from": old_version,
                "to": new_version,
                "file": delta_file,
                "sha256": delta_sha,
                "size": len(delta_bytes),
            })

        # Keep old delta entries (up to 50 total, newest first)
        old_deltas = manifest.get("deltas", []) if manifest else []
        all_deltas = delta_entries + old_deltas
        all_deltas = all_deltas[:50]  # keep last 50

        # Build manifest
        new_manifest = {
            "schema": 2,
            "version": new_version,
            "created": int(time.time()),
            "source_commit": _get_meta(new_db_path, "source_commit"),
            "entries": stats["entries"],
            "full": {
                "file": new_full_file,
                "sha256": sha,
                "size": gz_size,
                "raw_size": raw_size,
            },
            "deltas": all_deltas,
        }

        # Remove old full file (keep only newest)
        if manifest and manifest.get("full"):
            old_full = out / manifest["full"]["file"]
            if old_full.exists() and old_full.name != new_full_file:
                old_full.unlink()

        # Prune old deltas beyond 50
        delta_files_to_keep = {d["file"] for d in all_deltas}
        for f in out.glob("delta-*.json"):
            if f.name not in delta_files_to_keep:
                f.unlink()

        # Write manifest
        manifest_path = out / "manifest.json"
        manifest_path.write_text(json.dumps(new_manifest, indent=2))

        return {
            "version": new_version,
            "gz_size": gz_size,
            **{k: stats[k] for k in stats if k not in ("unparsed_lines", "zst_size", "gz_size")},
        }


def _check_content_equal(old_db: str, new_db: str) -> tuple[bool, str]:
    """
    Check if two databases have the same content, ignoring meta.created and meta.version.
    """
    from .delta import _DATA_TABLES, _PKS, _read_all_rows

    tables = _DATA_TABLES + ["changes"]

    old_con = sqlite3.connect(f"file:{old_db}?mode=ro", uri=True)
    new_con = sqlite3.connect(f"file:{new_db}?mode=ro", uri=True)
    try:
        for table in tables:
            old_rows = _read_all_rows(old_con, table)
            new_rows = _read_all_rows(new_con, table)
            if old_rows != new_rows:
                return False, f"Table {table} differs"

        # Check meta excluding created/version
        old_meta = {r[0]: r[1] for r in old_con.execute("SELECT key, value FROM meta")}
        new_meta = {r[0]: r[1] for r in new_con.execute("SELECT key, value FROM meta")}
        skip = {"created", "version"}
        for key in set(old_meta.keys()) | set(new_meta.keys()):
            if key in skip:
                continue
            if old_meta.get(key) != new_meta.get(key):
                return False, f"meta.{key} differs"

        return True, ""
    finally:
        old_con.close()
        new_con.close()


def _get_meta(db_path: str, key: str) -> str:
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        row = con.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
        return row[0] if row else ""
    finally:
        con.close()
