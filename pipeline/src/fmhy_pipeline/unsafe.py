"""
Unsafe domains loader.

Reads filterlist-domains.txt and filterlists-reasons.json to produce
a list of (domain, reason) pairs.
"""

from __future__ import annotations

import json


def load_unsafe(domains_txt: str, reasons_json: str) -> list[tuple[str, str]]:
    """
    Load unsafe domains from filter list files.
    
    Args:
        domains_txt: Content of filterlist-domains.txt
        reasons_json: Content of filterlists-reasons.json
    
    Returns:
        List of (domain, reason) tuples, deduplicated.
    """
    try:
        reasons: dict[str, str] = json.loads(reasons_json)
    except Exception:
        reasons = {}

    seen: set[str] = set()
    result: list[tuple[str, str]] = []

    for line in domains_txt.splitlines():
        line = line.strip()
        # Skip empty lines, comments
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        domain = line.lower()
        if domain in seen:
            continue
        seen.add(domain)
        reason = reasons.get(domain, "")
        result.append((domain, reason))

    return result
