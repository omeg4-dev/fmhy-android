"""Shared helpers for tools/hunt/*: device access via tools/emu, JSON output, timestamps."""
from __future__ import annotations

import importlib.machinery
import importlib.util
import json
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PKG = "io.github.omeg4dev.fmhy"
OUT_DIR = REPO / ".cache" / "hunt"


def load_emu():
    """tools/emu as a module (it has no .py suffix). SERIAL is picked once."""
    loader = importlib.machinery.SourceFileLoader("emu", str(REPO / "tools" / "emu"))
    spec = importlib.util.spec_from_loader("emu", loader)
    mod = importlib.util.module_from_spec(spec)
    loader.exec_module(mod)
    mod.SERIAL = mod.pick_serial()
    return mod


_EMU = None


def emu():
    global _EMU
    if _EMU is None:
        _EMU = load_emu()
    return _EMU


def adb(*args: str, timeout: float = 60, check: bool = False) -> subprocess.CompletedProcess:
    r = subprocess.run(["adb", "-s", emu().SERIAL, *args], capture_output=True, text=True,
                       timeout=timeout, errors="replace")
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip()[:300]}")
    return r


def sh(cmd: str, timeout: float = 60) -> str:
    return adb("shell", cmd, timeout=timeout).stdout.strip()


def stamp() -> str:
    return time.strftime("%Y%m%d-%H%M%S")


def out_path(name: str, given: str | None) -> Path:
    p = Path(given) if given else OUT_DIR / f"{name}-{stamp()}.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    return p


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n")


def rel(p: Path) -> str:
    try:
        return str(p.resolve().relative_to(REPO))
    except ValueError:
        return str(p)


def eprint(*a) -> None:
    print(*a, file=sys.stderr)
