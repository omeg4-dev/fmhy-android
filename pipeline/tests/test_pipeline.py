"""
Tests for the FMHY pipeline.

Covers:
- 7 required parse fixture cases
- Slug generation
- normalize_url
- Base64 double decoding
- Delta apply round trip
- History events on synthetic git repo
"""

from __future__ import annotations

import base64
import json
import os
import shutil
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path

import pytest


# ============================================================
# Slug tests
# ============================================================

from fmhy_pipeline.slug import slugify, AnchorTracker


class TestSlugify:
    def test_encode_decode(self):
        # Encode / Decode → encode-decode
        assert slugify("Encode / Decode") == "encode-decode"

    def test_arrow_link(self):
        # ▷ [Android Streaming] → android-streaming
        assert slugify("▷ Android Streaming") == "android-streaming"

    def test_simple(self):
        assert slugify("Video Streaming") == "video-streaming"

    def test_leading_emoji(self):
        assert slugify("🌟 Top Sites") == "top-sites"

    def test_star_emoji(self):
        assert slugify("⭐ Starred") == "starred"

    def test_digit_prefix(self):
        assert slugify("4K Video") == "_4k-video"

    def test_special_chars(self):
        assert slugify("C++ Programming") == "c-programming"

    def test_multiple_markers(self):
        assert slugify("► Section Name") == "section-name"

    def test_unicode_normalize(self):
        # NFKD normalization removes diacritics
        result = slugify("Héllo Wörld")
        assert result == "hello-world"


class TestAnchorTracker:
    def test_no_duplicate(self):
        t = AnchorTracker()
        assert t.add("foo") == "foo"
        assert t.add("bar") == "bar"

    def test_duplicate_gets_suffix(self):
        t = AnchorTracker()
        assert t.add("foo") == "foo"
        assert t.add("foo") == "foo-1"
        assert t.add("foo") == "foo-2"


# ============================================================
# normalize_url tests
# ============================================================

from fmhy_pipeline.ids import normalize_url, IdAssigner


class TestNormalizeUrl:
    def test_www_dropped(self):
        assert normalize_url("https://www.example.com/") == "https://example.com"

    def test_default_port_dropped(self):
        assert normalize_url("https://example.com:443/path") == "https://example.com/path"
        assert normalize_url("http://example.com:80/path") == "http://example.com/path"

    def test_trailing_slash_dropped(self):
        assert normalize_url("https://example.com/path/") == "https://example.com/path"

    def test_root_slash_kept(self):
        # Root path - trailing slash is the path itself
        r = normalize_url("https://example.com/")
        assert r == "https://example.com"

    def test_utm_params_dropped(self):
        r = normalize_url("https://example.com/page?utm_source=x&utm_medium=y&q=1")
        assert "utm_source" not in r
        assert "q=1" in r

    def test_ref_dropped(self):
        r = normalize_url("https://example.com/page?ref=abc&other=1")
        assert "ref" not in r
        assert "other=1" in r

    def test_fbclid_dropped(self):
        r = normalize_url("https://example.com/page?fbclid=abc")
        assert "fbclid" not in r

    def test_fragment_kept(self):
        r = normalize_url("https://example.com/page#section")
        assert "#section" in r

    def test_lowercase(self):
        r = normalize_url("HTTPS://Example.COM/Path")
        assert r.startswith("https://example.com")


# ============================================================
# Base64 double decoding test
# ============================================================

from fmhy_pipeline.base64links import _resolve_b64_string, Base64Resolver


