#!/usr/bin/env python3
"""Scan logcat for crashes, ANRs and StrictMode violations of io.github.omeg4dev.fmhy.

Groups by signature = kind + exception/violation type + top 3 frames in io.github.omeg4dev
(line numbers dropped, so a signature survives unrelated edits). Prints a short summary and
writes a JSON list [{kind, signature, count, first_lines}] to --out.

  crashscan.py [--strict-only] [--clear] [--json] [--out PATH]

Library use (journeys, monkey): scan() -> list[dict]; clear().
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import huntlib

LINE = re.compile(r"^\S+\s+\S+\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?)\s*: ?(.*)$")
OUR_FRAME = re.compile(r"at (io\.github\.omeg4dev\.[\w.$<>]+)\(")
EXC_TYPE = re.compile(r"^(?:Caused by: )?([a-zA-Z_][\w.$]*(?:Exception|Error|Throwable|Violation)\w*)")
STRICT_TYPE = re.compile(r"(android\.os\.strictmode\.\w+)")
MAX_BLOCK = 80
FIRST_LINES = 15


def read_logcat() -> list[tuple[str, str, str, str, str]]:
    out = huntlib.adb("logcat", "-d", "-b", "crash,main,system", "-v", "threadtime", timeout=90).stdout
    return [m.groups() for m in map(LINE.match, out.splitlines()) if m]


def our_pids(rows) -> set[str]:
    pids = set(huntlib.sh(f"pidof {huntlib.PKG}").split())
    for _, _, _, _, msg in rows:
        m = re.search(rf"Start proc (\d+):{re.escape(huntlib.PKG)}[/ ]", msg)
        if m:
            pids.add(m.group(1))
    return pids


def _collect(rows, i, same_tag: str, stop=None) -> tuple[list[str], int]:
    pid, tid, _, _, msg = rows[i]
    lines = [msg]
    j = i + 1
    while j < len(rows) and len(lines) < MAX_BLOCK:
        p2, t2, _, tag2, m2 = rows[j]
        if (p2, t2, tag2) != (pid, tid, same_tag) or (stop and stop(m2)):
            break
        lines.append(m2)
        j += 1
    return lines, j


def _frames(lines: list[str]) -> list[str]:
    out: list[str] = []
    for l in lines:
        m = OUR_FRAME.search(l)
        if m and m.group(1) not in out:
            out.append(m.group(1))
        if len(out) == 3:
            break
    return out


def _type(lines: list[str], kind: str) -> str:
    if kind == "strictmode":
        for l in lines[:4]:
            m = STRICT_TYPE.search(l)
            if m:
                return m.group(1).rsplit(".", 1)[-1]
        return "StrictMode"
    if kind == "anr":
        for l in lines:
            if l.strip().startswith("Reason:"):
                return re.sub(r"\d+", "#", l.strip())[:120]
        return "ANR"
    for l in lines[1:]:
        m = EXC_TYPE.match(l.strip())
        if m:
            return m.group(1)
    return "unknown"


def parse(rows, pids: set[str]) -> list[dict]:
    found: list[tuple[str, list[str]]] = []
    i = 0
    while i < len(rows):
        pid, _, _, tag, msg = rows[i]
        if tag == "AndroidRuntime" and "FATAL EXCEPTION" in msg:
            lines, j = _collect(rows, i, tag, stop=lambda m: "FATAL EXCEPTION" in m)
            if any(f"Process: {huntlib.PKG}," in l for l in lines[:3]):
                found.append(("fatal", lines))
            i = j
            continue
        if msg.startswith(f"ANR in {huntlib.PKG}"):
            lines, j = _collect(rows, i, tag, stop=lambda m: m.startswith("ANR in"))
            found.append(("anr", lines))
            i = j
            continue
        if tag == "StrictMode" and "StrictMode policy violation" in msg and (not pids or pid in pids):
            lines, j = _collect(rows, i, tag, stop=lambda m: "StrictMode policy violation" in m)
            found.append(("strictmode", lines))
            i = j
            continue
        i += 1

    groups: dict[str, dict] = {}
    for kind, lines in found:
        frames = _frames(lines)
        sig = " | ".join([kind, _type(lines, kind), *frames]) if frames else f"{kind} | {_type(lines, kind)} | (no app frame)"
        g = groups.get(sig)
        if g:
            g["count"] += 1
        else:
            groups[sig] = {"kind": kind, "signature": sig, "count": 1,
                           "first_lines": [l.rstrip() for l in lines[:FIRST_LINES]]}
    order = {"fatal": 0, "anr": 1, "strictmode": 2}
    return sorted(groups.values(), key=lambda g: (order[g["kind"]], -g["count"]))


def scan(strict_only: bool = False) -> list[dict]:
    rows = read_logcat()
    res = parse(rows, our_pids(rows))
    return [g for g in res if g["kind"] == "strictmode"] if strict_only else res


def clear() -> None:
    huntlib.adb("logcat", "-b", "crash,main,system", "-c")


def summary(res: list[dict]) -> str:
    n = {k: sum(g["count"] for g in res if g["kind"] == k) for k in ("fatal", "anr", "strictmode")}
    head = f"crashscan: {n['fatal']} fatal, {n['anr']} anr, {n['strictmode']} strictmode ({len(res)} signatures)"
    body = [f"  {g['count']:>3}x {g['signature']}" for g in res[:12]]
    if len(res) > 12:
        body.append(f"  … {len(res) - 12} more signatures in the JSON")
    return "\n".join([head, *body])


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--strict-only", action="store_true", help="only StrictMode violations")
    ap.add_argument("--clear", action="store_true", help="clear logcat after scanning")
    ap.add_argument("--json", action="store_true", help="print the JSON list to stdout instead of the summary")
    ap.add_argument("--out", help="JSON path (default .cache/hunt/crashscan-<ts>.json)")
    a = ap.parse_args()
    res = scan(a.strict_only)
    out = huntlib.out_path("crashscan", a.out)
    huntlib.write_json(out, res)
    if a.clear:
        clear()
    if a.json:
        print(json.dumps(res, indent=1, ensure_ascii=False))
    else:
        print(summary(res))
        print(f"json: {huntlib.rel(out)}")
    return 1 if any(g["kind"] in ("fatal", "anr") for g in res) else 0


if __name__ == "__main__":
    sys.exit(main())
