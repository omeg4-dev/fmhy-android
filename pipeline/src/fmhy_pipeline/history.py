"""
History database for tracking entry identity and changes across git commits.

Schema:
- ids: stable id assignment (id PK, num UNIQUE, first_url, section_id)
- aliases: (norm_url, section_id) → id (for move/url_change tracking)
- entry_facts: per-entry (id, first_seen, last_changed, starred_at, tier, name, url, section_id)
- change_log: full change log
- meta: last_commit, history_start
"""

from __future__ import annotations

import collections
import os
import sqlite3
import subprocess
import time
from pathlib import Path
from typing import Optional

from .ids import entry_id, normalize_url
from .parse import parse_section
from .sections import _EXCLUDED


_HISTORY_DDL = """
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS ids (
    id         TEXT PRIMARY KEY,
    num        INTEGER UNIQUE NOT NULL,
    first_url  TEXT NOT NULL,
    section_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS aliases (
    norm_url   TEXT NOT NULL,
    section_id TEXT NOT NULL,
    id         TEXT NOT NULL REFERENCES ids(id),
    PRIMARY KEY (norm_url, section_id)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS entry_facts (
    id           TEXT PRIMARY KEY,
    first_seen   INTEGER NOT NULL,
    last_changed INTEGER NOT NULL,
    starred_at   INTEGER,
    tier         INTEGER NOT NULL DEFAULT 0,
    name         TEXT NOT NULL DEFAULT '',
    url          TEXT NOT NULL DEFAULT '',
    section_id   TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS change_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    ts         INTEGER NOT NULL,
    kind       TEXT NOT NULL,
    entry_id   TEXT NOT NULL,
    section_id TEXT NOT NULL,
    name       TEXT NOT NULL,
    url        TEXT NOT NULL,
    detail     TEXT NOT NULL DEFAULT '',
    commit_sha TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS change_log_ts ON change_log(ts DESC);
"""