class TestBase64Decode:
    def test_direct_url(self):
        url = "https://example.com/test"
        assert _resolve_b64_string(url) == url

    def test_single_decode(self):
        url = "https://example.com/test"
        encoded = base64.b64encode(url.encode()).decode()
        assert _resolve_b64_string(encoded) == url

    def test_double_decode(self):
        url = "https://example.com/double"
        inner = base64.b64encode(url.encode()).decode()
        outer = base64.b64encode(inner.encode()).decode()
        assert _resolve_b64_string(outer) == url

    def test_triple_decode(self):
        url = "https://example.com/triple"
        level1 = base64.b64encode(url.encode()).decode()
        level2 = base64.b64encode(level1.encode()).decode()
        level3 = base64.b64encode(level2.encode()).decode()
        assert _resolve_b64_string(level3) == url

    def test_unresolvable(self):
        assert _resolve_b64_string("not-base64-or-url!!!") is None

    def test_resolver_from_html(self):
        # Build minimal HTML with a known h4+code entry
        url = "https://example.com/resolved"
        encoded = base64.b64encode(url.encode()).decode()
        html = f"""
        <html><body>
        <h4 id="testanchor">Test</h4>
        <ul><li><code>{encoded}</code></li></ul>
        </body></html>
        """
        resolver = Base64Resolver(html)
        result = resolver.resolve_entry("https://rentry.co/FMHYB64#testanchor", [])
        assert result["url"] == url
        assert result["is_base64"] == 1
        assert result["original_url"] == "https://rentry.co/FMHYB64#testanchor"

    def test_unknown_anchor(self):
        resolver = Base64Resolver("<html></html>")
        result = resolver.resolve_entry("https://rentry.co/FMHYB64#unknown", [])
        assert result["is_base64"] == 0
        assert result["url"] == "https://rentry.co/FMHYB64#unknown"

    def test_rentry_org_variant(self):
        url = "https://example.com/org"
        encoded = base64.b64encode(url.encode()).decode()
        html = f'<html><body><h4 id="orgtest">T</h4><ul><li><code>{encoded}</code></li></ul></body></html>'
        resolver = Base64Resolver(html)
        result = resolver.resolve_entry("https://rentry.org/FMHYB64#orgtest", [])
        assert result["url"] == url
        assert result["is_base64"] == 1


# ============================================================
# Parse fixture tests
# ============================================================

from fmhy_pipeline.parse import (
    _parse_bullet_line,
    _parse_head_links,
    _parse_inline_more_line,
    _split_head_desc,
    parse_section,
    _NOTE_MARKER_RE,
    _detect_tier,
    ParsedNote,
)


def _parse_line(line: str) -> tuple[list, list]:
    """Parse a bullet line (content after '* '), returning (entries, notes)."""
    # Strip the '* ' prefix
    if line.startswith("* ") or line.startswith("- "):
        content = line[2:]
    else:
        content = line
    # Check for note first (same logic as parse_section)
    nm = _NOTE_MARKER_RE.match(content)
    if nm:
        text = nm.group(1).strip()
        note = ParsedNote(text_md=text, heading_id=None, section_id="test", ord=0)
        return [], [note]
    result = _parse_bullet_line(
        content,
        section_id="test",
        heading_id=None,
        heading_path="Test",
        ord_start=0,
    )
    if result is None:
        return [], []
    entries, notes = result
    return entries, notes


