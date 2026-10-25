# 1.0.0 — rules for every batch session

## Reading and scope
- Read only CLAUDE.md, this file and your batch file (not README.md, other batch files or the Obsidian
  note, unless your batch file says so). Symbols: `.cache/codemap.txt` + `rg -n`; targeted reads
  (`sed -n 'A,Bp'`) for files over 400 lines. When the batch file and the code disagree, trust the code
  and say so in the report.
- Build only your batch's entries; features a later batch owns stay out (no placeholder UI). Wording
  follows the entry texts exactly. New settings go on the page SET-01's table assigns if it exists,
  else the closest page; existing setting values must survive updates.

## Delegate the coding to agy (skill `agy-agents`, `~/Projects/agyctl/docs/guide.md`)
You orchestrate (read, plan, brief, watch, verify, commit); agy types the code.
1. Note the start commit (`git rev-parse HEAD`). Plan-mode batches: get the plan approved first.
2. `agyctl quota`. Use the agy model from the session prompt; `flash` for mechanical parts; independent
   parts may run in parallel. No quota in either group: Claude subagents (`model: sonnet`, never opus).
3. One brief per part in `.work/agy/BATCH-NN-<part>.md` from `~/Projects/agyctl/templates/brief.md`:
   files with line hints, the copied "Desired behavior" items, exact texts, what not to touch (`gw`,
   other batches' features, existing tests and checks), the `./gw` commands below with expected output,
   decisions to ask you about. One local commit `wip(1.0.0): BATCH-NN <part>`; no push, `gh`, installs
   or emulator.
4. `agyctl spawn --repo . --brief <file> --model <m>`; watch with the Monitor tool (`agyctl watch <id>`);
   answer questions at once (`agyctl answer`), steer (`agyctl tell`), `stop`/`resume` when it drifts.
   Only product decisions go to the user (cask).
5. Review: `agyctl show <id>`, `git diff <start>..HEAD --stat`, targeted greps; re-run its checks
   yourself. Fix ≤ ~20 lines yourself, else `agyctl resume <id> "..."`.

## Build and test
- Only `./gw`, focused `--tests`, quiet: `./gw -q <task> --tests '<Class>' 2>&1 | tail -40`.
- Unit tests for pure logic; extract it from composables/WebView callbacks to test it. No emulator or
  screenshots unless asked: the user checks the phone.

## Finish (you, not agy)
1. Re-run the focused tests and `./gw -q :app:compileDebugKotlin 2>&1 | tail -40`; show the results.
2. Review the full diff from the start commit; remove debug code and unrelated changes.
3. Set your batch's status to "Done" in `docs/plan-1.0.0/README.md` (only that cell).
4. Squash: `git reset --soft <start>`, then one commit `feat(1.0.0): <IDs> <short title>`
   (or `fix(1.0.0)` / `refactor(1.0.0)`). Never commit `gw`.
5. Report: changes, tests run (with results), not tested, agy agents and models used, phone checks for
   the user, code/plan mismatches.
