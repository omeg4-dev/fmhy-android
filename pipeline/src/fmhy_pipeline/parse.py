"""
FMHY markdown parser.

Produces structured entries, headings, and notes from a docs/*.md page.
Uses markdown-it-py for inline parsing (no regex-only link extraction).
"""

from __future__ import annotations

import re
import json
from dataclasses import dataclass, field
from typing import Optional

from markdown_it import MarkdownIt

_MD = MarkdownIt()
_MD.normalizeLink = lambda url: url
_MD.inline.ruler.disable(["linkify", "strikethrough", "autolink", "entity"])
_MD.inline.ruler2.disable(["strikethrough"])


# ---------------------------------------------------------------------------
# Tier detection
# ---------------------------------------------------------------------------
_INVISIBLE_TRANSLATE = str.maketrans("", "", "\u2060\u200b\u200c\ufeff")

def strip_invisible_chars(text: str) -> str:
    return text.translate(_INVISIBLE_TRANSLATE)


def _detect_tier_and_kind(line: str) -> tuple[int, int, str]:
    tier = 0
    kind = 0
    s = line.lstrip()
    for _ in range(2):
        s = s.lstrip()
        if tier == 0:
            if s.startswith("🌟"):
                tier = 2
                s = s[len("🌟"):]
                continue
            elif s.startswith("⭐"):
                tier = 1
                s = s[len("⭐"):]
                continue
        if kind == 0:
            if s.startswith("🌐"):
                kind = 1
                s = s[len("🌐"):]
                if s.startswith("\ufe0f"):
                    s = s[1:]
                continue
            elif s.startswith("↪️"):
                kind = 2
                s = s[len("↪️"):]
                continue
            elif s.startswith("↪"):
                kind = 2
                s = s[len("↪"):]
                if s.startswith("\ufe0f"):
                    s = s[1:]
                continue
    return tier, kind, s.lstrip()


def _detect_tier(line: str) -> tuple[int, str]:
    tier, _, rest = _detect_tier_and_kind(line)
    return tier, rest


# ---------------------------------------------------------------------------
# Emoji stripping for entry names
# ---------------------------------------------------------------------------

_LEADING_EMOJI_RE = re.compile(
    r"^([\U0001F000-\U0001FFFF\U00002600-\U000027BF\U0001F300-\U0001FAFF"
    r"\u2194-\u2199\u21A9-\u21AA\u231A-\u231B\u2328\u23CF\u23E9-\u23F3"
    r"\u23F8-\u23FA\u24C2\u25AA-\u25AB\u25B6\u25C0\u25FB-\u25FE"
    r"\u2600-\u2604\u260E\u2611\u2614-\u2615\u2618\u261D\u2620"
    r"\u2622-\u2623\u2626\u262A\u262E-\u262F\u2638-\u263A\u2640\u2642"
    r"\u2648-\u2653\u265F-\u2660\u2663\u2665-\u2666\u2668\u267B"
    r"\u267E-\u267F\u2692-\u2697\u2699\u269B-\u269C\u26A0-\u26A1\u26A7"
    r"\u26AA-\u26AB\u26B0-\u26B1\u26BD-\u26BE\u26C4-\u26C5\u26CE-\u26CF"
    r"\u26D1\u26D3-\u26D4\u26E9-\u26EA\u26F0-\u26F5\u26F7-\u26FA\u26FD"
    r"\u2702\u2705\u2708-\u270D\u270F\u2712\u2714\u2716\u271D\u2721\u2728"
    r"\u2733-\u2734\u2744\u2747\u274C\u274E\u2753-\u2755\u2757\u2763-\u2764"
    r"\u2795-\u2797\u27A1\u27B0\u27BF\u2934-\u2935\u2B05-\u2B07"
    r"\u2B1B-\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299"
    r"\U0001F004\U0001F0CF\U0001F170-\U0001F171\U0001F17E-\U0001F17F"
    r"\U0001F18E\U0001F191-\U0001F19A\U0001F1E0-\U0001F1FF"
    r"\U0001F201-\U0001F202\U0001F21A\U0001F22F\U0001F232-\U0001F23A"
    r"\U0001F250-\U0001F251\uFE0F\u200D]|\u200D)+",
    re.UNICODE,
)


def strip_leading_emoji(text: str) -> str:
    """Strip leading emoji from a string."""
    return _LEADING_EMOJI_RE.sub("", text).lstrip()


