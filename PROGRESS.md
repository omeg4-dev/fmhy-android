# W5 progress (p9-whatsnew)
- [x] 1 S1 autoCorrectEnabled=false + Capitalization.None. inputType before 0x8011 (autocorrect flag 0x8000) -> after 0x11. "Example.Com/Path" kept verbatim.
- [x] 2 WN hub + search (WhatsNewHub.kt, filterByText + test, type param). Emulator verify: see below
- [x] 3 C1 verified on emulator: no history partials, dimmed previous verdict + progress, hero + "Found on FMHY" with Section > heading breadcrumbs. Shots .work-scripts/shots/c*.png h1-history.png
- [ ] 4 checks + evidence
- unit tests 355/0 failed (--no-build-cache), compile ok

## Navigation.kt edits
- entry<WhatsNewNavKey> (~l.547): viewModel key + ctor pass key.type; `startInHub = key.filter == "all" && key.type == null`
## Requests for Claude
- Matches in "Found on FMHY" do not navigate to section yet if not done (see report)
