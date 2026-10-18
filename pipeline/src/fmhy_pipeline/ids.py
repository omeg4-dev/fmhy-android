"""Entry ID assignment: normalize URLs and generate stable IDs.

In Part A, IDs are generated fresh from the content.
In Part B, the history DB takes over for stable IDs across revisions.
"""

import functools
import hashlib
import re
import urllib.parse


@functools.lru_cache(maxsize=65536)
def normalize_url(u: str) -> str:
    """
    Normalize a URL for identity comparison and ID generation:
    - Lowercase scheme and host
    - Drop 'www.' prefix from host
    - Drop default port (80 for http, 443 for https)
    - Drop trailing '/' from path (but keep '/' if path is just '/')
    - Drop utm_*, ref, fbclid query params
    - Keep fragment
    """
    try:
        parsed = urllib.parse.urlparse(u)
    except Exception:
        return u.lower()

    scheme = parsed.scheme.lower()
    netloc = parsed.netloc.lower()

    # Drop www.
    if netloc.startswith("www."):
        netloc = netloc[4:]

    # Drop default port
    if ":" in netloc:
        host, port = netloc.rsplit(":", 1)
        if (scheme == "http" and port == "80") or (scheme == "https" and port == "443"):
            netloc = host

    # Path: drop trailing slash (spec: "drop trailing / of the path")
    path = parsed.path
    if path.endswith("/"):
        path = path.rstrip("/")  # For "/" this gives ""

    # Filter query params
    query = parsed.query
    if query:
        params = urllib.parse.parse_qsl(query, keep_blank_values=True)
        _REMOVE = {"ref", "fbclid"}
        filtered = [
            (k, v)
            for k, v in params
            if k not in _REMOVE and not k.startswith("utm_")
        ]
        query = urllib.parse.urlencode(filtered)

    # Reconstruct
    result = urllib.parse.urlunparse((scheme, netloc, path, parsed.params, query, parsed.fragment))
    return result


def entry_id(norm_url: str, section_id: str) -> str:
    """
    Generate a 12-hex-char entry ID from sha1(norm_url|section_id).
    Duplicates (same norm_url within same section) get -2, -3, ... in document order.
    This function returns the base ID; deduplication is handled by IdAssigner.
    """
    digest = hashlib.sha1(f"{norm_url}|{section_id}".encode()).hexdigest()
    return digest[:12]


class IdAssigner:
    """
    Assigns stable IDs to entries in document order.
    Duplicate norm_url within the same section gets -2, -3, ...
    """

    def __init__(self) -> None:
        # (norm_url, section_id) -> count of how many times seen
        self._counts: dict[tuple[str, str], int] = {}

    def assign(self, norm_url: str, section_id: str) -> str:
        key = (norm_url, section_id)
        count = self._counts.get(key, 0)
        self._counts[key] = count + 1
        base = entry_id(norm_url, section_id)
        if count == 0:
            return base
        return f"{base}-{count + 1}"