# ---------------------------------------------------------------------------
# Inline token extraction via markdown-it-py
# ---------------------------------------------------------------------------

@dataclass
class InlineLink:
    label: str
    url: str
    # character position in the source text (approximate, used for splitting)
    pos: int = 0


@dataclass
class InlineToken:
    """A token from the inline parse: either a link or plain text."""
    kind: str  # 'link' | 'text' | 'bold_open' | 'bold_close' | 'softbreak' | 'other'
    text: str = ""
    url: str = ""
    children: list["InlineToken"] = field(default_factory=list)


def _extract_inline_links(text: str) -> list[InlineLink]:
    """
    Use markdown-it-py to extract all links from an inline markdown string.
    Returns list of InlineLink in order.
    """
    tokens = _MD.parseInline(text)
    links: list[InlineLink] = []
    for tok_block in tokens:
        children = tok_block.children or []
        i = 0
        while i < len(children):
            t = children[i]
            if t.type == "link_open":
                href = t.attrs.get("href", "") or ""
                # collect label from inline children until link_close
                label_parts = []
                i += 1
                while i < len(children) and children[i].type != "link_close":
                    if children[i].type in ("text", "code_inline", "softbreak"):
                        label_parts.append(children[i].content)
                    elif children[i].type == "image":
                        label_parts.append(children[i].attrs.get("alt", ""))
                    i += 1
                label = "".join(label_parts)
                links.append(InlineLink(label=label, url=href))
            i += 1
    return links


# ---------------------------------------------------------------------------
# HEAD splitting logic
# ---------------------------------------------------------------------------

def _is_number_label(label: str) -> bool:
    """Return True if the label is a plain integer >= 2 (mirror indicator)."""
    return bool(re.match(r"^\d+$", label)) and int(label) >= 2


def _is_url(s: str) -> bool:
    return s.startswith("http://") or s.startswith("https://")


# Regex to split text on ` - ` that is NOT inside [...] link labels
# We use a state machine approach via the token stream instead.

def _split_head_desc(line: str) -> tuple[str, str]:
    """
    Split a line (after bullet marker) at the first ' - ' that is NOT inside
    link text or URL brackets. Returns (HEAD, DESC).
    If no such split point, returns (line, '').
    
    Algorithm: Walk the string char by char, tracking bracket depth.
    A '[' opens bracket (link label), '](' closes it after the URL is consumed.
    """
    depth = 0  # bracket depth
    i = 0
    n = len(line)
    in_link_url = False  # after ]( until )

    while i < n:
        ch = line[i]

        if ch == "[":
            depth += 1
        elif ch == "]":
            depth -= 1
            if depth < 0:
                depth = 0
            # Check if next char is '(' (link URL)
            if i + 1 < n and line[i + 1] == "(":
                in_link_url = True
                i += 2  # skip ](
                # skip past the URL until matching )
                while i < n and line[i] != ")":
                    i += 1
                in_link_url = False
                if i < n:
                    i += 1  # skip ')'
                continue
        elif ch == "(" and depth == 0 and not in_link_url:
            pass  # bare parens, depth 0

        # Check for ' - ' separator when depth == 0
        if depth == 0 and not in_link_url:
            if line[i : i + 3] == " - ":
                return line[:i], line[i + 3:]

        i += 1

    return line, ""


# ---------------------------------------------------------------------------
# Entry dataclass
# ---------------------------------------------------------------------------

@dataclass
class ParsedEntry:
    name: str
    url: str
    tier: int = 0
    kind: int = 0
    line: int = 0
    is_inline_more: int = 0
    mirrors: list[dict] = field(default_factory=list)
    extra_links: list[dict] = field(default_factory=list)
    description: str = ""
    tags: str = ""
    heading_id: Optional[str] = None
    heading_path: str = ""
    section_id: str = ""
    ord: int = 0


@dataclass
class ParsedNote:
    text_md: str
    heading_id: Optional[str] = None
    section_id: str = ""
    ord: int = 0


@dataclass
class ParsedHeading:
    level: int  # 1..4
    title: str
    anchor: str
    parent_id: Optional[str] = None
    id: str = ""
    section_id: str = ""
    ord: int = 0


# ---------------------------------------------------------------------------
# HEAD parser: walks links to produce entries
# ---------------------------------------------------------------------------

