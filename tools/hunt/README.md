# tools/hunt — dynamic checks on the emulator

Deterministic evidence for a bug hunt: each script prints a short summary and writes JSON
(default `.cache/hunt/<name>-<timestamp>.json`, `--out PATH` to change). All run against the one
online emulator (or `$ANDROID_SERIAL`) and reuse `tools/emu`.

| Script | What | Typical time |
|---|---|---|
| `crashscan.py [--strict-only] [--json] [--clear]` | logcat → fatal exceptions, ANRs, StrictMode violations of our package, grouped by type + top 3 app frames; exit 1 on fatal/ANR | 2 s |
| `journeys.py [--list] [--only A,B]` | 10 scripted user flows (online) with asserts on visible text; fatal/ANR fail a journey, StrictMode is counted; screenshot only on failure | ~8 min |
| `monkey.sh [--seeds 3] [--events 4000]` / `--seed S` | offline monkey (airplane mode on, always switched off again), crashscan per seed | ~10 min default |
| `perf.py startup` / `jank home\|section\|search` / `mem` | cold start median/max, gfxinfo janky % and p90/p99, PSS growth over a 5-min loop; `--save-baseline` | 1–6 min |

StrictMode: every debug build turns it on (thread + VM policy, `penaltyLog`) unless
`adb shell setprop debug.fmhy.strict 0`; release builds never read the property. `journeys.py`
forces it to `1` and restores the previous value.

Notes
- `journeys.py onboarding` runs `pm clear` (the app's data on the emulator is reset).
- Monkey taps through the app offline and can change settings; run `journeys.py` (starts with
  `pm clear`) afterwards if a clean state matters.
- Perf numbers from swiftshader are not phone numbers: compare before/after on the same AVD and boot
  mode only; jank needs the emulator booted with `-gpu host`.
