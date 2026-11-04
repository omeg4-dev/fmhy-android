"""verify_feed: rows must be backed by the real diff of their commit."""

import os
import subprocess
from pathlib import Path

from fmhy_pipeline.feed import verify_feed

FIELDS = ["ts", "kind", "section_id", "name", "url", "detail", "commit_sha"]


def _git(repo, *args, env=None):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True, check=True, env=env).stdout.strip()


def _repo(tmp_path):
    repo = str(tmp_path / "r")
    os.makedirs(repo + "/docs")
    _git(repo, "init", "-b", "main")
    _git(repo, "config", "user.email", "t@t")
    _git(repo, "config", "user.name", "t")
    return repo


def _commit(repo, text, ts):
    Path(repo, "docs", "a.md").write_text(text)
    _git(repo, "add", "-A")
    env = {**os.environ, "GIT_COMMITTER_DATE": str(ts), "GIT_AUTHOR_DATE": str(ts)}
    _git(repo, "commit", "-m", "c", env=env)
    return _git(repo, "rev-parse", "HEAD")


def test_real_rows_are_backed_and_invented_rows_are_not(tmp_path):
    repo = _repo(tmp_path)
    _commit(repo, "# A\n* [One](https://one.example/) - x\n", 1_750_000_000)
    ts = 1_750_000_100
    sha = _commit(repo, "# A\n* [Two](https://two.example/) - y\n", ts)

    real = [[ts, "added", "a", "Two", "https://two.example/", "", sha],
            [ts, "removed", "a", "One", "https://one.example/", "", sha]]
    feed = {"fields": FIELDS, "changes": real}
    result = verify_feed(repo, feed)
    assert result["backed"] == 2 and result["share"] == 1.0

    invented = real + [[ts, "added", "a", "Fake", "https://fake.example/", "", sha],
                       [ts + 5, "added", "a", "Two", "https://two.example/", "", sha]]  # wrong commit time
    result = verify_feed(repo, {"fields": FIELDS, "changes": invented})
    assert result["backed"] == 2 and result["share"] == 0.5
    assert {r[3] for r in result["unbacked_sample"]} == {"Fake", "Two"}


def test_commit_sha_that_is_not_a_hash_is_never_passed_to_git(tmp_path):
    repo = _repo(tmp_path)
    _commit(repo, "# A\n", 1_750_000_000)
    for evil in ("--output=/tmp/should-not-exist", "-p", "HEAD", ""):
        feed = {"fields": FIELDS, "changes": [[1, "added", "a", "X", "https://x.example/", "", evil]]}
        assert verify_feed(repo, feed)["backed"] == 0
    assert not Path("/tmp/should-not-exist").exists()


def test_unknown_commit_is_not_backed(tmp_path):
    repo = _repo(tmp_path)
    _commit(repo, "# A\n", 1_750_000_000)
    feed = {"fields": FIELDS, "changes": [[1, "added", "a", "X", "https://x.example/", "", "f" * 40]]}
    assert verify_feed(repo, feed)["backed"] == 0
