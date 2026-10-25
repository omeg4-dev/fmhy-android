# 1.0.0 — rules for every batch session

## Reading
- Read only CLAUDE.md, this file and your batch file. Do not read README.md here, other batch files or
  the planning note in Obsidian unless your batch file says so.
- Find symbols with `.cache/codemap.txt` and `rg -n` (refresh: `./.claude/codemap.sh`). For files over
  400 lines, use targeted reads (`sed -n 'A,Bp'`), as in CLAUDE.md.
- Line numbers in the batch file are rough. When the batch file and the code disagree, trust the code
  and say so in the final report.

## Scope
- Build only the entries in your batch file. If an entry names a feature that a later batch owns
  (the batch file says which), leave it out; no placeholder UI.
- User-facing wording follows the texts quoted in the entries exactly (button labels, messages).
- New settings go on the page that SET-01's table assigns, if that page exists today; otherwise on the
  closest existing page. Batch 23 re-sorts everything. Existing setting values must survive updates.

## Build and test
- Build and test only with `./gw` and focused `--tests` filters; never `./gradlew`.
- Keep output quiet: `./gw -q <task> --tests '<Class>' 2>&1 | tail -40`. `GW_VERBOSE=1` only to debug.
- Add unit tests for pure logic (parsing, decisions, state machines, colour maths). Extract the logic
  out of composables/WebView callbacks when that makes it testable.
- Do not start an emulator or take screenshots unless the batch file or the user asks. The user checks
  the phone; list what they should check in the report.

## Finish
1. Review the full diff (`git diff`); remove debug code and unrelated changes.
2. Run the focused tests from the batch file plus any test class you touched; show the result lines.
3. Compile check: `./gw -q :app:compileDebugKotlin 2>&1 | tail -40`.
4. Set your batch's status to "Done" in `docs/plan-1.0.0/README.md` (only that cell).
5. One commit: `feat(1.0.0): <IDs> <short title>` (use `fix(1.0.0)` / `refactor(1.0.0)` when that
   fits better). Do not commit unrelated local changes (for example `gw`).
6. Report: what changed, what was tested (with results), what was not tested, what the user should
   check on the phone, and any code/plan mismatches.