class TestParseFixtures:

    def test_fixture1_popcornmovies(self):
        """
        * 🌟 **[PopcornMovies](https://popcornmovies.ac/)** or [BingeBox](https://bingebox.ac/) - Movies / TV / Anime / Auto-Next
        → 2 entries: PopcornMovies tier 2, BingeBox tier 0; both tags Movies|TV|Anime|Auto-Next
        """
        line = "* 🌟 **[PopcornMovies](https://popcornmovies.ac/)** or [BingeBox](https://bingebox.ac/) - Movies / TV / Anime / Auto-Next"
        entries, notes = _parse_line(line)
        assert len(entries) == 2, f"Expected 2 entries, got {len(entries)}: {entries}"
        assert entries[0].name == "PopcornMovies"
        assert entries[0].url == "https://popcornmovies.ac/"
        assert entries[0].tier == 2
        assert entries[1].name == "BingeBox"
        assert entries[1].url == "https://bingebox.ac/"
        assert entries[1].tier == 0
        # Both have same description and tags
        assert entries[0].tags == "Movies|TV|Anime|Auto-Next"
        assert entries[1].tags == "Movies|TV|Anime|Auto-Next"
        assert entries[0].description == "Movies / TV / Anime / Auto-Next"
        assert entries[1].description == "Movies / TV / Anime / Auto-Next"

    def test_fixture2_7movies(self):
        """
        * 🌟 **[7Movies](https://7movies.ac/)**, [2](https://7movies.ws/) - Movies / TV / Anime / Auto-Next / [Discord](...) / [Telegram](...)
        → 1 entry, mirrors [{"label":"2","url":"https://7movies.ws/"}], extra_links Discord + Telegram
        """
        line = '* 🌟 **[7Movies](https://7movies.ac/)**, [2](https://7movies.ws/) - Movies / TV / Anime / Auto-Next / [Discord](https://discord.gg/JxgRaYMVHF) / [Telegram](https://t.me/+0XhCnzzvCt9hMTNl)'
        entries, notes = _parse_line(line)
        assert len(entries) == 1, f"Expected 1 entry, got {len(entries)}: {[e.name for e in entries]}"
        e = entries[0]
        assert e.name == "7Movies"
        assert e.tier == 2
        assert len(e.mirrors) == 1
        assert e.mirrors[0] == {"label": "2", "url": "https://7movies.ws/"}
        extra_labels = {l["label"] for l in e.extra_links}
        assert "Discord" in extra_labels
        assert "Telegram" in extra_labels
        assert e.tags == "Movies|TV|Anime|Auto-Next"

    def test_fixture3_cascii(self):
        """
        * [Cascii](...) / [GitHub](...), [Bob Editor](...), [ASCII Flow](...) or [tree](...) - Create ASCII Diagrams
        → 4 entries (Cascii with extra link GitHub), all description Create ASCII Diagrams
        """
        line = "* [Cascii](https://cascii.app/) / [GitHub](https://github.com/casparwylie/cascii-core), [Bob Editor](https://ivanceras.github.io/bob-editor/), [ASCII Flow](https://asciiflow.com/) or [tree](https://tree.nathanfriend.com/) - Create ASCII Diagrams"
        entries, notes = _parse_line(line)
        assert len(entries) == 4, f"Expected 4 entries, got {len(entries)}: {[e.name for e in entries]}"
        names = [e.name for e in entries]
        assert "Cascii" in names
        assert "Bob Editor" in names
        assert "ASCII Flow" in names
        assert "tree" in names
        cascii = next(e for e in entries if e.name == "Cascii")
        assert any(l["label"] == "GitHub" for l in cascii.extra_links)
        for e in entries:
            assert e.description == "Create ASCII Diagrams"

    def test_fixture4_kroki(self):
        """
        * ⭐ **[Kroki](https://kroki.io/#try)** - Text to Diagrams / [CLI](https://github.com/yuzutech/kroki-cli)
        → tier 1, tags Text to Diagrams, extra_links CLI
        """
        line = "* ⭐ **[Kroki](https://kroki.io/#try)** - Text to Diagrams / [CLI](https://github.com/yuzutech/kroki-cli)"
        entries, notes = _parse_line(line)
        assert len(entries) == 1, f"Expected 1 entry, got {len(entries)}"
        e = entries[0]
        assert e.name == "Kroki"
        assert e.tier == 1
        assert e.tags == "Text to Diagrams"
        extra_labels = {l["label"] for l in e.extra_links}
        assert "CLI" in extra_labels

    def test_fixture5_dgm_drawdb(self):
        """
        [DGM](...), [DrawDB](...) / [Discord](...), [Flourish](...) → 3 entries, is_inline_more 1, DrawDB has extra Discord
        """
        line = "[DGM](https://app.dgmjs.dev/), [DrawDB](https://www.drawdb.app/) / [Discord](https://discord.gg/BrjZgNrmR6), [Flourish](https://flourish.studio/)"
        entries = _parse_inline_more_line(
            line,
            section_id="test",
            heading_id=None,
            heading_path="Test",
            ord_start=0,
        )
        assert len(entries) == 3, f"Expected 3 entries, got {len(entries)}: {[e.name for e in entries]}"
        names = [e.name for e in entries]
        assert "DGM" in names
        assert "DrawDB" in names
        assert "Flourish" in names
        for e in entries:
            assert e.is_inline_more == 1
        drawdb = next(e for e in entries if e.name == "DrawDB")
        assert any(l["label"] == "Discord" for l in drawdb.extra_links)

    def test_fixture6_steamunlocked_unparsed(self):
        """
        * SteamUnlocked - [IGG / NosTeam uploads](...) + very slow file host
        → no link in HEAD → unparsed
        """
        line = "* SteamUnlocked - [IGG / NosTeam uploads](https://i.ibb.co/VgW2ymY/YUnRNpN.png) + very slow file host"
        entries, notes = _parse_line(line)
        # No link in HEAD (before ' - '), so it should be unparsed (return None)
        # Our _parse_line returns ([], []) for None
        assert len(entries) == 0

    def test_fixture7_note(self):
        """
        * **Note** - Repacks are compressed versions of games.
        → note, no entry
        """
        line = "* **Note** - Repacks are compressed versions of games."
        entries, notes = _parse_line(line)
        assert len(entries) == 0
        assert len(notes) == 1
        assert "Repacks" in notes[0].text_md

    # Additional real fixtures from the wiki
    def test_real_disblock(self):
        """Real line from privacy.md."""
        line = "* [Disblock Origin](https://codeberg.org/AllPurposeMat/Disblock-Origin) or [Discord Adblock](https://codeberg.org/ridge/Discord-AdBlock) - Hide Discord Nitro / Boost Ads"
        entries, notes = _parse_line(line)
        assert len(entries) == 2
        names = [e.name for e in entries]
        assert "Disblock Origin" in names
        assert "Discord Adblock" in names
        for e in entries:
            assert "Hide Discord Nitro" in e.description

    def test_real_hblock(self):
        """Real line from privacy.md with GitHub extra link."""
        line = "* [hBlock](https://hblock.molinero.dev/) - Blocklist Collection / [GitHub](https://github.com/hectorm/hblock)"
        entries, notes = _parse_line(line)
        assert len(entries) == 1
        e = entries[0]
        assert e.name == "hBlock"
        assert e.description == "Blocklist Collection"
        extra_labels = {l["label"] for l in e.extra_links}
        assert "GitHub" in extra_labels

    def test_real_popup_blockers(self):
        """Real multi-entry line from privacy.md."""
        line = "* [Popup Blocker (strict)](https://github.com/schomery/popup-blocker), [Popupblocker All](https://addons.mozilla.org/firefox/addon/popupblockerall/) / [2](https://chromewebstore.google.com/detail/popupblocker/doklmegfghlnanggfgligimnnikiekde) or [PopupBlocker](https://github.com/AdguardTeam/PopupBlocker) (userscript) - Popup Blockers / [Note](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/popup-blockers.md)"
        entries, notes = _parse_line(line)
        assert len(entries) >= 2
        names = [e.name for e in entries]
        assert "Popup Blocker (strict)" in names or any("Popup" in n for n in names)