class HistoryDB:
    """Manages the history SQLite database with in-memory caching and batched writes."""

    def __init__(self, db_path: str) -> None:
        self.db_path = db_path
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self.con = sqlite3.connect(db_path)
        self.con.row_factory = sqlite3.Row
        self.con.execute("PRAGMA journal_mode = WAL")
        self.con.execute("PRAGMA synchronous = NORMAL")
        self.con.executescript(_HISTORY_DDL)
        self.con.commit()

        # In-memory caches for fast lookups
        self._ids: dict[str, int] = {}
        self._aliases: dict[tuple[str, str], str] = {}
        self._facts: dict[str, dict] = {}

        for r in self.con.execute("SELECT id, num FROM ids"):
            self._ids[r[0]] = r[1]
        for r in self.con.execute("SELECT norm_url, section_id, id FROM aliases"):
            self._aliases[(r[0], r[1])] = r[2]
        for r in self.con.execute("SELECT * FROM entry_facts"):
            self._facts[r["id"]] = dict(r)

        self._next_num = max(self._ids.values(), default=0) + 1

        # Batched write buffers
        self._pending_ids: list[tuple] = []
        self._pending_aliases: list[tuple] = []
        self._pending_facts: dict[str, tuple] = {}
        self._pending_changes: list[tuple] = []

    def get_last_commit(self) -> str | None:
        row = self.con.execute(
            "SELECT value FROM meta WHERE key='last_commit'"
        ).fetchone()
        return row[0] if row else None

    def set_last_commit(self, sha: str) -> None:
        self.con.execute(
            "INSERT OR REPLACE INTO meta(key,value) VALUES('last_commit',?)", (sha,)
        )

    def get_history_start(self) -> int | None:
        row = self.con.execute(
            "SELECT value FROM meta WHERE key='history_start'"
        ).fetchone()
        return int(row[0]) if row else None

    def set_history_start(self, ts: int) -> None:
        self.con.execute(
            "INSERT OR REPLACE INTO meta(key,value) VALUES('history_start',?)", (str(ts),)
        )

    def assign_id(self, norm_url: str, section_id: str, first_url: str) -> tuple[str, int]:
        """
        Get or create a stable (id, num) for (norm_url, section_id).
        Returns (id, num).
        """
        key = (norm_url, section_id)
        if key in self._aliases:
            eid = self._aliases[key]
            return eid, self._ids[eid]

        base_id = entry_id(norm_url, section_id)
        candidate = base_id
        suffix = 2
        while True:
            if candidate in self._ids:
                facts = self._facts.get(candidate)
                if facts and normalize_url(facts.get("url", "")) == normalize_url(first_url):
                    self._aliases[key] = candidate
                    self._pending_aliases.append((norm_url, section_id, candidate))
                    return candidate, self._ids[candidate]
                candidate = f"{base_id}-{suffix}"
                suffix += 1
            else:
                num = self._next_num
                self._next_num += 1
                self._ids[candidate] = num
                self._aliases[key] = candidate
                self._pending_ids.append((candidate, num, first_url, section_id))
                self._pending_aliases.append((norm_url, section_id, candidate))
                return candidate, num

    def get_id_for(self, norm_url: str, section_id: str) -> str | None:
        return self._aliases.get((norm_url, section_id))

    def get_num(self, eid: str) -> int | None:
        return self._ids.get(eid)

    def get_facts(self, eid: str) -> dict | None:
        return self._facts.get(eid)

    def set_alias(self, norm_url: str, section_id: str, eid: str) -> None:
        self._aliases[(norm_url, section_id)] = eid
        self._pending_aliases.append((norm_url, section_id, eid))

    def upsert_facts(self, eid: str, facts: dict) -> None:
        self._facts[eid] = dict(facts)
        first_seen = facts.get("first_seen", int(time.time()))
        last_changed = facts.get("last_changed", first_seen)
        self._pending_facts[eid] = (
            eid,
            first_seen,
            last_changed,
            facts.get("starred_at"),
            facts.get("tier", 0),
            facts.get("name", ""),
            facts.get("url", ""),
            facts.get("section_id", ""),
        )

    def append_change(self, row: dict) -> None:
        self._pending_changes.append((
            row["ts"], row["kind"], row["entry_id"], row["section_id"],
            row["name"], row["url"], row.get("detail", ""), row["commit_sha"],
        ))

    def flush(self) -> None:
        """Flush pending batches to SQLite inside a single transaction."""
        with self.con:
            if self._pending_ids:
                self.con.executemany(
                    "INSERT INTO ids(id, num, first_url, section_id) VALUES(?,?,?,?)",
                    self._pending_ids,
                )
                self._pending_ids.clear()
            if self._pending_aliases:
                self.con.executemany(
                    "INSERT OR REPLACE INTO aliases(norm_url, section_id, id) VALUES(?,?,?)",
                    self._pending_aliases,
                )
                self._pending_aliases.clear()
            if self._pending_facts:
                self.con.executemany(
                    "INSERT OR REPLACE INTO entry_facts"
                    "(id, first_seen, last_changed, starred_at, tier, name, url, section_id) "
                    "VALUES (?,?,?,?,?,?,?,?)",
                    list(self._pending_facts.values()),
                )
                self._pending_facts.clear()
            if self._pending_changes:
                self.con.executemany(
                    "INSERT INTO change_log(ts, kind, entry_id, section_id, name, url, detail, commit_sha) "
                    "VALUES (?,?,?,?,?,?,?,?)",
                    self._pending_changes,
                )
                self._pending_changes.clear()

    def get_change_log(self, since_ts: int = 0) -> list[dict]:
        self.flush()
        rows = self.con.execute(
            "SELECT * FROM change_log WHERE ts >= ? ORDER BY id ASC", (since_ts,)
        ).fetchall()
        return [dict(r) for r in rows]

    def get_all_entry_facts(self) -> dict[str, dict]:
        return dict(self._facts)

    def get_all_nums(self) -> dict[str, int]:
        return dict(self._ids)

    def get_all_aliases(self) -> dict[tuple[str, str], str]:
        return dict(self._aliases)

    def commit(self) -> None:
        self.flush()
        self.con.commit()

    def close(self) -> None:
        self.commit()
        self.con.close()


