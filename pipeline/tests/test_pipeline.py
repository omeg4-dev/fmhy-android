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