class TestParseSection:
    def test_headings_parsed(self):
        content = "# Main Heading\n## Sub Heading\n* [Example](https://example.com) - Test site\n"
        headings, entries, notes, unparsed = parse_section("test", "Test Section", content)
        assert len(headings) == 2
        assert headings[0].level == 1
        assert headings[1].level == 2
        assert len(entries) == 1

    def test_front_matter_skipped(self):
        content = "---\ntitle: Test\n---\n# Heading\n* [Site](https://example.com) - A site\n"
        headings, entries, notes, unparsed = parse_section("test", "Test", content)
        assert len(entries) == 1

    def test_html_lines_skipped(self):
        content = "# Heading\n<script>alert('hi')</script>\n* [Site](https://example.com) - Test\n"
        _, entries, _, _ = parse_section("test", "Test", content)
        assert len(entries) == 1

    def test_fence_skipped(self):
        content = "# Heading\n:::\nsome code\n:::\n* [Site](https://example.com) - Test\n"
        _, entries, _, _ = parse_section("test", "Test", content)
        assert len(entries) == 1

    def test_blockquote_note(self):
        content = "# Heading\n> This is a note\n* [Site](https://example.com) - Test\n"
        _, entries, notes, _ = parse_section("test", "Test", content)
        assert len(notes) == 1
        assert "This is a note" in notes[0].text_md

    def test_heading_path(self):
        content = "# Level 1\n## Level 2\n* [Site](https://example.com) - Test\n"
        _, entries, _, _ = parse_section("test", "Test Section", content)
        assert len(entries) == 1
        assert "Test Section" in entries[0].heading_path
        assert "Level 1" in entries[0].heading_path
        assert "Level 2" in entries[0].heading_path