def _parse_head_links(
    head_text: str,
    tier: int,
    is_inline_more: int,
    line_kind: int = 0,
    line: int = 0,
) -> list[dict]:
    """
    Walk the links in HEAD text and produce a list of partial entry dicts.
    Each dict has: name, url, tier, kind, line, is_inline_more, mirrors, extra_links.

    Rules:
    - First link encountered (not preceded by ' / ') starts a NEW entry.
    - A link with numeric label (>= 2) is a MIRROR of the current entry.
    - A link preceded by ' / ' in the source text is an EXTRA LINK of the current entry.
    - Separators ', ' or ' or ' start a NEW entry.
    - Tier applies to all entries on the line.
    """
    tokens = _MD.parseInline(head_text)
    if not tokens:
        return []

    children = tokens[0].children or []

    entries: list[dict] = []
    current: dict | None = None
    in_bold = False
    prev_text_tail = ""  # text content between links (for separator detection)

    def finish_entry():
        nonlocal current
        if current:
            entries.append(current)
        current = None

    def new_entry(name, url, link_tier, link_kind):
        nonlocal current
        finish_entry()
        current = {
            "name": strip_leading_emoji(name).strip(),
            "url": url,
            "tier": link_tier,
            "kind": link_kind,
            "line": line,
            "is_inline_more": is_inline_more,
            "mirrors": [],
            "extra_links": [],
        }

    i = 0
    while i < len(children):
        tok = children[i]

        if tok.type == "strong_open":
            in_bold = True
            i += 1
            continue
        elif tok.type == "strong_close":
            in_bold = False
            i += 1
            continue
        elif tok.type in ("text", "softbreak", "html_inline", "code_inline"):
            prev_text_tail += tok.content
            i += 1
            continue
        elif tok.type == "link_open":
            href = tok.attrs.get("href", "") or ""
            # Collect label
            label_parts = []
            i += 1
            while i < len(children) and children[i].type != "link_close":
                ct = children[i]
                if ct.type in ("text", "code_inline"):
                    label_parts.append(ct.content)
                elif ct.type == "softbreak":
                    label_parts.append(" ")
                elif ct.type == "image":
                    label_parts.append(ct.attrs.get("alt", ""))
                elif ct.type in ("strong_open", "strong_close", "em_open", "em_close"):
                    pass
                else:
                    label_parts.append(ct.content or "")
                i += 1
            label = "".join(label_parts)

            # Determine what this link is
            sep_text = prev_text_tail
            prev_text_tail = ""

            if _is_number_label(label) and current is not None:
                # Mirror
                current["mirrors"].append({"label": label, "url": href})
            else:
                # Check if preceded by ' / ' → extra link
                is_extra = " / " in sep_text or sep_text.strip() == "/"
                # Also treat it as extra if no current entry yet and we see a '/'
                
                if is_extra and current is not None:
                    current["extra_links"].append({"label": label, "url": href})
                else:
                    # Check if this is a separator-started new entry
                    # Separators: ', ', ' or ', text between links
                    # If sep_text has comma or 'or', it's a new entry
                    is_first_entry = len(entries) == 0 and current is None
                    link_tier = tier
                    if "🌐" in label.lstrip()[:2] or "🌐" in sep_text:
                        link_kind = 1
                    elif "↪" in label.lstrip()[:2] or "↪" in sep_text:
                        link_kind = 2
                    elif is_first_entry:
                        link_kind = line_kind
                    else:
                        link_kind = 0
                    new_entry(label, href, link_tier, link_kind)

        elif tok.type == "link_close":
            pass
        else:
            prev_text_tail += tok.content or ""

        i += 1

    finish_entry()
    return entries


