# FMHY 1.0.0 — batch plan

Source: the Obsidian note "FMHY 1.0.0 Changes" (2026-10-25). Each batch file holds its entries verbatim; sessions read only CLAUDE.md, [COMMON.md](COMMON.md) and their batch file.

| Batch | Entries | Title | Session model | agy model | Plan mode | Status |
|---|---|---|---|---|---|---|
| 01 | BUG-02, BUG-09, BUG-03 | [Browser reliability](BATCH-01-browser-reliability.md) | Opus | `opus` | yes | Done |
| 02 | BRW-05, BRW-07 | [FMHY status popup](BATCH-02-status-popup.md) | Sonnet | `sonnet` | yes | Done |
| 03 | BUG-10, BUG-11 | [Pastebin decoding and search glitch](BATCH-03-paste-decoding-search.md) | Sonnet | `sonnet` | no | Done |
| 04 | BUG-01 | [Sites that never load](BATCH-04-sites-never-load.md) | Opus | `opus` | yes | Done |
| 05 | BUG-04 | [One warning screen](BATCH-05-single-warning.md) | Sonnet | `sonnet` | no | Done |
| 06 | BUG-06, BRW-04 | [Browser start screen](BATCH-06-browser-start-screen.md) | Sonnet | `sonnet` | yes | Done |
| 07 | BRW-01 | [Back to FMHY and forward](BATCH-07-back-to-fmhy.md) | Sonnet | `sonnet` | no | Done |
| 08 | BUG-05, BUG-07 | [Progress display](BATCH-08-progress-display.md) | Sonnet | `sonnet` | yes | Done |
| 09 | BRW-08, BRW-09 | [Tab groups and empty tabs](BATCH-09-tab-groups-empty-tabs.md) | Sonnet | `sonnet` | yes | Done |
| 10 | THM-02 | [Accent palettes](BATCH-10-palettes.md) | Sonnet | `sonnet` | yes | Done |
| 11 | THM-01 | [Light mode pass](BATCH-11-light-mode.md) | Sonnet | `sonnet` | no | Done |
| 12 | BUG-08 | [Exact jumps and highlight](BATCH-12-exact-jumps.md) | Opus | `opus` | yes | Done |
| 13 | UI-04, UI-05 | [Section page top and following](BATCH-13-section-top-following.md) | Opus | `opus` | yes | Done |
| 14 | UI-02, UI-03 | [Swipe and tap gestures](BATCH-14-gestures.md) | Sonnet | `sonnet` | no | Done |
| 15 | UI-01 | [Website style](BATCH-15-website-style.md) | Opus | `opus` | yes | Done |
| 16 | HOME-01, HOME-02, HOME-03 | [Home screen](BATCH-16-home-screen.md) | Sonnet | `sonnet` | no | Done |
| 17 | ONB-01 | [First-run artwork](BATCH-17-first-run-artwork.md) | Sonnet | `sonnet` | no | Done |
| 18 | BRW-03 | [Wi-Fi only options](BATCH-18-wifi-only.md) | Sonnet | `sonnet` | yes | Done |
| 19 | APP-02, APP-03 | [Problem and crash reports](BATCH-19-reports.md) | Sonnet | `sonnet` | no | To do |
| 20 | APP-01 | [In-app updates](BATCH-20-in-app-updates.md) | Sonnet | `sonnet` | yes | To do |
| 21a | BRW-02 | [Download Manager (core)](BATCH-21a-download-manager-core.md) | Opus | `opus` | yes | To do |
| 21b | BRW-02 | [Download Manager (video and checks)](BATCH-21b-download-manager-checks.md) | Opus | `opus` | yes | To do |
| 22 | BRW-06 | [Downloads tied to FMHY](BATCH-22-downloads-fmhy.md) | Sonnet | `sonnet` | yes | To do |
| 23 | SET-01, SET-02 | [Settings re-sort](BATCH-23-settings-resort.md) | Sonnet | `sonnet` | yes | To do |
| 24 | QA-01 | [Bug hunt](BATCH-24-bug-hunt.md) | Sonnet | — (no agy) | no | To do |

## How to run a session

1. Start a fresh session; set the batch's model and, if the table says yes, plan mode.
2. Paste the session prompt from the top of the batch file.
3. The session reads only CLAUDE.md, COMMON.md and that batch file, and has agy agents write the code.
4. It ends with one commit and a report; check the report.
5. The status here is set to Done (the session does it in its commit; fix it by hand if not).

## Notes on the order

- The proposed order is kept. Batch 21 is split into 21a (downloader, sheet, background, folder, safety) and 21b (video button, VirusTotal / Hybrid Analysis checks), because BRW-02 is too big for one session; both carry the BRW-02 ID.
- UI-04 and UI-05 are in batch 13, but their website-style half needs website style (UI-01), so batch 15 builds it.
- BRW-03 is in batch 18; its download part is built in 21a and its update part in 20, as the plan's suggested order says.
- "Report this" buttons named by BUG-01, BUG-02, BUG-04 and BUG-07 are wired in batch 19 (APP-02).
- `docs/difficult-sites.md` is started by batch 01 and extended by batch 04; batch 24 uses it.

## Ideas parking lot (not for 1.0.0)

- Make the link checker (share to the app, popup, open) the headline feature in the README, the store listing and the first-run setup. Feedback says it is very convenient for people without browser extensions.
- A setting for how long the hold takes, or a slightly shorter default.
- FMHY suggestions in the address bar: while typing, show matching sites from the offline wiki next to the web search suggestions. This makes the browser feel like part of FMHY and gives users a reason to use it instead of a normal browser.
- Automatic, anonymous crash reports with an own small endpoint (see APP-03). Left out of 1.0.0 on purpose.
- Make the category path in the status popup tappable, so each part (section, subsection) jumps to that place in the lists.