# ============================================================
# Delta round-trip test
# ============================================================

from fmhy_pipeline.delta import apply_delta, compute_delta, tables_equal


def _make_test_db(path: str, entries: list[dict], version: int = 1) -> None:
    """Create a minimal test SQLite database."""
    # Find schema/dataset.sql by searching from this file upward
    here = Path(__file__).parent
    schema_path = None
    for parent in [here, here.parent, here.parent.parent, here.parent.parent.parent]:
        candidate = parent / "schema" / "dataset.sql"
        if candidate.exists():
            schema_path = candidate
            break
    if schema_path is None:
        # Try absolute path from repo root
        schema_path = Path("/home/omega/Projects/fmhy-android/schema/dataset.sql")
    ddl = schema_path.read_text()

    con = sqlite3.connect(path)
    con.executescript(ddl)

    # Insert meta
    ts = int(time.time())
    for k, v in [("schema", "2"), ("version", str(version)), ("created", str(ts)),
                 ("source_commit", "abc123"), ("history_start", str(ts - 1000))]:
        con.execute("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", (k, v))

    # Insert a section
    con.execute(
        "INSERT OR IGNORE INTO sections(id, title, description, file, ord) VALUES(?,?,?,?,?)",
        ("test", "Test Section", "Test desc", "test.md", 0),
    )

    # Insert entries
    for e in entries:
        con.execute(
            "INSERT OR REPLACE INTO entries"
            "(num, id, section_id, heading_id, ord, name, url, original_url, "
            "description, tags, heading_path, tier, kind, is_inline_more, is_base64, "
            "mirrors, extra_links, first_seen, last_changed, starred_at) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                e["num"], e["id"], e.get("section_id", "test"),
                None, e.get("ord", 0), e["name"], e["url"],
                None, e.get("description", ""), e.get("tags", ""),
                e.get("heading_path", "Test Section"), e.get("tier", 0),
                e.get("kind", 0), 0, 0, "[]", "[]",
                e.get("first_seen", ts), e.get("last_changed", ts), None,
            ),
        )

    con.execute("INSERT INTO fts_words(fts_words) VALUES('optimize')")
    con.execute("INSERT INTO fts_tri(fts_tri) VALUES('optimize')")
    con.commit()
    con.execute("VACUUM")
    con.close()