def _scan_link_at(text: str, start_idx: int) -> tuple[str, str, int, int] | None:
    if start_idx >= len(text) or text[start_idx] != '[':
        return None
    if start_idx > 0 and text[start_idx - 1] == '!':
        b = start_idx - 2
        cnt = 0
        while b >= 0 and text[b] == '\\':
            cnt += 1
            b -= 1
        if cnt % 2 == 0:
            return None
    b = start_idx - 1
    cnt = 0
    while b >= 0 and text[b] == '\\':
        cnt += 1
        b -= 1
    if cnt % 2 != 0:
        return None

    depth = 1
    j = start_idx + 1
    raw_label = []
    while j < len(text) and depth > 0:
        c = text[j]
        if c == '\\' and j + 1 < len(text):
            raw_label.append(c)
            raw_label.append(text[j + 1])
            j += 2
            continue
        if c == '[':
            depth += 1
            raw_label.append(c)
            j += 1
        elif c == ']':
            depth -= 1
            if depth == 0:
                j += 1
                break
            else:
                raw_label.append(c)
                j += 1
        else:
            raw_label.append(c)
            j += 1

    if depth != 0 or j >= len(text) or text[j] != '(':
        return None
    j += 1  # skip '('
    while j < len(text) and text[j].isspace():
        j += 1
    if j >= len(text):
        return None

    paren_depth = 1
    url_chars = []
    in_title = False
    title_quote = None
    while j < len(text):
        c = text[j]
        if c == '\\' and j + 1 < len(text):
            if not in_title:
                url_chars.append(text[j + 1])
            j += 2
            continue
        if in_title:
            if c == title_quote:
                in_title = False
                title_quote = None
            j += 1
            continue
        if c == '(':
            paren_depth += 1
            url_chars.append(c)
            j += 1
        elif c == ')':
            paren_depth -= 1
            if paren_depth == 0:
                j += 1
                break
            else:
                url_chars.append(c)
                j += 1
        elif c.isspace():
            if paren_depth == 1:
                j += 1
                while j < len(text) and text[j].isspace():
                    j += 1
                if j < len(text) and text[j] in ('"', "'", '('):
                    in_title = True
                    title_quote = ')' if text[j] == '(' else text[j]
                    j += 1
                elif j < len(text) and text[j] == ')':
                    paren_depth = 0
                    j += 1
                    break
            else:
                url_chars.append(c)
                j += 1
        else:
            url_chars.append(c)
            j += 1

    if paren_depth != 0:
        return None

    label = ''.join(raw_label).replace('\\', '')
    url = ''.join(url_chars)
    return label, url, start_idx, j


def _scan_all_links(text: str) -> list[tuple[str, str, int, int]]:
    links = []
    i = 0
    while i < len(text):
        res = _scan_link_at(text, i)
        if res:
            links.append(res)
            i = res[3]
        else:
            i += 1
    return links


SOCIAL_LABELS = {
    "discord", "github", "gitlab", "source code", "telegram",
    "subreddit", "x", "mastodon", "bluesky", ".onion", "status"
}


def clean_dangling_separators(s: str) -> str:
    s = s.strip()
    changed = True
    while changed and s:
        changed = False
        s = s.strip()
        if s.endswith(",") or s.endswith("/"):
            s = s[:-1].strip()
            changed = True
        if s.startswith(",") or s.startswith("/"):
            s = s[1:].strip()
            changed = True
    s = re.sub(r"\s+", " ", s)
    return s


# ---------------------------------------------------------------------------
# DESC parser
# ---------------------------------------------------------------------------

def _parse_desc(desc_text: str, first_entry_extra: list[dict]) -> tuple[str, str, list[dict]]:
    """
    Parse DESC part of an entry line.
    Split on ' / ' outside link text.
    Parts that are exactly one link → extra_link of first entry.
    Other parts → tags (plain text with link labels).
    Returns (description_text, tags_pipe, extra_links_for_first_entry).
    """
    if not desc_text.strip():
        return "", "", []

    # Split DESC on ' / ' outside link brackets
    parts = _split_outside_links(desc_text, " / ")

    tag_parts = []
    extra_links = list(first_entry_extra)

    for part in parts:
        part = part.strip()
        if not part:
            continue
        if "[" not in part:
            tag_parts.append(part)
            continue
        links = _extract_inline_links(part)
        if len(links) == 1 and _is_sole_link(part, links[0]):
            # This part is exactly one link → extra link
            extra_links.append({"label": links[0].label, "url": links[0].url})
        else:
            social_links = [l for l in links if l.label.strip().lower() in SOCIAL_LABELS]
            if social_links:
                for sl in social_links:
                    extra_links.append({"label": sl.label, "url": sl.url})
                current_text = part
                positioned = _scan_all_links(current_text)
                matching = [pl for pl in positioned if pl[0].strip().lower() in SOCIAL_LABELS]
                for pl in sorted(matching, key=lambda x: x[2], reverse=True):
                    current_text = current_text[:pl[2]] + current_text[pl[3]:]
                cleaned = clean_dangling_separators(current_text)
                if cleaned:
                    tag_parts.append(_markdown_to_plain(cleaned))
            else:
                tag_parts.append(_markdown_to_plain(part))

    tags = "|".join(tag_parts)
    description = " / ".join(tag_parts)
    return description, tags, extra_links


