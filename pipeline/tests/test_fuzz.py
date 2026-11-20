"""Fuzz/invariant probes for parse.parse_section (devenv D08).

Seeds are the real FMHY sections shared with the Kotlin FmhyParserFuzzTest
(core/data/src/test/resources/parse/fuzz/*.md). Each example picks a seed and applies 1-4
mutations; hypothesis shrinks failures and prints the falsifying example.

    uv run --project pipeline pytest pipeline/tests/test_fuzz.py -q
"""

from __future__ import annotations

import base64
import random
import re
from pathlib import Path

import pytest
from hypothesis import HealthCheck, Phase, given, settings
from hypothesis import strategies as st

from fmhy_pipeline.parse import parse_section

SEED_DIR = Path(__file__).resolve().parents[2] / "core/data/src/test/resources/parse/fuzz"
SEEDS = {p.name: p.read_text(encoding="utf-8") for p in sorted(SEED_DIR.glob("*.md"))}

# No shrink phase: shrinking 50 KB inputs took ~7 min; the failure message already names seed file + mutation seeds.
FUZZ = settings(max_examples=300, deadline=None, suppress_health_check=[HealthCheck.too_slow],
                phases=[Phase.explicit, Phase.reuse, Phase.generate])

UNICODE_BITS = ["‮", "‏", "​", "﻿", "é̈", "👨‍👩‍👧", "🇩🇪", "\ud800", "مرحبا", "שלום"]
SCHEMES = ["javascript:alert(1)", "ftp://x.org/", "magnet:?xt=urn:btih:abc", "//host.org/", "#anchor",
           "relative/path", "ttps://typo.org/", "HTTP://UPPER.ORG/", "https://ok.org/"]


def _lines(text: str) -> list[str]:
    return text.split("\n")


def m_drop(text: str, r: random.Random) -> str:
    ls = _lines(text)
    for _ in range(r.randint(1, 20)):
        if ls:
            ls.pop(r.randrange(len(ls)))
    return "\n".join(ls)


def m_duplicate(text: str, r: random.Random) -> str:
    ls = _lines(text)
    i = r.randrange(len(ls))
    ls[i:i] = [ls[i]] * r.randint(1, 5)
    return "\n".join(ls)


def m_cut(text: str, r: random.Random) -> str:
    ls = _lines(text)
    i = r.randrange(len(ls))
    line = ls[i]
    links = [m.start() for m in re.finditer(r"\]\(", line)]
    if links and r.random() < 0.5:
        pos = min(len(line), r.choice(links) + r.randint(1, 12))
    else:
        pos = r.randint(0, len(line))
    ls[i] = line[:pos]
    return "\n".join(ls)


def m_unicode(text: str, r: random.Random) -> str:
    chars = list(text)
    for _ in range(r.randint(1, 10)):
        chars.insert(r.randint(0, len(chars)), r.choice(UNICODE_BITS))
    return "".join(chars)


def m_huge_line(text: str, r: random.Random) -> str:
    alphabet = "[]()*abcxyz "
    parts: list[str] = []
    size = 0
    while size < 50_000:
        chunk = "https://" if r.random() < 0.05 else r.choice(alphabet)
        parts.append(chunk)
        size += len(chunk)
    ls = _lines(text)
    ls.insert(r.randint(0, len(ls)), "* " + "".join(parts))
    return "\n".join(ls)


def m_nest(text: str, r: random.Random) -> str:
    ls = _lines(text)
    for _ in range(r.randint(1, 15)):
        i = r.randrange(len(ls))
        ls[i] = " " * r.choice([2, 4, 8]) + r.choice(["* ", "- ", "1. "]) + ls[i].lstrip("*- ")
    return "\n".join(ls)


def m_pipes(text: str, r: random.Random) -> str:
    ls = _lines(text)
    for _ in range(r.randint(1, 5)):
        i = r.randrange(len(ls))
        ls[i] = r.choice(["| a | b |", "|", ls[i] + " | x", "|---|---|"]) if r.random() < 0.5 else ls[i].replace(" ", " | ", 1)
    return "\n".join(ls)


def m_base64(text: str, r: random.Random) -> str:
    blob = base64.b64encode(r.randbytes(r.randint(1500, 6000))).decode()
    line = f"* [x](https://rentry.co/FMHYB64#{blob})" if r.random() < 0.5 else blob
    ls = _lines(text)
    ls.insert(r.randint(0, len(ls)), line)
    return "\n".join(ls)


def m_crlf(text: str, r: random.Random) -> str:
    return text.replace("\n", "\r\n")


def m_tabs(text: str, r: random.Random) -> str:
    return text.replace("  ", "\t") if r.random() < 0.5 else text.replace(" ", "\t", r.randint(1, 50))