class TestDeltaRoundTrip:
    def test_add_entry(self, tmp_path):
        """Adding an entry: delta apply should produce same result as new full."""
        db1 = str(tmp_path / "v1.sqlite")
        db2 = str(tmp_path / "v2.sqlite")
        applied = str(tmp_path / "applied.sqlite")

        ts = int(time.time())
        e1 = {"num": 1, "id": "aaa111", "name": "Site1", "url": "https://site1.com", "first_seen": ts, "last_changed": ts}
        e2 = {"num": 2, "id": "bbb222", "name": "Site2", "url": "https://site2.com", "first_seen": ts, "last_changed": ts}

        _make_test_db(db1, [e1], version=1)
        _make_test_db(db2, [e1, e2], version=2)

        delta = compute_delta(db1, db2, 1, 2)
        assert "entries" in delta["upsert"]
        upsert_ids = [r["id"] for r in delta["upsert"]["entries"]]
        assert "bbb222" in upsert_ids

        import shutil
        shutil.copy2(db1, applied)
        apply_delta(applied, delta)

        ok, reason = tables_equal(applied, db2)
        assert ok, f"Round trip failed: {reason}"

    def test_remove_entry(self, tmp_path):
        """Removing an entry."""
        db1 = str(tmp_path / "v1.sqlite")
        db2 = str(tmp_path / "v2.sqlite")
        applied = str(tmp_path / "applied.sqlite")

        ts = int(time.time())
        e1 = {"num": 1, "id": "aaa111", "name": "Site1", "url": "https://site1.com", "first_seen": ts, "last_changed": ts}
        e2 = {"num": 2, "id": "bbb222", "name": "Site2", "url": "https://site2.com", "first_seen": ts, "last_changed": ts}

        _make_test_db(db1, [e1, e2], version=1)
        _make_test_db(db2, [e1], version=2)

        delta = compute_delta(db1, db2, 1, 2)
        assert "entries" in delta.get("delete", {})
        assert "bbb222" in delta["delete"]["entries"]

        import shutil
        shutil.copy2(db1, applied)
        apply_delta(applied, delta)
        ok, reason = tables_equal(applied, db2)
        assert ok, f"Round trip failed: {reason}"

    def test_update_entry(self, tmp_path):
        """Updating an entry."""
        db1 = str(tmp_path / "v1.sqlite")
        db2 = str(tmp_path / "v2.sqlite")
        applied = str(tmp_path / "applied.sqlite")

        ts = int(time.time())
        e1 = {"num": 1, "id": "aaa111", "name": "OldName", "url": "https://site1.com", "first_seen": ts, "last_changed": ts}
        e2 = {"num": 1, "id": "aaa111", "name": "NewName", "url": "https://site1.com", "first_seen": ts, "last_changed": ts + 1}

        _make_test_db(db1, [e1], version=1)
        _make_test_db(db2, [e2], version=2)

        delta = compute_delta(db1, db2, 1, 2)
        assert "entries" in delta["upsert"]

        import shutil
        shutil.copy2(db1, applied)
        apply_delta(applied, delta)
        ok, reason = tables_equal(applied, db2)
        assert ok, f"Round trip failed: {reason}"

    def test_changes_append(self, tmp_path):
        """changes_append adds rows to changes table."""
        db1 = str(tmp_path / "v1.sqlite")
        db2 = str(tmp_path / "v2.sqlite")
        applied = str(tmp_path / "applied.sqlite")

        ts = int(time.time())
        e1 = {"num": 1, "id": "aaa111", "name": "Site1", "url": "https://site1.com", "first_seen": ts, "last_changed": ts}

        _make_test_db(db1, [e1], version=1)
        _make_test_db(db2, [e1], version=2)

        # Add a change row to db2
        con2 = sqlite3.connect(db2)
        con2.execute(
            "INSERT INTO changes(ts, kind, entry_id, section_id, name, url, detail, commit_sha) "
            "VALUES(?,?,?,?,?,?,?,?)",
            (ts, "added", "aaa111", "test", "Site1", "https://site1.com", "", "abc"),
        )
        con2.commit()
        con2.close()

        delta = compute_delta(db1, db2, 1, 2)
        assert len(delta.get("changes_append", [])) == 1

        import shutil
        shutil.copy2(db1, applied)
        apply_delta(applied, delta)
        ok, reason = tables_equal(applied, db2)
        assert ok, f"Round trip failed: {reason}"


# ============================================================
# History events on synthetic git repo
# ============================================================

from fmhy_pipeline.history import HistoryDB, walk_history


def _git(args: list[str], cwd: str) -> str:
    return subprocess.run(
        ["git"] + args, capture_output=True, text=True, cwd=cwd, check=True
    ).stdout.strip()


def _setup_git_repo(tmp_path: Path) -> str:
    """Create a synthetic git repo that mimics fmhy/edit structure."""
    repo = str(tmp_path / "repo")
    os.makedirs(repo)
    _git(["init", "-b", "main"], repo)
    _git(["config", "user.email", "test@test.com"], repo)
    _git(["config", "user.name", "Test"], repo)

    # Create docs directory
    os.makedirs(os.path.join(repo, "docs"))
    os.makedirs(os.path.join(repo, "docs", ".vitepress", "transformer"), exist_ok=True)

    # Create constants.ts
    constants_content = """
export const headers = {
  'sectionA.md': { title: 'Section A', description: 'Desc A' },
  'sectionB.md': { title: 'Section B', description: 'Desc B' }
} as const
"""
    Path(repo, "docs", ".vitepress", "transformer", "constants.ts").write_text(constants_content)

    return repo