def _split_outside_links(text: str, sep: str) -> list[str]:
    """Split text on sep, but not when inside [...] or (...)."""
    parts = []
    depth = 0
    current = []
    i = 0
    n = len(text)
    sep_len = len(sep)

    while i < n:
        ch = text[i]
        if ch == "[":
            depth += 1
            current.append(ch)
            i += 1
        elif ch == "]":
            depth -= 1
            if depth < 0:
                depth = 0
            current.append(ch)
            # Consume (...) if present
            if i + 1 < n and text[i + 1] == "(":
                i += 1
                current.append(text[i])
                i += 1
                while i < n and text[i] != ")":
                    current.append(text[i])
                    i += 1
                if i < n:
                    current.append(text[i])
                    i += 1
            else:
                i += 1
        elif depth == 0 and text[i : i + sep_len] == sep:
            parts.append("".join(current))
            current = []
            i += sep_len
        else:
            current.append(ch)
            i += 1

    parts.append("".join(current))
    return parts


def _is_sole_link(text: str, link: InlineLink) -> bool:
    """Return True if text consists of exactly one markdown link and nothing else."""
    # Strip whitespace and check if it matches [label](url) pattern
    stripped = text.strip()
    # Simple check: text is just [label](url)
    expected = f"[{link.label}]({link.url})"
    return stripped == expected


def _markdown_to_plain(text: str) -> str:
    """Convert inline markdown to plain text (links → labels, strip bold/italic)."""
    tokens = _MD.parseInline(text)
    if not tokens:
        return text
    parts = []
    for block in tokens:
        children = block.children or []
        i = 0
        while i < len(children):
            t = children[i]
            if t.type == "link_open":
                # Collect label
                i += 1
                label_parts = []
                while i < len(children) and children[i].type != "link_close":
                    ct = children[i]
                    if ct.type in ("text", "code_inline"):
                        label_parts.append(ct.content)
                    i += 1
                parts.append("".join(label_parts))
            elif t.type in ("text", "code_inline"):
                parts.append(t.content)
            elif t.type == "softbreak":
                parts.append(" ")
            i += 1
    return "".join(parts)


# ---------------------------------------------------------------------------
# Main line parser
# ---------------------------------------------------------------------------

_FRONT_MATTER_RE = re.compile(r"^---\s*$")
_RULE_RE = re.compile(r"^\s*(\*\*\*|---)\s*$")
_HEADING_RE = re.compile(r"^(#{1,4})\s+(.*)")
_BULLET_RE = re.compile(r"^(\s*)([*\-])\s+(.*)", re.DOTALL)
_NOTE_MARKER_RE = re.compile(r"^\*\*Note\*\*\s*-\s*(.*)", re.DOTALL)
_BLOCKQUOTE_RE = re.compile(r"^>\s*(.*)", re.DOTALL)
_FENCE_RE = re.compile(r"^:::")
_BACK_TO_WIKI_RE = re.compile(r"back to wiki index", re.IGNORECASE)
_HTML_LINE_RE = re.compile(r"^\s*<")
_BACK_TO_TOP_RE = re.compile(r"^\[.*\]\(#\S*\)\s*$")


