"""History feed: the small file the app merges into its What's New history."""

import gzip
import json

import pytest

from fmhy_pipeline.feed import build_feed, write_feed
from fmhy_pipeline.history import HistoryDB

DAY = 86400
NOW = 1_800_000_000


def _change(ts, kind, url, sha, section="audio"):
    return {
        "ts": ts, "kind": kind, "entry_id": "e", "section_id": section,
        "name": "N", "url": url, "detail": "", "commit_sha": sha,
    }


@pytest.fixture
def hdb(tmp_path):
    db = HistoryDB(str(tmp_path / "history.sqlite"))
    db.append_change(_change(NOW - 200 * DAY, "added", "https://old.example", "old"))
    db.append_change(_change(NOW - 10 * DAY, "added", "https://a.example", "c1"))
    db.append_change(_change(NOW - 10 * DAY, "removed", "https://b.example", "c1"))
    db.append_change(_change(NOW - 2 * DAY, "starred", "https://c.example", "c2"))
    for i in range(500):
        db.append_change(_change(NOW - 5 * DAY, "added", f"https://bulk.example/{i}", "bulk"))
    db.set_last_commit("deadbeef")
    db.commit()
    yield db
    db.close()


def test_feed_keeps_window_and_drops_restructure_commits(hdb):
    feed = build_feed(hdb, now=NOW)
    assert feed["schema"] == 1
    assert feed["generated_at"] == NOW
    assert feed["source_commit"] == "deadbeef"
    urls = {c[4] for c in feed["changes"]}
    assert urls == {"https://a.example", "https://b.example", "https://c.example"}


def test_feed_rows_are_compact_arrays_oldest_first(hdb):
    feed = build_feed(hdb, now=NOW)
    # [ts, kind, section_id, name, url, detail, commit_sha]
    assert feed["fields"] == ["ts", "kind", "section_id", "name", "url", "detail", "commit_sha"]
    assert [c[0] for c in feed["changes"]] == sorted(c[0] for c in feed["changes"])
    assert all(len(c) == 7 for c in feed["changes"])


def test_write_feed_gz_and_meta(hdb, tmp_path):
    out = tmp_path / "out"
    meta = write_feed(build_feed(hdb, now=NOW), str(out))
    data = json.loads(gzip.decompress((out / "changes.json.gz").read_bytes()))
    assert len(data["changes"]) == 3
    assert meta == json.loads((out / "changes-meta.json").read_text())
    assert meta["count"] == 3 and meta["generated_at"] == NOW and len(meta["sha256"]) == 64


def test_write_feed_refuses_a_collapsed_feed(hdb, tmp_path):
    out = tmp_path / "out"
    write_feed(build_feed(hdb, now=NOW), str(out))
    empty = {"schema": 1, "generated_at": NOW, "source_commit": "x", "days": 120,
             "fields": [], "changes": []}
    with pytest.raises(RuntimeError):
        write_feed(empty, str(out))  # previous feed had rows: a sudden empty one is a bug