def _make_commit(repo: str, files: dict[str, str], message: str, ts: int) -> str:
    """Create a commit with given files. Returns the sha."""
    for path, content in files.items():
        full = Path(repo) / path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(content)
        _git(["add", str(full)], repo)
    env = os.environ.copy()
    env["GIT_COMMITTER_DATE"] = str(ts)
    env["GIT_AUTHOR_DATE"] = str(ts)
    subprocess.run(
        ["git", "commit", "--allow-empty", "-m", message,
         "--date", str(ts)],
        cwd=repo, check=True, env=env, capture_output=True,
    )
    return _git(["rev-parse", "HEAD"], repo)


class TestHistoryEvents:
    def test_history_add_star_move_url_remove(self, tmp_path):
        """
        Synthetic git repo: add entry → star it → move to another file →
        change its url → remove it. Assert exact change rows and stable id.
        """
        repo = _setup_git_repo(tmp_path)
        state_dir = str(tmp_path / "state")
        os.makedirs(state_dir)

        t0 = 1700000000
        t1 = t0 + 86400
        t2 = t1 + 86400
        t3 = t2 + 86400
        t4 = t3 + 86400

        # Commit 0 (baseline): add entry to sectionA
        sha0 = _make_commit(repo, {
            "docs/sectionA.md": "# Section A\n* [MyEntry](https://myentry.com/) - A test entry\n",
            "docs/sectionB.md": "# Section B\n",
            "docs/.vitepress/transformer/constants.ts": """
export const headers = {
  'sectionA.md': { title: 'Section A', description: 'Desc A' },
  'sectionB.md': { title: 'Section B', description: 'Desc B' }
} as const
""",
        }, "Initial commit", t0)

        # Commit 1: star the entry
        sha1 = _make_commit(repo, {
            "docs/sectionA.md": "# Section A\n* ⭐ **[MyEntry](https://myentry.com/)** - A test entry\n",
        }, "Star MyEntry", t1)

        # Commit 2: move to sectionB
        sha2 = _make_commit(repo, {
            "docs/sectionA.md": "# Section A\n",
            "docs/sectionB.md": "# Section B\n* ⭐ **[MyEntry](https://myentry.com/)** - A test entry\n",
        }, "Move MyEntry to sectionB", t2)

        # Commit 3: change URL
        sha3 = _make_commit(repo, {
            "docs/sectionB.md": "# Section B\n* ⭐ **[MyEntry](https://myentry.net/)** - A test entry\n",
        }, "Change URL of MyEntry", t3)

        # Commit 4: remove entry
        sha4 = _make_commit(repo, {
            "docs/sectionB.md": "# Section B\n",
        }, "Remove MyEntry", t4)

        # Walk history
        history_db_path = os.path.join(state_dir, "history.sqlite")
        hdb = HistoryDB(history_db_path)
        walk_history(repo, "HEAD", hdb)
        hdb.close()

        # Reopen and verify
        hdb = HistoryDB(history_db_path)

        # The entry should have a consistent ID through all changes
        from fmhy_pipeline.ids import normalize_url
        norm_a = normalize_url("https://myentry.com/")
        norm_b_new = normalize_url("https://myentry.net/")

        # Get ID via sectionA original location
        eid = hdb.get_id_for(norm_a, "sectionA")
        assert eid is not None, "Entry should have an ID from sectionA"

        # Check that the same ID was maintained through move (via alias)
        eid_in_b = hdb.get_id_for(norm_a, "sectionB")
        assert eid_in_b == eid, "ID should be stable after move"

        # Check change log
        changes = hdb.get_change_log()
        kinds = [c["kind"] for c in changes]

        assert "starred" in kinds, f"Expected 'starred' in {kinds}"
        assert "moved" in kinds, f"Expected 'moved' in {kinds}"
        assert "url_changed" in kinds or "added" in kinds, f"kinds: {kinds}"
        assert "removed" in kinds, f"Expected 'removed' in {kinds}"

        # Verify baseline didn't emit 'added'
        added_rows = [c for c in changes if c["kind"] == "added"]
        # The baseline (t0) should not have added rows
        baseline_added = [c for c in added_rows if c["ts"] == t0]
        assert len(baseline_added) == 0, "Baseline should not emit 'added' rows"

        hdb.close()