def parse_section(
    section_id: str,
    section_title: str,
    content: str,
) -> tuple[list[ParsedHeading], list[ParsedEntry], list[ParsedNote], list[str]]:
    """
    Parse a wiki page's markdown content.
    Returns (headings, entries, notes, unparsed_lines).
    """
    from .slug import slugify, AnchorTracker

    lines = [strip_invisible_chars(l) for l in content.splitlines()]
    headings: list[ParsedHeading] = []
    entries: list[ParsedEntry] = []
    notes: list[ParsedNote] = []
    unparsed: list[str] = []

    # State
    in_front_matter = False
    front_matter_done = False
    in_fence = False
    ord_counter = 0  # shared ord for entries+notes
    heading_ord = 0  # ord for headings within section

    anchor_tracker = AnchorTracker()

    # Heading stack: list of ParsedHeading (current ancestors)
    heading_stack: list[ParsedHeading] = []

    def current_heading() -> ParsedHeading | None:
        return heading_stack[-1] if heading_stack else None

    def current_heading_path() -> str:
        parts = [section_title]
        for h in heading_stack:
            parts.append(h.title)
        return " › ".join(parts)

    def push_heading(h: ParsedHeading) -> None:
        # Pop headings from stack that are at same or lower level
        while heading_stack and heading_stack[-1].level >= h.level:
            heading_stack.pop()
        heading_stack.append(h)

    # Check for YAML front matter
    i = 0
    if lines and _FRONT_MATTER_RE.match(lines[0]):
        in_front_matter = True
        i = 1
        while i < len(lines):
            if _FRONT_MATTER_RE.match(lines[i]):
                in_front_matter = False
                front_matter_done = True
                i += 1
                break
            i += 1
    else:
        front_matter_done = True

    while i < len(lines):
        line = lines[i]
        line_number = i + 1
        i += 1
        raw = line

        # Skip ::: fenced blocks
        if _FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue

        # Skip lines starting with < (Vue/HTML)
        if _HTML_LINE_RE.match(line):
            continue

        # Skip *** or --- rules
        if _RULE_RE.match(line):
            continue

        # Skip "Back to Wiki Index" lines
        if _BACK_TO_WIKI_RE.search(line):
            continue

        # Skip back-to-top links like [↑](#top)
        if _BACK_TO_TOP_RE.match(line.strip()):
            continue

        # Heading
        m = _HEADING_RE.match(line)
        if m:
            level = len(m.group(1))
            raw_title = m.group(2).strip()
            # Strip ► / ▷ from title display
            display_title = raw_title.replace("►", "").replace("▷", "").strip()
            anchor = slugify(raw_title)
            unique_anchor = anchor_tracker.add(anchor)
            parent = None
            for h in reversed(heading_stack):
                if h.level < level:
                    parent = h
                    break
            h = ParsedHeading(
                level=level,
                title=display_title,
                anchor=unique_anchor,
                parent_id=parent.id if parent else None,
                id=f"{section_id}/{unique_anchor}",
                section_id=section_id,
                ord=heading_ord,
            )
            heading_ord += 1
            push_heading(h)
            headings.append(h)
            continue

        # Blockquote note
        bq = _BLOCKQUOTE_RE.match(line)
        if bq:
            text = bq.group(1).strip()
            ch = current_heading()
            n = ParsedNote(
                text_md=text,
                heading_id=ch.id if ch else None,
                section_id=section_id,
                ord=ord_counter,
            )
            ord_counter += 1
            notes.append(n)
            continue

        # Bullet line
        bm = _BULLET_RE.match(line)
        if bm:
            bullet_content = bm.group(3)

            # Note line: * **Note** - text
            nm = _NOTE_MARKER_RE.match(bullet_content)
            if nm:
                text = nm.group(1).strip()
                ch = current_heading()
                n = ParsedNote(
                    text_md=text,
                    heading_id=ch.id if ch else None,
                    section_id=section_id,
                    ord=ord_counter,
                )
                ord_counter += 1
                notes.append(n)
                continue

            # Regular bullet entry
            result = _parse_bullet_line(
                bullet_content,
                section_id=section_id,
                heading_id=current_heading().id if current_heading() else None,
                heading_path=current_heading_path(),
                ord_start=ord_counter,
                is_inline_more=0,
                line_number=line_number,
            )
            if result is None:
                unparsed.append(raw.rstrip())
                continue
            new_entries, new_notes = result
            for e in new_entries:
                e.ord = ord_counter
                ord_counter += 1
            entries.extend(new_entries)
            notes.extend(new_notes)
            if not new_entries and not new_notes:
                unparsed.append(raw.rstrip())
            continue

        # Non-bullet paragraph line with >= 1 link → "more" line (is_inline_more)
        stripped = line.strip()
        if stripped:
            links = _extract_inline_links(stripped)
            if links:
                result = _parse_inline_more_line(
                    stripped,
                    section_id=section_id,
                    heading_id=current_heading().id if current_heading() else None,
                    heading_path=current_heading_path(),
                    ord_start=ord_counter,
                    line_number=line_number,
                )
                if result:
                    for e in result:
                        e.ord = ord_counter
                        ord_counter += 1
                    entries.extend(result)
                    continue
                # No entries parsed but has links
                unparsed.append(raw.rstrip())
            # else: plain text paragraph line, skip silently

    return headings, entries, notes, unparsed


