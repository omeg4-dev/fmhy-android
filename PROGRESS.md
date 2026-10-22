# W3 search progress
- [x] S1 no autocorrect
- [x] S2 pills / filters as tokens (core QueryParser.tokens/QueryEdit + VM + UI + unit tests done, 1a7e53f + later; emulator pending)
- [x] S3 smart suggestions
- [x] S4 Fuzzy/Exact toggle
- [x] S5 3 categories + View all
- [x] S6 match highlight + "in link" line
- [x] S7 What's new card + Try explanations
- [x] S8 checks + evidence

## Navigation.kt edits
(none yet)
## Requests for Claude

## Results
- core:data:test 181/0 fail, app unit tests 360/0 fail, parityTest 2/0 fail, compileDebugKotlin ok; logcat -b crash empty after final run.
- Evidence: .work-scripts/shots/*.png (pills, suggestions in:/is:/site:, sheet<->pills, fuzzy/exact, 3 categories+view all, "in link:" line, Try + What's new card), s1-input.log (inputType 0x8001 -> 0x1).
- S1: Compose autoCorrectEnabled=false yields no NO_SUGGESTIONS flag, just drops AUTO_CORRECT (0x8000); keyboard suggestion strip still shows. Check-link field is W5's.
- Navigation.kt edits: none (VM constructor unchanged).
- Notes: SpanStyle background is rectangular (no rounding). Pill row auto-scrolls to the end, so with 3+ pills the first is clipped left (scrollable). WhatsNew count uses WhatsNewGrouping.collapseChanges/filterByTypes/DEFAULT_TYPES (W5 file, read-only) - merge may need a look if W5 renamed them.
- Fixed existing bug: SearchEngine read highlight columns at wrong indexes (20/21), now 21/22.
