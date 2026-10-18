"""VitePress-compatible slugify for heading anchors."""

import re
import unicodedata

# Emoji range - strip leading emoji from titles before slugifying
_EMOJI_RE = re.compile(
    r"^[\U0001F300-\U0001F9FF\U00002600-\U000027BF\U0000FE00-\U0000FE0F\U00020000-\U0002A6DF\U0002A700-\U0002B73F\U0002B740-\U0002B81F\U0002B820-\U0002CEAF\U0002CEB0-\U0002EBEF\U0001FA00-\U0001FA6F\U0001FA70-\U0001FAFF\u2194-\u2199\u21A9-\u21AA\u231A-\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA\u24C2\u25AA-\u25AB\u25B6\u25C0\u25FB-\u25FE\u2600-\u2604\u260E\u2611\u2614-\u2615\u2618\u261D\u2620\u2622-\u2623\u2626\u262A\u262E-\u262F\u2638-\u263A\u2640\u2642\u2648-\u2653\u265F-\u2660\u2663\u2665-\u2666\u2668\u267B\u267E-\u267F\u2692-\u2697\u2699\u269B-\u269C\u26A0-\u26A1\u26A7\u26AA-\u26AB\u26B0-\u26B1\u26BD-\u26BE\u26C4-\u26C5\u26CE-\u26CF\u26D1\u26D3-\u26D4\u26E9-\u26EA\u26F0-\u26F5\u26F7-\u26FA\u26FD\u2702\u2705\u2708-\u270D\u270F\u2712\u2714\u2716\u271D\u2721\u2728\u2733-\u2734\u2744\u2747\u274C\u274E\u2753-\u2755\u2757\u2763-\u2764\u2795-\u2797\u27A1\u27B0\u27BF\u2934-\u2935\u2B05-\u2B07\u2B1B-\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299\U0001F004\U0001F0CF\U0001F170-\U0001F171\U0001F17E-\U0001F17F\U0001F18E\U0001F191-\U0001F19A\U0001F1E0-\U0001F1FF\U0001F201-\U0001F202\U0001F21A\U0001F22F\U0001F232-\U0001F23A\U0001F250-\U0001F251]+"
)

# Characters to replace with hyphens (VitePress separator set)
_SEP_RE = re.compile(r"[\s~`!@#$%^&*()\-_+=\[\]{}|\\;:\"'''""<>,.?/]+")

# Combining diacritical marks range
_COMBINING_RE = re.compile(r"[\u0300-\u036f]")

# Control characters
_CTRL_RE = re.compile(r"[\x00-\x1f\x7f-\x9f]")


def strip_title_prefix(title: str) -> str:
    """Strip ► / ▷ markers and leading emoji from a title (for anchor generation)."""
    title = title.replace("►", "").replace("▷", "")
    # Strip leading emoji characters
    # Simple approach: strip any character in emoji ranges at start
    result = []
    i = 0
    in_prefix = True
    while i < len(title):
        ch = title[i]
        cp = ord(ch)
        if in_prefix and (
            # Common emoji ranges
            (0x1F300 <= cp <= 0x1FAFF)
            or (0x2600 <= cp <= 0x27BF)
            or (0xFE00 <= cp <= 0xFE0F)
            or (0x2190 <= cp <= 0x21FF)
            or (0x2300 <= cp <= 0x23FF)
            or (0x2700 <= cp <= 0x27BF)
            or (0x2B00 <= cp <= 0x2BFF)
            or (0x3000 <= cp <= 0x303F)
            or cp == 0xFE0F  # variation selector
        ):
            i += 1
            continue
        in_prefix = False
        result.append(ch)
        i += 1
    return "".join(result).lstrip()


def slugify(title: str) -> str:
    """
    VitePress-compatible slugify.
    
    Steps:
    1. Strip ► / ▷ markers and leading emoji
    2. NFKD normalize
    3. Drop combining marks (U+0300–U+036F)  
    4. Drop control chars
    5. Replace runs of separator chars with '-'
    6. Collapse '-', trim '-'
    7. Prefix '_' if starts with digit
    8. Lowercase
    """
    # Step 1: strip markers / leading emoji
    title = strip_title_prefix(title)

    # Step 2: NFKD normalize
    title = unicodedata.normalize("NFKD", title)

    # Step 3: drop combining marks
    title = _COMBINING_RE.sub("", title)

    # Step 4: drop control chars
    title = _CTRL_RE.sub("", title)

    # Step 5: replace separator runs with '-'
    title = _SEP_RE.sub("-", title)

    # Step 6: collapse and trim '-'
    title = title.strip("-")

    # Step 7: prefix '_' if starts with digit
    if title and title[0].isdigit():
        title = "_" + title

    # Step 8: lowercase
    return title.lower()


class AnchorTracker:
    """Track duplicate anchors within a page and suffix with -1, -2, ..."""

    def __init__(self) -> None:
        self._seen: dict[str, int] = {}

    def add(self, raw_anchor: str) -> str:
        """Return unique anchor for this page, deduplicating with -1, -2, ..."""
        if raw_anchor not in self._seen:
            self._seen[raw_anchor] = 0
            return raw_anchor
        self._seen[raw_anchor] += 1
        return f"{raw_anchor}-{self._seen[raw_anchor]}"