def _parse_bullet_line(
    content: str,
    section_id: str,
    heading_id: Optional[str],
    heading_path: str,
    ord_start: int,
    is_inline_more: int = 0,
    line_number: int = 0,
) -> tuple[list[ParsedEntry], list[ParsedNote]] | None:
    """
    Parse a bullet line's content (after the '* ' or '- ' marker).
    Returns (entries, notes) or None if it should be counted as unparsed.
    """
    content = strip_invisible_chars(content)
    # Detect tier and kind
    tier, kind, content = _detect_tier_and_kind(content)

    # Split HEAD and DESC
    head, desc = _split_head_desc(content)

    links_in_head = _extract_inline_links(head)
    if not links_in_head:
        desc_links = _extract_inline_links(desc)
        if not desc_links:
            return None
        raw_name = head.replace("**", "").strip()
        name = strip_leading_emoji(raw_name).strip()
        if not name:
            return None
        first_url = desc_links[0].url
        extra_links = [{"label": l.label, "url": l.url} for l in desc_links]
        _, non_link_desc = _split_head_desc(desc)
        description = _markdown_to_plain(non_link_desc).strip() if non_link_desc else ""
        tags = description

        e = ParsedEntry(
            name=name,
            url=first_url,
            tier=tier,
            kind=0 if is_inline_more else kind,
            line=line_number,
            is_inline_more=is_inline_more,
            mirrors=[],
            extra_links=extra_links,
            description=description,
            tags=tags,
            heading_id=heading_id,
            heading_path=heading_path,
            section_id=section_id,
            ord=0,  # will be set by caller
        )
        return [e], []

    # Parse head links into entry candidates
    line_kind = 0 if is_inline_more else kind
    entry_dicts = _parse_head_links(head, tier, is_inline_more, line_kind, line=line_number)
    if not entry_dicts:
        return None

    # Parse DESC
    description, tags, extra_links = _parse_desc(desc, [])

    # The extra_links from DESC go to the FIRST entry
    entries = []
    for idx, ed in enumerate(entry_dicts):
        if idx == 0:
            ed["extra_links"] = ed.get("extra_links", []) + extra_links
        e = ParsedEntry(
            name=ed["name"],
            url=ed["url"],
            tier=ed["tier"],
            kind=ed.get("kind", 0),
            line=ed.get("line", line_number),
            is_inline_more=ed["is_inline_more"],
            mirrors=ed.get("mirrors", []),
            extra_links=ed.get("extra_links", []),
            description=description,
            tags=tags,
            heading_id=heading_id,
            heading_path=heading_path,
            section_id=section_id,
            ord=0,  # will be set by caller
        )
        entries.append(e)

    return entries, []


def _parse_inline_more_line(
    content: str,
    section_id: str,
    heading_id: Optional[str],
    heading_path: str,
    ord_start: int,
    line_number: int = 0,
) -> list[ParsedEntry]:
    """Parse a non-bullet paragraph line as 'more' entries."""
    content = strip_invisible_chars(content)
    tier, kind, content2 = _detect_tier_and_kind(content)
    head, desc = _split_head_desc(content2)
    if "[" not in head:
        return []

    entry_dicts = _parse_head_links(head, tier, is_inline_more=1, line_kind=0, line=line_number)
    if not entry_dicts:
        return []

    description, tags, extra_links = _parse_desc(desc, [])

    entries = []
    for idx, ed in enumerate(entry_dicts):
        if idx == 0:
            ed["extra_links"] = ed.get("extra_links", []) + extra_links
        e = ParsedEntry(
            name=ed["name"],
            url=ed["url"],
            tier=ed["tier"],
            kind=ed.get("kind", 0),
            line=ed.get("line", line_number),
            is_inline_more=1,
            mirrors=ed.get("mirrors", []),
            extra_links=ed.get("extra_links", []),
            description=description,
            tags=tags,
            heading_id=heading_id,
            heading_path=heading_path,
            section_id=section_id,
            ord=0,
        )
        entries.append(e)
    return entries