def m_unbalanced(text: str, r: random.Random) -> str:
    chars = list(text)
    for _ in range(r.randint(1, 10)):
        chars.insert(r.randint(0, len(chars)), r.choice(["**", "`", "[", "(", "]", ")"]))
    return "".join(chars)


def m_schemes(text: str, r: random.Random) -> str:
    def repl(m: re.Match) -> str:
        return f"]({r.choice(SCHEMES)})" if r.random() < 0.3 else m.group(0)

    return re.sub(r"\]\(https?://[^)\s]*\)", repl, text)


MUTATIONS = {f.__name__[2:]: f for f in (m_drop, m_duplicate, m_cut, m_unicode, m_huge_line, m_nest, m_pipes,
                                         m_base64, m_crlf, m_tabs, m_unbalanced, m_schemes)}
EDGE_INPUTS = ["", "# A\n## B\n### C\n#### D", "*"]


@st.composite
def fuzz_inputs(draw) -> tuple[str, list[tuple[str, int]]]:
    """Draw only (seed name, [(mutation, rng seed)]): small reprs, cheap shrinking, reproducible."""
    if draw(st.integers(0, 49)) == 0:
        return f"edge:{draw(st.integers(0, len(EDGE_INPUTS) - 1))}", []
    name = draw(st.sampled_from(sorted(SEEDS)))
    ops = draw(st.lists(st.tuples(st.sampled_from(sorted(MUTATIONS)), st.integers(0, 2**32 - 1)),
                        min_size=1, max_size=4))
    return name, ops


def build_input(name: str, ops: list[tuple[str, int]]) -> str:
    if name.startswith("edge:"):
        return EDGE_INPUTS[int(name[5:])]
    text = SEEDS[name]
    for op, seed in ops:
        text = MUTATIONS[op](text, random.Random(seed))
    return text


def _parse(text: str):
    return parse_section("fuzz", "Fuzz", text)


def _is_http(url: str) -> bool:
    return url.lower().startswith(("http://", "https://"))


def check_never_throws(text: str) -> None:
    _parse(text)


def check_links_http(text: str) -> None:
    _, entries, _, _ = _parse(text)
    for e in entries:
        urls = [e.url] + [m["url"] for m in e.mirrors] + [m["url"] for m in e.extra_links]
        bad = [u for u in urls if not _is_http(u)]
        assert not bad, f"non-http link kept: {bad[:3]} in entry {e.name!r} (line {e.line})"


def check_entry_count_bounded(text: str) -> None:
    _, entries, _, _ = _parse(text)
    bound = text.count("](") + text.lower().count("http")
    assert len(entries) <= bound, f"{len(entries)} entries > {bound} link markers"
    n_lines = len(text.splitlines())  # parse_section numbers lines 1-based over splitlines()
    out = [(e.name, e.line) for e in entries if not 1 <= e.line <= n_lines]
    assert not out, f"entry line outside 1..{n_lines}: {out[:3]}"


def check_deterministic(text: str) -> None:
    assert _parse(text) == _parse(text)


def check_no_empty_title(text: str) -> None:
    _, entries, _, _ = _parse(text)
    empty = [(e.url, e.line) for e in entries if not e.name.strip()]
    assert not empty, f"entries with blank name: {empty[:3]}"


CHECKS = [check_never_throws, check_links_http, check_entry_count_bounded, check_deterministic, check_no_empty_title]


@pytest.mark.parametrize("name", sorted(SEEDS))
@pytest.mark.parametrize("check", CHECKS, ids=lambda c: c.__name__)
def test_real_seeds_satisfy_invariants(check, name):
    check(SEEDS[name])


def _fuzz(check):
    @FUZZ
    @given(fuzz_inputs())
    def run(case):
        name, ops = case
        try:
            check(build_input(name, ops))
        except AssertionError as e:
            raise AssertionError(f"{e} [seed={name} mutations={ops}]") from None
    run()


def test_seeds_present():
    assert len(SEEDS) >= 3, f"fuzz seeds missing in {SEED_DIR}"


def test_fuzz_never_throws():
    _fuzz(check_never_throws)


@pytest.mark.xfail(strict=True, reason="hunt finding: parser keeps non-http link targets (javascript:, relative, ftp:)")
def test_fuzz_links_http():
    _fuzz(check_links_http)


def test_fuzz_entry_count_bounded():
    _fuzz(check_entry_count_bounded)


def test_fuzz_deterministic():
    _fuzz(check_deterministic)


@pytest.mark.xfail(strict=True, reason="hunt finding: blank entry name from empty or emoji-only label, e.g. [](url) / [⭐](url)")
def test_fuzz_no_empty_title():
    _fuzz(check_no_empty_title)
