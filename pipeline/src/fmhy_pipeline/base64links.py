"""
Base64 link resolver for FMHY.

Parses the rentry.co/FMHYB64 HTML page (already downloaded) to build a
mapping of anchor → [decoded URLs].

Entries whose url is https://rentry.co/FMHYB64#X (or rentry.org variant)
get their URL replaced with the first decoded URL of X, and additional
decoded URLs appended as mirrors.
"""

from __future__ import annotations

import base64
import re
from html.parser import HTMLParser


# Rentry URL patterns
_RENTRY_RE = re.compile(
    r"^https?://(www\.)?rentry\.(co|org)/FMHYB64#(.+)$",
    re.IGNORECASE,
)


def _try_decode_b64(s: str) -> str | None:
    """
    Try to base64-decode s (after stripping whitespace and fixing padding).
    Returns the decoded string if it decodes cleanly to UTF-8, else None.
    """
    s = s.strip()
    # Fix padding
    missing = len(s) % 4
    if missing:
        s += "=" * (4 - missing)
    try:
        decoded = base64.b64decode(s).decode("utf-8")
        return decoded
    except Exception:
        return None


def _resolve_b64_string(s: str, max_rounds: int = 3) -> str | None:
    """
    Decode s repeatedly (up to max_rounds) until we get an http(s):// URL.
    Returns the URL or None if we can't resolve it.
    """
    current = s.strip()
    for _ in range(max_rounds):
        if current.startswith("http://") or current.startswith("https://"):
            return current
        decoded = _try_decode_b64(current)
        if decoded is None:
            return None
        current = decoded.strip()
    # Final check
    if current.startswith("http://") or current.startswith("https://"):
        return current
    return None


class _RentryParser(HTMLParser):
    """Parse the rentry HTML to extract h4#id → [code strings] mapping."""

    def __init__(self):
        super().__init__()
        self.anchor_codes: dict[str, list[str]] = {}  # anchor → list of code strings
        self._current_anchor: str | None = None
        self._in_code = False
        self._code_buf: list[str] = []

    def handle_starttag(self, tag, attrs):
        attr_dict = dict(attrs)
        if tag == "h4":
            anchor_id = attr_dict.get("id", "")
            if anchor_id:
                self._current_anchor = anchor_id
                if anchor_id not in self.anchor_codes:
                    self.anchor_codes[anchor_id] = []
        elif tag == "code" and self._current_anchor is not None:
            self._in_code = True
            self._code_buf = []

    def handle_endtag(self, tag):
        if tag == "code" and self._in_code:
            self._in_code = False
            code_text = "".join(self._code_buf).strip()
            if code_text and self._current_anchor is not None:
                self.anchor_codes[self._current_anchor].append(code_text)
        elif tag == "h4":
            pass  # keep current_anchor until next h4

    def handle_data(self, data):
        if self._in_code:
            self._code_buf.append(data)


def load_base64_map(html_content: str) -> dict[str, list[str]]:
    """
    Parse the rentry HTML and return a mapping: anchor_id → [decoded URLs].
    Unresolvable codes are excluded from the list.
    """
    parser = _RentryParser()
    parser.feed(html_content)

    result: dict[str, list[str]] = {}
    for anchor, codes in parser.anchor_codes.items():
        urls = []
        for code in codes:
            url = _resolve_b64_string(code)
            if url:
                urls.append(url)
        result[anchor] = urls
    return result


class Base64Resolver:
    """
    Resolves rentry.co/FMHYB64#X links to actual URLs.
    """

    def __init__(self, html_content: str) -> None:
        self._map = load_base64_map(html_content)
        self.resolved_count = 0
        self.unresolved_count = 0

    def resolve_entry(self, url: str, existing_mirrors: list[dict]) -> dict:
        """
        If url is a rentry FMHYB64 link, resolve it.
        Returns a dict with keys: url, original_url, is_base64, mirrors (updated).
        """
        m = _RENTRY_RE.match(url)
        if not m:
            return {"url": url, "original_url": None, "is_base64": 0, "mirrors": existing_mirrors}

        anchor = m.group(3)
        urls = self._map.get(anchor)

        if not urls:
            # Unknown anchor or undecodable
            self.unresolved_count += 1
            return {"url": url, "original_url": None, "is_base64": 0, "mirrors": existing_mirrors}

        self.resolved_count += 1
        primary = urls[0]
        original = url

        # Additional decoded URLs become mirrors labelled "2", "3", ...
        extra_mirrors = [
            {"label": str(idx + 2), "url": u}
            for idx, u in enumerate(urls[1:])
        ]
        # Prepend existing mirrors after the base64 ones
        all_mirrors = extra_mirrors + existing_mirrors

        return {
            "url": primary,
            "original_url": original,
            "is_base64": 1,
            "mirrors": all_mirrors,
        }
