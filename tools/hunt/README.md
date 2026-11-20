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

Logic probes (no emulator):

| Entry point | What | Typical time |
|---|---|---|
| `tools/testq :core:data:test --tests '*QueryPlanTest'` | `EXPLAIN QUERY PLAN` of every SQL statement `DatasetStore`/`UserStore` run (bundled snapshot + seeded user DB); fails on an unindexed `SCAN` of a table ≥ 1000 rows unless allowlisted; known findings are printed as `QUERY-PLAN FINDING` | ~20 s |
| `tools/testq :core:data:test --tests '*FmhyParserFuzzTest'` | 2000 seeded mutations of real sections (`FUZZ_SEED=<n>` to replay); invariants: no throw, http(s) links only, entry count/line bounds, deterministic, no blank names | ~15 s |
| `uv run --project pipeline pytest pipeline/tests/test_fuzz.py -q` | same invariants for the Python `parse.py` (hypothesis, 300 examples per invariant, no shrinking) | ~75 s |
| `tools/hunt/parity.sh [--rev REV]` | Python reference dump of `.cache/fmhy-edit` REV, then Kotlin `ParityTest`; prints `PARITY OK` or the first 20 differing rows | ~15 s |

Known findings stay in the tests as `@Ignore("hunt finding: …")`, `pytest.mark.xfail(strict=True)` or
`knownFindings` (QueryPlanTest), so a fix shows up as an unexpected pass.

StrictMode: every debug build turns it on (thread + VM policy, `penaltyLog`) unless
`adb shell setprop debug.fmhy.strict 0`; release builds never read the property. `journeys.py`
forces it to `1` and restores the previous value.

Notes
- `journeys.py onboarding` runs `pm clear` (the app's data on the emulator is reset).
- Monkey taps through the app offline and can change settings; run `journeys.py` (starts with
  `pm clear`) afterwards if a clean state matters.
- Perf numbers from swiftshader are not phone numbers: compare before/after on the same AVD and boot
  mode only; jank needs the emulator booted with `-gpu host`.