class _BlobReader:
    """Reads git objects using a persistent git cat-file --batch process."""

    def __init__(self, repo: str) -> None:
        self.repo = repo
        self._proc = subprocess.Popen(
            ["git", "-C", repo, "cat-file", "--batch"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            text=False,
        )

    def read_blob(self, blob_sha: str) -> str:
        if not blob_sha or blob_sha.startswith("000000"):
            return ""
        self._proc.stdin.write(f"{blob_sha}\n".encode())
        self._proc.stdin.flush()
        header = self._proc.stdout.readline().decode()
        parts = header.split()
        if len(parts) < 3 or parts[1] != "blob":
            return ""
        size = int(parts[2])
        data = self._proc.stdout.read(size)
        self._proc.stdout.read(1)  # trailing newline
        return data.decode("utf-8", errors="replace")

    def close(self) -> None:
        try:
            self._proc.stdin.close()
            self._proc.stdout.close()
            self._proc.kill()
        except Exception:
            pass


class _BlobLRUCache:
    """Small bounded LRU cache for parsed blob entries."""

    def __init__(self, capacity: int = 64) -> None:
        self.capacity = capacity
        self.cache: collections.OrderedDict[tuple[str, str], dict] = collections.OrderedDict()

    def get(self, key: tuple[str, str]) -> dict | None:
        if key in self.cache:
            self.cache.move_to_end(key)
            return self.cache[key]
        return None

    def put(self, key: tuple[str, str], val: dict) -> None:
        self.cache[key] = val
        self.cache.move_to_end(key)
        if len(self.cache) > self.capacity:
            self.cache.popitem(last=False)


def _parse_entries_from_content(section_id: str, content: str) -> dict[tuple[str, str], dict]:
    """
    Parse a section file's content and return entries dict.
    Key is (norm_key, section_id), where norm_key handles within-file duplicates.
    Directly extracts entries to avoid heading AST, slugify, and note overhead during history walks.
    """
    if not content:
        return {}
    from .parse import (
        _FRONT_MATTER_RE, _FENCE_RE, _HTML_LINE_RE, _RULE_RE,
        _BACK_TO_WIKI_RE, _BACK_TO_TOP_RE, _HEADING_RE, _BULLET_RE,
        _NOTE_MARKER_RE, _BLOCKQUOTE_RE, _detect_tier, _split_head_desc,
        _parse_head_links, _parse_inline_more_line,
    )

    lines = content.splitlines()
    i = 0
    if lines and _FRONT_MATTER_RE.match(lines[0]):
        i = 1
        while i < len(lines):
            if _FRONT_MATTER_RE.match(lines[i]):
                i += 1
                break
            i += 1

    entries = []
    in_fence = False
    while i < len(lines):
        line = lines[i]
        i += 1
        if _FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if (in_fence or _HTML_LINE_RE.match(line) or _RULE_RE.match(line) or
                _BACK_TO_WIKI_RE.search(line) or _BACK_TO_TOP_RE.match(line.strip()) or
                _HEADING_RE.match(line) or _BLOCKQUOTE_RE.match(line)):
            continue
        bm = _BULLET_RE.match(line)
        if bm:
            bullet_content = bm.group(3)
            if _NOTE_MARKER_RE.match(bullet_content):
                continue
            tier, c = _detect_tier(bullet_content)
            head, _ = _split_head_desc(c)
            if "[" not in head:
                continue
            eds = _parse_head_links(head, tier, 0)
            entries.extend(eds)
            continue
        stripped = line.strip()
        if stripped and "[" in stripped:
            res = _parse_inline_more_line(stripped, section_id, None, "", 0)
            for e in res:
                entries.append({"name": e.name, "url": e.url, "tier": e.tier})

    result = {}
    occ_counts: dict[tuple[str, str], int] = {}
    for ed in entries:
        norm = normalize_url(ed["url"])
        base_key = (norm, section_id)
        occ = occ_counts.get(base_key, 0)
        occ_counts[base_key] = occ + 1
        norm_key = norm if occ == 0 else f"{norm}__dup{occ+1}"
        result[(norm_key, section_id)] = {
            "name": ed["name"],
            "url": ed["url"],
            "norm_url": norm,
            "norm_key": norm_key,
            "section_id": section_id,
            "tier": ed["tier"],
        }
    return result


def walk_history(
    repo: str,
    rev: str,
    history_db: HistoryDB,
    *,
    max_commits: Optional[int] = None,
) -> None:
    """
    Walk git history from rev, updating the history DB with changes.
    Only processes commits after history_db.get_last_commit().
    Uses current_file_entries to avoid redundant old blob parses,
    a bounded LRU cache for new blobs, and batched executemany writes.
    """
    last_commit = history_db.get_last_commit()
    rev_range = f"{last_commit}..{rev}" if last_commit else rev

    cmd = [
        "git", "-C", repo, "log",
        "--reverse", "--first-parent",
        "--raw", "--root", "--abbrev=40",
        "--format=COMMIT %H %ct",
        rev_range,
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)

    commits: list[tuple[str, int, list[tuple[str, str, str, str]]]] = []
    cur_sha: str | None = None
    cur_ts: int = 0
    cur_files: list[tuple[str, str, str, str]] = []

    for line in res.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("COMMIT "):
            if cur_sha:
                commits.append((cur_sha, cur_ts, cur_files))
                if max_commits and len(commits) >= max_commits:
                    break
            _, sha, ts_str = line.split()
            cur_sha = sha
            cur_ts = int(ts_str)
            cur_files = []
        elif line.startswith(":"):
            parts = line.split("\t", 1)
            meta = parts[0]
            path_part = parts[1]
            paths = path_part.split("\t")
            target_path = paths[-1]
            if (target_path.startswith("docs/") and
                    target_path.endswith(".md") and
                    target_path.count("/") == 1):
                fname = target_path[len("docs/"):]
                if fname not in _EXCLUDED:
                    meta_parts = meta.split()
                    old_blob = meta_parts[2]
                    new_blob = meta_parts[3]
                    status = meta_parts[4]
                    cur_files.append((target_path, old_blob, new_blob, status))

    if cur_sha and (not max_commits or len(commits) < max_commits):
        commits.append((cur_sha, cur_ts, cur_files))

    if not commits:
        return

    blob_reader = _BlobReader(repo)
    blob_lru = _BlobLRUCache(capacity=64)

    # Current parsed entry map per active file: path -> dict[(norm_key, section_id), einfo]
    # At most ~25 files in memory, avoiding all old_blob re-parsing and per-commit snapshots
    current_file_entries: dict[str, dict[tuple[str, str], dict]] = {}
    current_file_blobs: dict[str, str] = {}

    def get_new_entries(blob_sha: str, section_id: str) -> dict[tuple[str, str], dict]:
        if not blob_sha or blob_sha.startswith("000000"):
            return {}
        cache_key = (blob_sha, section_id)
        cached = blob_lru.get(cache_key)
        if cached is not None:
            return cached
        content = blob_reader.read_blob(blob_sha)
        parsed = _parse_entries_from_content(section_id, content)
        blob_lru.put(cache_key, parsed)
        return parsed

    is_first_commit = (last_commit is None)

    try:
        for commit_idx, (sha, ts, touched_files) in enumerate(commits):
            is_baseline = is_first_commit and (commit_idx == 0)

            if is_baseline:
                history_db.set_history_start(ts)
                for path, old_blob, new_blob, status in touched_files:
                    stem = path[len("docs/"):-3]
                    entries = get_new_entries(new_blob, stem)
                    current_file_entries[path] = entries
                    current_file_blobs[path] = new_blob
                    for (norm_key, sid), einfo in entries.items():
                        eid, num = history_db.assign_id(norm_key, sid, einfo["url"])
                        history_db.upsert_facts(eid, {
                            "first_seen": ts,
                            "last_changed": ts,
                            "starred_at": ts if einfo["tier"] >= 1 else None,
                            "tier": einfo["tier"],
                            "name": einfo["name"],
                            "url": einfo["url"],
                            "section_id": sid,
                        })
            else:
                if not touched_files:
                    history_db.set_last_commit(sha)
                    continue

                all_removed: dict[tuple[str, str], dict] = {}
                all_added: dict[tuple[str, str], dict] = {}
                unchanged_pairs: list[tuple[tuple[str, str], dict, dict]] = []

                for path, old_blob, new_blob, status in touched_files:
                    if old_blob == new_blob:
                        continue
                    stem = path[len("docs/"):-3]
                    par_entries = current_file_entries.get(path, {})
                    if current_file_blobs.get(path) == new_blob:
                        cur_entries = par_entries
                    else:
                        cur_entries = get_new_entries(new_blob, stem)
                        current_file_entries[path] = cur_entries
                        current_file_blobs[path] = new_blob

                    for key, info in par_entries.items():
                        if key not in cur_entries:
                            all_removed[key] = info

                    for key, info in cur_entries.items():
                        if key not in par_entries:
                            all_added[key] = info

                    for key in set(cur_entries.keys()) & set(par_entries.keys()):
                        unchanged_pairs.append((key, par_entries[key], cur_entries[key]))

                processed_removed: set[tuple[str, str]] = set()
                processed_added: set[tuple[str, str]] = set()
                changes: list[dict] = []

                # 1. Detect moves: removed from section A + added to section B with same norm_url
                removed_by_norm: dict[str, list[tuple[tuple[str, str], dict]]] = {}
                added_by_norm: dict[str, list[tuple[tuple[str, str], dict]]] = {}

                for key, info in all_removed.items():
                    removed_by_norm.setdefault(info["norm_url"], []).append((key, info))

                for key, info in all_added.items():
                    added_by_norm.setdefault(info["norm_url"], []).append((key, info))

                for norm, rem_list in removed_by_norm.items():
                    if norm in added_by_norm:
                        add_list = added_by_norm[norm]
                        for rem_key, rem_info in rem_list:
                            for add_key, add_info in add_list:
                                if rem_key not in processed_removed and add_key not in processed_added:
                                    if rem_key[1] != add_key[1]:  # different sections
                                        eid = history_db.get_id_for(rem_key[0], rem_key[1])
                                        if eid is None:
                                            eid, _ = history_db.assign_id(rem_key[0], rem_key[1], rem_info["url"])
                                        history_db.set_alias(add_key[0], add_key[1], eid)
                                        changes.append({
                                            "ts": ts,
                                            "kind": "moved",
                                            "entry_id": eid,
                                            "section_id": add_key[1],
                                            "name": add_info["name"],
                                            "url": add_info["url"],
                                            "detail": rem_key[1],
                                            "commit_sha": sha,
                                        })
                                        facts = history_db.get_facts(eid) or {}
                                        facts.setdefault("first_seen", ts)
                                        facts.update({
                                            "last_changed": ts,
                                            "tier": add_info["tier"],
                                            "name": add_info["name"],
                                            "url": add_info["url"],
                                            "section_id": add_key[1],
                                        })
                                        history_db.upsert_facts(eid, facts)
                                        processed_removed.add(rem_key)
                                        processed_added.add(add_key)

                # 2. Detect url_changed: same section, removed + added with same lowercase name
                removed_by_section: dict[str, list[tuple[tuple[str, str], dict]]] = {}
                added_by_section: dict[str, list[tuple[tuple[str, str], dict]]] = {}

                for key, info in all_removed.items():
                    if key not in processed_removed:
                        removed_by_section.setdefault(key[1], []).append((key, info))

                for key, info in all_added.items():
                    if key not in processed_added:
                        added_by_section.setdefault(key[1], []).append((key, info))

                for section_id, rem_list in removed_by_section.items():
                    add_list = added_by_section.get(section_id, [])
                    for rem_key, rem_info in rem_list:
                        if rem_key in processed_removed:
                            continue
                        for add_key, add_info in add_list:
                            if add_key in processed_added:
                                continue
                            if (rem_info["name"].lower() == add_info["name"].lower() and
                                    rem_info["norm_url"] != add_info["norm_url"]):
                                eid = history_db.get_id_for(rem_key[0], section_id)
                                if eid is None:
                                    eid, _ = history_db.assign_id(rem_key[0], section_id, rem_info["url"])
                                history_db.set_alias(add_key[0], section_id, eid)
                                changes.append({
                                    "ts": ts,
                                    "kind": "url_changed",
                                    "entry_id": eid,
                                    "section_id": section_id,
                                    "name": add_info["name"],
                                    "url": add_info["url"],
                                    "detail": rem_info["url"],
                                    "commit_sha": sha,
                                })
                                facts = history_db.get_facts(eid) or {}
                                facts.setdefault("first_seen", ts)
                                facts.update({
                                    "last_changed": ts,
                                    "tier": add_info["tier"],
                                    "name": add_info["name"],
                                    "url": add_info["url"],
                                    "section_id": section_id,
                                })
                                history_db.upsert_facts(eid, facts)
                                processed_removed.add(rem_key)
                                processed_added.add(add_key)
                                break

                # 3. Remaining removed → 'removed'
                for key, info in all_removed.items():
                    if key in processed_removed:
                        continue
                    eid = history_db.get_id_for(key[0], key[1])
                    if eid is None:
                        eid, _ = history_db.assign_id(key[0], key[1], info["url"])
                    changes.append({
                        "ts": ts,
                        "kind": "removed",
                        "entry_id": eid,
                        "section_id": key[1],
                        "name": info["name"],
                        "url": info["url"],
                        "detail": "",
                        "commit_sha": sha,
                    })
                    processed_removed.add(key)

                # 4. Remaining added → 'added'
                for key, info in all_added.items():
                    if key in processed_added:
                        continue
                    eid, _ = history_db.assign_id(key[0], key[1], info["url"])
                    existing_facts = history_db.get_facts(eid)
                    changes.append({
                        "ts": ts,
                        "kind": "added",
                        "entry_id": eid,
                        "section_id": key[1],
                        "name": info["name"],
                        "url": info["url"],
                        "detail": "",
                        "commit_sha": sha,
                    })
                    first_seen = existing_facts["first_seen"] if existing_facts else ts
                    starred_at = existing_facts.get("starred_at") if existing_facts else None
                    if info["tier"] >= 1 and (existing_facts is None or existing_facts.get("tier", 0) == 0):
                        starred_at = ts
                    history_db.upsert_facts(eid, {
                        "first_seen": first_seen,
                        "last_changed": ts,
                        "starred_at": starred_at,
                        "tier": info["tier"],
                        "name": info["name"],
                        "url": info["url"],
                        "section_id": key[1],
                    })
                    processed_added.add(key)

                # 5. Unchanged keys: check renamed, starred, unstarred
                for key, par_e, cur_e in unchanged_pairs:
                    eid = history_db.get_id_for(key[0], key[1])
                    if eid is None:
                        continue
                    existing_facts = history_db.get_facts(eid)
                    if existing_facts is None:
                        continue

                    changed = False
                    new_facts = dict(existing_facts)

                    # Renamed
                    if cur_e["name"].lower() != par_e["name"].lower():
                        changes.append({
                            "ts": ts,
                            "kind": "renamed",
                            "entry_id": eid,
                            "section_id": key[1],
                            "name": cur_e["name"],
                            "url": cur_e["url"],
                            "detail": par_e["name"],
                            "commit_sha": sha,
                        })
                        new_facts["name"] = cur_e["name"]
                        new_facts["last_changed"] = ts
                        changed = True

                    # Starred: tier 0 → >= 1
                    if cur_e["tier"] >= 1 and par_e["tier"] == 0:
                        changes.append({
                            "ts": ts,
                            "kind": "starred",
                            "entry_id": eid,
                            "section_id": key[1],
                            "name": cur_e["name"],
                            "url": cur_e["url"],
                            "detail": "",
                            "commit_sha": sha,
                        })
                        new_facts["starred_at"] = ts
                        new_facts["tier"] = cur_e["tier"]
                        new_facts["last_changed"] = ts
                        changed = True
                    # Unstarred: tier >= 1 → 0
                    elif cur_e["tier"] == 0 and par_e["tier"] >= 1:
                        changes.append({
                            "ts": ts,
                            "kind": "unstarred",
                            "entry_id": eid,
                            "section_id": key[1],
                            "name": cur_e["name"],
                            "url": cur_e["url"],
                            "detail": "",
                            "commit_sha": sha,
                        })
                        new_facts["tier"] = 0
                        new_facts["last_changed"] = ts
                        changed = True

                    if changed:
                        history_db.upsert_facts(eid, new_facts)

                for ch in changes:
                    history_db.append_change(ch)

            history_db.set_last_commit(sha)
            if commit_idx % 200 == 0:
                history_db.commit()

        history_db.commit()

    finally:
        blob_reader.close()
